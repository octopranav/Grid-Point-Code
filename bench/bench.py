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

"""Throughput, in all four ports, on the same work.

    python bench/bench.py                 measure and tabulate
    python bench/bench.py --check         and fail if a port has got far slower
    python bench/bench.py --update        rewrite the baseline from this run
    python bench/bench.py --only java,python

The site's benchmark page measures the library in a visitor's browser, which is
one language on one machine and says nothing about the other three. This runs
the same five operations through every port and puts the numbers beside each
other.

**Why there is a calibration task.** A shared runner is a bad clock. The same
job on the same commit can come back a factor of two apart depending on what
else is on the machine, so an absolute figure cannot be compared between runs
and a threshold on one would fire at random. Every leg therefore also times a
fixed arithmetic loop the library has nothing to do with, and every figure is
reported as a multiple of it. A slow morning makes both slow and the ratio
stands still.

That ratio is not comparable *between* languages -- 64 interpreted operations in
Python cost a hundred times what they cost in Java -- and this does not pretend
otherwise. It is comparable between runs of the same language, which is the only
comparison a regression check needs.

**What the check is for.** The band is deliberately wide. A benchmark that fails
on a fifteen percent drift would fail on a busy afternoon instead, and a check
that cries wolf gets switched off. This one catches the kind of regression that
matters and would otherwise ship unnoticed: a regular expression compiled inside
a loop, a table rebuilt per call, an accidental linear scan. Gradual drift is
what the table is for -- it is printed on every run, and read by a person.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent
BUILD = HERE / ".build"
BASELINE = HERE / "baseline.json"

PORTS = ("python", "typescript", "java", "csharp")

# In the order the drivers print them. `calibrate` is last and is not a result:
# it is the unit the others are quoted in.
TASKS = ("encode", "decode", "gridToCode", "codeToGrid", "normalise")
UNIT = "calibrate"

WHAT = {
    "encode": "a coordinate to a formatted code",
    "decode": "a code back to a coordinate",
    "gridToCode": "the integer core, no floating point",
    "codeToGrid": "the same, inverted",
    "normalise": "case-folding, separators, the alias table",
}


def run(argv, cwd=None):
    """A driver, or a step that prepares one. Its output, or a loud failure."""
    done = subprocess.run(
        argv, cwd=cwd or REPO, capture_output=True, text=True,
        encoding="utf-8", errors="replace",
    )
    if done.returncode != 0:
        print(f"\n{argv[0]} failed:\n{done.stdout}\n{done.stderr}", file=sys.stderr)
        raise SystemExit(1)
    return done.stdout


def parse(output, port):
    """`task|nanos|per-second|batch|guard` lines into a dictionary."""
    found = {}
    for line in output.splitlines():
        parts = line.strip().split("|")
        if len(parts) != 5:
            continue
        found[parts[0]] = {
            "nanos": float(parts[1]),
            "per_second": float(parts[2]),
            "batch": int(parts[3]),
        }

    missing = [task for task in (*TASKS, UNIT) if task not in found]
    if missing:
        raise SystemExit(f"{port} did not report {', '.join(missing)}")
    return found


def measure(ports):
    """Every requested leg, built if it needs building, then run."""
    results = {}

    if "python" in ports:
        print("  python", flush=True)
        results["python"] = parse(run([sys.executable, HERE / "driver.py"]), "python")

    if "typescript" in ports:
        print("  typescript", flush=True)
        # The driver reads the built package, not the source.
        if not (REPO / "typescript" / "dist" / "index.js").exists():
            run(["npm", "run", "build"], cwd=REPO / "typescript")
        results["typescript"] = parse(run(["node", HERE / "driver.js"]), "typescript")

    if "java" in ports:
        print("  java", flush=True)
        classes = REPO / "java" / "target" / "classes"
        if not classes.exists():
            run(["mvn", "-B", "-ntp", "-q", "compile"], cwd=REPO / "java")
        BUILD.mkdir(exist_ok=True)
        run(["javac", "-encoding", "UTF-8", "-cp", str(classes),
             "-d", str(BUILD), str(HERE / "Driver.java")])
        separator = ";" if sys.platform == "win32" else ":"
        results["java"] = parse(run([
            "java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
            "-cp", separator.join([str(classes), str(BUILD)]), "Driver",
        ]), "java")

    if "csharp" in ports:
        print("  csharp", flush=True)
        # Release, or the figure is for a build nobody ships.
        results["csharp"] = parse(run([
            "dotnet", "run", "--project", str(HERE / "csharp" / "driver.csproj"),
            "-c", "Release", "-v", "quiet", "--nologo",
        ]), "csharp")

    return results


def relative(results):
    """Each task as a multiple of that port's own calibration loop."""
    return {
        port: {
            task: found[task]["nanos"] / found[UNIT]["nanos"]
            for task in TASKS
        }
        for port, found in results.items()
    }


def thousands(value):
    """Operations a second, at a width a person can compare down a column."""
    if value >= 1e6:
        return f"{value / 1e6:,.2f} M"
    if value >= 1e3:
        return f"{value / 1e3:,.1f} k"
    return f"{value:,.0f}"


