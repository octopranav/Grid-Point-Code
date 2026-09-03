// The Java leg of the throughput suite. See README.md.
//
// Prints one `task|nanos|per-second|batch|guard` line per task, in the order
// every other leg prints them. Compiled against the classes next door by
// bench/bench.py, the same way the conformance driver is.

import ca.pranavpatel.algo.gridpointcode.Coordinates;
import ca.pranavpatel.algo.gridpointcode.GPC;

import java.util.Arrays;
import java.util.function.IntToDoubleFunction;

public class Driver {

    // -----------------------------------------------------------------------
    // The same inputs as every other leg, built by the same integer arithmetic
    // so the four measure the same work with no data file between them.

    static final int COUNT = 256;
    static final int MASK = COUNT - 1;

    static final double[] LATITUDES = new double[COUNT];
    static final double[] LONGITUDES = new double[COUNT];
    static final String[] CODES = new String[COUNT];
    static final String[] BARE = new String[COUNT];
    static final long[] ROWS = new long[COUNT];
    static final long[] COLUMNS = new long[COUNT];
    static final String[] MESSY = new String[COUNT];

    static {
        for (int i = 0; i < COUNT; i++) {
            LATITUDES[i] = ((i * 7919L) % 17000) / 100.0 - 85.0;
            LONGITUDES[i] = ((i * 6271L) % 35000) / 100.0 - 175.0;
            CODES[i] = GPC.Encode(LATITUDES[i], LONGITUDES[i]);
            BARE[i] = CODES[i].replaceAll("[^0-9A-Z]", "");
            long[] grid = GPC.CodeToGrid(BARE[i]);
            ROWS[i] = grid[0];
            COLUMNS[i] = grid[1];
            MESSY[i] = CODES[i].toLowerCase();
        }
    }

    // -----------------------------------------------------------------------
    // Whole batches rather than single operations: at these speeds a call per
    // operation is a large part of what would be timed and is not what is being
    // asked about. Each returns a number summed from every operation, because a
    // just-in-time compiler may delete work whose result nothing reads.

    static double encode(int size) {
        double total = 0;
        for (int i = 0; i < size; i++) {
            int j = i & MASK;
            total += GPC.Encode(LATITUDES[j], LONGITUDES[j]).length();
        }
        return total;
    }

    static double decode(int size) {
        double total = 0;
        for (int i = 0; i < size; i++) {
            Coordinates point = GPC.Decode(CODES[i & MASK]);
            total += point.Latitude + point.Longitude;
        }
        return total;
    }

    static double gridToCode(int size) {
        double total = 0;
        for (int i = 0; i < size; i++) {
            int j = i & MASK;
            total += GPC.GridToCode(ROWS[j], COLUMNS[j]).length();
        }
        return total;
    }

    static double codeToGrid(int size) {
        double total = 0;
        for (int i = 0; i < size; i++) {
            long[] grid = GPC.CodeToGrid(BARE[i & MASK]);
            total += grid[0] + grid[1];
        }
        return total;
    }

    static double normalise(int size) {
        double total = 0;
        for (int i = 0; i < size; i++) {
            total += GPC.Normalise(MESSY[i & MASK])[0].length();
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
    static double calibrate(int size) {
        double total = 0;
        for (int i = 0; i < size; i++) {
            int x = i;
            for (int k = 0; k < 64; k++) {
                x = (x * 1103515245 + 12345) & 0x7FFFFFFF;
            }
            total += x;
        }
        return total;
    }

    // -----------------------------------------------------------------------

    static final long LONG_ENOUGH = 50_000_000L;
    static final int WARMUP = 5;
    static final int ROUNDS = 7;
    static final int CAP = 1 << 24;

    static double guard = 0;

    static long measure(IntToDoubleFunction task, int size) {
        long started = System.nanoTime();
        double total = task.applyAsDouble(size);
        long elapsed = System.nanoTime() - started;
        guard += total;
        return elapsed;
    }

    static void run(String name, IntToDoubleFunction task) {
        // Grow the batch until the clock's resolution stops being part of the
        // answer. The growing doubles as warm-up, which matters here more than
        // anywhere: this code is interpreted until it has been run enough times
        // to be worth compiling, and the difference is an order of magnitude.
        int size = 64;
        while (measure(task, size) < LONG_ENOUGH && size < CAP) size *= 2;

        for (int i = 0; i < WARMUP; i++) measure(task, size);

        long[] batches = new long[ROUNDS];
        for (int i = 0; i < ROUNDS; i++) batches[i] = measure(task, size);
        Arrays.sort(batches);

        double nanos = (double) batches[(ROUNDS - 1) / 2] / size;
        System.out.printf("%s|%.4f|%.0f|%d|%.0f%n",
                name, nanos, 1e9 / nanos, size, guard);
    }

    public static void main(String[] args) {
        run("encode", Driver::encode);
        run("decode", Driver::decode);
        run("gridToCode", Driver::gridToCode);
        run("codeToGrid", Driver::codeToGrid);
        run("normalise", Driver::normalise);
        run("calibrate", Driver::calibrate);
    }
}
