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
using Ninja.Pranav.Algorithms.Kombin;

[assembly: CLSCompliant(true)]
namespace Ninja.Pranav.Algorithms.GridPointCode {
    /// <summary>
    /// Provides methods to encode coordinates to GPC and
    /// to decode GPC back to coordinates.
    /// </summary>
    public static class GPC {
        private const double MIN_LAT = -90;
        private const double MAX_LAT = 90;
        private const double MIN_LONG = -180;
        private const double MAX_LONG = 180;
        private const ulong MAX_POINT = 648_009_999_999_999; // MIN_POINT = 10_000_000_000
        private const ulong ELEVEN = 205_881_132_094_649; // For Uniformity
        private const string CHARACTERS = "CDFGHJKLMNPRTVWXY0123456789"; // base27
        private const int GPC_LENGTH = 11;
        private static readonly Table LatLongTable = new Table(180, 360, true);

        /*  PART 1 : ENCODE */

        /// <summary>Encode coordinates</summary>
        /// <param name="latitude">Latitude in Decimal Degrees</param>
        /// <param name="longitude">Longitude in Decimal Degrees</param>
        /// <returns>Grid Point Code</returns>
        /// <exception cref="ArgumentOutOfRangeException">
        /// if <paramref name="latitude" /> or <paramref name="longitude" /> is invalid.
        /// </exception>
        public static string Encode(double latitude, double longitude) {
            return Encode(latitude, longitude, true);
        }

        /// <summary>Encode coordinates</summary>
        /// <param name="latitude">Latitude in Decimal Degrees</param>
        /// <param name="longitude">Longitude in Decimal Degrees</param>
        /// <param name="formatted">True if GPC needs to be formatted otherwise false</param>
        /// <returns>Grid Point Code</returns>
        /// <exception cref="ArgumentOutOfRangeException">
        /// if <paramref name="latitude" /> or <paramref name="longitude" /> is invalid.
        /// </exception>
        public static string Encode(double latitude, double longitude, bool formatted) {
            /*  Validating Latitude and Longitude values */
            (bool valid, string message) = IsValid(latitude, longitude);
            if (!valid) {
                throw new ArgumentOutOfRangeException(paramName: message.ToLowerInvariant(),
                    message: $"{message}: value out of valid range.");
            }
            /*  Getting a Point Number  */
            ulong Point = GetPoint(latitude, longitude);
            /*  Encode Point    */
            string GridPointCode = EncodePoint(Point + ELEVEN);
            /*  Format GridPointCode   */
            if (formatted) {
                GridPointCode = FormatGPC(GridPointCode);
            }
            return GridPointCode;
        }

        /// <summary>Check if coordinates are valid</summary>
        /// <param name="latitude">Latitude in Decimal Degrees</param>
        /// <param name="longitude">Longitude in Decimal Degrees</param>
        /// <returns>Validity status with message if any.</returns>
        public static (bool status, string message) IsValid(double latitude, double longitude) {
            if (latitude <= MIN_LAT || latitude >= MAX_LAT) {
                return (false, "LATITUDE");
            }
            if (longitude <= MIN_LONG || longitude >= MAX_LONG) {
                return (false, "LONGITUDE");
            }
            return (true, string.Empty);
        }

        /// <summary>Get Point from Coordinates</summary>
        /// <param name="latitude">Latitude from Geographic coordinate</param>
        /// <param name="longitude">Longitude from Geographic coordinate</param>
        /// <returns>Point</returns>
        private static ulong GetPoint(double latitude, double longitude) {
            int[] Lat7 = SplitTo7(latitude);
            int[] Long7 = SplitTo7(longitude);
            // Whole-Number Part
            ulong Point = (ulong)(Math.Pow(10, 10) *
                ((int)LatLongTable.GetIndexOfElements(
                    (Lat7[1] * 2) + (Lat7[0] == -1 ? 1 : 0),
                    (Long7[1] * 2) + (Long7[0] == -1 ? 1 : 0)
                    ) + 1
                )
            );
            // Fractional Part
            int Power = 9;
            for (int index = 2; index <= 6; index++) {
                Point += (ulong)(Math.Pow(10, Power--) * Lat7[index]);
                Point += (ulong)(Math.Pow(10, Power--) * Long7[index]);
            }
            return Point;
        }

        /// <summary>Split Coordinate into 7 parts</summary>
        /// <param name="coordinate">Latitude or Longitude in Decimal Degrees</param>
        /// <returns>Integer array of coordinate</returns>
        private static int[] SplitTo7(double coordinate) {
            int[] Coord = new int[7];
            // Sign
            Coord[0] = coordinate < 0 ? -1 : 1;
            // Whole-Number
            Coord[1] = (int)Math.Truncate(Math.Abs(coordinate));
            // Fractional
            decimal AbsCoordinate = (decimal)Math.Abs(coordinate);
            decimal Fractional = AbsCoordinate - (int)Math.Truncate(AbsCoordinate);
            decimal Power10;
            for (int x = 1; x <= 5; x++) {
                Power10 = Fractional * 10;
                Coord[x + 1] = (int)Math.Truncate(Power10);
                Fractional = Power10 - Coord[x + 1];
            }
            return Coord;
        }

