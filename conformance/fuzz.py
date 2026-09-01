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

"""Generate awkward input, put it to all four ports, and hold them to account.

    python conformance/fuzz.py [--cases N] [--seed S] [--rounds R] [--released]

The fixed battery in compare.py pins what somebody thought to write down. This
generates what nobody did.

**What it looks for is drawn from what actually went wrong.** Version 1.1.0 was
a repair release, and its four faults are the shape of this whole file:

  * `encode(89.9999999999999, 0)` pushed an out-of-domain index into the lookup
    table. It was accepted without error and decoded to (0.0, -90.0) -- a wrong
    answer, silently, half a world away. So coordinates are generated hard up
    against whole degrees and the domain bounds, not merely at random: uniform
    sampling would never once have produced that number.
  * Three ports converted a double to decimal three different ways, so one
    coordinate could yield three different codes. So every case is put to all
    four and diffed.
  * `isValid("CCCC-CCCC-CCC")` reported valid, and then decoding it raised. So
    every generated code is asked both questions, and a port that says yes to
    the first and then throws on the second has contradicted itself.
  * `encode(-0.0, -0.0)` gave a different code from `encode(0.0, 0.0)`. So
    negative zero is generated on purpose.

**Agreement is not correctness, and this is the trap worth naming.** Four ports
that all crash agree perfectly. The drivers already separate a documented error
(`ERR:`) from an unexpected one (`EXC:`), so an `EXC:` from any port fails on
its own account, whether or not the others produced it too.

Every run is reproducible: the seed is printed, and passing it back regenerates
exactly the same cases.

One gap, stated rather than hidden: NaN and the infinities are not generated.
The case file carries numbers as text, and the four languages disagree about
what text means those values -- one parses `inf`, one throws, one quietly
returns NaN. Sending them would test the harness rather than the ports.
"""

import argparse
import math
import os
import random
import sys
from pathlib import Path

from compare import (
    HERE, REGISTRIES, REPO, canonical, fetch, latest, run_all,
)

#: The symbols a code is made of. No O, I, S, Z, B, A, E, U, Q, V or Y.
ALPHABET = "0123456789CDFGHJKLMNPRTWX"

#: A known-good code, used as the thing mutations start from.
GOOD = "G3RJM98NM9"

#: How far a point may move by being encoded and decoded again: one level-10
#: cell, and not one unit in the fifth decimal place, which is what this first
#: asked for and is a different quantity. The grid is 4 by 6 at level 1 and
#: fives all the way down, so a cell is 180/(4*5^9) by 360/(6*5^9) degrees --
#: 2.304e-5 by 3.072e-5. Encode picks the cell the point is in and decode hands
#: back a point in that cell, so the two can be a cell apart and nothing is
#: wrong. A tighter bound reported twenty-two faults that were arithmetic
#: working exactly as specified.
LAT_CELL = 180.0 / (4 * 5 ** 9)
LNG_CELL = 360.0 / (6 * 5 ** 9)

#: The case file the drivers are pointed at.
CASES = HERE / ".fuzz-cases.txt"


def near(rng, value, limit):
    """A value pressed up against `value` from whichever side has room.

    This is where 1.1.0's worst fault lived: not at a boundary, but within
    rounding distance of one. The offsets reach down to the last bit a double
    can hold, because that is where the lookup index went out of domain.
    """
    for _ in range(8):
        step = rng.choice([1e-9, 1e-11, 1e-13, 1e-14, 1e-15, 0.0])
        sign = rng.choice([-1.0, 1.0])
        if step == 0.0:
            candidate = math.nextafter(value, sign * math.inf)
        else:
            candidate = value + sign * step
        if -limit <= candidate <= limit:
            return candidate
    return value


def outside(rng, limit):
    """A value past the edge of the world. A documented error, never a crash."""
    beyond = rng.choice([1e-15, 1e-9, 1e-3, 0.5, 10.0])
    return (limit if rng.random() < 0.5 else -limit) * (1.0 + beyond)


def coordinate(rng, limit):
    """One latitude or longitude, from a mix that is deliberately not uniform."""
    kind = rng.random()
    if kind < 0.30:
        return rng.uniform(-limit, limit)
    if kind < 0.55:
        # Hard up against a whole degree, which is where the domain fault was.
        return near(rng, float(rng.randint(-int(limit), int(limit))), limit)
    if kind < 0.70:
        # The edges of the world, and both zeros.
        return rng.choice([limit, -limit, 0.0, -0.0,
                           near(rng, limit, limit), near(rng, -limit, limit)])
    if kind < 0.85:
        # Long decimals, which is what made three ports disagree.
        return round(rng.uniform(-limit, limit), rng.randint(6, 17))
    if kind < 0.95:
        return outside(rng, limit)
    # Very small magnitudes, including subnormals.
    return rng.choice([1e-300, -1e-300, 5e-324, -5e-324, 1e-15, -1e-15])


