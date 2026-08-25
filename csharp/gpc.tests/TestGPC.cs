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
}
