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

// The index that lets a reader type a place name.
//
//   node scripts/build-names.mjs --geonames <dir> [--out <dir>]
//
// The landmark archive answers "what is near this point". This answers the
// other direction, which is the first thing anybody reaches for and the one
// thing the site could not do.
//
// **One sorted file, read by the byte range that matters.** The obvious design
// is to shard by the first few letters of the name, the way the landmarks are
// sharded by cell. It does not survive the data: Ireland alone puts 7.7% of its
// names under one three-letter prefix, because a great many of them begin
// `Bal`. Fixed depth leaves buckets that are megabytes; adaptive depth needs a
// map of which prefixes were split, fetched before anything can be searched.
// And either way it adds tens of thousands of files to a site where the 82,249
// landmark shards already made the development server unusable.
//
// Sorted order costs none of that. Every name starting with what somebody typed
// is contiguous, so a prefix search is one range request -- and which range is
// answered by a sparse index of every 512th line, small enough to fetch once
// and keep.
//
// **The code is the coordinate**, so a hit needs nothing else fetched to put a
// pin on a map. That is the property the whole format exists for, spent here.
//
// A line is `folded, importance, name, code, region`, and importance is written
// inverted so that plain lexicographic order is already the answer order: names
// alphabetically, and within one name the largest place first. The reader sorts
// nothing, which matters because it only ever sees the first few kilobytes.
//
// Names are indexed by their ASCII form, which GeoNames supplies for every row
// -- verified, not assumed: 100% of admissible rows in a sample country carry
// one. That keeps the sort, the search and the file itself in one script, and
// means a reader typing `Trá Mhór` or `Tra Mhor` finds the same place.

import { createReadStream, createWriteStream } from 'node:fs';
import { mkdir, readdir, readFile, rm, writeFile } from 'node:fs/promises';
import { createInterface } from 'node:readline';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { GPC } from '@pranavpatel.ca/algo-gridpointcode';

const here = path.dirname(fileURLToPath(import.meta.url));
const web = path.resolve(here, '..');

/** The columns this reads, by their position in the dump. */
const field = {
    name: 1, ascii: 2, latitude: 4, longitude: 5,
    class: 6, code: 7, country: 8, admin1: 10, population: 14,
};

/** The same three kinds the landmark archive keeps. */
const KINDS = { S: 0, T: 1, P: 2 };

/** The same exclusions, for the same reasons. See build-landmarks.mjs. */
const NOT_LANDMARKS = new Set([
    'BANK', 'HTL', 'REST', 'MALL', 'MKT',
    'BLDO', 'EST', 'CMP', 'STNM', 'BUSTP',
]);

/** One index entry per this many lines. 512 keeps the index under a megabyte
 *  for the whole world while a single range request stays a few kilobytes. */
const STRIDE = 512;

/**
 * What a name is reduced to for searching.
 *
 * Case and punctuation go, because nobody types an apostrophe in the right
 * place and `Saint John's Point` should be found by `saint johns`. The result
 * is what the file is sorted by and what the reader compares against, so the
 * two must agree exactly -- this function is duplicated in `lib/search.ts`, and
 * a test holds them to the same answers.
 */
export function fold(name) {
    return name
        .toLowerCase()
        .normalize('NFKD')
        .replace(/[̀-ͯ]/g, '')
        .replace(/[^a-z0-9]+/g, ' ')
        .trim();
}

/**
 * How much a place deserves to be the first answer.
 *
 * Twenty-four places are called Toronto. Ordered by name alone the one in
 * Ontario came nineteenth, behind five identical rows in New South Wales --
 * a search that works and is still wrong. Population puts them in the order
 * somebody typing the word meant, and a seat of government stands in for a
 * count where the count is missing.
 *
 * Nine is the most important. It is written into the line inverted, so that
 * sorting the file the ordinary way puts the biggest place first within each
 * name and the file needs no second pass -- and the reader none at all.
 */
