# Grid Point Code, version 2

**Status:** draft. This document defines version 2 of the format. It is
complete enough to implement from without reading any existing source, and the
core carries no dependency on anything outside itself.

Version 1 remains published and is described in
[Appendix B](#appendix-b--decoding-version-1-optional). An implementation may
skip that appendix entirely and still be fully conformant.

## Contents

| | | | | |
| --- | --- | --- | --- | --- |
| [1. Overview](#1-overview) | [6. Decoding](#6-decoding) | [11. Ordering](#11-ordering) | [16. Seams](#16-seams) | [A. Reference implementation](#appendix-a--reference-implementation) |
| [2. The coordinate domain](#2-the-coordinate-domain) | [7. Floating-point rules](#7-floating-point-rules) | [12. The short form](#12-the-short-form) | [17. Advisory screening](#17-advisory-screening-non-normative) | [B. Decoding version 1](#appendix-b--decoding-version-1-optional) |
| [3. The grid](#3-the-grid) | [8. Parsing and normalisation](#8-parsing-and-normalisation) | [13. The integer form](#13-the-integer-form) | [18. The spatial API](#18-the-spatial-api) | [C. The reserved namespace](#appendix-c--the-reserved-namespace) |
| [4. The alphabet](#4-the-alphabet) | [9. Classification](#9-classification) | [14. The check character](#14-the-check-character-optional) | [19. Coordinate conversions](#19-coordinate-conversions) | [D. Sharing a code](#appendix-d--sharing-a-code-non-normative) |
| [5. Encoding](#5-encoding) | [10. The locality guarantee](#10-the-locality-guarantee) | [15. Typos](#15-typos) | [20. Conformance](#20-conformance) | |

## Conventions

**MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT** and **MAY** carry their
usual force. A conformant implementation satisfies every MUST in the numbered
sections below, plus the conformance vectors of [section 20](#20-conformance).

Numbers are decimal. `//` is integer division that truncates toward zero; both
operands are non-negative everywhere it appears. `%` is the remainder of that
division. Character positions are numbered from 1, so position 1 is the first
character of the code.

Where this document reports a measurement, it names the sample size and the
method. No number below is carried over from an earlier draft; each was
measured against the format as specified.

The executable companion to this document is [`reference/`](reference/).
`reference/verify.py` checks every exact claim made here, and
`reference/measure.py` reproduces every table of measured figures. A figure in
this document that the harness no longer produces means one of the two is
wrong, which is the point of keeping both.

---

## 1. Overview

A Grid Point Code names one cell of a fixed grid laid over the Earth. The code
is ten characters, always, written in two groups of five:

```
#G3RJM-98NM9
```

The first character divides the world into 24 cells of 45 degrees of latitude
by 60 degrees of longitude. Each of the nine characters after it divides the
cell named so far into 25 parts, five by five. After ten characters the cell is
2.56 m north to south and 3.42 m east to west at the equator.

Every character is therefore a refinement of the ones before it, and that is
the property the format exists for: **two codes that begin with the same k
characters name points in the same level-k cell.** This is containment, not
correlation, so it holds for every pair of points without exception.
[Section 10](#10-the-locality-guarantee) states it precisely and bounds it.

Three consequences fall out of the construction and are guaranteed by this
specification:

* Sorting codes as plain ASCII strings sorts them geographically
  ([section 11](#11-ordering)).
* The last five characters are a self-contained local form, recoverable to the
  full code against any nearby reference ([section 12](#12-the-short-form)).
* Codes beginning with `X` cannot be produced by encoding a coordinate, so that
  space is reserved rather than wasted
  ([Appendix C](#appendix-c--the-reserved-namespace)).

---

## 2. The coordinate domain

Coordinates are latitude and longitude in decimal degrees on the WGS84 datum
(EPSG:4326). Latitude is positive north, longitude positive east.

An implementation MUST accept every latitude in the closed interval
[-90, 90] and every longitude in the closed interval [-180, 180].

An implementation MUST reject NaN and both infinities, and MUST reject any
value outside those intervals. Rejection MUST be explicit: an error, not a
wrapped or clamped result.

The rejection MUST say which axis was at fault: `LATITUDE` or `LONGITUDE`.
Neither carries the `GPC_` prefix that the reasons in
[section 9](#9-classification) do, and the difference is deliberate. Those
describe a string that failed to parse; these describe an argument outside its
domain, which is a different kind of mistake and is reported on the terms set
out in [section 18.1](#181-cells). An argument out of range is raised the way
its own language raises one. A caller writing against more than one
implementation should therefore not assume the reason arrives as a typed error,
only that the axis is named.

Four inputs need a stated answer rather than an accident:

| Input | Rule |
| --- | --- |
| Latitude exactly -90 or +90 | Clamped into the first or last row of the grid. The poles encode. |
| Longitude exactly +180 | Normalised to -180 **before** anything else. One point, one code. |
| Longitude exactly -180 | Used as is. |
| Negative zero, in either axis | Identical to positive zero. The sign disappears in the offset applied in [section 5](#5-encoding); no separate rule is needed. |

The +180 normalisation MUST happen first, because it is the only case where two
distinct inputs must produce one code. Everything after it is arithmetic that
cannot tell the two apart.

Version 1 rejected the poles and rejected both ends of the antimeridian.
Version 2 accepts all of them.

---

## 3. The grid

The grid has ten levels. Level 1 divides the Earth into 4 rows by 6 columns.
Every level below it divides each cell into 5 rows by 5 columns.

The full grid is therefore

```
ROWS = 4 * 5^9 =  7,812,500   rows,    each 180/ROWS = 0.00002304 degrees of latitude
COLS = 6 * 5^9 = 11,718,750   columns, each 360/COLS = 0.00003072 degrees of longitude
```

Rows are numbered from 0 at latitude -90, increasing north. Columns are
numbered from 0 at longitude -180, increasing east.

A level-k cell spans `45 / 5^(k-1)` degrees of latitude by `60 / 5^(k-1)`
degrees of longitude.

The 4-by-6 first level is what makes the cells nearly square. Six columns of 60
degrees against four rows of 45 degrees absorbs the two-to-one aspect ratio of
the graticule in one step, so the ratio of a cell's height to its width is 0.75
at every level. A level-10 cell is exactly square at latitude 41.519 degrees.

Distances below use 111,132 m for one degree of latitude and 111,319.49 m for
one degree of longitude at the equator. East-west figures shrink with the
cosine of latitude; north-south figures do not.

| Level | Latitude span | Longitude span | North-south | East-west | Scale |
| ---: | --- | --- | ---: | ---: | --- |
| 1 | 45° | 60° | 5,000.9 km | 6,679.2 km | Continent |
| 2 | 9° | 12° | 1,000.2 km | 1,335.8 km | Country |
| 3 | 1.8° | 2.4° | 200.0 km | 267.2 km | Region |
| 4 | 0.36° | 0.48° | 40.0 km | 53.4 km | Metropolitan area |
| 5 | 0.072° | 0.096° | 8.0 km | 10.7 km | District |
| 6 | 0.0144° | 0.0192° | 1.6 km | 2.1 km | Suburb |
| 7 | 0.00288° | 0.00384° | 320.1 m | 427.5 m | Street |
| 8 | 0.000576° | 0.000768° | 64.0 m | 85.5 m | City block |
| 9 | 0.0001152° | 0.0001536° | 12.8 m | 17.1 m | Building |
| 10 | 0.00002304° | 0.00003072° | 2.6 m | 3.4 m | Doorway |

---

## 4. The alphabet

```
0123456789CDFGHJKLMNPRTWX
```

Twenty-five symbols. The symbol at index i is the i-th character of that
string, counting from 0.

Two properties of this ordering are load-bearing and MUST NOT be changed:

1. **It is ASCII-ascending.** Every symbol sorts after the one before it under
   a plain byte comparison. This is what makes a string sort a spatial sort
   ([section 11](#11-ordering)), and it is why the digits come first.
2. **It has twenty-five symbols, an odd perfect square.** The rule for levels 2
   to 10 needs a 5-by-5 grid per level, and the reflection needs the side to be
   odd. Twenty-five is the smallest size that provides both while excluding
   every vowel.

No vowel appears, so no English word can be spelled by a code. `V` is excluded
because it is the one letter with both a visual twin and a doubling failure:
`VV` reads as `W`. `Y` is excluded because it behaves as a vowel often enough
to let words form.

`L` is a member of the alphabet. It is never treated as a confusable of `1` or
`I`; see [section 8](#8-parsing-and-normalisation).

---

## 5. Encoding

### 5.1 Coordinate to grid

```
if longitude == +180.0 then longitude = -180.0

row = floor( (latitude  +  90.0) *  7812500.0 / 180.0 )
col = floor( (longitude + 180.0) * 11718750.0 / 360.0 )

row = clamp(row, 0,  7812499)
col = clamp(col, 0, 11718749)
```

The clamp catches exactly one input per axis, latitude +90 and longitude
+180 had it not already been normalised, and is otherwise never reached. It
MUST still be applied: it is what makes the poles encode instead of indexing
past the end of the grid.

[Section 7](#7-floating-point-rules) pins how these two expressions are
evaluated. They are the only floating-point arithmetic in the format.

### 5.2 Grid to code

```
ALPHABET = "0123456789CDFGHJKLMNPRTWX"

r1 = row // 5^9                       # 0..3
c1 = col // 5^9                       # 0..5

# Level 1: a serpentine over the 24 blocks, west to east, snaking northward.
if r1 is even then k = c1 else k = 5 - c1
emit ALPHABET[ r1 * 6 + k ]

sr = r1
sc = c1

for level = 2 to 10:
    if level == 6 then sr = 0; sc = 0        # see 5.3

    p = 5^(10 - level)
    r = (row // p) % 5
    c = (col // p) % 5

    if sc is even then R = r else R = 4 - r
    sr = sr + r
    if sr is even then C = c else C = 4 - c
    sc = sc + c

    emit ALPHABET[ R * 5 + C ]
```

Ten characters are emitted. The result is the unformatted code.

`sr` and `sc` accumulate the *unreflected* digits `r` and `c`. The order of the
four statements inside the loop is normative: `R` is decided from `sc` before
this level's `c` is added to it, and `C` is decided from `sr` *after* this
level's `r` has been added. Reversing either produces a different format.

This is a Peano digit reflection, each axis mirrored according to the parity
of the digits accumulated in the other. It is what puts consecutive codes in
adjacent cells ([section 11](#11-ordering)).

Because `r1 * 6 + k` ranges over 0 to 23 only, index 24 is unreachable at
position 1. No encoded code begins with `X`. See
[Appendix C](#appendix-c--the-reserved-namespace).

### 5.3 The parity reset at level 6

Entering level 6, both accumulators MUST be reset to 0.

Without the reset, the meaning of characters 6 to 10 would depend on the parity
of the digits in characters 1 to 5, and the last five characters would not name
anything on their own. With it, **the last five characters depend only on the
position within the level-5 cell**, which is what makes the short form of
[section 12](#12-the-short-form) recoverable rather than merely suggestive.

The reset costs one property, and it is worth naming honestly. The ordering is
continuous inside every level-5 cell, but the traversal of one cell ends at its
far corner while the next begins at its near corner, so every transition
between level-5 cells is a jump. There are 9,374,999 such transitions out of
91,552,734,374,999 steps, which leaves 99.99999 % of consecutive codes in
adjacent cells. [Section 11](#11-ordering) measures the effect on range
queries, which is nil.

### 5.4 Presentation

The formatted code is a `#`, five characters, a `-`, then five characters:

```
#G3RJM-98NM9
```

`encode` SHOULD return the formatted form by default and MUST be able to return
the unformatted ten-character form. Both denote the same code. The conformance
vectors use the unformatted form.

The grouping is not arbitrary. The second group is exactly the short form of
[section 12](#12-the-short-form), so the printed code shows its own local form.

### 5.5 Worked examples

| Place | Latitude, longitude | Code |
| --- | ---: | --- |
| Toronto | 43.65000, -79.38000 | `#G3RJM-98NM9` |
| CN Tower | 43.64260, -79.38710 | `#G3RJM-0M6DX` |
| Ahmedabad | 23.02250, 72.57140 | `#KDC8X-JM49X` |
| Sydney Opera House | -33.85680, 151.21530 | `#6LK4X-NRP0R` |
| Machu Picchu | -13.16310, -72.54500 | `#C8HKC-13C80` |
| Reykjavík | 64.14660, -21.94260 | `#RDX9R-TN19T` |
| North pole | 90.00000, 0.00000 | `#P4444-PPPPP` |
| South pole | -90.00000, 0.00000 | `#3PPPP-00000` |
| Antimeridian | 0.00000, -180.00000 and +180.00000 | `#F0000-00000` |
| Origin | 0.00000, 0.00000 | `#JPPPP-00000` |

Toronto and the CN Tower are a kilometre apart and share five characters; the
level-5 cell is 8.0 by 10.7 km.

---

## 6. Decoding

### 6.1 Code to grid

The inverse of [5.2](#52-grid-to-code), character by character:

```
i  = index of code[1] in ALPHABET          # 0..23; 24 is reserved, see section 9
r1 = i // 6
k  = i % 6
if r1 is even then c1 = k else c1 = 5 - k

row = r1
col = c1
sr  = r1
sc  = c1

for level = 2 to 10:
    if level == 6 then sr = 0; sc = 0

    j = index of code[level] in ALPHABET
    R = j // 5
    C = j % 5

    if sc is even then r = R else r = 4 - R
    sr = sr + r
    if sr is even then c = C else c = 4 - C
    sc = sc + c

    row = row * 5 + r
    col = col * 5 + c
```

### 6.2 Grid to coordinates

A code names a cell, not a point. `decode` MUST return the **centre** of that
cell.

The centre is an exact integer count of 10^-8 degrees:

```
lat_e8 = (2 * row + 1) * 1152 -  9_000_000_000
lng_e8 = (2 * col + 1) * 1536 - 18_000_000_000
```

Both fit in a signed 64-bit integer and, being below 2^53, in a double without
loss. `decode` MUST return these values rounded to six decimal places: divide
by 100 with halves away from zero, then scale by 10^-6.

**Rounding ties cannot occur.** Every reachable value of `lat_e8` is congruent
to a multiple of 4 modulo 100, and the same holds for `lng_e8`. A remainder of
exactly 50 is therefore unreachable, and no choice of rounding mode can change
any result. This is stated so that no implementation needs to make the choice.

Six decimal places resolve about 0.11 m, comfortably finer than the 2.56 m
cell, so `encode(decode(code))` returns the original code for every code. This
was verified over 200,000 random cells with no exceptions.

The corner was rejected as the decode result: corner values sit exactly on a
cell boundary, where a rounding step can push the re-encoded point into the
neighbouring cell. The centre has half a cell of margin in every direction.

### 6.3 The cell as a box

`decodeToArea` MUST return the boundaries of the cell:

```
south = row       * 180.0 /  7812500.0 -  90.0
north = (row + 1) * 180.0 /  7812500.0 -  90.0
west  = col       * 360.0 / 11718750.0 - 180.0
east  = (col + 1) * 360.0 / 11718750.0 - 180.0
```

These four expressions are subject to [section 7](#7-floating-point-rules). A
caller needing exactness beyond a double SHOULD use the 10^-8 integer form: the
south edge is `row * 2304 - 9_000_000_000` and the west edge is
`col * 3072 - 18_000_000_000`, both exact.

The northern edge of the top row is latitude +90 and the eastern edge of the
last column is longitude +180, even though neither value encodes to that cell.
A box is a closed region; the +180 normalisation of
[section 2](#2-the-coordinate-domain) applies to inputs, not to boundaries.

### 6.4 Worked examples

| Code | Latitude, longitude |
| --- | ---: |
| `#G3RJM-98NM9` | 43.650006, -79.380004 |
| `#KDC8X-JM49X` | 23.022501, 72.571407 |
| `#6LK4X-NRP0R` | -33.856808, 151.215314 |
| `#P4444-PPPPP` | 89.999988, 0.000015 |
| `#JPPPP-00000` | 0.000012, 0.000015 |

The pole decodes to 89.999988 rather than 90: the code names the cell that
contains the pole, and that cell's centre lies just inside it.

---

## 7. Floating-point rules

Four expressions in this specification touch floating point: the two in
[5.1](#51-coordinate-to-grid) and the four in [6.3](#63-the-cell-as-a-box).
Everything else is integer arithmetic. This section exists so that a port in a
compiled language cannot drift from a port in an interpreted one.

All floating-point values are IEEE 754 binary64. Addition, subtraction,
multiplication and division of binary64 values are correctly rounded, which
means every conforming platform produces the identical bit pattern for the
identical inputs. The rules below exist only to stop a compiler from evaluating
something other than what is written.

An implementation MUST evaluate

```
(latitude  +  90.0) *  7812500.0 / 180.0
(longitude + 180.0) * 11718750.0 / 360.0
```

as exactly three operations per axis, associating left to right:

```
t = latitude + 90.0        # correctly rounded
t = t * 7812500.0          # correctly rounded
t = t / 180.0              # correctly rounded
```

An implementation MUST NOT:

* **Reassociate.** `(latitude + 90.0) * (7812500.0 / 180.0)` is a different
  computation. `7812500 / 180` is not representable, so folding it changes
  results near cell boundaries. This is the most likely way to get this wrong.
* **Contract into a fused multiply-add.** No FMA may be formed across these
  operations, in either expression, even though the shapes do not obviously
  invite one. Compilers reassociate before they contract.
* **Evaluate at wider than binary64 precision.** Each of the three results MUST
  be rounded to binary64 before the next operation consumes it. This is a real
  hazard on 32-bit x86 targets that keep intermediates in x87 registers.
* **Enable fast-math or unsafe-math optimisations** for the file containing
  these expressions. Every rule above is one that fast-math is licensed to
  break.
* **Substitute a decimal or arbitrary-precision type.** The format is defined
  on binary64. A decimal type gives different answers, not better ones.

`floor` here is the floor toward negative infinity. Both operands are
non-negative after the offset is applied, so truncation toward zero gives the
same answer; an implementation MAY use either.

Version 1 required a documented decimal-conversion rule because it read five
decimal digits out of the coordinate as text. Version 2 has no such step, and
that whole class of divergence is gone with it.

---

## 8. Parsing and normalisation

A parser MUST apply these steps in this order.

**1. Check that there is anything to parse.** A null, empty or all-whitespace
input is `GPC_NULL`. This is the only rejection that happens before
normalisation; every other check waits until the steps below have run.

**2. Split off the check character.** If the input contains `*`, everything
after the first `*` is the check character and everything before it is the
payload. See [section 14](#14-the-check-character-optional). If no `*` is
present, the whole input is the payload and no check is performed.

**3. Case-fold to upper case using ASCII rules only.** An implementation MUST
NOT use a locale-sensitive upper-casing routine. In a Turkish locale the
default routine maps `i` to `İ`, which is not in the alphabet and would make
the same code valid in one locale and invalid in another.

**4. Remove `#`, `-`, and all whitespace.** These are presentation only and
MAY appear anywhere, in any number, including not at all.

**5. Apply the alias table** to every remaining character:

| Typed | Read as | | Typed | Read as |
| :---: | :---: | --- | :---: | :---: |
| `O` | `0` | | `A` | `4` |
| `I` | `1` | | `E` | `3` |
| `S` | `5` | | `B` | `8` |
| `Z` | `2` | | `V` | `W` |

`U`, `Q` and `Y` are not aliased. They MUST be rejected as `GPC_CHAR`, and an
implementation SHOULD say which character was at fault, because a person who
typed one of them has misread a symbol rather than mistyped a key.

These eleven letters are exactly the letters not in the alphabet, so after this
step every remaining letter is either a real symbol or an error. `L` is a real
symbol and MUST NOT be aliased to `1`; it is a distinct cell, and aliasing it
would make two different codes collide.

`A` maps to `4` and `E` maps to `3` rather than being rejected. Neither costs a
geometric code, since neither letter can appear in one, and both are common
enough in handwriting to be worth catching.

**6. Classify** what remains, per [section 9](#9-classification).

Normalisation is idempotent: normalising an already-normalised code returns it
unchanged.

---

## 9. Classification

After normalisation, a string falls into exactly one of three classes.
`classify(input)` MUST return which.

| Class | Condition |
| --- | --- |
| `INVALID` | Empty, or length is not 10, or any character is outside the alphabet, or a check character is present and does not match |
| `RESERVED` | Length 10, all characters in the alphabet, first character `X`, and either a matching check character or none |
| `GEOMETRIC` | Length 10, all characters in the alphabet, first character not `X`, and either a matching check character or none |

Reason codes for `INVALID` are `GPC_NULL` (nothing to parse), `GPC_LENGTH`
(wrong number of characters), `GPC_CHAR` (a character outside the alphabet
after aliasing) and `GPC_CHECK` (a check character that does not match),
tested in that order.

`GPC_CHECK` comes last because it is the only reason that needs a well-formed
payload before it can be evaluated, and it can never be reported for an input
that carried no `*`. [Section 14](#14-the-check-character-optional) states what
the check character has to satisfy.

`decode` MUST succeed on `GEOMETRIC`, and MUST raise a **typed** error on the
other two: `GPC_RESERVED` for a reserved code, distinct from the invalid
reasons above.

The distinction matters from the first release, not later. A reserved code is
well-formed and may become meaningful; an invalid one is a typing error. A
caller that cannot tell them apart cannot be taught the difference afterwards
without a breaking change, so the two are separated now even though nothing
currently resolves a reserved code.

`isValid` returns true for `GEOMETRIC` only. An implementation SHOULD also
expose the class directly so that a caller can distinguish the cases without
parsing an error message.

Classification sees exactly what `decode` sees, check character included. A
caller told that a code is valid MUST be able to decode it, so a wrong check
character has to fail in both places or in neither.

---

## 10. The locality guarantee

### 10.1 The guarantee

> **Theorem.** Let P and Q be coordinates in the domain, with codes p and q.
> For every k from 1 to 10: p and q agree in their first k characters **if and
> only if** P and Q lie in the same level-k cell.

*Proof.* By induction on k.

For k = 1, the map `(r1, c1) -> r1 * 6 + k` of [5.2](#52-grid-to-code) is a
bijection from the 24 level-1 cells onto the indices 0 to 23, so the first
character determines and is determined by the level-1 cell.

For k > 1, assume the first k-1 characters determine the level-(k-1) cell. The
parity state `(sr, sc)` entering level k is a function of the digits of that
cell alone, including at k = 6, where it is the constant `(0, 0)`. Given that
state, the map `(r, c) -> R * 5 + C` is a bijection from the 25 sub-cells onto
the indices 0 to 24, because reflecting a digit `r -> 4 - r` is a permutation of
`{0,1,2,3,4}`. So character k determines and is determined by which sub-cell of
the level-(k-1) cell the point lies in. ∎

Verified over 120,000 random pairs at all ten levels, 1,200,000 checks, with no
exceptions.

The per-prefix bound is the level table in [section 3](#3-the-grid). Two codes
sharing four characters are in the same 40 by 53 km cell; sharing seven, the
same 320 by 428 m cell. No pair anywhere on Earth can do otherwise.

### 10.2 The guarantee is one-directional

A shared prefix proves proximity. Proximity does **not** promise a shared
prefix, and an implementation MUST NOT be documented as though it does.

Every fixed grid has seams. [Section 16](#16-seams) maps them and gives
worked examples of points a few metres apart that share nothing.

Statistically the seams are rare. Over 20,000 pairs exactly 100 m apart, drawn
uniformly over the sphere:

| Shared prefix | Share of pairs |
| --- | ---: |
| 6 characters or more | 91.45 % |
| 5 characters or more | 98.31 % |
| 4 characters or more | 99.64 % |
| Fewer than 4 | 0.36 % |
| None at all | 0.000 % |

---

## 11. Ordering

### 11.1 A string sort is a spatial sort

The alphabet is ASCII-ascending ([section 4](#4-the-alphabet)) and each
character is the more significant the earlier it appears. Therefore, for any
two codes, comparing them as byte strings gives the same answer as comparing
their positions in the traversal order of the grid.

An implementation MUST NOT reorder the alphabet, and a consumer MAY rely on
plain `ORDER BY code` or a default string index reproducing spatial order with
no decoding, no custom collation and no separate index column.

This is why the digits precede the letters. The reverse convention would have
sorted `C` before `0`, and every database index built on the raw code would
have been silently non-spatial.

### 11.2 Continuity

Within a level-5 cell, consecutive codes always name edge-adjacent cells: the
traversal is a continuous curve over all 3,125 by 3,125 cells, entering at
offset (0, 0) and leaving at offset (3124, 3124).

Between level-5 cells the curve jumps, by the construction of
[5.3](#53-the-parity-reset-at-level-6). There are exactly 9,374,999 such jumps
in the 91,552,734,374,999 steps of the full traversal:

| | |
| --- | ---: |
| Consecutive codes in edge-adjacent cells | 99.99999 % |
| Discontinuities, worldwide | 9,374,999 |
| All of them at | level-5 cell boundaries |

### 11.3 Clustering

What a range query actually costs is the number of disjoint code ranges needed
to cover a rectangular window. Measured over random windows at full resolution:

| Window | Disjoint ranges, mean | Samples |
| --- | ---: | ---: |
| 4 × 4 cells | 3.97 | 3,000 |
| 16 × 16 cells | 16.04 | 3,000 |
| 32 × 32 cells | 32.12 | 800 |

A w × w window costs about w ranges, one contiguous run per row of the window,
which is the best a one-dimensional ordering of a two-dimensional space can do.
The parity reset does not show up here, because a window that small lies inside
one level-5 cell almost always.

---

## 12. The short form

### 12.1 Definition

The **short form** of a code is its last five characters, written with a
leading dash:

```
#G3RJM-98NM9     full code
      -98NM9     short form
```

It is literally the second printed group. No computation is involved in
producing it, and an implementation MUST define `shorten` as that slice: it
returns the five characters, and the leading dash belongs to the presentation
form, exactly as `#` does. `recoverShort` MUST accept the short form written
either way.

A short form names a position uniquely within its level-5 cell, which is 8.0 by
10.7 km. Because of the parity reset of
[5.3](#53-the-parity-reset-at-level-6), those five characters mean the same
thing in every level-5 cell on Earth, which is what makes the next part work.

### 12.2 Recovery

`recoverShort(short, nearLatitude, nearLongitude)` returns the full ten-
character code. It MUST be implemented as follows.

First read the five characters as an offset within a level-5 cell, with the
parity state seeded to zero, the same loop as [6.1](#61-code-to-grid) with
`sr = sc = 0` and no level-1 step:

```
rowLow = 0 ; colLow = 0 ; sr = 0 ; sc = 0
for each character ch of short:
    j = index of ch in ALPHABET
    R = j // 5 ; C = j % 5
    if sc is even then r = R else r = 4 - R
    sr = sr + r
    if sr is even then c = C else c = 4 - C
    sc = sc + c
    rowLow = rowLow * 5 + r
    colLow = colLow * 5 + c
```

Then pick the level-5 cell whose copy of that offset lies nearest the
reference. With `P5 = 5^5 = 3125`, `R5 = 4 * 5^4 = 2500` rows of level-5 cells
and `C5 = 6 * 5^4 = 3750` columns:

```
(rowRef, colRef) = the grid indices of the reference, per 5.1

cellRow = (rowRef - rowLow + 1562) // 3125
cellRow = clamp(cellRow, 0, 2499)                  # latitude does not wrap

cellCol = ((colRef - colLow + 1562) // 3125) mod 3750   # longitude wraps

row = cellRow * 3125 + rowLow
col = cellCol * 3125 + colLow
return the code for (row, col), per 5.2
```

Both divisions are floor divisions over values that may be negative; an
implementation MUST use floor semantics here, not truncation toward zero, or
the result is wrong west and south of the reference.

This is exact integer arithmetic. There is no search, no distance function and
no tie to break.

### 12.3 What recovery guarantees

Recovery returns the correct full code **whenever the reference lies within
half a level-5 cell of the true point in each axis**, at most 1,562 rows and
1,562 columns away, that is

```
|Δlatitude|  ≤ 0.03598848°     (3.999 km, at every latitude)
|Δlongitude| ≤ 0.04798464°     (5.342 km at the equator, less further north or south)
```

Verified over 60,000 random points with references drawn uniformly inside that
box: 60,000 correct, no exceptions.

The bound is exactly half a cell because the candidates are one level-5 cell
apart in each axis and 3,125 is odd, so the midpoint falls between two integer
row indices and no tie is possible.

Outside that box recovery returns a neighbouring cell's copy of the same
offset, which is a plausible location 8 or 10 km away. An implementation
SHOULD document the bound rather than the failure mode, and a caller that
cannot bound its reference SHOULD NOT use the short form.

The known weakness is shared with every reference-relative short form and is
not a defect of this construction: recovery is only as good as agreement about
the reference. Two services that place the same suburb several kilometres apart
can recover different codes. The short form is a convenience. **The full ten
characters are the form of record.**

---

## 13. The integer form

A code is ten symbols of an alphabet of 25, so it is a base-25 numeral:

```
value = 0
for each character ch of the code:
    value = value * 25 + index of ch in ALPHABET
```

The largest possible value is 25^10 - 1 = 95,367,431,640,624, which needs 47
bits. The integer form is therefore **48 bits, six bytes**, and an
implementation that serialises it MUST use big-endian byte order.

Two properties follow and are guaranteed:

* **It is order-preserving.** Because the alphabet is ASCII-ascending, sorting
  the integers gives the same order as sorting the code strings, which
  ([section 11](#11-ordering)) is spatial order. Big-endian bytes preserve it
  again, so a six-byte binary key sorts spatially too.
* **Reserved codes occupy the top of the range.** Every geometric code is below
  24 × 25^9 = 91,552,734,375,000, and every reserved code is at or above it. A
  single comparison classifies without parsing.

The integer form is the representation to use for a database key, a QR or NFC
payload, or any place where six bytes is worth more than twelve characters. It
is a re-encoding of the code, not a second format: conversion in both
directions is exact and lossless.

Six bytes do not fill a 64-bit column, and the sixteen bits left over are
spare. This specification assigns them no meaning: they are not a version
marker, they are not a flags field, and they are not a place to record
something a latitude and a longitude do not determine. That is said here rather
than left unsaid, because a field nobody has defined is read by the next
implementer as a field that is free, and two parties who each decide
differently find out only once the rows are written. An implementation that
stores the integer form in a 64-bit integer MUST zero the top sixteen bits, and
MUST reject a value that arrives with any of them set rather than masking them
away.

---

## 14. The check character (optional)

The canonical code is ten characters and carries no checksum. For voice, radio
and paper, where a code is read aloud and written down, this section defines an
optional eleventh character.

### 14.1 Syntax

```
#G3RJM-98NM9*T
```

A `*` followed by one symbol. The `*` is on every telephone keypad, and it
keeps an eleven-symbol check form mechanically distinct from an eleven-
character version 1 code.

The check character is normalised along with the payload: case-folded,
separators removed, aliases applied. What remains MUST be exactly one
symbol of the alphabet. Nothing after the `*`, more than one symbol, or a
character the alias table cannot resolve is `GPC_CHECK`, the same reason a
mismatch gets. In every one of those cases the input carried a check the
implementation could not confirm, and [section 9](#9-classification) does not
let it be discarded.

The check form is **not canonical**. `#G3RJM-98NM9` and `#G3RJM-98NM9*T` denote
the same location, and an implementation MUST NOT emit the check form unless
asked for it. Storage and interchange use the ten-character form.

### 14.2 The field

Arithmetic is in GF(25), the field of order 25, built as GF(5) extended by a
root `t` of `t² + t + 2`, which is irreducible over GF(5).

An element is `a + b·t` with `a` and `b` in `{0,1,2,3,4}`. **An element is
represented by the symbol index `b·5 + a`**. That is, a symbol's column digit
is the constant part and its row digit is the `t` part, matching the way
[5.2](#52-grid-to-code) builds a symbol out of a row and a column.

Since `t² = -t - 2 = 4t + 3`, the two operations are:

```
add(a + b·t, c + d·t) = ((a + c) mod 5) + ((b + d) mod 5)·t

mul(a + b·t, c + d·t) = ((a·c + 3·b·d) mod 5)
                      + ((a·d + b·c + 4·b·d) mod 5)·t
```

`t` is a primitive element: its powers run through all 24 non-zero elements
before returning to 1.

### 14.3 The check character

Let `v(s)` be the symbol index of character `s`, and let the payload be the ten
characters `a₁ … a₁₀`. Compute the syndrome

```
S = Σ  t^i · v(aᵢ)      for i = 1 … 10
```

The check character is the symbol whose index is

```
v(c) = t · S
```

That is the whole rule: multiply the syndrome by `t`. It works because
`t¹² = -1`, so weighting the check character by `t¹¹` makes the eleven terms
sum to zero.

To **verify**, recompute the syndrome over the payload, add the check
character weighted by `t¹¹`, and require zero:

```
( Σ t^i · v(aᵢ)  for i = 1 … 10 )  +  t¹¹ · v(c)  =  0
```

A mismatch is `GPC_CHECK`. An implementation MUST NOT accept a code with a
wrong check character by discarding the check, in `decode` or in `isValid`:
a code that fails the check fails everywhere.

The eleven weights, as symbol indices, are fixed:

| i | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `t^i` | 5 | 23 | 22 | 17 | 24 | 2 | 10 | 16 | 19 | 9 | 18 |

### 14.4 What it catches

Because the weights are non-zero and pairwise distinct, and a field has no zero
divisors:

* **Every single-symbol error**, in the payload or in the check character
  itself, is detected.
* **Every transposition of two adjacent symbols** is detected.

Verified exhaustively over 4,000 random codes: every one of the 1,056,000
single-symbol errors and 38,389 adjacent transpositions was detected.

### 14.5 Worked example

For `#G3RJM-98NM9` the syndrome is 23, so `v(c) = t · 23 = 22`, which is the
symbol `T`. The check form is `#G3RJM-98NM9*T`.

| Code | Check form |
| --- | --- |
| `#G3RJM-98NM9` | `#G3RJM-98NM9*T` |
| `#KDC8X-JM49X` | `#KDC8X-JM49X*D` |
| `#P4444-PPPPP` | `#P4444-PPPPP*2` |
| `#JPPPP-00000` | `#JPPPP-00000*M` |

### 14.6 Emitting it

An implementation SHOULD provide `withCheck(code)`, returning the check form as
a single string.

Composing it from parts is three operations and two chances to be wrong: the
star can be dropped, or the check character can be spliced inside the group
separator rather than after it. Neither mistake is caught by anything, because
the result is a string nobody validated. A single operation removes both.

`withCheck` computes the check character for the payload it is given and
ignores any check character that payload already carried, so applying it to a
code that is already in check form returns the same string with a correct
check.

---

## 15. Typos

### 15.1 What a typo does

A hierarchical code bounds the damage a typo can do, and the same property
makes the damage hard to see. This is true of every prefix-local code; what
follows is the measurement for this one.

Measured over 191,910 random single-character substitutions and 192,021 random
adjacent transpositions, applied to codes drawn uniformly over the sphere:

| | |
| --- | ---: |
| Caught before decoding (an `X` reaching position 1) | 0.42 % |
| Silent, landing between 0.5 and 50 km away | 29.1 % |
| Adjacent transpositions caught | 0.43 % |

The first figure is not really a measurement. A substitution is caught exactly
when it lands on position 1 and picks `X`, which for a uniformly chosen
position and replacement is `1/10 × 1/24`, or **one in 240**. The sample
returned 0.403 %, within one standard error of it.

Displacement depends almost entirely on which character was hit:

| Position | Median displacement | Maximum | Character of the error |
| --- | ---: | ---: | --- |
| 1 to 3 | 2,472 km | 20,009 km | Obviously wrong on any map |
| 4 to 6 | 20.8 km | 283 km | Plausible and silent, the dangerous middle |
| 7 to 10 | 65 m | 2.3 km | Usually harmless |

One code in 240 is not error detection in any useful sense; it is the structural
fact that `X` cannot begin a geometric code
([Appendix C](#appendix-c--the-reserved-namespace)). **An implementation MUST
NOT be documented as detecting typos.** The optional check character of
[section 14](#14-the-check-character-optional) is the only mechanism here that
detects, and it is not present unless someone asks for it.

### 15.2 Normative guidance

> A consuming application **MUST** display the decoded point on a map, or
> otherwise confirm it against something the user recognises, before acting on
> it.

Nearly 29 % of single-character typos produce a location in the right region
and the wrong place. No amount of format design removes that; confirmation
does.

### 15.3 Correction

The structure that hides an error also locates it. `suggestCorrections` MUST be
implemented as follows.

**Candidates.** At most 249, generated in this order so that every port
produces the same list:

1. For each position 1 to 10, in order, and for each symbol of the alphabet in
   index order, skipping the symbol already there: the code with that position
   replaced. Always 240 candidates.
2. For each position 1 to 9, in order, skipping positions where the two
   characters are equal: the code with that character and the next transposed.
   Up to 9 candidates.

The count is 249 only when no two adjacent characters are equal. A code such as
`#P4444-PPPPP` yields 242, and an implementation MUST NOT pad the list back to
249 with duplicates.

**Filter.** A candidate is kept if it is `GEOMETRIC` and its level-k cell is
the reference's level-k cell or one of that cell's eight neighbours, with
columns wrapping at the antimeridian and rows not wrapping. `k` is a parameter.

**Ranking.** Kept candidates are ordered by

```
score = 9 · Δrow² + 16 · Δcol²
```

where `Δrow` and `Δcol` are the full-resolution grid offsets from the
reference, `Δcol` taken the short way around the antimeridian. Ties are broken
by the integer form of [section 13](#13-the-integer-form), ascending.

The weights 9 and 16 make this the squared distance in degree space: a row step
is exactly three quarters of a column step, and `(3·Δrow)² + (4·Δcol)²` is
proportional to it. The whole expression is integer arithmetic bounded well
below 2⁵³, so every port ranks identically. It is deliberately not a
great-circle distance: trigonometric functions are not bit-identical across
languages, and this ordering must be.

**Choosing k.** The window is 3 by 3 level-k cells. It MUST comfortably exceed
the uncertainty in the reference. Measured over typos that landed more than
10 km from the reference, the ones worth correcting:

| k | Window | Reference good to | True code in the set | Ranked first | Median size | 90th |
| ---: | --- | ---: | ---: | ---: | ---: | ---: |
| 4 | 120 × 160 km | 5 km | 100.00 % | 97.27 % | 1 | 155 |
| 5 | 24 × 32 km | 5 km | 99.47 % | 96.80 % | 1 | 7 |
| 6 | 4.8 × 6.4 km | 500 m | 99.93 % | 99.93 % | 1 | 1 |
| 7 | 960 × 1,282 m | 100 m | 100.00 % | 100.00 % | 1 | 1 |

`k = 6` SHOULD be the default: it suits a device fix or a named suburb, returns
one candidate in the median case, and never returned more than four in 1,500
trials. Widening k to cover a poorer reference costs precision, not
correctness: at `k = 4` the true code is always present but arrives with up to
157 near neighbours, because a typo in the last three characters barely moves
the point.

The geography does the work a check digit would, and unlike a check digit it
corrects rather than merely detects.

---

## 16. Seams

Level-1 boundaries are the seams of the format. They lie on:

* the equator, and latitude 45° north and south;
* the prime meridian, and every 60th meridian east and west of it: 60°, 120°
  and the antimeridian.

Two points on opposite sides of one of these lines are in different level-1
cells, so they share **no** characters at all, however close they are. The
minimum possible separation across a seam is one cell: 2.56 m north to south,
or 3.42 m east to west at the equator and less at higher latitudes.

| Pair | Apart | Shared |
| --- | ---: | ---: |
| Across the Greenwich meridian, at the Royal Observatory | 2.8 m | 0 |
| Across the equator, near Pontianak | 4.4 m | 0 |
| Across the 60° E meridian | 4.1 m | 0 |
| Across the 45° N parallel | 4.4 m | 0 |

```
51.47780, -0.00002   ->  #R0NHX-XX50P
51.47780,  0.00002   ->  #PX5C0-PP94X
```

This is not a defect that a better construction would remove. Any grid of fixed
cells has boundaries, and any code built on one has seams. What a format can do
is put them on round, documentable lines, state where they are, and provide
containment and neighbour operations that cross them correctly. This one does.

The statistics in [10.2](#102-the-guarantee-is-one-directional) are the honest
summary: seams cost 0.36 % of nearby pairs their shared prefix.

---

## 17. Advisory screening (non-normative)

Nothing in this section is required for conformance.

The alphabet excludes every vowel, so no English word can appear in a code.
Words that substitute digits for letters can still appear, and at ten
characters there is no spare code space to skip them: every cell has exactly
one code, and that is a property worth more than the absence of the occasional
unfortunate string.

An implementation MAY offer `screen(code)`, which reports substrings of a code
that match a list of unwanted words and their digit-substituted variants. If it
does, the rules below apply, so that two implementations carrying the same list
report the same spans.

### 17.1 What screening is

* It MUST return matched spans and MUST NOT refuse to encode, decode or
  validate anything. Screening advises; it never blocks.
* The list MUST be stored as hashes of the variants, expanded at build time, so
  that **no published package carries the words**. That is the part that
  matters: a package is installed by people who never asked to receive a word
  list, and a hash is what keeps it from reaching them.
* Whatever holds the words themselves MUST NOT be plaintext in source control,
  so that they are not indexed, not greppable and not turned up by a search.
  Neither this nor the hashing is a security measure, and neither MUST be
  described as one: the variants are short strings over an alphabet of
  twenty-five symbols, and a space that small can be searched exhaustively by
  anyone who wants to. The aim is that nobody meets these words by accident,
  not that nobody can find them on purpose.
* The list MUST carry a version tag, reported alongside any match, so that a
  caller can tell a changed result from a changed list.

### 17.2 Expansion

A source word is a sequence of lower-case letters. Each letter expands to the
symbols it can appear as in a code:

```
a -> 4        h -> H        o -> 0        v -> --
b -> 8        i -> 1        p -> P        w -> W
c -> C        j -> J        q -> --       x -> X
d -> D        k -> K        r -> R        y -> --
e -> 3        l -> L 1      s -> 5        z -> 2
f -> F        m -> M        t -> T 7
g -> G 6 9    n -> N        u -> --
```

The variants of a word are every combination of those symbols, one per letter,
in order. A word containing a letter that expands to nothing cannot appear in a
code at all and MUST be dropped rather than partially expanded.

This table is not the alias table of [section 8](#8-parsing-and-normalisation)
and MUST NOT be confused with it. That one says which symbol a reader who typed
a confusable letter meant; this one says what a letter looks like once it is a
symbol. The direction is opposite and the relation is wider. `L` expands both
ways here, as itself and as `1`, precisely because reading is where the two
disagree.

Variants shorter than four symbols MUST NOT enter the list. Three symbols occur
by chance often enough that a warning built on them would mean nothing.

### 17.3 The list

Each variant is stored as its 32-bit FNV-1a hash, written as eight lower-case
hexadecimal characters:

```
h = 2166136261
for each byte b of the variant, encoded UTF-8:
    h = h XOR b
    h = h * 16777619, kept to 32 bits
```

The list is those strings, deduplicated and sorted ascending, together with a
version tag.

A cryptographic hash would be no better here and would cost three of the four
ports an import they otherwise do not need. [17.1](#171-what-screening-is)
already says this is not protection: what matters is that the words are not in
the repository, and thirty-two bits of a cheap mixer achieves that exactly as
well as thirty-two bits of an expensive one. What the function does have to be
is identical in every language, and this one is -- three integer operations per
byte, over symbols that are all ASCII, so there is no encoding question and no
library to agree with.

### 17.4 Matching

`screen(code)` normalises its argument per
[section 8](#8-parsing-and-normalisation) and then, for each length L from 4 to
10 and each start position from 1 to 11 - L, hashes that substring the same way
and looks it up. Twenty-eight lookups, no allocation worth the name.

It returns the version tag and the spans that matched, as (position, length),
ordered by position and then by length. Spans may overlap, and every one that
matched MUST be reported. A code that matches nothing returns no spans and the
version tag all the same, because a caller has to be able to tell "clean under
the list it was screened against" from "never screened".

How often a warning fires is set by the shortest entries and by almost nothing
else. A four-symbol variant occupies seven of those twenty-eight windows
against 25⁴ = 390,625 possibilities, so it matches about one code in 55,800; a
six-symbol variant matches about one in 49 million, and a longer one never
fires in practice. A list is therefore exactly as noisy as its four-letter
words and no noisier -- fifty such variants come to roughly one code in a
thousand -- so a list that has become noisy is shortened from the bottom, not
the top.

---

## 18. The spatial API

[Section 10](#10-the-locality-guarantee) is a statement about prefixes. This
section defines the operations that let a caller act on it without re-deriving
the arithmetic. Every one of them is exact integer arithmetic except
[`distance`](#185-distance), which is the single exception and says so.

### 18.1 Cells

A **cell** is the first k characters of a code, for k from 1 to 10. It names
the level-k cell those characters determine, which by
[section 10](#10-the-locality-guarantee) is exactly the set of points whose
codes begin with them.

```
#G3RJM-98NM9     the code -- a level-10 cell, 2.6 by 3.4 m
 G3RJM           the level-5 cell holding it, 8.0 by 10.7 km
 G3R             the level-3 cell holding that, 200 by 267 km
```

`cell(code, k)` returns that prefix. It MUST normalise its argument per
[section 8](#8-parsing-and-normalisation) first, so that the cell of a code
typed with confusable letters is the cell of the code spelled with the symbols
they stand for.

A cell is written without the `#` and without the separator, and MUST NOT be
presented as a code. Ten characters is a code; anything shorter is a region,
and the fixed length of [section 3](#3-the-grid) is what lets the two be told
apart on sight.

A cell beginning with `X` is reserved exactly as a code is
([section 9](#9-classification)), and every operation in this section MUST
reject one with `GPC_RESERVED`.

**A level outside 1 to 10 MUST be rejected**, and every operation here that
takes a level does so. How it is reported is left to the implementation, because
a level is an argument rather than a malformed code, and the two are not the
same kind of mistake. An implementation SHOULD raise whatever its language
already uses for an argument out of range: the reason code `GPC_LEVEL` where the
typed error can carry it, and the language's own argument error where it cannot.
The same licence extends to a coordinate outside the domain of
[section 2](#2-the-coordinate-domain), and to nothing else. Those two are
arguments; every other rejection in this specification is a judgement about a
code, and MUST arrive as a typed reason. A caller writing against more than one
implementation should not assume `GPC_LEVEL` is available everywhere.

### 18.2 Containment

`contains(cell, code)` is true when the code lies inside the cell. It is the
prefix test and nothing more:

```
contains(cell, code) = (cell == the first |cell| characters of code)
```

with both arguments normalised first. What
[section 10](#10-the-locality-guarantee) buys is that this is a true geometric
containment test rather than an approximation of one: no tolerance, no edge
case at a boundary, and no pair of points anywhere on Earth for which the
string answer and the geometric answer differ.

### 18.3 Neighbours

`neighbours(cell)` returns the cells sharing an edge or a corner with it, at the
same level, in this order:

```
north, north-east, east, south-east, south, south-west, west, north-west
```

Columns wrap: a cell against the antimeridian has neighbours on the other side
of it. Rows do not wrap, because the grid ends at the poles. A cell in the top
or bottom row therefore has five neighbours rather than eight, and the three
that would lie off the grid are **absent from the result** rather than present
and empty. The order of the entries that remain MUST be preserved.

With `p = 5^(10-k)`, `rowCells = 4 * 5^(k-1)` and `colCells = 6 * 5^(k-1)`, and
`(row, col)` from [section 6.1](#61-code-to-grid):

```
cellRow = row // p ; cellCol = col // p

for each (dRow, dCol) in the order above:
    r = cellRow + dRow
    if r < 0 or r >= rowCells: skip this one
    c = (cellCol + dCol + colCells) mod colCells
    emit the first k characters of the code for (r * p, c * p)
```

Crossing a seam is what this operation is for. Two cells either side of the
prime meridian share no characters at all ([section 16](#16-seams)), so no
amount of string arithmetic gets from one to the other. The grid indices do it
with no special case, which is why the rule is defined on them.

### 18.4 Cell dimensions

`cellDimensions(k)` returns the size of a level-k cell:

```
latitudeSpan  = 45 / 5^(k-1)                degrees
longitudeSpan = 60 / 5^(k-1)                degrees
northSouth    = latitudeSpan  * 111132.0    metres
eastWest      = longitudeSpan * 111319.49   metres, at the equator
```

The constants are those of [section 3](#3-the-grid). The north-south figure
holds at every latitude. The east-west figure is the value at the equator and
MUST be documented as such: it shrinks with the cosine of latitude, and this
format leaves that multiplication to the caller rather than taking a position
on which latitude is worth quoting.

All four values depend only on the level, so this takes a level and not a cell.

### 18.5 Distance

`distance(a, b)` returns the great-circle distance in metres between the centres
of two cells, which MAY be of different levels.

The centre of a level-k cell is exact in units of 1e-8 degrees, so it is
computed there and divided once:

```
p = 5^(10-k)
latitudeE8  = (2 * cellRow + 1) * p * 1152
longitudeE8 = (2 * cellCol + 1) * p * 1536

latitude  = latitudeE8  / 100000000 - 90
longitude = longitudeE8 / 100000000 - 180
```

The distance is the haversine formula on a sphere of radius 6,371,008.8 m, the
mean radius of the WGS 84 ellipsoid, evaluated in exactly this shape:

```
phi1 = latitudeA * PI / 180
phi2 = latitudeB * PI / 180
dPhi = phi2 - phi1
dLambda = (longitudeB - longitudeA) * PI / 180

h = sin(dPhi / 2) * sin(dPhi / 2)
  + cos(phi1) * cos(phi2) * sin(dLambda / 2) * sin(dLambda / 2)

h = min(h, 1)
distance = 2 * 6371008.8 * asin(sqrt(h))
```

The clamp is not cosmetic. For two points near opposite ends of the Earth the
rounded sum can land a unit in the last place above 1, where `asin` is
undefined and returns NaN.

**This is the one operation in this document that is not bit-identical across
implementations.** No standard library computes `sin`, `cos`, `asin` or `sqrt`
to the correctly rounded result except by accident, and pinning the shape of the
expression removes every source of divergence except the last unit in the last
place of each call. The vectors for `distance` are therefore the only ones
asserted to a tolerance rather than to equality: implementations MUST agree
within one millimetre, which is some eleven orders of magnitude below the error
the spherical model itself carries against the ellipsoid.

A caller that needs a reproducible ordering of candidates by distance MUST rank
on grid indices, the way [section 15.3](#153-correction) does, and not on this.

### 18.6 Grid indices

`decodeToGrid(code)` returns the `(row, col)` of
[section 5.1](#51-coordinate-to-grid) for the cell a code names, validating its
argument the way `decode` does. It is the accessor for a caller building a
spatial structure of its own -- a tile index, a join key, a quadtree -- who
wants the integers rather than degrees rounded to six places.

For a cell of k characters the corresponding indices are those of its
south-west corner, `cellRow * 5^(10-k)` and `cellCol * 5^(10-k)`.

---

## 19. Coordinate conversions

Two textual forms for coordinates, so that a caller reading off a survey sheet
or writing a link does not have to carry a parser of its own. Neither is part
of the code format and an implementation MAY omit both. One that offers them
MUST follow the rules here, because the vectors assert the strings exactly.

### 19.1 Degrees, minutes and seconds

`toDMS(latitude, longitude)` returns

```
43°39'00.02"N, 79°22'48.01"W
```

Each axis is built as follows, in integers after the first line:

```
u = floor(|value| * 360000 + 0.5)     hundredths of a second
degrees = u // 360000
minutes = (u // 6000) % 60
seconds = u % 6000                    still in hundredths
```

Rounding the whole value once, before splitting it, is what carries 59.999
seconds into the next minute with no special case anywhere.

The text is the degrees, `°`, the minutes padded to two digits, `'`, the
integer part of the seconds padded to two digits, `.`, its two fractional
digits, `"`, and then the hemisphere: `N` when the latitude is not negative and
`S` when it is, `E` when the longitude is not negative and `W` when it is. Only
the degrees are unpadded. The two axes are joined by a comma and one space,
latitude first.

Negative zero is not negative here: `-0.0` gives `N` and `E`, consistent with
[section 2](#2-the-coordinate-domain), where all four signed zeroes name the
origin.

`fromDMS(text)` accepts that form and a wider one. Each axis is

```
[ sign ] degrees ( ° | d ) [ minutes ( ' | m ) [ seconds ( " | s ) ] ] [ hemisphere ]
```

with any amount of ASCII whitespace between the pieces and none of it required.
The two axes are separated by a comma, or by whitespace alone.

The unit marker after the degrees is **required**. It is what tells one axis
from the next when no comma separates them, and without it `43 39` has two
readings. The marker after the minutes is required whenever minutes are
present, and likewise for the seconds.

Degrees and minutes are digits. Only the seconds may carry a decimal point, so
that every accepted string has one reading. A hemisphere letter is `N`, `S`,
`E` or `W` in either case, MUST NOT appear together with a sign, and MUST match
its axis. When neither axis carries one, position decides: latitude first.
Minutes or seconds of 60 or more are rejected, as is anything outside the
domain of [section 2](#2-the-coordinate-domain).

The value is assembled in exactly this shape, the only floating-point
arithmetic in this section:

```
value = sign * (degrees + (minutes + seconds / 60) / 60)
```

**This form is lossy and MUST be documented as such.** A hundredth of a second
is 0.309 m of latitude, so writing an arbitrary coordinate out and reading it
back moves it by up to 0.155 m, which is six per cent of a cell.

A code survives the trip all the same, and the reason is worth stating.
[Section 6.2](#62-grid-to-coordinates) returns the *centre* of a cell, which
lies 0.00001152 degrees from the nearest boundary in latitude -- eight times the
worst this conversion can do. Encoding what `fromDMS` returns therefore gives
back the code that was decoded, over 100,000 codes without exception. What the
rounding costs is the sixth decimal place of a coordinate, never the cell.

Degrees, minutes and seconds are for a person to read. The exact interchange
form is [19.2](#192-geo-uris), which carries all six decimal places.

### 19.2 geo: URIs

`toGeoURI(latitude, longitude)` returns an RFC 5870 URI in its simplest form:

```
geo:43.650006,-79.380004
```

Each coordinate is written with at most six decimal places: multiply by
1,000,000, round half away from zero to an integer, and write it back with a
decimal point six digits from the right. Trailing zeros are dropped, and the
decimal point with them when nothing follows it, so 43.65 is written `43.65`
and not `43.650000`. Negative zero is written `0`.

Six places is exactly what [section 6.2](#62-grid-to-coordinates) returns, and
resolves 0.11 m of latitude, which is finer than a cell.

`fromGeoURI(text)` accepts `geo:` in either case followed by two coordinates
separated by a comma. A third coordinate MAY follow; it is an altitude and is
discarded. Parameters MAY follow after `;` and are ignored, except that a `crs`
parameter MUST be rejected unless its value is `wgs84` in either case, since
this format is defined on WGS 84 alone. Coordinates outside the domain of
[section 2](#2-the-coordinate-domain) are rejected.

---

## 20. Conformance

An implementation is conformant if it satisfies every MUST above and reproduces
the shared conformance vectors. The vectors are part of this specification, not
an implementation detail: they live in [`test_data/`](test_data/), every port
reads the same bytes, and a disagreement between languages fails a test rather
than reaching a release.

The file conventions of [`test_data/README.md`](test_data/README.md) carry over
unchanged: UTF-8, LF endings, `#` for comments, blank lines ignored, commas
between fields, and any field that may itself contain a separator placed last.

Version 2 vectors are held in files parallel to the version 1 ones:

| File | Fields | Asserts |
| --- | --- | --- |
| `v2_encoding.csv` | `latitude,longitude,code` | The encoder produces exactly this string |
| `v2_decoding.csv` | `code,latitude,longitude` | Decoding is exact; equality, not tolerance |
| `v2_area.csv` | `code,south,west,north,east` | `decodeToArea` boundaries |
| `v2_classify.csv` | `class,message,input` | `GEOMETRIC`, `RESERVED` or `INVALID`, and the reason code |
| `v2_short.csv` | `short,refLatitude,refLongitude,code` | Short-form recovery, including cases across the antimeridian |
| `v2_check.csv` | `code,check` | The GF(25) check character |
| `v2_corrections.csv` | `level,refLatitude,refLongitude,input,candidates` | The ordered candidate list, joined by spaces |
| `v2_integer.csv` | `code,value` | The 48-bit integer form, both directions |
| `v2_cells.csv` | `level,code,cell,neighbours` | `cell` and `neighbours`, joined by spaces, five entries at the poles |
| `v2_distance.csv` | `a,b,metres` | `distance`, **to one millimetre** and not to equality |
| `v2_geo.csv` | `latitude,longitude,uri` | `toGeoURI`, and `fromGeoURI` reading it back |
| `v2_dms.csv` | `latitude,longitude,dms` | `toDMS`, and `fromDMS` reading it back |
| `v2_screen_list.csv` | `version,count,digest` | Every port carries the same advisory list |
| `v2_screen.csv` | `code,spans` | `screen`, as `position:length` joined by spaces |
| `v2_sample.csv` | `count,seed,digest` | A generated hundred-thousand-point sample, hashed |

`v2_sample.csv` follows the existing generated-sample design rather than
committing a large corpus: every port walks the same linear congruential
sequence, encodes every point, and compares one SHA-256 of the codes joined by
LF. A port that reproduces the digest agrees with the others byte for byte.
`v2_screen_list.csv` does the same job for the advisory list of
[section 17](#17-advisory-screening-non-normative), which every port embeds a
copy of: one row naming the version, the number of entries and their digest.

Every file above asserts equality except `v2_distance.csv`, for the reason given
in [18.5](#185-distance). A port that compares those figures for equality will
pass on the machine it was written on and fail somewhere else.

The edge-case corpus MUST include, at minimum: both poles; both ends of the
antimeridian; negative zero in each axis independently; coordinates one unit in
the last place either side of a level-1 boundary; the value `179.99999999999999`,
which is exactly 180 once stored as a double; and at least one code per class
for `classify`.

Vectors are append-only in spirit. Changing an existing expected value means
the format changed, which is a breaking change and needs a major version rather
than a quiet vector update.

---

## Appendix A — Reference implementation

The complete core, encoder and decoder, in one page. Everything else in this
document is either a rule about how to evaluate these lines, or an operation
built on top of them.

```
ALPHABET = "0123456789CDFGHJKLMNPRTWX"

# ---- encode -------------------------------------------------------------

function encode(latitude, longitude) -> string:

    reject unless latitude and longitude are finite
    reject unless -90.0 <= latitude <= 90.0
    reject unless -180.0 <= longitude <= 180.0

    if longitude == 180.0:
        longitude = -180.0

    row = floor((latitude  +  90.0) *  7812500.0 / 180.0)     # left to right,
    col = floor((longitude + 180.0) * 11718750.0 / 360.0)     # three operations

    if row < 0: row = 0
    if row > 7812499: row = 7812499
    if col < 0: col = 0
    if col > 11718749: col = 11718749

    out = ""
    r1 = row // 1953125                    # 5^9
    c1 = col // 1953125
    if r1 % 2 == 0: k = c1 else: k = 5 - c1
    out += ALPHABET[r1 * 6 + k]

    sr = r1
    sc = c1
    p  = 1953125
    for level in 2 .. 10:
        if level == 6:
            sr = 0
            sc = 0
        p = p // 5
        r = (row // p) % 5
        c = (col // p) % 5
        if sc % 2 == 0: R = r else: R = 4 - r
        sr = sr + r
        if sr % 2 == 0: C = c else: C = 4 - c
        sc = sc + c
        out += ALPHABET[R * 5 + C]

    return out

# ---- decode -------------------------------------------------------------

function decode(text) -> (latitude, longitude):

    code = normalise(text)                 # section 8
    if classify(code) != GEOMETRIC:        # section 9
        raise the matching typed error

    i  = index of code[1] in ALPHABET
    r1 = i // 6
    k  = i % 6
    if r1 % 2 == 0: c1 = k else: c1 = 5 - k

    row = r1
    col = c1
    sr  = r1
    sc  = c1
    for level in 2 .. 10:
        if level == 6:
            sr = 0
            sc = 0
        j = index of code[level] in ALPHABET
        R = j // 5
        C = j % 5
        if sc % 2 == 0: r = R else: r = 4 - R
        sr = sr + r
        if sr % 2 == 0: c = C else: c = 4 - C
        sc = sc + c
        row = row * 5 + r
        col = col * 5 + c

    latE8 = (2 * row + 1) * 1152 -  9000000000
    lngE8 = (2 * col + 1) * 1536 - 18000000000

    return (round6(latE8), round6(lngE8))

function round6(v):                        # exact; ties are unreachable
    q = abs(v) // 100
    if abs(v) % 100 >= 50: q = q + 1
    if v < 0: q = -q
    return q / 1000000
```

This page was transcribed into a working implementation without reference to
any other source, and that transcription is kept in the repository as
[`reference/from_spec.py`](reference/from_spec.py) precisely so it can go on
disagreeing. `reference/verify.py` holds the two against each other over
200,000 random coordinates plus every edge case of
[section 2](#2-the-coordinate-domain): identical codes and identical decoded
coordinates throughout, and `encode(decode(code))` returns the original code
every time.

If the transcription and the reference implementation ever disagree, it is this
document that needs correcting, not the transcription.

---

## Appendix B — Decoding version 1 (optional)

Nothing in this appendix is required. An implementation may support the version
2 core alone and be fully conformant. It exists so that a library carrying both
can resolve every code ever issued, and so that a new port can add that ability
later without reverse-engineering it.

Version 1 codes are **eleven** characters. Since version 2 codes are always
ten, `decode` MAY dispatch on length after separators are stripped: ten is
version 2, eleven is version 1. `encode` MUST NOT emit version 1.

### B.1 The format

| | |
| --- | --- |
| Alphabet | `CDFGHJKLMNPRTVWXY0123456789`, base 27, letters first |
| Length | 11 characters, presented `#XXXX-XXXX-XXX` |
| Precision | Five decimal places of latitude and longitude, about 1.11 m |
| Offset | `205881132094649`, added on encode so every code is exactly 11 characters |
| Point range | `10000000000` to `648009999999999` inclusive |

Note that the alphabet includes `V` and `Y`, which version 2 excludes, and that
it is letters-first, so it is **not** ASCII-ordered. Version 1 codes carry no
locality guarantee of any kind: two codes sharing four characters can be 19,874
km apart.

### B.2 Decoding

```
1.  Upper-case, then remove '#', '-' and whitespace.
2.  Reject unless the length is 11 and every character is in the v1 alphabet.
3.  point = the base-27 value of the 11 characters
4.  point = point - 205881132094649
5.  Reject unless 10000000000 <= point <= 648009999999999
6.  index      = point // 10^10
    fractional = point - index * 10^10
7.  (tLat, tLong) = combination pair at (index - 1)          # see B.3
8.  latSign  = -1 if tLat is odd else +1
    latWhole = (tLat - 1) / 2 if tLat is odd else tLat / 2
    longSign, longWhole: the same from tLong
9.  The ten decimal digits of `fractional` alternate, latitude first:
    digits 1, 3, 5, 7, 9 are latitude's five decimals, most significant first;
    digits 2, 4, 6, 8, 10 are longitude's.
10. latitude  = latSign  * (latWhole  + latDecimals  / 100000)
    longitude = longSign * (longWhole + longDecimals / 100000)
```

Version 1 returns the **corner** of its cell, not the centre. This differs from
version 2 by design, and an implementation MUST NOT change it: the value
returned is the one every version 1 release has returned.

### B.3 The combination pair

Step 7 is the only part that is not plain arithmetic. It inverts a bijection
between an index and a pair `(a, b)` drawn from a 180 by 360 table of whole
degrees of latitude and longitude, each doubled and offset by one for the sign,
which is why `tLat` runs to 180 and `tLong` to 360.

The pairs are enumerated in order of increasing `a + b`, so the traversal
sweeps anti-diagonals. The inverse is closed-form in three ranges: an opening
triangle where the diagonals grow, a middle band where they are all the same
length, and a closing triangle where they shrink. Each range recovers the
diagonal from the index with a square root and then the position along it by
subtraction.

The normative reference for this appendix is the vendored implementation
carried by all four ports, for example
[`python/src/gridpointcode_algo_pranavpatel_ca/table.py`](python/src/gridpointcode_algo_pranavpatel_ca/table.py).
It is about 150 lines, is Apache-2.0 like the rest of this project, and has no
dependencies of its own. A port adding version 1 support SHOULD translate it
rather than re-derive it, and MUST check the result against the version 1
conformance vectors in [`test_data/`](test_data/).

---

## Appendix C — The reserved namespace

The level-1 map of [5.2](#52-grid-to-code) computes `r1 * 6 + k` with `r1` in
0 to 3 and `k` in 0 to 5. It therefore produces the indices 0 to 23 and never
24. Index 24 is `X`.

**No encoded code begins with `X`**, so the 25⁹ = 3,814,697,265,625 strings that
do are unreachable by encoding. This specification claims them as a reserved
namespace.

Their treatment is fixed now, before anything uses them, because it cannot be
added later without breaking callers:

* `classify` MUST return `RESERVED` for a well-formed ten-character string
  beginning with `X`.
* `decode` MUST raise a typed `GPC_RESERVED` error, distinct from every invalid
  reason.
* `isValid` MUST return false, since a reserved code names no cell.
* No meaning is assigned to a reserved code by this specification, and none
  will be assigned by accident. An implementation that gives a reserved code a
  private meaning does so at its own risk: it MUST NOT expect any other party to
  understand that meaning, and it MUST NOT present such a code as though it
  named a location.

In the integer form of [section 13](#13-the-integer-form), reserved codes are
exactly the values at or above 24 × 25⁹ = 91,552,734,375,000, so a single
comparison separates them without parsing.

A reserved code is well-formed and merely unassigned. Treating it as a typing
error would be wrong, and a caller that cannot tell the two apart today cannot
be taught the difference tomorrow.

This is deliberately neither a grant nor a prohibition. Saying that anyone may
use the space would be a promise that cannot be withdrawn once made, and saying
that nobody may would be a rule with no way to enforce it and no reason behind
it. What the space is for is a question this specification leaves open, and the
rules above are what keep it answerable later: a reserved code never decodes,
never validates, and never passes for a location, whoever is using it and
whatever they have decided it means.

---

## Appendix D — Sharing a code (non-normative)

Nothing here is required for conformance and none of it constrains an
implementation. The sections above define four ways to write a location down;
this one is about what happens after a code leaves the software: which form to
hand to somebody, and how to say one out loud.

### D.1 Which form to share

**The ten characters are the form of record.** `#G3RJM-98NM9` is what a share
button emits, what a record stores and what a label prints. It is the same
length everywhere, it resolves on its own, and it needs nothing at the far end.
Share it formatted: the group separator is what stops a reader losing their
place in the middle of ten symbols.

**Add the check character wherever a person is in the path.** A code that will
be read aloud, written by hand, dictated down a telephone or typed from a
printed sign should be shared in the check form,
`#G3RJM-98NM9*T` ([section 14](#14-the-check-character-optional)). It costs one
character and detects every single-symbol error and every adjacent
transposition, which are the two things a person does.
[14.6](#146-emitting-it) produces it in one call, and
[section 8](#8-parsing-and-normalisation) means a reader who omits the check
character loses nothing but the detection.

**The short form is for two people in the same place.** The five characters of
[section 12](#12-the-short-form) resolve only against a reference near the true
point: a sign at the entrance to a village, a notice on a door, one person
telling another where to meet while both are standing in the same district. It
is the wrong thing to put behind a share button, in a message or in a stored
record, because none of those knows where the far end will be when it reads
them, and a short form resolved against a distant reference does not fail. It
returns a plausible location 8 or 10 km away
([12.3](#123-what-recovery-guarantees)).

**The integer form is for machines.** [Section 13](#13-the-integer-form) is a
database key, a QR or NFC payload, a sort key. It is not a thing to show a
person and not a thing to have one retype.

| Where it is going | Form | |
| --- | --- | --- |
| A share button, a message, a record, a label | `#G3RJM-98NM9` | complete on its own |
| Voice, radio, paper, anything dictated | `#G3RJM-98NM9*T` | one character buys detection |
| A sign in one place, two people in that place | `98NM9` | only resolves near the point |
| A key, a payload, a sort | six bytes | not for a person |

Whichever form is shared, [15.2](#152-normative-guidance) still applies at the
far end: nearly 29 % of single-character typos produce a code that is valid and
somewhere else, so a decoded point has to be shown on a map or checked against
something the reader recognises before it is acted on.

### D.2 Phonetic callouts

The alphabet of [section 4](#4-the-alphabet) was chosen to exclude vowels, so
that a code cannot spell a word, and to exclude the shapes a reader confuses on
paper. It was not chosen for phonetic distinctness, and it cannot be now
without changing the format: read out in English, `C`, `D`, `G`, `P`, `T` and
the digit `3` all rhyme.

The alias table of [section 8](#8-parsing-and-normalisation) is no help here.
That table repairs a reader who typed a letter the alphabet does not contain,
`O` for `0` or `S` for `5`, and every symbol in the rhyming set above is a real
symbol. A listener who hears `D` where `T` was said writes down a code that
parses, validates and decodes to somewhere else. The check character is what
detects that. Callouts are what avoid it.

**The rule is one line: a callout for a symbol is any word beginning with that
symbol.** The listener keeps the first character and discards the rest. Nothing
has to be memorised or agreed in advance, and a speaker who cannot remember the
word can invent one mid-sentence and still be understood.

**Digits are spoken as the number**, not as a callout word, because no word
begins with a seven.

**An application supplies the words.** The table below is a reference, not a
requirement, and it is English. Words that are unmistakable in one region are
not in another, and an application serving a particular place should replace
them with words its users already say. Replacing one word does not break
anything, because the rule is the first character and not the word.

| Symbol | Callout | Symbol | Callout | Symbol | Callout |
| --- | --- | --- | --- | --- | --- |
| `C` | Charlie | `J` | Juliett | `P` | Papa |
| `D` | Delta | `K` | Kilo | `R` | Romeo |
| `F` | Foxtrot | `L` | Lima | `T` | Tango |
| `G` | Golf | `M` | Mike | `W` | Whiskey |
| `H` | Hotel | `N` | November | `X` | X-ray |

Those are the words of the international radiotelephony spelling alphabet,
which has the longest record of any set of being understood over a bad
connection. `0` to `9` are spoken as themselves.

So `#G3RJM-98NM9*T` is:

```
Golf, three, Romeo, Juliett, Mike; nine, eight, November, Mike, nine; check Tango
```

The group boundary is worth a pause, and the check character is worth naming as
one rather than reading it as an eleventh symbol, so that a listener who is
writing knows where it goes.

**Outside the Latin script the rule does not hold.** A listener reading a
language written in another script has no first letter to take, so an
application serving one has to supply an explicit table pairing each symbol
with a word, and the listener learns the pairing instead of deriving it. The
same is true of any language whose words do not begin with these letters often
enough to give a speaker a choice.

**No port provides this.** An emitter would have to carry words, and words are
a language: the table above is English, and shipping it as an API would make
every caller's spoken procedure English whether or not anybody in the room
speaks it. The rule is one line and the table is fifteen rows, which is small
enough to be an application's own.
