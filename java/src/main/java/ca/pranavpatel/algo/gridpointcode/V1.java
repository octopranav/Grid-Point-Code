package ca.pranavpatel.algo.gridpointcode;

import java.util.Locale;

/**
 * Version 1 decoding, kept so that every code ever issued still resolves.
 *
 * <p>Version 1 is a base-27 numeral over a different alphabet, eleven characters
 * long, and it carries no locality guarantee: two codes sharing four characters
 * can be nineteen thousand kilometres apart. It is frozen. There is no version 1
 * encoder here and there will not be one -- the format is readable, not
 * writable, and anyone who still needs to mint version 1 codes pins 1.1.x.
 *
 * <p>Nothing in this class is reached by a version 2 code. It is entered only
 * when {@link ca.pranavpatel.algo.gridpointcode.GPC#Decode(String)} sees eleven
 * characters, or when a caller asks for
 * {@link ca.pranavpatel.algo.gridpointcode.GPC#DecodeV1(String)} outright.
 * Appendix B of SPEC.md describes the format.
 */
final class V1 {
    /** base27, letters first, so it is not in ASCII order. */
    static final String CHARACTERS = "CDFGHJKLMNPRTVWXY0123456789";

    /** Every version 1 code is this many characters. */
    static final int CODE_LENGTH = 11;

    private static final long MIN_POINT = 10_000_000_000L;
    private static final long MAX_POINT = 648_009_999_999_999L;

    /** The offset that makes every code exactly eleven characters. */
    private static final long ELEVEN = 205_881_132_094_649L;

    private static final Table LatLongTable = new Table(180, 360, true);

    private V1() {
        throw new IllegalStateException("V1 class");
    }

    /**
     * Upper-case and drop the separators. Version 1 has no alias table.
     *
     * @param gridPointCode formatted or unformatted code.
     * @return the bare characters.
     */
    static String Clean(String gridPointCode) {
        return gridPointCode.replace(" ", "").replace("-", "")
            .replace("#", "").trim().toUpperCase(Locale.ENGLISH);
    }

    /**
     * The version 1 presentation, {@code #XXXX-XXXX-XXX}.
     *
     * @param code unformatted eleven-character code.
     * @return the formatted code.
     */
    static String Format(String code) {
        return "#" + code.substring(0, 4) + "-" + code.substring(4, 8)
            + "-" + code.substring(8, 11);
    }

    /**
     * Decodes an eleven-character version 1 code to its cell's corner.
     *
     * <p>Version 1 returns the corner, not the centre. That differs from version
     * 2 by design: the value is the one every version 1 release has returned, and
     * changing it would move every code ever issued.
     *
     * @param gridPointCode formatted or unformatted code.
     * @return coordinates in decimal degrees.
     */
    static Coordinates Decode(String gridPointCode) {
        if (gridPointCode == null || gridPointCode.isBlank()) {
            throw new GPCException("GPC_NULL");
        }

        String code = Clean(gridPointCode);

        Validation gpc = ValidateCode(code);
        if (!gpc.IsValid) {
            throw new GPCException(gpc.Message);
        }

        long point = ToPoint(code) - ELEVEN;

        gpc = ValidatePoint(point);
        if (!gpc.IsValid) {
            throw new GPCException(gpc.Message);
        }

        return ToCoordinates(point);
    }

    /**
     * Whether a string is a version 1 code, and why not when it is not.
     *
     * @param gridPointCode formatted or unformatted code.
     * @return validity status with the reason code if any.
     */
    static Validation IsValid(String gridPointCode) {
        if (gridPointCode == null) {
            return new Validation(false, "GPC_NULL");
        }
        String code = Clean(gridPointCode);
        if (code.isBlank()) {
            return new Validation(false, "GPC_NULL");
        }
        Validation gpc = ValidateCode(code);
        if (!gpc.IsValid) {
            return gpc;
        }
        return ValidatePoint(ToPoint(code) - ELEVEN);
    }

