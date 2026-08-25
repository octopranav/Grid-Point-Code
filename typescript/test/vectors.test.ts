// Copyright 2017 Pranavkumar Patel
// Licensed under the Apache License, Version 2.0

// Runs the shared conformance vectors in test_data/. Every port reads these
// same files, so a disagreement between languages shows up here rather than in
// a release. The v2_ files hold version 2; the rest are version 1, and are
// asserted by decoding, because no package encodes version 1 any more.

import { expect } from 'chai';
import * as fs from 'fs';
import * as path from 'path';
import { GPC } from '../src/GPC';

// One cell of the version 1 grid: a hundred-thousandth of a degree on each axis.
const V1_CELL = 1e-5;

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

/**
 * Read one vector file, dropping comments and blank lines. Splits on the first
 * `fields - 1` commas so the final column keeps any comma, '#' or spacing.
 */
function rows(name: string, fields: number): string[][] {
    const text = fs.readFileSync(path.join(testDataDir(), name), 'utf8');
    const out: string[][] = [];
    for (const raw of text.split('\n')) {
        const line = raw.replace(/\r$/, '');
        if (line.trim() === '' || line.startsWith('#')) continue;
        const parts: string[] = [];
        let rest = line;
        for (let i = 0; i < fields - 1; i++) {
            const at = rest.indexOf(',');
            parts.push(rest.slice(0, at));
            rest = rest.slice(at + 1);
        }
        parts.push(rest);
        out.push(parts);
    }
    return out;
}

/** Rebuild the formatted #XXXX-XXXX-XXX form of an unformatted version 1 code. */
function formattedV1(code: string): string {
    return `#${code.slice(0, 4)}-${code.slice(4, 8)}-${code.slice(8, 11)}`;
}

describe('Version 2 conformance vectors', () => {
    it('encodes every vector to the expected code', () => {
        const data = rows('v2_encoding.csv', 3);
        expect(data.length).to.be.greaterThan(100);
        for (const [latitude, longitude, expected] of data) {
            expect(GPC.encode(Number(latitude), Number(longitude), false), `${latitude},${longitude}`).to.equal(
                expected,
            );
        }
    });

    it('decodes every vector exactly', () => {
        const data = rows('v2_decoding.csv', 3);
        expect(data.length).to.be.greaterThan(100);
        for (const [code, latitude, longitude] of data) {
            expect(GPC.decode(code), code).to.deep.equal([Number(latitude), Number(longitude)]);
        }
    });

    it('decodes the formatted and unformatted forms alike', () => {
        for (const [code, latitude, longitude] of rows('v2_decoding.csv', 3)) {
            expect(GPC.decode(GPC.formatGPC(code)), code).to.deep.equal([Number(latitude), Number(longitude)]);
        }
    });

    it('round-trips every encoded vector', () => {
        for (const [, , code] of rows('v2_encoding.csv', 3)) {
            const [latitude, longitude] = GPC.decode(code);
            expect(GPC.encode(latitude, longitude, false), code).to.equal(code);
        }
    });

    it('returns the expected cell boundaries', () => {
        const data = rows('v2_area.csv', 5);
        expect(data.length).to.be.greaterThan(100);
        for (const [code, south, west, north, east] of data) {
            expect(GPC.decodeToArea(code), code).to.deep.equal([
                Number(south),
                Number(west),
                Number(north),
                Number(east),
            ]);
        }
    });

    it('classifies every vector, with the expected reason', () => {
        const data = rows('v2_classify.csv', 3);
        expect(data.length).to.be.greaterThan(10);
        for (const [expectedClass, expectedMessage, text] of data) {
            expect(GPC.validate(text), JSON.stringify(text)).to.deep.equal([expectedClass, expectedMessage]);
            expect(GPC.classify(text), JSON.stringify(text)).to.equal(expectedClass);
            expect(GPC.isValid(text), JSON.stringify(text)).to.equal(expectedClass === 'GEOMETRIC');
        }
    });

    it('throws on anything that is not geometric', () => {
        for (const [expectedClass, , text] of rows('v2_classify.csv', 3)) {
            if (expectedClass === 'GEOMETRIC') continue;
            // Eleven characters is version 1 by definition, so decode reads it
            // rather than refusing it. classify describes the version 2 grid,
            // which this string is not part of.
            if (GPC.isValidV1(text)[0]) continue;
            expect(() => GPC.decode(text), JSON.stringify(text)).to.throw();
        }
    });

    it('gives a reserved code its own reason', () => {
        let seen = 0;
        for (const [expectedClass, , text] of rows('v2_classify.csv', 3)) {
            if (expectedClass !== 'RESERVED') continue;
            seen++;
            expect(() => GPC.decode(text), JSON.stringify(text)).to.throw(/GPC_RESERVED/);
        }
        expect(seen).to.be.greaterThan(0);
    });

    it('computes the expected check character', () => {
        const data = rows('v2_check.csv', 2);
        expect(data.length).to.be.greaterThan(10);
        for (const [code, check] of data) {
            expect(GPC.checkCharacter(code), code).to.equal(check);
            expect(GPC.classify(`${code}*${check}`), code).to.equal(GPC.classify(code));
        }
    });
});

describe('Version 1 conformance vectors', () => {
    it('decodes every vector exactly', () => {
        const data = rows('decoding.csv', 3);
        expect(data.length).to.be.greaterThan(100);
        for (const [code, latitude, longitude] of data) {
            expect(GPC.decodeV1(code), code).to.deep.equal([Number(latitude), Number(longitude)]);
        }
    });

    it('decodes the formatted and unformatted forms alike', () => {
        for (const [code, latitude, longitude] of rows('decoding.csv', 3)) {
            expect(GPC.decodeV1(formattedV1(code)), code).to.deep.equal([Number(latitude), Number(longitude)]);
        }
    });

    // encoding.csv was built by the version 1 encoder, which no longer ships.
    // What survives is the containment: the code names the cell the coordinate
    // falls in, so decoding lands within one cell of it.
    it('decodes every code inside the cell it was made from', () => {
        const data = rows('encoding.csv', 3);
        expect(data.length).to.be.greaterThan(100);
        for (const [latitude, longitude, code] of data) {
            const [decodedLat, decodedLong] = GPC.decodeV1(code);
            expect(Math.abs(Number(latitude) - decodedLat), code).to.be.lessThan(V1_CELL);
            expect(Math.abs(Number(longitude) - decodedLong), code).to.be.lessThan(V1_CELL);
        }
    });

    it('reports validity with the expected reason', () => {
        const data = rows('validity_codes.csv', 3);
        expect(data.length).to.be.greaterThan(10);
        for (const [expectedValid, expectedMessage, code] of data) {
            expect(GPC.isValidV1(code), JSON.stringify(code)).to.deep.equal([
                expectedValid === 'true',
                expectedMessage,
            ]);
        }
    });

    it('throws when decoding an invalid code', () => {
        for (const [expectedValid, , code] of rows('validity_codes.csv', 3)) {
            if (expectedValid === 'true') continue;
            expect(() => GPC.decodeV1(code), JSON.stringify(code)).to.throw();
        }
    });
});
