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

namespace Ca.Pranavpatel.Algo.GridPointCode {
    /// <summary>
    /// Version 1 decoding, kept so that every code ever issued still resolves.
    /// <para>
    /// Version 1 is a base-27 numeral over a different alphabet, eleven
    /// characters long, and it carries no locality guarantee: two codes sharing
    /// four characters can be nineteen thousand kilometres apart. It is frozen.
    /// There is no version 1 encoder here and there will not be one -- the
    /// format is readable, not writable, and anyone who still needs to mint
    /// version 1 codes pins 1.1.x.
    /// </para>
    /// <para>
    /// Nothing in this class is reached by a version 2 code. It is entered only
    /// when <see cref="GPC.Decode" /> sees eleven characters, or when a caller
    /// asks for <see cref="GPC.DecodeV1" /> outright. Appendix B of SPEC.md
    /// describes the format.
    /// </para>
    /// </summary>
    internal static class V1 {

        /// <summary>base27, letters first, so it is not in ASCII order.</summary>
        internal const string CHARACTERS = "CDFGHJKLMNPRTVWXY0123456789";

        /// <summary>Every version 1 code is this many characters.</summary>
        internal const int CODE_LENGTH = 11;

        private const ulong MIN_POINT = 10_000_000_000;
        private const ulong MAX_POINT = 648_009_999_999_999;

        /// <summary>The offset that makes every code exactly eleven characters.</summary>
        private const ulong ELEVEN = 205_881_132_094_649;

        private static readonly Table LatLongTable = new(180, 360, true);

        /// <summary>Upper-case and drop the separators. Version 1 has no alias table.</summary>
        /// <param name="gridPointCode">Formatted or unformatted code.</param>
        /// <returns>The bare characters.</returns>
        internal static string Clean(string gridPointCode) {
            return gridPointCode.Replace(" ", null, StringComparison.Ordinal)
                .Replace("-", null, StringComparison.Ordinal)
                .Replace("#", null, StringComparison.Ordinal).Trim().ToUpperInvariant();
        }

        /// <summary>The version 1 presentation, <c>#XXXX-XXXX-XXX</c>.</summary>
        /// <param name="code">Unformatted eleven-character code.</param>
        /// <returns>The formatted code.</returns>
        internal static string Format(string code) {
            return $"#{code[..4]}-{code[4..8]}-{code[8..11]}";
        }

        /// <summary>
        /// Decodes an eleven-character version 1 code to its cell's corner.
        /// <para>
        /// Version 1 returns the corner, not the centre. That differs from
        /// version 2 by design: the value is the one every version 1 release has
        /// returned, and changing it would move every code ever issued.
        /// </para>
        /// </summary>
        /// <param name="gridPointCode">Formatted or unformatted code.</param>
        /// <returns>Coordinates in decimal degrees.</returns>
        /// <exception cref="GPCException">
        /// if <paramref name="gridPointCode" /> is null, malformed, or outside the grid.
        /// </exception>
        internal static (double Latitude, double Longitude) Decode(string gridPointCode) {
            if (string.IsNullOrWhiteSpace(gridPointCode)) {
                throw new GPCException("GPC_NULL");
            }

            string code = Clean(gridPointCode);

            (bool valid, string message) = ValidateCode(code);
            if (!valid) {
                throw new GPCException(message);
            }

            ulong point = ToPoint(code) - ELEVEN;

            (valid, message) = ValidatePoint(point);
            if (!valid) {
                throw new GPCException(message);
            }

            return ToCoordinates(point);
        }

        /// <summary>Whether a string is a version 1 code, and why not when it is not.</summary>
        /// <param name="gridPointCode">Formatted or unformatted code.</param>
        /// <returns>Validity status with the reason code if any.</returns>
        internal static (bool status, string message) IsValid(string gridPointCode) {
            if (string.IsNullOrWhiteSpace(gridPointCode)) {
                return (false, "GPC_NULL");
            }
            string code = Clean(gridPointCode);
            if (code.Length == 0) {
                return (false, "GPC_NULL");
            }
            (bool valid, string message) = ValidateCode(code);
            if (!valid) {
                return (false, message);
            }
            return ValidatePoint(ToPoint(code) - ELEVEN);
        }

