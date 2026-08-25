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
 * a release. The v2_ files hold version 2; the rest are version 1, and are
 * asserted by decoding, because no package encodes version 1 any more.
 */
class VectorsTest {

    /** One cell of the version 1 grid: a hundred-thousandth of a degree. */
    private static final double V1_CELL = 1e-5;

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

    /** Rebuild the formatted #XXXX-XXXX-XXX form of an unformatted version 1 code. */
    private static String formattedV1(String code) {
        return "#" + code.substring(0, 4) + "-" + code.substring(4, 8) + "-" + code.substring(8, 11);
    }

    /*  Version 2  */

    @Test
    void encodesEveryVectorToTheExpectedCode() {
        List<String[]> data = rows("v2_encoding.csv", 3);
        assertTrue(data.size() > 100, "expected a substantial corpus");
        for (String[] r : data) {
            assertEquals(r[2], GPC.Encode(Double.parseDouble(r[0]), Double.parseDouble(r[1]), false),
                    r[0] + "," + r[1]);
        }
    }

    @Test
    void decodesEveryVectorToTheExpectedCoordinates() {
        List<String[]> data = rows("v2_decoding.csv", 3);
        assertTrue(data.size() > 100, "expected a substantial corpus");
        for (String[] r : data) {
            assertEquals(new Coordinates(Double.parseDouble(r[1]), Double.parseDouble(r[2])),
                    GPC.Decode(r[0]), r[0]);
        }
    }

    @Test
    void decodesTheFormattedAndUnformattedFormsAlike() {
        for (String[] r : rows("v2_decoding.csv", 3)) {
            assertEquals(new Coordinates(Double.parseDouble(r[1]), Double.parseDouble(r[2])),
                    GPC.Decode(GPC.FormatGPC(r[0])), r[0]);
        }
    }

    @Test
    void roundTripsEveryEncodedCodeBackToItself() {
        for (String[] r : rows("v2_encoding.csv", 3)) {
            Coordinates decoded = GPC.Decode(r[2]);
            assertEquals(r[2], GPC.Encode(decoded.Latitude, decoded.Longitude, false), r[2]);
        }
    }

    @Test
    void returnsTheExpectedCellBoundaries() {
        List<String[]> data = rows("v2_area.csv", 5);
        assertTrue(data.size() > 100, "expected a substantial corpus");
        for (String[] r : data) {
            assertEquals(new Area(Double.parseDouble(r[1]), Double.parseDouble(r[2]),
                            Double.parseDouble(r[3]), Double.parseDouble(r[4])),
                    GPC.DecodeToArea(r[0]), r[0]);
        }
    }

    @Test
    void agreesOnClassification() {
        List<String[]> data = rows("v2_classify.csv", 3);
        assertTrue(data.size() > 10, "expected a classification corpus");
        for (String[] r : data) {
            CodeClass expected = CodeClass.valueOf(r[0]);
            assertEquals(new Classification(expected, r[1]), GPC.Validate(r[2]), r[2]);
            assertEquals(expected, GPC.Classify(r[2]), r[2]);
            assertEquals(expected == CodeClass.GEOMETRIC, GPC.IsValid(r[2]), r[2]);
        }
    }

    @Test
    void throwsOnAnythingThatIsNotGeometric() {
        for (String[] r : rows("v2_classify.csv", 3)) {
            if (CodeClass.valueOf(r[0]) == CodeClass.GEOMETRIC) {
                continue;
            }
            // Eleven characters is version 1 by definition, so Decode reads it
            // rather than refusing it. Classify describes the version 2 grid,
            // which this string is not part of.
            if (GPC.IsValidV1(r[2]).IsValid) {
                continue;
            }
            assertThrows(GPCException.class, () -> GPC.Decode(r[2]), r[2]);
        }
    }

    @Test
    void givesAReservedCodeItsOwnReason() {
        int seen = 0;
        for (String[] r : rows("v2_classify.csv", 3)) {
            if (CodeClass.valueOf(r[0]) != CodeClass.RESERVED) {
                continue;
            }
            seen++;
            GPCException error = assertThrows(GPCException.class, () -> GPC.Decode(r[2]), r[2]);
            assertEquals("GPC_RESERVED", error.getReason());
        }
        assertTrue(seen > 0, "expected at least one reserved code");
    }

    @Test
    void computesTheExpectedCheckCharacter() {
        List<String[]> data = rows("v2_check.csv", 2);
        assertTrue(data.size() > 10, "expected a check corpus");
        for (String[] r : data) {
            assertEquals(r[1], GPC.CheckCharacter(r[0]), r[0]);
            assertEquals(GPC.Classify(r[0]), GPC.Classify(r[0] + "*" + r[1]), r[0]);
        }
    }

    /*  Version 1  */

    @Test
    void decodesEveryVersion1VectorToTheExpectedCoordinates() {
        List<String[]> data = rows("decoding.csv", 3);
        assertTrue(data.size() > 100, "expected a substantial corpus");
        for (String[] r : data) {
            assertEquals(new Coordinates(Double.parseDouble(r[1]), Double.parseDouble(r[2])),
                    GPC.DecodeV1(r[0]), r[0]);
        }
    }

    @Test
    void decodesTheFormattedAndUnformattedVersion1FormsAlike() {
        for (String[] r : rows("decoding.csv", 3)) {
            assertEquals(new Coordinates(Double.parseDouble(r[1]), Double.parseDouble(r[2])),
                    GPC.DecodeV1(formattedV1(r[0])), r[0]);
        }
    }

    /**
     * encoding.csv was built by the version 1 encoder, which no longer ships.
     * What survives is the containment: the code names the cell the coordinate
     * falls in, so decoding lands within one cell of it.
     */
    @Test
    void decodesEveryVersion1CodeInsideTheCellItWasMadeFrom() {
        List<String[]> data = rows("encoding.csv", 3);
        assertTrue(data.size() > 100, "expected a substantial corpus");
        for (String[] r : data) {
            Coordinates decoded = GPC.DecodeV1(r[2]);
            assertTrue(Math.abs(Double.parseDouble(r[0]) - decoded.Latitude) < V1_CELL, r[2]);
            assertTrue(Math.abs(Double.parseDouble(r[1]) - decoded.Longitude) < V1_CELL, r[2]);
        }
    }

    @Test
    void agreesOnVersion1CodeValidity() {
        List<String[]> data = rows("validity_codes.csv", 3);
        assertTrue(data.size() > 10, "expected a validity corpus");
        for (String[] r : data) {
            Validation validation = GPC.IsValidV1(r[2]);
            assertEquals(r[0].equals("true"), validation.IsValid, r[2]);
            assertEquals(r[1], validation.Message, r[2]);
        }
    }

    @Test
    void throwsWhenDecodingAnInvalidVersion1Code() {
        for (String[] r : rows("validity_codes.csv", 3)) {
            if (r[0].equals("true")) {
                continue;
            }
            assertThrows(GPCException.class, () -> GPC.DecodeV1(r[2]), r[2]);
        }
    }
}
