// Copyright 2017 Pranavkumar Patel
// Licensed under the Apache License, Version 2.0

// Version 2, case by case, plus the version 1 codes that still have to resolve.
//
// The worked examples come from SPEC.md rather than from running the code, so a
// change in behaviour shows up as a failure here instead of quietly becoming
// the new expected value.

import { expect } from 'chai';
import { CodeClass, GPC } from '../src/GPC';
import { GPCError } from '../src/GPCError';

/** The reason code carried by whatever the given call throws. */
function reasonOf(call: () => unknown): string {
    try {
        call();
    } catch (error) {
        return (error as GPCError).reason;
    }
    return 'nothing was thrown';
}

describe('Encoding', () => {
    it('produces the worked examples of section 5.5', () => {
        const examples: [number, number, string][] = [
            [43.65, -79.38, '#G3RJM-98NM9'],
            [43.6426, -79.3871, '#G3RJM-0M6DX'],
            [23.0225, 72.5714, '#KDC8X-JM49X'],
            [-33.8568, 151.2153, '#6LK4X-NRP0R'],
            [-13.1631, -72.545, '#C8HKC-13C80'],
            [64.1466, -21.9426, '#RDX9R-TN19T'],
        ];
        for (const [latitude, longitude, expected] of examples) {
            expect(GPC.encode(latitude, longitude), `${latitude},${longitude}`).to.equal(expected);
        }
    });

    it('encodes the poles, which version 1 rejected', () => {
        expect(GPC.encode(90.0, 0.0)).to.equal('#P4444-PPPPP');
        expect(GPC.encode(-90.0, 0.0)).to.equal('#3PPPP-00000');
    });

    it('gives the antimeridian one code from either end', () => {
        expect(GPC.encode(0.0, -180.0)).to.equal('#F0000-00000');
        expect(GPC.encode(0.0, 180.0)).to.equal('#F0000-00000');
        // 179.99999999999999 is exactly 180.0 once stored as a double.
        expect(GPC.encode(0.0, 179.99999999999999)).to.equal('#F0000-00000');
    });

    it('treats negative zero as the same point', () => {
        expect(GPC.encode(0.0, 0.0)).to.equal('#JPPPP-00000');
        expect(GPC.encode(-0.0, -0.0)).to.equal('#JPPPP-00000');
        expect(GPC.encode(0.0, -0.0)).to.equal('#JPPPP-00000');
        expect(GPC.encode(-0.0, 0.0)).to.equal('#JPPPP-00000');
    });

    it('formats a code as the unformatted one with separators', () => {
        expect(GPC.encode(43.65, -79.38, false)).to.equal('G3RJM98NM9');
        expect(GPC.encode(43.65, -79.38, true)).to.equal('#G3RJM-98NM9');
        expect(GPC.formatGPC('G3RJM98NM9')).to.equal('#G3RJM-98NM9');
    });

    it('never produces a code beginning with X', () => {
        for (let latitude = -90; latitude <= 90; latitude += 5) {
            for (let longitude = -180; longitude <= 180; longitude += 5) {
                expect(GPC.encode(latitude, longitude, false)[0]).to.not.equal('X');
            }
        }
    });

    it('rejects anything outside the domain', () => {
        const cases: [number, number, string][] = [
            [90.00001, 0.0, 'LATITUDE'],
            [-90.00001, 0.0, 'LATITUDE'],
            [1000.0, 0.0, 'LATITUDE'],
            [0.0, 180.00001, 'LONGITUDE'],
            [0.0, -180.00001, 'LONGITUDE'],
            [0.0, 1000.0, 'LONGITUDE'],
            [NaN, 0.0, 'LATITUDE'],
            [Infinity, 0.0, 'LATITUDE'],
            [0.0, NaN, 'LONGITUDE'],
            [0.0, -Infinity, 'LONGITUDE'],
        ];
        for (const [latitude, longitude, reason] of cases) {
            expect(
                reasonOf(() => GPC.encode(latitude, longitude)),
                `${latitude},${longitude}`,
            ).to.equal(reason);
        }
    });

    it('includes the edges of the domain', () => {
        const edges: [number, number][] = [
            [90.0, 0.0],
            [-90.0, 0.0],
            [0.0, 180.0],
            [0.0, -180.0],
            [90.0, 180.0],
            [-90.0, -180.0],
        ];
        for (const [latitude, longitude] of edges) {
            expect(GPC.isValidCoordinates(latitude, longitude)).to.deep.equal([true, '']);
        }
    });
});

