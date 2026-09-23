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

// The cell is drawn in inks its basemap can carry.
//
//   node scripts/test-basemap.mjs
//
// The drawing used to take the page theme's brass whatever map was under it,
// which put the light theme's brass on fiord at 1.4:1. Nothing failed: the cell
// was drawn, in the right place, and nobody could see it.

import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { build } from 'esbuild';

const here = path.dirname(fileURLToPath(import.meta.url));
const web = path.resolve(here, '..');
const tokens = JSON.parse(await readFile(path.resolve(web, '..', 'design', 'tokens.json'), 'utf8'));

/**
 * The browser module, bundled here with the tokens it reads.
 *
 * Bundled rather than only stripped of its types, because it imports
 * design/tokens.json and a module loaded from memory has no directory to find
 * that file from.
 */
async function browserModule() {
    const { outputFiles } = await build({
        entryPoints: [path.join(web, 'src', 'lib', 'basemap.ts')],
        bundle: true,
        format: 'esm',
        write: false,
        logLevel: 'silent',
    });
    const code = outputFiles[0].text;
    return import(`data:text/javascript;base64,${Buffer.from(code, 'utf8').toString('base64')}`);
}

const { BASEMAPS, resolveBasemap, inksFor } = await browserModule();

/**
 * Each style's background layer, as the provider serves it. These are the
 * provider's colours and can move without this repository changing, so a
 * failure here that no edit explains means measuring them again.
 */
const BACKGROUND = {
    positron: '#F2F3F0',
    bright: '#F8F4F0',
    liberty: '#F8F4F0',
    dark: '#0C0C0C',
    fiord: '#45516E',
};

/** The least a line on a map needs to be seen: the non-text contrast floor. */
const FLOOR = 3;

function luminance(hex) {
    const [r, g, b] = [1, 3, 5].map((at) => {
        const c = parseInt(hex.slice(at, at + 2), 16) / 255;
        return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
    });
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function ratio(a, b) {
    const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
    return (hi + 0.05) / (lo + 0.05);
}

let failures = 0;
function check(what, ok) {
    console.log(`${ok ? 'ok  ' : 'FAIL'}  ${what}`);
    if (!ok) failures += 1;
}

const style = (id) => resolveBasemap(id, false).style.split('/').pop();

check('matching the theme is positron by day', style('auto') === 'positron' && !resolveBasemap('auto', false).dark);
check('matching the theme is fiord by night',
    resolveBasemap('auto', true).style.endsWith('/fiord') && resolveBasemap('auto', true).dark);
check('a chosen map is the same in either theme', BASEMAPS.filter((each) => each.id !== 'auto').every(
    ({ id }) => JSON.stringify(resolveBasemap(id, false)) === JSON.stringify(resolveBasemap(id, true))));
check('every named map says whether it is dark',
    BASEMAPS.every(({ id, dark }) => id === 'auto' ? dark === null : typeof dark === 'boolean'));
check('a light map is drawn in the light inks even in the dark theme',
    inksFor(resolveBasemap('positron', true).dark).brass === tokens.colour.light.brass);
check('a dark map is drawn in the dark inks even in the light theme',
    inksFor(resolveBasemap('fiord', false).dark).brass === tokens.colour.dark.brass);
check('the grid under the cursor is the same theme\'s soft ink',
    inksFor(false).soft === tokens.colour.light['ink-soft'] && inksFor(true).soft === tokens.colour.dark['ink-soft']);

for (const darkPage of [false, true]) {
    for (const { id } of BASEMAPS) {
        const map = resolveBasemap(id, darkPage);
        const background = BACKGROUND[map.style.split('/').pop()];
        const seen = ratio(inksFor(map.dark).brass, background);
        check(`${id} in the ${darkPage ? 'dark' : 'light'} theme carries the cell at ${seen.toFixed(2)}:1`,
            seen >= FLOOR);
    }
}

if (failures) {
    console.error(`\n${failures} check(s) failed`);
    process.exit(1);
}
console.log('\nthe cell is drawn in inks every basemap can carry, in either theme');
