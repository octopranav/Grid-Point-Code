//  Copyright 2017 Pranavkumar Patel
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

// The three export formats, on the inputs that break them.
//
//   node scripts/test-exports.mjs
//
// Every case here is a rule somebody else's parser will hold the output to.
// None of them shows up on a place with an ordinary name, which is the problem:
// a file written from `Toronto` proves nothing about a file written from
// `Smith, J. & Sons; "the old yard"`, and the second one is what arrives.

import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { transform } from 'esbuild';

const here = path.dirname(fileURLToPath(import.meta.url));
const web = path.resolve(here, '..');

const source = await readFile(path.join(web, 'src', 'lib', 'exports.ts'), 'utf8');
const { code } = await transform(source, { loader: 'ts', format: 'esm' });
const { vcard, geojson, csv, FORMATS } = await import(
    `data:text/javascript;base64,${Buffer.from(code, 'utf8').toString('base64')}`
);

const TORONTO = {
    code: 'G3RJM98NM9',
    name: 'Old Toronto',
    latitude: 43.650006,
    longitude: -79.380004,
    saved: '2026-09-04T10:00:00.000Z',
};

// A name carrying every character the three formats treat as structure.
const AWKWARD = {
    ...TORONTO,
    name: 'Smith, J. & Sons; "the old yard" \\ back gate',
};

const LONG = {
    ...TORONTO,
    name: 'A name long enough to need folding, repeated until it certainly does: '
        + 'north gate of the old goods yard by the canal bridge',
};

const ACCENTED = { ...TORONTO, name: 'Trá Mhór, Port Láirge — 大阪' };

let wrong = 0;
const check = (what, got, want) => {
    const same = JSON.stringify(got) === JSON.stringify(want);
    if (!same) {
        console.error(`  ${what}\n    got  ${JSON.stringify(got)}\n    want ${JSON.stringify(want)}`);
        wrong += 1;
    }
};
const holds = (what, condition, detail = '') => {
    if (!condition) {
        console.error(`  ${what}${detail ? `\n    ${detail}` : ''}`);
        wrong += 1;
    }
};

// --- vCard ------------------------------------------------------------------

const card = vcard([TORONTO]);

holds('a card begins and ends the way the format says',
    card.startsWith('BEGIN:VCARD\r\nVERSION:4.0\r\n') && card.endsWith('END:VCARD\r\n'));

holds('every line ends CRLF',
    !/[^\r]\n/.test(card), 'a bare newline is in there somewhere');

holds('GEO is a geo: URI, latitude first',
    card.includes('GEO:geo:43.650006,-79.380004'),
    card.split('\r\n').find((line) => line.startsWith('GEO')));

holds('the code is where a person will see it',
    card.includes('NOTE:Grid Point Code #G3RJM-98NM9'));

holds('and where a machine would look',
    card.includes('X-GRIDPOINTCODE:#G3RJM-98NM9'));

// The one that matters. An unescaped comma or semicolon in FN does not fail to
// parse, it parses into the wrong number of fields.
const awkward = vcard([AWKWARD]);
const fn = awkward.split('\r\n').find((line) => line.startsWith('FN:'));
holds('a comma in a name is escaped', fn.includes('\\,'), fn);
holds('a semicolon in a name is escaped', fn.includes('\\;'), fn);
holds('a backslash is escaped first, not last',
    fn.includes('\\\\ back gate'), fn);
holds('a quote needs no escaping in vCard', fn.includes('"the old yard"'), fn);

// Folding: 75 octets, and never inside a character.
const folded = vcard([LONG]);
for (const line of folded.split('\r\n')) {
    holds(`no line is over 75 octets: ${line.slice(0, 40)}...`,
        new TextEncoder().encode(line).length <= 75,
        `${new TextEncoder().encode(line).length} octets`);
}
holds('a folded line is continued with a space',
    folded.includes('\r\n '), 'nothing was folded at all');

