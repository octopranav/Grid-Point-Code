"""Check that the documentation holds together.

Every heading, every link, every table. The failures this catches are the ones
nobody sees by reading: a cross-reference to a section that was renamed, a
relative link to a file that moved, a table whose separator row lost a column,
and three dashes under a paragraph that quietly turn its last line into a
heading.

Run from anywhere:

    python audit/markdown.py

Exits non-zero on the first kind of problem it finds, listing all of them.
"""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


# ── what to read ───────────────────────────────────────────────────────────

def tracked_markdown() -> list[Path]:
    """Every markdown file the repository tracks.

    Asked of git rather than the filesystem: a `find` here walks into
    `node_modules` and turns fifteen files into two hundred and fifty.
    """
    listed = subprocess.run(
        ['git', 'ls-files', '*.md'],
        cwd=ROOT, capture_output=True, text=True, check=True,
    ).stdout.split('\n')
    return [ROOT / name for name in listed if name]


# ── headings and the anchors they make ─────────────────────────────────────

FENCE = re.compile(r'^\s{0,3}(`{3,}|~{3,})')
ATX = re.compile(r'^\s{0,3}(#{1,6})\s+(.*?)\s*#*\s*$')
SETEXT = re.compile(r'^\s{0,3}(=+|-+)\s*$')

# GitHub keeps letters, digits, spaces, hyphens and underscores, drops the rest,
# then turns the spaces into hyphens.
DROP = re.compile(r'[^\w\- ]', re.UNICODE)
INLINE_CODE = re.compile(r'`([^`]*)`')
LINK_TEXT = re.compile(r'\[([^\]]*)\]\([^)]*\)')
HTML_TAG = re.compile(r'<[^>]+>')


def anchor(text: str, seen: dict[str, int]) -> str:
    """The fragment GitHub would give this heading, duplicates included."""
    plain = INLINE_CODE.sub(r'\1', text)
    plain = LINK_TEXT.sub(r'\1', plain)
    plain = HTML_TAG.sub('', plain)
    slug = DROP.sub('', plain.strip().lower()).replace(' ', '-')

    count = seen.get(slug, 0)
    seen[slug] = count + 1
    return slug if count == 0 else f'{slug}-{count}'


def outside_fences(lines: list[str]):
    """Yield `(number, line)` for every line that is not inside a code fence.

    Fence tracking is the whole point. A `#` at the start of a line inside a
    Python block is a comment, and counting it as a heading is how a check
    reports six headings a document does not have.
    """
    fence: str | None = None
    for number, line in enumerate(lines, start=1):
        marker = FENCE.match(line)
        if fence is None:
            if marker:
                fence = marker.group(1)[0]
                continue
            yield number, line
        elif marker and marker.group(1)[0] == fence:
            fence = None


def headings(lines: list[str]) -> list[tuple[int, int, str, str]]:
    """`(line, depth, text, anchor)` for every heading, ATX or setext."""
    found: list[tuple[int, int, str, str]] = []
    seen: dict[str, int] = {}
    live = list(outside_fences(lines))

    for index, (number, line) in enumerate(live):
        atx = ATX.match(line)
        if atx:
            text = atx.group(2)
            found.append((number, len(atx.group(1)), text, anchor(text, seen)))
            continue

        # A setext heading is a run of `=` or `-` under a line of text.
        underline = SETEXT.match(line)
        if underline and index > 0:
            previous_number, previous = live[index - 1]
            if previous.strip() and previous_number == number - 1 and not ATX.match(previous):
                depth = 1 if underline.group(1)[0] == '=' else 2
                found.append((number - 1, depth, previous.strip(),
                              anchor(previous.strip(), seen)))

    return found


# ── the checks ─────────────────────────────────────────────────────────────

LINK = re.compile(r'\[[^\]]*\]\(\s*<?([^)\s>]+)>?(?:\s+"[^"]*")?\s*\)')
EXTERNAL = re.compile(r'^[a-z][a-z0-9+.-]*:', re.IGNORECASE)


