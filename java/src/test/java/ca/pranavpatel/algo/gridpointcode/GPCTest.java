package ca.pranavpatel.algo.gridpointcode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Version 2, case by case, plus the version 1 codes that still have to resolve.
 *
 * <p>The worked examples come from SPEC.md rather than from running the code, so
 * a change in behaviour shows up as a failure here instead of quietly becoming
 * the new expected value.
 */
class GPCTest {

    /** The reason code carried by whatever the given call throws. */
    private static String reasonOf(Runnable call) {
        try {
            call.run();
        } catch (GPCException error) {
            return error.getReason();
        }
        return "nothing was thrown";
    }

    /*  Encoding  */

    @Test
    void producesTheWorkedExamples() {
        assertEquals("#G3RJM-98NM9", GPC.Encode(43.65, -79.38));
        assertEquals("#G3RJM-0M6DX", GPC.Encode(43.6426, -79.3871));
        assertEquals("#KDC8X-JM49X", GPC.Encode(23.0225, 72.5714));
        assertEquals("#6LK4X-NRP0R", GPC.Encode(-33.8568, 151.2153));
        assertEquals("#C8HKC-13C80", GPC.Encode(-13.1631, -72.545));
        assertEquals("#RDX9R-TN19T", GPC.Encode(64.1466, -21.9426));
    }

    @Test
    void encodesThePoles() {
        assertEquals("#P4444-PPPPP", GPC.Encode(90.0, 0.0));
        assertEquals("#3PPPP-00000", GPC.Encode(-90.0, 0.0));
    }

    @Test
    void givesTheAntimeridianOneCodeFromEitherEnd() {
        assertEquals("#F0000-00000", GPC.Encode(0.0, -180.0));
        assertEquals("#F0000-00000", GPC.Encode(0.0, 180.0));
        // 179.99999999999999 is exactly 180.0 once stored as a double.
        assertEquals("#F0000-00000", GPC.Encode(0.0, 179.99999999999999));
    }

    @Test
    void treatsNegativeZeroAsTheSamePoint() {
        assertEquals("#JPPPP-00000", GPC.Encode(0.0, 0.0));
        assertEquals("#JPPPP-00000", GPC.Encode(-0.0, -0.0));
        assertEquals("#JPPPP-00000", GPC.Encode(0.0, -0.0));
        assertEquals("#JPPPP-00000", GPC.Encode(-0.0, 0.0));
    }

    @Test
    void formatsACodeAsTheUnformattedOneWithSeparators() {
        assertEquals("G3RJM98NM9", GPC.Encode(43.65, -79.38, false));
        assertEquals("#G3RJM-98NM9", GPC.Encode(43.65, -79.38, true));
        assertEquals("#G3RJM-98NM9", GPC.FormatGPC("G3RJM98NM9"));
    }

    /** Level 1 produces the indices 0 to 23 only, so X is unreachable. */
    @Test
    void neverProducesACodeBeginningWithX() {
        for (int latitude = -90; latitude <= 90; latitude += 5) {
            for (int longitude = -180; longitude <= 180; longitude += 5) {
                assertNotEquals('X', GPC.Encode(latitude, longitude, false).charAt(0));
            }
        }
    }

    @Test
    void rejectsAnythingOutsideTheDomain() {
        assertEquals("LATITUDE", reasonOf(() -> GPC.Encode(90.00001, 0.0)));
        assertEquals("LATITUDE", reasonOf(() -> GPC.Encode(-90.00001, 0.0)));
        assertEquals("LATITUDE", reasonOf(() -> GPC.Encode(1000.0, 0.0)));
        assertEquals("LONGITUDE", reasonOf(() -> GPC.Encode(0.0, 180.00001)));
        assertEquals("LONGITUDE", reasonOf(() -> GPC.Encode(0.0, -180.00001)));
        assertEquals("LONGITUDE", reasonOf(() -> GPC.Encode(0.0, 1000.0)));
        assertEquals("LATITUDE", reasonOf(() -> GPC.Encode(Double.NaN, 0.0)));
        assertEquals("LATITUDE", reasonOf(() -> GPC.Encode(Double.POSITIVE_INFINITY, 0.0)));
        assertEquals("LONGITUDE", reasonOf(() -> GPC.Encode(0.0, Double.NaN)));
        assertEquals("LONGITUDE", reasonOf(() -> GPC.Encode(0.0, Double.NEGATIVE_INFINITY)));
    }

    @Test
    void includesTheEdgesOfTheDomain() {
        assertTrue(GPC.IsValid(90.0, 0.0).IsValid);
        assertTrue(GPC.IsValid(-90.0, 0.0).IsValid);
        assertTrue(GPC.IsValid(0.0, 180.0).IsValid);
        assertTrue(GPC.IsValid(0.0, -180.0).IsValid);
        assertTrue(GPC.IsValid(90.0, 180.0).IsValid);
        assertTrue(GPC.IsValid(-90.0, -180.0).IsValid);
    }

    /*  Decoding  */

    @Test
    void producesTheWorkedDecodes() {
        assertEquals(new Coordinates(43.650006, -79.380004), GPC.Decode("#G3RJM-98NM9"));
        assertEquals(new Coordinates(23.022501, 72.571407), GPC.Decode("#KDC8X-JM49X"));
        assertEquals(new Coordinates(-33.856808, 151.215314), GPC.Decode("#6LK4X-NRP0R"));
        assertEquals(new Coordinates(89.999988, 0.000015), GPC.Decode("#P4444-PPPPP"));
        assertEquals(new Coordinates(0.000012, 0.000015), GPC.Decode("#JPPPP-00000"));
    }

