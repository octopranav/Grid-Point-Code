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

"""The version 1 encoder, kept here and nowhere else.

Version 2 does not encode version 1 and no published package can: the old
format retires, and nobody mints a version 1 code by accident. The frozen
version 1 vectors still have to be reproducible from source, though, or the
job that regenerates the corpus and diffs it has nothing to run. So the
encoder lives here, beside the generator that is its only caller.

This is not part of any package. It is not published, not imported by any
port, and not something to translate into one. The decoder that ports do
carry is `v1.py` in the Python package, and Appendix B of SPEC.md describes
the format.

The decimal rule is the load-bearing part. Take the shortest decimal string
that reads back as the given double, then truncate it to five fractional
places. That recovers the decimal the caller actually wrote: exact-value
truncation would encode 43.65 as 43.64999, and rounding to nearest would
change codes wholesale.
"""

import sys
from decimal import Decimal
from pathlib import Path
from typing import List, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "python" / "src"))

from gridpointcode_algo_pranavpatel_ca.table import Table  # noqa: E402

MIN_LAT = -90
MAX_LAT = 90
MIN_LONG = -180
MAX_LONG = 180
CHARACTERS = "CDFGHJKLMNPRTVWXY0123456789"  # base27
ELEVEN = 205_881_132_094_649
MAX_WHOLE_LAT = 89
MAX_WHOLE_LONG = 179

LatLongTable = Table(180, 360, True)


def encode(latitude: float, longitude: float, formatted: bool = True) -> str:
    """Encode coordinates as an eleven-character version 1 code."""
    valid, message = is_valid_coordinates(latitude, longitude)
    if not valid:
        raise ValueError("%s: value out of valid range." % message)

    code = encode_point(get_point(latitude, longitude) + ELEVEN)
    return format_gpc(code) if formatted else code


def is_valid_coordinates(latitude: float, longitude: float) -> Tuple[bool, str]:
    """The version 1 domain, which excluded the poles and the antimeridian."""
    if not MIN_LAT < latitude < MAX_LAT:
        return False, "LATITUDE"
    if not MIN_LONG < longitude < MAX_LONG:
        return False, "LONGITUDE"
    return True, ""


def format_gpc(code: str) -> str:
    """The version 1 presentation, `#XXXX-XXXX-XXX`."""
    return "#%s-%s-%s" % (code[:4], code[4:8], code[8:11])


def get_point(latitude: float, longitude: float) -> int:
    """Both axes woven into one integer: a table index, then ten digits."""
    lat7 = split_to_7(latitude, MAX_WHOLE_LAT)
    long7 = split_to_7(longitude, MAX_WHOLE_LONG)

    point = int(10 ** 10 * (
        LatLongTable.GetIndexOfElements(
            (lat7[1] * 2) + (1 if lat7[0] == -1 else 0),
            (long7[1] * 2) + (1 if long7[0] == -1 else 0)
        ) + 1
    ))

    power = 9
    for i in range(2, 7):
        point += int(10 ** power * lat7[i])
        power -= 1
        point += int(10 ** power * long7[i])
        power -= 1
    return point


def split_to_7(coordinate: float, max_whole: int) -> List[int]:
    """Sign, whole degrees, and five decimal digits."""
    value = float(coordinate)
    # Negative zero and positive zero are the same point, so give them the
    # same sign and therefore the same code.
    if value == 0:
        value = 0.0
    coord = [0] * 7
    coord[0] = -1 if value < 0 else 1
    # The shortest decimal string that reads back as this double is the number
    # the caller wrote. Every port truncated that one string, so no two ports
    # could disagree about the digits.
    text = format(Decimal(repr(abs(value))), "f")
    fractional = ""

    if "." in text:
        integer_part, fractional_part = text.split(".")
        coord[1] = int(integer_part)
        fractional = fractional_part
    else:
        coord[1] = int(text)

    fractional = (fractional + "00000")[:5]
    coord[2:7] = [int(fractional[i]) for i in range(5)]

    # A coordinate just short of the limit can round up to the limit itself
    # while being formatted. Hold it in the last cell of the grid instead of
    # letting an out-of-domain whole part reach the table.
    if coord[1] > max_whole:
        coord[1] = max_whole
        coord[2:7] = [9] * 5

    return coord


def encode_point(point: int) -> str:
    """The base-27 spelling of a point."""
    code = ""
    while point > 0:
        code = CHARACTERS[point % 27] + code
        point //= 27
    return code
