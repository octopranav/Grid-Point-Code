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

"""The typefaces the Android app bundles are the designers' own files, unchanged.

    python audit/fonts.py

Bitter and IBM Plex are under the SIL Open Font Licence, and both reserve their
names: a modified version, a subset included, may not be called Bitter Pro or
Plex without the copyright holder's permission. The app ships them under those
names, so it ships them unmodified, byte for byte the files published in the
google/fonts repository. A font opened and saved by a tool, subset to save a
few kilobytes, or swapped for a newer build would still render, and nothing
would turn red. This is what turns it red.

Each file is pinned by the hash git gives the upstream file, which is the one
the GitHub contents API reports for it, so checking a pin against its source
needs nothing but that API. Each licence travels with the fonts it covers, as
the licence requires, and must still name the reserved font name.
"""

from __future__ import annotations

import hashlib
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent

FONTS = REPO / "android" / "designsystem" / "src" / "main" / "res" / "font"
LICENCES = REPO / "android" / "designsystem" / "src" / "main" / "assets" / "licences"

# Bundled file -> (its path in github.com/google/fonts, git's hash of that file).
PINNED: dict[str, tuple[str, str]] = {
    "bitter.ttf": ("ofl/bitter/Bitter[wght].ttf", "7f6d58e633e22ab99597e0c0c17bd21c11630d99"),
    "ibm_plex_sans.ttf": ("ofl/ibmplexsans/IBMPlexSans[wdth,wght].ttf", "30f837e8b02e8ecddce6cbebc425b1f422392515"),
    "ibm_plex_mono_regular.ttf": ("ofl/ibmplexmono/IBMPlexMono-Regular.ttf", "0c9770d5183ba60dc4350d3e011b782a320761ae"),
    "ibm_plex_mono_medium.ttf": ("ofl/ibmplexmono/IBMPlexMono-Medium.ttf", "33c546f68b6007a45a3f10e845523abb2db25399"),
    "ibm_plex_mono_semibold.ttf": ("ofl/ibmplexmono/IBMPlexMono-SemiBold.ttf", "8f8998a139d8f61d3b96bdd3b512fca501a1f6b9"),
}

# Licence file -> the reserved font name it must carry, and the fonts it covers.
COVERS: dict[str, tuple[str, tuple[str, ...]]] = {
    "bitter-OFL.txt": ('Reserved Font Name "Bitter Pro"', ("bitter.ttf",)),
    "ibm-plex-OFL.txt": (
        'Reserved Font Name "Plex"',
        ("ibm_plex_sans.ttf", "ibm_plex_mono_regular.ttf", "ibm_plex_mono_medium.ttf", "ibm_plex_mono_semibold.ttf"),
    ),
}


def blob(data: bytes) -> str:
    """The hash git gives a file with these contents."""
    return hashlib.sha1(b"blob %d\0" % len(data) + data).hexdigest()


def shown(path: Path) -> str:
    """A path as a reader of the repository would write it."""
    try:
        return path.relative_to(REPO).as_posix()
    except ValueError:
        return path.as_posix()


def problems(fonts: Path, licences: Path) -> list[str]:
    """Everything wrong with the bundled typefaces, one line each; empty when nothing is."""
    found: list[str] = []
    present = {path.name for path in fonts.iterdir() if path.is_file()} if fonts.exists() else set()

    for name, (upstream, pinned) in PINNED.items():
        if name not in present:
            found.append(f"{name} is missing from {shown(fonts)}")
            continue
        if blob((fonts / name).read_bytes()) != pinned:
            found.append(
                f"{name} is not the unmodified {upstream} from google/fonts, "
                "and under its reserved font name it has to be"
            )

    for name in sorted(present - PINNED.keys()):
        found.append(f"{name} is bundled but pinned to no upstream file")

    for name, (reserved, covered) in COVERS.items():
        path = licences / name
        if not path.exists():
            found.append(f"{name} is missing, so {', '.join(covered)} would ship without its licence")
        elif reserved not in path.read_text(encoding="utf-8"):
            found.append(f"{name} no longer names its {reserved}")

    return found


def main() -> int:
    found = problems(FONTS, LICENCES)
    for line in found:
        print(line, file=sys.stderr)
    if found:
        return 1
    print(f"{len(PINNED)} typefaces are the designers' own files, each with its licence")
    return 0


if __name__ == "__main__":
    sys.exit(main())
