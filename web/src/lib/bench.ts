// Measuring the library in the reader's own browser, and being honest about it.
//
// The number is the easy part. What makes a benchmark worth printing is
// everything done to stop it lying, and there are four ways this one could:
//
//   1. **The work gets deleted.** An engine that can see a result is never used
//      is entitled to remove the call that produced it, and then the page
//      reports the speed of an empty loop. Every operation here returns a
//      number that is summed into a running total, and that total is shown. A
//      data dependency the optimiser has to respect is the only real guard, and
//      putting the total on the page is how a reader can tell it was kept.
//
//   2. **The clock is too coarse.** `performance.now()` is deliberately blunted
//      against timing attacks -- tens of microseconds in some browsers -- so
//      timing one operation measures the clock. Batches are sized up until each
//      one lasts long enough that the resolution stops mattering.
//
//   3. **The first run is not the steady state.** Code starts interpreted and
//      is compiled once it looks worth compiling, so early iterations can be an
//      order of magnitude slower. Warmup batches are run and thrown away.
//
//   4. **One number hides the spread.** A machine with a busy tab, a thermal
//      limit or a garbage collection pause produces a wide distribution, and a
//      single figure from it is a guess. Every batch is kept and the median,
//      the best and the worst are all reported.
//
// None of that makes the result comparable between devices, and the page says
// so. It is a measurement of this machine, this browser, this moment.

/** One thing worth timing. */
export interface Task {
    id: string;
    name: string;
    /** What a single operation does, in the reader's language. */
    what: string;
    /**
     * One operation, returning a number.
     *
     * The number is not the point -- summing it is. It gives the optimiser a
     * reason to actually do the work.
     */
    step: (index: number) => number;
}

/** What one task's run came to. */
export interface Measurement {
    id: string;
    /** Operations a second: the median batch, not the best one. */
    perSecond: number;
    fastest: number;
    slowest: number;
    /** Nanoseconds per operation, from the median. */
    nanos: number;
    /** How many operations each timed batch ran. */
    size: number;
    /** Every batch, so the spread can be drawn rather than described. */
    batches: number[];
    /** The accumulated total, shown so a reader can see it was consumed. */
    guard: number;
}

/** A batch must last this long for the clock's bluntness to stop mattering. */
const TARGET_MS = 50;

/** Thrown away: this is the compiler making its mind up, not the steady state. */
const WARMUP = 3;

/** Kept. Enough to have a median worth the name and a spread worth showing. */
const BATCHES = 15;

/** No batch grows past this, however fast the operation turns out to be. */
const MOST = 1 << 20;

/** Nor does finding the size take longer than this, however slow it is. */
const PATIENCE = 1500;

/**
 * The same work on every machine.
 *
 * A benchmark that measured different points on different devices would be
 * measuring the points. This is xorshift32, four lines and no dependency,
 * seeded the same everywhere.
 */
export function scatter(count: number, seed = 0x9e3779b9): [number, number][] {
    let x = seed | 0 || 1;
    const next = () => {
        x ^= x << 13;
        x ^= x >>> 17;
        x ^= x << 5;
        return (x >>> 0) / 4294967296;
    };

    const points: [number, number][] = [];
    for (let i = 0; i < count; i += 1) {
        points.push([next() * 180 - 90, next() * 360 - 180]);
    }
    return points;
}

function median(values: number[]): number {
    const sorted = [...values].sort((a, b) => a - b);
    const middle = sorted.length >> 1;
    return sorted.length % 2 === 1
        ? sorted[middle]
        : (sorted[middle - 1] + sorted[middle]) / 2;
}

/** Run one batch and return how long it took, in milliseconds. */
function batch(task: Task, size: number, from: number): { ms: number; guard: number } {
    let guard = 0;
    const started = performance.now();
    for (let i = 0; i < size; i += 1) {
        guard += task.step(from + i);
    }
    return { ms: performance.now() - started, guard };
}

/**
 * Grow the batch until it lasts long enough to be worth timing.
 *
 * This doubles rather than extrapolating from a small sample: the small sample
 * is exactly the one the clock cannot measure, so predicting from it would be
 * predicting from noise.
 */
function sizeFor(task: Task): number {
    let size = 256;
    let spent = 0;

    while (size < MOST) {
        const took = batch(task, size, 0).ms;
        if (took >= TARGET_MS) return size;

        // A doubling that has already cost more than the whole measurement is
        // meant to is not tuning any more, it is a hang. Counting attempts is
        // not enough of a bound: each attempt costs twice the last, so the
        // difference between the twentieth and the twenty-first can be minutes
        // on a slow device, and the page just stops.
        spent += took;
        if (spent > PATIENCE) return size;
        size *= 2;
    }
    return size;
}

/** Let the browser paint, so a long run does not look like a hung page. */
const breathe = () => new Promise((resume) => setTimeout(resume, 0));

export async function measure(
    task: Task,
    onBatch?: (done: number, total: number) => void,
): Promise<Measurement> {
    const size = sizeFor(task);
    let guard = 0;
    let at = 0;

    for (let i = 0; i < WARMUP; i += 1) {
        guard += batch(task, size, at).guard;
        at += size;
        await breathe();
    }

    const batches: number[] = [];
    for (let i = 0; i < BATCHES; i += 1) {
        const one = batch(task, size, at);
        at += size;
        guard += one.guard;
        // A batch that somehow took no time at all would divide by zero and
        // report infinite speed, which is the most flattering possible lie.
        batches.push(one.ms > 0 ? (size / one.ms) * 1000 : 0);
        onBatch?.(i + 1, BATCHES);
        await breathe();
    }

    const middle = median(batches);
    return {
        id: task.id,
        perSecond: middle,
        fastest: Math.max(...batches),
        slowest: Math.min(...batches),
        nanos: middle > 0 ? 1e9 / middle : 0,
        size,
        batches,
        guard,
    };
}

/** A rate, said the way a reader would say it. */
export function rate(perSecond: number): string {
    if (perSecond >= 1e6) return (perSecond / 1e6).toFixed(2) + ' million/s';
    if (perSecond >= 1e3) return Math.round(perSecond / 1e3) + ' thousand/s';
    return Math.round(perSecond) + '/s';
}

/** A duration, in whichever unit does not need a run of zeros. */
export function duration(nanos: number): string {
    if (nanos < 1000) return nanos.toFixed(0) + ' ns';
    if (nanos < 1e6) return (nanos / 1000).toFixed(2) + ' µs';
    return (nanos / 1e6).toFixed(2) + ' ms';
}
