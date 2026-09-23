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

"""Everything the build puts in the site must reach the server.

    python audit/publishes.py

The step that packs the built site into an artifact hides files by default. It
adds `--exclude=.[^/]*` to its own tar unless it is told otherwise, so anything
whose name begins with a dot is built into `web/dist`, packed by nothing, and
never appears on the domain. Nothing turns red. The file is simply not there.

The name that makes this matter is `/.well-known/`, which is where a domain
answers questions other software asks about it, and which cannot be moved
somewhere without a dot: the location is part of the question. A file put there,
merged, and deployed green would be a 404 for every reader, and the natural
conclusion would be that the domain or the host was at fault.

So the setting is required rather than assumed, whether or not a dot file is in
the tree today. Its absence is the failure this checks for; the entries it would
drop right now are printed with it, because a list of real paths is what makes
the message believable.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent

WORKFLOW = REPO / ".github" / "workflows" / "pages.yml"
PUBLIC = REPO / "web" / "public"

ACTION = "actions/upload-pages-artifact"
SETTING = "include-hidden-files"


def packing(source: str) -> str:
    """The artifact step, from its `uses:` line to the start of the next one.

    Read as text rather than parsed as YAML so that the check needs nothing
    installed, and so it reports the step the way a reader sees it.
    """
    lines = source.splitlines()
    for start, line in enumerate(lines):
        if ACTION not in line or not line.lstrip().startswith("-"):
            continue
        indent = len(line) - len(line.lstrip())
        for end in range(start + 1, len(lines)):
            later = lines[end]
            if not later.strip():
                continue
            # Anything at the step's own indent or less has left it: the next
            # step, or the end of the job.
            if len(later) - len(later.lstrip()) <= indent:
                return "\n".join(lines[start:end])
        return "\n".join(lines[start:])
    return ""


def keeps_hidden(source: str) -> bool:
    """Whether that step is told to pack dot files as well as the rest."""
    step = packing(source)
    if not step:
        return False
    setting = re.search(rf"{SETTING}:\s*(\S+)", step)
    return bool(setting) and setting.group(1).strip("'\"").lower() == "true"


def hidden(root: Path) -> list[str]:
    """Every dot entry under a directory, as a path relative to the repository.

    A dot directory is named once, not once per file inside it: the step drops
    the whole thing, and printing four hundred tiles under one name is noise.
    """
    if not root.exists():
        return []

    found: list[str] = []
    for path in sorted(root.rglob(".*")):
        relative = path.relative_to(REPO).as_posix()
        if any(relative.startswith(parent + "/") for parent in found):
            continue
        found.append(relative)
    return found


def main() -> int:
    if not WORKFLOW.exists():
        print(f"cannot find {WORKFLOW}", file=sys.stderr)
        return 2

    source = WORKFLOW.read_text(encoding="utf-8")
    if not packing(source):
        print(f"no {ACTION} step in {WORKFLOW.name} to check", file=sys.stderr)
        return 1

    dropped = hidden(PUBLIC)

    if not keeps_hidden(source):
        for path in dropped:
            print(f"{path} is built into the site and would not be deployed", file=sys.stderr)
        print(
            f"\nThe artifact step in .github/workflows/pages.yml does not set "
            f"`{SETTING}: true`, so every path beginning with a dot is left out of "
            "the deployment while the build stays green."
            + (
                f"\n{len(dropped)} such path{'' if len(dropped) == 1 else 's'} "
                "would be lost today."
                if dropped
                else "\nNothing is lost today, and the first file put in "
                "web/public/.well-known/ would be."
            ),
            file=sys.stderr,
        )
        return 1

    if dropped:
        print(
            f"the artifact keeps hidden files, so all {len(dropped)} dot "
            f"path{'' if len(dropped) == 1 else 's'} in web/public reach the site"
        )
    else:
        print("the artifact keeps hidden files, so a dot path in web/public would reach the site")
    return 0


if __name__ == "__main__":
    sys.exit(main())
