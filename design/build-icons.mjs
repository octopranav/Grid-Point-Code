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

// The icons, drawn from the same tokens as everything else.
//
//   node design/build-icons.mjs [--check]
//
// A code is ten characters and the full mark is ten cells, which is legible on
// a page and illegible at sixteen pixels. The masthead already answered this:
// four bars at levels 1, 4, 7 and 10 -- the mark reduced until only its
// structure is left, a ramp from the world to a doorway. That is what survives
// being shrunk, so that is the icon.
//
// **No image library.** PNG is a signature, three chunks and a CRC, and the
// deflate it needs is in the standard library. A dependency here would be a
// dependency in a project that ships none, for four rectangles.
//
// `--check` regenerates and compares, so CI can prove the committed icons are
// what the tokens currently say. Deflate is deterministic for the same input,
// so the comparison is byte for byte.

import { createHash } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { deflateSync } from 'node:zlib';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const out = path.join(root, 'web', 'public');

const tokens = JSON.parse(await readFile(path.join(here, 'tokens.json'), 'utf8'));

/** The four the masthead uses: coarsest, two steps in, two more, finest. */
const STEPS = [0, 3, 6, 9];

const tints = STEPS.map((i) => tokens.level.light.tint[i]);
const ground = '#E9EEEF';

/**
 * Where the bars sit inside the square.
 *
 * Kept well inside the edge because an installed icon is masked to whatever
 * shape the platform likes -- a circle, a squircle, a rounded square -- and
 * anything near the corner is the first thing cropped. The safe area is the
 * middle 80%; this uses rather less than that and loses nothing by it.
 */
const MASKED = { band: 0.56, tall: 0.52, gap: 0.035 };

/**
 * The favicon is never masked, so it can use the whole square -- and it needs
 * to.
 *
 * Four bars and three gaps is seven features across, and at sixteen pixels the
 * safe-area band gives them nine pixels to live in: two of the three gaps close
 * up and the mark reads as two blocks rather than four bars. Widened to most of
 * the square, the same seven features get fourteen pixels and separate. The
 * geometry differs between the two because the constraints do, not because the
 * mark does.
 */
const FLAT = { band: 0.78, tall: 0.58, gap: 0.05 };

function channels(hex) {
    const value = hex.replace('#', '');
    return [
        parseInt(value.slice(0, 2), 16),
        parseInt(value.slice(2, 4), 16),
        parseInt(value.slice(4, 6), 16),
    ];
}

/** The icon as raw RGBA, drawn a pixel at a time. Four rectangles; no library. */
function draw(size, shape) {
    const pixels = Buffer.alloc(size * size * 4);
    const [br, bg, bb] = channels(ground);
    for (let i = 0; i < size * size; i += 1) {
        pixels[i * 4] = br;
        pixels[i * 4 + 1] = bg;
        pixels[i * 4 + 2] = bb;
        pixels[i * 4 + 3] = 255;
    }

    const gap = shape.gap * size;
    const width = (shape.band * size - gap * 3) / 4;
    const height = shape.tall * size;
    const top = Math.round((size - height) / 2);
    const left = (size - shape.band * size) / 2;

    tints.forEach((tint, index) => {
        const [r, g, b] = channels(tint);
        const from = Math.round(left + index * (width + gap));
        const to = Math.round(from + width);
        for (let y = top; y < top + height; y += 1) {
            for (let x = from; x < to; x += 1) {
                const at = (y * size + x) * 4;
                pixels[at] = r;
                pixels[at + 1] = g;
                pixels[at + 2] = b;
                pixels[at + 3] = 255;
            }
        }
    });

    return pixels;
}

const CRC = (() => {
    const table = new Int32Array(256);
    for (let n = 0; n < 256; n += 1) {
        let c = n;
        for (let k = 0; k < 8; k += 1) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
        table[n] = c;
    }
    return (buffer) => {
        let c = -1;
        for (const byte of buffer) c = table[(c ^ byte) & 0xff] ^ (c >>> 8);
        return (c ^ -1) >>> 0;
    };
})();

