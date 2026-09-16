// Naming a place in the address bar.
//
// The playground exists to produce a string somebody hands to somebody else,
// and until now the page producing it could not itself be pointed at: /play
// always opened on the same rooftop in Toronto, a refresh lost the point, and
// there was no way to send anyone what you were looking at.
//
// The code is the whole identifier. Nothing else about looking goes in: not the
// basemap, not the zoom, not which group is open. Those are preferences, they
// already live in this browser's storage, and a link that carries them tells
// the person receiving it how to hold their screen.
//
// Directions are different, and are the one addition. "Blue gate, second floor"
// is not a way of looking at a place; it is part of the address being handed
// over, the part a ten-character code cannot say. It rides in the link and in
// the square code made from it, and nowhere else: this site stores nothing, so
// the link is the only place the directions exist.

import { GPC } from '@pranavpatel.ca/algo-gridpointcode';

/** The query parameter. One letter, because it is written out by hand. */
export const PARAM = 'c';

/** Directions, when there are any. */
export const NOTE = 'n';

/**
 * Longest directions a link will carry.
 *
 * A sentence, not a paragraph. A QR code grows with every character, and a
 * square too dense to scan from a doorway defeats the reason it is there.
 */
export const NOTE_MOST = 80;

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
export function linkTo(code: string, note = ''): string {
    const query = new URLSearchParams({ [PARAM]: GPC.normalise(code)[0] });
    const said = tidyNote(note);
    if (said) query.set(NOTE, said);
    return `?${query.toString()}`;
}

/** The directions a URL carries, tidied, or an empty string. */
export function noteIn(search: string): string {
    return tidyNote(new URLSearchParams(search).get(NOTE) ?? '');
}

/** Directions as a link carries them: one line, trimmed, not too long. */
export function tidyNote(note: string): string {
    return note.replace(/\s+/g, ' ').trim().slice(0, NOTE_MOST);
}

/**
 * The whole address, for copying or for a QR payload.
 *
 * Absolute, because a link is only useful somewhere else.
 */
export function addressOf(code: string, from: Location | URL, note = ''): string {
    return new URL(linkTo(code, note), `${from.origin}${from.pathname}`).href;
}
