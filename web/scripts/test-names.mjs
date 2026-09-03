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

// The name index must answer with the place that was meant.
//
//   node scripts/test-names.mjs
//
// Twenty-four places in the world are called Toronto. The first index built
// from the whole dump put the one in Ontario nineteenth, behind five rows that
// all read `Toronto, New South Wales, Australia` and could not be told apart on
// screen. Every one of those rows was correct. The search was still wrong.
//
// So the fixture below is that case, shrunk: one large city, its namesakes, the
// duplicates, and a name that merely starts the same. It is built through the
// real builder and read back through the reader's own columns.

import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { transform } from 'esbuild';

import { build } from './build-names.mjs';

const web = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const TAB = '\t';

/** A dump row, given only the columns that matter. */
function row({ id, name, latitude, longitude, kind, code, country, admin, people }) {
    const columns = new Array(19).fill('');
    columns[0] = String(id);
    columns[1] = name;
    columns[2] = name;
    columns[4] = String(latitude);
    columns[5] = String(longitude);
    columns[6] = kind;
    columns[7] = code;
    columns[8] = country;
    columns[10] = admin;
    columns[14] = String(people ?? 0);
    return columns.join(TAB);
}

const PLACES = [
    // The one anybody typing the word means, and it sorts late by name alone.
    {
        id: 1, name: 'Toronto', latitude: 43.70011, longitude: -79.4163,
        kind: 'P', code: 'PPLA', country: 'CA', admin: '08', people: 2600000,
    },

    // Five rows, one line on screen. A suburb, its centre, and three features
    // inside it, every one of them `Toronto, New South Wales, Australia`.
    ...[2, 3, 4, 5, 6].map((id) => ({
        id, name: 'Toronto', latitude: -33.0167, longitude: 151.5833,
        kind: 'P', code: 'PPL', country: 'AU', admin: '02', people: id === 2 ? 5000 : 0,
    })),

    // Counted well above the one in New South Wales, so the order between them
    // is one the ranking states rather than one the sort happened to produce.
    {
        id: 7, name: 'Toronto', latitude: 40.4645, longitude: -80.6009,
        kind: 'P', code: 'PPL', country: 'US', admin: 'OH', people: 32000,
    },
    {
        id: 8, name: 'Toronto', latitude: -5.0667, longitude: 38.9,
        kind: 'S', code: 'FRM', country: 'TZ', admin: '15', people: 0,
    },

    // Same name and same country as the first, different province: this one
    // must survive the collapse that removes the four above.
    {
        id: 9, name: 'Toronto', latitude: 44.9, longitude: -66.0,
        kind: 'P', code: 'PPL', country: 'CA', admin: '04', people: 0,
    },

    // Shares the prefix without sharing the name.
    {
        id: 10, name: 'Torontoville', latitude: 41.0, longitude: -80.0,
        kind: 'P', code: 'PPL', country: 'US', admin: 'OH', people: 300,
    },

    // A seat of government with no count recorded still outranks a feature
    // with none, which is the only thing the fallback has to get right.
    {
        id: 11, name: 'Zedcity', latitude: 10.0, longitude: 10.0,
        kind: 'P', code: 'PPLC', country: 'US', admin: 'OH', people: 0,
    },
    {
        id: 12, name: 'Zedcity', latitude: 11.0, longitude: 11.0,
        kind: 'S', code: 'FRM', country: 'CA', admin: '08', people: 0,
    },
];

const COUNTRIES = [
    ['CA', 'CAN', '124', 'CA', 'Canada'],
    ['AU', 'AUS', '036', 'AS', 'Australia'],
    ['US', 'USA', '840', 'US', 'United States'],
    ['TZ', 'TZA', '834', 'TZ', 'Tanzania'],
].map((parts) => parts.join(TAB)).join('\n');

