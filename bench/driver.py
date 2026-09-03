"""The Python leg of the throughput suite. See README.md.

Prints one `task|nanos|per-second|batch|guard` line per task. Every port prints
the same task names in the same order; bench/bench.py runs all four and
tabulates.
"""

import os
import statistics
import sys
import time
from pathlib import Path

# The source next door by default, the same way the conformance driver does it,
# so this can be pointed at an installed wheel without a second copy existing.
SOURCE = os.environ.get("GPC_PYTHON_PATH") or str(
    Path(__file__).resolve().parent.parent / "python" / "src"
)
sys.path.insert(0, SOURCE)

from gridpointcode_algo_pranavpatel_ca import GPC  # noqa: E402

# --------------------------------------------------------------------------
# The inputs, built by integer arithmetic so that all four ports measure the
# same work on the same points without a shared data file to load.

COUNT = 256
MASK = COUNT - 1

LATITUDES = [((i * 7919) % 17000) / 100.0 - 85.0 for i in range(COUNT)]
LONGITUDES = [((i * 6271) % 35000) / 100.0 - 175.0 for i in range(COUNT)]

CODES = [GPC.encode(LATITUDES[i], LONGITUDES[i]) for i in range(COUNT)]
BARE = [code.replace("#", "").replace("-", "") for code in CODES]
GRID = [GPC.code_to_grid(bare) for bare in BARE]
ROWS = [pair[0] for pair in GRID]
COLUMNS = [pair[1] for pair in GRID]

# What somebody actually types: the formatted code in the wrong case.
MESSY = [code.lower() for code in CODES]


# --------------------------------------------------------------------------
# The tasks. Each one runs a whole batch rather than a single operation: at
# these speeds the cost of calling a function per operation is a large part of
# what would be measured, and it is not part of what is being asked about.
#
# Each returns a number summed from every operation. That is the guard -- an
# interpreter or a JIT is entitled to delete work whose result is never looked
# at, and a benchmark that has been optimised away reports the speed of an
# empty loop rather than saying anything is wrong.


def task_encode(size):
    total = 0.0
    for i in range(size):
        j = i & MASK
        total += len(GPC.encode(LATITUDES[j], LONGITUDES[j]))
    return total


def task_decode(size):
    total = 0.0
    for i in range(size):
        latitude, longitude = GPC.decode(CODES[i & MASK])
        total += latitude + longitude
    return total


def task_grid_to_code(size):
    total = 0.0
    for i in range(size):
        j = i & MASK
        total += len(GPC.grid_to_code(ROWS[j], COLUMNS[j]))
    return total


def task_code_to_grid(size):
    total = 0.0
    for i in range(size):
        row, column = GPC.code_to_grid(BARE[i & MASK])
        total += row + column
    return total


def task_normalise(size):
    total = 0.0
    for i in range(size):
        payload, _ = GPC.normalise(MESSY[i & MASK])
        total += len(payload)
    return total


def task_calibrate(size):
    """Arithmetic the library has nothing to do with.

    Every figure is divided by this one. A runner that is having a slow morning
    is slow at both, so the ratio survives the shared machine that the absolute
    number does not -- which is the whole reason this suite can run in CI and
    mean something.
    """
    total = 0.0
    for i in range(size):
        x = i
        for _ in range(64):
            x = (x * 1103515245 + 12345) & 0x7FFFFFFF
        total += x
    return total


TASKS = [
    ("encode", task_encode),
    ("decode", task_decode),
    ("gridToCode", task_grid_to_code),
    ("codeToGrid", task_code_to_grid),
    ("normalise", task_normalise),
    ("calibrate", task_calibrate),
]


# --------------------------------------------------------------------------

LONG_ENOUGH = 50_000_000        # nanoseconds a batch must last to be timed
WARMUP = 5
ROUNDS = 7
CAP = 1 << 24

guard = 0.0


def measure(task, size):
    global guard
    started = time.perf_counter_ns()
    total = task(size)
    elapsed = time.perf_counter_ns() - started
    guard += total
    return elapsed


def run(name, task):
    # Grow the batch until it lasts long enough that the clock's resolution
    # stops being part of the answer. The growing doubles as warm-up.
    size = 64
    while measure(task, size) < LONG_ENOUGH and size < CAP:
        size *= 2

    for _ in range(WARMUP):
        measure(task, size)

    # Every batch kept, and the median reported rather than the best: one
    # collection pause or one busy neighbour should not become the headline.
    batches = sorted(measure(task, size) for _ in range(ROUNDS))
    nanos = statistics.median(batches) / size

    print(f"{name}|{nanos:.4f}|{1e9 / nanos:.0f}|{size}|{guard:.0f}", flush=True)


for name, task in TASKS:
    run(name, task)
