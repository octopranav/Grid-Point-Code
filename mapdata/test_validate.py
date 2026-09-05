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

"""The validator, against data carrying each way this goes wrong.

    python -m unittest discover --start-directory mapdata --top-level-directory mapdata

A checker nobody has watched fail is a checker nobody knows works, so every
failure it claims to catch is fed to it here. The fixtures are in `examples/`
and are also what the README points a reader at.
"""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from validate import GPC, TAG, parse, validate  # noqa: E402

EXAMPLES = HERE / "examples"
CN_TOWER = (43.6426, -79.3871)


def written(body, suffix):
    """A fixture on disk, since the reader takes a path like a real caller."""
    handle = tempfile.NamedTemporaryFile("w", suffix=suffix, delete=False,
                                         encoding="utf-8")
    handle.write(body)
    handle.close()
    return handle.name


def feature(code, latitude, longitude, name="somewhere", geometry="Point"):
    shape = {"type": "Point", "coordinates": [longitude, latitude]}
    if geometry == "Polygon":
        shape = {"type": "Polygon",
                 "coordinates": [[[longitude, latitude], [longitude + 0.01, latitude],
                                  [longitude, latitude + 0.01], [longitude, latitude]]]}
    return written(json.dumps({
        "type": "FeatureCollection",
        "features": [{"type": "Feature",
                      "properties": {"name": name, TAG: code},
                      "geometry": shape}],
    }), ".geojson")


class TheGoodCase(unittest.TestCase):

    def test_the_shipped_example_is_clean(self):
        tagged, findings = validate(EXAMPLES / "good.geojson")
        self.assertEqual(tagged, 3)
        self.assertEqual([str(f) for f in findings], [])

    def test_a_code_matching_its_own_point_passes(self):
        code = GPC.encode(*CN_TOWER)
        tagged, findings = validate(feature(code, *CN_TOWER))
        self.assertEqual((tagged, findings), (1, []))

    def test_the_bare_form_is_accepted_too(self):
        # The tag ought to carry the formatted code, but a file that does not
        # is not wrong about where the thing is, and this checks position.
        code = GPC.encode(*CN_TOWER, False)
        _, findings = validate(feature(code, *CN_TOWER))
        self.assertEqual(findings, [])

    def test_nothing_tagged_is_not_a_failure(self):
        empty = written(json.dumps({"type": "FeatureCollection", "features": [
            {"type": "Feature", "properties": {"name": "untagged"},
             "geometry": {"type": "Point", "coordinates": [0, 0]}}]}), ".geojson")
        self.assertEqual(validate(empty), (0, []))


class TheWaysItGoesWrong(unittest.TestCase):

    def test_a_moved_node_is_caught(self):
        # The node was corrected by a later survey; the tag was not.
        code = GPC.encode(*CN_TOWER)
        _, findings = validate(feature(code, CN_TOWER[0] + 0.00036, CN_TOWER[1]))
        self.assertEqual(len(findings), 1)
        self.assertIn("m away", str(findings[0]))

    def test_a_code_from_somewhere_else_is_caught(self):
        code = GPC.encode(*CN_TOWER)
        _, findings = validate(feature(code, 51.5007, -0.1246))
        self.assertEqual(len(findings), 1)
        # And the distance says which kind of mistake it was.
        self.assertIn("km" if "km" in str(findings[0]) else "m", str(findings[0]))

    def test_a_code_that_is_not_a_code_is_caught(self):
        # Q is not in the alphabet and is not aliased to anything.
        _, findings = validate(feature("#G3RJM-0M6DQ", *CN_TOWER))
        self.assertEqual(len(findings), 1)
        self.assertIn("GPC_CHAR", str(findings[0]))

    def test_a_code_on_a_shape_is_caught(self):
        code = GPC.encode(*CN_TOWER)
        _, findings = validate(feature(code, *CN_TOWER, geometry="Polygon"))
        self.assertEqual(len(findings), 1)
        self.assertIn("names a point", str(findings[0]))

    def test_the_shipped_wrong_example_catches_all_four(self):
        tagged, findings = validate(EXAMPLES / "wrong.geojson")
        self.assertEqual(tagged, 4)
        self.assertEqual(len(findings), 4)

    def test_axes_the_wrong_way_round_are_caught(self):
        # GeoJSON is longitude first. Swapped, this is a code for a real place
        # a long way away rather than an error, which is why it needs a check.
        code = GPC.encode(*CN_TOWER)
        swapped = written(json.dumps({
            "type": "FeatureCollection",
            "features": [{"type": "Feature", "properties": {TAG: code},
                          "geometry": {"type": "Point",
                                       "coordinates": [CN_TOWER[0], CN_TOWER[1]]}}],
        }), ".geojson")
        _, findings = validate(swapped)
        self.assertEqual(len(findings), 1)


