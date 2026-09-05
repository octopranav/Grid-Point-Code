# Grid Point Code in PostgreSQL

```
psql -d yourdb -f sql/gpc.sql
```

Plain SQL and PL/pgSQL. No extension to compile, nothing to install on the
server, no superuser. It is a file of functions, so it works on a managed
instance where `CREATE EXTENSION` does not.

## Why put it in the database

Because the ordering property turns an ordinary B-tree into a spatial index.
The alphabet ascends in ASCII and the earlier characters are the more
significant, so comparing two codes as bytes gives the same answer as comparing
their positions along the traversal of the grid. A cell is a prefix, and a
prefix on a B-tree is a range scan.

```sql
CREATE TABLE places (
    name text,
    code text COLLATE "C"        -- see below; this matters
);
CREATE INDEX ON places (code);

-- everything inside a cell
SELECT * FROM places WHERE code LIKE 'G3RJM%';

-- the same question, said out loud
SELECT * FROM places WHERE gpc_contains('G3RJM', code);

-- and in spatial order, with no decoding and no custom collation
SELECT * FROM places ORDER BY code;
```

No GiST index, no geometry column, no second column to keep in step, and no
PostGIS. On 200,000 rows the first query plans as an *Index Only Scan* and the
last needs no sort at all. Both are checked in CI rather than claimed here.

How well the order holds up, measured over those 200,000 codes:

| | median distance on the grid |
| --- | ---: |
| rows next to each other in `ORDER BY code` | 5,580 cells |
| rows 500 apart in that order | 739,234 cells |

## The collation, which is the one thing that will catch you

**Declare the column `COLLATE "C"`.** The ordering property is a property of
*bytes*, and most databases are created with a collation that does not sort
bytes: `en_US.UTF-8` sorts the way a person alphabetises.

What that costs was measured rather than guessed, over 200,000 codes in a column
collated `und-x-icu`:

| | under a human-language collation |
| --- | --- |
| `WHERE code LIKE 'G3RJM%'` | **sequential scan**, the index is not used |
| `WHERE code >= 'G3RJM' AND code < 'G3RJN'` | index scan, unaffected |
| `ORDER BY code` | identical to byte order at all 200,000 positions |

So the damage is narrower than it first appears, and in one direction than
expected. The *order* survives: this alphabet is digits and consonants with no
vowels and no lower case, and a human-language collation happens to sort those
exactly as bytes do. What does not survive is the prefix query, which is the
common case and the reason to reach for a code in the first place.

`COLLATE "C"` on the column fixes it and costs nothing, because there is no
human-language ordering here to lose. An index built with `text_pattern_ops`
fixes it too. If you can change neither, write the bounded range instead of the
`LIKE`; it compares whole values rather than asking for a prefix, and the
planner is happy with it under any collation.

None of the three rows above is asserted here. `sql/check_index.sql` builds the
ICU-collated column and asks the planner, so if a later PostgreSQL changes any
of this, the check says so rather than this page going quietly stale.

## What is here

| function | what it does |
| --- | --- |
| `gpc_encode(latitude, longitude [, formatted])` | a coordinate to a code |
| `gpc_decode(code)` | a code to the centre of its cell, as `(latitude, longitude)` |
| `gpc_decode_area(code)` | the cell as `(south, west, north, east)` |
| `gpc_grid_to_code(row, col)` / `gpc_code_to_grid(code)` | the integer core, both ways |
| `gpc_normalise(code)` | case, separators and the alias table; returns `[payload, check]` |
| `gpc_classify(code)` | `[GEOMETRIC|RESERVED|INVALID, reason]` |
| `gpc_is_valid(code)` | the same thing as a boolean |
| `gpc_cell(code, level)` | the first `level` characters: the containing cell |
| `gpc_contains(cell, code)` | whether a code is in a cell, as a prefix test |
| `gpc_check_character(payload)` | the optional GF(25) check character |
| `gpc_format(payload)` | `#XXXXX-XXXXX` |

Every one is `IMMUTABLE` and `PARALLEL SAFE`, which is what lets the planner use
them in an index expression and inside a parallel scan:

```sql
CREATE INDEX ON readings (gpc_encode(latitude, longitude, false));
```

Errors are raised with the specification's reason codes as the message:
`GPC_NULL`, `GPC_CHAR`, `GPC_LENGTH`, `GPC_CHECK`, `GPC_RESERVED`,
`GPC_LATITUDE`, `GPC_LONGITUDE`, `GPC_LEVEL`, under SQLSTATE `22023`, so a
caller can catch the class and read the reason.

## Geometry

```
psql -d yourdb -f sql/gpc.sql -f sql/gpc_postgis.sql
```

Optional and separate, because nothing above needs it. It adds
`gpc_encode(geometry)`, `gpc_decode_point(code)` and `gpc_decode_box(code)`, so
the axis order stops being the caller's problem. Longitude is X and latitude is
Y, which is the reverse of the order the format writes them in and the classic
way to get this wrong.

A point in any SRID other than 4326 is refused rather than encoded as though its
metres were degrees. That failure would otherwise be silent and produce a
perfectly well-formed code for the wrong place.

## Testing

```
psql -d yourdb -v ON_ERROR_STOP=1 -f sql/gpc.sql -f sql/load.sql
```

Against `test_data/`, the same conformance vectors the four library ports are
held to, 3,921 of them. A fifth implementation checked against assertions
written alongside it would prove nothing; these are generated from the
specification and shared.

`check.sql` is plain SQL with no psql meta-commands in it, so the assertions can
be run through anything that speaks the protocol. `load.sql` is the part that
knows about psql. That separation exists because it is what let this port be
developed against PostgreSQL compiled to WebAssembly, with no server and no
container, while CI runs the identical assertions against a real one.
