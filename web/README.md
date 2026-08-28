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
  components/     CodeMark, the masthead, the theme control
  layouts/        the shell every page sits in
  pages/          one file per route
  styles/         global.css, and tokens.css generated from ../design
public/           copied verbatim; CNAME lives here
```

`src/styles/tokens.css` is generated. Edit
[`../design/tokens.json`](../design/README.md) and regenerate; the build refuses
to run against a stylesheet that has drifted from it.

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
