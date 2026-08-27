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

"""Regenerates the conformance vectors beside this file.

Run it from anywhere:

    python test_data/generate.py

Expected values come from the Python port. That is a starting point, not an
authority: the vectors are only correct once every port agrees on them, which
is what each port's runner checks. Before committing a regenerated corpus, run
all four suites.

The version 1 files are frozen and are rebuilt from `v1_encoder.py`, which is
the only version 1 encoder left anywhere in the repository. No published
package can write a version 1 code.

Output is deterministic. Running this without changing the corpus definitions
below must leave the files byte for byte identical, so an unexpected diff means
encoding behaviour changed. That is a breaking change and needs a major
version, never a quiet vector update.
"""

import hashlib
import math
import random
import sys
from decimal import Decimal
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent
sys.path.insert(0, str(REPO / "python" / "src"))
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(REPO / "screening"))

import expand  # noqa: E402
import v1_encoder  # noqa: E402
from gridpointcode_algo_pranavpatel_ca import GPC, screen_list  # noqa: E402


def fmt(value):
    """Shortest decimal that reads back as this double, never in exponent form."""
    text = repr(float(value))
    if "e" in text or "E" in text:
        return format(Decimal(text), "f")
    return text


def write(name, lines):
    """Write one vector file as UTF-8 with LF endings on every platform.

    Both are named explicitly because both have platform defaults that differ.
    The degree sign in v2_dms.csv is the only character above ASCII in the
    corpus, and without the encoding here it is written in the platform code
    page -- which regenerates to different bytes on Windows than on a runner,
    and fails the job that checks the committed corpus is what this produces.
    """
    with open(HERE / name, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines))


def below(value):
    """The next double toward negative infinity."""
    return math.nextafter(value, -math.inf)


def above(value):
    """The next double toward positive infinity."""
    return math.nextafter(value, math.inf)


# ============================================================== version 1 ====
#
# Frozen. Version 2 reads these codes and cannot write them, so the encoder
# behind this section lives in v1_encoder.py rather than in any package.

# ------------------------------------------------------------------ encoding --

sections = [
    ("Origin, and the four signed zeroes that name the same point", [
        (0.0, 0.0), (-0.0, -0.0), (0.0, -0.0), (-0.0, 0.0),
    ]),
    ("Smallest step away from the origin in each quadrant", [
        (0.00001, 0.00001), (-0.00001, 0.00001),
        (0.00001, -0.00001), (-0.00001, -0.00001),
    ]),
    ("Grid extremes: the last cell in each corner", [
        (89.99999, 179.99999), (-89.99999, 179.99999),
        (89.99999, -179.99999), (-89.99999, -179.99999),
        (89.99999, 0.0), (-89.99999, 0.0), (0.0, 179.99999), (0.0, -179.99999),
    ]),
    ("Coordinates that round up to a whole degree while formatting", [
        (89.9999999999999, 0.0), (89.99999999999999, 0.0),
        (-89.9999999999999, 0.0), (0.0, 179.9999999999999),
        (0.0, -179.9999999999999),
        (1.9999999999999998, 1.9999999999999998),
        (-1.9999999999999998, -1.9999999999999998),
        (44.99999999999999, -0.9999999999999999),
    ]),
    ("Decimal representations that used to diverge between ports", [
        (1.999999999999999, 1.999999999999999),
        (-1.999999999999999, -1.999999999999999),
        (43.649999999999999, -79.379999999999999),
        (0.1 + 0.2, 0.1 + 0.2),
        (2.675, -2.675), (0.615, -0.615), (1.005, -1.005),
        (43.65, -79.38), (23.0225, 72.5714),
    ]),
    ("Truncation, not rounding, at the fifth decimal", [
        (1.234564999, 1.234565001), (1.2345649, -1.2345651),
        (0.999999, -0.999999), (10.000009, -10.000009),
        (5.5555555555, -5.5555555555),
    ]),
    ("Landmarks", [
        (43.65, -79.38), (43.6426, -79.3871), (23.0225, 72.5714),
        (-33.8568, 151.2153), (-13.1631, -72.545), (64.1466, -21.9426),
        (51.5007, -0.1246), (35.6586, 139.7454), (-22.9519, -43.2105),
        (30.0444, 31.2357), (55.7558, 37.6173), (1.2897, 103.8501),
    ]),
]

sweep = [(latitude, longitude)
         for latitude in (-89.5, -45.25, -1.125, -0.5, 0.5, 1.125, 45.25, 89.5)
         for longitude in (-179.5, -90.25, -1.125, -0.5, 0.5, 1.125, 90.25, 179.5)]
sections.append(("Systematic sweep over signs and magnitudes", sweep))

random.seed(20260825)
randomised = []
while len(randomised) < 2500:
    places = [0, 1, 2, 3, 4, 5, 6, 7, 9, 12]
    latitude = round(random.uniform(-89.999, 89.999), random.choice(places))
    longitude = round(random.uniform(-179.999, 179.999), random.choice(places))
    # Rounding to a whole degree can land exactly on a pole or the antimeridian,
    # neither of which the format accepts.
    if abs(latitude) >= 90 or abs(longitude) >= 180:
        continue
    randomised.append((latitude, longitude))
sections.append(("Randomised sample, seed 20260825, mixed decimal precision", randomised))

lines = ["# latitude,longitude,code",
         "# Lines starting with # are comments. Blank lines are ignored.",
         "# Version 1. code is the unformatted 11-character form. No published",
         "# package encodes version 1 any more, so a port asserts these rows by",
         "# decoding: decodeV1(code) must land inside the cell the coordinate is in,",
         "# which is one hundred-thousandth of a degree on each axis.",
         ""]
encoded = 0
for title, points in sections:
    lines.append("# --- " + title)
    for latitude, longitude in points:
        valid, why = v1_encoder.is_valid_coordinates(latitude, longitude)
        assert valid, f"{title}: ({latitude!r}, {longitude!r}) is outside the domain ({why})"
        lines.append(f"{fmt(latitude)},{fmt(longitude)},"
                     f"{v1_encoder.encode(latitude, longitude, False)}")
        encoded += 1
    lines.append("")
write("encoding.csv", lines)
print(f"encoding.csv              {encoded:5} vectors")

