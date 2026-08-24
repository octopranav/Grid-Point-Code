# Grid Point Code (GPC) – Python

## Overview

Grid Point Code (GPC) is a geocoding system that gives any global geographic location a compact 11-character alphanumeric code. This Python implementation encodes and decodes between latitude/longitude coordinates and GPCs offline, at the format's fixed precision of five decimal places.

## Features

* **Unique Global Codes:** Every location is mapped to its own alphanumeric string.
* **Bi-directional Conversion:** Exact at a fixed precision of 5 decimal places.
* **Offline Functionality:** No network access required.
* **Formatted Output:** Standardized format: `#XXXX-XXXX-XXX`.
* **Open Source:** Available under the Apache License 2.0.

## How It Works

* **Encoding:** Converts latitude and longitude into an 11-character base-27 alphanumeric code.
* **Decoding:** Converts a GPC string back to geographic coordinates.
* **Validation:** Ensures coordinates and codes fall within valid ranges.
* **Formatting:** Adds visual separators to GPCs for readability.

## Installation

Install from PyPI:

```bash
pip install gridpointcode-algo-pranavpatel-ca
```

## Usage

### Encoding Coordinates

```python
from gridpointcode_algo_pranavpatel_ca import GPC

# Encode latitude and longitude into GPC
gpc_code = GPC.encode(43.65000, -79.38000)  # Toronto
print(gpc_code)  # Output: #FN5G-CDKL-HDC
```

### Decoding a GPC

```python
# Decode GPC into latitude and longitude
lat, lng = GPC.decode("#FN5G-CDKL-HDC")
print(lat, lng)
```

### Validating a GPC

```python
# Validate GPC format and range
valid, message = GPC.is_valid_gpc("#FN5G-CDKL-HDC")
print(valid, message)
```

## Code Format

* **GPC Structure:** `#XXXX-XXXX-XXX` (11 base-27 characters)
* **Alphabet:** `"CDFGHJKLMNPRTVWXY0123456789"` (base-27)
* **Precision:** 5 decimal places in latitude/longitude

## Precision and Limits

* A code addresses a cell of five decimal places of latitude and longitude, roughly 1.1 m across at the equator. `decode` returns the coordinates of that cell, so a value carrying more than five decimals does not come back unchanged: encoding and then decoding is exact only to the format's fixed precision.
* Codes are not ordered by geography. Two codes that look alike may be anywhere on Earth, and two neighbouring locations may be given codes with nothing in common. Never read distance or containment out of the characters themselves; decode both codes and compare the coordinates.

## License

Licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

## Contributing

Contributions are welcome! Feel free to open issues or submit pull requests on GitHub.
