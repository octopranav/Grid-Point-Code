// Copyright 2017 Pranavkumar Patel
// Licensed under the Apache License, Version 2.0

// Properties that hold for every point, checked over a wide generated sample.
//
// The files in test_data/ pin behaviour case by case. This file pins the rules
// that must hold everywhere: a code is always ten characters, always spelled
// from the alphabet, always valid, and always decodes back inside the cell it
// came from. It also pins the two properties the whole format exists for --
// containment of a shared prefix, and continuity of the ordering.
//
// The sample behind them is a hundred thousand coordinates that are generated
// rather than stored, so the same inputs reach every port without a large file
// in the repository. Its definition lives in test_data/README.md; the digest of
// the codes it produces lives in test_data/v2_sample.csv, which is what makes
// this file a cross-port check as well as a local one.
//
// Every constant below is written out rather than read from the implementation.
// A test that borrows the constant it is checking proves nothing.

import { expect } from 'chai';
import * as crypto from 'crypto';
import * as fs from 'fs';
import * as path from 'path';
import { GPC } from '../src/GPC';

const ALPHABET = '0123456789CDFGHJKLMNPRTWX';
const CODE_LENGTH = 10;
const FORMATTED_LENGTH = 12;

// The grid of section 3.
const ROWS = 7_812_500; // 4 * 5^9
const COLS = 11_718_750; // 6 * 5^9

// Generator constants. Kept beside the code that uses them so this file reads
// as a standalone statement of the sample, the same way every other port does.
const MULTIPLIER = 1_664_525;
const INCREMENT = 1_013_904_223;
const MODULUS = 4_294_967_296; // 2^32
const LAT_SPAN = 18_000_001; // -90.00000 .. 90.00000 in units of 1e-5
const LONG_SPAN = 36_000_001; // -180.00000 .. 180.00000 in units of 1e-5

// 24 * 25^4 level-5 cells, so one fewer transition between them, out of
// 24 * 25^9 - 1 steps in all. That is the 99.99999 % of section 5.3.
const LEVEL_5_CELLS = 9_375_000;
const TOTAL_STEPS = 91_552_734_374_999;

/** Walk up from this file until the shared test_data directory appears. */
function testDataDir(): string {
    let dir = __dirname;
    for (;;) {
        const candidate = path.join(dir, 'test_data');
        if (fs.existsSync(candidate) && fs.statSync(candidate).isDirectory()) {
            return candidate;
        }
        const parent = path.dirname(dir);
        if (parent === dir) {
            throw new Error('test_data directory not found above ' + __dirname);
        }
        dir = parent;
    }
}

/** Read count, seed and expected digest from test_data/v2_sample.csv. */
function sampleSpec(): { count: number; seed: number; digest: string } {
    const file = path.join(testDataDir(), 'v2_sample.csv');
    for (const raw of fs.readFileSync(file, 'utf8').split('\n')) {
        const line = raw.trim();
        if (line === '' || line.startsWith('#')) continue;
        const [count, seed, digest] = line.split(',');
        return { count: Number(count), seed: Number(seed), digest };
    }
    throw new Error('no data row in ' + file);
}

/**
 * The shared sample. A linear congruential sequence whose products stay below
 * 2 ** 53, so every port walks it exactly, including this one, whose only
 * number is a double.
 */
function samplePoints(count: number, seed: number): [number, number][] {
    const points: [number, number][] = [];
    let state = seed;
    for (let i = 0; i < count; i++) {
        state = (MULTIPLIER * state + INCREMENT) % MODULUS;
        const latitude = ((state % LAT_SPAN) - (LAT_SPAN - 1) / 2) / 100000;
        state = (MULTIPLIER * state + INCREMENT) % MODULUS;
        const longitude = ((state % LONG_SPAN) - (LONG_SPAN - 1) / 2) / 100000;
        points.push([latitude, longitude]);
    }
    return points;
}

/** Section 5.1, restated. The row and column a coordinate falls in. */
function grid(latitude: number, longitude: number): [number, number] {
    if (longitude === 180.0) longitude = -180.0;
    const row = Math.floor(((latitude + 90.0) * 7812500.0) / 180.0);
    const col = Math.floor(((longitude + 180.0) * 11718750.0) / 360.0);
    return [Math.min(Math.max(row, 0), ROWS - 1), Math.min(Math.max(col, 0), COLS - 1)];
}

