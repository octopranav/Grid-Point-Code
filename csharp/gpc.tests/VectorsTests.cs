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
using Xunit;

namespace Ca.Pranavpatel.Algo.GridPointCode.Tests {
    /// <summary>
    /// Runs the shared conformance vectors in test_data/. Every port reads these
    /// same files, so a disagreement between languages shows up here rather than
    /// in a release.
    /// </summary>
    public class VectorsTests {

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

        /// <summary>Rebuild the formatted #XXXX-XXXX-XXX form of an unformatted code.</summary>
        /// <param name="code">Unformatted eleven-character code.</param>
        /// <returns>The formatted code.</returns>
        private static string Formatted(string code) {
            return $"#{code[..4]}-{code[4..8]}-{code[8..11]}";
        }

        /// <summary>Every vector encodes to the expected code.</summary>
        [Fact]
        public void EncodesEveryVectorToTheExpectedCode() {
            List<string[]> data = Rows("encoding.csv", 3);
            Assert.True(data.Count > 100, "expected a substantial corpus");
            foreach (string[] r in data) {
                Assert.Equal(r[2], GPC.Encode(Num(r[0]), Num(r[1]), false));
            }
        }

        /// <summary>Every vector decodes to the expected coordinates.</summary>
        [Fact]
        public void DecodesEveryVectorToTheExpectedCoordinates() {
            List<string[]> data = Rows("decoding.csv", 3);
            Assert.True(data.Count > 100, "expected a substantial corpus");
            foreach (string[] r in data) {
                Assert.Equal((Num(r[1]), Num(r[2])), GPC.Decode(r[0]));
            }
        }

        /// <summary>The formatted and unformatted forms decode alike.</summary>
        [Fact]
        public void DecodesTheFormattedAndUnformattedFormsAlike() {
            foreach (string[] r in Rows("decoding.csv", 3)) {
                Assert.Equal((Num(r[1]), Num(r[2])), GPC.Decode(Formatted(r[0])));
            }
        }

        /// <summary>Decoding then encoding returns the original code.</summary>
        [Fact]
        public void RoundTripsEveryEncodedCodeBackToItself() {
            foreach (string[] r in Rows("encoding.csv", 3)) {
                (double latitude, double longitude) = GPC.Decode(r[2]);
                Assert.Equal(r[2], GPC.Encode(latitude, longitude, false));
            }
        }

        /// <summary>Code validity matches the shared expectations.</summary>
        [Fact]
        public void AgreesOnCodeValidity() {
            List<string[]> data = Rows("validity_codes.csv", 3);
            Assert.True(data.Count > 10, "expected a validity corpus");
            foreach (string[] r in data) {
                (bool status, string message) = GPC.IsValid(r[2]);
                Assert.Equal(string.Equals(r[0], "true", StringComparison.Ordinal), status);
                Assert.Equal(r[1], message);
            }
        }

        /// <summary>Decoding an invalid code throws.</summary>
        [Fact]
        public void ThrowsWhenDecodingAnInvalidCode() {
            foreach (string[] r in Rows("validity_codes.csv", 3)) {
                if (string.Equals(r[0], "true", StringComparison.Ordinal)) {
                    continue;
                }
                _ = Assert.ThrowsAny<ArgumentException>(() => GPC.Decode(r[2]));
            }
        }

        /// <summary>Coordinate validity matches the shared expectations.</summary>
        [Fact]
        public void AgreesOnCoordinateValidity() {
            List<string[]> data = Rows("validity_coordinates.csv", 4);
            Assert.True(data.Count > 10, "expected a validity corpus");
            foreach (string[] r in data) {
                (bool status, string message) = GPC.IsValid(Num(r[0]), Num(r[1]));
                Assert.Equal(string.Equals(r[2], "true", StringComparison.Ordinal), status);
                Assert.Equal(r[3], message);
            }
        }

        /// <summary>Encoding an out-of-range coordinate throws.</summary>
        [Fact]
        public void ThrowsWhenEncodingAnOutOfRangeCoordinate() {
            foreach (string[] r in Rows("validity_coordinates.csv", 4)) {
                if (string.Equals(r[2], "true", StringComparison.Ordinal)) {
                    continue;
                }
                _ = Assert.ThrowsAny<ArgumentException>(() => GPC.Encode(Num(r[0]), Num(r[1])));
            }
        }
    }
}