        /// <summary>Encode Point to GPC</summary>
        /// <param name="point">Point number</param>
        /// <returns>Grid Point Code</returns>
        private static string EncodePoint(ulong point) {
            string GPC = string.Empty;
            while (point > 0) {
                GPC = CHARACTERS[(int)(point % 27)] + GPC;
                point /= 27;
            }
            return GPC;
        }

        /// <summary>Format GPC</summary>
        /// <param name="gridPointCode">Unformatted GPC</param>
        /// <returns>Formatted GPC</returns>
        private static string FormatGPC(string gridPointCode) {
            return "#" + gridPointCode.Substring(0, 4) + "-"
                + gridPointCode.Substring(4, 4) + "-" + gridPointCode.Substring(8, 3);
        }

        /*  PART 2 : DECODE */

        /// <summary>Decode Grid Point Code to Coordinates</summary>
        /// <param name="gridPointCode">Grid Point Code</param>
        /// <returns>Latitude and Longitude in Decimal Degrees</returns>
        /// <exception cref="ArgumentNullException">
        /// if <paramref name="gridPointCode" /> is null, empty or whitespaces.
        /// </exception>
        /// <exception cref="ArgumentOutOfRangeException">
        /// if <paramref name="gridPointCode" /> is invalid.
        /// </exception>
        public static (double Latitude, double Longitude) Decode(string gridPointCode) {
            if (string.IsNullOrWhiteSpace(gridPointCode)) {
                throw new ArgumentNullException(paramName: nameof(gridPointCode),
                    message: "GPC_NULL: Invalid GPC.");
            }
            /*  Removing Format */
            gridPointCode = gridPointCode.Replace(" ", null, StringComparison.Ordinal)
                .Replace("-", null, StringComparison.Ordinal)
                .Replace("#", null, StringComparison.Ordinal).Trim().ToUpperInvariant();
            /*  Validating GPC  */
            (bool valid, string message) = Validate(gridPointCode);
            if (!valid) {
                throw new ArgumentOutOfRangeException(paramName: nameof(gridPointCode),
                    message: $"{message}: Invalid GPC.");
            }
            /*  Getting a Point Number  */
            ulong Point = DecodeToPoint(gridPointCode) - ELEVEN;
            /*  Validate Point  */
            (valid, message) = Validate(Point);
            if (!valid) {
                throw new ArgumentOutOfRangeException(paramName: nameof(gridPointCode),
                    message: $"{message}: Invalid GPC.");
            }
            /* Getting Coordinates from Point  */
            return GetCoordinates(Point);
        }

        /// <summary>Validates GPC</summary>
        /// <param name="gridPointCode">GPC</param>
        /// <returns>Validity status with message if any</returns>
        private static (bool status, string message) Validate(string gridPointCode) {
            if (gridPointCode.Length != GPC_LENGTH) {
                return (false, "GPC_LENGTH");
            }
            foreach (char character in gridPointCode) {
                if (!CHARACTERS.Contains(character.ToString(), StringComparison.Ordinal)) {
                    return (false, "GPC_CHAR");
                }
            }
            return (true, string.Empty);
        }

        /// <summary>Validates Point number</summary>
        /// <param name="point">Point number</param>
        /// <returns>Validity status with message if any</returns>
        private static (bool status, string message) Validate(ulong point) {
            if (point > MAX_POINT) {
                return (false, "GPC_RANGE");
            }
            return (true, string.Empty);
        }

        /// <summary>Check if grid point code is valid</summary>
        /// <param name="gridPointCode">Grid Point Code</param>
        /// <returns>Validity status with message if any</returns>
        public static (bool status, string message) IsValid(string gridPointCode) {
            if (string.IsNullOrWhiteSpace(gridPointCode)) {
                return (false, "GPC_NULL");
            }
            (bool valid, string message) = Validate(gridPointCode);
            if (!valid) {
                return (false, message);
            }
            (valid, message) = Validate(DecodeToPoint(gridPointCode) - ELEVEN);
            if (valid) {
                return (false, message);
            }
            return (true, string.Empty);
        }

        /// <summary>Decode string to Point</summary>
        /// <param name="gridPointCode">Valid GPC</param>
        /// <returns>Point number</returns>
        private static ulong DecodeToPoint(string gridPointCode) {
            ulong Point = 0;
            for (int i = 0; i < GPC_LENGTH; i++) {
                Point *= 27;
                char character = gridPointCode[i];
                Point += (ulong)CHARACTERS.IndexOf(character, StringComparison.Ordinal);
            }
            return Point;
        }

        /// <summary>Get a Coordinates from Point</summary>
        /// <param name="point">Valid Point number</param>
        /// <returns>Coordinates in Decimal Degrees</returns>
        private static (double Lat, double Long) GetCoordinates(ulong point) {
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

        /// <summary>Get 7 parts of coordinates</summary>
        /// <param name="latLongIndex">Latitude and Longitude pair index from Table</param>
        /// <param name="fractional">Fractional part of coordinates</param>
        /// <returns>Integer arrays of coordinates</returns>
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
