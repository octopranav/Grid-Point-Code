package ca.pranavpatel.algo.gridpointcode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