const ADMINS = [
    ['CA.08', 'Ontario', 'Ontario', '1'],
    ['CA.04', 'New Brunswick', 'New Brunswick', '2'],
    ['AU.02', 'New South Wales', 'New South Wales', '3'],
    ['US.OH', 'Ohio', 'Ohio', '4'],
    ['TZ.15', 'Tanga', 'Tanga', '5'],
].map((parts) => parts.join(TAB)).join('\n');

/** The file read back through the columns `lib/search.ts` reads. */
async function readBack(out) {
    const index = JSON.parse(await readFile(path.join(out, 'names.index.json'), 'utf8'));
    const body = await readFile(path.join(out, 'names.txt'), 'utf8');
    const places = body.split('\n').filter(Boolean).map((line) => {
        const parts = line.split(TAB);
        return {
            folded: parts[0],
            name: parts[2],
            code: parts[3],
            region: index.regions[Number(parts[4])] ?? '',
        };
    });
    return { index, places, bytes: Buffer.byteLength(body) };
}

/**
 * The browser half, over the file just built.
 *
 * Not a copy of it: `lib/search.ts` itself, compiled here and given a `fetch`
 * that serves byte ranges out of the temporary directory. The columns of a line
 * are agreed between two files, and the way that agreement breaks is silent --
 * the search returns the right number of hits with a region in the name field
 * and nothing raises. This is what notices.
 */
async function reader(out) {
    const source = await readFile(path.join(web, 'src', 'lib', 'search.ts'), 'utf8');
    const { code } = await transform(source, {
        loader: 'ts',
        format: 'esm',
        define: { 'import.meta.env.BASE_URL': JSON.stringify('/') },
    });

    globalThis.fetch = async (url, options) => {
        const file = path.join(out, path.basename(String(url)));
        const body = await readFile(file);

        const range = /bytes=(\d+)-(\d+)/.exec(options?.headers?.Range ?? '');
        if (!range) {
            return {
                ok: true,
                status: 200,
                text: async () => body.toString('utf8'),
                json: async () => JSON.parse(body.toString('utf8')),
            };
        }

        // Inclusive at both ends, the way a server reads it.
        const slice = body.subarray(Number(range[1]), Number(range[2]) + 1);
        return { ok: true, status: 206, text: async () => slice.toString('utf8') };
    };

    return import(
        `data:text/javascript;base64,${Buffer.from(code, 'utf8').toString('base64')}`
    );
}

const where = await mkdtemp(path.join(tmpdir(), 'gpc-names-'));
let failed = 0;
const complain = (what) => { console.error(`  ${what}`); failed += 1; };

