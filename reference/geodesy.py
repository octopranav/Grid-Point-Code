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

"""Spherical helpers used only by the measurement harness.

None of this is part of the format. The format itself needs no trigonometry
and no Earth radius; these functions exist so that measure.py can express its
samples in metres. Distances are on a sphere, which is close enough for
figures quoted to three significant digits and keeps the harness readable.
"""

import math

R_EARTH = 6371008.8          # WGS84 mean radius, metres
M_PER_DEG_LAT = 111132.0     # mean metres per degree of latitude
M_PER_DEG_LNG = 111319.49    # metres per degree of longitude at the equator


def offset(latitude, longitude, bearing, distance):
    """Move `distance` metres along `bearing` degrees from a point."""
    d = distance / R_EARTH
    p1 = math.radians(latitude)
    l1 = math.radians(longitude)
    b = math.radians(bearing)
    p2 = math.asin(math.sin(p1) * math.cos(d)
                   + math.cos(p1) * math.sin(d) * math.cos(b))
    l2 = l1 + math.atan2(math.sin(b) * math.sin(d) * math.cos(p1),
                         math.cos(d) - math.sin(p1) * math.sin(p2))
    return math.degrees(p2), (math.degrees(l2) + 540) % 360 - 180


def haversine(a, b):
    """Great-circle distance in metres between two (latitude, longitude) pairs."""
    p1, l1 = math.radians(a[0]), math.radians(a[1])
    p2, l2 = math.radians(b[0]), math.radians(b[1])
    h = (math.sin((p2 - p1) / 2) ** 2
         + math.cos(p1) * math.cos(p2) * math.sin((l2 - l1) / 2) ** 2)
    return 2 * R_EARTH * math.asin(math.sqrt(h))


def random_point(rng):
    """Uniform over the sphere, not over the graticule. Sampling uniformly in
    latitude would crowd the poles and overstate how often nearby points share
    a prefix."""
    return math.degrees(math.asin(rng.uniform(-1.0, 1.0))), rng.uniform(-180.0, 180.0)


def shared_prefix(a, b):
    n = 0
    for x, y in zip(a, b):
        if x != y:
            break
        n += 1
    return n
