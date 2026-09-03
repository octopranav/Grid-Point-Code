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

// The two halves of the name index must fold a name the same way.
//
//   node scripts/test-search.mjs
//
// One `fold` sorts a quarter-gigabyte file; the other decides where to look in
// it. They live in different files, in different languages, and they are the
// one thing in this design that cannot be allowed to drift: a disagreement is
// not a wrong answer but a binary search landing in the wrong part of the file
// and confidently reporting that a place does not exist.
//
// So they are compared here, on the cases that would break them. If this ever
// fails, the index needs rebuilding as well as the code fixing -- the file on
// disk was sorted by the old rule.

import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { transform } from 'esbuild';

import { fold as buildFold } from './build-names.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const web = path.resolve(here, '..');

/** Names chosen because each one breaks a different simplification. */
const CASES = [
    'Dublin',
    'DUBLIN',
    '  Dublin  ',
    "Saint John's Point",              // an apostrophe nobody types
    'Trá Mhór',                        // accents, which the ascii name drops
    'Ó Briain',                        // a leading accented capital
    'Stratford-upon-Avon',             // hyphens become one space
    'St. Mary’s',                      // a curly apostrophe, and a stop
    'Baile Átha Cliath',
    '  ',                              // nothing at all
    '123 Main',                        // digits are kept
    'Kraków',
    'İstanbul',                        // a dotted capital I
    'Ωμέγα',                           // a script the fold cannot keep
    'A  double   space',
];

/**
 * The browser half, compiled and loaded here.
 *
 * In process rather than through a command: this only needs the types stripped,
 * and shelling out to do it means a child process whose arguments have to
 * survive a shell -- which on Windows is now refused outright.
 */
async function browserFold() {
    const source = await readFile(path.join(web, 'src', 'lib', 'search.ts'), 'utf8');
    const { code } = await transform(source, { loader: 'ts', format: 'esm' });

    // Loaded from memory, so nothing is written and nothing is left behind.
    const module = await import(
        `data:text/javascript;base64,${Buffer.from(code, 'utf8').toString('base64')}`
    );
    return CASES.map(module.fold);
}

const theirs = await browserFold();
const ours = CASES.map(buildFold);

let wrong = 0;
for (const [i, name] of CASES.entries()) {
    if (ours[i] !== theirs[i]) {
        console.error(
            `  ${JSON.stringify(name)}\n`
            + `    the builder folds to ${JSON.stringify(ours[i])}\n`
            + `    the browser folds to ${JSON.stringify(theirs[i])}`,
        );
        wrong += 1;
    }
}

if (wrong > 0) {
    console.error(
        `\n${wrong} name${wrong === 1 ? '' : 's'} fold differently in the two halves.`
        + '\nThe index is sorted by the builder\'s rule, so the browser would search'
        + '\nthe wrong part of the file and report the place missing.',
    );
    process.exit(1);
}

console.log(`the two halves fold all ${CASES.length} names identically`);
