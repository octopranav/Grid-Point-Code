// Copyright 2017 Pranavkumar Patel
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Turns tokens.json into the files each platform actually reads.
//
//   node design/build-tokens.mjs            write every target
//   node design/build-tokens.mjs --check    write nothing; fail if a target has drifted
//
// The check mode is what runs in continuous integration. A generated file that
// someone edited by hand is a fork of the design system that nobody announced,
// and it will be silently undone the next time this runs, so the build refuses
// to go green while one exists.
//
// No dependencies, in keeping with the rest of the project.

import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, '..');
const CHECK = process.argv.includes('--check');

const tokens = JSON.parse(readFileSync(join(HERE, 'tokens.json'), 'utf8'));

// tokens.json documents itself in $comment keys. They are for the reader of
// that file and belong in none of the output.
const real = (object) =>
    Object.fromEntries(Object.entries(object).filter(([key]) => key !== '$comment'));

const BANNER = (source) =>
    `Generated from ${source} by design/build-tokens.mjs. Do not edit by hand.`;

// ── the stylesheet the website imports ───────────────────────────────────────

function css() {
    const { colour, level, space, radius, type } = tokens;
    const light = real(colour.light);
    const dark = real(colour.dark);

    const themed = (palette, tints) => [
        ...Object.entries(palette).map(([name, value]) => `  --${name}: ${value};`),
        ...tints.tint.map((value, index) => `  --level-${index + 1}: ${value};`),
        ...tints.ink.map((value, index) => `  --level-${index + 1}-ink: ${value};`),
    ].join('\n');

    const constants = [
        ...Object.entries(real(space)).map(([step, value]) => `  --space-${step}: ${value};`),
        '',
        ...Object.entries(real(radius)).map(([name, value]) => `  --radius-${name}: ${value};`),
        '',
        ...Object.entries(real(type.family)).map(([name, value]) => `  --font-${name}: ${value};`),
        `  --measure: ${type.measure};`,
        `  --code-tracking: ${type.code['letter-spacing']};`,
        '',
        ...Object.entries(real(type.scale)).flatMap(([name, face]) => [
            `  --text-${name}-size: ${face.size};`,
            `  --text-${name}-line: ${face.line};`,
            `  --text-${name}-weight: ${face.weight};`,
        ]),
    ].join('\n');

    // The three blocks below are one rule with three ways of being reached, and
    // the order matters. A viewer has three states, not two: an explicit choice
    // stamps the root element, and the default setting stamps nothing at all,
    // so the bare :root has to carry a complete palette, the media query may
    // only override, and it is guarded so that choosing light beats a dark
    // operating system. Defining any colour solely inside one of the last two
    // blocks is the bug that renders one theme's text on the other's paper.
    return `/* ${BANNER('design/tokens.json')} */

:root {
${themed(light, level.light)}

${constants}
}

@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) {
${themed(dark, level.dark)}
  }
}

:root[data-theme="dark"] {
${themed(dark, level.dark)}
}
`;
}

// ── the Android resources, for when that application starts ──────────────────

const androidName = (name) => name.replace(/-/g, '_');
const androidColours = (palette, tints) => `<?xml version="1.0" encoding="utf-8"?>
<!-- ${BANNER('design/tokens.json')} -->
<resources>
${Object.entries(palette).map(([name, value]) => `    <color name="${androidName(name)}">${value}</color>`).join('\n')}

${tints.tint.map((value, index) => `    <color name="level_${index + 1}">${value}</color>`).join('\n')}

${tints.ink.map((value, index) => `    <color name="level_${index + 1}_ink">${value}</color>`).join('\n')}
</resources>
`;

const androidDimens = () => `<?xml version="1.0" encoding="utf-8"?>
<!-- ${BANNER('design/tokens.json')} -->
<resources>
${Object.entries(real(tokens.space)).map(([step, value]) => `    <dimen name="space_${step}">${value.replace('px', 'dp')}</dimen>`).join('\n')}

${Object.entries(real(tokens.radius)).map(([name, value]) => `    <dimen name="radius_${name}">${value.replace('px', 'dp')}</dimen>`).join('\n')}

${Object.entries(real(tokens.type.scale)).map(([name, face]) => `    <dimen name="text_${androidName(name)}">${face.size.replace('px', 'sp')}</dimen>`).join('\n')}
</resources>
`;

const kotlinName = (name) =>
    name.replace(/-(.)/g, (_, character) => character.toUpperCase());

