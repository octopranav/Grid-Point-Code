//  Copyright 2017 Pranavkumar Patel
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

// The places the site opens on, and the places it has a page for.
//
//   node scripts/build-places.mjs --geonames <dir> [--out src/data/places.json]
//
// <dir> needs countryInfo.txt, admin1CodesASCII.txt and cities1000.txt from
// https://download.geonames.org/export/dump/. Run it by hand when the list
// changes; the output is committed, so a build never reaches for the network.
//
// Three sets, for three jobs.
//
// **Featured places** are what a visitor is shown first: a market, a bazaar,
// an old quarter in their own country. Somewhere busy and well known where
// the stalls, shops and doors have no address of their own, and directions are
// given the way they are given there: beside this, behind that. That is the
// problem the format exists for, so it is the example a visitor is shown. Not
// a religious site, and not in territory two states contest.
//
// **Sights** are famous places with no address at all: waterfalls, canyons,
// ruins. They keep a page each, because people search for them, but they are
// not what the site opens on. A waterfall has no door to give an address to.
//
// Both are chosen by a person, in featured-places.txt and sights.txt, one
// English Wikipedia title or Wikidata item per line. Every fact about a choice
// is read, not typed: the title resolves through Wikipedia's own redirects to
// exactly one Wikidata item, and its coordinates, its country and its region
// come from there and from GeoNames. A name search was tried first and
// resolved "Petra" to a given name, which is why the title is the key. An item
// is accepted too, for a market with no English article.
//
// **Cities** are what a search engine is shown: every national capital, every
// city of half a million or more, and every regional capital of a quarter
// million or more. That rule is what catches Toronto, which a cut at a million
// would keep, and Seattle and San Francisco, which it would not.
//
// The script refuses to write anything it cannot check. A title that does not
// resolve, a place whose country does not match, or one that turns out to carry
// a religion statement stops the run with the reason. So does a sight with a
// street address. A market with one is only reported: the market as a whole
// may have an address while none of the stalls inside it do.

