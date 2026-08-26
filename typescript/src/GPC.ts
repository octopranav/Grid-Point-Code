// Copyright 2017 Pranavkumar Patel
// Licensed under the Apache License, Version 2.0

import { GPCError } from './GPCError';
import * as ScreenList from './ScreenList';
import { V1 } from './V1';

/** Section 19.1. U+00B0, written out so no editor can lose it. */
const DEGREE_SIGN = '\u00b0';

/**
 * Section 18.3. North, north-east, east, south-east, south, south-west, west,
 * north-west. Rows increase northward, so north is +1.
 */
const NEIGHBOUR_STEPS: readonly (readonly [number, number])[] = [
    [1, 0],
    [1, 1],
    [0, 1],
    [-1, 1],
    [-1, 0],
    [-1, -1],
    [0, -1],
    [1, -1],
];

// Section 18.4, and the radius is 18.5. These are the only physical quantities
// in the format; everything else is arithmetic.
const M_PER_DEGREE_LAT = 111_132;
const M_PER_DEGREE_LONG = 111_319.49; // at the equator
const EARTH_RADIUS = 6_371_008.8; // mean radius of WGS 84

/**
 * What a string turns out to be once it has been normalised.
 *
 * No encoded code begins with `X`, so that space is reserved rather than
 * wasted. A reserved code is well formed and names no cell; it is not a typing
 * error, and the two are kept apart from the first release because a caller
 * that cannot tell them apart today cannot be taught the difference tomorrow.
 */
export const CodeClass = {
    GEOMETRIC: 'GEOMETRIC',
    RESERVED: 'RESERVED',
    INVALID: 'INVALID',
} as const;

export type CodeClass = (typeof CodeClass)[keyof typeof CodeClass];

/**
 * Version 2 of the Grid Point Code format.
 *
 * A code names one cell of a fixed grid laid over the Earth. Ten characters,
 * always. The first divides the world into 24 cells of 45 by 60 degrees; each
 * of the nine after it divides the cell named so far into 25 parts, five by
 * five. Two codes that begin with the same k characters therefore name points
 * in the same level-k cell -- containment, not correlation, so it holds for
 * every pair of points without exception.
 *
 * The whole format is arithmetic. There are no ordering tables and no generated
 * constants: a serpentine at level 1, a Peano digit reflection below it, and
 * one parity reset entering level 6. Section numbers in the comments refer to
 * SPEC.md, which is the normative description and the thing to implement from.
 *
 * Version 1 codes still decode, because codes end up on signs and in records
 * and removing that would orphan every one of them. `decode` dispatches on
 * length -- ten characters is version 2, eleven is version 1 -- and `encode`
 * emits version 2 only, so the old format cannot be minted again.
 */
export class GPC {
    // Section 4. Twenty-five symbols, digits first so that the alphabet is
    // ASCII-ascending and a plain string sort is a spatial sort. No vowel
    // appears, so no English word can be spelled by a code.
    private static readonly ALPHABET = '0123456789CDFGHJKLMNPRTWX';

    // Section 3.
    private static readonly CODE_LENGTH = 10;
    private static readonly LEVELS = 10;
    /** Section 5.3: both parity accumulators reset entering this level. */
    private static readonly RESET_LEVEL = 6;
    private static readonly P9 = 1_953_125; // 5 ** 9
    private static readonly P5 = 3_125; // 5 ** 5, the rows and columns inside one level-5 cell
    private static readonly HALF_P5 = 1_562; // section 12.2 adds this, not 1562.5
    private static readonly R5 = 2_500; // 4 * 5^4, rows of level-5 cells
    private static readonly C5 = 3_750; // 6 * 5^4, columns of level-5 cells
    private static readonly CODE_SPACE = 25 ** 10; // section 13
    private static readonly ROWS = 7_812_500; // 4 * 5^9
    private static readonly COLS = 11_718_750; // 6 * 5^9

    // Section 2.
    private static readonly MIN_LAT = -90;
    private static readonly MAX_LAT = 90;
    private static readonly MIN_LONG = -180;
    private static readonly MAX_LONG = 180;

    // Section 8. Exactly the letters that are not in the alphabet, less U, Q
    // and Y, which are rejected rather than aliased. L is a real symbol and is
    // never aliased to 1: it names a different cell, and aliasing it would make
    // two different codes collide.
    private static readonly ALIASES: Readonly<Record<string, string>> = {
        O: '0',
        I: '1',
        S: '5',
        Z: '2',
        B: '8',
        A: '4',
        E: '3',
        V: 'W',
    };

    private static readonly PREFIX = '#';
    private static readonly SEPARATOR = '-';
    private static readonly CHECK_MARK = '*';

    // Section 17.2. Three symbols turn up by chance too often to warn about.
    private static readonly SCREEN_MIN = 4;
    // ASCII whitespace only. A routine that also stripped the Unicode spaces
    // would accept in one port what another rejects, which is the whole thing
    // the shared vectors exist to prevent.
    private static readonly WHITESPACE = ' \t\n\v\f\r';

    /** The field element t, whose symbol index is 1 * 5 + 0. Section 14.2. */
    private static readonly T = 5;
    /** t^1 to t^11, the eleven check weights. Computed rather than transcribed. */
    private static readonly WEIGHTS = GPC.powersOfT();

    /*  PART 1 : ENCODE */

    /**
     * Encodes coordinates as a version 2 Grid Point Code.
     * @param latitude Latitude in decimal degrees, -90 to 90 inclusive.
     * @param longitude Longitude in decimal degrees, -180 to 180 inclusive.
     * @param formatted True for `#XXXXX-XXXXX`, false for the bare ten
     * characters. Both denote the same code.
     * @returns The code.
     * @throws GPCError if either coordinate is outside the domain, NaN or infinite.
     */
    public static encode(latitude: number, longitude: number, formatted: boolean = true): string {
        const [valid, message] = this.isValidCoordinates(latitude, longitude);
        if (!valid) throw new GPCError(message, `${message}: value out of valid range.`);

        const [row, col] = this.toGrid(latitude, longitude);
        const code = this.gridToCode(row, col);
        return formatted ? this.formatGPC(code) : code;
    }

