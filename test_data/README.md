# Conformance vectors

These files are the shared source of truth for every port. All four
implementations read the same bytes and must produce the same results, so a
divergence between languages fails a test instead of reaching a release.

Each port has two runners that read these files directly:

| Port | Runners | Command, from that port's directory |
| --- | --- | --- |
| C# | `csharp/gpc.tests/VectorsTests.cs`, `PropertiesTests.cs` | `dotnet run --project gpc.tests/gpc.tests.csproj -f net9.0` |
| Java | `java/src/test/java/ca/pranavpatel/algo/gridpointcode/VectorsTest.java`, `PropertiesTest.java` | `mvn test` |
| Python | `python/tests/test_vectors.py`, `test_properties.py` | `python -m unittest discover -s tests -t .` |
| TypeScript | `typescript/test/vectors.test.ts`, `properties.test.ts` | `npm test` |

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

## encoding.csv

```
latitude,longitude,code
```

`code` is the unformatted eleven-character form, that is `encode(lat, lng,
formatted = false)`. A port must produce exactly this string.

## decoding.csv

```
code,latitude,longitude
```

Decoding is exact: a code names one cell and resolves to that cell's corner, so
these are equality assertions rather than tolerance comparisons. Runners also
check that the formatted form `#XXXX-XXXX-XXX` of the same code decodes
identically, which covers separator stripping.

## validity_codes.csv

```
valid,message,input
```

`valid` is `true` or `false`. `message` is empty when valid, otherwise the
reason code: `GPC_NULL`, `GPC_LENGTH`, `GPC_CHAR` or `GPC_RANGE`.

**`input` is the last field on purpose.** It may contain `#`, spaces, separators
or nothing at all. Keeping it last means no data line can start with `#` and
nothing inside it can be mistaken for a column break. Split on the first two
commas only, and do not trim it — one case is whitespace only.

## validity_coordinates.csv

```
latitude,longitude,valid,message
```

`message` is empty when valid, otherwise `LATITUDE` or `LONGITUDE`. Latitude is
checked first when both are out of range.

## sample.csv

```
count,seed,digest
```

The first four files hold vectors one line at a time. This one holds a single
row describing a hundred thousand more.

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
    latitude  = (next() mod 17999999 -  8999999) / 100000
    longitude = (next() mod 35999999 - 17999999) / 100000
```

The two spans place latitude in [-89.99999, 89.99999] and longitude in
[-179.99999, 179.99999], so every point is inside the domain and no sample is
ever skipped. Both divisions are by a power-of-ten denominator that all four
languages divide correctly, giving bit-identical doubles.

`digest` is the SHA-256, lowercase hex, of the unformatted codes joined by a
single LF with no trailing newline, encoded UTF-8.

Each port checks the digest in its properties runner, which also asserts what
must be true of every code the encoder emits: the fixed length, the alphabet,
that the code validates, that decoding lands inside the cell the point came
from, and that decoding then encoding returns the code unchanged.

When a port disagrees about the digest, the failure says the ports differ but
not where. To find the line, dump the whole sample and compare directly:

```
python test_data/generate.py --dump codes.csv
```

## Numbers

Coordinates are written as the shortest decimal string that reads back as the
same double, never in exponent form. Decimal to binary conversion is correctly
rounded in all four languages, so every port parses these to bit-identical
values.

One entry is worth knowing about: `179.99999999999999` is exactly `180.0` once
stored as a double, so it appears in `validity_coordinates.csv` as a rejected
longitude rather than in `encoding.csv`.

## Regenerating

`generate.py` rebuilds all five files. Run it from anywhere:

```
python test_data/generate.py
```

Expected values come from the Python port. That is a starting point, not an
authority: the vectors are only correct once every port agrees on them, so run
all four suites before committing a regenerated corpus.

The output is deterministic. Regenerating without editing the corpus
definitions must leave the files byte for byte identical, which makes an
unexpected diff meaningful on its own: it says encoding behaviour changed.
Vectors are append-only in spirit. Changing an existing expected value means
the format changed, which is a breaking change and needs a major version, not
a quiet vector update.
