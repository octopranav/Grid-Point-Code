# The website

[gridpointcode.com](https://gridpointcode.com) — a playground, a specification
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
site is computed at build time by that package — none of them are written out by
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
over the plate, and its absence costs the page nothing — if the tiles fail, or
the library never loads, or a reader has scripting off, the plate is still there
and still right. Tiles come from OpenFreeMap, which needs no key, no account and
sets no cookies; MapLibre is loaded on demand and is a 264&nbsp;KB chunk
(compressed) that never reaches a reader who does not get a map.

That ordering is the argument the whole site exists to make. A format whose
claim is that it needs nobody should not have a front page that breaks when
somebody else's server is down.

The version is pinned to MapLibre 5 deliberately. MapLibre 6 loads and runs, but
never finishes loading an OpenMapTiles vector source from this provider — the
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

A reader has three states, not two: light, dark, and following their system —
which is the default and is a real setting rather than a missing one. An
explicit choice stamps `data-theme` on the root element; the system setting
stamps nothing and lets `prefers-color-scheme` decide. A small inline script in
the head applies the stored choice before the first paint, because drawing the
wrong theme and then correcting it is worse than either theme.

Every colour comes from a token defined for both. A value that exists only
inside a media query is the bug that renders one theme's text on the other's
paper.

## Deployment

Pushing to `main` builds and publishes through
[`.github/workflows/pages.yml`](../.github/workflows/pages.yml).

**The custom domain is not configured from this tree.** Publishing from a
workflow rather than a branch means the domain is repository settings and
nothing else — a `CNAME` file in the built output is ignored, which is why there
is not one here. It is set once, by hand, under Settings then Pages. The
workflow cannot change it, so instead it checks the address the deployment
answered on and warns if that is not the expected one.

The DNS records for the domain are **not** proxied. Putting a proxy in front of
GitHub Pages prevents the certificate from being renewed: the site works for
ninety days and then stops, and the only fix is to turn the proxy off and wait.
Nothing is gained by it here — Pages is already behind a content network, and
there is no origin to conceal.
