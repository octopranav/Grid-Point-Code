// Where the format comes apart, and by how much.
//
// Level-1 boundaries are the seams. Two points on opposite sides of one are in
// different level-1 cells, so they share no characters at all however close
// they are. The minimum crossing is a single cell, 2.56 m north to south.
//
// This is not a defect a better construction would remove. Every grid of fixed
// cells has boundaries and every code built on one has seams. What a format can
// do is put them on round, documentable lines, say where they are, and make the
// neighbour operations cross them correctly. Section 16.

import { GPC } from '@pranavpatel.ca/algo-gridpointcode';

import { apart } from './typos';

export interface Seam {
    id: string;
    /** What to call it. */
    name: string;
    /** Which way the line runs, which is also which way you step across it. */
    axis: 'meridian' | 'parallel';
    /** A point just on one side, chosen somewhere a person could stand. */
    latitude: number;
    longitude: number;
    /** Where this is, for a reader who wants to know why that spot. */
    note: string;
}

/**
 * The seams, at places with names rather than at arbitrary coordinates.
 *
 * A reader shown `0.00002, 109.33330` learns nothing; shown that it is a street
 * in Pontianak, where the equator runs through a city of half a million people,
 * they learn that seams are not a theoretical concern in an empty ocean.
 */
export const SEAMS: Seam[] = [
    {
        id: 'greenwich',
        name: 'The prime meridian',
        axis: 'meridian',
        latitude: 51.4778,
        longitude: 0,
        note: 'At the Royal Observatory, where the line is drawn on the ground and people stand either side of it for photographs.',
    },
    {
        id: 'equator',
        name: 'The equator',
        axis: 'parallel',
        latitude: 0,
        longitude: 109.3333,
        note: 'At Pontianak, a city of half a million that the equator runs straight through.',
    },
    {
        id: 'sixty-east',
        name: 'The 60° E meridian',
        axis: 'meridian',
        latitude: 41.3,
        longitude: 60,
        note: 'Through Uzbekistan. Every sixtieth meridian is a seam, not just the one at Greenwich.',
    },
    {
        id: 'forty-five-north',
        name: 'The 45° N parallel',
        axis: 'parallel',
        latitude: 45,
        longitude: 11.8,
        note: 'Through northern Italy. Halfway from the equator to the pole, and the top of the first row of cells.',
    },
];

export interface Crossing {
    west: { latitude: number; longitude: number; code: string };
    east: { latitude: number; longitude: number; code: string };
    /** Great-circle metres between the two. */
    metres: number;
    /** How many leading characters the two codes have in common. */
    shared: number;
}

/** How many leading characters two codes agree on. */
export function sharedPrefix(one: string, two: string): number {
    const a = GPC.normalise(one)[0];
    const b = GPC.normalise(two)[0];
    let count = 0;
    while (count < a.length && a[count] === b[count]) count += 1;
    return count;
}

/**
 * Two hundredths of a thousandth of a degree either side.
 *
 * The same step section 16 uses for its worked example, so the figures this
 * page shows are the figures the specification prints, 2.8 m at Greenwich,
 * and a reader can hold the two next to each other.
 */
const STEP = 0.00002;

const step = (seam: Seam, side: 1 | -1) =>
    seam.axis === 'meridian'
        ? { latitude: seam.latitude, longitude: seam.longitude + side * STEP }
        : { latitude: seam.latitude + side * STEP, longitude: seam.longitude };

/** The two points either side of a seam, and what they cost each other. */
export function crossing(seam: Seam): Crossing {
    const near = step(seam, -1);
    const far = step(seam, 1);
    const west = { ...near, code: GPC.encode(near.latitude, near.longitude) };
    const east = { ...far, code: GPC.encode(far.latitude, far.longitude) };

    return {
        west,
        east,
        metres: apart(near.latitude, near.longitude, far.latitude, far.longitude),
        shared: sharedPrefix(west.code, east.code),
    };
}

/**
 * The same two steps, taken somewhere ordinary.
 *
 * Without this the page is a list of alarming numbers with nothing to weigh
 * them against. Two doors sharing nothing means very little until you have seen
 * two doors the same distance apart sharing nine characters, which is what
 * happens everywhere that is not a seam.
 */
export function ordinary(seam: Seam): Crossing {
    // A quarter of a degree away: far enough to be clear of the boundary,
    // close enough to be the same neighbourhood and the same kind of place.
    const shifted: Seam = {
        ...seam,
        latitude: seam.axis === 'parallel' ? seam.latitude + 0.25 : seam.latitude,
        longitude: seam.axis === 'meridian' ? seam.longitude + 0.25 : seam.longitude,
    };
    return crossing(shifted);
}

/**
 * Whether the neighbour operation crosses this seam.
 *
 * The claim section 16 makes for the format is not that seams are absent, but
 * that the operations cross them correctly. That is checkable, so the page
 * checks it in front of the reader rather than repeating it.
 */
export function neighboursCross(seam: Seam, level = 5): {
    from: string;
    to: string;
    around: string[];
    crosses: boolean;
} {
    const { west, east } = crossing(seam);
    const from = GPC.cell(west.code, level);
    const to = GPC.cell(east.code, level);
    const around = GPC.neighbours(from);
    return { from, to, around, crosses: around.includes(to) };
}
