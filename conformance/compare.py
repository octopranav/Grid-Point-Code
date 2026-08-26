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

"""Runs the four drivers and requires them to agree.

    python conformance/compare.py

The shared vectors in test_data/ pin what the four ports agree on. They cannot
pin what nobody thought to write down, and a case absent from the corpus is a
case where four implementations may quietly differ. This closes that gap from
the other side: one battery of awkward inputs, compiled against each port, and
diffed. See README.md.

Exits non-zero on any disagreement, naming the case and printing what each port
produced.
"""

import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent
BUILD = HERE / ".build"

# The one difference the four ports are allowed, and why it exists.
#
# A level outside 1 to 10 is an argument error. Java's typed error derives from
# IllegalArgumentException and Python's from ValueError, so both carry the
# GPC_LEVEL reason and stay idiomatic. C#'s derives from FormatException, which
# is the wrong parent for an argument that is out of range, so C# raises
# ArgumentOutOfRangeException and GPC_LEVEL is unreachable there. Deliberate,
# and recorded in SPEC.md section 18.1.
#
# Anything not listed here is a divergence and fails. Making C# uniform would
# mean deleting this entry, not adding to it.
SANCTIONED = {
    "EXC:ArgumentOutOfRangeException": "ERR:GPC_LEVEL",
}

# A value that is entirely numbers, or a comma-separated list of them.
NUMERIC = re.compile(r"^-?\d+(?:\.\d+)?(?:[eE][-+]?\d+)?"
                     r"(?:,-?\d+(?:\.\d+)?(?:[eE][-+]?\d+)?)*$")


def run(label, argv, cwd=None):
    """Run one driver and return its output, or exit with what went wrong.

    Every driver is asked for UTF-8 and decoded strictly. Left to itself a
    driver writes in whatever the console encoding happens to be, and a degree
    sign arriving as one byte rather than two decodes to a replacement
    character -- which then reads as a divergence between ports that agree.
    Better to fail on the encoding than to chase a phantom.
    """
    print(f"  {label:12} {Path(str(argv[0])).name} ...", flush=True)
    environment = dict(os.environ, PYTHONIOENCODING="utf-8", PYTHONUTF8="1")
    done = subprocess.run(argv, cwd=cwd or REPO, capture_output=True,
                          env=environment)
    if done.returncode != 0:
        print(f"\n{label} driver failed (exit {done.returncode}):\n")
        print(done.stdout.decode("utf-8", "replace")[-3000:])
        print(done.stderr.decode("utf-8", "replace")[-3000:])
        sys.exit(2)
    try:
        return done.stdout.decode("utf-8")
    except UnicodeDecodeError as bad:
        sys.exit(f"{label} driver did not emit UTF-8: {bad}")


def normalise_numbers(value):
    """One spelling for a numeric result, and nothing at all for anything else.

    Every language prints doubles its own way -- 0 against 0.0, 2.304e-05
    against 0.00002304 -- and none of that is a difference in behaviour. Only a
    value that is entirely numbers is rewritten. Formatted output is left as the
    port produced it: degrees, minutes and seconds carry padded fields like
    00.02, and a rewrite that helpfully turned those into 0.02 would compare
    four identical strings after destroying the thing being compared.
    """
    if not NUMERIC.match(value):
        return value

    def one(match):
        number = float(match.group(0))
        if number != int(number) or abs(number) >= 1e15:
            return repr(number)
        return str(int(number))

    return re.sub(r"-?\d+(?:\.\d+)?(?:[eE][-+]?\d+)?", one, value)


def canonical(text):
    """label -> result, in the order the driver produced them."""
    out = {}
    order = []
    for line in text.splitlines():
        line = line.strip()
        if not line or "|" not in line:
            continue
        label, value = line.split("|", 1)
        out[label] = normalise_numbers(SANCTIONED.get(value, value))
        order.append(label)
    return out, order


def main():
    # The battery carries degree signs and this may run at a console that
    # cannot print them.
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass

    if not (REPO / "typescript" / "dist" / "index.js").exists():
        sys.exit("typescript/dist is missing. Run `npm run build` in typescript/ first.")
    if not (REPO / "java" / "target" / "classes").is_dir():
        sys.exit("java/target/classes is missing. Run `mvn -q compile` in java/ first.")
    for tool in ("node", "javac", "java", "dotnet"):
        if shutil.which(tool) is None:
            sys.exit(f"{tool} is not on PATH; this harness needs all four toolchains.")

    BUILD.mkdir(exist_ok=True)
    classes = REPO / "java" / "target" / "classes"
    separator = ";" if sys.platform == "win32" else ":"

    print("running the four drivers")
    results = {}
    results["python"] = run("python", [sys.executable, HERE / "driver.py"])
    results["typescript"] = run("typescript", ["node", HERE / "driver.js"])
    run("java(javac)", ["javac", "-encoding", "UTF-8", "-cp", str(classes),
                        "-d", str(BUILD), str(HERE / "Driver.java")])
    results["java"] = run("java", ["java", "-Dfile.encoding=UTF-8",
                                   "-Dstdout.encoding=UTF-8",
                                   "-cp", separator.join([str(classes), str(BUILD)]),
                                   "Driver"])
    results["csharp"] = run("csharp", ["dotnet", "run", "--project",
                                       str(HERE / "csharp" / "driver.csproj"),
                                       "-v", "quiet", "--nologo"])

    parsed = {}
    orders = {}
    for port, text in results.items():
        parsed[port], orders[port] = canonical(text)

    ports = list(parsed)
    reference = orders[ports[0]]
    print(f"\n{len(reference)} cases x {len(ports)} ports = {len(reference) * len(ports)} results")

    failures = 0
    for port in ports[1:]:
        if orders[port] != reference:
            missing = sorted(set(reference) - set(orders[port]))
            extra = sorted(set(orders[port]) - set(reference))
            print(f"\n{port} does not run the same cases as {ports[0]}.")
            if missing:
                print(f"  missing: {missing}")
            if extra:
                print(f"  extra:   {extra}")
            print("  The four drivers have to stay in lockstep.")
            failures += 1

    for label in reference:
        values = {p: parsed[p].get(label, "<MISSING>") for p in ports}
        if len(set(values.values())) > 1:
            failures += 1
            print(f"\nDIVERGENCE  {label}")
            for port, value in values.items():
                print(f"    {port:11} {value[:150]}")

    print()
    if failures:
        print(f"{failures} divergences. The four ports do not agree.")
        return 1
    print(f"no divergence: all four ports agree on every one of the {len(reference)} cases")
    return 0


if __name__ == "__main__":
    sys.exit(main())
