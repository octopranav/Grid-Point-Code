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
using System.Globalization;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using Xunit;

namespace Ca.Pranavpatel.Algo.GridPointCode.Tests {
    /// <summary>
    /// Runs the shared conformance vectors in test_data/. Every port reads these
    /// same files, so a disagreement between languages shows up here rather than
    /// in a release. The v2_ files hold version 2; the rest are version 1, and
    /// are asserted by decoding, because no package encodes version 1 any more.
    /// </summary>
    public class VectorsTests {

        /// <summary>One cell of the version 1 grid: a hundred-thousandth of a degree.</summary>
        private const double V1Cell = 1e-5;

        /// <summary>Walk up from the test assembly until test_data appears.</summary>
        /// <returns>Full path to the shared test_data directory.</returns>
        private static string TestDataDir() {
            DirectoryInfo dir = new(AppContext.BaseDirectory);
            while (dir is not null) {
                string candidate = Path.Combine(dir.FullName, "test_data");
                if (Directory.Exists(candidate)) {
                    return candidate;
                }
                dir = dir.Parent;
            }
            throw new DirectoryNotFoundException(
                "test_data directory not found above " + AppContext.BaseDirectory);
        }

        /// <summary>
        /// Read one vector file, dropping comments and blank lines. Splits on the
        /// first <paramref name="fields" /> minus one commas so the final column
        /// keeps any comma, '#' or spacing it contains.
        /// </summary>
        /// <param name="name">File name inside test_data.</param>
        /// <param name="fields">Number of columns in the file.</param>
        /// <returns>One string array per vector.</returns>
        private static List<string[]> Rows(string name, int fields) {
            List<string[]> rows = [];
            foreach (string raw in File.ReadAllLines(Path.Combine(TestDataDir(), name))) {
                string line = raw.EndsWith('\r') ? raw[..^1] : raw;
                if (string.IsNullOrWhiteSpace(line) || line.StartsWith('#')) {
                    continue;
                }
                rows.Add(line.Split(',', fields));
            }
            return rows;
        }

        /// <summary>Parse a coordinate using the invariant culture.</summary>
        /// <param name="text">Decimal string from a vector file.</param>
        /// <returns>The parsed value.</returns>
        private static double Num(string text) {
            return double.Parse(text, CultureInfo.InvariantCulture);
        }

        /// <summary>The class a vector names, as this port spells it.</summary>
        /// <param name="text">GEOMETRIC, RESERVED or INVALID.</param>
        /// <returns>The matching enum member.</returns>
        private static CodeClass Kind(string text) {
            return Enum.Parse<CodeClass>(text, ignoreCase: true);
        }

        /// <summary>Rebuild the formatted #XXXX-XXXX-XXX form of a version 1 code.</summary>
        /// <param name="code">Unformatted eleven-character code.</param>
        /// <returns>The formatted code.</returns>
        private static string FormattedV1(string code) {
            return $"#{code[..4]}-{code[4..8]}-{code[8..11]}";
        }

        /*  Version 2  */

        /// <summary>Every vector encodes to the expected code.</summary>
        [Fact]
        public void EncodesEveryVectorToTheExpectedCode() {
            List<string[]> data = Rows("v2_encoding.csv", 3);
            Assert.True(data.Count > 100, "expected a substantial corpus");
            foreach (string[] r in data) {
                Assert.Equal(r[2], GPC.Encode(Num(r[0]), Num(r[1]), false));
            }
        }

        /// <summary>Every vector decodes to the expected coordinates.</summary>
        [Fact]
        public void DecodesEveryVectorToTheExpectedCoordinates() {
            List<string[]> data = Rows("v2_decoding.csv", 3);
            Assert.True(data.Count > 100, "expected a substantial corpus");
            foreach (string[] r in data) {
                Assert.Equal((Num(r[1]), Num(r[2])), GPC.Decode(r[0]));
            }
        }

        /// <summary>The formatted and unformatted forms decode alike.</summary>
        [Fact]
        public void DecodesTheFormattedAndUnformattedFormsAlike() {
            foreach (string[] r in Rows("v2_decoding.csv", 3)) {
                Assert.Equal((Num(r[1]), Num(r[2])), GPC.Decode(GPC.FormatGPC(r[0])));
            }
        }

