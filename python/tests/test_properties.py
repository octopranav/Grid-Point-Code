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
that must hold everywhere: a code is always the same length, always spelled
from the alphabet, always valid, and always decodes back inside the cell it
came from.

The sample behind them is a hundred thousand coordinates that are generated
rather than stored, so the same inputs reach every port without a large file in
the repository. Its definition lives in test_data/README.md; the digest of the
codes it produces lives in test_data/sample.csv, which is what makes this file
a cross-port check as well as a local one.
"""

import hashlib
import unittest
from pathlib import Path

from src.gridpointcode_algo_pranavpatel_ca import GPC

# The specified alphabet, written out rather than read from the implementation:
# a test that borrows the constant it is checking proves nothing.
ALPHABET = "CDFGHJKLMNPRTVWXY0123456789"
CODE_LENGTH = 11
FORMATTED_LENGTH = 14

# One cell is a hundred-thousandth of a degree on each axis.
CELL = 1e-5

# Generator constants. Kept beside the code that uses them so this file reads
# as a standalone statement of the sample, the same way every other port does.
MULTIPLIER = 1_664_525
INCREMENT = 1_013_904_223
MODULUS = 4_294_967_296  # 2^32
LAT_SPAN = 17_999_999
LONG_SPAN = 35_999_999


def _test_data_dir() -> Path:
    """Walk up from this file until the shared test_data directory appears."""
    for parent in Path(__file__).resolve().parents:
        candidate = parent / "test_data"
        if candidate.is_dir():
            return candidate
    raise FileNotFoundError("test_data directory not found above " + __file__)


def _sample_spec() -> tuple[int, int, str]:
    """Read count, seed and expected digest from test_data/sample.csv."""
    path = _test_data_dir() / "sample.csv"
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

    def test_every_code_validates(self):
        for code in self.codes:
            valid, message = GPC.is_valid_gpc(code)
            if not valid:
                self.fail(f"{code!r} came out of encode but failed validation: {message}")

    def test_decoding_lands_inside_the_cell_it_came_from(self):
        for (latitude, longitude), code in zip(self.points, self.codes):
            decoded_lat, decoded_long = GPC.decode(code)
            if abs(latitude - decoded_lat) >= CELL or abs(longitude - decoded_long) >= CELL:
                self.fail(f"{code!r} decoded to ({decoded_lat}, {decoded_long}), "
                          f"more than one cell from ({latitude}, {longitude})")

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
            self.assertEqual(f"#{code[:4]}-{code[4:8]}-{code[8:11]}", formatted)


if __name__ == "__main__":
    unittest.main()
