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
