# The advisory list

`screen(code)` reports substrings of a code that spell something unwanted.
[Section 17 of SPEC.md](../SPEC.md#17-advisory-screening-non-normative) defines
it. It advises and never blocks: nothing in the library refuses to encode,
decode or validate because of what it found.

This directory turns a list of words into the form the four ports carry.

## The word file is not here

`screening/words.txt` is the input, and it is deliberately absent from this
repository and ignored by git. What is committed is the output: hashes.

That keeps the words out of source control, out of search results and out of
four published packages. It is not a security measure and the specification
says so — the variants are short strings over an alphabet of twenty-five
symbols, and a space that small can be searched exhaustively by anyone who
cares to. That is also why the hash is a cheap mixer rather than a
cryptographic one: it would buy nothing here, and it would cost three of the
four ports an import they otherwise do not need.

Its format is one word per line, lower case, `#` for a comment, blank lines
ignored:

```
# unwanted words, one per line
gnat
cattle
```

Words shorter than four letters are dropped, because a three-symbol run turns
up by chance often enough that warning about it would mean nothing. So are
words containing `q`, `u`, `v` or `y`, which have no representation in the
alphabet and therefore cannot appear in a code at all.

## Running it

```bash
python screening/expand.py
```

Every word expands to each way it could be spelled in a code — `o` as `0`, `t`
as `T` or `7`, and so on down the table in section 17.2 — and each variant is
stored as its 32-bit FNV-1a hash, eight lower-case hexadecimal characters. The
script writes one generated file per port:

| Port | File |
| --- | --- |
| Python | `python/src/gridpointcode_algo_pranavpatel_ca/screen_list.py` |
| TypeScript | `typescript/src/ScreenList.ts` |
| C# | `csharp/gpc/ScreenList.cs` |
| Java | `java/src/main/java/ca/pranavpatel/algo/gridpointcode/ScreenList.java` |

Do not edit those by hand. Then regenerate the vectors, because the screening
ones are built from the list and will have moved:

```bash
python test_data/generate.py
```

## The version tag

`VERSION` holds it, one line, and every result `screen` returns carries it. A
caller that stored a result can then tell a changed code from a changed list.
Bump it whenever the words change.

## What CI can check, and what it cannot

CI has no word file, so it cannot rebuild this. What it does instead is hold
the four ports to each other: each asserts the version, the entry count and the
digest recorded in `test_data/v2_screen_list.csv`, so a port whose copy drifted
from the others fails the suite.

## The list in this repository today

A placeholder, so that the machinery is exercised end to end and the vectors
have something to assert. The words in it are ordinary and harmless. Replace it
before 2.0.0 ships.