/** The next code in plain ASCII order, which is base-25 counting. */
function successor(code: string): string {
    const out = code.split('');
    for (let position = out.length - 1; position >= 0; position--) {
        const index = ALPHABET.indexOf(out[position]) + 1;
        if (index < ALPHABET.length) {
            out[position] = ALPHABET[index];
            return out.join('');
        }
        out[position] = ALPHABET[0];
    }
    throw new Error('ran off the end of the code space');
}

const spec = sampleSpec();
const points = samplePoints(spec.count, spec.seed);
const codes = points.map(([latitude, longitude]) => GPC.encode(latitude, longitude, false));

describe('Properties over the whole sample', () => {
    it('has a substantial sample', () => {
        expect(spec.count).to.be.at.least(100_000);
        expect(codes.length).to.equal(spec.count);
    });

    // The one assertion that fails when two ports stop agreeing.
    it('reproduces the digest every other port reproduces', () => {
        const digest = crypto.createHash('sha256').update(codes.join('\n'), 'utf8').digest('hex');
        expect(digest).to.equal(spec.digest);
    });

    it('gives every code the fixed length', () => {
        for (const code of codes) {
            if (code.length !== CODE_LENGTH) {
                expect.fail(`${code} is ${code.length} characters, not ${CODE_LENGTH}`);
            }
        }
    });

    it('spells every code from the alphabet', () => {
        for (const code of codes) {
            for (const character of code) {
                if (!ALPHABET.includes(character)) {
                    expect.fail(`${code} contains ${character}, outside the alphabet`);
                }
            }
        }
    });

    // Level 1 yields 24 indices, so the X-prefixed space is unreachable.
    it('never encodes into the reserved namespace', () => {
        for (const code of codes) {
            if (code[0] === 'X') expect.fail(`${code} was encoded but begins with X`);
        }
    });

    it('validates every code it produces', () => {
        for (const code of codes) {
            if (!GPC.isValid(code)) {
                expect.fail(`${code} came out of encode but failed validation: ${GPC.validate(code)[1]}`);
            }
        }
    });

    it('decodes inside the cell the point came from', () => {
        for (const code of codes) {
            const [south, west, north, east] = GPC.decodeToArea(code);
            const [latitude, longitude] = GPC.decode(code);
            if (latitude < south || latitude > north || longitude < west || longitude > east) {
                expect.fail(`${code} decoded outside its own area`);
            }
        }
    });

    it('round-trips every code unchanged', () => {
        for (const code of codes) {
            const [latitude, longitude] = GPC.decode(code);
            const again = GPC.encode(latitude, longitude, false);
            if (again !== code) expect.fail(`${code} re-encoded as ${again} after decoding`);
        }
    });

    it('formats a code as the unformatted one with separators', () => {
        for (let i = 0; i < 1000; i++) {
            const formatted = GPC.encode(points[i][0], points[i][1], true);
            expect(formatted.length).to.equal(FORMATTED_LENGTH);
            expect(formatted).to.equal(`#${codes[i].slice(0, 5)}-${codes[i].slice(5)}`);
        }
    });

    // Section 11.1. The alphabet is ASCII-ascending, so sorting codes as bytes
    // sorts them the way the grid is traversed.
    it('sorts as a string the way it sorts in space', () => {
        const sorted = codes.slice(0, 20_000).sort();
        for (let i = 1; i < sorted.length; i++) {
            expect(sorted[i - 1] <= sorted[i]).to.equal(true);
        }
    });
});

