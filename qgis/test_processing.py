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

"""The three algorithms, run through Processing exactly as a user runs them.

    python -m unittest discover --start-directory qgis --top-level-directory qgis

Skipped without QGIS, which is most machines, and run in the container. It goes
through `processing.run` and the algorithm ids rather than calling the classes
directly, because half of what can be wrong with a plugin is the registration:
an algorithm nobody can reach is not working, however correct its arithmetic.
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

try:
    from qgis.core import (
        QgsApplication, QgsCoordinateReferenceSystem, QgsFeature, QgsGeometry,
        QgsPointXY, QgsProcessingContext, QgsProcessingFeedback,
        QgsProcessingUtils, QgsVectorLayer,
    )
    HAS_QGIS = True
except ImportError:                                     # pragma: no cover
    HAS_QGIS = False

from gridpointcode_algo_pranavpatel_ca import GPC       # noqa: E402

CN_TOWER = (43.6426, -79.3871)

_app = None
_provider = None        # see setUpModule: this must outlive the call


def setUpModule():                                      # noqa: N802
    """One headless QGIS for the whole file."""
    global _app, _provider
    if not HAS_QGIS:
        return

    QgsApplication.setPrefixPath(os.environ.get("QGIS_PREFIX_PATH", "/usr"), True)
    _app = QgsApplication([], False)
    _app.initQgis()

    # Processing lives with the bundled plugins rather than in the library.
    for candidate in ("/usr/share/qgis/python/plugins",
                      "/usr/local/share/qgis/python/plugins"):
        if os.path.isdir(candidate) and candidate not in sys.path:
            sys.path.append(candidate)

    from processing.core.Processing import Processing

    Processing.initialize()

    from gridpointcode.provider import GridPointCodeProvider

    # Kept in a module variable, and that is not tidiness. The registry takes
    # the C++ side of the provider; nothing holds the Python side, so it is
    # collected, and every algorithm under it loses its Python class. What
    # comes back out of the registry is then a bare QgsProcessingAlgorithm:
    # groupId() answers '' and createInstance() builds something with no
    # parameters, which the registry rejects. The plugin does the same thing
    # by keeping self.provider, which is why a user never sees this.
    _provider = GridPointCodeProvider()
    QgsApplication.processingRegistry().addProvider(_provider)


def tearDownModule():                                   # noqa: N802
    global _app, _provider
    _provider = None
    if _app is not None:
        _app.exitQgis()
        _app = None


def points(crs="EPSG:4326", coordinates=((-79.3871, 43.6426),)):
    """An in-memory point layer. x first, as everything in QGIS is."""
    layer = QgsVectorLayer(f"Point?crs={crs}&field=name:string", "places", "memory")
    provider = layer.dataProvider()

    made = []
    for at, (x, y) in enumerate(coordinates):
        feature = QgsFeature(layer.fields())
        feature.setAttributes([f"point {at}"])
        feature.setGeometry(QgsGeometry.fromPointXY(QgsPointXY(x, y)))
        made.append(feature)

    provider.addFeatures(made)
    layer.updateExtents()
    return layer


def table(codes):
    """A layer with no geometry at all, which is the interesting input."""
    layer = QgsVectorLayer("None?field=gpc:string", "codes", "memory")
    provider = layer.dataProvider()

    made = []
    for code in codes:
        feature = QgsFeature(layer.fields())
        feature.setAttributes([code])
        made.append(feature)

    provider.addFeatures(made)
    return layer


def run(algorithm, parameters):
    """`processing.run`, with the output resolved back to a layer."""
    import processing

    context = QgsProcessingContext()
    feedback = QgsProcessingFeedback()
    parameters = dict(parameters)
    parameters.setdefault("OUTPUT", "TEMPORARY_OUTPUT")

    result = processing.run(algorithm, parameters, context=context, feedback=feedback)
    return QgsProcessingUtils.mapLayerFromString(result["OUTPUT"], context)


@unittest.skipUnless(HAS_QGIS, "needs QGIS")
class TheProvider(unittest.TestCase):

    def test_all_three_algorithms_are_reachable(self):
        registry = QgsApplication.processingRegistry()
        for name in ("encodepoints", "decodecodes", "codecells"):
            self.assertIsNotNone(registry.algorithmById(f"gridpointcode:{name}"),
                                 f"gridpointcode:{name} is not registered")

    def test_they_are_in_one_group(self):
        registry = QgsApplication.processingRegistry()
        algorithm = registry.algorithmById("gridpointcode:encodepoints")
        self.assertEqual(algorithm.groupId(), "gridpointcode")


@unittest.skipUnless(HAS_QGIS, "needs QGIS")
class PointsToCodes(unittest.TestCase):

    def test_a_point_gets_the_code_the_library_gives(self):
        out = run("gridpointcode:encodepoints",
                  {"INPUT": points(), "FIELD": "gpc", "LEVEL": 10})

        self.assertIsNotNone(out)
        feature = next(out.getFeatures())
        self.assertEqual(feature["gpc"], GPC.encode(*CN_TOWER, False))
        self.assertEqual(feature["name"], "point 0")

    def test_a_projected_layer_is_reprojected(self):
        # Web mercator metres. Encoding them unchanged would raise; encoding
        # them with the axes swapped would give a code for somewhere real.
        out = run("gridpointcode:encodepoints",
                  {"INPUT": points("EPSG:3857", ((-8837500.0, 5411500.0),)),
                   "FIELD": "gpc", "LEVEL": 10})

        got = next(out.getFeatures())["gpc"]
        self.assertLess(GPC.distance(got, GPC.encode(*CN_TOWER, False)), 500)

    def test_a_level_writes_the_cell(self):
        out = run("gridpointcode:encodepoints",
                  {"INPUT": points(), "FIELD": "cell", "LEVEL": 6})

        self.assertEqual(next(out.getFeatures())["cell"],
                         GPC.cell(GPC.encode(*CN_TOWER, False), 6))

    def test_the_output_is_in_degrees(self):
        out = run("gridpointcode:encodepoints",
                  {"INPUT": points("EPSG:3857", ((-8837500.0, 5411500.0),)),
                   "FIELD": "gpc", "LEVEL": 10})
        self.assertEqual(out.crs(), QgsCoordinateReferenceSystem("EPSG:4326"))


@unittest.skipUnless(HAS_QGIS, "needs QGIS")
class CodesToPoints(unittest.TestCase):

    def test_a_table_with_no_geometry_becomes_points(self):
        code = GPC.encode(*CN_TOWER, False)
        out = run("gridpointcode:decodecodes",
                  {"INPUT": table([code]), "FIELD": "gpc"})

        feature = next(out.getFeatures())
        point = feature.geometry().asPoint()
        # x is longitude. Reversed, this point is in the sea off Africa.
        self.assertEqual(GPC.encode(point.y(), point.x(), False), code)

    def test_a_bad_code_keeps_its_row(self):
        code = GPC.encode(*CN_TOWER, False)
        out = run("gridpointcode:decodecodes",
                  {"INPUT": table([code, "NOTACODE12"]), "FIELD": "gpc"})

        features = list(out.getFeatures())
        self.assertEqual(len(features), 2)
        self.assertTrue(features[1].geometry().isEmpty())


@unittest.skipUnless(HAS_QGIS, "needs QGIS")
class CodesToCells(unittest.TestCase):

    def test_the_cell_contains_the_point_it_came_from(self):
        code = GPC.encode(*CN_TOWER, False)
        out = run("gridpointcode:codecells",
                  {"INPUT": table([code]), "FIELD": "gpc", "LEVEL": 10})

        box = next(out.getFeatures()).geometry().boundingBox()
        latitude, longitude = GPC.decode(code)
        self.assertTrue(box.contains(QgsPointXY(longitude, latitude)))

    def test_a_coarser_level_is_a_bigger_box(self):
        code = GPC.encode(*CN_TOWER, False)
        fine = next(run("gridpointcode:codecells",
                        {"INPUT": table([code]), "FIELD": "gpc", "LEVEL": 10}
                        ).getFeatures()).geometry().boundingBox()
        coarse = next(run("gridpointcode:codecells",
                          {"INPUT": table([code]), "FIELD": "gpc", "LEVEL": 6}
                          ).getFeatures()).geometry().boundingBox()

        self.assertGreater(coarse.width(), fine.width())
        self.assertTrue(coarse.contains(fine))


if __name__ == "__main__":
    unittest.main()
