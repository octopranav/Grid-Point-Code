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

export async function build({ geonames, out, dumps, level }) {
    const lookup = await tables(geonames);

    // Pass one counts the text we would print. A name that is not unique
    // within its own region makes an ambiguous string, and an ambiguous string
    // is the silent failure again by another route: Scarborough is a district
    // of Toronto and a place in the north, and a listener who picks the wrong
    // one is not told. Counted rather than assumed, and counted over the text
    // rather than the name, because the region is what disambiguates it.
    const seen = new Map();
    await eachRow(dumps, (row) => {
        const text = describe(row[field.name], regionOf(row, lookup));
        seen.set(text, (seen.get(text) ?? 0) + 1);
    });

    // Pass two keeps the ones that name exactly one place, and files each under
    // the level-3 cell that contains it -- the first three characters of its
    // own code. A level-3 cell is about 200 by 267 km, so the recovery box sits
    // inside one of them better than nine times in ten and never touches more
    // than four.
    const shards = new Map();
    let kept = 0, ambiguous = 0, unplaceable = 0;

    await eachRow(dumps, (row) => {
        const region = regionOf(row, lookup);
        const text = describe(row[field.name], region);
        if (seen.get(text) !== 1) { ambiguous++; return; }

        const latitude = Number(row[field.latitude]);
        const longitude = Number(row[field.longitude]);
        if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) { unplaceable++; return; }

        let shard;
        try {
            shard = GPC.cell(GPC.encode(latitude, longitude), level);
        } catch {
            unplaceable++;                    // reserved, or outside the domain
            return;
        }

        if (!shards.has(shard)) shards.set(shard, { regions: new Map(), landmarks: [] });
        const bucket = shards.get(shard);
        if (!bucket.regions.has(region)) bucket.regions.set(region, bucket.regions.size);

        bucket.landmarks.push([
            row[field.name],
            // Five decimals is about a metre. The box this feeds is kilometres
            // across, and the digits beyond are bytes in every reader's cache.
            Math.round(latitude * 1e5) / 1e5,
            Math.round(longitude * 1e5) / 1e5,
            bucket.regions.get(region),
            KINDS[row[field.class]],
        ]);
        kept++;
    });

    await rm(out, { recursive: true, force: true });
    await mkdir(out, { recursive: true });

    let bytes = 0;
    for (const [shard, bucket] of shards) {
        bucket.landmarks.sort((a, b) => a[0].localeCompare(b[0]));
        const json = JSON.stringify({
            regions: [...bucket.regions.keys()],
            landmarks: bucket.landmarks,
        });
        await writeFile(path.join(out, `${shard}.json`), json, 'utf8');
        bytes += Buffer.byteLength(json);
    }

    // The shards say how they were cut. The reader has to know the level to
    // work out which files a box reaches into, and a reader that assumes one
    // while the build used another asks for names that do not exist -- which
    // does not look like a broken build, it looks like a place with no
    // landmarks near it. Written beside the data so the two cannot drift.
    await writeFile(
        path.join(out, 'manifest.json'),
        JSON.stringify({ level, shards: shards.size, landmarks: kept }),
        'utf8',
    );

    return { kept, ambiguous, unplaceable, shards: shards.size, bytes };
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
    const out = argument('out', 'public/landmarks');
    const level = Number(argument('level', '4'));

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
    const report = await build({ geonames, out, dumps, level });
    const mb = (report.bytes / 1048576).toFixed(1);

    console.log(`from ${dumps.length} dump(s) at level ${level} in ${((Date.now() - started) / 1000).toFixed(1)}s`);
    console.log(`  kept        ${report.kept.toLocaleString('en')}`);
    console.log(`  ambiguous   ${report.ambiguous.toLocaleString('en')}  (name not unique in its region)`);
    console.log(`  unplaceable ${report.unplaceable.toLocaleString('en')}`);
    console.log(`  shards      ${report.shards.toLocaleString('en')}  ${mb} MB total`);
}