    @Test
    void returnsTheCentreOfTheCellItNames() {
        Area area = GPC.DecodeToArea("#G3RJM-98NM9");
        Coordinates centre = GPC.Decode("#G3RJM-98NM9");
        assertTrue(area.South < centre.Latitude && centre.Latitude < area.North);
        assertTrue(area.West < centre.Longitude && centre.Longitude < area.East);
    }

    /** A box is a closed region, so it may name +90 and +180. */
    @Test
    void reachesTheEdgeOfTheWorldInTheCornerCells() {
        assertEquals(90.0, GPC.DecodeToArea("P4444PPPPP").North);
        assertEquals(-90.0, GPC.DecodeToArea("3PPPP00000").South);
        assertEquals(-180.0, GPC.DecodeToArea("F000000000").West);
    }

    @Test
    void ignoresCaseAndSeparators() {
        Coordinates expected = GPC.Decode("G3RJM98NM9");
        assertEquals(expected, GPC.Decode("#G3RJM-98NM9"));
        assertEquals(expected, GPC.Decode("g3rjm98nm9"));
        assertEquals(expected, GPC.Decode("  G3RJM 98NM9  "));
        assertEquals(expected, GPC.Decode("#g3rjm-98nm9"));
        assertEquals(expected, GPC.Decode("--G3RJM98NM9##"));
    }

    @Test
    void carriesATypedReasonOnEveryRefusal() {
        assertEquals("GPC_RESERVED", reasonOf(() -> GPC.Decode("XG3RJ98NM9")));
        assertEquals("GPC_NULL", reasonOf(() -> GPC.Decode("")));
        assertEquals("GPC_NULL", reasonOf(() -> GPC.Decode("   ")));
        assertEquals("GPC_NULL", reasonOf(() -> GPC.Decode(null)));
        assertEquals("GPC_LENGTH", reasonOf(() -> GPC.Decode("G3RJM98NM")));
        assertEquals("GPC_LENGTH", reasonOf(() -> GPC.Decode("G3RJM98NM999")));
        assertEquals("GPC_CHAR", reasonOf(() -> GPC.Decode("G3RJM98NMQ")));
        assertEquals("GPC_CHAR", reasonOf(() -> GPC.Decode("G3RJM98NMU")));
        assertEquals("GPC_CHAR", reasonOf(() -> GPC.Decode("G3RJM98NMY")));
        assertEquals("GPC_CHECK", reasonOf(() -> GPC.Decode("#G3RJM-98NM9*5")));
    }

    @Test
    void refusesTheAreaOfAReservedCode() {
        assertEquals("GPC_RESERVED", reasonOf(() -> GPC.DecodeToArea("XG3RJ98NM9")));
    }

