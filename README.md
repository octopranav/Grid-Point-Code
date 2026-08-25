# Grid Point Code (GPC)

[![CI](https://github.com/octopranav/Grid-Point-Code/actions/workflows/ci.yml/badge.svg)](https://github.com/octopranav/Grid-Point-Code/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/ca.pranavpatel.algo/gridpointcode.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/ca.pranavpatel.algo/gridpointcode)
[![NuGet](https://img.shields.io/nuget/v/Ca.Pranavpatel.Algo.GridPointCode?label=NuGet)](https://www.nuget.org/packages/Ca.Pranavpatel.Algo.GridPointCode)
[![npm (scoped)](https://img.shields.io/npm/v/@pranavpatel.ca/algo-gridpointcode)](https://www.npmjs.com/package/@pranavpatel.ca/algo-gridpointcode)
[![PyPI](https://img.shields.io/pypi/v/gridpointcode-algo-pranavpatel-ca)](https://pypi.org/project/gridpointcode-algo-pranavpatel-ca/)

## Overview

**Grid Point Code (GPC)** is a geocoding system that gives any geographic location - a home, an office, or any other place - a compact 11-character alphanumeric code. Conversion runs offline in both directions, and a code round-trips exactly at the format's fixed precision of five decimal places of latitude and longitude.

## Features

* **Unique Global Codes** – Every location gets its own code
* **Bidirectional Conversion** – Encode and decode at a fixed precision of 5 decimal places
* **Offline Support** – Works without internet or APIs
* **Zero Dependencies** – No third-party packages in any of the four ports
* **Formatted Output** – Easy-to-read `#XXXX-XXXX-XXX` format
* **Open Source** – Licensed under Apache 2.0

## Code Structure

* **GPC Format**: `#XXXX-XXXX-XXX` (11-character alphanumeric string)
* **Encoding Base**: `CDFGHJKLMNPRTVWXY0123456789` (base-27)
* **Precision**: 5 decimal places for latitude/longitude

## Precision and Limits

* A code addresses a cell of five decimal places of latitude and longitude, roughly 1.1 m across at the equator. `decode` returns the coordinates of that cell, so a value carrying more than five decimals does not come back unchanged: encoding and then decoding is exact only to the format's fixed precision.
* Codes are not ordered by geography. Two codes that look alike may be anywhere on Earth, and two neighbouring locations may be given codes with nothing in common. Never read distance or containment out of the characters themselves; decode both codes and compare the coordinates.

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
print(gpc_code)  # Output: #FN5G-CDKL-HDC

# Decode
lat, lng = GPC.decode("#FN5G-CDKL-HDC")
print(lat, lng)

# Validate
valid, msg = GPC.is_valid_gpc("#FN5G-CDKL-HDC")
print(valid, msg)
```

---

### TypeScript

```ts
import { GPC } from '@pranavpatel.ca/algo-gridpointcode';

// Encode
const code = GPC.encode(43.65, -79.38);
console.log(code);  // #FN5G-CDKL-HDC

// Decode
const [lat, lng] = GPC.decode('#FN5G-CDKL-HDC');
console.log(lat, lng);

// Validate
const [valid, message] = GPC.isValid('#FN5G-CDKL-HDC');
console.log(valid, message);
```

---

### C\#

```csharp
using Ca.Pranavpatel.Algo.GridPointCode;

// Encode
string gpc = GPC.Encode(43.65000, -79.38000);  // Toronto
// Output: #FN5G-CDKL-HDC

// Decode
(double lat, double lng) = GPC.Decode("#FN5G-CDKL-HDC");

// Validate
(bool isValid, string message) = GPC.IsValid("#FN5G-CDKL-HDC");
```

---

### Java

```java
import ca.pranavpatel.algo.gridpointcode.GPC;
import ca.pranavpatel.algo.gridpointcode.Coordinates;
import ca.pranavpatel.algo.gridpointcode.Validation;

// Encode
String gpc = GPC.Encode(43.65, -79.38);  // Toronto
// Output: #FN5G-CDKL-HDC

// Decode
Coordinates coords = GPC.Decode("#FN5G-CDKL-HDC");
double lat = coords.Latitude;
double lng = coords.Longitude;

// Validate
Validation result = GPC.IsValid("#FN5G-CDKL-HDC");
boolean isValid = result.IsValid;
String message = result.Message;
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

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for what changed in each release.

## License

Licensed under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).

## Contributing

Pull requests, issues, and suggestions are welcome!
Please use GitHub to suggest features, report bugs, or contribute improvements.
