// The one page that names other systems, and the evidence behind every line.
//
// The repository rule is that other systems are not named in its files. This is
// the single scoped exception, because a reader who does not already know the
// field cannot evaluate "twenty-two times finer" with nothing on the other side
// of it.
//
// Two rules follow from that, and they are the whole reason this file exists
// rather than a table written straight into the page.
//
// **Every claim about another system is sourced to that system's own
// documentation**, and the source travels with the claim so a reader can check
// it. Nothing here is from memory or from a third party's summary.
//
// **The dimensions include the ones this format loses.** A comparison table is
// not neutral: choosing the axes decides the winner, and picking only the axes
// where you come out ahead is disparagement by arrangement even when every
// individual fact is true. Adoption, ecosystem and length in speech all go the
// other way, and they are in the table.

export interface Source {
    label: string;
    url: string;
}

export interface System {
    id: string;
    name: string;
    /** What kind of thing it is, in its own terms. */
    kind: string;
    /** Where the facts below come from. */
    source: Source;
}

export const SYSTEMS: System[] = [
    {
        id: 'gpc',
        name: 'Grid Point Code',
        kind: 'Coordinate encoding',
        source: {
            label: 'This site\'s specification',
            url: '/spec',
        },
    },
    {
        id: 'digipin',
        name: 'DIGIPIN',
        kind: 'Coordinate encoding',
        source: {
            label: 'DIGIPIN Technical Document, Department of Posts, March 2025',
            url: 'https://www.indiapost.gov.in/documents/offerings/intiatives/DIGIPIN_Technical_document.pdf',
        },
    },
    {
        id: 'olc',
        name: 'Open Location Code',
        kind: 'Coordinate encoding',
        source: {
            label: 'Open Location Code specification',
            url: 'https://github.com/google/open-location-code/blob/main/Documentation/Specification/specification.md',
        },
    },
    {
        id: 'geohash',
        name: 'Geohash',
        kind: 'Coordinate encoding',
        source: {
            label: 'Geohash, Wikipedia',
            url: 'https://en.wikipedia.org/wiki/Geohash',
        },
    },
    {
        id: 'w3w',
        name: 'what3words',
        kind: 'Word-based address',
        source: {
            label: 'what3words, About us',
            url: 'https://what3words.com/about-us/',
        },
    },
];

export interface Row {
    /** The question, put the same way to every system. */
    dimension: string;
    /** Why a reader should care, and where the answer is not flattering, why. */
    note?: string;
    /** Keyed by system id. `null` means the source does not say. */
    values: Record<string, string | null>;
}

