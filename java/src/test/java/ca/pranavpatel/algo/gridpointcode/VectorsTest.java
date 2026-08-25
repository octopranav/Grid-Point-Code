package ca.pranavpatel.algo.gridpointcode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Runs the shared conformance vectors in test_data/. Every port reads these
 * same files, so a disagreement between languages shows up here rather than in
 * a release.
 */
class VectorsTest {

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
     * Read one vector file, dropping comments and blank lines. Splits on the
     * first {@code fields - 1} commas so the final column keeps any comma,
     * '#' or spacing it contains.
     */
    private static List<String[]> rows(String name, int fields) {
        List<String[]> out = new ArrayList<>();
        try {
            for (String raw : Files.readAllLines(testDataDir().resolve(name), StandardCharsets.UTF_8)) {
                String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }
                out.add(line.split(",", fields));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    /** Rebuild the formatted #XXXX-XXXX-XXX form of an unformatted code. */
    private static String formatted(String code) {
        return "#" + code.substring(0, 4) + "-" + code.substring(4, 8) + "-" + code.substring(8, 11);
    }

    @Test
    void encodesEveryVectorToTheExpectedCode() {
        List<String[]> data = rows("encoding.csv", 3);
        assertTrue(data.size() > 100, "expected a substantial corpus");
        for (String[] r : data) {
            assertEquals(r[2], GPC.Encode(Double.parseDouble(r[0]), Double.parseDouble(r[1]), false),
                    "encode(" + r[0] + ", " + r[1] + ")");
        }
    }

    @Test
    void decodesEveryVectorToTheExpectedCoordinates() {
        List<String[]> data = rows("decoding.csv", 3);
        assertTrue(data.size() > 100, "expected a substantial corpus");
        for (String[] r : data) {
            Coordinates got = GPC.Decode(r[0]);
            assertEquals(Double.parseDouble(r[1]), got.Latitude, "decode(" + r[0] + ") latitude");
            assertEquals(Double.parseDouble(r[2]), got.Longitude, "decode(" + r[0] + ") longitude");
        }
    }

    @Test
    void decodesTheFormattedAndUnformattedFormsAlike() {
        for (String[] r : rows("decoding.csv", 3)) {
            Coordinates got = GPC.Decode(formatted(r[0]));
            assertEquals(Double.parseDouble(r[1]), got.Latitude, "decode(" + formatted(r[0]) + ")");
            assertEquals(Double.parseDouble(r[2]), got.Longitude, "decode(" + formatted(r[0]) + ")");
        }
    }

    @Test
    void roundTripsEveryEncodedCodeBackToItself() {
        for (String[] r : rows("encoding.csv", 3)) {
            Coordinates got = GPC.Decode(r[2]);
            assertEquals(r[2], GPC.Encode(got.Latitude, got.Longitude, false), "round trip " + r[2]);
        }
    }

    @Test
    void agreesOnCodeValidity() {
        List<String[]> data = rows("validity_codes.csv", 3);
        assertTrue(data.size() > 10, "expected a validity corpus");
        for (String[] r : data) {
            Validation v = GPC.IsValid(r[2]);
            assertEquals("true".equals(r[0]), v.IsValid, "isValid(" + r[2] + ")");
            assertEquals(r[1], v.Message, "isValid(" + r[2] + ") message");
        }
    }

    @Test
    void throwsWhenDecodingAnInvalidCode() {
        for (String[] r : rows("validity_codes.csv", 3)) {
            if ("true".equals(r[0])) {
                continue;
            }
            assertThrows(IllegalArgumentException.class, () -> GPC.Decode(r[2]),
                    "decode(" + r[2] + ")");
        }
    }

    @Test
    void agreesOnCoordinateValidity() {
        List<String[]> data = rows("validity_coordinates.csv", 4);
        assertTrue(data.size() > 10, "expected a validity corpus");
        for (String[] r : data) {
            Validation v = GPC.IsValid(Double.parseDouble(r[0]), Double.parseDouble(r[1]));
            assertEquals("true".equals(r[2]), v.IsValid, "isValid(" + r[0] + ", " + r[1] + ")");
            assertEquals(r[3], v.Message, "isValid(" + r[0] + ", " + r[1] + ") message");
        }
    }

    @Test
    void throwsWhenEncodingAnOutOfRangeCoordinate() {
        for (String[] r : rows("validity_coordinates.csv", 4)) {
            if ("true".equals(r[2])) {
                continue;
            }
            assertThrows(IllegalArgumentException.class,
                    () -> GPC.Encode(Double.parseDouble(r[0]), Double.parseDouble(r[1])),
                    "encode(" + r[0] + ", " + r[1] + ")");
        }
    }
}
