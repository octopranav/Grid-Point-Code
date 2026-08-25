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
using System.Text;

[assembly: CLSCompliant(true)]
namespace Ca.Pranavpatel.Algo.GridPointCode {
    /// <summary>
    /// Version 2 of the Grid Point Code format.
    /// <para>
    /// A code names one cell of a fixed grid laid over the Earth. Ten
    /// characters, always. The first divides the world into 24 cells of 45 by 60
    /// degrees; each of the nine after it divides the cell named so far into 25
    /// parts, five by five. Two codes that begin with the same k characters
    /// therefore name points in the same level-k cell -- containment, not
    /// correlation, so it holds for every pair of points without exception.
    /// </para>
    /// <para>
    /// The whole format is arithmetic. There are no ordering tables and no
    /// generated constants: a serpentine at level 1, a Peano digit reflection
    /// below it, and one parity reset entering level 6. Section numbers in the
    /// comments refer to SPEC.md, which is the normative description and the
    /// thing to implement from.
    /// </para>
    /// <para>
    /// Version 1 codes still decode, because codes end up on signs and in
    /// records and removing that would orphan every one of them.
    /// <see cref="Decode" /> dispatches on length -- ten characters is version 2,
    /// eleven is version 1 -- and <see cref="Encode(double, double)" /> emits
    /// version 2 only, so the old format cannot be minted again.
    /// </para>
    /// </summary>
    public static class GPC {
        // Section 4. Twenty-five symbols, digits first so that the alphabet is
        // ASCII-ascending and a plain string sort is a spatial sort. No vowel
        // appears, so no English word can be spelled by a code.
        private const string ALPHABET = "0123456789CDFGHJKLMNPRTWX";

        // Section 3.
        private const int CODE_LENGTH = 10;
        private const int LEVELS = 10;
        // Section 5.3: both parity accumulators reset entering this level.
        private const int RESET_LEVEL = 6;
        private const long P9 = 1_953_125;      // 5^9
        private const long ROWS = 7_812_500;    // 4 * 5^9
        private const long COLS = 11_718_750;   // 6 * 5^9

        // Section 2.
        private const double MIN_LAT = -90;
        private const double MAX_LAT = 90;
        private const double MIN_LONG = -180;
        private const double MAX_LONG = 180;

        private const char PREFIX = '#';
        private const char SEPARATOR = '-';
        private const char CHECK_MARK = '*';

        // Section 8. Exactly the letters that are not in the alphabet, less U, Q
        // and Y, which are rejected rather than aliased. L is a real symbol and
        // is never aliased to 1: it names a different cell, and aliasing it
        // would make two different codes collide.
        private const string ALIASED = "OISZBAEV";
        private const string ALIASES = "0152843W";

        // ASCII whitespace only. A routine that also stripped the Unicode spaces
        // would accept in one port what another rejects, which is the whole
        // thing the shared vectors exist to prevent.
        private const string WHITESPACE = " \t\n\v\f\r";

        // The field element t, whose symbol index is 1 * 5 + 0. Section 14.2.
        private const int T = 5;

        // t^1 to t^11, the eleven check weights. Computed rather than transcribed.
        private static readonly int[] WEIGHTS = PowersOfT();

        /*  PART 1 : ENCODE */

        /// <summary>Encodes coordinates as a version 2 Grid Point Code.</summary>
        /// <param name="latitude">Latitude in decimal degrees, -90 to 90 inclusive.</param>
        /// <param name="longitude">Longitude in decimal degrees, -180 to 180 inclusive.</param>
        /// <returns>The formatted code.</returns>
        /// <exception cref="ArgumentOutOfRangeException">
        /// if <paramref name="latitude" /> or <paramref name="longitude" /> is
        /// outside the domain, NaN or infinite.
        /// </exception>
        public static string Encode(double latitude, double longitude) {
            return Encode(latitude, longitude, true);
        }

