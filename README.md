# Grid Point Code (GPC)

[![Maven Central](https://img.shields.io/maven-central/v/ninja.pranav.algorithms/gridpointcode.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/ca.pranavpatel.algo/gridpointcode)
[![NuGet](https://img.shields.io/nuget/v/Ca.Pranavpatel.Algo.GridPointCode?label=NuGet)](https://www.nuget.org/packages/Ca.Pranavpatel.Algo.GridPointCode)

## Overview

Grid Point Code (GPC) is a global geocoding system that provides a unique, lossless, and compact alphanumeric code for any geographic location (home, office, or other places). It enables precise identification and offline conversion between geographic coordinates and codes.

## Features

- **Unique Global Identification:** Every location receives a unique code.
- **Lossless Encoding & Decoding:** Convert between latitude/longitude and GPC without loss of precision (up to 5 decimal places).
- **Offline Conversion:** No network required for encoding or decoding.
- **Easy-to-Read Format:** Codes are formatted as `#xxxx-xxxx-xxx` for clarity.
- **Proximity Awareness:** Similar codes represent nearby locations.
- **Open Source:** Freely available for use and modification.

## How It Works

- **Encoding:** Converts latitude and longitude into a unique 11-character code using a custom base-27 alphabet.
- **Decoding:** Recovers the original coordinates from a GPC.
- **Validation:** Ensures input coordinates and codes are within valid ranges.
- **Formatting:** Provides formatted and unformatted code representations.

## Usage

### Encoding Coordinates

```csharp
using Ca.Pranavpatel.Algo.GridPointCode;

// Encode latitude and longitude to GPC
string gpc = GPC.Encode(37.7749, -122.4194); // San Francisco
// Output: #XXXX-XXXX-XXX (example)
```

### Decoding a GPC

```csharp
using Ca.Pranavpatel.Algo.GridPointCode;

// Decode GPC to latitude and longitude
(double lat, double lng) = GPC.Decode("#XXXX-XXXX-XXX");
```

### Validation

```csharp
(bool isValid, string message) = GPC.IsValid("#XXXX-XXXX-XXX");
```

## Format

- **Code Structure:** `#xxxx-xxxx-xxx` (11 alphanumeric characters)
- **Alphabet:** Uses base-27 characters: `CDFGHJKLMNPRTVWXY0123456789`
- **Precision:** Up to 5 decimal places for coordinates

## License

Licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

## Contributing

Contributions are welcome! Please submit issues or pull requests via GitHub.
