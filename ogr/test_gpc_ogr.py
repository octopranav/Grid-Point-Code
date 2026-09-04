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

"""The OGR tool, against real files.

    python -m unittest discover --start-directory ogr --top-level-directory ogr

The parts with no GDAL in them run anywhere. The rest is skipped without the
bindings and runs in the container, which is the only place this machine can
exercise them.

The test that earns its keep is `test_a_projected_layer_is_reprojected`. A
transposed or unprojected coordinate still encodes -- to a real place, in the
wrong hemisphere -- so nothing raises and nothing looks wrong. The only way to
catch it is to check the answer.
"""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from gpc_ogr import FIELD, GPC, decode, encode, field_name  # noqa: E402

try:
    from osgeo import ogr, osr
    ogr.UseExceptions()
    osr.UseExceptions()
    HAS_GDAL = True
except ImportError:                                     # pragma: no cover
    HAS_GDAL = False

# Somewhere whose latitude and longitude cannot be mistaken for each other:
# 43.6426 is a valid longitude and -79.3871 is a valid latitude, so a swap
# produces a code rather than an error.
CN_TOWER = (43.6426, -79.3871)


class FieldNames(unittest.TestCase):
    """No GDAL needed: this is the part that keeps a driver from mangling it."""

    def test_a_plain_name_is_left_alone(self):
        self.assertEqual(field_name("gpc", []), "gpc")

    def test_a_long_name_is_cut_to_what_shapefile_allows(self):
        # Shapefile truncates at ten. Doing it here means the collision is
        # resolved before the driver can resolve it by overwriting.
        self.assertEqual(len(field_name("gridpointcode_value", [])), 10)

    def test_a_taken_name_gets_out_of_the_way(self):
        self.assertNotEqual(field_name("gpc", ["gpc"]), "gpc")
        self.assertEqual(len(field_name("gridpointc", ["gridpointc"])), 10)

    def test_punctuation_is_replaced(self):
        self.assertEqual(field_name("ref:gpc", []), "ref_gpc")

    def test_an_empty_name_falls_back(self):
        self.assertEqual(field_name("", []), FIELD)


def _points(path, points, epsg=4326, driver="GPKG"):
    """A layer with one point per (x, y) given, in the CRS asked for."""
    srs = osr.SpatialReference()
    srs.ImportFromEPSG(epsg)
    srs.SetAxisMappingStrategy(osr.OAMS_TRADITIONAL_GIS_ORDER)

    source = ogr.GetDriverByName(driver).CreateDataSource(str(path))
    layer = source.CreateLayer("places", srs, ogr.wkbPoint)
    layer.CreateField(ogr.FieldDefn("name", ogr.OFTString))

    for at, (x, y) in enumerate(points):
        feature = ogr.Feature(layer.GetLayerDefn())
        feature.SetField("name", f"point {at}")
        geometry = ogr.Geometry(ogr.wkbPoint)
        geometry.AddPoint_2D(x, y)
        feature.SetGeometry(geometry)
        layer.CreateFeature(feature)

    source = None
    return path


def _read(path, field):
    source = ogr.Open(str(path))
    layer = source.GetLayer(0)
    rows = [(f.GetField(field), f.GetGeometryRef().Clone() if f.GetGeometryRef()
             else None) for f in layer]
    source = None
    return rows