describe('Decoding', () => {
    it('produces the worked examples of section 6.4', () => {
        const examples: [string, [number, number]][] = [
            ['#G3RJM-98NM9', [43.650006, -79.380004]],
            ['#KDC8X-JM49X', [23.022501, 72.571407]],
            ['#6LK4X-NRP0R', [-33.856808, 151.215314]],
            ['#P4444-PPPPP', [89.999988, 0.000015]],
            ['#JPPPP-00000', [0.000012, 0.000015]],
        ];
        for (const [code, expected] of examples) {
            expect(GPC.decode(code), code).to.deep.equal(expected);
        }
    });

    it('returns the centre of the cell it names', () => {
        const [south, west, north, east] = GPC.decodeToArea('#G3RJM-98NM9');
        const [latitude, longitude] = GPC.decode('#G3RJM-98NM9');
        expect(south).to.be.lessThan(latitude);
        expect(latitude).to.be.lessThan(north);
        expect(west).to.be.lessThan(longitude);
        expect(longitude).to.be.lessThan(east);
    });

    // A box is a closed region, so it may name +90 and +180.
    it('reaches the edge of the world in the corner cells', () => {
        expect(GPC.decodeToArea('P4444PPPPP')[2]).to.equal(90.0);
        expect(GPC.decodeToArea('3PPPP00000')[0]).to.equal(-90.0);
        expect(GPC.decodeToArea('F000000000')[1]).to.equal(-180.0);
    });

    it('ignores case and separators', () => {
        const expected = GPC.decode('G3RJM98NM9');
        for (const form of ['#G3RJM-98NM9', 'g3rjm98nm9', '  G3RJM 98NM9  ', '#g3rjm-98nm9', '--G3RJM98NM9##']) {
            expect(GPC.decode(form), form).to.deep.equal(expected);
        }
    });

    it('carries a typed reason on every refusal', () => {
        const cases: [string, string][] = [
            ['XG3RJ98NM9', 'GPC_RESERVED'],
            ['', 'GPC_NULL'],
            ['   ', 'GPC_NULL'],
            ['G3RJM98NM', 'GPC_LENGTH'],
            ['G3RJM98NM999', 'GPC_LENGTH'],
            ['G3RJM98NMQ', 'GPC_CHAR'],
            ['G3RJM98NMU', 'GPC_CHAR'],
            ['G3RJM98NMY', 'GPC_CHAR'],
            ['#G3RJM-98NM9*5', 'GPC_CHECK'],
        ];
        for (const [code, reason] of cases) {
            expect(
                reasonOf(() => GPC.decode(code)),
                JSON.stringify(code),
            ).to.equal(reason);
        }
    });

    it('refuses the area of a reserved code as well', () => {
        expect(reasonOf(() => GPC.decodeToArea('XG3RJ98NM9'))).to.equal('GPC_RESERVED');
    });
});

describe('Parsing', () => {
    it('reads a confusable letter as the symbol it stands for', () => {
        const aliases: [string, string][] = [
            ['O', '0'],
            ['I', '1'],
            ['S', '5'],
            ['Z', '2'],
            ['B', '8'],
            ['A', '4'],
            ['E', '3'],
            ['V', 'W'],
        ];
        for (const [typed, meant] of aliases) {
            expect(GPC.decode('G3RJM98NM' + typed), typed).to.deep.equal(GPC.decode('G3RJM98NM' + meant));
        }
    });

    it('never reads L as 1', () => {
        expect(GPC.isValid('G3RJM98NML')).to.equal(true);
        expect(GPC.decode('G3RJM98NML')).to.not.deep.equal(GPC.decode('G3RJM98NM1'));
    });

    it('rejects U, Q and Y rather than aliasing them', () => {
        for (const character of ['U', 'Q', 'Y']) {
            expect(GPC.validate('G3RJM98NM' + character), character).to.deep.equal([CodeClass.INVALID, 'GPC_CHAR']);
        }
    });

    // Space, tab, line feed, vertical tab, form feed and carriage return, and
    // nothing wider. A port that also stripped the Unicode spaces would accept
    // what another port rejects, which is the whole thing the shared vectors
    // exist to prevent.
    it('strips the ASCII whitespace set and nothing wider', () => {
        const expected = GPC.decode('G3RJM98NM9');
        for (const space of [' ', '\t', '\n', '\v', '\f', '\r']) {
            expect(GPC.decode(space + 'G3RJM' + space + '98NM9' + space)).to.deep.equal(expected);
            expect(GPC.validate(space.repeat(3))).to.deep.equal([CodeClass.INVALID, 'GPC_NULL']);
        }
        // U+00A0 is a space to Unicode and a symbol outside this alphabet.
        expect(GPC.validate('\u00a03RJM98NM9')).to.deep.equal([CodeClass.INVALID, 'GPC_CHAR']);
    });

    it('is idempotent', () => {
        const [once] = GPC.normalise('#g3rjm-98nm9');
        const [twice] = GPC.normalise(once);
        expect(once).to.equal('G3RJM98NM9');
        expect(twice).to.equal(once);
    });
});