export const ROWS: Row[] = [
    {
        dimension: 'What the address is',
        values: {
            gpc: 'Ten characters, always ten',
            digipin: 'Ten characters, always ten',
            olc: 'Ten digits by default, eleven for a finer cell, plus a "+"',
            geohash: 'As many characters as the precision needs',
            w3w: 'Three words',
        },
    },
    {
        dimension: 'Area it names',
        note:
            'The figure that started this page. Against Open Location Code\'s default ten '
            + 'digits a cell here is about twenty-two times smaller, but that is a '
            + 'comparison of defaults, not of capability. At eleven digits the two are '
            + 'within about a tenth of each other, and saying only the first number would '
            + 'be picking the flattering half.',
        values: {
            gpc: '8.8 m², 2.56 by 3.42 m at the equator',
            digipin: '14 m², 3.8 by 3.8 m; its document notes this varies with latitude',
            olc: '193 m² at ten digits; 9.8 m² at eleven',
            geohash: 'Varies with length; ±0.61 km at six characters',
            w3w: '9 m², 3 by 3 m',
        },
    },
    {
        dimension: 'Where it works',
        note:
            'The row that makes the rest of this column legible. Covering one country '
            + 'rather than a sphere is what lets sixteen symbols reach 3.8 m in ten '
            + 'characters, where twenty-five symbols are needed to reach 2.5 m over the '
            + 'whole Earth. That is an engineering trade, made deliberately, and not a '
            + 'shortcoming of either. '
            + 'Every figure in this column is the published specification\'s. Sites '
            + 'unaffiliated with its authors offer a longer code extended to the whole '
            + 'Earth; that is somebody else\'s work and is not what the document defines, '
            + 'so it is not reported here as though it were.',
        values: {
            gpc: 'Everywhere on Earth',
            digipin: 'India and its maritime zone: 63.5 to 99.5° E, 2.5 to 38.5° N, as published',
            olc: 'Everywhere on Earth',
            geohash: 'Everywhere on Earth',
            w3w: 'Everywhere on Earth',
        },
    },
    {
        dimension: 'Same length everywhere',
        note:
            'A fixed length is what lets a code be recognised on sight, validated by '
            + 'shape, and stored in a fixed field.',
        values: {
            gpc: 'Yes',
            digipin: 'Yes',
            olc: 'No, length is chosen for precision',
            geohash: 'No, length is chosen for precision',
            w3w: 'Three words, of varying character length',
        },
    },
    {
        dimension: 'A shared prefix means nearby',
        note:
            'Every grid encoding here that claims this is right to, and the two whose '
            + 'documents discuss the converse both say it does not hold: two points can '
            + 'be metres apart and share nothing. That is a property of fixed grids rather '
            + 'than of any one design, and the seams page measures what it costs.',
        values: {
            gpc: 'Yes, and the converse is stated not to hold',
            digipin: 'Yes, "identifying the cells is done in a hierarchical fashion"',
            olc: null,
            geohash: 'Yes; "the reverse of this is not guaranteed"',
            w3w: 'No, the words are unrelated by design',
        },
    },
    {
        dimension: 'Symbols it is written in',
        note:
            'Every system here guards against this, which is worth saying plainly: it is '
            + 'settled practice rather than anybody\'s idea. Open Location Code\'s symbols '
            + 'are "selected to reduce writing errors and prevent accidentally spelling '
            + 'words"; DIGIPIN\'s document records replacing G, W and X "to maintain the '
            + 'phonetic and visual clarity"; geohash drops a, i, l and o; the word lists '
            + 'are curated. This one has no vowels, so a code cannot spell a word.',
        values: {
            gpc: '25 symbols, no vowels',
            digipin: '16 symbols',
            olc: '20 digits',
            geohash: '32 characters, base-32',
            w3w: 'Words from a curated list',
        },
    },
    {
        dimension: 'Converts without a network or a table',
        values: {
            gpc: 'Yes, arithmetic only',
            digipin: 'Yes, described as an offline grid system',
            olc: 'Yes, arithmetic only',
            geohash: 'Yes, arithmetic only',
            w3w: 'The published interface is an API',
        },
    },
    {
        dimension: 'A way to tell a mistyped one',
        note:
            'Read carefully: "its document does not define one" is not the same as "it '
            + 'cannot". These cells report what each specification sets out, and a system '
            + 'may well do something its document leaves unsaid. The check character here '
            + 'is optional and absent unless somebody asks for it, which is worth '
            + 'admitting in the same breath as claiming it.',
        values: {
            gpc: 'An optional check character, added on request',
            digipin: 'Its document does not define one',
            olc: 'Its specification does not define one',
            geohash: 'Not described',
            w3w: 'Homophones and spelling variants removed from the lists',
        },
    },
    {
        dimension: 'A way to recover a mistyped one',
        note:
            'The row where this format has something the others\' documents do not '
            + 'describe. The structure that hides an error also locates it: the wrong '
            + 'code\'s neighbourhood is searched for codes one keystroke away, ranked '
            + 'against a rough idea of where you were. The typos page here does it in '
            + 'front of the reader.',
        values: {
            gpc: 'Yes, candidates one keystroke away, ranked by distance',
            digipin: 'Its document does not define one',
            olc: 'Its specification does not define one',
            geohash: 'Not described',
            w3w: 'Not described',
        },
    },
    {
        dimension: 'Codes that read badly',
        note:
            'Two approaches, and the difference is which way each can be corrected later. '
            + 'The word lists were built with native speakers and rude words removed, but '
            + 'because the addresses are permanent the lists cannot be revised. The list '
            + 'here is advisory and versioned instead: it can be updated without changing '
            + 'a single code, and it screens rather than forbids.',
        values: {
            gpc: 'An advisory, versioned list; screening, not blocking',
            digipin: 'Its document does not mention this',
            olc: 'Its specification does not mention this',
            geohash: 'Not described',
            w3w: 'Rude and offensive words removed when each list is built',
        },
    },
    {
        dimension: 'Saying it out loud',
        note:
            'The row this format loses on its own evidence. Its appendix states that read '
            + 'out in English, C, D, G, P, T and the digit three all rhyme, and that the '
            + 'alphabet was not chosen for phonetic distinctness and cannot be now. Three '
            + 'words chosen with native speakers are easier to say down a telephone than '
            + 'ten characters, and no amount of callout words closes that gap entirely.',
        values: {
            gpc: 'Ten characters; callout words, and a rhyming set it admits to',
            digipin: 'Ten characters, chosen partly for phonetic clarity',
            olc: 'Ten or eleven characters',
            geohash: 'A string of base-32 characters',
            w3w: 'Three words, chosen with native speakers',
        },
    },
    {
        dimension: 'Terms',
        values: {
            gpc: 'Apache-2.0',
            digipin: 'Open source, published by the Department of Posts',
            olc: 'Apache-2.0',
            geohash: 'Placed in the public domain by its inventor, 2008',
            w3w: 'Commercial; an API key is required',
        },
    },
    {
        dimension: 'How widely it is already used',
        note:
            'This is where this format is furthest behind, and it is not close. The others '
            + 'are in mapping applications, databases and emergency services that people '
            + 'depend on today. A format is worth what it is accepted by, and by that '
            + 'measure this one is worth very little so far.',
        values: {
            gpc: 'Not adopted anywhere',
            digipin: 'National addressing infrastructure, backed by India Post',
            olc: 'Integrated in widely used mapping products',
            geohash: 'Long-standing use in databases and search systems',
            w3w: 'Integrated by many navigation and emergency services',
        },
    },
];

/**
 * What this page will not do.
 *
 * Kept beside the data rather than in the markup, because it is the part most
 * likely to be quietly dropped in a later edit.
 */
export const PROMISES = [
    'Every figure about another system comes from that system\'s own documentation, linked beside it.',
    'Where a document does not answer a question, the cell says so rather than guessing, and "its document does not define one" is never a claim that a system cannot do the thing.',
    'The dimensions include the ones this format loses, adoption and saying an address out loud, because choosing the axes is how a comparison lies while every fact in it stays true. So does choosing who appears at all, which is why the closest system to this one is here rather than absent.',
    'No system here is described as bad, and none of them is. They answer different questions, and several answer theirs better than this one answers anything yet.',
];