    /**
     * Length and alphabet, on an already cleaned code.
     *
     * @param code cleaned code.
     * @return validity status with the reason code if any.
     */
    private static Validation ValidateCode(String code) {
        if (code.length() != CODE_LENGTH) {
            return new Validation(false, "GPC_LENGTH");
        }
        for (char character : code.toCharArray()) {
            if (CHARACTERS.indexOf(character) < 0) {
                return new Validation(false, "GPC_CHAR");
            }
        }
        return new Validation(true, "");
    }

    /**
     * Whether a decoded point falls inside the version 1 grid.
     *
     * @param point point number.
     * @return validity status with the reason code if any.
     */
    private static Validation ValidatePoint(long point) {
        if (point < MIN_POINT || point > MAX_POINT) {
            return new Validation(false, "GPC_RANGE");
        }
        return new Validation(true, "");
    }

    /**
     * The base-27 value of an eleven-character code.
     *
     * @param code cleaned, validated code.
     * @return point number.
     */
    private static long ToPoint(String code) {
        long point = 0;
        for (int i = 0; i < CODE_LENGTH; i++) {
            point *= 27;
            point += (long)CHARACTERS.indexOf(code.charAt(i));
        }
        return point;
    }

    /**
     * Splits a point back into latitude and longitude.
     *
     * @param point valid point number.
     * @return coordinates in decimal degrees.
     */
    private static Coordinates ToCoordinates(long point) {
        // Seperating whole-number and fractional parts
        int LatLongIndex = (int)Truncate(point / Math.pow(10, 10));
        long Fractional = (long)(point - (LatLongIndex * Math.pow(10, 10)));
        // Spliting into 7
        int[][] seven = SplitTo7(LatLongIndex, Fractional);
        int[] Lat7 = seven[0];
        int[] Long7 = seven[1];
        // Constructing coordinates
        int Power = 0;
        int TempLat = 0;
        int TempLong = 0;
        for (int x = 6; x >= 1; x--) {
            TempLat += (int)(Lat7[x] * Math.pow(10, Power));
            TempLong += (int)(Long7[x] * Math.pow(10, Power++));
        }
        double Lat = TempLat / Math.pow(10, 5) * Lat7[0];
        double Long = TempLong / Math.pow(10, 5) * Long7[0];
        return new Coordinates(Lat, Long);
    }

    /**
     * Sign, whole degrees and five decimals, for each axis.
     *
     * <p>The whole degrees come back through the combination table, which pairs
     * an index with two doubled-and-offset whole values. The ten decimal digits
     * of the fractional part alternate, latitude first.
     *
     * @param latLongIndex latitude and longitude pair index from Table.
     * @param fractional fractional part of the coordinates.
     * @return integer arrays of coordinates.
     */
    private static int[][] SplitTo7(int latLongIndex, long fractional) {
        int[] Lat7 = new int[7];
        int[] Long7 = new int[7];
        // TLat, TLong - Assigned positive values in Table
        Pair TLatLong = LatLongTable.GetElementsAtIndex((long)latLongIndex - 1);
        int TLat = (int)TLatLong.ai;
        int TLong = (int)TLatLong.bi;
        // Getting sign and whole-number parts
        Lat7[0] = TLat % 2 != 0 ? -1 : 1;
        Lat7[1] = Lat7[0] == -1 ? --TLat / 2 : TLat / 2;
        Long7[0] = TLong % 2 != 0 ? -1 : 1;
        Long7[1] = Long7[0] == -1 ? --TLong / 2 : TLong / 2;
        // Getting fractional parts
        int Power = 9;
        for (int x = 2; x <= 6; x++) {
            Lat7[x] = (int)(((long)Truncate(fractional / Math.pow(10, Power--))) % 10);
            Long7[x] = (int)(((long)Truncate(fractional / Math.pow(10, Power--))) % 10);
        }
        return new int[][] {Lat7, Long7};
    }

    /**
     * Truncate value.
     *
     * @param value the value.
     * @return truncated double value.
     */
    private static double Truncate(double value) {
        return value < 0 ? Math.ceil(value) : Math.floor(value);
    }
}
