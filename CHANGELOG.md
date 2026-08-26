# Changelog

## Unreleased

Version 2 of the format, which will be released as 2.0.0 on the four existing
package names. Nothing is published yet; the registries still carry 1.1.0.

A code is now ten characters rather than eleven, and every character is a
refinement of the ones before it, so two codes that begin with the same k
characters name points in the same level-k cell. That is containment, not
correlation: it holds for every pair of points without exception. Version 1
had no such property, and could give two points nineteen thousand kilometres
apart four characters in common.

The format is specified in [SPEC.md](SPEC.md), precisely enough to implement
from without reading any source.

### Added

* **Version 2 encoding and decoding**, in all four ports. `encode` emits
  version 2 only. `decode` returns the centre of the cell a code names, to six
  decimal places, and `decodeToArea` returns the cell's boundaries.
* **The poles and the antimeridian encode.** Version 1 rejected latitude ±90
  and longitude ±180. Version 2 accepts the whole closed domain, and both ends
  of the antimeridian give the one code.
* **`classify`**, returning `GEOMETRIC`, `RESERVED` or `INVALID`. No encoded
  code can begin with `X`, so that space is reserved rather than wasted. A
  reserved code is well formed and names no cell; it is not a typing error, and
  decoding one raises a `GPC_RESERVED` reason distinct from every invalid one.
* **An optional check character** for voice, radio and paper, written after a
  star: `#G3RJM-98NM9*T`. A linear check over GF(25) that detects every
  single-character error and every adjacent transposition. It is not canonical
  and is never emitted unless asked for.
* **An alias table** for confusable input: `O` reads as `0`, `I` as `1`, `S` as
  `5`, `Z` as `2`, `B` as `8`, `A` as `4`, `E` as `3` and `V` as `W`. `L` is a
  real symbol and is never read as `1`. `U`, `Q` and `Y` are rejected.
* **A spatial API on top of the guarantee.** A shared prefix means a shared
  cell, and these are the operations that follow from that: `cell` takes the
  first k characters as a region identifier, `contains` is the prefix test,
  `neighbours` returns the eight cells around one, `cellDimensions` says how big
  a cell is at each level, `distance` gives great-circle metres between two cell
  centres, and `decodeToGrid` hands back the raw row and column for a caller
  building its own spatial structure.

  Columns wrap at the antimeridian and rows do not, so a cell in a polar row has
  five neighbours rather than eight. `distance` is the one operation in the
  format that is not bit-identical across the four ports — no standard library
  rounds sine, cosine or arc sine correctly — and the ports agree to within a
  millimetre rather than exactly.
* **The short form.** `shorten` returns the last five characters of a code,
  which is literally the second printed group, and `recoverShort` turns those
  five back into a full code given a nearby reference. Exact whenever the
  reference is within half a level-5 cell of the true point on each axis: 4.0 km
  of latitude, and 5.3 km of longitude at the equator. Outside that box it
  returns a plausible location 8 or 10 km away, so the full ten characters
  remain the form of record.
* **Typo correction.** `suggestCorrections(code, nearLatitude, nearLongitude)`
  returns the codes one typo away that are plausible near a reference, best
  first. The structure that hides an error also locates it: at the default
  level, the true code is usually the only candidate. This corrects rather than
  detects and is not a checksum — the advice to confirm a decoded point on a map
  applies to its output as much as to anything else.
* **The 48-bit integer form.** `toInteger` and `fromInteger` convert both ways.
  Six bytes big-endian, order-preserving, so a binary key sorts spatially the
  way the string does, and a single comparison separates geometric codes from
  reserved ones without parsing.
* **Coordinate conversions.** `toGeoURI` and `fromGeoURI` for RFC 5870 `geo:`
  URIs, which carry all six decimal places and round-trip a code exactly, and
  `toDMS` and `fromDMS` for degrees, minutes and seconds, which are for a person
  to read and are rounded to a hundredth of a second.
* **Advisory screening.** `screen(code)` reports substrings that spell something
  unwanted, as spans, alongside the version of the list it used. It advises and
  never blocks: nothing refuses to encode, decode or validate because of what it
  found. The list is stored as hashes and the words themselves are not in the
  repository.
