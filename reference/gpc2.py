#  Copyright 2026 Pranavkumar Patel
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

"""The executable reference for version 2 of the format.

This is the companion to SPEC.md: every rule in that document is implemented
here once, plainly, so that the numbers it quotes can be reproduced and a new
port has something to disagree with. It is not a published package and is not
one of the four ports.

Section numbers in the comments refer to SPEC.md.
"""

import math

ALPHABET = "0123456789CDFGHJKLMNPRTWX"          # section 4

P9 = 5 ** 9                                     # 1_953_125
P5 = 5 ** 5                                     # 3_125
ROWS = 4 * P9                                   # 7_812_500      section 3
COLS = 6 * P9                                   # 11_718_750
R5 = 4 * 5 ** 4                                 # 2_500 rows of level-5 cells
C5 = 6 * 5 ** 4                                 # 3_750 columns of level-5 cells
RESET = 6                                       # parity resets entering this level

ALIASES = {"O": "0", "I": "1", "S": "5", "Z": "2",
           "B": "8", "A": "4", "E": "3", "V": "W"}

GEOMETRIC, RESERVED, INVALID = "GEOMETRIC", "RESERVED", "INVALID"


class GpcError(ValueError):
    """Carries the reason code so a caller can branch on it rather than on text."""

    def __init__(self, reason):
        super().__init__(reason)
        self.reason = reason


# ---------------------------------------------------------------- section 8

def normalise(text):
    """Case-fold, strip separators, apply the alias table. Returns the payload
    and the check text, which is None when no '*' was present. The check is
    returned as it normalised, however long: section 14.1 makes anything other
    than a single alphabet symbol a GPC_CHECK, and that is validate's call."""
    if text is None:
        raise GpcError("GPC_NULL")
    if not text.strip():
        raise GpcError("GPC_NULL")

    check = None
    if "*" in text:
        text, _, check = text.partition("*")

    def clean(s):
        out = []
        for ch in s:
            if "a" <= ch <= "z":                # ASCII case folding only
                ch = chr(ord(ch) - 32)
            if ch in "#-" or ch.isspace():
                continue
            out.append(ALIASES.get(ch, ch))
        return "".join(out)

    payload = clean(text)
    if check is not None:
        check = clean(check)
    return payload, check


# ---------------------------------------------------------------- section 9

def classify(text):
    return validate(text)[0]


def validate(text):
    """Returns (class, reason). reason is '' for anything that is not INVALID.

    The check character is verified here and not only in decode. A caller told
    that a code is valid has to be able to decode it."""
    try:
        code, check = normalise(text)
    except GpcError as exc:
        return INVALID, exc.reason
    if len(code) != 10:
        return INVALID, "GPC_LENGTH"
    if any(ch not in ALPHABET for ch in code):
        return INVALID, "GPC_CHAR"
    if check is not None and check != check_character(code):
        return INVALID, "GPC_CHECK"
    return (RESERVED if code[0] == "X" else GEOMETRIC), ""


def is_valid(text):
    return validate(text)[0] == GEOMETRIC


# --------------------------------------------------------------- section 5.1

def to_grid(latitude, longitude):
    if not (math.isfinite(latitude) and math.isfinite(longitude)):
        raise GpcError("LATITUDE" if not math.isfinite(latitude) else "LONGITUDE")
    if not -90.0 <= latitude <= 90.0:
        raise GpcError("LATITUDE")
    if not -180.0 <= longitude <= 180.0:
        raise GpcError("LONGITUDE")

    if longitude == 180.0:
        longitude = -180.0

    # Three operations per axis, left to right. See section 7.
    row = math.floor((latitude + 90.0) * 7812500.0 / 180.0)
    col = math.floor((longitude + 180.0) * 11718750.0 / 360.0)

    row = 0 if row < 0 else (ROWS - 1 if row > ROWS - 1 else row)
    col = 0 if col < 0 else (COLS - 1 if col > COLS - 1 else col)
    return row, col


# --------------------------------------------------------------- section 5.2

