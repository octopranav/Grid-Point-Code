//  Copyright 2017 Pranavkumar Patel
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

using System;
using System.Collections.Generic;
using Xunit;

[assembly: CLSCompliant(false)]
namespace Ca.Pranavpatel.Algo.GridPointCode.Tests {
    /// <summary>
    /// Version 2, case by case, plus the version 1 codes that still have to
    /// resolve.
    /// <para>
    /// The worked examples come from SPEC.md rather than from running the code,
    /// so a change in behaviour shows up as a failure here instead of quietly
    /// becoming the new expected value.
    /// </para>
    /// </summary>
    public class TestGPC {

        /// <summary>The reason code carried by whatever the given call throws.</summary>
        /// <param name="call">The call under test.</param>
        /// <returns>The reason, or a note that nothing was thrown.</returns>
        private static string ReasonOf(Action call) {
            try {
                call();
            }
            catch (GPCException error) {
                return error.Reason;
            }
            catch (ArgumentOutOfRangeException error) {
                return error.ParamName;
            }
            return "nothing was thrown";
        }

        /*  Encoding  */

        /// <summary>The worked examples of section 5.5.</summary>
        [Fact]
        public void ProducesTheWorkedExamples() {
            Assert.Equal("#G3RJM-98NM9", GPC.Encode(43.65, -79.38));
            Assert.Equal("#G3RJM-0M6DX", GPC.Encode(43.6426, -79.3871));
            Assert.Equal("#KDC8X-JM49X", GPC.Encode(23.0225, 72.5714));
            Assert.Equal("#6LK4X-NRP0R", GPC.Encode(-33.8568, 151.2153));
            Assert.Equal("#C8HKC-13C80", GPC.Encode(-13.1631, -72.545));
            Assert.Equal("#RDX9R-TN19T", GPC.Encode(64.1466, -21.9426));
        }

        /// <summary>The poles encode, where version 1 rejected them.</summary>
        [Fact]
        public void EncodesThePoles() {
            Assert.Equal("#P4444-PPPPP", GPC.Encode(90.0, 0.0));
            Assert.Equal("#3PPPP-00000", GPC.Encode(-90.0, 0.0));
        }

        /// <summary>Both ends of the antimeridian give the one code.</summary>
        [Fact]
        public void GivesTheAntimeridianOneCodeFromEitherEnd() {
            Assert.Equal("#F0000-00000", GPC.Encode(0.0, -180.0));
            Assert.Equal("#F0000-00000", GPC.Encode(0.0, 180.0));
            // 179.99999999999999 is exactly 180.0 once stored as a double.
            Assert.Equal("#F0000-00000", GPC.Encode(0.0, 179.99999999999999));
        }

        /// <summary>Negative zero names the same point as positive zero.</summary>
        [Fact]
        public void TreatsNegativeZeroAsTheSamePoint() {
            Assert.Equal("#JPPPP-00000", GPC.Encode(0.0, 0.0));
            Assert.Equal("#JPPPP-00000", GPC.Encode(-0.0, -0.0));
            Assert.Equal("#JPPPP-00000", GPC.Encode(0.0, -0.0));
            Assert.Equal("#JPPPP-00000", GPC.Encode(-0.0, 0.0));
        }

        /// <summary>Formatting adds separators and changes nothing else.</summary>
        [Fact]
        public void FormatsACodeAsTheUnformattedOneWithSeparators() {
            Assert.Equal("G3RJM98NM9", GPC.Encode(43.65, -79.38, false));
            Assert.Equal("#G3RJM-98NM9", GPC.Encode(43.65, -79.38, true));
            Assert.Equal("#G3RJM-98NM9", GPC.FormatGPC("G3RJM98NM9"));
        }

        /// <summary>Level 1 produces the indices 0 to 23 only, so X is unreachable.</summary>
        [Fact]
        public void NeverProducesACodeBeginningWithX() {
            for (int latitude = -90; latitude <= 90; latitude += 5) {
                for (int longitude = -180; longitude <= 180; longitude += 5) {
                    Assert.NotEqual('X', GPC.Encode(latitude, longitude, false)[0]);
                }
            }
        }

        /// <summary>Anything outside the domain is rejected, never wrapped.</summary>
        [Fact]
        public void RejectsAnythingOutsideTheDomain() {
            Assert.Equal("LATITUDE", ReasonOf(() => GPC.Encode(90.00001, 0.0)));
            Assert.Equal("LATITUDE", ReasonOf(() => GPC.Encode(-90.00001, 0.0)));
            Assert.Equal("LATITUDE", ReasonOf(() => GPC.Encode(1000.0, 0.0)));
            Assert.Equal("LONGITUDE", ReasonOf(() => GPC.Encode(0.0, 180.00001)));
            Assert.Equal("LONGITUDE", ReasonOf(() => GPC.Encode(0.0, -180.00001)));
            Assert.Equal("LONGITUDE", ReasonOf(() => GPC.Encode(0.0, 1000.0)));
            Assert.Equal("LATITUDE", ReasonOf(() => GPC.Encode(double.NaN, 0.0)));
            Assert.Equal("LATITUDE", ReasonOf(() => GPC.Encode(double.PositiveInfinity, 0.0)));
            Assert.Equal("LONGITUDE", ReasonOf(() => GPC.Encode(0.0, double.NaN)));
            Assert.Equal("LONGITUDE", ReasonOf(() => GPC.Encode(0.0, double.NegativeInfinity)));
        }

        /// <summary>The domain includes its own edges.</summary>
        [Fact]
        public void IncludesTheEdgesOfTheDomain() {
            Assert.Equal((true, string.Empty), GPC.IsValid(90.0, 0.0));
            Assert.Equal((true, string.Empty), GPC.IsValid(-90.0, 0.0));
            Assert.Equal((true, string.Empty), GPC.IsValid(0.0, 180.0));
            Assert.Equal((true, string.Empty), GPC.IsValid(0.0, -180.0));
            Assert.Equal((true, string.Empty), GPC.IsValid(90.0, 180.0));
            Assert.Equal((true, string.Empty), GPC.IsValid(-90.0, -180.0));
        }

        /*  Decoding  */

