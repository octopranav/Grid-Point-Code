package ninja.pranav.algorithms.gridpointcode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

import ninja.pranav.algorithms.kombin.Table;
import ninja.pranav.algorithms.kombin.Pair;

/**
 * <p>GPC class.</p>
 *
 * @author pranav.ninja
 * @version $Id: $Id
 */
public final class GPC {
    private static final double MIN_LAT = -90;
    private static final double MAX_LAT = 90;
    private static final double MIN_LONG = -180;
    private static final double MAX_LONG = 180;
    // MIN_POINT = 10_000_000_000
    private static final long MAX_POINT = 648_009_999_999_999L;
    // For Uniformity
    private static final long ELEVEN = 205_881_132_094_649L;
    private static final String CHARACTERS = "CDFGHJKLMNPRTVWXY0123456789"; // base27
    private static final int GPC_LENGTH = 11;
    private static final char PREFIX = 35;
    private static final char SEPERATOR = 45;
    private static final Table LatLongTable = new Table(180, 360, true);
    
    private GPC() {
        throw new IllegalStateException("GPC class");
    }
    /**
     * Truncate value
     * @param value
     * @return truncated double value
     */
    private static double Truncate(double value) {
        if(value < 0) {
            return Math.ceil(value);
        }
        else {
            return Math.floor(value);
        }
    }

    /**
     * 
     */
    private static BigDecimal Truncate(BigDecimal value) {
        if(value.compareTo(BigDecimal.ZERO) < 0) {
            return value.setScale(0, RoundingMode.CEILING);
        }
        else {
            return value.setScale(0, RoundingMode.FLOOR);
        }
    }

    /*  PART 1 : ENCODE */

    /**
     * Encode coordinates
     *
     * @param latitude Latitude in Decimal Degrees
     * @param longitude Longitude in Decimal Degrees
     * @return Grid Point Code
     * @throws java.lang.IllegalArgumentException if latitude or longitude is invalid.
     */
    public static String Encode(double latitude, double longitude) {
        return Encode(latitude, longitude, true);
    }

    /**
     * Encode coordinates
     *
     * @param latitude Latitude in Decimal Degrees
     * @param longitude Longitude in Decimal Degrees
     * @param formatted True if GPC needs to be formatted otherwise false
     * @return Grid Point Code
     * @throws java.lang.IllegalArgumentException if latitude or longitude is invalid.
     */
    public static String Encode(double latitude, double longitude, Boolean formatted) {
        /*  Validating Latitude and Longitude values */
        Validation LatLong = IsValid(latitude, longitude);
        if (!LatLong.IsValid) {
            throw new IllegalArgumentException(LatLong.Message + ": value out of valid range.");
        }
        /*  Getting a Point Number  */
        long Point = GetPoint(latitude, longitude);
        /*  Encode Point    */
        String GridPointCode = EncodePoint(Point + ELEVEN);
        /*  Format GridPointCode   */
        if (Boolean.TRUE.equals(formatted)) {
            GridPointCode = FormatGPC(GridPointCode);
        }
        return GridPointCode;
    }

    /**
     * Check if coordinates are valid
     *
     * @param latitude Latitude in Decimal Degrees
     * @param longitude Longitude in Decimal Degrees
     * @return {@link ninja.pranav.algorithms.gridpointcode.Validation} object containing status and message if any.
     */
    public static Validation IsValid(double latitude, double longitude) {
        if (latitude <= MIN_LAT || latitude >= MAX_LAT) {
            return new Validation(false, "LATITUDE");
        }
        if (longitude <= MIN_LONG || longitude >= MAX_LONG) {
            return new Validation(false, "LONGITUDE");
        }
        return new Validation(true, "");
    }

    /**
     * Get Point from Coordinates
     * @param latitude Latitude from Geographic coordinate
     * @param longitude Longitude from Geographic coordinate
     * @return Point
     */
    private static long GetPoint(double latitude, double longitude) {
        int[] Lat7 = SplitTo7(latitude);
        int[] Long7 = SplitTo7(longitude);
        // Whole-Number Part
        long Point = (long)(Math.pow(10, 10) *
            (LatLongTable.GetIndexOfElements(
                (Lat7[1] * 2) + (long)(Lat7[0] == -1 ? 1 : 0),
                (Long7[1] * 2) + (long)(Long7[0] == -1 ? 1 : 0)
                ) + 1
            )
        );
        // Fractional Part
        int Power = 9;
        for (int index = 2; index <= 6; index++) {
            Point += (long)(Math.pow(10, Power--) * Lat7[index]);
            Point += (long)(Math.pow(10, Power--) * Long7[index]);
        }
        return Point;
    }

