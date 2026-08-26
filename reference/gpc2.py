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


# ---------------------------------------------------------------- section 18

LEVELS = 10
EARTH_RADIUS = 6371008.8                         # mean radius of WGS 84

# North, north-east, east, south-east, south, south-west, west, north-west.
# Rows increase northward, so north is +1.
NEIGHBOUR_STEPS = ((1, 0), (1, 1), (0, 1), (-1, 1),
                   (-1, 0), (-1, -1), (0, -1), (1, -1))


def _cell(text):
    """A normalised cell of 1 to 10 symbols, or the typed error. Section 18.1."""
    payload, check = normalise(text)
    if check is not None and (len(payload) != 10 or check != check_character(payload)):
        raise GpcError("GPC_CHECK")
    if not 1 <= len(payload) <= LEVELS:
        raise GpcError("GPC_LENGTH")
    if any(ch not in ALPHABET for ch in payload):
        raise GpcError("GPC_CHAR")
    if payload[0] == "X":
        raise GpcError("GPC_RESERVED")
    return payload


def _cell_grid(cell_text):
    """(level, p, cellRow, cellCol) for a normalised cell.

    Any symbol will do as padding: by section 10 the first k characters fix the
    level-k cell, so whatever the padding names, dividing by p lands on the
    same cell indices."""
    level = len(cell_text)
    p = 5 ** (LEVELS - level)
    row, col = code_to_grid(cell_text + ALPHABET[0] * (LEVELS - level))
    return level, p, row // p, col // p


def cell(text, level):
    """The first `level` characters of a code, normalised. Section 18.1."""
    if not 1 <= level <= LEVELS:
        raise GpcError("GPC_LEVEL")
    code = _cell(text)
    if len(code) < level:
        raise GpcError("GPC_LENGTH")
    return code[:level]


def contains(cell_text, code):
    """Whether the code lies in the cell. The prefix test. Section 18.2."""
    prefix = _cell(cell_text)
    full = _cell(code)
    return len(full) >= len(prefix) and full[:len(prefix)] == prefix


def neighbours(text):
    """The cells sharing an edge or a corner, in order. Section 18.3."""
    level, p, cell_row, cell_col = _cell_grid(_cell(text))
    row_cells = 4 * 5 ** (level - 1)
    col_cells = 6 * 5 ** (level - 1)

    out = []
    for d_row, d_col in NEIGHBOUR_STEPS:
        r = cell_row + d_row
        if r < 0 or r >= row_cells:              # rows do not wrap
            continue
        c = (cell_col + d_col + col_cells) % col_cells
        out.append(grid_to_code(r * p, c * p)[:level])
    return out


def cell_dimensions(level):
    """Latitude span, longitude span, and the two in metres. Section 18.4."""
    if not 1 <= level <= LEVELS:
        raise GpcError("GPC_LEVEL")
    divisor = 5 ** (level - 1)
    lat_span = 45.0 / divisor
    lng_span = 60.0 / divisor
    return lat_span, lng_span, lat_span * 111132.0, lng_span * 111319.49


def cell_centre(text):
    """The centre of a cell of any level, exact to 1e-8 degrees. Section 18.5."""
    _, p, cell_row, cell_col = _cell_grid(_cell(text))
    return ((2 * cell_row + 1) * p * 1152 / 100_000_000 - 90.0,
            (2 * cell_col + 1) * p * 1536 / 100_000_000 - 180.0)


def distance(a, b):
    """Great-circle metres between two cell centres. Section 18.5.

    The one operation here that is not bit-identical across languages."""
    lat_a, lng_a = cell_centre(a)
    lat_b, lng_b = cell_centre(b)

    phi1 = lat_a * math.pi / 180.0
    phi2 = lat_b * math.pi / 180.0
    d_phi = phi2 - phi1
    d_lambda = (lng_b - lng_a) * math.pi / 180.0

    h = (math.sin(d_phi / 2) * math.sin(d_phi / 2)
         + math.cos(phi1) * math.cos(phi2)
         * math.sin(d_lambda / 2) * math.sin(d_lambda / 2))
    if h > 1.0:                                  # rounding, near antipodal
        h = 1.0
    return 2 * EARTH_RADIUS * math.asin(math.sqrt(h))