        /// <summary>The worked examples of section 6.4.</summary>
        [Fact]
        public void ProducesTheWorkedDecodes() {
            Assert.Equal((43.650006, -79.380004), GPC.Decode("#G3RJM-98NM9"));
            Assert.Equal((23.022501, 72.571407), GPC.Decode("#KDC8X-JM49X"));
            Assert.Equal((-33.856808, 151.215314), GPC.Decode("#6LK4X-NRP0R"));
            Assert.Equal((89.999988, 0.000015), GPC.Decode("#P4444-PPPPP"));
            Assert.Equal((0.000012, 0.000015), GPC.Decode("#JPPPP-00000"));
        }

        /// <summary>A code decodes to the centre of the cell it names.</summary>
        [Fact]
        public void ReturnsTheCentreOfTheCellItNames() {
            (double south, double west, double north, double east) = GPC.DecodeToArea("#G3RJM-98NM9");
            (double latitude, double longitude) = GPC.Decode("#G3RJM-98NM9");
            Assert.True(south < latitude && latitude < north);
            Assert.True(west < longitude && longitude < east);
        }

        /// <summary>A box is a closed region, so it may name +90 and +180.</summary>
        [Fact]
        public void ReachesTheEdgeOfTheWorldInTheCornerCells() {
            Assert.Equal(90.0, GPC.DecodeToArea("P4444PPPPP").North);
            Assert.Equal(-90.0, GPC.DecodeToArea("3PPPP00000").South);
            Assert.Equal(-180.0, GPC.DecodeToArea("F000000000").West);
        }

        /// <summary>Case and separators do not matter.</summary>
        [Fact]
        public void IgnoresCaseAndSeparators() {
            (double Latitude, double Longitude) expected = GPC.Decode("G3RJM98NM9");
            Assert.Equal(expected, GPC.Decode("#G3RJM-98NM9"));
            Assert.Equal(expected, GPC.Decode("g3rjm98nm9"));
            Assert.Equal(expected, GPC.Decode("  G3RJM 98NM9  "));
            Assert.Equal(expected, GPC.Decode("#g3rjm-98nm9"));
            Assert.Equal(expected, GPC.Decode("--G3RJM98NM9##"));
        }

        /// <summary>Every refusal carries a typed reason.</summary>
        [Fact]
        public void CarriesATypedReasonOnEveryRefusal() {
            Assert.Equal("GPC_RESERVED", ReasonOf(() => GPC.Decode("XG3RJ98NM9")));
            Assert.Equal("GPC_NULL", ReasonOf(() => GPC.Decode(string.Empty)));
            Assert.Equal("GPC_NULL", ReasonOf(() => GPC.Decode("   ")));
            Assert.Equal("GPC_NULL", ReasonOf(() => GPC.Decode(null)));
            Assert.Equal("GPC_LENGTH", ReasonOf(() => GPC.Decode("G3RJM98NM")));
            Assert.Equal("GPC_LENGTH", ReasonOf(() => GPC.Decode("G3RJM98NM999")));
            Assert.Equal("GPC_CHAR", ReasonOf(() => GPC.Decode("G3RJM98NMQ")));
            Assert.Equal("GPC_CHAR", ReasonOf(() => GPC.Decode("G3RJM98NMU")));
            Assert.Equal("GPC_CHAR", ReasonOf(() => GPC.Decode("G3RJM98NMY")));
            Assert.Equal("GPC_CHECK", ReasonOf(() => GPC.Decode("#G3RJM-98NM9*5")));
        }

        /// <summary>A reserved code has no area either.</summary>
        [Fact]
        public void RefusesTheAreaOfAReservedCode() {
            Assert.Equal("GPC_RESERVED", ReasonOf(() => GPC.DecodeToArea("XG3RJ98NM9")));
        }

        /*  Parsing  */

        /// <summary>A confusable letter is read as the symbol it stands for.</summary>
        [Fact]
        public void ReadsAConfusableLetterAsTheSymbolItStandsFor() {
            Assert.Equal(GPC.Decode("G3RJM98NM0"), GPC.Decode("G3RJM98NMO"));
            Assert.Equal(GPC.Decode("G3RJM98NM1"), GPC.Decode("G3RJM98NMI"));
            Assert.Equal(GPC.Decode("G3RJM98NM5"), GPC.Decode("G3RJM98NMS"));
            Assert.Equal(GPC.Decode("G3RJM98NM2"), GPC.Decode("G3RJM98NMZ"));
            Assert.Equal(GPC.Decode("G3RJM98NM8"), GPC.Decode("G3RJM98NMB"));
            Assert.Equal(GPC.Decode("G3RJM98NM4"), GPC.Decode("G3RJM98NMA"));
            Assert.Equal(GPC.Decode("G3RJM98NM3"), GPC.Decode("G3RJM98NME"));
            Assert.Equal(GPC.Decode("G3RJM98NMW"), GPC.Decode("G3RJM98NMV"));
        }

        /// <summary>L is a symbol of the alphabet and is never read as 1.</summary>
        [Fact]
        public void NeverReadsLAsOne() {
            Assert.True(GPC.IsValid("G3RJM98NML"));
            Assert.NotEqual(GPC.Decode("G3RJM98NM1"), GPC.Decode("G3RJM98NML"));
        }

        /// <summary>U, Q and Y are rejected rather than aliased.</summary>
        [Fact]
        public void RejectsUQAndYRatherThanAliasingThem() {
            Assert.Equal((CodeClass.Invalid, "GPC_CHAR"), GPC.Validate("G3RJM98NMU"));
            Assert.Equal((CodeClass.Invalid, "GPC_CHAR"), GPC.Validate("G3RJM98NMQ"));
            Assert.Equal((CodeClass.Invalid, "GPC_CHAR"), GPC.Validate("G3RJM98NMY"));
        }

        /// <summary>
        /// Space, tab, line feed, vertical tab, form feed and carriage return,
        /// and nothing wider. A port that also stripped the Unicode spaces
        /// would accept what another port rejects, which is the whole thing
        /// the shared vectors exist to prevent.
        /// </summary>
        [Fact]
        public void StripsTheAsciiWhitespaceSetAndNothingWider() {
            (double Latitude, double Longitude) expected = GPC.Decode("G3RJM98NM9");
            foreach (string space in new[] { " ", "\t", "\n", "\v", "\f", "\r" }) {
                Assert.Equal(expected, GPC.Decode(space + "G3RJM" + space + "98NM9" + space));
                Assert.Equal((CodeClass.Invalid, "GPC_NULL"), GPC.Validate(space + space + space));
            }
            // U+00A0 is a space to Unicode and a symbol outside this alphabet.
            Assert.Equal((CodeClass.Invalid, "GPC_CHAR"), GPC.Validate("\u00a03RJM98NM9"));
        }

