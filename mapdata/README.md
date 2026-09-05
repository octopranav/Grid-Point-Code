# Codes in open map data

```
python mapdata/validate.py extract.osm
python mapdata/validate.py places.geojson --tolerance 5
```

## Start with the case against

A code is derived from a coordinate. Writing it into a file that already has the
coordinate stores the same fact twice, and the two copies do not stay equal: the
node gets corrected by three metres in a later survey, the coordinate changes,
and the tag does not. The file now carries two answers and nothing in it says
which one is older.

Nothing catches this on its own, because a stale code is a perfectly well-formed
code that decodes to a perfectly plausible place a short walk away. That is
worse than a broken value, which somebody would notice.

So for **OpenStreetMap and any other shared database that holds the geometry**,
the answer is: do not tag it. Derive it. The library computes a code from a node
in microseconds, `sql/` computes it in the database, and neither can go stale.
A project that adds derived data to a shared map is asking every future editor
to maintain something the machine can recompute.

## Where a code does earn its place

When it is the thing being exchanged rather than a copy of something else:

* **A file handed to someone who will read it aloud.** A delivery manifest, a
  list of survey points, a column in a spreadsheet a person types from. The code
  is what the human uses; the coordinate is what the machine uses; both belong
  in the file because they are for different readers.
* **Data with no geometry at all.** A CSV of addresses, a database of asset
  locations, a form submission. Here the code *is* the location.
* **A reference somebody quoted.** If a report says `#G3RJM-0M6DX`, that string
  is a fact about the report and not derived from anything in your file.

In every one of those the code is exchanged, so it is worth writing the check
character too. It costs one character and catches every single-symbol error and
every adjacent transposition, which is what a code read over a phone needs:

```python
from gridpointcode_algo_pranavpatel_ca import GPC

GPC.encode(43.6426, -79.3871)           # '#G3RJM-0M6DX'
GPC.with_check('#G3RJM-0M6DX')          # '#G3RJM-0M6DX*J'
```

That value is checked by `audit/examples.py` rather than typed here, because the
first draft of this page had the check character wrong and nothing would have
noticed.

## The convention

If you are going to write one down:

| | |
| --- | --- |
| key | `ref:gridpointcode` |
| value | the formatted code, `#G3RJM-0M6DX`, optionally with `*` and its check character |
| goes on | a **node** or a **Point** feature, never a way, area or relation |

The last row is the one people get wrong. A code names a cell 2.56 m across. On
a building outline or a street it names one arbitrary point of something that is
not a point, and no reader can tell which point was meant. Put it on the node
the code actually refers to, or leave it off.

This project does not get to decide what OpenStreetMap tags things. The key
above is what this repository uses and validates; it is a proposal, not an
established tag, and it is deliberately in the `ref:` namespace so that it reads
as what it is: a reference somebody supplied, not a computed attribute.

## The check

`validate.py` reads OpenStreetMap XML and GeoJSON, and for every tagged element
recomputes the code from the element's own position. Anything that disagrees is
reported with the distance, which is what tells one kind of mistake from
another:

```
Moved since it was tagged: says #G3RJM-0M6DX
    -- but its position is #G3RJM-0M33P, 38.4 m away
Code from somewhere else: says #G3RJM-0M6DX
    -- but its position is #R0NJ1-6FT1N, 5,713,977.7 m away
Mistyped: is not a code: #G3RJM-0M6DQ -- GPC_CHAR
A park, which is not a point: is a Polygon carrying a code, but a code names a
    point -- use the point it refers to, or leave it off
```

Thirty-eight metres is a node that moved. Five thousand kilometres is a code
pasted from the wrong row. `GPC_CHAR` is a typo that a check character would
have caught before it reached the file.

`--tolerance` in metres accepts drift, for data where the geometry is known to
be approximate. The default is zero: the code must name the cell the point is
actually in.

Both fixtures under `examples/` are generated from real coordinates rather than
typed, so the good one is correct by construction. The wrong one carries each
failure above, and the tests feed every one of them to the checker, because a checker
nobody has watched fail is a checker nobody knows works.

## Testing

```
python -m unittest discover --start-directory mapdata --top-level-directory mapdata
```