// A reader unfolds, then unescapes. Both, in that order, and the second one
// has to be a scan rather than a chain of replacements: `\\,` is an escaped
// backslash followed by a comma, and any `replace` that handles `\,` first
// turns it into an escaped comma instead.
function unescape(value) {
    let out = '';
    for (let at = 0; at < value.length; at += 1) {
        if (value[at] !== '\\') { out += value[at]; continue; }
        at += 1;
        out += value[at] === 'n' || value[at] === 'N' ? '\n' : value[at];
    }
    return out;
}

function fields(text) {
    return text
        .split('\r\n')
        .reduce((lines, line) => {
            if (line.startsWith(' ')) lines[lines.length - 1] += line.slice(1);
            else lines.push(line);
            return lines;
        }, [])
        .map((line) => {
            const at = line.indexOf(':');
            return at < 0 ? [line, ''] : [line.slice(0, at), unescape(line.slice(at + 1))];
        });
}

const readAccented = fields(vcard([ACCENTED])).find(([key]) => key === 'FN');
check('an accented name survives folding and unfolding',
    readAccented[1], ACCENTED.name);

// The one the escaping exists for, taken all the way back.
const readAwkward = fields(vcard([AWKWARD])).find(([key]) => key === 'FN');
check('and so does a name full of separators and a backslash',
    readAwkward[1], AWKWARD.name);

const readLong = fields(vcard([LONG])).find(([key]) => key === 'FN');
check('and a name long enough to be folded', readLong[1], LONG.name);

// --- GeoJSON ----------------------------------------------------------------

const parsed = JSON.parse(geojson([TORONTO, AWKWARD]));

check('a collection of two', parsed.features.length, 2);
check('longitude comes first, which is the opposite of everything else here',
    parsed.features[0].geometry.coordinates, [-79.380004, 43.650006]);
check('the code is formatted', parsed.features[0].properties.code, '#G3RJM-98NM9');
check('a name with quotes and backslashes survives JSON',
    parsed.features[1].properties.name, AWKWARD.name);

// --- CSV --------------------------------------------------------------------

const table = csv([TORONTO, AWKWARD]);
const lines = table.split('\r\n').filter(Boolean);

check('a header and two rows', lines.length, 3);
check('the header', lines[0], 'code,name,latitude,longitude,saved');
check('an ordinary row is not quoted', lines[1],
    '#G3RJM-98NM9,Old Toronto,43.650006,-79.380004,2026-09-04T10:00:00.000Z');

// A field with a comma must be quoted, and a quote inside it doubled. Getting
// this wrong shifts every column after it by one, in silence.
holds('a field with a comma is quoted', lines[2].includes('"Smith, J.'), lines[2]);
holds('a quote inside a quoted field is doubled',
    lines[2].includes('""the old yard""'), lines[2]);

// Round-trip it through a minimal reader, which is the real test.
function read(line) {
    const fields = [];
    let field = '';
    let quoted = false;
    for (let at = 0; at < line.length; at += 1) {
        const character = line[at];
        if (quoted) {
            if (character === '"' && line[at + 1] === '"') { field += '"'; at += 1; }
            else if (character === '"') quoted = false;
            else field += character;
        } else if (character === '"') quoted = true;
        else if (character === ',') { fields.push(field); field = ''; }
        else field += character;
    }
    fields.push(field);
    return fields;
}

check('the awkward row reads back as five fields', read(lines[2]).length, 5);
check('and the name comes back unchanged', read(lines[2])[1], AWKWARD.name);

// --- empty ------------------------------------------------------------------

for (const format of FORMATS) {
    const empty = format.of([]);
    holds(`${format.name} handles an empty list`, typeof empty === 'string');
}
check('an empty collection is still a collection',
    JSON.parse(geojson([])).features, []);
holds('an empty table is still a header', csv([]).startsWith('code,name,'));
check('an empty card list is nothing at all', vcard([]), '\r\n');

// ---------------------------------------------------------------------------

if (wrong > 0) {
    console.error(
        `\n${wrong} thing${wrong === 1 ? '' : 's'} wrong with the exports.`
        + '\nEvery one of these is a rule another program will hold the file to,'
        + '\nand none of them shows on a place with an ordinary name.',
    );
    process.exit(1);
}

console.log('vCard, GeoJSON and CSV survive the names that break them');
