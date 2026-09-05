// What a typo does to a code, and what can be done about it afterwards.
//
// Section 15 of the specification measures this over 191,910 substitutions and
// says the uncomfortable part plainly: one typo in 240 is caught, 29.1 per cent
// land somewhere plausible and silent, and an implementation MUST NOT be
// documented as detecting typos.
//
// The figures below are not those. They are the same arithmetic applied to
// whichever code a reader is holding, because a median over the sphere is a
// fact about the format and a median over *your* code is a fact about you.

import { GPC } from '@pranavpatel.ca/algo-gridpointcode';

import { ALPHABET, insteadOf } from './alphabet';

const EARTH = 6371008.8;
const rad = (degrees: number) => (degrees * Math.PI) / 180;

/** Great-circle metres between two points. */
export function apart(
    latitude: number,
    longitude: number,
    otherLatitude: number,
    otherLongitude: number,
): number {
    const dLat = rad(otherLatitude - latitude);
    const dLng = rad(otherLongitude - longitude);
    const a =
        Math.sin(dLat / 2) ** 2 +
        Math.cos(rad(latitude)) * Math.cos(rad(otherLatitude)) * Math.sin(dLng / 2) ** 2;
    return 2 * EARTH * Math.asin(Math.min(1, Math.sqrt(a)));
}

/** Where a code lands, or the reason it does not. */
export function landing(code: string): { latitude: number; longitude: number } | null {
    try {
        const [latitude, longitude] = GPC.decode(code);
        return { latitude, longitude };
    } catch {
        return null;
    }
}

export interface Damage {
    /** 1 to 10. */
    position: number;
    /** The symbol currently there. */
    symbol: string;
    /** Metres from the true point, for each of the 24 wrong symbols that decode. */
    displacements: number[];
    /** The middle one, which is the honest single number for this position. */
    median: number;
    /** How many of the 24 are refused before decoding. Almost always zero. */
    caught: number;
}

/**
 * What a single wrong character at each position would cost.
 *
 * All 240 substitutions, computed for one code, in about three milliseconds.
 * The shape it produces is the specification's table in miniature: the first
 * few positions throw the point across the world where any map would catch it,
 * the last few move it by metres, and positions four to six land it tens of
 * kilometres away, near enough to look like a real answer.
 */
export function damage(code: string): Damage[] {
    const bare = GPC.normalise(code)[0];
    const truth = landing(bare);
    if (truth === null) return [];

    return [...bare].map((symbol, index) => {
        const displacements: number[] = [];
        let caught = 0;

        for (const other of insteadOf(symbol)) {
            const broken = bare.slice(0, index) + other + bare.slice(index + 1);
            const where = landing(broken);
            if (where === null) {
                // Refused before decoding, which for a substitution means an
                // `X` reached position 1. It is the only structural catch there
                // is, and it is one in 240 rather than error detection.
                caught += 1;
                continue;
            }
            displacements.push(
                apart(truth.latitude, truth.longitude, where.latitude, where.longitude),
            );
        }

        const sorted = [...displacements].sort((a, b) => a - b);
        return {
            position: index + 1,
            symbol,
            displacements,
            median: sorted.length === 0 ? 0 : sorted[Math.floor(sorted.length / 2)],
            caught,
        };
    });
}

/** The code with one position replaced. */
export function replace(code: string, position: number, symbol: string): string {
    const bare = GPC.normalise(code)[0];
    return bare.slice(0, position - 1) + symbol + bare.slice(position);
}

/**
 * A wrong symbol for this position that still decodes, chosen for the demonstration.
 *
 * The one nearest the position's own median, so pressing a character gives the
 * typical outcome rather than the luckiest or the most alarming. A lab that
 * quietly picks the worst case is making the same mistake as one that picks the
 * best.
 */
export function typicalMistake(code: string, position: number): string | null {
    const bare = GPC.normalise(code)[0];
    const truth = landing(bare);
    if (truth === null) return null;

    const here = damage(bare)[position - 1];
    let best: { symbol: string; gap: number } | null = null;

    for (const other of insteadOf(bare[position - 1])) {
        const where = landing(replace(bare, position, other));
        if (where === null) continue;
        const moved = apart(truth.latitude, truth.longitude, where.latitude, where.longitude);
        const gap = Math.abs(moved - here.median);
        if (best === null || gap < best.gap) best = { symbol: other, gap };
    }

    return best?.symbol ?? null;
}

export { ALPHABET };
