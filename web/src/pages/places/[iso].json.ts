// One country's places, for the browser to fetch once it knows the country.
//
// Split per country rather than shipped whole: a visitor needs one of these,
// and the whole set is most of half a megabyte.

import type { APIRoute } from 'astro';

import { COUNTRIES, shownFor } from '../../lib/places';

export function getStaticPaths() {
    return Object.keys(COUNTRIES)
        .filter((iso) => shownFor(iso).length)
        .map((iso) => ({ params: { iso } }));
}

export const GET: APIRoute = ({ params }) => {
    const iso = params.iso!;
    const places = shownFor(iso).map(({ name, region, country, lat, lon, slug }) => ({
        name, region, country, lat, lon, slug,
    }));
    return new Response(JSON.stringify({ iso, places }), {
        headers: { 'Content-Type': 'application/json; charset=utf-8' },
    });
};
