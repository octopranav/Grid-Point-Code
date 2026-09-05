"""Run every documented Python example and check what it claims.

Documentation rots quietly. A renamed method, a changed return shape, a value
that drifted by a digit: none of it shows up in a test suite, because the
examples live in prose and nothing executes prose. This does.

Two conventions are in the documentation and both are checked:

    GPC.encode(43.65, -79.38)     # '#G3RJM-98NM9'      an expression and its value
    print(code)                   # Output: #G3RJM-98NM9   a print and its output

Comments that are prose rather than a value are left alone and counted, so the
report says how much was actually checked rather than implying it was all of it.

    python audit/examples.py
"""

from __future__ import annotations

import ast
import io
import re
import subprocess
import sys
from contextlib import redirect_stdout
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'python' / 'src'))

BLOCK = re.compile(r'^```python\s*$')
CLOSE = re.compile(r'^```\s*$')


def tracked_markdown() -> list[Path]:
    listed = subprocess.run(
        ['git', 'ls-files', '*.md'],
        cwd=ROOT, capture_output=True, text=True, check=True,
    ).stdout.split('\n')
    return [ROOT / name for name in listed if name]


def blocks(lines: list[str]) -> list[tuple[int, str]]:
    """`(first line number, source)` for every fenced Python block."""
    found: list[tuple[int, str]] = []
    start: int | None = None
    for number, line in enumerate(lines, start=1):
        if start is None:
            if BLOCK.match(line):
                start = number
        elif CLOSE.match(line):
            found.append((start + 1, '\n'.join(lines[start:number - 1])))
            start = None
    return found


def comment_on(lines: list[str], index: int) -> str | None:
    """The text of a `#` comment on this line, ignoring one inside a string."""
    if index < 0 or index >= len(lines):
        return None
    line = lines[index]
    quote: str | None = None
    for position, character in enumerate(line):
        if quote:
            if character == quote:
                quote = None
        elif character in '"\'':
            quote = character
        elif character == '#':
            return line[position + 1:].strip()
    return None


def as_literal(text: str):
    """The longest leading part of `text` that is a Python literal.

    Documented values are followed by prose often enough that taking the whole
    comment would reject most of them: `'G3RJM', a cell 8.0 by 10.7 km` is a
    value and a sentence. Returns `(value, source)` or `None` for pure prose.
    """
    for end in range(len(text), 0, -1):
        candidate = text[:end].rstrip().rstrip(',')
        if not candidate:
            continue
        try:
            return ast.literal_eval(candidate), candidate
        except (ValueError, SyntaxError):
            continue
    return None


def decimals(source: str) -> int | None:
    """How many decimal places a documented number was written to."""
    found = re.findall(r'\d+\.(\d+)', source)
    return max((len(d) for d in found), default=None)


def same(actual, expected, source: str) -> bool:
    """Whether a value matches what the documentation claims.

    Floats are compared at the precision the documentation wrote them to. The
    documented `15566716.58 metres` is a rounded figure, and demanding that the
    full double match it exactly would fail a document that is telling the
    truth.
    """
    if actual == expected:
        return True

    places = decimals(source)
    if places is None:
        return False

    def round_all(value):
        if isinstance(value, float):
            return round(value, places)
        if isinstance(value, (tuple, list)):
            return type(value)(round_all(item) for item in value)
        return value

    return round_all(actual) == round_all(expected)


def is_illustration(node: ast.stmt) -> bool:
    """Whether a statement is showing a shape rather than doing something.

    `for code in GPC.encode_stream(points): ...` names no real `points` and is
    not meant to: the body is an ellipsis, which is the documentation saying
    "your code here". Running it raises `NameError` and proves nothing about
    the API. Recognised by the elided body, not by guessing at undefined names,
    so a genuinely broken example still fails.
    """
    body = getattr(node, 'body', None)
    if not body:
        return False
    return all(
        isinstance(statement, ast.Expr)
        and isinstance(statement.value, ast.Constant)
        and statement.value.value is Ellipsis
        for statement in body
    )


def run(path: Path, problems: list[str]) -> tuple[int, int]:
    """Execute one file's examples in one namespace. Returns (checked, skipped)."""
    lines = path.read_text(encoding='utf-8').split('\n')
    namespace: dict[str, object] = {}
    checked = skipped = 0

    for first, source in blocks(lines):
        block_lines = source.split('\n')
        try:
            tree = ast.parse(source)
        except SyntaxError as error:
            problems.append(f'{path.relative_to(ROOT)}:{first}: will not parse, {error}')
            continue

        for node in tree.body:
            where = first + node.lineno - 1
            segment = ast.get_source_segment(source, node) or ''

            if is_illustration(node):
                skipped += 1
                continue

            if not isinstance(node, ast.Expr):
                try:
                    exec(compile(ast.Module([node], []), str(path), 'exec'), namespace)
                except Exception as error:
                    problems.append(
                        f'{path.relative_to(ROOT)}:{where}: `{segment.splitlines()[0]}` '
                        f'raised {type(error).__name__}: {error}'
                    )
                continue

            # The claim is on the same line as the expression's last line, or on
            # the line after it when the expression is too long to share one.
            end = (node.end_lineno or node.lineno) - 1
            claim = comment_on(block_lines, end)
            if claim is None:
                after = comment_on(block_lines, end + 1)
                stripped = block_lines[end + 1].strip() if end + 1 < len(block_lines) else ''
                claim = after if stripped.startswith('#') else None

            is_print = isinstance(node.value, ast.Call) and \
                getattr(node.value.func, 'id', None) == 'print'

            try:
                if is_print:
                    printed = io.StringIO()
                    with redirect_stdout(printed):
                        exec(compile(ast.Module([node], []), str(path), 'exec'), namespace)
                    produced = printed.getvalue().strip()
                else:
                    produced = eval(segment, namespace)
            except Exception as error:
                problems.append(
                    f'{path.relative_to(ROOT)}:{where}: `{segment.splitlines()[0]}` '
                    f'raised {type(error).__name__}: {error}'
                )
                continue

            if claim is None:
                skipped += 1
                continue

            if is_print:
                wanted = re.sub(r'^Output:\s*', '', claim).strip()
                if not wanted:
                    skipped += 1
                    continue
                checked += 1
                if produced != wanted:
                    problems.append(
                        f'{path.relative_to(ROOT)}:{where}: `{segment}` printed '
                        f'{produced!r}, documented as {wanted!r}'
                    )
                continue

            literal = as_literal(claim)
            if literal is None:
                skipped += 1          # a sentence about the value, not the value
                continue

            expected, written = literal
            checked += 1
            if not same(produced, expected, written):
                problems.append(
                    f'{path.relative_to(ROOT)}:{where}: `{segment}` returned '
                    f'{produced!r}, documented as {written}'
                )

    return checked, skipped


def main() -> int:
    problems: list[str] = []
    checked = skipped = files = 0

    for path in tracked_markdown():
        text = path.read_text(encoding='utf-8')
        if '```python' not in text:
            continue
        files += 1
        one, two = run(path, problems)
        checked += one
        skipped += two

    print(f'{files} files, {checked} claims checked, {skipped} comments were prose')

    if problems:
        print(f'\n{len(problems)} problem(s):\n', file=sys.stderr)
        for problem in problems:
            print(f'  {problem}', file=sys.stderr)
        return 1

    print('every documented example runs, and says what it does')
    return 0


if __name__ == '__main__':
    sys.exit(main())