import { readFile, writeFile, mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const web = path.resolve(here, '..');

const AGENT = 'gridpointcode-places/1.0 (https://gridpointcode.com)';

// ── what gets excluded ───────────────────────────────────────────────────────

/**
 * Words in what Wikidata says a place is that rule it out.
 *
 * Religious sites, by the project's decision, and the places a sightseeing
 * example should not be made of. Ancient funerary sites are deliberately not
 * here: the pyramids at Giza are Egypt's most visited place and excluding them
 * as a tomb would be a technicality, not a judgement.
 */
const RULED_OUT = [
    'church', 'cathedral', 'basilica', 'chapel', 'abbey', 'monaster', 'convent',
    'mosque', 'synagogue', 'shrine', 'pagoda', 'stupa', 'gurdwara', 'mandir',
    'religious', 'place of worship', 'pilgrim', 'sacred', 'holy site',
    'cemetery', 'memorial', 'concentration camp', 'extermination', 'genocide',
    'prison', 'massacre', 'battlefield',
];

/**
 * Territory two states actively contest, as rings of [longitude, latitude].
 *
 * Generous, because a place left out near a border costs one example and a
 * place put in contested territory costs the page its neutrality. Not so
 * generous that a capital falls in: the first version of these was boxes, and
 * the Kashmir box held Islamabad, the Transnistria box Chisinau, the northern
 * Cyprus box Nicosia and the Essequibo box a Brazilian state capital. Every
 * city this excludes is printed, so that cannot happen quietly again.
 */
const box = (south, west, north, east) =>
    [[west, south], [east, south], [east, north], [west, north]];

const CONTESTED = {
    'Crimea': box(44.3, 32.4, 46.3, 36.7),
    'Kashmir and Aksai Chin': [
        [73.60, 32.95], [73.35, 33.60], [73.35, 34.30], [73.60, 34.90], [72.50, 36.10],
        [73.60, 36.90], [74.60, 37.10], [75.90, 36.95], [77.80, 35.90], [80.30, 35.60],
        [80.40, 34.30], [79.40, 32.40], [78.40, 32.40], [76.70, 32.70], [75.70, 32.35],
        [74.75, 32.62],
    ],
    'Western Sahara': box(21.3, -17.2, 27.7, -8.7),
    'Golan Heights': box(32.6, 35.6, 33.35, 35.95),
    'West Bank and Jerusalem': box(31.3, 34.95, 32.6, 35.6),
    'Gaza': box(31.2, 34.2, 31.6, 34.6),
    'Abkhazia': box(42.4, 40.0, 43.6, 41.7),
    'South Ossetia': box(42.05, 43.4, 42.8, 44.6),
    'Transnistria': box(46.5, 29.2, 48.2, 30.2),
    'Northern Cyprus': box(35.19, 32.7, 35.7, 34.6),
    'Nagorno-Karabakh': box(39.2, 46.1, 40.3, 47.3),
    // A polygon, not a box: a box reaching the eastern edge of Luhansk also
    // reached Rostov-on-Don.
    'Occupied eastern Ukraine': [
        [37.45, 47.05], [38.20, 47.10], [39.75, 47.83], [40.20, 48.90], [39.80, 49.60],
        [38.30, 50.05], [37.90, 49.20], [37.70, 48.60], [37.45, 47.90],
    ],
    'Occupied southern Ukraine': box(46.0, 33.0, 47.4, 37.3),
    'Kuril Islands': box(43.3, 145.4, 45.6, 148.9),
    'Senkaku Islands': box(25.6, 123.3, 26.0, 124.7),
    'Arunachal Pradesh': box(26.9, 91.5, 29.5, 97.4),
    'South China Sea islands': box(8.0, 111.0, 17.2, 117.5),
    'Falkland Islands': box(-52.5, -61.5, -51.0, -57.5),
    'Gibraltar': box(36.1, -5.37, 36.16, -5.33),
    'Somaliland': box(8.0, 43.2, 11.5, 49.0),
    "Hala'ib Triangle": box(21.8, 35.6, 22.3, 36.9),
    'Abyei': box(9.2, 27.8, 10.3, 29.3),
    'Essequibo': [
        [-59.80, 8.30], [-58.45, 7.00], [-58.60, 5.00], [-58.80, 3.00], [-59.60, 1.90],
        [-59.90, 2.30], [-59.90, 2.70], [-59.80, 3.60], [-60.10, 4.50], [-61.30, 5.20],
        [-61.40, 5.90], [-60.70, 6.20], [-60.90, 6.80], [-61.10, 7.20], [-60.50, 7.80],
    ],
};

/** Ray casting. */
function inside(lon, lat, ring) {
    let hit = false;
    for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
        const [xi, yi] = ring[i];
        const [xj, yj] = ring[j];
        if ((yi > lat) !== (yj > lat) && lon < ((xj - xi) * (lat - yi)) / (yj - yi) + xi) {
            hit = !hit;
        }
    }
    return hit;
}

function contested(lat, lon) {
    for (const [name, ring] of Object.entries(CONTESTED)) {
        if (inside(lon, lat, ring)) return name;
    }
    return null;
}

/**
 * Layers of an administrative hierarchy nobody writes in an address.
 *
 * Wikidata puts statistical regions and administrative groupings above the
 * region a reader would name: without this, the Dune of Pilat was in
 * "metropolitan France", and Greece's places were in a "Decentralized
 * Administration" with a name longer than the sentence around it.
 */
const UNSPOKEN = [
    'metropolitan ', 'decentralized administration', 'eastern denmark',
    'western denmark', 'adriatic croatia', 'pannonian croatia', 'northern croatia',
    'central serbia', 'serbia-north', 'serbia-south',
];