# ------------------------------------------------------------------ decoding --

candidates = [v1_encoder.encode(latitude, longitude, False)
              for _, points in sections[:7] for latitude, longitude in points]
random.seed(7)
candidates += [v1_encoder.encode(latitude, longitude, False)
               for latitude, longitude in random.sample(randomised, 400)]

seen = set()
codes = []
for code in candidates:
    if code not in seen:
        seen.add(code)
        codes.append(code)

lines = ["# code,latitude,longitude",
         "# Version 1. Every code names one cell and decodes to that cell's corner,",
         "# which is where version 1 differs from version 2 by design.",
         "# The formatted and unformatted forms of a code MUST decode identically.",
         ""]
for code in codes:
    latitude, longitude = GPC.decode_v1(code)
    lines.append(f"{code},{fmt(latitude)},{fmt(longitude)}")
lines.append("")
write("decoding.csv", lines)
print(f"decoding.csv              {len(codes):5} vectors")

# -------------------------------------------------------------- code validity --

# The input is the last field on purpose. It may contain '#', spaces or nothing
# at all, so keeping it last means no data line can start with the comment
# marker and nothing inside it can be mistaken for a column break.
code_groups = [
    ("Nothing to parse", [("", "GPC_NULL"), ("   ", "GPC_NULL")]),
    ("Accepted forms of the same code", [
        ("#FN5G-CDKL-HDC", ""), ("FN5GCDKLHDC", ""), ("fn5gcdklhdc", ""),
        ("  FN5GCDKLHDC  ", ""), ("FN5G CDKL HDC", ""), ("#fn5g-cdkl-hdc", ""),
    ]),
    ("Other valid codes", [("#HG9K-PCVH-DPV", ""), ("DCCCCCCCCCC", "")]),
    ("Wrong length", [
        ("ABC", "GPC_LENGTH"), ("FN5GCDKLHD", "GPC_LENGTH"),
        ("FN5GCDKLHDCC", "GPC_LENGTH"),
    ]),
    ("Characters outside the alphabet", [
        ("AAAAAAAAAAA", "GPC_CHAR"), ("FN5GCDKLHDA", "GPC_CHAR"),
        ("FN5GCDKLHD!", "GPC_CHAR"),
    ]),
    ("Right shape, but the point is outside the grid", [
        ("CCCCCCCCCCC", "GPC_RANGE"), ("CCCCCCCCCCD", "GPC_RANGE"),
        ("9999999999Y", "GPC_RANGE"), ("YYYYYYYYYYY", "GPC_RANGE"),
    ]),
]

lines = ["# valid,message,input",
         "# Version 1. valid is true or false; message is empty when valid,",
         "# otherwise the reason code. A port asserts these with isValidV1.",
         "# input is the final field and runs verbatim to end of line, including any",
         "# '#', spaces or separators. Split on the first two commas only, and do not",
         "# trim the input field - one case is whitespace only.",
         ""]
code_count = 0
for title, cases in code_groups:
    lines.append("# --- " + title)
    for raw, expected in cases:
        valid, message = GPC.is_valid_v1(raw)
        assert message == expected, (raw, message, expected)
        lines.append(f"{str(valid).lower()},{message},{raw}")
        code_count += 1
    lines.append("")
write("validity_codes.csv", lines)
print(f"validity_codes.csv        {code_count:5} vectors")

# -------------------------------------------------------- coordinate validity --

coordinate_cases = [
    (0.0, 0.0, ""), (89.99999, 179.99999, ""), (-89.99999, -179.99999, ""),
    (89.999999999, 179.999999999, ""),
    (90.0, 0.0, "LATITUDE"), (-90.0, 0.0, "LATITUDE"),
    (90.00001, 0.0, "LATITUDE"), (-90.00001, 0.0, "LATITUDE"),
    (91.0, 0.0, "LATITUDE"), (1000.0, 0.0, "LATITUDE"),
    (0.0, 180.0, "LONGITUDE"), (0.0, -180.0, "LONGITUDE"),
    (0.0, 180.00001, "LONGITUDE"), (0.0, -180.00001, "LONGITUDE"),
    (0.0, 181.0, "LONGITUDE"), (0.0, 1000.0, "LONGITUDE"),
    # 179.99999999999999 is exactly 180.0 once stored as a double.
    (0.0, 179.99999999999999, "LONGITUDE"),
    (90.0, 180.0, "LATITUDE"),
]

lines = ["# latitude,longitude,valid,message",
         "# Version 1 only, and a record rather than a port assertion: this is the",
         "# domain of an encoder no package carries any more. The poles and the",
         "# antimeridian were outside it. Version 2 accepts all of them, and",
         "# v2_encoding.csv holds the same coordinates with codes beside them.",
         "# Latitude is checked before longitude when both are out of range.",
         ""]
for latitude, longitude, expected in coordinate_cases:
    valid, message = v1_encoder.is_valid_coordinates(latitude, longitude)
    assert message == expected, (latitude, longitude, message, expected)
    lines.append(f"{fmt(latitude)},{fmt(longitude)},{str(valid).lower()},{message}")
lines.append("")
write("validity_coordinates.csv", lines)
print(f"validity_coordinates.csv  {len(coordinate_cases):5} vectors")

# --------------------------------------------------------------- wide sample --

# A hundred thousand coordinates are too many to commit, but their codes still
# have to agree across the ports. The sample is therefore defined by arithmetic
# rather than stored: every port walks the same generator and hashes the codes
# it produces, and only the digest is committed. The generator is a plain
# linear congruential sequence whose products stay below 2^53, so it is exact
# in every language including the ones with no integer type wider than a
# double.

SAMPLE_COUNT = 100_000
SAMPLE_SEED = 20260824
SAMPLE_MULTIPLIER = 1_664_525
SAMPLE_INCREMENT = 1_013_904_223
SAMPLE_MODULUS = 4_294_967_296  # 2^32
SAMPLE_LAT_SPAN = 17_999_999    # -89.99999 .. 89.99999 in units of 1e-5
SAMPLE_LONG_SPAN = 35_999_999   # -179.99999 .. 179.99999 in units of 1e-5


