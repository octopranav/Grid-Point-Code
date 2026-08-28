// The ten cells a point falls in, one per level.
//
// This is the arithmetic the whole site is about, and it is deliberately short.
// A level-k cell spans 5^(10-k) units of the underlying grid on both axes, so
// finding the one that holds a point is a floor division -- no lookup, no
// search, and no network. The unit sizes come from the library rather than
// being written out again here, which is what stops this file and the format
// drifting apart.
//
// Runs unchanged at build time and in the browser.

import { GPC } from '@pranavpatel.ca/algo-gridpointcode';

const LEVELS = 10;

// cellDimensions(10) is one grid unit: the size of the smallest cell, in
// degrees north-south and east-west.
const [LATITUDE_UNIT, LONGITUDE_UNIT] = GPC.cellDimensions(LEVELS);

/** What a reader recognises a level by, coarsest first. */
export const LEVEL_NAMES = [
    'Block of the world',
    'Country',
    'Region',
    'Metropolitan area',
    'District',
    'Suburb',
    'A few streets',
    'Building',
    'Entrance',
    'Doorway',
] as const;

export interface LevelCell {
    /** 1 for the coarsest cell, 10 for the doorway. */
    level: number;
    /** The single character this level contributes to the code. */
    symbol: string;
    /** The code up to and including this level. */
    prefix: string;
    name: string;
    south: number;
    north: number;
    west: number;
    east: number;
    /** The cell's size at the equator, already worded for a reader. */
    size: string;

    // Where this cell sits inside its parent. Level one divides the world four
    // ways by six, absorbing Earth's two-to-one aspect ratio in a single step;
    // every level below it divides its parent five by five.
    //
    // `row` counts from the south, because the underlying grid does. Anything
    // drawing these has to flip them, since screens count from the top.
    rows: number;
    columns: number;
    row: number;
    column: number;
}

export interface Resolution {
    /** The full ten-character code, formatted. */
    code: string;
    /** The centre of the level-10 cell -- what decoding a code gives back. */
    centre: [latitude: number, longitude: number];
    levels: LevelCell[];
}

/** 5,001 km reads better than 5000.512 km, and metres below a kilometre. */
function metric(metres: number): string {
    return metres >= 1000
        ? `${(metres / 1000).toLocaleString('en', { maximumSignificantDigits: 4 })} km`
        : `${metres.toLocaleString('en', { maximumSignificantDigits: 3 })} m`;
}

/**
 * Every cell containing the point, coarsest first.
 *
 * Throws for a coordinate outside the world, which is the library's own
 * behaviour rather than something invented here.
 */
export function resolve(latitude: number, longitude: number): Resolution {
    const code = GPC.encode(latitude, longitude);
    const symbols = code.replace(/[^0-9A-Z]/g, '');
    const [row, column] = GPC.decodeToGrid(code);

    const levels: LevelCell[] = [];

    for (let level = 1; level <= LEVELS; level += 1) {
        const span = 5 ** (LEVELS - level);
        const firstRow = Math.floor(row / span) * span;
        const firstColumn = Math.floor(column / span) * span;
        const [, , northSouth, eastWest] = GPC.cellDimensions(level);

        // The world is 4 by 6 level-one cells; everything below divides 5 by 5.
        const rows = level === 1 ? 4 : 5;
        const columns = level === 1 ? 6 : 5;

        levels.push({
            rows,
            columns,
            row: Math.floor(row / span) % rows,
            column: Math.floor(column / span) % columns,
            level,
            symbol: symbols[level - 1],
            prefix: symbols.slice(0, level),
            name: LEVEL_NAMES[level - 1],
            // Both edges are computed from their own grid index rather than one
            // from the other, so the north edge of a cell is bit-identical to
            // the south edge of the cell above it and no seam can open up.
            south: firstRow * LATITUDE_UNIT - 90,
            north: (firstRow + span) * LATITUDE_UNIT - 90,
            west: firstColumn * LONGITUDE_UNIT - 180,
            east: (firstColumn + span) * LONGITUDE_UNIT - 180,
            size: `${metric(northSouth)} × ${metric(eastWest)}`,
        });
    }

    return { code, centre: GPC.decode(code), levels };
}

/**
 * The sample points from the specification, verified against it at build time.
 *
 * They are here because they are already the worked examples a reader will meet
 * in the specification and the four package READMEs, so the site showing the
 * same places keeps one set of numbers in the reader's head instead of two.
 */
export const SAMPLES = [
    { name: 'Toronto', latitude: 43.65, longitude: -79.38, code: '#G3RJM-98NM9' },
    { name: 'CN Tower', latitude: 43.6426, longitude: -79.3871, code: '#G3RJM-0M6DX' },
    { name: 'Ahmedabad', latitude: 23.0225, longitude: 72.5714, code: '#KDC8X-JM49X' },
    { name: 'Sydney Opera House', latitude: -33.8568, longitude: 151.2153, code: '#6LK4X-NRP0R' },
    { name: 'Machu Picchu', latitude: -13.1631, longitude: -72.545, code: '#C8HKC-13C80' },
    { name: 'Reykjavík', latitude: 64.1466, longitude: -21.9426, code: '#RDX9R-TN19T' },
] as const;

/**
 * Fails the build if a sample stops encoding to the code the specification
 * prints for it. The site and section 9.4 of SPEC.md then cannot disagree
 * without somebody being told.
 */
export function checkSamples(): void {
    for (const { name, latitude, longitude, code } of SAMPLES) {
        const encoded = GPC.encode(latitude, longitude);
        if (encoded !== code) {
            throw new Error(
                `${name} encodes to ${encoded}, but the specification prints ${code}.`,
            );
        }
    }
}
