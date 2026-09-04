#  Copyright 2017 Pranavkumar Patel
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

"""The tokenising convention, and the one property it exists to provide.

    python -m unittest discover --start-directory search --top-level-directory search

The interesting test is `test_nothing_within_the_radius_is_missed`. Everything
else here checks that the terms come out in the shape the README describes; that
one checks the claim the README makes, which is that a query built this way
loses nothing. It is a measurement rather than an example, because the failure
it guards against is statistical: a naive version of this works on almost every
case anybody tries by hand and quietly drops the ones near a boundary.
"""

from __future__ import annotations

import math
import random
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from tokenise import (  # noqa: E402
    COARSEST, FINEST, GPC, level_for, query_at, query_for, terms_for,
)

TORONTO = "G3RJM98NM9"


def offset(latitude, longitude, metres, bearing):
    """A point roughly `metres` away. Roughly is enough: the tests below
    measure the distance that results rather than trusting this."""
    north = metres * math.cos(bearing) / 111_320.0
    east = metres * math.sin(bearing) / (
        111_320.0 * math.cos(math.radians(latitude))
    )
    return latitude + north, longitude + east


class Terms(unittest.TestCase):

    def test_one_term_per_level(self):
        terms = terms_for(TORONTO)
        self.assertEqual(len(terms), FINEST - COARSEST + 1)
        self.assertEqual(terms[0], TORONTO[:COARSEST])
        self.assertEqual(terms[-1], TORONTO[:FINEST])

    def test_every_term_is_a_prefix_of_the_code(self):
        for term in terms_for(TORONTO):
            self.assertTrue(TORONTO.startswith(term), term)

    def test_terms_grow_by_one_character(self):
        terms = terms_for(TORONTO)
        for shorter, longer in zip(terms, terms[1:]):
            self.assertEqual(len(longer), len(shorter) + 1)
            self.assertTrue(longer.startswith(shorter))

    def test_a_formatted_code_gives_the_same_terms(self):
        self.assertEqual(terms_for("#G3RJM-98NM9"), terms_for(TORONTO))

    def test_the_range_can_be_narrowed(self):
        self.assertEqual(terms_for(TORONTO, coarsest=5, finest=7),
                         ["G3RJM", "G3RJM9", "G3RJM98"])

    def test_an_upside_down_range_is_refused(self):
        with self.assertRaises(ValueError):
            terms_for(TORONTO, coarsest=7, finest=5)

    def test_a_reserved_code_is_not_indexed(self):
        # It names nothing, so there is no cell to file it under.
        with self.assertRaises(Exception):
            terms_for("X" + TORONTO[1:])


class Queries(unittest.TestCase):

    def test_the_query_holds_the_cell_and_its_neighbours(self):
        terms = query_for(TORONTO, 7)
        self.assertEqual(len(terms), 9)
        self.assertEqual(terms[0], "G3RJM98")
        self.assertEqual(len(set(terms)), 9)

    def test_every_query_term_is_a_cell_of_that_level(self):
        for term in query_for(TORONTO, 6):
            self.assertEqual(len(term), 6)

    def test_a_coordinate_and_its_code_ask_the_same_question(self):
        latitude, longitude = GPC.decode(TORONTO)
        self.assertEqual(query_at(latitude, longitude, 7), query_for(TORONTO, 7))

    def test_the_antimeridian_is_not_a_wall(self):
        # The line is a convention, not an edge: two points either side of it
        # can be metres apart. The query has to reach across, and it does --
        # the westernmost cell lists cells from the far side among its
        # neighbours. Without this a search at the dateline would find half of
        # what is around it and give no sign that it had.
        west = GPC.encode(0.0, -180.0, False)
        terms = query_for(west, 6)
        self.assertEqual(len(terms), 9)
        self.assertTrue(any(term.startswith('L') for term in terms),
                        f"nothing from the eastern side is in {terms}")

    def test_the_poles_are_an_edge(self):
        # Unlike the antimeridian there is genuinely nothing above the north
        # pole, so the list comes back short rather than naming a cell that
        # does not exist and would match nothing forever.
        pole = GPC.encode(89.999, 0.0, False)
        self.assertLess(len(query_for(pole, 6)), 9)


class Levels(unittest.TestCase):

    def test_a_bigger_radius_takes_a_coarser_level(self):
        self.assertGreater(level_for(10), level_for(1000))

    def test_the_level_is_the_finest_that_still_covers(self):
        for radius in (5, 50, 300, 2000, 20000):
            level = level_for(radius)
            _, _, north_south, east_west = GPC.cell_dimensions(level)
            self.assertGreaterEqual(min(north_south, east_west), radius,
                                    f"{radius} m does not fit in level {level}")
            if level < FINEST:
                finer = GPC.cell_dimensions(level + 1)
                self.assertLess(min(finer[2], finer[3]), radius,
                                f"level {level + 1} would have done for {radius} m")

    def test_cells_narrow_toward_the_poles(self):
        # The same radius needs a coarser level at 70 degrees than at the
        # equator, because a cell is a third as wide there.
        self.assertLessEqual(level_for(300, 70.0), level_for(300, 0.0))

    def test_a_radius_has_to_be_positive(self):
        for bad in (0, -1):
            with self.assertRaises(ValueError):
                level_for(bad)


class TheClaim(unittest.TestCase):
    """The property the convention exists to provide."""

    def test_nothing_within_the_radius_is_missed(self):
        # For a point and anything within the radius of it, the query built for
        # that point must carry the term the other one is indexed under. Not
        # nearly always -- the whole reason the eight neighbours are in the
        # query is that the naive version is right about 64 % of the time.
        random.seed(20260903)

        checked = 0
        missed = []

        for _ in range(20000):
            latitude = random.uniform(-70, 70)
            longitude = random.uniform(-179, 179)
            radius = random.choice((25, 100, 400))
            level = level_for(radius, latitude)

            other = offset(latitude, longitude,
                           random.uniform(1, radius), random.uniform(0, 2 * math.pi))
            try:
                here = GPC.encode(latitude, longitude, False)
                there = GPC.encode(other[0], other[1], False)
            except Exception:
                continue

            # The offset above is approximate, so the pair is measured rather
            # than assumed to be within the radius. Anything that came out
            # further away is not what this test is about.
            if GPC.distance(here, there) > radius:
                continue

            checked += 1
            if GPC.cell(there, level) not in query_for(here, level):
                missed.append((here, there, radius, level))

        self.assertGreater(checked, 15000, "too few pairs to be worth believing")
        self.assertEqual(missed[:3], [], f"{len(missed)} of {checked} pairs were missed")

    def test_the_naive_query_really_does_lose_things(self):
        # The other half of the argument. If searching the single cell were
        # good enough, the eight neighbours would be waste, so the cost of
        # leaving them out is measured here rather than asserted in a comment.
        random.seed(1)
        alone = 0
        checked = 0

        for _ in range(5000):
            latitude = random.uniform(-60, 60)
            longitude = random.uniform(-179, 179)
            radius = 100
            level = level_for(radius, latitude)
            other = offset(latitude, longitude, radius * 0.9,
                           random.uniform(0, 2 * math.pi))
            try:
                here = GPC.encode(latitude, longitude, False)
                there = GPC.encode(other[0], other[1], False)
            except Exception:
                continue
            checked += 1
            if GPC.cell(there, level) == GPC.cell(here, level):
                alone += 1

        share = alone / checked
        self.assertLess(share, 0.95,
                        "the single cell found nearly everything, so this "
                        "convention is more complicated than it needs to be")


if __name__ == "__main__":
    unittest.main()
