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

// When each page last changed, for the sitemap's `lastmod`.
//
// A build cannot tell by itself: every page it writes is new to it. So each
// deployment publishes what every page said, as a fingerprint, beside the date
// that fingerprint was first seen, and the next build reads that back. A page
// whose fingerprint matches keeps its date. A page whose fingerprint differs, or
// that was not there before, is dated the day of the build.
//
// The alternative, stamping every page with the build date, tells a crawler that
// two thousand pages changed whenever one did. A date that is wrong that often is
// one a search engine learns to ignore, which is why the sitemap had none.
//
// What counts as a change is what a reader would call one: the title, the
// description a search result shows, and the words inside <main>. Not the
// header or footer every page shares, not scripts or styles, and not the hashed
// file names of assets, which move when any stylesheet does. Otherwise a new
// footer line would redate every page on the site.

import { createHash } from 'node:crypto';

/** Published at the root of the site, beside sitemap.xml. */
export const STATE = 'lastmod.json';

const DATE = /^\d{4}-\d{2}-\d{2}$/;

/** A short fingerprint of what a page says to a reader. */
export function fingerprint(html) {
    const title = /<title>([\s\S]*?)<\/title>/i.exec(html)?.[1] ?? '';
    const description = /<meta\s+name="description"\s+content="([^"]*)"/i.exec(html)?.[1] ?? '';
    const body = /<main\b[^>]*>([\s\S]*?)<\/main>/i.exec(html)?.[1]
        ?? /<body\b[^>]*>([\s\S]*)<\/body>/i.exec(html)?.[1]
        ?? html;
    const words = body
        .replace(/<(script|style|template)\b[\s\S]*?<\/\1>/gi, ' ')
        .replace(/<[^>]*>/g, ' ')
        .replace(/\s+/g, ' ')
        .trim();
    return createHash('sha256')
        .update([title.trim(), description, words].join('\n'))
        .digest('hex')
        .slice(0, 16);
}

/**
 * The published state, or an empty one for anything that is not a state.
 *
 * Entries are checked one by one, so a single malformed line costs that page
 * its date and nothing else.
 */
export function readState(json) {
    const pages = {};
    for (const [page, entry] of Object.entries(json?.pages ?? {})) {
        if (!Array.isArray(entry) || entry.length !== 2) continue;
        const [print, date] = entry;
        if (typeof print === 'string' && typeof date === 'string' && DATE.test(date)) {
            pages[page] = [print, date];
        }
    }
    return pages;
}

/**
 * Each page's fingerprint and date, given what the last deployment published.
 *
 * `current` maps a page to its fingerprint now. A page that is gone is dropped,
 * so a page that comes back later is dated the day it returns.
 */
export function dated(current, previous, today) {
    if (!DATE.test(today)) throw new Error(`not a date: ${today}`);
    const pages = {};
    for (const page of Object.keys(current).sort()) {
        const print = current[page];
        const before = previous[page];
        pages[page] = before && before[0] === print ? before : [print, today];
    }
    return pages;
}
