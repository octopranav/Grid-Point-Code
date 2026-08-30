// Everything a code can also be said as.
//
// Kept together and kept pure so the panel has one place to ask. Each form is a
// function of the code and its point and nothing else, which is what lets the
// component compute all of them in a single pass and blank all of them together
// -- the alternative, a separate update path per form, is how a panel ends up
// showing one answer beside a stale one.

import { GPC } from '@pranavpatel.ca/algo-gridpointcode';

export interface Form {
    key: string;
    /** What the row is called, and what it is for. */
    label: string;
    /** A word of caution, where one is owed. */
    caution?: string;
    of: (code: string, latitude: number, longitude: number) => string;
}

export const FORMS: Form[] = [
    {
        key: 'short',
        label: 'Short',
        // Section 12 of the specification: unique within its level-five cell and
        // recovered against a nearby reference. It is for a sign in a village or
        // two people standing together, never for a share button, which has no
        // idea what reference the far end will use.
        caution: 'local only',
        of: (code) => '-' + GPC.shorten(code),
    },
    {
        key: 'check',
        label: 'Check',
        caution: 'for voice and paper',
        of: (code) => GPC.withCheck(code),
    },
    {
        key: 'integer',
        label: 'Integer',
        caution: '48-bit, six bytes',
        of: (code) => GPC.toInteger(code).toLocaleString('en'),
    },
    {
        key: 'dms',
        label: 'Degrees, minutes, seconds',
        of: (_code, latitude, longitude) => GPC.toDMS(latitude, longitude),
    },
    {
        key: 'geo',
        label: 'Geo URI',
        of: (_code, latitude, longitude) => GPC.toGeoURI(latitude, longitude),
    },
];

// The words of the international radiotelephony spelling alphabet, which the
// specification prints as a reference in appendix D.2. They are not normative:
// the rule is that a callout is any word beginning with the symbol, and an
// application serving a particular region should use words its own users say.
const CALLOUTS: Record<string, string> = {
    C: 'Charlie', D: 'Delta', F: 'Foxtrot', G: 'Golf', H: 'Hotel',
    J: 'Juliett', K: 'Kilo', L: 'Lima', M: 'Mike', N: 'November',
    P: 'Papa', R: 'Romeo', T: 'Tango', W: 'Whiskey', X: 'X-ray',
};

// Digits are spoken as the number, because no word begins with a seven.
const NUMBERS = [
    'zero', 'one', 'two', 'three', 'four',
    'five', 'six', 'seven', 'eight', 'nine',
];

const callout = (symbol: string) => CALLOUTS[symbol] ?? NUMBERS[Number(symbol)];

/**
 * A code as it should be read aloud — which is always the check form.
 *
 * The alphabet excludes vowels so a code cannot spell a word, and excludes the
 * shapes a reader confuses on paper — but it was never chosen for phonetic
 * distinctness and cannot be now: said in English, C, D, G, P, T and the digit
 * three all rhyme. A listener who hears D where T was said writes down a code
 * that parses, validates, and decodes somewhere else.
 *
 * Callouts are what avoid that; the check character is what catches it when
 * they fail, and appendix D.1 puts one on anything dictated for exactly this
 * reason. So the check character is added here rather than expected from the
 * caller: a spoken line without it is the one case the specification names as
 * a mistake, and asking every call site to remember that is how it gets made.
 * `withCheck` is idempotent, so a code that already carries one is unharmed.
 *
 * Two details are the specification's and both matter to a listener who is
 * writing: the group boundary is worth a pause, and the check character is
 * named as one rather than read as an eleventh symbol, so they know where it
 * goes. Verified against the worked example in appendix D.2.
 */
export function aloud(code: string): string {
    const [payload, check] = GPC.normalise(GPC.withCheck(code));

    // Unreachable: the code was just put into the check form. Stated rather
    // than defaulted, because the only alternatives are a spoken line with the
    // check character silently missing or one with the word `undefined` in it,
    // and this is the form whose whole purpose is catching a wrong character.
    if (check === null) throw new Error('the check form has no check character');

    const head = [...payload.slice(0, 5)].map(callout).join(', ');
    const tail = [...payload.slice(5)].map(callout).join(', ');
    return head + ' — ' + tail + ' — check ' + callout(check);
}

/**
 * The eight cells around this one, clockwise from north.
 *
 * The library returns them in that order; this names it. Verified by decoding
 * each and comparing it to the centre, rather than trusted from documentation.
 */
export const COMPASS = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'] as const;

/** Where each direction sits in a three-by-three pad, reading left to right. */
export const PAD: (typeof COMPASS[number] | null)[] = [
    'NW', 'N', 'NE',
    'W', null, 'E',
    'SW', 'S', 'SE',
];