        /// <summary>Encodes coordinates as a version 2 Grid Point Code.</summary>
        /// <param name="latitude">Latitude in decimal degrees, -90 to 90 inclusive.</param>
        /// <param name="longitude">Longitude in decimal degrees, -180 to 180 inclusive.</param>
        /// <param name="formatted">
        /// True for <c>#XXXXX-XXXXX</c>, false for the bare ten characters. Both
        /// denote the same code.
        /// </param>
        /// <returns>The code.</returns>
        /// <exception cref="ArgumentOutOfRangeException">
        /// if <paramref name="latitude" /> or <paramref name="longitude" /> is
        /// outside the domain, NaN or infinite.
        /// </exception>
        public static string Encode(double latitude, double longitude, bool formatted) {
            (bool valid, string message) = IsValid(latitude, longitude);
            if (!valid) {
                throw new ArgumentOutOfRangeException(paramName: message,
                    message: $"{message}: value out of valid range.");
            }

            (long row, long col) = ToGrid(latitude, longitude);
            string code = GridToCode(row, col);
            return formatted ? FormatGPC(code) : code;
        }

        /// <summary>
        /// Whether a coordinate pair is inside the domain, and which axis is not.
        /// <para>
        /// The poles and both ends of the antimeridian are inside it; version 1
        /// rejected all of them. NaN and the infinities fail the comparisons and
        /// so are rejected here as well, in every language, without a separate
        /// test.
        /// </para>
        /// </summary>
        /// <param name="latitude">Latitude in decimal degrees.</param>
        /// <param name="longitude">Longitude in decimal degrees.</param>
        /// <returns>Validity status with "LATITUDE" or "LONGITUDE" if any.</returns>
        public static (bool status, string message) IsValid(double latitude, double longitude) {
            if (!(latitude >= MIN_LAT && latitude <= MAX_LAT)) {
                return (false, "LATITUDE");
            }
            if (!(longitude >= MIN_LONG && longitude <= MAX_LONG)) {
                return (false, "LONGITUDE");
            }
            return (true, string.Empty);
        }

        /// <summary>
        /// Coordinates to a row and column of the full grid. Section 5.1.
        /// <para>
        /// Three floating-point operations per axis, associating left to right.
        /// They are the only floating-point arithmetic in the format, and section
        /// 7 pins how they are evaluated: no reassociation, no fused
        /// multiply-add, no wider intermediate. Everything after this is integers.
        /// </para>
        /// </summary>
        /// <param name="latitude">Latitude in decimal degrees.</param>
        /// <param name="longitude">Longitude in decimal degrees.</param>
        /// <returns>The row and column.</returns>
        public static (long row, long col) ToGrid(double latitude, double longitude) {
            // The one case where two distinct inputs must give one code, so it
            // happens before any arithmetic that could no longer tell them apart.
            if (longitude == MAX_LONG) {
                longitude = MIN_LONG;
            }

            long row = (long)Math.Floor((latitude + 90.0) * 7812500.0 / 180.0);
            long col = (long)Math.Floor((longitude + 180.0) * 11718750.0 / 360.0);


            // Catches latitude +90, and nothing else. It is what makes the poles
            // encode instead of indexing past the end of the grid.
            row = row < 0 ? 0 : row > ROWS - 1 ? ROWS - 1 : row;
            col = col < 0 ? 0 : col > COLS - 1 ? COLS - 1 : col;
            return (row, col);
        }

