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

Output is deterministic. Running this without changing the corpus definitions
below must leave the files byte for byte identical, so an unexpected diff means
encoding behaviour changed. That is a breaking change and needs a major
version, never a quiet vector update.
"""

import hashlib
import random
import sys
from decimal import Decimal
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent
sys.path.insert(0, str(REPO / "python" / "src"))

from gridpointcode_algo_pranavpatel_ca import GPC  # noqa: E402


def fmt(value):
    """Shortest decimal that reads back as this double, never in exponent form."""
    text = repr(float(value))
    if "e" in text or "E" in text:
        return format(Decimal(text), "f")
    return text


def write(name, lines):
    """Write one vector file with LF endings on every platform."""
    with open(HERE / name, "w", newline="\n") as handle:
        handle.write("\n".join(lines))


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
         "# code is the unformatted 11-character form: encode(lat, lng, formatted = false)",
         ""]
encoded = 0
for title, points in sections:
    lines.append("# --- " + title)
    for latitude, longitude in points:
        valid, why = GPC.is_valid_coordinates(latitude, longitude)
        assert valid, f"{title}: ({latitude!r}, {longitude!r}) is outside the domain ({why})"
        lines.append(f"{fmt(latitude)},{fmt(longitude)},{GPC.encode(latitude, longitude, False)}")
        encoded += 1
    lines.append("")
write("encoding.csv", lines)
print(f"encoding.csv              {encoded:5} vectors")

# ------------------------------------------------------------------ decoding --

candidates = [GPC.encode(latitude, longitude, False)
              for _, points in sections[:7] for latitude, longitude in points]
random.seed(7)
candidates += [GPC.encode(latitude, longitude, False)
               for latitude, longitude in random.sample(randomised, 400)]

seen = set()
codes = []
for code in candidates:
    if code not in seen:
        seen.add(code)
        codes.append(code)

lines = ["# code,latitude,longitude",
         "# Every code names one cell and decodes to that cell's corner.",
         "# The formatted and unformatted forms of a code MUST decode identically.",
         ""]
for code in codes:
    latitude, longitude = GPC.decode(code)
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
         "# valid is true or false. message is empty when valid, otherwise the reason code.",
         "# input is the final field and runs verbatim to end of line, including any",
         "# '#', spaces or separators. Split on the first two commas only, and do not",
         "# trim the input field - one case is whitespace only.",
         ""]
code_count = 0
for title, cases in code_groups:
    lines.append("# --- " + title)
    for raw, expected in cases:
        valid, message = GPC.is_valid_gpc(raw)
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
         "# The poles and the antimeridian are outside the v1 domain and are rejected.",
         "# Latitude is checked before longitude when both are out of range.",
         ""]
for latitude, longitude, expected in coordinate_cases:
    valid, message = GPC.is_valid_coordinates(latitude, longitude)
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
    """Yield the shared wide sample, one (latitude, longitude) pair at a time."""
    state = seed
    for _ in range(count):
        state = (SAMPLE_MULTIPLIER * state + SAMPLE_INCREMENT) % SAMPLE_MODULUS
        latitude = (state % SAMPLE_LAT_SPAN - (SAMPLE_LAT_SPAN - 1) // 2) / 100000
        state = (SAMPLE_MULTIPLIER * state + SAMPLE_INCREMENT) % SAMPLE_MODULUS
        longitude = (state % SAMPLE_LONG_SPAN - (SAMPLE_LONG_SPAN - 1) // 2) / 100000
        yield latitude, longitude


sample_codes = [GPC.encode(latitude, longitude, False)
                for latitude, longitude in sample_points()]
sample_digest = hashlib.sha256("\n".join(sample_codes).encode("utf-8")).hexdigest()

lines = ["# count,seed,digest",
         "# The wide sample is generated, not stored. Walk the linear congruential",
         "# sequence described in README.md, encode every point, join the unformatted",
         "# codes with a single LF and take the SHA-256 of those UTF-8 bytes. A port",
         "# that reproduces this digest agrees with the other three byte for byte.",
         "",
         f"{SAMPLE_COUNT},{SAMPLE_SEED},{sample_digest}",
         ""]
write("sample.csv", lines)
print(f"sample.csv                {SAMPLE_COUNT:5} points, digest {sample_digest[:16]}...")

# Nothing above writes the sample itself. When a port disagrees about the
# digest, `python test_data/generate.py --dump codes.csv` writes every point and
# its expected code so the first differing line can be found directly.
if "--dump" in sys.argv:
    target = Path(sys.argv[sys.argv.index("--dump") + 1])
    with open(target, "w", newline="\n") as handle:
        handle.write("latitude,longitude,code\n")
        for (latitude, longitude), code in zip(sample_points(), sample_codes):
            handle.write(f"{fmt(latitude)},{fmt(longitude)},{code}\n")
    print(f"dumped {SAMPLE_COUNT} points to {target}")