        /// <summary>Decoding then encoding returns the original code.</summary>
        [Fact]
        public void RoundTripsEveryEncodedCodeBackToItself() {
            foreach (string[] r in Rows("v2_encoding.csv", 3)) {
                (double latitude, double longitude) = GPC.Decode(r[2]);
                Assert.Equal(r[2], GPC.Encode(latitude, longitude, false));
            }
        }

        /// <summary>Every vector reports the expected cell boundaries.</summary>
        [Fact]
        public void ReturnsTheExpectedCellBoundaries() {
            List<string[]> data = Rows("v2_area.csv", 5);
            Assert.True(data.Count > 100, "expected a substantial corpus");
            foreach (string[] r in data) {
                Assert.Equal((Num(r[1]), Num(r[2]), Num(r[3]), Num(r[4])), GPC.DecodeToArea(r[0]));
            }
        }

        /// <summary>Classification and its reason match the shared expectations.</summary>
        [Fact]
        public void AgreesOnClassification() {
            List<string[]> data = Rows("v2_classify.csv", 3);
            Assert.True(data.Count > 10, "expected a classification corpus");
            foreach (string[] r in data) {
                (CodeClass kind, string message) = GPC.Validate(r[2]);
                Assert.Equal(Kind(r[0]), kind);
                Assert.Equal(r[1], message);
                Assert.Equal(Kind(r[0]), GPC.Classify(r[2]));
                Assert.Equal(Kind(r[0]) == CodeClass.Geometric, GPC.IsValid(r[2]));
            }
        }

        /// <summary>Decoding anything that is not geometric throws.</summary>
        [Fact]
        public void ThrowsOnAnythingThatIsNotGeometric() {
            foreach (string[] r in Rows("v2_classify.csv", 3)) {
                if (Kind(r[0]) == CodeClass.Geometric) {
                    continue;
                }
                // Eleven characters is version 1 by definition, so Decode reads
                // it rather than refusing it. Classify describes the version 2
                // grid, which this string is not part of.
                if (GPC.IsValidV1(r[2]).status) {
                    continue;
                }
                _ = Assert.ThrowsAny<Exception>(() => GPC.Decode(r[2]));
            }
        }

        /// <summary>A reserved code gets its own reason, not an invalid one.</summary>
        [Fact]
        public void GivesAReservedCodeItsOwnReason() {
            int seen = 0;
            foreach (string[] r in Rows("v2_classify.csv", 3)) {
                if (Kind(r[0]) != CodeClass.Reserved) {
                    continue;
                }
                seen++;
                GPCException error = Assert.Throws<GPCException>(() => GPC.Decode(r[2]));
                Assert.Equal("GPC_RESERVED", error.Reason);
            }
            Assert.True(seen > 0, "expected at least one reserved code");
        }

        /// <summary>The check character matches the shared expectations.</summary>
        [Fact]
        public void ComputesTheExpectedCheckCharacter() {
            List<string[]> data = Rows("v2_check.csv", 2);
            Assert.True(data.Count > 10, "expected a check corpus");
            foreach (string[] r in data) {
                Assert.Equal(r[1], GPC.CheckCharacter(r[0]));
                Assert.Equal(GPC.Classify(r[0]), GPC.Classify($"{r[0]}*{r[1]}"));
            }
        }


        /// <summary>Short-form recovery, against every reference in the corpus.</summary>
        [Fact]
        public void RecoversEveryShortFormAgainstItsReference() {
            List<string[]> data = Rows("v2_short.csv", 4);
            Assert.True(data.Count > 100, "expected a short-form corpus");
            foreach (string[] r in data) {
                Assert.Equal(r[3], GPC.RecoverShort(r[0], Num(r[1]), Num(r[2]), false));
                Assert.Equal(r[0], GPC.Shorten(r[3]));
            }
        }

        /// <summary>The candidate list, and the order it comes back in.</summary>
        [Fact]
        public void SuggestsTheSameCorrectionsInTheSameOrder() {
            List<string[]> data = Rows("v2_corrections.csv", 5);
            Assert.True(data.Count > 10, "expected a corrections corpus");
            foreach (string[] r in data) {
                string[] expected = r[4].Length == 0 ? [] : r[4].Split(' ');
                Assert.Equal(expected, GPC.SuggestCorrections(r[3], Num(r[1]), Num(r[2]),
                    int.Parse(r[0], CultureInfo.InvariantCulture), false));
            }
        }

