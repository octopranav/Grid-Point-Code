package ca.pranavpatel.algo.gridpointcode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Version 2 of the Grid Point Code format.
 *
 * <p>A code names one cell of a fixed grid laid over the Earth. Ten characters,
 * always. The first divides the world into 24 cells of 45 by 60 degrees; each of
 * the nine after it divides the cell named so far into 25 parts, five by five.
 * Two codes that begin with the same k characters therefore name points in the
 * same level-k cell -- containment, not correlation, so it holds for every pair
 * of points without exception.
 *
 * <p>The whole format is arithmetic. There are no ordering tables and no
 * generated constants: a serpentine at level 1, a Peano digit reflection below
 * it, and one parity reset entering level 6. Section numbers in the comments
 * refer to SPEC.md, which is the normative description and the thing to
 * implement from.
 *
 * <p>Version 1 codes still decode, because codes end up on signs and in records
 * and removing that would orphan every one of them. {@code Decode} dispatches on
 * length -- ten characters is version 2, eleven is version 1 -- and
 * {@code Encode} emits version 2 only, so the old format cannot be minted again.
 */
public final class GPC {
    // Section 4. Twenty-five symbols, digits first so that the alphabet is
    // ASCII-ascending and a plain string sort is a spatial sort. No vowel
    // appears, so no English word can be spelled by a code.
    private static final String ALPHABET = "0123456789CDFGHJKLMNPRTWX";

    // Section 3.
    private static final int CODE_LENGTH = 10;
    private static final int LEVELS = 10;
    // Section 5.3: both parity accumulators reset entering this level.
    private static final int RESET_LEVEL = 6;
    private static final long P9 = 1_953_125L;      // 5^9
    private static final long P5 = 3_125L;          // 5^5, inside one level-5 cell
    private static final long HALF_P5 = 1_562L;     // section 12.2 adds this, not 1562.5
    private static final long R5 = 2_500L;          // 4 * 5^4, rows of level-5 cells
    private static final long C5 = 3_750L;          // 6 * 5^4, columns of level-5 cells
    private static final long CODE_SPACE = 95_367_431_640_625L;   // 25^10, section 13
    private static final long ROWS = 7_812_500L;    // 4 * 5^9
    private static final long COLS = 11_718_750L;   // 6 * 5^9

    // Section 2.
    private static final double MIN_LAT = -90;
    private static final double MAX_LAT = 90;
    private static final double MIN_LONG = -180;
    private static final double MAX_LONG = 180;

    private static final char PREFIX = '#';
    private static final char SEPERATOR = '-';
    private static final char CHECK_MARK = '*';
    // Section 19.1. Written as an escape rather than as the character, so that it
    // survives whatever a compiler assumes about source encoding.
    static final char DEGREE_SIGN = '\u00b0';

    // Section 17.2. Three symbols turn up by chance too often to warn about.
    private static final int SCREEN_MIN = 4;

    // Section 18.3. North, north-east, east, south-east, south, south-west,
    // west, north-west, as row and column steps in pairs. Rows increase
    // northward, so north is +1.
    private static final int[] NEIGHBOUR_STEPS = {
        1, 0, 1, 1, 0, 1, -1, 1, -1, 0, -1, -1, 0, -1, 1, -1,
    };

    // Section 18.4, and the radius is 18.5. These are the only physical
    // quantities in the format; everything else is arithmetic.
    private static final double M_PER_DEGREE_LAT = 111_132.0;
    private static final double M_PER_DEGREE_LONG = 111_319.49;   // at the equator
    private static final double EARTH_RADIUS = 6_371_008.8;       // mean radius of WGS 84

    // Section 8. Exactly the letters that are not in the alphabet, less U, Q and
    // Y, which are rejected rather than aliased. L is a real symbol and is never
    // aliased to 1: it names a different cell, and aliasing it would make two
    // different codes collide.
    private static final String ALIASED = "OISZBAEV";
    private static final String ALIASES = "0152843W";

    // ASCII whitespace only. A routine that also stripped the Unicode spaces
    // would accept in one port what another rejects, which is the whole thing the
    // shared vectors exist to prevent. Java has no escape for the vertical
    // tab, and a unicode escape for it is resolved by the lexer before this is
    // a string, so it is spelled as the character it is.
    private static final String WHITESPACE = " \t\n" + (char)0x0B + "\f\r";

    // The field element t, whose symbol index is 1 * 5 + 0. Section 14.2.
    private static final int T = 5;

    // t^1 to t^11, the eleven check weights. Computed rather than transcribed.
    private static final int[] WEIGHTS = PowersOfT();

    private GPC() {
        throw new IllegalStateException("GPC class");
    }

    /*  PART 1 : ENCODE */

    /**
     * Encodes coordinates as a version 2 Grid Point Code.
     *
     * @param latitude Latitude in decimal degrees, -90 to 90 inclusive.
     * @param longitude Longitude in decimal degrees, -180 to 180 inclusive.
     * @return the formatted code.
     * @throws ca.pranavpatel.algo.gridpointcode.GPCException if either coordinate
     *  is outside the domain, NaN or infinite.
     */
    public static String Encode(double latitude, double longitude) {
        return Encode(latitude, longitude, true);
    }

    /**
     * Encodes coordinates as a version 2 Grid Point Code.
     *
     * @param latitude Latitude in decimal degrees, -90 to 90 inclusive.
     * @param longitude Longitude in decimal degrees, -180 to 180 inclusive.
     * @param formatted True for {@code #XXXXX-XXXXX}, false for the bare ten
     *  characters. Both denote the same code.
     * @return the code.
     * @throws ca.pranavpatel.algo.gridpointcode.GPCException if either coordinate
     *  is outside the domain, NaN or infinite.
     */
    public static String Encode(double latitude, double longitude, Boolean formatted) {
        Validation coordinates = IsValid(latitude, longitude);
        if (!coordinates.IsValid) {
            throw new GPCException(coordinates.Message,
                coordinates.Message + ": value out of valid range.");
        }

        long[] grid = ToGrid(latitude, longitude);
        String code = GridToCode(grid[0], grid[1]);
        return formatted ? FormatGPC(code) : code;
    }

    /**
     * Whether a coordinate pair is inside the domain, and which axis is not.
     *
     * <p>The poles and both ends of the antimeridian are inside it; version 1
     * rejected all of them. NaN and the infinities fail the comparisons and so
     * are rejected here as well, in every language, without a separate test.
     *
     * @param latitude Latitude in decimal degrees.
     * @param longitude Longitude in decimal degrees.
     * @return validity status with "LATITUDE" or "LONGITUDE" if any.
     */
    public static Validation IsValid(double latitude, double longitude) {
        if (!(latitude >= MIN_LAT && latitude <= MAX_LAT)) {
            return new Validation(false, "LATITUDE");
        }
        if (!(longitude >= MIN_LONG && longitude <= MAX_LONG)) {
            return new Validation(false, "LONGITUDE");
        }
        return new Validation(true, "");
    }

