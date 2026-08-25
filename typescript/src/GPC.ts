// Copyright 2017 Pranavkumar Patel
// Licensed under the Apache License, Version 2.0

import { GPCError } from './GPCError';
import { V1 } from './V1';

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

    /*  PART 4 : INTERNALS */

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
}
