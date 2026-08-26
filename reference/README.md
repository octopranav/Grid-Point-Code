# Reference implementation

The executable companion to [`SPEC.md`](../SPEC.md). Every rule in that document
is implemented here once, plainly, so that its claims can be checked and its
figures reproduced.

**This is not a fifth port.** It is not published to any registry, it is not
part of any package, and nothing in `csharp/`, `java/`, `python/` or
`typescript/` imports it. It is a tool for developing and maintaining the
specification. The four ports are held to the conformance vectors in
[`test_data/`](../test_data/), not to this code.

Python 3.9 or later. No dependencies.

## Files

| File | What it is |
| --- | --- |
| `gpc2.py` | The whole of version 2: encode, decode, area, parsing, classification, short form, integer form, the GF(25) check character, typo correction, the spatial operations, and the two coordinate conversions |
| `geodesy.py` | Spherical helpers used only by the harness, and the second opinion `distance` is held against |
| `from_spec.py` | A second opinion, transcribed from Appendix A of the specification and nothing else |
| `verify.py` | Checks every exact claim the specification makes |
| `measure.py` | Reproduces every table of measured figures in the specification |

## Running it

```bash
python reference/verify.py
```

Checks the constants, the worked examples, the structural properties, the
round-trips, the containment and ordering guarantees, the spatial operations
and the coordinate conversions, then exits non-zero on the first failure. Takes
about a minute.

```bash
python reference/measure.py
python reference/measure.py locality typos
```

Prints the measured tables laid out to be read beside the document. Sections are
`cells`, `locality`, `ordering`, `clustering`, `typos`, `corrections` and
`seams`. Sampling is seeded, so a figure that moves means behaviour changed
rather than that the dice fell differently. The full run takes several minutes,
mostly in `clustering`.

If a figure in `SPEC.md` no longer matches what `measure.py` prints, one of the
two is wrong. That is the reason both are kept.

## About `from_spec.py`

It exists to answer one question: does the specification stand on its own?

It is a line-by-line transcription of the pseudocode in Appendix A, written
without consulting `gpc2.py`. `verify.py` runs the two against each other over
200,000 coordinates and every edge case. Keep it that way:

* Do not refactor it to share code with `gpc2.py`.
* Do not fix it by reading `gpc2.py`.
* If the two disagree, correct the specification, then re-transcribe.

The moment it becomes a wrapper around `gpc2.py` it stops being evidence of
anything.

## For a new port

Work from `SPEC.md`. This directory is for checking your reading of it, not for
translating. A port written by translating this code inherits whatever this code
gets wrong, and the whole point of the specification is that it should not need
to exist for a port to be written.

## What is deliberately absent

The advisory word list of specification section 17. `gpc2.py` implements the
mechanism -- the expansion table, the hash, the matching -- and `screen` takes
the entries it is to match against, so nothing in this directory carries a
word. The list lives in [`screening/`](../screening/), in an encrypted archive
so that the words are not plaintext in the repository, and is expanded into the
four ports by `screening/expand.py`.
