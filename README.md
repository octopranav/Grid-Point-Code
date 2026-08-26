# Grid Point Code (GPC)

[![CI](https://github.com/octopranav/Grid-Point-Code/actions/workflows/ci.yml/badge.svg)](https://github.com/octopranav/Grid-Point-Code/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/ca.pranavpatel.algo/gridpointcode.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/ca.pranavpatel.algo/gridpointcode)
[![NuGet](https://img.shields.io/nuget/v/Ca.Pranavpatel.Algo.GridPointCode?label=NuGet)](https://www.nuget.org/packages/Ca.Pranavpatel.Algo.GridPointCode)
[![npm (scoped)](https://img.shields.io/npm/v/@pranavpatel.ca/algo-gridpointcode)](https://www.npmjs.com/package/@pranavpatel.ca/algo-gridpointcode)
[![PyPI](https://img.shields.io/pypi/v/gridpointcode-algo-pranavpatel-ca)](https://pypi.org/project/gridpointcode-algo-pranavpatel-ca/)

## Overview

**Grid Point Code (GPC)** names one cell of a fixed grid laid over the Earth with a compact ten-character code. Conversion runs offline in both directions, and every character is a refinement of the ones before it, so **two codes that begin with the same k characters name points in the same level-k cell.**

## Features

* **Ten Characters, Fixed** – Every location, everywhere, same length
* **Prefix Locality** – A shared prefix means a shared cell, for every pair of points
* **Offline Support** – Works without internet or APIs
* **Zero Dependencies** – No third-party packages in any of the four ports
* **Formatted Output** – Easy-to-read `#XXXXX-XXXXX` format
* **A Spatial API** – Cells, neighbours, containment, distance, the short form,
  typo correction and the 48-bit integer form, all following from the guarantee
* **Reads Version 1 Codes** – Every code ever issued still resolves
* **Open Source** – Licensed under Apache 2.0

## Code Structure

* **GPC Format**: `#XXXXX-XXXXX` (10-character alphanumeric string)
* **Alphabet**: `0123456789CDFGHJKLMNPRTWX` (25 symbols, no vowels, digits first)
* **Cell**: 2.56 m north to south by 3.42 m east to west at the equator

| Shared characters | Cell, north-south | Cell, east-west | Scale |
| ---: | ---: | ---: | --- |
| 1 | 5,000.9 km | 6,679.2 km | Continent |
| 3 | 200.0 km | 267.2 km | Region |
| 5 | 8.0 km | 10.7 km | District |
| 7 | 320.1 m | 427.5 m | Street |
| 10 | 2.6 m | 3.4 m | Doorway |

## Precision and Limits

* A code names a cell, not a point. `decode` returns the centre of that cell, so a coordinate carrying more precision than the 2.56 m cell does not come back unchanged. Encoding what you decoded always returns the same code.
* A shared prefix proves proximity. Proximity does not promise a shared prefix: level-1 boundaries lie on the equator, on 45 degrees north and south, and on every 60th meridian, and two points a few metres apart across one of those lines share nothing at all. Of random pairs 100 m apart, 91.45 % share at least six characters.
* Nearly 29 % of single-character typos produce a location in the right region and the wrong place. Show the decoded point on a map, or check it against something the reader recognises, before acting on it.

---

## Requirements

| Port | Requires |
| --- | --- |
| Python | 3.9 or later |
| TypeScript | Node.js 22 or later |
| C# | .NET 9.0 or .NET 10.0 |
| Java | Java 21 or later |

None of the four ports has a third-party dependency.

## Installation

### Python

```bash
pip install gridpointcode-algo-pranavpatel-ca
```

### TypeScript (Node.js)

```bash
npm i @pranavpatel.ca/algo-gridpointcode
```

### C# (.NET)

Add the project reference via NuGet:

```bash
dotnet add package Ca.Pranavpatel.Algo.GridPointCode
```

### Java (Maven)

Add the following to your `pom.xml`:

```xml
<dependency>
    <groupId>ca.pranavpatel.algo</groupId>
    <artifactId>gridpointcode</artifactId>
    <version>1.1.0</version>
</dependency>
```

---

## Usage Examples

### Python

```python
from gridpointcode_algo_pranavpatel_ca import GPC

# Encode
gpc_code = GPC.encode(43.65000, -79.38000)
print(gpc_code)  # Output: #G3RJM-98NM9

# Decode
lat, lng = GPC.decode("#G3RJM-98NM9")
print(lat, lng)  # 43.650006 -79.380004

# Validate and classify
print(GPC.is_valid("#G3RJM-98NM9"))   # True
print(GPC.classify("XG3RJ98NM9"))     # RESERVED

# Version 1 codes still decode
print(GPC.decode("#FN5G-CDKL-HDC"))   # (43.65, -79.38)
```

---

### TypeScript

```ts
import { GPC } from '@pranavpatel.ca/algo-gridpointcode';

// Encode
const code = GPC.encode(43.65, -79.38);
console.log(code);  // #G3RJM-98NM9

// Decode
const [lat, lng] = GPC.decode('#G3RJM-98NM9');
console.log(lat, lng);  // 43.650006 -79.380004

// Validate and classify
console.log(GPC.isValid('#G3RJM-98NM9'));  // true
console.log(GPC.classify('XG3RJ98NM9'));   // 'RESERVED'

// Version 1 codes still decode
console.log(GPC.decode('#FN5G-CDKL-HDC'));  // [43.65, -79.38]
```

---

### C\#

```csharp
using Ca.Pranavpatel.Algo.GridPointCode;

// Encode
string gpc = GPC.Encode(43.65000, -79.38000);  // Toronto
// Output: #G3RJM-98NM9

// Decode
(double lat, double lng) = GPC.Decode("#G3RJM-98NM9");

// Validate and classify
bool isValid = GPC.IsValid("#G3RJM-98NM9");
CodeClass kind = GPC.Classify("XG3RJ98NM9");  // CodeClass.Reserved

// Version 1 codes still decode
(double v1Lat, double v1Lng) = GPC.Decode("#FN5G-CDKL-HDC");
```

---

### Java

```java
import ca.pranavpatel.algo.gridpointcode.CodeClass;
import ca.pranavpatel.algo.gridpointcode.Coordinates;
import ca.pranavpatel.algo.gridpointcode.GPC;

// Encode
String gpc = GPC.Encode(43.65, -79.38);  // Toronto
// Output: #G3RJM-98NM9

// Decode
Coordinates coords = GPC.Decode("#G3RJM-98NM9");
double lat = coords.Latitude;   // 43.650006
double lng = coords.Longitude;  // -79.380004

// Validate and classify
boolean isValid = GPC.IsValid("#G3RJM-98NM9");
CodeClass kind = GPC.Classify("XG3RJ98NM9");  // CodeClass.RESERVED

// Version 1 codes still decode
Coordinates old = GPC.Decode("#FN5G-CDKL-HDC");
```

---

## The Locality API

The guarantee is only useful if a caller can act on it, so each port carries the
operations that follow from it rather than leaving everyone to re-derive the
arithmetic. Every one of them is exact integer arithmetic except `distance`.

| What it does | Python | TypeScript | C# and Java |
| --- | --- | --- | --- |
| The region the first k characters name | `cell` | `cell` | `Cell` |
| Whether a code lies in a cell | `contains` | `contains` | `Contains` |
| The eight cells around one | `neighbours` | `neighbours` | `Neighbours` |
| How big a cell is at a level | `cell_dimensions` | `cellDimensions` | `CellDimensions` |
| Great-circle metres between two cells | `distance` | `distance` | `Distance` |
| The grid row and column | `decode_to_grid` | `decodeToGrid` | `DecodeToGrid` |
| The last five characters | `shorten` | `shorten` | `Shorten` |
| The full code, from those five and a reference | `recover_short` | `recoverShort` | `RecoverShort` |
| Codes one typo away, best first | `suggest_corrections` | `suggestCorrections` | `SuggestCorrections` |
| The 48-bit integer form | `to_integer`, `from_integer` | `toInteger`, `fromInteger` | `ToInteger`, `FromInteger` |
| An RFC 5870 `geo:` URI | `to_geo_uri`, `from_geo_uri` | `toGeoURI`, `fromGeoURI` | `ToGeoURI`, `FromGeoURI` |
| Degrees, minutes and seconds | `to_dms`, `from_dms` | `toDMS`, `fromDMS` | `ToDMS`, `FromDMS` |
| Advisory word screening | `screen` | `screen` | `Screen` |
| Batch and streaming conversion | `encode_all`, `encode_stream` | `encodeAll`, `encodeStream` | `EncodeAll`, `EncodeStream` |

```python
GPC.cell("#G3RJM-98NM9", 5)                    # 'G3RJM', the 8.0 by 10.7 km cell
GPC.contains("G3RJM", "G3RJM98NM9")            # True -- the prefix test, exactly
GPC.neighbours("G3RJM")                        # the eight cells around it

GPC.shorten("#G3RJM-98NM9")                    # '98NM9', the second printed group
GPC.recover_short("-98NM9", 43.66, -79.39)     # '#G3RJM-98NM9', near a reference

GPC.suggest_corrections("#G3RJT-98NM9", 43.65, -79.38)
# ['#G3RJM-98NM9'] -- the geography does the work a check digit would
```

A few things worth knowing before reaching for these:

* **`contains` is the prefix test and nothing else.** There is no tolerance and
  no edge case at a boundary, because the guarantee makes the string answer and
  the geometric answer the same answer.
* **Columns wrap at the antimeridian and rows do not**, so a cell in the top or
  bottom row has five neighbours rather than eight. The three that would lie off
  the grid are absent from the result rather than present and empty.
* **`distance` is the one operation that is not bit-identical across the four
  ports.** No standard library rounds sine, cosine or arc sine correctly, so the
  ports agree to about a millimetre rather than exactly, and its conformance
  vectors are the only ones in the corpus asserted to a tolerance. Anything that
  needs a reproducible ordering should rank on the grid indices instead.
* **The short form is a convenience and the ten characters are the form of
  record.** Recovery is exact whenever the reference is within half a level-5
  cell of the true point on each axis -- 4.0 km of latitude, 5.3 km of longitude
  at the equator -- and outside that box it returns a plausible location 8 or
  10 km away.
* **`suggest_corrections` corrects, it does not detect.** It is not a checksum,
  and the advice above about confirming on a map applies to its output as much
  as to anything else.
* **`screen` advises and never blocks.** Nothing in any port refuses to encode,
  decode or validate because of what it found, and it reports the version of the
  list it used whether or not anything matched.

The optional check character of the specification is the one mechanism here that
detects rather than corrects, and it is never present unless someone asks for
it: `#G3RJM-98NM9*T`.

---

## Testing

Every port is built and tested on every push. Each suite reads the same
conformance vectors from [`test_data/`](test_data/), and each one reproduces the
digest of a shared hundred-thousand-point sample, so the four implementations
are held to byte-identical output rather than to four separate sets of
expectations.

| Port | From that port's directory |
| --- | --- |
| Python | `python -m unittest discover -s tests -t .` |
| TypeScript | `npm ci && npm test` |
| C# | `dotnet run --project gpc.tests/gpc.tests.csproj -f net10.0` |
| Java | `mvn test` |

[`test_data/README.md`](test_data/README.md) describes the vector files and the
generated sample. [`.github/workflows/README.md`](.github/workflows/README.md)
describes what runs in CI and how releases are published.

---

## Specification

[SPEC.md](SPEC.md) is the normative specification for **version 2** of the
format. It defines the format precisely enough to implement from without
reading any source, and it carries the measurements behind every claim it
makes. [`reference/`](reference/) is its executable companion: it checks every
exact claim the document makes and reproduces every measured figure in it.

All four ports in this repository implement version 2. **The published packages
are still 1.1.0**, which is version 1; version 2 ships as 2.0.0 on the same four
package names.

Version 1 codes are eleven characters and version 2 codes are ten, so `decode`
tells them apart on length and reads both. There is no version 1 encoder in
2.0.0 -- the old format is readable, not writable -- and Appendix B of the
specification describes what a port needs to keep reading it. Anyone who still
needs to write version 1 codes should pin `1.1.x`, which stays published.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for what changed in each release.

## License

Licensed under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).

## Contributing

Pull requests, issues, and suggestions are welcome!
Please use GitHub to suggest features, report bugs, or contribute improvements.