        /// <summary>Normalising an already normalised code returns it unchanged.</summary>
        [Fact]
        public void IsIdempotent() {
            (string once, _) = GPC.Normalise("#g3rjm-98nm9");
            (string twice, _) = GPC.Normalise(once);
            Assert.Equal("G3RJM98NM9", once);
            Assert.Equal(once, twice);
        }

        /*  Classification  */

        /// <summary>A string sorts into exactly one of three classes.</summary>
        [Fact]
        public void SortsAStringIntoOneOfThreeClasses() {
            Assert.Equal(CodeClass.Geometric, GPC.Classify("#G3RJM-98NM9"));
            Assert.Equal(CodeClass.Reserved, GPC.Classify("XG3RJ98NM9"));
            Assert.Equal(CodeClass.Invalid, GPC.Classify("nope"));
        }

        /// <summary>A reserved code is well formed, and is not a typing error.</summary>
        [Fact]
        public void KeepsAReservedCodeApartFromATypingError() {
            Assert.False(GPC.IsValid("XXXXXXXXXX"));
            Assert.Equal((CodeClass.Reserved, string.Empty), GPC.Validate("XXXXXXXXXX"));
        }

        /// <summary>The reasons are tested in order.</summary>
        [Fact]
        public void TestsTheReasonsInOrder() {
            Assert.Equal((CodeClass.Invalid, "GPC_NULL"), GPC.Validate(string.Empty));
            Assert.Equal((CodeClass.Invalid, "GPC_LENGTH"), GPC.Validate("Q"));
            Assert.Equal((CodeClass.Invalid, "GPC_CHAR"), GPC.Validate("QQQQQQQQQQ"));
        }

        /// <summary>Classify describes this grid, and eleven characters are not in it.</summary>
        [Fact]
        public void DoesNotCallAVersion1CodeAVersion2Code() {
            Assert.Equal((CodeClass.Invalid, "GPC_LENGTH"), GPC.Validate("#FN5G-CDKL-HDC"));
            Assert.False(GPC.IsValid("#FN5G-CDKL-HDC"));
            Assert.Equal((true, string.Empty), GPC.IsValidV1("#FN5G-CDKL-HDC"));
        }

        /*  The check character  */

        /// <summary>The worked examples of section 14.5.</summary>
        [Fact]
        public void ProducesTheWorkedCheckCharacters() {
            Assert.Equal("T", GPC.CheckCharacter("#G3RJM-98NM9"));
            Assert.Equal("D", GPC.CheckCharacter("#KDC8X-JM49X"));
            Assert.Equal("2", GPC.CheckCharacter("#P4444-PPPPP"));
            Assert.Equal("M", GPC.CheckCharacter("#JPPPP-00000"));
        }

        /// <summary>A correct check character is accepted and stripped.</summary>
        [Fact]
        public void AcceptsAndStripsACorrectCheckCharacter() {
            Assert.Equal(GPC.Decode("#G3RJM-98NM9"), GPC.Decode("#G3RJM-98NM9*T"));
            Assert.True(GPC.IsValid("#G3RJM-98NM9*T"));
            Assert.Equal(CodeClass.Geometric, GPC.Classify("#g3rjm-98nm9*t"));
        }

        /// <summary>Never a silent ignore, and never valid-but-undecodable.</summary>
        [Fact]
        public void FailsAWrongCheckCharacterEverywhere() {
            foreach (string text in new[] {
                    "#G3RJM-98NM9*5", "#G3RJM-98NM9*", "#G3RJM-98NM9*TT", "#G3RJM-98NM9*Q" }) {
                Assert.Equal((CodeClass.Invalid, "GPC_CHECK"), GPC.Validate(text));
                Assert.False(GPC.IsValid(text));
                _ = Assert.Throws<GPCException>(() => GPC.Decode(text));
            }
        }

        /// <summary>Every single-symbol error is detected.</summary>
        [Fact]
        public void DetectsEverySingleSymbolError() {
            const string alphabet = "0123456789CDFGHJKLMNPRTWX";
            const string code = "G3RJM98NM9";
            string check = GPC.CheckCharacter(code);
            for (int position = 0; position < 10; position++) {
                foreach (char symbol in alphabet) {
                    if (symbol == code[position]) {
                        continue;
                    }
                    string wrong = code[..position] + symbol + code[(position + 1)..];
                    Assert.Equal((CodeClass.Invalid, "GPC_CHECK"), GPC.Validate($"{wrong}*{check}"));
                }
            }
        }

        /// <summary>Every adjacent transposition is detected.</summary>
        [Fact]
        public void DetectsEveryAdjacentTransposition() {
            const string code = "G3RJM98NM9";
            string check = GPC.CheckCharacter(code);
            for (int position = 0; position < 9; position++) {
                if (code[position] == code[position + 1]) {
                    continue;
                }
                string swapped = code[..position] + code[position + 1] + code[position]
                    + code[(position + 2)..];
                Assert.Equal((CodeClass.Invalid, "GPC_CHECK"), GPC.Validate($"{swapped}*{check}"));
            }
        }

        /// <summary>A reserved code has a check character like any other.</summary>
        [Fact]
        public void GivesAReservedCodeACheckCharacterLikeAnyOther() {
            Assert.Equal(CodeClass.Reserved,
                GPC.Classify("XG3RJ98NM9*" + GPC.CheckCharacter("XG3RJ98NM9")));
        }

        /*  Version 1 codes  */

        /// <summary>Decode dispatches on length.</summary>
        [Fact]
        public void DispatchesOnLength() {
            Assert.Equal((43.65, -79.38), GPC.Decode("#FN5G-CDKL-HDC"));
            Assert.Equal((43.650006, -79.380004), GPC.Decode("#G3RJM-98NM9"));
        }

