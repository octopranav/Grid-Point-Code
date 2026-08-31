// Fetching the landmarks a short form can be anchored to.
//
// The shards are built by scripts/build-landmarks.mjs and keyed by level-3
// cell -- the first three characters of a landmark's own code. Which shards a
// lookup needs is decided by the recovery box of section 12.3, never by a
// radius: see reference.ts for why that distinction is not cosmetic.

import { GPC } from '@pranavpatel.ca/algo-gridpointcode';
import { RECOVERY, references, type Candidate, type Landmark } from './reference';

/** The order is the file format's, written as an index by the build script. */
const KINDS = ['structure', 'natural', 'place'] as const;
export type Kind = (typeof KINDS)[number];

export interface Choice extends Candidate {
    kind: Kind;
    region: string;
    /**
     * The landmark shares the point's level-5 cell.
     *
     * That is worth marking because it is a stronger promise than the box. A
     * listener who has this data can take the landmark's cell and use its
     * centre, and recovery is then exact by construction rather than dependent
     * on anyone's coordinates agreeing -- which is the weakness section 12.3 is
     * candid about and the one no arithmetic here can repair.
     */
    exact: boolean;
}

interface Shard {
    regions: string[];
    landmarks: [string, number, number, number, number][];
}

// A shard once fetched is kept: the data does not change between deployments,
// and a reader nudging a point around asks for the same three characters over
// and over. A miss is remembered too -- most of the planet is ocean, and a
// shard that does not exist should be asked for once, not on every keystroke.
const held = new Map<string, Promise<Shard | null>>();

const base = () => import.meta.env.BASE_URL.replace(/\/$/, '');
const url = (shard: string) => `${base()}/landmarks/${shard}.json`;

function fetchShard(shard: string): Promise<Shard | null> {
    const already = held.get(shard);
    if (already) return already;

    const pending = fetch(url(shard))
        .then((response) => (response.ok ? (response.json() as Promise<Shard>) : null))
        .catch(() => null);          // offline, or no shard there: the same answer

    held.set(shard, pending);
    return pending;
}

interface Manifest {
    level: number;
    shards: number;
    landmarks: number;
    /** When the archive was built. What tells a held copy from a current one. */
    built: string;
}

// Which level the shards were cut at is a property of the data, not of this
// file. Assuming it means that rebuilding at a different level turns every
// lookup into a miss, and a miss reads as `no landmarks near here` rather than
// as the broken deployment it is.
let described: Promise<Manifest | null> | null = null;

function manifest(): Promise<Manifest | null> {
    described ??= fetch(`${base()}/landmarks/manifest.json`)
        .then((response) => (response.ok ? (response.json() as Promise<Manifest>) : null))
        .catch(() => null);
    return described;
}

/**
 * The shards the recovery box reaches into.
 *
 * Taken from the corners rather than the centre, because a box that straddles
 * a boundary is exactly the case a centre lookup would miss -- and missing one
 * does not raise anything, it just quietly shortens the list.
 */
export function shardsFor(latitude: number, longitude: number, level: number): string[] {
    const shards = new Set<string>();
    for (const dy of [-1, 1]) {
        for (const dx of [-1, 1]) {
            const corner = latitude + dy * RECOVERY.latitude;
            const wrapped = ((longitude + dx * RECOVERY.longitude + 540) % 360) - 180;
            try {
                shards.add(GPC.cell(GPC.encode(Math.max(-90, Math.min(90, corner)), wrapped), level));
            } catch {
                // A corner past the pole or inside the reserved range has no
                // shard. The other corners still do, and the box is clipped to
                // what the world actually holds.
            }
        }
    }
    return [...shards];
}

/**
 * Every landmark this point's short form may be given as its reference,
 * nearest first.
 *
 * An empty result is an answer rather than a failure, and the caller is
 * expected to say so: in open country there is no landmark near enough, and
 * the honest response is the full ten characters, which is the form of record
 * in any case.
 */
export async function nearby(latitude: number, longitude: number): Promise<Choice[]> {
    const described = await manifest();

    // Not an empty list. An empty list means open country, and saying that
    // when the archive simply is not deployed would be a lie the reader
    // cannot see through -- they would believe there is nowhere near them.
    if (!described) throw new Error('no landmark data is deployed');

    const shards = await Promise.all(
        shardsFor(latitude, longitude, described.level).map(fetchShard),
    );

    let here: string | null = null;
    try {
        here = GPC.cell(GPC.encode(latitude, longitude), 5);
    } catch {
        return [];                   // a point with no code has no short form
    }

    const found: (Landmark & { kind: Kind; region: string })[] = [];
    for (const shard of shards) {
        if (!shard) continue;
        for (const [name, lat, lng, region, kind] of shard.landmarks) {
            found.push({
                name,
                latitude: lat,
                longitude: lng,
                region: shard.regions[region] ?? '',
                kind: KINDS[kind] ?? 'place',
            });
        }
    }

    return references(latitude, longitude, found).map((candidate) => {
        const source = candidate as Candidate & { kind: Kind; region: string };
        let exact = false;
        try {
            exact = GPC.cell(GPC.encode(candidate.latitude, candidate.longitude), 5) === here;
        } catch {
            exact = false;
        }
        return { ...source, exact };
    });
}