    /**
     * Whether a coordinate pair is inside the domain, and which axis is not.
     *
     * The poles and both ends of the antimeridian are inside it; version 1
     * rejected all of them. NaN and the infinities fail the comparisons and so
     * are rejected here as well, in every language, without a separate test.
     * @param latitude Latitude in decimal degrees.
     * @param longitude Longitude in decimal degrees.
     * @returns [true, ""] if valid, otherwise [false, "LATITUDE" or "LONGITUDE"].
     */
    public static isValidCoordinates(latitude: number, longitude: number): [boolean, string] {
        if (!(latitude >= this.MIN_LAT && latitude <= this.MAX_LAT)) return [false, 'LATITUDE'];
        if (!(longitude >= this.MIN_LONG && longitude <= this.MAX_LONG)) return [false, 'LONGITUDE'];
        return [true, ''];
    }

    /**
     * Coordinates to a row and column of the full grid. Section 5.1.
     *
     * Three floating-point operations per axis, associating left to right. They
     * are the only floating-point arithmetic in the format, and section 7 pins
     * how they are evaluated: no reassociation, no fused multiply-add, no wider
     * intermediate. Everything after this is integers.
     * @param latitude Latitude in decimal degrees.
     * @param longitude Longitude in decimal degrees.
     * @returns Tuple [row, col].
     */
    public static toGrid(latitude: number, longitude: number): [number, number] {
        // The one case where two distinct inputs must give one code, so it
        // happens before any arithmetic that could no longer tell them apart.
        if (longitude === this.MAX_LONG) longitude = this.MIN_LONG;

        let row = Math.floor(((latitude + 90.0) * 7812500.0) / 180.0);
        let col = Math.floor(((longitude + 180.0) * 11718750.0) / 360.0);

        // Catches latitude +90, and nothing else. It is what makes the poles
        // encode instead of indexing past the end of the grid.
        row = row < 0 ? 0 : row > this.ROWS - 1 ? this.ROWS - 1 : row;
        col = col < 0 ? 0 : col > this.COLS - 1 ? this.COLS - 1 : col;
        return [row, col];
    }

    /**
     * A row and column to ten characters. Section 5.2.
     *
     * Level 1 is a serpentine over the 24 blocks, west to east, snaking
     * northward. Levels 2 to 10 are a Peano digit reflection: each axis is
     * mirrored according to the parity of the digits accumulated in the other,
     * which is what puts consecutive codes in adjacent cells.
     * @param row Row of the full grid.
     * @param col Column of the full grid.
     * @returns The unformatted ten-character code.
     */
    public static gridToCode(row: number, col: number): string {
        const r1 = Math.floor(row / this.P9);
        const c1 = Math.floor(col / this.P9);
        let out = this.ALPHABET[r1 * 6 + (r1 % 2 === 0 ? c1 : 5 - c1)];

        let sr = r1;
        let sc = c1;
        let p = this.P9;
        for (let level = 2; level <= this.LEVELS; level++) {
            if (level === this.RESET_LEVEL) {
                // Section 5.3. Without this the last five characters would mean
                // something different in every level-5 cell, and the short form
                // would name nothing on its own.
                sr = 0;
                sc = 0;
            }
            p /= 5;
            const r = Math.floor(row / p) % 5;
            const c = Math.floor(col / p) % 5;
            // The order of these four statements is normative. R is decided
            // from sc before this level's c is added to it, and C from sr after
            // this level's r has been added. Reversing either is a different
            // format.
            const bigR = sc % 2 === 0 ? r : 4 - r;
            sr += r;
            const bigC = sr % 2 === 0 ? c : 4 - c;
            sc += c;
            out += this.ALPHABET[bigR * 5 + bigC];
        }

        return out;
    }

    /**
     * The presentation form, `#XXXXX-XXXXX`. Section 5.4.
     *
     * The grouping is not arbitrary: the second group is exactly the short
     * form, so a printed code shows its own local form.
     * @param code Unformatted ten-character code.
     * @returns The formatted code.
     */
    public static formatGPC(code: string): string {
        return `${this.PREFIX}${code.slice(0, 5)}${this.SEPARATOR}${code.slice(5)}`;
    }

    /*  PART 2 : DECODE */

    /**
     * Decodes a code to the centre of the cell it names.
     *
     * Dispatches on length once the separators are stripped: ten characters is
     * version 2, eleven is version 1. A code carrying a check character is
     * always version 2, since version 1 has none.
     * @param gridPointCode Formatted or unformatted, with or without a `*` check character.
     * @returns Tuple [latitude, longitude], six decimal places.
     * @throws GPCError with reason GPC_RESERVED for a well-formed code beginning
     * with X, or one of the invalid reasons otherwise.
     */
    public static decode(gridPointCode: string): [number, number] {
        const [payload, check] = this.split(gridPointCode);
        if (check === null && payload.length === V1.CODE_LENGTH) return V1.decode(payload);

        const [row, col] = this.codeToGrid(this.geometric(gridPointCode));
        return [this.round6((2 * row + 1) * 1152 - 9_000_000_000), this.round6((2 * col + 1) * 1536 - 18_000_000_000)];
    }

    /**
     * The boundaries of the cell a version 2 code names. Section 6.3.
     * @param gridPointCode Formatted or unformatted code.
     * @returns Tuple [south, west, north, east].
     * @throws GPCError as `decode`. Version 1 codes have no area; they resolve
     * to a corner and are not part of this grid.
     */
    public static decodeToArea(gridPointCode: string): [number, number, number, number] {
        const [row, col] = this.codeToGrid(this.geometric(gridPointCode));
        return [
            (row * 180.0) / 7812500.0 - 90.0,
            (col * 360.0) / 11718750.0 - 180.0,
            ((row + 1) * 180.0) / 7812500.0 - 90.0,
            ((col + 1) * 360.0) / 11718750.0 - 180.0,
        ];
    }

    /**
     * Decodes an eleven-character version 1 code. Appendix B.
     *
     * `decode` reaches this on its own for anything eleven characters long. The
     * explicit entry point is here for a caller that knows which format it
     * holds and wants to say so.
     *
     * Version 1 returns the corner of its cell rather than the centre, which is
     * what every version 1 release has returned.
     * @param gridPointCode Formatted or unformatted version 1 code.
     * @returns Tuple [latitude, longitude] in decimal degrees.
     */
    public static decodeV1(gridPointCode: string): [number, number] {
        return V1.decode(gridPointCode);
    }

