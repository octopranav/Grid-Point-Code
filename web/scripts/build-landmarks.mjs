// Turn a GeoNames dump into the landmark shards the playground fetches.
//
// A short form is five characters and a reference, and the reference is the
// whole risk: outside half a level-5 cell recovery does not fail, it returns a
// plausible place eight or ten kilometres away with nothing raised. So every
// judgement in this file is about whether a listener, given only the text we
// print, arrives at the same point we meant.
//
//   node scripts/build-landmarks.mjs --geonames <dir> [--out <dir>]
//
// Reads <dir>/*.txt dumps plus admin1CodesASCII.txt and countryInfo.txt.
// Data from GeoNames (https://www.geonames.org), CC BY 4.0.

import { createReadStream } from 'node:fs';
import { readFile, mkdir, rm, writeFile } from 'node:fs/promises';
import { createInterface } from 'node:readline';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { GPC } from '@pranavpatel.ca/algo-gridpointcode';

// ── what may serve as a reference ──────────────────────────────────────────

// Classes we keep. Everything else is a line or an area whose single published
// coordinate is a centroid: a river, a road, a park, a province. A centroid is
// not a place anyone stands, and for a 50 km lake it is not even close to one.
// Written into each shard as an index, so the order is part of the file format:
// 0 structure, 1 natural, 2 populated place.
const KINDS = { S: 0, T: 1, P: 2 };

// Structures that are real and fixed and still make poor references, for two
// separate reasons. Some are branches of something: this dump holds a single
// row named `Bank Of Montreal`, so it passes the uniqueness test below, and a
// listener who geocodes it lands on whichever of hundreds of branches their
// search engine prefers. Others are premises nobody can look up at all -- a
// numbered meteorological station, an office building, a bus stop.
//
// Listed rather than derived: GeoNames describes both a bank and an AIRPORT as
// `a business establishment`, and an airport is one of the best references
// there is. The category that matters here is not in the data.
const NOT_LANDMARKS = new Set([
    'BANK', 'HTL', 'REST', 'MALL', 'MKT',      // a branch, not a place
    'BLDO', 'EST', 'CMP', 'STNM', 'BUSTP',     // premises nobody can look up
]);

const field = {
    name: 1, latitude: 4, longitude: 5, class: 6, code: 7,
    country: 8, admin1: 10,
};

/** The string a listener would be given, minus the code itself. */
const describe = (name, region) => (region ? `${name}, ${region}` : name);

/**
 * A 52-bit fingerprint of that string.
 *
 * Pass one below only needs to know which descriptions occur more than once,
 * and holding nine million of them as strings to find out costs several
 * gigabytes -- enough that the build died on a 3 GB heap and would have needed
 * a large runner to survive. Numbers pack into a typed array at eight bytes
 * each and sort in place, which turns the question into a scan.
 *
 * Two independent hashes, truncated to 52 bits so every value stays an exact
 * integer in a double. Over nine million descriptions that is about a one per
 * cent chance of a single collision anywhere in the world, and a collision
 * drops a good landmark rather than admitting a bad one -- the same direction
 * every other judgement here leans.
 */
function fingerprint(text) {
    let a = 0x811c9dc5;
    let b = 0x1505;
    for (let i = 0; i < text.length; i++) {
        const c = text.charCodeAt(i);
        a = Math.imul(a ^ c, 0x01000193) >>> 0;
        b = (Math.imul(b, 33) + c) >>> 0;
    }
    return a * 1048576 + (b >>> 12);
}

async function tables(dir) {
    const countries = new Map();
    for (const line of (await readFile(path.join(dir, 'countryInfo.txt'), 'utf8')).split('\n')) {
        if (!line || line.startsWith('#')) continue;
        const parts = line.split('\t');
        if (parts[0] && parts[4]) countries.set(parts[0], parts[4]);
    }

    const regions = new Map();
    for (const line of (await readFile(path.join(dir, 'admin1CodesASCII.txt'), 'utf8')).split('\n')) {
        if (!line) continue;
        const parts = line.split('\t');
        if (parts[0] && parts[1]) regions.set(parts[0], parts[1]);
    }

    return { countries, regions };
}

/**
 * A row's region, spelled out rather than coded.
 *
 * GeoNames gives Canada's provinces as FIPS numerals -- Ontario is `08` -- so
 * there is no abbreviation to print even if we wanted one. Spelling it in full
 * is the better answer anyway: `Scarborough, ON, CA` can be read as Ontario,
 * California, and the listener geocoding it has no way to know which we meant.
 */
function regionOf(row, { countries, regions }) {
    const cc = row[field.country];
    const country = countries.get(cc) ?? cc;
    const province = regions.get(`${cc}.${row[field.admin1]}`);
    return province ? `${province}, ${country}` : country;
}

function admissible(row) {
    if (row.length < 12) return false;
    if (!(row[field.class] in KINDS)) return false;
    if (NOT_LANDMARKS.has(row[field.code])) return false;
    return row[field.name] !== '';
}

async function eachRow(files, visit) {
    for (const file of files) {
        const lines = createInterface({
            input: createReadStream(file, 'utf8'),
            crlfDelay: Infinity,
        });
        for await (const line of lines) {
            if (!line) continue;
            const row = line.split('\t');
            if (admissible(row)) visit(row);
        }
    }
}