def table(results, costs, ports):
    lines = []
    width = max(len(task) for task in TASKS) + 2

    head = "task".ljust(width) + "".join(port.rjust(14) for port in ports)
    lines.append(head)
    lines.append("-" * len(head))

    for task in TASKS:
        row = task.ljust(width)
        for port in ports:
            row += thousands(results[port][task]["per_second"]).rjust(14)
        lines.append(row)

    lines.append("")
    lines.append("operations a second, median batch. Absolute figures are for this")
    lines.append("machine at this moment and are not a comparison between languages.")
    lines.append("")

    head = "cost".ljust(width) + "".join(port.rjust(14) for port in ports)
    lines.append(head)
    lines.append("-" * len(head))
    for task in TASKS:
        row = task.ljust(width)
        for port in ports:
            row += f"{costs[port][task]:.2f}".rjust(14)
        lines.append(row)

    lines.append("")
    lines.append("the same work as a multiple of each port's own calibration loop.")
    lines.append("This is the number the check reads: comparable between runs of one")
    lines.append("language, meaningless between languages.")

    return "\n".join(lines)


def summary(results, costs, ports, band, breaches):
    """The same thing as markdown, for the run summary in Actions."""
    lines = ["## Throughput", "",
             "| task | what it does | " + " | ".join(ports) + " |",
             "| --- | --- | " + " | ".join("---:" for _ in ports) + " |"]
    for task in TASKS:
        cells = " | ".join(thousands(results[port][task]["per_second"]) for port in ports)
        lines.append(f"| `{task}` | {WHAT[task]} | {cells} |")

    lines += ["", "Operations a second, median batch, on a shared runner. Compare a",
              "column with itself over time, not with the column beside it.", "",
              "### Cost against the baseline", "",
              "| task | " + " | ".join(ports) + " |",
              "| --- | " + " | ".join("---:" for _ in ports) + " |"]

    held = load()
    for task in TASKS:
        cells = []
        for port in ports:
            now = costs[port][task]
            was = (held.get(port) or {}).get(task)
            if was:
                drift = now / was
                over = " :warning:" if drift > band else ""
                cells.append(f"{now:.2f} ({drift:.2f}× of {was:.2f}){over}")
            else:
                cells.append(f"{now:.2f}")
        lines.append(f"| `{task}` | " + " | ".join(cells) + " |")

    lines += ["", f"Each operation as a multiple of that port's own calibration loop, "
                  f"and how that compares with the committed baseline. The check "
                  f"fires above {band:g}×."]

    if breaches:
        lines += ["", "**Outside the band:**", ""]
        lines += [f"- {one}" for one in breaches]

    return "\n".join(lines) + "\n"


def load():
    if not BASELINE.exists():
        return {}
    return json.loads(BASELINE.read_text(encoding="utf-8")).get("cost", {})


def check(costs, ports, band):
    """Every task against what it cost when the baseline was taken."""
    held = load()
    if not held:
        return None                 # not "nothing wrong": nothing to compare

    breaches = []
    for port in ports:
        for task in TASKS:
            was = (held.get(port) or {}).get(task)
            if not was:
                continue
            now = costs[port][task]
            drift = now / was
            if drift > band:
                breaches.append(
                    f"{port} {task} costs {now:.2f} calibration units, "
                    f"was {was:.2f} — {drift:.1f}× slower"
                )
    return breaches


def store(costs):
    BASELINE.write_text(
        json.dumps({
            "note": "Cost of each operation as a multiple of that port's own "
                    "calibration loop. Taken on a CI runner, not a workstation: "
                    "the ratio is stable across machines but not perfectly, and "
                    "the check runs there. Rewrite with bench.py --update.",
            "cost": {port: {task: round(costs[port][task], 4) for task in TASKS}
                     for port in sorted(costs)},
        }, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"\nbaseline written to {BASELINE.relative_to(REPO)}")


def main():
    argv = sys.argv[1:]

    def option(name, fallback=None):
        if name not in argv:
            return fallback
        at = argv.index(name)
        return argv[at + 1] if at + 1 < len(argv) else fallback

    only = option("--only")
    ports = tuple(p for p in PORTS if not only or p in only.split(","))
    if not ports:
        raise SystemExit(f"--only must name some of: {', '.join(PORTS)}")

    band = float(option("--band", "2.0"))

    print(f"measuring {len(ports)} port{'' if len(ports) == 1 else 's'}")
    results = measure(ports)
    costs = relative(results)

    print()
    print(table(results, costs, ports))

    breaches = []
    if "--check" in argv:
        breaches = check(costs, ports, band)
        print()
        if breaches is None:
            print("no baseline to compare against; --update writes one")
            breaches = []
        elif breaches:
            for one in breaches:
                print(f"  {one}", file=sys.stderr)
            print(
                f"\n{len(breaches)} operation{'' if len(breaches) == 1 else 's'} "
                f"now cost{'s' if len(breaches) == 1 else ''} more than {band:g}× "
                "what the baseline records.\nThat is past what a shared runner "
                "explains. Look for work that moved inside a loop.",
                file=sys.stderr,
            )
        else:
            print(f"every operation is within {band:g}× of its baseline")

    where = os.environ.get("GITHUB_STEP_SUMMARY")
    if where:
        with open(where, "a", encoding="utf-8") as handle:
            handle.write(summary(results, costs, ports, band, breaches))

    if "--update" in argv:
        store(costs)

    return 1 if breaches else 0


if __name__ == "__main__":
    sys.exit(main())