        /// <summary>Cells and their neighbours, at every level of every code.</summary>
        [Fact]
        public void TakesTheExpectedCellAndNeighboursAtEveryLevel() {
            List<string[]> data = Rows("v2_cells.csv", 4);
            Assert.True(data.Count > 50, "expected a cell corpus");
            foreach (string[] r in data) {
                string cell = GPC.Cell(r[1], int.Parse(r[0], CultureInfo.InvariantCulture));
                Assert.Equal(r[2], cell);
                Assert.Equal(r[3].Length == 0 ? [] : r[3].Split(' '), GPC.Neighbours(cell));
                Assert.True(GPC.Contains(cell, r[1]), $"{cell} should contain {r[1]}");
            }
        }

        /// <summary>The base-25 integer form, both directions.</summary>
        [Fact]
        public void ConvertsToAndFromTheIntegerForm() {
            List<string[]> data = Rows("v2_integer.csv", 2);
            Assert.True(data.Count > 50, "expected an integer corpus");
            foreach (string[] r in data) {
                long value = long.Parse(r[1], CultureInfo.InvariantCulture);
                Assert.Equal(value, GPC.ToInteger(r[0]));
                Assert.Equal(r[0], GPC.FromInteger(value, false));
            }
        }

        /// <summary>
        /// The one file compared to a tolerance. See SPEC.md 18.5: no standard
        /// library rounds sine, cosine or arc sine correctly, so asserting
        /// equality here would pass on one machine and fail on the next.
        /// </summary>
        [Fact]
        public void MeasuresEveryDistanceToWithinAMillimetre() {
            List<string[]> data = Rows("v2_distance.csv", 3);
            Assert.True(data.Count > 10, "expected a distance corpus");
            foreach (string[] r in data) {
                Assert.True(Math.Abs(GPC.Distance(r[0], r[1]) - Num(r[2])) < 0.001,
                    $"{r[0]} to {r[1]}: {GPC.Distance(r[0], r[1])} against {r[2]}");
            }
        }

        /// <summary>The geo URI, written and read back.</summary>
        [Fact]
        public void WritesAndReadsEveryGeoUri() {
            List<string[]> data = Rows("v2_geo.csv", 3);
            Assert.True(data.Count > 50, "expected a geo URI corpus");
            foreach (string[] r in data) {
                Assert.Equal(r[2], GPC.ToGeoURI(Num(r[0]), Num(r[1])));
                // Six decimal places, so a coordinate carrying more comes back
                // rounded. Everything Decode produces already has six, which is
                // why the round trip through a code is exact; that is asserted
                // in TestGPC rather than here.
                (double latitude, double longitude) = GPC.FromGeoURI(r[2]);
                Assert.True(Math.Abs(latitude - Num(r[0])) < 5e-7, r[2]);
                Assert.True(Math.Abs(longitude - Num(r[1])) < 5e-7, r[2]);
            }
        }

        /// <summary>Degrees, minutes and seconds, written and read back.</summary>
        [Fact]
        public void WritesAndReadsEveryDegreesMinutesSecondsForm() {
            List<string[]> data = Rows("v2_dms.csv", 3);
            Assert.True(data.Count > 50, "expected a DMS corpus");
            foreach (string[] r in data) {
                Assert.Equal(r[2], GPC.ToDMS(Num(r[0]), Num(r[1])));
                // Lossy by a hundredth of a second, so the coordinates come back
                // near rather than equal.
                (double latitude, double longitude) = GPC.FromDMS(r[2]);
                Assert.True(Math.Abs(latitude - Num(r[0])) < (0.5 / 360000) + 1e-12, r[2]);
                Assert.True(Math.Abs(longitude - Num(r[1])) < (0.5 / 360000) + 1e-12, r[2]);
            }
        }

