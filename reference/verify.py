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
            ("G3RJM98NMY", g.INVALID, "GPC_CHAR")]:
        check("validate %r" % text, g.validate(text), (kind, reason))
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

    print()
    if FAILURES:
        print("%d FAILURES" % len(FAILURES))
        return 1
    print("all checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
