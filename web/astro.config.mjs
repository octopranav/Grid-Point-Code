// @ts-check
import { createReadStream, existsSync, statSync } from 'node:fs';
import { cp } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { defineConfig } from 'astro/config';
import { satteri } from '@astrojs/markdown-satteri';

// The site is a pile of static files and nothing else: no server, no database,
// no API. Everything a visitor asks of it is arithmetic their own browser can
// do, which is the format's claim made physical rather than merely stated.
//
// `site` is the real origin because absolute URLs end up in the sitemap, in
// social previews, and in canonical links, and a wrong one there is invisible
// until somebody else follows it.

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
                properties: { className: ['table-scroll'] },
                children: [],
            });
        },
    },
};

export default defineConfig({
    site: 'https://gridpointcode.com',
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
        processor: satteri({ hastPlugins: [repositoryLinks, scrollableTables] }),
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
    integrations: [landmarks()],
});
