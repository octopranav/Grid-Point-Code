# Throughput

Five operations, four ports, the same work.

```
python bench/bench.py                 measure and tabulate
python bench/bench.py --check         and fail if a port has got far slower
python bench/bench.py --update        rewrite the baseline from this run
python bench/bench.py --only java     one leg, while you are changing it
```

The site's `/bench` page measures the library in a visitor's own browser, which
is one language on one machine and says nothing about the other three. This is
the other half: the same operations in Python, TypeScript, Java and C#, run
together so a regression in one of them is visible rather than reported by
somebody months later.

## What it measures

| task | what one operation does |
| --- | --- |
| `encode` | a coordinate to a formatted code |
| `decode` | a formatted code back to a coordinate |
| `gridToCode` | the integer core, with no floating point in it |
| `codeToGrid` | the same, inverted |
| `normalise` | case-folding, separators and the alias table |

`encode` and `decode` are the pair almost every caller uses. `gridToCode` and
`codeToGrid` are underneath them, and having both means a change can be located:
if `encode` slows down and `gridToCode` does not, the cost is in the coordinate
arithmetic rather than the traversal.

`decode` is the slowest of the five in every port, by a factor of three to
seven. That is not a defect. It normalises its input, verifies the check
character and then does the traversal, where `encode` is handed two numbers and
does the traversal alone.

## Why the numbers are ratios

A shared runner is a bad clock. The same commit measured twice on the same
service can come back a factor of two apart depending on what else was on the
machine, so an absolute figure cannot be compared between runs, and a threshold
on one would fire at random until somebody switched it off.

So each leg also times a fixed arithmetic loop that has nothing to do with the
library, and every figure is quoted as a multiple of it. A runner having a slow
morning is slow at both, and the ratio stands still.

That is measurable, so it was measured. Two runs of this job on the same commit,
minutes apart, on the same service:

| | worst movement between the two runs |
| --- | --- |
| operations a second | 1.62× — Java `decode`, 2.01 M then 3.26 M |
| calibrated cost | 1.22× — Python `gridToCode`, 0.27 then 0.33 |

Nothing about the library changed between those runs. The first column is what a
check on absolute throughput would have had to tolerate; the second is what this
one tolerates.

That ratio is **not** comparable between languages. Sixty-four interpreted
operations in Python cost about a hundred times what they cost in Java, so the
unit itself is different in each column. Compare a column with itself over time.

It is not perfectly comparable between *machines* either. The same ratios taken
on a workstation and on the runner differ by as much as 2.6× — TypeScript
`codeToGrid` is 4.76 calibration units on one and 1.82 on the other. The loop
removes a machine's speed, not its architecture or its just-in-time compiler,
which is why the baseline is taken where the check runs.

## Why the band is wide

`--check` fails when an operation costs more than twice its baseline. That is
deliberately loose, and the measurement above says how loose: the worst
run-to-run movement observed with nothing changed was 1.22×, so the threshold
sits with about 60 % of headroom above the noise rather than being a guess.

What this catches is the kind of regression that is otherwise invisible until a
user reports it: a regular expression compiled inside a loop, a lookup table
rebuilt per call, an accidental linear scan through the alphabet. Those are not
twenty percent, they are five times, and they show up immediately.

Gradual drift is what the table is for. It is printed on every run and it goes
into the job summary, where a person reads it.

## How a figure is arrived at

Each leg does the same four things, for the same reasons the `/bench` page does
them:

1. **Whole batches, not single operations.** At these speeds the cost of calling
   a function per operation is a large part of what would be timed, and it is
   not what is being asked about.
2. **The batch grows until it lasts 50 ms.** A clock with tens of nanoseconds of
   resolution cannot time one operation; timing one measures the clock.
3. **The growing doubles as warm-up, and five more batches are thrown away.**
   Java and C# start interpreted and compile once the code looks worth
   compiling, and the difference is an order of magnitude.
4. **Seven batches are kept and the median is reported**, not the best one. A
   collection pause or a busy neighbour should not become the headline.

Every operation returns a number that is summed into a running total, and the
total is printed. A compiler is entitled to delete work whose result nothing
reads, and a benchmark that has been optimised away reports the speed of an
empty loop rather than saying anything is wrong. The total is the data
dependency that stops it, and printing it is how a reader can tell it was kept.

The inputs are 256 points built by integer arithmetic from an index, identically
in all four languages, so the four measure the same work with no data file
between them. Cycling through them rather than repeating one point keeps a
branch predictor from learning the answer.

## The baseline

`baseline.json` holds the calibrated cost of each operation, taken on a CI
runner rather than a workstation, for the reason given above. Rewrite it with
`--update` when a change makes something legitimately slower or faster, and say
why in the commit.