const unspoken = (label) => {
    const lower = (label ?? '').toLowerCase();
    return UNSPOKEN.some((u) => lower.startsWith(u) || lower === u.trim());
};

/**
 * GeoNames records known to be wrong.
 *
 * 2180221, an Auckland suburb with a population of 5,550, placed on the South
 * Island's West Coast. It made Aoraki / Mount Cook "near" it.
 *
 * 2422488, Camayenne, a neighbourhood of Conakry recorded with 1.87 million
 * people, nearly the whole city's. It took a market in Conakry out of Conakry
 * and gave a neighbourhood a page as a city.
 */
const WRONG_IN_GEONAMES = new Set(['2180221', '2422488']);

/** Where GeoNames' English name for a country is not the one a reader uses. */
const COUNTRY_NAMES = {
    NL: 'Netherlands', CI: "Côte d'Ivoire", RE: 'Réunion', CW: 'Curaçao',
    BL: 'Saint Barthélemy', ST: 'São Tomé and Príncipe', AX: 'Åland', TL: 'Timor-Leste',
};

// ── the network, politely ────────────────────────────────────────────────────

async function get(url) {
    for (let attempt = 0; attempt < 5; attempt++) {
        try {
            const response = await fetch(url, { headers: { 'User-Agent': AGENT } });
            if (response.ok) return await response.json();
        } catch {
            /* retried below */
        }
        await new Promise((done) => setTimeout(done, 1500 * (attempt + 1)));
    }
    throw new Error(`could not fetch ${url.slice(0, 120)}`);
}

async function getText(url) {
    const response = await fetch(url, { headers: { 'User-Agent': AGENT } });
    if (!response.ok) throw new Error(`${response.status} for ${url}`);
    return response.text();
}

const pause = () => new Promise((done) => setTimeout(done, 300));

function* batches(list, size = 50) {
    for (let i = 0; i < list.length; i += size) yield list.slice(i, i + size);
}

const wikidata = (params) =>
    get('https://www.wikidata.org/w/api.php?' + new URLSearchParams({ format: 'json', ...params }));

/** Statements that are true now: not deprecated, not ended, preferred if any. */
function current(claims, property) {
    const live = (claims?.[property] ?? []).filter(
        (c) => c.rank !== 'deprecated' && !c.qualifiers?.P582,
    );
    const preferred = live.filter((c) => c.rank === 'preferred');
    return preferred.length ? preferred : live;
}

const itemIds = (entity, property) =>
    current(entity?.claims, property)
        .map((c) => c.mainsnak?.datavalue?.value?.id)
        .filter(Boolean);

// ── names into addresses ─────────────────────────────────────────────────────

/**
 * Punctuation written plainly, the way the rest of the site is written.
 *
 * Wikipedia and GeoNames set names with typographic dashes and apostrophes, so
 * a Vietnamese national park arrived with an en dash in its name and a Chinese
 * city with a curly apostrophe in its. On a page those read as a different hand from the
 * prose around them, and the plain spellings are standard too. Letters are
 * never touched: accents stay, and so does any letter that merely looks like
 * punctuation.
 */
export function plain(text) {
    if (typeof text !== 'string') return text;
    return text
        .replace(/[\u2018\u2019\u02BC\u00B4]/g, "'")
        .replace(/[\u201C\u201D]/g, '"')
        .replace(/[\u2013\u2014]/g, '-')
        .replace(/\u00A0/g, ' ')
        .replace(/[\u200B-\u200D\uFEFF]/g, '');
}

const LETTERS = { đ: 'd', ł: 'l', ø: 'o', æ: 'ae', ħ: 'h', ß: 'ss', þ: 'th', œ: 'oe', ı: 'i' };