describe('Classification', () => {
    it('sorts a string into one of three classes', () => {
        expect(GPC.classify('#G3RJM-98NM9')).to.equal(CodeClass.GEOMETRIC);
        expect(GPC.classify('XG3RJ98NM9')).to.equal(CodeClass.RESERVED);
        expect(GPC.classify('nope')).to.equal(CodeClass.INVALID);
    });

    it('keeps a reserved code apart from a typing error', () => {
        expect(GPC.isValid('XXXXXXXXXX')).to.equal(false);
        expect(GPC.validate('XXXXXXXXXX')).to.deep.equal([CodeClass.RESERVED, '']);
    });

    it('tests the reasons in order', () => {
        expect(GPC.validate('')).to.deep.equal([CodeClass.INVALID, 'GPC_NULL']);
        expect(GPC.validate('Q')).to.deep.equal([CodeClass.INVALID, 'GPC_LENGTH']);
        expect(GPC.validate('QQQQQQQQQQ')).to.deep.equal([CodeClass.INVALID, 'GPC_CHAR']);
    });

    // classify describes this grid, and eleven characters are not in it.
    it('does not call a version 1 code a version 2 code', () => {
        expect(GPC.validate('#FN5G-CDKL-HDC')).to.deep.equal([CodeClass.INVALID, 'GPC_LENGTH']);
        expect(GPC.isValid('#FN5G-CDKL-HDC')).to.equal(false);
        expect(GPC.isValidV1('#FN5G-CDKL-HDC')).to.deep.equal([true, '']);
    });
});

describe('The check character', () => {
    it('produces the worked examples of section 14.5', () => {
        const examples: [string, string][] = [
            ['#G3RJM-98NM9', 'T'],
            ['#KDC8X-JM49X', 'D'],
            ['#P4444-PPPPP', '2'],
            ['#JPPPP-00000', 'M'],
        ];
        for (const [code, check] of examples) {
            expect(GPC.checkCharacter(code), code).to.equal(check);
        }
    });

    it('accepts and strips a correct check character', () => {
        expect(GPC.decode('#G3RJM-98NM9*T')).to.deep.equal(GPC.decode('#G3RJM-98NM9'));
        expect(GPC.isValid('#G3RJM-98NM9*T')).to.equal(true);
        expect(GPC.classify('#g3rjm-98nm9*t')).to.equal(CodeClass.GEOMETRIC);
    });

    // Never a silent ignore, and never valid-but-undecodable.
    it('fails a wrong check character everywhere', () => {
        for (const text of ['#G3RJM-98NM9*5', '#G3RJM-98NM9*', '#G3RJM-98NM9*TT', '#G3RJM-98NM9*Q']) {
            expect(GPC.validate(text), text).to.deep.equal([CodeClass.INVALID, 'GPC_CHECK']);
            expect(GPC.isValid(text), text).to.equal(false);
            expect(() => GPC.decode(text), text).to.throw();
        }
    });

    it('detects every single-symbol error', () => {
        const alphabet = '0123456789CDFGHJKLMNPRTWX';
        const code = 'G3RJM98NM9';
        const check = GPC.checkCharacter(code);
        for (let position = 0; position < 10; position++) {
            for (const symbol of alphabet) {
                if (symbol === code[position]) continue;
                const wrong = code.slice(0, position) + symbol + code.slice(position + 1);
                expect(GPC.validate(`${wrong}*${check}`), wrong).to.deep.equal([CodeClass.INVALID, 'GPC_CHECK']);
            }
        }
    });

    it('detects every adjacent transposition', () => {
        const code = 'G3RJM98NM9';
        const check = GPC.checkCharacter(code);
        for (let position = 0; position < 9; position++) {
            if (code[position] === code[position + 1]) continue;
            const swapped = code.slice(0, position) + code[position + 1] + code[position] + code.slice(position + 2);
            expect(GPC.validate(`${swapped}*${check}`), swapped).to.deep.equal([CodeClass.INVALID, 'GPC_CHECK']);
        }
    });

    it('builds the check form with withCheck', () => {
        const examples: [string, string][] = [
            ['#G3RJM-98NM9', '#G3RJM-98NM9*T'],
            ['#KDC8X-JM49X', '#KDC8X-JM49X*D'],
            ['#P4444-PPPPP', '#P4444-PPPPP*2'],
            ['#JPPPP-00000', '#JPPPP-00000*M'],
        ];
        for (const [code, form] of examples) {
            expect(GPC.withCheck(code), code).to.equal(form);
        }
    });

    it('honours the formatted flag on withCheck', () => {
        expect(GPC.withCheck('#G3RJM-98NM9', false)).to.equal('G3RJM98NM9*T');
    });

    it('accepts every form the parser does', () => {
        for (const text of ['#G3RJM-98NM9', 'G3RJM98NM9', 'g3rjm98nm9', '  G3RJM 98NM9  ']) {
            expect(GPC.withCheck(text), text).to.equal('#G3RJM-98NM9*T');
        }
    });

    it('recomputes rather than trusting a check character on the input', () => {
        for (const text of ['#G3RJM-98NM9*T', '#G3RJM-98NM9*5', '#G3RJM-98NM9*']) {
            expect(GPC.withCheck(text), text).to.equal('#G3RJM-98NM9*T');
        }
    });

    it('produces something that validates and decodes the same', () => {
        const code = GPC.encode(43.65, -79.38, false);
        expect(GPC.isValid(GPC.withCheck(code))).to.equal(true);
        expect(GPC.decode(GPC.withCheck(code))).to.deep.equal(GPC.decode(code));
    });

    it('gives a reserved code a check form', () => {
        expect(GPC.withCheck('XG3RJ98NM9')).to.equal('#XG3RJ-98NM9*6');
    });

    it('rejects what is not a code', () => {
        const cases: [string, string][] = [
            ['G3RJM98NM', 'GPC_LENGTH'],
            ['G3RJM98NM99', 'GPC_LENGTH'],
            ['G3RJM98NMQ', 'GPC_CHAR'],
            ['', 'GPC_NULL'],
        ];
        for (const [text, reason] of cases) {
            expect(() => GPC.withCheck(text), text)
                .to.throw(GPCError)
                .with.property('reason', reason);
        }
    });

    it('gives a reserved code a check character like any other', () => {
        expect(GPC.classify('XG3RJ98NM9*' + GPC.checkCharacter('XG3RJ98NM9'))).to.equal(CodeClass.RESERVED);
    });
});

