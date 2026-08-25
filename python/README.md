# Grid Point Code (GPC) – Python

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
The last belongs to version 1 only.

### The optional check character

For voice, radio and paper, a code may carry an eleventh character after a star.
It detects every single-character error and every adjacent transposition.

```python
GPC.check_character("#G3RJM-98NM9")   # 'T'
GPC.decode("#G3RJM-98NM9*T")          # (43.650006, -79.380004)
GPC.is_valid("#G3RJM-98NM9*Z")        # False, the check does not hold
```

The check form is not canonical and is never emitted unless asked for. Storage
and interchange use the ten characters.

### Version 1 codes

```python
GPC.decode("#FN5G-CDKL-HDC")      # (43.65, -79.38), read as version 1
GPC.decode_v1("#FN5G-CDKL-HDC")   # the same, said explicitly
GPC.is_valid_v1("#FN5G-CDKL-HDC") # (True, '')
```

`decode` dispatches on length once separators are stripped: ten characters is
version 2, eleven is version 1. There is no version 1 encoder — the old format
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
