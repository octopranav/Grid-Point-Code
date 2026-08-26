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

"""Version 2 of the Grid Point Code format.

A code names one cell of a fixed grid laid over the Earth. Ten characters,
always. The first divides the world into 24 cells of 45 by 60 degrees; each of
the nine after it divides the cell named so far into 25 parts, five by five.
Two codes that begin with the same k characters therefore name points in the
same level-k cell -- containment, not correlation, so it holds for every pair
of points without exception.

The whole format is arithmetic. There are no ordering tables and no generated
constants: a serpentine at level 1, a Peano digit reflection below it, and one
parity reset entering level 6. Section numbers in the comments refer to
SPEC.md, which is the normative description and the thing to implement from.

Version 1 codes still decode, because codes end up on signs and in records and
removing that would orphan every one of them. `decode` dispatches on length --
ten characters is version 2, eleven is version 1 -- and `encode` emits version
2 only, so the old format cannot be minted again.
"""

import math
from typing import Tuple

from . import screen_list, v1
from .errors import GPCError

# Section 4. Twenty-five symbols, digits first so that the alphabet is
# ASCII-ascending and a plain string sort is a spatial sort. No vowel appears,
# so no English word can be spelled by a code.
ALPHABET = "0123456789CDFGHJKLMNPRTWX"

# Section 3.
CODE_LENGTH = 10
LEVELS = 10
RESET_LEVEL = 6  # section 5.3: both parity accumulators reset entering this level
P9 = 1_953_125   # 5 ** 9
P5 = 3_125       # 5 ** 5, the rows and columns inside one level-5 cell
R5 = 2_500       # 4 * 5^4, rows of level-5 cells
C5 = 3_750       # 6 * 5^4, columns of level-5 cells
ROWS = 4 * P9    # 7_812_500
COLS = 6 * P9    # 11_718_750

# Section 2.
MIN_LAT = -90.0
MAX_LAT = 90.0
MIN_LONG = -180.0
MAX_LONG = 180.0

# Section 9.
GEOMETRIC = "GEOMETRIC"
RESERVED = "RESERVED"
INVALID = "INVALID"

# Section 8. Exactly the letters that are not in the alphabet, less U, Q and Y,
# which are rejected rather than aliased. L is a real symbol and is never
# aliased to 1: it names a different cell, and aliasing it would make two
# different codes collide.
ALIASES = {"O": "0", "I": "1", "S": "5", "Z": "2",
           "B": "8", "A": "4", "E": "3", "V": "W"}

PREFIX = "#"
SEPARATOR = "-"
CHECK_MARK = "*"
DEGREE_SIGN = "\u00b0"

# Section 18. North, north-east, east, south-east, south, south-west, west,
# north-west. Rows increase northward, so north is +1.
NEIGHBOUR_STEPS = ((1, 0), (1, 1), (0, 1), (-1, 1),
                   (-1, 0), (-1, -1), (0, -1), (1, -1))

# Section 18.4. The distance constants, and section 18.5 the radius. They are
# the only physical quantities in the format; everything else is arithmetic.
M_PER_DEGREE_LAT = 111_132.0
M_PER_DEGREE_LONG = 111_319.49       # at the equator
EARTH_RADIUS = 6_371_008.8           # mean radius of WGS 84

# Section 17.2. Three symbols turn up by chance too often to warn about.
SCREEN_MIN = 4
# ASCII whitespace only. A routine that also stripped the Unicode spaces would
# accept in one port what another rejects, which is the whole thing the shared
# vectors exist to prevent.
WHITESPACE = " \t\n\v\f\r"

_T = 5  # the field element t, whose symbol index is 1 * 5 + 0. Section 14.2.