        /// <summary>
        /// Every port embeds its own copy of the advisory list. This is how the
        /// four are held to being the same list, since CI cannot rebuild it.
        /// </summary>
        [Fact]
        public void CarriesTheSameAdvisoryListAsEveryOtherPort() {
            List<string[]> data = Rows("v2_screen_list.csv", 3);
            string[] row = Assert.Single(data);
            List<string> entries = [.. ScreenList.Entries];
            entries.Sort(StringComparer.Ordinal);
            Assert.Equal(int.Parse(row[1], CultureInfo.InvariantCulture), entries.Count);
            Assert.Equal(ScreenList.Version, row[0]);
            byte[] joined = Encoding.UTF8.GetBytes(string.Join('\n', entries));
            Assert.Equal(row[2], Convert.ToHexStringLower(SHA256.HashData(joined)));
        }

        /// <summary>The matched spans, including the codes that match nothing.</summary>
        [Fact]
        public void ScreensEveryVectorToTheExpectedSpans() {
            List<string[]> data = Rows("v2_screen.csv", 2);
            Assert.True(data.Count > 10, "expected a screening corpus");
            int matched = 0;
            foreach (string[] r in data) {
                List<(int Position, int Length)> expected = [];
                if (r[1].Length > 0) {
                    foreach (string span in r[1].Split(' ')) {
                        string[] parts = span.Split(':');
                        expected.Add((int.Parse(parts[0], CultureInfo.InvariantCulture),
                                      int.Parse(parts[1], CultureInfo.InvariantCulture)));
                    }
                }
                (string version, IReadOnlyList<(int Position, int Length)> spans) = GPC.Screen(r[0]);
                Assert.Equal(expected, spans);
                // The version comes back either way: a caller has to be able to
                // tell "clean under this list" from "never screened".
                Assert.Equal(ScreenList.Version, version);
                matched += expected.Count > 0 ? 1 : 0;
            }
            Assert.True(matched > 0, "expected at least one code to match");
        }

        /*  Version 1  */

        /// <summary>Every version 1 vector decodes to the expected coordinates.</summary>
        [Fact]
        public void DecodesEveryVersion1VectorToTheExpectedCoordinates() {
            List<string[]> data = Rows("decoding.csv", 3);
            Assert.True(data.Count > 100, "expected a substantial corpus");
            foreach (string[] r in data) {
                Assert.Equal((Num(r[1]), Num(r[2])), GPC.DecodeV1(r[0]));
            }
        }

        /// <summary>The formatted and unformatted version 1 forms decode alike.</summary>
        [Fact]
        public void DecodesTheFormattedAndUnformattedVersion1FormsAlike() {
            foreach (string[] r in Rows("decoding.csv", 3)) {
                Assert.Equal((Num(r[1]), Num(r[2])), GPC.DecodeV1(FormattedV1(r[0])));
            }
        }

        /// <summary>
        /// encoding.csv was built by the version 1 encoder, which no longer
        /// ships. What survives is the containment: the code names the cell the
        /// coordinate falls in, so decoding lands within one cell of it.
        /// </summary>
        [Fact]
        public void DecodesEveryVersion1CodeInsideTheCellItWasMadeFrom() {
            List<string[]> data = Rows("encoding.csv", 3);
            Assert.True(data.Count > 100, "expected a substantial corpus");
            foreach (string[] r in data) {
                (double latitude, double longitude) = GPC.DecodeV1(r[2]);
                Assert.True(Math.Abs(Num(r[0]) - latitude) < V1Cell, r[2]);
                Assert.True(Math.Abs(Num(r[1]) - longitude) < V1Cell, r[2]);
            }
        }

        /// <summary>Version 1 code validity matches the shared expectations.</summary>
        [Fact]
        public void AgreesOnVersion1CodeValidity() {
            List<string[]> data = Rows("validity_codes.csv", 3);
            Assert.True(data.Count > 10, "expected a validity corpus");
            foreach (string[] r in data) {
                (bool status, string message) = GPC.IsValidV1(r[2]);
                Assert.Equal(string.Equals(r[0], "true", StringComparison.Ordinal), status);
                Assert.Equal(r[1], message);
            }
        }

        /// <summary>Decoding an invalid version 1 code throws.</summary>
        [Fact]
        public void ThrowsWhenDecodingAnInvalidVersion1Code() {
            foreach (string[] r in Rows("validity_codes.csv", 3)) {
                if (string.Equals(r[0], "true", StringComparison.Ordinal)) {
                    continue;
                }
                _ = Assert.ThrowsAny<Exception>(() => GPC.DecodeV1(r[2]));
            }
        }
    }
}