        /// <summary>
        /// A row and column to ten characters. Section 5.2.
        /// <para>
        /// Level 1 is a serpentine over the 24 blocks, west to east, snaking
        /// northward. Levels 2 to 10 are a Peano digit reflection: each axis is
        /// mirrored according to the parity of the digits accumulated in the
        /// other, which is what puts consecutive codes in adjacent cells.
        /// </para>
        /// </summary>
        /// <param name="row">Row of the full grid.</param>
        /// <param name="col">Column of the full grid.</param>
        /// <returns>The unformatted ten-character code.</returns>
        public static string GridToCode(long row, long col) {
            long r1 = row / P9;
            long c1 = col / P9;
            StringBuilder code = new(CODE_LENGTH);
            _ = code.Append(ALPHABET[(int)((r1 * 6) + (r1 % 2 == 0 ? c1 : 5 - c1))]);

            long sr = r1;
            long sc = c1;
            long p = P9;
            for (int level = 2; level <= LEVELS; level++) {
                if (level == RESET_LEVEL) {
                    // Section 5.3. Without this the last five characters would
                    // mean something different in every level-5 cell, and the
                    // short form would name nothing on its own.
                    sr = 0;
                    sc = 0;
                }
                p /= 5;
                long r = row / p % 5;
                long c = col / p % 5;
                // The order of these four statements is normative. R is decided
                // from sc before this level's c is added to it, and C from sr
                // after this level's r has been added. Reversing either is a
                // different format.
                long bigR = sc % 2 == 0 ? r : 4 - r;
                sr += r;
                long bigC = sr % 2 == 0 ? c : 4 - c;
                sc += c;
                _ = code.Append(ALPHABET[(int)((bigR * 5) + bigC)]);
            }

            return code.ToString();
        }

        /// <summary>
        /// The presentation form, <c>#XXXXX-XXXXX</c>. Section 5.4.
        /// <para>
        /// The grouping is not arbitrary: the second group is exactly the short
        /// form, so a printed code shows its own local form.
        /// </para>
        /// </summary>
        /// <param name="code">Unformatted ten-character code.</param>
        /// <returns>The formatted code.</returns>
        public static string FormatGPC(string code) {
            return $"{PREFIX}{code[..5]}{SEPARATOR}{code[5..]}";
        }

        /*  PART 2 : DECODE */

        /// <summary>
        /// Decodes a code to the centre of the cell it names.
        /// <para>
        /// Dispatches on length once the separators are stripped: ten characters
        /// is version 2, eleven is version 1. A code carrying a check character
        /// is always version 2, since version 1 has none.
        /// </para>
        /// </summary>
        /// <param name="gridPointCode">
        /// Formatted or unformatted, with or without a <c>*</c> check character.
        /// </param>
        /// <returns>Coordinates in decimal degrees, six decimal places.</returns>
        /// <exception cref="GPCException">
        /// with reason GPC_RESERVED for a well-formed code beginning with X, or
        /// one of the invalid reasons otherwise.
        /// </exception>
        public static (double Latitude, double Longitude) Decode(string gridPointCode) {
            (string payload, string check) = Split(gridPointCode);
            if (check is null && payload.Length == V1.CODE_LENGTH) {
                return V1.Decode(payload);
            }

            (long row, long col) = CodeToGrid(Geometric(gridPointCode));
            return (Round6((((2 * row) + 1) * 1152) - 9_000_000_000L),
                    Round6((((2 * col) + 1) * 1536) - 18_000_000_000L));
        }

        /// <summary>The boundaries of the cell a version 2 code names. Section 6.3.</summary>
        /// <param name="gridPointCode">Formatted or unformatted code.</param>
        /// <returns>The south, west, north and east edges of the cell.</returns>
        /// <exception cref="GPCException">
        /// as <see cref="Decode" />. Version 1 codes have no area; they resolve
        /// to a corner and are not part of this grid.
        /// </exception>
        public static (double South, double West, double North, double East) DecodeToArea(string gridPointCode) {
            (long row, long col) = CodeToGrid(Geometric(gridPointCode));
            return ((row * 180.0 / 7812500.0) - 90.0,
                    (col * 360.0 / 11718750.0) - 180.0,
                    ((row + 1) * 180.0 / 7812500.0) - 90.0,
                    ((col + 1) * 360.0 / 11718750.0) - 180.0);
        }

