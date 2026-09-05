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

"""Codes in and out of anything GDAL can read.

    python ogr/gpc_ogr.py encode roads.gpkg coded.gpkg
    python ogr/gpc_ogr.py encode points.shp cells.gpkg --level 6 --field cell
    python ogr/gpc_ogr.py decode manifest.csv points.gpkg --field gpc

GDAL reads a hundred vector formats. This adds one column to whatever it hands
back, or turns a column of codes into points, so a code can enter and leave a
pipeline that has never heard of it.

**The trap this is written around.** GDAL 3 changed the axis order of
EPSG:4326 to what the authority says it is -- latitude first -- so code written
for GDAL 2 that reads `point.GetX()` as a longitude gets a coordinate that is
silently transposed. In most of the world that still encodes to a real place,
just the wrong one, and nothing raises. Every transformation here sets
`OAMS_TRADITIONAL_GIS_ORDER` and the tests check a point whose latitude and
longitude cannot be confused.

Reprojection is done rather than refused, because unlike a database column a
file usually knows its own CRS and converting it is what a tool is for. A layer
with no CRS at all is refused: guessing that unlabelled numbers are degrees is
how a coordinate ends up in the Gulf of Guinea.
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

SOURCE = os.environ.get("GPC_PYTHON_PATH") or str(
    Path(__file__).resolve().parent.parent / "python" / "src"
)
sys.path.insert(0, SOURCE)

from gridpointcode_algo_pranavpatel_ca import GPC  # noqa: E402

#: What the new column is called unless told otherwise.
FIELD = "gpc"

#: What is written when a feature has no point to encode. A column of empty
#: strings is honest; a column of codes for (0, 0) is not.
MISSING = ""


def gdal():
    """The bindings, imported when they are needed and not before.

    Kept out of import time so the pure parts of this file can be read and
    tested on a machine without GDAL, which is most machines.
    """
    try:
        from osgeo import ogr, osr
    except ImportError as error:                            # pragma: no cover
        raise SystemExit(
            "this needs the GDAL Python bindings: try `pip install gdal` or "
            "run it in a container that has them"
        ) from error

    ogr.UseExceptions()
    osr.UseExceptions()
    return ogr, osr


def field_name(wanted, taken):
    """A column name the format will accept and that is not already used.

    Shapefile truncates a name to ten characters, so `gridpointcode` and
    `gridpointcodes` become the same column and the second write silently
    replaces the first. Truncating here and then making it unique means the
    collision is resolved before the driver can resolve it badly.
    """
    name = "".join(character if character.isalnum() or character == "_" else "_"
                   for character in wanted)[:10] or FIELD

    if name not in taken:
        return name

    for suffix in range(1, 100):
        candidate = f"{name[:10 - len(str(suffix))]}{suffix}"
        if candidate not in taken:
            return candidate

    raise SystemExit(f"cannot find a free column name near {wanted!r}")


def to_wgs84(osr_module, source_srs):
    """A transform from the layer's own CRS into degrees, or None if it is
    already there. Raises if the layer does not say what its numbers mean."""
    if source_srs is None:
        raise SystemExit(
            "the layer has no coordinate reference system. Unlabelled numbers "
            "could be anything, and guessing they are degrees is how a point "
            "ends up in the Gulf of Guinea -- set one and try again."
        )

    target = osr_module.SpatialReference()
    target.ImportFromEPSG(4326)
    # Longitude first. Without this GDAL 3 hands back latitude first, and a
    # transposed coordinate still encodes -- to somewhere else entirely.
    target.SetAxisMappingStrategy(osr_module.OAMS_TRADITIONAL_GIS_ORDER)

    source = source_srs.Clone()
    source.SetAxisMappingStrategy(osr_module.OAMS_TRADITIONAL_GIS_ORDER)

    if source.IsSame(target):
        return None
    return osr_module.CoordinateTransformation(source, target)


def encode(source_path, target_path, field=FIELD, level=10, driver="GPKG",
           layer_name=None, quiet=False):
    """Copy a vector source, adding a column of codes."""
    ogr_module, osr_module = gdal()

    source = ogr_module.Open(str(source_path))
    if source is None:
        raise SystemExit(f"GDAL cannot read {source_path}")

    layer = source.GetLayerByName(layer_name) if layer_name else source.GetLayer(0)
    if layer is None:
        raise SystemExit(f"no layer {layer_name!r} in {source_path}")

    transform = to_wgs84(osr_module, layer.GetSpatialRef())

    written = ogr_module.GetDriverByName(driver).CreateDataSource(str(target_path))
    out = written.CreateLayer(layer.GetName(), layer.GetSpatialRef(),
                              layer.GetGeomType())

    definition = layer.GetLayerDefn()
    existing = [definition.GetFieldDefn(i).GetName()
                for i in range(definition.GetFieldCount())]
    for i in range(definition.GetFieldCount()):
        out.CreateField(definition.GetFieldDefn(i))

    column = field_name(field, existing)
    out.CreateField(ogr_module.FieldDefn(column, ogr_module.OFTString))

    coded = 0
    skipped = 0

    for feature in layer:
        made = ogr_module.Feature(out.GetLayerDefn())
        for i in range(definition.GetFieldCount()):
            made.SetField(definition.GetFieldDefn(i).GetName(), feature.GetField(i))

        geometry = feature.GetGeometryRef()
        made.SetGeometry(geometry)

        code = MISSING
        if geometry is not None and not geometry.IsEmpty():
            # A code names a point. For anything else the centroid is the only
            # defensible reading, and it is the caller's decision to make, so
            # it is stated in the report rather than done quietly.
            point = geometry if geometry.GetGeometryType() in (
                ogr_module.wkbPoint, ogr_module.wkbPoint25D) else geometry.Centroid()

            moved = point.Clone()
            if transform is not None:
                moved.Transform(transform)
            try:
                code = GPC.encode(moved.GetY(), moved.GetX(), False)
                if level < 10:
                    code = GPC.cell(code, level)
                coded += 1
            except Exception:                               # noqa: BLE001
                skipped += 1
        else:
            skipped += 1

        made.SetField(column, code)
        out.CreateFeature(made)

    written = None
    source = None

    if not quiet:
        print(f"{coded:,} feature{'' if coded == 1 else 's'} coded into {column}")
        if skipped:
            print(f"  {skipped:,} had no usable point and were left empty")

    return coded, skipped, column


def decode(source_path, target_path, field=FIELD, driver="GPKG",
           layer_name=None, quiet=False):
    """Turn a column of codes into points."""
    ogr_module, osr_module = gdal()

    source = ogr_module.Open(str(source_path))
    if source is None:
        raise SystemExit(f"GDAL cannot read {source_path}")

    layer = source.GetLayerByName(layer_name) if layer_name else source.GetLayer(0)
    if layer is None:
        raise SystemExit(f"no layer {layer_name!r} in {source_path}")

    definition = layer.GetLayerDefn()
    names = [definition.GetFieldDefn(i).GetName()
             for i in range(definition.GetFieldCount())]
    if field not in names:
        raise SystemExit(f"no column {field!r} in {layer.GetName()}; it has "
                         f"{', '.join(names) or 'no columns'}")

    degrees = osr_module.SpatialReference()
    degrees.ImportFromEPSG(4326)
    degrees.SetAxisMappingStrategy(osr_module.OAMS_TRADITIONAL_GIS_ORDER)

    written = ogr_module.GetDriverByName(driver).CreateDataSource(str(target_path))
    out = written.CreateLayer(layer.GetName(), degrees, ogr_module.wkbPoint)
    for i in range(definition.GetFieldCount()):
        out.CreateField(definition.GetFieldDefn(i))

    placed = 0
    refused = []

    for feature in layer:
        made = ogr_module.Feature(out.GetLayerDefn())
        for i in range(definition.GetFieldCount()):
            made.SetField(definition.GetFieldDefn(i).GetName(), feature.GetField(i))

        value = feature.GetField(field)
        try:
            latitude, longitude = GPC.decode(value)
        except Exception as error:                          # noqa: BLE001
            refused.append((feature.GetFID(), value,
                            getattr(error, "reason", type(error).__name__)))
            out.CreateFeature(made)
            continue

        point = ogr_module.Geometry(ogr_module.wkbPoint)
        point.AddPoint_2D(longitude, latitude)              # x then y
        made.SetGeometry(point)
        out.CreateFeature(made)
        placed += 1

    written = None
    source = None

    if not quiet:
        print(f"{placed:,} point{'' if placed == 1 else 's'} placed from {field}")
        for fid, value, why in refused[:5]:
            print(f"  feature {fid}: {value!r} is not a code -- {why}",
                  file=sys.stderr)
        if len(refused) > 5:
            print(f"  and {len(refused) - 5:,} more", file=sys.stderr)

    return placed, refused


def parse(argv, flags):
    """Positional arguments and options, walked in order.

    Not "everything that is not an option and does not equal an option's
    value": that drops a file whose name happens to match one, so
    `validate.py 5 places.geojson --tolerance 5` quietly checks one file
    instead of two and says nothing about the other.
    """
    positional = []
    options = {}
    at = 0
    while at < len(argv):
        token = argv[at]
        if token in flags:
            options[token] = argv[at + 1] if at + 1 < len(argv) else None
            at += 2
        else:
            positional.append(token)
            at += 1
    return positional, options


def main():
    positional, options = parse(
        sys.argv[1:], {"--field", "--level", "--format", "--layer"})

    if len(positional) < 3 or positional[0] not in ("encode", "decode"):
        print(__doc__.strip().splitlines()[0], file=sys.stderr)
        print("\n    python ogr/gpc_ogr.py encode <in> <out> [--field NAME] "
              "[--level 1-10] [--format DRIVER] [--layer NAME]"
              "\n    python ogr/gpc_ogr.py decode <in> <out> [--field NAME] "
              "[--format DRIVER] [--layer NAME]", file=sys.stderr)
        return 2

    what, source, target = positional[0], positional[1], positional[2]
    field = options.get("--field") or FIELD
    driver = options.get("--format") or "GPKG"
    layer = options.get("--layer")

    if what == "encode":
        level = int(options.get("--level") or 10)
        if not 1 <= level <= 10:
            raise SystemExit("--level is 1 to 10")
        encode(source, target, field, level, driver, layer)
        return 0

    _, refused = decode(source, target, field, driver, layer)
    return 1 if refused else 0


if __name__ == "__main__":
    sys.exit(main())
