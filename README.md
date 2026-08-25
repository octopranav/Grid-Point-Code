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