/** A URL segment a reader could type: accents folded, everything else a hyphen. */
export function slugify(text) {
    return plain(text)
        .toLowerCase()
        // Xi'an is xian and Saint John's is saint-johns: an apostrophe joins.
        .replace(/'/g, '')
        .replace(/[đłøæħßþœı]/g, (c) => LETTERS[c])
        .normalize('NFKD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-+|-+$/g, '');
}

/** "Twelve Apostles (Victoria)" is searched for as "Twelve Apostles". */
function display(title, country) {
    let name = title;
    if (name.endsWith(')') && name.includes(' (')) name = name.slice(0, name.lastIndexOf(' ('));
    if (country && name.endsWith(`, ${country}`)) name = name.slice(0, -`, ${country}`.length);
    return name;
}

function km(a, b) {
    const rad = Math.PI / 180;
    const dLat = (b[0] - a[0]) * rad;
    const dLon = (b[1] - a[1]) * rad;
    const h = Math.sin(dLat / 2) ** 2
        + Math.cos(a[0] * rad) * Math.cos(b[0] * rad) * Math.sin(dLon / 2) ** 2;
    return 6371 * 2 * Math.asin(Math.sqrt(Math.min(1, h)));
}

// ── GeoNames ─────────────────────────────────────────────────────────────────

async function geonames(dir) {
    const countries = new Map();
    for (const line of (await readFile(path.join(dir, 'countryInfo.txt'), 'utf8')).split('\n')) {
        if (!line || line.startsWith('#')) continue;
        const f = line.split('\t');
        countries.set(f[0], COUNTRY_NAMES[f[0]] ?? f[4]);
    }

    const regions = new Map();
    for (const line of (await readFile(path.join(dir, 'admin1CodesASCII.txt'), 'utf8')).split('\n')) {
        const f = line.split('\t');
        if (f.length > 1) regions.set(f[0], f[1]);
    }

    const cities = [];
    const grid = new Map();
    for (const line of (await readFile(path.join(dir, 'cities1000.txt'), 'utf8')).split('\n')) {
        const f = line.split('\t');
        if (f.length < 15 || WRONG_IN_GEONAMES.has(f[0])) continue;
        const city = {
            id: f[0], name: f[1], lat: Number(f[4]), lon: Number(f[5]),
            code: f[7], iso: f[8], admin1: f[10], population: Number(f[14]) || 0,
        };
        cities.push(city);
        const key = `${Math.floor(city.lat)},${Math.floor(city.lon)}`;
        if (!grid.has(key)) grid.set(key, []);
        grid.get(key).push(city);
    }

    /** The nearest place in the given country, optionally above a population. */
    function nearest(lat, lon, iso, minimum = 0, reach = 3) {
        let best = null;
        for (let dy = -reach; dy <= reach; dy++) {
            for (let dx = -reach; dx <= reach; dx++) {
                for (const c of grid.get(`${Math.floor(lat) + dy},${Math.floor(lon) + dx}`) ?? []) {
                    if (c.iso !== iso || c.population < minimum) continue;
                    const d = km([lat, lon], [c.lat, c.lon]);
                    if (!best || d < best.km) best = { km: d, city: c };
                }
            }
        }
        return best;
    }

    /**
     * The city a place is said to be in, within 12 km and the same country.
     *
     * Neither the largest nor the nearest. Largest put the souq in Manama in
     * Al Muharraq, across the water, and the market in Valletta in a larger
     * town on the far side of Malta; nearest puts a market in an old city in
     * whichever ward's centre is a little closer. People divided by distance
     * weighs both: a capital a few hundred metres away beats a bigger town ten
     * kilometres off, and a city of millions beats the ward next door. A
     * capital counts double, because a capital is what a place is called by:
     * Valletta has fewer people than the town across its harbour, and its
     * market is still in Valletta. Sections of a city and abandoned places are
     * not cities.
     */
    const NOT_A_CITY = new Set(['PPLX', 'PPLH', 'PPLQ', 'PPLW']);
    function cityOf(lat, lon, iso) {
        let best = null;
        for (let dy = -1; dy <= 1; dy++) {
            for (let dx = -1; dx <= 1; dx++) {
                for (const c of grid.get(`${Math.floor(lat) + dy},${Math.floor(lon) + dx}`) ?? []) {
                    if (c.iso !== iso || NOT_A_CITY.has(c.code)) continue;
                    const d = km([lat, lon], [c.lat, c.lon]);
                    if (d > 12) continue;
                    const weight = (c.code === 'PPLC' ? 2 : 1) * c.population / (1 + d);
                    if (!best || weight > best.weight) best = { weight, name: c.name };
                }
            }
        }
        return best?.name ?? null;
    }

    return { countries, regions, cities, nearest, cityOf };
}

// ── featured places and sights ───────────────────────────────────────────────

const ITEM = /^Q\d+$/;

async function chosen(listPath, kind, geo) {
    const wanted = [];
    for (const raw of (await readFile(listPath, 'utf8')).split('\n')) {
        const line = raw.trim();
        if (!line || line.startsWith('#')) continue;
        const [iso, ...rest] = line.split(/\s+/);
        wanted.push({ iso, title: rest.join(' ') });
    }

    // Title to item, through Wikipedia's own normalisation and redirects. An
    // item is its own answer.
    const qidOf = new Map();
    const pageOf = new Map();
    const problems = [];
    const reported = [];
    for (const w of wanted) if (ITEM.test(w.title)) qidOf.set(w.title, w.title);
    const titles = wanted.map((w) => w.title).filter((t) => !ITEM.test(t));
    for (const batch of batches([...new Set(titles)])) {
        const data = await get('https://en.wikipedia.org/w/api.php?' + new URLSearchParams({
            action: 'query', format: 'json', redirects: '1', prop: 'pageprops',
            ppprop: 'wikibase_item|disambiguation', titles: batch.join('|'),
        }));
        const step = new Map(batch.map((t) => [t, t]));
        for (const n of data.query?.normalized ?? []) {
            for (const [k, v] of step) if (v === n.from) step.set(k, n.to);
        }
        for (const r of data.query?.redirects ?? []) {
            for (const [k, v] of step) if (v === r.from) step.set(k, r.to);
        }
        const pages = new Map(Object.values(data.query?.pages ?? {}).map((p) => [p.title, p]));
        for (const [asked, final] of step) {
            const page = pages.get(final);
            if (!page || 'missing' in page) continue;
            if ('disambiguation' in (page.pageprops ?? {})) {
                problems.push(`${asked}: is a disambiguation page`);
                continue;
            }
            if (page.pageprops?.wikibase_item) {
                qidOf.set(asked, page.pageprops.wikibase_item);
                pageOf.set(asked, final);
            }
        }
        await pause();
    }

    const entities = {};
    for (const batch of batches([...new Set(qidOf.values())])) {
        Object.assign(entities, (await wikidata({
            action: 'wbgetentities', ids: batch.join('|'),
            props: 'labels|descriptions|claims|sitelinks', languages: 'en',
        })).entities);
        await pause();
    }

    // What the types, countries and religions referred to are called.
    const refs = new Map();
    const referenced = new Set();
    for (const e of Object.values(entities)) {
        for (const p of ['P31', 'P17', 'P140']) for (const id of itemIds(e, p)) referenced.add(id);
    }
    for (const batch of batches([...referenced])) {
        const data = await wikidata({
            action: 'wbgetentities', ids: batch.join('|'), props: 'labels|claims', languages: 'en',
        });
        for (const [id, e] of Object.entries(data.entities)) {
            const iso = current(e.claims, 'P297')[0]?.mainsnak?.datavalue?.value ?? null;
            refs.set(id, { label: e.labels?.en?.value ?? '', iso });
        }
        await pause();
    }

    // The administrative chain, walked upwards one level at a time.
    const parents = new Map();
    const names = new Map();
    for (const [id, e] of Object.entries(entities)) parents.set(id, itemIds(e, 'P131'));
    let frontier = new Set([...parents.values()].flat());
    for (let depth = 0; depth < 8 && frontier.size; depth++) {
        const todo = [...frontier].filter((id) => !parents.has(id));
        for (const batch of batches(todo)) {
            const data = await wikidata({
                action: 'wbgetentities', ids: batch.join('|'), props: 'labels|claims', languages: 'en',
            });
            for (const [id, e] of Object.entries(data.entities)) {
                parents.set(id, itemIds(e, 'P131'));
                names.set(id, e.labels?.en?.value ?? null);
            }
            await pause();
        }
        frontier = new Set(todo.flatMap((id) => parents.get(id) ?? []));
    }

    const countryItem = new Map();
    for (const e of Object.values(entities)) {
        for (const id of itemIds(e, 'P17')) {
            const iso = refs.get(id)?.iso;
            if (iso) countryItem.set(iso, id);
        }
    }

    /** The top division a reader would name, found by walking to the country. */
    function regionOf(id, iso) {
        const target = countryItem.get(iso);
        if (!target) return null;
        let found = null;
        const walk = (node, chain, seen) => {
            if (found || seen.has(node) || seen.size > 10) return;
            const ups = parents.get(node) ?? [];
            if (ups.includes(target)) { found = chain; return; }
            for (const up of ups) walk(up, [...chain, up], new Set([...seen, node]));
        };
        walk(id, [], new Set());
        for (const step of [...(found ?? [])].reverse()) {
            const label = names.get(step);
            if (label && !unspoken(label)) return label;
        }
        return null;
    }

    const places = [];
    for (const { iso, title } of wanted) {
        const id = qidOf.get(title);
        if (!id) { problems.push(`${iso} ${title}: no Wikipedia article with a Wikidata item`); continue; }
        const e = entities[id];
        const where = current(e.claims, 'P625')
            .map((c) => c.mainsnak?.datavalue?.value)
            .find((v) => v && typeof v.latitude === 'number');
        if (!where) { problems.push(`${iso} ${title}: no coordinates`); continue; }

        const lat = Number(where.latitude.toFixed(6));
        const lon = Number(where.longitude.toFixed(6));
        const countriesSaid = itemIds(e, 'P17').map((c) => refs.get(c)?.iso);
        const types = itemIds(e, 'P31').map((t) => refs.get(t)?.label ?? '');
        const home = geo.nearest(lat, lon, iso);

        if (current(e.claims, 'P140').length) problems.push(`${iso} ${title}: carries a religion statement`);
        if (current(e.claims, 'P6375').length || current(e.claims, 'P669').length) {
            (kind === 'sight' ? problems : reported).push(`${iso} ${title}: has a street address`);
        }
        const ruled = types.filter((t) => RULED_OUT.some((w) => t.toLowerCase().includes(w)));
        if (ruled.length) problems.push(`${iso} ${title}: is a ${ruled.join(', ')}`);
        const zone = contested(lat, lon);
        if (zone) problems.push(`${iso} ${title}: is in ${zone}`);
        if (!countriesSaid.includes(iso) && (!home || home.km > 150)) {
            problems.push(`${iso} ${title}: Wikidata says ${countriesSaid.join('/') || 'nothing'}`);
        }

        const town = geo.nearest(lat, lon, iso, 5000);
        let region = regionOf(id, iso)
            ?? (home && home.km <= 150 ? geo.regions.get(`${iso}.${home.city.admin1}`) : null);
        if (unspoken(region) || region === geo.countries.get(iso)) region = null;

        const article = pageOf.get(title) ?? e.sitelinks?.enwiki?.title;
        const label = article ?? e.labels?.en?.value;
        if (!label) { problems.push(`${iso} ${title}: has no English name`); continue; }
        let name = display(label, geo.countries.get(iso));
        // Only a market is said to be in a city. A sight is out of town, and
        // the place within reach of a mountain with the most people is a
        // village that happens to be near its summit, not an address anyone
        // uses. A city-state's city is its country, and is said once.
        let city = kind === 'featured' ? geo.cityOf(lat, lon, iso) : null;
        if (city === geo.countries.get(iso) || city === name) city = null;
        // "Grand Bazaar, Tehran" is the Grand Bazaar, in Tehran: the city is
        // said once, where every other place says it.
        if (city && name.endsWith(`, ${city}`)) name = name.slice(0, -`, ${city}`.length);
        places.push({
            kind, iso, name, region,
            city,
            // A sight is out of town, so the town it is near is worth saying.
            // A market is in one, and the city already says which.
            near: kind === 'sight' && town && town.km <= 60 && town.city.name !== name
                ? town.city.name
                : null,
            lat, lon,
            description: e.descriptions?.en?.value ?? null,
            ...(article ? { wikipedia: article } : {}),
            wikidata: id,
        });
    }

    return { places, problems, reported };
}

// ── cities ───────────────────────────────────────────────────────────────────

function cities(geo) {
    const excluded = [];
    const chosen = [];
    for (const c of geo.cities) {
        const wanted = c.code === 'PPLC'
            || c.population >= 500_000
            || (c.code === 'PPLA' && c.population >= 250_000);
        if (!wanted || !geo.countries.has(c.iso)) continue;
        const zone = contested(c.lat, c.lon);
        if (zone) {
            excluded.push(`${c.name} (${c.iso}, ${c.population.toLocaleString('en')}): ${zone}`);
            continue;
        }
        let region = geo.regions.get(`${c.iso}.${c.admin1}`) ?? null;
        if (unspoken(region) || region === c.name || region === geo.countries.get(c.iso)) region = null;
        chosen.push({
            kind: 'city', iso: c.iso, name: c.name, region, city: null, near: null,
            lat: Number(c.lat.toFixed(6)), lon: Number(c.lon.toFixed(6)),
            population: c.population, capital: c.code === 'PPLC',
        });
    }
    return { chosen, excluded };
}

// ── addresses, unique within a country ───────────────────────────────────────

function address(places, countries) {
    const byKey = new Map();
    for (const p of places) {
        p.country = countries.get(p.iso);
        const key = `${slugify(p.country)}/${slugify(p.name)}`;
        if (!byKey.has(key)) byKey.set(key, []);
        byKey.get(key).push(p);
    }
    for (const [key, group] of byKey) {
        if (group.length === 1) { group[0].slug = key; continue; }
        // Two Suzhous: the region tells them apart, and failing that the kind.
        // Two Grand Bazaars: the city does, before the region.
        for (const p of group) p.slug = `${key}-${slugify(p.city ?? p.region ?? p.kind)}`;
        const seen = new Map();
        for (const p of group) {
            const n = (seen.get(p.slug) ?? 0) + 1;
            seen.set(p.slug, n);
            if (n > 1) p.slug = `${p.slug}-${n}`;
        }
    }
}

// ── timezones, for when the country lookup is blocked ───────────────────────

/**
 * Which country a browser's timezone implies.
 *
 * A content blocker commonly blocks country lookup services, and a reader who
 * runs one should still be shown their own country rather than a default. The
 * timezone is on the device already. Canonical zones come from zone.tab, and
 * the old names browsers still report (Asia/Calcutta, Europe/Kiev) from backward.
 */
async function zones() {
    const base = 'https://raw.githubusercontent.com/eggert/tz/main/';
    const map = {};
    for (const line of (await getText(base + 'zone.tab')).split('\n')) {
        if (!line || line.startsWith('#')) continue;
        const [iso, , zone] = line.split('\t');
        map[zone] = iso;
    }
    for (const line of (await getText(base + 'backward')).split('\n')) {
        const f = line.trim().split(/\s+/);
        if (f[0] === 'Link' && map[f[1]] && !map[f[2]]) map[f[2]] = map[f[1]];
    }
    return Object.fromEntries(Object.entries(map).sort());
}

// ── main ─────────────────────────────────────────────────────────────────────

async function main() {
    const argument = (name, fallback) => {
        const at = process.argv.indexOf(`--${name}`);
        return at === -1 ? fallback : process.argv[at + 1];
    };
    const dir = argument('geonames', null);
    if (!dir) {
        console.error('usage: node scripts/build-places.mjs --geonames <dir> [--out <file>]');
        process.exit(2);
    }
    const out = argument('out', path.join(web, 'src', 'data', 'places.json'));

    const geo = await geonames(dir);
    const picked = await chosen(path.join(here, 'featured-places.txt'), 'featured', geo);
    const sights = await chosen(path.join(here, 'sights.txt'), 'sight', geo);
    const problems = [...picked.problems, ...sights.problems];
    if (problems.length) {
        console.error('Not written. These need a decision in featured-places.txt or sights.txt:\n');
        for (const p of problems) console.error('  ' + p);
        process.exit(1);
    }

    // A place in both lists is a mistake in one of them.
    const listed = new Set(picked.places.map((p) => p.wikidata));
    const twice = sights.places.filter((p) => listed.has(p.wikidata));
    if (twice.length) {
        console.error('Not written. Listed as both featured and a sight:\n');
        for (const p of twice) console.error(`  ${p.iso} ${p.name}`);
        process.exit(1);
    }

    const town = cities(geo);
    const places = [...picked.places, ...sights.places, ...town.chosen];
    // The article title too. Wikipedia keeps a hyphenated redirect for a
    // title with a dash in it, so the plain spelling still reaches the article.
    for (const p of places) {
        for (const field of ['name', 'region', 'city', 'near', 'description', 'wikipedia']) {
            p[field] = plain(p[field]);
        }
    }
    for (const [iso, name] of geo.countries) geo.countries.set(iso, plain(name));
    address(places, geo.countries);

    const slugs = new Set();
    for (const p of places) {
        if (slugs.has(p.slug)) throw new Error(`two places at ${p.slug}`);
        slugs.add(p.slug);
    }

    const countries = {};
    for (const p of places) {
        countries[p.iso] ??= { name: geo.countries.get(p.iso), slug: slugify(geo.countries.get(p.iso)) };
    }

    const document = {
        about: [
            'Generated by web/scripts/build-places.mjs. Do not edit by hand: change',
            'featured-places.txt, sights.txt or the rules in that script, and run it again.',
            'Places from Wikidata (CC0) and GeoNames (CC BY 4.0).',
        ],
        countries: Object.fromEntries(Object.entries(countries).sort()),
        places,
        zones: await zones(),
    };

    await mkdir(path.dirname(out), { recursive: true });
    await writeFile(out, JSON.stringify(document, null, 1) + '\n', 'utf8');

    const featuredCountries = new Set(picked.places.map((p) => p.iso));
    console.log(`wrote ${path.relative(web, out)}`);
    console.log(`  ${picked.places.length} featured places in ${featuredCountries.size} countries`);
    console.log(`  ${sights.places.length} sights in ${new Set(sights.places.map((p) => p.iso)).size} countries`);
    console.log(`  ${town.chosen.length} cities in ${new Set(town.chosen.map((c) => c.iso)).size} countries`);
    console.log(`  ${Object.keys(document.zones).length} timezones`);
    if (picked.reported.length) {
        console.log(`\n  ${picked.reported.length} featured places with an address of their own, for review:`);
        for (const line of picked.reported) console.log('    ' + line);
    }
    if (town.excluded.length) {
        console.log(`\n  ${town.excluded.length} cities left out as contested, for review:`);
        for (const line of town.excluded) console.log('    ' + line);
    }
}

await main();
