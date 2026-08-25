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

"""Reproduces every measured figure quoted in SPEC.md.

    python reference/measure.py            # everything
    python reference/measure.py locality   # one section

Sections: cells, locality, ordering, clustering, typos, corrections, seams.

Output is laid out to be read beside the document. Sampling is seeded, so a
figure that moves means behaviour changed, not that the dice fell differently.
Statistical figures are quoted in SPEC.md to three significant digits and will
not move at that precision unless the format does.
"""

import math
import random
import statistics
import sys

import geodesy as geo
import gpc2 as g

SEED = 20260825


def heading(text):
    print("\n%s\n%s" % (text, "-" * len(text)))


def metres(m):
    if m >= 1000:
        return "%.1f km" % (m / 1000)
    return "%.1f m" % m if m < 10 else "%.0f m" % m


# ---------------------------------------------------------------------------

def cells():
    heading("Cell dimensions by level  (SPEC.md section 3)")
    print("%5s  %-14s %-14s %12s %12s"
          % ("Level", "Latitude", "Longitude", "North-south", "East-west"))
    for k in range(1, 11):
        dlat = 45.0 / 5 ** (k - 1)
        dlng = 60.0 / 5 ** (k - 1)
        print("%5d  %-14.8f %-14.8f %12s %12s"
              % (k, dlat, dlng,
                 metres(dlat * geo.M_PER_DEG_LAT), metres(dlng * geo.M_PER_DEG_LNG)))
    ns = 45.0 / 5 ** 9 * geo.M_PER_DEG_LAT
    ew = 60.0 / 5 ** 9 * geo.M_PER_DEG_LNG
    print("\nlevel-10 cell   %.4f m by %.4f m" % (ns, ew))
    print("square at       %.3f degrees of latitude" % math.degrees(math.acos(ns / ew)))


def locality():
    heading("Pairs 100 m apart  (SPEC.md section 10.2)")
    rng = random.Random(SEED)
    counts = [0] * 11
    total = 0
    for _ in range(20_000):
        la, lo = geo.random_point(rng)
        if abs(la) > 89.9:
            continue
        la2, lo2 = geo.offset(la, lo, rng.uniform(0, 360), 100.0)
        if abs(la2) > 90:
            continue
        counts[geo.shared_prefix(g.encode(la, lo, False),
                                 g.encode(la2, lo2, False))] += 1
        total += 1
    print("sample: %d pairs, uniform over the sphere, seed %d\n" % (total, SEED))
    for k in (6, 5, 4):
        print("  share at least %d characters   %6.2f %%"
              % (k, 100.0 * sum(counts[k:]) / total))
    print("  share fewer than 4            %6.2f %%"
          % (100.0 - 100.0 * sum(counts[4:]) / total))
    print("  share nothing at all          %6.3f %%" % (100.0 * counts[0] / total))


def ordering():
    heading("Continuity  (SPEC.md section 11.2)")
    entry = g.read_tail("00000")
    exit_ = g.read_tail("XXXXX")
    print("a level-5 cell is entered at offset %s and left at %s" % (entry, exit_))

    sub = sorted((g.to_integer(g.grid_to_code(r * 25, c * 25)) % (25 ** 5), r, c)
                 for r in range(125) for c in range(125))
    adjacent = sum(1 for i in range(len(sub) - 1)
                   if abs(sub[i][1] - sub[i + 1][1])
                   + abs(sub[i][2] - sub[i + 1][2]) == 1)
    print("inside one cell (levels 6-8, 125 x 125): %.4f %% adjacent steps"
          % (100.0 * adjacent / (len(sub) - 1)))

    print("\nevery level-5 step breaks, in all four directions:")
    for dr, dc in ((0, 1), (0, -1), (1, 0), (-1, 0)):
        nr = dr * g.P5 + entry[0] - exit_[0]
        nc = dc * g.P5 + entry[1] - exit_[1]
        print("  step (%2d,%2d)  offset to next entry (%6d,%6d)  adjacent: %s"
              % (dr, dc, nr, nc, abs(nr) + abs(nc) == 1))

    steps = g.ROWS * g.COLS - 1
    breaks = g.R5 * g.C5 - 1
    print("\n  discontinuities   %s" % format(breaks, ","))
    print("  of steps          %s" % format(steps, ","))
    print("  continuity        %.7f %%" % (100.0 * (1 - breaks / steps)))


def clustering():
    heading("Clustering  (SPEC.md section 11.3)")
    rng = random.Random(SEED)
    print("%8s  %12s  %8s" % ("Window", "Mean ranges", "Samples"))
    for w, n in ((4, 3000), (16, 3000), (32, 800)):
        runs = []
        for _ in range(n):
            r0 = rng.randrange(g.ROWS - w)
            c0 = rng.randrange(g.COLS - w)
            ranks = sorted(g.to_integer(g.grid_to_code(r0 + i, c0 + j))
                           for i in range(w) for j in range(w))
            runs.append(1 + sum(1 for i in range(len(ranks) - 1)
                                if ranks[i + 1] != ranks[i] + 1))
        print("%5d x%-2d  %12.2f  %8d" % (w, w, statistics.mean(runs), n))


