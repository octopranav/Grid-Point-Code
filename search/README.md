# Finding places with a search engine that knows nothing about places

```
python search/tokenise.py '#G3RJM-98NM9' 100
```

Every full-text engine matches terms. A code's prefixes name nested cells, so
indexing the prefixes as terms turns *what is near here* into an ordinary term
query, with no geo type, no spatial index, no plugin, and nothing the engine has to
understand about the earth.

This directory is a convention and a reference implementation of it. There is no
algorithm here that is not already in the library; the gap it closes is that
nobody knows what the convention should be.

## The convention

**Index** the cells the document falls in, one term per level:

```
G3RJM98NM9  →  G3R  G3RJ  G3RJM  G3RJM9  G3RJM98  G3RJM98N
```

Six terms, levels 3 to 8. Levels 1 and 2 match a sixth of the world and narrow
nothing down; levels 9 and 10 are unique to one document, so they are a term the
index carries and no query ever asks for.

**Query** the cell the point falls in *and its eight neighbours*, combined with
OR:

```
within 100 m of #G3RJM-98NM9, at level 7:
G3RJM98  G3RJM9D  G3RJM9C  G3RJM99  G3RJM90  G3RJM91  G3RJM92  G3RJM97  G3RJM9F
```

The two sides are **not** symmetrical, and that is the whole point of writing
this down.

## Why the neighbours are not optional

A boundary does not care how close two things are. Two doors a metre apart can
fall either side of one and share no prefix at all, so searching the single cell
a point falls in silently loses a share of what is near it.

Measured over 20,000 random pairs, at the level `level_for` picks for the radius:

| distance apart | found by the cell alone | found by the cell and its neighbours |
| ---: | ---: | ---: |
| 50 m | 81.8 % | **100 %** |
| 100 m | 63.6 % | **100 %** |

A search that returns two thirds of what is there is worse than one that fails,
because nothing about it looks wrong. This is the same one-directional guarantee
the specification states in section 10.2: a shared prefix proves proximity,
proximity does not promise a shared prefix, and the eight neighbours are what
turns it around.

## Choosing the level

One rule: **the cell must be at least as large as the search radius.** The nine
cells reach exactly one cell beyond the point in every direction, so a radius
larger than a cell escapes them no matter how many neighbours are added.

| radius over cell size | what happens |
| --- | --- |
| under 1 | every nearby document is found |
| over 1 | documents go missing, and nothing says so |

Measured: at level 7 (320 m cells) a 500 m radius found 62.3 % even with the
neighbours included. `level_for(radius, latitude)` picks the finest level that
still covers, which is the fewest documents for the engine to sift.

It takes the latitude because a cell narrows toward the poles by the cosine of
it while its height does not change. At 60° a cell is half the width it is at
the equator, so a level chosen from the equatorial figure quietly stops
covering. This is the kind of thing that works in testing in London and fails in
Reykjavík.

| level | cell, north to south | cell, east to west at the equator |
| ---: | ---: | ---: |
| 3 | 200 km | 267 km |
| 4 | 40 km | 53 km |
| 5 | 8.0 km | 10.7 km |
| 6 | 1.6 km | 2.1 km |
| 7 | 320 m | 427 m |
| 8 | 64 m | 85 m |

## The antimeridian is not a wall

The 180th meridian is a convention, not an edge, and two points either side of
it can be metres apart. The neighbours of the westernmost cell include cells
from the far side, so a query there reaches across and finds them.

The poles are a real edge, because there is no cell above the north pole, and the
query comes back with fewer than nine terms rather than naming one that does not
exist and would match nothing forever.

Both are tested, because both are the kind of thing that is discovered by a user
in Fiji rather than by a developer.

## Recipes

The terms are opaque strings of digits and capitals. Every engine below needs
the field treated as **keyword**, not analysed text: an analyser that lowercases
or splits on anything will not match what was indexed.

**Elasticsearch or OpenSearch**

```json
{ "mappings": { "properties": { "cells": { "type": "keyword" } } } }
```
```json
{ "query": { "terms": { "cells": ["G3RJM98", "G3RJM9D", "G3RJM9C", "..."] } } }
```

**Lucene**: a `StringField` per term, and a `TermInSetQuery` over the nine.

**SQLite FTS5**: the terms are already tokens; join them with `OR`:

```sql
CREATE VIRTUAL TABLE docs USING fts5(name, cells);
SELECT name FROM docs WHERE cells MATCH 'G3RJM98 OR G3RJM9D OR G3RJM9C';
```

**PostgreSQL full text**, or better, do not. `sql/` in this repository does the
same job with a plain B-tree and a prefix, which is faster and needs no second
column. Use this convention there only if the codes are already inside a
`tsvector` for another reason.

**Meilisearch, Typesense and the rest**: an array-of-strings field with an
exact-match filter. The shape is the same everywhere.

## Ranking

These terms answer *which documents are near*, not *which is nearest*. They
carry no distance, and an engine scoring them by term frequency will rank
nonsense.

Sort by distance after retrieval: the cell block is small, and `GPC.distance` on
the codes gives metres without a second lookup, because the code is the
coordinate. Retrieve with the terms, then order the handful that come back.

## Testing

```
python -m unittest discover --start-directory search --top-level-directory search
```

The tests that matter are in `TheClaim`. One measures that nothing within the
radius is missed over 20,000 pairs; the other measures that the single-cell
version really does lose things, because if it did not, the eight neighbours
would be waste and this convention would be more complicated than it needs to
be. Both are measurements rather than examples: the failure they guard against
is statistical, and it passes every case anybody tries by hand.
