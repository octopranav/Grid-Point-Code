// Keeps the site, and the landmarks it looks up, working with the network cut.
//
// A Grid Point Code needs no lookup: turning a coordinate into ten characters
// is arithmetic the browser does on its own, and that has been true since the
// first page loaded. The one part that does need data is the list of landmarks
// a short form can be anchored to, and it would be a strange claim to make for
// an offline format if that list were the thing that broke on a train.
//
// Four stores, because four kinds of thing expire differently.
//
//   the shell     HTML asked for over the network first, so a deployment is
//                 picked up the moment there is a network to pick it up from,
//                 falling back to the held copy when there is not. Astro's
//                 built assets carry a hash in the name, so a copy of one is
//                 never wrong -- those are served from the cache first.
//
//   the manifest  Network first, same reasoning, and it is the thing that
//                 names the two stores below.
//
//   kept shards   What a reader asked for by name, through the page. Read
//                 first and never written from here: it is theirs.
//
//   seen shards   Whatever this worker served along the way, which is why a
//                 place already looked at still works with the network cut.
//
// Both shard stores are named for the moment the archive was built. Shard
// names do not change between builds, only what is inside them, so without
// that stamp a held copy would be served for as long as the browser kept it.

const SHELL = 'gpc-shell';
const META = 'gpc-meta';

// What a reader deliberately kept, and what this worker cached along the way.
// It reads both and writes only the second, so pressing Forget in the page
// gives back an area for good rather than until the next lookup refills it.
const KEPT = 'gpc-kept-';
const RUNTIME = 'gpc-landmarks-';

const MANIFEST = new URL('landmarks/manifest.json', self.registration.scope).pathname;

// ── the switch ──────────────────────────────────────────────────────────────
//
// The site claims that cutting the network changes nothing except the basemap.
// A claim like that is worth very little if testing it means finding a setting
// in the operating system, so the page can close the door itself.
//
// **It fails the way the real thing fails.** A dead connection rejects a fetch
// with a TypeError, and so does this -- which means every fallback already
// written below is the code being exercised, rather than a second path built to
// imitate it. A simulation that failed differently would be testing the
// simulation.
//
// The state outlives the worker, which is stopped and restarted freely, so it
// is kept in a cache rather than a variable. It outlives a reload too, on
// purpose: reloading is the interesting test, because it is the one that finds
// out whether the page really came from the cache.

const SWITCH = new URL('__network', self.registration.scope).pathname;

let switched = null;

// The same answer, readable without waiting.
//
// `fetch` has to decide synchronously whether to take a request over at all,
// and for the requests this worker otherwise leaves alone -- another origin's
// map tiles above all -- there is nothing to await inside. It starts closed-
// door-open on a fresh worker and is corrected by the first same-origin
// request, which always precedes a tile.
let shut = false;

async function cut() {
    switched ??= (async () => {
        const cache = await caches.open(META);
        const held = await cache.match(SWITCH);
        return held ? (await held.text()) === 'off' : false;
    })();
    shut = await switched;
    return shut;
}

async function setCut(off) {
    const cache = await caches.open(META);
    await cache.put(SWITCH, new Response(off ? 'off' : 'on'));
    switched = Promise.resolve(off);
    shut = off;
    await announce();
}

async function announce(to) {
    const off = await cut();
    const clients = to ? [to] : await self.clients.matchAll({ includeUncontrolled: true });
    for (const client of clients) client.postMessage({ gpc: 'network', off });
}

/**
 * The only place this worker touches the network.
 *
 * Everything below goes through it, so the switch is one condition rather than
 * four, and a fifth caller added later cannot forget.
 */
async function reach(input, init) {
    if (await cut()) throw new TypeError('the network is switched off in this page');
    return fetch(input, init);
}

self.addEventListener('message', (event) => {
    const asked = event.data;
    if (!asked || asked.gpc !== 'network') return;
    event.waitUntil(
        asked.ask ? announce(event.source) : setCut(Boolean(asked.off)),
    );
});

self.addEventListener('install', () => self.skipWaiting());

self.addEventListener('activate', (event) => {
    event.waitUntil(
        (async () => {
            await self.clients.claim();
            await sweep();
        })(),
    );
});

