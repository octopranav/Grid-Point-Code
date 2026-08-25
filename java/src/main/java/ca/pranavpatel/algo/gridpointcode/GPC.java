package ca.pranavpatel.algo.gridpointcode;

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

    /*  PART 4 : INTERNALS */

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
}
