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
    /// Properties that hold for every point, checked over a wide generated sample.
    /// <para>
    /// The files in test_data/ pin behaviour case by case. This class pins the
    /// rules that must hold everywhere: a code is always ten characters, always
    /// spelled from the alphabet, always valid, and always decodes back inside
    /// the cell it came from. It also pins the two properties the whole format
    /// exists for -- containment of a shared prefix, and continuity of the
    /// ordering.
    /// </para>
    /// <para>
    /// The sample behind them is a hundred thousand coordinates that are
    /// generated rather than stored, so the same inputs reach every port without
    /// a large file in the repository. Its definition lives in
    /// test_data/README.md; the digest of the codes it produces lives in
    /// test_data/v2_sample.csv, which is what makes this class a cross-port check
    /// as well as a local one.
    /// </para>
    /// <para>
    /// Every constant below is written out rather than read from the
    /// implementation. A test that borrows the constant it is checking proves
    /// nothing.
    /// </para>
    /// </summary>
    public class PropertiesTests {

        /// <summary>The specified alphabet, written out rather than read from the implementation.</summary>
        private const string Alphabet = "0123456789CDFGHJKLMNPRTWX";

        /// <summary>Every code is this many characters, without separators.</summary>
        private const int CodeLength = 10;

        /// <summary>Every code is this many characters once formatted.</summary>
        private const int FormattedLength = 12;

        /// <summary>The grid of section 3.</summary>
        private const long Rows = 7_812_500;   // 4 * 5^9
        private const long Cols = 11_718_750;  // 6 * 5^9

        /// <summary>
        /// 24 * 25^4 level-5 cells, so one fewer transition between them, out of
        /// 24 * 25^9 - 1 steps in all. That is the 99.99999 % of section 5.3.
        /// </summary>
        private const long Level5Cells = 9_375_000;
        private const long TotalSteps = 91_552_734_374_999;

        // Generator constants. Kept beside the code that uses them so this file
        // reads as a standalone statement of the sample, the same way every
        // other port does.
        private const long Multiplier = 1_664_525;
        private const long Increment = 1_013_904_223;
        private const long Modulus = 4_294_967_296; // 2^32
        private const long LatitudeSpan = 18_000_001;   // -90.00000 .. 90.00000 in units of 1e-5
        private const long LongitudeSpan = 36_000_001;  // -180.00000 .. 180.00000 in units of 1e-5

        /// <summary>The sample, encoded once and shared by every test below.</summary>
        private static readonly Lazy<Sample> Shared = new(Build);

        /// <summary>The generated points, their codes, and the expected digest.</summary>
        /// <param name="Count">How many points the sample holds.</param>
        /// <param name="Seed">The value the generator starts from.</param>
        /// <param name="Digest">The digest every port must reproduce.</param>
        /// <param name="Points">The generated coordinates.</param>
        /// <param name="Codes">The unformatted code for each point.</param>
        private sealed record Sample(
            int Count, long Seed, string Digest, (double Latitude, double Longitude)[] Points, string[] Codes);

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

        /// <summary>Generate the sample and encode every point in it.</summary>
        /// <returns>The whole sample, ready to assert against.</returns>
        private static Sample Build() {
            string file = Path.Combine(TestDataDir(), "v2_sample.csv");
            int count = 0;
            long seed = 0;
            string digest = null;
            foreach (string raw in File.ReadAllLines(file)) {
                string line = raw.Trim();
                if (line.Length == 0 || line.StartsWith('#')) {
                    continue;
                }
                string[] fields = line.Split(',');
                count = int.Parse(fields[0], CultureInfo.InvariantCulture);
                seed = long.Parse(fields[1], CultureInfo.InvariantCulture);
                digest = fields[2];
                break;
            }
            if (digest is null) {
                throw new InvalidDataException("no data row in " + file);
            }

            // A linear congruential sequence whose products stay below 2^53, so
            // every port walks it exactly, including the ones whose only number
            // is a double.
            (double Latitude, double Longitude)[] points = new (double, double)[count];
            string[] codes = new string[count];
            long state = seed;
            for (int i = 0; i < count; i++) {
                state = ((Multiplier * state) + Increment) % Modulus;
                double latitude = ((state % LatitudeSpan) - ((LatitudeSpan - 1) / 2)) / 100000.0;
                state = ((Multiplier * state) + Increment) % Modulus;
                double longitude = ((state % LongitudeSpan) - ((LongitudeSpan - 1) / 2)) / 100000.0;
                points[i] = (latitude, longitude);
                codes[i] = GPC.Encode(latitude, longitude, false);
            }
            return new Sample(count, seed, digest, points, codes);
        }

        /// <summary>Section 5.1, restated. The row and column a coordinate falls in.</summary>
        /// <param name="latitude">Latitude in decimal degrees.</param>
        /// <param name="longitude">Longitude in decimal degrees.</param>
        /// <returns>The row and column.</returns>
        private static (long row, long col) Grid(double latitude, double longitude) {
            if (longitude == 180.0) {
                longitude = -180.0;
            }
            long row = (long)Math.Floor((latitude + 90.0) * 7812500.0 / 180.0);
            long col = (long)Math.Floor((longitude + 180.0) * 11718750.0 / 360.0);
            return (Math.Clamp(row, 0, Rows - 1), Math.Clamp(col, 0, Cols - 1));
        }

        /// <summary>The next code in plain ASCII order, which is base-25 counting.</summary>
        /// <param name="code">Any code short of the last one.</param>
        /// <returns>The code that follows it.</returns>
        private static string Successor(string code) {
            char[] out_ = code.ToCharArray();
            for (int position = out_.Length - 1; position >= 0; position--) {
                int index = Alphabet.IndexOf(out_[position], StringComparison.Ordinal) + 1;
                if (index < Alphabet.Length) {
                    out_[position] = Alphabet[index];
                    return new string(out_);
                }
                out_[position] = Alphabet[0];
            }
            throw new OverflowException("ran off the end of the code space");
        }

        /// <summary>The sample is large enough to be worth trusting.</summary>
        [Fact]
        public void DrawsASubstantialSample() {
            Sample sample = Shared.Value;
            Assert.True(sample.Count >= 100_000, "expected at least a hundred thousand points");
            Assert.Equal(sample.Count, sample.Codes.Length);
        }

        /// <summary>The one assertion that fails when two ports stop agreeing.</summary>
        [Fact]
        public void ReproducesTheDigestEveryOtherPortReproduces() {
            Sample sample = Shared.Value;
            byte[] joined = Encoding.UTF8.GetBytes(string.Join('\n', sample.Codes));
            Assert.Equal(sample.Digest, Convert.ToHexStringLower(SHA256.HashData(joined)));
        }

        /// <summary>Every code is exactly ten characters.</summary>
        [Fact]
        public void GivesEveryCodeTheFixedLength() {
            foreach (string code in Shared.Value.Codes) {
                Assert.True(code.Length == CodeLength,
                    $"{code} is {code.Length} characters, not {CodeLength}");
            }
        }

        /// <summary>Every code is spelled from the specified alphabet.</summary>
        [Fact]
        public void SpellsEveryCodeFromTheAlphabet() {
            foreach (string code in Shared.Value.Codes) {
                foreach (char character in code) {
                    Assert.True(Alphabet.Contains(character, StringComparison.Ordinal),
                        $"{code} contains {character}, outside the alphabet");
                }
            }
        }

        /// <summary>Level 1 yields 24 indices, so the X-prefixed space is unreachable.</summary>
        [Fact]
        public void NeverEncodesIntoTheReservedNamespace() {
            foreach (string code in Shared.Value.Codes) {
                Assert.True(code[0] != 'X', $"{code} was encoded but begins with X");
            }
        }

        /// <summary>Every code the encoder produces passes validation.</summary>
        [Fact]
        public void ValidatesEveryCodeItProduced() {
            foreach (string code in Shared.Value.Codes) {
                Assert.True(GPC.IsValid(code),
                    $"{code} came out of Encode but failed validation: {GPC.Validate(code).message}");
            }
        }

        /// <summary>Decoding lands inside the cell the point came from.</summary>
        [Fact]
        public void DecodesBackInsideTheCellThePointCameFrom() {
            Sample sample = Shared.Value;
            for (int i = 0; i < sample.Count; i++) {
                (double south, double west, double north, double east) = GPC.DecodeToArea(sample.Codes[i]);
                (double latitude, double longitude) = GPC.Decode(sample.Codes[i]);
                Assert.True(
                    latitude >= south && latitude <= north && longitude >= west && longitude <= east,
                    $"{sample.Codes[i]} decoded outside its own area");
            }
        }

        /// <summary>Decoding then encoding returns the code unchanged.</summary>
        [Fact]
        public void RoundTripsEveryCodeUnchanged() {
            foreach (string code in Shared.Value.Codes) {
                (double latitude, double longitude) = GPC.Decode(code);
                string again = GPC.Encode(latitude, longitude, false);
                Assert.True(again == code, $"{code} re-encoded as {again} after decoding");
            }
        }

        /// <summary>Formatting adds separators and changes nothing else.</summary>
        [Fact]
        public void FormatsTheCodeByAddingSeparatorsAndNothingElse() {
            Sample sample = Shared.Value;
            for (int i = 0; i < 1000; i++) {
                (double latitude, double longitude) = sample.Points[i];
                string code = sample.Codes[i];
                string formatted = GPC.Encode(latitude, longitude, true);
                Assert.Equal(FormattedLength, formatted.Length);
                Assert.Equal($"#{code[..5]}-{code[5..]}", formatted);
            }
        }

        /// <summary>
        /// Section 11.1. The alphabet is ASCII-ascending, so sorting codes as
        /// bytes sorts them the way the grid is traversed.
        /// </summary>
        [Fact]
        public void SortsAsAStringTheWayItSortsInSpace() {
            string[] sorted = new string[20_000];
            Array.Copy(Shared.Value.Codes, sorted, sorted.Length);
            Array.Sort(sorted, StringComparer.Ordinal);
            for (int i = 1; i < sorted.Length; i++) {
                Assert.True(string.CompareOrdinal(sorted[i - 1], sorted[i]) <= 0);
            }
        }

        /// <summary>
        /// Section 10. Two codes agree in their first k characters if and only if
        /// the points lie in the same level-k cell.
        /// </summary>
        [Fact]
        public void GivesOnePrefixToOneCellAndOneCellToOnePrefix() {
            Sample sample = Shared.Value;
            Dictionary<string, string> cells = [];
            Dictionary<string, string> byPrefix = [];
            for (int i = 0; i < 20_000; i++) {
                (long row, long col) = Grid(sample.Points[i].Latitude, sample.Points[i].Longitude);
                for (int k = 1; k <= 10; k++) {
                    long p = (long)Math.Pow(5, 10 - k);
                    string key = $"{k}:{row / p}:{col / p}";
                    string prefix = $"{k}:{sample.Codes[i][..k]}";
                    if (cells.TryGetValue(key, out string seen)) {
                        Assert.True(string.Equals(seen, prefix, StringComparison.Ordinal),
                            $"{key} named twice");
                    } else {
                        cells[key] = prefix;
                    }
                    if (byPrefix.TryGetValue(prefix, out string named)) {
                        Assert.True(string.Equals(named, key, StringComparison.Ordinal),
                            $"{prefix} names two cells");
                    } else {
                        byPrefix[prefix] = key;
                    }
                }
            }
        }

        /// <summary>The box of a code lies inside its level-k cell, for every k.</summary>
        [Fact]
        public void KeepsTheBoxOfACodeInsideItsLevelKCell() {
            Sample sample = Shared.Value;
            for (int i = 0; i < 2000; i++) {
                (long row, long col) = Grid(sample.Points[i].Latitude, sample.Points[i].Longitude);
                (double south, double west, double north, double east) = GPC.DecodeToArea(sample.Codes[i]);
                for (int k = 1; k <= 10; k++) {
                    long p = (long)Math.Pow(5, 10 - k);
                    // The same expression shape section 6.3 uses, so when the
                    // cell edge and the box edge coincide they are the identical
                    // double.
                    double cellSouth = (row / p * p * 180.0 / 7812500.0) - 90.0;
                    double cellNorth = (((row / p) + 1) * p * 180.0 / 7812500.0) - 90.0;
                    double cellWest = (col / p * p * 360.0 / 11718750.0) - 180.0;
                    double cellEast = (((col / p) + 1) * p * 360.0 / 11718750.0) - 180.0;
                    Assert.True(cellSouth <= south && north <= cellNorth,
                        $"{sample.Codes[i]} k={k} escapes its cell in latitude");
                    Assert.True(cellWest <= west && east <= cellEast,
                        $"{sample.Codes[i]} k={k} escapes its cell in longitude");
                }
            }
        }

        /// <summary>The discontinuity count is the one section 5.3 states.</summary>
        [Fact]
        public void CountsTheDiscontinuitiesTheSpecificationCounts() {
            Assert.Equal(Level5Cells, 24 * 25L * 25 * 25 * 25);
            Assert.Equal(9_374_999L, Level5Cells - 1);
            Assert.Equal(TotalSteps, (24 * 25L * 25 * 25 * 25 * 25 * 25 * 25 * 25 * 25) - 1);
            double share = (double)(TotalSteps - (Level5Cells - 1)) / TotalSteps;
            Assert.Equal("99.99999", (share * 100).ToString("F5", CultureInfo.InvariantCulture));
        }

        /// <summary>
        /// Section 11.2. Consecutive codes are adjacent cells inside a level-5
        /// cell. A transcription error anywhere in the reflection breaks this.
        /// </summary>
        [Fact]
        public void PutsConsecutiveCodesInAdjacentCellsInsideALevelFiveCell() {
            (double, double)[] starts = [
                (43.65, -79.38), (-33.8568, 151.2153), (0.0, 0.0),
                (64.1466, -21.9426), (-13.1631, -72.545), (23.0225, 72.5714)
            ];
            foreach ((double latitude, double longitude) in starts) {
                string code = GPC.Encode(latitude, longitude, false);
                string prefix = code[..5];
                (double first, double second) = GPC.Decode(code);
                (long row, long col) previous = Grid(first, second);
                int walked = 0;
                for (int step = 0; step < 4000; step++) {
                    code = Successor(code);
                    if (!string.Equals(code[..5], prefix, StringComparison.Ordinal)) {
                        break;
                    }
                    (double decodedLatitude, double decodedLongitude) = GPC.Decode(code);
                    (long row, long col) current = Grid(decodedLatitude, decodedLongitude);
                    long distance = Math.Abs(current.row - previous.row)
                        + Math.Abs(current.col - previous.col);
                    Assert.True(distance == 1, $"{code} is {distance} cells from the code before it");
                    previous = current;
                    walked++;
                }
                Assert.True(walked > 100, "expected a substantial walk inside one cell");
            }
        }

        /// <summary>
        /// The traversal of one level-5 cell ends at its far corner and the next
        /// begins at its near corner, so the step between them is never adjacent.
        /// </summary>
        [Fact]
        public void MakesEveryLevelFiveTransitionAJump() {
            int tested = 0;
            foreach (double latitude in new[] { -80.0, -40.0, -5.0, 5.0, 40.0, 80.0 }) {
                foreach (double longitude in new[] { -170.0, -100.0, -20.0, 20.0, 100.0, 170.0 }) {
                    string prefix = GPC.Encode(latitude, longitude, false)[..5];
                    string following = Successor(prefix);
                    if (following[0] == 'X') {
                        continue; // ran into the reserved namespace
                    }
                    (double lastLatitude, double lastLongitude) = GPC.Decode(prefix + "XXXXX");
                    (double firstLatitude, double firstLongitude) = GPC.Decode(following + "00000");
                    (long row, long col) = Grid(lastLatitude, lastLongitude);
                    (long row, long col) first = Grid(firstLatitude, firstLongitude);
                    long distance = Math.Abs(row - first.row) + Math.Abs(col - first.col);
                    Assert.True(distance != 1, $"{prefix} runs straight into the next cell");
                    tested++;
                }
            }
            Assert.True(tested > 20, "expected a substantial number of transitions");
        }
    }
}
