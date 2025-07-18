# Grid Point Code (GPC)

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

```java
import ca.pranavpatel.algo.gridpointcode.GPC;

// Encode latitude and longitude to GPC
String gpc = GPC.Encode(37.7749, -122.4194); // San Francisco
// Output: #XXXX-XXXX-XXX (example)
```

### Decoding a GPC

```java
import ca.pranavpatel.algo.gridpointcode.GPC;
import ca.pranavpatel.algo.gridpointcode.Coordinates;

// Decode GPC to latitude and longitude
Coordinates coords = GPC.Decode("#XXXX-XXXX-XXX");
double lat = coords.Latitude;
double lng = coords.Longitude;
```

### Validation

```java
import ca.pranavpatel.algo.gridpointcode.GPC;
import ca.pranavpatel.algo.gridpointcode.Validation;

Validation result = GPC.IsValid("#XXXX-XXXX-XXX");
boolean isValid = result.IsValid;
String message = result.Message;
```

## Format

- **Code Structure:** `#xxxx-xxxx-xxx` (11 alphanumeric characters)
- **Alphabet:** Uses base-27 characters: `CDFGHJKLMNPRTVWXY0123456789`
- **Precision:** Up to 5 decimal places for coordinates

## License

Licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

## Contributing

Contributions are welcome! Please submit issues or pull requests via GitHub.