        /// <summary>Length and alphabet, on an already cleaned code.</summary>
        /// <param name="code">Cleaned code.</param>
        /// <returns>Validity status with the reason code if any.</returns>
        private static (bool status, string message) ValidateCode(string code) {
            if (code.Length != CODE_LENGTH) {
                return (false, "GPC_LENGTH");
            }
            foreach (char character in code) {
                if (!CHARACTERS.Contains(character, StringComparison.Ordinal)) {
                    return (false, "GPC_CHAR");
                }
            }
            return (true, string.Empty);
        }

        /// <summary>Whether a decoded point falls inside the version 1 grid.</summary>
        /// <param name="point">Point number.</param>
        /// <returns>Validity status with the reason code if any.</returns>
        private static (bool status, string message) ValidatePoint(ulong point) {
            if (point < MIN_POINT || point > MAX_POINT) {
                return (false, "GPC_RANGE");
            }
            return (true, string.Empty);
        }

        /// <summary>The base-27 value of an eleven-character code.</summary>
        /// <param name="code">Cleaned, validated code.</param>
        /// <returns>Point number.</returns>
        private static ulong ToPoint(string code) {
            ulong point = 0;
            for (int i = 0; i < CODE_LENGTH; i++) {
                point *= 27;
                point += (ulong)CHARACTERS.IndexOf(code[i], StringComparison.Ordinal);
            }
            return point;
        }

        /// <summary>Splits a point back into latitude and longitude.</summary>
        /// <param name="point">Valid point number.</param>
        /// <returns>Coordinates in decimal degrees.</returns>
        private static (double Latitude, double Longitude) ToCoordinates(ulong point) {
            // Seperating whole-number and fractional parts
            int LatLongIndex = (int)Math.Truncate(point / Math.Pow(10, 10));
            ulong Fractional = (ulong)(point - (LatLongIndex * Math.Pow(10, 10)));
            // Spliting into 7
            (int[] Lat7, int[] Long7) = SplitTo7(LatLongIndex, Fractional);
            // Constructing coordinates
            int Power = 0;
            int TempLat = 0;
            int TempLong = 0;
            for (int x = 6; x >= 1; x--) {
                TempLat += (int)(Lat7[x] * Math.Pow(10, Power));
                TempLong += (int)(Long7[x] * Math.Pow(10, Power++));
            }
            double Lat = TempLat / Math.Pow(10, 5) * Lat7[0];
            double Long = TempLong / Math.Pow(10, 5) * Long7[0];
            return (Lat, Long);
        }

        /// <summary>
        /// Sign, whole degrees and five decimals, for each axis.
        /// <para>
        /// The whole degrees come back through the combination table, which
        /// pairs an index with two doubled-and-offset whole values. The ten
        /// decimal digits of <paramref name="fractional" /> alternate, latitude
        /// first.
        /// </para>
        /// </summary>
        /// <param name="latLongIndex">Latitude and longitude pair index from Table.</param>
        /// <param name="fractional">Fractional part of the coordinates.</param>
        /// <returns>Integer arrays of coordinates.</returns>
        private static (int[] Lat7, int[] Long7) SplitTo7(int latLongIndex, ulong fractional) {
            int[] Long7 = new int[7];
            int[] Lat7 = new int[7];
            // TLat, TLong - Assigned positive values in Table
            (int TLat, int TLong) = ((int, int))LatLongTable.GetElementsAtIndex(latLongIndex - 1);
            // Getting sign and whole-number parts
            Lat7[0] = TLat % 2 != 0 ? -1 : 1;
            Lat7[1] = Lat7[0] == -1 ? --TLat / 2 : TLat / 2;
            Long7[0] = TLong % 2 != 0 ? -1 : 1;
            Long7[1] = Long7[0] == -1 ? --TLong / 2 : TLong / 2;
            // Getting fractional parts
            int Power = 9;
            for (int x = 2; x <= 6; x++) {
                Lat7[x] = (int)(((ulong)Math.Truncate(fractional / Math.Pow(10, Power--))) % 10);
                Long7[x] = (int)(((ulong)Math.Truncate(fractional / Math.Pow(10, Power--))) % 10);
            }
            return (Lat7, Long7);
        }
    }
}
