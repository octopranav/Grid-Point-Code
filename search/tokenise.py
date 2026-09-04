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

"""Making a place findable by a search engine that knows nothing about places.

    python search/tokenise.py '#G3RJM-98NM9'

A full-text index matches terms. A code's prefixes name nested cells, so
indexing the prefixes as terms turns "what is near here" into an ordinary term
query -- no geo type, no spatial index, no plugin, and nothing the engine has
to understand about the earth.

**The part that is easy to get wrong.** Searching for the cell a point falls in
does not find everything near that point, because a neighbour a metre away can
sit on the other side of a boundary and share no prefix at all. Measured over
20,000 random pairs 100 m apart, a query for the level-7 cell alone found 63.6 %
of them. The fix is to search the cell *and its eight neighbours*, which found
100 %.

That is why this module has two sides that are not symmetrical: `terms_for`
returns what to put in the index, and `query_for` returns what to search for.
Anyone who uses the first for both will have a search that works in testing and
loses things in the field.

The choice of level then decides how much the engine has to sift, and there is
one rule: **the cell must be at least as large as the search radius.** Below
that the nine cells do not cover the circle and documents go missing however
many neighbours are added. `level_for` picks it, and accounts for cells getting
narrower toward the poles.
"""

from __future__ import annotations

import math
import os
import sys
from pathlib import Path

SOURCE = os.environ.get("GPC_PYTHON_PATH") or str(
    Path(__file__).resolve().parent.parent / "python" / "src"
)
sys.path.insert(0, SOURCE)

from gridpointcode_algo_pranavpatel_ca import GPC  # noqa: E402

LEVELS = 10

#: The finest level whose cell is still worth a term of its own. Levels 9 and
#: 10 name a doorway and a footstep: a term for either is unique to one
#: document, which is a term the index carries and no query ever asks for.
FINEST = 8

#: The coarsest. Level 1 is a sixth of the world and matches almost everything,
#: so it costs an index entry to narrow nothing down.
COARSEST = 3


def terms_for(code: str, coarsest: int = COARSEST, finest: int = FINEST) -> list[str]:
    """What to put in the index for a document at `code`.

    One term per level: the cell containing the point at that level. A document
    in `G3RJM98NM9` is indexed under `G3R`, `G3RJ`, `G3RJM` and so on, so a
    query at any level is an exact term match.

    Reserved codes have no place in a spatial index -- they name nothing -- and
    raise rather than being indexed under a cell that does not exist.
    """
    if coarsest < 1 or finest > LEVELS or coarsest > finest:
        raise ValueError(f"levels {coarsest} to {finest} are not a range")

    return [GPC.cell(code, level) for level in range(coarsest, finest + 1)]


def query_for(code: str, level: int) -> list[str]:
    """What to search for, to find everything near `code` at `level`.

    The cell and its eight neighbours.

    Across the antimeridian this wraps, because the earth does: the cell at
    180 degrees west lists two on the far side of the line as neighbours, and a
    search there finds what is a hundred metres away rather than half a world
    of nothing. At the poles it does not, because there is no cell above the
    north pole, and the list comes back short rather than naming one that does
    not exist.

    These are terms to be combined with OR. Any document carrying one of them is
    within one cell of the query point; anything further away carries none.
    """
    centre = GPC.cell(code, level)
    return [centre] + list(GPC.neighbours(centre))


def query_at(latitude: float, longitude: float, level: int) -> list[str]:
    """`query_for`, from a coordinate rather than a code."""
    return query_for(GPC.encode(latitude, longitude, False), level)


def level_for(radius_metres: float, latitude: float = 0.0) -> int:
    """The finest level whose cells still cover a circle of that radius.

    Finer is better -- it is fewer documents for the engine to sift -- but only
    down to the level where the nine cells stop covering the circle. A point
    can sit anywhere in its cell, so the query must reach one whole cell beyond
    it in every direction, which the eight neighbours do exactly. The cell must
    therefore be at least the radius.

    East-west is the binding direction, because a cell narrows toward the poles
    by the cosine of the latitude while its height does not change. At 60° a
    cell is half the width it is at the equator, and a level chosen from the
    equatorial figure would quietly stop covering the circle.
    """
    if radius_metres <= 0:
        raise ValueError("a radius has to be positive")

    narrowing = math.cos(math.radians(min(abs(latitude), 89.0)))

    for level in range(FINEST, COARSEST - 1, -1):
        _, _, north_south, east_west = GPC.cell_dimensions(level)
        if min(north_south, east_west * narrowing) >= radius_metres:
            return level

    return COARSEST


def _main() -> int:
    if len(sys.argv) < 2:
        print(__doc__.strip().splitlines()[0], file=sys.stderr)
        print(f"\n    python {Path(__file__).name} '#G3RJM-98NM9' [radius]",
              file=sys.stderr)
        return 2

    code = GPC.cell(sys.argv[1], 10)
    radius = float(sys.argv[2]) if len(sys.argv) > 2 else 100.0
    latitude, longitude = GPC.decode(code)
    level = level_for(radius, latitude)

    print(f"{GPC.format_gpc(code)}  at {latitude}, {longitude}")
    print(f"\nindex these {len(terms_for(code))} terms:")
    print("   ", " ".join(terms_for(code)))
    print(f"\nto find everything within {radius:g} m, search level {level} "
          f"({GPC.cell_dimensions(level)[2]:,.0f} m cells) for any of:")
    print("   ", " ".join(query_for(code, level)))
    return 0


if __name__ == "__main__":
    sys.exit(_main())