def code(rng):
    """One candidate code string, mostly wrong on purpose."""
    kind = rng.random()

    if kind < 0.20:
        # Ten symbols at random. Many fall below the valid floor, which is the
        # exact shape that 1.1.0 called valid and then crashed on.
        return "".join(rng.choice(ALPHABET) for _ in range(10))

    if kind < 0.45:
        # A good code with one thing done to it.
        broken = list(GOOD)
        what = rng.randrange(4)
        at = rng.randrange(len(broken))
        if what == 0:
            broken[at] = rng.choice(ALPHABET)
        elif what == 1:
            del broken[at]
        elif what == 2:
            broken.insert(at, rng.choice(ALPHABET))
        else:
            broken[at] = broken[at].lower()
        return "".join(broken)

    if kind < 0.60:
        # Formatting a reader might type, and some they should not.
        body = "".join(rng.choice(ALPHABET) for _ in range(10))
        return rng.choice([
            "#" + body,
            body[:5] + "-" + body[5:],
            "#" + body[:5] + "-" + body[5:],
            " " + body + " ",
            body.lower(),
            "#" + body[:4] + "-" + body[4:7] + "-" + body[7:],
        ])

    if kind < 0.75:
        # Characters the alphabet threw out, which the alias table is meant to
        # absorb, plus some nobody ever promised anything about.
        body = list("".join(rng.choice(ALPHABET) for _ in range(10)))
        body[rng.randrange(10)] = rng.choice("OISZBAEUQVYoiszl!@ /*.é中")
        return "".join(body)

    if kind < 0.90:
        # Any length but ten.
        size = rng.choice([0, 1, 2, 5, 9, 11, 12, 20, 64])
        return "".join(rng.choice(ALPHABET) for _ in range(size))

    # The shapes that have their own history.
    return rng.choice([
        "CCCC-CCCC-CCC", "CCCCCCCCCC", "0000000000", "PPPPPPPPPP",
        "XG3RJ98NM9", "X000000000", GOOD + GOOD, "----------",
    ])


def transmissible(text):
    """Whether a string survives the case file unchanged.

    One case per line, fields split on a bar, so a string carrying a bar or a
    line break cannot be sent. Those are dropped rather than escaped: four
    drivers would each need the same unescaping, and a code with a bar in it is
    only another invalid character, which the generator has plenty of other
    ways to produce.
    """
    return not any(character in text for character in "|\n\r")


def generate(rng, count):
    """The cases for one round, and a note of what each one was."""
    cases = []
    origin = {}

    for i in range(count):
        latitude = coordinate(rng, 90.0)
        longitude = coordinate(rng, 180.0)
        label = "fz.encode.%d" % i
        cases.append("%s|encode|%r|%r" % (label, latitude, longitude))
        origin[label] = (latitude, longitude)

    for i in range(count):
        candidate = code(rng)
        if not transmissible(candidate):
            continue
        # Both questions about the same string. Saying it is valid and then
        # refusing to decode it is a contradiction, and it shipped once.
        cases.append("fz.isvalid.%d|isvalid|%s" % (i, candidate))
        cases.append("fz.decode.%d|decode|%s" % (i, candidate))
        origin["fz.isvalid.%d" % i] = (candidate,)
        origin["fz.decode.%d" % i] = (candidate,)

    return cases, origin


def ask(cases, classes, csharp_source):
    """Put a list of cases to all four ports; return label -> {port: answer}."""
    CASES.write_text("\n".join(cases) + "\n", encoding="utf-8")
    os.environ["GPC_FUZZ_CASES"] = str(CASES)
    try:
        results = run_all(classes, csharp_source, quiet=True)
    finally:
        os.environ.pop("GPC_FUZZ_CASES", None)

    answers = {}
    for port, text in results.items():
        parsed, _ = canonical(text)
        for label, value in parsed.items():
            answers.setdefault(label, {})[port] = value
    return answers


def broken(value):
    return value.startswith("ERR:") or value.startswith("EXC:")


def apart(east, west):
    """Degrees between two longitudes, the short way round.

    180 and -180 are the same meridian. Subtracting them gives 360, which is
    the whole world and reads as a catastrophe; a coordinate on the
    antimeridian encodes and decodes perfectly and this measured it as the
    worst failure in the run.
    """
    gap = abs(east - west) % 360.0
    return min(gap, 360.0 - gap)