    /**
     * Ten characters back to a row and column. Section 6.1.
     *
     * The inverse of `gridToCode`, character by character. Expects a
     * normalised, geometric code.
     * @param code Normalised ten-character code.
     * @returns Tuple [row, col].
     */
    public static codeToGrid(code: string): [number, number] {
        const i = this.ALPHABET.indexOf(code[0]);
        const r1 = Math.floor(i / 6);
        const k = i % 6;
        const c1 = r1 % 2 === 0 ? k : 5 - k;

        let row = r1;
        let col = c1;
        let sr = r1;
        let sc = c1;
        for (let level = 2; level <= this.LEVELS; level++) {
            if (level === this.RESET_LEVEL) {
                sr = 0;
                sc = 0;
            }
            const j = this.ALPHABET.indexOf(code[level - 1]);
            const bigR = Math.floor(j / 5);
            const bigC = j % 5;
            const r = sc % 2 === 0 ? bigR : 4 - bigR;
            sr += r;
            const c = sr % 2 === 0 ? bigC : 4 - bigC;
            sc += c;
            row = row * 5 + r;
            col = col * 5 + c;
        }

        return [row, col];
    }

    /*  PART 3 : PARSE, CLASSIFY, CHECK */

    /**
     * Case-folds, strips separators, applies the alias table. Section 8.
     * @param gridPointCode Anything a person might have typed.
     * @returns Tuple [payload, check]. The check is null when the input carried
     * no `*`, and is otherwise returned however long it normalised: deciding
     * whether it is acceptable belongs to `validate`.
     * @throws GPCError GPC_NULL if there is nothing at all to parse.
     */
    public static normalise(gridPointCode: string): [string, string | null] {
        const [payload, check] = this.split(gridPointCode);
        return [this.alias(payload), check === null ? null : this.alias(check)];
    }

    /**
     * Classifies a string and says why, if the answer is INVALID. Section 9.
     * @param gridPointCode Anything a person might have typed.
     * @returns Tuple [class, reason]. The reason is empty for anything that is
     * not INVALID, and is otherwise GPC_NULL, GPC_LENGTH, GPC_CHAR or
     * GPC_CHECK, tested in that order.
     */
    public static validate(gridPointCode: string): [CodeClass, string] {
        let code: string, check: string | null;
        try {
            [code, check] = this.normalise(gridPointCode);
        } catch (error) {
            return [CodeClass.INVALID, (error as GPCError).reason];
        }
        if (code.length !== this.CODE_LENGTH) return [CodeClass.INVALID, 'GPC_LENGTH'];
        for (const character of code) {
            if (!this.ALPHABET.includes(character)) return [CodeClass.INVALID, 'GPC_CHAR'];
        }
        // A check that does not hold is not something to discard. A caller told
        // a code is valid has to be able to decode it.
        if (check !== null && check !== this.checkSymbol(code)) return [CodeClass.INVALID, 'GPC_CHECK'];
        return [code[0] === 'X' ? CodeClass.RESERVED : CodeClass.GEOMETRIC, ''];
    }

    /**
     * GEOMETRIC, RESERVED or INVALID. Section 9 and Appendix C.
     * @param gridPointCode Anything a person might have typed.
     * @returns The class.
     */
    public static classify(gridPointCode: string): CodeClass {
        return this.validate(gridPointCode)[0];
    }

    /**
     * Whether a string is a version 2 code that decodes.
     *
     * True for GEOMETRIC only. A reserved code is false, because it names no
     * cell, and so is a version 1 code: `classify` describes this grid, and
     * eleven characters are not part of it. `decode` still reads version 1, and
     * `isValidV1` answers for it.
     * @param gridPointCode Anything a person might have typed.
     * @returns True if the string is a decodable version 2 code.
     */
    public static isValid(gridPointCode: string): boolean {
        return this.validate(gridPointCode)[0] === CodeClass.GEOMETRIC;
    }

    /**
     * Whether a string is a version 1 code, and why not when it is not.
     * @param gridPointCode Anything a person might have typed.
     * @returns [true, ""] if valid, otherwise [false, reason].
     */
    public static isValidV1(gridPointCode: string): [boolean, string] {
        return V1.isValid(gridPointCode);
    }

    /**
     * The optional GF(25) check character for a code. Section 14.
     *
     * For voice, radio and paper. Written after a star, `#G3RJM-98NM9*T`. It
     * detects every single-symbol error and every adjacent transposition, and
     * it is not canonical: the ten-character form is what gets stored and
     * interchanged, and this is never emitted unless asked for.
     * @param gridPointCode Formatted or unformatted code.
     * @returns The check character.
     * @throws GPCError if the input is not ten symbols of the alphabet. A
     * reserved code has a check character like any other.
     */
    public static checkCharacter(gridPointCode: string): string {
        const [code] = this.normalise(gridPointCode);
        if (code.length !== this.CODE_LENGTH) throw new GPCError('GPC_LENGTH');
        for (const character of code) {
            if (!this.ALPHABET.includes(character)) throw new GPCError('GPC_CHAR');
        }
        return this.checkSymbol(code);
    }

    /**
     * A code in its check form, `#G3RJM-98NM9*T`. Section 14.6.
     *
     * The form to use for voice, radio and paper, and the one an application
     * should share when the code may be read aloud or written down. Building it
     * by hand is three operations and two chances to be wrong: the star can be
     * dropped, or the check character spliced inside the separator instead of
     * after it. Neither mistake is caught by anything.
     *
     * The check character is computed for the payload given. Any check character
     * the input already carried is ignored, so a code already in check form
     * comes back with a correct one.
     * @param gridPointCode Formatted or unformatted, with or without a check
     * character.
     * @param formatted True for `#XXXXX-XXXXX*K`, false for `XXXXXXXXXX*K`.
     * @returns The check form.
     * @throws GPCError if the input is not ten symbols of the alphabet. A
     * reserved code has a check form like any other.
     */
    public static withCheck(gridPointCode: string, formatted: boolean = true): string {
        const [code] = this.normalise(gridPointCode);
        const check = this.checkCharacter(code);
        return (formatted ? this.formatGPC(code) : code) + this.CHECK_MARK + check;
    }

    /*  PART 4 : THE LOCALITY API */

    /**
     * The first `level` characters of a code, normalised. Section 18.1.
     *
     * A cell names a region: two codes lie in the same level-k cell exactly when
     * they share their first k characters, so this is the region identifier the
     * guarantee is about.
     * @param gridPointCode A code, or a longer cell.
     * @param level 1 to 10.
     * @returns The cell, bare -- no `#` and no separator. Ten characters is a
     * code and anything shorter is a region; presenting a cell as a code would
     * break the fixed length the format is recognised by.
     * @throws GPCError GPC_LEVEL for a level outside 1 to 10, GPC_LENGTH if the
     * argument is shorter than the level asked for, GPC_RESERVED for a cell
     * beginning with X, or one of the parsing reasons.
     */
    public static cell(gridPointCode: string, level: number): string {
        if (!Number.isInteger(level) || level < 1 || level > this.LEVELS) throw new GPCError('GPC_LEVEL');
        const code = this.cellOf(gridPointCode);
        if (code.length < level) throw new GPCError('GPC_LENGTH');
        return code.slice(0, level);
    }

