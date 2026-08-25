# Conformance vectors

These files are the shared source of truth for every port. All four
implementations read the same bytes and must produce the same results, so a
divergence between languages fails a test instead of reaching a release.

The `v2_*.csv` files hold version 2 and are what the four ports are held to.
The rest are version 1, which is frozen: the ports still decode it, so they are
asserted from the decoding side, and the two files that describe the version 1
*encoder* are kept as a record rather than a port assertion, because no
published package encodes version 1 any more.
[Section 18 of SPEC.md](../SPEC.md#18-conformance) specifies the version 2
fields.

Each port has three runners that read these files directly:

| Port | Runners | Command, from that port's directory |
| --- | --- | --- |
| C# | `csharp/gpc.tests/VectorsTests.cs`, `PropertiesTests.cs`, `TestGPC.cs` | `dotnet run --project gpc.tests/gpc.tests.csproj -f net9.0` |
| Java | `java/src/test/java/ca/pranavpatel/algo/gridpointcode/VectorsTest.java`, `PropertiesTest.java`, `GPCTest.java` | `mvn test` |
| Python | `python/tests/test_vectors.py`, `test_properties.py`, `test_gpc.py`, `test_v1.py` | `python -m unittest discover -s tests -t .` |
| TypeScript | `typescript/test/vectors.test.ts`, `properties.test.ts`, `GPS.test.ts` | `npm test` |

The C# suite is run with `dotnet run`, not `dotnet test`; see
`csharp/README.md` for why.

## File format

Plain UTF-8, LF line endings, no quoting and no escaping.

* Lines beginning with `#` are comments. They also carry the section headings
  that group the vectors by what they exercise.
* Blank lines are ignored.
* Every other line is one vector, with fields separated by commas.

Line endings are pinned to LF in `.gitattributes` so the files are identical on
every platform. Runners strip a trailing carriage return anyway.

## Version 2

### v2_encoding.csv

```
latitude,longitude,code
```

`code` is the unformatted ten-character form, that is `encode(lat, lng,
formatted = false)`. A port must produce exactly this string.

The corpus covers what the format has to answer for by construction: both
poles, both ends of the antimeridian, negative zero in each axis independently,
one unit in the last place either side of every level-1 boundary, pairs metres
apart across a seam, and the value `179.99999999999999`, which is exactly
`180.0` once stored as a double and so appears as the row `0.0,180.0`.

### v2_decoding.csv

```
code,latitude,longitude
```

Decoding is exact: a code names one cell and resolves to that cell's centre,
rounded to six decimal places, so these are equality assertions rather than
tolerance comparisons. Runners also check that the formatted form
`#XXXXX-XXXXX` of the same code decodes identically, which covers separator
stripping.

The last section is the alias table of specification section 8, asserted
through the decoded value rather than through classification: a code spelled
with a confusable letter has to reach the same cell as the code spelled with
the symbol it stands for. Classifying it is not enough — a port that aliases
`V` to the wrong symbol still produces a well-formed code, just not this one.

### v2_area.csv

```
code,south,west,north,east
```

The boundaries of the cell, as `decodeToArea` returns them. A box is a closed
region, so the north edge of the top row is `+90` and the east edge of the last
column is `+180`, even though neither value encodes to that cell.

### v2_classify.csv

```
class,message,input
```

`class` is `GEOMETRIC`, `RESERVED` or `INVALID`. `message` is the reason code,
empty unless the class is `INVALID`: `GPC_NULL`, `GPC_LENGTH`, `GPC_CHAR` or
`GPC_CHECK`.

**`input` is the last field on purpose.** It may contain `#`, `*`, spaces,
separators or nothing at all. Keeping it last means no data line can start with
`#` and nothing inside it can be mistaken for a column break. Split on the
first two commas only, and do not trim it — two cases are whitespace only.

### v2_check.csv

```
code,check
```

The optional GF(25) check character of specification section 14, written after
a star: `#G3RJM-98NM9*T`. It is not canonical and is never emitted unless asked
for.

### v2_sample.csv

```
count,seed,digest
```

The other files hold vectors one line at a time. This one holds a single row
describing a hundred thousand more, and it is the assertion that fails when two
ports stop agreeing.

A corpus that size is not worth committing, but the codes still have to agree
across the ports, so the sample is defined by arithmetic rather than stored.
Every port walks the same generator, encodes every point it produces, and
hashes the result. Only the digest is committed, and a port that reproduces it
agrees with the other three byte for byte.

The generator is a linear congruential sequence. Its products stay below
2<sup>53</sup>, so it is exact in every language, including the ones whose only
number is a double:

```
state = seed
next():
    state = (1664525 * state + 1013904223) mod 4294967296
    return state

for each of count points:
    latitude  = (next() mod 18000001 -  9000000) / 100000
    longitude = (next() mod 36000001 - 18000000) / 100000
```

The two spans are inclusive at both ends, placing latitude in [-90, 90] and
longitude in [-180, 180], so the sample exercises the poles, the clamp and the
`+180` normalisation rather than stopping short of them. Both divisions are by
a power-of-ten denominator that all four languages divide correctly, giving
bit-identical doubles.

`digest` is the SHA-256, lowercase hex, of the unformatted codes joined by a
single LF with no trailing newline, encoded UTF-8.

Each port checks the digest in its properties runner, which also asserts what
must be true of every code the encoder emits: the fixed length, the alphabet,
that no code reaches the reserved namespace, that the code validates, that
decoding lands inside its own cell, and that decoding then encoding returns the
code unchanged. The same runner pins the two properties the format exists for —
containment, that two codes agreeing in *k* characters lie in one level-*k*
cell and the reverse; and continuity, that consecutive codes are adjacent cells
everywhere except at a level-5 boundary.

When a port disagrees about the digest, the failure says the ports differ but
not where. To find the line, dump the whole sample and compare directly:

```
python test_data/generate.py --dump codes.csv
```

## Version 1

Version 1 is frozen and is decode-only from 2.0.0 onward. `decode` dispatches
on length once separators are stripped — ten characters is version 2, eleven is
version 1 — and every port also exposes an explicit `decodeV1` entry point.

### decoding.csv

```
code,latitude,longitude
```

Every code names one cell and decodes to that cell's **corner**, which is where
version 1 differs from version 2 by design. Equality assertions. Runners also
check the formatted `#XXXX-XXXX-XXX` form.

### encoding.csv

```
latitude,longitude,code
```

Built by the version 1 encoder, which no longer ships in any package. What
survives is the containment: the code names the cell the coordinate falls in,
so a port asserts these rows by decoding and checking that the result lands
within one cell — a hundred-thousandth of a degree on each axis.

### validity_codes.csv

```
valid,message,input
```

`valid` is `true` or `false`. `message` is empty when valid, otherwise the
reason code: `GPC_NULL`, `GPC_LENGTH`, `GPC_CHAR` or `GPC_RANGE`. A port
asserts these with `isValidV1`.

**`input` is the last field on purpose**, for the reason given under
`v2_classify.csv` above.

### validity_coordinates.csv

```
latitude,longitude,valid,message
```

A record rather than a port assertion: this is the domain of an *encoder* no
package carries any more. The poles and the antimeridian were outside it.
Version 2 accepts all of them, and `v2_encoding.csv` holds the same coordinates
with codes beside them.

### sample.csv

```
count,seed,digest
```

Also a record rather than a port assertion, for the same reason: reproducing it
needs the version 1 encoder. Its spans are narrower than the version 2 ones —
17999999 and 35999999, stopping one cell short of the poles and the
antimeridian, which version 1 rejected.

## Numbers

Coordinates are written as the shortest decimal string that reads back as the
same double, never in exponent form. Decimal to binary conversion is correctly
rounded in all four languages, so every port parses these to bit-identical
values.

## Regenerating

`generate.py` rebuilds all eleven files. Run it from anywhere:

```
python test_data/generate.py
```

Expected values come from the Python port. That is a starting point, not an
authority: the vectors are only correct once every port agrees on them, so run
all four suites before committing a regenerated corpus.

The version 1 files are rebuilt from `v1_encoder.py`, which is the only version
1 encoder left anywhere in the repository. It is not part of any package, is
not published, and is not something to translate into one — the decoder the
ports carry is described in Appendix B of `SPEC.md`.

The output is deterministic. Regenerating without editing the corpus
definitions must leave the files byte for byte identical, which makes an
unexpected diff meaningful on its own: it says encoding behaviour changed.
Vectors are append-only in spirit. Changing an existing expected value means
the format changed, which is a breaking change and needs a major version, not
a quiet vector update.
