// Copyright 2017 Pranavkumar Patel
// Licensed under the Apache License, Version 2.0

// Properties that hold for every point, checked over a wide generated sample.
//
// The files in test_data/ pin behaviour case by case. This file pins the rules
// that must hold everywhere: a code is always the same length, always spelled
// from the alphabet, always valid, and always decodes back inside the cell it
// came from.
//
// The sample behind them is a hundred thousand coordinates that are generated
// rather than stored, so the same inputs reach every port without a large file
// in the repository. Its definition lives in test_data/README.md; the digest of
// the codes it produces lives in test_data/sample.csv, which is what makes this
// file a cross-port check as well as a local one.

import { expect } from 'chai';
import * as crypto from 'crypto';
import * as fs from 'fs';
import * as path from 'path';
import { GPC } from '../src/GPC';

// The specified alphabet, written out rather than read from the implementation:
// a test that borrows the constant it is checking proves nothing.
const ALPHABET = 'CDFGHJKLMNPRTVWXY0123456789';
const CODE_LENGTH = 11;
const FORMATTED_LENGTH = 14;

// One cell is a hundred-thousandth of a degree on each axis.
const CELL = 1e-5;

// Generator constants. Kept beside the code that uses them so this file reads
// as a standalone statement of the sample, the same way every other port does.
const MULTIPLIER = 1664525;
const INCREMENT = 1013904223;
const MODULUS = 4294967296; // 2^32
const LAT_SPAN = 17999999;
const LONG_SPAN = 35999999;

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

/** Read count, seed and expected digest from test_data/sample.csv. */
function sampleSpec(): { count: number; seed: number; digest: string } {
    const file = path.join(testDataDir(), 'sample.csv');
    for (const raw of fs.readFileSync(file, 'utf8').split('\n')) {
        const line = raw.trim();
        if (line === '' || line.startsWith('#')) continue;
        const [count, seed, digest] = line.split(',');
        return { count: Number(count), seed: Number(seed), digest };
    }
    throw new Error('no data row in ' + file);
}

/**
 * Build the shared sample. A linear congruential sequence whose products stay
 * below 2 ** 53, so it is exact here as well as in the ports that have integers
 * wider than a double.
 */
function samplePoints(count: number, seed: number): [number, number][] {
    const points: [number, number][] = new Array(count);
    let state = seed;
    for (let i = 0; i < count; i++) {
        state = (MULTIPLIER * state + INCREMENT) % MODULUS;
        const latitude = ((state % LAT_SPAN) - (LAT_SPAN - 1) / 2) / 100000;
        state = (MULTIPLIER * state + INCREMENT) % MODULUS;
        const longitude = ((state % LONG_SPAN) - (LONG_SPAN - 1) / 2) / 100000;
        points[i] = [latitude, longitude];
    }
    return points;
}

describe('Properties over the wide sample', function () {
    // A hundred thousand encodes and decodes runs well past mocha's default.
    this.timeout(300000);

    const spec = sampleSpec();
    let points: [number, number][];
    let codes: string[];

    before(() => {
        points = samplePoints(spec.count, spec.seed);
        codes = points.map(([latitude, longitude]) => GPC.encode(latitude, longitude, false));
    });

    it('draws a substantial sample', () => {
        expect(spec.count).to.be.at.least(100000);
        expect(codes.length).to.equal(spec.count);
    });

    // The one assertion that fails when two ports stop agreeing.
    it('reproduces the digest every other port reproduces', () => {
        const joined = codes.join('\n');
        const digest = crypto.createHash('sha256').update(joined, 'utf8').digest('hex');
        expect(digest).to.equal(spec.digest);
    });

    it('gives every code the fixed length', () => {
        for (const code of codes) {
            if (code.length !== CODE_LENGTH) {
                throw new Error(`${code} is ${code.length} characters, not ${CODE_LENGTH}`);
            }
        }
    });

    it('spells every code from the alphabet', () => {
        for (const code of codes) {
            for (const character of code) {
                if (!ALPHABET.includes(character)) {
                    throw new Error(`${code} contains ${character}, outside the alphabet`);
                }
            }
        }
    });

    it('validates every code it produced', () => {
        for (const code of codes) {
            const [valid, message] = GPC.isValid(code);
            if (!valid) {
                throw new Error(`${code} came out of encode but failed validation: ${message}`);
            }
        }
    });

    it('decodes back inside the cell the point came from', () => {
        for (let i = 0; i < codes.length; i++) {
            const [latitude, longitude] = points[i];
            const [decodedLat, decodedLong] = GPC.decode(codes[i]);
            if (Math.abs(latitude - decodedLat) >= CELL || Math.abs(longitude - decodedLong) >= CELL) {
                throw new Error(
                    `${codes[i]} decoded to (${decodedLat}, ${decodedLong}), ` +
                        `more than one cell from (${latitude}, ${longitude})`,
                );
            }
        }
    });

    it('round-trips every code unchanged', () => {
        for (const code of codes) {
            const [decodedLat, decodedLong] = GPC.decode(code);
            const again = GPC.encode(decodedLat, decodedLong, false);
            if (again !== code) {
                throw new Error(`${code} re-encoded as ${again} after decoding`);
            }
        }
    });

    it('formats the code by adding separators and nothing else', () => {
        for (let i = 0; i < 1000; i++) {
            const [latitude, longitude] = points[i];
            const formatted = GPC.encode(latitude, longitude, true);
            const code = codes[i];
            expect(formatted.length).to.equal(FORMATTED_LENGTH);
            expect(formatted).to.equal(`#${code.slice(0, 4)}-${code.slice(4, 8)}-${code.slice(8, 11)}`);
        }
    });
});