    /**
     * Whether a code lies inside a cell. Section 18.2.
     *
     * The prefix test, and nothing more. What section 10 buys is that this is a
     * true geometric containment test rather than an approximation of one: no
     * tolerance, no edge case at a boundary, and no pair of points on Earth for
     * which the string answer and the geometric answer differ.
     * @param cell A cell of 1 to 10 characters.
     * @param gridPointCode A code, or a cell.
     * @returns True if the code lies inside the cell.
     */
    public static contains(cell: string, gridPointCode: string): boolean {
        const prefix = this.cellOf(cell);
        const code = this.cellOf(gridPointCode);
        return code.length >= prefix.length && code.slice(0, prefix.length) === prefix;
    }

    /**
     * The cells sharing an edge or a corner, in order. Section 18.3.
     *
     * North, north-east, east, south-east, south, south-west, west, north-west.
     * Columns wrap at the antimeridian; rows do not, because the grid ends at
     * the poles, so a cell in the top or bottom row has five neighbours and the
     * three that would lie off the grid are absent rather than empty.
     * @param cell A cell of 1 to 10 characters.
     * @returns Bare cells of the same length as the argument.
     */
    public static neighbours(cell: string): string[] {
        const code = this.cellOf(cell);
        const level = code.length;
        const p = Math.pow(5, this.LEVELS - level);
        const [row, col] = this.codeToGrid(code + this.ALPHABET[0].repeat(this.LEVELS - level));
        const cellRow = Math.floor(row / p);
        const cellCol = Math.floor(col / p);
        const rowCells = 4 * Math.pow(5, level - 1);
        const colCells = 6 * Math.pow(5, level - 1);

        const out: string[] = [];
        for (const [dRow, dCol] of NEIGHBOUR_STEPS) {
            const r = cellRow + dRow;
            if (r < 0 || r >= rowCells) continue;
            const c = (cellCol + dCol + colCells) % colCells;
            out.push(this.gridToCode(r * p, c * p).slice(0, level));
        }
        return out;
    }

    /**
     * How big a level-k cell is. Section 18.4.
     * @param level 1 to 10.
     * @returns Tuple [latitude span, longitude span, north-south metres,
     * east-west metres]. The north-south figure holds everywhere; the east-west
     * one is the value at the equator and shrinks with the cosine of latitude,
     * which is a multiplication left to the caller.
     */
    public static cellDimensions(level: number): [number, number, number, number] {
        if (!Number.isInteger(level) || level < 1 || level > this.LEVELS) throw new GPCError('GPC_LEVEL');
        const divisor = Math.pow(5, level - 1);
        const latitudeSpan = 45 / divisor;
        const longitudeSpan = 60 / divisor;
        return [latitudeSpan, longitudeSpan, latitudeSpan * M_PER_DEGREE_LAT, longitudeSpan * M_PER_DEGREE_LONG];
    }

    /**
     * Great-circle metres between the centres of two cells. Section 18.5.
     *
     * The cells may be of different levels. This is the one operation in the
     * format that is not bit-identical across languages: no standard library
     * rounds sine, cosine or arc sine correctly, so two ports agree to about a
     * millimetre rather than exactly. Anything that needs a reproducible
     * ordering must rank on grid indices, as `suggestCorrections` does.
     * @param a A cell of 1 to 10 characters.
     * @param b Another.
     * @returns Metres.
     */
    public static distance(a: string, b: string): number {
        const [latitudeA, longitudeA] = this.cellCentre(a);
        const [latitudeB, longitudeB] = this.cellCentre(b);

        const phi1 = (latitudeA * Math.PI) / 180;
        const phi2 = (latitudeB * Math.PI) / 180;
        const dPhi = phi2 - phi1;
        const dLambda = ((longitudeB - longitudeA) * Math.PI) / 180;

        let h =
            Math.sin(dPhi / 2) * Math.sin(dPhi / 2) +
            Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        // Rounding can carry the sum a unit past 1 for points near opposite ends
        // of the Earth, where arc sine is undefined.
        if (h > 1) h = 1;
        return 2 * EARTH_RADIUS * Math.asin(Math.sqrt(h));
    }

    /**
     * The row and column of the cell a code names. Section 18.6.
     *
     * The accessor for a caller building a spatial structure of its own -- a
     * tile index, a join key, a quadtree -- who wants the integers rather than
     * degrees rounded to six places.
     * @param gridPointCode Anything a person might have typed.
     * @returns Tuple [row, col].
     */
    public static decodeToGrid(gridPointCode: string): [number, number] {
        return this.codeToGrid(this.geometric(gridPointCode));
    }

    /**
     * The last five characters of a code. Section 12.1.
     *
     * Literally the second printed group of `#XXXXX-XXXXX`, so a printed code
     * shows its own short form. The leading dash belongs to the presentation
     * form and is not returned; `recoverShort` accepts it either way.
     * @param gridPointCode Anything a person might have typed.
     * @returns The five characters.
     */
    public static shorten(gridPointCode: string): string {
        return this.geometric(gridPointCode).slice(5);
    }

    /**
     * The full code a short form names, near a reference. Section 12.2.
     *
     * Exact integer arithmetic -- no search, no distance, no tie to break -- and
     * exact whenever the reference is within half a level-5 cell of the true
     * point on each axis, which is 0.03598848 degrees of latitude (3.999 km) and
     * 0.04798464 degrees of longitude (5.342 km at the equator, less elsewhere).
     *
     * Outside that box it returns a neighbouring cell's copy of the same offset,
     * a plausible location 8 or 10 km away. A caller that cannot bound its
     * reference should not be using the short form.
     * @param short The five characters, with or without the leading dash.
     * @param nearLatitude Reference latitude.
     * @param nearLongitude Reference longitude.
     * @param formatted True for `#XXXXX-XXXXX`, false for the bare ten.
     * @returns The full code.
     */
    public static recoverShort(
        short: string,
        nearLatitude: number,
        nearLongitude: number,
        formatted: boolean = true,
    ): string {
        const [tail] = this.normalise(short);
        if (tail.length !== this.CODE_LENGTH - 5) throw new GPCError('GPC_LENGTH');
        for (const character of tail) {
            if (!this.ALPHABET.includes(character)) throw new GPCError('GPC_CHAR');
        }

        const [rowLow, colLow] = this.readTail(tail);
        const [valid, message] = this.isValidCoordinates(nearLatitude, nearLongitude);
        if (!valid) throw new GPCError(message, message + ': value out of valid range.');
        const [rowRef, colRef] = this.toGrid(nearLatitude, nearLongitude);

        // Floor division over values that may be negative. Truncation toward
        // zero is wrong here, and wrong only west and south of the reference.
        let cellRow = Math.floor((rowRef - rowLow + this.HALF_P5) / this.P5);
        cellRow = cellRow < 0 ? 0 : cellRow > this.R5 - 1 ? this.R5 - 1 : cellRow;
        const cellCol = ((Math.floor((colRef - colLow + this.HALF_P5) / this.P5) % this.C5) + this.C5) % this.C5;

        const code = this.gridToCode(cellRow * this.P5 + rowLow, cellCol * this.P5 + colLow);
        return formatted ? this.formatGPC(code) : code;
    }

