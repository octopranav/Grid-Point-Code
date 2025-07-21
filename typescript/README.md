# Grid Point Code (GPC) – TypeScript

## Overview

**Grid Point Code (GPC)** is a geocoding system that provides a unique, compact, and lossless alphanumeric code for any geographic location. This TypeScript implementation enables offline encoding and decoding of latitude/longitude coordinates into a highly precise and standardized string format.

## Features

* **Compact Global Codes**: Unique alphanumeric string for every lat/lng location
* **Bidirectional Conversion**: Encode and decode with precision up to 5 decimal places
* **Offline Support**: No internet or API required
* **Formatted Output**: Default format is `#XXXX-XXXX-XXX` for easy readability
* **Proximity-Aware**: Nearby locations produce similar codes
* **Open Source**: Licensed under Apache License 2.0

## Installation

Add the package (assumes you publish it to npm):

```bash
npm install @pranavpatel.ca/algo-gridpointcode
```

## Usage

```ts
import { GPC } from '@pranavpatel.ca/algo-gridpointcode';

// Encode latitude and longitude to GPC
const code = GPC.encode(43.65, -79.38);  // Toronto
console.log(code);  // Example: #FN5G-CDKL-HDC

// Decode a GPC back to coordinates
const [lat, lng] = GPC.decode('#FN5G-CDKL-HDC');
console.log(lat, lng);

// Validate a GPC string
const [valid, message] = GPC.isValid('#FN5G-CDKL-HDC');
console.log(valid, message);
```

## Code Structure

* **GPC Format**: `#XXXX-XXXX-XXX` (11 characters, base-27)
* **Alphabet**: `"CDFGHJKLMNPRTVWXY0123456789"` (Base-27 encoding)
* **Precision**: \~5 decimal places for lat/lng
* **Validation**: Coordinates and GPCs are range-checked and format-verified

## API Reference

### `GPC.encode(latitude: number, longitude: number, formatted = true): string`

Encodes a latitude/longitude pair into a GPC string. Optional `formatted` flag adds separators.

### `GPC.decode(code: string): [number, number]`

Decodes a GPC string back into `[latitude, longitude]`.

### `GPC.isValidCoordinates(lat: number, lng: number): [boolean, string]`

Checks if latitude and longitude are within valid global ranges.

### `GPC.isValid(code: string): [boolean, string]`

Validates the GPC format and ensures it maps to a valid point.

## License

Licensed under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).

## Contributing

Pull requests, issues, and suggestions are welcome!
