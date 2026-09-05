# The website

[gridpointcode.com](https://gridpointcode.com): a playground, a specification
and a proof, all of which keep working with the network cut.

```
npm install
npm run dev        # local, with reloading
npm run build      # checks the design tokens, then builds to dist/
npm run preview    # serve what was built
npm run check      # type-check the components
```

Node 22.12 or newer.

## What it is built on

[Astro](https://astro.build), producing static files and nothing else: no
server, no database, no API. Documentation pages ship as HTML with no JavaScript
at all; only the parts that have to be interactive send any.

The site imports **the package published to npm**, not the source next door.
That makes it a standing test of the release: if a version ever encoded a point
differently, or dropped an operation the site calls, the build fails here rather
than in somebody else's project. Every code and every cell measurement on the
site is computed at build time by that package. None of them are written out by
hand, so none of them can quietly go stale.

Type is served from this origin rather than a font network. A page whose whole
claim is that it needs nobody should not need somebody for its lettering, and
only the Latin ranges are shipped: anything outside them falls back to the
reader's own system font, which is the right answer for a name we did not
typeset.

There are no cookies, no analytics and nothing to dismiss.

## Layout

```
src/
  components/     CodeMark, Resolve, the masthead, the theme control
  layouts/        the shell every page sits in
  pages/          one file per route
  lib/            the level arithmetic, shared by the build and the browser
  styles/         global.css, and tokens.css generated from ../design
scripts/          build-landmarks.mjs, which makes the landmark shards
public/sw.js      keeps the site and its shards working with the network cut
landmarks/        generated, never committed: see below
```

`src/styles/tokens.css` is generated. Edit
[`../design/tokens.json`](../design/README.md) and regenerate; the build refuses
to run against a stylesheet that has drifted from it.

## The hero, and the order it loads in

The front page shows a coordinate arriving one character at a time, on two
surfaces that show the same step.

The **plate** is a drawing of the subdivision the current level performs: the
world four by six, everything under it five by five, with the containing cell
lit. It is rendered on the server, it is exact, and it needs no network, no
basemap and no JavaScript. Without a script it stands still at level one, which
is a true and complete statement on its own.

The **map** is the geographic version of the same step. It arrives afterwards,
over the plate, and its absence costs the page nothing. If the tiles fail, or
the library never loads, or a reader has scripting off, the plate is still there
and still right. Tiles come from OpenFreeMap, which needs no key, no account and
sets no cookies; MapLibre is loaded on demand and is a 264&nbsp;KB chunk
(compressed) that never reaches a reader who does not get a map.

That ordering is the argument the whole site exists to make. A format whose
claim is that it needs nobody should not have a front page that breaks when
somebody else's server is down.

The version is pinned to MapLibre 5 deliberately. MapLibre 6 loads and runs, but
never finishes loading an OpenMapTiles vector source from this provider. The
style parses, the worker fetches tiles perfectly well on its own, and yet the
source reports unloaded forever and the map draws nothing. Version 5 is what the
tile provider documents, and it works. Newest is not automatically right when the
service is somebody else's.

Two more things worth knowing before changing it.

The map container must never be `hidden`. That attribute is `display: none`, a
WebGL map given a zero-size container never finishes loading, and hiding it that
way guarantees the thing it was hiding. It is laid over the plate at zero opacity
instead.

The map is revealed on `style.load`, never on `load`. `load` waits for every
source in the viewport to finish downloading, so a single slow or stalled tile
source holds a map hidden that is perfectly able to draw. `style.load` fires as
soon as the style is parsed and layers can be added; tiles then arrive as they
arrive, which is what a map is supposed to look like.

## Themes

A reader has three states, not two: light, dark, and following their system.
which is the default and is a real setting rather than a missing one. An
explicit choice stamps `data-theme` on the root element; the system setting
stamps nothing and lets `prefers-color-scheme` decide. A small inline script in
the head applies the stored choice before the first paint, because drawing the
wrong theme and then correcting it is worse than either theme.

Every colour comes from a token defined for both. A value that exists only
inside a media query is the bug that renders one theme's text on the other's
paper.

## The landmarks

A short form is five characters and a reference. It resolves only against a
point within half a level-5 cell of the true one, and outside that box recovery
does **not** fail; it returns a plausible place eight or ten kilometres away
with nothing raised. So the playground has to be able to say which references
are near enough, which means shipping a gazetteer.

`scripts/build-landmarks.mjs` turns a [GeoNames](https://www.geonames.org) dump
(CC BY 4.0) into one file per cell, keyed by the first characters of a
landmark's own code, so `#G3RJM-98NM9` is filed under `G3RJ`. The reader works out
which files the recovery box reaches into from its four corners, which is one
file nine times in ten and never more than four.

```
node scripts/build-landmarks.mjs --geonames <dir> [--out <dir>] [--level 4] [--slices 8]
```

`<dir>` needs `allCountries.txt` (or per-country `CA.txt` and friends) plus
`admin1CodesASCII.txt` and `countryInfo.txt`. The world takes about five
minutes and produces roughly 6.5 million landmarks over 82,000 files, 260 MB.

Three numbers decided the shape of it, and all three were measured rather than
guessed:

- **Level 4, not 3.** At level 3 the densest cells (New York, Seoul, Berlin)
  came out over a megabyte each, and those are where readers are. Level 4 costs
  1.4 files a lookup instead of 1.08 and drops the worst case from 420 KB to
  131 KB gzipped, with a median of 0.3 KB.
- **Slices, not one pass.** Holding every kept landmark at once needed more than
  4 GB. Each slice keeps a share of the shards and re-reads the dump, which
  trades minutes for a 2 GB ceiling that any runner has.
- **A fingerprint, not the text.** Finding which descriptions repeat by keeping
  nine million strings cost gigabytes; a 52-bit fingerprint per description in a
  typed array costs 74 MB and gave identical totals.

What is kept is filtered twice. Only classes whose published coordinate is
somewhere a person stands, not a river, a road, a park or a province, whose
one coordinate is a centroid. And only descriptions unique inside their own
region, because `Scarborough` is a district of Toronto and a settlement 1,900 km
north, and a listener who picks the wrong one is not told they did.

**The archive lives beside `public/`, not inside it, and that is not a
preference.** With 82,000 files in `public/` the dev server never finished
starting. It gave up after thirty seconds, every time, while the same tree with
the archive moved aside answered in under five. The production build never
minded, which is what made it a trap: the site built and deployed perfectly and
could not be worked on.

Moving it out of `public/` is only half the fix, because Vite watches the whole
project root, so the archive has to be excluded from the watcher as well. Both
halves are in [`astro.config.mjs`](astro.config.mjs), along with the small
integration that serves the files in development and copies them into the output
at the end of a build.

`landmarks/` is generated and **not committed**. It is built by
[`.github/workflows/landmarks.yml`](../.github/workflows/landmarks.yml), run by
hand, and published as a release asset under the `landmarks` tag. The Pages
build downloads that asset; if there is none it warns and carries on, and the
reference list says it could not be loaded.

Rebuilding the data is therefore a decision rather than a side effect: a new
archive does not reach the site until the next Pages run.

## Working offline

`public/sw.js` keeps three things, because three kinds of thing go stale
differently.

| | strategy | why |
| --- | --- | --- |
| pages | network first | a deployment should be picked up the moment there is a network to pick it up from |
| hashed assets | cache first | Astro puts a hash in the name, so a copy is never the wrong one |
| kept shards | cache first, named for the build | what a reader asked for by name; read first, and the worker never writes to it |
| seen shards | cache first, named for the build | whatever the worker served along the way, so a place already visited still works |

That last row is what the `built` stamp in `landmarks/manifest.json` is for.
Without it a held copy would be served for as long as the browser felt like
keeping it.

The two shard stores are separate on purpose. Counting them together made the
panel say `1 shard held for this area` immediately after a reader pressed
Forget. True, in that one shard was in a cache, and useless, because they had
not asked for it and the area was not actually available. Only the kept store is
ever counted or reported: telling someone an area is held because they once
glanced at it is the same sentence as telling them it is ready for a journey.
Forget empties both, and the seen store refills on its own, invisibly.

Shards are also kept as they are looked up, and the playground offers to keep
the area around a point: the cell one level above a shard, about 200 by 267 km,
a few hundred kilobytes. That is a deliberate size: worth asking for before a
journey, where the whole world at eighty-odd megabytes would not be.

One trap worth knowing about, because it cost an afternoon. Reading twenty-five
cached response bodies **concurrently** returned 1,177 bytes where reading the
same twenty-five in turn returned 83,506. The stored data was whole either way,
but concurrent reads of it were not. `keepArea` therefore fetches together and
measures one at a time.

## Deployment

Pushing to `main` builds and publishes through
[`.github/workflows/pages.yml`](../.github/workflows/pages.yml), which fetches
the landmark archive before building it.

**The custom domain is not configured from this tree.** Publishing from a
workflow rather than a branch means the domain is repository settings and
nothing else. A `CNAME` file in the built output is ignored, which is why there
is not one here. It is set once, by hand, under Settings then Pages. The
workflow cannot change it, so instead it checks the address the deployment
answered on and warns if that is not the expected one.

The DNS records for the domain are **not** proxied. Putting a proxy in front of
GitHub Pages prevents the certificate from being renewed: the site works for
ninety days and then stops, and the only fix is to turn the proxy off and wait.
Nothing is gained by it here. Pages is already behind a content network, and
there is no origin to conceal.