    /**
     * Codes one typo away that are plausible near a reference. Section 15.3.
     *
     * At most 249 candidates -- 240 single-character substitutions and up to 9
     * adjacent transpositions -- filtered to those in the reference's level-k
     * cell or one of its eight neighbours, and ranked by
     * `9*dRow^2 + 16*dCol^2`, which is squared distance in degree space. Ties
     * break on the integer form. Every step is integer arithmetic, so all four
     * ports return the same list in the same order.
     *
     * Level 6 is the default: it suits a device fix or a named suburb and
     * returns one candidate in the median case. Widening it to cover a poorer
     * reference costs precision, not correctness.
     * @param gridPointCode The code as typed, which need not decode: a code with
     * a wrong character is exactly what this is for. It must still normalise to
     * ten symbols of the alphabet.
     * @param nearLatitude Reference latitude.
     * @param nearLongitude Reference longitude.
     * @param level The window is 3 by 3 cells at this level.
     * @param formatted True for `#XXXXX-XXXXX`, false for the bare ten.
     * @returns The candidates, best first.
     */
    public static suggestCorrections(
        gridPointCode: string,
        nearLatitude: number,
        nearLongitude: number,
        level: number = 6,
        formatted: boolean = true,
    ): string[] {
        if (!Number.isInteger(level) || level < 1 || level > this.LEVELS) throw new GPCError('GPC_LEVEL');
        const [code] = this.normalise(gridPointCode);
        if (code.length !== this.CODE_LENGTH) throw new GPCError('GPC_LENGTH');
        for (const character of code) {
            if (!this.ALPHABET.includes(character)) throw new GPCError('GPC_CHAR');
        }

        const [valid, message] = this.isValidCoordinates(nearLatitude, nearLongitude);
        if (!valid) throw new GPCError(message, message + ': value out of valid range.');
        const [rowRef, colRef] = this.toGrid(nearLatitude, nearLongitude);

        const p = Math.pow(5, this.LEVELS - level);
        const refRowCell = Math.floor(rowRef / p);
        const refColCell = Math.floor(colRef / p);
        const colCells = this.COLS / p;

        const scored: [number, number, string][] = [];
        for (const candidate of this.candidates(code)) {
            if (candidate[0] === 'X') continue; // reserved, never geometric
            const [row, col] = this.codeToGrid(candidate);

            const dRowCell = Math.floor(row / p) - refRowCell;
            let dColCell = (Math.floor(col / p) - refColCell + colCells) % colCells;
            if (dColCell > Math.floor(colCells / 2)) dColCell -= colCells;
            if (Math.abs(dRowCell) > 1 || Math.abs(dColCell) > 1) continue;

            const dRow = row - rowRef;
            let dCol = col - colRef;
            if (dCol > this.COLS / 2) dCol -= this.COLS;
            else if (dCol < -this.COLS / 2) dCol += this.COLS;

            scored.push([9 * dRow * dRow + 16 * dCol * dCol, this.toInteger(candidate), candidate]);
        }

        scored.sort((x, y) => x[0] - y[0] || x[1] - y[1]);
        return scored.map(([, , candidate]) => (formatted ? this.formatGPC(candidate) : candidate));
    }

    /**
     * The code as a base-25 numeral. Section 13.
     *
     * Forty-seven bits, so six bytes big-endian, and order-preserving: sorting
     * the integers sorts the codes, which sorts the cells geographically. A
     * reserved code is at or above 91,552,734,375,000 and a geometric one below
     * it, so one comparison classifies without parsing.
     *
     * The largest value is 95,367,431,640,624, well inside the range a number
     * holds exactly, so nothing here loses a unit.
     * @param gridPointCode Anything a person might have typed.
     * @returns The value.
     */
    public static toInteger(gridPointCode: string): number {
        const code = this.payload(gridPointCode);
        let value = 0;
        for (const character of code) {
            value = value * 25 + this.ALPHABET.indexOf(character);
        }
        return value;
    }

    /**
     * The code a base-25 numeral names. Section 13.
     * @param value 0 to 25^10 - 1.
     * @param formatted True for `#XXXXX-XXXXX`, false for the bare ten.
     * @returns The code.
     * @throws GPCError GPC_RANGE if the value is outside the range.
     */
    public static fromInteger(value: number, formatted: boolean = true): string {
        if (!Number.isInteger(value) || value < 0 || value >= this.CODE_SPACE) throw new GPCError('GPC_RANGE');
        const out: string[] = new Array(this.LEVELS);
        let rest = value;
        for (let i = this.LEVELS - 1; i >= 0; i--) {
            out[i] = this.ALPHABET[rest % 25];
            rest = Math.floor(rest / 25);
        }
        const code = out.join('');
        return formatted ? this.formatGPC(code) : code;
    }

    /**
     * Substrings of a code that spell something unwanted. Section 17.
     *
     * Advisory, and non-normative. It reports and never blocks: nothing in this
     * package refuses to encode, decode or validate because of what this found.
     * @param gridPointCode Anything a person might have typed.
     * @returns Tuple [version, spans]. The spans are [position, length] with
     * position counted from 1, ordered by position and then by length. Spans may
     * overlap and every match is reported. A clean code returns the version and
     * no spans, because a caller has to be able to tell "clean under this list"
     * from "never screened".
     */
    public static screen(gridPointCode: string): [string, [number, number][]] {
        const code = this.payload(gridPointCode);
        const spans: [number, number][] = [];
        for (let length = this.SCREEN_MIN; length <= this.CODE_LENGTH; length++) {
            for (let start = 0; start <= this.CODE_LENGTH - length; start++) {
                if (ScreenList.ENTRIES.has(this.screenHash(code.substring(start, start + length)))) {
                    spans.push([start + 1, length]);
                }
            }
        }
        return [ScreenList.VERSION, spans];
    }

