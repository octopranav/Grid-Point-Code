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
    /// Thrown for a code that will not decode.
    /// <para>
    /// <see cref="Reason" /> is the part to branch on. <c>GPC_RESERVED</c> is
    /// deliberately distinct from every invalid reason: a reserved code is well
    /// formed and may one day mean something, while an invalid one is a typing
    /// error.
    /// </para>
    /// <para>
    /// Reasons are <c>GPC_NULL</c>, <c>GPC_LENGTH</c>, <c>GPC_CHAR</c>,
    /// <c>GPC_CHECK</c>, <c>GPC_RESERVED</c> and <c>GPC_RANGE</c>. The last
    /// belongs to version 1 only. A coordinate outside the domain throws
    /// <see cref="ArgumentOutOfRangeException" /> instead, as it did in version 1.
    /// </para>
    /// </summary>
    public class GPCException : FormatException {

        /// <summary>The reason code, for a caller that wants to branch on it.</summary>
        public string Reason { get; } = string.Empty;

        /// <summary>Creates an exception carrying a reason code.</summary>
        /// <param name="reason">One of the reason codes named on this type.</param>
        public GPCException(string reason) : base($"{reason}: Invalid GPC.") {
            Reason = reason;
        }

        /// <summary>Creates an exception with no reason code.</summary>
        public GPCException() : base() {
        }

        /// <summary>Creates an exception with a message and no reason code.</summary>
        /// <param name="message">The message.</param>
        /// <param name="inner">The exception that caused this one.</param>
        public GPCException(string message, Exception inner) : base(message, inner) {
        }
    }
}