    /**
     * Coordinates to a row and column of the full grid. Section 5.1.
     *
     * <p>Three floating-point operations per axis, associating left to right.
     * They are the only floating-point arithmetic in the format, and section 7
     * pins how they are evaluated: no reassociation, no fused multiply-add, no
     * wider intermediate. Everything after this is integers.
     *
     * @param latitude Latitude in decimal degrees.
     * @param longitude Longitude in decimal degrees.
     * @return the row and the column, in that order.
     */
    public static long[] ToGrid(double latitude, double longitude) {
        // The one case where two distinct inputs must give one code, so it
        // happens before any arithmetic that could no longer tell them apart.
        if (longitude == MAX_LONG) {
            longitude = MIN_LONG;
        }

        long row = (long)Math.floor((latitude + 90.0) * 7812500.0 / 180.0);
        long col = (long)Math.floor((longitude + 180.0) * 11718750.0 / 360.0);

        // Catches latitude +90, and nothing else. It is what makes the poles
        // encode instead of indexing past the end of the grid.
        row = row < 0 ? 0 : row > ROWS - 1 ? ROWS - 1 : row;
        col = col < 0 ? 0 : col > COLS - 1 ? COLS - 1 : col;
        return new long[] {row, col};
    }

    /**
     * A row and column to ten characters. Section 5.2.
     *
     * <p>Level 1 is a serpentine over the 24 blocks, west to east, snaking
     * northward. Levels 2 to 10 are a Peano digit reflection: each axis is
     * mirrored according to the parity of the digits accumulated in the other,
     * which is what puts consecutive codes in adjacent cells.
     *
     * @param row Row of the full grid.
     * @param col Column of the full grid.
     * @return the unformatted ten-character code.
     */
    public static String GridToCode(long row, long col) {
        long r1 = row / P9;
        long c1 = col / P9;
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        code.append(ALPHABET.charAt((int)(r1 * 6 + (r1 % 2 == 0 ? c1 : 5 - c1))));

        long sr = r1;
        long sc = c1;
        long p = P9;
        for (int level = 2; level <= LEVELS; level++) {
            if (level == RESET_LEVEL) {
                // Section 5.3. Without this the last five characters would mean
                // something different in every level-5 cell, and the short form
                // would name nothing on its own.
                sr = 0;
                sc = 0;
            }
            p /= 5;
            long r = (row / p) % 5;
            long c = (col / p) % 5;
            // The order of these four statements is normative. R is decided from
            // sc before this level's c is added to it, and C from sr after this
            // level's r has been added. Reversing either is a different format.
            long bigR = sc % 2 == 0 ? r : 4 - r;
            sr += r;
            long bigC = sr % 2 == 0 ? c : 4 - c;
            sc += c;
            code.append(ALPHABET.charAt((int)(bigR * 5 + bigC)));
        }

        return code.toString();
    }

    /**
     * The presentation form, {@code #XXXXX-XXXXX}. Section 5.4.
     *
     * <p>The grouping is not arbitrary: the second group is exactly the short
     * form, so a printed code shows its own local form.
     *
     * @param code Unformatted ten-character code.
     * @return the formatted code.
     */
    public static String FormatGPC(String code) {
        return PREFIX + code.substring(0, 5) + SEPERATOR + code.substring(5);
    }

    /*  PART 2 : DECODE */

    /**
     * Decodes a code to the centre of the cell it names.
     *
     * <p>Dispatches on length once the separators are stripped: ten characters is
     * version 2, eleven is version 1. A code carrying a check character is always
     * version 2, since version 1 has none.
     *
     * @param gridPointCode Formatted or unformatted, with or without a
     *  {@code *} check character.
     * @return latitude and longitude in decimal degrees, six decimal places.
     * @throws ca.pranavpatel.algo.gridpointcode.GPCException with reason
     *  GPC_RESERVED for a well-formed code beginning with X, or one of the
     *  invalid reasons otherwise.
     */
    public static Coordinates Decode(String gridPointCode) {
        String[] parts = Split(gridPointCode);
        if (parts[1] == null && parts[0].length() == V1.CODE_LENGTH) {
            return V1.Decode(parts[0]);
        }

        long[] grid = CodeToGrid(Geometric(gridPointCode));
        return new Coordinates(Round6((2 * grid[0] + 1) * 1152 - 9_000_000_000L),
                               Round6((2 * grid[1] + 1) * 1536 - 18_000_000_000L));
    }

    /**
     * The boundaries of the cell a version 2 code names. Section 6.3.
     *
     * @param gridPointCode Formatted or unformatted code.
     * @return the south, west, north and east edges of the cell.
     * @throws ca.pranavpatel.algo.gridpointcode.GPCException as {@code Decode}.
     *  Version 1 codes have no area; they resolve to a corner and are not part
     *  of this grid.
     */
    public static Area DecodeToArea(String gridPointCode) {
        long[] grid = CodeToGrid(Geometric(gridPointCode));
        return new Area(grid[0] * 180.0 / 7812500.0 - 90.0,
                        grid[1] * 360.0 / 11718750.0 - 180.0,
                        (grid[0] + 1) * 180.0 / 7812500.0 - 90.0,
                        (grid[1] + 1) * 360.0 / 11718750.0 - 180.0);
    }

    /**
     * Decodes an eleven-character version 1 code. Appendix B.
     *
     * <p>{@code Decode} reaches this on its own for anything eleven characters
     * long. The explicit entry point is here for a caller that knows which format
     * it holds and wants to say so.
     *
     * <p>Version 1 returns the corner of its cell rather than the centre, which
     * is what every version 1 release has returned.
     *
     * @param gridPointCode Formatted or unformatted version 1 code.
     * @return latitude and longitude in decimal degrees.
     * @throws ca.pranavpatel.algo.gridpointcode.GPCException if the code is null,
     *  malformed, or outside the grid.
     */
    public static Coordinates DecodeV1(String gridPointCode) {
        return V1.Decode(gridPointCode);
    }

    /**
     * Ten characters back to a row and column. Section 6.1.
     *
     * <p>The inverse of {@code GridToCode}, character by character. Expects a
     * normalised, geometric code.
     *
     * @param code Normalised ten-character code.
     * @return the row and the column, in that order.
     */
    public static long[] CodeToGrid(String code) {
        int i = ALPHABET.indexOf(code.charAt(0));
        long r1 = i / 6;
        long k = i % 6;
        long c1 = r1 % 2 == 0 ? k : 5 - k;

        long row = r1;
        long col = c1;
        long sr = r1;
        long sc = c1;
        for (int level = 2; level <= LEVELS; level++) {
            if (level == RESET_LEVEL) {
                sr = 0;
                sc = 0;
            }
            int j = ALPHABET.indexOf(code.charAt(level - 1));
            long bigR = j / 5;
            long bigC = j % 5;
            long r = sc % 2 == 0 ? bigR : 4 - bigR;
            sr += r;
            long c = sr % 2 == 0 ? bigC : 4 - bigC;
            sc += c;
            row = row * 5 + r;
            col = col * 5 + c;
        }

        return new long[] {row, col};
    }

    /*  PART 3 : PARSE, CLASSIFY, CHECK */

