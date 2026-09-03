// How a code is built, worked out rather than described.
//
// Every figure on the Learn page comes from here, and everything here comes
// from `gridToCode` and `codeToGrid` in the library. Nothing re-implements the
// encoder: a page explaining the construction that had its own copy of the
// construction would be able to explain it wrongly and stay green.
//
// The work is integer arithmetic on the grid, so there is no floating point in
// any of it and the diagrams are exact rather than nearly right.
//
// Runs at build time. The page ships as HTML.

import { GPC } from '@pranavpatel.ca/algo-gridpointcode';

/** A level-k cell spans this many grid units on each axis. */
export const span = (level: number) => 5 ** (10 - level);

/** The world, in level-1 blocks. Four by six, and the six is the wide axis. */
export const BLOCK_ROWS = 4;
export const BLOCK_COLUMNS = 6;

export interface Block {
    /** From the south, because the grid counts that way. Screens do not. */
    row: number;
    /** From the antimeridian, eastward. */
    column: number;
    /** The character this block contributes at position one. */
    symbol: string;
    /** Where it falls in the serpentine, 0 to 23. */
    order: number;
    south: number;
    north: number;
    west: number;
    east: number;
}

/**
 * The twenty-four blocks, each asked for its own symbol.
 *
 * The serpentine is not written out here. Each block is handed to the library
 * as a grid position and reports the character it comes back with, so the
 * diagram is the format's own answer and not a second opinion about it.
 */
export function blocks(): Block[] {
    const found: Block[] = [];
    const unit = span(1);

    for (let row = 0; row < BLOCK_ROWS; row += 1) {
        for (let column = 0; column < BLOCK_COLUMNS; column += 1) {
            const symbol = GPC.gridToCode(row * unit, column * unit)[0];
            found.push({
                row,
                column,
                symbol,
                order: [...ALPHABET].indexOf(symbol),
                // A block is exactly 45 degrees by 60. Arriving at the same
                // number through the grid and a unit size gives 2.8e-14 for
                // what should be the equator, and the page prints these.
                south: row * 45 - 90,
                north: (row + 1) * 45 - 90,
                west: column * 60 - 180,
                east: (column + 1) * 60 - 180,
            });
        }
    }

    return found;
}

const ALPHABET = '0123456789CDFGHJKLMNPRTWX';

/**
 * The twenty-five children of one cell, as they are lettered.
 *
 * `[row from the south][column from the west]`. Which character lands where
 * depends on the parity of everything above it, which is the whole point: the
 * same position in two different parents is a different character, and that is
 * what keeps the curve joined up where it crosses from one parent to the next.
 */
export function children(row: number, column: number, level: number): string[][] {
    const step = span(level);
    const quilt: string[][] = [];

    for (let down = 0; down < 5; down += 1) {
        const line: string[] = [];
        for (let across = 0; across < 5; across += 1) {
            const code = GPC.gridToCode(row + down * step, column + across * step);
            line.push(code[level - 1]);
        }
        quilt.push(line);
    }

    return quilt;
}

/** A block of the world named by where it is, for a caption. */
export interface Parent {
    /** Grid row of the block's south-west corner. */
    row: number;
    /** Grid column of the block's south-west corner. */
    column: number;
    symbol: string;
    /** `4 rows up, 2 across` is no use to a reader; degrees are. */
    where: string;
}

export function parent(blockRow: number, blockColumn: number): Parent {
    const unit = span(1);
    const row = blockRow * unit;
    const column = blockColumn * unit;

    // From the block indices rather than from the grid through a unit size:
    // the same number arrived at that way comes out as 2.8e-14 degrees west.
    const south = blockRow * 45 - 90;
    const west = blockColumn * 60 - 180;
    const degrees = (value: number, positive: string, negative: string) =>
        `${Math.abs(value)}°${value === 0 ? '' : value > 0 ? positive : negative}`;

    return {
        row,
        column,
        symbol: GPC.gridToCode(row, column)[0],
        where: `${degrees(south, 'N', 'S')} to ${degrees(south + 45, 'N', 'S')}, `
            + `${degrees(west, 'E', 'W')} to ${degrees(west + 60, 'E', 'W')}`,
    };
}

export interface Step {
    /** Cell position inside the block: 0 to 24 from the south. */
    up: number;
    /** 0 to 24 from the west. */
    across: number;
    /** The two characters this cell adds, at levels 2 and 3. */
    symbols: string;
}

/**
 * The traversal of one block, two levels down: 625 cells in code order.
 *
 * Sorted as strings, because that is the claim being drawn. Whether the result
 * is a curve rather than a scatter is then not assumed -- `continuous` counts
 * the steps that are not to an edge-adjacent cell, and the page fails to build
 * if that is anything but zero.
 */
export function traversal(blockRow: number, blockColumn: number): Step[] {
    const unit = span(1);
    const step = span(3);
    const origin = { row: blockRow * unit, column: blockColumn * unit };

    const cells: Step[] = [];
    for (let up = 0; up < 25; up += 1) {
        for (let across = 0; across < 25; across += 1) {
            const code = GPC.gridToCode(origin.row + up * step, origin.column + across * step);
            cells.push({ up, across, symbols: code.slice(1, 3) });
        }
    }

    cells.sort((one, other) => (one.symbols < other.symbols ? -1 : 1));
    return cells;
}

/** How many steps of a traversal land somewhere other than next door. */
export function breaks(path: Step[]): number {
    let broken = 0;
    for (let at = 1; at < path.length; at += 1) {
        const up = Math.abs(path[at].up - path[at - 1].up);
        const across = Math.abs(path[at].across - path[at - 1].across);
        if (up + across !== 1) broken += 1;
    }
    return broken;
}