    /**
     * Encodes a sequence of [latitude, longitude] pairs.
     *
     * For dataset work. The first bad coordinate throws, rather than a bad row
     * being silently dropped; `encodeStream` is the one to reach for when the
     * caller wants to handle failures row by row.
     * @param points The coordinates.
     * @param formatted True for `#XXXXX-XXXXX`, false for the bare ten.
     * @returns The codes.
     */
    public static encodeAll(points: Iterable<[number, number]>, formatted: boolean = true): string[] {
        return [...this.encodeStream(points, formatted)];
    }

    /**
     * Encodes a sequence lazily, one code at a time.
     * @param points The coordinates.
     * @param formatted True for `#XXXXX-XXXXX`, false for the bare ten.
     */
    public static *encodeStream(points: Iterable<[number, number]>, formatted: boolean = true): Generator<string> {
        for (const [latitude, longitude] of points) {
            yield this.encode(latitude, longitude, formatted);
        }
    }

    /**
     * Decodes a sequence of codes to [latitude, longitude] pairs.
     * @param codes The codes.
     * @returns The coordinates.
     */
    public static decodeAll(codes: Iterable<string>): [number, number][] {
        return [...this.decodeStream(codes)];
    }

    /**
     * Decodes a sequence lazily, one pair at a time.
     * @param codes The codes.
     */
    public static *decodeStream(codes: Iterable<string>): Generator<[number, number]> {
        for (const code of codes) {
            yield this.decode(code);
        }
    }

    /*  PART 5 : COORDINATE CONVERSIONS */

    /**
     * Degrees, minutes and seconds, latitude first. Section 19.1.
     *
     * `43°39'00.00"N, 79°22'48.00"W`.
     *
     * Lossy: a hundredth of a second is 0.309 m of latitude. A decoded code
     * survives the trip all the same, because `decode` returns a cell centre and
     * that sits eight times further from the nearest boundary than this rounding
     * can move it. For exact interchange use `toGeoURI`.
     * @param latitude Decimal degrees.
     * @param longitude Decimal degrees.
     * @returns The text.
     */
    public static toDMS(latitude: number, longitude: number): string {
        const [valid, message] = this.isValidCoordinates(latitude, longitude);
        if (!valid) throw new GPCError(message, message + ': value out of valid range.');
        return this.dmsAxis(latitude, 'N', 'S') + ', ' + this.dmsAxis(longitude, 'E', 'W');
    }

    /**
     * Reads degrees, minutes and seconds back. Section 19.1.
     *
     * Each axis is a signed or hemisphere-marked value; the unit marker after
     * the degrees is required, because it is what tells one axis from the next
     * when no comma separates them.
     * @param text The DMS text.
     * @returns Tuple [latitude, longitude].
     * @throws GPCError GPC_DMS for anything the grammar does not accept, or
     * LATITUDE or LONGITUDE for a value outside the domain.
     */
    public static fromDMS(text: string): [number, number] {
        const scan = new Scan(text);
        const latitude = scan.axis(true);
        scan.spaces();
        if (scan.peek() === ',') scan.take();
        const longitude = scan.axis(false);
        scan.spaces();
        if (!scan.done()) throw new GPCError('GPC_DMS');

        const [valid, message] = this.isValidCoordinates(latitude, longitude);
        if (!valid) throw new GPCError(message, message + ': value out of valid range.');
        return [latitude, longitude];
    }

    /**
     * An RFC 5870 URI in its simplest form. Section 19.2.
     *
     * `geo:43.650006,-79.380004`. Six decimal places, trailing zeros dropped,
     * which is exactly what `decode` produces, so a code written out this way
     * and read back encodes to the same code every time.
     * @param latitude Decimal degrees.
     * @param longitude Decimal degrees.
     * @returns The URI.
     */
    public static toGeoURI(latitude: number, longitude: number): string {
        const [valid, message] = this.isValidCoordinates(latitude, longitude);
        if (!valid) throw new GPCError(message, message + ': value out of valid range.');
        return 'geo:' + this.decimal6(latitude) + ',' + this.decimal6(longitude);
    }

    /**
     * Reads an RFC 5870 URI back. Section 19.2.
     *
     * A third coordinate is an altitude and is discarded. Parameters are
     * ignored, except that `crs` is rejected unless it is `wgs84`: this format
     * is defined on WGS 84 alone, and silently reading a code as though it were
     * on another datum would put it in the wrong place.
     * @param text The URI.
     * @returns Tuple [latitude, longitude].
     */
    public static fromGeoURI(text: string): [number, number] {
        if (text === null || text === undefined) throw new GPCError('GPC_NULL');
        let body = text.trim();
        if (body.slice(0, 4).toLowerCase() !== 'geo:') throw new GPCError('GPC_GEO');
        body = body.slice(4);

        const semicolon = body.indexOf(';');
        if (semicolon >= 0) {
            for (const parameter of body.slice(semicolon + 1).split(';')) {
                const equals = parameter.indexOf('=');
                const name = equals < 0 ? parameter : parameter.slice(0, equals);
                const value = equals < 0 ? '' : parameter.slice(equals + 1);
                if (name.toLowerCase() === 'crs' && value.toLowerCase() !== 'wgs84') throw new GPCError('GPC_GEO');
            }
            body = body.slice(0, semicolon);
        }

        const parts = body.split(',');
        if (parts.length !== 2 && parts.length !== 3) throw new GPCError('GPC_GEO');
        const latitude = this.geoNumber(parts[0]);
        const longitude = this.geoNumber(parts[1]);
        if (parts.length === 3) this.geoNumber(parts[2]); // altitude, parsed and dropped

        const [valid, message] = this.isValidCoordinates(latitude, longitude);
        if (!valid) throw new GPCError(message, message + ': value out of valid range.');
        return [latitude, longitude];
    }

    /*  PART 6 : INTERNALS */

    /**
     * Payload and check character, cleaned but not yet aliased.
     *
     * The dispatch in `decode` needs to see the characters as typed, because
     * version 1 has its own alphabet and the version 2 alias table would
     * corrupt it.
     */
    private static split(gridPointCode: string): [string, string | null] {
        if (gridPointCode === null || gridPointCode === undefined) throw new GPCError('GPC_NULL');
        let blank = true;
        for (const character of gridPointCode) {
            if (!this.WHITESPACE.includes(character)) {
                blank = false;
                break;
            }
        }
        if (blank) throw new GPCError('GPC_NULL');

        let text = gridPointCode;
        let check: string | null = null;
        const star = text.indexOf(this.CHECK_MARK);
        if (star >= 0) {
            check = this.clean(text.slice(star + 1));
            text = text.slice(0, star);
        }
        return [this.clean(text), check];
    }

