// @ts-check
import { createReadStream, existsSync, statSync } from 'node:fs';
import { cp, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { defineConfig } from 'astro/config';
import { satteri } from '@astrojs/markdown-satteri';

import { STATE, dated, fingerprint, readState } from './scripts/lastmod.mjs';

// The site is a pile of static files and nothing else: no server, no database,
// no API. Everything a visitor asks of it is arithmetic their own browser can
// do, which is the format's claim made physical rather than merely stated.
//
// `site` is the real origin because absolute URLs end up in the sitemap, in
// social previews, and in canonical links, and a wrong one there is invisible
// until somebody else follows it.

// One origin, because it is written into three places that have to agree: the
// canonical link on every page, the sitemap, and the line in robots.txt that
// points at the sitemap. Three copies of a hostname is three chances to move
// two of them.
const ORIGIN = 'https://gridpointcode.com';

const here = path.dirname(fileURLToPath(import.meta.url));
const LANDMARKS = path.join(here, 'landmarks');

// A shard is named for a cell, or is the manifest. Anything else is not a file
// this serves -- which is also what keeps a request from walking out of the
// directory, since no amount of `..` matches this.
const SHARD = /^\/landmarks\/([0-9A-Z]{1,10}|manifest)\.json$/;

/**
 * Serve the landmark archive without putting it in `public/`.
 *
 * The archive is 82,000 files. Left in `public/` the dev server never finishes
 * starting -- it gave up after thirty seconds, every time, while the same tree
 * with the archive moved aside was answering in under five. The production
 * build never minded, which is what made it a trap: the site built and deployed
 * perfectly and could not be worked on.
 *
 * So the files live beside `public/` rather than inside it, and this hands them
 * out in development and copies them in at the end of a build. Two small pieces
 * of explicit plumbing in place of one directory that quietly cost the dev
 * server its start.
 */
function landmarks() {
    return {
        name: 'landmarks',
        hooks: {
            'astro:server:setup': ({ server, logger }) => {
                if (!existsSync(LANDMARKS)) {
                    logger.warn(
                        'No landmarks/ directory; the reference list will say so. '
                            + 'Build one with scripts/build-landmarks.mjs.',
                    );
                    return;
                }

                server.middlewares.use((req, res, next) => {
                    if (req.method !== 'GET' && req.method !== 'HEAD') return next();

                    const asked = (req.url ?? '').split('?')[0];
                    const match = SHARD.exec(asked);
                    if (!match) return next();

                    const file = path.join(LANDMARKS, `${match[1]}.json`);
                    if (!existsSync(file)) {
                        // Ocean, mostly. A real answer, and the reader's code
                        // already treats it as one.
                        res.statusCode = 404;
                        return res.end();
                    }

                    res.setHeader('Content-Type', 'application/json; charset=utf-8');
                    res.setHeader('Content-Length', statSync(file).size);
                    if (req.method === 'HEAD') return res.end();
                    createReadStream(file).pipe(res);
                });
            },

            'astro:build:done': async ({ dir, logger }) => {
                if (!existsSync(LANDMARKS)) {
                    logger.warn('No landmarks/ directory; building without the archive.');
                    return;
                }
                await cp(LANDMARKS, fileURLToPath(new URL('landmarks/', dir)), {
                    recursive: true,
                });
            },
        },
    };
}

const REPO = 'https://github.com/octopranav/Grid-Point-Code';

/**
 * Point the repository's own relative links at the repository.
 *
 * `SPEC.md` links to files beside it -- `reference/from_spec.py`, `test_data/`
 * -- which resolve when the document is read on GitHub and resolve to nothing
 * when it is read here. The document is not edited to suit the website; the
 * website sends those links where they were always going.
 */
const repositoryLinks = {
    name: 'repository-links',
    element: {
        filter: ['a'],
        visit(node) {
            const href = node.properties?.href;
            if (typeof href !== 'string') return;

            // The four READMEs each link to SPEC.md on GitHub, which was the
            // only place it could be read when they were written. It is read
            // here now, so a reader following that link should stay here rather
            // than being sent out to a raw markdown file. Same document, better
            // rendering, and the source keeps the link that works everywhere
            // else.
            const rendered = `${REPO}/blob/main/SPEC.md`;
            if (href === rendered || href.startsWith(`${rendered}#`)) {
                return {
                    ...node,
                    properties: { ...node.properties, href: href.replace(rendered, '/spec') },
                };
            }

            // Schemes, fragments and site-absolute paths are already right.
            if (/^([a-z][a-z0-9+.-]*:|#|\/)/i.test(href)) return;

            const directory = href.endsWith('/');
            const path = href.replace(/\/$/, '');
            return {
                ...node,
                properties: {
                    ...node.properties,
                    href: `${REPO}/${directory ? 'tree' : 'blob'}/main/${path}`,
                },
            };
        },
    },
};

/**
 * A header cell with nothing in it heads nothing.
 *
 * The specification's tables use an empty corner, which is a data cell wearing
 * the wrong tag -- and a screen reader announces it as a column heading all the
 * same. Fixed here rather than in `SPEC.md`, because the document is normative
 * and this is a rendering detail rather than a change to what it says.
 */
const emptyHeaders = {
    name: 'empty-headers',
    element: {
        filter: ['th'],
        visit(node) {
            // A header cell with nothing in it heads nothing, and is announced
            // as a column heading all the same. The specification's tables use
            // an empty corner, which is a data cell wearing the wrong tag.
            const words = (from) =>
                from.type === 'text'
                    ? from.value
                    : (from.children ?? []).map(words).join('');

            if (words(node).trim() !== '') return;

            // Returned rather than mutated: this pipeline takes the visitor's
            // return value as the replacement, and a node edited in place is
            // quietly discarded. Eight header cells stayed headers until this
            // was written the way the plugin beside it is.
            const { scope, ...rest } = node.properties ?? {};
            return { ...node, tagName: 'td', properties: rest };
        },
    },
};

/**
 * Let a wide table scroll inside its own box.
 *
 * The specification has tables that will not fit a phone -- the alias table,
 * the seam measurements -- and a table cannot scroll by itself. Each is wrapped
 * so the box scrolls and the page never does, which is the rule everywhere else
 * on this site.
 */
const scrollableTables = {
    name: 'scrollable-tables',
    element: {
        filter: ['table'],
        visit(node, ctx) {
            ctx.wrapNode(node, {
                type: 'element',
                tagName: 'div',
                // Focusable, because a region that scrolls sideways has to be
                // reachable by somebody who is not holding a mouse. Without this
                // the specification alone had eighteen tables a keyboard could
                // not scroll.
                properties: { className: ['table-scroll'], tabIndex: 0 },
                children: [],
            });
        },
    },
};

/**
 * A sitemap and a robots.txt, written from the pages that were actually built.
 *
 * Neither existed. Both were 404, which is worth more than it sounds: a machine
 * reading about this format finds the package registries, because those are
 * indexed, and the specification only if something leads it there. Nothing did.
 *
 * Written from `pages` rather than from a list kept by hand, so a route added
 * later is in the sitemap without anybody remembering to add it.
 *
 * Each page's `lastmod` is the day what it says last changed, not the build
 * date: the build date would tell a crawler that every page changed whenever
 * one did, and a date that is wrong that often is one it learns to ignore.
 * scripts/lastmod.mjs explains how a build can know, which is by reading back
 * what the last deployment published.
 *
 * The archive is disallowed. It is 82,000 shards and a name index, none of it
 * anything a search result should point at, and all of it expensive to crawl.
 */

/**
 * What the site currently deployed says each page said, and when.
 *
 * Asked of the live site, because that is the only place the last deployment's
 * answer exists. Not found means nothing has been published yet, and every page
 * starts from today. Any other failure stops a build on CI rather than guessing:
 * a guess would redate every page on the site, which is the one thing this is
 * for not doing. A build on a desk only warns, since it deploys nothing.
 */
async function published(logger) {
    try {
        const response = await fetch(`${ORIGIN}/${STATE}?at=${Date.now()}`, {
            cache: 'no-store',
            signal: AbortSignal.timeout(20_000),
        });
        if (response.status === 404) {
            logger.warn(`no ${STATE} is published yet; every page is dated today`);
            return {};
        }
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return readState(await response.json());
    } catch (error) {
        const reason = error instanceof Error ? error.message : String(error);
        if (process.env.CI) {
            throw new Error(`could not read ${ORIGIN}/${STATE} (${reason}), so every page would be redated`);
        }
        logger.warn(`could not read ${ORIGIN}/${STATE} (${reason}); dating every page today`);
        return {};
    }
}
function discovery() {
    return {
        name: 'discovery',
        hooks: {
            'astro:build:done': async ({ dir, pages, logger }) => {
                // `format: 'file'` and `trailingSlash: 'never'` mean the address
                // a reader sees has no suffix and no trailing slash. The
                // pathnames here arrive with neither, or with a slash on the
                // front, depending on the route, so both ends are trimmed.
                // Pages only. The per-country place files are routes too, and a
                // JSON file is not something to send a searcher to.
                const slugs = pages
                    .filter((page) => !/\.[a-z0-9]+$/i.test(page.pathname.replace(/\/+$/, '')))
                    .map((page) => page.pathname.replace(/^\/+/, '').replace(/\/+$/, ''))
                    .sort();

                // `format: 'file'` puts /play at play.html, and the front page
                // at index.html.
                const current = {};
                for (const slug of slugs) {
                    const file = fileURLToPath(new URL(`${slug || 'index'}.html`, dir));
                    current[`/${slug}`] = fingerprint(await readFile(file, 'utf8'));
                }
                const today = new Date().toISOString().slice(0, 10);
                const state = dated(current, await published(logger), today);

                const sitemap = [
                    '<?xml version="1.0" encoding="UTF-8"?>',
                    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">',
                    ...Object.entries(state).map(([page, [, date]]) =>
                        `  <url><loc>${ORIGIN}${page}</loc><lastmod>${date}</lastmod></url>`),
                    '</urlset>',
                    '',
                ].join('\n');

                const robots = [
                    'User-agent: *',
                    'Allow: /',
                    '',
                    '# The landmark archive and the name index are data the site reads,',
                    '# not pages anybody should be sent to. Together they are most of',
                    '# what is deployed and none of what is worth indexing.',
                    'Disallow: /landmarks/',
                    'Disallow: /names/',
                    '',
                    `Sitemap: ${ORIGIN}/sitemap.xml`,
                    '',
                ].join('\n');

                const record = {
                    about: [
                        'What each page said, as a fingerprint, and the day that was first seen.',
                        'Read back by the next build to date the sitemap. See web/scripts/lastmod.mjs.',
                    ],
                    pages: state,
                };

                await writeFile(fileURLToPath(new URL('sitemap.xml', dir)), sitemap, 'utf8');
                await writeFile(fileURLToPath(new URL(STATE, dir)), JSON.stringify(record), 'utf8');
                await writeFile(fileURLToPath(new URL('robots.txt', dir)), robots, 'utf8');
                const moved = Object.values(state).filter(([, date]) => date === today).length;
                logger.info(
                    `sitemap.xml lists ${slugs.length} pages, ${moved} dated today; robots.txt written`,
                );
            },
        },
    };
}

export default defineConfig({
    site: ORIGIN,
    trailingSlash: 'never',
    build: {
        // A page at /play rather than /play/index.html, so a printed or spoken
        // URL has no trailing slash to remember.
        format: 'file',
    },
    devToolbar: {
        enabled: false,
    },
    markdown: {
        // Astro slugs headings the way GitHub does, so the ninety-six
        // `#fragment` links already inside SPEC.md keep working here without
        // the document being touched.
        //
        // Smart punctuation is off. It is on by default, and it was quietly
        // rewriting the source: `--` in SPEC.md arrived on the page as an en
        // dash, and a straight quote as a curly one, so the specification the
        // site published was not character for character the one in the
        // repository. Six en dashes and fifteen curly quotes on /spec alone
        // came from here rather than from anything anybody wrote.
        processor: satteri({
            features: { smartPunctuation: false },
            hastPlugins: [repositoryLinks, emptyHeaders, scrollableTables],
        }),
    },
    vite: {
        server: {
            // Kept out of the watcher as well as out of `public/`. Vite watches
            // the project root, so moving the archive next door to `public/`
            // only moved it from one watched place to another: the dev server
            // went on failing to start until it was excluded here too.
            watch: {
                ignored: ['**/landmarks/**'],
            },
        },
    },
    integrations: [landmarks(), discovery()],
});
