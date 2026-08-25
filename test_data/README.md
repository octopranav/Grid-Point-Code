# Conformance vectors

These files are the shared source of truth for every port. All four
implementations read the same bytes and must produce the same results, so a
divergence between languages fails a test instead of reaching a release.

Each port has a runner that reads these files directly:

| Port | Runner | Command, from that port's directory |
| --- | --- | --- |
| C# | `csharp/gpc.tests/VectorsTests.cs` | `dotnet run --project gpc.tests/gpc.tests.csproj -f net9.0` |
| Java | `java/src/test/java/ca/pranavpatel/algo/gridpointcode/VectorsTest.java` | `mvn test` |
| Python | `python/tests/test_vectors.py` | `python -m pytest` |
| TypeScript | `typescript/test/vectors.test.ts` | `npm test` |

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

## Numbers

Coordinates are written as the shortest decimal string that reads back as the
same double, never in exponent form. Decimal to binary conversion is correctly
rounded in all four languages, so every port parses these to bit-identical
values.

One entry is worth knowing about: `179.99999999999999` is exactly `180.0` once
stored as a double, so it appears in `validity_coordinates.csv` as a rejected
longitude rather than in `encoding.csv`.

## Regenerating

Expected values are produced by the Python port and verified byte-identical
against the C#, Java and TypeScript ports before being committed. Vectors are
append-only in spirit: changing an existing expected value means the format
changed, which is a breaking change and needs a major version.