    /**
     * Upper-case by ASCII rules, then drop `#`, `-` and whitespace.
     *
     * A locale-sensitive upper-casing routine would map `i` to a dotted capital
     * in a Turkish locale, and the same code would be valid in one locale and
     * invalid in another.
     */
    private static clean(text: string): string {
        let out = '';
        for (let character of text) {
            if (character >= 'a' && character <= 'z') {
                character = String.fromCharCode(character.charCodeAt(0) - 32);
            }
            if (character === this.PREFIX || character === this.SEPARATOR || this.WHITESPACE.includes(character)) {
                continue;
            }
            out += character;
        }
        return out;
    }

    /** Reads the confusable letters as the symbols they were meant to be. */
    private static alias(text: string): string {
        let out = '';
        for (const character of text) {
            out += this.ALIASES[character] ?? character;
        }
        return out;
    }

    /** The ten characters, or the typed error that stops decoding. */
    private static geometric(gridPointCode: string): string {
        const [kind, reason] = this.validate(gridPointCode);
        if (kind === CodeClass.INVALID) throw new GPCError(reason);
        if (kind === CodeClass.RESERVED) throw new GPCError('GPC_RESERVED');
        return this.normalise(gridPointCode)[0];
    }

    /** (a + b*t) + (c + d*t), elements indexed b*5 + a. Section 14.2. */
    private static gfAdd(x: number, y: number): number {
        return ((Math.floor(x / 5) + Math.floor(y / 5)) % 5) * 5 + (((x % 5) + (y % 5)) % 5);
    }

    /** (a + b*t)(c + d*t) with t^2 = 4t + 3. Section 14.2. */
    private static gfMul(x: number, y: number): number {
        const a = x % 5,
            b = Math.floor(x / 5);
        const c = y % 5,
            d = Math.floor(y / 5);
        return ((a * d + b * c + 4 * b * d) % 5) * 5 + ((a * c + 3 * b * d) % 5);
    }

    /** t^1 to t^11. */
    private static powersOfT(): number[] {
        const weights: number[] = [];
        let x = 1;
        for (let i = 0; i < 11; i++) {
            x = GPC.gfMul(x, 5);
            weights.push(x);
        }
        return weights;
    }

    /** c = t * S, where S is the syndrome over the ten payload symbols. */
    private static checkSymbol(code: string): string {
        let syndrome = 0;
        for (let i = 0; i < code.length; i++) {
            syndrome = this.gfAdd(syndrome, this.gfMul(this.WEIGHTS[i], this.ALPHABET.indexOf(code[i])));
        }
        return this.ALPHABET[this.gfMul(this.T, syndrome)];
    }

    /**
     * Rounds a count of 1e-8 degrees to six decimal places. Section 6.2.
     *
     * Ties are unreachable -- every reachable value is congruent to a multiple
     * of 4 modulo 100 -- so no choice of rounding mode can change any result,
     * and no implementation has to make the choice.
     */
    private static round6(value: number): number {
        const magnitude = Math.abs(value);
        let quotient = Math.floor(magnitude / 100);
        if (magnitude % 100 >= 50) quotient += 1;
        return (value < 0 ? -quotient : quotient) / 1_000_000;
    }

    /**
     * Ten symbols of the alphabet, reserved ones included.
     *
     * What `screen` and `toInteger` need: both act on the string rather than on
     * the cell it names, so an X in position 1 is no obstacle to either.
     */
    private static payload(gridPointCode: string): string {
        const [code, check] = this.normalise(gridPointCode);
        if (code.length !== this.CODE_LENGTH) throw new GPCError('GPC_LENGTH');
        for (const character of code) {
            if (!this.ALPHABET.includes(character)) throw new GPCError('GPC_CHAR');
        }
        if (check !== null && check !== this.checkSymbol(code)) throw new GPCError('GPC_CHECK');
        return code;
    }

    /** A normalised cell of 1 to 10 symbols, or the typed error. Section 18.1. */
    private static cellOf(text: string): string {
        const [code, check] = this.normalise(text);
        if (code.length < 1 || code.length > this.LEVELS) throw new GPCError('GPC_LENGTH');
        for (const character of code) {
            if (!this.ALPHABET.includes(character)) throw new GPCError('GPC_CHAR');
        }
        if (check !== null && (code.length !== this.CODE_LENGTH || check !== this.checkSymbol(code))) {
            throw new GPCError('GPC_CHECK');
        }
        if (code[0] === 'X') throw new GPCError('GPC_RESERVED');
        return code;
    }

    /**
     * The centre of a cell of any level, exact to 1e-8 degrees. Section 18.5.
     *
     * Private on purpose. For a ten-character code this differs from `decode` in
     * the seventh decimal place, and two public answers to "where is this cell"
     * would be one too many.
     *
     * Any symbol will do as padding. By section 10 the first k characters fix
     * the level-k cell, so whatever the padded code names, dividing by p lands
     * on the same cell indices.
     */
    private static cellCentre(text: string): [number, number] {
        const code = this.cellOf(text);
        const p = Math.pow(5, this.LEVELS - code.length);
        const [row, col] = this.codeToGrid(code + this.ALPHABET[0].repeat(this.LEVELS - code.length));
        return [
            ((2 * Math.floor(row / p) + 1) * p * 1152) / 100_000_000 - 90,
            ((2 * Math.floor(col / p) + 1) * p * 1536) / 100_000_000 - 180,
        ];
    }

    /**
     * The last five characters as an offset in a level-5 cell. Section 12.2.
     *
     * The loop of `codeToGrid` with the parity seeded at zero and no level-1
     * step, which is what the reset of section 5.3 makes meaningful.
     */
    private static readTail(tail: string): [number, number] {
        let row = 0,
            col = 0,
            sr = 0,
            sc = 0;
        for (const character of tail) {
            const j = this.ALPHABET.indexOf(character);
            const bigR = Math.floor(j / 5);
            const bigC = j % 5;
            const r = sc % 2 === 0 ? bigR : 4 - bigR;
            sr += r;
            const c = sr % 2 === 0 ? bigC : 4 - bigC;
            sc += c;
            row = row * 5 + r;
            col = col * 5 + c;
        }
        return [row, col];
    }