def grid_to_code(row, col):
    out = []
    r1, c1 = row // P9, col // P9
    out.append(ALPHABET[r1 * 6 + (c1 if r1 % 2 == 0 else 5 - c1)])

    sr, sc = r1, c1
    for level in range(2, 11):
        if level == RESET:
            sr, sc = 0, 0
        p = 5 ** (10 - level)
        r = (row // p) % 5
        c = (col // p) % 5
        R = r if sc % 2 == 0 else 4 - r
        sr += r
        C = c if sr % 2 == 0 else 4 - c
        sc += c
        out.append(ALPHABET[R * 5 + C])
    return "".join(out)


def code_to_grid(code):
    i = ALPHABET.index(code[0])
    r1, k = divmod(i, 6)
    c1 = k if r1 % 2 == 0 else 5 - k

    row, col = r1, c1
    sr, sc = r1, c1
    for level in range(2, 11):
        if level == RESET:
            sr, sc = 0, 0
        R, C = divmod(ALPHABET.index(code[level - 1]), 5)
        r = R if sc % 2 == 0 else 4 - R
        sr += r
        c = C if sr % 2 == 0 else 4 - C
        sc += c
        row = row * 5 + r
        col = col * 5 + c
    return row, col


def encode(latitude, longitude, formatted=True):
    code = grid_to_code(*to_grid(latitude, longitude))
    return "#" + code[:5] + "-" + code[5:] if formatted else code


# --------------------------------------------------------------- section 6.2

def lat_e8(row):
    return (2 * row + 1) * 1152 - 9_000_000_000


def lng_e8(col):
    return (2 * col + 1) * 1536 - 18_000_000_000


def _round6(v):
    """v counts 1e-8 degrees. Ties are unreachable, so the mode cannot matter."""
    q, r = divmod(abs(v), 100)
    if r >= 50:
        q += 1
    return (-q if v < 0 else q) / 1_000_000


def _geometric(text):
    """The payload of a code that decode and decode_to_area may both act on."""
    kind, reason = validate(text)
    if kind == INVALID:
        raise GpcError(reason)
    if kind == RESERVED:
        raise GpcError("GPC_RESERVED")
    return normalise(text)[0]


def decode(text):
    row, col = code_to_grid(_geometric(text))
    return _round6(lat_e8(row)), _round6(lng_e8(col))


def decode_to_area(text):
    """South, west, north, east. See section 6.3."""
    row, col = code_to_grid(_geometric(text))
    return (row * 180.0 / 7812500.0 - 90.0,
            col * 360.0 / 11718750.0 - 180.0,
            (row + 1) * 180.0 / 7812500.0 - 90.0,
            (col + 1) * 360.0 / 11718750.0 - 180.0)


# ---------------------------------------------------------------- section 13

def to_integer(code):
    v = 0
    for ch in code:
        v = v * 25 + ALPHABET.index(ch)
    return v


def from_integer(v):
    out = []
    for _ in range(10):
        v, d = divmod(v, 25)
        out.append(ALPHABET[d])
    return "".join(reversed(out))


# ---------------------------------------------------------------- section 12

def shorten(code):
    return code[5:]


def read_tail(chars):
    """Levels 6 to 10 with the parity seeded at zero, which is what the reset
    of section 5.3 guarantees. Returns the offset within a level-5 cell."""
    row = col = sr = sc = 0
    for ch in chars:
        R, C = divmod(ALPHABET.index(ch), 5)
        r = R if sc % 2 == 0 else 4 - R
        sr += r
        c = C if sr % 2 == 0 else 4 - C
        sc += c
        row = row * 5 + r
        col = col * 5 + c
    return row, col


def recover_short(short, near_latitude, near_longitude):
    row_low, col_low = read_tail(short)
    row_ref, col_ref = to_grid(near_latitude, near_longitude)

    cell_row = (row_ref - row_low + P5 // 2) // P5        # floor division
    cell_row = max(0, min(R5 - 1, cell_row))              # latitude does not wrap
    cell_col = ((col_ref - col_low + P5 // 2) // P5) % C5  # longitude wraps

    return grid_to_code(cell_row * P5 + row_low, cell_col * P5 + col_low)


# ------------------------------------------------- section 14, GF(25) check

def gf_add(x, y):
    return ((x // 5 + y // 5) % 5) * 5 + ((x % 5 + y % 5) % 5)


def gf_mul(x, y):
    """(a + b t)(c + d t) with t^2 = 4t + 3, elements indexed b*5 + a."""
    a, b = x % 5, x // 5
    c, d = y % 5, y // 5
    return ((a * d + b * c + 4 * b * d) % 5) * 5 + ((a * c + 3 * b * d) % 5)


T = 5                                            # the element t, index 1*5 + 0


def _weights():
    w, x = [], 1
    for _ in range(11):
        x = gf_mul(x, T)
        w.append(x)
    return w


WEIGHTS = _weights()                             # t^1 .. t^11


def syndrome(code):
    s = 0
    for i, ch in enumerate(code):
        s = gf_add(s, gf_mul(WEIGHTS[i], ALPHABET.index(ch)))
    return s


def check_character(code):
    return ALPHABET[gf_mul(T, syndrome(code))]   # c = t * S


def check_holds(code, check):
    return gf_add(syndrome(code),
                  gf_mul(WEIGHTS[10], ALPHABET.index(check))) == 0


# ---------------------------------------------------------------- section 15

def candidates(code):
    """At most 249: 240 substitutions, then the adjacent transpositions that
    actually change the code."""
    out = []
    for p in range(10):
        for ch in ALPHABET:
            if ch != code[p]:
                out.append(code[:p] + ch + code[p + 1:])
    for p in range(9):
        if code[p] != code[p + 1]:
            out.append(code[:p] + code[p + 1] + code[p] + code[p + 2:])
    return out


def suggest_corrections(code, near_latitude, near_longitude, level=6):
    row_ref, col_ref = to_grid(near_latitude, near_longitude)
    p = 5 ** (10 - level)
    ref_row_cell, ref_col_cell = row_ref // p, col_ref // p
    col_cells = COLS // p

    scored = []
    for cand in candidates(code):
        if cand[0] == "X":                       # reserved, never geometric
            continue
        row, col = code_to_grid(cand)

        d_row_cell = row // p - ref_row_cell
        d_col_cell = (col // p - ref_col_cell + col_cells) % col_cells
        if d_col_cell > col_cells // 2:
            d_col_cell -= col_cells
        if abs(d_row_cell) > 1 or abs(d_col_cell) > 1:
            continue

        d_row = row - row_ref
        d_col = col - col_ref
        if d_col > COLS // 2:
            d_col -= COLS
        elif d_col < -COLS // 2:
            d_col += COLS

        scored.append((9 * d_row * d_row + 16 * d_col * d_col,
                       to_integer(cand), cand))
    scored.sort()
    return [cand for _, _, cand in scored]