        /// <summary>The explicit entry point agrees with the dispatch.</summary>
        [Fact]
        public void AgreesWithTheExplicitEntryPoint() {
            Assert.Equal(GPC.Decode("#FN5G-CDKL-HDC"), GPC.DecodeV1("#FN5G-CDKL-HDC"));
            Assert.Equal(GPC.Decode("FN5GCDKLHDC"), GPC.DecodeV1("FN5GCDKLHDC"));
            Assert.Equal(GPC.Decode("#HG9K-PCVH-DPV"), GPC.DecodeV1("#HG9K-PCVH-DPV"));
        }

        /// <summary>
        /// Version 1 returns the corner of its cell, where version 2 returns the
        /// centre. The difference is deliberate: the value is the one every
        /// version 1 release has returned.
        /// </summary>
        [Fact]
        public void ReturnsTheCornerOfItsCell() {
            Assert.Equal((0.0, 0.0), GPC.DecodeV1("DCCCCCCCCCC"));
            Assert.Equal((89.99999, 179.99999), GPC.DecodeV1("HG9KPCVHDPV"));
            Assert.Equal((-89.99999, -179.99999), GPC.DecodeV1("HG9PJLHJX69"));
        }

        /// <summary>Version 1 validity carries the expected reason.</summary>
        [Fact]
        public void ReportsVersion1ValidityWithTheExpectedReason() {
            Assert.Equal((true, string.Empty), GPC.IsValidV1("#FN5G-CDKL-HDC"));
            Assert.Equal((true, string.Empty), GPC.IsValidV1("DCCCCCCCCCC"));
            Assert.Equal((false, "GPC_NULL"), GPC.IsValidV1(string.Empty));
            Assert.Equal((false, "GPC_NULL"), GPC.IsValidV1("   "));
            Assert.Equal((false, "GPC_NULL"), GPC.IsValidV1(null));
            Assert.Equal((false, "GPC_LENGTH"), GPC.IsValidV1("ABC"));
            Assert.Equal((false, "GPC_LENGTH"), GPC.IsValidV1("FN5GCDKLHDCC"));
            Assert.Equal((false, "GPC_CHAR"), GPC.IsValidV1("FN5GCDKLHDA"));
            Assert.Equal((false, "GPC_RANGE"), GPC.IsValidV1("CCCCCCCCCCC"));
            Assert.Equal((false, "GPC_RANGE"), GPC.IsValidV1("YYYYYYYYYYY"));
        }

        /// <summary>
        /// V and Y are version 1 symbols. Version 2 excludes both, reads V as W
        /// and rejects Y outright, and none of that may reach this path.
        /// </summary>
        [Fact]
        public void NeverLetsTheVersion2AliasTableTouchAVersion1Code() {
            Assert.Equal((true, string.Empty), GPC.IsValidV1("#HG9K-PCVH-DPV"));
            Assert.Equal((89.99999, 179.99999), GPC.Decode("#HG9K-PCVH-DPV"));
            Assert.Equal((false, "GPC_RANGE"), GPC.IsValidV1("9999999999Y"));
        }

        /// <summary>
        /// The dispatch is on length alone, so an eleven-character string that
        /// happens to be a valid version 1 code decodes as one -- even when what
        /// the caller meant was a version 2 code with a character too many. This
        /// is the price of carrying both formats in one install, and it is why
        /// section 15.2 says to show the decoded point on a map before acting.
        /// </summary>
        [Fact]
        public void ReadsElevenCharactersAsVersion1WhateverWasMeant() {
            Assert.Equal((CodeClass.Invalid, "GPC_LENGTH"), GPC.Validate("G3RJM98NM99"));
            Assert.Equal((true, string.Empty), GPC.IsValidV1("G3RJM98NM99"));
            Assert.Equal(GPC.DecodeV1("G3RJM98NM99"), GPC.Decode("G3RJM98NM99"));
        }
    }

    /// <summary>Sections 18.1 and 18.2. Cells, and the prefix test.</summary>
    public class CellTests {

        /// <summary>A cell is a prefix of the code.</summary>
        [Fact]
        public void TakesACellAsAPrefix() {
            Assert.Equal("G3R", GPC.Cell("#G3RJM-98NM9", 3));
            Assert.Equal("G3RJM", GPC.Cell("#G3RJM-98NM9", 5));
            Assert.Equal("G3RJM98NM9", GPC.Cell("#G3RJM-98NM9", 10));
        }

        /// <summary>Normalisation happens before the slice.</summary>
        [Fact]
        public void NormalisesBeforeSlicing() {
            Assert.Equal("G3RJM1", GPC.Cell("#g3rjm-i8nm9", 6));
        }

        /// <summary>A cell of a cell is a shorter cell.</summary>
        [Fact]
        public void TakesACellOfACell() {
            Assert.Equal("G3", GPC.Cell("G3RJM", 2));
        }

        /// <summary>
        /// A cell comes back bare. Ten characters is a code and anything shorter
        /// is a region; a cell written as a code would claim to be one.
        /// </summary>
        [Fact]
        public void ReturnsACellBare() {
            for (int level = 1; level <= 10; level++) {
                string cell = GPC.Cell("#G3RJM-98NM9", level);
                Assert.DoesNotContain('#', cell);
                Assert.DoesNotContain('-', cell);
            }
        }

        /// <summary>A level outside 1 to 10 is an argument error, as in Encode.</summary>
        [Fact]
        public void RefusesALevelOutsideOneToTen() {
            foreach (int level in new[] { 0, 11, -1, 100 }) {
                _ = Assert.Throws<ArgumentOutOfRangeException>(
                    () => GPC.Cell("G3RJM98NM9", level));
            }
        }

        /// <summary>Asking for more characters than there are.</summary>
        [Fact]
        public void RefusesACellShorterThanTheLevelAskedFor() {
            GPCException error = Assert.Throws<GPCException>(() => GPC.Cell("G3R", 5));
            Assert.Equal("GPC_LENGTH", error.Reason);
        }

        /// <summary>A reserved cell has its own reason, as a reserved code does.</summary>
        [Fact]
        public void RefusesAReservedCellWithItsOwnReason() {
            foreach (string text in new[] { "XG3RJ", "XG3RJ98NM9" }) {
                GPCException error = Assert.Throws<GPCException>(() => GPC.Cell(text, 3));
                Assert.Equal("GPC_RESERVED", error.Reason);
            }
        }

