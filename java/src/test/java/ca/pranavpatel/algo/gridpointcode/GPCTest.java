package ca.pranavpatel.algo.gridpointcode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for the GPC (Grid Point Code) encoding and decoding functionality.
 * Validates that GPC can be correctly encoded from geographic coordinates
 * and decoded back to the original coordinates.
 */
public class GPCTest {

        @ParameterizedTest
        @CsvSource({
            "'#DCCC-CCCC-CCC', 0, 0",
            "'#DCCC-CCCC-CCR', 0.00001, 0.00001",
            "'#DCCD-7Y5W-LLH', -0.00001, 0.00001",
            "'#DCCC-8473-0G4', 0.00001, -0.00001",
            "'#DCCG-5K1D-WV7', -0.00001, -0.00001",
            "'#HG9K-PCVH-DPV', 89.99999, 179.99999",
            "'#HG9N-KTKR-83Y', -89.99999, 179.99999",
            "'#HG9M-L0M1-M0K', 89.99999, -179.99999",
            "'#HG9P-JLHJ-X69', -89.99999, -179.99999"
        })
        void testMin(String gpc, double latitude, double longitude) {
            Coordinates latLong = new Coordinates(latitude, longitude);
            String actGPC = GPC.Encode(latitude, longitude);
            assertEquals(gpc, actGPC);
            Coordinates actCoord = GPC.Decode(gpc);
            assertEquals(latLong, actCoord);
        }

        @Test
        void testTruncate() {
            String gpc = "#FYGC-MF89-XH2";
            Coordinates latLong = new Coordinates(-12.12345, -123.12345);
            String actGPC = GPC.Encode(-12.1234567, -123.1234567);
            assertEquals(gpc, actGPC);
            Coordinates actCoord = GPC.Decode(gpc);
            assertEquals(latLong, actCoord);
        }

        /**
         * <p>testLATITUDE.</p>
         *
         * @param error a {@link java.lang.String} object.
         * @param latitude a double.
         * @param longitude a double.
         */
        @ParameterizedTest
        @CsvSource({
            "'LATITUDE', -90, -123",
            "'LATITUDE', 90, 123",
            "'LONGITUDE', -12, -180",
            "'LONGITUDE', 12, 180"
        })
        public void testLATITUDE(String error, double latitude, double longitude) {
            Throwable ex = assertThrows(IllegalArgumentException.class, () -> GPC.Encode(latitude, longitude));
            assertEquals(error + ": value out of valid range.", ex.getMessage());
        }

        /**
         * <p>testGPCFormatted.</p>
         */
        @Test
        public void testGPCFormatted() {
            String gpc = "#HG9P-JLHJ-X69";
            double latitude = -89.99999;
            double longitude = -179.99999;
            Coordinates latLong = new Coordinates(latitude, longitude);
            String actGPC = GPC.Encode(latitude, longitude, true);
            assertEquals(gpc, actGPC);
            Coordinates actCoord = GPC.Decode(gpc);
            assertEquals(latLong, actCoord);
        }

        /**
         * <p>testGPCUnformatted.</p>
         */
        @Test
        public void testGPCUnformatted() {
            String gpc = "HG9PJLHJX69";
            double latitude = -89.99999;
            double longitude = -179.99999;
            Coordinates latLong = new Coordinates(latitude, longitude);
            String actGPC = GPC.Encode(latitude, longitude, false);
            assertEquals(gpc, actGPC);
            Coordinates actCoord = GPC.Decode(gpc);
            assertEquals(latLong, actCoord);
        }

        /**
         * <p>testGPCBlank.</p>
         *
         * @param gridPointCode a {@link java.lang.String} object.
         */
        @ParameterizedTest
        @NullSource
        @ValueSource(strings = { "", "     " })
        public void testGPCBlank(String gridPointCode) {
            Throwable ex = assertThrows(IllegalArgumentException.class, () -> GPC.Decode(gridPointCode));
            assertEquals("GPC_NULL: Invalid GPC.", ex.getMessage());
        }

