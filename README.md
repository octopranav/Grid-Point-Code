# Grid Point Code (GPC)

[![CI](https://github.com/octopranav/Grid-Point-Code/actions/workflows/ci.yml/badge.svg)](https://github.com/octopranav/Grid-Point-Code/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/ca.pranavpatel.algo/gridpointcode.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/ca.pranavpatel.algo/gridpointcode)
[![NuGet](https://img.shields.io/nuget/v/Ca.Pranavpatel.Algo.GridPointCode?label=NuGet)](https://www.nuget.org/packages/Ca.Pranavpatel.Algo.GridPointCode)
[![npm (scoped)](https://img.shields.io/npm/v/@pranavpatel.ca/algo-gridpointcode)](https://www.npmjs.com/package/@pranavpatel.ca/algo-gridpointcode)
[![PyPI](https://img.shields.io/pypi/v/gridpointcode-algo-pranavpatel-ca)](https://pypi.org/project/gridpointcode-algo-pranavpatel-ca/)

**Grid Point Code** names one cell of a fixed grid over the Earth with a
ten-character code. Conversion runs offline in both directions and is a few
lines of integer arithmetic, with no lookup table and no network.

```
43.65000, -79.38000   ->   #G3RJM-98NM9
```

## Who this is for

**If you just need to tell somebody where something is**, a code is ten
characters and names any spot on Earth to about three metres. A gate in a field,
a stall in a market, the door round the back of a building, the bench you agreed
to meet at. It works where there is no street number and no street name, it
needs no app and no account, and neither end needs a signal to turn it back into
a point on a map.

**If you write software**, conversion is a few lines of integer arithmetic in
both directions. No service to call, no API key, no rate limit, no data file to
ship, and no third-party dependency in any of the four ports. What you compute
on a phone in a basement is what you compute on a server.

**If you keep locations in a database**, the guarantee below does the work an
extra index usually does. Codes sort geographically as plain strings, so
`ORDER BY code` is a spatial sort and an ordinary index is a spatial index. A
prefix is a region, so `WHERE code LIKE 'G3RJM%'` means "everything in this
8.0 by 10.7 km cell". And a code fits in six bytes if you would rather store a
number than a string.

**If codes get spoken or written down** — over a radio, down a telephone, onto a
sign or a delivery note — an optional check character catches every
single-character mistake and every swapped pair, and
[Appendix D](SPEC.md#appendix-d--sharing-a-code-non-normative) covers reading
one aloud.

**If you work on small hardware or at scale**, there is no lookup table and no
state to keep. The same ten-step loop fits in a microcontroller, a database
function, or a batch over a billion rows.

### What you would reach for

| You want to | Use |
| --- | --- |
| Share one exact spot | The full code, `#G3RJM-98NM9` |
| Say it aloud, or have it written down | The check form, `#G3RJM-98NM9*T` |
| Group points by area | The first k characters — `cell` |
| Ask whether a point is in an area | `contains`, which is a string comparison |
| Find what is next door | `neighbours` |
| Store it small, or sort it | The 48-bit integer form |
| Repair a code somebody mistyped | `suggest_corrections` |
| Warn before a code goes on a sign | `screen` |

Two things worth knowing before you start. A code names a **cell, not a point**,
so it comes back as the centre of a 2.56 by 3.42 m box rather than the exact
coordinate you put in. And a shared prefix proves nearness, but nearness does
not promise a shared prefix — two points either side of a grid boundary can
share nothing at all. Both are covered under
[Precision and limits](#precision-and-limits).

## The guarantee

> Two codes agree in their first **k** characters **if and only if** the two
> points lie in the same level-**k** cell.

Both directions, for every pair of points on Earth, without exception. That is
containment rather than correlation, and it is a theorem about how the code is
built rather than a tendency measured over samples: it is proved in
[section 10 of the specification](SPEC.md#10-the-locality-guarantee) and
re-checked over 1,200,000 pairs on every push.

Everything below follows from that one property.

| Because a shared prefix **is** a shared cell | |
| --- | --- |
| A prefix is a region identifier | ten nested scales, continent down to doorway, with nothing to mint and nothing extra to store |
| The prefix test **is** the containment test | `contains` is a string comparison — no geometry, no tolerance, no special case at a boundary |
| The alphabet is ASCII-ascending | `ORDER BY code` is a spatial sort, and an ordinary string index is a spatial index |
| Cells nest exactly | neighbours, cell sizes, the short form and typo correction are all integer arithmetic on the grid |

### What a prefix is worth

| Shared characters | Cell, north-south | Cell, east-west | Scale |
| ---: | ---: | ---: | --- |
| 1 | 5,000.9 km | 6,679.2 km | Continent |
| 3 | 200.0 km | 267.2 km | Region |
| 5 | 8.0 km | 10.7 km | District |
| 7 | 320.1 m | 427.5 m | Street |
| 10 | 2.6 m | 3.4 m | Doorway |

[Section 3](SPEC.md#3-the-grid) has all ten levels.

## Code structure

* **Format**: `#XXXXX-XXXXX`, ten characters, the same length everywhere
* **Alphabet**: `0123456789CDFGHJKLMNPRTWX` — 25 symbols, no vowels, digits
  first so that the ASCII order is the spatial order
* **Cell**: 2.56 m north to south by 3.42 m east to west at the equator
* **No dependencies**: nothing third-party in any of the four ports
* **Reads version 1 codes**: every code ever issued still resolves

## Precision and limits

* **A code names a cell, not a point.** `decode` returns the centre of that
  cell, so a coordinate carrying more precision than the 2.56 m cell does not
  come back unchanged. Encoding what you decoded always returns the same code.
* **A shared prefix proves proximity; proximity does not promise a shared
  prefix.** Level-1 boundaries lie on the equator, on 45 degrees north and
  south, and on every 60th meridian, and two points a few metres apart across
  one of those seams share nothing at all. Of random pairs 100 m apart, 91.45 %
  share at least six characters and 0.36 % share fewer than four.
  [Section 16](SPEC.md#16-seams) maps the seams and works through the examples.
* **Nearly 29 % of single-character typos** produce a location in the right
  region and the wrong place. Show the decoded point on a map, or check it
  against something the reader recognises, before acting on it. No amount of
  format design removes that; confirmation does.

## Which form to share

| Where it is going | Share | Why |
| --- | --- | --- |
| A share button, a message, a record, a label | `#G3RJM-98NM9` | complete on its own |
| Voice, radio, paper, anything dictated | `#G3RJM-98NM9*T` | one character buys detection |
| A sign in one place, two people standing in it | `98NM9` | only resolves near the point |
| A database key, a QR or NFC payload | six bytes | not for a person to read |

The ten characters are the form of record, and they are what a share button
emits. `with_check` returns the check form in one call; its one extra character
detects every single-character error and every adjacent transposition, which
are the two mistakes a person makes, so it earns its place wherever a code will
be read aloud or written down.

The short form is the one to be careful with. Five characters resolve only
against a reference near the true point — right for a sign at the entrance to a
village, wrong behind a share button, which cannot know where the far end will
be standing. Out of range it does not fail; it returns a plausible location 8
or 10 km away.

[Appendix D](SPEC.md#appendix-d--sharing-a-code-non-normative) covers the rest,
including how to read a code out loud given that `C`, `D`, `G`, `P`, `T` and
the digit `3` all rhyme.

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
    <version>2.0.0</version>
</dependency>
```

---

## Usage Examples

### Python

```python
from gridpointcode_algo_pranavpatel_ca import GPC

# Encode
gpc_code = GPC.encode(43.65000, -79.38000)
print(gpc_code)  # Output: #G3RJM-98NM9

# Decode
lat, lng = GPC.decode("#G3RJM-98NM9")
print(lat, lng)  # 43.650006 -79.380004

# Validate and classify
print(GPC.is_valid("#G3RJM-98NM9"))   # True
print(GPC.classify("XG3RJ98NM9"))     # RESERVED

# Version 1 codes still decode
print(GPC.decode("#FN5G-CDKL-HDC"))   # (43.65, -79.38)
```

---

### TypeScript

```ts
import { GPC } from '@pranavpatel.ca/algo-gridpointcode';

// Encode
const code = GPC.encode(43.65, -79.38);
console.log(code);  // #G3RJM-98NM9

// Decode
const [lat, lng] = GPC.decode('#G3RJM-98NM9');
console.log(lat, lng);  // 43.650006 -79.380004

// Validate and classify
console.log(GPC.isValid('#G3RJM-98NM9'));  // true
console.log(GPC.classify('XG3RJ98NM9'));   // 'RESERVED'

// Version 1 codes still decode
console.log(GPC.decode('#FN5G-CDKL-HDC'));  // [43.65, -79.38]
```

---

### C\#

```csharp
using Ca.Pranavpatel.Algo.GridPointCode;

// Encode
string gpc = GPC.Encode(43.65000, -79.38000);  // Toronto
// Output: #G3RJM-98NM9

// Decode
(double lat, double lng) = GPC.Decode("#G3RJM-98NM9");

// Validate and classify
bool isValid = GPC.IsValid("#G3RJM-98NM9");
CodeClass kind = GPC.Classify("XG3RJ98NM9");  // CodeClass.Reserved

// Version 1 codes still decode
(double v1Lat, double v1Lng) = GPC.Decode("#FN5G-CDKL-HDC");
```

---

### Java

```java
import ca.pranavpatel.algo.gridpointcode.CodeClass;
import ca.pranavpatel.algo.gridpointcode.Coordinates;
import ca.pranavpatel.algo.gridpointcode.GPC;

// Encode
String gpc = GPC.Encode(43.65, -79.38);  // Toronto
// Output: #G3RJM-98NM9

// Decode
Coordinates coords = GPC.Decode("#G3RJM-98NM9");
double lat = coords.Latitude;   // 43.650006
double lng = coords.Longitude;  // -79.380004

// Validate and classify
boolean isValid = GPC.IsValid("#G3RJM-98NM9");
CodeClass kind = GPC.Classify("XG3RJ98NM9");  // CodeClass.RESERVED

// Version 1 codes still decode
Coordinates old = GPC.Decode("#FN5G-CDKL-HDC");
```

---

## The Locality API

The guarantee is only useful if a caller can act on it, so each port carries the
operations that follow from it rather than leaving everyone to re-derive the
arithmetic. Every one of them is exact integer arithmetic except `distance`.

| What it does | Python | TypeScript | C# and Java |
| --- | --- | --- | --- |
| The region the first k characters name | `cell` | `cell` | `Cell` |
| Whether a code lies in a cell | `contains` | `contains` | `Contains` |
| The eight cells around one | `neighbours` | `neighbours` | `Neighbours` |
| How big a cell is at a level | `cell_dimensions` | `cellDimensions` | `CellDimensions` |
| Great-circle metres between two cells | `distance` | `distance` | `Distance` |
| The grid row and column | `decode_to_grid` | `decodeToGrid` | `DecodeToGrid` |
| The last five characters | `shorten` | `shorten` | `Shorten` |
| The full code, from those five and a reference | `recover_short` | `recoverShort` | `RecoverShort` |
| Codes one typo away, best first | `suggest_corrections` | `suggestCorrections` | `SuggestCorrections` |
| The code in its check form | `with_check` | `withCheck` | `WithCheck` |
| The 48-bit integer form | `to_integer`, `from_integer` | `toInteger`, `fromInteger` | `ToInteger`, `FromInteger` |
| An RFC 5870 `geo:` URI | `to_geo_uri`, `from_geo_uri` | `toGeoURI`, `fromGeoURI` | `ToGeoURI`, `FromGeoURI` |
| Degrees, minutes and seconds | `to_dms`, `from_dms` | `toDMS`, `fromDMS` | `ToDMS`, `FromDMS` |
| Advisory word screening | `screen` | `screen` | `Screen` |
| Batch and streaming conversion | `encode_all`, `encode_stream` | `encodeAll`, `encodeStream` | `EncodeAll`, `EncodeStream` |

```python
GPC.cell("#G3RJM-98NM9", 5)                    # 'G3RJM', the 8.0 by 10.7 km cell
GPC.contains("G3RJM", "G3RJM98NM9")            # True -- the prefix test, exactly
GPC.neighbours("G3RJM")                        # the eight cells around it

GPC.shorten("#G3RJM-98NM9")                    # '98NM9', the second printed group
GPC.recover_short("-98NM9", 43.66, -79.39)     # '#G3RJM-98NM9', near a reference

GPC.suggest_corrections("#G3RJT-98NM9", 43.65, -79.38)
# ['#G3RJM-98NM9'] -- the geography does the work a check digit would
```

A few things worth knowing before reaching for these:

* **`contains` is the prefix test and nothing else.** There is no tolerance and
  no edge case at a boundary, because the guarantee makes the string answer and
  the geometric answer the same answer.
* **Columns wrap at the antimeridian and rows do not**, so a cell in the top or
  bottom row has five neighbours rather than eight. The three that would lie off
  the grid are absent from the result rather than present and empty.
* **`distance` is the one operation that is not bit-identical across the four
  ports.** No standard library rounds sine, cosine or arc sine correctly, so the
  ports agree to about a millimetre rather than exactly, and its conformance
  vectors are the only ones in the corpus asserted to a tolerance. Anything that
  needs a reproducible ordering should rank on the grid indices instead.
* **The short form is a convenience and the ten characters are the form of
  record.** Recovery is exact whenever the reference is within half a level-5
  cell of the true point on each axis -- 4.0 km of latitude, 5.3 km of longitude
  at the equator -- and outside that box it returns a plausible location 8 or
  10 km away.
* **`suggest_corrections` corrects, it does not detect.** It is not a checksum,
  and the advice above about confirming on a map applies to its output as much
  as to anything else.
* **`screen` advises and never blocks.** Nothing in any port refuses to encode,
  decode or validate because of what it found, and it reports the version of the
  list it used whether or not anything matched.

The optional check character is the one mechanism here that detects rather than
corrects, and it is never present unless someone asks for it:
`with_check("#G3RJM-98NM9")` returns `#G3RJM-98NM9*T`.

---

## Version 1 and version 2

|  | Version 1 | Version 2 |
| --- | --- | --- |
| Characters | eleven | ten |
| Written | `#FN5G-CDKL-HDC` | `#G3RJM-98NM9` |
| Groups | three, two dashes | two, one dash |
| A shared prefix means | nothing | the same cell, always |

That is the whole test: count the characters. `decode` does exactly the same
thing — it strips the separators and dispatches on length — so a version 1 code
reads correctly under 2.0.0 without anyone asking, and `decode_v1` is there for
a caller that wants to be explicit. Because the dispatch is on length alone, an
eleven-character string that happens to be a valid version 1 code decodes as
one.

Every code ever issued still resolves, and always will. A geocode is not an
API: codes end up on signs, on labels and in records, and
[Appendix B](SPEC.md#appendix-b--decoding-version-1-optional) of the
specification carries everything a port needs to keep reading them.

**There is no version 1 encoder in 2.0.0.** The old format is readable, not
writable, so nobody mints a version 1 code by accident. Anyone who still needs
to write them should pin `1.1.x`, which stays published on all four registries.

There is no migration for a stored code. Version 1 codes stay valid and stay
readable, and new codes are version 2. The same coordinates encode to a
different, shorter code under version 2, so the two are not interchangeable as
strings.

---

## Testing

Every port is built and tested on every push. Each suite reads the same
conformance vectors from [`test_data/`](test_data/), and each one reproduces the
digest of a shared hundred-thousand-point sample, so the four implementations
are held to byte-identical output rather than to four separate sets of
expectations. [`conformance/`](conformance/) comes at the same question from the
other side: one set of awkward inputs is put through all four ports and the
answers are diffed against each other, which catches a divergence on a case
nobody thought to write a vector for.

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

## Specification

[SPEC.md](SPEC.md) is the normative specification for **version 2** of the
format. It defines the format precisely enough to implement from without
reading any source, and it carries the measurements behind every claim it
makes. [`reference/`](reference/) is its executable companion: it checks every
exact claim the document makes and reproduces every measured figure in it, and
it runs in CI, so the document and the code cannot drift apart quietly.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for what changed in each release.

## License

Licensed under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).

## Contributing

Pull requests, issues, and suggestions are welcome!
Please use GitHub to suggest features, report bugs, or contribute improvements.
