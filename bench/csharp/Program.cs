// The C# leg of the throughput suite. See ../README.md.
//
// Prints one `task|nanos|per-second|batch|guard` line per task, in the order
// every other leg prints them.

using System.Diagnostics;
using Ca.Pranavpatel.Algo.GridPointCode;

// ---------------------------------------------------------------------------
// The same inputs as every other leg, built by the same integer arithmetic so
// the four measure the same work with no data file between them.

const int COUNT = 256;
const int MASK = COUNT - 1;

var latitudes = new double[COUNT];
var longitudes = new double[COUNT];
var codes = new string[COUNT];
var bare = new string[COUNT];
var rows = new long[COUNT];
var columns = new long[COUNT];
var messy = new string[COUNT];

for (int i = 0; i < COUNT; i++) {
    latitudes[i] = ((i * 7919L) % 17000) / 100.0 - 85.0;
    longitudes[i] = ((i * 6271L) % 35000) / 100.0 - 175.0;
    codes[i] = GPC.Encode(latitudes[i], longitudes[i]);
    bare[i] = codes[i].Replace("#", "").Replace("-", "");
    var grid = GPC.CodeToGrid(bare[i]);
    rows[i] = grid.row;
    columns[i] = grid.col;
    messy[i] = codes[i].ToLowerInvariant();
}

// ---------------------------------------------------------------------------
// Whole batches rather than single operations: at these speeds a call per
// operation is a large part of what would be timed and is not what is being
// asked about. Each returns a number summed from every operation, because a
// just-in-time compiler may delete work whose result nothing reads.

double Encode(int size) {
    double total = 0;
    for (int i = 0; i < size; i++) {
        int j = i & MASK;
        total += GPC.Encode(latitudes[j], longitudes[j]).Length;
    }
    return total;
}

double Decode(int size) {
    double total = 0;
    for (int i = 0; i < size; i++) {
        var point = GPC.Decode(codes[i & MASK]);
        total += point.Latitude + point.Longitude;
    }
    return total;
}

double GridToCode(int size) {
    double total = 0;
    for (int i = 0; i < size; i++) {
        int j = i & MASK;
        total += GPC.GridToCode(rows[j], columns[j]).Length;
    }
    return total;
}

double CodeToGrid(int size) {
    double total = 0;
    for (int i = 0; i < size; i++) {
        var grid = GPC.CodeToGrid(bare[i & MASK]);
        total += grid.row + grid.col;
    }
    return total;
}

double Normalise(int size) {
    double total = 0;
    for (int i = 0; i < size; i++) {
        total += GPC.Normalise(messy[i & MASK]).payload.Length;
    }
    return total;
}

// Arithmetic the library has nothing to do with. Every figure is divided by
// this one, so a runner having a slow morning is slow at both and the ratio
// survives the shared machine that the absolute number does not.
double Calibrate(int size) {
    double total = 0;
    for (int i = 0; i < size; i++) {
        int x = i;
        for (int k = 0; k < 64; k++) {
            x = unchecked(x * 1103515245 + 12345) & 0x7FFFFFFF;
        }
        total += x;
    }
    return total;
}

// ---------------------------------------------------------------------------

const double LONG_ENOUGH = 50_000_000.0;
const int WARMUP = 5;
const int ROUNDS = 7;
const int CAP = 1 << 24;

double guard = 0;
double perTick = 1e9 / Stopwatch.Frequency;

double Measure(Func<int, double> task, int size) {
    long started = Stopwatch.GetTimestamp();
    double total = task(size);
    long elapsed = Stopwatch.GetTimestamp() - started;
    guard += total;
    return elapsed * perTick;
}

void Run(string name, Func<int, double> task) {
    // Grow the batch until the clock's resolution stops being part of the
    // answer. The growing doubles as warm-up: this code is compiled quickly but
    // tiered, and the first tier is not the one worth reporting.
    int size = 64;
    while (Measure(task, size) < LONG_ENOUGH && size < CAP) size *= 2;

    for (int i = 0; i < WARMUP; i++) Measure(task, size);

    var batches = new double[ROUNDS];
    for (int i = 0; i < ROUNDS; i++) batches[i] = Measure(task, size);
    Array.Sort(batches);

    double nanos = batches[(ROUNDS - 1) / 2] / size;
    Console.WriteLine("{0}|{1:F4}|{2:F0}|{3}|{4:F0}",
        name, nanos, 1e9 / nanos, size, guard);
}

Run("encode", Encode);
Run("decode", Decode);
Run("gridToCode", GridToCode);
Run("codeToGrid", CodeToGrid);
Run("normalise", Normalise);
Run("calibrate", Calibrate);
