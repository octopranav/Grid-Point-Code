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

// A sitemap date moves when a page changes, and only then.
//
//   node scripts/test-lastmod.mjs
//
// Both ways of getting this wrong are silent. Too sensitive, and every deploy
// redates the whole site, which is the build-date stamp this replaced. Not
// sensitive enough, and a page that changed keeps telling crawlers it did not.

import { dated, fingerprint, readState } from './lastmod.mjs';

let failures = 0;
function check(what, ok) {
    console.log(`${ok ? 'ok  ' : 'FAIL'}  ${what}`);
    if (!ok) failures += 1;
}

const page = ({
    title = 'Manek Chowk, Ahmedabad, Gujarat, India · #KDC8W-NRKD6 · Grid Point Code',
    description = 'The Grid Point Code for Manek Chowk is #KDC8W-NRKD6.',
    header = '<header><a href="/play">Play</a></header>',
    main = '<h1>Manek Chowk</h1><p>The code is <code>#KDC8W-NRKD6</code>.</p>',
    footer = '<footer>No cookies.</footer>',
    head = '<link rel="stylesheet" href="/_astro/Base.a1b2c3.css">',
} = {}) => `<!DOCTYPE html><html><head><title>${title}</title>`
    + `<meta name="description" content="${description}">${head}</head>`
    + `<body>${header}<main id="main">${main}</main>${footer}</body></html>`;

const base = fingerprint(page());

// What must not move a date.
check('the same page twice', fingerprint(page()) === base);
check('a new footer line', fingerprint(page({ footer: '<footer>No cookies. New line.</footer>' })) === base);
check('a changed header', fingerprint(page({ header: '<header><a href="/learn">Learn</a></header>' })) === base);
check('a renamed stylesheet', fingerprint(page({ head: '<link rel="stylesheet" href="/_astro/Base.ffffff.css">' })) === base);
check('markup and attributes around the same words', fingerprint(page({
    main: '<h1 class="x" data-astro-cid-abc>Manek Chowk</h1>\n  <p>The code is\n<code class="code">#KDC8W-NRKD6</code>.</p>',
})) === base);
check('a script or style inside main', fingerprint(page({
    main: '<h1>Manek Chowk</h1><script>var a = 1;</script><style>p{}</style><p>The code is <code>#KDC8W-NRKD6</code>.</p>',
})) === base);

// What must.
check('a changed title', fingerprint(page({ title: 'Manek Chowk, Ahmedabad · #KDC8W-NRKD6' })) !== base);
check('a changed description', fingerprint(page({ description: 'Said aloud as Manek Chowk NRKD6.' })) !== base);
check('changed words in main', fingerprint(page({ main: '<h1>Manek Chowk</h1><p>The code is <code>#KDC8W-NRKD7</code>.</p>' })) !== base);

// Dates.
const previous = readState({
    pages: {
        '/': ['aaaa', '2026-09-01'],
        '/spec': ['bbbb', '2026-09-02'],
        '/gone': ['cccc', '2026-09-03'],
        '/broken': ['dddd', 'yesterday'],
        '/short': ['eeee'],
    },
});
check('a malformed date is not read', !('/broken' in previous));
check('a malformed entry is not read', !('/short' in previous));

const now = dated({ '/': 'aaaa', '/spec': 'b2b2', '/new': 'ffff', '/broken': 'dddd' }, previous, '2026-09-17');
check('an unchanged page keeps its date', now['/'][1] === '2026-09-01');
check('a changed page is dated today', now['/spec'][1] === '2026-09-17' && now['/spec'][0] === 'b2b2');
check('a new page is dated today', now['/new'][1] === '2026-09-17');
check('a page with an unreadable date is dated today', now['/broken'][1] === '2026-09-17');
check('a page that is gone is dropped', !('/gone' in now));
check('nothing is read from something that is not a state', Object.keys(readState(null)).length === 0
    && Object.keys(readState({ pages: 'x' })).length === 0);

let refused = false;
try {
    dated({}, {}, '17 September');
} catch {
    refused = true;
}
check('a build date that is not a date is refused', refused);

if (failures) {
    console.error(`\n${failures} check(s) failed`);
    process.exit(1);
}
console.log('\na sitemap date moves when what a page says does, and not otherwise');
