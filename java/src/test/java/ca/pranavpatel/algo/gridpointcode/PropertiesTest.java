package ca.pranavpatel.algo.gridpointcode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Properties that hold for every point, checked over a wide generated sample.
 *
 * <p>The files in test_data/ pin behaviour case by case. This class pins the
 * rules that must hold everywhere: a code is always ten characters, always
 * spelled from the alphabet, always valid, and always decodes back inside the
 * cell it came from. It also pins the two properties the whole format exists
 * for -- containment of a shared prefix, and continuity of the ordering.
 *
 * <p>The sample behind them is a hundred thousand coordinates that are
 * generated rather than stored, so the same inputs reach every port without a
 * large file in the repository. Its definition lives in test_data/README.md;
 * the digest of the codes it produces lives in test_data/v2_sample.csv, which
 * is what makes this class a cross-port check as well as a local one.
 *
 * <p>Every constant below is written out rather than read from the
 * implementation. A test that borrows the constant it is checking proves
 * nothing.
 */
class PropertiesTest {

    /** The specified alphabet, written out rather than read from the implementation. */
    private static final String ALPHABET = "0123456789CDFGHJKLMNPRTWX";

    private static final int CODE_LENGTH = 10;
    private static final int FORMATTED_LENGTH = 12;

    /** The grid of section 3. */
    private static final long ROWS = 7_812_500L;   // 4 * 5^9
    private static final long COLS = 11_718_750L;  // 6 * 5^9

    /**
     * 24 * 25^4 level-5 cells, so one fewer transition between them, out of
     * 24 * 25^9 - 1 steps in all. That is the 99.99999 % of section 5.3.
     */
    private static final long LEVEL_5_CELLS = 9_375_000L;
    private static final long TOTAL_STEPS = 91_552_734_374_999L;

    // Generator constants. Kept beside the code that uses them so this file
    // reads as a standalone statement of the sample, the same way every other
    // port does.
    private static final long MULTIPLIER = 1_664_525L;
    private static final long INCREMENT = 1_013_904_223L;
    private static final long MODULUS = 4_294_967_296L; // 2^32
    private static final long LATITUDE_SPAN = 18_000_001L;   // -90.00000 .. 90.00000 in units of 1e-5
    private static final long LONGITUDE_SPAN = 36_000_001L;  // -180.00000 .. 180.00000 in units of 1e-5

    private static int count;
    private static long seed;
    private static String digest;
    private static double[] latitudes;
    private static double[] longitudes;
    private static String[] codes;

