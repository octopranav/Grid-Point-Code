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
    /// rules that must hold everywhere: a code is always the same length, always
    /// spelled from the alphabet, always valid, and always decodes back inside
    /// the cell it came from.
    /// </para>
    /// <para>
    /// The sample behind them is a hundred thousand coordinates that are
    /// generated rather than stored, so the same inputs reach every port without
    /// a large file in the repository. Its definition lives in
    /// test_data/README.md; the digest of the codes it produces lives in
    /// test_data/sample.csv, which is what makes this class a cross-port check as
    /// well as a local one.
    /// </para>
    /// </summary>
    public class PropertiesTests {

        /// <summary>
        /// The specified alphabet, written out rather than read from the
        /// implementation: a test that borrows the constant it is checking
        /// proves nothing.
        /// </summary>
        private const string Alphabet = "CDFGHJKLMNPRTVWXY0123456789";

        /// <summary>Every code is this many characters, without separators.</summary>
        private const int CodeLength = 11;

        /// <summary>Every code is this many characters once formatted.</summary>
        private const int FormattedLength = 14;

        /// <summary>One cell is a hundred-thousandth of a degree on each axis.</summary>
        private const double Cell = 1e-5;

        // Generator constants. Kept beside the code that uses them so this file
        // reads as a standalone statement of the sample, the same way every
        // other port does.
        private const long Multiplier = 1_664_525;
        private const long Increment = 1_013_904_223;
        private const long Modulus = 4_294_967_296; // 2^32
        private const long LatitudeSpan = 17_999_999;
        private const long LongitudeSpan = 35_999_999;

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
            string file = Path.Combine(TestDataDir(), "sample.csv");
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

        /// <summary>Every code is exactly eleven characters.</summary>
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

        /// <summary>Every code the encoder produces passes validation.</summary>
        [Fact]
        public void ValidatesEveryCodeItProduced() {
            foreach (string code in Shared.Value.Codes) {
                (bool status, string message) = GPC.IsValid(code);
                Assert.True(status, $"{code} came out of Encode but failed validation: {message}");
            }
        }

        /// <summary>Decoding lands inside the cell the point came from.</summary>
        [Fact]
        public void DecodesBackInsideTheCellThePointCameFrom() {
            Sample sample = Shared.Value;
            for (int i = 0; i < sample.Count; i++) {
                (double latitude, double longitude) = sample.Points[i];
                (double decodedLatitude, double decodedLongitude) = GPC.Decode(sample.Codes[i]);
                Assert.True(
                    Math.Abs(latitude - decodedLatitude) < Cell
                    && Math.Abs(longitude - decodedLongitude) < Cell,
                    $"{sample.Codes[i]} decoded to ({decodedLatitude}, {decodedLongitude}), "
                    + $"more than one cell from ({latitude}, {longitude})");
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
                Assert.Equal($"#{code[..4]}-{code[4..8]}-{code[8..11]}", formatted);
            }
        }
    }
}
