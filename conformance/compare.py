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
    python conformance/compare.py --released [--pin python=2.0.0 ...]

The shared vectors in test_data/ pin what the four ports agree on. They cannot
pin what nobody thought to write down, and a case absent from the corpus is a
case where four implementations may quietly differ. This closes that gap from
the other side: one battery of awkward inputs, compiled against each port, and
diffed. See README.md.

With --released the same battery is put to the four *published* packages --
what npm, PyPI, Maven Central and NuGet serve -- instead of the source in this
repository. The default mode proves the four implementations agree; this proves
that what people actually install does, which is a different claim and the one
that reaches anybody. It covers the packaging and publishing step: a build that
ships the wrong files, a release cut from the wrong commit, a package that
resolves to a different version than intended.

Latest published version of each, unless --pin says otherwise. Testing what is
published means asking what is published, so a release that breaks agreement
should turn this red without anybody editing a pin first.

Exits non-zero on any disagreement, naming the case and printing what each port
produced.
"""

import json
import os
import re
import shutil
import subprocess
import sys
import urllib.request
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


# Where each port is published, and how to ask that registry what the newest
# version is. Names rather than URLs in the failure messages: the point of the
# released run is to say which artifact was tested.
REGISTRIES = {
    "python": ("PyPI", "gridpointcode-algo-pranavpatel-ca"),
    "typescript": ("npm", "@pranavpatel.ca/algo-gridpointcode"),
    "java": ("Maven Central", "ca.pranavpatel.algo:gridpointcode"),
    "csharp": ("NuGet", "Ca.Pranavpatel.Algo.GridPointCode"),
}

RELEASED = HERE / ".released"


def ask(url, parse):
    try:
        with urllib.request.urlopen(url, timeout=30) as answer:
            return parse(answer.read().decode("utf-8"))
    except Exception as bad:                                  # noqa: BLE001
        sys.exit(f"could not ask {url}: {bad}")


def latest(port):
    """What the registry says is newest, asked of the registry."""
    name = REGISTRIES[port][1]
    if port == "python":
        return ask(f"https://pypi.org/pypi/{name}/json",
                   lambda body: json.loads(body)["info"]["version"])
    if port == "typescript":
        quoted = name.replace("/", "%2F")
        return ask(f"https://registry.npmjs.org/{quoted}/latest",
                   lambda body: json.loads(body)["version"])
    if port == "java":
        group, artifact = name.split(":")
        path = group.replace(".", "/")
        return ask(
            f"https://repo1.maven.org/maven2/{path}/{artifact}/maven-metadata.xml",
            lambda body: re.search(r"<release>([^<]+)</release>", body).group(1),
        )
    return ask(
        f"https://api.nuget.org/v3-flatcontainer/{name.lower()}/index.json",
        lambda body: json.loads(body)["versions"][-1],
    )


def maven_environment():
    """Java carries its own trust store rather than the operating system's.

    On Windows that can mean Maven Central is unreachable here while curl, npm
    and pip all reach it -- a PKIX failure that looks like the artifact is
    missing. Pointing Java at the OS store fixes it and changes nothing where
    it was already working.
    """
    environment = dict(os.environ)
    if sys.platform == "win32":
        options = environment.get("MAVEN_OPTS", "")
        environment["MAVEN_OPTS"] = (
            options + " -Djavax.net.ssl.trustStoreType=Windows-ROOT").strip()
    return environment


def fetch(versions):
    """Install the four published packages and say how to reach each.

    Nothing here is built from the tree. That is the entire point: if this
    resolved anything locally it would be answering the question the default
    mode already answers.
    """
    shutil.rmtree(RELEASED, ignore_errors=True)
    RELEASED.mkdir(parents=True)

    print("fetching the published packages")
    for port, version in versions.items():
        print(f"  {REGISTRIES[port][0]:14} {REGISTRIES[port][1]} {version}")

    where = {}

    into = RELEASED / "python"
    done = subprocess.run(
        [sys.executable, "-m", "pip", "install", "--quiet", "--target", str(into),
         f"{REGISTRIES['python'][1]}=={versions['python']}"],
        capture_output=True,
    )
    if done.returncode != 0:
        sys.exit("pip could not install the wheel: "
                 + done.stderr.decode("utf-8", "replace"))
    where["python"] = str(into)

    into = RELEASED / "typescript"
    into.mkdir()
    done = subprocess.run(
        ["npm", "install", "--silent", "--no-audit", "--no-fund",
         "--prefix", str(into),
         f"{REGISTRIES['typescript'][1]}@{versions['typescript']}"],
        capture_output=True, shell=(sys.platform == "win32"),
    )
    if done.returncode != 0:
        sys.exit("npm could not install the package: "
                 + done.stderr.decode("utf-8", "replace"))
    # Asked of node rather than guessed: the package says in its own manifest
    # which file is its entry point, and that is not this harness's business.
    found = subprocess.run(
        ["node", "-p",
         "require.resolve(process.argv[1], {paths: [process.argv[2]]})",
         REGISTRIES["typescript"][1], str(into)],
        capture_output=True, shell=(sys.platform == "win32"),
    )
    if found.returncode != 0:
        sys.exit("could not resolve the installed package: "
                 + found.stderr.decode("utf-8", "replace"))
    where["typescript"] = found.stdout.decode("utf-8").strip()

    into = RELEASED / "java"
    group, artifact = REGISTRIES["java"][1].split(":")
    done = subprocess.run(
        ["mvn", "-B", "-q", "dependency:copy",
         f"-Dartifact={group}:{artifact}:{versions['java']}",
         f"-DoutputDirectory={into}"],
        capture_output=True, shell=(sys.platform == "win32"),
        env=maven_environment(),
    )
    if done.returncode != 0:
        sys.exit("maven could not fetch the jar: "
                 + done.stdout.decode("utf-8", "replace")[-3000:]
                 + done.stderr.decode("utf-8", "replace")[-2000:])
    jars = list(into.glob("*.jar"))
    if not jars:
        sys.exit(f"maven reported success but left no jar in {into}")
    where["java"] = str(jars[0])

    # C# needs no fetching of its own: the driver takes the version and lets
    # restore do it.
    where["csharp"] = versions["csharp"]
    return where


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

    released = "--released" in sys.argv

    for tool in ("node", "javac", "java", "dotnet"):
        if shutil.which(tool) is None:
            sys.exit(f"{tool} is not on PATH; this harness needs all four toolchains.")

    if released:
        if shutil.which("npm") is None or shutil.which("mvn") is None:
            sys.exit("--released needs npm and mvn to fetch what the registries serve.")

        # Newest published, unless told otherwise. A pin is for reproducing a
        # past run; the standing question is whether what people can install
        # today agrees.
        pinned = {}
        for argument in sys.argv[sys.argv.index("--released") + 1:]:
            if "=" not in argument:
                continue
            port, _, version = argument.partition("=")
            port = port.lstrip("-")
            if port not in REGISTRIES:
                sys.exit(f"--pin {argument}: no port called {port!r}. "
                         f"One of {', '.join(REGISTRIES)}.")
            pinned[port] = version
        for port in REGISTRIES:
            if port in pinned:
                continue
            pinned[port] = latest(port)

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

    BUILD.mkdir(exist_ok=True)
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
                                       "-v", "quiet", "--nologo"] + csharp_source)

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
    subject = "published packages" if released else "ports"
    print(f"no divergence: all four {subject} agree on every one of "
          f"the {len(reference)} cases")
    return 0


if __name__ == "__main__":
    sys.exit(main())