def _gf_add(x: int, y: int) -> int:
    """(a + b·t) + (c + d·t), elements indexed b·5 + a."""
    return ((x // 5 + y // 5) % 5) * 5 + ((x % 5 + y % 5) % 5)


def _gf_mul(x: int, y: int) -> int:
    """(a + b·t)(c + d·t) with t² = 4t + 3."""
    a, b = x % 5, x // 5
    c, d = y % 5, y // 5
    return ((a * d + b * c + 4 * b * d) % 5) * 5 + ((a * c + 3 * b * d) % 5)


def _powers_of_t() -> Tuple[int, ...]:
    """t¹ to t¹¹, the eleven check weights. Computed rather than transcribed."""
    weights = []
    x = 1
    for _ in range(11):
        x = _gf_mul(x, _T)
        weights.append(x)
    return tuple(weights)


WEIGHTS = _powers_of_t()


class GPC:
    """Encode coordinates to a Grid Point Code, and decode one back.

    Every method is static. Nothing here holds state.
    """

    #  PART 1 : ENCODE

    @staticmethod
    def encode(latitude: float, longitude: float, formatted: bool = True) -> str:
        """Encode coordinates as a version 2 Grid Point Code.

        Args:
            latitude (float): Latitude in decimal degrees, -90 to 90 inclusive.
            longitude (float): Longitude in decimal degrees, -180 to 180 inclusive.
            formatted (bool): True for `#XXXXX-XXXXX`, False for the bare ten
                characters. Both denote the same code.

        Returns:
            str: The code.

        Raises:
            GPCError: If either coordinate is outside the domain, NaN or infinite.
        """
        valid, message = GPC.is_valid_coordinates(latitude, longitude)
        if not valid:
            raise GPCError(message, message + ": value out of valid range.")

        row, col = GPC.to_grid(latitude, longitude)
        code = GPC.grid_to_code(row, col)
        return GPC.format_gpc(code) if formatted else code

    @staticmethod
    def is_valid_coordinates(latitude: float, longitude: float) -> Tuple[bool, str]:
        """Whether a coordinate pair is inside the domain, and which axis is not.

        The poles and both ends of the antimeridian are inside it; version 1
        rejected all of them. NaN and the infinities fail the comparisons and
        so are rejected here as well, in every language, without a separate
        test.

        Returns:
            tuple[bool, str]: Validity, and "LATITUDE", "LONGITUDE" or "".
        """
        if not MIN_LAT <= latitude <= MAX_LAT:
            return False, "LATITUDE"
        if not MIN_LONG <= longitude <= MAX_LONG:
            return False, "LONGITUDE"
        return True, ""

    @staticmethod
    def to_grid(latitude: float, longitude: float) -> Tuple[int, int]:
        """Coordinates to a row and column of the full grid. Section 5.1.

        Three floating-point operations per axis, associating left to right.
        They are the only floating-point arithmetic in the format, and section
        7 pins how they are evaluated: no reassociation, no fused multiply-add,
        no wider intermediate. Everything after this is integers.
        """
        if longitude == MAX_LONG:
            # The one case where two distinct inputs must give one code, so it
            # happens before any arithmetic that could no longer tell them apart.
            longitude = MIN_LONG

        row = math.floor((latitude + 90.0) * 7812500.0 / 180.0)
        col = math.floor((longitude + 180.0) * 11718750.0 / 360.0)

        # Catches latitude +90, and nothing else. It is what makes the poles
        # encode instead of indexing past the end of the grid.
        row = 0 if row < 0 else (ROWS - 1 if row > ROWS - 1 else row)
        col = 0 if col < 0 else (COLS - 1 if col > COLS - 1 else col)
        return row, col

    @staticmethod
    def grid_to_code(row: int, col: int) -> str:
        """A row and column to ten characters. Section 5.2.

        Level 1 is a serpentine over the 24 blocks, west to east, snaking
        northward. Levels 2 to 10 are a Peano digit reflection: each axis is
        mirrored according to the parity of the digits accumulated in the
        other, which is what puts consecutive codes in adjacent cells.
        """
        r1 = row // P9
        c1 = col // P9
        out = [ALPHABET[r1 * 6 + (c1 if r1 % 2 == 0 else 5 - c1)]]

        sr = r1
        sc = c1
        p = P9
        for level in range(2, LEVELS + 1):
            if level == RESET_LEVEL:
                # Section 5.3. Without this the last five characters would mean
                # something different in every level-5 cell, and the short form
                # would name nothing on its own.
                sr = 0
                sc = 0
            p //= 5
            r = (row // p) % 5
            c = (col // p) % 5
            # The order of these four statements is normative. R is decided
            # from sc before this level's c is added to it, and C from sr after
            # this level's r has been added. Reversing either is a different
            # format.
            big_r = r if sc % 2 == 0 else 4 - r
            sr += r
            big_c = c if sr % 2 == 0 else 4 - c
            sc += c
            out.append(ALPHABET[big_r * 5 + big_c])

        return "".join(out)

    @staticmethod
    def format_gpc(code: str) -> str:
        """The presentation form, `#XXXXX-XXXXX`. Section 5.4.

        The grouping is not arbitrary: the second group is exactly the short
        form, so a printed code shows its own local form.
        """
        return PREFIX + code[:5] + SEPARATOR + code[5:]

    #  PART 2 : DECODE

    @staticmethod
    def decode(grid_point_code: str) -> Tuple[float, float]:
        """Decode a code to the centre of the cell it names.

        Dispatches on length once the separators are stripped: ten characters
        is version 2, eleven is version 1. A code carrying a check character is
        always version 2, since version 1 has none.

        Args:
            grid_point_code (str): Formatted or unformatted, with or without a
                `*` check character.

        Returns:
            tuple[float, float]: Latitude and longitude, six decimal places.

        Raises:
            GPCError: With reason GPC_RESERVED for a well-formed code beginning
                with X, or one of the invalid reasons otherwise.
        """
        payload, check = GPC._split(grid_point_code)
        if check is None and len(payload) == v1.CODE_LENGTH:
            return v1.decode(payload)

        row, col = GPC.code_to_grid(GPC._geometric(grid_point_code))
        return (GPC._round6((2 * row + 1) * 1152 - 9_000_000_000),
                GPC._round6((2 * col + 1) * 1536 - 18_000_000_000))

    @staticmethod
    def decode_to_area(grid_point_code: str) -> Tuple[float, float, float, float]:
        """The boundaries of the cell a version 2 code names. Section 6.3.

        Returns:
            tuple[float, float, float, float]: South, west, north and east.

        Raises:
            GPCError: As `decode`. Version 1 codes have no area; they resolve
                to a corner and are not part of this grid.
        """
        row, col = GPC.code_to_grid(GPC._geometric(grid_point_code))
        return (row * 180.0 / 7812500.0 - 90.0,
                col * 360.0 / 11718750.0 - 180.0,
                (row + 1) * 180.0 / 7812500.0 - 90.0,
                (col + 1) * 360.0 / 11718750.0 - 180.0)

    @staticmethod
    def decode_v1(grid_point_code: str) -> Tuple[float, float]:
        """Decode an eleven-character version 1 code. Appendix B.

        `decode` reaches this on its own for anything eleven characters long.
        The explicit entry point is here for a caller that knows which format
        it holds and wants to say so.

        Version 1 returns the corner of its cell rather than the centre, which
        is what every version 1 release has returned.
        """
        return v1.decode(grid_point_code)

    @staticmethod
    def code_to_grid(code: str) -> Tuple[int, int]:
        """Ten characters back to a row and column. Section 6.1.

        The inverse of `grid_to_code`, character by character. Expects a
        normalised, geometric code.
        """
        i = ALPHABET.index(code[0])
        r1 = i // 6
        k = i % 6
        c1 = k if r1 % 2 == 0 else 5 - k

        row = r1
        col = c1
        sr = r1
        sc = c1
        for level in range(2, LEVELS + 1):
            if level == RESET_LEVEL:
                sr = 0
                sc = 0
            j = ALPHABET.index(code[level - 1])
            big_r = j // 5
            big_c = j % 5
            r = big_r if sc % 2 == 0 else 4 - big_r
            sr += r
            c = big_c if sr % 2 == 0 else 4 - big_c
            sc += c
            row = row * 5 + r
            col = col * 5 + c

        return row, col

    #  PART 3 : PARSE, CLASSIFY, CHECK

    @staticmethod
    def normalise(grid_point_code: str) -> Tuple[str, str]:
        """Case-fold, strip separators, apply the alias table. Section 8.

        Returns:
            tuple[str, str]: The payload, and the check character, which is
                None when the input carried no `*`. The check is returned
                however long it normalised: deciding whether it is acceptable
                belongs to `validate`.

        Raises:
            GPCError: GPC_NULL if there is nothing at all to parse.
        """
        payload, check = GPC._split(grid_point_code)
        return (GPC._alias(payload),
                None if check is None else GPC._alias(check))

    @staticmethod
    def validate(grid_point_code: str) -> Tuple[str, str]:
        """Classify a string and say why, if the answer is INVALID. Section 9.

        Returns:
            tuple[str, str]: One of GEOMETRIC, RESERVED or INVALID, and the
                reason code, which is empty for anything that is not INVALID.
                Reasons are tested in the order GPC_NULL, GPC_LENGTH, GPC_CHAR,
                GPC_CHECK.
        """
        try:
            code, check = GPC.normalise(grid_point_code)
        except GPCError as error:
            return INVALID, error.reason
        if len(code) != CODE_LENGTH:
            return INVALID, "GPC_LENGTH"
        if any(character not in ALPHABET for character in code):
            return INVALID, "GPC_CHAR"
        # A check that does not hold is not something to discard. A caller told
        # a code is valid has to be able to decode it.
        if check is not None and check != GPC._check_symbol(code):
            return INVALID, "GPC_CHECK"
        return (RESERVED if code[0] == "X" else GEOMETRIC), ""

    @staticmethod
    def classify(grid_point_code: str) -> str:
        """GEOMETRIC, RESERVED or INVALID. Section 9.

        A reserved code is well formed, begins with X, and names no cell. No
        encoded code can begin with X, so that space is reserved rather than
        wasted, and it is kept distinct from a typing error from the first
        release because it cannot be separated out later without breaking
        callers.
        """
        return GPC.validate(grid_point_code)[0]

    @staticmethod
    def is_valid(grid_point_code: str) -> bool:
        """Whether a string is a version 2 code that decodes.

        True for GEOMETRIC only. A reserved code is false, because it names no
        cell, and so is a version 1 code: `classify` describes this grid, and
        eleven characters are not part of it. `decode` still reads version 1,
        and `is_valid_v1` answers for it.
        """
        return GPC.validate(grid_point_code)[0] == GEOMETRIC

    @staticmethod
    def is_valid_v1(grid_point_code: str) -> Tuple[bool, str]:
        """Whether a string is a version 1 code, and why not when it is not.

        Returns:
            tuple[bool, str]: Validity, and GPC_NULL, GPC_LENGTH, GPC_CHAR,
                GPC_RANGE or "".
        """
        return v1.is_valid(grid_point_code)

    @staticmethod
    def check_character(grid_point_code: str) -> str:
        """The optional GF(25) check character for a code. Section 14.

        For voice, radio and paper. Written after a star, `#G3RJM-98NM9*T`.
        It detects every single-symbol error and every adjacent transposition,
        and it is not canonical: the ten-character form is what gets stored and
        interchanged, and this is never emitted unless asked for.

        Raises:
            GPCError: If the input is not ten symbols of the alphabet. A
                reserved code has a check character like any other.
        """
        code, _ = GPC.normalise(grid_point_code)
        if len(code) != CODE_LENGTH:
            raise GPCError("GPC_LENGTH")
        if any(character not in ALPHABET for character in code):
            raise GPCError("GPC_CHAR")
        return GPC._check_symbol(code)

    #  PART 4 : THE LOCALITY API

    @staticmethod
    def cell(grid_point_code: str, level: int) -> str:
        """The first `level` characters of a code, normalised. Section 18.1.

        A cell names a region: two codes lie in the same level-k cell exactly
        when they share their first k characters, so this is the region
        identifier the guarantee is about.

        Args:
            grid_point_code (str): A code, or a longer cell.
            level (int): 1 to 10.

        Returns:
            str: The cell, bare -- no `#`, no separator. Ten characters is a
                code and anything shorter is a region; presenting a cell as a
                code would break the fixed length the format is recognised by.

        Raises:
            GPCError: GPC_LEVEL for a level outside 1 to 10, GPC_LENGTH if the
                argument is shorter than the level asked for, GPC_RESERVED for
                a cell beginning with X, or one of the parsing reasons.
        """
        if not 1 <= level <= LEVELS:
            raise GPCError("GPC_LEVEL")
        code = GPC._cell(grid_point_code)
        if len(code) < level:
            raise GPCError("GPC_LENGTH")
        return code[:level]

    @staticmethod
    def contains(cell: str, grid_point_code: str) -> bool:
        """Whether a code lies inside a cell. Section 18.2.

        The prefix test, and nothing more. What section 10 buys is that this is
        a true geometric containment test rather than an approximation of one:
        no tolerance, no edge case at a boundary, and no pair of points on
        Earth for which the string answer and the geometric answer differ.
        """
        prefix = GPC._cell(cell)
        code = GPC._cell(grid_point_code)
        return len(code) >= len(prefix) and code[:len(prefix)] == prefix

    @staticmethod
    def neighbours(cell: str) -> list:
        """The cells sharing an edge or a corner, in order. Section 18.3.

        North, north-east, east, south-east, south, south-west, west,
        north-west. Columns wrap at the antimeridian; rows do not, because the
        grid ends at the poles, so a cell in the top or bottom row has five
        neighbours and the three that would lie off the grid are absent rather
        than empty.

        Returns:
            list[str]: Bare cells of the same length as the argument.
        """
        code = GPC._cell(cell)
        level, p, cell_row, cell_col = GPC._cell_grid(code)
        row_cells = 4 * 5 ** (level - 1)
        col_cells = 6 * 5 ** (level - 1)

        out = []
        for d_row, d_col in NEIGHBOUR_STEPS:
            row = cell_row + d_row
            if row < 0 or row >= row_cells:
                continue
            col = (cell_col + d_col + col_cells) % col_cells
            out.append(GPC.grid_to_code(row * p, col * p)[:level])
        return out

    @staticmethod
    def cell_dimensions(level: int) -> Tuple[float, float, float, float]:
        """How big a level-k cell is. Section 18.4.

        Returns:
            tuple[float, float, float, float]: The latitude span and longitude
                span in degrees, then the same two in metres. The north-south
                figure holds everywhere; the east-west one is the value at the
                equator and shrinks with the cosine of latitude, which is a
                multiplication left to the caller.
        """
        if not 1 <= level <= LEVELS:
            raise GPCError("GPC_LEVEL")
        divisor = 5 ** (level - 1)
        lat_span = 45.0 / divisor
        long_span = 60.0 / divisor
        return (lat_span, long_span,
                lat_span * M_PER_DEGREE_LAT, long_span * M_PER_DEGREE_LONG)

    @staticmethod
    def distance(a: str, b: str) -> float:
        """Great-circle metres between the centres of two cells. Section 18.5.

        The cells may be of different levels. This is the one operation in the
        format that is not bit-identical across languages: no standard library
        rounds sine, cosine or arc sine correctly, so two ports agree to about
        a millimetre rather than exactly. Anything that needs a reproducible
        ordering must rank on grid indices, as `suggest_corrections` does.
        """
        lat_a, long_a = GPC._cell_centre(a)
        lat_b, long_b = GPC._cell_centre(b)

        phi1 = lat_a * math.pi / 180.0
        phi2 = lat_b * math.pi / 180.0
        d_phi = phi2 - phi1
        d_lambda = (long_b - long_a) * math.pi / 180.0

        h = (math.sin(d_phi / 2) * math.sin(d_phi / 2)
             + math.cos(phi1) * math.cos(phi2)
             * math.sin(d_lambda / 2) * math.sin(d_lambda / 2))
        if h > 1.0:
            # Rounding can carry the sum a unit past 1 for points near opposite
            # ends of the Earth, where arc sine is undefined.
            h = 1.0
        return 2 * EARTH_RADIUS * math.asin(math.sqrt(h))

    @staticmethod
    def decode_to_grid(grid_point_code: str) -> Tuple[int, int]:
        """The row and column of the cell a code names. Section 18.6.

        The accessor for a caller building a spatial structure of its own -- a
        tile index, a join key, a quadtree -- who wants the integers rather
        than degrees rounded to six places.
        """
        return GPC.code_to_grid(GPC._geometric(grid_point_code))

    @staticmethod
    def shorten(grid_point_code: str) -> str:
        """The last five characters of a code. Section 12.1.

        Literally the second printed group of `#XXXXX-XXXXX`, so a printed code
        shows its own short form. The leading dash belongs to the presentation
        form and is not returned; `recover_short` accepts it either way.
        """
        return GPC._geometric(grid_point_code)[5:]

    @staticmethod
    def recover_short(short: str, near_latitude: float, near_longitude: float,
                      formatted: bool = True) -> str:
        """The full code a short form names, near a reference. Section 12.2.

        Exact integer arithmetic -- no search, no distance, no tie to break --
        and exact whenever the reference is within half a level-5 cell of the
        true point on each axis, which is 0.03598848 degrees of latitude
        (3.999 km) and 0.04798464 degrees of longitude (5.342 km at the
        equator, less elsewhere).

        Outside that box it returns a neighbouring cell's copy of the same
        offset, a plausible location 8 or 10 km away. A caller that cannot
        bound its reference should not be using the short form.

        Raises:
            GPCError: GPC_LENGTH unless the short form is five symbols, or
                LATITUDE or LONGITUDE for a reference outside the domain.
        """
        tail, _ = GPC.normalise(short)
        if len(tail) != CODE_LENGTH - 5:
            raise GPCError("GPC_LENGTH")
        if any(character not in ALPHABET for character in tail):
            raise GPCError("GPC_CHAR")

        row_low, col_low = GPC._read_tail(tail)
        valid, message = GPC.is_valid_coordinates(near_latitude, near_longitude)
        if not valid:
            raise GPCError(message, message + ": value out of valid range.")
        row_ref, col_ref = GPC.to_grid(near_latitude, near_longitude)

        # Floor division over values that may be negative. Truncation toward
        # zero is wrong here, and wrong only west and south of the reference.
        cell_row = (row_ref - row_low + P5 // 2) // P5
        cell_row = 0 if cell_row < 0 else (R5 - 1 if cell_row > R5 - 1 else cell_row)
        cell_col = ((col_ref - col_low + P5 // 2) // P5) % C5

        code = GPC.grid_to_code(cell_row * P5 + row_low, cell_col * P5 + col_low)
        return GPC.format_gpc(code) if formatted else code

    @staticmethod
    def suggest_corrections(grid_point_code: str, near_latitude: float,
                            near_longitude: float, level: int = 6,
                            formatted: bool = True) -> list:
        """Codes one typo away that are plausible near a reference. 15.3.

        At most 249 candidates -- 240 single-character substitutions and up to
        9 adjacent transpositions -- filtered to those in the reference's
        level-k cell or one of its eight neighbours, and ranked by
        `9*drow^2 + 16*dcol^2`, which is squared distance in degree space. Ties
        break on the integer form. Every step is integer arithmetic, so all
        four ports return the same list in the same order.

        Level 6 is the default: it suits a device fix or a named suburb and
        returns one candidate in the median case. Widening it to cover a poorer
        reference costs precision, not correctness.

        The argument is the code as typed, which need not decode: a code with a
        wrong character is exactly what this is for. It must still normalise to
        ten symbols of the alphabet.
        """
        if not 1 <= level <= LEVELS:
            raise GPCError("GPC_LEVEL")
        code, _ = GPC.normalise(grid_point_code)
        if len(code) != CODE_LENGTH:
            raise GPCError("GPC_LENGTH")
        if any(character not in ALPHABET for character in code):
            raise GPCError("GPC_CHAR")

        valid, message = GPC.is_valid_coordinates(near_latitude, near_longitude)
        if not valid:
            raise GPCError(message, message + ": value out of valid range.")
        row_ref, col_ref = GPC.to_grid(near_latitude, near_longitude)

        p = 5 ** (LEVELS - level)
        ref_row_cell = row_ref // p
        ref_col_cell = col_ref // p
        col_cells = COLS // p

        scored = []
        for candidate in GPC._candidates(code):
            if candidate[0] == "X":              # reserved, never geometric
                continue
            row, col = GPC.code_to_grid(candidate)

            d_row_cell = row // p - ref_row_cell
            d_col_cell = (col // p - ref_col_cell + col_cells) % col_cells
            if d_col_cell > col_cells // 2:
                d_col_cell -= col_cells
            if abs(d_row_cell) > 1 or abs(d_col_cell) > 1:
                continue

            d_row = row - row_ref
            d_col = col - col_ref
            if d_col > COLS // 2:                # the short way round
                d_col -= COLS
            elif d_col < -COLS // 2:
                d_col += COLS

            scored.append((9 * d_row * d_row + 16 * d_col * d_col,
                           GPC.to_integer(candidate), candidate))

        scored.sort()
        return [GPC.format_gpc(c) if formatted else c for _, _, c in scored]

    @staticmethod
    def to_integer(grid_point_code: str) -> int:
        """The code as a base-25 numeral. Section 13.

        Forty-seven bits, so six bytes big-endian, and order-preserving: sorting
        the integers sorts the codes, which sorts the cells geographically. A
        reserved code is at or above 91,552,734,375,000 and a geometric one
        below it, so one comparison classifies without parsing.
        """
        code = GPC._payload(grid_point_code)
        value = 0
        for character in code:
            value = value * 25 + ALPHABET.index(character)
        return value

    @staticmethod
    def from_integer(value: int, formatted: bool = True) -> str:
        """The code a base-25 numeral names. Section 13.

        Raises:
            GPCError: GPC_RANGE unless the value is between 0 and 25^10 - 1.
        """
        if value < 0 or value >= 25 ** LEVELS:
            raise GPCError("GPC_RANGE")
        out = [""] * LEVELS
        for i in range(LEVELS - 1, -1, -1):
            value, digit = divmod(value, 25)
            out[i] = ALPHABET[digit]
        code = "".join(out)
        return GPC.format_gpc(code) if formatted else code

    @staticmethod
    def screen(grid_point_code: str) -> Tuple[str, list]:
        """Substrings of a code that spell something unwanted. Section 17.

        Advisory, and non-normative. It reports and never blocks: nothing in
        this package refuses to encode, decode or validate because of what this
        found.

        Returns:
            tuple[str, list]: The version of the list, and the matched spans as
                (position, length) with position counted from 1, ordered by
                position and then by length. Spans may overlap and every match
                is reported. A clean code returns the version and no spans,
                because a caller has to be able to tell "clean under this list"
                from "never screened".
        """
        code = GPC._payload(grid_point_code)
        spans = []
        for length in range(SCREEN_MIN, CODE_LENGTH + 1):
            for start in range(0, CODE_LENGTH - length + 1):
                if GPC._screen_hash(code[start:start + length]) in screen_list.ENTRIES:
                    spans.append((start + 1, length))
        return screen_list.VERSION, spans

    @staticmethod
    def encode_all(points, formatted: bool = True) -> list:
        """Encode a sequence of (latitude, longitude) pairs.

        For dataset work. The first bad coordinate raises, rather than a bad
        row being silently dropped; `encode_stream` is the one to reach for
        when the caller wants to handle failures row by row.
        """
        return list(GPC.encode_stream(points, formatted))

    @staticmethod
    def encode_stream(points, formatted: bool = True):
        """Encode a sequence lazily, one code at a time."""
        for latitude, longitude in points:
            yield GPC.encode(latitude, longitude, formatted)

    @staticmethod
    def decode_all(codes) -> list:
        """Decode a sequence of codes to (latitude, longitude) pairs."""
        return list(GPC.decode_stream(codes))

    @staticmethod
    def decode_stream(codes):
        """Decode a sequence lazily, one pair at a time."""
        for code in codes:
            yield GPC.decode(code)

    #  PART 5 : COORDINATE CONVERSIONS

    @staticmethod
    def to_dms(latitude: float, longitude: float) -> str:
        """Degrees, minutes and seconds, latitude first. Section 19.1.

        `43°39'00.00"N, 79°22'48.00"W`.

        Lossy: a hundredth of a second is 0.309 m of latitude. A decoded code
        survives the trip all the same, because `decode` returns a cell centre
        and that sits eight times further from the nearest boundary than this
        rounding can move it. For exact interchange use `to_geo_uri`.
        """
        valid, message = GPC.is_valid_coordinates(latitude, longitude)
        if not valid:
            raise GPCError(message, message + ": value out of valid range.")
        return (GPC._dms_axis(latitude, "N", "S") + ", "
                + GPC._dms_axis(longitude, "E", "W"))

    @staticmethod
    def from_dms(text: str) -> Tuple[float, float]:
        """Read degrees, minutes and seconds back. Section 19.1.

        Each axis is a signed or hemisphere-marked value; the unit marker after
        the degrees is required, because it is what tells one axis from the
        next when no comma separates them.

        Raises:
            GPCError: GPC_DMS for anything the grammar does not accept, or
                LATITUDE or LONGITUDE for a value outside the domain.
        """
        scan = _Scan(text)
        latitude = scan.axis(True)
        scan.spaces()
        if scan.peek() == ",":
            scan.take()
        longitude = scan.axis(False)
        scan.spaces()
        if not scan.done():
            raise GPCError("GPC_DMS")

        valid, message = GPC.is_valid_coordinates(latitude, longitude)
        if not valid:
            raise GPCError(message, message + ": value out of valid range.")
        return latitude, longitude

    @staticmethod
    def to_geo_uri(latitude: float, longitude: float) -> str:
        """An RFC 5870 URI in its simplest form. Section 19.2.

        `geo:43.650006,-79.380004`. Six decimal places, trailing zeros dropped,
        which is exactly what `decode` produces, so a code written out this way
        and read back encodes to the same code every time.
        """
        valid, message = GPC.is_valid_coordinates(latitude, longitude)
        if not valid:
            raise GPCError(message, message + ": value out of valid range.")
        return "geo:" + GPC._decimal6(latitude) + "," + GPC._decimal6(longitude)

    @staticmethod
    def from_geo_uri(text: str) -> Tuple[float, float]:
        """Read an RFC 5870 URI back. Section 19.2.

        A third coordinate is an altitude and is discarded. Parameters are
        ignored, except that `crs` is rejected unless it is `wgs84`: this
        format is defined on WGS 84 alone, and silently reading a code as
        though it were on another datum would put it in the wrong place.
        """
        if text is None:
            raise GPCError("GPC_NULL")
        body = text.strip(WHITESPACE)
        if body[:4].lower() != "geo:":
            raise GPCError("GPC_GEO")
        body = body[4:]

        body, _, params = body.partition(";")
        if params:
            for param in params.split(";"):
                name, _, value = param.partition("=")
                if name.lower() == "crs" and value.lower() != "wgs84":
                    raise GPCError("GPC_GEO")

        parts = body.split(",")
        if len(parts) not in (2, 3):
            raise GPCError("GPC_GEO")
        latitude = GPC._geo_number(parts[0])
        longitude = GPC._geo_number(parts[1])
        if len(parts) == 3:
            GPC._geo_number(parts[2])            # altitude, parsed and dropped

        valid, message = GPC.is_valid_coordinates(latitude, longitude)
        if not valid:
            raise GPCError(message, message + ": value out of valid range.")
        return latitude, longitude

    #  PART 6 : INTERNALS

    @staticmethod
    def _split(grid_point_code: str) -> Tuple[str, str]:
        """Payload and check character, cleaned but not yet aliased.

        The dispatch in `decode` needs to see the characters as typed, because
        version 1 has its own alphabet and the version 2 alias table would
        corrupt it.
        """
        if grid_point_code is None:
            raise GPCError("GPC_NULL")
        if not grid_point_code.strip(WHITESPACE):
            raise GPCError("GPC_NULL")

        check = None
        text = grid_point_code
        if CHECK_MARK in text:
            text, _, check = text.partition(CHECK_MARK)
            check = GPC._clean(check)
        return GPC._clean(text), check

    @staticmethod
    def _clean(text: str) -> str:
        """Upper-case by ASCII rules, then drop `#`, `-` and whitespace.

        A locale-sensitive upper-casing routine would map `i` to a dotted
        capital in a Turkish locale, and the same code would be valid in one
        locale and invalid in another.
        """
        out = []
        for character in text:
            if "a" <= character <= "z":
                character = chr(ord(character) - 32)
            if character == PREFIX or character == SEPARATOR or character in WHITESPACE:
                continue
            out.append(character)
        return "".join(out)

    @staticmethod
    def _alias(text: str) -> str:
        """Read the confusable letters as the symbols they were meant to be."""
        return "".join(ALIASES.get(character, character) for character in text)

    @staticmethod
    def _geometric(grid_point_code: str) -> str:
        """The ten characters, or the typed error that stops decoding."""
        kind, reason = GPC.validate(grid_point_code)
        if kind == INVALID:
            raise GPCError(reason)
        if kind == RESERVED:
            raise GPCError("GPC_RESERVED")
        return GPC.normalise(grid_point_code)[0]

    @staticmethod
    def _check_symbol(code: str) -> str:
        """c = t · S, where S is the syndrome over the ten payload symbols."""
        syndrome = 0
        for i, character in enumerate(code):
            syndrome = _gf_add(syndrome,
                               _gf_mul(WEIGHTS[i], ALPHABET.index(character)))
        return ALPHABET[_gf_mul(_T, syndrome)]

    @staticmethod
    def _round6(value: int) -> float:
        """Round a count of 1e-8 degrees to six decimal places. Section 6.2.

        Ties are unreachable -- every reachable value is congruent to a
        multiple of 4 modulo 100 -- so no choice of rounding mode can change
        any result, and no implementation has to make the choice.
        """
        quotient, remainder = divmod(abs(value), 100)
        if remainder >= 50:
            quotient += 1
        return (-quotient if value < 0 else quotient) / 1_000_000

    @staticmethod
    def _payload(grid_point_code: str) -> str:
        """Ten symbols of the alphabet, reserved ones included.

        What `screen` and `to_integer` need: both act on the string rather than
        on the cell it names, so an X in position 1 is no obstacle to either.
        """
        code, check = GPC.normalise(grid_point_code)
        if check is not None and check != GPC._check_symbol(code) \
                if len(code) == CODE_LENGTH else False:
            raise GPCError("GPC_CHECK")
        if len(code) != CODE_LENGTH:
            raise GPCError("GPC_LENGTH")
        if any(character not in ALPHABET for character in code):
            raise GPCError("GPC_CHAR")
        return code

    @staticmethod
    def _cell(text: str) -> str:
        """A normalised cell of 1 to 10 symbols, or the typed error. 18.1."""
        payload, check = GPC.normalise(text)
        if not 1 <= len(payload) <= LEVELS:
            raise GPCError("GPC_LENGTH")
        if any(character not in ALPHABET for character in payload):
            raise GPCError("GPC_CHAR")
        if check is not None and (len(payload) != CODE_LENGTH
                                  or check != GPC._check_symbol(payload)):
            raise GPCError("GPC_CHECK")
        if payload[0] == "X":
            raise GPCError("GPC_RESERVED")
        return payload

    @staticmethod
    def _cell_grid(cell: str) -> Tuple[int, int, int, int]:
        """The level, the divisor, and the cell indices of a normalised cell.

        Any symbol will do as padding. By section 10 the first k characters fix
        the level-k cell, so whatever the padded code names, dividing by p
        lands on the same cell indices.
        """
        level = len(cell)
        p = 5 ** (LEVELS - level)
        row, col = GPC.code_to_grid(cell + ALPHABET[0] * (LEVELS - level))
        return level, p, row // p, col // p

    @staticmethod
    def _cell_centre(cell: str) -> Tuple[float, float]:
        """The centre of a cell of any level, exact to 1e-8 degrees. 18.5.

        Private on purpose. For a ten-character code this differs from `decode`
        in the seventh decimal place, and two public answers to "where is this
        cell" would be one too many.
        """
        _, p, cell_row, cell_col = GPC._cell_grid(GPC._cell(cell))
        return ((2 * cell_row + 1) * p * 1152 / 100_000_000 - 90.0,
                (2 * cell_col + 1) * p * 1536 / 100_000_000 - 180.0)

    @staticmethod
    def _read_tail(tail: str) -> Tuple[int, int]:
        """The last five characters as an offset in a level-5 cell. 12.2.

        The loop of `code_to_grid` with the parity seeded at zero and no
        level-1 step, which is what the reset of section 5.3 makes meaningful.
        """
        row = col = sr = sc = 0
        for character in tail:
            j = ALPHABET.index(character)
            big_r = j // 5
            big_c = j % 5
            r = big_r if sc % 2 == 0 else 4 - big_r
            sr += r
            c = big_c if sr % 2 == 0 else 4 - big_c
            sc += c
            row = row * 5 + r
            col = col * 5 + c
        return row, col

    @staticmethod
    def _candidates(code: str) -> list:
        """At most 249 codes one typo away, in the order section 15.3 fixes.

        240 substitutions, then the adjacent transpositions that actually
        change the code. A code such as P4444PPPPP yields 242, and the list is
        never padded back to 249 with duplicates.
        """
        out = []
        for position in range(CODE_LENGTH):
            for character in ALPHABET:
                if character != code[position]:
                    out.append(code[:position] + character + code[position + 1:])
        for position in range(CODE_LENGTH - 1):
            if code[position] != code[position + 1]:
                out.append(code[:position] + code[position + 1] + code[position]
                           + code[position + 2:])
        return out

    @staticmethod
    def _screen_hash(text: str) -> str:
        """The 32-bit FNV-1a hash, eight lower-case hex characters. 17.3.

        Not a cryptographic hash, and section 17.1 says why it does not need to
        be. Three integer operations per byte, over ASCII symbols, so all four
        ports compute it identically with nothing imported.
        """
        h = 2166136261
        for byte in text.encode("utf-8"):
            h = ((h ^ byte) * 16777619) & 0xFFFFFFFF
        return "%08x" % h

    @staticmethod
    def _dms_axis(value: float, positive: str, negative: str) -> str:
        """One axis of section 19.1, in integers after the first line."""
        u = math.floor(abs(value) * 360000.0 + 0.5)   # hundredths of a second
        return "%d%s%02d'%02d.%02d\"%s" % (
            u // 360000, DEGREE_SIGN, (u // 6000) % 60,
            (u % 6000) // 100, u % 100,
            negative if value < 0 else positive)

    @staticmethod
    def _decimal6(value: float) -> str:
        """At most six decimal places, trailing zeros dropped. 19.2."""
        u = math.floor(abs(value) * 1000000.0 + 0.5)
        sign = "-" if value < 0 and u != 0 else ""
        fraction = ("%06d" % (u % 1000000)).rstrip("0")
        return sign + str(u // 1000000) + ("." + fraction if fraction else "")

    @staticmethod
    def _geo_number(text: str) -> float:
        """RFC 5870 num: an optional minus, digits, optionally more digits."""
        body = text[1:] if text.startswith("-") else text
        whole, dot, fraction = body.partition(".")
        if not whole.isdigit() or (dot and not fraction.isdigit()):
            raise GPCError("GPC_GEO")
        return float(text)


class _Scan:
    """A cursor over degrees-minutes-seconds text. Section 19.1.

    Small enough to keep the grammar readable, and deliberately strict: every
    numeric piece carries its unit marker, so no accepted string has two
    readings.
    """

    def __init__(self, text: str):
        if text is None:
            raise GPCError("GPC_NULL")
        self.text = text
        self.at = 0

    def done(self) -> bool:
        return self.at >= len(self.text)

    def peek(self) -> str:
        """The character under the cursor, or "" at the end of the text.

        Every membership test on this has to check for the empty string first.
        "" is a substring of every string, so `peek() in choices` is true at
        the end of the text, which is the opposite of what it looks like.
        """
        return "" if self.done() else self.text[self.at]

    def take(self) -> str:
        self.at += 1
        return self.text[self.at - 1]

    def spaces(self) -> None:
        while not self.done() and self.text[self.at] in WHITESPACE:
            self.at += 1

    def marker(self, choices: str) -> None:
        self.spaces()
        if not self.peek() or self.peek() not in choices:
            raise GPCError("GPC_DMS")
        self.take()

    def digits(self) -> int:
        start = self.at
        while not self.done() and self.text[self.at].isdigit():
            self.at += 1
        if self.at == start:
            raise GPCError("GPC_DMS")
        return int(self.text[start:self.at])

    def number(self) -> float:
        start = self.at
        while not self.done() and self.text[self.at].isdigit():
            self.at += 1
        if not self.done() and self.text[self.at] == ".":
            self.at += 1
            while not self.done() and self.text[self.at].isdigit():
                self.at += 1
        body = self.text[start:self.at]
        if not body or body == ".":
            raise GPCError("GPC_DMS")
        return float(body)

    def axis(self, is_latitude: bool) -> float:
        """One axis: [sign] degrees marker [minutes marker [seconds marker]]."""
        self.spaces()
        signed = bool(self.peek()) and self.peek() in "+-"
        sign = -1.0 if signed and self.take() == "-" else 1.0

        self.spaces()
        degrees = self.digits()
        self.marker(DEGREE_SIGN + "dD")

        minutes = 0
        seconds = 0.0
        save = self.at
        self.spaces()
        if self.peek().isdigit():
            minutes = self.digits()
            self.marker("'mM")
            if minutes >= 60:
                raise GPCError("GPC_DMS")
            save = self.at
            self.spaces()
            if self.peek().isdigit():
                seconds = self.number()
                self.marker("\"sS")
                if seconds >= 60.0:
                    raise GPCError("GPC_DMS")
            else:
                self.at = save
        else:
            self.at = save

        self.spaces()
        letter = self.peek().upper()
        if letter and letter in "NSEW":
            self.take()
            if signed:
                raise GPCError("GPC_DMS")        # a sign and a hemisphere both
            if (letter in "NS") != is_latitude:
                raise GPCError("GPC_DMS")        # the wrong axis
            if letter in "SW":
                sign = -1.0

        return sign * (degrees + (minutes + seconds / 60.0) / 60.0)
