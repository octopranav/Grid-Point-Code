# Audit

Checks that keep the documentation honest. Nothing here ships; all of it runs in
CI, and it exists because these are the failures reading cannot catch.

| Script | What it will not let past |
| --- | --- |
| [`markdown.py`](markdown.py) | A cross-reference to a heading that was renamed, a relative link to a file that moved, a table whose separator row lost a column, and three dashes under a paragraph |
| [`examples.py`](examples.py) | A documented example that no longer runs, or that claims a value the API does not return |
| [`test_markdown.py`](test_markdown.py) | Either of the above quietly passing everything |

```
python audit/markdown.py
python audit/examples.py
python -m unittest discover --start-directory audit --top-level-directory audit
```

## What `markdown.py` reads

Every markdown file **git tracks**, fifteen of them. Asked of git rather than
found on disk, because a `find` walks into `node_modules` and turns fifteen
files into two hundred and fifty.

Headings are collected outside code fences, which is the whole difficulty: a `#`
at the start of a line inside a Python block is a comment, and counting it as a
heading reports six headings a document does not have. Setext headings count
too: a line of text with `===` or `---` under it is a heading whether or not
anybody meant one.

Anchors are computed the way GitHub computes them: lowercase, drop everything
that is not a letter, digit, space, hyphen or underscore, turn spaces into
hyphens, and number duplicates from `-1`. **Verified against ground truth**
rather than trusted. The site renders `SPEC.md` through Astro's github-slugger,
and all seventy anchors come out identical, in the same order.

### The three dashes

```markdown
Some paragraph that ends here.
---
```

That is not a horizontal rule. It is a setext underline, it wins silently, and
it turns the paragraph's last line into a level-two heading. Three paragraphs in
`SPEC.md` were doing exactly this before the Phase 5 audit found them. A rule
needs a blank line above it.

## What `examples.py` runs

Every fenced `python` block in tracked markdown, one namespace per file so a
later block can use what an earlier one imported. Two conventions are in the
documentation and both are checked:

```python
from gridpointcode_algo_pranavpatel_ca import GPC

GPC.encode(43.65, -79.38)           # '#G3RJM-98NM9'
print(GPC.encode(43.65, -79.38))    # Output: #G3RJM-98NM9
```

The first line is an expression and the value it returns; the second is a call
and the output it prints. That block is real, and `examples.py` checks it along
with everything else. A page about verifying documentation should not be the
one place carrying an example nobody runs.

**An `Output:` comment is taken literally**, all of it, because standard output
is arbitrary text and there is no way to tell a trailing remark from the last
word printed. Annotate the prose around such a line, never on it. A comment
holding a *value* may carry a remark after it, since a value has a shape and the
remark does not fit it.

Three things it is deliberately careful about.

**Prose is not a claim.** `# the eight cells around it` describes a value rather
than being one. The longest leading part of a comment that parses as a Python
literal is the claim, and a comment with none is counted as prose, so the report
says how many, so the number checked is never mistaken for the number written.

**Rounded figures are honest.** The documented `15566716.58 metres` is a float
rounded for a reader. Floats are compared at the precision the documentation
wrote them to, so a true document is not failed for being readable.

**An ellipsis means "your code here".** `for code in GPC.encode_stream(points):
...` names no real `points` and is not meant to. A statement whose body is only
`...` is an illustration and is not run, recognised by the elided body and not by
guessing at undefined names, so a genuinely broken example still fails.

## Still to build

The Phase 5 audit also ran a **coverage matrix**: enumerate the API, map
`snake_case` to `camelCase` and `PascalCase`, and check every operation appears
in all five READMEs. That is what found the locality API documented sixteen
operations out of thirty-six. It is not rebuilt here yet.
