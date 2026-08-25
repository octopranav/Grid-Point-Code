package ca.pranavpatel.algo.gridpointcode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Properties that hold for every point, checked over a wide generated sample.
 *
 * <p>The files in test_data/ pin behaviour case by case. This class pins the
 * rules that must hold everywhere: a code is always the same length, always
 * spelled from the alphabet, always valid, and always decodes back inside the
 * cell it came from.
 *
 * <p>The sample behind them is a hundred thousand coordinates that are
 * generated rather than stored, so the same inputs reach every port without a
 * large file in the repository. Its definition lives in test_data/README.md;
 * the digest of the codes it produces lives in test_data/sample.csv, which is
 * what makes this class a cross-port check as well as a local one.
 */
class PropertiesTest {

    /**
     * The specified alphabet, written out rather than read from the
     * implementation: a test that borrows the constant it is checking proves
     * nothing.
     */
    private static final String ALPHABET = "CDFGHJKLMNPRTVWXY0123456789";

    private static final int CODE_LENGTH = 11;
    private static final int FORMATTED_LENGTH = 14;

    /** One cell is a hundred-thousandth of a degree on each axis. */
    private static final double CELL = 1e-5;

    // Generator constants. Kept beside the code that uses them so this file
    // reads as a standalone statement of the sample, the same way every other
    // port does.
    private static final long MULTIPLIER = 1_664_525L;
    private static final long INCREMENT = 1_013_904_223L;
    private static final long MODULUS = 4_294_967_296L; // 2^32
    private static final long LATITUDE_SPAN = 17_999_999L;
    private static final long LONGITUDE_SPAN = 35_999_999L;

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
        Path file = testDataDir().resolve("sample.csv");
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

    @Test
    void validatesEveryCodeItProduced() {
        for (String code : codes) {
            Validation validation = GPC.IsValid(code);
            assertTrue(validation.IsValid,
                    () -> code + " came out of Encode but failed validation: " + validation.Message);
        }
    }

    @Test
    void decodesBackInsideTheCellThePointCameFrom() {
        for (int i = 0; i < count; i++) {
            Coordinates decoded = GPC.Decode(codes[i]);
            final int at = i;
            assertTrue(Math.abs(latitudes[i] - decoded.Latitude) < CELL
                    && Math.abs(longitudes[i] - decoded.Longitude) < CELL,
                    () -> codes[at] + " decoded to (" + decoded.Latitude + ", " + decoded.Longitude
                            + "), more than one cell from (" + latitudes[at] + ", " + longitudes[at] + ")");
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
            assertEquals("#" + code.substring(0, 4) + "-" + code.substring(4, 8)
                    + "-" + code.substring(8, 11), formatted);
        }
    }
}