describe('Version 1 codes', () => {
    it('dispatches on length', () => {
        expect(GPC.decode('#FN5G-CDKL-HDC')).to.deep.equal([43.65, -79.38]);
        expect(GPC.decode('#G3RJM-98NM9')).to.deep.equal([43.650006, -79.380004]);
    });

    it('agrees with the explicit entry point', () => {
        for (const code of ['#FN5G-CDKL-HDC', 'FN5GCDKLHDC', '#HG9K-PCVH-DPV']) {
            expect(GPC.decodeV1(code), code).to.deep.equal(GPC.decode(code));
        }
    });

    // Version 2 returns the centre. This difference is deliberate: the value is
    // the one every version 1 release has returned.
    it('returns the corner of its cell', () => {
        expect(GPC.decodeV1('DCCCCCCCCCC')).to.deep.equal([0.0, 0.0]);
        expect(GPC.decodeV1('HG9KPCVHDPV')).to.deep.equal([89.99999, 179.99999]);
        expect(GPC.decodeV1('HG9PJLHJX69')).to.deep.equal([-89.99999, -179.99999]);
    });

    it('reports validity with the expected reason', () => {
        const cases: [string, [boolean, string]][] = [
            ['#FN5G-CDKL-HDC', [true, '']],
            ['DCCCCCCCCCC', [true, '']],
            ['', [false, 'GPC_NULL']],
            ['   ', [false, 'GPC_NULL']],
            ['ABC', [false, 'GPC_LENGTH']],
            ['FN5GCDKLHDCC', [false, 'GPC_LENGTH']],
            ['FN5GCDKLHDA', [false, 'GPC_CHAR']],
            ['CCCCCCCCCCC', [false, 'GPC_RANGE']],
            ['YYYYYYYYYYY', [false, 'GPC_RANGE']],
        ];
        for (const [code, expected] of cases) {
            expect(GPC.isValidV1(code), JSON.stringify(code)).to.deep.equal(expected);
        }
    });

    // V and Y are version 1 symbols. Version 2 excludes both, reads V as W and
    // rejects Y outright, and none of that may reach this path.
    it('never lets the version 2 alias table touch a version 1 code', () => {
        expect(GPC.isValidV1('#HG9K-PCVH-DPV')).to.deep.equal([true, '']);
        expect(GPC.decode('#HG9K-PCVH-DPV')).to.deep.equal([89.99999, 179.99999]);
        expect(GPC.isValidV1('9999999999Y')).to.deep.equal([false, 'GPC_RANGE']);
    });

    // The dispatch is on length alone, so an eleven-character string that
    // happens to be a valid version 1 code decodes as one -- even when what the
    // caller meant was a version 2 code with a character too many. This is the
    // price of carrying both formats in one install, and it is why section 15.2
    // says to show the decoded point on a map before acting on it.
    it('reads eleven characters as version 1 whatever was meant', () => {
        expect(GPC.validate('G3RJM98NM99')).to.deep.equal([CodeClass.INVALID, 'GPC_LENGTH']);
        expect(GPC.isValidV1('G3RJM98NM99')).to.deep.equal([true, '']);
        expect(GPC.decode('G3RJM98NM99')).to.deep.equal(GPC.decodeV1('G3RJM98NM99'));
    });
});

