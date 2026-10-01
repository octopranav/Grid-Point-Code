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

"""The site vouches for the Android app, and only for it.

    python audit/assetlinks.py

A link to gridpointcode.com opens the app without asking only once Android has
checked both ends: the app's manifest asks for its links to be verified, and the
domain answers at `/.well-known/assetlinks.json` with the app's package name and
the fingerprint of the key it is signed with. Either end wrong and nothing fails
loudly. The link opens in the browser, as though the feature had never been
built, and the device keeps that answer until the app is installed again.

The ways it goes wrong are small: a fingerprint pasted in lower case or without
its colons, a package name that drifted from the build's application ID, the
relation spelled from memory, or a manifest that never asks. Each is checked
here against the file the site publishes and the build that names the app.

An empty list is allowed and says so. It is what the site publishes until the
app has a signing key, and it vouches for nothing, so links stay as they are.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent

LINKS = REPO / "web" / "public" / ".well-known" / "assetlinks.json"
BUILD = REPO / "android" / "app" / "build.gradle.kts"
MANIFEST = REPO / "android" / "app" / "src" / "main" / "AndroidManifest.xml"

HOST = "gridpointcode.com"
RELATION = "delegate_permission/common.handle_all_urls"
FINGERPRINT = re.compile(r"(?:[0-9A-F]{2}:){31}[0-9A-F]{2}")


def application_id(build: str) -> str | None:
    """The package name the build gives the app."""
    found = re.search(r'applicationId\s*=\s*"([^"]+)"', build)
    return found.group(1) if found else None


def asks_to_verify(manifest: str, host: str = HOST) -> bool:
    """Whether an intent filter for https links to [host] asks to be verified.

    Read as text, filter by filter, so the check needs nothing installed.
    """
    for block in re.findall(r"<intent-filter\b[^>]*>.*?</intent-filter>", manifest, re.S):
        opening = block[: block.index(">") + 1]
        if 'android:autoVerify="true"' not in opening:
            continue
        if re.search(r'android:scheme="https"', block) and re.search(rf'android:host="{re.escape(host)}"', block):
            return True
    return False


def problems(links: object, package: str | None) -> list[str]:
    """What is wrong with the published statements, if anything."""
    if not isinstance(links, list):
        return ["assetlinks.json is not a list of statements"]
    found: list[str] = []
    for number, statement in enumerate(links, start=1):
        where = f"statement {number}"
        if not isinstance(statement, dict):
            found.append(f"{where} is not an object")
            continue
        if statement.get("relation") != [RELATION]:
            found.append(f"{where} grants {statement.get('relation')!r}, not [{RELATION!r}]")
        target = statement.get("target")
        if not isinstance(target, dict):
            found.append(f"{where} names no target")
            continue
        if target.get("namespace") != "android_app":
            found.append(f"{where} is for {target.get('namespace')!r}, not an Android app")
        if target.get("package_name") != package:
            found.append(f"{where} vouches for {target.get('package_name')!r}, but the build calls the app {package!r}")
        prints = target.get("sha256_cert_fingerprints")
        if not isinstance(prints, list) or not prints:
            found.append(f"{where} lists no fingerprint, so it vouches for no key")
            continue
        for value in prints:
            if not isinstance(value, str) or not FINGERPRINT.fullmatch(value):
                found.append(
                    f"{where} has {value!r}, which is not a SHA-256 fingerprint as Android "
                    "compares it: 32 pairs of upper-case hex, joined by colons"
                )
        if len(set(map(str, prints))) != len(prints):
            found.append(f"{where} lists a fingerprint twice")
    return found


def main() -> int:
    for path in (LINKS, BUILD, MANIFEST):
        if not path.exists():
            print(f"cannot find {path.relative_to(REPO).as_posix()}", file=sys.stderr)
            return 2

    try:
        links = json.loads(LINKS.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        print(f"web/public/.well-known/assetlinks.json is not JSON: {error}", file=sys.stderr)
        return 1

    package = application_id(BUILD.read_text(encoding="utf-8"))
    found = problems(links, package)
    if not asks_to_verify(MANIFEST.read_text(encoding="utf-8")):
        found.append(
            f'no intent filter for https://{HOST} in the app\'s manifest sets android:autoVerify="true", '
            "so Android never asks the site, whatever it publishes"
        )

    if found:
        for problem in found:
            print(problem, file=sys.stderr)
        return 1

    keys = sum(len(statement["target"]["sha256_cert_fingerprints"]) for statement in links)
    if keys:
        print(f"the site vouches for {package} signed with {keys} key{'' if keys == 1 else 's'}, and the app asks it to")
    else:
        print(f"the app asks to be verified; the site vouches for no key yet, so links to {HOST} stay unverified")
    return 0


if __name__ == "__main__":
    sys.exit(main())
