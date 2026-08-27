# Artwork

| File | Size | Where it is used |
| --- | --- | --- |
| `hero-light.png` | 2560 × 1000 | The top of [`../README.md`](../README.md), on a light background |
| `hero-dark.png` | 2560 × 1000 | The same, on a dark one |
| `social-preview.png` | 1280 × 640 | The repository's social preview, uploaded under Settings and referenced from nowhere in the tree |

The heroes are one drawing in two palettes, wired with `<picture>` and
`prefers-color-scheme` so a reader gets the one that suits their theme. They are
twice the size they are displayed at, which is what keeps them sharp on a
high-density screen.

Both drawings are accurate rather than decorative. Every highlighted cell is the
one that actually contains 43.65 N, 79.38 W — the coordinates behind
`#G3RJM-98NM9` throughout the documentation — computed from the reference
implementation. The first plate is a true 4 by 6 world grid with square cells,
which is why it is wider than the nine 5 by 5 subdivisions after it.

The type is Bitter and IBM Plex Mono, both under the SIL Open Font License.