class Tolerance(unittest.TestCase):

    def test_drift_is_refused_by_default(self):
        code = GPC.encode(*CN_TOWER)
        _, findings = validate(feature(code, CN_TOWER[0] + 0.00036, CN_TOWER[1]))
        self.assertEqual(len(findings), 1)

    def test_drift_can_be_allowed(self):
        # The same file, with a survey's worth of slack. 38 m of drift is
        # accepted at 50 and not at 10, which is the point of the option.
        code = GPC.encode(*CN_TOWER)
        moved = feature(code, CN_TOWER[0] + 0.00036, CN_TOWER[1])
        self.assertEqual(validate(moved, tolerance=50.0)[1], [])
        self.assertEqual(len(validate(moved, tolerance=10.0)[1]), 1)


class OpenStreetMap(unittest.TestCase):

    def test_nodes_are_read(self):
        tagged, findings = validate(EXAMPLES / "extract.osm")
        self.assertEqual(tagged, 4)
        # Three good nodes and one way that should not carry a code at all.
        self.assertEqual(len(findings), 1)
        self.assertIn("way 500", str(findings[0]))

    def test_a_way_is_refused_even_when_its_code_would_be_right(self):
        # There is no position to check it against, and no way to know which
        # point of the way was meant. That is the convention, not a limitation.
        _, findings = validate(EXAMPLES / "extract.osm")
        self.assertIn("names a point", str(findings[0]))


class Arguments(unittest.TestCase):

    def test_options_and_files_are_told_apart(self):
        files, options = parse(["a.geojson", "--tag", "gpc"], {"--tag"})
        self.assertEqual(files, ["a.geojson"])
        self.assertEqual(options, {"--tag": "gpc"})

    def test_a_file_named_like_an_option_value_survives(self):
        # The earlier version picked files by excluding anything equal to an
        # option's value, so this lost the file called 5 and said nothing.
        files, options = parse(["5", "b.geojson", "--tolerance", "5"],
                               {"--tag", "--tolerance"})
        self.assertEqual(files, ["5", "b.geojson"])
        self.assertEqual(options["--tolerance"], "5")

    def test_a_trailing_option_with_nothing_after_it(self):
        files, options = parse(["a.geojson", "--tag"], {"--tag"})
        self.assertEqual(files, ["a.geojson"])
        self.assertIsNone(options["--tag"])


    def test_the_key_can_be_changed(self):
        code = GPC.encode(*CN_TOWER)
        body = json.dumps({"type": "FeatureCollection", "features": [
            {"type": "Feature", "properties": {"gpc": code},
             "geometry": {"type": "Point", "coordinates": [CN_TOWER[1], CN_TOWER[0]]}}]})
        path = written(body, ".geojson")
        self.assertEqual(validate(path)[0], 0)          # not under the default key
        self.assertEqual(validate(path, tag="gpc")[0], 1)

    def test_an_unknown_format_is_refused(self):
        with self.assertRaises(SystemExit):
            validate(written("nothing", ".txt"))


if __name__ == "__main__":
    unittest.main()
