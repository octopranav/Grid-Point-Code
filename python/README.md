# Grid Point Code (GPC) – Python

## Overview

Grid Point Code (GPC) is a geocoding system that provides a unique, compact, and lossless alphanumeric code for any global geographic location. This Python implementation allows offline encoding and decoding between latitude/longitude coordinates and GPCs with high precision.

## Features

* **Unique Global Codes:** Every location is mapped to a unique alphanumeric string.
* **Lossless Bi-directional Conversion:** Accurate up to 5 decimal places.
* **Offline Functionality:** No network access required.
* **Formatted Output:** Standardized format: `#XXXX-XXXX-XXX`.
* **Proximity Awareness:** Similar codes correspond to nearby locations.
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
* **Precision:** Approx. 5 decimal places in latitude/longitude

## License

Licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

## Contributing

Contributions are welcome! Feel free to open issues or submit pull requests on GitHub.
