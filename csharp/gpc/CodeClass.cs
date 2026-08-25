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

namespace Ca.Pranavpatel.Algo.GridPointCode {
    /// <summary>
    /// What a string turns out to be once it has been normalised.
    /// <para>
    /// No encoded code begins with <c>X</c>, so that space is reserved rather
    /// than wasted. A reserved code is well formed and names no cell; it is not
    /// a typing error, and the two are kept apart from the first release because
    /// a caller that cannot tell them apart today cannot be taught the
    /// difference tomorrow.
    /// </para>
    /// </summary>
    public enum CodeClass {
        /// <summary>Not a code: empty, the wrong length, outside the alphabet, or a check that does not hold.</summary>
        Invalid = 0,

        /// <summary>Well formed, begins with X, names no cell. Reserved for a future version of the format.</summary>
        Reserved = 1,

        /// <summary>A code that names a cell of the grid.</summary>
        Geometric = 2
    }
}
