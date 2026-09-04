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

"""Three Processing algorithms: points to codes, codes to points, codes to cells.

Every one of them reprojects into degrees first. A layer in a national grid or
in web mercator holds numbers that are not degrees, and encoding them anyway
gives a well-formed code for the wrong hemisphere -- which is a failure with no
symptom, so the transform is not optional and not the user's to remember.
"""

from qgis.core import (
    QgsCoordinateReferenceSystem,
    QgsCoordinateTransform,
    QgsFeature,
    QgsFeatureSink,
    QgsField,
    QgsFields,
    QgsGeometry,
    QgsPointXY,
    QgsProcessing,
    QgsProcessingAlgorithm,
    QgsProcessingException,
    QgsProcessingParameterEnum,
    QgsProcessingParameterFeatureSink,
    QgsProcessingParameterFeatureSource,
    QgsProcessingParameterField,
    QgsProcessingParameterNumber,
    QgsProcessingParameterString,
    QgsRectangle,
    QgsWkbTypes,
)
from qgis.PyQt.QtCore import QCoreApplication, QVariant

from .geometry import cell_box

WGS84 = "EPSG:4326"

def library():
    """The port, or a message a user can act on.

    A plugin that fails with ImportError deep inside a run tells somebody
    nothing. This is raised as a Processing exception, so it arrives in the log
    window with the one instruction that fixes it.
    """
    try:
        from gridpointcode_algo_pranavpatel_ca import GPC
    except ImportError as error:                            # pragma: no cover
        raise QgsProcessingException(
            "The Grid Point Code library is not installed in QGIS's Python. "
            "Open the Python console and run:\n\n"
            "    import pip; pip.main(['install', "
            "'gridpointcode-algo-pranavpatel-ca'])\n\n"
            "then restart QGIS."
        ) from error
    return GPC


class _Base(QgsProcessingAlgorithm):
    """Shared plumbing. QGIS asks for each of these by name."""

    def tr(self, string):
        return QCoreApplication.translate("GridPointCode", string)

    def group(self):
        return self.tr("Grid Point Code")

    def groupId(self):
        return "gridpointcode"

    def createInstance(self):
        return type(self)()

    def _degrees(self, crs, context):
        """A transform into degrees, or None when the layer is already there."""
        target = QgsCoordinateReferenceSystem(WGS84)
        if not crs.isValid():
            raise QgsProcessingException(
                "The layer has no coordinate reference system. Unlabelled "
                "numbers could be anything, and assuming they are degrees is "
                "how a point ends up in the Gulf of Guinea."
            )
        if crs == target:
            return None
        return QgsCoordinateTransform(crs, target, context.transformContext())


class EncodePoints(_Base):
    """A column of codes for a point layer."""

    INPUT = "INPUT"
    FIELD = "FIELD"
    LEVEL = "LEVEL"
    OUTPUT = "OUTPUT"

    def name(self):
        return "encodepoints"

    def displayName(self):
        return self.tr("Points to codes")

    def shortHelpString(self):
        return self.tr(
            "Adds a text column holding the Grid Point Code of each point.\n\n"
            "A level below 10 writes the cell containing the point instead of "
            "the full code, which is how you group points by area: every point "
            "in the same cell gets the same value, and sorting that column "
            "sorts them geographically."
        )

    def initAlgorithm(self, config=None):
        self.addParameter(QgsProcessingParameterFeatureSource(
            self.INPUT, self.tr("Points"), [QgsProcessing.TypeVectorPoint]))
        self.addParameter(QgsProcessingParameterString(
            self.FIELD, self.tr("Column name"), defaultValue="gpc"))
        self.addParameter(QgsProcessingParameterNumber(
            self.LEVEL, self.tr("Level (10 is the full code)"),
            type=QgsProcessingParameterNumber.Integer,
            defaultValue=10, minValue=1, maxValue=10))
        self.addParameter(QgsProcessingParameterFeatureSink(
            self.OUTPUT, self.tr("Coded")))

    def processAlgorithm(self, parameters, context, feedback):
        GPC = library()

        source = self.parameterAsSource(parameters, self.INPUT, context)
        if source is None:
            raise QgsProcessingException(self.tr("No input layer"))

        column = self.parameterAsString(parameters, self.FIELD, context) or "gpc"
        level = self.parameterAsInt(parameters, self.LEVEL, context)

        fields = QgsFields(source.fields())
        if fields.indexFromName(column) >= 0:
            raise QgsProcessingException(
                f"The layer already has a column called {column!r}. "
                "Choose another name rather than overwrite it."
            )
        fields.append(QgsField(column, QVariant.String, len=12))

        sink, target = self.parameterAsSink(
            parameters, self.OUTPUT, context, fields,
            QgsWkbTypes.Point, QgsCoordinateReferenceSystem(WGS84))

        transform = self._degrees(source.sourceCrs(), context)
        total = source.featureCount() or 1
        coded = 0
        empty = 0

        for at, feature in enumerate(source.getFeatures()):
            if feedback.isCanceled():
                break

            geometry = feature.geometry()
            made = QgsFeature(fields)
            attributes = list(feature.attributes())

            if geometry.isEmpty():
                made.setAttributes(attributes + [None])
                empty += 1
            else:
                if transform is not None:
                    geometry = QgsGeometry(geometry)
                    geometry.transform(transform)
                point = geometry.asPoint()
                try:
                    code = GPC.encode(point.y(), point.x(), False)
                    if level < 10:
                        code = GPC.cell(code, level)
                    coded += 1
                except Exception as error:                  # noqa: BLE001
                    feedback.pushWarning(
                        f"feature {feature.id()}: {getattr(error, 'reason', error)}")
                    code = None
                    empty += 1
                made.setAttributes(attributes + [code])
                made.setGeometry(geometry)

            sink.addFeature(made, QgsFeatureSink.FastInsert)
            feedback.setProgress(int(100 * at / total))

        feedback.pushInfo(f"{coded} coded, {empty} left empty")
        return {self.OUTPUT: target}


