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
which is missing.

## In CI

The `differential` job runs this on every push. It is the only job that needs
all four toolchains at once, which is why it is slower than the per-port jobs and
why those still exist: when a port breaks, its own job says so in a language its
maintainer recognises, and this one says the four no longer agree.