describe('Cells and containment', () => {
    it('takes a cell as a prefix of the code', () => {
        expect(GPC.cell('#G3RJM-98NM9', 3)).to.equal('G3R');
        expect(GPC.cell('#G3RJM-98NM9', 5)).to.equal('G3RJM');
        expect(GPC.cell('#G3RJM-98NM9', 10)).to.equal('G3RJM98NM9');
    });

    it('normalises before slicing', () => {
        expect(GPC.cell('#g3rjm-i8nm9', 6)).to.equal('G3RJM1');
    });

    it('takes a cell of a cell', () => {
        expect(GPC.cell('G3RJM', 2)).to.equal('G3');
    });

    it('returns a cell bare, because ten characters is what a code looks like', () => {
        for (let level = 1; level <= 10; level++) {
            const cell = GPC.cell('#G3RJM-98NM9', level);
            expect(cell).to.not.contain('#');
            expect(cell).to.not.contain('-');
        }
    });

    it('refuses a level outside 1 to 10', () => {
        for (const level of [0, 11, -1, 100, 2.5]) {
            expect(
                reasonOf(() => GPC.cell('G3RJM98NM9', level)),
                String(level),
            ).to.equal('GPC_LEVEL');
        }
    });

    it('refuses a cell shorter than the level asked for', () => {
        expect(reasonOf(() => GPC.cell('G3R', 5))).to.equal('GPC_LENGTH');
    });

    it('refuses a reserved cell and says which reason', () => {
        for (const text of ['XG3RJ', 'XG3RJ98NM9']) {
            expect(
                reasonOf(() => GPC.cell(text, 3)),
                text,
            ).to.equal('GPC_RESERVED');
        }
    });

    it('answers containment with the prefix test', () => {
        expect(GPC.contains('G3RJM', 'G3RJM98NM9')).to.equal(true);
        expect(GPC.contains('G', 'G3RJM98NM9')).to.equal(true);
        expect(GPC.contains('G3RJD', 'G3RJM98NM9')).to.equal(false);
    });

    it('holds containment between cells, in one direction only', () => {
        expect(GPC.contains('G3R', 'G3RJM')).to.equal(true);
        expect(GPC.contains('G3RJM', 'G3R')).to.equal(false);
    });

    it('normalises both sides', () => {
        expect(GPC.contains('#g3rjm', '#G3RJM-98NM9')).to.equal(true);
    });
});

describe('Neighbours', () => {
    it('finds eight away from the poles', () => {
        expect(GPC.neighbours('G3RJM98NM9').length).to.equal(8);
        expect(GPC.neighbours('G3RJM').length).to.equal(8);
    });

    it('finds five in a polar row, absent rather than empty', () => {
        expect(GPC.neighbours('#P4444-PPPPP').length).to.equal(5);
        expect(GPC.neighbours('#3PPPP-00000').length).to.equal(5);
    });

    it('returns cells of the same length as the argument', () => {
        for (let level = 1; level <= 10; level++) {
            const cell = GPC.cell('#G3RJM-98NM9', level);
            for (const neighbour of GPC.neighbours(cell)) {
                expect(neighbour.length, cell).to.equal(level);
            }
        }
    });

    it('wraps columns at the antimeridian', () => {
        // The first column of the grid. Its western neighbour is the last
        // column, and no amount of string arithmetic would have found it: the
        // two share no characters at all.
        const first = GPC.encode(0.0, -180.0, false);
        const west = GPC.neighbours(first)[6];
        expect(west).to.equal(GPC.encode(0.0, 179.99999, false));
        expect(west[0]).to.not.equal(first[0]);
    });

    it('keeps the order fixed', () => {
        const [row, col] = GPC.decodeToGrid('G3RJM98NM9');
        const steps: [number, number][] = [
            [1, 0],
            [1, 1],
            [0, 1],
            [-1, 1],
            [-1, 0],
            [-1, -1],
            [0, -1],
            [1, -1],
        ];
        expect(GPC.neighbours('G3RJM98NM9')).to.deep.equal(
            steps.map(([dRow, dCol]) => GPC.gridToCode(row + dRow, col + dCol)),
        );
    });

    it('never includes the cell itself', () => {
        expect(GPC.neighbours('G3RJM')).to.not.contain('G3RJM');
    });
});

describe('Cell dimensions', () => {
    it('reproduces the table of section 3', () => {
        const table: [number, number, number][] = [
            [1, 5000.9, 6679.2],
            [2, 1000.2, 1335.8],
            [3, 200.0, 267.2],
            [4, 40.0, 53.4],
            [5, 8.0, 10.7],
        ];
        for (const [level, northSouth, eastWest] of table) {
            const dimensions = GPC.cellDimensions(level);
            expect(Number((dimensions[2] / 1000).toFixed(1)), `level ${level}`).to.equal(northSouth);
            expect(Number((dimensions[3] / 1000).toFixed(1)), `level ${level}`).to.equal(eastWest);
        }
    });

    it('measures a doorway at level 10', () => {
        const dimensions = GPC.cellDimensions(10);
        expect(Number(dimensions[2].toFixed(1))).to.equal(2.6);
        expect(Number(dimensions[3].toFixed(1))).to.equal(3.4);
    });

    it('keeps the aspect ratio at three quarters everywhere', () => {
        for (let level = 1; level <= 10; level++) {
            const [latitude, longitude] = GPC.cellDimensions(level);
            expect(Number((latitude / longitude).toFixed(12)), `level ${level}`).to.equal(0.75);
        }
    });

    it('refuses a level outside 1 to 10', () => {
        expect(reasonOf(() => GPC.cellDimensions(0))).to.equal('GPC_LEVEL');
    });
});

