package ninja.pranav.algorithms.gridpointcode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for GPC class
 *
 * @author pranav.ninja
 * @version $Id: $Id
 * @since 0.0.1
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
}
