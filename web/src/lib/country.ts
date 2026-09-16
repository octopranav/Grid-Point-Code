// Which country a visitor is in, so the front page can open somewhere of theirs.
//
// The browser side of lib/places.ts. It carries no place data of its own: it
// finds a country, then fetches that one country's file.
//
// **Asked of a lookup service, first.** A connection's country is the most
// accurate answer available without asking the reader for their location, and
// a hero that opened with a permission prompt would be a worse front page than
// one showing a market in somebody else's country. Two free services, no key, each
// answering with a country code and nothing else; the second is asked only if
// the first does not answer in time.
//
//   api.country.is    Cloudflare's own geolocation, then MaxMind's
//   get.geojs.io      MaxMind's
//
// Asking either sends the visitor's IP address to it, which is why the footer
// says so. The answer is kept for a week, so a returning reader costs nobody a
// request.
//
// **Then the device, if both are blocked.** Content blockers commonly block
// these services, and a reader who runs one is still somewhere. The timezone
// the device is set to names a country in almost every case; the language it
// prefers sometimes does. Neither leaves the browser.

const REMEMBERED = 'gpc-country';
const KEPT_FOR = 7 * 24 * 60 * 60 * 1000;

const SERVICES = [
    'https://api.country.is/',
    'https://get.geojs.io/v1/ip/country.json',
];

/** One place, as much of it as a hero or a playground needs. */
export interface Shown {
    kind: 'featured' | 'sight' | 'city';
    name: string;
    city: string | null;
    region: string | null;
    country: string;
    lat: number;
    lon: number;
    slug: string;
}

const isCode = (value: unknown): value is string =>
    typeof value === 'string' && /^[A-Z]{2}$/.test(value);

function recall(): string | null {
    try {
        const held = JSON.parse(localStorage.getItem(REMEMBERED) ?? 'null');
        if (held && isCode(held.iso) && Date.now() - held.at < KEPT_FOR) return held.iso;
    } catch {
        /* a blocked or malformed store just means asking again */
    }
    return null;
}

function remember(iso: string): void {
    try {
        localStorage.setItem(REMEMBERED, JSON.stringify({ iso, at: Date.now() }));
    } catch {
        /* nothing to do: the answer lasts as long as the page */
    }
}

/** A request that gives up, rather than holding a front page hostage. */
async function within<T>(ms: number, work: (signal: AbortSignal) => Promise<T>): Promise<T | null> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), ms);
    try {
        return await work(controller.signal);
    } catch {
        return null;
    } finally {
        clearTimeout(timer);
    }
}

async function fromService(url: string): Promise<string | null> {
    return within(1500, async (signal) => {
        const response = await fetch(url, { signal, credentials: 'omit', cache: 'no-store' });
        if (!response.ok) return null;
        const answer = await response.json();
        return isCode(answer?.country) ? answer.country : null;
    });
}

async function fromDevice(): Promise<string | null> {
    const zone = (() => {
        try {
            return Intl.DateTimeFormat().resolvedOptions().timeZone;
        } catch {
            return undefined;
        }
    })();
    if (zone) {
        const iso = await within(2000, async (signal) => {
            const response = await fetch('/places/zones.json', { signal });
            const zones = (await response.json()) as Record<string, string>;
            return zones[zone] ?? null;
        });
        if (isCode(iso)) return iso;
    }
    const region = navigator.language?.split('-')[1]?.toUpperCase();
    return isCode(region) ? region : null;
}

/** The visitor's country, or null when nothing can tell. */
export async function visitorCountry(): Promise<string | null> {
    const held = recall();
    if (held) return held;

    for (const url of SERVICES) {
        const iso = await fromService(url);
        if (iso) {
            remember(iso);
            return iso;
        }
    }
    return fromDevice();
}

/**
 * The places a country's visitors are shown, best first.
 *
 * A country the site has nothing for, or a file that will not load, falls back
 * to the default, so a caller always gets something to show.
 */
export async function placesFor(iso: string | null, fallback: string): Promise<Shown[]> {
    for (const code of iso && iso !== fallback ? [iso, fallback] : [fallback]) {
        const places = await within(3000, async (signal) => {
            const response = await fetch(`/places/${code}.json`, { signal });
            if (!response.ok) return null;
            return ((await response.json()) as { places: Shown[] }).places;
        });
        if (places?.length) return places;
    }
    return [];
}

/** "Manek Chowk, Ahmedabad, India": the name, its city and the country. */
export function label(place: Shown): string {
    const parts = [place.name, place.city, place.country].filter(Boolean) as string[];
    return parts.filter((part, at) => part !== parts[at - 1]).join(', ');
}
