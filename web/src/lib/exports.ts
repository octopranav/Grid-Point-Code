// Getting places out of the browser, in formats other things already read.
//
// Three, because they are for three different destinations and no one of them
// substitutes for another: a vCard goes into a contact list, GeoJSON goes into
// a map, and CSV goes into a spreadsheet. All three are text, all three are
// built here, and none of them needs a server.
//
// The details below are the whole reason this is a file rather than three
// template literals at the call site. Every one of them is a rule somebody
// else's parser will hold this to, and every one is the kind of thing that
// works on the place you tested and breaks on a name with a comma in it.

/** A place somebody kept. */
export interface Kept {
    /** The bare ten characters. */
    code: string;
    /** What the reader called it, or what was nearby when they saved it. */
    name: string;
    latitude: number;
    longitude: number;
    /** When it was saved, ISO 8601. */
    saved: string;
}

/** The formatted code, from the bare one. */
const shown = (code: string) => `#${code.slice(0, 5)}-${code.slice(5)}`;

// ---------------------------------------------------------------------------
// vCard 4.0, RFC 6350.

/**
 * A text value, escaped the way the format requires.
 *
 * Backslash first: doing it after the others would escape the backslashes they
 * just introduced. A comma or a semicolon inside a name is not decoration --
 * they are the field and value separators, and a parser reading an unescaped
 * one gets a card with the wrong number of fields rather than an error.
 */
function escaped(value: string): string {
    return value
        .replace(/\\/g, '\\\\')
        .replace(/\n/g, '\\n')
        .replace(/,/g, '\\,')
        .replace(/;/g, '\\;');
}

/**
 * One content line, folded to 75 octets.
 *
 * The limit is octets rather than characters, so a line is measured after
 * encoding: a name in a script that costs three bytes a character folds three
 * times sooner than its length suggests. Folding at the wrong place splits a
 * character in half and the card arrives corrupted.
 *
 * A continuation begins with one space, which the reader removes.
 */
function fold(line: string): string {
    const bytes = new TextEncoder().encode(line);
    if (bytes.length <= 75) return line;

    const parts: string[] = [];
    let start = 0;

    while (start < bytes.length) {
        // 75 on the first line, 74 on the rest: the leading space counts.
        let end = Math.min(start + (parts.length === 0 ? 75 : 74), bytes.length);

        // Never cut inside a character. A continuation byte is 10xxxxxx.
        while (end < bytes.length && (bytes[end] & 0xc0) === 0x80) end -= 1;

        parts.push(new TextDecoder().decode(bytes.slice(start, end)));
        start = end;
    }

    return parts.join('\r\n ');
}

/**
 * A contact card carrying the place.
 *
 * `GEO` in version 4 is a URI rather than a pair of numbers, so it is a `geo:`
 * one -- which is latitude first, unlike almost everything else here.
 *
 * The code goes in twice on purpose. `X-GRIDPOINTCODE` is where something that
 * knows the format would look, and the note is where a person will see it,
 * because nothing in a phone's contact list displays an X- property.
 */
export function vcard(places: Kept[]): string {
    const cards = places.map((place) => [
        'BEGIN:VCARD',
        'VERSION:4.0',
        fold(`FN:${escaped(place.name || shown(place.code))}`),
        `GEO:geo:${place.latitude},${place.longitude}`,
        fold(`NOTE:${escaped(`Grid Point Code ${shown(place.code)}`)}`),
        `X-GRIDPOINTCODE:${shown(place.code)}`,
        `REV:${place.saved}`,
        'END:VCARD',
    ].join('\r\n'));

    // CRLF throughout, including at the end. The format says so, and a reader
    // that tolerates bare newlines is being generous rather than correct.
    return cards.join('\r\n') + '\r\n';
}

// ---------------------------------------------------------------------------
// GeoJSON, RFC 7946.

/**
 * A feature collection of points.
 *
 * **Longitude first.** It is the one thing this format is famous for getting
 * argued about, it is the opposite of the order a code is written and spoken
 * in, and a file with them the wrong way round is not an error -- it is a map
 * of somewhere else. There is a test whose only job is this.
 */
export function geojson(places: Kept[]): string {
    return JSON.stringify({
        type: 'FeatureCollection',
        features: places.map((place) => ({
            type: 'Feature',
            geometry: {
                type: 'Point',
                coordinates: [place.longitude, place.latitude],
            },
            properties: {
                name: place.name,
                code: shown(place.code),
                saved: place.saved,
            },
        })),
    }, null, 2) + '\n';
}

// ---------------------------------------------------------------------------
// CSV, RFC 4180.

/**
 * One field, quoted when it has to be.
 *
 * A quote inside a quoted field is written twice. Everything else about CSV is
 * a matter of taste; this part is not, and getting it wrong shifts every column
 * after it by one.
 */
function cell(value: string): string {
    if (!/[",\r\n]/.test(value)) return value;
    return `"${value.replace(/"/g, '""')}"`;
}

export function csv(places: Kept[]): string {
    const rows = [
        ['code', 'name', 'latitude', 'longitude', 'saved'],
        ...places.map((place) => [
            shown(place.code),
            place.name,
            String(place.latitude),
            String(place.longitude),
            place.saved,
        ]),
    ];

    // CRLF, because the specification says so and because a spreadsheet on
    // Windows is the most likely thing to open this.
    return rows.map((row) => row.map(cell).join(',')).join('\r\n') + '\r\n';
}

// ---------------------------------------------------------------------------

export interface Format {
    id: 'vcard' | 'geojson' | 'csv';
    name: string;
    /** What it is for, in the reader's terms. */
    what: string;
    extension: string;
    /** The MIME type, which decides what the operating system offers to open. */
    type: string;
    of: (places: Kept[]) => string;
}

export const FORMATS: Format[] = [
    {
        id: 'vcard',
        name: 'vCard',
        what: 'a contact card, with the place in it',
        extension: 'vcf',
        type: 'text/vcard;charset=utf-8',
        of: vcard,
    },
    {
        id: 'geojson',
        name: 'GeoJSON',
        what: 'points, for a map or anything that reads one',
        extension: 'geojson',
        type: 'application/geo+json;charset=utf-8',
        of: geojson,
    },
    {
        id: 'csv',
        name: 'CSV',
        what: 'a table, for a spreadsheet',
        extension: 'csv',
        type: 'text/csv;charset=utf-8',
        of: csv,
    },
];