        /// <summary>Containment is the prefix test and nothing more.</summary>
        [Fact]
        public void AnswersContainmentWithThePrefixTest() {
            Assert.True(GPC.Contains("G3RJM", "G3RJM98NM9"));
            Assert.True(GPC.Contains("G", "G3RJM98NM9"));
            Assert.False(GPC.Contains("G3RJD", "G3RJM98NM9"));
        }

        /// <summary>Cells contain cells, in one direction only.</summary>
        [Fact]
        public void HoldsContainmentBetweenCells() {
            Assert.True(GPC.Contains("G3R", "G3RJM"));
            Assert.False(GPC.Contains("G3RJM", "G3R"));
        }

        /// <summary>Both sides are normalised.</summary>
        [Fact]
        public void NormalisesBothSides() {
            Assert.True(GPC.Contains("#g3rjm", "#G3RJM-98NM9"));
        }
    }

    /// <summary>Section 18.3.</summary>
    public class NeighbourTests {

        /// <summary>Eight neighbours away from the poles.</summary>
        [Fact]
        public void FindsEightAwayFromThePoles() {
            Assert.Equal(8, GPC.Neighbours("G3RJM98NM9").Count);
            Assert.Equal(8, GPC.Neighbours("G3RJM").Count);
        }

        /// <summary>
        /// Five in a polar row. Rows do not wrap, so the three that would lie
        /// off the grid are absent rather than present and empty.
        /// </summary>
        [Fact]
        public void FindsFiveInAPolarRow() {
            Assert.Equal(5, GPC.Neighbours("#P4444-PPPPP").Count);
            Assert.Equal(5, GPC.Neighbours("#3PPPP-00000").Count);
        }

        /// <summary>Neighbours are cells of the same level.</summary>
        [Fact]
        public void ReturnsCellsOfTheSameLength() {
            for (int level = 1; level <= 10; level++) {
                string cell = GPC.Cell("#G3RJM-98NM9", level);
                foreach (string neighbour in GPC.Neighbours(cell)) {
                    Assert.Equal(level, neighbour.Length);
                }
            }
        }

        /// <summary>
        /// The first column of the grid: its western neighbour is the last
        /// column. No amount of string arithmetic would have found it -- the two
        /// share no characters at all.
        /// </summary>
        [Fact]
        public void WrapsColumnsAtTheAntimeridian() {
            string first = GPC.Encode(0.0, -180.0, false);
            string west = GPC.Neighbours(first)[6];
            Assert.Equal(GPC.Encode(0.0, 179.99999, false), west);
            Assert.NotEqual(first[0], west[0]);
        }

        /// <summary>North, north-east, east, south-east, south, south-west, west, north-west.</summary>
        [Fact]
        public void KeepsTheOrderFixed() {
            (long row, long col) = GPC.DecodeToGrid("G3RJM98NM9");
            int[] steps = [1, 0, 1, 1, 0, 1, -1, 1, -1, 0, -1, -1, 0, -1, 1, -1];
            List<string> expected = [];
            for (int i = 0; i < steps.Length; i += 2) {
                expected.Add(GPC.GridToCode(row + steps[i], col + steps[i + 1]));
            }
            Assert.Equal(expected, GPC.Neighbours("G3RJM98NM9"));
        }

        /// <summary>A cell is never its own neighbour.</summary>
        [Fact]
        public void NeverIncludesTheCellItself() {
            Assert.DoesNotContain("G3RJM", GPC.Neighbours("G3RJM"));
        }
    }

    /// <summary>Section 18.4, against the table of section 3.</summary>
    public class CellDimensionTests {

        /// <summary>The table, to the tenth of a kilometre it quotes.</summary>
        [Fact]
        public void ReproducesTheTable() {
            (int Level, double NorthSouth, double EastWest)[] table = [
                (1, 5000.9, 6679.2), (2, 1000.2, 1335.8), (3, 200.0, 267.2),
                (4, 40.0, 53.4), (5, 8.0, 10.7),
            ];
            foreach ((int level, double northSouth, double eastWest) in table) {
                (_, _, double n, double e) = GPC.CellDimensions(level);
                Assert.Equal(northSouth, Math.Round(n / 1000, 1));
                Assert.Equal(eastWest, Math.Round(e / 1000, 1));
            }
        }

        /// <summary>A level-10 cell is a doorway.</summary>
        [Fact]
        public void MeasuresADoorwayAtLevelTen() {
            (_, _, double northSouth, double eastWest) = GPC.CellDimensions(10);
            Assert.Equal(2.6, Math.Round(northSouth, 1));
            Assert.Equal(3.4, Math.Round(eastWest, 1));
        }

        /// <summary>Height over width is three quarters at every level.</summary>
        [Fact]
        public void KeepsTheAspectRatioAtThreeQuarters() {
            for (int level = 1; level <= 10; level++) {
                (double latitude, double longitude, _, _) = GPC.CellDimensions(level);
                Assert.Equal(0.75, Math.Round(latitude / longitude, 12));
            }
        }

        /// <summary>A level outside 1 to 10.</summary>
        [Fact]
        public void RefusesALevelOutsideOneToTen() {
            _ = Assert.Throws<ArgumentOutOfRangeException>(() => GPC.CellDimensions(0));
        }
    }

    /// <summary>Section 18.5. Compared to a tolerance, never to equality.</summary>
    public class DistanceTests {

        /// <summary>A cell is no distance from itself.</summary>
        [Fact]
        public void IsZeroFromACellToItself() {
            Assert.Equal(0.0, GPC.Distance("G3RJM98NM9", "G3RJM98NM9"));
        }

        /// <summary>The order of the arguments does not matter.</summary>
        [Fact]
        public void IsSymmetric() {
            Assert.Equal(GPC.Distance("G3RJM98NM9", "6LK4XNRP0R"),
                         GPC.Distance("6LK4XNRP0R", "G3RJM98NM9"));
        }

        /// <summary>Pole to pole is half the meridian.</summary>
        [Fact]
        public void MakesPoleToPoleHalfTheMeridian() {
            Assert.Equal(20015.1,
                Math.Round(GPC.Distance("#P4444-PPPPP", "#3PPPP-00000") / 1000, 1));
        }