def decode_to_grid(text):
    """The row and column of the cell a code names. Section 18.6."""
    return code_to_grid(_geometric(text))


# ---------------------------------------------------------------- section 19

DEGREE = "°"


def _dms_axis(value, positive, negative):
    u = math.floor(abs(value) * 360000.0 + 0.5)  # hundredths of a second
    return "%d%s%02d'%02d.%02d\"%s" % (u // 360000, DEGREE, (u // 6000) % 60,
                                       (u % 6000) // 100, u % 100,
                                       negative if value < 0 else positive)


def to_dms(latitude, longitude):
    """Degrees, minutes and seconds, latitude first. Section 19.1."""
    return _dms_axis(latitude, "N", "S") + ", " + _dms_axis(longitude, "E", "W")


class _Scan:
    """A cursor over the DMS text. Small enough to keep the grammar readable."""

    def __init__(self, text):
        self.text = text
        self.at = 0

    def spaces(self):
        while self.at < len(self.text) and self.text[self.at] in " \t\n\v\f\r":
            self.at += 1

    def peek(self):
        return self.text[self.at] if self.at < len(self.text) else ""

    def take(self):
        self.at += 1
        return self.text[self.at - 1]

    def digits(self):
        start = self.at
        while self.at < len(self.text) and self.text[self.at].isdigit():
            self.at += 1
        if self.at == start:
            raise GpcError("GPC_DMS")
        return int(self.text[start:self.at])

    def marker(self, choices):
        # peek() returns "" at the end of the text, and "" is a substring of
        # every string, so the emptiness is tested before the membership.
        self.spaces()
        if not self.peek() or self.peek() not in choices:
            raise GpcError("GPC_DMS")
        self.take()

    def axis(self, is_latitude):
        self.spaces()
        sign = 1
        if self.peek() and self.peek() in "+-":
            sign = -1 if self.take() == "-" else 1
            signed = True
        else:
            signed = False

        self.spaces()
        degrees = self.digits()
        self.marker(DEGREE + "dD")

        minutes = 0
        seconds = 0.0
        save = self.at
        self.spaces()
        if self.peek().isdigit():
            minutes = self.digits()
            self.marker("'mM")
            if minutes >= 60:
                raise GpcError("GPC_DMS")
            save = self.at
            self.spaces()
            if self.peek().isdigit():
                seconds = self.number()
                self.marker("\"sS")
                if seconds >= 60.0:
                    raise GpcError("GPC_DMS")
            else:
                self.at = save
        else:
            self.at = save

        self.spaces()
        letter = self.peek().upper()
        if letter and letter in "NSEW":
            self.take()
            if signed:
                raise GpcError("GPC_DMS")       # a sign and a hemisphere both
            if (letter in "NS") != is_latitude:
                raise GpcError("GPC_DMS")       # the wrong axis
            if letter in "SW":
                sign = -1

        return sign * (degrees + (minutes + seconds / 60.0) / 60.0)

    def number(self):
        start = self.at
        while self.at < len(self.text) and self.text[self.at].isdigit():
            self.at += 1
        if self.at < len(self.text) and self.text[self.at] == ".":
            self.at += 1
            while self.at < len(self.text) and self.text[self.at].isdigit():
                self.at += 1
        text = self.text[start:self.at]
        if not text or text == ".":
            raise GpcError("GPC_DMS")
        return float(text)


def from_dms(text):
    """Read degrees, minutes and seconds back. Section 19.1."""
    if text is None:
        raise GpcError("GPC_NULL")
    scan = _Scan(text)
    latitude = scan.axis(True)
    scan.spaces()
    if scan.peek() == ",":
        scan.take()
    longitude = scan.axis(False)
    scan.spaces()
    if scan.at != len(scan.text):
        raise GpcError("GPC_DMS")
    if not -90.0 <= latitude <= 90.0:
        raise GpcError("LATITUDE")
    if not -180.0 <= longitude <= 180.0:
        raise GpcError("LONGITUDE")
    return latitude, longitude


