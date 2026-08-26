#  Copyright 2026 Pranavkumar Patel
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

"""Checks every exact claim SPEC.md makes. Exits non-zero on the first failure.

    python reference/verify.py

These are the assertions that must hold for any conforming implementation:
constants, worked examples, the structural properties, and the round-trips. The
statistical figures live in measure.py, because they take minutes rather than
seconds.
"""

import math
import random
import sys

import from_spec
import geodesy
import gpc2 as g

FAILURES = []


def check(name, got, want):
    if got != want:
        FAILURES.append((name, got, want))
        print("FAIL  %-46s got %r, want %r" % (name, got, want))
    else:
        print("ok    %s" % name)


def section(title):
    print("\n-- %s" % title)


def main():
    section("constants, section 3 and 13")
    check("ROWS", g.ROWS, 7_812_500)
    check("COLS", g.COLS, 11_718_750)
    check("alphabet size", len(g.ALPHABET), 25)
    check("alphabet is ASCII-ascending", list(g.ALPHABET), sorted(g.ALPHABET))
    check("alphabet has no duplicates", len(set(g.ALPHABET)), 25)
    check("no vowel in the alphabet",
          [v for v in "AEIOUY" if v in g.ALPHABET], [])
    check("integer form needs 47 bits", (25 ** 10 - 1).bit_length(), 47)
    check("integer form fits 48 bits", 25 ** 10 < 2 ** 48, True)
    check("reserved namespace size", 25 ** 9, 3_814_697_265_625)
    check("reserved integer floor", 24 * 25 ** 9, 91_552_734_375_000)
    check("full traversal steps", g.ROWS * g.COLS - 1, 91_552_734_374_999)
    check("level-5 transitions", g.R5 * g.C5 - 1, 9_374_999)
    check("half a level-5 cell, rows", g.P5 // 2, 1562)
    check("half-cell latitude bound",
          round(1562 * 180.0 / g.ROWS, 8), 0.03598848)
    check("half-cell longitude bound",
          round(1562 * 360.0 / g.COLS, 8), 0.04798464)

    section("the level-1 map, section 5.2 and Appendix C")
    used = sorted({r * 6 + (c if r % 2 == 0 else 5 - c)
                   for r in range(4) for c in range(6)})
    check("level 1 uses indices 0..23", used, list(range(24)))
    check("X is index 24", g.ALPHABET.index("X"), 24)

    section("rounding ties are unreachable, section 6.2")
    check("latitude residues mod 100 never 50",
          50 in {abs(g.lat_e8(r)) % 100 for r in range(2000)}, False)
    check("longitude residues mod 100 never 50",
          50 in {abs(g.lng_e8(c)) % 100 for c in range(2000)}, False)

    section("worked examples, section 5.5 and 6.4")
    samples = [
        ("Toronto", 43.65000, -79.38000, "#G3RJM-98NM9", "T"),
        ("CN Tower", 43.64260, -79.38710, "#G3RJM-0M6DX", "J"),
        ("Ahmedabad", 23.02250, 72.57140, "#KDC8X-JM49X", "D"),
        ("Sydney Opera House", -33.85680, 151.21530, "#6LK4X-NRP0R", "M"),
        ("Machu Picchu", -13.16310, -72.54500, "#C8HKC-13C80", "4"),
        ("Reykjavik", 64.14660, -21.94260, "#RDX9R-TN19T", "1"),
        ("North pole", 90.0, 0.0, "#P4444-PPPPP", "2"),
        ("South pole", -90.0, 0.0, "#3PPPP-00000", "K"),
        ("Antimeridian east", 0.0, 180.0, "#F0000-00000", "5"),
        ("Antimeridian west", 0.0, -180.0, "#F0000-00000", "5"),
        ("Origin", 0.0, 0.0, "#JPPPP-00000", "M"),
        ("Negative zero", -0.0, -0.0, "#JPPPP-00000", "M"),
    ]
    for name, la, lo, code, chk in samples:
        check("encode %s" % name, g.encode(la, lo), code)
        check("check character %s" % name,
              g.check_character(g.normalise(code)[0]), chk)
    for code, la, lo in [("#G3RJM-98NM9", 43.650006, -79.380004),
                         ("#KDC8X-JM49X", 23.022501, 72.571407),
                         ("#6LK4X-NRP0R", -33.856808, 151.215314),
                         ("#P4444-PPPPP", 89.999988, 0.000015),
                         ("#JPPPP-00000", 0.000012, 0.000015)]:
        check("decode %s" % code, g.decode(code), (la, lo))

    section("the coordinate domain, section 2")
    for la, lo, reason in [(90.1, 0, "LATITUDE"), (-90.1, 0, "LATITUDE"),
                           (0, 180.1, "LONGITUDE"), (0, -180.1, "LONGITUDE"),
                           (float("nan"), 0, "LATITUDE"),
                           (0, float("inf"), "LONGITUDE")]:
        try:
            g.encode(la, lo)
            check("reject (%r, %r)" % (la, lo), "accepted", reason)
        except g.GpcError as exc:
            check("reject (%r, %r)" % (la, lo), exc.reason, reason)

    section("classification and parsing, sections 8 and 9")
    for text, kind, reason in [
            ("#g3rjm-98nm9", g.GEOMETRIC, ""),
            ("  G3RJM 98NM9 ", g.GEOMETRIC, ""),
            ("G3RJMI8NM9", g.GEOMETRIC, ""),          # I aliases to 1
            ("XG3RJ98NM9", g.RESERVED, ""),
            ("", g.INVALID, "GPC_NULL"),
            ("   ", g.INVALID, "GPC_NULL"),
            (None, g.INVALID, "GPC_NULL"),
            ("TOOSHORT", g.INVALID, "GPC_LENGTH"),
            ("G3RJM98NMQ", g.INVALID, "GPC_CHAR"),
            ("G3RJM98NMU", g.INVALID, "GPC_CHAR"),
            ("G3RJM98NMY", g.INVALID, "GPC_CHAR"),
            # The check character is verified here too, not only in decode.
            ("#G3RJM-98NM9*T", g.GEOMETRIC, ""),
            ("#g3rjm-98nm9*t", g.GEOMETRIC, ""),
            ("XG3RJ98NM9*6", g.RESERVED, ""),
            ("#G3RJM-98NM9*5", g.INVALID, "GPC_CHECK"),
            ("#G3RJM-98NM9*", g.INVALID, "GPC_CHECK"),
            ("#G3RJM-98NM9*TT", g.INVALID, "GPC_CHECK"),
            ("#G3RJM-98NM9*Q", g.INVALID, "GPC_CHECK"),
            ("XG3RJ98NM9*T", g.INVALID, "GPC_CHECK")]:
        check("validate %r" % text, g.validate(text), (kind, reason))
        check("classify agrees %r" % text, g.classify(text), kind)
        check("is_valid agrees %r" % text, g.is_valid(text), kind == g.GEOMETRIC)
    check("L is never aliased", "L" in g.ALIASES, False)
    check("aliases cover every letter outside the alphabet",
          sorted(set(g.ALIASES) | set("QUY")),
          sorted(set("ABCDEFGHIJKLMNOPQRSTUVWXYZ") - set(g.ALPHABET)))
    check("normalisation is idempotent",
          g.normalise(g.normalise("#g3rjm-98nm9")[0])[0], "G3RJM98NM9")

    section("typed errors, section 9 and 14")
    for text, reason in [("XG3RJ98NM9", "GPC_RESERVED"),
                         ("#G3RJM-98NM9*5", "GPC_CHECK"),
                         ("G3RJM98NMQ", "GPC_CHAR"),
                         ("", "GPC_NULL")]:
        try:
            g.decode(text)
            check("decode %r raises" % text, "accepted", reason)
        except g.GpcError as exc:
            check("decode %r raises" % text, exc.reason, reason)
    check("decode accepts a correct check character",
          g.decode("#G3RJM-98NM9*T"), (43.650006, -79.380004))

    section("the check character, section 14")
    powers, x = [], 1
    for _ in range(24):
        x = g.gf_mul(x, g.T)
        powers.append(x)
    check("t is primitive: its powers cover every non-zero element",
          sorted(powers), list(range(1, 25)))
    check("t has order 24, not less", powers.index(1) + 1, 24)
    check("t^12 = -1", g.gf_add(powers[11], 1), 0)
    check("every non-zero element is invertible",
          all(any(g.gf_mul(a, b) == 1 for b in range(1, 25)) for a in range(1, 25)),
          True)
    check("weights t^1..t^11", g.WEIGHTS, [5, 23, 22, 17, 24, 2, 10, 16, 19, 9, 18])
    check("weights are distinct and non-zero",
          sorted(set(g.WEIGHTS)) == sorted(g.WEIGHTS) and 0 not in g.WEIGHTS, True)
    rng = random.Random(4242)
    missed_sub = missed_tr = n_sub = n_tr = 0
    for _ in range(4000):
        code = g.encode(rng.uniform(-90, 90), rng.uniform(-180, 180), False)
        full = code + g.check_character(code)
        for p in range(11):
            for ch in g.ALPHABET:
                if ch == full[p]:
                    continue
                n_sub += 1
                bad = full[:p] + ch + full[p + 1:]
                if g.check_holds(bad[:10], bad[10]):
                    missed_sub += 1
        for p in range(10):
            if full[p] == full[p + 1]:
                continue
            n_tr += 1
            bad = full[:p] + full[p + 1] + full[p] + full[p + 2:]
            if g.check_holds(bad[:10], bad[10]):
                missed_tr += 1
    check("every single-symbol error detected (%d tested)" % n_sub, missed_sub, 0)
    check("every adjacent transposition detected (%d tested)" % n_tr, missed_tr, 0)

    section("candidate generation, section 15.3")
    check("candidates, no repeats", len(g.candidates("G3RJM98NM9")), 249)
    check("candidates with repeats", len(g.candidates("P4444PPPPP")), 242)
    check("candidates are all distinct",
          len(set(g.candidates("G3RJM98NM9"))), 249)

    section("round-trips and bijection, 200,000 cells")
    rng = random.Random(20260825)
    bad_grid = bad_trip = bad_int = bad_spec = 0
    for _ in range(200_000):
        row = rng.randrange(g.ROWS)
        col = rng.randrange(g.COLS)
        code = g.grid_to_code(row, col)
        if g.code_to_grid(code) != (row, col):
            bad_grid += 1
        la, lo = g.decode(code)
        if g.encode(la, lo, False) != code:
            bad_trip += 1
        if g.from_integer(g.to_integer(code)) != code:
            bad_int += 1
    check("grid -> code -> grid", bad_grid, 0)
    check("decode -> encode returns the code", bad_trip, 0)
    check("integer form round-trips", bad_int, 0)

    section("the specification stands alone, Appendix A")
    rng = random.Random(424242)
    for _ in range(200_000):
        la = rng.uniform(-90, 90)
        lo = rng.uniform(-180, 180)
        code = from_spec.encode(la, lo)
        if code != g.encode(la, lo, False) or from_spec.decode(code) != g.decode(code):
            bad_spec += 1
    check("Appendix A transcription agrees, 200,000 coordinates", bad_spec, 0)
    edges = [(90, 0), (-90, 0), (0, 180), (0, -180), (0, 0), (-0.0, -0.0),
             (89.9999999999999, 0), (-90, -180), (90, 180), (45, 60),
             (-45, -60), (89.99999, 179.99999)]
    check("Appendix A transcription agrees on the edge cases",
          [e for e in edges
           if from_spec.encode(*e) != g.encode(e[0], e[1], False)], [])

    section("containment, section 10.1")
    rng = random.Random(23)
    violations = 0
    for _ in range(60_000):
        a = (rng.randrange(g.ROWS), rng.randrange(g.COLS))
        b = (rng.randrange(g.ROWS), rng.randrange(g.COLS))
        ca, cb = g.grid_to_code(*a), g.grid_to_code(*b)
        shared = 0
        for x, y in zip(ca, cb):
            if x != y:
                break
            shared += 1
        for k in range(1, 11):
            p = 5 ** (10 - k)
            same_cell = a[0] // p == b[0] // p and a[1] // p == b[1] // p
            if same_cell != (shared >= k):
                violations += 1
    check("containment holds, 600,000 checks", violations, 0)

    section("string order is spatial order, section 11.1")
    rng = random.Random(17)
    pairs = sorted((g.grid_to_code(rng.randrange(g.ROWS), rng.randrange(g.COLS)))
                   for _ in range(50_000))
    check("string sort equals integer sort",
          all(g.to_integer(pairs[i]) < g.to_integer(pairs[i + 1])
              for i in range(len(pairs) - 1)), True)
    check("every geometric code is below the reserved floor",
          max(g.to_integer(c) for c in pairs) < 24 * 25 ** 9, True)

    section("the short form, section 12")
    rng = random.Random(99)
    bad_short = trials = 0
    while trials < 60_000:
        la, lo = rng.uniform(-89.9, 89.9), rng.uniform(-180, 180)
        code = g.encode(la, lo, False)
        ref_la = la + rng.uniform(-0.0359, 0.0359)
        ref_lo = lo + rng.uniform(-0.0479, 0.0479)
        if abs(ref_la) > 90 or abs(ref_lo) > 180:
            continue
        trials += 1
        if g.recover_short(g.shorten(code), ref_la, ref_lo) != code:
            bad_short += 1
    check("short-form recovery inside half a cell, 60,000 trials", bad_short, 0)

    section("the tail is self-contained, section 5.3")
    rng = random.Random(31)
    seen = {}
    consistent = True
    for _ in range(120_000):
        row, col = rng.randrange(g.ROWS), rng.randrange(g.COLS)
        key = (row % g.P5, col % g.P5)
        tail = g.grid_to_code(row, col)[5:]
        if seen.setdefault(key, tail) != tail:
            consistent = False
    check("the tail depends only on the within-cell offset", consistent, True)
    check("read_tail inverts the tail",
          g.read_tail(g.grid_to_code(*g.to_grid(43.65, -79.38))[5:]),
          (g.to_grid(43.65, -79.38)[0] % g.P5, g.to_grid(43.65, -79.38)[1] % g.P5))

    section("cells and containment, sections 18.1 and 18.2")
    check("cell of a code", g.cell("#G3RJM-98NM9", 3), "G3R")
    check("cell normalises first", g.cell("#g3rjm-98nm9", 5), "G3RJM")
    check("cell reads confusable letters", g.cell("G3RJMI8NM9", 6), "G3RJM1")
    check("cell of a cell", g.cell("G3RJM", 2), "G3")
    check("a cell contains its own code",
          g.contains("G3RJM", "G3RJM98NM9"), True)
    check("a cell does not contain its neighbour",
          g.contains("G3RJM", "G3RJD98NM9"), False)
    check("containment is transitive through levels",
          g.contains("G3R", "G3RJM") and g.contains("G3RJM", "G3RJM98NM9"), True)
    for text, reason in [("XG3RJ", "GPC_RESERVED"),
                         ("", "GPC_NULL"),
                         ("G3RJM98NM99", "GPC_LENGTH"),
                         ("G3RJQ", "GPC_CHAR")]:
        try:
            g.neighbours(text)
            check("neighbours %r raises" % text, "accepted", reason)
        except g.GpcError as exc:
            check("neighbours %r raises" % text, exc.reason, reason)
    for level in (0, 11, -1):
        try:
            g.cell("G3RJM98NM9", level)
            check("cell at level %d raises" % level, "accepted", "GPC_LEVEL")
        except g.GpcError as exc:
            check("cell at level %d raises" % level, exc.reason, "GPC_LEVEL")

    rng = random.Random(1801)
    bad_contains = 0
    for _ in range(40_000):
        a = (rng.randrange(g.ROWS), rng.randrange(g.COLS))
        b = (rng.randrange(g.ROWS), rng.randrange(g.COLS))
        if rng.random() < 0.5:                   # half the pairs close together
            b = (min(g.ROWS - 1, a[0] + rng.randrange(4000)),
                 min(g.COLS - 1, a[1] + rng.randrange(4000)))
        ca, cb = g.grid_to_code(*a), g.grid_to_code(*b)
        k = rng.randrange(1, 11)
        p = 5 ** (10 - k)
        same = a[0] // p == b[0] // p and a[1] // p == b[1] // p
        if g.contains(g.cell(ca, k), cb) != same:
            bad_contains += 1
    check("contains agrees with the grid, 40,000 pairs", bad_contains, 0)

    section("neighbours, section 18.3")
    check("eight neighbours inland", len(g.neighbours("G3RJM98NM9")), 8)
    check("five at the top row", len(g.neighbours("#P4444-PPPPP")), 5)
    check("five at the bottom row", len(g.neighbours("#3PPPP-00000")), 5)
    check("eight at level 1 away from the poles",
          len(g.neighbours("G")), 8)
    # Level 1 rows 0 and 3 are the polar rows, so every cell in them has five.
    check("five at level 1 in the polar row", len(g.neighbours("0")), 5)
    # The first column of the grid: its western neighbour is the last column,
    # on the far side of the antimeridian.
    row, col = g.to_grid(0.0, -180.0)
    check("columns wrap at the antimeridian",
          g.neighbours(g.grid_to_code(row, col))[6],
          g.grid_to_code(row, g.COLS - 1))
    rng = random.Random(1803)
    bad_adjacent = bad_distinct = bad_order = 0
    for _ in range(20_000):
        k = rng.randrange(1, 11)
        p = 5 ** (10 - k)
        row_cells, col_cells = 4 * 5 ** (k - 1), 6 * 5 ** (k - 1)
        code = g.grid_to_code(rng.randrange(g.ROWS), rng.randrange(g.COLS))
        here = g.cell(code, k)
        _, _, cell_row, cell_col = g._cell_grid(here)
        got = g.neighbours(here)
        expected = 8 if 0 < cell_row < row_cells - 1 else 5
        if len(got) != expected:
            bad_order += 1
        if len(set(got)) != len(got) or here in got:
            bad_distinct += 1
        for neighbour in got:
            _, _, n_row, n_col = g._cell_grid(neighbour)
            d_col = (n_col - cell_col + col_cells) % col_cells
            if d_col > col_cells // 2:
                d_col -= col_cells
            if abs(n_row - cell_row) > 1 or abs(d_col) > 1:
                bad_adjacent += 1
    check("every neighbour is one cell away, 20,000 cells", bad_adjacent, 0)
    check("neighbours are distinct and exclude the cell", bad_distinct, 0)
    check("the count is eight inland and five in a polar row", bad_order, 0)

    section("cell dimensions, section 18.4")
    check("level 1 spans", g.cell_dimensions(1)[:2], (45.0, 60.0))
    check("level 10 spans",
          [round(v, 8) for v in g.cell_dimensions(10)[:2]], [2.304e-05, 3.072e-05])
    check("level 1 in kilometres",
          [round(v / 1000, 1) for v in g.cell_dimensions(1)[2:]], [5000.9, 6679.2])
    check("level 5 in kilometres",
          [round(v / 1000, 1) for v in g.cell_dimensions(5)[2:]], [8.0, 10.7])
    check("level 10 in metres",
          [round(v, 1) for v in g.cell_dimensions(10)[2:]], [2.6, 3.4])
    check("the aspect ratio is 0.75 at every level",
          {round(g.cell_dimensions(k)[0] / g.cell_dimensions(k)[1], 12)
           for k in range(1, 11)}, {0.75})

    section("distance, section 18.5")
    check("zero to itself", g.distance("G3RJM98NM9", "G3RJM98NM9"), 0.0)
    check("symmetric",
          g.distance("G3RJM98NM9", "6LK4XNRP0R"),
          g.distance("6LK4XNRP0R", "G3RJM98NM9"))
    # geodesy.haversine is written differently -- radians(), a squaring
    # operator -- so agreeing with it to a millimetre is a second opinion and
    # not a restatement.
    check("agrees with an independently written haversine",
          abs(g.distance("#G3RJM-98NM9", "#6LK4X-NRP0R")
              - geodesy.haversine(g.cell_centre("#G3RJM-98NM9"),
                                  g.cell_centre("#6LK4X-NRP0R"))) < 0.001, True)
    check("pole to pole is half the meridian",
          round(g.distance("#P4444-PPPPP", "#3PPPP-00000") / 1000), 20015)
    check("antipodal points do not produce NaN",
          g.distance(g.encode(0.0, 0.0, False), g.encode(0.0, 180.0, False)) > 0,
          True)
    check("a cell and the code inside it are close",
          g.distance("G3RJM", "G3RJM98NM9") < 7000, True)

    section("grid indices, section 18.6")
    check("decodeToGrid inverts toGrid",
          g.decode_to_grid(g.encode(43.65, -79.38, False)),
          g.to_grid(43.65, -79.38))
    try:
        g.decode_to_grid("XG3RJ98NM9")
        check("decodeToGrid rejects a reserved code", "accepted", "GPC_RESERVED")
    except g.GpcError as exc:
        check("decodeToGrid rejects a reserved code", exc.reason, "GPC_RESERVED")

    section("degrees, minutes and seconds, section 19.1")
    check("worked example", g.to_dms(43.65, -79.38),
          "43°39'00.00\"N, 79°22'48.00\"W")
    check("the poles", g.to_dms(90.0, 0.0), "90°00'00.00\"N, 0°00'00.00\"E")
    check("negative zero is not negative",
          g.to_dms(-0.0, -0.0), "0°00'00.00\"N, 0°00'00.00\"E")
    check("seconds carry into the next minute",
          g.to_dms(1.0 - 1e-9, 0.0).split(",")[0], "1°00'00.00\"N")
    check("read back", g.from_dms("43°39'00.00\"N, 79°22'48.00\"W"),
          (43.65, -79.38))
    check("letters for the markers", g.from_dms("43d39m0s N 79d22m48s W"),
          (43.65, -79.38))
    check("signs instead of hemispheres", g.from_dms("-43°39', +79°22'"),
          (-43.65, 79.36666666666666))
    check("degrees alone", g.from_dms("43°N 79°W"), (43.0, -79.0))
    for text in ["43°39'00.00\"N",                      # one axis only
                 "43 39",                                     # no unit markers
                 "-43°N, 79°W",                     # sign and hemisphere
                 "43°W, 79°N",                      # axes crossed
                 "43°60'00.00\"N, 0°0'0\"E",        # sixty minutes
                 "43°39'60.00\"N, 0°0'0\"E",        # sixty seconds
                 "91°N, 0°E",                       # outside the domain
                 "43°39'N, 79°22'W extra"]:         # trailing text
        try:
            g.from_dms(text)
            check("fromDMS rejects %r" % text, "accepted", "an error")
        except g.GpcError:
            check("fromDMS rejects %r" % text, "an error", "an error")
    rng = random.Random(1901)
    worst = 0.0
    for _ in range(20_000):
        la, lo = rng.uniform(-90, 90), rng.uniform(-180, 180)
        back = g.from_dms(g.to_dms(la, lo))
        worst = max(worst, abs(back[0] - la), abs(back[1] - lo))
    check("the round trip stays within half a hundredth of a second",
          worst <= 0.5 / 360000 + 1e-12, True)
    rng = random.Random(1903)
    bad_dms = 0
    for _ in range(20_000):
        code = g.encode(rng.uniform(-90, 90), rng.uniform(-180, 180), False)
        if g.encode(*g.from_dms(g.to_dms(*g.decode(code))), False) != code:
            bad_dms += 1
    check("a decoded code survives the trip, 20,000 codes", bad_dms, 0)

    section("geo URIs, section 19.2")
    check("worked example", g.to_geo_uri(43.650006, -79.380004),
          "geo:43.650006,-79.380004")
    check("trailing zeros are dropped", g.to_geo_uri(43.65, -79.38),
          "geo:43.65,-79.38")
    check("and the point with them", g.to_geo_uri(43.0, -79.0), "geo:43,-79")
    check("negative zero is written 0", g.to_geo_uri(-0.0, -0.0), "geo:0,0")
    check("read back", g.from_geo_uri("geo:43.650006,-79.380004"),
          (43.650006, -79.380004))
    check("altitude is discarded", g.from_geo_uri("geo:43.65,-79.38,76.1"),
          (43.65, -79.38))
    check("parameters are ignored", g.from_geo_uri("geo:43.65,-79.38;u=35"),
          (43.65, -79.38))
    check("the scheme is case-insensitive", g.from_geo_uri("GEO:43.65,-79.38"),
          (43.65, -79.38))
    check("wgs84 is accepted", g.from_geo_uri("geo:43.65,-79.38;crs=WGS84"),
          (43.65, -79.38))
    for text in ["geo:43.65", "43.65,-79.38", "geo:43.65,-79.38;crs=nad83",
                 "geo:+43.65,-79.38", "geo:43.65,-79.38,1,2", "geo:1e2,0",
                 "geo:91,0", "geo:0,181"]:
        try:
            g.from_geo_uri(text)
            check("fromGeoURI rejects %r" % text, "accepted", "an error")
        except g.GpcError:
            check("fromGeoURI rejects %r" % text, "an error", "an error")
    rng = random.Random(1902)
    bad_uri = 0
    for _ in range(20_000):
        code = g.encode(rng.uniform(-90, 90), rng.uniform(-180, 180), False)
        if g.encode(*g.from_geo_uri(g.to_geo_uri(*g.decode(code))), False) != code:
            bad_uri += 1
    check("a geo URI round-trips to the same code, 20,000 codes", bad_uri, 0)

    section("advisory screening, section 17")
    check("every letter of the alphabet has a rule",
          sorted(g.SCREEN_LETTERS), list("abcdefghijklmnopqrstuvwxyz"))
    check("only the symbols of the alphabet appear on the right",
          sorted({s for v in g.SCREEN_LETTERS.values() for s in v}
                 - set(g.ALPHABET)), [])
    check("the letters with no symbol", sorted(k for k, v in
          g.SCREEN_LETTERS.items() if not v), ["q", "u", "v", "y"])
    check("expansion is the product of the choices",
          len(g.expand_word("gnat")), 6)
    check("expansion keeps the order",
          g.expand_word("gnat"),
          ["GN4T", "GN47", "6N4T", "6N47", "9N4T", "9N47"])
    check("a word with an unrepresentable letter is dropped",
          g.expand_word("quart"), [])
    check("a word below the floor is dropped", g.expand_word("cat"), [])
    check("the hash is eight lower-case hexadecimal characters",
          len(g.screen_hash("GN4T")) == 8
          and all(c in "0123456789abcdef" for c in g.screen_hash("GN4T")), True)
    planted = {g.screen_hash("CDFG"), g.screen_hash("N4TL")}
    check("a planted variant is found",
          g.screen("CDFGN4TL00", planted), [(1, 4), (5, 4)])
    check("a clean code matches nothing",
          g.screen("G3RJM98NM9", planted), [])
    check("a reserved code screens like any other",
          g.screen("XCDFG00000", {g.screen_hash("CDFG")}), [(2, 4)])
    check("overlapping spans are both reported",
          g.screen("CDFG000000", {g.screen_hash("CDFG"), g.screen_hash("DFG0")}),
          [(1, 4), (2, 4)])
    check("spans below the floor are never reported",
          g.screen("CDF0000000", {g.screen_hash("CDF")}), [])

    print()
    if FAILURES:
        print("%d FAILURES" % len(FAILURES))
        return 1
    print("all checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