    /**
     * Split Coordinate into 7 parts
     * @param coordinate Latitude or Longitude in Decimal Degrees
     * @return Integer array of coordinate
     */
    private static int[] SplitTo7(double coordinate) {
        int[] Coord = new int[7];
        // Sign
        Coord[0] = coordinate < 0 ? -1 : 1;
        // Whole-Number
        Coord[1] = (int)Truncate(Math.abs(coordinate));
        // Fractional
        BigDecimal AbsCoordinate = BigDecimal.valueOf(Math.abs(coordinate));
        BigDecimal Fractional = AbsCoordinate.subtract(Truncate(AbsCoordinate));
        BigDecimal Power10;
        for (int x = 1; x <= 5; x++) {
            Power10 = Fractional.multiply(new BigDecimal(10));
            BigDecimal temp = Truncate(Power10);
            Coord[x + 1] = temp.intValue();
            Fractional = Power10.subtract(temp);
        }
        return Coord;
    }

    /**
     * Encode Point to GPC
     * @param point Point number
     * @return Grid Point Code
     */
    private static String EncodePoint(long point) {
        StringBuilder gpc = new StringBuilder();
        while (point > 0) {
            gpc.append(CHARACTERS.charAt((int)(point % 27)));
            point /= 27;
        }
        return gpc.reverse().toString();
    }

    /**
     * Format GPC
     * @param gridPointCode Unformatted GPC
     * @return Formatted GPC
     */
    private static String FormatGPC(String gridPointCode) {
        StringBuilder gpc = new StringBuilder();
        gpc.append(PREFIX).append(gridPointCode.substring(0, 4))
        .append(SEPERATOR).append(gridPointCode.substring(4, 8))
        .append(SEPERATOR).append(gridPointCode.substring(8, 11));
        return gpc.toString();
    }

    /*  PART 2 : DECODE */

    /**
     * Decode Grid Point Code to Coordinates
     *
     * @param gridPointCode Grid Point Code
     * @return Latitude and Longitude in Decimal Degrees
     * @throws java.lang.IllegalArgumentException if { @param gridPointCode } is NULL, blank, whitespaces or invalid.
     */
    public static Coordinates Decode(String gridPointCode) {
        if (gridPointCode == null || gridPointCode.isBlank()) {
            throw new IllegalArgumentException("GPC_NULL: Invalid GPC.");
        }
        /*  Removing Format */
        gridPointCode = gridPointCode.replace(" ", "").replace("-", "")
            .replace("#", "").trim().toUpperCase(Locale.ENGLISH);
        /*  Validating GPC  */
        Validation gpc = Validate(gridPointCode);
        if (!gpc.IsValid) {
            throw new IllegalArgumentException(gpc.Message + ": Invalid GPC.");
        }
        /*  Getting a Point Number  */
        long Point = DecodeToPoint(gridPointCode) - ELEVEN;
        /*  Validate Point  */
        gpc = Validate(Point);
        if (!gpc.IsValid) {
            throw new IllegalArgumentException(gpc.Message + ": Invalid GPC.");
        }
        /* Getting Coordinates from Point  */
        return GetCoordinates(Point);
    }

    /**
     * Validates GPC
     * @param gridPointCode GPC
     * @return Validity status with message if any
     */
    private static Validation Validate(String gridPointCode) {
        if (gridPointCode.length() != GPC_LENGTH) {
            return new Validation(false, "GPC_LENGTH");
        }
        for (char character : gridPointCode.toCharArray()) {
            if (!CHARACTERS.contains(String.valueOf(character))) {
                return new Validation(false, "GPC_CHAR");
            }
        }
        return new Validation(true, "");
    }

    /**
     * Validates Point number
     * @param point Point number
     * @return Validity status with message if any
     */
    private static Validation Validate(long point) {
        if (point > MAX_POINT) {
            return new Validation(false, "GPC_RANGE");
        }
        return new Validation(true, "");
    }

    /**
     * Check if grid point code is valid
     *
     * @param gridPointCode Grid Point Code
     * @return Validity status with message if any
     */
    public static Validation IsValid(String gridPointCode) {
        if (gridPointCode.isBlank()) {
            return new Validation(false, "GPC_NULL");
        }
        Validation gpc = Validate(gridPointCode);
        if (!gpc.IsValid) {
            return gpc;
        }
        gpc = Validate(DecodeToPoint(gridPointCode) - ELEVEN);
        if (!gpc.IsValid) {
            return gpc;
        }
        return new Validation(true, "");
    }

    /**
     * Decode string to Point
     * 
     * @param gridPointCode Valid GPC
     * @return Point number
     */
    private static long DecodeToPoint(String gridPointCode) {
        long Point = 0;
        for (int i = 0; i < GPC_LENGTH; i++) {
            Point *= 27;
            char character = gridPointCode.charAt(i);
            Point += (long)CHARACTERS.indexOf(character);
        }
        return Point;
    }

    /**
     * Get a Coordinates from Point
     * 
     * @param point Valid Point number
     * @return Coordinates in Decimal Degrees
     */
    private static Coordinates GetCoordinates(long point) {
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
     * Get 7 parts of coordinates
     * 
     * @param latLongIndex Latitude and Longitude pair index from Table
     * @param fractional Fractional part of coordinates
     * @return Integer arrays of coordinates
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
}