/** The reference line a reader can copy, said the way section 12.1 writes it. */
export function anchored(short: string, choice: Choice): string {
    const dashed = short.startsWith('-') ? short : `-${short}`;
    return `${dashed} near ${choice.name}, ${choice.region}`;
}

// ── keeping an area to hand ────────────────────────────────────────────────

/**
 * The alphabet of section 4, written out rather than read from the library,
 * which keeps it private. It cannot change without changing the format, so
 * this is a constant in the same sense the grid is.
 */
const ALPHABET = '0123456789CDFGHJKLMNPRTWX';

/**
 * Two stores, because two different things are being remembered.
 *
 * This one holds what a reader deliberately asked for, and is the only one this
 * file writes or counts. The worker keeps a second store of whatever it served
 * along the way -- a free convenience, and nobody's decision -- which is never
 * reported, because telling someone an area is held when they merely glanced at
 * it is the same sentence as telling them it is ready for a journey, and only
 * one of those is true.
 *
 * Named for the build, so a new archive does not inherit an old one's copies.
 */
const keptName = (described: Manifest) => `gpc-kept-${described.built}`;

/** Whether the browser will let anything be kept at all. */
export const canKeep = () => typeof caches !== 'undefined';

export interface Area {
    /** The cell being kept: one level up from a shard. */
    cell: string;
    /** How many of its shards hold anything. Most of the planet is ocean. */
    shards: number;
    bytes: number;
}

/**
 * Fetch and keep every shard of the area around a point.
 *
 * The area is the cell one level above a shard -- about 200 by 267 km at the
 * level the archive is cut for. That is a deliberate size: large enough to be
 * worth asking for before a journey, small enough that the answer is a few
 * hundred kilobytes rather than the eighty-odd megabytes the whole world
 * weighs. Shards already held are not fetched again.
 *
 * Written into the cache the worker reads from, under the name the current
 * build stamped, so keeping something and being served it offline are the
 * same act rather than two that have to agree.
 */
export async function keepArea(latitude: number, longitude: number): Promise<Area> {
    const described = await manifest();
    if (!described) throw new Error('no landmark data is deployed');
    if (!canKeep()) throw new Error('this browser will not keep anything');

    const area = GPC.cell(GPC.encode(latitude, longitude), described.level - 1);
    const cache = await caches.open(keptName(described));
    const wanted = [...ALPHABET].map((symbol) => url(area + symbol));

    // Fetching happens together, because it is all network waiting and there
    // are at most twenty-five of them.
    await Promise.all(
        wanted.map(async (address) => {
            if (await cache.match(address)) return;

            let response: Response;
            try {
                response = await fetch(address);
            } catch {
                return;                  // no network for this one; the rest may work
            }
            // A 404 is ocean, which is an answer rather than a failure.
            if (!response.ok) return;

            await cache.put(address, response);
        }),
    );

    // Measuring happens one at a time, and that is not fussiness. Reading
    // twenty-five cached bodies at once returned 1,177 bytes where reading the
    // same twenty-five in turn returned 83,506 -- the stored data was whole
    // either way, but concurrent reads of it were not. A wrong size is a small
    // lie told confidently, which is worse than the millisecond this costs.
    let shards = 0;
    let bytes = 0;
    for (const address of wanted) {
        const held = await cache.match(address);
        if (!held) continue;
        shards += 1;
        bytes += (await held.arrayBuffer()).byteLength;
    }

    return { cell: area, shards, bytes };
}

/**
 * How much of the area around one point is already held.
 *
 * Separate from `kept` below, which counts everything anywhere. The difference
 * matters to a reader: being told that twenty-five shards are held is no use
 * when standing somewhere none of them cover, and is worse than no message at
 * all, because it reads as reassurance.
 */
export async function heldArea(
    latitude: number,
    longitude: number,
): Promise<{ cell: string; shards: number } | null> {
    if (!canKeep()) return null;

    const described = await manifest();
    if (!described) return null;

    const area = GPC.cell(GPC.encode(latitude, longitude), described.level - 1);
    const name = keptName(described);
    if (!(await caches.keys()).includes(name)) return { cell: area, shards: 0 };

    const cache = await caches.open(name);
    let shards = 0;
    for (const symbol of ALPHABET) {
        if (await cache.match(url(area + symbol))) shards += 1;
    }
    return { cell: area, shards };
}

/** How much is being kept, across every area asked for so far. */
export async function kept(): Promise<{ shards: number } | null> {
    if (!canKeep()) return null;
    const described = await manifest();
    if (!described) return null;

    const names = await caches.keys();
    const name = keptName(described);
    if (!names.includes(name)) return { shards: 0 };

    const cache = await caches.open(name);
    return { shards: (await cache.keys()).length };
}

/**
 * Give it all back: what was kept and what was merely passed through, from
 * every build rather than only the current one.
 *
 * Both, because a reader pressing this wants the space back and does not care
 * which of our two stores happens to hold it. The runtime store fills again on
 * its own as they look around, which is invisible and costs nothing -- and,
 * because only the kept store is ever counted, it will not be mistaken for
 * something they asked for.
 */
export async function forget(): Promise<void> {
    if (!canKeep()) return;
    for (const name of await caches.keys()) {
        if (name.startsWith('gpc-kept-') || name.startsWith('gpc-landmarks-')) {
            await caches.delete(name);
        }
    }
}
