# Design tokens

Every colour, spacing step, corner radius and size of type the Grid Point Code
applications use is decided once, in [`tokens.json`](tokens.json), and generated
from there into whatever each platform reads.

```
node design/build-tokens.mjs            write every target
node design/build-tokens.mjs --check    write nothing; fail if a target has drifted
```

| Generated file | Read by |
| --- | --- |
| `../web/src/styles/tokens.css` | The website, as custom properties |
| `generated/android/values/colors.xml` | An Android application, light |
| `generated/android/values-night/colors.xml` | The same, dark |
| `generated/android/values/dimens.xml` | Spacing, radii and type sizes |
| `generated/android/Palette.kt` | The same palette for Compose |

The check mode runs in continuous integration. A generated file somebody edited
by hand is a fork of the design system that nobody announced, and the next
regeneration would silently undo it — so the build refuses to go green while one
exists. Edit `tokens.json` and regenerate.

The generator has no dependencies, like everything else here.

## Two decisions worth knowing before editing

**Both themes carry literal values rather than one theme plus opacities.** A
translucent colour has to be composited against whatever sits behind it, which
is a different answer on every surface and an extra layer on Android.

**The ten level tints are the format drawn as itself.** One per level of a code,
prussian stepped into the ground in ten equal parts, so depth reads as weight and
the eye starts where the world does. The ramp inverts on the dark theme: level
one is brightest there and level ten sinks into the page. Each tint carries the
type colour that stays legible on it, which changes at level six on both themes.

## The typeface was chosen by measurement

Codes mix digits and capitals, so tabular figures are not enough — only a true
monospace gives every code on Earth the same printed width.

Which monospace is a narrower question than it looks. The alphabet has no `O`,
`I`, `S`, `Z` or `B` and no vowels, so the confusions every typeface guide leads
with cannot occur in a code at all. Eight pairs remain:

```
0/D   6/G   1/7   1/L   1/J   7/T   C/G   K/X
```

Four candidates were rendered at 13 px, the size a code is actually read at, and
scored on how much of the combined silhouette the two glyphs of each pair do not
share. A code is only as safe as its worst pair, so the worst pair decides.

| Face | Worst pair | Score |
| --- | --- | --- |
| **IBM Plex Mono** | 0/D | **0.300** |
| Source Code Pro | C/G | 0.321 |
| Martian Mono | C/G | 0.280 |
| JetBrains Mono | C/G | 0.192 |

IBM Plex Mono was chosen: it has the best worst pair, and it is the only
candidate whose weak point is not `C` against `G` — the pair that decides three
of the four, in an alphabet where both letters are common. It also shares a
skeleton with the body face, so that pairing is structural rather than lucky.

Two results are worth recording because they are not what anyone expects. The
pairs people worry about are not the dangerous ones here: `1/7`, `1/L` and `1/J`
score between 0.60 and 0.83 in every face. And all four candidates already draw a
marked zero, so no slashed-zero feature needs switching on and none is set.

What is left of the risk is answered by the ten-cell mark rather than the
typeface: one character to a box removes adjacency entirely.