describe('Distance', () => {
    it('is zero from a cell to itself', () => {
        expect(GPC.distance('G3RJM98NM9', 'G3RJM98NM9')).to.equal(0);
    });

    it('is symmetric', () => {
        expect(GPC.distance('G3RJM98NM9', '6LK4XNRP0R')).to.equal(GPC.distance('6LK4XNRP0R', 'G3RJM98NM9'));
    });

    it('makes pole to pole half the meridian', () => {
        expect(Number((GPC.distance('#P4444-PPPPP', '#3PPPP-00000') / 1000).toFixed(1))).to.equal(20015.1);
    });

    it('does not produce a NaN for antipodal cells', () => {
        const metres = GPC.distance(GPC.encode(0.0, 0.0, false), GPC.encode(0.0, 180.0, false));
        expect(Number((metres / 1000).toFixed(1))).to.equal(20015.1);
    });

    it('accepts cells of different levels', () => {
        expect(GPC.distance('G3RJM', 'G3RJM98NM9')).to.be.lessThan(7000);
    });
});

describe('The short form', () => {
    it('is the second printed group', () => {
        expect(GPC.shorten('#G3RJM-98NM9')).to.equal('98NM9');
        expect(GPC.shorten('G3RJM98NM9')).to.equal('98NM9');
    });

    it('recovers with or without the leading dash', () => {
        for (const short of ['98NM9', '-98NM9', ' -98nm9 ']) {
            expect(GPC.recoverShort(short, 43.66, -79.39), short).to.equal('#G3RJM-98NM9');
        }
    });

    it('is exact within half a level-5 cell', () => {
        const code = GPC.encode(43.65, -79.38, false);
        const short = GPC.shorten(code);
        for (const dLatitude of [-0.0359, 0.0, 0.0359]) {
            for (const dLongitude of [-0.0479, 0.0, 0.0479]) {
                expect(
                    GPC.recoverShort(short, 43.65 + dLatitude, -79.38 + dLongitude, false),
                    `${dLatitude},${dLongitude}`,
                ).to.equal(code);
            }
        }
    });

    it('crosses the antimeridian', () => {
        // A reference east of the line recovering a code west of it. The column
        // arithmetic wraps; the row arithmetic must not.
        const code = GPC.encode(0.0, -179.99, false);
        expect(GPC.recoverShort(GPC.shorten(code), 0.0, 179.995, false)).to.equal(code);
    });

    it('refuses a short form that is not five symbols', () => {
        for (const short of ['98NM', '98NM99']) {
            expect(
                reasonOf(() => GPC.recoverShort(short, 43.65, -79.38)),
                short,
            ).to.equal('GPC_LENGTH');
        }
    });

    it('refuses a reference outside the domain', () => {
        expect(reasonOf(() => GPC.recoverShort('98NM9', 91.0, 0.0))).to.equal('LATITUDE');
    });
});

describe('Corrections', () => {
    it('finds the true code and ranks it first, whichever character was hit', () => {
        const code = GPC.encode(43.65, -79.38, false);
        for (let position = 0; position < 10; position++) {
            const wrong = code.slice(0, position) + (code[position] === '0' ? '1' : '0') + code.slice(position + 1);
            expect(GPC.suggestCorrections(wrong, 43.65, -79.38, 6, false)[0], wrong).to.equal(code);
        }
    });

    it('takes a code that decodes nowhere near the reference', () => {
        // The whole point: a code with a wrong character is what this is for.
        expect(GPC.suggestCorrections('03RJM98NM9', 43.65, -79.38, 6, false)).to.contain(
            GPC.encode(43.65, -79.38, false),
        );
    });

    it('never suggests a reserved code', () => {
        for (const candidate of GPC.suggestCorrections('XG3RJ98NM9', 43.65, -79.38, 4, false)) {
            expect(candidate[0]).to.not.equal('X');
        }
    });

    it('returns fewer candidates at a narrower level', () => {
        const wide = GPC.suggestCorrections('G3RJM98NM8', 43.65, -79.38, 4, false);
        const narrow = GPC.suggestCorrections('G3RJM98NM8', 43.65, -79.38, 8, false);
        expect(wide.length).to.be.greaterThan(narrow.length);
    });

    it('refuses a code that will not normalise to ten symbols', () => {
        expect(reasonOf(() => GPC.suggestCorrections('G3RJM98NM', 43.65, -79.38))).to.equal('GPC_LENGTH');
    });

    it('never pads the list back with duplicates', () => {
        // P4444PPPPP has adjacent repeats, so it yields 242 rather than 249.
        const every = GPC.suggestCorrections('P4444PPPPP', 90.0, 0.0, 1, false);
        expect(new Set(every).size).to.equal(every.length);
    });
});

