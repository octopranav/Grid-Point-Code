# Differential conformance

One battery of awkward inputs, compiled against all four ports, diffed.

```bash
python conformance/compare.py
```

Exits non-zero on any disagreement, naming the case and printing what each port
produced.

## Why this exists alongside the vectors

The shared vectors in [`test_data/`](../test_data/) are the primary conformance
mechanism and this does not replace them. But there is a class of divergence
they cannot catch, by construction.

A vector records an answer somebody already knew. It pins the cases the author
thought to write down, which are the cases where the ports were already
understood to agree. Where four implementations quietly differ is precisely
where nobody wrote a vector — a level of zero, a cell prefix on the polar row, a
`geo:` URI with a coordinate reference system parameter, an empty batch, an
integer one past the end of the range. Those are not interesting enough to
become vectors until one of them is wrong.

This runs from the other side. It does not assert *what* the answer is. It
asserts only that four independent implementations produce the *same* answer,
whatever that answer turns out to be. A case can be added without anyone
deciding first what it should return, which is what makes it cheap enough to
cover the long tail.

It found a real divergence the first time it was run: a level outside 1 to 10
raised a different error type in C# from the other three, which no vector could
have expressed because a vector has one expected value and the C# one was
legitimately different.

## How it works

Each port has a driver that runs the same cases in the same order and prints one
`label|result` line per case:

| Port | Driver |
| --- | --- |
| Python | `driver.py` |
| TypeScript | `driver.js` |
| Java | `Driver.java` |
| C# | `csharp/Program.cs` |

`compare.py` runs all four, normalises the two things that differ for reasons
that are not behaviour, and diffs the rest:

* **Number spelling.** Each language prints doubles its own way — `0` against
  `0.0`, `2.304e-05` against `0.00002304`. Only a value that is *entirely*
  numbers is rewritten. Formatted output is compared exactly as produced, so the
  padded fields of `43°39'00.02"N` survive.
* **One sanctioned difference**, listed in `SANCTIONED` at the top of
  `compare.py` with the reason. Anything not listed there fails. Making a port
  uniform means deleting an entry, never adding one.

The drivers must stay in lockstep. If one runs a case the others do not,
`compare.py` reports the missing or extra labels rather than silently comparing
a shorter list.

## Adding a case

Add the same line to all four drivers, in the same position, with the same
label. Do not work out the expected value first — the point is that the four
ports agree, not that they match a number somebody wrote by hand. If they
disagree, that is the finding.

Reach for a vector instead when the answer matters in its own right: a code that
must decode to a particular coordinate belongs in `test_data/`, where it is
checked against a value rather than against three other implementations.

## Prerequisites

All four toolchains, and two build steps, because the drivers link against built
output rather than source:

```bash
cd typescript && npm run build
cd java && mvn -q compile
```

`compare.py` checks for both and for the four tools before it starts, and says
which is missing. It checks that the built output is *there*, not that it is
current, so rebuild after changing a port or the harness will faithfully report
a divergence between your new source and yesterday's `dist/`.

## The same battery, put to the published packages

```bash
python conformance/compare.py --released
python conformance/compare.py --released python=2.0.0 java=2.0.0
```

Everything above proves the four *implementations* agree. Both the shared
vectors and the default mode of this harness build from the tree, which leaves
one step unexamined: everything between a commit passing and a stranger typing
`pip install`.

That gap is not hypothetical. A package can ship the wrong files, be cut from
the wrong commit, resolve to a version nobody meant, or lose a module to a
packaging exclusion that no test in this repository can see. The source can be
perfect and the artifact wrong.

So `--released` installs from npm, PyPI, Maven Central and NuGet, and puts the
same battery to those. Nothing is built from the tree; if anything were, this
would only be answering the question the default mode already answers. It takes
the newest published version of each unless told otherwise, because the standing
question is whether what somebody can install *today* agrees — a pin could only
ever confirm what was true when the pin was written. Pins exist for reproducing
a past run.