    /** Version 1 threw IllegalArgumentException. Existing handlers keep working. */
    @Test
    void theErrorIsAnIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> GPC.Decode("nonsense"));
    }

    /*  Parsing  */

    @Test
    void readsAConfusableLetterAsTheSymbolItStandsFor() {
        assertEquals(GPC.Decode("G3RJM98NM0"), GPC.Decode("G3RJM98NMO"));
        assertEquals(GPC.Decode("G3RJM98NM1"), GPC.Decode("G3RJM98NMI"));
        assertEquals(GPC.Decode("G3RJM98NM5"), GPC.Decode("G3RJM98NMS"));
        assertEquals(GPC.Decode("G3RJM98NM2"), GPC.Decode("G3RJM98NMZ"));
        assertEquals(GPC.Decode("G3RJM98NM8"), GPC.Decode("G3RJM98NMB"));
        assertEquals(GPC.Decode("G3RJM98NM4"), GPC.Decode("G3RJM98NMA"));
        assertEquals(GPC.Decode("G3RJM98NM3"), GPC.Decode("G3RJM98NME"));
        assertEquals(GPC.Decode("G3RJM98NMW"), GPC.Decode("G3RJM98NMV"));
    }

    @Test
    void neverReadsLAsOne() {
        assertTrue(GPC.IsValid("G3RJM98NML"));
        assertNotEquals(GPC.Decode("G3RJM98NM1"), GPC.Decode("G3RJM98NML"));
    }

    @Test
    void rejectsUQAndYRatherThanAliasingThem() {
        assertEquals(new Classification(CodeClass.INVALID, "GPC_CHAR"), GPC.Validate("G3RJM98NMU"));
        assertEquals(new Classification(CodeClass.INVALID, "GPC_CHAR"), GPC.Validate("G3RJM98NMQ"));
        assertEquals(new Classification(CodeClass.INVALID, "GPC_CHAR"), GPC.Validate("G3RJM98NMY"));
    }

    /**
     * Space, tab, line feed, vertical tab, form feed and carriage return, and
     * nothing wider. A port that also stripped the Unicode spaces would accept
     * what another port rejects, which is the whole thing the shared vectors
     * exist to prevent.
     */
    @Test
    void stripsTheAsciiWhitespaceSetAndNothingWider() {
        Coordinates expected = GPC.Decode("G3RJM98NM9");
        for (String space : new String[] {" ", "\t", "\n", String.valueOf((char)0x0B), "\f", "\r"}) {
            assertEquals(expected, GPC.Decode(space + "G3RJM" + space + "98NM9" + space), space);
            assertEquals(new Classification(CodeClass.INVALID, "GPC_NULL"),
                    GPC.Validate(space + space + space), space);
        }
        // U+00A0 is a space to Unicode and a symbol outside this alphabet.
        assertEquals(new Classification(CodeClass.INVALID, "GPC_CHAR"),
                GPC.Validate("\u00a03RJM98NM9"));
    }

    @Test
    void isIdempotent() {
        String once = GPC.Normalise("#g3rjm-98nm9")[0];
        String twice = GPC.Normalise(once)[0];
        assertEquals("G3RJM98NM9", once);
        assertEquals(once, twice);
    }

    /*  Classification  */

    @Test
    void sortsAStringIntoOneOfThreeClasses() {
        assertEquals(CodeClass.GEOMETRIC, GPC.Classify("#G3RJM-98NM9"));
        assertEquals(CodeClass.RESERVED, GPC.Classify("XG3RJ98NM9"));
        assertEquals(CodeClass.INVALID, GPC.Classify("nope"));
    }

    @Test
    void keepsAReservedCodeApartFromATypingError() {
        assertFalse(GPC.IsValid("XXXXXXXXXX"));
        assertEquals(new Classification(CodeClass.RESERVED, ""), GPC.Validate("XXXXXXXXXX"));
    }

    @Test
    void testsTheReasonsInOrder() {
        assertEquals(new Classification(CodeClass.INVALID, "GPC_NULL"), GPC.Validate(""));
        assertEquals(new Classification(CodeClass.INVALID, "GPC_LENGTH"), GPC.Validate("Q"));
        assertEquals(new Classification(CodeClass.INVALID, "GPC_CHAR"), GPC.Validate("QQQQQQQQQQ"));
    }

    /** Classify describes this grid, and eleven characters are not in it. */
    @Test
    void doesNotCallAVersion1CodeAVersion2Code() {
        assertEquals(new Classification(CodeClass.INVALID, "GPC_LENGTH"), GPC.Validate("#FN5G-CDKL-HDC"));
        assertFalse(GPC.IsValid("#FN5G-CDKL-HDC"));
        assertTrue(GPC.IsValidV1("#FN5G-CDKL-HDC").IsValid);
    }

    /*  The check character  */

    @Test
    void producesTheWorkedCheckCharacters() {
        assertEquals("T", GPC.CheckCharacter("#G3RJM-98NM9"));
        assertEquals("D", GPC.CheckCharacter("#KDC8X-JM49X"));
        assertEquals("2", GPC.CheckCharacter("#P4444-PPPPP"));
        assertEquals("M", GPC.CheckCharacter("#JPPPP-00000"));
    }

    @Test
    void acceptsAndStripsACorrectCheckCharacter() {
        assertEquals(GPC.Decode("#G3RJM-98NM9"), GPC.Decode("#G3RJM-98NM9*T"));
        assertTrue(GPC.IsValid("#G3RJM-98NM9*T"));
        assertEquals(CodeClass.GEOMETRIC, GPC.Classify("#g3rjm-98nm9*t"));
    }

    /** Never a silent ignore, and never valid-but-undecodable. */
    @Test
    void failsAWrongCheckCharacterEverywhere() {
        for (String text : new String[] {
                "#G3RJM-98NM9*5", "#G3RJM-98NM9*", "#G3RJM-98NM9*TT", "#G3RJM-98NM9*Q"}) {
            assertEquals(new Classification(CodeClass.INVALID, "GPC_CHECK"), GPC.Validate(text), text);
            assertFalse(GPC.IsValid(text), text);
            assertThrows(GPCException.class, () -> GPC.Decode(text), text);
        }
    }

    @Test
    void detectsEverySingleSymbolError() {
        final String alphabet = "0123456789CDFGHJKLMNPRTWX";
        final String code = "G3RJM98NM9";
        String check = GPC.CheckCharacter(code);
        for (int position = 0; position < 10; position++) {
            for (int i = 0; i < alphabet.length(); i++) {
                char symbol = alphabet.charAt(i);
                if (symbol == code.charAt(position)) {
                    continue;
                }
                String wrong = code.substring(0, position) + symbol + code.substring(position + 1);
                assertEquals(new Classification(CodeClass.INVALID, "GPC_CHECK"),
                        GPC.Validate(wrong + "*" + check), wrong);
            }
        }
    }

    @Test
    void detectsEveryAdjacentTransposition() {
        final String code = "G3RJM98NM9";
        String check = GPC.CheckCharacter(code);
        for (int position = 0; position < 9; position++) {
            if (code.charAt(position) == code.charAt(position + 1)) {
                continue;
            }
            String swapped = code.substring(0, position) + code.charAt(position + 1)
                    + code.charAt(position) + code.substring(position + 2);
            assertEquals(new Classification(CodeClass.INVALID, "GPC_CHECK"),
                    GPC.Validate(swapped + "*" + check), swapped);
        }
    }

    @Test
    void givesAReservedCodeACheckCharacterLikeAnyOther() {
        assertEquals(CodeClass.RESERVED,
                GPC.Classify("XG3RJ98NM9*" + GPC.CheckCharacter("XG3RJ98NM9")));
    }

    @Test
    void buildsTheCheckForm() {
        assertEquals("#G3RJM-98NM9*T", GPC.WithCheck("#G3RJM-98NM9"));
        assertEquals("#KDC8X-JM49X*D", GPC.WithCheck("#KDC8X-JM49X"));
        assertEquals("#P4444-PPPPP*2", GPC.WithCheck("#P4444-PPPPP"));
        assertEquals("#JPPPP-00000*M", GPC.WithCheck("#JPPPP-00000"));
    }

    @Test
    void honoursTheFormattedFlagOnWithCheck() {
        assertEquals("G3RJM98NM9*T", GPC.WithCheck("#G3RJM-98NM9", false));
    }

    @Test
    void withCheckAcceptsEveryFormTheParserDoes() {
        for (String text : new String[] {
                "#G3RJM-98NM9", "G3RJM98NM9", "g3rjm98nm9", "  G3RJM 98NM9  " }) {
            assertEquals("#G3RJM-98NM9*T", GPC.WithCheck(text), text);
        }
    }

    @Test
    void withCheckRecomputesRatherThanTrustingTheInput() {
        for (String text : new String[] {
                "#G3RJM-98NM9*T", "#G3RJM-98NM9*5", "#G3RJM-98NM9*" }) {
            assertEquals("#G3RJM-98NM9*T", GPC.WithCheck(text), text);
        }
    }

    @Test
    void withCheckOutputValidates() {
        String code = GPC.Encode(43.65, -79.38, false);
        assertTrue(GPC.IsValid(GPC.WithCheck(code)));
        assertEquals(GPC.Decode(code), GPC.Decode(GPC.WithCheck(code)));
    }

    @Test
    void withCheckGivesAReservedCodeACheckForm() {
        assertEquals("#XG3RJ-98NM9*6", GPC.WithCheck("XG3RJ98NM9"));
    }

    @Test
    void withCheckRejectsWhatIsNotACode() {
        assertEquals("GPC_LENGTH",
                assertThrows(GPCException.class, () -> GPC.WithCheck("G3RJM98NM")).getReason());
        assertEquals("GPC_LENGTH",
                assertThrows(GPCException.class, () -> GPC.WithCheck("G3RJM98NM99")).getReason());
        assertEquals("GPC_CHAR",
                assertThrows(GPCException.class, () -> GPC.WithCheck("G3RJM98NMQ")).getReason());
        assertEquals("GPC_NULL",
                assertThrows(GPCException.class, () -> GPC.WithCheck("")).getReason());
    }

    /*  Version 1 codes  */

    @Test
    void dispatchesOnLength() {
        assertEquals(new Coordinates(43.65, -79.38), GPC.Decode("#FN5G-CDKL-HDC"));
        assertEquals(new Coordinates(43.650006, -79.380004), GPC.Decode("#G3RJM-98NM9"));
    }

    @Test
    void agreesWithTheExplicitEntryPoint() {
        for (String code : new String[] {"#FN5G-CDKL-HDC", "FN5GCDKLHDC", "#HG9K-PCVH-DPV"}) {
            assertEquals(GPC.Decode(code), GPC.DecodeV1(code), code);
        }
    }

    /**
     * Version 1 returns the corner of its cell, where version 2 returns the
     * centre. The difference is deliberate: the value is the one every version 1
     * release has returned.
     */
    @Test
    void returnsTheCornerOfItsCell() {
        assertEquals(new Coordinates(0.0, 0.0), GPC.DecodeV1("DCCCCCCCCCC"));
        assertEquals(new Coordinates(89.99999, 179.99999), GPC.DecodeV1("HG9KPCVHDPV"));
        assertEquals(new Coordinates(-89.99999, -179.99999), GPC.DecodeV1("HG9PJLHJX69"));
    }

    @Test
    void reportsVersion1ValidityWithTheExpectedReason() {
        assertTrue(GPC.IsValidV1("#FN5G-CDKL-HDC").IsValid);
        assertTrue(GPC.IsValidV1("DCCCCCCCCCC").IsValid);
        assertEquals("GPC_NULL", GPC.IsValidV1("").Message);
        assertEquals("GPC_NULL", GPC.IsValidV1("   ").Message);
        assertEquals("GPC_NULL", GPC.IsValidV1(null).Message);
        assertEquals("GPC_LENGTH", GPC.IsValidV1("ABC").Message);
        assertEquals("GPC_LENGTH", GPC.IsValidV1("FN5GCDKLHDCC").Message);
        assertEquals("GPC_CHAR", GPC.IsValidV1("FN5GCDKLHDA").Message);
        assertEquals("GPC_RANGE", GPC.IsValidV1("CCCCCCCCCCC").Message);
        assertEquals("GPC_RANGE", GPC.IsValidV1("YYYYYYYYYYY").Message);
    }

    /**
     * V and Y are version 1 symbols. Version 2 excludes both, reads V as W and
     * rejects Y outright, and none of that may reach this path.
     */
    @Test
    void neverLetsTheVersion2AliasTableTouchAVersion1Code() {
        assertTrue(GPC.IsValidV1("#HG9K-PCVH-DPV").IsValid);
        assertEquals(new Coordinates(89.99999, 179.99999), GPC.Decode("#HG9K-PCVH-DPV"));
        assertEquals("GPC_RANGE", GPC.IsValidV1("9999999999Y").Message);
    }

    /**
     * The dispatch is on length alone, so an eleven-character string that happens
     * to be a valid version 1 code decodes as one -- even when what the caller
     * meant was a version 2 code with a character too many. This is the price of
     * carrying both formats in one install, and it is why section 15.2 says to
     * show the decoded point on a map before acting on it.
     */
    @Test
    void readsElevenCharactersAsVersion1WhateverWasMeant() {
        assertEquals(new Classification(CodeClass.INVALID, "GPC_LENGTH"), GPC.Validate("G3RJM98NM99"));
        assertTrue(GPC.IsValidV1("G3RJM98NM99").IsValid);
        assertEquals(GPC.DecodeV1("G3RJM98NM99"), GPC.Decode("G3RJM98NM99"));
    }

    /*  Sections 18.1 and 18.2. Cells, and the prefix test.  */

    @Test
    void takesACellAsAPrefix() {
        assertEquals("G3R", GPC.Cell("#G3RJM-98NM9", 3));
        assertEquals("G3RJM", GPC.Cell("#G3RJM-98NM9", 5));
        assertEquals("G3RJM98NM9", GPC.Cell("#G3RJM-98NM9", 10));
    }

    @Test
    void normalisesBeforeSlicing() {
        assertEquals("G3RJM1", GPC.Cell("#g3rjm-i8nm9", 6));
    }

    @Test
    void takesACellOfACell() {
        assertEquals("G3", GPC.Cell("G3RJM", 2));
    }

    /**
     * A cell comes back bare. Ten characters is a code and anything shorter is a
     * region; a cell written as a code would claim to be one.
     */
    @Test
    void returnsACellBare() {
        for (int level = 1; level <= 10; level++) {
            String cell = GPC.Cell("#G3RJM-98NM9", level);
            assertTrue(cell.indexOf('#') < 0, cell);
            assertTrue(cell.indexOf('-') < 0, cell);
        }
    }

    @Test
    void refusesALevelOutsideOneToTen() {
        for (int level : new int[] {0, 11, -1, 100}) {
            assertEquals("GPC_LEVEL",
                assertThrows(GPCException.class, () -> GPC.Cell("G3RJM98NM9", level)).getReason());
        }
    }

    @Test
    void refusesACellShorterThanTheLevelAskedFor() {
        assertEquals("GPC_LENGTH",
            assertThrows(GPCException.class, () -> GPC.Cell("G3R", 5)).getReason());
    }

    @Test
    void refusesAReservedCellWithItsOwnReason() {
        for (String text : new String[] {"XG3RJ", "XG3RJ98NM9"}) {
            assertEquals("GPC_RESERVED",
                assertThrows(GPCException.class, () -> GPC.Cell(text, 3)).getReason());
        }
    }

    @Test
    void answersContainmentWithThePrefixTest() {
        assertTrue(GPC.Contains("G3RJM", "G3RJM98NM9"));
        assertTrue(GPC.Contains("G", "G3RJM98NM9"));
        assertFalse(GPC.Contains("G3RJD", "G3RJM98NM9"));
    }

    @Test
    void holdsContainmentBetweenCellsInOneDirectionOnly() {
        assertTrue(GPC.Contains("G3R", "G3RJM"));
        assertFalse(GPC.Contains("G3RJM", "G3R"));
    }

    @Test
    void normalisesBothSidesOfContainment() {
        assertTrue(GPC.Contains("#g3rjm", "#G3RJM-98NM9"));
    }

    /*  Section 18.3. Neighbours.  */

    @Test
    void findsEightNeighboursAwayFromThePoles() {
        assertEquals(8, GPC.Neighbours("G3RJM98NM9").size());
        assertEquals(8, GPC.Neighbours("G3RJM").size());
    }

    /**
     * Rows do not wrap, so the three that would lie off the grid are absent
     * rather than present and empty.
     */
    @Test
    void findsFiveNeighboursInAPolarRow() {
        assertEquals(5, GPC.Neighbours("#P4444-PPPPP").size());
        assertEquals(5, GPC.Neighbours("#3PPPP-00000").size());
    }

    @Test
    void returnsNeighboursOfTheSameLength() {
        for (int level = 1; level <= 10; level++) {
            String cell = GPC.Cell("#G3RJM-98NM9", level);
            for (String neighbour : GPC.Neighbours(cell)) {
                assertEquals(level, neighbour.length(), neighbour);
            }
        }
    }

    /**
     * The first column of the grid: its western neighbour is the last column. No
     * amount of string arithmetic would have found it -- the two share no
     * characters at all.
     */
    @Test
    void wrapsColumnsAtTheAntimeridian() {
        String first = GPC.Encode(0.0, -180.0, false);
        String west = GPC.Neighbours(first).get(6);
        assertEquals(GPC.Encode(0.0, 179.99999, false), west);
        assertNotEquals(first.charAt(0), west.charAt(0));
    }

    @Test
    void keepsTheNeighbourOrderFixed() {
        long[] grid = GPC.DecodeToGrid("G3RJM98NM9");
        int[] steps = {1, 0, 1, 1, 0, 1, -1, 1, -1, 0, -1, -1, 0, -1, 1, -1};
        List<String> expected = new ArrayList<>(8);
        for (int i = 0; i < steps.length; i += 2) {
            expected.add(GPC.GridToCode(grid[0] + steps[i], grid[1] + steps[i + 1]));
        }
        assertEquals(expected, GPC.Neighbours("G3RJM98NM9"));
    }

    @Test
    void neverIncludesTheCellItself() {
        assertFalse(GPC.Neighbours("G3RJM").contains("G3RJM"));
    }

    /*  Section 18.4, against the table of section 3.  */

    @Test
    void reproducesTheCellDimensionTable() {
        double[][] table = {{1, 5000.9, 6679.2}, {2, 1000.2, 1335.8}, {3, 200.0, 267.2},
                            {4, 40.0, 53.4}, {5, 8.0, 10.7}};
        for (double[] row : table) {
            Dimensions dimensions = GPC.CellDimensions((int)row[0]);
            assertEquals(row[1], Math.round(dimensions.NorthSouth / 100.0) / 10.0);
            assertEquals(row[2], Math.round(dimensions.EastWest / 100.0) / 10.0);
        }
    }

    @Test
    void measuresADoorwayAtLevelTen() {
        Dimensions dimensions = GPC.CellDimensions(10);
        assertEquals(2.6, Math.round(dimensions.NorthSouth * 10.0) / 10.0);
        assertEquals(3.4, Math.round(dimensions.EastWest * 10.0) / 10.0);
    }

    @Test
    void keepsTheAspectRatioAtThreeQuarters() {
        for (int level = 1; level <= 10; level++) {
            Dimensions dimensions = GPC.CellDimensions(level);
            assertEquals(0.75, dimensions.LatitudeSpan / dimensions.LongitudeSpan, 1e-12);
        }
    }

    @Test
    void refusesADimensionLevelOutsideOneToTen() {
        assertEquals("GPC_LEVEL",
            assertThrows(GPCException.class, () -> GPC.CellDimensions(0)).getReason());
    }

    /*  Section 18.5. Distance, compared to a tolerance and never to equality.  */

    @Test
    void isZeroFromACellToItself() {
        assertEquals(0.0, GPC.Distance("G3RJM98NM9", "G3RJM98NM9"));
    }

    @Test
    void isSymmetric() {
        assertEquals(GPC.Distance("G3RJM98NM9", "6LK4XNRP0R"),
                     GPC.Distance("6LK4XNRP0R", "G3RJM98NM9"));
    }

    @Test
    void makesPoleToPoleHalfTheMeridian() {
        assertEquals(20015.1,
            Math.round(GPC.Distance("#P4444-PPPPP", "#3PPPP-00000") / 100.0) / 10.0);
    }

    /** Antipodal cells need the clamp, or arc sine returns NaN. */
    @Test
    void doesNotProduceANanForAntipodalCells() {
        double metres = GPC.Distance(GPC.Encode(0.0, 0.0, false), GPC.Encode(0.0, 180.0, false));
        assertEquals(20015.1, Math.round(metres / 100.0) / 10.0);
    }

    @Test
    void acceptsCellsOfDifferentLevels() {
        assertTrue(GPC.Distance("G3RJM", "G3RJM98NM9") < 7000.0);
    }

    /*  Section 12. The short form.  */

    @Test
    void isTheSecondPrintedGroup() {
        assertEquals("98NM9", GPC.Shorten("#G3RJM-98NM9"));
        assertEquals("98NM9", GPC.Shorten("G3RJM98NM9"));
    }

    @Test
    void recoversWithOrWithoutTheLeadingDash() {
        for (String text : new String[] {"98NM9", "-98NM9", " -98nm9 "}) {
            assertEquals("#G3RJM-98NM9", GPC.RecoverShort(text, 43.66, -79.39), text);
        }
    }

    @Test
    void isExactWithinHalfALevelFiveCell() {
        String code = GPC.Encode(43.65, -79.38, false);
        String shortForm = GPC.Shorten(code);
        for (double dLatitude : new double[] {-0.0359, 0.0, 0.0359}) {
            for (double dLongitude : new double[] {-0.0479, 0.0, 0.0479}) {
                assertEquals(code, GPC.RecoverShort(shortForm,
                    43.65 + dLatitude, -79.38 + dLongitude, false));
            }
        }
    }

    /**
     * A reference east of the antimeridian recovering a code west of it. The
     * column arithmetic wraps; the row arithmetic must not.
     */
    @Test
    void crossesTheAntimeridian() {
        String code = GPC.Encode(0.0, -179.99, false);
        assertEquals(code, GPC.RecoverShort(GPC.Shorten(code), 0.0, 179.995, false));
    }

    @Test
    void refusesAShortFormThatIsNotFiveSymbols() {
        for (String text : new String[] {"98NM", "98NM99"}) {
            assertEquals("GPC_LENGTH",
                assertThrows(GPCException.class,
                    () -> GPC.RecoverShort(text, 43.65, -79.38)).getReason());
        }
    }

    @Test
    void refusesAReferenceOutsideTheDomain() {
        assertEquals("LATITUDE",
            assertThrows(GPCException.class,
                () -> GPC.RecoverShort("98NM9", 91.0, 0.0)).getReason());
    }

    /*  Section 15.3. Corrections.  */

    @Test
    void findsTheTrueCodeAndRanksItFirst() {
        String code = GPC.Encode(43.65, -79.38, false);
        for (int position = 0; position < 10; position++) {
            String wrong = code.substring(0, position)
                + (code.charAt(position) == '0' ? '1' : '0') + code.substring(position + 1);
            assertEquals(code, GPC.SuggestCorrections(wrong, 43.65, -79.38, 6, false).get(0), wrong);
        }
    }

    /** The whole point: a code with a wrong character is what this is for. */
    @Test
    void takesACodeThatDecodesNowhereNearTheReference() {
        assertTrue(GPC.SuggestCorrections("03RJM98NM9", 43.65, -79.38, 6, false)
            .contains(GPC.Encode(43.65, -79.38, false)));
    }

    @Test
    void neverSuggestsAReservedCode() {
        for (String candidate : GPC.SuggestCorrections("XG3RJ98NM9", 43.65, -79.38, 4, false)) {
            assertNotEquals('X', candidate.charAt(0));
        }
    }

    @Test
    void returnsFewerCandidatesAtANarrowerLevel() {
        int wide = GPC.SuggestCorrections("G3RJM98NM8", 43.65, -79.38, 4, false).size();
        int narrow = GPC.SuggestCorrections("G3RJM98NM8", 43.65, -79.38, 8, false).size();
        assertTrue(wide > narrow, wide + " should exceed " + narrow);
    }

    @Test
    void refusesACodeThatWillNotNormaliseToTenSymbols() {
        assertEquals("GPC_LENGTH",
            assertThrows(GPCException.class,
                () -> GPC.SuggestCorrections("G3RJM98NM", 43.65, -79.38)).getReason());
    }

    /** P4444PPPPP yields 242 rather than 249, and is not padded back. */
    @Test
    void neverPadsTheListBackWithDuplicates() {
        List<String> every = GPC.SuggestCorrections("P4444PPPPP", 90.0, 0.0, 1, false);
        assertEquals(every.size(), new HashSet<>(every).size());
    }

    /*  Section 13. The integer form.  */

    @Test
    void roundTripsTheIntegerForm() {
        String code = GPC.Encode(43.65, -79.38, false);
        assertEquals(code, GPC.FromInteger(GPC.ToInteger(code), false));
    }

    @Test
    void placesTheFirstAndLastCodesAtTheEnds() {
        assertEquals(0L, GPC.ToInteger("0000000000"));
        assertEquals(95_367_431_640_624L, GPC.ToInteger("XXXXXXXXXX"));
    }

    @Test
    void putsTheReservedNamespaceAtTheTop() {
        long floor = 91_552_734_375_000L;
        assertTrue(GPC.ToInteger("X000000000") >= floor);
        assertTrue(GPC.ToInteger("W999999999") < floor);
    }

    @Test
    void refusesAValueOutsideTheRange() {
        for (long value : new long[] {-1L, 95_367_431_640_625L}) {
            assertEquals("GPC_RANGE",
                assertThrows(GPCException.class, () -> GPC.FromInteger(value)).getReason());
        }
    }

    /*  Section 17. Screening: it reports and never blocks.  */

    @Test
    void returnsTheVersionEvenWhenNothingMatched() {
        Screening screening = GPC.Screen("G3RJM98NM9");
        assertNotEquals("", screening.Version);
        assertTrue(screening.Spans.isEmpty());
    }

    @Test
    void reportsTheSpanOfAMatch() {
        assertEquals(List.of(new Span(1, 4)), GPC.Screen("GN4T000000").Spans);
    }

    /** An X in position 1 does not stop the other nine spelling something. */
    @Test
    void screensAReservedCodeLikeAnyOther() {
        assertEquals(List.of(new Span(2, 4)), GPC.Screen("XGN4T00000").Spans);
    }

    /** Whatever the list says, the code still encodes, decodes and validates. */
    @Test
    void screeningNeverBlocks() {
        assertTrue(GPC.IsValid("GN4T000000"));
        assertEquals(CodeClass.GEOMETRIC, GPC.Classify("GN4T000000"));
        Coordinates point = GPC.Decode("GN4T000000");
        assertEquals("GN4T000000", GPC.Encode(point.Latitude, point.Longitude, false));
    }

    @Test
    void screensTheFormattedAndBareFormsAlike() {
        assertEquals(GPC.Screen("GN4T000000"), GPC.Screen("#GN4T0-00000"));
    }

    /*  Batch and streaming, for dataset work.  */

    @Test
    void encodesABatch() {
        List<Coordinates> points = List.of(new Coordinates(43.65, -79.38),
                                           new Coordinates(0.0, 0.0));
        assertEquals(List.of("G3RJM98NM9", "JPPPP00000"), GPC.EncodeAll(points, false));
    }

    @Test
    void decodesABatch() {
        assertEquals(List.of(new Coordinates(43.650006, -79.380004)),
                     GPC.DecodeAll(List.of("#G3RJM-98NM9")));
    }

    /**
     * A stream is lazy, so a bad row throws where it is reached rather than
     * before -- which is what lets a caller handle failures row by row.
     */
    @Test
    void streamsLazily() {
        List<Coordinates> points = List.of(new Coordinates(43.65, -79.38),
                                           new Coordinates(91.0, 0.0));
        assertEquals("G3RJM98NM9",
            GPC.EncodeStream(points.stream(), false).findFirst().orElseThrow());
        assertEquals("LATITUDE",
            assertThrows(GPCException.class,
                () -> GPC.EncodeStream(points.stream(), false).toList()).getReason());
    }

    @Test
    void stopsABatchAtTheFirstBadRow() {
        List<Coordinates> points = List.of(new Coordinates(43.65, -79.38),
                                           new Coordinates(0.0, 181.0));
        assertEquals("LONGITUDE",
            assertThrows(GPCException.class, () -> GPC.EncodeAll(points, true)).getReason());
    }

    @Test
    void handlesAnEmptySequence() {
        assertTrue(GPC.EncodeAll(List.of(), true).isEmpty());
        assertTrue(GPC.DecodeAll(List.of()).isEmpty());
    }

    /*  Section 18.6. Grid indices.  */

    @Test
    void agreesWithToGrid() {
        assertArrayEquals(GPC.ToGrid(43.65, -79.38), GPC.DecodeToGrid("#G3RJM-98NM9"));
    }

    @Test
    void reachesTheCornersOfTheGrid() {
        assertArrayEquals(new long[] {0L, 0L},
            GPC.DecodeToGrid(GPC.Encode(-90.0, -180.0, false)));
        assertArrayEquals(new long[] {7812499L, 11718749L},
            GPC.DecodeToGrid(GPC.Encode(90.0, 179.99999, false)));
    }

    @Test
    void refusesAReservedCodeForGridIndices() {
        assertEquals("GPC_RESERVED",
            assertThrows(GPCException.class, () -> GPC.DecodeToGrid("XG3RJ98NM9")).getReason());
    }

    /*  Section 19. Coordinate conversions.  */

    @Test
    void writesTheWorkedExample() {
        assertEquals("43°39'00.00\"N, 79°22'48.00\"W", GPC.ToDMS(43.65, -79.38));
    }

    /** All four signed zeroes name the origin, so none of them is negative. */
    @Test
    void doesNotTreatNegativeZeroAsNegative() {
        assertEquals("0°00'00.00\"N, 0°00'00.00\"E", GPC.ToDMS(-0.0, -0.0));
    }

    /** Rounding the whole value first is what carries the seconds. */
    @Test
    void carriesSecondsIntoTheNextMinute() {
        assertEquals("1°00'00.00\"N, 0°00'00.00\"E", GPC.ToDMS(1.0 - 1e-9, 0.0));
    }

    @Test
    void readsItsOwnDmsBack() {
        assertEquals(new Coordinates(43.65, -79.38), GPC.FromDMS(GPC.ToDMS(43.65, -79.38)));
    }

    @Test
    void acceptsTheWiderDmsForms() {
        assertEquals(new Coordinates(43.65, -79.38), GPC.FromDMS("43d39m0s N 79d22m48s W"));
        assertEquals(new Coordinates(43.0, -79.0), GPC.FromDMS("43°N 79°W"));
        assertEquals(new Coordinates(-43.0, 79.0), GPC.FromDMS("-43°, +79°"));
    }

    @Test
    void refusesWhatTheDmsGrammarDoesNotAccept() {
        String[] bad = {
            "43°39'00.00\"N",                  // one axis only
            "43 39",                                // no unit markers
            "-43°N, 79°W",                // a sign and a hemisphere
            "43°W, 79°N",                 // the axes crossed
            "43°60'N, 0°0'E",             // sixty minutes
            "43°39'60.0\"N, 0°0'0\"E",    // sixty seconds
            "43°N, 79°W extra",           // trailing text
        };
        for (String text : bad) {
            assertEquals("GPC_DMS",
                assertThrows(GPCException.class, () -> GPC.FromDMS(text)).getReason(), text);
        }
    }

    @Test
    void refusesADmsValueOutsideTheDomain() {
        assertEquals("LATITUDE",
            assertThrows(GPCException.class,
                () -> GPC.FromDMS("91°N, 0°E")).getReason());
    }

    /**
     * Decode returns a cell centre, which sits eight times further from the
     * nearest boundary than this rounding can move it.
     */
    @Test
    void letsADecodedCodeSurviveTheDmsRoundTrip() {
        for (Coordinates point : conversionPoints()) {
            String code = GPC.Encode(point.Latitude, point.Longitude, false);
            Coordinates centre = GPC.Decode(code);
            Coordinates back = GPC.FromDMS(GPC.ToDMS(centre.Latitude, centre.Longitude));
            assertEquals(code, GPC.Encode(back.Latitude, back.Longitude, false));
        }
    }

    @Test
    void writesAGeoUri() {
        assertEquals("geo:43.650006,-79.380004", GPC.ToGeoURI(43.650006, -79.380004));
    }

    @Test
    void dropsTrailingZerosAndThePoint() {
        assertEquals("geo:43.65,-79.38", GPC.ToGeoURI(43.65, -79.38));
        assertEquals("geo:43,-79", GPC.ToGeoURI(43.0, -79.0));
        assertEquals("geo:0,0", GPC.ToGeoURI(-0.0, -0.0));
    }

    @Test
    void readsItsOwnUriBack() {
        assertEquals(new Coordinates(43.650006, -79.380004),
                     GPC.FromGeoURI("geo:43.650006,-79.380004"));
    }

    @Test
    void dropsTheAltitudeAndTheParameters() {
        assertEquals(new Coordinates(43.65, -79.38), GPC.FromGeoURI("geo:43.65,-79.38,76.1"));
        assertEquals(new Coordinates(43.65, -79.38), GPC.FromGeoURI("geo:43.65,-79.38;u=35"));
        assertEquals(new Coordinates(43.65, -79.38),
                     GPC.FromGeoURI("GEO:43.65,-79.38;crs=WGS84"));
    }

    /**
     * Reading a code as though it were on another datum would put it in the
     * wrong place, quietly.
     */
    @Test
    void refusesAnotherDatumRatherThanIgnoringIt() {
        assertEquals("GPC_GEO",
            assertThrows(GPCException.class,
                () -> GPC.FromGeoURI("geo:43.65,-79.38;crs=nad83")).getReason());
    }

    @Test
    void refusesAUriTheGrammarDoesNotAccept() {
        String[] bad = {"geo:43.65", "43.65,-79.38", "geo:+43.65,-79.38",
                        "geo:43.65,-79.38,1,2", "geo:1e2,0"};
        for (String text : bad) {
            assertEquals("GPC_GEO",
                assertThrows(GPCException.class, () -> GPC.FromGeoURI(text)).getReason(), text);
        }
    }

    @Test
    void letsADecodedCodeSurviveTheGeoUriRoundTrip() {
        for (Coordinates point : conversionPoints()) {
            String code = GPC.Encode(point.Latitude, point.Longitude, false);
            Coordinates centre = GPC.Decode(code);
            Coordinates back = GPC.FromGeoURI(GPC.ToGeoURI(centre.Latitude, centre.Longitude));
            assertEquals(code, GPC.Encode(back.Latitude, back.Longitude, false));
        }
    }

    /** The corners of the domain, plus two landmarks. */
    private static List<Coordinates> conversionPoints() {
        return List.of(new Coordinates(43.65, -79.38), new Coordinates(-33.8568, 151.2153),
                       new Coordinates(90.0, 0.0), new Coordinates(-90.0, 0.0),
                       new Coordinates(0.0, -180.0));
    }
}
