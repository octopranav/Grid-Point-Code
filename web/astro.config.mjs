// @ts-check
import { defineConfig } from 'astro/config';

// The site is a pile of static files and nothing else: no server, no database,
// no API. Everything a visitor asks of it is arithmetic their own browser can
// do, which is the format's claim made physical rather than merely stated.
//
// `site` is the real origin because absolute URLs end up in the sitemap, in
// social previews, and in canonical links, and a wrong one there is invisible
// until somebody else follows it.
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
});