describe('The integer form', () => {
    it('round-trips', () => {
        const code = GPC.encode(43.65, -79.38, false);
        expect(GPC.fromInteger(GPC.toInteger(code), false)).to.equal(code);
    });

    it('places the first and last codes at the ends of the range', () => {
        expect(GPC.toInteger('0000000000')).to.equal(0);
        expect(GPC.toInteger('XXXXXXXXXX')).to.equal(25 ** 10 - 1);
    });

    it('sorts the same way the strings do', () => {
        const codes: string[] = [];
        for (const latitude of [-80.0, -20.0, 0.0, 20.0, 80.0]) {
            for (const longitude of [-170.0, -60.0, 0.0, 60.0, 170.0]) {
                codes.push(GPC.encode(latitude, longitude, false));
            }
        }
        codes.sort();
        const values = codes.map((code) => GPC.toInteger(code));
        expect(values).to.deep.equal([...values].sort((a, b) => a - b));
    });

    it('puts the reserved namespace at the top of the range', () => {
        const floor = 24 * 25 ** 9;
        expect(GPC.toInteger('X000000000')).to.be.at.least(floor);
        expect(GPC.toInteger('W999999999')).to.be.lessThan(floor);
    });

    it('refuses a value outside the range', () => {
        for (const value of [-1, 25 ** 10, 1.5]) {
            expect(
                reasonOf(() => GPC.fromInteger(value)),
                String(value),
            ).to.equal('GPC_RANGE');
        }
    });
});

describe('Screening', () => {
    it('returns the version even when nothing matched', () => {
        const [version, spans] = GPC.screen('G3RJM98NM9');
        expect(version).to.not.equal('');
        expect(spans).to.deep.equal([]);
    });

    it('reports the span of a match', () => {
        const [version, spans] = GPC.screen('GN4T000000');
        expect(version).to.not.equal('');
        expect(spans).to.deep.equal([[1, 4]]);
    });

    it('screens a reserved code like any other', () => {
        expect(GPC.screen('XGN4T00000')[1]).to.deep.equal([[2, 4]]);
    });

    it('never blocks', () => {
        // Whatever the list says, the code still encodes, decodes and validates.
        expect(GPC.isValid('GN4T000000')).to.equal(true);
        expect(GPC.classify('GN4T000000')).to.equal('GEOMETRIC');
        const [latitude, longitude] = GPC.decode('GN4T000000');
        expect(GPC.encode(latitude, longitude, false)).to.equal('GN4T000000');
    });

    it('screens the formatted and bare forms alike', () => {
        expect(GPC.screen('GN4T000000')).to.deep.equal(GPC.screen('#GN4T0-00000'));
    });
});

describe('Batch and streaming', () => {
    it('encodes a batch', () => {
        expect(
            GPC.encodeAll(
                [
                    [43.65, -79.38],
                    [0.0, 0.0],
                ],
                false,
            ),
        ).to.deep.equal(['G3RJM98NM9', 'JPPPP00000']);
    });

    it('decodes a batch', () => {
        expect(GPC.decodeAll(['#G3RJM-98NM9'])).to.deep.equal([[43.650006, -79.380004]]);
    });

    it('streams lazily, so a bad row throws where it is reached', () => {
        const stream = GPC.encodeStream(
            [
                [43.65, -79.38],
                [91.0, 0.0],
            ],
            false,
        );
        expect(stream.next().value).to.equal('G3RJM98NM9');
        expect(reasonOf(() => stream.next())).to.equal('LATITUDE');
    });

    it('stops a batch at the first bad row', () => {
        expect(
            reasonOf(() =>
                GPC.encodeAll([
                    [43.65, -79.38],
                    [0.0, 181.0],
                ]),
            ),
        ).to.equal('LONGITUDE');
    });

    it('handles an empty sequence', () => {
        expect(GPC.encodeAll([])).to.deep.equal([]);
        expect(GPC.decodeAll([])).to.deep.equal([]);
    });
});

describe('Grid indices', () => {
    it('agrees with toGrid', () => {
        expect(GPC.decodeToGrid('#G3RJM-98NM9')).to.deep.equal(GPC.toGrid(43.65, -79.38));
    });

    it('reaches the corners of the grid', () => {
        expect(GPC.decodeToGrid(GPC.encode(-90.0, -180.0, false))).to.deep.equal([0, 0]);
        expect(GPC.decodeToGrid(GPC.encode(90.0, 179.99999, false))).to.deep.equal([7812499, 11718749]);
    });

    it('refuses a reserved code', () => {
        expect(reasonOf(() => GPC.decodeToGrid('XG3RJ98NM9'))).to.equal('GPC_RESERVED');
    });
});

