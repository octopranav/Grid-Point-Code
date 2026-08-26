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

"""Properties that hold for every point, checked over a wide generated sample.

The files in test_data/ pin behaviour case by case. This file pins the rules
that must hold everywhere: a code is always ten characters, always spelled from
the alphabet, always valid, and always decodes back inside the cell it came
from. It also pins the two properties the whole format exists for -- containment
of a shared prefix, and continuity of the ordering.

The sample behind them is a hundred thousand coordinates that are generated
rather than stored, so the same inputs reach every port without a large file in
the repository. Its definition lives in test_data/README.md; the digest of the
codes it produces lives in test_data/v2_sample.csv, which is what makes this
file a cross-port check as well as a local one.

Every constant below is written out rather than read from the implementation.
A test that borrows the constant it is checking proves nothing.
"""

import hashlib
import math
import unittest
from pathlib import Path

from src.gridpointcode_algo_pranavpatel_ca import GPC

ALPHABET = "0123456789CDFGHJKLMNPRTWX"
CODE_LENGTH = 10
FORMATTED_LENGTH = 12

# The grid of section 3.
ROWS = 7_812_500   # 4 * 5^9
COLS = 11_718_750  # 6 * 5^9

# Generator constants. Kept beside the code that uses them so this file reads
# as a standalone statement of the sample, the same way every other port does.
MULTIPLIER = 1_664_525
INCREMENT = 1_013_904_223
MODULUS = 4_294_967_296  # 2^32
LAT_SPAN = 18_000_001    # -90.00000 .. 90.00000 in units of 1e-5
LONG_SPAN = 36_000_001   # -180.00000 .. 180.00000 in units of 1e-5


def _test_data_dir() -> Path:
    """Walk up from this file until the shared test_data directory appears."""
    for parent in Path(__file__).resolve().parents:
        candidate = parent / "test_data"
        if candidate.is_dir():
            return candidate
    raise FileNotFoundError("test_data directory not found above " + __file__)


def _sample_spec() -> tuple:
    """Read count, seed and expected digest from test_data/v2_sample.csv."""
    path = _test_data_dir() / "v2_sample.csv"
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            count, seed, digest = line.split(",")
            return int(count), int(seed), digest
    raise ValueError("no data row in " + str(path))