export async function build({ geonames, out, dumps, level, slices = 8 }) {
    const lookup = await tables(geonames);

    // Pass one finds the text we would print more than once. A name that is
    // not unique within its own region makes an ambiguous string, and an
    // ambiguous string is the silent failure again by another route:
    // Scarborough is a district of Toronto and a place 1,900 km north, and a
    // listener who picks the wrong one is not told. Measured rather than
    // assumed, and measured over the whole printed text rather than the name,
    // because the region is the part that disambiguates it.
    const marks = [];
    await eachRow(dumps, (row) => {
        marks.push(fingerprint(describe(row[field.name], regionOf(row, lookup))));
    });

    // Sorted so duplicates sit next to each other; only the repeats are kept,
    // and there are far fewer of those than there are descriptions.
    const sorted = Float64Array.from(marks);
    marks.length = 0;
    sorted.sort();

    const repeated = new Set();
    for (let i = 1; i < sorted.length; i++) {
        if (sorted[i] === sorted[i - 1]) repeated.add(sorted[i]);
    }

    // Pass two keeps the descriptions that name exactly one place and files
    // each landmark under the cell that contains it -- the first `level`
    // characters of its own code.
    //
    // Run in slices, because holding every kept landmark at once is what makes
    // this build expensive: six and a half million small arrays did not fit in
    // a 4 GB heap, and a job that needs a large runner is a job that breaks the
    // first time it is moved. Each slice keeps a share of the shards and
    // re-reads the dump to fill it, trading a few minutes of parsing for a
    // ceiling low enough to run anywhere. Sliced on a hash of the shard name so
    // the shares come out even -- the alphabet would not, since most of the
    // planet is ocean and holds nothing.
    await rm(out, { recursive: true, force: true });
    await mkdir(out, { recursive: true });

    let kept = 0;
    let ambiguous = 0;
    let unplaceable = 0;
    let written = 0;
    let bytes = 0;

    for (let slice = 0; slice < slices; slice++) {
        const counting = slice === 0;      // totals are the same every time round
        const shards = new Map();

        await eachRow(dumps, (row) => {
            const region = regionOf(row, lookup);
            const text = describe(row[field.name], region);
            if (repeated.has(fingerprint(text))) { if (counting) ambiguous++; return; }

            const latitude = Number(row[field.latitude]);
            const longitude = Number(row[field.longitude]);
            if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
                if (counting) unplaceable++;
                return;
            }

            let shard;
            try {
                shard = GPC.cell(GPC.encode(latitude, longitude), level);
            } catch {
                if (counting) unplaceable++;   // reserved, or outside the domain
                return;
            }

            if (fingerprint(shard) % slices !== slice) return;

            if (!shards.has(shard)) shards.set(shard, { regions: new Map(), landmarks: [] });
            const bucket = shards.get(shard);
            if (!bucket.regions.has(region)) bucket.regions.set(region, bucket.regions.size);

            bucket.landmarks.push([
                row[field.name],
                // Five decimals is about a metre. The box this feeds is
                // kilometres across, and the digits past that are bytes in
                // every reader's cache.
                Math.round(latitude * 1e5) / 1e5,
                Math.round(longitude * 1e5) / 1e5,
                bucket.regions.get(region),
                KINDS[row[field.class]],
            ]);
            kept++;
        });

        for (const [shard, bucket] of shards) {
            bucket.landmarks.sort((a, b) => a[0].localeCompare(b[0]));
            const json = JSON.stringify({
                regions: [...bucket.regions.keys()],
                landmarks: bucket.landmarks,
            });
            await writeFile(path.join(out, `${shard}.json`), json, 'utf8');
            bytes += Buffer.byteLength(json);
            written++;
        }
    }

    // The shards say how they were cut. A reader has to know the level to work
    // out which files a box reaches into, and one that assumes a level the
    // build did not use asks for names that do not exist -- which does not look
    // like a broken deployment, it looks like a place with no landmarks near
    // it. Written beside the data so the two cannot drift apart.
    await writeFile(
        path.join(out, 'manifest.json'),
        // `built` is what tells a held copy from a current one. Shard names do
        // not change between builds, only their contents, so without a stamp a
        // cache-first reader serves last year's landmarks for as long as the
        // browser keeps them.
        JSON.stringify({
            level,
            shards: written,
            landmarks: kept,
            built: new Date().toISOString(),
        }),
        'utf8',
    );

    return { kept, ambiguous, unplaceable, shards: written, bytes };
}

// ── command line ───────────────────────────────────────────────────────────

function argument(name, fallback) {
    const at = process.argv.indexOf(`--${name}`);
    return at === -1 ? fallback : process.argv[at + 1];
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
    const geonames = argument('geonames');
    if (!geonames) {
        console.error('usage: node scripts/build-landmarks.mjs --geonames <dir> [--out <dir>]');
        process.exit(2);
    }
    const out = argument('out', 'landmarks');
    const level = Number(argument('level', '4'));
    const slices = Number(argument('slices', '8'));

    // The world dump contains every country dump, so taking both would file
    // those countries twice. Whole world if it is there, per-country otherwise.
    const { readdir } = await import('node:fs/promises');
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
    const report = await build({ geonames, out, dumps, level, slices });
    const mb = (report.bytes / 1048576).toFixed(1);

    console.log(
        `from ${dumps.length} dump(s) at level ${level}, ${slices} slice(s), `
            + `in ${((Date.now() - started) / 1000).toFixed(1)}s`,
    );
    console.log(`  kept        ${report.kept.toLocaleString('en')}`);
    console.log(`  ambiguous   ${report.ambiguous.toLocaleString('en')}  (name not unique in its region)`);
    console.log(`  unplaceable ${report.unplaceable.toLocaleString('en')}`);
    console.log(`  shards      ${report.shards.toLocaleString('en')}  ${mb} MB total`);
}
