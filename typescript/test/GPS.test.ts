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
