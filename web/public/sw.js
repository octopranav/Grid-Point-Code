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

    // The basemap. Held as it is used, so a place already looked at is still
    // drawn with the network cut -- and so the switch refuses tiles only after
    // the cache has been asked, rather than instead of asking it.
    if (url.origin === BASEMAP) {

        // The style and the tile index are not versioned, so they are asked
        // for first; everything else names its own contents in its path.
        const named = url.pathname.startsWith('/styles/') || url.pathname === '/planet';
        return event.respondWith(
            named ? freshest(request, MAP) : held(request, MAP),
        );
    }

    // Any other origin: nothing on this site uses one, and a switch that let an
    // unknown third party through would be reporting something it had not
    // tested.
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
    const hit = await cache.match(request, { ignoreVary: true });
    if (hit) return hit;

    const response = await reach(request);
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
        const response = await reach(request);
        if (response.ok) await cache.put(request, response.clone());
        return response;
    } catch (offline) {
        // `ignoreVary` because the fallback is the whole point. A response
        // stored with a Vary header will not match a later request whose
        // headers differ by so much as an encoding, and a shell that misses
        // for that reason is a shell that was never there.
        const hit = await cache.match(request, { ignoreVary: true });
        if (hit) return hit;

        // A page, with the door shut and nothing held: this is the one case
        // that must not become the browser's own error screen. There would be
        // no way back from it -- every page on this origin is refused, so
        // nothing can load to offer the switch again, and the reader would be
        // left with a site that looks broken by us rather than by them.
        if (shut && isPage(request)) return wayBack();
        throw offline;
    }
}

/**
 * The page that exists so the switch can never strand anybody.
 *
 * Self-contained on purpose: no stylesheet, no module, nothing that would have
 * to be fetched through a worker that is currently refusing to fetch.
 */
function wayBack() {
    const body = `<!doctype html>
<html lang="en">
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>The network is switched off</title>
<style>
  :root { color-scheme: light dark; }
  body { margin: 0; min-height: 100vh; display: grid; place-items: center;
         background: #E9EEEF; color: #0E1F28; padding: 1.5rem;
         font: 16px/1.6 "IBM Plex Sans", system-ui, sans-serif; }
  main { max-width: 34rem; }
  h1 { font-family: Bitter, Georgia, serif; font-size: 1.4rem; margin: 0 0 .75rem; }
  p { margin: 0 0 1rem; color: #47606B; }
  button { font: inherit; padding: .5rem 1rem; border: 1px solid #C2CED3;
           border-radius: 3px; background: #FDFEFE; color: #0E1F28; cursor: pointer; }
  button:hover { border-color: #8A6110; }
  @media (prefers-color-scheme: dark) {
    body { background: #0A151B; color: #DFE9EC; }
    p { color: #9FB4BC; }
    button { background: #102028; color: #DFE9EC; border-color: #273C46; }
  }
</style>
<main>
  <h1>The network is switched off</h1>
  <p>
    You switched it off on this site to see what still works. This page was not
    one of the ones already held, so there was nothing to show you — which is
    exactly what would have happened on a train.
  </p>
  <p>Letting the network back in needs no network of its own.</p>
  <button type="button" id="back">Let the network back in</button>
</main>
<script>
  document.getElementById('back').addEventListener('click', async () => {
    const registration = await navigator.serviceWorker.ready;
    (navigator.serviceWorker.controller || registration.active)
        .postMessage({ gpc: 'network', off: false });
    try { localStorage.setItem('gpc-network', 'on'); } catch (blocked) {}
    setTimeout(() => location.reload(), 250);
  });
</script>
</html>`;

    return new Response(body, {
        status: 503,
        headers: { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' },
    });
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
