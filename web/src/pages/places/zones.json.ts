// Which country each timezone is in, for a visitor whose content blocker
// stops the country lookup. Fetched only then.

import type { APIRoute } from 'astro';

import { ZONES } from '../../lib/places';

export const GET: APIRoute = () =>
    new Response(JSON.stringify(ZONES), {
        headers: { 'Content-Type': 'application/json; charset=utf-8' },
    });
