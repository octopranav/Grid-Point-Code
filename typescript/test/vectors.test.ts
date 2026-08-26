// Copyright 2017 Pranavkumar Patel
// Licensed under the Apache License, Version 2.0

// Runs the shared conformance vectors in test_data/. Every port reads these
// same files, so a disagreement between languages shows up here rather than in
// a release. The v2_ files hold version 2; the rest are version 1, and are
// asserted by decoding, because no package encodes version 1 any more.

import { expect } from 'chai';
import * as crypto from 'crypto';
import * as fs from 'fs';
import * as path from 'path';
import { GPC } from '../src/GPC';
import * as ScreenList from '../src/ScreenList';

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

    it('recovers every short form against its reference', () => {
        const data = rows('v2_short.csv', 4);
        expect(data.length).to.be.greaterThan(100);
        for (const [short, latitude, longitude, expected] of data) {
            expect(GPC.recoverShort(short, Number(latitude), Number(longitude), false), short).to.equal(expected);
            expect(GPC.shorten(expected)).to.equal(short);
        }
    });

    it('suggests the same corrections in the same order', () => {
        const data = rows('v2_corrections.csv', 5);
        expect(data.length).to.be.greaterThan(10);
        for (const [level, latitude, longitude, typo, candidates] of data) {
            expect(
                GPC.suggestCorrections(typo, Number(latitude), Number(longitude), Number(level), false),
                typo,
            ).to.deep.equal(candidates === '' ? [] : candidates.split(' '));
        }
    });

    it('takes the expected cell and neighbours at every level', () => {
        const data = rows('v2_cells.csv', 4);
        expect(data.length).to.be.greaterThan(50);
        for (const [level, code, expectedCell, neighbours] of data) {
            const cell = GPC.cell(code, Number(level));
            expect(cell, `${code} at ${level}`).to.equal(expectedCell);
            expect(GPC.neighbours(cell), cell).to.deep.equal(neighbours === '' ? [] : neighbours.split(' '));
            expect(GPC.contains(cell, code)).to.equal(true);
        }
    });

    it('converts to and from the integer form', () => {
        const data = rows('v2_integer.csv', 2);
        expect(data.length).to.be.greaterThan(50);
        for (const [code, value] of data) {
            expect(GPC.toInteger(code), code).to.equal(Number(value));
            expect(GPC.fromInteger(Number(value), false), value).to.equal(code);
        }
    });

    // The one file compared to a tolerance. See SPEC.md 18.5: no standard
    // library rounds sine, cosine or arc sine correctly, so asserting equality
    // here would pass on one machine and fail on the next.
    it('measures every distance to within a millimetre', () => {
        const data = rows('v2_distance.csv', 3);
        expect(data.length).to.be.greaterThan(10);
        for (const [a, b, metres] of data) {
            expect(GPC.distance(a, b), `${a} to ${b}`).to.be.closeTo(Number(metres), 0.001);
        }
    });

    it('writes and reads every geo URI', () => {
        const data = rows('v2_geo.csv', 3);
        expect(data.length).to.be.greaterThan(50);
        for (const [latitude, longitude, uri] of data) {
            expect(GPC.toGeoURI(Number(latitude), Number(longitude)), uri).to.equal(uri);
            // Six decimal places, so a coordinate carrying more comes back
            // rounded. Everything decode produces already has six, which is why
            // the round trip through a code is exact; that is asserted in the
            // unit suite rather than here.
            const [backLatitude, backLongitude] = GPC.fromGeoURI(uri);
            expect(Math.abs(backLatitude - Number(latitude))).to.be.lessThan(5e-7);
            expect(Math.abs(backLongitude - Number(longitude))).to.be.lessThan(5e-7);
        }
    });

    it('writes and reads every degrees-minutes-seconds form', () => {
        const data = rows('v2_dms.csv', 3);
        expect(data.length).to.be.greaterThan(50);
        for (const [latitude, longitude, dms] of data) {
            expect(GPC.toDMS(Number(latitude), Number(longitude)), dms).to.equal(dms);
            // Lossy by a hundredth of a second, so the coordinates come back
            // near rather than equal.
            const [backLatitude, backLongitude] = GPC.fromDMS(dms);
            expect(Math.abs(backLatitude - Number(latitude))).to.be.lessThan(0.5 / 360000 + 1e-12);
            expect(Math.abs(backLongitude - Number(longitude))).to.be.lessThan(0.5 / 360000 + 1e-12);
        }
    });

    it('carries the same advisory list as every other port', () => {
        const data = rows('v2_screen_list.csv', 3);
        expect(data.length).to.equal(1);
        const [version, count, digest] = data[0];
        const entries = [...ScreenList.ENTRIES].sort();
        expect(entries.length).to.equal(Number(count));
        expect(ScreenList.VERSION).to.equal(version);
        expect(crypto.createHash('sha256').update(entries.join('\n'), 'utf8').digest('hex')).to.equal(digest);
    });

    it('screens every vector to the expected spans', () => {
        const data = rows('v2_screen.csv', 2);
        expect(data.length).to.be.greaterThan(10);
        let matched = 0;
        for (const [code, spans] of data) {
            const expected =
                spans === '' ? [] : spans.split(' ').map((span) => span.split(':').map(Number) as [number, number]);
            const [version, got] = GPC.screen(code);
            expect(got, code).to.deep.equal(expected);
            // The version comes back either way: a caller has to be able to tell
            // "clean under this list" from "never screened".
            expect(version).to.equal(ScreenList.VERSION);
            matched += expected.length > 0 ? 1 : 0;
        }
        expect(matched).to.be.greaterThan(0);
    });

    it('screens the formatted and bare forms alike', () => {
        // The probe comes from the corpus rather than from a literal, so that
        // replacing the advisory list never means editing four test suites --
        // and so that no code known to spell something sits in the source.
        const matching = rows('v2_screen.csv', 2).filter(([, spans]) => spans !== '');
        expect(matching.length).to.be.greaterThan(0);
        const code = matching[0][0];
        const formatted = '#' + code.slice(0, 5) + '-' + code.slice(5);
        expect(GPC.screen(code)[1]).to.not.deep.equal([]);
        expect(GPC.screen(code)).to.deep.equal(GPC.screen(formatted));
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
