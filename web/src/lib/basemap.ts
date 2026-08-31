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
 */
export const BASEMAPS = [
    { id: 'auto', name: 'Match theme' },
    { id: 'positron', name: 'Positron' },
    { id: 'bright', name: 'Bright' },
    { id: 'liberty', name: 'Liberty' },
    { id: 'dark', name: 'Dark' },
    { id: 'fiord', name: 'Fiord' },
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

/**
 * Positron and Fiord are the desaturated styles, which is what a drawing laid
 * over the map needs: the cell has to be the brightest thing on the panel. They
 * are what `auto` picks, following the page's own light or dark.
 */
export function basemapStyle(choice: Basemap = 'auto'): string {
    if (choice !== 'auto') return STYLES + choice;

    const root = document.documentElement;
    const dark =
        root.dataset.theme === 'dark' ||
        (!root.dataset.theme && matchMedia('(prefers-color-scheme: dark)').matches);
    return STYLES + (dark ? 'fiord' : 'positron');
}

/** A design token, resolved, with a fallback for the moment before they load. */
export function token(name: string, fallback: string): string {
    return (
        getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback
    );
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