function chunk(type, body) {
    const length = Buffer.alloc(4);
    length.writeUInt32BE(body.length);
    const tagged = Buffer.concat([Buffer.from(type, 'latin1'), body]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(CRC(tagged));
    return Buffer.concat([length, tagged, crc]);
}

function png(size, shape = MASKED) {
    const pixels = draw(size, shape);

    // Each scanline carries a filter byte. Zero means "stored as is", which for
    // flat rectangles compresses perfectly well and keeps this readable.
    const raw = Buffer.alloc(size * (size * 4 + 1));
    for (let y = 0; y < size; y += 1) {
        raw[y * (size * 4 + 1)] = 0;
        pixels.copy(raw, y * (size * 4 + 1) + 1, y * size * 4, (y + 1) * size * 4);
    }

    const header = Buffer.alloc(13);
    header.writeUInt32BE(size, 0);
    header.writeUInt32BE(size, 4);
    header[8] = 8;          // bits per channel
    header[9] = 6;          // truecolour with alpha
    header[10] = 0;         // deflate
    header[11] = 0;         // adaptive filtering
    header[12] = 0;         // no interlace

    return Buffer.concat([
        Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
        chunk('IHDR', header),
        chunk('IDAT', deflateSync(raw, { level: 9 })),
        chunk('IEND', Buffer.alloc(0)),
    ]);
}

/** The same four bars, but resolution independent, for browsers that take it. */
function svg() {
    const gap = FLAT.gap * 100;
    const width = (FLAT.band * 100 - gap * 3) / 4;
    const height = FLAT.tall * 100;
    const top = (100 - height) / 2;
    const left = (100 - FLAT.band * 100) / 2;

    const bars = tints.map((tint, index) => {
        const x = (left + index * (width + gap)).toFixed(2);
        return `  <rect x="${x}" y="${top.toFixed(2)}" width="${width.toFixed(2)}"`
            + ` height="${height.toFixed(2)}" rx="1.5" fill="${tint}"/>`;
    });

    return [
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">',
        `  <rect width="100" height="100" fill="${ground}"/>`,
        ...bars,
        '</svg>',
        '',
    ].join('\n');
}

/**
 * The manifest, generated rather than written, because two of its fields are
 * colours and colours have one home.
 *
 * `background_color` is what a platform paints while the app is starting, so it
 * is the page's own ground: anything else is a flash of the wrong colour before
 * the first paint. Both icons are declared `maskable` as well as `any` because
 * they are drawn inside the safe area, which is the whole reason the masked
 * geometry is narrower than the favicon's.
 */
function manifest() {
    const body = {
        id: '/',
        name: 'Grid Point Code',
        short_name: 'GPC',
        description:
            'Ten characters for any place on Earth. Encode, decode and correct '
            + 'them with no network and no account.',
        start_url: '/',
        scope: '/',
        display: 'standalone',
        background_color: ground,
        theme_color: tints[0],
        categories: ['navigation', 'utilities'],
        icons: [
            { src: '/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any maskable' },
            { src: '/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any maskable' },
        ],
    };
    return Buffer.from(`${JSON.stringify(body, null, 4)}\n`, 'utf8');
}

const FILES = [
    ['site.webmanifest', manifest],
    ['favicon.svg', () => Buffer.from(svg(), 'utf8')],
    ['icon-192.png', () => png(192)],
    ['icon-512.png', () => png(512)],
    ['apple-touch-icon.png', () => png(180)],
];

const digest = (body) => createHash('sha256').update(body).digest('hex').slice(0, 12);

async function main() {
    const checking = process.argv.includes('--check');
    let wrong = 0;

    for (const [name, make] of FILES) {
        const wanted = make();
        const file = path.join(out, name);

        if (checking) {
            if (!existsSync(file)) {
                console.error(`${name} is missing`);
                wrong += 1;
                continue;
            }
            const found = await readFile(file);
            if (!found.equals(wanted)) {
                console.error(
                    `${name} is not what the tokens produce`
                    + ` (${digest(found)} on disk, ${digest(wanted)} expected)`,
                );
                wrong += 1;
            }
            continue;
        }

        await writeFile(file, wanted);
        console.log(`  ${name.padEnd(22)} ${String(wanted.length).padStart(6)} bytes`);
    }

    if (checking) {
        if (wrong) {
            console.error(`${wrong} icon${wrong === 1 ? '' : 's'} out of date. Run: npm run icons`);
            process.exit(1);
        }
        console.log(`${FILES.length} icons match design/tokens.json`);
    }
}

await main();
