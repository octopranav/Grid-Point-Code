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

from . import v1
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

    #  PART 4 : INTERNALS

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
