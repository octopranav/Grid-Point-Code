//  Copyright 2026 Pranavkumar Patel
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

namespace Ca.Pranavpatel.Algo.GridPointCode {
    /// <summary>
    /// A cursor over degrees-minutes-seconds text. Section 19.1.
    /// <para>
    /// Small enough to keep the grammar readable, and deliberately strict: every
    /// numeric piece carries its unit marker, so no accepted string has two
    /// readings.
    /// </para>
    /// </summary>
    internal sealed class Scan {
        private const string WHITESPACE = " \t\n\v\f\r";

        private readonly string text;
        private int at;

        /// <summary>Creates a cursor at the start of the text.</summary>
        /// <param name="text">The text to read.</param>
        /// <exception cref="GPCException">with reason GPC_NULL for a null argument.</exception>
        internal Scan(string text) {
            this.text = text ?? throw new GPCException("GPC_NULL");
        }

        /// <summary>Whether the cursor has passed the end of the text.</summary>
        /// <returns>True at the end.</returns>
        internal bool Done() {
            return at >= text.Length;
        }

        /// <summary>
        /// The character under the cursor, or the null character at the end.
        /// <para>
        /// The end of the text has to be a value no membership test accepts. In
        /// the two other ports it is the empty string, which is a substring of
        /// every string and therefore has to be tested for separately; here the
        /// null character is not in any of the marker sets, so it falls out.
        /// </para>
        /// </summary>
        /// <returns>The character, or '\0'.</returns>
        internal char Peek() {
            return Done() ? '\0' : text[at];
        }

        /// <summary>Takes the character under the cursor and advances.</summary>
        /// <returns>The character.</returns>
        internal char Take() {
            at++;
            return text[at - 1];
        }

        /// <summary>Advances over any ASCII whitespace.</summary>
        internal void Spaces() {
            while (!Done() && WHITESPACE.Contains(text[at], StringComparison.Ordinal)) {
                at++;
            }
        }

        /// <summary>Whether the character under the cursor is an ASCII digit.</summary>
        /// <returns>True if it is.</returns>
        private bool Digit() {
            return !Done() && text[at] is >= '0' and <= '9';
        }

        /// <summary>Consumes one of the expected unit markers, or fails.</summary>
        /// <param name="choices">The characters that would do.</param>
        /// <exception cref="GPCException">with reason GPC_DMS if none of them is there.</exception>
        private void Marker(string choices) {
            Spaces();
            char character = Peek();
            if (character == '\0' || !choices.Contains(character, StringComparison.Ordinal)) {
                throw new GPCException("GPC_DMS");
            }
            _ = Take();
        }

        /// <summary>Reads one or more digits as an integer.</summary>
        /// <returns>The value.</returns>
        /// <exception cref="GPCException">with reason GPC_DMS if there are none.</exception>
        private long Digits() {
            int start = at;
            while (Digit()) {
                at++;
            }
            if (at == start) {
                throw new GPCException("GPC_DMS");
            }
            return long.Parse(text[start..at], CultureInfo.InvariantCulture);
        }

        /// <summary>Reads digits with an optional decimal point.</summary>
        /// <returns>The value.</returns>
        /// <exception cref="GPCException">with reason GPC_DMS if there is no number.</exception>
        private double Number() {
            int start = at;
            while (Digit()) {
                at++;
            }
            if (!Done() && text[at] == '.') {
                at++;
                while (Digit()) {
                    at++;
                }
            }
            string body = text[start..at];
            if (body.Length == 0 || body == ".") {
                throw new GPCException("GPC_DMS");
            }
            return double.Parse(body, CultureInfo.InvariantCulture);
        }

        /// <summary>
        /// One axis: an optional sign, degrees and their marker, then optional
        /// minutes and seconds with theirs, then an optional hemisphere letter.
        /// </summary>
        /// <param name="isLatitude">True for the first axis, false for the second.</param>
        /// <returns>The value in decimal degrees.</returns>
        /// <exception cref="GPCException">with reason GPC_DMS for anything the grammar refuses.</exception>
        internal double Axis(bool isLatitude) {
            Spaces();
            bool signed = Peek() is '+' or '-';
            double sign = signed && Take() == '-' ? -1.0 : 1.0;

            Spaces();
            long degrees = Digits();
            Marker(GPC.DEGREE_SIGN + "dD");

            long minutes = 0;
            double seconds = 0.0;
            int save = at;
            Spaces();
            if (Digit()) {
                minutes = Digits();
                Marker("'mM");
                if (minutes >= 60) {
                    throw new GPCException("GPC_DMS");
                }
                save = at;
                Spaces();
                if (Digit()) {
                    seconds = Number();
                    Marker("\"sS");
                    if (seconds >= 60.0) {
                        throw new GPCException("GPC_DMS");
                    }
                } else {
                    at = save;
                }
            } else {
                at = save;
            }

            Spaces();
            char letter = char.ToUpperInvariant(Peek());
            if (letter is 'N' or 'S' or 'E' or 'W') {
                _ = Take();
                if (signed) {                       // a sign and a hemisphere both
                    throw new GPCException("GPC_DMS");
                }
                if ((letter is 'N' or 'S') != isLatitude) {
                    throw new GPCException("GPC_DMS");  // the wrong axis
                }
                if (letter is 'S' or 'W') {
                    sign = -1.0;
                }
            }

            return sign * (degrees + ((minutes + (seconds / 60.0)) / 60.0));
        }
    }
}
