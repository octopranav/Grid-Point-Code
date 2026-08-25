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

"""Runs the shared conformance vectors in test_data/.

Every port reads these same files, so a disagreement between languages shows up
here rather than in a release. The `v2_*` files hold version 2; the rest are
version 1, and are asserted by decoding, because no package encodes version 1
any more.
"""

import unittest
from pathlib import Path

from src.gridpointcode_algo_pranavpatel_ca import GPC

# One cell of the version 1 grid: a hundred-thousandth of a degree on each axis.
V1_CELL = 1e-5


def _test_data_dir() -> Path:
    """Walk up from this file until the shared test_data directory appears."""
    for parent in Path(__file__).resolve().parents:
        candidate = parent / "test_data"
        if candidate.is_dir():
            return candidate
    raise FileNotFoundError("test_data directory not found above " + __file__)


def _rows(name: str, fields: int) -> list:
    """Read one vector file, dropping comments and blank lines.

    Splits on the first `fields - 1` commas so the final column keeps any
    comma, '#' or spacing it contains.
    """
    path = _test_data_dir() / name
    rows = []
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            line = line.rstrip("\n").rstrip("\r")
            if not line.strip() or line.startswith("#"):
                continue
            rows.append(line.split(",", fields - 1))
    return rows


class TestVersion2Vectors(unittest.TestCase):
    """Version 2, driven by the v2_ files."""

    def test_encoding(self):
        rows = _rows("v2_encoding.csv", 3)
        self.assertGreater(len(rows), 100)
        for latitude, longitude, expected in rows:
            with self.subTest(latitude=latitude, longitude=longitude):
                self.assertEqual(
                    expected, GPC.encode(float(latitude), float(longitude), False)
                )

    def test_decoding(self):
        rows = _rows("v2_decoding.csv", 3)
        self.assertGreater(len(rows), 100)
        for code, latitude, longitude in rows:
            with self.subTest(code=code):
                self.assertEqual((float(latitude), float(longitude)), GPC.decode(code))

    def test_formatted_and_unformatted_decode_alike(self):
        for code, latitude, longitude in _rows("v2_decoding.csv", 3):
            formatted = GPC.format_gpc(code)
            with self.subTest(code=formatted):
                self.assertEqual((float(latitude), float(longitude)),
                                 GPC.decode(formatted))

    def test_round_trip(self):
        for _, _, code in _rows("v2_encoding.csv", 3):
            with self.subTest(code=code):
                latitude, longitude = GPC.decode(code)
                self.assertEqual(code, GPC.encode(latitude, longitude, False))

    def test_area(self):
        rows = _rows("v2_area.csv", 5)
        self.assertGreater(len(rows), 100)
        for code, south, west, north, east in rows:
            with self.subTest(code=code):
                self.assertEqual(
                    (float(south), float(west), float(north), float(east)),
                    GPC.decode_to_area(code))

    def test_classification(self):
        rows = _rows("v2_classify.csv", 3)
        self.assertGreater(len(rows), 10)
        for expected_class, expected_message, text in rows:
            with self.subTest(text=repr(text)):
                self.assertEqual((expected_class, expected_message),
                                 GPC.validate(text))
                self.assertEqual(expected_class, GPC.classify(text))
                self.assertEqual(expected_class == "GEOMETRIC", GPC.is_valid(text))

    def test_anything_not_geometric_raises_on_decode(self):
        for expected_class, _, text in _rows("v2_classify.csv", 3):
            if expected_class == "GEOMETRIC":
                continue
            if GPC.is_valid_v1(text)[0]:
                # Eleven characters is version 1 by definition, so decode reads
                # it rather than refusing it. classify describes the version 2
                # grid, which this string is not part of.
                continue
            with self.subTest(text=repr(text)):
                with self.assertRaises(ValueError):
                    GPC.decode(text)

    def test_reserved_codes_raise_their_own_reason(self):
        seen = 0
        for expected_class, _, text in _rows("v2_classify.csv", 3):
            if expected_class != "RESERVED":
                continue
            seen += 1
            with self.subTest(text=repr(text)):
                with self.assertRaises(ValueError) as caught:
                    GPC.decode(text)
                self.assertEqual("GPC_RESERVED", caught.exception.reason)
        self.assertGreater(seen, 0)

    def test_check_characters(self):
        rows = _rows("v2_check.csv", 2)
        self.assertGreater(len(rows), 10)
        for code, check in rows:
            with self.subTest(code=code):
                self.assertEqual(check, GPC.check_character(code))
                self.assertEqual(GPC.classify(code),
                                 GPC.classify(code + "*" + check))


class TestVersion1Vectors(unittest.TestCase):
    """Version 1, asserted from the decoding side only."""

    def test_decoding(self):
        rows = _rows("decoding.csv", 3)
        self.assertGreater(len(rows), 100)
        for code, latitude, longitude in rows:
            with self.subTest(code=code):
                self.assertEqual((float(latitude), float(longitude)),
                                 GPC.decode_v1(code))

    def test_formatted_and_unformatted_decode_alike(self):
        for code, latitude, longitude in _rows("decoding.csv", 3):
            formatted = "#%s-%s-%s" % (code[:4], code[4:8], code[8:11])
            with self.subTest(code=formatted):
                self.assertEqual((float(latitude), float(longitude)),
                                 GPC.decode_v1(formatted))

    def test_every_code_decodes_inside_the_cell_it_was_made_from(self):
        """encoding.csv was built by the version 1 encoder, which no longer
        ships. What survives is the containment: the code names the cell the
        coordinate falls in, so decoding lands within one cell of it."""
        rows = _rows("encoding.csv", 3)
        self.assertGreater(len(rows), 100)
        for latitude, longitude, code in rows:
            with self.subTest(code=code):
                decoded_lat, decoded_long = GPC.decode_v1(code)
                self.assertLess(abs(float(latitude) - decoded_lat), V1_CELL)
                self.assertLess(abs(float(longitude) - decoded_long), V1_CELL)

    def test_validity(self):
        rows = _rows("validity_codes.csv", 3)
        self.assertGreater(len(rows), 10)
        for expected_valid, expected_message, code in rows:
            with self.subTest(code=repr(code)):
                valid, message = GPC.is_valid_v1(code)
                self.assertEqual(expected_valid == "true", valid)
                self.assertEqual(expected_message, message)

    def test_invalid_codes_raise_on_decode(self):
        for expected_valid, _, code in _rows("validity_codes.csv", 3):
            if expected_valid == "true":
                continue
            with self.subTest(code=repr(code)):
                with self.assertRaises(ValueError):
                    GPC.decode_v1(code)


if __name__ == "__main__":
    unittest.main()
