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
//
// The Android app's launcher icon is the same four bars, written as vector
// layers rather than drawn as pixels: every Android the app runs on takes an
// adaptive icon, so one resolution-independent drawing covers every density.

import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { deflateSync } from 'node:zlib';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const web = 'web/public';
const res = 'android/app/src/main/res';
const wear = 'android/wear/src/main/res';
const car = 'android/automotive/src/main/res';

const tokens = JSON.parse(await readFile(path.join(here, 'tokens.json'), 'utf8'));

/** The four the masthead uses: coarsest, two steps in, two more, finest. */
const STEPS = [0, 3, 6, 9];

const tints = STEPS.map((i) => tokens.level.light.tint[i]);
const ground = tokens.colour.light.ground;
const night = tokens.colour.dark.ground;

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

/** CIE lightness, 0 to 100, of a colour written as hex. */
function lightness(hex) {
    const linear = channels(hex).map((value) => {
        const c = value / 255;
        return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
    });
    const y = 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2];
    return y > 216 / 24389 ? 116 * Math.cbrt(y) - 16 : (24389 / 27) * y;
}

/**
 * The launcher icon's drawing: the four bars on Android's adaptive canvas.
 *
 * The canvas is 108 units and a launcher shows the middle 72, masked to its own
 * shape, so the bars take the masked geometry of the installable web icon
 * measured against those 72: an installed site and an installed app look the
 * same on one home screen. The corners are the favicon's, in proportion.
 *
 * The monochrome layer is what Android 13 tints to the wallpaper when themed
 * icons are on. One colour cannot carry a ramp of tints, so the ramp is carried
 * in opacity instead, each bar as opaque as its tint is dark against the
 * ground, measured in lightness. The finest bar stays faint, as it is in colour.
 *
 * The watch's complication draws the same monochrome bars on a small canvas of
 * its own, which the watch face tints; `canvas` and `shown` set that size.
 */
function launcherLayer(monochrome, canvas = 108, shown = 72) {
    const gap = MASKED.gap * shown;
    const width = (MASKED.band * shown - gap * 3) / 4;
    const height = MASKED.tall * shown;
    const top = (canvas - height) / 2;
    const left = (canvas - MASKED.band * shown) / 2;
    const radius = (1.5 / 100) * shown;
    const n = (value) => Number(value.toFixed(3)).toString();

    const inkOf = (tint) => (lightness(ground) - lightness(tint)) / (lightness(ground) - lightness(tints[0]));
    const paths = tints.map((tint, index) => {
        const x = left + index * (width + gap);
        const d = `M${n(x + radius)},${n(top)}`
            + `h${n(width - 2 * radius)}a${n(radius)},${n(radius)} 0 0,1 ${n(radius)},${n(radius)}`
            + `v${n(height - 2 * radius)}a${n(radius)},${n(radius)} 0 0,1 -${n(radius)},${n(radius)}`
            + `h-${n(width - 2 * radius)}a${n(radius)},${n(radius)} 0 0,1 -${n(radius)},-${n(radius)}`
            + `v-${n(height - 2 * radius)}a${n(radius)},${n(radius)} 0 0,1 ${n(radius)},-${n(radius)}z`;
        const fill = monochrome
            ? `android:fillColor="#FFFFFFFF"\n        android:fillAlpha="${n(inkOf(tint))}"`
            : `android:fillColor="${tint}"`;
        return `    <path\n        ${fill}\n        android:pathData="${d}" />`;
    });

    return Buffer.from([
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!-- Generated from design/tokens.json by design/build-icons.mjs. Do not edit by hand. -->',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        `    android:width="${canvas}dp"`,
        `    android:height="${canvas}dp"`,
        `    android:viewportWidth="${canvas}"`,
        `    android:viewportHeight="${canvas}">`,
        ...paths,
        '</vector>',
        '',
    ].join('\n'), 'utf8');
}

/** The adaptive icon: the ground behind, the bars in front, and the bars alone for a themed icon. */
function launcher() {
    return Buffer.from([
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!-- Generated by design/build-icons.mjs. Do not edit by hand. -->',
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">',
        '    <background android:drawable="@color/launcher_ground" />',
        '    <foreground android:drawable="@drawable/launcher_bars" />',
        '    <monochrome android:drawable="@drawable/launcher_bars_monochrome" />',
        '</adaptive-icon>',
        '',
    ].join('\n'), 'utf8');
}

/**
 * The colours the icon and the starting window need, as resources.
 *
 * The icon's ground is fixed: an icon is the same in either theme. The window's
 * ground follows the theme, because it is what Android paints while the app is
 * starting, and on Android 12 and later the icon is shown on it. Anything but
 * the page's own ground is a flash of the wrong colour before the first frame,
 * the same reason the web manifest's background colour is the ground.
 */
function launcherColours(dark) {
    const lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!-- Generated from design/tokens.json by design/build-icons.mjs. Do not edit by hand. -->',
        '<resources>',
    ];
    if (!dark) lines.push(`    <color name="launcher_ground">${ground}</color>`);
    lines.push(`    <color name="window_ground">${dark ? night : ground}</color>`, '</resources>', '');
    return Buffer.from(lines.join('\n'), 'utf8');
}

const FILES = [
    [`${web}/site.webmanifest`, manifest],
    [`${web}/favicon.svg`, () => Buffer.from(svg(), 'utf8')],
    [`${web}/icon-192.png`, () => png(192)],
    [`${web}/icon-512.png`, () => png(512)],
    [`${web}/apple-touch-icon.png`, () => png(180)],
    [`${res}/mipmap-anydpi/ic_launcher.xml`, launcher],
    [`${res}/drawable/launcher_bars.xml`, () => launcherLayer(false)],
    [`${res}/drawable/launcher_bars_monochrome.xml`, () => launcherLayer(true)],
    [`${res}/values/launcher.xml`, () => launcherColours(false)],
    [`${res}/values-night/launcher.xml`, () => launcherColours(true)],
    // The watch app is the same app on the store, with the same icon.
    [`${wear}/mipmap-anydpi/ic_launcher.xml`, launcher],
    [`${wear}/drawable/launcher_bars.xml`, () => launcherLayer(false)],
    [`${wear}/drawable/launcher_bars_monochrome.xml`, () => launcherLayer(true)],
    [`${wear}/values/launcher.xml`, () => launcherColours(false)],
    // The complication's mark, on any watch face, in the face's own colour.
    [`${wear}/drawable/complication_bars.xml`, () => launcherLayer(true, 24, 22)],
    // And the car's own app, on the car's launcher.
    [`${car}/mipmap-anydpi/ic_launcher.xml`, launcher],
    [`${car}/drawable/launcher_bars.xml`, () => launcherLayer(false)],
    [`${car}/drawable/launcher_bars_monochrome.xml`, () => launcherLayer(true)],
    [`${car}/values/launcher.xml`, () => launcherColours(false)],
];

const digest = (body) => createHash('sha256').update(body).digest('hex').slice(0, 12);

async function main() {
    const checking = process.argv.includes('--check');
    let wrong = 0;

    for (const [name, make] of FILES) {
        const wanted = make();
        const file = path.join(root, name);

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

        await mkdir(path.dirname(file), { recursive: true });
        await writeFile(file, wanted);
        console.log(`  ${name.padEnd(62)} ${String(wanted.length).padStart(6)} bytes`);
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