function rank(row) {
    const people = Number(row[field.population]) || 0;
    if (people >= 1_000_000) return 9;
    if (people >= 300_000) return 8;
    if (people >= 100_000) return 7;
    if (people >= 30_000) return 6;
    if (people >= 10_000) return 5;
    if (people >= 3_000) return 4;
    if (people >= 1_000) return 3;
    if (people > 0) return 2;

    // No count at all: a capital or an administrative seat is still the place
    // somebody meant, and everything else falls back to what kind of thing
    // it is -- a town above a hill above a building.
    const code = row[field.code];
    if (code === 'PPLC' || code === 'PPLA') return 2;
    return row[field.class] === 'P' ? 1 : 0;
}

function admissible(row) {
    if (row.length < 12) return false;
    if (!(row[field.class] in KINDS)) return false;
    if (NOT_LANDMARKS.has(row[field.code])) return false;
    return row[field.name] !== '';
}

/** Country and province names, so a result can say where it is. */
async function tables(dir) {
    const countries = new Map();
    const regions = new Map();

    const country = path.join(dir, 'countryInfo.txt');
    const admin = path.join(dir, 'admin1CodesASCII.txt');

    for await (const line of createInterface({
        input: createReadStream(country, 'utf8'), crlfDelay: Infinity,
    })) {
        if (!line || line.startsWith('#')) continue;
        const parts = line.split('\t');
        if (parts.length > 4) countries.set(parts[0], parts[4]);
    }

    for await (const line of createInterface({
        input: createReadStream(admin, 'utf8'), crlfDelay: Infinity,
    })) {
        if (!line) continue;
        const parts = line.split('\t');
        if (parts.length > 1) regions.set(parts[0], parts[1]);
    }

    return { countries, regions };
}

/**
 * Sort without holding the world in memory.
 *
 * Every entry is written to a bucket named for the first character of its
 * folded name. Sorted order overall is then the buckets in order, each sorted
 * on its own -- and the largest single bucket is a fraction of the whole, which
 * is the difference between fitting in memory and not.
 */
function bucketOf(folded) {
    const first = folded.charCodeAt(0);
    if (first >= 97 && first <= 122) return folded[0];      // a-z
    if (first >= 48 && first <= 57) return 'd';             // digits together
    return 'x';                                             // anything else
}

