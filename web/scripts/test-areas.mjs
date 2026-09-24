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

// A link to an area opens as that area.
//
//   node scripts/test-areas.mjs
//
// The app shares an area as a link here holding the cell, fewer than ten
// characters, and before this the playground called that a code of the wrong
// length. The edges below are the ones the app draws for the same cell, so the
// two cannot come to disagree about where an area is.

import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { build } from 'esbuild';

const web = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

/** The browser module, bundled here with the library it reads. */
async function browserModule() {
    const { outputFiles } = await build({
        entryPoints: [path.join(web, 'src', 'lib', 'levels.ts')],
        bundle: true,
        format: 'esm',
        platform: 'node',
        write: false,
        logLevel: 'silent',
    });
    return import(`data:text/javascript;base64,${Buffer.from(outputFiles[0].text, 'utf8').toString('base64')}`);
}

const { areaOf } = await browserModule();

let failures = 0;
function check(what, ok) {
    console.log(`${ok ? 'ok  ' : 'FAIL'}  ${what}`);
    if (!ok) failures += 1;
}

const near = (a, b) => Math.abs(a - b) < 1e-9;

const district = areaOf('G3RJM');
check('a five-character cell is a level-5 area', district?.level === 5 && district.prefix === 'G3RJM');
check('its edges are the ones the app draws',
    near(district.south, 43.632) && near(district.west, -79.392) && near(district.north, 43.704) && near(district.east, -79.296));
check('the specification\'s example lies inside it',
    43.650006 > district.south && 43.650006 < district.north && -79.380004 > district.west && -79.380004 < district.east);
check('a cell is read as a code is read', areaOf('g3rjm')?.prefix === 'G3RJM' && areaOf('G3RJMO')?.prefix === 'G3RJM0');
check('ten characters is a code, not an area', areaOf('G3RJM98NM9') === null);
check('the hash marks a code', areaOf('#G3RJM') === null);
check('the reserved range names nowhere', areaOf('XG3') === null);
check('a symbol outside the alphabet is not a cell', areaOf('G3RJQ') === null);
check('nothing is not an area', areaOf('') === null && areaOf('   ') === null);

if (failures) {
    console.error(`\n${failures} check(s) failed`);
    process.exit(1);
}
console.log('\na link to an area opens as the area the app shared');
