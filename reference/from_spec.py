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

"""A second opinion, transcribed from Appendix A of SPEC.md and nothing else.

This file exists to answer one question: does the specification stand on its
own? It is a line-by-line transcription of the pseudocode on that page, written
without consulting gpc2.py, and it is deliberately kept that way. Do not
refactor it to share code with gpc2.py, and do not fix it by looking at
gpc2.py -- if the two disagree, the specification is what needs correcting.

verify.py holds the two against each other. Keep this file honest and it stays
the only real evidence that a fifth port can be written from the document.
"""

import math

ALPHABET = "0123456789CDFGHJKLMNPRTWX"


def encode(latitude, longitude):
    if not (math.isfinite(latitude) and math.isfinite(longitude)):
        raise ValueError
    if not -90.0 <= latitude <= 90.0:
        raise ValueError
    if not -180.0 <= longitude <= 180.0:
        raise ValueError

    if longitude == 180.0:
        longitude = -180.0

    row = math.floor((latitude + 90.0) * 7812500.0 / 180.0)
    col = math.floor((longitude + 180.0) * 11718750.0 / 360.0)

    if row < 0:
        row = 0
    if row > 7812499:
        row = 7812499
    if col < 0:
        col = 0
    if col > 11718749:
        col = 11718749

    out = ""
    r1 = row // 1953125
    c1 = col // 1953125
    k = c1 if r1 % 2 == 0 else 5 - c1
    out += ALPHABET[r1 * 6 + k]

    sr = r1
    sc = c1
    p = 1953125
    for level in range(2, 11):
        if level == 6:
            sr = 0
            sc = 0
        p = p // 5
        r = (row // p) % 5
        c = (col // p) % 5
        R = r if sc % 2 == 0 else 4 - r
        sr = sr + r
        C = c if sr % 2 == 0 else 4 - c
        sc = sc + c
        out += ALPHABET[R * 5 + C]

    return out


def round6(v):
    q = abs(v) // 100
    if abs(v) % 100 >= 50:
        q = q + 1
    if v < 0:
        q = -q
    return q / 1000000


def decode(code):
    i = ALPHABET.index(code[0])
    r1 = i // 6
    k = i % 6
    c1 = k if r1 % 2 == 0 else 5 - k

    row = r1
    col = c1
    sr = r1
    sc = c1
    for level in range(2, 11):
        if level == 6:
            sr = 0
            sc = 0
        j = ALPHABET.index(code[level - 1])
        R = j // 5
        C = j % 5
        r = R if sc % 2 == 0 else 4 - R
        sr = sr + r
        c = C if sr % 2 == 0 else 4 - C
        sc = sc + c
        row = row * 5 + r
        col = col * 5 + c

    return (round6((2 * row + 1) * 1152 - 9000000000),
            round6((2 * col + 1) * 1536 - 18000000000))