        /// <summary>Antipodal cells do not produce a NaN, which needs the clamp.</summary>
        [Fact]
        public void DoesNotProduceANanForAntipodalCells() {
            double metres = GPC.Distance(GPC.Encode(0.0, 0.0, false), GPC.Encode(0.0, 180.0, false));
            Assert.Equal(20015.1, Math.Round(metres / 1000, 1));
        }

        /// <summary>The two cells may be of different levels.</summary>
        [Fact]
        public void AcceptsCellsOfDifferentLevels() {
            Assert.True(GPC.Distance("G3RJM", "G3RJM98NM9") < 7000.0);
        }
    }

    /// <summary>Section 12.</summary>
    public class ShortFormTests {

        /// <summary>The short form is the second printed group.</summary>
        [Fact]
        public void IsTheSecondPrintedGroup() {
            Assert.Equal("98NM9", GPC.Shorten("#G3RJM-98NM9"));
            Assert.Equal("98NM9", GPC.Shorten("G3RJM98NM9"));
        }

        /// <summary>The leading dash belongs to the presentation form.</summary>
        [Fact]
        public void RecoversWithOrWithoutTheLeadingDash() {
            foreach (string text in new[] { "98NM9", "-98NM9", " -98nm9 " }) {
                Assert.Equal("#G3RJM-98NM9", GPC.RecoverShort(text, 43.66, -79.39));
            }
        }

        /// <summary>Exact anywhere within half a level-5 cell on each axis.</summary>
        [Fact]
        public void IsExactWithinHalfALevel5Cell() {
            string code = GPC.Encode(43.65, -79.38, false);
            string shortForm = GPC.Shorten(code);
            foreach (double dLatitude in new[] { -0.0359, 0.0, 0.0359 }) {
                foreach (double dLongitude in new[] { -0.0479, 0.0, 0.0479 }) {
                    Assert.Equal(code, GPC.RecoverShort(shortForm,
                        43.65 + dLatitude, -79.38 + dLongitude, false));
                }
            }
        }

        /// <summary>
        /// A reference east of the antimeridian recovering a code west of it.
        /// The column arithmetic wraps; the row arithmetic must not.
        /// </summary>
        [Fact]
        public void CrossesTheAntimeridian() {
            string code = GPC.Encode(0.0, -179.99, false);
            Assert.Equal(code, GPC.RecoverShort(GPC.Shorten(code), 0.0, 179.995, false));
        }

        /// <summary>A short form has to be five symbols.</summary>
        [Fact]
        public void RefusesAShortFormThatIsNotFiveSymbols() {
            foreach (string text in new[] { "98NM", "98NM99" }) {
                GPCException error = Assert.Throws<GPCException>(
                    () => GPC.RecoverShort(text, 43.65, -79.38));
                Assert.Equal("GPC_LENGTH", error.Reason);
            }
        }

        /// <summary>A reference outside the domain, as Encode treats one.</summary>
        [Fact]
        public void RefusesAReferenceOutsideTheDomain() {
            _ = Assert.Throws<ArgumentOutOfRangeException>(
                () => GPC.RecoverShort("98NM9", 91.0, 0.0));
        }
    }

    /// <summary>Section 15.3.</summary>
    public class CorrectionTests {

        /// <summary>The true code is found and ranked first, whichever character was hit.</summary>
        [Fact]
        public void FindsTheTrueCodeAndRanksItFirst() {
            string code = GPC.Encode(43.65, -79.38, false);
            for (int position = 0; position < 10; position++) {
                string wrong = code[..position] + (code[position] == '0' ? '1' : '0')
                    + code[(position + 1)..];
                Assert.Equal(code, GPC.SuggestCorrections(wrong, 43.65, -79.38, 6, false)[0]);
            }
        }

        /// <summary>The input need not decode anywhere near the reference.</summary>
        [Fact]
        public void TakesACodeThatDecodesNowhereNearTheReference() {
            Assert.Contains(GPC.Encode(43.65, -79.38, false),
                GPC.SuggestCorrections("03RJM98NM9", 43.65, -79.38, 6, false));
        }

        /// <summary>A reserved code names no cell, so it is never a correction.</summary>
        [Fact]
        public void NeverSuggestsAReservedCode() {
            foreach (string candidate in GPC.SuggestCorrections("XG3RJ98NM9", 43.65, -79.38, 4, false)) {
                Assert.NotEqual('X', candidate[0]);
            }
        }

        /// <summary>A narrower window returns fewer candidates.</summary>
        [Fact]
        public void ReturnsFewerCandidatesAtANarrowerLevel() {
            int wide = GPC.SuggestCorrections("G3RJM98NM8", 43.65, -79.38, 4, false).Count;
            int narrow = GPC.SuggestCorrections("G3RJM98NM8", 43.65, -79.38, 8, false).Count;
            Assert.True(wide > narrow, $"{wide} should exceed {narrow}");
        }

        /// <summary>The input still has to be ten symbols of the alphabet.</summary>
        [Fact]
        public void RefusesACodeThatWillNotNormaliseToTenSymbols() {
            GPCException error = Assert.Throws<GPCException>(
                () => GPC.SuggestCorrections("G3RJM98NM", 43.65, -79.38));
            Assert.Equal("GPC_LENGTH", error.Reason);
        }

        /// <summary>P4444PPPPP yields 242 rather than 249, and is not padded back.</summary>
        [Fact]
        public void NeverPadsTheListBackWithDuplicates() {
            IReadOnlyList<string> every = GPC.SuggestCorrections("P4444PPPPP", 90.0, 0.0, 1, false);
            Assert.Equal(every.Count, new HashSet<string>(every).Count);
        }
    }

    /// <summary>Section 13.</summary>
    public class IntegerFormTests {

        /// <summary>It round-trips.</summary>
        [Fact]
        public void RoundTrips() {
            string code = GPC.Encode(43.65, -79.38, false);
            Assert.Equal(code, GPC.FromInteger(GPC.ToInteger(code), false));
        }

        /// <summary>The first and last codes sit at the ends of the range.</summary>
        [Fact]
        public void PlacesTheFirstAndLastCodesAtTheEnds() {
            Assert.Equal(0L, GPC.ToInteger("0000000000"));
            Assert.Equal(95_367_431_640_624L, GPC.ToInteger("XXXXXXXXXX"));
        }