class DecodeCodes(_Base):
    """Points from a column of codes."""

    INPUT = "INPUT"
    FIELD = "FIELD"
    OUTPUT = "OUTPUT"

    def name(self):
        return "decodecodes"

    def displayName(self):
        return self.tr("Codes to points")

    def shortHelpString(self):
        return self.tr(
            "Reads a column of Grid Point Codes and puts a point at the centre "
            "of the cell each one names.\n\n"
            "The input needs no geometry at all: a spreadsheet of codes becomes "
            "a point layer. Rows whose code will not parse keep their "
            "attributes and get no geometry, so nothing is silently dropped."
        )

    def initAlgorithm(self, config=None):
        self.addParameter(QgsProcessingParameterFeatureSource(
            self.INPUT, self.tr("Table or layer"), [QgsProcessing.TypeVector]))
        self.addParameter(QgsProcessingParameterField(
            self.FIELD, self.tr("Column of codes"), parentLayerParameterName=self.INPUT,
            type=QgsProcessingParameterField.String))
        self.addParameter(QgsProcessingParameterFeatureSink(
            self.OUTPUT, self.tr("Points")))

    def processAlgorithm(self, parameters, context, feedback):
        GPC = library()

        source = self.parameterAsSource(parameters, self.INPUT, context)
        if source is None:
            raise QgsProcessingException(self.tr("No input layer"))

        column = self.parameterAsString(parameters, self.FIELD, context)
        at_column = source.fields().indexFromName(column)
        if at_column < 0:
            raise QgsProcessingException(f"No column called {column!r}")

        sink, target = self.parameterAsSink(
            parameters, self.OUTPUT, context, source.fields(),
            QgsWkbTypes.Point, QgsCoordinateReferenceSystem(WGS84))

        placed = 0
        refused = 0

        for feature in source.getFeatures():
            if feedback.isCanceled():
                break

            made = QgsFeature(source.fields())
            made.setAttributes(feature.attributes())

            value = feature.attributes()[at_column]
            try:
                latitude, longitude = GPC.decode(str(value))
                made.setGeometry(QgsGeometry.fromPointXY(
                    QgsPointXY(longitude, latitude)))
                placed += 1
            except Exception as error:                      # noqa: BLE001
                # Kept, without geometry. Dropping the row would lose the only
                # copy of whatever else was on it.
                feedback.pushWarning(
                    f"{value!r}: {getattr(error, 'reason', error)}")
                refused += 1

            sink.addFeature(made, QgsFeatureSink.FastInsert)

        feedback.pushInfo(f"{placed} placed, {refused} would not parse")
        return {self.OUTPUT: target}


class CodeCells(_Base):
    """The cell each code names, as a polygon."""

    INPUT = "INPUT"
    FIELD = "FIELD"
    LEVEL = "LEVEL"
    OUTPUT = "OUTPUT"

    def name(self):
        return "codecells"

    def displayName(self):
        return self.tr("Codes to cells")

    def shortHelpString(self):
        return self.tr(
            "Draws the cell each code names, at whatever level you ask for.\n\n"
            "This is the one that makes the format visible: a code is not a "
            "point but a box, and at level 10 that box is about 2.5 metres "
            "across. Drawing the boxes is how somebody sees what a code "
            "actually claims."
        )

    def initAlgorithm(self, config=None):
        self.addParameter(QgsProcessingParameterFeatureSource(
            self.INPUT, self.tr("Table or layer"), [QgsProcessing.TypeVector]))
        self.addParameter(QgsProcessingParameterField(
            self.FIELD, self.tr("Column of codes"), parentLayerParameterName=self.INPUT,
            type=QgsProcessingParameterField.String))
        self.addParameter(QgsProcessingParameterNumber(
            self.LEVEL, self.tr("Level"),
            type=QgsProcessingParameterNumber.Integer,
            defaultValue=10, minValue=1, maxValue=10))
        self.addParameter(QgsProcessingParameterFeatureSink(
            self.OUTPUT, self.tr("Cells")))

    def processAlgorithm(self, parameters, context, feedback):
        GPC = library()

        source = self.parameterAsSource(parameters, self.INPUT, context)
        if source is None:
            raise QgsProcessingException(self.tr("No input layer"))

        column = self.parameterAsString(parameters, self.FIELD, context)
        at_column = source.fields().indexFromName(column)
        if at_column < 0:
            raise QgsProcessingException(f"No column called {column!r}")

        level = self.parameterAsInt(parameters, self.LEVEL, context)

        sink, target = self.parameterAsSink(
            parameters, self.OUTPUT, context, source.fields(),
            QgsWkbTypes.Polygon, QgsCoordinateReferenceSystem(WGS84))

        drawn = 0

        for feature in source.getFeatures():
            if feedback.isCanceled():
                break

            value = str(feature.attributes()[at_column])
            try:
                south, west, north, east = cell_box(GPC, value, level)
            except Exception as error:                      # noqa: BLE001
                feedback.pushWarning(f"{value!r}: {getattr(error, 'reason', error)}")
                continue

            made = QgsFeature(source.fields())
            made.setAttributes(feature.attributes())
            made.setGeometry(QgsGeometry.fromRect(
                QgsRectangle(west, south, east, north)))
            sink.addFeature(made, QgsFeatureSink.FastInsert)
            drawn += 1

        feedback.pushInfo(f"{drawn} cells drawn at level {level}")
        return {self.OUTPUT: target}


ALGORITHMS = (EncodePoints, DecodeCodes, CodeCells)
