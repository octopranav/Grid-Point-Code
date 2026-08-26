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
using System.Diagnostics.CodeAnalysis;
using System.Globalization;
using System.Runtime.CompilerServices;
using System.Text;

[assembly: CLSCompliant(true)]

// The advisory list of section 17 is internal, as it is package-private in the
// Java port and unexported in the other two. The suite has to reach it to hold
// this port's copy to the digest in test_data/v2_screen_list.csv, which is what
// catches a list that drifted from the other three.
[assembly: InternalsVisibleTo("gpc.tests")]
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
        private const long P5 = 3_125;          // 5^5, the rows and columns inside one level-5 cell
        private const long HALF_P5 = 1_562;     // section 12.2 adds this, not 1562.5
        private const long R5 = 2_500;          // 4 * 5^4, rows of level-5 cells
        private const long C5 = 3_750;          // 6 * 5^4, columns of level-5 cells
        private const long CODE_SPACE = 95_367_431_640_625L;   // 25^10, section 13
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
        // Section 19.1. Written as an escape rather than as the character, so
        // that it survives whatever a compiler assumes about source encoding.
        internal const char DEGREE_SIGN = '\u00b0';

        // Section 17.2. Three symbols turn up by chance too often to warn about.
        private const int SCREEN_MIN = 4;

        // Section 18.3. North, north-east, east, south-east, south, south-west,
        // west, north-west, as row and column steps in pairs. Rows increase
        // northward, so north is +1.
        private static readonly int[] NEIGHBOUR_STEPS = [
            1, 0, 1, 1, 0, 1, -1, 1, -1, 0, -1, -1, 0, -1, 1, -1,
        ];

        // Section 18.4, and the radius is 18.5. These are the only physical
        // quantities in the format; everything else is arithmetic.
        private const double M_PER_DEGREE_LAT = 111_132.0;
        private const double M_PER_DEGREE_LONG = 111_319.49;   // at the equator
        private const double EARTH_RADIUS = 6_371_008.8;       // mean radius of WGS 84

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

        /*  PART 4 : THE LOCALITY API */

        /// <summary>
        /// The first <paramref name="level" /> characters of a code, normalised.
        /// Section 18.1.
        /// <para>
        /// A cell names a region: two codes lie in the same level-k cell exactly
        /// when they share their first k characters, so this is the region
        /// identifier the guarantee is about.
        /// </para>
        /// </summary>
        /// <param name="gridPointCode">A code, or a longer cell.</param>
        /// <param name="level">1 to 10.</param>
        /// <returns>
        /// The cell, bare -- no <c>#</c> and no separator. Ten characters is a
        /// code and anything shorter is a region; presenting a cell as a code
        /// would break the fixed length the format is recognised by.
        /// </returns>
        /// <exception cref="ArgumentOutOfRangeException">
        /// if <paramref name="level" /> is outside 1 to 10.
        /// </exception>
        /// <exception cref="GPCException">
        /// with reason GPC_LENGTH if the argument is shorter than the level asked
        /// for, GPC_RESERVED for a cell beginning with X, or one of the parsing
        /// reasons.
        /// </exception>
        public static string Cell(string gridPointCode, int level) {
            CheckLevel(level);
            string code = CellOf(gridPointCode);
            if (code.Length < level) {
                throw new GPCException("GPC_LENGTH");
            }
            return code[..level];
        }

        /// <summary>
        /// Whether a code lies inside a cell. Section 18.2.
        /// <para>
        /// The prefix test, and nothing more. What section 10 buys is that this
        /// is a true geometric containment test rather than an approximation of
        /// one: no tolerance, no edge case at a boundary, and no pair of points
        /// on Earth for which the string answer and the geometric answer differ.
        /// </para>
        /// </summary>
        /// <param name="cell">A cell of 1 to 10 characters.</param>
        /// <param name="gridPointCode">A code, or a cell.</param>
        /// <returns>True if the code lies inside the cell.</returns>
        public static bool Contains(string cell, string gridPointCode) {
            string prefix = CellOf(cell);
            string code = CellOf(gridPointCode);
            return code.Length >= prefix.Length
                && string.Equals(code[..prefix.Length], prefix, StringComparison.Ordinal);
        }

        /// <summary>
        /// The cells sharing an edge or a corner, in order. Section 18.3.
        /// <para>
        /// North, north-east, east, south-east, south, south-west, west,
        /// north-west. Columns wrap at the antimeridian; rows do not, because the
        /// grid ends at the poles, so a cell in the top or bottom row has five
        /// neighbours and the three that would lie off the grid are absent rather
        /// than empty.
        /// </para>
        /// </summary>
        /// <param name="cell">A cell of 1 to 10 characters.</param>
        /// <returns>Bare cells of the same length as the argument.</returns>
        public static IReadOnlyList<string> Neighbours(string cell) {
            string code = CellOf(cell);
            int level = code.Length;
            long p = Pow5(LEVELS - level);
            (long row, long col) = CodeToGrid(code + new string(ALPHABET[0], LEVELS - level));
            long cellRow = row / p;
            long cellCol = col / p;
            long rowCells = 4 * Pow5(level - 1);
            long colCells = 6 * Pow5(level - 1);

            List<string> found = [];
            for (int i = 0; i < NEIGHBOUR_STEPS.Length; i += 2) {
                long r = cellRow + NEIGHBOUR_STEPS[i];
                if (r < 0 || r >= rowCells) {
                    continue;
                }
                long c = (cellCol + NEIGHBOUR_STEPS[i + 1] + colCells) % colCells;
                found.Add(GridToCode(r * p, c * p)[..level]);
            }
            return found;
        }

        /// <summary>How big a level-k cell is. Section 18.4.</summary>
        /// <param name="level">1 to 10.</param>
        /// <returns>
        /// The latitude and longitude spans in degrees, then the same two in
        /// metres. The north-south figure holds everywhere; the east-west one is
        /// the value at the equator and shrinks with the cosine of latitude,
        /// which is a multiplication left to the caller.
        /// </returns>
        /// <exception cref="ArgumentOutOfRangeException">
        /// if <paramref name="level" /> is outside 1 to 10.
        /// </exception>
        public static (double LatitudeSpan, double LongitudeSpan, double NorthSouth, double EastWest)
                CellDimensions(int level) {
            CheckLevel(level);
            double divisor = Pow5(level - 1);
            double latitudeSpan = 45.0 / divisor;
            double longitudeSpan = 60.0 / divisor;
            return (latitudeSpan, longitudeSpan,
                    latitudeSpan * M_PER_DEGREE_LAT, longitudeSpan * M_PER_DEGREE_LONG);
        }

        /// <summary>
        /// Great-circle metres between the centres of two cells. Section 18.5.
        /// <para>
        /// The cells may be of different levels. This is the one operation in the
        /// format that is not bit-identical across languages: no standard library
        /// rounds sine, cosine or arc sine correctly, so two ports agree to about
        /// a millimetre rather than exactly. Anything that needs a reproducible
        /// ordering must rank on grid indices, as <see cref="SuggestCorrections(string, double, double, int, bool)" />
        /// does.
        /// </para>
        /// </summary>
        /// <param name="a">A cell of 1 to 10 characters.</param>
        /// <param name="b">Another.</param>
        /// <returns>Metres.</returns>
        public static double Distance(string a, string b) {
            (double latitudeA, double longitudeA) = CellCentre(a);
            (double latitudeB, double longitudeB) = CellCentre(b);

            double phi1 = latitudeA * Math.PI / 180.0;
            double phi2 = latitudeB * Math.PI / 180.0;
            double dPhi = phi2 - phi1;
            double dLambda = (longitudeB - longitudeA) * Math.PI / 180.0;

            double h = (Math.Sin(dPhi / 2) * Math.Sin(dPhi / 2))
                + (Math.Cos(phi1) * Math.Cos(phi2) * Math.Sin(dLambda / 2) * Math.Sin(dLambda / 2));
            if (h > 1.0) {
                // Rounding can carry the sum a unit past 1 for points near
                // opposite ends of the Earth, where arc sine is undefined.
                h = 1.0;
            }
            return 2 * EARTH_RADIUS * Math.Asin(Math.Sqrt(h));
        }

        /// <summary>
        /// The row and column of the cell a code names. Section 18.6.
        /// <para>
        /// The accessor for a caller building a spatial structure of its own -- a
        /// tile index, a join key, a quadtree -- who wants the integers rather
        /// than degrees rounded to six places.
        /// </para>
        /// </summary>
        /// <param name="gridPointCode">Anything a person might have typed.</param>
        /// <returns>The row and column.</returns>
        public static (long row, long col) DecodeToGrid(string gridPointCode) {
            return CodeToGrid(Geometric(gridPointCode));
        }

        /// <summary>
        /// The last five characters of a code. Section 12.1.
        /// <para>
        /// Literally the second printed group of <c>#XXXXX-XXXXX</c>, so a
        /// printed code shows its own short form. The leading dash belongs to the
        /// presentation form and is not returned; <see cref="RecoverShort(string, double, double, bool)" />
        /// accepts it either way.
        /// </para>
        /// </summary>
        /// <param name="gridPointCode">Anything a person might have typed.</param>
        /// <returns>The five characters.</returns>
        public static string Shorten(string gridPointCode) {
            return Geometric(gridPointCode)[5..];
        }

        /// <summary>
        /// The full code a short form names, near a reference. Section 12.2.
        /// <para>
        /// Exact integer arithmetic -- no search, no distance, no tie to break --
        /// and exact whenever the reference is within half a level-5 cell of the
        /// true point on each axis, which is 0.03598848 degrees of latitude
        /// (3.999 km) and 0.04798464 degrees of longitude (5.342 km at the
        /// equator, less elsewhere).
        /// </para>
        /// <para>
        /// Outside that box it returns a neighbouring cell's copy of the same
        /// offset, a plausible location 8 or 10 km away. A caller that cannot
        /// bound its reference should not be using the short form.
        /// </para>
        /// </summary>
        /// <param name="shortForm">The five characters, with or without the leading dash.</param>
        /// <param name="nearLatitude">Reference latitude.</param>
        /// <param name="nearLongitude">Reference longitude.</param>
        /// <param name="formatted">True for <c>#XXXXX-XXXXX</c>, false for the bare ten.</param>
        /// <returns>The full code.</returns>
        /// <exception cref="ArgumentOutOfRangeException">
        /// if the reference is outside the domain.
        /// </exception>
        /// <exception cref="GPCException">
        /// with reason GPC_LENGTH unless the short form is five symbols.
        /// </exception>
        public static string RecoverShort(string shortForm, double nearLatitude, double nearLongitude,
                bool formatted) {
            (string tail, _) = Normalise(shortForm);
            if (tail.Length != CODE_LENGTH - 5) {
                throw new GPCException("GPC_LENGTH");
            }
            foreach (char character in tail) {
                if (!ALPHABET.Contains(character, StringComparison.Ordinal)) {
                    throw new GPCException("GPC_CHAR");
                }
            }

            (long rowLow, long colLow) = ReadTail(tail);
            (bool valid, string message) = IsValid(nearLatitude, nearLongitude);
            if (!valid) {
                throw new ArgumentOutOfRangeException(paramName: message,
                    message: $"{message}: value out of valid range.");
            }
            (long rowRef, long colRef) = ToGrid(nearLatitude, nearLongitude);

            // Floor division over values that may be negative. C# divides toward
            // zero, which is wrong here, and wrong only west and south of the
            // reference -- so a truncating port passes about a quarter of the
            // vectors and looks merely unlucky.
            long cellRow = FloorDiv(rowRef - rowLow + HALF_P5, P5);
            cellRow = cellRow < 0 ? 0 : (cellRow > R5 - 1 ? R5 - 1 : cellRow);
            long cellCol = ((FloorDiv(colRef - colLow + HALF_P5, P5) % C5) + C5) % C5;

            string code = GridToCode((cellRow * P5) + rowLow, (cellCol * P5) + colLow);
            return formatted ? FormatGPC(code) : code;
        }

        /// <summary>
        /// The full code a short form names, formatted. Section 12.2.
        /// </summary>
        /// <param name="shortForm">The five characters, with or without the leading dash.</param>
        /// <param name="nearLatitude">Reference latitude.</param>
        /// <param name="nearLongitude">Reference longitude.</param>
        /// <returns>The formatted code.</returns>
        public static string RecoverShort(string shortForm, double nearLatitude, double nearLongitude) {
            return RecoverShort(shortForm, nearLatitude, nearLongitude, true);
        }

        /// <summary>
        /// Codes one typo away that are plausible near a reference. Section 15.3.
        /// <para>
        /// At most 249 candidates -- 240 single-character substitutions and up to
        /// 9 adjacent transpositions -- filtered to those in the reference's
        /// level-k cell or one of its eight neighbours, and ranked by
        /// <c>9*dRow^2 + 16*dCol^2</c>, which is squared distance in degree
        /// space. Ties break on the integer form. Every step is integer
        /// arithmetic, so all four ports return the same list in the same order.
        /// </para>
        /// <para>
        /// Level 6 suits a device fix or a named suburb and returns one candidate
        /// in the median case. Widening it to cover a poorer reference costs
        /// precision, not correctness.
        /// </para>
        /// </summary>
        /// <param name="gridPointCode">
        /// The code as typed, which need not decode: a code with a wrong
        /// character is exactly what this is for. It must still normalise to ten
        /// symbols of the alphabet.
        /// </param>
        /// <param name="nearLatitude">Reference latitude.</param>
        /// <param name="nearLongitude">Reference longitude.</param>
        /// <param name="level">The window is 3 by 3 cells at this level.</param>
        /// <param name="formatted">True for <c>#XXXXX-XXXXX</c>, false for the bare ten.</param>
        /// <returns>The candidates, best first.</returns>
        public static IReadOnlyList<string> SuggestCorrections(string gridPointCode, double nearLatitude,
                double nearLongitude, int level, bool formatted) {
            CheckLevel(level);
            (string code, _) = Normalise(gridPointCode);
            if (code.Length != CODE_LENGTH) {
                throw new GPCException("GPC_LENGTH");
            }
            foreach (char character in code) {
                if (!ALPHABET.Contains(character, StringComparison.Ordinal)) {
                    throw new GPCException("GPC_CHAR");
                }
            }

            (bool valid, string message) = IsValid(nearLatitude, nearLongitude);
            if (!valid) {
                throw new ArgumentOutOfRangeException(paramName: message,
                    message: $"{message}: value out of valid range.");
            }
            (long rowRef, long colRef) = ToGrid(nearLatitude, nearLongitude);

            long p = Pow5(LEVELS - level);
            long refRowCell = rowRef / p;
            long refColCell = colRef / p;
            long colCells = COLS / p;

            List<(long Score, long Value, string Code)> scored = [];
            foreach (string candidate in Candidates(code)) {
                if (candidate[0] == 'X') {          // reserved, never geometric
                    continue;
                }
                (long row, long col) = CodeToGrid(candidate);

                long dRowCell = (row / p) - refRowCell;
                long dColCell = ((col / p) - refColCell + colCells) % colCells;
                if (dColCell > colCells / 2) {
                    dColCell -= colCells;
                }
                if (Math.Abs(dRowCell) > 1 || Math.Abs(dColCell) > 1) {
                    continue;
                }

                long dRow = row - rowRef;
                long dCol = col - colRef;
                if (dCol > COLS / 2) {              // the short way round
                    dCol -= COLS;
                } else if (dCol < -COLS / 2) {
                    dCol += COLS;
                }

                scored.Add(((9 * dRow * dRow) + (16 * dCol * dCol), ToInteger(candidate), candidate));
            }

            scored.Sort((x, y) => x.Score != y.Score ? x.Score.CompareTo(y.Score)
                                                     : x.Value.CompareTo(y.Value));
            List<string> ordered = [];
            foreach ((_, _, string candidate) in scored) {
                ordered.Add(formatted ? FormatGPC(candidate) : candidate);
            }
            return ordered;
        }

        /// <summary>
        /// Codes one typo away, at the default level of 6, formatted. Section 15.3.
        /// </summary>
        /// <param name="gridPointCode">The code as typed.</param>
        /// <param name="nearLatitude">Reference latitude.</param>
        /// <param name="nearLongitude">Reference longitude.</param>
        /// <returns>The candidates, best first.</returns>
        public static IReadOnlyList<string> SuggestCorrections(string gridPointCode, double nearLatitude,
                double nearLongitude) {
            return SuggestCorrections(gridPointCode, nearLatitude, nearLongitude, 6, true);
        }

        /// <summary>
        /// The code as a base-25 numeral. Section 13.
        /// <para>
        /// Forty-seven bits, so six bytes big-endian, and order-preserving:
        /// sorting the integers sorts the codes, which sorts the cells
        /// geographically. A reserved code is at or above 91,552,734,375,000 and
        /// a geometric one below it, so one comparison classifies without
        /// parsing.
        /// </para>
        /// </summary>
        /// <param name="gridPointCode">Anything a person might have typed.</param>
        /// <returns>The value.</returns>
        public static long ToInteger(string gridPointCode) {
            string code = Payload(gridPointCode);
            long value = 0;
            foreach (char character in code) {
                value = (value * 25) + ALPHABET.IndexOf(character, StringComparison.Ordinal);
            }
            return value;
        }

        /// <summary>The code a base-25 numeral names. Section 13.</summary>
        /// <param name="value">0 to 25^10 - 1.</param>
        /// <param name="formatted">True for <c>#XXXXX-XXXXX</c>, false for the bare ten.</param>
        /// <returns>The code.</returns>
        /// <exception cref="GPCException">
        /// with reason GPC_RANGE if the value is outside the range.
        /// </exception>
        public static string FromInteger(long value, bool formatted) {
            if (value < 0 || value >= CODE_SPACE) {
                throw new GPCException("GPC_RANGE");
            }
            char[] out_ = new char[LEVELS];
            long rest = value;
            for (int i = LEVELS - 1; i >= 0; i--) {
                out_[i] = ALPHABET[(int)(rest % 25)];
                rest /= 25;
            }
            string code = new(out_);
            return formatted ? FormatGPC(code) : code;
        }

        /// <summary>The code a base-25 numeral names, formatted. Section 13.</summary>
        /// <param name="value">0 to 25^10 - 1.</param>
        /// <returns>The formatted code.</returns>
        public static string FromInteger(long value) {
            return FromInteger(value, true);
        }

        /// <summary>
        /// Substrings of a code that spell something unwanted. Section 17.
        /// <para>
        /// Advisory, and non-normative. It reports and never blocks: nothing in
        /// this package refuses to encode, decode or validate because of what
        /// this found.
        /// </para>
        /// </summary>
        /// <param name="gridPointCode">Anything a person might have typed.</param>
        /// <returns>
        /// The version of the list, and the matched spans as position and length
        /// with position counted from 1, ordered by position and then by length.
        /// Spans may overlap and every match is reported. A clean code returns
        /// the version and no spans, because a caller has to be able to tell
        /// "clean under this list" from "never screened".
        /// </returns>
        public static (string Version, IReadOnlyList<(int Position, int Length)> Spans)
                Screen(string gridPointCode) {
            string code = Payload(gridPointCode);
            List<(int Position, int Length)> spans = [];
            for (int length = SCREEN_MIN; length <= CODE_LENGTH; length++) {
                for (int start = 0; start <= CODE_LENGTH - length; start++) {
                    if (ScreenList.Entries.Contains(ScreenHash(code.Substring(start, length)))) {
                        spans.Add((start + 1, length));
                    }
                }
            }
            return (ScreenList.Version, spans);
        }

        /// <summary>
        /// Encodes a sequence of coordinates.
        /// <para>
        /// For dataset work. The first bad coordinate throws, rather than a bad
        /// row being silently dropped; <see cref="EncodeStream" /> is the one to
        /// reach for when the caller wants to handle failures row by row.
        /// </para>
        /// </summary>
        /// <param name="points">The coordinates.</param>
        /// <param name="formatted">True for <c>#XXXXX-XXXXX</c>, false for the bare ten.</param>
        /// <returns>The codes.</returns>
        public static IReadOnlyList<string> EncodeAll(
                IEnumerable<(double Latitude, double Longitude)> points, bool formatted) {
            List<string> codes = [];
            foreach (string code in EncodeStream(points, formatted)) {
                codes.Add(code);
            }
            return codes;
        }

        /// <summary>Encodes a sequence lazily, one code at a time.</summary>
        /// <param name="points">The coordinates.</param>
        /// <param name="formatted">True for <c>#XXXXX-XXXXX</c>, false for the bare ten.</param>
        /// <returns>The codes, produced as they are asked for.</returns>
        public static IEnumerable<string> EncodeStream(
                IEnumerable<(double Latitude, double Longitude)> points, bool formatted) {
            ArgumentNullException.ThrowIfNull(points);
            foreach ((double latitude, double longitude) in points) {
                yield return Encode(latitude, longitude, formatted);
            }
        }

        /// <summary>Decodes a sequence of codes.</summary>
        /// <param name="codes">The codes.</param>
        /// <returns>The coordinates.</returns>
        public static IReadOnlyList<(double Latitude, double Longitude)> DecodeAll(IEnumerable<string> codes) {
            List<(double Latitude, double Longitude)> points = [];
            foreach ((double Latitude, double Longitude) point in DecodeStream(codes)) {
                points.Add(point);
            }
            return points;
        }

        /// <summary>Decodes a sequence lazily, one pair at a time.</summary>
        /// <param name="codes">The codes.</param>
        /// <returns>The coordinates, produced as they are asked for.</returns>
        public static IEnumerable<(double Latitude, double Longitude)> DecodeStream(IEnumerable<string> codes) {
            ArgumentNullException.ThrowIfNull(codes);
            foreach (string code in codes) {
                yield return Decode(code);
            }
        }

        /*  PART 5 : COORDINATE CONVERSIONS */

        /// <summary>
        /// Degrees, minutes and seconds, latitude first. Section 19.1.
        /// <para>
        /// <c>43°39'00.00"N, 79°22'48.00"W</c>.
        /// </para>
        /// <para>
        /// Lossy: a hundredth of a second is 0.309 m of latitude. A decoded code
        /// survives the trip all the same, because <see cref="Decode" /> returns
        /// a cell centre and that sits eight times further from the nearest
        /// boundary than this rounding can move it. For exact interchange use
        /// <see cref="ToGeoURI" />.
        /// </para>
        /// </summary>
        /// <param name="latitude">Decimal degrees.</param>
        /// <param name="longitude">Decimal degrees.</param>
        /// <returns>The text.</returns>
        /// <exception cref="ArgumentOutOfRangeException">
        /// if either coordinate is outside the domain.
        /// </exception>
        public static string ToDMS(double latitude, double longitude) {
            (bool valid, string message) = IsValid(latitude, longitude);
            if (!valid) {
                throw new ArgumentOutOfRangeException(paramName: message,
                    message: $"{message}: value out of valid range.");
            }
            return DmsAxis(latitude, 'N', 'S') + ", " + DmsAxis(longitude, 'E', 'W');
        }

        /// <summary>
        /// Reads degrees, minutes and seconds back. Section 19.1.
        /// <para>
        /// Each axis is a signed or hemisphere-marked value; the unit marker
        /// after the degrees is required, because it is what tells one axis from
        /// the next when no comma separates them.
        /// </para>
        /// </summary>
        /// <param name="text">The DMS text.</param>
        /// <returns>The coordinates.</returns>
        /// <exception cref="GPCException">
        /// with reason GPC_DMS for anything the grammar does not accept.
        /// </exception>
        /// <exception cref="ArgumentOutOfRangeException">
        /// if either value is outside the domain.
        /// </exception>
        public static (double Latitude, double Longitude) FromDMS(string text) {
            Scan scan = new(text);
            double latitude = scan.Axis(true);
            scan.Spaces();
            if (scan.Peek() == ',') {
                _ = scan.Take();
            }
            double longitude = scan.Axis(false);
            scan.Spaces();
            if (!scan.Done()) {
                throw new GPCException("GPC_DMS");
            }

            (bool valid, string message) = IsValid(latitude, longitude);
            if (!valid) {
                throw new ArgumentOutOfRangeException(paramName: message,
                    message: $"{message}: value out of valid range.");
            }
            return (latitude, longitude);
        }

        /// <summary>
        /// An RFC 5870 URI in its simplest form. Section 19.2.
        /// <para>
        /// <c>geo:43.650006,-79.380004</c>. Six decimal places, trailing zeros
        /// dropped, which is exactly what <see cref="Decode" /> produces, so a
        /// code written out this way and read back encodes to the same code every
        /// time.
        /// </para>
        /// </summary>
        /// <param name="latitude">Decimal degrees.</param>
        /// <param name="longitude">Decimal degrees.</param>
        /// <returns>The URI.</returns>
        /// <exception cref="ArgumentOutOfRangeException">
        /// if either coordinate is outside the domain.
        /// </exception>
        [SuppressMessage("Design", "CA1055:URI-like return values should not be strings",
            Justification = "Section 19.2 pins the exact characters and the shared vectors assert "
                + "them. A Uri would re-normalise the text, so the four ports would stop agreeing.")]
        public static string ToGeoURI(double latitude, double longitude) {
            (bool valid, string message) = IsValid(latitude, longitude);
            if (!valid) {
                throw new ArgumentOutOfRangeException(paramName: message,
                    message: $"{message}: value out of valid range.");
            }
            return "geo:" + Decimal6(latitude) + "," + Decimal6(longitude);
        }

        /// <summary>
        /// Reads an RFC 5870 URI back. Section 19.2.
        /// <para>
        /// A third coordinate is an altitude and is discarded. Parameters are
        /// ignored, except that <c>crs</c> is rejected unless it is <c>wgs84</c>:
        /// this format is defined on WGS 84 alone, and silently reading a code as
        /// though it were on another datum would put it in the wrong place.
        /// </para>
        /// </summary>
        /// <param name="text">The URI.</param>
        /// <returns>The coordinates.</returns>
        /// <exception cref="GPCException">
        /// with reason GPC_NULL for a null argument, or GPC_GEO for anything the
        /// grammar does not accept.
        /// </exception>
        /// <exception cref="ArgumentOutOfRangeException">
        /// if either value is outside the domain.
        /// </exception>
        public static (double Latitude, double Longitude) FromGeoURI(string text) {
            if (text is null) {
                throw new GPCException("GPC_NULL");
            }
            string body = text.Trim();
            if (body.Length < 4
                    || !body[..4].Equals("geo:", StringComparison.OrdinalIgnoreCase)) {
                throw new GPCException("GPC_GEO");
            }
            body = body[4..];

            int semicolon = body.IndexOf(';', StringComparison.Ordinal);
            if (semicolon >= 0) {
                foreach (string parameter in body[(semicolon + 1)..].Split(';')) {
                    int equals = parameter.IndexOf('=', StringComparison.Ordinal);
                    string name = equals < 0 ? parameter : parameter[..equals];
                    string value = equals < 0 ? string.Empty : parameter[(equals + 1)..];
                    if (name.Equals("crs", StringComparison.OrdinalIgnoreCase)
                            && !value.Equals("wgs84", StringComparison.OrdinalIgnoreCase)) {
                        throw new GPCException("GPC_GEO");
                    }
                }
                body = body[..semicolon];
            }

            string[] parts = body.Split(',');
            if (parts.Length is not 2 and not 3) {
                throw new GPCException("GPC_GEO");
            }
            double latitude = GeoNumber(parts[0]);
            double longitude = GeoNumber(parts[1]);
            if (parts.Length == 3) {
                _ = GeoNumber(parts[2]);            // altitude, parsed and dropped
            }

            (bool valid, string message) = IsValid(latitude, longitude);
            if (!valid) {
                throw new ArgumentOutOfRangeException(paramName: message,
                    message: $"{message}: value out of valid range.");
            }
            return (latitude, longitude);
        }

        /*  PART 6 : INTERNALS */

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

        /// <summary>5 raised to a small power, as an exact integer.</summary>
        /// <param name="power">0 to 9.</param>
        /// <returns>5^power.</returns>
        private static long Pow5(int power) {
            long value = 1;
            for (int i = 0; i < power; i++) {
                value *= 5;
            }
            return value;
        }

        /// <summary>Floor division. C# divides toward zero; sections 12.2 and 18.3 do not.</summary>
        /// <param name="numerator">May be negative.</param>
        /// <param name="denominator">Positive.</param>
        /// <returns>The quotient, rounded toward negative infinity.</returns>
        private static long FloorDiv(long numerator, long denominator) {
            long quotient = numerator / denominator;
            return numerator % denominator != 0 && (numerator < 0) ? quotient - 1 : quotient;
        }

        /// <summary>Rejects a level outside 1 to 10. Section 18.1.</summary>
        /// <param name="level">The level to check.</param>
        /// <exception cref="ArgumentOutOfRangeException">if it is outside 1 to 10.</exception>
        private static void CheckLevel(int level) {
            if (level is < 1 or > LEVELS) {
                throw new ArgumentOutOfRangeException(nameof(level),
                    "level: must be between 1 and 10.");
            }
        }

        /// <summary>
        /// Ten symbols of the alphabet, reserved ones included.
        /// <para>
        /// What <see cref="Screen" /> and <see cref="ToInteger" /> need: both act
        /// on the string rather than on the cell it names, so an X in position 1
        /// is no obstacle to either.
        /// </para>
        /// </summary>
        /// <param name="gridPointCode">Anything a person might have typed.</param>
        /// <returns>The ten normalised symbols.</returns>
        private static string Payload(string gridPointCode) {
            (string code, string check) = Normalise(gridPointCode);
            if (code.Length != CODE_LENGTH) {
                throw new GPCException("GPC_LENGTH");
            }
            foreach (char character in code) {
                if (!ALPHABET.Contains(character, StringComparison.Ordinal)) {
                    throw new GPCException("GPC_CHAR");
                }
            }
            if (check is not null && !string.Equals(check, CheckSymbol(code), StringComparison.Ordinal)) {
                throw new GPCException("GPC_CHECK");
            }
            return code;
        }

        /// <summary>A normalised cell of 1 to 10 symbols, or the typed error. Section 18.1.</summary>
        /// <param name="text">Anything a person might have typed.</param>
        /// <returns>The normalised cell.</returns>
        private static string CellOf(string text) {
            (string code, string check) = Normalise(text);
            if (code.Length is < 1 or > LEVELS) {
                throw new GPCException("GPC_LENGTH");
            }
            foreach (char character in code) {
                if (!ALPHABET.Contains(character, StringComparison.Ordinal)) {
                    throw new GPCException("GPC_CHAR");
                }
            }
            if (check is not null && (code.Length != CODE_LENGTH
                    || !string.Equals(check, CheckSymbol(code), StringComparison.Ordinal))) {
                throw new GPCException("GPC_CHECK");
            }
            if (code[0] == 'X') {
                throw new GPCException("GPC_RESERVED");
            }
            return code;
        }

        /// <summary>
        /// The centre of a cell of any level, exact to 1e-8 degrees. Section 18.5.
        /// <para>
        /// Private on purpose. For a ten-character code this differs from
        /// <see cref="Decode" /> in the seventh decimal place, and two public
        /// answers to "where is this cell" would be one too many.
        /// </para>
        /// <para>
        /// Any symbol will do as padding. By section 10 the first k characters fix
        /// the level-k cell, so whatever the padded code names, dividing by p
        /// lands on the same cell indices.
        /// </para>
        /// </summary>
        /// <param name="text">A cell of 1 to 10 characters.</param>
        /// <returns>The centre, in decimal degrees.</returns>
        private static (double Latitude, double Longitude) CellCentre(string text) {
            string code = CellOf(text);
            long p = Pow5(LEVELS - code.Length);
            (long row, long col) = CodeToGrid(code + new string(ALPHABET[0], LEVELS - code.Length));
            return ((((2 * (row / p)) + 1) * p * 1152 / 100_000_000.0) - 90.0,
                    (((2 * (col / p)) + 1) * p * 1536 / 100_000_000.0) - 180.0);
        }

        /// <summary>
        /// The last five characters as an offset in a level-5 cell. Section 12.2.
        /// <para>
        /// The loop of <see cref="CodeToGrid" /> with the parity seeded at zero
        /// and no level-1 step, which is what the reset of section 5.3 makes
        /// meaningful.
        /// </para>
        /// </summary>
        /// <param name="tail">The five characters, normalised.</param>
        /// <returns>The row and column offsets inside the cell.</returns>
        private static (long row, long col) ReadTail(string tail) {
            long row = 0, col = 0, sr = 0, sc = 0;
            foreach (char character in tail) {
                int j = ALPHABET.IndexOf(character, StringComparison.Ordinal);
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

        /// <summary>
        /// At most 249 codes one typo away, in the order section 15.3 fixes.
        /// <para>
        /// 240 substitutions, then the adjacent transpositions that actually
        /// change the code. A code such as P4444PPPPP yields 242, and the list is
        /// never padded back to 249 with duplicates.
        /// </para>
        /// </summary>
        /// <param name="code">The ten normalised symbols.</param>
        /// <returns>The candidates, in the fixed order.</returns>
        private static List<string> Candidates(string code) {
            List<string> found = [];
            for (int position = 0; position < CODE_LENGTH; position++) {
                foreach (char character in ALPHABET) {
                    if (character != code[position]) {
                        found.Add(code[..position] + character + code[(position + 1)..]);
                    }
                }
            }
            for (int position = 0; position < CODE_LENGTH - 1; position++) {
                if (code[position] != code[position + 1]) {
                    found.Add(code[..position] + code[position + 1] + code[position]
                        + code[(position + 2)..]);
                }
            }
            return found;
        }

        /// <summary>
        /// The 32-bit FNV-1a hash, eight lower-case hex characters. Section 17.3.
        /// <para>
        /// Not a cryptographic hash, and section 17.1 says why it does not need
        /// to be. Three integer operations per byte, over ASCII symbols, so all
        /// four ports compute it identically with nothing imported.
        /// </para>
        /// </summary>
        /// <param name="text">The substring to hash.</param>
        /// <returns>Eight lower-case hexadecimal characters.</returns>
        private static string ScreenHash(string text) {
            uint h = 2166136261;
            foreach (char character in text) {
                h = (h ^ character) * 16777619;
            }
            return h.ToString("x8", CultureInfo.InvariantCulture);
        }

        /// <summary>One axis of section 19.1, in integers after the first line.</summary>
        /// <param name="value">Decimal degrees.</param>
        /// <param name="positive">The hemisphere letter when the value is not negative.</param>
        /// <param name="negative">The letter when it is.</param>
        /// <returns>The axis, written out.</returns>
        private static string DmsAxis(double value, char positive, char negative) {
            long u = (long)Math.Floor((Math.Abs(value) * 360000.0) + 0.5);  // hundredths of a second
            StringBuilder axis = new(16);
            _ = axis.Append((u / 360000).ToString(CultureInfo.InvariantCulture));
            _ = axis.Append(DEGREE_SIGN);
            _ = axis.Append((u / 6000 % 60).ToString("D2", CultureInfo.InvariantCulture));
            _ = axis.Append('\'');
            _ = axis.Append((u % 6000 / 100).ToString("D2", CultureInfo.InvariantCulture));
            _ = axis.Append('.');
            _ = axis.Append((u % 100).ToString("D2", CultureInfo.InvariantCulture));
            _ = axis.Append('"');
            _ = axis.Append(value < 0 ? negative : positive);
            return axis.ToString();
        }

        /// <summary>At most six decimal places, trailing zeros dropped. Section 19.2.</summary>
        /// <param name="value">Decimal degrees.</param>
        /// <returns>The number, written out.</returns>
        private static string Decimal6(double value) {
            long u = (long)Math.Floor((Math.Abs(value) * 1000000.0) + 0.5);
            string sign = value < 0 && u != 0 ? "-" : string.Empty;
            string fraction = (u % 1000000).ToString("D6", CultureInfo.InvariantCulture).TrimEnd('0');
            return sign + (u / 1000000).ToString(CultureInfo.InvariantCulture)
                + (fraction.Length > 0 ? "." + fraction : string.Empty);
        }

        /// <summary>RFC 5870 num: an optional minus, digits, optionally more digits.</summary>
        /// <param name="text">One coordinate from the URI.</param>
        /// <returns>The value.</returns>
        private static double GeoNumber(string text) {
            string body = text.StartsWith('-') ? text[1..] : text;
            int dot = body.IndexOf('.', StringComparison.Ordinal);
            string whole = dot < 0 ? body : body[..dot];
            string fraction = dot < 0 ? string.Empty : body[(dot + 1)..];
            if (!Digits(whole) || (dot >= 0 && !Digits(fraction))) {
                throw new GPCException("GPC_GEO");
            }
            return double.Parse(text, CultureInfo.InvariantCulture);
        }

        /// <summary>Whether a string is one or more ASCII digits and nothing else.</summary>
        /// <param name="text">The string.</param>
        /// <returns>True if it is.</returns>
        private static bool Digits(string text) {
            if (text.Length == 0) {
                return false;
            }
            foreach (char character in text) {
                if (character is < '0' or > '9') {
                    return false;
                }
            }
            return true;
        }
    }
}