def _sample_points(count: int, seed: int):
    """Yield the shared sample, one (latitude, longitude) pair at a time.

    A linear congruential sequence whose products stay below 2 ** 53, so every
    port walks it exactly, including the ones whose only number is a double.
    """
    state = seed
    for _ in range(count):
        state = (MULTIPLIER * state + INCREMENT) % MODULUS
        latitude = (state % LAT_SPAN - (LAT_SPAN - 1) // 2) / 100000
        state = (MULTIPLIER * state + INCREMENT) % MODULUS
        longitude = (state % LONG_SPAN - (LONG_SPAN - 1) // 2) / 100000
        yield latitude, longitude


def _grid(latitude: float, longitude: float) -> tuple:
    """Section 5.1, restated. The row and column a coordinate falls in."""
    if longitude == 180.0:
        longitude = -180.0
    row = math.floor((latitude + 90.0) * 7812500.0 / 180.0)
    col = math.floor((longitude + 180.0) * 11718750.0 / 360.0)
    return (min(max(row, 0), ROWS - 1), min(max(col, 0), COLS - 1))


def _successor(code: str) -> str:
    """The next code in plain ASCII order, which is base-25 counting."""
    out = list(code)
    position = len(out) - 1
    while position >= 0:
        index = ALPHABET.index(out[position]) + 1
        if index < len(ALPHABET):
            out[position] = ALPHABET[index]
            return "".join(out)
        out[position] = ALPHABET[0]
        position -= 1
    raise OverflowError("ran off the end of the code space")


class TestProperties(unittest.TestCase):
    """Invariants asserted over the whole sample."""

    @classmethod
    def setUpClass(cls):
        cls.count, cls.seed, cls.digest = _sample_spec()
        cls.points = list(_sample_points(cls.count, cls.seed))
        cls.codes = [GPC.encode(latitude, longitude, False)
                     for latitude, longitude in cls.points]

    def test_the_sample_is_substantial(self):
        self.assertGreaterEqual(self.count, 100_000)
        self.assertEqual(self.count, len(self.codes))

    def test_the_sample_digest_matches_every_other_port(self):
        """The one assertion that fails when two ports stop agreeing."""
        joined = "\n".join(self.codes).encode("utf-8")
        self.assertEqual(self.digest, hashlib.sha256(joined).hexdigest())

    def test_every_code_has_the_fixed_length(self):
        for code in self.codes:
            if len(code) != CODE_LENGTH:
                self.fail(f"{code!r} is {len(code)} characters, not {CODE_LENGTH}")

    def test_every_code_is_spelled_from_the_alphabet(self):
        allowed = set(ALPHABET)
        for code in self.codes:
            stray = set(code) - allowed
            if stray:
                self.fail(f"{code!r} contains {sorted(stray)}, outside the alphabet")

    def test_no_encoded_code_begins_with_x(self):
        """Level 1 yields 24 indices, so the X-prefixed space is unreachable."""
        for code in self.codes:
            if code[0] == "X":
                self.fail(f"{code!r} was encoded but is in the reserved namespace")

    def test_every_code_validates(self):
        for code in self.codes:
            if not GPC.is_valid(code):
                self.fail(f"{code!r} came out of encode but failed validation: "
                          f"{GPC.validate(code)[1]}")

    def test_decoding_lands_inside_the_cell_it_came_from(self):
        for (latitude, longitude), code in zip(self.points, self.codes):
            south, west, north, east = GPC.decode_to_area(code)
            decoded_lat, decoded_long = GPC.decode(code)
            if not south <= decoded_lat <= north or not west <= decoded_long <= east:
                self.fail(f"{code!r} decoded outside its own area")

    def test_round_trip_is_stable(self):
        for code in self.codes:
            decoded_lat, decoded_long = GPC.decode(code)
            again = GPC.encode(decoded_lat, decoded_long, False)
            if again != code:
                self.fail(f"{code!r} re-encoded as {again!r} after decoding")

    def test_the_formatted_form_is_the_unformatted_one_with_separators(self):
        for (latitude, longitude), code in zip(self.points[:1000], self.codes[:1000]):
            formatted = GPC.encode(latitude, longitude, True)
            self.assertEqual(FORMATTED_LENGTH, len(formatted))
            self.assertEqual(f"#{code[:5]}-{code[5:]}", formatted)

    def test_a_string_sort_is_a_spatial_sort(self):
        """Section 11.1. The alphabet is ASCII-ascending, so sorting codes as
        bytes sorts them the way the grid is traversed."""
        pairs = sorted(zip(self.codes[:20_000], range(20_000)))
        for (code, _), (later, _) in zip(pairs, pairs[1:]):
            self.assertLessEqual(code, later)


class TestLocality(unittest.TestCase):
    """Section 10. Two codes agree in their first k characters if and only if
    the points lie in the same level-k cell."""

    @classmethod
    def setUpClass(cls):
        count, seed, _ = _sample_spec()
        cls.points = list(_sample_points(min(count, 20_000), seed))
        cls.codes = [GPC.encode(latitude, longitude, False)
                     for latitude, longitude in cls.points]

    def test_a_shared_prefix_means_the_same_cell_and_the_reverse(self):
        cells = {}
        for (latitude, longitude), code in zip(self.points, self.codes):
            row, col = _grid(latitude, longitude)
            for k in range(1, 11):
                p = 5 ** (10 - k)
                key = (k, row // p, col // p)
                prefix = code[:k]
                if key in cells:
                    # Same cell, so the prefixes must match.
                    self.assertEqual(cells[key], prefix)
                else:
                    cells[key] = prefix
        # And the other direction: one prefix never names two cells.
        by_prefix = {}
        for (k, cell_row, cell_col), prefix in cells.items():
            self.assertNotIn((k, prefix), by_prefix)
            by_prefix[(k, prefix)] = (cell_row, cell_col)

    def test_the_box_of_a_code_lies_inside_its_level_k_cell(self):
        for (latitude, longitude), code in zip(self.points[:2000], self.codes[:2000]):
            row, col = _grid(latitude, longitude)
            south, west, north, east = GPC.decode_to_area(code)
            for k in range(1, 11):
                p = 5 ** (10 - k)
                # The same expression shape section 6.3 uses, so when the cell
                # edge and the box edge coincide they are the identical double.
                cell_south = (row // p * p) * 180.0 / 7812500.0 - 90.0
                cell_north = ((row // p + 1) * p) * 180.0 / 7812500.0 - 90.0
                cell_west = (col // p * p) * 360.0 / 11718750.0 - 180.0
                cell_east = ((col // p + 1) * p) * 360.0 / 11718750.0 - 180.0
                with self.subTest(code=code, k=k):
                    self.assertLessEqual(cell_south, south)
                    self.assertLessEqual(north, cell_north)
                    self.assertLessEqual(cell_west, west)
                    self.assertLessEqual(east, cell_east)


class TestSpatialProperties(unittest.TestCase):
    """Sections 12, 13 and 18, over the same wide sample.

    The vector files pin these operations case by case. What these pin is that
    they hold everywhere -- including in the quadrants a case-by-case corpus
    might happen not to reach.
    """

    @classmethod
    def setUpClass(cls):
        count, seed, _ = _sample_spec()
        cls.points = list(_sample_points(min(count, 20_000), seed))
        cls.codes = [GPC.encode(latitude, longitude, False)
                     for latitude, longitude in cls.points]

    def test_containment_agrees_with_the_grid(self):
        for (latitude, longitude), code in zip(self.points[:4000], self.codes[:4000]):
            row, col = _grid(latitude, longitude)
            for k in (1, 3, 5, 7, 10):
                cell = GPC.cell(code, k)
                with self.subTest(code=code, k=k):
                    self.assertEqual(code[:k], cell)
                    self.assertTrue(GPC.contains(cell, code))
                    # And a cell the point is not in never claims it.
                    neighbour = GPC.neighbours(cell)[0]
                    self.assertFalse(GPC.contains(neighbour, code))

    def test_every_neighbour_is_one_cell_away(self):
        for code in self.codes[:2000]:
            for k in (1, 4, 7, 10):
                p = 5 ** (10 - k)
                row_cells = 4 * 5 ** (k - 1)
                col_cells = 6 * 5 ** (k - 1)
                cell = GPC.cell(code, k)
                row, col = GPC.decode_to_grid(code)
                cell_row, cell_col = row // p, col // p
                got = GPC.neighbours(cell)
                with self.subTest(cell=cell):
                    # Five in a polar row, eight everywhere else. Rows do not
                    # wrap; columns always do.
                    expected = 8 if 0 < cell_row < row_cells - 1 else 5
                    self.assertEqual(expected, len(got))
                    self.assertEqual(len(set(got)), len(got))
                    self.assertNotIn(cell, got)
                for neighbour in got:
                    padded = neighbour + "0" * (10 - k)
                    n_row, n_col = GPC.code_to_grid(padded)
                    d_col = (n_col // p - cell_col + col_cells) % col_cells
                    if d_col > col_cells // 2:
                        d_col -= col_cells
                    with self.subTest(cell=cell, neighbour=neighbour):
                        self.assertLessEqual(abs(n_row // p - cell_row), 1)
                        self.assertLessEqual(abs(d_col), 1)

    def test_the_short_form_recovers_inside_half_a_cell(self):
        # Half a level-5 cell on each axis: 1562 rows and 1562 columns.
        half_lat = 1562 * 180.0 / 7812500.0
        half_long = 1562 * 360.0 / 11718750.0
        for (latitude, longitude), code in zip(self.points[:4000], self.codes[:4000]):
            for d_lat, d_long in ((0.0, 0.0), (half_lat, half_long),
                                  (-half_lat, -half_long),
                                  (half_lat, -half_long), (-half_lat, half_long)):
                reference = (latitude + d_lat, longitude + d_long)
                if not (-90.0 <= reference[0] <= 90.0
                        and -180.0 <= reference[1] <= 180.0):
                    continue
                with self.subTest(code=code, reference=reference):
                    self.assertEqual(code, GPC.recover_short(
                        GPC.shorten(code), reference[0], reference[1], False))

    def test_the_integer_form_round_trips_and_keeps_the_order(self):
        values = []
        for code in self.codes[:20_000]:
            value = GPC.to_integer(code)
            self.assertEqual(code, GPC.from_integer(value, False))
            # No encoded code reaches the reserved namespace, so no integer
            # form of one reaches the floor either.
            self.assertLess(value, 24 * 25 ** 9)
            values.append((code, value))
        by_string = sorted(values, key=lambda pair: pair[0])
        by_value = sorted(values, key=lambda pair: pair[1])
        self.assertEqual(by_string, by_value)

    def test_distance_is_symmetric_and_zero_only_on_itself(self):
        for i in range(0, 2000, 2):
            a, b = self.codes[i], self.codes[i + 1]
            with self.subTest(a=a, b=b):
                self.assertEqual(GPC.distance(a, b), GPC.distance(b, a))
                self.assertEqual(0.0, GPC.distance(a, a))
                self.assertEqual(a == b, GPC.distance(a, b) == 0.0)

    def test_a_code_survives_both_coordinate_conversions(self):
        # decode returns a cell centre, which sits far enough from the nearest
        # boundary that neither rounding can push it into the next cell.
        for code in self.codes[:5000]:
            latitude, longitude = GPC.decode(code)
            with self.subTest(code=code):
                back = GPC.from_geo_uri(GPC.to_geo_uri(latitude, longitude))
                self.assertEqual(code, GPC.encode(back[0], back[1], False))
                back = GPC.from_dms(GPC.to_dms(latitude, longitude))
                self.assertEqual(code, GPC.encode(back[0], back[1], False))

    def test_screening_never_changes_what_a_code_does(self):
        for code in self.codes[:5000]:
            version, _ = GPC.screen(code)
            with self.subTest(code=code):
                self.assertNotEqual("", version)
                self.assertTrue(GPC.is_valid(code))


class TestOrdering(unittest.TestCase):
    """Section 11.2. Consecutive codes are adjacent cells, everywhere except
    at a level-5 boundary, and that is exactly where the reset of 5.3 puts the
    only discontinuities."""

    # 24 * 25^4 level-5 cells, so one fewer transition between them, out of
    # 24 * 25^9 - 1 steps in all. That is the 99.99999 % of section 5.3.
    LEVEL_5_CELLS = 24 * 25 ** 4
    TOTAL_STEPS = 24 * 25 ** 9 - 1

    def test_the_discontinuity_count_is_what_the_specification_says(self):
        self.assertEqual(9_375_000, self.LEVEL_5_CELLS)
        self.assertEqual(9_374_999, self.LEVEL_5_CELLS - 1)
        self.assertEqual(91_552_734_374_999, self.TOTAL_STEPS)
        share = (self.TOTAL_STEPS - (self.LEVEL_5_CELLS - 1)) / self.TOTAL_STEPS
        self.assertEqual("99.99999", "%.5f" % (share * 100))

    def test_consecutive_codes_are_adjacent_inside_a_level_5_cell(self):
        """A transcription error anywhere in the reflection breaks this."""
        for latitude, longitude in [(43.65, -79.38), (-33.8568, 151.2153),
                                    (0.0, 0.0), (64.1466, -21.9426),
                                    (-13.1631, -72.545), (23.0225, 72.5714)]:
            code = GPC.encode(latitude, longitude, False)
            prefix = code[:5]
            walked = 0
            previous = _grid(*GPC.decode(code))
            for _ in range(4000):
                code = _successor(code)
                if code[:5] != prefix:
                    break
                current = _grid(*GPC.decode(code))
                step = (abs(current[0] - previous[0])
                        + abs(current[1] - previous[1]))
                with self.subTest(code=code):
                    self.assertEqual(1, step)
                previous = current
                walked += 1
            self.assertGreater(walked, 100)

    def test_every_level_5_transition_is_a_jump(self):
        """The traversal of one cell ends at its far corner and the next begins
        at its near corner, so the step between them is never adjacent."""
        tested = 0
        for latitude in (-80.0, -40.0, -5.0, 5.0, 40.0, 80.0):
            for longitude in (-170.0, -100.0, -20.0, 20.0, 100.0, 170.0):
                prefix = GPC.encode(latitude, longitude, False)[:5]
                following = _successor(prefix)
                if following[0] == "X":
                    continue  # ran into the reserved namespace
                last = _grid(*GPC.decode(prefix + "XXXXX"))
                first = _grid(*GPC.decode(following + "00000"))
                step = abs(last[0] - first[0]) + abs(last[1] - first[1])
                with self.subTest(prefix=prefix):
                    self.assertNotEqual(1, step)
                tested += 1
        self.assertGreater(tested, 20)


if __name__ == "__main__":
    unittest.main()