    /** Walk up from the working directory until the shared test_data directory appears. */
    private static Path testDataDir() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("test_data");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("test_data directory not found above "
                + Paths.get("").toAbsolutePath());
    }

    /**
     * Read the sample definition, then generate and encode every point in it.
     *
     * <p>The generator is a linear congruential sequence whose products stay
     * below 2^53, so every port walks it exactly, including the ones whose only
     * number is a double.
     */
    @BeforeAll
    static void buildSample() {
        Path file = testDataDir().resolve("v2_sample.csv");
        try {
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split(",");
                count = Integer.parseInt(fields[0]);
                seed = Long.parseLong(fields[1]);
                digest = fields[2];
                break;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (digest == null) {
            throw new IllegalStateException("no data row in " + file);
        }

        latitudes = new double[count];
        longitudes = new double[count];
        codes = new String[count];
        long state = seed;
        for (int i = 0; i < count; i++) {
            state = ((MULTIPLIER * state) + INCREMENT) % MODULUS;
            latitudes[i] = ((state % LATITUDE_SPAN) - ((LATITUDE_SPAN - 1) / 2)) / 100000.0;
            state = ((MULTIPLIER * state) + INCREMENT) % MODULUS;
            longitudes[i] = ((state % LONGITUDE_SPAN) - ((LONGITUDE_SPAN - 1) / 2)) / 100000.0;
            codes[i] = GPC.Encode(latitudes[i], longitudes[i], false);
        }
    }

    /** Section 5.1, restated. The row and column a coordinate falls in. */
    private static long[] grid(double latitude, double longitude) {
        if (longitude == 180.0) {
            longitude = -180.0;
        }
        long row = (long)Math.floor((latitude + 90.0) * 7812500.0 / 180.0);
        long col = (long)Math.floor((longitude + 180.0) * 11718750.0 / 360.0);
        return new long[] {Math.min(Math.max(row, 0), ROWS - 1), Math.min(Math.max(col, 0), COLS - 1)};
    }

    /** The next code in plain ASCII order, which is base-25 counting. */
    private static String successor(String code) {
        char[] out = code.toCharArray();
        for (int position = out.length - 1; position >= 0; position--) {
            int index = ALPHABET.indexOf(out[position]) + 1;
            if (index < ALPHABET.length()) {
                out[position] = ALPHABET.charAt(index);
                return new String(out);
            }
            out[position] = ALPHABET.charAt(0);
        }
        throw new IllegalStateException("ran off the end of the code space");
    }

    @Test
    void drawsASubstantialSample() {
        assertTrue(count >= 100_000, "expected at least a hundred thousand points");
        assertEquals(count, codes.length);
    }

    /** The one assertion that fails when two ports stop agreeing. */
    @Test
    void reproducesTheDigestEveryOtherPortReproduces() throws NoSuchAlgorithmException {
        byte[] joined = String.join("\n", codes).getBytes(StandardCharsets.UTF_8);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(joined);
        assertEquals(digest, HexFormat.of().formatHex(hash));
    }

    @Test
    void givesEveryCodeTheFixedLength() {
        for (String code : codes) {
            assertTrue(code.length() == CODE_LENGTH,
                    () -> code + " is " + code.length() + " characters, not " + CODE_LENGTH);
        }
    }

    @Test
    void spellsEveryCodeFromTheAlphabet() {
        for (String code : codes) {
            for (int i = 0; i < code.length(); i++) {
                char character = code.charAt(i);
                assertTrue(ALPHABET.indexOf(character) >= 0,
                        () -> code + " contains " + character + ", outside the alphabet");
            }
        }
    }

    /** Level 1 yields 24 indices, so the X-prefixed space is unreachable. */
    @Test
    void neverEncodesIntoTheReservedNamespace() {
        for (String code : codes) {
            assertTrue(code.charAt(0) != 'X', () -> code + " was encoded but begins with X");
        }
    }

    @Test
    void validatesEveryCodeItProduced() {
        for (String code : codes) {
            assertTrue(GPC.IsValid(code),
                    () -> code + " came out of Encode but failed validation: "
                            + GPC.Validate(code).Reason);
        }
    }

    @Test
    void decodesBackInsideTheCellThePointCameFrom() {
        for (int i = 0; i < count; i++) {
            Area area = GPC.DecodeToArea(codes[i]);
            Coordinates decoded = GPC.Decode(codes[i]);
            final int at = i;
            assertTrue(decoded.Latitude >= area.South && decoded.Latitude <= area.North
                    && decoded.Longitude >= area.West && decoded.Longitude <= area.East,
                    () -> codes[at] + " decoded outside its own area");
        }
    }

    @Test
    void roundTripsEveryCodeUnchanged() {
        for (String code : codes) {
            Coordinates decoded = GPC.Decode(code);
            String again = GPC.Encode(decoded.Latitude, decoded.Longitude, false);
            assertTrue(again.equals(code), () -> code + " re-encoded as " + again + " after decoding");
        }
    }

    @Test
    void formatsTheCodeByAddingSeparatorsAndNothingElse() {
        for (int i = 0; i < 1000; i++) {
            String formatted = GPC.Encode(latitudes[i], longitudes[i], true);
            String code = codes[i];
            assertEquals(FORMATTED_LENGTH, formatted.length());
            assertEquals("#" + code.substring(0, 5) + "-" + code.substring(5), formatted);
        }
    }

    /**
     * Section 11.1. The alphabet is ASCII-ascending, so sorting codes as bytes
     * sorts them the way the grid is traversed.
     */
    @Test
    void sortsAsAStringTheWayItSortsInSpace() {
        String[] sorted = Arrays.copyOf(codes, 20_000);
        Arrays.sort(sorted);
        for (int i = 1; i < sorted.length; i++) {
            assertTrue(sorted[i - 1].compareTo(sorted[i]) <= 0);
        }
    }

    /**
     * Section 10. Two codes agree in their first k characters if and only if the
     * points lie in the same level-k cell.
     */
    @Test
    void givesOnePrefixToOneCellAndOneCellToOnePrefix() {
        Map<String, String> cells = new HashMap<>();
        Map<String, String> byPrefix = new HashMap<>();
        for (int i = 0; i < 20_000; i++) {
            long[] rc = grid(latitudes[i], longitudes[i]);
            for (int k = 1; k <= 10; k++) {
                long p = (long)Math.pow(5, 10 - k);
                String key = k + ":" + (rc[0] / p) + ":" + (rc[1] / p);
                String prefix = k + ":" + codes[i].substring(0, k);
                String seen = cells.putIfAbsent(key, prefix);
                if (seen != null) {
                    assertEquals(seen, prefix, key + " named twice");
                }
                String named = byPrefix.putIfAbsent(prefix, key);
                if (named != null) {
                    assertEquals(named, key, prefix + " names two cells");
                }
            }
        }
    }

    /** The box of a code lies inside its level-k cell, for every k. */
    @Test
    void keepsTheBoxOfACodeInsideItsLevelKCell() {
        for (int i = 0; i < 2000; i++) {
            long[] rc = grid(latitudes[i], longitudes[i]);
            Area area = GPC.DecodeToArea(codes[i]);
            for (int k = 1; k <= 10; k++) {
                long p = (long)Math.pow(5, 10 - k);
                // The same expression shape section 6.3 uses, so when the cell
                // edge and the box edge coincide they are the identical double.
                double cellSouth = (rc[0] / p) * p * 180.0 / 7812500.0 - 90.0;
                double cellNorth = ((rc[0] / p) + 1) * p * 180.0 / 7812500.0 - 90.0;
                double cellWest = (rc[1] / p) * p * 360.0 / 11718750.0 - 180.0;
                double cellEast = ((rc[1] / p) + 1) * p * 360.0 / 11718750.0 - 180.0;
                final int level = k;
                final int at = i;
                assertTrue(cellSouth <= area.South && area.North <= cellNorth,
                        () -> codes[at] + " k=" + level + " escapes its cell in latitude");
                assertTrue(cellWest <= area.West && area.East <= cellEast,
                        () -> codes[at] + " k=" + level + " escapes its cell in longitude");
            }
        }
    }

    /** The discontinuity count is the one section 5.3 states. */
    @Test
    void countsTheDiscontinuitiesTheSpecificationCounts() {
        assertEquals(LEVEL_5_CELLS, 24L * 25 * 25 * 25 * 25);
        assertEquals(9_374_999L, LEVEL_5_CELLS - 1);
        assertEquals(TOTAL_STEPS, (24L * 25 * 25 * 25 * 25 * 25 * 25 * 25 * 25 * 25) - 1);
        double share = (double)(TOTAL_STEPS - (LEVEL_5_CELLS - 1)) / TOTAL_STEPS;
        assertEquals("99.99999", String.format(java.util.Locale.ROOT, "%.5f", share * 100));
    }

    /**
     * Section 11.2. Consecutive codes are adjacent cells inside a level-5 cell.
     * A transcription error anywhere in the reflection breaks this.
     */
    @Test
    void putsConsecutiveCodesInAdjacentCellsInsideALevelFiveCell() {
        double[][] starts = {
            {43.65, -79.38}, {-33.8568, 151.2153}, {0.0, 0.0},
            {64.1466, -21.9426}, {-13.1631, -72.545}, {23.0225, 72.5714}
        };
        for (double[] start : starts) {
            String code = GPC.Encode(start[0], start[1], false);
            String prefix = code.substring(0, 5);
            Coordinates decoded = GPC.Decode(code);
            long[] previous = grid(decoded.Latitude, decoded.Longitude);
            int walked = 0;
            for (int step = 0; step < 4000; step++) {
                code = successor(code);
                if (!code.substring(0, 5).equals(prefix)) {
                    break;
                }
                Coordinates here = GPC.Decode(code);
                long[] current = grid(here.Latitude, here.Longitude);
                long distance = Math.abs(current[0] - previous[0]) + Math.abs(current[1] - previous[1]);
                final String at = code;
                assertEquals(1L, distance, () -> at + " is not adjacent to the code before it");
                previous = current;
                walked++;
            }
            assertTrue(walked > 100, "expected a substantial walk inside one cell");
        }
    }

    /**
     * The traversal of one level-5 cell ends at its far corner and the next
     * begins at its near corner, so the step between them is never adjacent.
     */
    @Test
    void makesEveryLevelFiveTransitionAJump() {
        int tested = 0;
        for (double latitude : new double[] {-80.0, -40.0, -5.0, 5.0, 40.0, 80.0}) {
            for (double longitude : new double[] {-170.0, -100.0, -20.0, 20.0, 100.0, 170.0}) {
                String prefix = GPC.Encode(latitude, longitude, false).substring(0, 5);
                String following = successor(prefix);
                if (following.charAt(0) == 'X') {
                    continue; // ran into the reserved namespace
                }
                Coordinates lastPoint = GPC.Decode(prefix + "XXXXX");
                Coordinates firstPoint = GPC.Decode(following + "00000");
                long[] last = grid(lastPoint.Latitude, lastPoint.Longitude);
                long[] first = grid(firstPoint.Latitude, firstPoint.Longitude);
                long distance = Math.abs(last[0] - first[0]) + Math.abs(last[1] - first[1]);
                assertNotEquals(1L, distance, prefix + " runs straight into the next cell");
                tested++;
            }
        }
        assertTrue(tested > 20, "expected a substantial number of transitions");
    }

    /*  Sections 12, 13 and 18, over the same wide sample.
     *
     *  The vector files pin these operations case by case. What follows pins
     *  that they hold everywhere -- including in the quadrants a case-by-case
     *  corpus might happen not to reach.  */

    @Test
    void agreesWithTheGridAboutContainment() {
        for (int i = 0; i < 4000; i++) {
            String code = codes[i];
            for (int k : new int[] {1, 3, 5, 7, 10}) {
                String cell = GPC.Cell(code, k);
                assertEquals(code.substring(0, k), cell);
                assertTrue(GPC.Contains(cell, code), cell + " should contain " + code);
                // And a cell the point is not in never claims it.
                assertFalse(GPC.Contains(GPC.Neighbours(cell).get(0), code));
            }
        }
    }

    @Test
    void putsEveryNeighbourOneCellAway() {
        for (int i = 0; i < 2000; i++) {
            String code = codes[i];
            for (int k : new int[] {1, 4, 7, 10}) {
                long p = (long)Math.pow(5, 10 - k);
                long rowCells = 4 * (long)Math.pow(5, k - 1);
                long colCells = 6 * (long)Math.pow(5, k - 1);
                String cell = GPC.Cell(code, k);
                long[] grid = GPC.DecodeToGrid(code);
                long cellRow = grid[0] / p;
                long cellCol = grid[1] / p;
                List<String> got = GPC.Neighbours(cell);

                // Five in a polar row, eight everywhere else. Rows do not wrap;
                // columns always do.
                assertEquals(cellRow > 0 && cellRow < rowCells - 1 ? 8 : 5, got.size(), cell);
                assertEquals(got.size(), new HashSet<>(got).size(), cell);
                assertFalse(got.contains(cell), cell);

                for (String neighbour : got) {
                    long[] other = GPC.CodeToGrid(neighbour + "0".repeat(10 - k));
                    long dCol = (other[1] / p - cellCol + colCells) % colCells;
                    if (dCol > colCells / 2) {
                        dCol -= colCells;
                    }
                    assertTrue(Math.abs(other[0] / p - cellRow) <= 1, neighbour);
                    assertTrue(Math.abs(dCol) <= 1, neighbour);
                }
            }
        }
    }

    @Test
    void recoversEveryShortFormInsideHalfALevelFiveCell() {
        // Half a level-5 cell on each axis: 1562 rows and 1562 columns.
        double halfLatitude = 1562 * 180.0 / 7812500.0;
        double halfLongitude = 1562 * 360.0 / 11718750.0;
        double[][] offsets = {{0.0, 0.0}, {halfLatitude, halfLongitude},
                              {-halfLatitude, -halfLongitude},
                              {halfLatitude, -halfLongitude},
                              {-halfLatitude, halfLongitude}};
        for (int i = 0; i < 4000; i++) {
            String code = codes[i];
            String shortForm = GPC.Shorten(code);
            for (double[] offset : offsets) {
                double latitude = latitudes[i] + offset[0];
                double longitude = longitudes[i] + offset[1];
                if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                    continue;
                }
                assertEquals(code, GPC.RecoverShort(shortForm, latitude, longitude, false));
            }
        }
    }

    @Test
    void roundTripsTheIntegerFormAndKeepsTheOrder() {
        long reservedFloor = 91_552_734_375_000L;
        String[] sorted = Arrays.copyOf(codes, 20_000);
        for (String code : sorted) {
            long value = GPC.ToInteger(code);
            assertEquals(code, GPC.FromInteger(value, false));
            // No encoded code reaches the reserved namespace, so no integer form
            // of one reaches the floor either.
            assertTrue(value < reservedFloor, code);
        }
        Arrays.sort(sorted);
        for (int i = 1; i < sorted.length; i++) {
            assertTrue(GPC.ToInteger(sorted[i - 1]) <= GPC.ToInteger(sorted[i]));
        }
    }

    @Test
    void measuresDistanceSymmetrically() {
        for (int i = 0; i < 2000; i += 2) {
            String a = codes[i];
            String b = codes[i + 1];
            assertEquals(GPC.Distance(a, b), GPC.Distance(b, a));
            assertEquals(0.0, GPC.Distance(a, a));
            assertEquals(a.equals(b), GPC.Distance(a, b) == 0.0, a + " " + b);
        }
    }

    /**
     * Decode returns a cell centre, which sits far enough from the nearest
     * boundary that neither rounding can push it into the next cell.
     */
    @Test
    void letsACodeSurviveBothCoordinateConversions() {
        for (int i = 0; i < 5000; i++) {
            String code = codes[i];
            Coordinates centre = GPC.Decode(code);
            Coordinates viaUri = GPC.FromGeoURI(GPC.ToGeoURI(centre.Latitude, centre.Longitude));
            assertEquals(code, GPC.Encode(viaUri.Latitude, viaUri.Longitude, false));
            Coordinates viaDms = GPC.FromDMS(GPC.ToDMS(centre.Latitude, centre.Longitude));
            assertEquals(code, GPC.Encode(viaDms.Latitude, viaDms.Longitude, false));
        }
    }

    @Test
    void neverLetsScreeningChangeWhatACodeDoes() {
        for (int i = 0; i < 5000; i++) {
            String code = codes[i];
            assertNotEquals("", GPC.Screen(code).Version);
            assertTrue(GPC.IsValid(code), code);
        }
    }
}