        /// <summary>
        /// Decodes an eleven-character version 1 code. Appendix B.
        /// <para>
        /// <see cref="Decode" /> reaches this on its own for anything eleven
        /// characters long. The explicit entry point is here for a caller that
        /// knows which format it holds and wants to say so.
        /// </para>
        /// <para>
        /// Version 1 returns the corner of its cell rather than the centre, which
        /// is what every version 1 release has returned.
        /// </para>
        /// </summary>
        /// <param name="gridPointCode">Formatted or unformatted version 1 code.</param>
        /// <returns>Coordinates in decimal degrees.</returns>
        /// <exception cref="GPCException">if the code is null, malformed, or outside the grid.</exception>
        public static (double Latitude, double Longitude) DecodeV1(string gridPointCode) {
            return V1.Decode(gridPointCode);
        }

        /// <summary>
        /// Ten characters back to a row and column. Section 6.1.
        /// <para>
        /// The inverse of <see cref="GridToCode" />, character by character.
        /// Expects a normalised, geometric code.
        /// </para>
        /// </summary>
        /// <param name="code">Normalised ten-character code.</param>
        /// <returns>The row and column.</returns>
        public static (long row, long col) CodeToGrid(string code) {
            int i = ALPHABET.IndexOf(code[0], StringComparison.Ordinal);
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
                int j = ALPHABET.IndexOf(code[level - 1], StringComparison.Ordinal);
                long bigR = j / 5;
                long bigC = j % 5;
                long r = sc % 2 == 0 ? bigR : 4 - bigR;
                sr += r;
                long c = sr % 2 == 0 ? bigC : 4 - bigC;
                sc += c;
                row = (row * 5) + r;
                col = (col * 5) + c;
            }

            return (row, col);
        }

        /*  PART 3 : PARSE, CLASSIFY, CHECK */

        /// <summary>
        /// Case-folds, strips separators, applies the alias table. Section 8.
        /// </summary>
        /// <param name="gridPointCode">Anything a person might have typed.</param>
        /// <returns>
        /// The payload, and the check character, which is null when the input
        /// carried no <c>*</c>. The check is returned however long it normalised:
        /// deciding whether it is acceptable belongs to
        /// <see cref="Validate(string)" />.
        /// </returns>
        /// <exception cref="GPCException">GPC_NULL if there is nothing at all to parse.</exception>
        public static (string payload, string check) Normalise(string gridPointCode) {
            (string payload, string check) = Split(gridPointCode);
            return (Alias(payload), check is null ? null : Alias(check));
        }

        /// <summary>
        /// Classifies a string and says why, if the answer is INVALID. Section 9.
        /// </summary>
        /// <param name="gridPointCode">Anything a person might have typed.</param>
        /// <returns>
        /// The class, and the reason code, which is empty for anything that is
        /// not INVALID, and is otherwise GPC_NULL, GPC_LENGTH, GPC_CHAR or
        /// GPC_CHECK, tested in that order.
        /// </returns>
        public static (CodeClass kind, string message) Validate(string gridPointCode) {
            string code;
            string check;
            try {
                (code, check) = Normalise(gridPointCode);
            }
            catch (GPCException error) {
                return (CodeClass.Invalid, error.Reason);
            }
            if (code.Length != CODE_LENGTH) {
                return (CodeClass.Invalid, "GPC_LENGTH");
            }
            foreach (char character in code) {
                if (!ALPHABET.Contains(character, StringComparison.Ordinal)) {
                    return (CodeClass.Invalid, "GPC_CHAR");
                }
            }
            // A check that does not hold is not something to discard. A caller
            // told a code is valid has to be able to decode it.
            if (check is not null && !string.Equals(check, CheckSymbol(code), StringComparison.Ordinal)) {
                return (CodeClass.Invalid, "GPC_CHECK");
            }
            return (code[0] == 'X' ? CodeClass.Reserved : CodeClass.Geometric, string.Empty);
        }

        /// <summary>Geometric, Reserved or Invalid. Section 9 and Appendix C.</summary>
        /// <param name="gridPointCode">Anything a person might have typed.</param>
        /// <returns>The class.</returns>
        public static CodeClass Classify(string gridPointCode) {
            return Validate(gridPointCode).kind;
        }