const compose = () => {
    const light = real(tokens.colour.light);
    const dark = real(tokens.colour.dark);
    const scheme = (palette, tints, suffix) => [
        ...Object.entries(palette).map(([name, value]) =>
            `val ${kotlinName(name)}${suffix} = Color(0xFF${value.slice(1).toUpperCase()})`),
        ...tints.tint.map((value, index) =>
            `val level${index + 1}${suffix} = Color(0xFF${value.slice(1).toUpperCase()})`),
        ...tints.ink.map((value, index) =>
            `val level${index + 1}Ink${suffix} = Color(0xFF${value.slice(1).toUpperCase()})`),
    ].join('\n');

    return `// ${BANNER('design/tokens.json')}

package ca.pranavpatel.algo.gridpointcode.design

import androidx.compose.ui.graphics.Color

${scheme(light, tokens.level.light, 'Light')}

${scheme(dark, tokens.level.dark, 'Dark')}
`;
};

// ── write, or check ──────────────────────────────────────────────────────────

/**
 * Every level tint must carry its own ink at 4.5:1.
 *
 * This is checked rather than trusted because it is exactly the kind of thing
 * that looks fine and is not: an even ramp from prussian to the ground has to
 * pass through a band of middle lightness that is too light for the light ink
 * and too dark for the dark one at the same time, and whichever level lands
 * inside it fails silently. Three did, on the live site, until an audit said so.
 *
 * The ramp now steps across that band rather than into it. If a tint is ever
 * edited back into it, this says so here rather than leaving it to be found by
 * somebody who could not read the page.
 */
function contrastHolds() {
    const channel = (c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
    const luminance = (hex) => {
        const [r, g, b] = [1, 3, 5].map((i) => channel(parseInt(hex.slice(i, i + 2), 16) / 255));
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    };
    const ratio = (a, b) => {
        const [x, y] = [luminance(a), luminance(b)].sort((m, n) => n - m);
        return (x + 0.05) / (y + 0.05);
    };

    const WANTED = 4.5;
    const failures = [];

    for (const theme of ['light', 'dark']) {
        const { tint, ink } = tokens.level[theme];
        tint.forEach((colour, i) => {
            const measured = ratio(colour, ink[i]);
            if (measured < WANTED) {
                failures.push(
                    `  ${theme} level ${i + 1}: ${colour} on ${ink[i]}`
                    + ` is ${measured.toFixed(2)}:1, wanted ${WANTED}`,
                );
            }
        });
    }

    if (failures.length > 0) {
        console.error('Level tints that type cannot be read on:');
        for (const line of failures) console.error(line);
        console.error('');
        console.error('A tint can fail against both inks at once -- see the'
            + ' note in tokens.json. Move it out of the middle band rather'
            + ' than looking for a better ink.');
        process.exit(1);
    }
}

contrastHolds();

const targets = [
    ['web/src/styles/tokens.css', css()],
    ['design/generated/android/values/colors.xml', androidColours(real(tokens.colour.light), tokens.level.light)],
    ['design/generated/android/values-night/colors.xml', androidColours(real(tokens.colour.dark), tokens.level.dark)],
    ['design/generated/android/values/dimens.xml', androidDimens()],
    ['design/generated/android/Palette.kt', compose()],
];

let drifted = 0;

for (const [path, contents] of targets) {
    const absolute = join(ROOT, path);
    const current = existsSync(absolute) ? readFileSync(absolute, 'utf8') : null;

    // Written and compared with newlines exactly as they are here, so a
    // checkout that translated line endings does not read as a design change.
    const same = current !== null && current.replace(/\r\n/g, '\n') === contents;

    if (CHECK) {
        if (!same) {
            console.error(current === null
                ? `missing:  ${path}`
                : `drifted:  ${path}`);
            drifted += 1;
        }
        continue;
    }

    if (same) {
        console.log(`unchanged ${path}`);
        continue;
    }

    mkdirSync(dirname(absolute), { recursive: true });
    writeFileSync(absolute, contents);
    console.log(`wrote     ${path}`);
}

if (CHECK) {
    if (drifted > 0) {
        console.error(`\n${drifted} generated file${drifted === 1 ? '' : 's'} out of date. Run: node ${relative(ROOT, fileURLToPath(import.meta.url)).replace(/\\/g, '/')}`);
        process.exit(1);
    }
    console.log(`${targets.length} generated files match design/tokens.json`);
}