def typos():
    heading("Typo behaviour  (SPEC.md section 15.1)")
    rng = random.Random(SEED)
    groups = {"1-3": [], "4-6": [], "7-10": []}
    every = []
    caught = trials = 0
    for _ in range(200_000):
        la, lo = geo.random_point(rng)
        code = g.encode(la, lo, False)
        p = rng.randrange(10)
        ch = g.ALPHABET[rng.randrange(25)]
        if ch == code[p]:
            continue
        trials += 1
        if p == 0 and ch == "X":
            caught += 1
            continue
        bad = code[:p] + ch + code[p + 1:]
        d = geo.haversine(g.decode(code), g.decode(bad))
        groups["1-3" if p < 3 else ("4-6" if p < 6 else "7-10")].append(d)
        every.append(d)

    tr = tr_caught = 0
    for _ in range(200_000):
        la, lo = geo.random_point(rng)
        code = g.encode(la, lo, False)
        p = rng.randrange(9)
        if code[p] == code[p + 1]:
            continue
        tr += 1
        if (code[:p] + code[p + 1] + code[p] + code[p + 2:])[0] == "X":
            tr_caught += 1

    print("substitutions %s, transpositions %s, seed %d\n"
          % (format(trials, ","), format(tr, ","), SEED))
    print("  caught before decoding         %6.3f %%   (exactly 1/240 = %.3f %%)"
          % (100.0 * caught / trials, 100.0 / 240))
    print("  silent, 0.5 to 50 km away      %6.1f %%"
          % (100.0 * sum(1 for d in every if 500 <= d <= 50000) / trials))
    print("  adjacent transpositions caught %6.3f %%" % (100.0 * tr_caught / tr))
    print("\n%10s %16s %16s %8s" % ("Position", "Median", "Maximum", "n"))
    for name in ("1-3", "4-6", "7-10"):
        v = groups[name]
        print("%10s %16s %16s %8d"
              % (name, metres(statistics.median(v)), metres(max(v)), len(v)))


def corrections():
    heading("Typo correction  (SPEC.md section 15.3)")
    print("typos landing more than 10 km from the reference, 1,500 each\n")
    print("%3s %18s %14s %10s %10s %8s %6s"
          % ("k", "Window", "Reference", "In the set", "First", "Median", "90th"))
    for level, ref_error in ((4, 5000), (5, 5000), (6, 500), (7, 100)):
        rng = random.Random(SEED)
        found = first = n = 0
        sizes = []
        while n < 1500:
            la, lo = geo.random_point(rng)
            if abs(la) > 85:
                continue
            true = g.encode(la, lo, False)
            p = rng.randrange(10)
            ch = g.ALPHABET[rng.randrange(25)]
            if ch == true[p]:
                continue
            bad = true[:p] + ch + true[p + 1:]
            if bad[0] == "X":
                continue
            ref = geo.offset(la, lo, rng.uniform(0, 360), rng.uniform(0, ref_error))
            if geo.haversine(g.decode(bad), ref) <= 10000:
                continue
            n += 1
            out = g.suggest_corrections(bad, ref[0], ref[1], level=level)
            sizes.append(len(out))
            if true in out:
                found += 1
            if out and out[0] == true:
                first += 1
        win_ns = 3 * 45.0 / 5 ** (level - 1) * geo.M_PER_DEG_LAT
        win_ew = 3 * 60.0 / 5 ** (level - 1) * geo.M_PER_DEG_LNG
        print("%3d %18s %12s   %8.2f %% %8.2f %% %8d %6d"
              % (level, "%s x %s" % (metres(win_ns), metres(win_ew)),
                 metres(ref_error), 100.0 * found / n, 100.0 * first / n,
                 statistics.median(sizes), sorted(sizes)[int(0.9 * len(sizes))]))


def seams():
    heading("Seams  (SPEC.md section 16)")
    pairs = [("Royal Observatory, across the Greenwich meridian",
              51.47780, -0.00002, 51.47780, 0.00002),
             ("Near Pontianak, across the equator",
              -0.00002, 109.32000, 0.00002, 109.32000),
             ("Across the 60 degree E meridian",
              23.60000, 59.99998, 23.60000, 60.00002),
             ("Across the 45 degree N parallel",
              44.99998, -0.57000, 45.00002, -0.57000)]
    for name, la1, lo1, la2, lo2 in pairs:
        a, b = g.encode(la1, lo1), g.encode(la2, lo2)
        print("\n%s" % name)
        print("  %12.5f, %11.5f   %s" % (la1, lo1, a))
        print("  %12.5f, %11.5f   %s" % (la2, lo2, b))
        print("  %.1f m apart, %d shared characters"
              % (geo.haversine((la1, lo1), (la2, lo2)),
                 geo.shared_prefix(g.normalise(a)[0], g.normalise(b)[0])))
    print("\nsmallest possible separation across a seam:")
    print("  north-south          %.2f m" % (45.0 / 5 ** 9 * geo.M_PER_DEG_LAT))
    print("  east-west, equator   %.2f m" % (60.0 / 5 ** 9 * geo.M_PER_DEG_LNG))


SECTIONS = {"cells": cells, "locality": locality, "ordering": ordering,
            "clustering": clustering, "typos": typos,
            "corrections": corrections, "seams": seams}


def main(argv):
    wanted = argv[1:] or list(SECTIONS)
    unknown = [name for name in wanted if name not in SECTIONS]
    if unknown:
        print("unknown section(s): %s" % ", ".join(unknown))
        print("choose from: %s" % ", ".join(SECTIONS))
        return 2
    for name in wanted:
        SECTIONS[name]()
    print()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
