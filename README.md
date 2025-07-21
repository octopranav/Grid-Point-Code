# Grid Point Code (GPC)

[![Maven Central](https://img.shields.io/maven-central/v/ca.pranavpatel.algo/gridpointcode.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/ca.pranavpatel.algo/gridpointcode)
[![NuGet](https://img.shields.io/nuget/v/Ca.Pranavpatel.Algo.GridPointCode?label=NuGet)](https://www.nuget.org/packages/Ca.Pranavpatel.Algo.GridPointCode)
[![npm (scoped)](https://img.shields.io/npm/v/@pranavpatel.ca/algo-gridpointcode)](https://www.npmjs.com/package/@pranavpatel.ca/algo-gridpointcode)
[![PyPI](https://img.shields.io/pypi/v/gridpointcode-algo-pranavpatel-ca)](https://pypi.org/project/gridpointcode-algo-pranavpatel-ca/)

## Overview

**Grid Point Code (GPC)** is a geocoding system that provides a unique, lossless, and compact alphanumeric code for any geographic location - whether it is a home, office, or any other area. It enables offline conversion between geographic coordinates and standardized codes with high precision.

## Features

* **Unique Global Codes** – Every location gets a unique, proximity-aware code
* **Bidirectional Conversion** – Encode/decode with up to 5 decimal places of precision
* **Offline Support** – Works without internet or APIs
* **Formatted Output** – Easy-to-read `#XXXX-XXXX-XXX` format
* **Open Source** – Licensed under Apache 2.0

## Code Structure

* **GPC Format**: `#XXXX-XXXX-XXX` (11-character alphanumeric string)
* **Encoding Base**: `CDFGHJKLMNPRTVWXY0123456789` (base-27)
* **Precision**: \~5 decimal places for latitude/longitude

---

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
    <version>1.0</version>
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

## License

Licensed under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).

## Contributing

Pull requests, issues, and suggestions are welcome!
Please use GitHub to suggest features, report bugs, or contribute improvements.