    /**
     * Case-folds, strips separators, applies the alias table. Section 8.
     *
     * @param gridPointCode Anything a person might have typed.
     * @return the payload, and the check character, which is null when the input
     *  carried no {@code *}. The check is returned however long it normalised:
     *  deciding whether it is acceptable belongs to {@code Validate}.
     * @throws ca.pranavpatel.algo.gridpointcode.GPCException GPC_NULL if there is
     *  nothing at all to parse.
     */
    public static String[] Normalise(String gridPointCode) {
        String[] parts = Split(gridPointCode);
        return new String[] {Alias(parts[0]), parts[1] == null ? null : Alias(parts[1])};
    }

    /**
     * Classifies a string and says why, if the answer is INVALID. Section 9.
     *
     * @param gridPointCode Anything a person might have typed.
     * @return the class, and the reason code, which is empty for anything that is
     *  not INVALID, and is otherwise GPC_NULL, GPC_LENGTH, GPC_CHAR or GPC_CHECK,
     *  tested in that order.
     */
    public static Classification Validate(String gridPointCode) {
        String[] parts;
        try {
            parts = Normalise(gridPointCode);
        } catch (GPCException error) {
            return new Classification(CodeClass.INVALID, error.getReason());
        }
        String code = parts[0];
        String check = parts[1];
        if (code.length() != CODE_LENGTH) {
            return new Classification(CodeClass.INVALID, "GPC_LENGTH");
        }
        for (char character : code.toCharArray()) {
            if (ALPHABET.indexOf(character) < 0) {
                return new Classification(CodeClass.INVALID, "GPC_CHAR");
            }
        }
        // A check that does not hold is not something to discard. A caller told a
        // code is valid has to be able to decode it.
        if (check != null && !check.equals(CheckSymbol(code))) {
            return new Classification(CodeClass.INVALID, "GPC_CHECK");
        }
        return new Classification(
            code.charAt(0) == 'X' ? CodeClass.RESERVED : CodeClass.GEOMETRIC, "");
    }

    /**
     * GEOMETRIC, RESERVED or INVALID. Section 9 and Appendix C.
     *
     * @param gridPointCode Anything a person might have typed.
     * @return the class.
     */
    public static CodeClass Classify(String gridPointCode) {
        return Validate(gridPointCode).Kind;
    }

    /**
     * Whether a string is a version 2 code that decodes.
     *
     * <p>True for GEOMETRIC only. A reserved code is false, because it names no
     * cell, and so is a version 1 code: {@code Classify} describes this grid, and
     * eleven characters are not part of it. {@code Decode} still reads version 1,
     * and {@code IsValidV1} answers for it.
     *
     * @param gridPointCode Anything a person might have typed.
     * @return true if the string is a decodable version 2 code.
     */
    public static boolean IsValid(String gridPointCode) {
        return Validate(gridPointCode).Kind == CodeClass.GEOMETRIC;
    }

    /**
     * Whether a string is a version 1 code, and why not when it is not.
     *
     * @param gridPointCode Anything a person might have typed.
     * @return validity status with the reason code if any.
     */
    public static Validation IsValidV1(String gridPointCode) {
        return V1.IsValid(gridPointCode);
    }

    /**
     * The optional GF(25) check character for a code. Section 14.
     *
     * <p>For voice, radio and paper. Written after a star,
     * {@code #G3RJM-98NM9*T}. It detects every single-symbol error and every
     * adjacent transposition, and it is not canonical: the ten-character form is
     * what gets stored and interchanged, and this is never emitted unless asked
     * for.
     *
     * @param gridPointCode Formatted or unformatted code.
     * @return the check character.
     * @throws ca.pranavpatel.algo.gridpointcode.GPCException if the input is not
     *  ten symbols of the alphabet. A reserved code has a check character like
     *  any other.
     */
    public static String CheckCharacter(String gridPointCode) {
        String code = Normalise(gridPointCode)[0];
        if (code.length() != CODE_LENGTH) {
            throw new GPCException("GPC_LENGTH");
        }
        for (char character : code.toCharArray()) {
            if (ALPHABET.indexOf(character) < 0) {
                throw new GPCException("GPC_CHAR");
            }
        }
        return CheckSymbol(code);
    }

    /**
     * A code in its check form, {@code #G3RJM-98NM9*T}. Section 14.6.
     *
     * <p>The form to use for voice, radio and paper, and the one an application
     * should share when the code may be read aloud or written down. Building it
     * by hand is three operations and two chances to be wrong: the star can be
     * dropped, or the check character spliced inside the separator instead of
     * after it. Neither mistake is caught by anything.
     *
     * <p>The check character is computed for the payload given. Any check
     * character the input already carried is ignored, so a code already in check
     * form comes back with a correct one.
     *
     * @param gridPointCode Formatted or unformatted, with or without a check
     *  character.
     * @return the check form, as {@code #XXXXX-XXXXX*K}.
     * @throws ca.pranavpatel.algo.gridpointcode.GPCException if the input is not
     *  ten symbols of the alphabet. A reserved code has a check form like any
     *  other.
     */
    public static String WithCheck(String gridPointCode) {
        return WithCheck(gridPointCode, true);
    }

    /**
     * A code in its check form, {@code #G3RJM-98NM9*T}. Section 14.6.
     *
     * @param gridPointCode Formatted or unformatted, with or without a check
     *  character.
     * @param formatted {@code true} for {@code #XXXXX-XXXXX*K}, {@code false}
     *  for {@code XXXXXXXXXX*K}.
     * @return the check form.
     * @throws ca.pranavpatel.algo.gridpointcode.GPCException if the input is not
     *  ten symbols of the alphabet. A reserved code has a check form like any
     *  other.
     */
    public static String WithCheck(String gridPointCode, Boolean formatted) {
        String code = Normalise(gridPointCode)[0];
        String check = CheckCharacter(code);
        return (formatted ? FormatGPC(code) : code) + CHECK_MARK + check;
    }

    /*  PART 4 : THE LOCALITY API  */

    /**
     * The first {@code level} characters of a code, normalised. Section 18.1.
     *
     * <p>A cell names a region: two codes lie in the same level-k cell exactly
     * when they share their first k characters, so this is the region identifier
     * the guarantee is about.
     *
     * @param gridPointCode A code, or a longer cell.
     * @param level 1 to 10.
     * @return the cell, bare -- no {@code #} and no separator. Ten characters is
     *  a code and anything shorter is a region; presenting a cell as a code would
     *  break the fixed length the format is recognised by.
     * @throws ca.pranavpatel.algo.gridpointcode.GPCException with reason
     *  GPC_LEVEL for a level outside 1 to 10, GPC_LENGTH if the argument is
     *  shorter than the level asked for, GPC_RESERVED for a cell beginning with
     *  X, or one of the parsing reasons.
     */
    public static String Cell(String gridPointCode, int level) {
        CheckLevel(level);
        String code = CellOf(gridPointCode);
        if (code.length() < level) {
            throw new GPCException("GPC_LENGTH");
        }
        return code.substring(0, level);
    }