        /// <summary>
        /// Whether a string is a version 2 code that decodes.
        /// <para>
        /// True for Geometric only. A reserved code is false, because it names no
        /// cell, and so is a version 1 code: <see cref="Classify" /> describes
        /// this grid, and eleven characters are not part of it.
        /// <see cref="Decode" /> still reads version 1, and
        /// <see cref="IsValidV1" /> answers for it.
        /// </para>
        /// </summary>
        /// <param name="gridPointCode">Anything a person might have typed.</param>
        /// <returns>True if the string is a decodable version 2 code.</returns>
        public static bool IsValid(string gridPointCode) {
            return Validate(gridPointCode).kind == CodeClass.Geometric;
        }

        /// <summary>Whether a string is a version 1 code, and why not when it is not.</summary>
        /// <param name="gridPointCode">Anything a person might have typed.</param>
        /// <returns>Validity status with the reason code if any.</returns>
        public static (bool status, string message) IsValidV1(string gridPointCode) {
            return V1.IsValid(gridPointCode);
        }

        /// <summary>
        /// The optional GF(25) check character for a code. Section 14.
        /// <para>
        /// For voice, radio and paper. Written after a star,
        /// <c>#G3RJM-98NM9*T</c>. It detects every single-symbol error and every
        /// adjacent transposition, and it is not canonical: the ten-character
        /// form is what gets stored and interchanged, and this is never emitted
        /// unless asked for.
        /// </para>
        /// </summary>
        /// <param name="gridPointCode">Formatted or unformatted code.</param>
        /// <returns>The check character.</returns>
        /// <exception cref="GPCException">
        /// if the input is not ten symbols of the alphabet. A reserved code has a
        /// check character like any other.
        /// </exception>
        public static string CheckCharacter(string gridPointCode) {
            (string code, _) = Normalise(gridPointCode);
            if (code.Length != CODE_LENGTH) {
                throw new GPCException("GPC_LENGTH");
            }
            foreach (char character in code) {
                if (!ALPHABET.Contains(character, StringComparison.Ordinal)) {
                    throw new GPCException("GPC_CHAR");
                }
            }
            return CheckSymbol(code);
        }

        /*  PART 4 : INTERNALS */

        /// <summary>
        /// Payload and check character, cleaned but not yet aliased.
        /// <para>
        /// The dispatch in <see cref="Decode" /> needs to see the characters as
        /// typed, because version 1 has its own alphabet and the version 2 alias
        /// table would corrupt it.
        /// </para>
        /// </summary>
        /// <param name="gridPointCode">Anything a person might have typed.</param>
        /// <returns>The cleaned payload, and the cleaned check or null.</returns>
        private static (string payload, string check) Split(string gridPointCode) {
            if (gridPointCode is null) {
                throw new GPCException("GPC_NULL");
            }
            bool blank = true;
            foreach (char character in gridPointCode) {
                if (!WHITESPACE.Contains(character, StringComparison.Ordinal)) {
                    blank = false;
                    break;
                }
            }
            if (blank) {
                throw new GPCException("GPC_NULL");
            }

            string text = gridPointCode;
            string check = null;
            int star = text.IndexOf(CHECK_MARK, StringComparison.Ordinal);
            if (star >= 0) {
                check = Clean(text[(star + 1)..]);
                text = text[..star];
            }
            return (Clean(text), check);
        }

        /// <summary>
        /// Upper-cases by ASCII rules, then drops <c>#</c>, <c>-</c> and whitespace.
        /// <para>
        /// A locale-sensitive upper-casing routine would map <c>i</c> to a dotted
        /// capital in a Turkish locale, and the same code would be valid in one
        /// locale and invalid in another.
        /// </para>
        /// </summary>
        /// <param name="text">Raw input.</param>
        /// <returns>The cleaned characters.</returns>
        private static string Clean(string text) {
            StringBuilder cleaned = new(text.Length);
            foreach (char raw in text) {
                char character = raw is >= 'a' and <= 'z' ? (char)(raw - 32) : raw;
                if (character == PREFIX || character == SEPARATOR
                        || WHITESPACE.Contains(character, StringComparison.Ordinal)) {
                    continue;
                }
                _ = cleaned.Append(character);
            }
            return cleaned.ToString();
        }

