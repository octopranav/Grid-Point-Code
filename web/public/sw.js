// Keeps the pages, and the landmarks already looked up, available offline.
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
//                 place already looked at still works without a connection.
//
// Both shard stores are named for the moment the archive was built. Shard
// names do not change between builds, only what is inside them, so without
// that stamp a held copy would be served for as long as the browser kept it.

const SHELL = 'gpc-shell';
const META = 'gpc-meta';

/**
 * The basemap: tiles, glyphs, sprites and the style that names them.
 *
 * Everything the map needs comes from one host, and it is the only part of this
 * site that is not arithmetic. Kept as it is used rather than pre-packed: the
 * provider publishes its planet for self-hosting only as an image of the whole
 * world, so there is no honest way to hand a reader one region -- but there is
 * an obvious way to hand them back the places they have already been.
 *
 * The tile path carries the date the provider built it
 * (`/planet/20260830_080001_pt/...`), so a tile URL names its contents and can
 * be kept indefinitely. The style and the tile index are not versioned that
 * way, so those are asked for first and only fall back to what is held.
 */
const MAP = 'gpc-map';
const BASEMAP = 'https://tiles.openfreemap.org';

/**
 * How many basemap responses to keep.
 *
 * A reader who pans across a country would otherwise fill their disk quietly.
 * The Cache API hands back keys in the order they were written, so the oldest
 * are the ones that go -- an approximation of least-recently-used that costs no
 * bookkeeping of its own.
 */
const MAP_MOST = 1500;

// What a reader deliberately kept, and what this worker cached along the way.
// It reads both and writes only the second, so pressing Forget in the page
// gives back an area for good rather than until the next lookup refills it.
const KEPT = 'gpc-kept-';
const RUNTIME = 'gpc-landmarks-';

const MANIFEST = new URL('landmarks/manifest.json', self.registration.scope).pathname;

self.addEventListener('install', () => self.skipWaiting());

self.addEventListener('activate', (event) => {
    event.waitUntil(
        (async () => {
            await self.clients.claim();
            await sweep();
            // The page once had a control that made this worker refuse the
            // network, and it kept its state here. The control is gone; a reader
            // who left it switched off should not carry the leftover forever.
            await (await caches.open(META)).delete(
                new URL('__network', self.registration.scope).pathname,
            );
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
        const fresh = await fetch(MANIFEST, { cache: 'no-store' });
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

    // The basemap. Held as it is used, so a place already looked at is still
    // drawn without a connection. A place never looked at needs the network,
    // and nothing here can change that.
    if (url.origin === BASEMAP) {

        // The style and the tile index are not versioned, so they are asked
        // for first; everything else names its own contents in its path.
        const named = url.pathname.startsWith('/styles/') || url.pathname === '/planet';
        return event.respondWith(
            named ? freshest(request, MAP) : held(request, MAP),
        );
    }

    // Any other origin is the browser's business, not this worker's.
    if (url.origin !== self.location.origin) return;

    if (url.pathname === MANIFEST) return event.respondWith(freshest(request, META));
    if (isShard(url.pathname)) return event.respondWith(shard(request));
    if (STABLE.has(url.pathname)) return event.respondWith(freshest(request, SHELL));
    if (isHashed(url.pathname)) return event.respondWith(held(request, SHELL));
    if (isPage(request)) return event.respondWith(freshest(request, SHELL));
});

/** Cache first. For things whose name changes when their contents do. */
async function held(request, name) {
    const cache = await caches.open(name);
    const hit = await cache.match(request, { ignoreVary: true });
    if (hit) return hit;

    const response = await fetch(request);
    if (response.ok) {
        await cache.put(request, response.clone());
        if (name === MAP) await keepBounded(cache);
    }
    return response;
}

/**
 * Drop the oldest entries once a store has grown past what it is allowed.
 *
 * Only counted every so often: `keys()` walks the whole store, and doing that
 * on every tile would cost more than the tiles.
 */
let puts = 0;

async function keepBounded(cache) {
    if (++puts % 50 !== 0) return;
    const keys = await cache.keys();
    if (keys.length <= MAP_MOST) return;
    for (const old of keys.slice(0, keys.length - MAP_MOST)) await cache.delete(old);
}

/** Network first, falling back to whatever was kept. */
async function freshest(request, name) {
    const cache = await caches.open(name);
    try {
        const response = await fetch(request);
        if (response.ok) await cache.put(request, response.clone());
        return response;
    } catch (offline) {
        // `ignoreVary` because the fallback is the whole point. A response
        // stored with a Vary header will not match a later request whose
        // headers differ by so much as an encoding, and a shell that misses
        // for that reason is a shell that was never there.
        const hit = await cache.match(request, { ignoreVary: true });
        if (hit) return hit;
        throw offline;
    }
}

async function shard(request) {
    const names = await shardCaches();
    if (!names) return fetch(request);       // nothing deployed to cache against

    // What the reader asked to keep is looked at first, then what happened to
    // be cached before.
    const keeping = await caches.open(names.kept);
    const kept = await keeping.match(request);
    if (kept) return kept;

    const runtime = await caches.open(names.runtime);
    const seen = await runtime.match(request);
    if (seen) return seen;

    const response = await fetch(request);

    // A 404 is a real answer here -- most of the planet is ocean and has no
    // shard -- but not one worth keeping, since a later build may put something
    // there. Kept areas are never written from here: they are the reader's.
    if (response.ok) await runtime.put(request, response.clone());
    return response;
}
