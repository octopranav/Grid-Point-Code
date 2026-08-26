# The advisory list

`screen(code)` reports substrings of a code that spell something unwanted.
[Section 17 of SPEC.md](../SPEC.md#17-advisory-screening-non-normative) defines
it. It advises and never blocks: nothing in the library refuses to encode,
decode or validate because of what it found.

This directory turns a list of words into the form the four ports carry.

## The words are here, and they are not plaintext

`screening/words.zip` holds the word list. It is encrypted, and the passphrase
is `gridpointcode` — written here, and in `expand.py`, because it is not a
secret and nothing in this repository should pretend otherwise.

The point is narrower than secrecy. Encrypting the archive keeps the words out
of code search, out of `grep`, out of a web search for this repository, and out
of the four published packages, which carry only hashes. It does not keep them
from anyone who wants to read them, and it is not meant to: the specification
says the same of the hashes, which are thirty-two bits over an alphabet of
twenty-five symbols and would give the list up to anyone willing to spend a few
seconds on it. That is also why the hash is a cheap mixer rather than a
cryptographic one: it would buy nothing here, and it would cost three of the
four ports an import they otherwise do not need.

So the list stays auditable. Anyone who wants to know what this library warns
about can open the archive and read it, which is a better position for a public
format than asking people to take it on trust.

To read it by hand:

```bash
unzip -P gridpointcode screening/words.zip
```

`screening/words.txt` is the plain working copy. It is ignored by git and is
optional — `expand.py` reads the archive. When the working copy is present it
must agree with the archive, and the script stops if it does not, so a
forgotten re-zip fails locally rather than in CI.

## The word file

One word per line, lower case, `#` for a comment, blank lines ignored:

```
# unwanted words, one per line
gnat
cattle
```

Words shorter than four letters are dropped, because a three-symbol run turns
up by chance often enough that warning about it would mean nothing. So are
words containing `q`, `u`, `v` or `y`, which have no representation in the
alphabet and therefore cannot appear in a code at all.

## Changing the list

```bash
unzip -P gridpointcode screening/words.zip     # then edit words.txt
zip -q -j -X -P gridpointcode screening/words.zip screening/words.txt
python screening/expand.py
python test_data/generate.py
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

Do not edit those by hand. The vectors have to be regenerated as well, because
the screening ones are built from the list and will have moved.

Re-zipping is not reproducible: the cipher salts every archive with a random
twelve-byte header, so the file differs even when the words do not. Re-zip only
when the list actually changes, or each run leaves a diff behind for nothing.

## The version tag

`VERSION` holds it, one line, and every result `screen` returns carries it. A
caller that stored a result can then tell a changed result from a changed list.
Bump it whenever the words change.

## What CI checks

Two things, in two places.

`screening/expand.py` runs on every push, and the four generated files must not
move — which catches one edited by hand, and an archive that was changed
without rebuilding them. Separately, each port's suite asserts the version, the
entry count and the digest recorded in `test_data/v2_screen_list.csv`, which
catches a port whose copy drifted from the other three.

## The list in this repository today

Seventy-one English words expanding to 193 variants. Measured over 300,000
random codes, **one code in 1,107 matches something**.

Two rules of the format decide what can be on the list at all, and both cut
deeper than any editorial judgement. Four letters minimum, because a
three-symbol run occurs by chance often enough that a warning built on it would
mean nothing. And no `q`, `u`, `v` or `y`, because those have no symbol in the
alphabet, so a word containing one cannot appear in a code however much anyone
might want it flagged. Between them they remove most of the obvious English
vocabulary before there is anything to decide.

The warning rate is made almost entirely of the four-letter entries: one of
those matches about one code in 55,800, where a six-letter entry matches about
one in 49 million and a longer one never fires at all. Long words are therefore
free to add and cover nothing — if the list ever needs to be quieter, it is the
four-letter words that have to go.

The list is English. A word that is unremarkable in English and offensive
somewhere else is not covered, and adding one is a release rather than a
setting, because the hashes are compiled into the four ports.