        /// <summary>Reads the confusable letters as the symbols they were meant to be.</summary>
        /// <param name="text">Cleaned characters.</param>
        /// <returns>The aliased characters.</returns>
        private static string Alias(string text) {
            StringBuilder aliased = new(text.Length);
            foreach (char character in text) {
                int at = ALIASED.IndexOf(character, StringComparison.Ordinal);
                _ = aliased.Append(at < 0 ? character : ALIASES[at]);
            }
            return aliased.ToString();
        }

        /// <summary>The ten characters, or the typed error that stops decoding.</summary>
        /// <param name="gridPointCode">Anything a person might have typed.</param>
        /// <returns>The normalised, geometric code.</returns>
        private static string Geometric(string gridPointCode) {
            (CodeClass kind, string message) = Validate(gridPointCode);
            if (kind == CodeClass.Invalid) {
                throw new GPCException(message);
            }
            if (kind == CodeClass.Reserved) {
                throw new GPCException("GPC_RESERVED");
            }
            return Normalise(gridPointCode).payload;
        }

        /// <summary>(a + b*t) + (c + d*t), elements indexed b*5 + a. Section 14.2.</summary>
        /// <param name="x">One element.</param>
        /// <param name="y">The other element.</param>
        /// <returns>Their sum.</returns>
        private static int GfAdd(int x, int y) {
            return (((x / 5) + (y / 5)) % 5 * 5) + (((x % 5) + (y % 5)) % 5);
        }

        /// <summary>(a + b*t)(c + d*t) with t^2 = 4t + 3. Section 14.2.</summary>
        /// <param name="x">One element.</param>
        /// <param name="y">The other element.</param>
        /// <returns>Their product.</returns>
        private static int GfMul(int x, int y) {
            int a = x % 5;
            int b = x / 5;
            int c = y % 5;
            int d = y / 5;
            return (((a * d) + (b * c) + (4 * b * d)) % 5 * 5) + (((a * c) + (3 * b * d)) % 5);
        }

        /// <summary>t^1 to t^11.</summary>
        /// <returns>The eleven check weights.</returns>
        private static int[] PowersOfT() {
            int[] weights = new int[11];
            int x = 1;
            for (int i = 0; i < weights.Length; i++) {
                x = GfMul(x, T);
                weights[i] = x;
            }
            return weights;
        }

        /// <summary>c = t * S, where S is the syndrome over the ten payload symbols.</summary>
        /// <param name="code">Normalised ten-character code.</param>
        /// <returns>The check character.</returns>
        private static string CheckSymbol(string code) {
            int syndrome = 0;
            for (int i = 0; i < code.Length; i++) {
                syndrome = GfAdd(syndrome,
                    GfMul(WEIGHTS[i], ALPHABET.IndexOf(code[i], StringComparison.Ordinal)));

            }
            return ALPHABET[GfMul(T, syndrome)].ToString();
        }

        /// <summary>
        /// Rounds a count of 1e-8 degrees to six decimal places. Section 6.2.
        /// <para>
        /// Ties are unreachable -- every reachable value is congruent to a
        /// multiple of 4 modulo 100 -- so no choice of rounding mode can change
        /// any result, and no implementation has to make the choice.
        /// </para>
        /// </summary>
        /// <param name="value">A count of 1e-8 degrees.</param>
        /// <returns>The value in degrees, six decimal places.</returns>
        private static double Round6(long value) {
            long magnitude = Math.Abs(value);
            long quotient = magnitude / 100;
            if (magnitude % 100 >= 50) {
                quotient++;
            }
            return (value < 0 ? -quotient : quotient) / 1_000_000.0;
        }
    }
}