// Section 10. Two codes agree in their first k characters if and only if the
// points lie in the same level-k cell.
describe('Locality', () => {
    const localityPoints = points.slice(0, 20_000);
    const localityCodes = codes.slice(0, 20_000);

    it('gives one prefix to one cell, and one cell to one prefix', () => {
        const cells = new Map<string, string>();
        const byPrefix = new Map<string, string>();
        for (let i = 0; i < localityPoints.length; i++) {
            const [row, col] = grid(localityPoints[i][0], localityPoints[i][1]);
            for (let k = 1; k <= 10; k++) {
                const p = Math.pow(5, 10 - k);
                const key = `${k}:${Math.floor(row / p)}:${Math.floor(col / p)}`;
                const prefix = `${k}:${localityCodes[i].slice(0, k)}`;
                const seen = cells.get(key);
                if (seen === undefined) {
                    cells.set(key, prefix);
                } else {
                    expect(seen, `${key} named twice`).to.equal(prefix);
                }
                const named = byPrefix.get(prefix);
                if (named === undefined) {
                    byPrefix.set(prefix, key);
                } else {
                    expect(named, `${prefix} names two cells`).to.equal(key);
                }
            }
        }
    });

    it('keeps the box of a code inside its level-k cell', () => {
        for (let i = 0; i < 2000; i++) {
            const [row, col] = grid(localityPoints[i][0], localityPoints[i][1]);
            const [south, west, north, east] = GPC.decodeToArea(localityCodes[i]);
            for (let k = 1; k <= 10; k++) {
                const p = Math.pow(5, 10 - k);
                // The same expression shape section 6.3 uses, so when the cell
                // edge and the box edge coincide they are the identical double.
                const cellSouth = (Math.floor(row / p) * p * 180.0) / 7812500.0 - 90.0;
                const cellNorth = ((Math.floor(row / p) + 1) * p * 180.0) / 7812500.0 - 90.0;
                const cellWest = (Math.floor(col / p) * p * 360.0) / 11718750.0 - 180.0;
                const cellEast = ((Math.floor(col / p) + 1) * p * 360.0) / 11718750.0 - 180.0;
                expect(cellSouth <= south && north <= cellNorth, `${localityCodes[i]} k=${k}`).to.equal(true);
                expect(cellWest <= west && east <= cellEast, `${localityCodes[i]} k=${k}`).to.equal(true);
            }
        }
    });
});

// Section 11.2. Consecutive codes are adjacent cells, everywhere except at a
// level-5 boundary, and that is exactly where the reset of 5.3 puts the only
// discontinuities.
describe('Ordering', () => {
    it('counts the discontinuities the specification counts', () => {
        expect(24 * Math.pow(25, 4)).to.equal(LEVEL_5_CELLS);
        expect(LEVEL_5_CELLS - 1).to.equal(9_374_999);
        expect(24 * Math.pow(25, 9) - 1).to.equal(TOTAL_STEPS);
        const share = (TOTAL_STEPS - (LEVEL_5_CELLS - 1)) / TOTAL_STEPS;
        expect((share * 100).toFixed(5)).to.equal('99.99999');
    });

    // A transcription error anywhere in the reflection breaks this.
    it('puts consecutive codes in adjacent cells inside a level-5 cell', () => {
        for (const [latitude, longitude] of [
            [43.65, -79.38],
            [-33.8568, 151.2153],
            [0.0, 0.0],
            [64.1466, -21.9426],
            [-13.1631, -72.545],
            [23.0225, 72.5714],
        ]) {
            let code = GPC.encode(latitude, longitude, false);
            const prefix = code.slice(0, 5);
            let previous = grid(...GPC.decode(code));
            let walked = 0;
            for (let step = 0; step < 4000; step++) {
                code = successor(code);
                if (code.slice(0, 5) !== prefix) break;
                const current = grid(...GPC.decode(code));
                const distance = Math.abs(current[0] - previous[0]) + Math.abs(current[1] - previous[1]);
                expect(distance, code).to.equal(1);
                previous = current;
                walked++;
            }
            expect(walked).to.be.greaterThan(100);
        }
    });

    // The traversal of one cell ends at its far corner and the next begins at
    // its near corner, so the step between them is never adjacent.
    it('makes every level-5 transition a jump', () => {
        let tested = 0;
        for (const latitude of [-80.0, -40.0, -5.0, 5.0, 40.0, 80.0]) {
            for (const longitude of [-170.0, -100.0, -20.0, 20.0, 100.0, 170.0]) {
                const prefix = GPC.encode(latitude, longitude, false).slice(0, 5);
                const following = successor(prefix);
                if (following[0] === 'X') continue; // ran into the reserved namespace
                const last = grid(...GPC.decode(prefix + 'XXXXX'));
                const first = grid(...GPC.decode(following + '00000'));
                const distance = Math.abs(last[0] - first[0]) + Math.abs(last[1] - first[1]);
                expect(distance, prefix).to.not.equal(1);
                tested++;
            }
        }
        expect(tested).to.be.greaterThan(20);
    });
});