describe('Coordinate conversions', () => {
    it('writes the worked example', () => {
        expect(GPC.toDMS(43.65, -79.38)).to.equal('43°39\'00.00"N, 79°22\'48.00"W');
    });

    it('does not treat negative zero as negative', () => {
        expect(GPC.toDMS(-0.0, -0.0)).to.equal('0°00\'00.00"N, 0°00\'00.00"E');
    });

    it('carries seconds into the next minute', () => {
        expect(GPC.toDMS(1.0 - 1e-9, 0.0)).to.equal('1°00\'00.00"N, 0°00\'00.00"E');
    });

    it('reads its own output back', () => {
        expect(GPC.fromDMS(GPC.toDMS(43.65, -79.38))).to.deep.equal([43.65, -79.38]);
    });

    it('accepts the wider forms', () => {
        expect(GPC.fromDMS('43d39m0s N 79d22m48s W')).to.deep.equal([43.65, -79.38]);
        expect(GPC.fromDMS('43°N 79°W')).to.deep.equal([43.0, -79.0]);
        expect(GPC.fromDMS('-43°, +79°')).to.deep.equal([-43.0, 79.0]);
    });

    it('refuses what the grammar does not accept', () => {
        const bad = [
            '43°39\'00.00"N', // one axis only
            '43 39', // no unit markers
            '-43°N, 79°W', // a sign and a hemisphere
            '43°W, 79°N', // the axes crossed
            "43°60'N, 0°0'E", // sixty minutes
            '43°39\'60.0"N, 0°0\'0"E', // sixty seconds
            '43°N, 79°W extra', // trailing text
        ];
        for (const text of bad) {
            expect(
                reasonOf(() => GPC.fromDMS(text)),
                text,
            ).to.equal('GPC_DMS');
        }
    });

    it('refuses a DMS value outside the domain', () => {
        expect(reasonOf(() => GPC.fromDMS('91°N, 0°E'))).to.equal('LATITUDE');
    });

    it('lets a decoded code survive the DMS round trip', () => {
        // decode returns a cell centre, which sits eight times further from the
        // nearest boundary than this rounding can move it.
        const points: [number, number][] = [
            [43.65, -79.38],
            [-33.8568, 151.2153],
            [90.0, 0.0],
            [-90.0, 0.0],
            [0.0, -180.0],
        ];
        for (const [latitude, longitude] of points) {
            const code = GPC.encode(latitude, longitude, false);
            const [back, backLong] = GPC.fromDMS(GPC.toDMS(...GPC.decode(code)));
            expect(GPC.encode(back, backLong, false), code).to.equal(code);
        }
    });

    it('writes a geo URI', () => {
        expect(GPC.toGeoURI(43.650006, -79.380004)).to.equal('geo:43.650006,-79.380004');
    });

    it('drops trailing zeros and the point with them', () => {
        expect(GPC.toGeoURI(43.65, -79.38)).to.equal('geo:43.65,-79.38');
        expect(GPC.toGeoURI(43.0, -79.0)).to.equal('geo:43,-79');
        expect(GPC.toGeoURI(-0.0, -0.0)).to.equal('geo:0,0');
    });

    it('reads its own URI back', () => {
        expect(GPC.fromGeoURI('geo:43.650006,-79.380004')).to.deep.equal([43.650006, -79.380004]);
    });

    it('drops the altitude and the parameters', () => {
        expect(GPC.fromGeoURI('geo:43.65,-79.38,76.1')).to.deep.equal([43.65, -79.38]);
        expect(GPC.fromGeoURI('geo:43.65,-79.38;u=35')).to.deep.equal([43.65, -79.38]);
        expect(GPC.fromGeoURI('GEO:43.65,-79.38;crs=WGS84')).to.deep.equal([43.65, -79.38]);
    });

    it('refuses another datum rather than ignoring it', () => {
        // Reading a code as though it were on another datum would put it in the
        // wrong place, quietly.
        expect(reasonOf(() => GPC.fromGeoURI('geo:43.65,-79.38;crs=nad83'))).to.equal('GPC_GEO');
    });

    it('refuses a URI the grammar does not accept', () => {
        for (const text of ['geo:43.65', '43.65,-79.38', 'geo:+43.65,-79.38', 'geo:43.65,-79.38,1,2', 'geo:1e2,0']) {
            expect(
                reasonOf(() => GPC.fromGeoURI(text)),
                text,
            ).to.equal('GPC_GEO');
        }
    });

    it('lets a decoded code survive the geo URI round trip', () => {
        const points: [number, number][] = [
            [43.65, -79.38],
            [-33.8568, 151.2153],
            [90.0, 0.0],
            [-90.0, 0.0],
            [0.0, -180.0],
        ];
        for (const [latitude, longitude] of points) {
            const code = GPC.encode(latitude, longitude, false);
            const [back, backLong] = GPC.fromGeoURI(GPC.toGeoURI(...GPC.decode(code)));
            expect(GPC.encode(back, backLong, false), code).to.equal(code);
        }
    });
});
