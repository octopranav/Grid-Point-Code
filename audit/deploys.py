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

"""Everything the site renders must be able to trigger a deployment.

    python audit/deploys.py

The site reads two things from outside `web/`: `/spec` renders `SPEC.md`, and
`/docs` renders each port's own README. That is deliberate -- a page generated
from the source of truth cannot fork from it -- but it has a consequence that is
easy to miss. The Pages workflow only runs on a list of paths, and for a long
time that list was `web/` and `design/`. So the specification could be amended,
reviewed, merged, and never reach the page that publishes it: every check green,
the site quietly a version behind.

It was found the slow way, by editing `SPEC.md` and waiting for a deployment
that was never coming. This is the check that would have found it instead.

It reads the globs out of the content configuration rather than being told what
they are, so a collection added later is covered without anybody remembering to
come back here.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent

CONFIG = REPO / "web" / "src" / "content.config.ts"
WORKFLOW = REPO / ".github" / "workflows" / "pages.yml"


def collections(source: str) -> list[str]:
    """Every path a `glob({ pattern, base })` loader reads, relative to the repo.

    Only loaders reaching outside `web/` matter: anything under it is already
    covered by the `web/**` trigger, and listing those again would be noise
    that goes stale.
    """
    found: list[str] = []
    for pattern, base in re.findall(
        r"glob\(\{\s*pattern:\s*'([^']+)'\s*,\s*base:\s*'([^']+)'", source
    ):
        if not base.startswith(".."):
            continue                       # inside web/, already triggered

        # `{a,b}/README.md` names one file per alternative.
        braces = re.search(r"\{([^}]+)\}", pattern)
        if braces:
            for one in braces.group(1).split(","):
                found.append(pattern.replace(braces.group(0), one.strip()))
        else:
            found.append(pattern)
    return found


def triggers(source: str) -> list[str]:
    """The `paths:` list of the push trigger, as written."""
    block = re.search(r"\n    paths:\n((?:\s*-\s*'[^']+'\n)+)", source)
    if not block:
        return []
    return re.findall(r"-\s*'([^']+)'", block.group(1))


def covered(path: str, patterns: list[str]) -> bool:
    """Whether any trigger pattern would fire for a change to `path`."""
    for pattern in patterns:
        if pattern == path:
            return True
        if pattern.endswith("/**") and path.startswith(pattern[:-3] + "/"):
            return True
        if pattern.endswith("/*") and path.startswith(pattern[:-2] + "/"):
            return True
    return False


def main() -> int:
    if not CONFIG.exists() or not WORKFLOW.exists():
        print(f"cannot find {CONFIG} or {WORKFLOW}", file=sys.stderr)
        return 2

    wanted = collections(CONFIG.read_text(encoding="utf-8"))
    have = triggers(WORKFLOW.read_text(encoding="utf-8"))

    if not wanted:
        print("no collection reads outside web/; nothing to check")
        return 0
    if not have:
        print("the Pages workflow has no push paths to check", file=sys.stderr)
        return 1

    missing = [path for path in wanted if not covered(path, have)]
    for path in missing:
        print(
            f"{path} is rendered by the site but would not trigger a deployment",
            file=sys.stderr,
        )

    if missing:
        print(
            f"\n{len(missing)} source"
            f"{'' if len(missing) == 1 else 's'} the site renders cannot deploy it."
            "\nAdd them to the `paths:` list in .github/workflows/pages.yml.",
            file=sys.stderr,
        )
        return 1

    print(
        f"all {len(wanted)} source{'' if len(wanted) == 1 else 's'} "
        "the site renders from outside web/ can trigger a deployment"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