def sample_points(count=SAMPLE_COUNT, seed=SAMPLE_SEED):
    """Yield the version 1 wide sample, one (latitude, longitude) at a time."""
    state = seed
    for _ in range(count):
        state = (SAMPLE_MULTIPLIER * state + SAMPLE_INCREMENT) % SAMPLE_MODULUS
        latitude = (state % SAMPLE_LAT_SPAN - (SAMPLE_LAT_SPAN - 1) // 2) / 100000
        state = (SAMPLE_MULTIPLIER * state + SAMPLE_INCREMENT) % SAMPLE_MODULUS
        longitude = (state % SAMPLE_LONG_SPAN - (SAMPLE_LONG_SPAN - 1) // 2) / 100000
        yield latitude, longitude


sample_codes = [v1_encoder.encode(latitude, longitude, False)
                for latitude, longitude in sample_points()]
sample_digest = hashlib.sha256("\n".join(sample_codes).encode("utf-8")).hexdigest()

lines = ["# count,seed,digest",
         "# Version 1, and a record rather than a port assertion: reproducing it",
         "# needs the version 1 encoder, which no package carries any more. The",
         "# live cross-port check is v2_sample.csv, built the same way.",
         "",
         f"{SAMPLE_COUNT},{SAMPLE_SEED},{sample_digest}",
         ""]
write("sample.csv", lines)
print(f"sample.csv                {SAMPLE_COUNT:5} points, digest {sample_digest[:16]}...")


# ============================================================== version 2 ====

# --------------------------------------------------------------- v2 encoding --

# The level-1 seams. A code changes completely across one of these lines, so a
# port that gets the serpentine or the clamp wrong fails here rather than
# somewhere subtle.
LAT_SEAMS = (-90.0, -45.0, 0.0, 45.0, 90.0)
LONG_SEAMS = (-180.0, -120.0, -60.0, 0.0, 60.0, 120.0, 180.0)

boundary = []
for seam in LAT_SEAMS:
    for latitude in (below(seam), seam, above(seam)):
        if -90.0 <= latitude <= 90.0:
            boundary.append((latitude, 10.0))
for seam in LONG_SEAMS:
    for longitude in (below(seam), seam, above(seam)):
        if -180.0 <= longitude <= 180.0:
            boundary.append((10.0, longitude))

v2_sections = [
    ("The domain version 1 rejected: the poles and both ends of the antimeridian", [
        (90.0, 0.0), (-90.0, 0.0), (0.0, 180.0), (0.0, -180.0),
        (90.0, 180.0), (90.0, -180.0), (-90.0, 180.0), (-90.0, -180.0),
    ]),
    ("Origin, and the four signed zeroes that name the same point", [
        (0.0, 0.0), (-0.0, -0.0), (0.0, -0.0), (-0.0, 0.0),
        (-0.0, 90.0), (45.0, -0.0),
    ]),
    ("One unit in the last place either side of every level-1 boundary", boundary),
    ("Landmarks", [
        (43.65, -79.38), (43.6426, -79.3871), (23.0225, 72.5714),
        (-33.8568, 151.2153), (-13.1631, -72.545), (64.1466, -21.9426),
        (51.5007, -0.1246), (35.6586, 139.7454), (-22.9519, -43.2105),
        (30.0444, 31.2357), (55.7558, 37.6173), (1.2897, 103.8501),
    ]),
    ("Metres apart, across a seam, sharing nothing", [
        (51.4778, -0.00002), (51.4778, 0.00002),
        (-0.00002, 109.3), (0.00002, 109.3),
        (44.99999, 20.0), (45.00001, 20.0),
        (10.0, 59.99999), (10.0, 60.00001),
    ]),
    ("Cell corners and centres, where a rounding step would show", [
        (-90.0, -180.0), (-89.99999424, -179.99998464),
        (-89.99997696, -179.99996928), (12.34567, 98.76543),
        (12.345678, 98.765432), (-12.345678, -98.765432),
    ]),
    ("Doubles that are awkward to convert", [
        (0.1 + 0.2, 0.1 + 0.2), (1.9999999999999998, 1.9999999999999998),
        (-1.9999999999999998, -1.9999999999999998),
        (43.649999999999999, -79.379999999999999),
        (2.675, -2.675), (0.615, -0.615), (1.005, -1.005),
        (5.5555555555, -5.5555555555), (1e-7, -1e-7), (1e-9, -1e-9),
    ]),
]

v2_sweep = [(latitude, longitude)
            for latitude in (-89.5, -45.25, -1.125, -0.5, 0.5, 1.125, 45.25, 89.5)
            for longitude in (-179.5, -90.25, -1.125, -0.5, 0.5, 1.125, 90.25, 179.5)]
v2_sections.append(("Systematic sweep over signs and magnitudes", v2_sweep))

random.seed(20260826)
v2_randomised = []
while len(v2_randomised) < 2500:
    places = [0, 1, 2, 3, 4, 5, 6, 7, 9, 12]
    latitude = round(random.uniform(-90.0, 90.0), random.choice(places))
    longitude = round(random.uniform(-180.0, 180.0), random.choice(places))
    if abs(latitude) > 90 or abs(longitude) > 180:
        continue
    v2_randomised.append((latitude, longitude))
v2_sections.append(("Randomised sample, seed 20260826, mixed decimal precision",
                    v2_randomised))

lines = ["# latitude,longitude,code",
         "# Version 2. code is the unformatted 10-character form:",
         "# encode(lat, lng, formatted = false).",
         "# The row 0.0,180.0 is also the row for 179.99999999999999, which is",
         "# exactly 180.0 once stored as a double. Version 1 rejected it; version 2",
         "# normalises it to -180 and encodes it.",
         ""]
v2_encoded = 0
v2_points = []
for title, points in v2_sections:
    lines.append("# --- " + title)
    for latitude, longitude in points:
        valid, why = GPC.is_valid_coordinates(latitude, longitude)
        assert valid, f"{title}: ({latitude!r}, {longitude!r}) is outside the domain ({why})"
        code = GPC.encode(latitude, longitude, False)
        lines.append(f"{fmt(latitude)},{fmt(longitude)},{code}")
        v2_points.append((latitude, longitude, code))
        v2_encoded += 1
    lines.append("")
write("v2_encoding.csv", lines)
print(f"v2_encoding.csv           {v2_encoded:5} vectors")

# --------------------------------------------------------------- v2 decoding --

v2_candidates = [code for _, _, code in v2_points[:len(v2_points) - len(v2_randomised)]]
random.seed(11)
v2_candidates += [GPC.encode(latitude, longitude, False)
                  for latitude, longitude in random.sample(v2_randomised, 400)]

seen = set()
v2_codes = []
for code in v2_candidates:
    if code not in seen:
        seen.add(code)
        v2_codes.append(code)

# The alias table of section 8, asserted through the decoded value rather than
# through classification: a code spelled with a confusable letter has to reach
# the same cell as the code spelled with the symbol it stands for. Classifying
# it is not enough -- a port that aliases V to the wrong symbol still produces a
# well-formed code, just not this one.
ALIAS_PAIRS = [("O", "0"), ("I", "1"), ("S", "5"), ("Z", "2"),
               ("B", "8"), ("A", "4"), ("E", "3"), ("V", "W")]
alias_rows = []
for typed, meant in ALIAS_PAIRS:
    for position in (1, 5, 9):
        base = "G3RJM98NM9"
        spelled = base[:position] + meant + base[position + 1:]
        if spelled[0] == "X":
            continue
        alias_rows.append((base[:position] + typed + base[position + 1:],
                           GPC.decode(spelled)))

lines = ["# code,latitude,longitude",
         "# Version 2. A code names a cell and decodes to that cell's centre,",
         "# rounded to six decimal places. Equality, not tolerance.",
         "# The formatted and unformatted forms of a code MUST decode identically.",
         ""]
for code in v2_codes:
    latitude, longitude = GPC.decode(code)
    lines.append(f"{code},{fmt(latitude)},{fmt(longitude)}")
lines.append("")
lines.append("# --- The alias table: a confusable letter reaches the symbol's own cell")
for code, (latitude, longitude) in alias_rows:
    lines.append(f"{code},{fmt(latitude)},{fmt(longitude)}")
lines.append("")
write("v2_decoding.csv", lines)
print(f"v2_decoding.csv           {len(v2_codes) + len(alias_rows):5} vectors")

# ------------------------------------------------------------------- v2 area --

lines = ["# code,south,west,north,east",
         "# Version 2. The boundaries of the cell, as decodeToArea returns them.",
         "# The north edge of the top row is +90 and the east edge of the last",
         "# column is +180, even though neither value encodes to that cell.",
         ""]
for code in v2_codes[:400]:
    south, west, north, east = GPC.decode_to_area(code)
    lines.append(f"{code},{fmt(south)},{fmt(west)},{fmt(north)},{fmt(east)}")
lines.append("")
write("v2_area.csv", lines)
print(f"v2_area.csv               {min(400, len(v2_codes)):5} vectors")

# --------------------------------------------------------------- v2 classify --

classify_groups = [
    ("Nothing to parse", [("", "GPC_NULL"), ("   ", "GPC_NULL"), ("\t", "GPC_NULL")]),
    ("Accepted forms of the same geometric code", [
        ("#G3RJM-98NM9", ""), ("G3RJM98NM9", ""), ("g3rjm98nm9", ""),
        ("  G3RJM98NM9  ", ""), ("G3RJM 98NM9", ""), ("#g3rjm-98nm9", ""),
        ("--G3RJM98NM9##", ""),
    ]),
    ("The alias table reads a confusable letter as the symbol it stands for", [
        ("G3RJMI8NM9", ""), ("G3RJM98NMO", ""), ("G3RJM98NMB", ""),
        ("G3RJM98NMA", ""), ("G3RJM98NME", ""), ("G3RJM98NMS", ""),
        ("G3RJM98NMZ", ""), ("G3RJM98NMV", ""),
    ]),
    ("L is a symbol of the alphabet and is never read as 1", [
        ("G3RJM98NML", ""), ("LLLLLLLLLL", ""),
    ]),
    ("Reserved: well formed, begins with X, names no cell", [
        ("X000000000", ""), ("XXXXXXXXXX", ""), ("#XG3RJ-98NM9", ""),
    ]),
    ("Wrong length, including an eleven-character version 1 code", [
        ("G3RJM98NM", "GPC_LENGTH"), ("G3RJM98NM99", "GPC_LENGTH"),
        ("#FN5G-CDKL-HDC", "GPC_LENGTH"), ("#", "GPC_LENGTH"),
    ]),
    ("U, Q and Y are rejected rather than aliased", [
        ("G3RJM98NMU", "GPC_CHAR"), ("G3RJM98NMQ", "GPC_CHAR"),
        ("G3RJM98NMY", "GPC_CHAR"), ("G3RJM98NM!", "GPC_CHAR"),
    ]),
    ("The check character has to hold, here as well as in decode", [
        ("#G3RJM-98NM9*T", ""), ("#g3rjm-98nm9*t", ""), ("XG3RJ98NM9*6", ""),
        ("#G3RJM-98NM9*5", "GPC_CHECK"), ("#G3RJM-98NM9*", "GPC_CHECK"),
        ("#G3RJM-98NM9*TT", "GPC_CHECK"), ("#G3RJM-98NM9*Q", "GPC_CHECK"),
        ("XG3RJ98NM9*T", "GPC_CHECK"),
    ]),
]

lines = ["# class,message,input",
         "# Version 2. class is GEOMETRIC, RESERVED or INVALID. message is the",
         "# reason code, empty unless the class is INVALID.",
         "# input is the final field and runs verbatim to end of line, including any",
         "# '#', '*', spaces or separators. Split on the first two commas only, and",
         "# do not trim the input field - two cases are whitespace only.",
         ""]
classify_count = 0
for title, cases in classify_groups:
    lines.append("# --- " + title)
    for raw, expected in cases:
        kind, message = GPC.validate(raw)
        assert message == expected, (raw, kind, message, expected)
        lines.append(f"{kind},{message},{raw}")
        classify_count += 1
    lines.append("")
write("v2_classify.csv", lines)
print(f"v2_classify.csv           {classify_count:5} vectors")

# ------------------------------------------------------------------ v2 check --

check_codes = [code for _, _, code in v2_points[:40]]
random.seed(13)
check_codes += random.sample(v2_codes, 160)

lines = ["# code,check",
         "# Version 2. The optional GF(25) check character, written after a star:",
         "# #G3RJM-98NM9*T. Not canonical, and never emitted unless asked for.",
         ""]
seen = set()
check_rows = 0
for code in check_codes:
    if code in seen:
        continue
    seen.add(code)
    lines.append(f"{code},{GPC.check_character(code)}")
    check_rows += 1
lines.append("")
write("v2_check.csv", lines)
print(f"v2_check.csv              {check_rows:5} vectors")

# ------------------------------------------------------------------ v2 short --

# The short form is the last five characters, and recovery is exact whenever the
# reference is within half a level-5 cell on each axis. The corpus walks that
# box to its corners, crosses the antimeridian in both directions, and clamps at
# the poles, because those are the three places the arithmetic could be wrong
# without any random sample noticing.

HALF_LAT = 1562 * 180.0 / 7812500.0     # 0.03598848
HALF_LONG = 1562 * 360.0 / 11718750.0   # 0.04798464

short_cases = []


def add_short(title, points):
    rows = []
    for latitude, longitude, d_lat, d_long in points:
        code = GPC.encode(latitude, longitude, False)
        reference = (latitude + d_lat, longitude + d_long)
        assert -90.0 <= reference[0] <= 90.0 and -180.0 <= reference[1] <= 180.0
        recovered = GPC.recover_short(GPC.shorten(code), reference[0],
                                      reference[1], False)
        assert recovered == code, (code, reference, recovered)
        rows.append((GPC.shorten(code), reference[0], reference[1], code))
    short_cases.append((title, rows))


add_short("The reference on the point", [
    (43.65, -79.38, 0.0, 0.0), (23.0225, 72.5714, 0.0, 0.0),
    (-33.8568, 151.2153, 0.0, 0.0), (-13.1631, -72.545, 0.0, 0.0),
    (0.0, 0.0, 0.0, 0.0),
])

# Every corner of the box recovery is guaranteed over, at three latitudes.
corners = [(dla, dlo) for dla in (-HALF_LAT, 0.0, HALF_LAT)
           for dlo in (-HALF_LONG, 0.0, HALF_LONG)]
add_short("Every corner of the half-cell box", [
    (latitude, longitude, dla, dlo)
    for latitude, longitude in ((43.65, -79.38), (-33.8568, 151.2153),
                                (0.00001, 0.00001))
    for dla, dlo in corners
])

add_short("Across the antimeridian, in both directions", [
    (0.0, -179.99, 0.0, HALF_LONG), (0.0, 179.99, 0.0, -HALF_LONG),
    (0.0, -179.999, 0.0, 0.04), (0.0, 179.999, 0.0, -0.04),
    (45.5, -179.95, 0.0, 0.045), (-45.5, 179.95, 0.0, -0.045),
])

add_short("At the poles, where rows clamp rather than wrap", [
    (90.0, 0.0, -HALF_LAT, 0.0), (-90.0, 0.0, HALF_LAT, 0.0),
    (89.99, 45.0, 0.009, 0.0), (-89.99, -45.0, -0.009, 0.0),
])

random.seed(20260827)
add_short("Randomised, reference drawn inside the guaranteed box", [
    (latitude, longitude,
     random.uniform(-HALF_LAT, HALF_LAT), random.uniform(-HALF_LONG, HALF_LONG))
    for latitude, longitude in [(round(random.uniform(-88.0, 88.0), 6),
                                 round(random.uniform(-178.0, 178.0), 6))
                                for _ in range(300)]
])

lines = ["# short,refLatitude,refLongitude,code",
         "# Version 2. The last five characters of a code, recovered against a",
         "# reference. Exact whenever the reference is within half a level-5 cell",
         "# on each axis: 0.03598848 degrees of latitude, 0.04798464 of longitude.",
         "# A port must use floor division, not truncation, or the rows with a",
         "# reference north-east of the point are the only ones that pass.",
         ""]
short_rows = 0
for title, rows in short_cases:
    lines.append("# --- " + title)
    for short, ref_lat, ref_long, code in rows:
        lines.append(f"{short},{fmt(ref_lat)},{fmt(ref_long)},{code}")
        short_rows += 1
    lines.append("")
write("v2_short.csv", lines)
print(f"v2_short.csv              {short_rows:5} vectors")

# ------------------------------------------------------------ v2 corrections --

# The ordered candidate list. What this pins is not that the right code is in
# the set -- that is a statistical claim and lives in reference/measure.py --
# but that all four ports generate the same candidates and rank them the same
# way, down to the tie-break on the integer form.

correction_cases = []
for level, latitude, longitude, position, replacement in [
        (6, 43.65, -79.38, 9, "0"),        # a typo in the last three, harmless
        (6, 43.65, -79.38, 5, "T"),        # the dangerous middle
        (4, 43.65, -79.38, 3, "1"),        # a wide window, many neighbours
        (7, 23.0225, 72.5714, 8, "W"),
        (6, -33.8568, 151.2153, 6, "5"),
        (5, -13.1631, -72.545, 4, "N"),
        (6, 64.1466, -21.9426, 2, "8"),
        (6, 0.0, -179.999, 7, "J"),        # against the antimeridian
        (6, 0.0, 179.999, 7, "J"),
        (3, 90.0, 0.0, 5, "M"),            # the polar row, where rows clamp
        (3, -90.0, 0.0, 5, "M"),
        (8, 51.5007, -0.1246, 10, "R"),
        (6, 35.6586, 139.7454, 1, "R")]:   # position 1, usually caught by X
    code = GPC.encode(latitude, longitude, False)
    typo = code[:position - 1] + replacement + code[position:]
    correction_cases.append((level, latitude, longitude, typo))

# A code with adjacent repeats yields 242 candidates rather than 249, and no
# port may pad it back with duplicates.
correction_cases.append((2, 90.0, 0.0, "P4444PPPPP"))
correction_cases.append((6, 43.65, -79.38, "G3RJM98NM9"))   # no typo at all

# The ends of the level range, which the corpus otherwise never reached. Level 1
# is the whole 45-by-60 degree block and its eight neighbours, so almost every
# candidate survives; levels 9 and 10 are a few hundred metres across, so almost
# none does.
correction_cases.append((1, 43.65, -79.38, "G3RJM98N09"))
correction_cases.append((9, 43.65, -79.38, "G3RJM98N09"))
correction_cases.append((10, 43.65, -79.38, "G3RJM98N09"))

# Nothing survives the filter. The header promises an empty candidate list is
# possible and nothing demonstrated it: a reserved code yields only candidates
# that change position 1, and changing position 1 moves the point into another
# level-1 block, which no window at level 6 can contain. A port that returned a
# candidate here, or that refused rather than returning nothing, would pass every
# other row in this file.
correction_cases.append((6, 43.65, -79.38, "XG3RJ98NM9"))
correction_cases.append((10, 0.0, 0.0, "G3RJM98NM9"))       # reference far away

lines = ["# level,refLatitude,refLongitude,input,candidates",
         "# Version 2. suggestCorrections: at most 249 candidates -- 240",
         "# substitutions then the adjacent transpositions that change the code --",
         "# filtered to the reference's level-k cell and its eight neighbours, and",
         "# ranked by 9*drow^2 + 16*dcol^2 with ties broken on the integer form.",
         "# candidates is the last field and is the unformatted codes joined by",
         "# single spaces. It is empty when nothing survived the filter.",
         ""]
for level, latitude, longitude, typo in correction_cases:
    candidates = GPC.suggest_corrections(typo, latitude, longitude, level, False)
    lines.append(f"{level},{fmt(latitude)},{fmt(longitude)},{typo},"
                 + " ".join(candidates))
lines.append("")
write("v2_corrections.csv", lines)
print(f"v2_corrections.csv        {len(correction_cases):5} vectors")

# ------------------------------------------------------------------ v2 cells --

# cell and neighbours together, because the second is only meaningful in terms
# of the first. The polar rows are here for the five-neighbour case and the
# antimeridian for the wrapping one.

cell_points = [(43.65, -79.38), (23.0225, 72.5714), (-33.8568, 151.2153),
               (0.0, 0.0), (90.0, 0.0), (-90.0, 0.0), (0.0, -180.0),
               (0.0, 179.99999), (89.99999, 179.99999), (-89.99999, -179.99999),
               (44.99999, 20.0), (45.00001, 20.0)]

lines = ["# level,code,cell,neighbours",
         "# Version 2. cell is the first `level` characters of the code, bare.",
         "# neighbours is the last field: the cells sharing an edge or a corner,",
         "# joined by single spaces, in the order north, north-east, east,",
         "# south-east, south, south-west, west, north-west.",
         "# Columns wrap at the antimeridian and rows do not, so a cell in the top",
         "# or bottom row has five entries and not eight.",
         ""]
cell_rows = 0
for latitude, longitude in cell_points:
    code = GPC.encode(latitude, longitude, False)
    for level in range(1, 11):
        cell = GPC.cell(code, level)
        lines.append(f"{level},{code},{cell}," + " ".join(GPC.neighbours(cell)))
        cell_rows += 1
    lines.append("")
write("v2_cells.csv", lines)
print(f"v2_cells.csv              {cell_rows:5} vectors")

# ---------------------------------------------------------------- v2 integer --

integer_codes = ["0000000000", "XXXXXXXXXX", "X000000000", "W999999999",
                 "0000000001", "JPPPP00000"]
integer_codes += [code for _, _, code in v2_points[:60]]
random.seed(17)
integer_codes += random.sample(v2_codes, 140)

lines = ["# code,value",
         "# Version 2. The base-25 integer form of section 13, both directions.",
         "# Forty-seven bits, so six bytes big-endian. Sorting the values sorts the",
         "# codes, and every geometric code is below 91552734375000 while every",
         "# reserved one is at or above it.",
         ""]
seen = set()
integer_rows = 0
for code in integer_codes:
    if code in seen:
        continue
    seen.add(code)
    lines.append(f"{code},{GPC.to_integer(code)}")
    integer_rows += 1
lines.append("")
write("v2_integer.csv", lines)
print(f"v2_integer.csv            {integer_rows:5} vectors")

# --------------------------------------------------------------- v2 distance --

# The only file here compared to a tolerance rather than to equality. Section
# 18.5 says why: no standard library rounds sine, cosine or arc sine correctly,
# so four ports agree to about a millimetre and not exactly.
#
# The same reasoning applies to this file's own contents, which is easy to miss.
# A full-precision haversine result differs in its last bits between one
# platform's library and another's -- measured at about seven nanometres over a
# fifteen-thousand kilometre pair -- so writing every digit would make the
# corpus regenerate differently on a different machine and break the one
# property the regenerate-identically job exists to hold. The value is therefore
# quantised to a millimetre, which is a thousand times finer than any real
# implementation error and a hundred thousand times coarser than the platform
# divergence.
#
# Quantising has one edge: a true value sitting almost exactly halfway between
# two millimetres could round up on one platform and down on another. GUARD
# below rejects any pair within a tenth of a micrometre of that midpoint, which
# is ten times the divergence, so the corpus fails here at authoring time rather
# than mysteriously in CI later. If it ever fires, replace the offending pair.

DISTANCE_PLACES = 3        # millimetres
GUARD = 1e-7               # metres from a rounding tie


def quantised_metres(a, b):
    """The distance to a millimetre, refusing anything near a rounding tie."""
    exact = GPC.distance(a, b)
    scaled = exact * 10 ** DISTANCE_PLACES
    if abs(scaled - math.floor(scaled) - 0.5) < GUARD * 10 ** DISTANCE_PLACES:
        raise AssertionError(
            f"distance({a}, {b}) = {exact!r} sits on a rounding tie and would "
            f"not regenerate identically on another platform. Replace the pair.")
    return round(exact, DISTANCE_PLACES)


distance_pairs = [
    ("G3RJM98NM9", "G3RJM98NM9"),          # zero
    ("G3RJM98NM9", "6LK4XNRP0R"),          # Toronto to Sydney
    ("P4444PPPPP", "3PPPP00000"),          # pole to pole
    ("KDC8XJM49X", "C8HKC13C80"),
    ("G3RJM98NM9", "G3RJM98NM8"),          # one cell apart
    ("G3RJM", "G3RJM98NM9"),               # different levels
    ("G", "6"),                            # level 1
    ("G3RJM98NM9", "RDX9RTN19T"),
]
distance_pairs += [(GPC.encode(0.0, 0.0, False), GPC.encode(0.0, 180.0, False)),
                   (GPC.encode(0.0, -0.00002, False),
                    GPC.encode(0.0, 0.00002, False)),
                   (GPC.encode(51.4778, -0.00002, False),
                    GPC.encode(51.4778, 0.00002, False)),
                   (GPC.encode(89.99999, 0.0, False),
                    GPC.encode(89.99999, 180.0, False))]

random.seed(19)
distance_pairs += [(GPC.encode(*random.choice(v2_randomised), formatted=False),
                    GPC.encode(*random.choice(v2_randomised), formatted=False))
                   for _ in range(60)]

lines = ["# a,b,metres",
         "# Version 2. Great-circle metres between two cell centres, on a sphere",
         "# of radius 6371008.8 m. The cells may be of different levels.",
         "#",
         "# THIS FILE IS COMPARED TO A TOLERANCE, NOT TO EQUALITY. Ports must",
         "# agree within one millimetre; asserting equality passes on the machine",
         "# it was written on and fails somewhere else. Section 18.5.",
         "#",
         "# The expected value is itself quantised to a millimetre, for the same",
         "# reason: a full-precision haversine result differs in its last bits",
         "# between platform libraries, and this corpus has to regenerate",
         "# identically anywhere.",
         ""]
for a, b in distance_pairs:
    lines.append(f"{a},{b},{fmt(quantised_metres(a, b))}")
lines.append("")
write("v2_distance.csv", lines)
print(f"v2_distance.csv           {len(distance_pairs):5} vectors")

# ------------------------------------------------------------ v2 coordinates --

coordinate_points = [(43.65, -79.38), (43.650006, -79.380004), (0.0, 0.0),
                     (-0.0, -0.0), (90.0, 0.0), (-90.0, 0.0), (0.0, 180.0),
                     (0.0, -180.0), (1.0 - 1e-9, 0.0), (43.0, -79.0),
                     (-33.856808, 151.215314), (23.022501, 72.571407),
                     (64.1466, -21.9426), (12.345678, -98.765432),
                     (0.000012, 0.000015), (89.999988, 0.000015),
                     (-89.999988, -179.999985), (59.999999, 119.999999)]
coordinate_points += [GPC.decode(code) for code in v2_codes[:80]]

# Two files rather than one, because both forms carry a comma of their own and
# only the last field of a row may.

seen = set()
coordinates = []
for latitude, longitude in coordinate_points:
    if (latitude, longitude) not in seen:
        seen.add((latitude, longitude))
        coordinates.append((latitude, longitude))

lines = ["# latitude,longitude,uri",
         "# Version 2. toGeoURI, and fromGeoURI reading it back to the same",
         "# coordinates. Six decimal places, trailing zeros dropped, which is",
         "# exactly what decode produces -- so a code written out this way and read",
         "# back encodes to the same code every time. Section 19.2.",
         "# uri is the last field: it contains a comma of its own.",
         ""]
for latitude, longitude in coordinates:
    lines.append(f"{fmt(latitude)},{fmt(longitude)},"
                 + GPC.to_geo_uri(latitude, longitude))
lines.append("")
write("v2_geo.csv", lines)
print(f"v2_geo.csv                {len(coordinates):5} vectors")

lines = ["# latitude,longitude,dms",
         "# Version 2. toDMS, and fromDMS reading it back. The DMS form is rounded",
         "# to a hundredth of a second and is lossy: reading back gives a",
         "# coordinate within 0.155 m, not the one written. What survives is the",
         "# code, because decode returns a cell centre and that sits eight times",
         "# further from the nearest boundary. Section 19.1.",
         "# dms is the last field: it contains a comma of its own.",
         ""]
for latitude, longitude in coordinates:
    lines.append(f"{fmt(latitude)},{fmt(longitude)},"
                 + GPC.to_dms(latitude, longitude))
lines.append("")
write("v2_dms.csv", lines)
print(f"v2_dms.csv                {len(coordinates):5} vectors")

# ------------------------------------------------------------ v2 wide sample --

# The same design as the version 1 sample, over the version 2 domain, which is
# wider at both ends: the spans below are inclusive of the poles and of both
# ends of the antimeridian, so the sample exercises the clamp and the +180
# normalisation rather than stopping short of them.

V2_SAMPLE_COUNT = 100_000
V2_SAMPLE_SEED = 20260824
V2_SAMPLE_LAT_SPAN = 18_000_001   # -90.00000 .. 90.00000 in units of 1e-5
V2_SAMPLE_LONG_SPAN = 36_000_001  # -180.00000 .. 180.00000 in units of 1e-5


def v2_sample_points(count=V2_SAMPLE_COUNT, seed=V2_SAMPLE_SEED):
    """Yield the version 2 wide sample, one (latitude, longitude) at a time."""
    state = seed
    for _ in range(count):
        state = (SAMPLE_MULTIPLIER * state + SAMPLE_INCREMENT) % SAMPLE_MODULUS
        latitude = (state % V2_SAMPLE_LAT_SPAN - (V2_SAMPLE_LAT_SPAN - 1) // 2) / 100000
        state = (SAMPLE_MULTIPLIER * state + SAMPLE_INCREMENT) % SAMPLE_MODULUS
        longitude = (state % V2_SAMPLE_LONG_SPAN - (V2_SAMPLE_LONG_SPAN - 1) // 2) / 100000
        yield latitude, longitude


v2_sample_codes = [GPC.encode(latitude, longitude, False)
                   for latitude, longitude in v2_sample_points()]
v2_sample_digest = hashlib.sha256("\n".join(v2_sample_codes).encode("utf-8")).hexdigest()

lines = ["# count,seed,digest",
         "# Version 2. The wide sample is generated, not stored. Walk the linear",
         "# congruential sequence described in README.md, encode every point, join",
         "# the unformatted codes with a single LF and take the SHA-256 of those",
         "# UTF-8 bytes. A port that reproduces this digest agrees with the other",
         "# three byte for byte.",
         "",
         f"{V2_SAMPLE_COUNT},{V2_SAMPLE_SEED},{v2_sample_digest}",
         ""]
write("v2_sample.csv", lines)
print(f"v2_sample.csv             {V2_SAMPLE_COUNT:5} points, "
      f"digest {v2_sample_digest[:16]}...")

# ----------------------------------------------------------------- v2 screen --

# The advisory list is expanded from a word file that is not in this repository,
# so this section cannot construct a matching code: it does not know what would
# match. It searches instead, walking the wide sample already generated above
# and keeping the codes that flag. Deterministic, because the sample is.
#
# Changing the list changes these two files. That is expected, and is why
# screening/expand.py says to run this script afterwards.

screen_digest = hashlib.sha256(
    "\n".join(sorted(screen_list.ENTRIES)).encode("utf-8")).hexdigest()

lines = ["# version,count,digest",
         "# Version 2. Not a vector so much as an identity: every port embeds its",
         "# own copy of the advisory list, and this is how they are held to being",
         "# the same list. digest is the SHA-256 of the sorted entries joined by",
         "# LF. CI cannot rebuild the list -- it has no word file -- so this row is",
         "# what catches a port whose copy drifted.",
         "",
         f"{screen_list.VERSION},{len(screen_list.ENTRIES)},{screen_digest}",
         ""]
write("v2_screen_list.csv", lines)
print(f"v2_screen_list.csv        {len(screen_list.ENTRIES):5} entries, "
      f"list {screen_list.VERSION}, digest {screen_digest[:16]}...")

flagged = []
for code in v2_sample_codes:
    _, spans = GPC.screen(code)
    if spans:
        flagged.append((code, spans))
    if len(flagged) >= 40:
        break

# Reserved codes screen like any other: an X in position 1 does not stop the
# remaining nine characters spelling something. Take the flagged codes and put
# an X in front of a nine-character tail to prove it.
reserved = []
for code, _ in flagged[:6]:
    candidate = "X" + code[1:]
    _, spans = GPC.screen(candidate)
    if spans:
        reserved.append((candidate, spans))

# Codes that match nothing, so that a port cannot pass by returning every span
# it can think of.
clean = []
for code in v2_sample_codes:
    _, spans = GPC.screen(code)
    if not spans:
        clean.append((code, spans))
    if len(clean) >= 20:
        break

# The rows above are whatever the sample happened to contain, which leaves the
# coverage to luck: it produced no span longer than four symbols and only one
# starting at position 6. A port could drop the last window of its loop, or stop
# short of the longest length, and every row above would still pass.
#
# So the rows below are built rather than found. One variant of each length the
# list contains is placed at every position it can occupy, with the rest of the
# code filled by a symbol chosen so that nothing else matches. The variants come
# from the archive, since a hash cannot be put anywhere; the expected spans still
# come from GPC.screen, so what is asserted remains what the compiled list says
# and not what this script believes.
ALPHABET = "0123456789CDFGHJKLMNPRTWX"
all_variants, _ = expand.variants()
by_length = {}
for variant in all_variants:
    by_length.setdefault(len(variant), []).append(variant)


def plant(variant, start):
    """`variant` at 1-based `start`, padded to ten symbols and nothing else hit.

    Tries each symbol of the alphabet as the padding and keeps the first that
    leaves exactly the one span, so the row says what it is there to say. A
    padding symbol is never X in position 1: a reserved code is the subject of
    its own section below.
    """
    length = len(variant)
    want = [(start, length)]
    fallback = None
    for filler in ALPHABET:
        head = filler * (start - 1)
        tail = filler * (10 - length - (start - 1))
        code = head + variant + tail
        if code[0] == "X":
            continue
        _, spans = GPC.screen(code)
        if spans == want:
            return code, spans
        if fallback is None and want[0] in spans:
            fallback = (code, spans)
    if fallback is None:
        raise SystemExit(
            "no padding leaves %s findable at position %d" % (length, start))
    return fallback


planted = []
for length in sorted(by_length):
    variant = by_length[length][0]
    for start in range(1, 10 - length + 2):
        planted.append(plant(variant, start))

# Two matches in one code, twice over. Different variants first, which fails a
# port that stops at its first match. Then the same variant twice, which fails a
# port that collects matched hashes in a set: it has two spans to report and one
# hash to report them from, and returning one span is the easy mistake.
pairs = []
if len(by_length.get(4, [])) >= 2:
    first, second = by_length[4][0], by_length[4][1]
    for a, b in ((first, second), (first, first)):
        for filler in ALPHABET:
            code = a + filler + b + filler
            if code[0] == "X":
                continue
            _, spans = GPC.screen(code)
            if spans == [(1, 4), (6, 4)]:
                pairs.append((code, spans))
                break

lines = ["# code,spans",
         "# Version 2. screen: the matched substrings of a code, as",
         "# position:length joined by single spaces, ordered by position and then",
         "# by length. Positions count from 1. The field is empty when nothing",
         "# matched, which is a result and not an absence -- section 17.4.",
         "# These rows depend on the list identified in v2_screen_list.csv.",
         ""]


def screen_rows(title, rows):
    lines.append("# --- " + title)
    for code, spans in rows:
        lines.append(f"{code}," + " ".join(f"{p}:{n}" for p, n in spans))
    lines.append("")


screen_rows("Codes that match nothing", clean)
screen_rows("Codes that match", flagged)
screen_rows("A reserved code screens like any other", reserved)
screen_rows("One variant of each length, at every position it can occupy", planted)
screen_rows("Two matches in one code, different and identical", pairs)
write("v2_screen.csv", lines)
_screen_total = (len(clean) + len(flagged) + len(reserved) + len(planted)
                 + len(pairs))
print(f"v2_screen.csv             {_screen_total:5} "
      f"vectors, {len(flagged) + len(reserved) + len(planted) + len(pairs)}"
      f" of them matching")

# Nothing above writes the sample itself. When a port disagrees about the
# digest, `python test_data/generate.py --dump codes.csv` writes every point and
# its expected code so the first differing line can be found directly.
if "--dump" in sys.argv:
    target = Path(sys.argv[sys.argv.index("--dump") + 1])
    with open(target, "w", newline="\n") as handle:
        handle.write("latitude,longitude,code\n")
        for (latitude, longitude), code in zip(v2_sample_points(), v2_sample_codes):
            handle.write(f"{fmt(latitude)},{fmt(longitude)},{code}\n")
    print(f"dumped {V2_SAMPLE_COUNT} points to {target}")
