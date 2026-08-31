// The documentation the site renders is the documentation the repository ships.
//
// Nothing here is a copy. `SPEC.md` and the four port READMEs are read from
// where they live, so a page cannot drift from the source it describes -- the
// failure mode this exists to prevent is a site that quietly documents last
// year's API while the README next door says something else.

import { defineCollection } from 'astro:content';
import { glob } from 'astro/loaders';

const specification = defineCollection({
    loader: glob({ pattern: 'SPEC.md', base: '../' }),
});

const ports = defineCollection({
    loader: glob({ pattern: '{csharp,java,python,typescript}/README.md', base: '../' }),
});

export const collections = { specification, ports };