try {
    const geonames = path.join(where, 'geonames');
    const out = path.join(where, 'out');
    await mkdir(geonames, { recursive: true });
    await mkdir(out, { recursive: true });

    await writeFile(path.join(geonames, 'countryInfo.txt'), COUNTRIES, 'utf8');
    await writeFile(path.join(geonames, 'admin1CodesASCII.txt'), ADMINS, 'utf8');
    const dump = path.join(geonames, 'XX.txt');
    await writeFile(dump, PLACES.map(row).join('\n'), 'utf8');

    const report = await build({ geonames, out, dumps: [dump] });
    const { index, places, bytes } = await readBack(out);
    const toronto = places.filter((place) => place.folded === 'toronto');

    // The whole reason this file exists.
    if (toronto[0]?.region !== 'Ontario, Canada') {
        complain(`the first Toronto is ${JSON.stringify(toronto[0]?.region)}, not Ontario`);
    }

    // Five rows in, one row out, and the other Canadian one untouched.
    const nsw = toronto.filter((place) => place.region === 'New South Wales, Australia');
    if (nsw.length !== 1) {
        complain(`${nsw.length} rows read Toronto, New South Wales; one is enough`);
    }
    if (toronto.filter((place) => place.region === 'New Brunswick, Canada').length !== 1) {
        complain('the New Brunswick Toronto was collapsed into the Ontario one');
    }
    if (report.doubled !== 4) {
        complain(`${report.doubled} duplicates dropped, expected 4`);
    }

    // Population order within the name, once the duplicates are gone.
    const order = toronto.map((place) => place.region);
    const expected = [
        'Ontario, Canada',                  // 2,600,000
        'Ohio, United States',              // 32,000
        'New South Wales, Australia',       // 5,000
        'New Brunswick, Canada',            // a town, uncounted
        'Tanga, Tanzania',                  // a farm, uncounted
    ];
    if (JSON.stringify(order) !== JSON.stringify(expected)) {
        complain(`the order is ${JSON.stringify(order)}`);
        complain(`it should be   ${JSON.stringify(expected)}`);
    }

    // A seat of government with no count beats a feature with none. The two
    // are in different provinces on purpose: in the same one they would be the
    // same line to a reader, and the rule above would keep only the first.
    const zed = places.filter((place) => place.folded === 'zedcity');
    if (zed.length !== 2) complain(`${zed.length} Zedcity rows, expected 2`);
    if (zed[0]?.region !== 'Ohio, United States') {
        complain(`the first Zedcity is ${JSON.stringify(zed[0]?.region)}, not the capital`);
    }

    // A longer name is not the same name.
    if (toronto.some((place) => place.name === 'Torontoville')) {
        complain('Torontoville was folded into Toronto');
    }
    if (!places.some((place) => place.folded === 'torontoville')) {
        complain('Torontoville is missing from the index');
    }

    // Every code is a code, because a hit puts a pin on a map without asking
    // anything else for the coordinate.
    for (const place of places) {
        if (!/^[0-9A-Z]{10}$/.test(place.code)) {
            complain(`${place.name} carries ${JSON.stringify(place.code)}, not a code`);
            break;
        }
    }

    // The two numbers the reader trusts to find its way into the file.
    if (report.bytes !== bytes) {
        complain(`the index claims ${report.bytes} bytes, the file has ${bytes}`);
    }
    if (report.lines !== places.length) {
        complain(`the index claims ${report.lines} lines, the file has ${places.length}`);
    }
    if (index.marks[0]?.[1] !== 0) {
        complain('the first mark does not point at the start of the file');
    }

    // And now the same file through the reader that will actually read it.
    const search = await reader(out);
    const hits = await search.search('toronto', 20);

    if (hits === null) {
        complain('the reader found no index beside the file it just built');
    } else {
        // Five Torontos and the Torontoville behind them: a prefix search, so
        // the longer name is a hit and comes after every exact one.
        if (hits.length !== 6) complain(`the reader returned ${hits.length} hits, expected 6`);
        if (hits[5]?.name !== 'Torontoville') {
            complain(`the last hit is ${JSON.stringify(hits[5]?.name)}, not Torontoville`);
        }
        if (hits[0]?.name !== 'Toronto' || hits[0]?.region !== 'Ontario, Canada') {
            complain(`the reader's first hit is ${JSON.stringify(hits[0])}`);
        }
        if (!/^[0-9A-Z]{10}$/.test(hits[0]?.code ?? '')) {
            complain(`the reader read ${JSON.stringify(hits[0]?.code)} as a code`);
        }
        if (hits.some((hit) => hit.name === '' || hit.region === '')) {
            complain('the reader read an empty name or region, so the columns disagree');
        }
    }

    // A prefix past the shared part narrows to the one name that has it.
    const longer = await search.search('torontov', 20);
    if ((longer ?? []).length !== 1 || longer?.[0]?.name !== 'Torontoville') {
        complain(`a longer prefix returned ${JSON.stringify(longer)}`);
    }

    // Below two characters the reader answers nothing rather than a block.
    if ((await search.search('t', 20))?.length !== 0) {
        complain('a single letter returned hits');
    }
} finally {
    await rm(where, { recursive: true, force: true });
}

if (failed > 0) {
    console.error(
        `\n${failed} thing${failed === 1 ? '' : 's'} wrong with the index.`
        + '\nA search returning the right places in the wrong order is still the'
        + '\nwrong answer to the person who typed the name.',
    );
    process.exit(1);
}

console.log('the index answers Toronto with Toronto');