The drivers are the same four files. Two of them take an environment variable
naming where their port lives, the C# project takes a `Released` property, and
the Java classpath is a jar instead of a classes directory. A second set of
drivers would be a second thing to keep in step, and the first time they drifted
the harness would be comparing two different questions.

It needs `npm` and `mvn` on top of the usual four toolchains, and a network.

## Generated cases, and why they are generated the way they are

```bash
python conformance/fuzz.py
python conformance/fuzz.py --cases 5000 --rounds 6
python conformance/fuzz.py --released
python conformance/fuzz.py --seed 20260901
```

The battery pins what somebody thought to write down. `fuzz.py` generates what
nobody did, puts it to the same four drivers, and holds them to three things
the battery cannot check on its own.

**What it generates is drawn from what actually went wrong.** 1.1.0 was a repair
release, and its four faults are the shape of the whole generator:

| What broke in 1.0 | What the generator does about it |
| --- | --- |
| `encode(89.9999999999999, 0)` went out of domain, was accepted, and decoded half a world away | Coordinates are pressed hard up against whole degrees and the edges of the world, down to the last bit a double holds. Uniform sampling would never once have produced that number. |
| Three ports converted a double to decimal three different ways | Every case goes to all four and is diffed |
| `isValid("CCCC-CCCC-CCC")` said yes, and decoding it then raised | Every generated code is asked both questions, and a port that says yes and then throws has contradicted itself |
| `encode(-0.0, -0.0)` differed from `encode(0.0, 0.0)` | Negative zero is generated on purpose |

**Agreement is not correctness.** Four ports that all crash agree perfectly. The
drivers already separate a documented error (`ERR:`) from an unexpected one
(`EXC:`), so an `EXC:` fails on its own account whether or not the others
produced it too. The valid-then-undecodable check is the same idea: all four
could have been wrong together, and diffing them would have said nothing.

**Round-trip is judged against a cell, not against five decimal places.** Encode
picks the cell a point is in and decode hands back a point in that cell, so the
two may differ by up to `180/(4*5^9)` of latitude and `360/(6*5^9)` of
longitude and nothing is wrong. Judged against one unit in the fifth decimal
place instead, the first run reported twenty-two faults that were the
arithmetic working exactly as specified. Longitude is compared the short way
round, or a point on the antimeridian encodes perfectly and measures as 360
degrees of error.

Every run prints its seed, and passing it back regenerates that run exactly.

Two limits, stated rather than hidden. NaN and the infinities are not
generated: the case file carries numbers as text, and the four languages
disagree about what text means those values, so sending them would test the
harness rather than the ports. And a string carrying a bar or a line break
cannot be sent, because the case file splits on bars -- those are dropped
rather than escaped, since four drivers would each need the same unescaping and
a bar is only one more invalid character among many the generator can already
produce.

`test_fuzz.py` gives every one of those checks answers that break it. A fuzzer
nobody has watched fail is a fuzzer nobody knows works, and ninety thousand
clean cases read exactly like ninety thousand cases and no working checks.

## In CI

The `differential` job runs this on every push. It is the only job that needs
all four toolchains at once, which is why it is slower than the per-port jobs and
why those still exist: when a port breaks, its own job says so in a language its
maintainer recognises, and this one says the four no longer agree.

The `differential` job also runs `test_fuzz.py` and then a fuzzing pass on a
fresh seed every time, so each run reaches somewhere the battery does not. It
rides on that job because the job has already paid for the four toolchains,
which is the expensive part; the fuzzing itself is seconds. A failure prints
the seed that reproduces it.

The `Released` workflow runs `--released` weekly and on request, both the
battery and a fuzzing pass. It is **deliberately not a pull-request check**:
what is published has nothing to do with the diff under review, and a bad
release must not block unrelated work. Run it by hand after publishing.
