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

"""The plugin's own arithmetic, checked against the library's.

    python -m unittest discover --start-directory qgis --top-level-directory qgis

`cell_box` is a second copy of section 6.3, written because `decodeToArea` takes
a whole code and the plugin needs the box of a cell. A second copy of a formula
drifts, so at level 10 it is held to being bit-identical to the first.

Runs anywhere: `geometry.py` imports no QGIS.
"""

from __future__ import annotations

import os
import sys
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, os.environ.get("GPC_PYTHON_PATH")
                or str(HERE.parent / "python" / "src"))

from gridpointcode.geometry import cell_box            # noqa: E402
from gridpointcode_algo_pranavpatel_ca import GPC      # noqa: E402

PLACES = ("G3RJM98NM9", "KDC8XJM49X", "6LK4XNRP0R", "C8HKC13C80",
          "RDX9RTN19T", "P4444PPPPP", "3PPPP00000")


class CellBox(unittest.TestCase):

    def test_level_ten_matches_the_library_exactly(self):
        # Not "close to": the same floating-point expression, so the same bits.
        for code in PLACES:
            self.assertEqual(cell_box(GPC, code, 10), GPC.decode_to_area(code), code)

    def test_every_cell_contains_its_own_point(self):
        for code in PLACES:
            latitude, longitude = GPC.decode(code)
            for level in range(1, 11):
                south, west, north, east = cell_box(GPC, code, level)
                self.assertLessEqual(south, latitude, f"{code} level {level}")
                self.assertLessEqual(latitude, north, f"{code} level {level}")
                self.assertLessEqual(west, longitude, f"{code} level {level}")
                self.assertLessEqual(longitude, east, f"{code} level {level}")

    def test_a_cell_contains_the_finer_cell_inside_it(self):
        for code in PLACES:
            for level in range(1, 10):
                outer = cell_box(GPC, code, level)
                inner = cell_box(GPC, code, level + 1)
                self.assertLessEqual(outer[0], inner[0])
                self.assertLessEqual(outer[1], inner[1])
                self.assertGreaterEqual(outer[2], inner[2])
                self.assertGreaterEqual(outer[3], inner[3])

    def test_the_boxes_are_the_documented_size(self):
        # Level 1 is 45 by 60 degrees. If this drifts, the grid has changed.
        south, west, north, east = cell_box(GPC, "G3RJM98NM9", 1)
        self.assertAlmostEqual(north - south, 45.0, places=9)
        self.assertAlmostEqual(east - west, 60.0, places=9)

    def test_a_formatted_code_works_too(self):
        self.assertEqual(cell_box(GPC, "#G3RJM-98NM9", 7),
                         cell_box(GPC, "G3RJM98NM9", 7))


if __name__ == "__main__":
    unittest.main()
