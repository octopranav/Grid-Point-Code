# Grid Point Code (GPC) for Python

## Overview

Grid Point Code (GPC) names one cell of a fixed grid laid over the Earth with a
ten-character code. This Python implementation encodes and decodes between
latitude/longitude coordinates and codes offline, with no dependencies.

The format is specified in [SPEC.md](https://github.com/octopranav/Grid-Point-Code/blob/main/SPEC.md).

## What a code is

```
#G3RJM-98NM9
```

Ten characters, always. The first divides the world into 24 cells of 45 by 60
degrees. Each of the nine after it divides the cell named so far into 25 parts,
five by five. After ten characters the cell is 2.56 m north to south and 3.42 m
east to west at the equator.

Every character is a refinement of the ones before it, so **two codes that begin
with the same k characters name points in the same level-k cell.** That is
containment, not correlation: it holds for every pair of points without
exception.

| Shared characters | Cell, north-south | Cell, east-west | Scale |
| ---: | ---: | ---: | --- |
| 1 | 5,000.9 km | 6,679.2 km | Continent |
| 3 | 200.0 km | 267.2 km | Region |
| 5 | 8.0 km | 10.7 km | District |
| 7 | 320.1 m | 427.5 m | Street |
| 10 | 2.6 m | 3.4 m | Doorway |

A shared prefix proves proximity. Proximity does not promise a shared prefix:
level-1 boundaries lie on the equator, on 45 degrees north and south, and on
every 60th meridian, and two points a few metres apart across one of those
lines share nothing.

## Features

* **Ten characters, fixed.** Every location, everywhere, same length.
* **Prefix locality.** Sorting codes as plain strings sorts them geographically.
* **Offline.** No network access, no API, no data files.
* **No dependencies.** Nothing beyond the Python standard library.
* **A spatial API on top of the guarantee.** Cells, neighbours, containment,
  distance, the short form, typo correction and the integer form.
* **Reads version 1 codes.** Every code ever issued still resolves.

## Installation

```bash
pip install gridpointcode-algo-pranavpatel-ca
```

## Requirements

Python 3.9 or later. No third-party dependencies.

## Usage

### Encoding

```python
from gridpointcode_algo_pranavpatel_ca import GPC

GPC.encode(43.65000, -79.38000)          # '#G3RJM-98NM9'
GPC.encode(43.65000, -79.38000, False)   # 'G3RJM98NM9'
```

Latitude runs from -90 to 90 and longitude from -180 to 180, both inclusive.
The poles encode, and both ends of the antimeridian give the one code.

### Decoding

```python
GPC.decode("#G3RJM-98NM9")        # (43.650006, -79.380004)
GPC.decode_to_area("#G3RJM-98NM9")
# (43.64999424000001, -79.3800192, 43.650017279999986, -79.37998848)
```

`decode` returns the centre of the cell the code names, rounded to six decimal
places. `decode_to_area` returns its boundaries, south, west, north and east.

### Validating and classifying

```python
GPC.is_valid("#G3RJM-98NM9")   # True
GPC.classify("#G3RJM-98NM9")   # 'GEOMETRIC'
GPC.classify("XG3RJ98NM9")     # 'RESERVED'
GPC.classify("nonsense")       # 'INVALID'
GPC.validate("G3RJM98NMQ")     # ('INVALID', 'GPC_CHAR')
```

No encoded code begins with `X`, so that space is reserved rather than wasted.
A reserved code is well formed and names no cell; it is not a typing error, and
the two are kept apart. `decode` raises with reason `GPC_RESERVED` for one.

### Errors

`GPCError` subclasses `ValueError` and carries a reason code:

```python
from gridpointcode_algo_pranavpatel_ca import GPCError

try:
    GPC.decode("XG3RJ98NM9")
except GPCError as error:
    error.reason    # 'GPC_RESERVED'
```

Reasons are `LATITUDE` and `LONGITUDE` for coordinates, and `GPC_NULL`,
`GPC_LENGTH`, `GPC_CHAR`, `GPC_CHECK`, `GPC_RESERVED` and `GPC_RANGE` for codes.
`GPC_RANGE` covers both an eleven-character version 1 code out of range and an
integer form outside 0 to 25^10 - 1. The locality API adds `GPC_LEVEL` for a
level outside 1 to 10, and `GPC_DMS` and `GPC_GEO` for text the two coordinate
parsers do not accept; none of the three ever comes back from `validate`.

### The locality API

A shared prefix means a shared cell. These are the operations that let a caller
act on that without re-deriving the arithmetic.

```python
# A cell is the first k characters: the region those characters name.
GPC.cell("#G3RJM-98NM9", 5)                    # 'G3RJM', a cell 8.0 by 10.7 km
GPC.contains("G3RJM", "G3RJM98NM9")            # True -- the prefix test, exactly
GPC.neighbours("G3RJM")                        # the eight cells around it
GPC.cell_dimensions(5)                         # spans in degrees, then in metres
GPC.distance("#G3RJM-98NM9", "#6LK4X-NRP0R")   # 15566716.58 metres

# The row and column, for building your own spatial structure.
GPC.decode_to_grid("#G3RJM-98NM9")             # (5800781, 3275390)

# The integer form: 48 bits, big-endian, and it sorts spatially too.
GPC.to_integer("G3RJM98NM9")                   # 50180843496709
GPC.from_integer(50180843496709)               # '#G3RJM-98NM9'
```

Columns wrap at the antimeridian and rows do not, so a cell in the top or bottom
row has five neighbours rather than eight, and the missing three are absent from
the result rather than present and empty.

`distance` is the one operation here that is not bit-identical across the four
ports: no standard library rounds sine, cosine or arc sine correctly, so they
agree to about a millimetre rather than exactly. Anything that needs a
reproducible ordering should rank on the grid indices instead.

### The short form

The last five characters of a code -- literally the second printed group -- name
a position uniquely inside a level-5 cell, which is 8.0 by 10.7 km.

```python
GPC.shorten("#G3RJM-98NM9")                    # '98NM9'
GPC.recover_short("-98NM9", 43.66, -79.39)     # '#G3RJM-98NM9'
```

Recovery is exact whenever the reference lies within half a cell of the true
point on each axis: 0.036 degrees of latitude, which is 4.0 km, and 0.048 of
longitude, 5.3 km at the equator and less elsewhere. Outside that box it returns
a neighbouring cell's copy of the same offset, which is a plausible location 8
or 10 km away, so a caller that cannot bound its reference should not use the
short form. **The full ten characters are the form of record.**

### Correcting a typo

A hierarchical code bounds the damage a typo does, and the same structure
locates it. Given a reference point, `suggest_corrections` returns the codes one typo
away that are plausible near it, best first.

```python
GPC.suggest_corrections("#G3RJT-98NM9", 43.65, -79.38)
# ['#G3RJM-98NM9']
```

The window is three by three cells at the level you pass, so the level to choose
is the one that comfortably exceeds the uncertainty in your reference. Level 6
is the default: it suits a device fix or a named suburb, and returns a single
candidate in the median case.

This corrects rather than detects, and it is not a checksum. **Show the decoded
point on a map before acting on it** -- nearly 29 % of single-character typos
produce a location in the right region and the wrong place.

### Coordinate conversions

Two textual forms, for reading off a survey sheet and for writing a link.

```python
GPC.to_geo_uri(43.650006, -79.380004)          # 'geo:43.650006,-79.380004'
GPC.from_geo_uri("geo:43.65,-79.38")           # (43.65, -79.38)

GPC.to_dms(43.65, -79.38)                      # '43°39\'00.00"N, 79°22\'48.00"W'
GPC.from_dms("43°39'00.00\"N, 79°22'48.00\"W")   # (43.65, -79.38)
```

The `geo:` URI is exact: six decimal places, which is what `decode` returns, so
a code written out this way and read back encodes to the same code every time.
Degrees, minutes and seconds are for a person to read, and are rounded to a
hundredth of a second -- lossy by up to 0.155 m, though a decoded code still
survives the trip, because a cell centre sits eight times further from the
nearest boundary than that.

### Screening

The alphabet has no vowels, so no English word can appear in a code. Words that
substitute digits for letters still can, and at ten characters there is no spare
code space to skip them.

```python
version, spans = GPC.screen("#G3RJM-98NM9")
# ('2026.2', [])  -- the version, and nothing matched
```

`screen` reports and never blocks: nothing in this package refuses to
encode, decode or validate because of what it found. It returns the version of
the list either way, so a caller can tell "clean under this list" from "never
screened". Roughly one code in a thousand matches something.

### Bulk conversion

```python
GPC.encode_all([(43.65, -79.38), (0.0, 0.0)])  # ['#G3RJM-98NM9', '#JPPPP-00000']
GPC.decode_all(["#G3RJM-98NM9"])               # [(43.650006, -79.380004)]

for code in GPC.encode_stream(points):         # lazily, one at a time
    ...

for latitude, longitude in GPC.decode_stream(codes):
    ...
```

The batch form throws on the first bad row rather than dropping it silently. The
streaming form produces codes as they are asked for, so a caller that wants to
handle failures row by row can.

### Normalising and formatting

```python
GPC.normalise("  g3rjm-98nm9  ")   # ('G3RJM98NM9', None) -- case and spacing
GPC.normalise("#G3RJM-9BNM9")      # ('G3RJM98NM9', None) -- B read as 8
GPC.normalise("#G3RJM-98NM9*T")    # ('G3RJM98NM9', 'T') -- payload and check
GPC.format_gpc("G3RJM98NM9")       # '#G3RJM-98NM9'
```

`normalise` is the step every other entry point runs first: it case-folds with
ASCII rules only, removes `#`, `-` and whitespace wherever they appear, applies
the alias table, and splits off a check character if there is one. It is
idempotent, so normalising an already-normalised code returns it unchanged.
`format_gpc` goes the other way, adding the `#` and the group separator for
display.

Use them when you need the two halves of a check form separately, or when you
want to store the bare ten characters and print the formatted one.

### Checking coordinates before encoding

```python
GPC.is_valid_coordinates(43.65, -79.38)   # (True, '')
GPC.is_valid_coordinates(91.0, 0.0)       # (False, 'LATITUDE')
```

`encode` raises for a coordinate outside the domain. When you would rather ask
than catch -- validating a form field, filtering a dataset row by row -- this
answers without raising and names the axis at fault.

### The raw grid

```python
GPC.to_grid(43.65, -79.38)          # (5800781, 3275390) -- coordinates to the grid
GPC.grid_to_code(5800781, 3275390)  # 'G3RJM98NM9'       -- grid to a code
GPC.code_to_grid("G3RJM98NM9")      # (5800781, 3275390) -- code back to the grid
GPC.decode_to_grid("#G3RJM-98NM9")  # (5800781, 3275390) -- the same, from any form
```

The grid is 7,812,500 rows by 11,718,750 columns, numbered from 0 at latitude
-90 and longitude -180. These four are the layer `encode` and `decode` are built
from, exposed because a caller building its own spatial structure -- a tile
index, a nearest-neighbour search, a raster join -- usually wants the integers
rather than the string. `decode_to_grid` is the one to reach for if you already
have a code; the other three are there when you are working from coordinates or
constructing codes directly.

### The optional check character

A code is ten characters and carries no checksum, because eleven characters
everywhere would be a high price for a problem that only exists once a person is
involved. So the eleventh character is optional, written after a star, and you
add it exactly where the people are.

**What it buys.** It detects **every single-character error** and **every
transposition of two adjacent characters**, the two mistakes people make when
they hear a code, write it down, and type it in later. Verified exhaustively:
over 4,000 random codes, all 1,056,000 possible single-symbol errors and all
38,389 adjacent transpositions were caught.

**Why that matters.** Without it, a mistyped code is usually still a valid code.
Nearly 29 % of single-character typos land somewhere plausible in the right
region (the wrong door, the wrong block, sometimes 20 km away), and nothing in
the format objects, because very nearly every ten-character string over the
alphabet names some real cell. This is the one mechanism that says "that is not
what was sent" instead of quietly naming the wrong place.

**When to use it.** Wherever a code is read aloud, spoken over a radio or a
telephone, written by hand, or printed on a sign or a delivery note: anywhere a
person is in the path. Not for machine-to-machine traffic, storage or URLs,
where it is only an extra character to strip.

```python
GPC.with_check("#G3RJM-98NM9")        # '#G3RJM-98NM9*T', the whole form
GPC.check_character("#G3RJM-98NM9")   # 'T', the character alone
GPC.decode("#G3RJM-98NM9*T")          # (43.650006, -79.380004), check confirmed
GPC.is_valid("#G3RJM-98NM9*Z")        # False, the check does not hold
```

Reach for `with_check` rather than composing the string yourself. Building it
by hand is three operations and two ways to be quietly wrong -- the star
dropped, or the character spliced inside the group separator rather than after
it -- and neither mistake is caught by anything, because the result is a string
nobody validated. It recomputes rather than trusting, so a code arriving with a
wrong check character comes back with a right one.

**It is never in the way.** The check form is **not canonical** and is never
emitted unless asked for: `#G3RJM-98NM9` and `#G3RJM-98NM9*T` denote the same
place, storage and interchange use the ten characters, and a reader who drops
the star and the character loses only the detection. A code that arrives with a
*wrong* check character is refused with reason `GPC_CHECK` rather than decoded
to the wrong place.

### Version 1 codes

```python
GPC.decode("#FN5G-CDKL-HDC")      # (43.65, -79.38), read as version 1
GPC.decode_v1("#FN5G-CDKL-HDC")   # the same, said explicitly
GPC.is_valid_v1("#FN5G-CDKL-HDC") # (True, '')
```

`decode` dispatches on length once separators are stripped: ten characters is
version 2, eleven is version 1. There is no version 1 encoder, because the old format
is readable, not writable. Anyone who still needs to write version 1 codes
should pin `1.1.x`.

Note that the dispatch is on length alone, so an eleven-character string that
happens to be a valid version 1 code decodes as one.

## Reading a code

* **Confirm before acting.** Nearly 29 % of single-character typos produce a
  location in the right region and the wrong place. Show the decoded point on a
  map, or check it against something the reader recognises, before acting on it.
* **Case and separators do not matter.** `#G3RJM-98NM9`, `g3rjm98nm9` and
  `G3RJM 98NM9` are the same code. Confusable letters are read as the symbols
  they stand for: `O` as `0`, `I` as `1`, `S` as `5`, `Z` as `2`, `B` as `8`,
  `A` as `4`, `E` as `3` and `V` as `W`. `L` is a real symbol and is never read
  as `1`.
* **A code names a cell, not a point.** `decode` returns the centre, so a
  coordinate carrying more precision than the 2.56 m cell does not come back
  unchanged. Encoding what you decoded always returns the same code.

## Changelog

See [CHANGELOG.md](https://github.com/octopranav/Grid-Point-Code/blob/main/CHANGELOG.md) for what changed in each release.

## License

Licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

## Contributing

Contributions are welcome! Feel free to open issues or submit pull requests on GitHub.
