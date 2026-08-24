# Grid Point Code (GPC)

## Overview

Grid Point Code (GPC) is a global geocoding system that gives any geographic location (home, office, or other places) a compact 11-character alphanumeric code. Conversion runs offline in both directions, at the format's fixed precision of five decimal places.

## Features

- **Unique Global Identification:** Every location receives its own code.
- **Encoding & Decoding:** Convert between latitude/longitude and GPC at a fixed precision of 5 decimal places.
- **Offline Conversion:** No network required for encoding or decoding.
- **Easy-to-Read Format:** Codes are formatted as `#xxxx-xxxx-xxx` for clarity.
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
- **Precision:** 5 decimal places for coordinates

## Precision and Limits

* A code addresses a cell of five decimal places of latitude and longitude, roughly 1.1 m across at the equator. `decode` returns the coordinates of that cell, so a value carrying more than five decimals does not come back unchanged: encoding and then decoding is exact only to the format's fixed precision.
* Codes are not ordered by geography. Two codes that look alike may be anywhere on Earth, and two neighbouring locations may be given codes with nothing in common. Never read distance or containment out of the characters themselves; decode both codes and compare the coordinates.

## License

Licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

## Contributing

Contributions are welcome! Please submit issues or pull requests via GitHub.