    /**
     * Whether a code lies inside a cell. Section 18.2.
     *
     * <p>The prefix test, and nothing more. What section 10 buys is that this is
     * a true geometric containment test rather than an approximation of one: no
     * tolerance, no edge case at a boundary, and no pair of points on Earth for
     * which the string answer and the geometric answer differ.
     *
     * @param cell A cell of 1 to 10 characters.
     * @param gridPointCode A code, or a cell.
     * @return true if the code lies inside the cell.
     */
    public static boolean Contains(String cell, String gridPointCode) {
        String prefix = CellOf(cell);
        String code = CellOf(gridPointCode);
        return code.length() >= prefix.length()
                && code.substring(0, prefix.length()).equals(prefix);
    }

    /**
     * The cells sharing an edge or a corner, in order. Section 18.3.
     *
     * <p>North, north-east, east, south-east, south, south-west, west,
     * north-west. Columns wrap at the antimeridian; rows do not, because the grid
     * ends at the poles, so a cell in the top or bottom row has five neighbours
     * and the three that would lie off the grid are absent rather than empty.
     *
     * @param cell A cell of 1 to 10 characters.
     * @return bare cells of the same length as the argument.
     */
    public static List<String> Neighbours(String cell) {
        String code = CellOf(cell);
        int level = code.length();
        long p = Pow5(LEVELS - level);
        long[] grid = CodeToGrid(code + Repeat(ALPHABET.charAt(0), LEVELS - level));
        long cellRow = grid[0] / p;
        long cellCol = grid[1] / p;
        long rowCells = 4 * Pow5(level - 1);
        long colCells = 6 * Pow5(level - 1);

        List<String> found = new ArrayList<>(8);
        for (int i = 0; i < NEIGHBOUR_STEPS.length; i += 2) {
            long r = cellRow + NEIGHBOUR_STEPS[i];
            if (r < 0 || r >= rowCells) {
                continue;
            }
            long c = (cellCol + NEIGHBOUR_STEPS[i + 1] + colCells) % colCells;
            found.add(GridToCode(r * p, c * p).substring(0, level));
        }
        return found;
    }

    /**
     * How big a level-k cell is. Section 18.4.
     *
     * @param level 1 to 10.
     * @return the latitude and longitude spans in degrees, then the same two in
     *  metres. The north-south figure holds everywhere; the east-west one is the
     *  value at the equator and shrinks with the cosine of latitude, which is a
     *  multiplication left to the caller.
     * @throws ca.pranavpatel.algo.gridpointcode.GPCException with reason
     *  GPC_LEVEL if the level is outside 1 to 10.
     */
    public static Dimensions CellDimensions(int level) {
        CheckLevel(level);
        double divisor = Pow5(level - 1);
        double latitudeSpan = 45.0 / divisor;
        double longitudeSpan = 60.0 / divisor;
        return new Dimensions(latitudeSpan, longitudeSpan,
                latitudeSpan * M_PER_DEGREE_LAT, longitudeSpan * M_PER_DEGREE_LONG);
    }

