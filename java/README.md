# Grid Point Code (GPC)

## Overview

Grid Point Code (GPC) names one cell of a fixed grid laid over the Earth with a
ten-character code. This Java implementation encodes and decodes between
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

- **Ten characters, fixed.** Every location, everywhere, same length.
- **Prefix locality.** Sorting codes as plain strings sorts them geographically.
- **Offline.** No network access, no API, no data files.
- **No dependencies.** Nothing beyond the Java standard library.
- **Reads version 1 codes.** Every code ever issued still resolves.

## Requirements

Java 21 or later. No third-party dependencies.

## Usage

### Encoding

```java
import ca.pranavpatel.algo.gridpointcode.GPC;

GPC.Encode(43.65, -79.38);          // "#G3RJM-98NM9"
GPC.Encode(43.65, -79.38, false);   // "G3RJM98NM9"
```

Latitude runs from -90 to 90 and longitude from -180 to 180, both inclusive.
The poles encode, and both ends of the antimeridian give the one code.

### Decoding

```java
import ca.pranavpatel.algo.gridpointcode.Area;
import ca.pranavpatel.algo.gridpointcode.Coordinates;

Coordinates point = GPC.Decode("#G3RJM-98NM9");
point.Latitude;   // 43.650006
point.Longitude;  // -79.380004

Area cell = GPC.DecodeToArea("#G3RJM-98NM9");
cell.South; cell.West; cell.North; cell.East;
```

`Decode` returns the centre of the cell the code names, rounded to six decimal
places. `DecodeToArea` returns its boundaries.

### Validating and classifying

```java
import ca.pranavpatel.algo.gridpointcode.Classification;
import ca.pranavpatel.algo.gridpointcode.CodeClass;

GPC.IsValid("#G3RJM-98NM9");   // true
GPC.Classify("#G3RJM-98NM9");  // CodeClass.GEOMETRIC
GPC.Classify("XG3RJ98NM9");    // CodeClass.RESERVED
GPC.Classify("nonsense");      // CodeClass.INVALID

Classification result = GPC.Validate("G3RJM98NMQ");
result.Kind;    // CodeClass.INVALID
result.Reason;  // "GPC_CHAR"
```

No encoded code begins with `X`, so that space is reserved rather than wasted.
A reserved code is well formed and names no cell; it is not a typing error, and
the two are kept apart. `Decode` throws `GPCException` with reason
`GPC_RESERVED` for one.

### Errors

`GPCException` extends `IllegalArgumentException`, which is what version 1
threw, and carries a reason code:

```java
import ca.pranavpatel.algo.gridpointcode.GPCException;

try {
    GPC.Decode("XG3RJ98NM9");
} catch (GPCException error) {
    error.getReason(); // "GPC_RESERVED"
}
```

Reasons are `LATITUDE` and `LONGITUDE` for coordinates, and `GPC_NULL`,
`GPC_LENGTH`, `GPC_CHAR`, `GPC_CHECK`, `GPC_RESERVED` and `GPC_RANGE` for codes.
The last belongs to version 1 only.

### The optional check character

For voice, radio and paper, a code may carry an eleventh character after a star.
It detects every single-character error and every adjacent transposition.

```java
GPC.CheckCharacter("#G3RJM-98NM9");  // "T"
GPC.Decode("#G3RJM-98NM9*T");        // 43.650006, -79.380004
GPC.IsValid("#G3RJM-98NM9*Z");       // false, the check does not hold
```

The check form is not canonical and is never emitted unless asked for. Storage
and interchange use the ten characters.

### Version 1 codes

```java
GPC.Decode("#FN5G-CDKL-HDC");      // 43.65, -79.38, read as version 1
GPC.DecodeV1("#FN5G-CDKL-HDC");    // the same, said explicitly
GPC.IsValidV1("#FN5G-CDKL-HDC");   // Validation, IsValid = true
```

`Decode` dispatches on length once separators are stripped: ten characters is
version 2, eleven is version 1. There is no version 1 encoder — the old format
is readable, not writable. Anyone who still needs to write version 1 codes
should pin `1.1.x`.

Note that the dispatch is on length alone, so an eleven-character string that
happens to be a valid version 1 code decodes as one.

## Reading a code

- **Confirm before acting.** Nearly 29 % of single-character typos produce a
  location in the right region and the wrong place. Show the decoded point on a
  map, or check it against something the reader recognises, before acting on it.
- **Case and separators do not matter.** `#G3RJM-98NM9`, `g3rjm98nm9` and
  `G3RJM 98NM9` are the same code. Confusable letters are read as the symbols
  they stand for: `O` as `0`, `I` as `1`, `S` as `5`, `Z` as `2`, `B` as `8`,
  `A` as `4`, `E` as `3` and `V` as `W`. `L` is a real symbol and is never read
  as `1`.
- **A code names a cell, not a point.** `Decode` returns the centre, so a
  coordinate carrying more precision than the 2.56 m cell does not come back
  unchanged. Encoding what you decoded always returns the same code.

## Changelog

See [CHANGELOG.md](https://github.com/octopranav/Grid-Point-Code/blob/main/CHANGELOG.md) for what changed in each release.

## License

Licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

## Contributing

Contributions are welcome! Please submit issues or pull requests via GitHub.
