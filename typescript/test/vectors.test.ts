// Copyright 2017 Pranavkumar Patel
// Licensed under the Apache License, Version 2.0

// Runs the shared conformance vectors in test_data/. Every port reads these
// same files, so a disagreement between languages shows up here rather than in
// a release.

import { expect } from 'chai';
import * as fs from 'fs';
import * as path from 'path';
import { GPC } from '../src/GPC';

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

/** Rebuild the formatted #XXXX-XXXX-XXX form of an unformatted code. */
function formatted(code: string): string {
    return `#${code.slice(0, 4)}-${code.slice(4, 8)}-${code.slice(8, 11)}`;
}

describe('Shared conformance vectors', () => {
    it('encodes every vector to the expected code', () => {
        const data = rows('encoding.csv', 3);
        expect(data.length).to.be.greaterThan(100);
        for (const [lat, lng, expected] of data) {
            expect(GPC.encode(Number(lat), Number(lng), false),
                `encode(${lat}, ${lng})`).to.equal(expected);
        }
    });

    it('decodes every vector to the expected coordinates', () => {
        const data = rows('decoding.csv', 3);
        expect(data.length).to.be.greaterThan(100);
        for (const [code, lat, lng] of data) {
            const [gotLat, gotLng] = GPC.decode(code);
            expect(gotLat, `decode(${code}) latitude`).to.equal(Number(lat));
            expect(gotLng, `decode(${code}) longitude`).to.equal(Number(lng));
        }
    });

    it('decodes the formatted and unformatted forms alike', () => {
        for (const [code, lat, lng] of rows('decoding.csv', 3)) {
            const [gotLat, gotLng] = GPC.decode(formatted(code));
            expect(gotLat, `decode(${formatted(code)}) latitude`).to.equal(Number(lat));
            expect(gotLng, `decode(${formatted(code)}) longitude`).to.equal(Number(lng));
        }
    });

    it('round-trips every encoded code back to itself', () => {
        for (const [, , code] of rows('encoding.csv', 3)) {
            const [lat, lng] = GPC.decode(code);
            expect(GPC.encode(lat, lng, false), `round trip ${code}`).to.equal(code);
        }
    });

    it('agrees on code validity', () => {
        const data = rows('validity_codes.csv', 3);
        expect(data.length).to.be.greaterThan(10);
        for (const [expectedValid, expectedMessage, code] of data) {
            const [valid, message] = GPC.isValid(code);
            expect(valid, `isValid(${JSON.stringify(code)})`).to.equal(expectedValid === 'true');
            expect(message, `isValid(${JSON.stringify(code)}) message`).to.equal(expectedMessage);
        }
    });

    it('throws when decoding an invalid code', () => {
        for (const [expectedValid, , code] of rows('validity_codes.csv', 3)) {
            if (expectedValid === 'true') continue;
            expect(() => GPC.decode(code), `decode(${JSON.stringify(code)})`).to.throw();
        }
    });

    it('agrees on coordinate validity', () => {
        const data = rows('validity_coordinates.csv', 4);
        expect(data.length).to.be.greaterThan(10);
        for (const [lat, lng, expectedValid, expectedMessage] of data) {
            const [valid, message] = GPC.isValidCoordinates(Number(lat), Number(lng));
            expect(valid, `isValidCoordinates(${lat}, ${lng})`).to.equal(expectedValid === 'true');
            expect(message, `isValidCoordinates(${lat}, ${lng}) message`).to.equal(expectedMessage);
        }
    });

    it('throws when encoding an out-of-range coordinate', () => {
        for (const [lat, lng, expectedValid] of rows('validity_coordinates.csv', 4)) {
            if (expectedValid === 'true') continue;
            expect(() => GPC.encode(Number(lat), Number(lng)),
                `encode(${lat}, ${lng})`).to.throw();
        }
    });
});