        /**
         * <p>testGPCLength.</p>
         *
         * @param gridPointCode a {@link java.lang.String} object.
         */
        @ParameterizedTest
        @ValueSource(strings = {"#HG9P-JLHJ-X696", "#HG9P-JLHJ-X6"})
        public void testGPCLength(String gridPointCode) {
            Throwable ex = assertThrows(IllegalArgumentException.class, () -> GPC.Decode(gridPointCode));
            assertEquals("GPC_LENGTH: Invalid GPC.", ex.getMessage());
        }

        /**
         * <p>testGPCChar.</p>
         *
         * @param gridPointCode a {@link java.lang.String} object.
         */
        @ParameterizedTest
        @ValueSource(strings = { "#HG9P-JLHJ-A69", "#HG9P-JLHJ-E69"})
        public void testGPCChar(String gridPointCode) {
            Throwable ex = assertThrows(IllegalArgumentException.class, () -> GPC.Decode(gridPointCode));
            assertEquals("GPC_CHAR: Invalid GPC.", ex.getMessage());
        }

        /**
         * <p>testGPCRange.</p>
         *
         * @param gridPointCode a {@link java.lang.String} object.
         */
        @ParameterizedTest
        @ValueSource(strings = {"#HG9P-JLHJ-X7C", "#JG9P-JLHJ-X7C"})
        public void testGPCRange(String gridPointCode) {
            Throwable ex = assertThrows(IllegalArgumentException.class, () -> GPC.Decode(gridPointCode));
            assertEquals("GPC_RANGE: Invalid GPC.", ex.getMessage());
        }

        /**
         * <p>testNearLimitClamped.</p>
         *
         * @param gridPointCode a {@link java.lang.String} object.
         * @param latitude a double.
         * @param longitude a double.
         * @param expectedLatitude a double.
         * @param expectedLongitude a double.
         */
        @ParameterizedTest
        @CsvSource({
            "'#D4GP-770H-J19', 89.9999999999999, 0, 89.99999, 0",
            "'#GNPK-GGM8-DK1', 0, 179.9999999999999, 0, 179.99999",
            "'#HG9P-JLHJ-X69', -89.9999999999999, -179.9999999999999, -89.99999, -179.99999"
        })
        public void testNearLimitClamped(String gridPointCode, double latitude, double longitude,
                double expectedLatitude, double expectedLongitude) {
            assertEquals(gridPointCode, GPC.Encode(latitude, longitude));
            assertEquals(new Coordinates(expectedLatitude, expectedLongitude), GPC.Decode(gridPointCode));
        }

        /**
         * <p>testNegativeZero.</p>
         */
        @Test
        public void testNegativeZero() {
            assertEquals(GPC.Encode(0.0, 0.0), GPC.Encode(-0.0, -0.0));
            assertEquals("#DCCC-CCCC-CCC", GPC.Encode(-0.0, -0.0));
        }

        /**
         * <p>testGPCBelowRange.</p>
         */
        @Test
        public void testGPCBelowRange() {
            Validation result = GPC.IsValid("CCCC-CCCC-CCC");
            assertFalse(result.IsValid);
            assertEquals("GPC_RANGE", result.Message);
            Throwable ex = assertThrows(IllegalArgumentException.class, () -> GPC.Decode("CCCC-CCCC-CCC"));
            assertEquals("GPC_RANGE: Invalid GPC.", ex.getMessage());
        }

        /**
         * <p>testIsValidAcceptsFormattedCode.</p>
         */
        @Test
        public void testIsValidAcceptsFormattedCode() {
            Validation result = GPC.IsValid("#FN5G-CDKL-HDC");
            assertTrue(result.IsValid);
            assertEquals("", result.Message);
        }

        /**
         * <p>testShortestDecimalPinned.</p>
         *
         * @param gridPointCode a {@link java.lang.String} object.
         * @param latitude a double.
         * @param longitude a double.
         */
        @ParameterizedTest
        @CsvSource({
            "'#DCCT-RW78-KY4', 1.999999999999999, 1.999999999999999",
            "'#GH1J-5VH9-1WL', 6.999999999999987, 163.39847676453326"
        })
        public void testShortestDecimalPinned(String gridPointCode, double latitude, double longitude) {
            assertEquals(gridPointCode, GPC.Encode(latitude, longitude));
        }
}
