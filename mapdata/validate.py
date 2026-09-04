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

"""Check that a code written into map data still says where the thing is.

    python mapdata/validate.py extract.osm
    python mapdata/validate.py places.geojson --tolerance 5

A code in a data file is a copy of a coordinate, and copies go stale. The node
gets moved by three metres in a later survey, the coordinate changes, and the
tag does not -- so the file now carries two answers and nothing in it says which
one is older. Nobody notices, because a stale code is still a perfectly
well-formed code that decodes to a perfectly plausible place.

That is the whole reason this exists. See README.md for the convention itself,
including the case for not writing the code down at all.

Reads OpenStreetMap XML and GeoJSON, because between them they cover what people
actually hand each other. Neither needs a library: both are in the standard one.
"""

from __future__ import annotations

import json
import os
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

SOURCE = os.environ.get("GPC_PYTHON_PATH") or str(
    Path(__file__).resolve().parent.parent / "python" / "src"
)
sys.path.insert(0, SOURCE)

from gridpointcode_algo_pranavpatel_ca import GPC  # noqa: E402
from gridpointcode_algo_pranavpatel_ca.errors import GPCError  # noqa: E402

#: The proposed key. See README.md -- this project does not get to decide what
#: OpenStreetMap tags things, and says so.
TAG = "ref:gridpointcode"


class Finding:
    """One thing wrong with one element."""

    def __init__(self, where, kind, said, detail=""):
        self.where = where
        self.kind = kind
        self.said = said
        self.detail = detail

    def __str__(self):
        line = f"{self.where}: {self.kind}"
        if self.said:
            line += f" {self.said}"
        if self.detail:
            line += f" -- {self.detail}"
        return line


def _check(where, code, latitude, longitude, tolerance):
    """One tagged point against its own coordinates."""
    try:
        payload = GPC.cell(code, 10)
    except GPCError as error:
        return Finding(where, "is not a code:", code, error.reason)
    except Exception as error:                              # noqa: BLE001
        return Finding(where, "is not a code:", code, type(error).__name__)

    try:
        actual = GPC.encode(latitude, longitude, False)
    except GPCError as error:
        return Finding(where, "has no usable position:", "", error.reason)

    if payload == actual:
        return None

    # A code names a cell, so a tag one cell out is a real disagreement rather
    # than a rounding artefact. The distance is what makes it possible to tell
    # a moved node from a copied-in code for somewhere else entirely.
    apart = GPC.distance(payload, actual)
    if apart <= tolerance:
        return None

    return Finding(
        where,
        "says",
        GPC.format_gpc(payload),
        f"but its position is {GPC.format_gpc(actual)}, {apart:,.1f} m away",
    )


def from_osm(path, tag, tolerance):
    """OpenStreetMap XML. Nodes carry the position; ways and relations do not."""
    root = ElementTree.parse(path).getroot()
    findings = []
    tagged = 0

    for element in root:
        if element.tag not in ("node", "way", "relation"):
            continue

        tags = {t.get("k"): t.get("v") for t in element.findall("tag")}
        if tag not in tags:
            continue

        tagged += 1
        where = f"{element.tag} {element.get('id')}"

        if element.tag != "node":
            # A code names one cell of 2.56 m. On a way or a relation it names
            # one arbitrary point of something that is not a point, and there
            # is no way to tell which point was meant.
            findings.append(Finding(
                where, "is tagged with a code, but a code names a point", "",
                "put it on a node, or leave it off"))
            continue

        finding = _check(where, tags[tag], float(element.get("lat")),
                         float(element.get("lon")), tolerance)
        if finding:
            findings.append(finding)

    return tagged, findings


def from_geojson(path, tag, tolerance):
    """GeoJSON. Only Point features can carry one, for the same reason."""
    with open(path, encoding="utf-8") as handle:
        document = json.load(handle)

    features = document.get("features", []) if document.get("type") == "FeatureCollection" \
        else [document]

    findings = []
    tagged = 0

    for at, feature in enumerate(features):
        properties = feature.get("properties") or {}
        if tag not in properties:
            continue

        tagged += 1
        name = properties.get("name") or feature.get("id") or f"feature {at}"
        where = str(name)

        geometry = feature.get("geometry") or {}
        if geometry.get("type") != "Point":
            findings.append(Finding(
                where, f"is a {geometry.get('type') or 'feature with no geometry'} "
                       "carrying a code, but a code names a point", "",
                "use the point it refers to, or leave it off"))
            continue

        # GeoJSON is longitude first. Getting this backwards produces a code
        # for somewhere real, which is why it is worth saying out loud.
        longitude, latitude = geometry["coordinates"][0], geometry["coordinates"][1]

        finding = _check(where, properties[tag], latitude, longitude, tolerance)
        if finding:
            findings.append(finding)

    return tagged, findings


def validate(path, tag=TAG, tolerance=0.0):
    """Every tagged element in the file. Returns (how many, what is wrong)."""
    path = Path(path)
    if path.suffix.lower() in (".osm", ".xml"):
        return from_osm(path, tag, tolerance)
    if path.suffix.lower() in (".geojson", ".json"):
        return from_geojson(path, tag, tolerance)
    raise SystemExit(f"{path.name}: expected .osm, .xml, .geojson or .json")


def main():
    argv = sys.argv[1:]

    def option(name, fallback=None):
        if name not in argv:
            return fallback
        at = argv.index(name)
        return argv[at + 1] if at + 1 < len(argv) else fallback

    files = [one for one in argv if not one.startswith("--")
             and one not in (option("--tag"), option("--tolerance"))]
    if not files:
        print(__doc__.strip().splitlines()[0], file=sys.stderr)
        print("\n    python mapdata/validate.py <file> [--tag KEY] "
              "[--tolerance METRES]", file=sys.stderr)
        return 2

    tag = option("--tag", TAG)
    tolerance = float(option("--tolerance", "0"))

    total = 0
    wrong = []

    for one in files:
        tagged, findings = validate(one, tag, tolerance)
        total += tagged
        wrong.extend(findings)

    if not total:
        print(f"nothing carries {tag}")
        return 0

    for finding in wrong:
        print(f"  {finding}", file=sys.stderr)

    if wrong:
        print(
            f"\n{len(wrong)} of {total} tagged element"
            f"{'' if total == 1 else 's'} disagree with their own position."
            "\nA code copied into a file is a coordinate that stopped being "
            "checked; this is the check.",
            file=sys.stderr,
        )
        return 1

    print(f"all {total} tagged element{'' if total == 1 else 's'} agree with "
          "their own position")
    return 0


if __name__ == "__main__":
    sys.exit(main())