/** Forget shard caches from builds that are no longer the current one. */
async function sweep() {
    const current = await shardCaches();
    for (const name of await caches.keys()) {
        if (!name.startsWith(KEPT) && !name.startsWith(RUNTIME)) continue;
        if (current && (name === current.kept || name === current.runtime)) continue;
        await caches.delete(name);
    }
}

// Asked once per worker lifetime rather than once per shard: the worker is
// stopped and restarted often enough on its own that this stays fresh, and a
// round trip before every lookup would undo the point of caching them.
let naming = null;

async function manifest() {
    const cache = await caches.open(META);
    try {
        const fresh = await reach(MANIFEST, { cache: 'no-store' });
        if (fresh.ok) {
            await cache.put(MANIFEST, fresh.clone());
            return await fresh.json();
        }
    } catch {
        /* no network: the held copy below is exactly what it is for */
    }
    const held = await cache.match(MANIFEST);
    return held ? await held.json() : null;
}

function shardCaches() {
    naming ??= manifest().then((described) =>
        described && described.built
            ? { kept: KEPT + described.built, runtime: RUNTIME + described.built }
            : null,
    );
    return naming;
}

/**
 * The icons and the web app manifest.
 *
 * These are generated from the design tokens and keep the same names forever,
 * which is exactly what `held` says it must not be given: cache-first would
 * pin the first favicon a reader ever saw and no later deploy would move it.
 * Network first, cached as it goes, so they are still there with the network
 * cut and still correct when it comes back.
 */
const STABLE = new Set(
    ['favicon.svg', 'apple-touch-icon.png', 'icon-192.png', 'icon-512.png',
     'site.webmanifest']
        .map((name) => new URL(name, self.registration.scope).pathname),
);

const isShard = (path) => path.includes('/landmarks/') && path !== MANIFEST;
const isHashed = (path) => path.includes('/_astro/') || /\.(woff2?|png|svg|webp)$/.test(path);
const isPage = (request) =>
    request.mode === 'navigate' || (request.headers.get('accept') ?? '').includes('text/html');

self.addEventListener('fetch', (event) => {
    const { request } = event;
    if (request.method !== 'GET') return;

    const url = new URL(request.url);

    // Another origin: the basemap, and nothing else. Left alone entirely while
    // the door is open, and refused while it is shut -- because the tiles are
    // the one thing on this site that genuinely needs a network, and a switch
    // that left them loading would let a reader conclude something the page
    // does not claim.
    if (url.origin !== self.location.origin) {
        if (shut) event.respondWith(refused());
        return;
    }

    if (url.pathname === MANIFEST) return event.respondWith(freshest(request, META));
    if (isShard(url.pathname)) return event.respondWith(shard(request));
    if (STABLE.has(url.pathname)) return event.respondWith(freshest(request, SHELL));
    if (isHashed(url.pathname)) return event.respondWith(held(request, SHELL));
    if (isPage(request)) return event.respondWith(freshest(request, SHELL));

    // Anything else of ours that has no rule above: served normally, refused
    // when shut. Shut has to mean shut, or the experiment proves nothing.
    if (shut) event.respondWith(refused());
});

const refused = () =>
    Promise.reject(new TypeError('the network is switched off in this page'));

/** Cache first. For things whose name changes when their contents do. */
async function held(request, name) {
    const cache = await caches.open(name);
    const hit = await cache.match(request);
    if (hit) return hit;

    const response = await reach(request);
    if (response.ok) await cache.put(request, response.clone());
    return response;
}

/** Network first, falling back to whatever was kept. */
async function freshest(request, name) {
    const cache = await caches.open(name);
    try {
        const response = await reach(request);
        if (response.ok) await cache.put(request, response.clone());
        return response;
    } catch (offline) {
        const hit = await cache.match(request);
        if (hit) return hit;
        throw offline;
    }
}

async function shard(request) {
    const names = await shardCaches();
    if (!names) return reach(request);       // nothing deployed to cache against

    // What the reader asked to keep is looked at first, then what happened to
    // be cached before.
    const keeping = await caches.open(names.kept);
    const kept = await keeping.match(request);
    if (kept) return kept;

    const runtime = await caches.open(names.runtime);
    const seen = await runtime.match(request);
    if (seen) return seen;

    const response = await reach(request);

    // A 404 is a real answer here -- most of the planet is ocean and has no
    // shard -- but not one worth keeping, since a later build may put something
    // there. Kept areas are never written from here: they are the reader's.
    if (response.ok) await runtime.put(request, response.clone());
    return response;
}
