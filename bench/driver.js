// The TypeScript leg of the throughput suite. See README.md.
//
// Reads the built package, so `npm run build` in typescript/ has to have run.
// Prints one `task|nanos|per-second|batch|guard` line per task, in the order
// every other leg prints them.

const path = require('path');

const main = process.env.GPC_TYPESCRIPT_MAIN
    || path.join(__dirname, '..', 'typescript', 'dist', 'index.js');
const { GPC } = require(main);

// ---------------------------------------------------------------------------
// The same inputs as every other leg, built by the same integer arithmetic so
// that no data file has to be shared to make the four measure the same work.

const COUNT = 256;
const MASK = COUNT - 1;

const LATITUDES = [];
const LONGITUDES = [];
for (let i = 0; i < COUNT; i += 1) {
    LATITUDES.push(((i * 7919) % 17000) / 100 - 85);
    LONGITUDES.push(((i * 6271) % 35000) / 100 - 175);
}

const CODES = LATITUDES.map((latitude, i) => GPC.encode(latitude, LONGITUDES[i]));
const BARE = CODES.map((code) => code.replace(/[^0-9A-Z]/g, ''));
const GRID = BARE.map((bare) => GPC.codeToGrid(bare));
const ROWS = GRID.map((pair) => pair[0]);
const COLUMNS = GRID.map((pair) => pair[1]);
const MESSY = CODES.map((code) => code.toLowerCase());

// ---------------------------------------------------------------------------
// Whole batches rather than single operations: per-operation call overhead is
// a large part of what would otherwise be timed, and it is not what is being
// asked about. Each returns a number summed from every operation, because an
// engine may delete work whose result nothing looks at.

function taskEncode(size) {
    let total = 0;
    for (let i = 0; i < size; i += 1) {
        const j = i & MASK;
        total += GPC.encode(LATITUDES[j], LONGITUDES[j]).length;
    }
    return total;
}

function taskDecode(size) {
    let total = 0;
    for (let i = 0; i < size; i += 1) {
        const [latitude, longitude] = GPC.decode(CODES[i & MASK]);
        total += latitude + longitude;
    }
    return total;
}

function taskGridToCode(size) {
    let total = 0;
    for (let i = 0; i < size; i += 1) {
        const j = i & MASK;
        total += GPC.gridToCode(ROWS[j], COLUMNS[j]).length;
    }
    return total;
}

function taskCodeToGrid(size) {
    let total = 0;
    for (let i = 0; i < size; i += 1) {
        const [row, column] = GPC.codeToGrid(BARE[i & MASK]);
        total += row + column;
    }
    return total;
}

function taskNormalise(size) {
    let total = 0;
    for (let i = 0; i < size; i += 1) {
        total += GPC.normalise(MESSY[i & MASK])[0].length;
    }
    return total;
}

/**
 * Arithmetic the library has nothing to do with.
 *
 * Every figure is divided by this one, so a runner having a slow morning is
 * slow at both and the ratio survives the shared machine that the absolute
 * number does not.
 */
function taskCalibrate(size) {
    let total = 0;
    for (let i = 0; i < size; i += 1) {
        let x = i;
        for (let k = 0; k < 64; k += 1) {
            x = (Math.imul(x, 1103515245) + 12345) & 0x7FFFFFFF;
        }
        total += x;
    }
    return total;
}

const TASKS = [
    ['encode', taskEncode],
    ['decode', taskDecode],
    ['gridToCode', taskGridToCode],
    ['codeToGrid', taskCodeToGrid],
    ['normalise', taskNormalise],
    ['calibrate', taskCalibrate],
];

// ---------------------------------------------------------------------------

const LONG_ENOUGH = 50000000n;      // nanoseconds a batch must last to be timed
const WARMUP = 5;
const ROUNDS = 7;
const CAP = 1 << 24;

let guard = 0;

function measure(task, size) {
    const started = process.hrtime.bigint();
    const total = task(size);
    const elapsed = process.hrtime.bigint() - started;
    guard += total;
    return elapsed;
}

function run(name, task) {
    // Grow the batch until the clock's resolution stops being part of the
    // answer. The growing doubles as warm-up, which matters more here than in
    // most places: this code starts interpreted and is compiled only once it
    // has been run enough to look worth compiling.
    let size = 64;
    while (measure(task, size) < LONG_ENOUGH && size < CAP) size *= 2;

    for (let i = 0; i < WARMUP; i += 1) measure(task, size);

    const batches = [];
    for (let i = 0; i < ROUNDS; i += 1) batches.push(measure(task, size));
    batches.sort((one, other) => (one < other ? -1 : one > other ? 1 : 0));

    const nanos = Number(batches[(ROUNDS - 1) >> 1]) / size;
    process.stdout.write(
        `${name}|${nanos.toFixed(4)}|${(1e9 / nanos).toFixed(0)}|${size}|${guard.toFixed(0)}\n`,
    );
}

for (const [name, task] of TASKS) run(name, task);
