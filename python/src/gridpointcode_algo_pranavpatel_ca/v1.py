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

"""Version 1 decoding, kept so that every code ever issued still resolves.

Version 1 is a base-27 numeral over a different alphabet, eleven characters
long, and it carries no locality guarantee: two codes sharing four characters
can be nineteen thousand kilometres apart. It is frozen. There is no version 1
encoder here and there will not be one -- the format is readable, not
writable, and anyone who still needs to mint version 1 codes pins 1.1.x.

Nothing in this module is reached by a version 2 code. It is entered only when
`GPC.decode` sees eleven characters, or when a caller asks for `decode_v1`
outright. Appendix B of SPEC.md describes the format.
"""

import re
from typing import List, Tuple

from .errors import GPCError
from .table import Table

CHARACTERS = "CDFGHJKLMNPRTVWXY0123456789"  # base27, letters first, not ASCII order
CODE_LENGTH = 11
MIN_POINT = 10_000_000_000
MAX_POINT = 648_009_999_999_999
ELEVEN = 205_881_132_094_649  # the offset that makes every code exactly 11 characters

LatLongTable = Table(180, 360, True)


def clean(grid_point_code: str) -> str:
    """Upper-case and drop the separators. Version 1 has no alias table."""
    return re.sub(r"[#\-\s]", "", grid_point_code.upper())


def format_gpc(code: str) -> str:
    """The version 1 presentation, `#XXXX-XXXX-XXX`."""
    return "#%s-%s-%s" % (code[:4], code[4:8], code[8:11])


def decode(grid_point_code: str) -> Tuple[float, float]:
    """Decode an eleven-character version 1 code to its cell's corner.

    Version 1 returns the corner, not the centre. That differs from version 2
    by design: the value is the one every version 1 release has returned, and
    changing it would move every code ever issued.
    """
    if grid_point_code is None or not grid_point_code.strip():
        raise GPCError("GPC_NULL")

    code = clean(grid_point_code)

    valid, message = validate_code(code)
    if not valid:
        raise GPCError(message)

    point = to_point(code) - ELEVEN

    valid, message = validate_point(point)
    if not valid:
        raise GPCError(message)

    return to_coordinates(point)


def is_valid(grid_point_code: str) -> Tuple[bool, str]:
    """Whether a string is a version 1 code, and why not when it is not."""
    if grid_point_code is None:
        return False, "GPC_NULL"
    code = clean(grid_point_code)
    if not code:
        return False, "GPC_NULL"
    valid, message = validate_code(code)
    if not valid:
        return False, message
    return validate_point(to_point(code) - ELEVEN)


def validate_code(code: str) -> Tuple[bool, str]:
    """Length and alphabet, on an already cleaned code."""
    if len(code) != CODE_LENGTH:
        return False, "GPC_LENGTH"
    if any(character not in CHARACTERS for character in code):
        return False, "GPC_CHAR"
    return True, ""


def validate_point(point: int) -> Tuple[bool, str]:
    """Whether a decoded point falls inside the version 1 grid."""
    if point < MIN_POINT or point > MAX_POINT:
        return False, "GPC_RANGE"
    return True, ""


def to_point(code: str) -> int:
    """The base-27 value of an eleven-character code."""
    point = 0
    for character in code:
        point *= 27
        point += CHARACTERS.index(character)
    return point


def to_coordinates(point: int) -> Tuple[float, float]:
    """Split a point back into latitude and longitude."""
    latlong_index = point // 10 ** 10
    fractional = point - latlong_index * 10 ** 10

    lat7, long7 = split_to_7(latlong_index, fractional)

    power = 0
    temp_lat = 0
    temp_long = 0

    for i in range(6, 0, -1):
        temp_lat += lat7[i] * (10 ** power)
        temp_long += long7[i] * (10 ** power)
        power += 1

    return (temp_lat / 10 ** 5 * lat7[0], temp_long / 10 ** 5 * long7[0])


def split_to_7(index: int, fractional: int) -> Tuple[List[int], List[int]]:
    """Sign, whole degrees and five decimals, for each axis.

    The whole degrees come back through the combination table, which pairs an
    index with two doubled-and-offset whole values. The ten decimal digits of
    `fractional` alternate, latitude first.
    """
    lat7 = [0] * 7
    long7 = [0] * 7

    t_lat, t_long = LatLongTable.GetElementsAtIndex(index - 1)

    lat7[0] = -1 if t_lat % 2 else 1
    lat7[1] = (t_lat - 1) // 2 if lat7[0] == -1 else t_lat // 2

    long7[0] = -1 if t_long % 2 else 1
    long7[1] = (t_long - 1) // 2 if long7[0] == -1 else t_long // 2

    power = 9
    for i in range(2, 7):
        lat7[i] = (fractional // (10 ** power)) % 10
        power -= 1
        long7[i] = (fractional // (10 ** power)) % 10
        power -= 1

    return lat7, long7