        /// <summary>The reserved namespace is the top of the range.</summary>
        [Fact]
        public void PutsTheReservedNamespaceAtTheTop() {
            const long Floor = 91_552_734_375_000L;
            Assert.True(GPC.ToInteger("X000000000") >= Floor);
            Assert.True(GPC.ToInteger("W999999999") < Floor);
        }

        /// <summary>A value outside the range.</summary>
        [Fact]
        public void RefusesAValueOutsideTheRange() {
            foreach (long value in new[] { -1L, 95_367_431_640_625L }) {
                GPCException error = Assert.Throws<GPCException>(() => GPC.FromInteger(value));
                Assert.Equal("GPC_RANGE", error.Reason);
            }
        }
    }

    /// <summary>Section 17. Advisory: it reports and never blocks.</summary>
    public class ScreeningTests {

        /// <summary>The version comes back even when nothing matched.</summary>
        [Fact]
        public void ReturnsTheVersionEvenWhenNothingMatched() {
            (string version, IReadOnlyList<(int Position, int Length)> spans) = GPC.Screen("G3RJM98NM9");
            Assert.NotEqual(string.Empty, version);
            Assert.Empty(spans);
        }

        /// <summary>A match reports its span.</summary>
        [Fact]
        public void ReportsTheSpanOfAMatch() {
            (_, IReadOnlyList<(int Position, int Length)> spans) = GPC.Screen("GN4T000000");
            (int Position, int Length) span = Assert.Single(spans);
            Assert.Equal((1, 4), span);
        }

        /// <summary>An X in position 1 does not stop the other nine spelling something.</summary>
        [Fact]
        public void ScreensAReservedCodeLikeAnyOther() {
            (_, IReadOnlyList<(int Position, int Length)> spans) = GPC.Screen("XGN4T00000");
            Assert.Equal((2, 4), Assert.Single(spans));
        }

        /// <summary>Whatever the list says, the code still encodes, decodes and validates.</summary>
        [Fact]
        public void NeverBlocks() {
            Assert.True(GPC.IsValid("GN4T000000"));
            Assert.Equal(CodeClass.Geometric, GPC.Classify("GN4T000000"));
            (double latitude, double longitude) = GPC.Decode("GN4T000000");
            Assert.Equal("GN4T000000", GPC.Encode(latitude, longitude, false));
        }

        /// <summary>The formatted and bare forms screen alike.</summary>
        [Fact]
        public void ScreensTheFormattedAndBareFormsAlike() {
            Assert.Equal(GPC.Screen("GN4T000000").Spans, GPC.Screen("#GN4T0-00000").Spans);
        }
    }

    /// <summary>Batch and streaming, for dataset work.</summary>
    public class BulkTests {

        private static readonly (double Latitude, double Longitude)[] TwoPoints =
            [(43.65, -79.38), (0.0, 0.0)];

        private static readonly (double Latitude, double Longitude)[] OneBadRow =
            [(43.65, -79.38), (0.0, 181.0)];

        private static readonly (double Latitude, double Longitude)[] LazyRows =
            [(43.65, -79.38), (91.0, 0.0)];

        private static readonly string[] OneCode = ["#G3RJM-98NM9"];

        private static readonly string[] TwoCodes = ["G3RJM98NM9", "JPPPP00000"];

        private static readonly (double Latitude, double Longitude)[] OneDecoded =
            [(43.650006, -79.380004)];

        /// <summary>A batch of coordinates.</summary>
        [Fact]
        public void EncodesABatch() {
            Assert.Equal(TwoCodes, GPC.EncodeAll(TwoPoints, false));
        }

        /// <summary>A batch of codes.</summary>
        [Fact]
        public void DecodesABatch() {
            Assert.Equal(OneDecoded, GPC.DecodeAll(OneCode));
        }

        /// <summary>
        /// The stream is deferred, so a bad row throws where it is reached rather
        /// than before -- which is what lets a caller handle failures row by row.
        /// </summary>
        [Fact]
        public void StreamsLazily() {
            IEnumerator<string> stream = GPC.EncodeStream(LazyRows, false).GetEnumerator();
            Assert.True(stream.MoveNext());
            Assert.Equal("G3RJM98NM9", stream.Current);
            _ = Assert.Throws<ArgumentOutOfRangeException>(() => stream.MoveNext());
        }

        /// <summary>A batch stops at the first bad row.</summary>
        [Fact]
        public void StopsABatchAtTheFirstBadRow() {
            _ = Assert.Throws<ArgumentOutOfRangeException>(
                () => GPC.EncodeAll(OneBadRow, true));
        }

        /// <summary>An empty sequence.</summary>
        [Fact]
        public void HandlesAnEmptySequence() {
            Assert.Empty(GPC.EncodeAll([], true));
            Assert.Empty(GPC.DecodeAll([]));
        }
    }

    /// <summary>Section 18.6.</summary>
    public class GridIndexTests {

        /// <summary>It agrees with ToGrid.</summary>
        [Fact]
        public void AgreesWithToGrid() {
            Assert.Equal(GPC.ToGrid(43.65, -79.38), GPC.DecodeToGrid("#G3RJM-98NM9"));
        }

        /// <summary>The corners of the grid.</summary>
        [Fact]
        public void ReachesTheCornersOfTheGrid() {
            Assert.Equal((0L, 0L), GPC.DecodeToGrid(GPC.Encode(-90.0, -180.0, false)));
            Assert.Equal((7812499L, 11718749L),
                GPC.DecodeToGrid(GPC.Encode(90.0, 179.99999, false)));
        }

        /// <summary>A reserved code names no cell.</summary>
        [Fact]
        public void RefusesAReservedCode() {
            GPCException error = Assert.Throws<GPCException>(() => GPC.DecodeToGrid("XG3RJ98NM9"));
            Assert.Equal("GPC_RESERVED", error.Reason);
        }
    }

    /// <summary>Section 19.</summary>
    public class ConversionTests {

        /// <summary>The worked example.</summary>
        [Fact]
        public void WritesTheWorkedExample() {
            Assert.Equal("43°39'00.00\"N, 79°22'48.00\"W", GPC.ToDMS(43.65, -79.38));
        }