@unittest.skipUnless(HAS_GDAL, "needs the GDAL Python bindings")
class Encoding(unittest.TestCase):

    def setUp(self):
        self.where = Path(tempfile.mkdtemp())

    def test_a_point_gets_the_code_the_library_gives(self):
        source = _points(self.where / "in.gpkg", [(CN_TOWER[1], CN_TOWER[0])])
        coded, skipped, column = encode(source, self.where / "out.gpkg", quiet=True)

        self.assertEqual((coded, skipped, column), (1, 0, "gpc"))
        self.assertEqual(_read(self.where / "out.gpkg", "gpc")[0][0],
                         GPC.encode(*CN_TOWER, False))

    def test_a_projected_layer_is_reprojected(self):
        # The same place in web mercator. Encoding the metres as though they
        # were degrees would fail outright; encoding them after a transform
        # that got the axis order wrong would give a code for somewhere real.
        # Only the right answer passes.
        x, y = -8837500.0, 5411500.0                # roughly the CN Tower
        source = _points(self.where / "merc.gpkg", [(x, y)], epsg=3857)
        encode(source, self.where / "out.gpkg", quiet=True)

        got = _read(self.where / "out.gpkg", "gpc")[0][0]
        # Within a couple of hundred metres of the real thing is enough to
        # prove the transform happened and the axes are the right way round.
        self.assertLess(GPC.distance(got, GPC.encode(*CN_TOWER, False)), 500)

    def test_a_layer_with_no_crs_is_refused(self):
        source = self.where / "bare.gpkg"
        written = ogr.GetDriverByName("GPKG").CreateDataSource(str(source))
        written.CreateLayer("places", None, ogr.wkbPoint)
        written = None

        with self.assertRaises(SystemExit):
            encode(source, self.where / "out.gpkg", quiet=True)

    def test_a_level_writes_the_cell(self):
        source = _points(self.where / "in.gpkg", [(CN_TOWER[1], CN_TOWER[0])])
        encode(source, self.where / "out.gpkg", level=6, quiet=True)

        got = _read(self.where / "out.gpkg", "gpc")[0][0]
        self.assertEqual(len(got), 6)
        self.assertEqual(got, GPC.cell(GPC.encode(*CN_TOWER, False), 6))

    def test_existing_columns_survive(self):
        source = _points(self.where / "in.gpkg", [(CN_TOWER[1], CN_TOWER[0])])
        encode(source, self.where / "out.gpkg", quiet=True)

        opened = ogr.Open(str(self.where / "out.gpkg"))
        feature = opened.GetLayer(0).GetNextFeature()
        self.assertEqual(feature.GetField("name"), "point 0")


@unittest.skipUnless(HAS_GDAL, "needs the GDAL Python bindings")
class Decoding(unittest.TestCase):

    def setUp(self):
        self.where = Path(tempfile.mkdtemp())

    def test_a_round_trip_comes_back_to_the_same_cell(self):
        source = _points(self.where / "in.gpkg", [(CN_TOWER[1], CN_TOWER[0])])
        encode(source, self.where / "coded.gpkg", quiet=True)
        placed, refused = decode(self.where / "coded.gpkg",
                                 self.where / "back.gpkg", quiet=True)

        self.assertEqual((placed, refused), (1, []))
        code, geometry = _read(self.where / "back.gpkg", "gpc")[0]
        # x is longitude. If this file had them the other way round the point
        # would be in the sea off west Africa and this would say so.
        self.assertEqual(GPC.encode(geometry.GetY(), geometry.GetX(), False), code)

    def test_a_bad_code_keeps_its_row_and_loses_its_geometry(self):
        # Dropping the row would lose the only copy of everything else on it.
        source = self.where / "codes.gpkg"
        written = ogr.GetDriverByName("GPKG").CreateDataSource(str(source))
        srs = osr.SpatialReference()
        srs.ImportFromEPSG(4326)
        layer = written.CreateLayer("codes", srs, ogr.wkbNone)
        layer.CreateField(ogr.FieldDefn("gpc", ogr.OFTString))
        for value in (GPC.encode(*CN_TOWER, False), "NOTACODE12"):
            feature = ogr.Feature(layer.GetLayerDefn())
            feature.SetField("gpc", value)
            layer.CreateFeature(feature)
        written = None

        placed, refused = decode(source, self.where / "out.gpkg", quiet=True)
        self.assertEqual(placed, 1)
        self.assertEqual(len(refused), 1)
        self.assertEqual(len(_read(self.where / "out.gpkg", "gpc")), 2)

    def test_a_missing_column_says_which_ones_there_are(self):
        source = _points(self.where / "in.gpkg", [(0.0, 0.0)])
        with self.assertRaises(SystemExit) as caught:
            decode(source, self.where / "out.gpkg", field="nope", quiet=True)
        self.assertIn("name", str(caught.exception))


if __name__ == "__main__":
    unittest.main()