export async function build({ geonames, out, dumps }) {
    const { countries, regions } = await tables(geonames);

    const spill = path.join(out, '.spill');
    await rm(spill, { recursive: true, force: true });
    await mkdir(spill, { recursive: true });

    const writers = new Map();
    const regionIds = new Map();
    let counted = 0;
    let skipped = 0;
    let doubled = 0;

    const writerFor = (bucket) => {
        let handle = writers.get(bucket);
        if (!handle) {
            handle = createWriteStream(path.join(spill, `${bucket}.tsv`), 'utf8');
            writers.set(bucket, handle);
        }
        return handle;
    };

    for (const file of dumps) {
        for await (const line of createInterface({
            input: createReadStream(file, 'utf8'), crlfDelay: Infinity,
        })) {
            if (!line) continue;
            const row = line.split('\t');
            if (!admissible(row)) continue;

            const folded = fold(row[field.ascii] || row[field.name]);
            if (!folded) { skipped++; continue; }

            const latitude = Number(row[field.latitude]);
            const longitude = Number(row[field.longitude]);
            if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
                skipped++;
                continue;
            }

            let code;
            try {
                code = GPC.encode(latitude, longitude, false);
            } catch {
                skipped++;                  // outside the domain, or reserved
                continue;
            }

            const cc = row[field.country];
            const where = regions.get(`${cc}.${row[field.admin1]}`)
                ? `${regions.get(`${cc}.${row[field.admin1]}`)}, ${countries.get(cc) ?? cc}`
                : (countries.get(cc) ?? cc);
            if (!regionIds.has(where)) regionIds.set(where, regionIds.size);

            // Tabs separate, so they cannot appear in a field. A name carrying
            // one would split a line into the wrong columns.
            const display = row[field.name].replace(/\t/g, ' ');
            writerFor(bucketOf(folded)).write(
                `${folded}\t${9 - rank(row)}\t${display}\t${code}\t${regionIds.get(where)}\n`,
            );
            counted++;
        }
    }

    await Promise.all([...writers.values()].map(
        (handle) => new Promise((done) => handle.end(done)),
    ));

    // Now the buckets in order, each sorted, appended into one file -- with
    // every STRIDE-th line remembered so a reader can find its way in.
    const names = path.join(out, 'names.txt');
    const output = createWriteStream(names, 'utf8');
    const sparse = [];
    let offset = 0;
    let lines = 0;

    // One place, one line. Five rows read `Toronto, New South Wales, Australia`
    // and nothing on screen tells them apart, so four of them are noise in the
    // twelve answers a reader gets. Dropped here rather than in the browser:
    // the reader cannot drop what its byte range never reached. The landmark
    // archive still holds every one -- this is the search index, not the record.
    let group = null;
    let seen = new Set();

    for (const bucket of [...writers.keys()].sort()) {
        const body = await readFile(path.join(spill, `${bucket}.tsv`), 'utf8');
        const rows = body.split('\n').filter(Boolean);
        rows.sort();

        for (const row of rows) {
            const parts = row.split('\t');
            if (parts[0] !== group) {
                group = parts[0];
                seen = new Set();
            }
            const same = `${parts[2]}\t${parts[4]}`;   // what it says, and where
            if (seen.has(same)) { doubled++; continue; }
            seen.add(same);

            if (lines % STRIDE === 0) sparse.push([parts[0], offset]);
            const chunk = Buffer.byteLength(row) + 1;
            if (!output.write(row + '\n')) {
                await new Promise((drained) => output.once('drain', drained));
            }
            offset += chunk;
            lines += 1;
        }
    }

    await new Promise((done) => output.end(done));
    await rm(spill, { recursive: true, force: true });

    await writeFile(
        path.join(out, 'names.index.json'),
        JSON.stringify({
            stride: STRIDE,
            lines,
            bytes: offset,
            regions: [...regionIds.keys()],
            marks: sparse,
            built: new Date().toISOString(),
        }),
        'utf8',
    );

    return { counted, skipped, doubled, lines, bytes: offset, marks: sparse.length };
}

async function main() {
    const argument = (name, fallback) => {
        const at = process.argv.indexOf(`--${name}`);
        return at === -1 ? fallback : process.argv[at + 1];
    };

    const geonames = argument('geonames', path.join(web, '..', 'geonames'));
    const out = argument('out', path.join(web, 'names'));

    await mkdir(out, { recursive: true });

    const present = await readdir(geonames);
    const dumps = (present.includes('allCountries.txt')
        ? ['allCountries.txt']
        : present.filter((f) => /^[A-Z]{2}\.txt$/.test(f))
    ).map((f) => path.join(geonames, f));

    if (dumps.length === 0) {
        console.error(`no country dumps found in ${geonames}`);
        process.exit(2);
    }

    const started = Date.now();
    const report = await build({ geonames, out, dumps });
    const seconds = ((Date.now() - started) / 1000).toFixed(1);

    console.log(`indexed ${report.counted.toLocaleString('en')} names in ${seconds} s`);
    console.log(`  ${(report.bytes / 1e6).toFixed(1)} MB in names.txt`);
    console.log(`  ${report.marks.toLocaleString('en')} marks in names.index.json`);
    if (report.doubled) {
        console.log(`  ${report.doubled.toLocaleString('en')} said the same name in the same place`);
    }
    if (report.skipped) {
        console.log(`  ${report.skipped.toLocaleString('en')} rows had no usable name or point`);
    }
}

if (import.meta.url === `file://${process.argv[1]}` || process.argv[1]?.endsWith('build-names.mjs')) {
    await main();
}
