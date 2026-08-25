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
here rather than in a release.
"""

import unittest
from pathlib import Path

from src.gridpointcode_algo_pranavpatel_ca import GPC


def _test_data_dir() -> Path:
    """Walk up from this file until the shared test_data directory appears."""
    for parent in Path(__file__).resolve().parents:
        candidate = parent / "test_data"
        if candidate.is_dir():
            return candidate
    raise FileNotFoundError("test_data directory not found above " + __file__)


def _rows(name: str, fields: int) -> list[list[str]]:
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


class TestVectors(unittest.TestCase):
    """Conformance tests driven by the shared vector files."""

    def test_encoding(self):
        rows = _rows("encoding.csv", 3)
        self.assertGreater(len(rows), 100)
        for latitude, longitude, expected in rows:
            with self.subTest(latitude=latitude, longitude=longitude):
                self.assertEqual(
                    expected, GPC.encode(float(latitude), float(longitude), False)
                )

    def test_decoding(self):
        rows = _rows("decoding.csv", 3)
        self.assertGreater(len(rows), 100)
        for code, latitude, longitude in rows:
            with self.subTest(code=code):
                self.assertEqual((float(latitude), float(longitude)), GPC.decode(code))

    def test_formatted_and_unformatted_decode_alike(self):
        for code, latitude, longitude in _rows("decoding.csv", 3):
            formatted = GPC.format_gpc(code)
            with self.subTest(code=formatted):
                self.assertEqual((float(latitude), float(longitude)), GPC.decode(formatted))

    def test_round_trip(self):
        for latitude, longitude, code in _rows("encoding.csv", 3):
            with self.subTest(code=code):
                decoded_lat, decoded_long = GPC.decode(code)
                self.assertEqual(code, GPC.encode(decoded_lat, decoded_long, False))

    def test_code_validity(self):
        rows = _rows("validity_codes.csv", 3)
        self.assertGreater(len(rows), 10)
        for expected_valid, expected_message, code in rows:
            with self.subTest(code=repr(code)):
                valid, message = GPC.is_valid_gpc(code)
                self.assertEqual(expected_valid == "true", valid)
                self.assertEqual(expected_message, message)

    def test_invalid_codes_raise_on_decode(self):
        for expected_valid, _, code in _rows("validity_codes.csv", 3):
            if expected_valid == "true":
                continue
            with self.subTest(code=repr(code)):
                with self.assertRaises(ValueError):
                    GPC.decode(code)

    def test_coordinate_validity(self):
        rows = _rows("validity_coordinates.csv", 4)
        self.assertGreater(len(rows), 10)
        for latitude, longitude, expected_valid, expected_message in rows:
            with self.subTest(latitude=latitude, longitude=longitude):
                valid, message = GPC.is_valid_coordinates(float(latitude), float(longitude))
                self.assertEqual(expected_valid == "true", valid)
                self.assertEqual(expected_message, message)

    def test_out_of_range_coordinates_raise_on_encode(self):
        for latitude, longitude, expected_valid, _ in _rows("validity_coordinates.csv", 4):
            if expected_valid == "true":
                continue
            with self.subTest(latitude=latitude, longitude=longitude):
                with self.assertRaises(ValueError):
                    GPC.encode(float(latitude), float(longitude))


if __name__ == "__main__":
    unittest.main()