* **Batch and streaming conversion.** `encodeAll` and `decodeAll` for dataset
  work, and lazy `encodeStream` and `decodeStream` beside them for callers that
  want to handle a bad row without losing the rest.
* **A typed error carrying a reason code** in every port, alongside the
  existing exception types, so a caller can branch on the reason rather than on
  message text. `GPC_LEVEL`, `GPC_DMS` and `GPC_GEO` join the reasons the
  locality API can raise; none of them ever comes back from `validate`. In the
  C# port a level outside 1 to 10 is an `ArgumentOutOfRangeException`, as a
  coordinate outside the domain already was.

### Changed

* **Codes are ten characters**, written `#XXXXX-XXXXX`, over a 25-symbol
  alphabet with the digits first: `0123456789CDFGHJKLMNPRTWX`. Because that
  alphabet is ASCII-ascending, sorting codes as plain strings sorts them
  geographically.
* **`decode` returns the centre of a cell**, where version 1 returned the
  corner. The cell is 2.56 m north to south by 3.42 m east to west at the
  equator, against version 1's 1.1 m square, and encoding what you decoded
  always returns the same code.
* **`isValid` answers about version 2** and returns a plain boolean. The reason
  is available from `validate`, which returns the class alongside it.

### Removed

* **The version 1 encoder.** The old format retires: it is readable, not
  writable, and nobody can mint a version 1 code by accident. Anyone who needs
  to write them should pin `1.1.x`, which stays published.

### Upgrading

Stored version 1 codes still decode. `decode` dispatches on length once
separators are stripped — ten characters is version 2, eleven is version 1 —
and every port also exposes an explicit `decodeV1`. Note that the dispatch is
on length alone, so an eleven-character string that happens to be a valid
version 1 code decodes as one.

Coordinates that encoded under version 1 encode under version 2 to a different,
shorter code. There is no migration for a stored code: version 1 codes stay
valid and stay readable, and new codes are version 2.

## 1.1.0

A repair release. Codes are unchanged for ordinary coordinates, and every code
issued by 1.0 still decodes. The fixes below change the result only for inputs
that previously produced a wrong or port-dependent answer.

### Fixed

* **Coordinates within rounding distance of a whole degree no longer corrupt
  silently.** `encode(89.9999999999999, 0)` pushed an out-of-domain index into
  the lookup table, which was accepted without error and decoded to
  `(0.0, -90.0)`. It now returns `#D4GP-770H-J19`, which decodes to
  `(89.99999, 0.0)`. The same fault affected longitudes near 180 degrees.
* **All four ports now produce identical codes for identical input.** Python,
  TypeScript and C# each converted a double to decimal differently, so one
  coordinate could yield three different codes. Every port now takes the five
  decimal digits from the shortest decimal string that reads back as the given
  double, which is what the Java port already did. Codes from the Java port are
  unchanged.
* **Codes below the valid floor are rejected rather than accepted and then
  crashed on.** `isValid("CCCC-CCCC-CCC")` reported valid in the Python and
  TypeScript ports and decoding it raised. It now reports `GPC_RANGE`.
* **Negative zero yields one code.** In the Python port `encode(-0.0, -0.0)`
  produced a second, different code for the same point as `encode(0.0, 0.0)`.
* **Java `IsValid(String)` accepts formatted codes.** It did not strip the `#`
  and `-` separators before checking length, so `IsValid("#FN5G-CDKL-HDC")`
  returned `GPC_LENGTH`. It now also returns `GPC_NULL` for null input instead
  of throwing.

### Changed

* **No external dependencies.** The combination table is vendored into all four
  ports and removed from every manifest.
* **The C# library targets net9.0 and net10.0.** Consumers on net9.0 are
  unaffected.
* Documentation no longer claims proximity awareness, which was measurably
  untrue: two codes sharing four characters can be 19,874 km apart. "Lossless"
  is qualified as round-tripping at the format's fixed precision of five decimal
  places.
* Python package metadata declares `requires-python >= 3.9`, which the type
  annotations have always required, and a valid licence classifier.
* Build and test dependencies are current across all four ports, closing every
  reported advisory.

### Upgrading

Stored codes need no migration. If you generate codes from coordinates carrying
more than five decimal places, a small number of them may now encode
differently. In every such case the 1.0 result was either wrong or differed
between ports.

## 1.0

First release.
