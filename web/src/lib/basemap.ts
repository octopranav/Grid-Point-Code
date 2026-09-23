// The basemap, in one place.
//
// Two things here are knowledge rather than configuration, and both cost an
// afternoon to rediscover:
//
// The version is pinned to MapLibre 5. Version 6 starts, fetches the style and
// paints a canvas, but never finishes loading an OpenMapTiles vector source
// from this provider -- the source reports unloaded indefinitely with no error,
// while its own worker fetches tiles perfectly well when asked directly. Five is
// what the tile provider documents. See web/README.md.
//
// A map is revealed on `style.load` and never on `load`. `load` waits for every
// source in the viewport to finish downloading, so one slow source keeps a map
// hidden that is perfectly able to draw.

import { colour } from '../../../design/tokens.json';

/** Tiles need no key, no account and set no cookies. */
const STYLES = 'https://tiles.openfreemap.org/styles/';

/** Where a reader's choice of basemap is remembered. */
const REMEMBERED = 'gpc-basemap';

/**
 * What the provider offers. All vector, all free, all keyless.
 *
 * There is deliberately no satellite or hybrid here. The tile endpoints that
 * serve imagery without a key are widely used but not clearly licensed for a
 * third-party site, and a page whose whole argument is that it needs nobody
 * should not quietly depend on somebody whose terms it cannot point at.
 *
 * Each says whether it is a dark map, because the drawing laid over it takes
 * its colours from that and not from the page. `auto` is whichever the page is.
 */
export const BASEMAPS = [
    { id: 'auto', name: 'Match theme', dark: null },
    { id: 'positron', name: 'Positron', dark: false },
    { id: 'bright', name: 'Bright', dark: false },
    { id: 'liberty', name: 'Liberty', dark: false },
    { id: 'dark', name: 'Dark', dark: true },
    { id: 'fiord', name: 'Fiord', dark: true },
] as const;

export type Basemap = (typeof BASEMAPS)[number]['id'];

/** The reader's last choice, or following the theme if they have none. */
export function chosenBasemap(): Basemap {
    try {
        const stored = localStorage.getItem(REMEMBERED);
        if (BASEMAPS.some((each) => each.id === stored)) return stored as Basemap;
    } catch {
        // A blocked store just means the choice lasts as long as the page does.
    }
    return 'auto';
}

export function rememberBasemap(choice: Basemap): void {
    try {
        localStorage.setItem(REMEMBERED, choice);
    } catch {
        /* nothing to do */
    }
}

/** Whether the page is wearing its dark theme, chosen or inherited from the system. */
export function pageIsDark(): boolean {
    const root = document.documentElement;
    return (
        root.dataset.theme === 'dark' ||
        (!root.dataset.theme && matchMedia('(prefers-color-scheme: dark)').matches)
    );
}

/** The style a choice comes to, and whether what it draws is dark. */
export interface Resolved {
    style: string;
    dark: boolean;
}

/**
 * Positron and Fiord are the desaturated styles, which is what a drawing laid
 * over the map needs: the cell has to be the brightest thing on the panel. They
 * are what `auto` picks, following the page's own light or dark.
 */
export function resolveBasemap(choice: Basemap, darkPage: boolean): Resolved {
    const named = BASEMAPS.find((each) => each.id === choice)?.dark ?? null;
    const dark = named ?? darkPage;
    const style = choice === 'auto' ? (dark ? 'fiord' : 'positron') : choice;
    return { style: STYLES + style, dark };
}

/** The colours the drawing is made in. */
export interface Inks {
    /** The chosen cell. */
    brass: string;
    /** The grid offered under the cursor. */
    soft: string;
}

/**
 * The drawing takes its colours from the map beneath it, not from the page.
 *
 * A reader can pick a light map in the dark theme or a dark one in the light,
 * and the cell has to stay visible on whichever it is. Measured against each
 * style's own background: the light theme's brass is about 5:1 on positron,
 * bright and liberty and 1.4:1 on fiord, where it all but disappears; the dark
 * theme's brass is 3.5:1 on fiord and 8.5:1 on dark, and 2.1:1 on the light
 * maps. So a light map gets the light theme's inks and a dark map the dark
 * theme's, whatever the rest of the page is wearing.
 *
 * Read from the tokens rather than the page's custom properties, which only
 * ever hold the theme the page is in.
 */
export function inksFor(darkMap: boolean): Inks {
    const palette = darkMap ? colour.dark : colour.light;
    return { brass: palette.brass, soft: palette['ink-soft'] };
}

/** Mercator gives up near the poles, so the whole world is this box. */
export const WORLD = { west: -180, south: -85, east: 180, north: 85 };

export interface Box {
    west: number;
    south: number;
    east: number;
    north: number;
}

/** A cell as GeoJSON, wound so the ring closes. */
export function outline(box: Box) {
    return {
        type: 'Feature' as const,
        properties: {},
        geometry: {
            type: 'Polygon' as const,
            coordinates: [[
                [box.west, box.south],
                [box.east, box.south],
                [box.east, box.north],
                [box.west, box.north],
                [box.west, box.south],
            ]],
        },
    };
}

/** A single point as GeoJSON. */
export function dot(latitude: number, longitude: number) {
    return {
        type: 'Feature' as const,
        properties: {},
        geometry: { type: 'Point' as const, coordinates: [longitude, latitude] },
    };
}
