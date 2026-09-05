// A list of places, kept in this browser and nowhere else.
//
// No account, no server, nothing leaves the device. That is not a limitation
// being apologised for -- a list of where somebody has been is exactly the kind
// of thing a site should not be holding, and the format does not need it to.
// Everything a saved place is made of fits in the code.
//
// `localStorage` can throw rather than return nothing: a browser set to block
// site data raises on the property itself, and a private window can be full.
// Every read and write here is wrapped, and a failure is treated as an empty
// list rather than an error the reader has to think about. The one thing that
// must not happen is the playground failing to load because a list would not.

import type { Kept } from './exports';

export type { Kept };

/** Versioned, so a later shape can be told from this one rather than guessed. */
const KEY = 'gpc.saved.v1';

/**
 * How many places are kept.
 *
 * A cap because storage is small and shared with everything else this origin
 * keeps. The oldest go first, which is the behaviour somebody would guess.
 */
export const MOST = 200;

function storage(): Storage | null {
    try {
        // The access itself is what throws, so it has to be inside the try.
        const store = window.localStorage;
        const probe = `${KEY}.probe`;
        store.setItem(probe, '1');
        store.removeItem(probe);
        return store;
    } catch {
        return null;
    }
}

/**
 * Whether the browser will store anything at all. Asked so the page can say
 * so rather than appearing to save and quietly not.
 *
 * Deliberately not called `canKeep`: the landmark archive already has one of
 * those, and it means holding a map area offline.
 */
export const canStore = () => storage() !== null;

/** Every place kept here, newest first. */
export function saved(): Kept[] {
    const store = storage();
    if (!store) return [];

    try {
        const held = JSON.parse(store.getItem(KEY) ?? '[]');
        if (!Array.isArray(held)) return [];

        // Anything that is not a place is dropped rather than trusted. This is
        // the reader's own storage, but it is also the one input here that
        // another tab, an older version or a person with a console can write.
        return held.filter((one): one is Kept =>
            one
            && typeof one.code === 'string'
            && /^[0-9A-Z]{10}$/.test(one.code)
            && typeof one.name === 'string'
            && Number.isFinite(one.latitude)
            && Number.isFinite(one.longitude)
            && typeof one.saved === 'string');
    } catch {
        return [];
    }
}

function write(places: Kept[]): boolean {
    const store = storage();
    if (!store) return false;

    try {
        store.setItem(KEY, JSON.stringify(places.slice(0, MOST)));
        return true;
    } catch {
        // Full, most likely. Saying so is better than a list that silently
        // forgets the thing just added.
        return false;
    }
}

/**
 * Keep a place, or update the name of one already kept.
 *
 * Keyed by the code, so saving the same cell twice does not make two entries --
 * and re-saving moves it to the top, which is what somebody means by it.
 */
export function keep(place: Kept): boolean {
    const held = saved().filter((one) => one.code !== place.code);
    return write([place, ...held]);
}

export function drop(code: string): boolean {
    return write(saved().filter((one) => one.code !== code));
}

export function clear(): boolean {
    const store = storage();
    if (!store) return false;
    try {
        store.removeItem(KEY);
        return true;
    } catch {
        return false;
    }
}

/** A filename for an export, with the date in it so two do not collide. */
export function filename(extension: string): string {
    const day = new Date().toISOString().slice(0, 10);
    return `places-${day}.${extension}`;
}