export interface Twin {
    code: string;
    /** Where it is, in the world. */
    latitude: number;
    longitude: number;
    /** Where it is inside its level-5 cell, in cells from the south-west. */
    up: number;
    across: number;
}

/**
 * The same five characters, in two level-5 cells on different continents.
 *
 * This is what the reset at level 6 buys. Both codes end in the same five
 * characters and both name the same position within their own cell -- so those
 * five characters mean something on their own, which is what makes the short
 * form recoverable instead of merely suggestive.
 */
export function twins(prefixes: string[], suffix: string): Twin[] {
    const level5 = span(5);

    return prefixes.map((prefix) => {
        const code = prefix + suffix;
        const [row, column] = GPC.codeToGrid(code);
        const [latitude, longitude] = GPC.decode(code);
        return {
            code: GPC.formatGPC(code),
            latitude,
            longitude,
            up: row % level5,
            across: column % level5,
        };
    });
}

/** The next code in string order, which is the next cell in the traversal. */
function after(code: string): string {
    const digits = [...code].map((symbol) => ALPHABET.indexOf(symbol));
    for (let at = digits.length - 1; at >= 0; at -= 1) {
        if (digits[at] < 24) {
            digits[at] += 1;
            return digits.map((index) => ALPHABET[index]).join('');
        }
        digits[at] = 0;
    }
    throw new Error('there is no code after the last one');
}

export interface Move {
    from: string;
    to: string;
    metres: number;
}

/** One step along the traversal, from the cell at this grid position. */
function stepFrom(row: number, column: number): Move {
    const from = GPC.gridToCode(row, column);
    const to = after(from);
    return { from: GPC.formatGPC(from), to: GPC.formatGPC(to), metres: GPC.distance(from, to) };
}

/**
 * What the reset costs, measured rather than counted.
 *
 * The traversal of a level-5 cell enters at its near corner and leaves at its
 * far one, so the step out of it is not a step next door. The count of those
 * steps is in the specification; the page shows one, beside an ordinary step,
 * because 9,374,999 means nothing without the other number.
 */
export function steps(): { ordinary: Move; across: Move } {
    const level5 = span(5);
    const row = 1000 * level5;          // a level-5 cell well inside the grid
    const column = 2000 * level5;

    return {
        ordinary: stepFrom(row + 400, column + 700),
        // The far corner of that cell: the last code in it, in order.
        across: stepFrom(row + level5 - 1, column + level5 - 1),
    };
}

/**
 * Where the traversal leaves one block and where it takes up the next.
 *
 * The page says the two cells touch. They do because of the mirror, not because
 * of anything obvious, so it is worth asking rather than asserting -- and the
 * corners are easy to describe backwards, which is how the first version of the
 * sentence had them at the top of the blocks instead of the bottom.
 */
export function joint(
    leftRow: number, leftColumn: number,
    rightRow: number, rightColumn: number,
): { down: number; across: number } {
    const step = span(2);

    const corner = (row: number, column: number, wanted: string) => {
        for (let down = 0; down < 5; down += 1) {
            for (let across = 0; across < 5; across += 1) {
                const code = GPC.gridToCode(row + down * step, column + across * step);
                if (code[1] === wanted) return { down, across, row: row + down * step,
                    column: column + across * step };
            }
        }
        throw new Error(`no ${wanted} cell in the block at ${row}, ${column}`);
    };

    const out = corner(leftRow, leftColumn, 'X');       // the last cell in order
    const back = corner(rightRow, rightColumn, '0');    // the first of the next

    if (out.row !== back.row || back.column - out.column !== step) {
        throw new Error(
            'the traversal leaves one block and takes up the next at cells that do '
            + 'not touch. The page draws them meeting.',
        );
    }

    return { down: out.down, across: out.across };
}

/**
 * Everything above, checked rather than claimed.
 *
 * Called from the page, so a construction that stopped behaving takes the build
 * down instead of printing a sentence that is no longer true.
 */
export function verify(): {
    cells: number; steps: number; suffixes: number; leaves: string;
} {
    const path = traversal(2, 3);
    if (path.length !== 625) {
        throw new Error(`a block holds ${path.length} cells two levels down, not 625`);
    }

    const broken = breaks(path);
    if (broken !== 0) {
        throw new Error(
            `${broken} of the 624 steps inside one block are not to an adjacent cell. `
            + 'The page draws this as a continuous curve.',
        );
    }

    // The last five characters must name the same offset wherever they appear.
    // Every level-5 cell is too many to walk, so this walks a spread of them.
    let suffixes = 0;
    const level5 = span(5);
    for (let up = 7; up < 2500; up += 313) {
        for (let across = 11; across < 3750; across += 457) {
            const inside = { up: (up * 37) % level5, across: (across * 53) % level5 };
            const one = GPC.gridToCode(up * level5 + inside.up, across * level5 + inside.across);
            const other = GPC.gridToCode(
                ((up + 1231) % 2500) * level5 + inside.up,
                ((across + 1777) % 3750) * level5 + inside.across,
            );

            if (one.slice(5) !== other.slice(5)) {
                throw new Error(
                    `the same position in two level-5 cells gives ${one.slice(5)} and `
                    + `${other.slice(5)}. The reset of section 5.3 is not holding.`,
                );
            }
            suffixes += 1;
        }
    }

    // The two blocks the page draws side by side, and where they join.
    const unit = span(1);
    const meeting = joint(2 * unit, 3 * unit, 2 * unit, 4 * unit);
    const leaves = meeting.down === 0 ? 'bottom' : meeting.down === 4 ? 'top' : 'middle';

    return { cells: path.length, steps: path.length - 1, suffixes, leaves };
}