def faults(answers, origin):
    """Every way a set of answers can be wrong, as (kind, label, detail)."""
    found = []

    for label, byport in sorted(answers.items()):
        if not label.startswith("fz."):
            continue                    # the fixed battery, compared elsewhere

        crashed = {p: v for p, v in byport.items() if v.startswith("EXC:")}
        if crashed:
            found.append(("crash", label, ", ".join(
                "%s %s" % (p, v) for p, v in sorted(crashed.items()))))
            continue

        if len(set(byport.values())) > 1:
            found.append(("divergence", label, ", ".join(
                "%s %s" % (p, v) for p, v in sorted(byport.items()))))

    # A port that calls a code valid must be able to decode it. This is the
    # 1.1.0 fault, and it is the one property no amount of agreement would have
    # caught: all four could have been wrong together and agreed perfectly.
    for label, byport in sorted(answers.items()):
        if not label.startswith("fz.isvalid."):
            continue
        index = label.rsplit(".", 1)[1]
        decoded = answers.get("fz.decode." + index, {})
        for port, verdict in sorted(byport.items()):
            if verdict != "true":
                continue
            answer = decoded.get(port, "")
            if broken(answer):
                found.append((
                    "contradiction", label,
                    "%s called %r valid and then %s"
                    % (port, origin[label][0], answer)))

    return found


def roundtrips(answers, origin, classes, csharp_source):
    """Decode what encode produced, and see whether the point survived."""
    cases = []
    came_from = {}

    for label, byport in sorted(answers.items()):
        if not label.startswith("fz.encode."):
            continue
        produced = next(iter(byport.values()))
        if broken(produced) or not transmissible(produced):
            continue                    # out of domain, and it said so
        back = "fz.round." + label.rsplit(".", 1)[1]
        cases.append("%s|decode|%s" % (back, produced))
        came_from[back] = (origin[label][0], origin[label][1], produced)

    if not cases:
        return [], 0.0

    second = ask(cases, classes, csharp_source)
    found = faults(second, {})
    worst = 0.0

    for label, byport in sorted(second.items()):
        if not label.startswith("fz.round."):
            continue
        value = next(iter(byport.values()))
        wanted_lat, wanted_lng, produced = came_from[label]

        if broken(value):
            found.append((
                "unreadable", label,
                "encode gave %s and decode would not read it back (%s)"
                % (produced, value)))
            continue
        try:
            latitude, longitude = (float(x) for x in value.split(","))
        except ValueError:
            continue

        north = abs(latitude - wanted_lat)
        east = apart(longitude, wanted_lng)
        # Each axis against its own cell: they are different sizes, and judging
        # longitude by the latitude bound would fail points that are fine.
        worst = max(worst, north / LAT_CELL, east / LNG_CELL)
        if north > LAT_CELL or east > LNG_CELL:
            found.append((
                "drift", label,
                "(%r, %r) -> %s -> (%r, %r), out by %.3g lat and %.3g lng"
                % (wanted_lat, wanted_lng, produced, latitude, longitude,
                   north, east)))

    return found, worst


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--cases", type=int, default=500,
                        help="coordinates and codes per round (default 500 of each)")
    parser.add_argument("--rounds", type=int, default=1,
                        help="rounds to run, each with fresh cases")
    parser.add_argument("--seed", type=int, default=None,
                        help="the seed that reproduces a past run")
    parser.add_argument("--released", action="store_true",
                        help="put the cases to the published packages instead")
    args = parser.parse_args()

    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass

    seed = args.seed if args.seed is not None else random.randrange(2 ** 31)
    print("seed %d -- pass --seed %d to run exactly this again" % (seed, seed))

    if args.released:
        pinned = {port: latest(port) for port in REGISTRIES}
        where = fetch(pinned)
        os.environ["GPC_PYTHON_PATH"] = where["python"]
        os.environ["GPC_TYPESCRIPT_MAIN"] = where["typescript"]
        classes = Path(where["java"])
        csharp_source = ["-p:Released=" + where["csharp"]]
    else:
        if not (REPO / "typescript" / "dist" / "index.js").exists():
            sys.exit("typescript/dist is missing. Run `npm run build` in typescript/ first.")
        if not (REPO / "java" / "target" / "classes").is_dir():
            sys.exit("java/target/classes is missing. Run `mvn -q compile` in java/ first.")
        classes = REPO / "java" / "target" / "classes"
        csharp_source = []

    rng = random.Random(seed)
    total = 0
    worst = 0.0

    for number in range(1, args.rounds + 1):
        cases, origin = generate(rng, args.cases)
        answers = ask(cases, classes, csharp_source)
        found = faults(answers, origin)

        more, drift = roundtrips(answers, origin, classes, csharp_source)
        found += more
        worst = max(worst, drift)
        total += len(cases)

        print("round %d of %d: %d cases, %d faults"
              % (number, args.rounds, len(cases), len(found)))

        if found:
            print()
            for kind, label, detail in found[:40]:
                print("%-14s %-18s %s" % (kind, label, detail))
            if len(found) > 40:
                print("... and %d more" % (len(found) - 40))
            print()
            print("%d faults. Reproduce this round with --seed %d." % (len(found), seed))
            print("The cases as they were put to the ports are in %s" % CASES)
            return 1

    print("%d cases put to four %s: no divergence, no crash, no contradiction."
          % (total, "published packages" if args.released else "ports"))
    print("worst round-trip drift %.2f of a cell, and a cell is the whole allowance"
          % worst)
    return 0


if __name__ == "__main__":
    sys.exit(main())