    /**
     * At most 249 codes one typo away, in the order section 15.3 fixes.
     *
     * 240 substitutions, then the adjacent transpositions that actually change
     * the code. A code such as P4444PPPPP yields 242, and the list is never
     * padded back to 249 with duplicates.
     */
    private static candidates(code: string): string[] {
        const out: string[] = [];
        for (let position = 0; position < this.CODE_LENGTH; position++) {
            for (const character of this.ALPHABET) {
                if (character !== code[position]) {
                    out.push(code.slice(0, position) + character + code.slice(position + 1));
                }
            }
        }
        for (let position = 0; position < this.CODE_LENGTH - 1; position++) {
            if (code[position] !== code[position + 1]) {
                out.push(code.slice(0, position) + code[position + 1] + code[position] + code.slice(position + 2));
            }
        }
        return out;
    }

    /**
     * The 32-bit FNV-1a hash, eight lower-case hex characters. Section 17.3.
     *
     * Not a cryptographic hash, and section 17.1 says why it does not need to
     * be. Three integer operations per byte, over ASCII symbols, so all four
     * ports compute it identically with nothing imported -- which is what keeps
     * this package free of any import at all.
     */
    private static screenHash(text: string): string {
        let h = 2166136261;
        for (let i = 0; i < text.length; i++) {
            h = Math.imul(h ^ text.charCodeAt(i), 16777619);
        }
        return (h >>> 0).toString(16).padStart(8, '0');
    }

    /** One axis of section 19.1, in integers after the first line. */
    private static dmsAxis(value: number, positive: string, negative: string): string {
        const u = Math.floor(Math.abs(value) * 360000 + 0.5); // hundredths of a second
        const minutes = Math.floor(u / 6000) % 60;
        const seconds = u % 6000;
        return (
            Math.floor(u / 360000) +
            DEGREE_SIGN +
            String(minutes).padStart(2, '0') +
            "'" +
            String(Math.floor(seconds / 100)).padStart(2, '0') +
            '.' +
            String(u % 100).padStart(2, '0') +
            '"' +
            (value < 0 ? negative : positive)
        );
    }

    /** At most six decimal places, trailing zeros dropped. Section 19.2. */
    private static decimal6(value: number): string {
        const u = Math.floor(Math.abs(value) * 1000000 + 0.5);
        const sign = value < 0 && u !== 0 ? '-' : '';
        const fraction = String(u % 1000000)
            .padStart(6, '0')
            .replace(/0+$/, '');
        return sign + Math.floor(u / 1000000) + (fraction ? '.' + fraction : '');
    }

    /** RFC 5870 num: an optional minus, digits, optionally more digits. */
    private static geoNumber(text: string): number {
        const body = text.startsWith('-') ? text.slice(1) : text;
        const dot = body.indexOf('.');
        const whole = dot < 0 ? body : body.slice(0, dot);
        const fraction = dot < 0 ? '' : body.slice(dot + 1);
        if (!/^[0-9]+$/.test(whole)) throw new GPCError('GPC_GEO');
        if (dot >= 0 && !/^[0-9]+$/.test(fraction)) throw new GPCError('GPC_GEO');
        return Number(text);
    }
}

/**
 * A cursor over degrees-minutes-seconds text. Section 19.1.
 *
 * Small enough to keep the grammar readable, and deliberately strict: every
 * numeric piece carries its unit marker, so no accepted string has two
 * readings.
 */
class Scan {
    private static readonly WHITESPACE = ' \t\n\v\f\r';
    private static readonly DIGITS = '0123456789';

    private readonly text: string;
    private at: number = 0;

    public constructor(text: string) {
        if (text === null || text === undefined) throw new GPCError('GPC_NULL');
        this.text = text;
    }

    public done(): boolean {
        return this.at >= this.text.length;
    }

    /**
     * The character under the cursor, or '' at the end of the text.
     *
     * Every membership test on this has to check for the empty string first.
     * `'abc'.includes('')` is true, so `choices.includes(peek())` succeeds at
     * the end of the text, which is the opposite of what it reads as.
     */
    public peek(): string {
        return this.done() ? '' : this.text[this.at];
    }

    public take(): string {
        this.at += 1;
        return this.text[this.at - 1];
    }

    public spaces(): void {
        while (!this.done() && Scan.WHITESPACE.includes(this.text[this.at])) this.at += 1;
    }

    private digit(): boolean {
        return !this.done() && Scan.DIGITS.includes(this.text[this.at]);
    }

    private marker(choices: string): void {
        this.spaces();
        const character = this.peek();
        if (character === '' || !choices.includes(character)) throw new GPCError('GPC_DMS');
        this.take();
    }

    private digits(): number {
        const start = this.at;
        while (this.digit()) this.at += 1;
        if (this.at === start) throw new GPCError('GPC_DMS');
        return Number(this.text.slice(start, this.at));
    }

    private number(): number {
        const start = this.at;
        while (this.digit()) this.at += 1;
        if (!this.done() && this.text[this.at] === '.') {
            this.at += 1;
            while (this.digit()) this.at += 1;
        }
        const body = this.text.slice(start, this.at);
        if (body === '' || body === '.') throw new GPCError('GPC_DMS');
        return Number(body);
    }

    /** One axis: [sign] degrees marker [minutes marker [seconds marker]]. */
    public axis(isLatitude: boolean): number {
        this.spaces();
        const signed = this.peek() !== '' && '+-'.includes(this.peek());
        let sign = signed && this.take() === '-' ? -1 : 1;

        this.spaces();
        const degrees = this.digits();
        this.marker(DEGREE_SIGN + 'dD');

        let minutes = 0;
        let seconds = 0;
        let save = this.at;
        this.spaces();
        if (this.digit()) {
            minutes = this.digits();
            this.marker("'mM");
            if (minutes >= 60) throw new GPCError('GPC_DMS');
            save = this.at;
            this.spaces();
            if (this.digit()) {
                seconds = this.number();
                this.marker('"sS');
                if (seconds >= 60) throw new GPCError('GPC_DMS');
            } else {
                this.at = save;
            }
        } else {
            this.at = save;
        }

        this.spaces();
        const letter = this.peek().toUpperCase();
        if (letter !== '' && 'NSEW'.includes(letter)) {
            this.take();
            if (signed) throw new GPCError('GPC_DMS'); // a sign and a hemisphere both
            if ('NS'.includes(letter) !== isLatitude) throw new GPCError('GPC_DMS'); // the wrong axis
            if ('SW'.includes(letter)) sign = -1;
        }

        return sign * (degrees + (minutes + seconds / 60) / 60);
    }
}