        /// <summary>Negative zero is not negative; all four signed zeroes name the origin.</summary>
        [Fact]
        public void DoesNotTreatNegativeZeroAsNegative() {
            Assert.Equal("0°00'00.00\"N, 0°00'00.00\"E", GPC.ToDMS(-0.0, -0.0));
        }

        /// <summary>Rounding the whole value first is what carries the seconds.</summary>
        [Fact]
        public void CarriesSecondsIntoTheNextMinute() {
            Assert.Equal("1°00'00.00\"N, 0°00'00.00\"E", GPC.ToDMS(1.0 - 1e-9, 0.0));
        }

        /// <summary>It reads its own output back.</summary>
        [Fact]
        public void ReadsItsOwnDmsBack() {
            Assert.Equal((43.65, -79.38), GPC.FromDMS(GPC.ToDMS(43.65, -79.38)));
        }

        /// <summary>The wider forms the grammar accepts.</summary>
        [Fact]
        public void AcceptsTheWiderDmsForms() {
            Assert.Equal((43.65, -79.38), GPC.FromDMS("43d39m0s N 79d22m48s W"));
            Assert.Equal((43.0, -79.0), GPC.FromDMS("43°N 79°W"));
            Assert.Equal((-43.0, 79.0), GPC.FromDMS("-43°, +79°"));
        }

        /// <summary>What the grammar does not accept.</summary>
        [Fact]
        public void RefusesWhatTheDmsGrammarDoesNotAccept() {
            string[] bad = [
                "43°39'00.00\"N",              // one axis only
                "43 39",                            // no unit markers
                "-43°N, 79°W",            // a sign and a hemisphere
                "43°W, 79°N",             // the axes crossed
                "43°60'N, 0°0'E",         // sixty minutes
                "43°39'60.0\"N, 0°0'0\"E", // sixty seconds
                "43°N, 79°W extra",       // trailing text
            ];
            foreach (string text in bad) {
                GPCException error = Assert.Throws<GPCException>(() => GPC.FromDMS(text));
                Assert.Equal("GPC_DMS", error.Reason);
            }
        }

        /// <summary>A DMS value outside the domain.</summary>
        [Fact]
        public void RefusesADmsValueOutsideTheDomain() {
            _ = Assert.Throws<ArgumentOutOfRangeException>(() => GPC.FromDMS("91°N, 0°E"));
        }

        /// <summary>
        /// Decode returns a cell centre, which sits eight times further from the
        /// nearest boundary than this rounding can move it.
        /// </summary>
        [Fact]
        public void LetsADecodedCodeSurviveTheDmsRoundTrip() {
            foreach ((double latitude, double longitude) in Points()) {
                string code = GPC.Encode(latitude, longitude, false);
                (double a, double b) = GPC.Decode(code);
                (double back, double backLong) = GPC.FromDMS(GPC.ToDMS(a, b));
                Assert.Equal(code, GPC.Encode(back, backLong, false));
            }
        }

        /// <summary>The geo URI.</summary>
        [Fact]
        public void WritesAGeoUri() {
            Assert.Equal("geo:43.650006,-79.380004", GPC.ToGeoURI(43.650006, -79.380004));
        }

        /// <summary>Trailing zeros go, and the point with them.</summary>
        [Fact]
        public void DropsTrailingZerosAndThePoint() {
            Assert.Equal("geo:43.65,-79.38", GPC.ToGeoURI(43.65, -79.38));
            Assert.Equal("geo:43,-79", GPC.ToGeoURI(43.0, -79.0));
            Assert.Equal("geo:0,0", GPC.ToGeoURI(-0.0, -0.0));
        }

        /// <summary>It reads its own URI back.</summary>
        [Fact]
        public void ReadsItsOwnUriBack() {
            Assert.Equal((43.650006, -79.380004), GPC.FromGeoURI("geo:43.650006,-79.380004"));
        }

        /// <summary>The altitude and the parameters are dropped.</summary>
        [Fact]
        public void DropsTheAltitudeAndTheParameters() {
            Assert.Equal((43.65, -79.38), GPC.FromGeoURI("geo:43.65,-79.38,76.1"));
            Assert.Equal((43.65, -79.38), GPC.FromGeoURI("geo:43.65,-79.38;u=35"));
            Assert.Equal((43.65, -79.38), GPC.FromGeoURI("GEO:43.65,-79.38;crs=WGS84"));
        }

        /// <summary>
        /// Reading a code as though it were on another datum would put it in the
        /// wrong place, quietly.
        /// </summary>
        [Fact]
        public void RefusesAnotherDatumRatherThanIgnoringIt() {
            GPCException error = Assert.Throws<GPCException>(
                () => GPC.FromGeoURI("geo:43.65,-79.38;crs=nad83"));
            Assert.Equal("GPC_GEO", error.Reason);
        }

        /// <summary>What the URI grammar does not accept.</summary>
        [Fact]
        public void RefusesAUriTheGrammarDoesNotAccept() {
            string[] bad = ["geo:43.65", "43.65,-79.38", "geo:+43.65,-79.38",
                            "geo:43.65,-79.38,1,2", "geo:1e2,0"];
            foreach (string text in bad) {
                GPCException error = Assert.Throws<GPCException>(() => GPC.FromGeoURI(text));
                Assert.Equal("GPC_GEO", error.Reason);
            }
        }

        /// <summary>The URI carries all six decimal places, so the code survives.</summary>
        [Fact]
        public void LetsADecodedCodeSurviveTheGeoUriRoundTrip() {
            foreach ((double latitude, double longitude) in Points()) {
                string code = GPC.Encode(latitude, longitude, false);
                (double a, double b) = GPC.Decode(code);
                (double back, double backLong) = GPC.FromGeoURI(GPC.ToGeoURI(a, b));
                Assert.Equal(code, GPC.Encode(back, backLong, false));
            }
        }

        /// <summary>The corners of the domain, plus two landmarks.</summary>
        /// <returns>Coordinates worth round-tripping.</returns>
        private static (double Latitude, double Longitude)[] Points() {
            return [(43.65, -79.38), (-33.8568, 151.2153), (90.0, 0.0), (-90.0, 0.0),
                    (0.0, -180.0)];
        }
    }
}