def _decimal6(value):
    """At most six places, trailing zeros dropped. Section 19.2."""
    u = math.floor(abs(value) * 1000000.0 + 0.5)
    sign = "-" if value < 0 and u != 0 else ""
    frac = ("%06d" % (u % 1000000)).rstrip("0")
    return sign + str(u // 1000000) + ("." + frac if frac else "")


def to_geo_uri(latitude, longitude):
    """An RFC 5870 URI in its simplest form. Section 19.2."""
    return "geo:" + _decimal6(latitude) + "," + _decimal6(longitude)


def _geo_number(text):
    body = text[1:] if text.startswith("-") else text
    whole, dot, frac = body.partition(".")
    if not whole.isdigit() or (dot and not frac.isdigit()):
        raise GpcError("GPC_GEO")
    return float(text)


def from_geo_uri(text):
    """Read an RFC 5870 URI back. Altitude and parameters are dropped."""
    if text is None:
        raise GpcError("GPC_NULL")
    body = text.strip()
    if body[:4].lower() != "geo:":
        raise GpcError("GPC_GEO")
    body = body[4:]

    body, _, params = body.partition(";")
    for param in params.split(";") if params else []:
        name, _, value = param.partition("=")
        if name.lower() == "crs" and value.lower() != "wgs84":
            raise GpcError("GPC_GEO")

    parts = body.split(",")
    if len(parts) not in (2, 3):
        raise GpcError("GPC_GEO")
    latitude = _geo_number(parts[0])
    longitude = _geo_number(parts[1])
    if len(parts) == 3:
        _geo_number(parts[2])                    # altitude, parsed and dropped

    if not -90.0 <= latitude <= 90.0:
        raise GpcError("LATITUDE")
    if not -180.0 <= longitude <= 180.0:
        raise GpcError("LONGITUDE")
    return latitude, longitude


# ---------------------------------------------------------------- section 17
#
# The mechanism only. The list itself is expanded at build time from a word
# file that is deliberately not in this repository, so nothing here carries a
# word: `screen` takes the entries it is to match against.

SCREEN_MIN = 4                                   # section 17.2

SCREEN_LETTERS = {
    "a": "4", "b": "8", "c": "C", "d": "D", "e": "3", "f": "F", "g": "G69",
    "h": "H", "i": "1", "j": "J", "k": "K", "l": "L1", "m": "M", "n": "N",
    "o": "0", "p": "P", "q": "", "r": "R", "s": "5", "t": "T7", "u": "",
    "v": "", "w": "W", "x": "X", "y": "", "z": "2",
}


def screen_hash(text):
    """The 32-bit FNV-1a hash, eight lower-case hex characters. Section 17.3."""
    h = 2166136261
    for byte in text.encode("utf-8"):
        h = ((h ^ byte) * 16777619) & 0xFFFFFFFF
    return "%08x" % h


def expand_word(word):
    """Every way a word can be spelled in a code, in order. Section 17.2.

    Every variant is as long as the word, so a word shorter than the floor
    contributes nothing rather than contributing something too short."""
    if len(word) < SCREEN_MIN:
        return []
    variants = [""]
    for letter in word:
        symbols = SCREEN_LETTERS.get(letter)
        if not symbols:
            return []                            # cannot appear at all
        variants = [v + s for v in variants for s in symbols]
    return variants


def screen(text, entries):
    """Matched spans as (position, length), position counted from 1. 17.4.

    Reserved codes screen like any other: an X in position 1 does not stop the
    remaining nine characters spelling something."""
    code, _ = normalise(text)
    if len(code) != 10 or any(ch not in ALPHABET for ch in code):
        raise GpcError("GPC_LENGTH" if len(code) != 10 else "GPC_CHAR")
    spans = []
    for length in range(SCREEN_MIN, len(code) + 1):
        for start in range(0, len(code) - length + 1):
            if screen_hash(code[start:start + length]) in entries:
                spans.append((start + 1, length))
    spans.sort()
    return spans