def check_setext_traps(path: Path, lines: list[str], problems: list[str]) -> None:
    """Three dashes under a paragraph is a heading, not a rule.

    The underline wins, silently, and the paragraph's last line becomes an h2.
    Three paragraphs in SPEC.md were doing this before anyone noticed. A rule
    needs a blank line above it; anything else is a heading somebody meant to
    be a rule.
    """
    live = list(outside_fences(lines))
    for index, (number, line) in enumerate(live):
        if not re.match(r'^\s{0,3}-{3,}\s*$', line) or index == 0:
            continue
        previous_number, previous = live[index - 1]
        if previous_number == number - 1 and previous.strip() and not ATX.match(previous):
            problems.append(
                f'{path}:{number}: `---` directly under text is a setext heading '
                f'underline, not a horizontal rule — it turns line {number - 1} '
                f'into a heading. Put a blank line above it.'
            )


def check_tables(path: Path, lines: list[str], problems: list[str]) -> None:
    """A separator row that does not match its header loses the table.

    The row after a table's header says how many columns it has. Where the two
    disagree the block stops being a table and renders as a paragraph of pipes.
    """
    cells = lambda row: len([c for c in row.strip().strip('|').split('|')])
    separator = re.compile(r'^\s*\|?\s*:?-{1,}:?\s*(\|\s*:?-{1,}:?\s*)*\|?\s*$')

    live = list(outside_fences(lines))
    for index, (number, line) in enumerate(live):
        if index == 0 or '|' not in line or not separator.match(line):
            continue
        previous_number, header = live[index - 1]
        if previous_number != number - 1 or '|' not in header:
            continue
        if cells(header) != cells(line):
            problems.append(
                f'{path}:{number}: the separator row has {cells(line)} columns '
                f'and its header has {cells(header)} — the table will not render.'
            )


def check_links(path: Path, lines: list[str], anchors: dict[Path, set[str]],
                problems: list[str]) -> None:
    """Every relative link points at something, every fragment at a heading."""
    for number, line in outside_fences(lines):
        for target in LINK.findall(line):
            if EXTERNAL.match(target) or target.startswith('#') is False and target == '':
                continue

            if target.startswith('#'):
                fragment = target[1:]
                if fragment not in anchors[path]:
                    problems.append(
                        f'{path}:{number}: `#{fragment}` matches no heading in this file.'
                    )
                continue

            if EXTERNAL.match(target):
                continue

            file_part, _, fragment = target.partition('#')
            destination = (path.parent / file_part).resolve()
            if not destination.exists():
                problems.append(f'{path}:{number}: `{file_part}` does not exist.')
                continue

            if fragment and destination.suffix == '.md':
                if destination not in anchors:
                    anchors[destination] = {
                        slug for _, _, _, slug in
                        headings(destination.read_text(encoding='utf-8').split('\n'))
                    }
                if fragment not in anchors[destination]:
                    problems.append(
                        f'{path}:{number}: `{file_part}#{fragment}` — '
                        f'that file has no such heading.'
                    )


def main() -> int:
    files = tracked_markdown()
    if not files:
        print('no tracked markdown found', file=sys.stderr)
        return 2

    anchors: dict[Path, set[str]] = {}
    contents: dict[Path, list[str]] = {}
    for path in files:
        lines = path.read_text(encoding='utf-8').split('\n')
        contents[path] = lines
        anchors[path] = {slug for _, _, _, slug in headings(lines)}

    problems: list[str] = []
    for path in files:
        lines = contents[path]
        check_setext_traps(path.relative_to(ROOT), lines, problems)
        check_tables(path.relative_to(ROOT), lines, problems)

    # Links are checked against the anchor map, which is keyed by real paths.
    for path in files:
        check_links(path, contents[path], anchors, problems)

    total_headings = sum(len(headings(contents[path])) for path in files)
    print(f'{len(files)} files, {total_headings} headings, '
          f'{sum(len(a) for a in anchors.values())} anchors')

    if problems:
        print(f'\n{len(problems)} problem(s):\n', file=sys.stderr)
        for problem in problems:
            print(f'  {problem}', file=sys.stderr)
        return 1

    print('every link resolves, every table holds, no stray underlines')
    return 0


if __name__ == '__main__':
    sys.exit(main())
