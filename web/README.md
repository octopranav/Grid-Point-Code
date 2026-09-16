# The website

[gridpointcode.com](https://gridpointcode.com): a playground, a specification
and a proof. Encoding and decoding need no network. The map does, for any place
not already looked at.

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

There are no cookies and no analytics. The one thing that leaves the page about
a visitor is their IP address, sent at most once a week to a country lookup
service so the front page can open on a place in their own country; see
[Places](#places) for which services and why.

## Layout

```
src/
  components/     CodeMark, Resolve, the masthead, the theme control
  layouts/        the shell every page sits in
  pages/          one file per route
  lib/            the level arithmetic, shared by the build and the browser
  styles/         global.css, and tokens.css generated from ../design
scripts/          build-landmarks.mjs, which makes the landmark shards, and
                  build-places.mjs, which makes src/data/places.json
public/sw.js      keeps visited pages, shards and map tiles available offline
landmarks/        generated, never committed: see below
```

`src/styles/tokens.css` is generated. Edit
[`../design/tokens.json`](../design/README.md) and regenerate; the build refuses
to run against a stylesheet that has drifted from it.

## The hero, and the order it loads in

The front page shows an address arriving one character at a time, on two
surfaces that show the same step. It opens on a market in the visitor's own
country, says how that place is found without a code (a name, a city, and then
directions) and how its code is said aloud, and offers to code the reader's own
door.

The descent starts from a city, not from the world. Level four is a cell about
40 km across, and the three characters above it (continents, countries,
regions) are filled in from the start: nobody gives directions in those, and a
story that spends its first three seconds on them is about the grid rather than
the door.

The **plate** is a drawing of the subdivision the current level performs, five
by five, with the containing cell lit. It is rendered on the server, it is
exact, and it needs no network, no basemap and no JavaScript. Without a script
it stands still at level four, which is a true and complete statement on its
own.

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

## Places

The front page and the playground open on somewhere in the visitor's own
country, and every place the site knows has a page of its own under `/play`,
titled the way somebody would search for it:
`Toronto, Ontario, Canada · #G3RJF-4318R`.

### Three sets

**Featured places** are chosen by hand, in
[`scripts/featured-places.txt`](scripts/featured-places.txt): a market, bazaar,
souq or old quarter that people in that country know by name, where the stalls,
shops and doors inside have no address of their own and are found by landmark.
That is the problem the format exists for, so it is what a visitor is shown.
Not a religious site, not in territory two states contest, and not a shopping
centre, whose units are numbered. Up to three a country, first line first. 179
of them, across 114 countries. Countries that already use a national digital
addressing system were done last, and are in their own block at the end.

**Sights** are in [`scripts/sights.txt`](scripts/sights.txt): famous places
with no address at all, such as waterfalls, canyons and ruins. 357 of them,
across 164 countries. They were the first featured set, and they keep their
pages because people search for them, but none is what the site opens on. A
waterfall has no door to give an address to.

**Cities** are chosen by rule: every national capital, every city of half a
million or more, and every regional capital of a quarter million or more. 1,675
of them, across 240 countries. A country with no featured place opens on its
capital.

### The choice is made by a person; the facts are not

A featured place or sight is listed by its English Wikipedia title, because a
title resolves to exactly one Wikidata item through Wikipedia's own redirects. A
Wikidata item (`Q12345`) is accepted too, for a market with no English article. A name
search was tried first and matched "Petra" to a given name, "Gullfoss" and
"Moraine Lake" to unrelated items with no links at all, which is why the list is
of titles.

Everything else is read. `build-places.mjs` takes the coordinates and country
from Wikidata, reading only statements that are current, so Lake Baikal is not
disqualified for once having been in the Soviet Union. It takes the region by
walking Wikidata's administrative hierarchy to the country, because the nearest
town is a poor guide to a state: the nearest town to Old Faithful is in Montana.
It takes a sight's nearby town from GeoNames, only from the listed country,
because taking the nearest place of any country put Horseshoe Falls in New York.

A market's city is the GeoNames place within 25 km with the most people for its
distance, capitals counted double. The reach is that wide because a city's
recorded point can be far from its old centre: Mumbai's is 14 km from Crawford
Market. Neither simpler rule worked. The largest
place in reach put the souq in Manama in Al Muharraq and the market in Valletta
in a bigger town across the harbour; the nearest puts a market in an old city in
whichever ward's centre is a little closer. GeoNames records Camayenne, one
neighbourhood of Conakry, with nearly the whole city's population, so that
record is ignored.

A market's point is where the code on its page names, and that page says the
code is one spot in the market, so the point has to be in it. Wikidata's stated
precision is no guide (a street given to six decimals claims 1.3 km), so points
given to two or three decimals were checked against OpenStreetMap. Aleppo's
Al-Madina Souq was left out: its point is 600 m from the souq's lanes.

The script will not write a file it cannot check. A title that does not resolve,
a place whose country does not match, or one carrying a religion statement
stops the run and says which, and so does a sight with a street address. A
market with one is listed for review instead: the market as a whole can have an
address while none of the stalls inside it do. What it cannot catch is a title
that resolves cleanly to the wrong thing, and that is found by reading the
output: the first pass turned "Trafalgar Falls" into a village and "Maracas Bay"
into the whole island of Trinidad.

Contested territory is a set of shapes, printed against every city they exclude.
The first version was boxes, and it held Islamabad, Chisinau, Nicosia and a
Brazilian state capital.

```
node scripts/build-places.mjs --geonames <dir>
```

`<dir>` needs `countryInfo.txt`, `admin1CodesASCII.txt` and `cities1000.txt`
from GeoNames. The output is committed, so a build never reaches the network,
and `checkPlaces()` fails the build if the file stops being true.

### Which country a visitor is in

`src/lib/country.ts`, in this order:

| Step | Where the answer comes from | What leaves the browser |
| --- | --- | --- |
| a week's memory | `localStorage` | a returning visitor sends nothing |
| `api.country.is` | Cloudflare's geolocation, then MaxMind's | sends the IP address |
| `get.geojs.io` | MaxMind's | sends the IP address, only if the first fails |
| the device's timezone | `/places/zones.json`, from the IANA database | stays in the browser |
| the device's language | its region, when it has one | stays in the browser |

Both services are free, need no key and answer with a country code and nothing
else. They were chosen over others that answer the same question on terms a
public site could not rely on; one alternative's free plan, for instance, is
marked "not for production use".

The timezone step exists because content blockers commonly block these
services, and a visitor who runs one is still somewhere. A guess from the device
is not remembered, so the services are asked again next time.

A country's places are fetched as `/places/<ISO>.json`, a few hundred bytes,
rather than shipping the whole set to every visitor.

## Giving an address

A place page leads with the address in use there today, then the code, then the
code as it is said: `Khan el-Khalili 66194`, a name both people know and the last
five characters. The page gives the distance within which those five recover
the exact code, computed for that latitude, and says plainly that further away
they name somewhere else.

The playground's link can carry directions: `?c=J7MNH66194&n=Blue+gate`. They
are part of the address being handed over, the part a ten-character code cannot
say, so they ride in the link and in the QR code made from it. Nothing about
them is stored anywhere else, which is why the link is capped at 80 characters
of them: a square code grows with every character, and one too dense to scan
from a doorway defeats the reason it is there. A link with directions and no
code shows no directions, since there is nothing for them to be directions to.

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

### There is no offline basemap, and there is not going to be one

The tiles are the one thing on the site that needs a network, and they stay that
way. A world basemap is not a thing that can be shipped here: the vector tiles
these styles are drawn from are tens of gigabytes for the planet, and even the
handful of zoom levels that would show a country are hundreds of megabytes
against a published site that already stands at four fifths of what Pages will
serve. Cutting the landmarks to make room for a picture would be trading the
part that answers questions for the part that decorates the answer.

So the offline map is the plate: a drawing of the cell, at the coordinates the
code names, rendered from the code itself and correct with nothing fetched at
all. That is not a consolation. The plate is the thing that is true, and the
basemap is context laid behind it.

What this does mean is that **nothing may sit behind the basemap arriving**, and
that is worth checking whenever this code is touched, because the failure is
invisible on a desk with a network. Both maps now settle whether the tiles come
or not:

| | when the library will not load | when WebGL2 is missing | when the tiles never arrive |
| --- | --- | --- | --- |
| `Resolve` | plate, descent runs | plate, descent runs | plate, descent runs |
| `Playground` | code still resolves | code still resolves | code still resolves |

The playground did not, and the way it failed is the argument for the row. A
shared link carries its code in the query string, and the code was read out of
it inside the handler for `style.load`. With the tile host unreachable, that
handler never ran: following somebody's link to Sydney showed the pre-rendered
example in Toronto, with no error and nothing on screen to suggest the link had
been read at all. The constructor was unguarded too, so a browser without WebGL2
threw out of `loadMap` and lost the basemap control on the way past.

To reproduce any of it, point `STYLES` in `src/lib/basemap.ts` at
`http://127.0.0.1:9/` and load `/play?c=6LK4X-N2242`. The field should say
`#6LK4X-N2242` and the coordinates should say `-33.868788, 151.209293`.

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
