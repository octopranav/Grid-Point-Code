// Keeps the site, and the landmarks it looks up, working with the network cut.
//
// A Grid Point Code needs no lookup: turning a coordinate into ten characters
// is arithmetic the browser does on its own, and that has been true since the
// first page loaded. The one part that does need data is the list of landmarks
// a short form can be anchored to, and it would be a strange claim to make for
// an offline format if that list were the thing that broke on a train.
//
// Three caches, because three kinds of thing expire differently.
//
//   the shell     HTML asked for over the network first, so a deployment is
//                 picked up the moment there is a network to pick it up from,
//                 falling back to the held copy when there is not. Astro's
//                 built assets carry a hash in the name, so a copy of one is
//                 never wrong -- those are served from the cache first.
//
//   the manifest  Network first, same reasoning, and it is the thing that
//                 names the current shard cache.
//
//   the shards    Cache first, and named for the moment the data was built.
//                 Shard names do not change between builds, only what is
//                 inside them, so without that stamp a held copy would be
//                 served for as long as the browser felt like keeping it.

const SHELL = 'gpc-shell';
const META = 'gpc-meta';
const SHARDS = 'gpc-landmarks-';

const MANIFEST = new URL('landmarks/manifest.json', self.registration.scope).pathname;

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
    const current = await shardCache();
    for (const name of await caches.keys()) {
        if (name.startsWith(SHARDS) && name !== current) await caches.delete(name);
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

function shardCache() {
    naming ??= manifest().then((described) =>
        described && described.built ? SHARDS + described.built : null,
    );
    return naming;
}

const isShard = (path) => path.includes('/landmarks/') && path !== MANIFEST;
const isHashed = (path) => path.includes('/_astro/') || /\.(woff2?|png|svg|webp)$/.test(path);
const isPage = (request) =>
    request.mode === 'navigate' || (request.headers.get('accept') ?? '').includes('text/html');

self.addEventListener('fetch', (event) => {
    const { request } = event;
    if (request.method !== 'GET') return;

    const url = new URL(request.url);
    if (url.origin !== self.location.origin) return;

    if (url.pathname === MANIFEST) return event.respondWith(freshest(request, META));
    if (isShard(url.pathname)) return event.respondWith(shard(request));
    if (isHashed(url.pathname)) return event.respondWith(held(request, SHELL));
    if (isPage(request)) return event.respondWith(freshest(request, SHELL));
});

/** Cache first. For things whose name changes when their contents do. */
async function held(request, name) {
    const cache = await caches.open(name);
    const hit = await cache.match(request);
    if (hit) return hit;

    const response = await fetch(request);
    if (response.ok) await cache.put(request, response.clone());
    return response;
}

/** Network first, falling back to whatever was kept. */
async function freshest(request, name) {
    const cache = await caches.open(name);
    try {
        const response = await fetch(request);
        if (response.ok) await cache.put(request, response.clone());
        return response;
    } catch (offline) {
        const hit = await cache.match(request);
        if (hit) return hit;
        throw offline;
    }
}

async function shard(request) {
    const name = await shardCache();
    if (!name) return fetch(request);        // nothing deployed to cache against

    const cache = await caches.open(name);
    const hit = await cache.match(request);
    if (hit) return hit;

    const response = await fetch(request);

    // A 404 is a real answer here -- most of the planet is ocean and has no
    // shard -- but it is not one worth keeping, because the next build may put
    // something there.
    if (response.ok) await cache.put(request, response.clone());
    return response;
}
