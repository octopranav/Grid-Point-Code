// Naming a place in the address bar.
//
// The playground exists to produce a string somebody hands to somebody else,
// and until now the page producing it could not itself be pointed at: /play
// always opened on the same rooftop in Toronto, a refresh lost the point, and
// there was no way to send anyone what you were looking at.
//
// The code is the whole identifier. Nothing else goes in: not the basemap, not
// the zoom, not which group is open. Those are preferences about looking, they
// already live in this browser's storage, and a link that carries them tells
// the person receiving it how to hold their screen.

import { GPC } from '@pranavpatel.ca/algo-gridpointcode';

/** The query parameter. One letter, because it is written out by hand. */
export const PARAM = 'c';

/**
 * The code a URL names, exactly as it was written there.
 *
 * Returned raw rather than validated. A bad code in a link should behave like a
 * bad code typed into the field -- same message, same classification, same
 * explanation of what the alias table did -- and that path already exists and
 * is already tested. Deciding here would mean a second, quieter verdict.
 */
export function codeIn(search: string): string | null {
    const raw = new URLSearchParams(search).get(PARAM);
    if (raw === null) return null;
    const trimmed = raw.trim();
    return trimmed === '' ? null : trimmed;
}

/**
 * The query naming a code.
 *
 * Unformatted, because `#` in a URL starts a fragment and would take the code
 * with it. The ten characters are `0-9A-Z`, so nothing needs escaping, and
 * section 8 means a reader who pastes the formatted one back gets the same
 * place anyway.
 */
export function linkTo(code: string): string {
    return `?${PARAM}=${GPC.normalise(code)[0]}`;
}

/**
 * The whole address, for copying or for a QR payload.
 *
 * Absolute, because a link is only useful somewhere else.
 */
export function addressOf(code: string, from: Location | URL): string {
    return new URL(linkTo(code), `${from.origin}${from.pathname}`).href;
}
