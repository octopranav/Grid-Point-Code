# Grid Point Code (GPC) – TypeScript

## Overview

**Grid Point Code (GPC)** is a geocoding system that gives any geographic location a compact 11-character alphanumeric code. This TypeScript implementation encodes and decodes latitude/longitude coordinates offline, at the format's fixed precision of five decimal places.

## Features

* **Compact Global Codes**: Unique alphanumeric string for every lat/lng location
* **Bidirectional Conversion**: Encode and decode at a fixed precision of 5 decimal places
* **Offline Support**: No internet or API required
* **No Dependencies**: No third-party packages at runtime
* **Formatted Output**: Default format is `#XXXX-XXXX-XXX` for easy readability
* **Open Source**: Licensed under Apache License 2.0

## Installation

Add the package:

```bash
npm install @pranavpatel.ca/algo-gridpointcode
```

## Requirements

Node.js 22 or later. Compiled to ES2022 CommonJS, with type declarations
included. No runtime dependencies.

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
* **Precision**: 5 decimal places for lat/lng
* **Validation**: Coordinates and GPCs are range-checked and format-verified

## Precision and Limits

* A code addresses a cell of five decimal places of latitude and longitude, roughly 1.1 m across at the equator. `decode` returns the coordinates of that cell, so a value carrying more than five decimals does not come back unchanged: encoding and then decoding is exact only to the format's fixed precision.
* Codes are not ordered by geography. Two codes that look alike may be anywhere on Earth, and two neighbouring locations may be given codes with nothing in common. Never read distance or containment out of the characters themselves; decode both codes and compare the coordinates.

## API Reference

### `GPC.encode(latitude: number, longitude: number, formatted = true): string`

Encodes a latitude/longitude pair into a GPC string. Optional `formatted` flag adds separators.

### `GPC.decode(code: string): [number, number]`

Decodes a GPC string back into `[latitude, longitude]`.

### `GPC.isValidCoordinates(lat: number, lng: number): [boolean, string]`

Checks if latitude and longitude are within valid global ranges.

### `GPC.isValid(code: string): [boolean, string]`

Validates the GPC format and ensures it maps to a valid point.

## Changelog

See [CHANGELOG.md](https://github.com/octopranav/Grid-Point-Code/blob/main/CHANGELOG.md) for what changed in each release.

## License

Licensed under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).

## Contributing

Pull requests, issues, and suggestions are welcome!