    /**
     * Great-circle metres between the centres of two cells. Section 18.5.
     *
     * <p>The cells may be of different levels. This is the one operation in the
     * format that is not bit-identical across languages: no standard library
     * rounds sine, cosine or arc sine correctly, so two ports agree to about a
     * millimetre rather than exactly. Anything that needs a reproducible ordering
     * must rank on grid indices, as {@code SuggestCorrections} does.
     *
     * @param a A cell of 1 to 10 characters.
     * @param b Another.
     * @return metres.
     */
    public static double Distance(String a, String b) {
        Coordinates first = CellCentre(a);
        Coordinates second = CellCentre(b);

        double phi1 = first.Latitude * Math.PI / 180.0;
        double phi2 = second.Latitude * Math.PI / 180.0;
        double dPhi = phi2 - phi1;
        double dLambda = (second.Longitude - first.Longitude) * Math.PI / 180.0;

        double h = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2)
                * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        if (h > 1.0) {
            // Rounding can carry the sum a unit past 1 for points near opposite
            // ends of the Earth, where arc sine is undefined.
            h = 1.0;
        }
        return 2 * EARTH_RADIUS * Math.asin(Math.sqrt(h));
    }

    /**
     * The row and column of the cell a code names. Section 18.6.
     *
     * <p>The accessor for a caller building a spatial structure of its own -- a
     * tile index, a join key, a quadtree -- who wants the integers rather than
     * degrees rounded to six places.
     *
     * @param gridPointCode Anything a person might have typed.
     * @return the row and column.
     */
    public static long[] DecodeToGrid(String gridPointCode) {
        return CodeToGrid(Geometric(gridPointCode));
    }

    /**
     * The last five characters of a code. Section 12.1.
     *
     * <p>Literally the second printed group of {@code #XXXXX-XXXXX}, so a printed
     * code shows its own short form. The leading dash belongs to the presentation
     * form and is not returned; {@code RecoverShort} accepts it either way.
     *
     * @param gridPointCode Anything a person might have typed.
     * @return the five characters.
     */
    public static String Shorten(String gridPointCode) {
        return Geometric(gridPointCode).substring(5);
    }

    /**
     * The full code a short form names, near a reference. Section 12.2.
     *
     * <p>Exact integer arithmetic -- no search, no distance, no tie to break --
     * and exact whenever the reference is within half a level-5 cell of the true
     * point on each axis, which is 0.03598848 degrees of latitude (3.999 km) and
     * 0.04798464 degrees of longitude (5.342 km at the equator, less elsewhere).
     *
     * <p>Outside that box it returns a neighbouring cell's copy of the same
     * offset, a plausible location 8 or 10 km away. A caller that cannot bound
     * its reference should not be using the short form.
     *
     * @param shortForm The five characters, with or without the leading dash.
     * @param nearLatitude Reference latitude.
     * @param nearLongitude Reference longitude.
     * @param formatted True for {@code #XXXXX-XXXXX}, false for the bare ten.
     * @return the full code.
     * @throws ca.pranavpatel.algo.gridpointcode.GPCException with reason
     *  GPC_LENGTH unless the short form is five symbols, or LATITUDE or LONGITUDE
     *  for a reference outside the domain.
     */
    public static String RecoverShort(String shortForm, double nearLatitude,
            double nearLongitude, Boolean formatted) {
        String tail = Normalise(shortForm)[0];
        if (tail.length() != CODE_LENGTH - 5) {
            throw new GPCException("GPC_LENGTH");
        }
        for (char character : tail.toCharArray()) {
            if (ALPHABET.indexOf(character) < 0) {
                throw new GPCException("GPC_CHAR");
            }
        }

        long[] low = ReadTail(tail);
        Validation coordinates = IsValid(nearLatitude, nearLongitude);
        if (!coordinates.IsValid) {
            throw new GPCException(coordinates.Message,
                coordinates.Message + ": value out of valid range.");
        }
        long[] reference = ToGrid(nearLatitude, nearLongitude);

        // Floor division over values that may be negative. Java divides toward
        // zero, which is wrong here, and wrong only west and south of the
        // reference -- so a truncating port passes about a quarter of the vectors
        // and looks merely unlucky.
        long cellRow = Math.floorDiv(reference[0] - low[0] + HALF_P5, P5);
        cellRow = cellRow < 0 ? 0 : (cellRow > R5 - 1 ? R5 - 1 : cellRow);
        long cellCol = Math.floorMod(Math.floorDiv(reference[1] - low[1] + HALF_P5, P5), C5);

        String code = GridToCode(cellRow * P5 + low[0], cellCol * P5 + low[1]);
        return formatted ? FormatGPC(code) : code;
    }

    /**
     * The full code a short form names, formatted. Section 12.2.
     *
     * @param shortForm The five characters, with or without the leading dash.
     * @param nearLatitude Reference latitude.
     * @param nearLongitude Reference longitude.
     * @return the formatted code.
     */
    public static String RecoverShort(String shortForm, double nearLatitude, double nearLongitude) {
        return RecoverShort(shortForm, nearLatitude, nearLongitude, true);
    }

    /**
     * Codes one typo away that are plausible near a reference. Section 15.3.
     *
     * <p>At most 249 candidates -- 240 single-character substitutions and up to 9
     * adjacent transpositions -- filtered to those in the reference's level-k cell
     * or one of its eight neighbours, and ranked by {@code 9*dRow^2 + 16*dCol^2},
     * which is squared distance in degree space. Ties break on the integer form.
     * Every step is integer arithmetic, so all four ports return the same list in
     * the same order.
     *
     * <p>Level 6 suits a device fix or a named suburb and returns one candidate in
     * the median case. Widening it to cover a poorer reference costs precision,
     * not correctness.
     *
     * @param gridPointCode The code as typed, which need not decode: a code with a
     *  wrong character is exactly what this is for. It must still normalise to ten
     *  symbols of the alphabet.
     * @param nearLatitude Reference latitude.
     * @param nearLongitude Reference longitude.
     * @param level The window is 3 by 3 cells at this level.
     * @param formatted True for {@code #XXXXX-XXXXX}, false for the bare ten.
     * @return the candidates, best first.
     */
    public static List<String> SuggestCorrections(String gridPointCode, double nearLatitude,
            double nearLongitude, int level, Boolean formatted) {
        CheckLevel(level);
        String code = Normalise(gridPointCode)[0];
        if (code.length() != CODE_LENGTH) {
            throw new GPCException("GPC_LENGTH");
        }
        for (char character : code.toCharArray()) {
            if (ALPHABET.indexOf(character) < 0) {
                throw new GPCException("GPC_CHAR");
            }
        }

        Validation coordinates = IsValid(nearLatitude, nearLongitude);
        if (!coordinates.IsValid) {
            throw new GPCException(coordinates.Message,
                coordinates.Message + ": value out of valid range.");
        }
        long[] reference = ToGrid(nearLatitude, nearLongitude);

        long p = Pow5(LEVELS - level);
        long refRowCell = reference[0] / p;
        long refColCell = reference[1] / p;
        long colCells = COLS / p;

        List<long[]> scored = new ArrayList<>();
        List<String> candidates = Candidates(code);
        for (int i = 0; i < candidates.size(); i++) {
            String candidate = candidates.get(i);
            if (candidate.charAt(0) == 'X') {   // reserved, never geometric
                continue;
            }
            long[] grid = CodeToGrid(candidate);

            long dRowCell = grid[0] / p - refRowCell;
            long dColCell = (grid[1] / p - refColCell + colCells) % colCells;
            if (dColCell > colCells / 2) {
                dColCell -= colCells;
            }
            if (Math.abs(dRowCell) > 1 || Math.abs(dColCell) > 1) {
                continue;
            }

            long dRow = grid[0] - reference[0];
            long dCol = grid[1] - reference[1];
            if (dCol > COLS / 2) {              // the short way round
                dCol -= COLS;
            } else if (dCol < -COLS / 2) {
                dCol += COLS;
            }

            scored.add(new long[] {9 * dRow * dRow + 16 * dCol * dCol,
                                   ToInteger(candidate), i});
        }

        scored.sort((x, y) -> x[0] != y[0] ? Long.compare(x[0], y[0])
                                           : Long.compare(x[1], y[1]));
        List<String> ordered = new ArrayList<>(scored.size());
        for (long[] row : scored) {
            String candidate = candidates.get((int)row[2]);
            ordered.add(formatted ? FormatGPC(candidate) : candidate);
        }
        return ordered;
    }

    /**
     * Codes one typo away, at the default level of 6, formatted. Section 15.3.
     *
     * @param gridPointCode The code as typed.
     * @param nearLatitude Reference latitude.
     * @param nearLongitude Reference longitude.
     * @return the candidates, best first.
     */
    public static List<String> SuggestCorrections(String gridPointCode, double nearLatitude,
            double nearLongitude) {
        return SuggestCorrections(gridPointCode, nearLatitude, nearLongitude, 6, true);
    }

    /**
     * The code as a base-25 numeral. Section 13.
     *
     * <p>Forty-seven bits, so six bytes big-endian, and order-preserving: sorting
     * the integers sorts the codes, which sorts the cells geographically. A
     * reserved code is at or above 91,552,734,375,000 and a geometric one below
     * it, so one comparison classifies without parsing.
     *
     * @param gridPointCode Anything a person might have typed.
     * @return the value.
     */
    public static long ToInteger(String gridPointCode) {
        String code = Payload(gridPointCode);
        long value = 0;
        for (char character : code.toCharArray()) {
            value = value * 25 + ALPHABET.indexOf(character);
        }
        return value;
    }

    /**
     * The code a base-25 numeral names. Section 13.
     *
     * @param value 0 to 25^10 - 1.
     * @param formatted True for {@code #XXXXX-XXXXX}, false for the bare ten.
     * @return the code.
     * @throws ca.pranavpatel.algo.gridpointcode.GPCException with reason
     *  GPC_RANGE if the value is outside the range.
     */
    public static String FromInteger(long value, Boolean formatted) {
        if (value < 0 || value >= CODE_SPACE) {
            throw new GPCException("GPC_RANGE");
        }
        char[] out = new char[LEVELS];
        long rest = value;
        for (int i = LEVELS - 1; i >= 0; i--) {
            out[i] = ALPHABET.charAt((int)(rest % 25));
            rest /= 25;
        }
        String code = new String(out);
        return formatted ? FormatGPC(code) : code;
    }

    /**
     * The code a base-25 numeral names, formatted. Section 13.
     *
     * @param value 0 to 25^10 - 1.
     * @return the formatted code.
     */
    public static String FromInteger(long value) {
        return FromInteger(value, true);
    }

    /**
     * Substrings of a code that spell something unwanted. Section 17.
     *
     * <p>Advisory, and non-normative. It reports and never blocks: nothing in this
     * package refuses to encode, decode or validate because of what this found.
     *
     * @param gridPointCode Anything a person might have typed.
     * @return the version of the list, and the matched spans, ordered by position
     *  and then by length. Spans may overlap and every match is reported. A clean
     *  code returns the version and no spans, because a caller has to be able to
     *  tell "clean under this list" from "never screened".
     */
    public static Screening Screen(String gridPointCode) {
        String code = Payload(gridPointCode);
        List<Span> spans = new ArrayList<>();
        for (int length = SCREEN_MIN; length <= CODE_LENGTH; length++) {
            for (int start = 0; start <= CODE_LENGTH - length; start++) {
                if (ScreenList.ENTRIES.contains(ScreenHash(code.substring(start, start + length)))) {
                    spans.add(new Span(start + 1, length));
                }
            }
        }
        return new Screening(ScreenList.VERSION, spans);
    }

    /**
     * Encodes a sequence of coordinates.
     *
     * <p>For dataset work. The first bad coordinate throws, rather than a bad row
     * being silently dropped; {@code EncodeStream} is the one to reach for when
     * the caller wants to handle failures row by row.
     *
     * @param points The coordinates.
     * @param formatted True for {@code #XXXXX-XXXXX}, false for the bare ten.
     * @return the codes.
     */
    public static List<String> EncodeAll(List<Coordinates> points, Boolean formatted) {
        List<String> codes = new ArrayList<>(points.size());
        for (Coordinates point : points) {
            codes.add(Encode(point.Latitude, point.Longitude, formatted));
        }
        return codes;
    }

    /**
     * Encodes a stream lazily, one code at a time.
     *
     * @param points The coordinates.
     * @param formatted True for {@code #XXXXX-XXXXX}, false for the bare ten.
     * @return the codes, produced as they are asked for.
     */
    public static Stream<String> EncodeStream(Stream<Coordinates> points, Boolean formatted) {
        return points.map(point -> Encode(point.Latitude, point.Longitude, formatted));
    }

    /**
     * Decodes a sequence of codes.
     *
     * @param codes The codes.
     * @return the coordinates.
     */
    public static List<Coordinates> DecodeAll(List<String> codes) {
        List<Coordinates> points = new ArrayList<>(codes.size());
        for (String code : codes) {
            points.add(Decode(code));
        }
        return points;
    }

    /**
     * Decodes a stream lazily, one pair at a time.
     *
     * @param codes The codes.
     * @return the coordinates, produced as they are asked for.
     */
    public static Stream<Coordinates> DecodeStream(Stream<String> codes) {
        return codes.map(GPC::Decode);
    }

    /*  PART 5 : COORDINATE CONVERSIONS  */

    /**
     * Degrees, minutes and seconds, latitude first. Section 19.1.
     *
     * <p>{@code 43°39'00.00"N, 79°22'48.00"W}.
     *
     * <p>Lossy: a hundredth of a second is 0.309 m of latitude. A decoded code
     * survives the trip all the same, because {@code Decode} returns a cell centre
     * and that sits eight times further from the nearest boundary than this
     * rounding can move it. For exact interchange use {@code ToGeoURI}.
     *
     * @param latitude Decimal degrees.
     * @param longitude Decimal degrees.
     * @return the text.
     */
    public static String ToDMS(double latitude, double longitude) {
        Validation coordinates = IsValid(latitude, longitude);
        if (!coordinates.IsValid) {
            throw new GPCException(coordinates.Message,
                coordinates.Message + ": value out of valid range.");
        }
        return DmsAxis(latitude, 'N', 'S') + ", " + DmsAxis(longitude, 'E', 'W');
    }

    /**
     * Reads degrees, minutes and seconds back. Section 19.1.
     *
     * <p>Each axis is a signed or hemisphere-marked value; the unit marker after
     * the degrees is required, because it is what tells one axis from the next
     * when no comma separates them.
     *
     * @param text The DMS text.
     * @return the coordinates.
     * @throws ca.pranavpatel.algo.gridpointcode.GPCException with reason GPC_DMS
     *  for anything the grammar does not accept, or LATITUDE or LONGITUDE for a
     *  value outside the domain.
     */
    public static Coordinates FromDMS(String text) {
        Scan scan = new Scan(text);
        double latitude = scan.Axis(true);
        scan.Spaces();
        if (scan.Peek() == ',') {
            scan.Take();
        }
        double longitude = scan.Axis(false);
        scan.Spaces();
        if (!scan.Done()) {
            throw new GPCException("GPC_DMS");
        }

        Validation coordinates = IsValid(latitude, longitude);
        if (!coordinates.IsValid) {
            throw new GPCException(coordinates.Message,
                coordinates.Message + ": value out of valid range.");
        }
        return new Coordinates(latitude, longitude);
    }

    /**
     * An RFC 5870 URI in its simplest form. Section 19.2.
     *
     * <p>{@code geo:43.650006,-79.380004}. Six decimal places, trailing zeros
     * dropped, which is exactly what {@code Decode} produces, so a code written
     * out this way and read back encodes to the same code every time.
     *
     * @param latitude Decimal degrees.
     * @param longitude Decimal degrees.
     * @return the URI.
     */
    public static String ToGeoURI(double latitude, double longitude) {
        Validation coordinates = IsValid(latitude, longitude);
        if (!coordinates.IsValid) {
            throw new GPCException(coordinates.Message,
                coordinates.Message + ": value out of valid range.");
        }
        return "geo:" + Decimal6(latitude) + "," + Decimal6(longitude);
    }

    /**
     * Reads an RFC 5870 URI back. Section 19.2.
     *
     * <p>A third coordinate is an altitude and is discarded. Parameters are
     * ignored, except that {@code crs} is rejected unless it is {@code wgs84}:
     * this format is defined on WGS 84 alone, and silently reading a code as
     * though it were on another datum would put it in the wrong place.
     *
     * @param text The URI.
     * @return the coordinates.
     * @throws ca.pranavpatel.algo.gridpointcode.GPCException with reason GPC_NULL
     *  for a null argument, GPC_GEO for anything the grammar does not accept, or
     *  LATITUDE or LONGITUDE for a value outside the domain.
     */
    public static Coordinates FromGeoURI(String text) {
        if (text == null) {
            throw new GPCException("GPC_NULL");
        }
        String body = text.trim();
        if (body.length() < 4 || !body.substring(0, 4).equalsIgnoreCase("geo:")) {
            throw new GPCException("GPC_GEO");
        }
        body = body.substring(4);

        int semicolon = body.indexOf(';');
        if (semicolon >= 0) {
            for (String parameter : body.substring(semicolon + 1).split(";", -1)) {
                int equals = parameter.indexOf('=');
                String name = equals < 0 ? parameter : parameter.substring(0, equals);
                String value = equals < 0 ? "" : parameter.substring(equals + 1);
                if (name.equalsIgnoreCase("crs") && !value.equalsIgnoreCase("wgs84")) {
                    throw new GPCException("GPC_GEO");
                }
            }
            body = body.substring(0, semicolon);
        }

        String[] parts = body.split(",", -1);
        if (parts.length != 2 && parts.length != 3) {
            throw new GPCException("GPC_GEO");
        }
        double latitude = GeoNumber(parts[0]);
        double longitude = GeoNumber(parts[1]);
        if (parts.length == 3) {
            GeoNumber(parts[2]);                // altitude, parsed and dropped
        }

        Validation coordinates = IsValid(latitude, longitude);
        if (!coordinates.IsValid) {
            throw new GPCException(coordinates.Message,
                coordinates.Message + ": value out of valid range.");
        }
        return new Coordinates(latitude, longitude);
    }

    /*  PART 6 : INTERNALS */

    /**
     * Payload and check character, cleaned but not yet aliased.
     *
     * <p>The dispatch in {@code Decode} needs to see the characters as typed,
     * because version 1 has its own alphabet and the version 2 alias table would
     * corrupt it.
     *
     * @param gridPointCode Anything a person might have typed.
     * @return the cleaned payload, and the cleaned check or null.
     */
    private static String[] Split(String gridPointCode) {
        if (gridPointCode == null) {
            throw new GPCException("GPC_NULL");
        }
        boolean blank = true;
        for (char character : gridPointCode.toCharArray()) {
            if (WHITESPACE.indexOf(character) < 0) {
                blank = false;
                break;
            }
        }
        if (blank) {
            throw new GPCException("GPC_NULL");
        }

        String text = gridPointCode;
        String check = null;
        int star = text.indexOf(CHECK_MARK);
        if (star >= 0) {
            check = Clean(text.substring(star + 1));
            text = text.substring(0, star);
        }
        return new String[] {Clean(text), check};
    }

    /**
     * Upper-cases by ASCII rules, then drops {@code #}, {@code -} and whitespace.
     *
     * <p>A locale-sensitive upper-casing routine would map {@code i} to a dotted
     * capital in a Turkish locale, and the same code would be valid in one locale
     * and invalid in another.
     *
     * @param text Raw input.
     * @return the cleaned characters.
     */
    private static String Clean(String text) {
        StringBuilder cleaned = new StringBuilder(text.length());
        for (char raw : text.toCharArray()) {
            char character = raw >= 'a' && raw <= 'z' ? (char)(raw - 32) : raw;
            if (character == PREFIX || character == SEPERATOR
                    || WHITESPACE.indexOf(character) >= 0) {
                continue;
            }
            cleaned.append(character);
        }
        return cleaned.toString();
    }

    /**
     * Reads the confusable letters as the symbols they were meant to be.
     *
     * @param text Cleaned characters.
     * @return the aliased characters.
     */
    private static String Alias(String text) {
        StringBuilder aliased = new StringBuilder(text.length());
        for (char character : text.toCharArray()) {
            int at = ALIASED.indexOf(character);
            aliased.append(at < 0 ? character : ALIASES.charAt(at));
        }
        return aliased.toString();
    }

    /**
     * The ten characters, or the typed error that stops decoding.
     *
     * @param gridPointCode Anything a person might have typed.
     * @return the normalised, geometric code.
     */
    private static String Geometric(String gridPointCode) {
        Classification classified = Validate(gridPointCode);
        if (classified.Kind == CodeClass.INVALID) {
            throw new GPCException(classified.Reason);
        }
        if (classified.Kind == CodeClass.RESERVED) {
            throw new GPCException("GPC_RESERVED");
        }
        return Normalise(gridPointCode)[0];
    }

    /**
     * (a + b*t) + (c + d*t), elements indexed b*5 + a. Section 14.2.
     *
     * @param x One element.
     * @param y The other element.
     * @return their sum.
     */
    private static int GfAdd(int x, int y) {
        return ((x / 5 + y / 5) % 5) * 5 + ((x % 5 + y % 5) % 5);
    }

    /**
     * (a + b*t)(c + d*t) with t^2 = 4t + 3. Section 14.2.
     *
     * @param x One element.
     * @param y The other element.
     * @return their product.
     */
    private static int GfMul(int x, int y) {
        int a = x % 5;
        int b = x / 5;
        int c = y % 5;
        int d = y / 5;
        return ((a * d + b * c + 4 * b * d) % 5) * 5 + ((a * c + 3 * b * d) % 5);
    }

    /**
     * t^1 to t^11.
     *
     * @return the eleven check weights.
     */
    private static int[] PowersOfT() {
        int[] weights = new int[11];
        int x = 1;
        for (int i = 0; i < weights.length; i++) {
            x = GfMul(x, T);
            weights[i] = x;
        }
        return weights;
    }

    /**
     * c = t * S, where S is the syndrome over the ten payload symbols.
     *
     * @param code Normalised ten-character code.
     * @return the check character.
     */
    private static String CheckSymbol(String code) {
        int syndrome = 0;
        for (int i = 0; i < code.length(); i++) {
            syndrome = GfAdd(syndrome, GfMul(WEIGHTS[i], ALPHABET.indexOf(code.charAt(i))));
        }
        return String.valueOf(ALPHABET.charAt(GfMul(T, syndrome)));
    }

    /**
     * Rounds a count of 1e-8 degrees to six decimal places. Section 6.2.
     *
     * <p>Ties are unreachable -- every reachable value is congruent to a multiple
     * of 4 modulo 100 -- so no choice of rounding mode can change any result, and
     * no implementation has to make the choice.
     *
     * @param value A count of 1e-8 degrees.
     * @return the value in degrees, six decimal places.
     */
    private static double Round6(long value) {
        long magnitude = Math.abs(value);
        long quotient = magnitude / 100;
        if (magnitude % 100 >= 50) {
            quotient++;
        }
        return (value < 0 ? -quotient : quotient) / 1_000_000.0;
    }

    /**
     * 5 raised to a small power, as an exact integer.
     *
     * @param power 0 to 9.
     * @return 5^power.
     */
    private static long Pow5(int power) {
        long value = 1;
        for (int i = 0; i < power; i++) {
            value *= 5;
        }
        return value;
    }

    /**
     * A character repeated, for padding a cell out to ten symbols.
     *
     * @param character The character.
     * @param count How many.
     * @return the padding.
     */
    private static String Repeat(char character, int count) {
        StringBuilder padding = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            padding.append(character);
        }
        return padding.toString();
    }

    /**
     * Rejects a level outside 1 to 10. Section 18.1.
     *
     * @param level The level to check.
     */
    private static void CheckLevel(int level) {
        if (level < 1 || level > LEVELS) {
            throw new GPCException("GPC_LEVEL");
        }
    }

    /**
     * Ten symbols of the alphabet, reserved ones included.
     *
     * <p>What {@code Screen} and {@code ToInteger} need: both act on the string
     * rather than on the cell it names, so an X in position 1 is no obstacle to
     * either.
     *
     * @param gridPointCode Anything a person might have typed.
     * @return the ten normalised symbols.
     */
    private static String Payload(String gridPointCode) {
        String[] parts = Normalise(gridPointCode);
        String code = parts[0];
        if (code.length() != CODE_LENGTH) {
            throw new GPCException("GPC_LENGTH");
        }
        for (char character : code.toCharArray()) {
            if (ALPHABET.indexOf(character) < 0) {
                throw new GPCException("GPC_CHAR");
            }
        }
        if (parts[1] != null && !parts[1].equals(CheckSymbol(code))) {
            throw new GPCException("GPC_CHECK");
        }
        return code;
    }

    /**
     * A normalised cell of 1 to 10 symbols, or the typed error. Section 18.1.
     *
     * @param text Anything a person might have typed.
     * @return the normalised cell.
     */
    private static String CellOf(String text) {
        String[] parts = Normalise(text);
        String code = parts[0];
        if (code.length() < 1 || code.length() > LEVELS) {
            throw new GPCException("GPC_LENGTH");
        }
        for (char character : code.toCharArray()) {
            if (ALPHABET.indexOf(character) < 0) {
                throw new GPCException("GPC_CHAR");
            }
        }
        if (parts[1] != null && (code.length() != CODE_LENGTH
                || !parts[1].equals(CheckSymbol(code)))) {
            throw new GPCException("GPC_CHECK");
        }
        if (code.charAt(0) == 'X') {
            throw new GPCException("GPC_RESERVED");
        }
        return code;
    }

    /**
     * The centre of a cell of any level, exact to 1e-8 degrees. Section 18.5.
     *
     * <p>Private on purpose. For a ten-character code this differs from
     * {@code Decode} in the seventh decimal place, and two public answers to
     * "where is this cell" would be one too many.
     *
     * <p>Any symbol will do as padding. By section 10 the first k characters fix
     * the level-k cell, so whatever the padded code names, dividing by p lands on
     * the same cell indices.
     *
     * @param text A cell of 1 to 10 characters.
     * @return the centre, in decimal degrees.
     */
    private static Coordinates CellCentre(String text) {
        String code = CellOf(text);
        long p = Pow5(LEVELS - code.length());
        long[] grid = CodeToGrid(code + Repeat(ALPHABET.charAt(0), LEVELS - code.length()));
        return new Coordinates(
            (2 * (grid[0] / p) + 1) * p * 1152 / 100_000_000.0 - 90.0,
            (2 * (grid[1] / p) + 1) * p * 1536 / 100_000_000.0 - 180.0);
    }

    /**
     * The last five characters as an offset in a level-5 cell. Section 12.2.
     *
     * <p>The loop of {@code CodeToGrid} with the parity seeded at zero and no
     * level-1 step, which is what the reset of section 5.3 makes meaningful.
     *
     * @param tail The five characters, normalised.
     * @return the row and column offsets inside the cell.
     */
    private static long[] ReadTail(String tail) {
        long row = 0;
        long col = 0;
        long sr = 0;
        long sc = 0;
        for (char character : tail.toCharArray()) {
            int j = ALPHABET.indexOf(character);
            long bigR = j / 5;
            long bigC = j % 5;
            long r = sc % 2 == 0 ? bigR : 4 - bigR;
            sr += r;
            long c = sr % 2 == 0 ? bigC : 4 - bigC;
            sc += c;
            row = row * 5 + r;
            col = col * 5 + c;
        }
        return new long[] {row, col};
    }

    /**
     * At most 249 codes one typo away, in the order section 15.3 fixes.
     *
     * <p>240 substitutions, then the adjacent transpositions that actually change
     * the code. A code such as P4444PPPPP yields 242, and the list is never padded
     * back to 249 with duplicates.
     *
     * @param code The ten normalised symbols.
     * @return the candidates, in the fixed order.
     */
    private static List<String> Candidates(String code) {
        List<String> found = new ArrayList<>(249);
        for (int position = 0; position < CODE_LENGTH; position++) {
            for (char character : ALPHABET.toCharArray()) {
                if (character != code.charAt(position)) {
                    found.add(code.substring(0, position) + character
                        + code.substring(position + 1));
                }
            }
        }
        for (int position = 0; position < CODE_LENGTH - 1; position++) {
            if (code.charAt(position) != code.charAt(position + 1)) {
                found.add(code.substring(0, position) + code.charAt(position + 1)
                    + code.charAt(position) + code.substring(position + 2));
            }
        }
        return found;
    }

    /**
     * The 32-bit FNV-1a hash, eight lower-case hex characters. Section 17.3.
     *
     * <p>Not a cryptographic hash, and section 17.1 says why it does not need to
     * be. Three integer operations per byte, over ASCII symbols, so all four ports
     * compute it identically with nothing imported.
     *
     * @param text The substring to hash.
     * @return eight lower-case hexadecimal characters.
     */
    private static String ScreenHash(String text) {
        int h = (int)2166136261L;
        for (char character : text.toCharArray()) {
            h = (h ^ character) * 16777619;
        }
        return String.format(Locale.ROOT, "%08x", h & 0xFFFFFFFFL);
    }

    /**
     * One axis of section 19.1, in integers after the first line.
     *
     * @param value Decimal degrees.
     * @param positive The hemisphere letter when the value is not negative.
     * @param negative The letter when it is.
     * @return the axis, written out.
     */
    private static String DmsAxis(double value, char positive, char negative) {
        long u = (long)Math.floor(Math.abs(value) * 360000.0 + 0.5);  // hundredths of a second
        StringBuilder axis = new StringBuilder(16);
        axis.append(u / 360000);
        axis.append(DEGREE_SIGN);
        axis.append(Two(u / 6000 % 60));
        axis.append('\'');
        axis.append(Two(u % 6000 / 100));
        axis.append('.');
        axis.append(Two(u % 100));
        axis.append('"');
        axis.append(value < 0 ? negative : positive);
        return axis.toString();
    }

    /**
     * A value below 100, written with two digits.
     *
     * @param value 0 to 99.
     * @return the two digits.
     */
    private static String Two(long value) {
        return value < 10 ? "0" + value : Long.toString(value);
    }

    /**
     * At most six decimal places, trailing zeros dropped. Section 19.2.
     *
     * @param value Decimal degrees.
     * @return the number, written out.
     */
    private static String Decimal6(double value) {
        long u = (long)Math.floor(Math.abs(value) * 1000000.0 + 0.5);
        String sign = value < 0 && u != 0 ? "-" : "";
        String fraction = String.format(Locale.ROOT, "%06d", u % 1000000);
        int end = fraction.length();
        while (end > 0 && fraction.charAt(end - 1) == '0') {
            end--;
        }
        fraction = fraction.substring(0, end);
        return sign + (u / 1000000) + (fraction.isEmpty() ? "" : "." + fraction);
    }

    /**
     * RFC 5870 num: an optional minus, digits, optionally more digits.
     *
     * @param text One coordinate from the URI.
     * @return the value.
     */
    private static double GeoNumber(String text) {
        String body = text.startsWith("-") ? text.substring(1) : text;
        int dot = body.indexOf('.');
        String whole = dot < 0 ? body : body.substring(0, dot);
        String fraction = dot < 0 ? "" : body.substring(dot + 1);
        if (!Digits(whole) || (dot >= 0 && !Digits(fraction))) {
            throw new GPCException("GPC_GEO");
        }
        return Double.parseDouble(text);
    }

    /**
     * Whether a string is one or more ASCII digits and nothing else.
     *
     * @param text The string.
     * @return true if it is.
     */
    private static boolean Digits(String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (char character : text.toCharArray()) {
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }
}
