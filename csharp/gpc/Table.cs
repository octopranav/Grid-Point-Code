//  Copyright 2020 Pranavkumar Patel
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
//
//  Vendored from KombiN 1.0.3 so that Grid Point Code carries no external
//  dependencies. Internal to this assembly; not part of the public API.

using System;

namespace Ca.Pranavpatel.Algo.GridPointCode {
    /// <summary>
    /// Provides a methods to get index of combination pair
    /// and to get combination pair from index value.
    /// </summary>
    internal sealed class Table {
        /// <summary>
        /// Initializes a new instance of the <see cref="Table"/> class.
        /// </summary>
        /// <param name="lengthOfA">Number of elements in first set.</param>
        /// <param name="lengthOfB">Number of elements in second set.</param>
        /// <param name="zeroBasedIndex">True if sets index starts with zero otherwise False.</param>
        /// <returns><see cref="Table"/> object.</returns>
        /// <exception cref="ArgumentOutOfRangeException">
        /// if <paramref name="lengthOfA" /> or <paramref name="lengthOfB" /> is 0 or less.
        /// </exception>
        public Table(long lengthOfA, long lengthOfB, bool zeroBasedIndex) {
            if (lengthOfA < 1 || lengthOfB < 1) {
                throw new ArgumentOutOfRangeException(
                    paramName: (lengthOfA < 1) ? nameof(lengthOfA) : nameof(lengthOfB),
                    message: "Length of both sets must be greater than 0.");
            }
            LengthOfA = lengthOfA;
            LengthOfB = lengthOfB;
            ZeroBasedIndex = zeroBasedIndex;
            MaxIndexRange1 = MaxIndexRange2 = MaxIndexRange3 = 0;
            MaxSumRange1 = MaxSumRange2 = MaxSumRange3 = 0;
            Abstract();
        }

        /// <summary>Length of Set A</summary>
        public long LengthOfA { get; private set; }
        /// <summary>Length of Set B</summary>
        public long LengthOfB { get; private set; }
        /// <summary>Lower length of both sets</summary>
        public long LowerLength { get; private set; }
        /// <summary>Maximum Sum for Range 1</summary>
        public long MaxSumRange1 { get; private set; }
        /// <summary>Maximum Sum for Range 2</summary>
        public long MaxSumRange2 { get; private set; }
        /// <summary>Maximum Sum for Range 3</summary>
        public long MaxSumRange3 { get; private set; }
        /// <summary>Maximum Index for Range 1</summary>
        public long MaxIndexRange1 { get; private set; }
        /// <summary>Maximum Index for Range 2</summary>
        public long MaxIndexRange2 { get; private set; }
        /// <summary>Maximum Index for Range 3</summary>
        public long MaxIndexRange3 { get; private set; }
        /// <summary>Zero based indexing or not</summary>
        public bool ZeroBasedIndex { get; private set; }

        /// <summary>
        /// Sets an abstract values useful to get index and combination pair.
        /// </summary>
        /// <returns>none</returns>
        /// <exception cref="OverflowException">
        /// if arithmetic operation results in an overflow.
        /// </exception>
        private void Abstract() {
            checked {
                LowerLength = Math.Min(LengthOfA, LengthOfB);
                long higherLength = Math.Max(LengthOfA, LengthOfB);
                long difference = higherLength - LowerLength;
                long product = higherLength * LowerLength;
                long sum = higherLength + LowerLength;
                MaxSumRange1 = LowerLength + 1;
                if (difference == 0) {
                    MaxIndexRange1 = (product * MaxSumRange1) / sum;
                } else if (difference == 1) {
                    MaxIndexRange1 = product / 2;
                } else if (difference >= 2) {
                    MaxSumRange2 = higherLength;
                    MaxIndexRange1 = (product - LowerLength * (sum - 1 - 2 * LowerLength)) / 2;
                    MaxIndexRange2 = (product + LowerLength * (sum - 1 - 2 * LowerLength)) / 2;
                }

                if (product >= 2) {
                    MaxSumRange3 = sum;
                    MaxIndexRange3 = product;
                }
            }
        }

        /// <summary>
        /// Get index value for the combination pair.
        /// </summary>
        /// <param name="ai">Element index of set A.</param>
        /// <param name="bi">Element index of set B.</param>
        /// <returns>Index value for the given combination pair.</returns>
        /// <exception cref="ArgumentOutOfRangeException">
        /// if <paramref name="ai" /> or <paramref name="bi" /> has invalid value.
        /// </exception>
        /// <exception cref="OverflowException">
        /// if arithmetic operation results in an overflow.
        /// </exception>
        public long GetIndexOfElements(long ai, long bi) {
            if (ZeroBasedIndex) {
                if (ai < 0 || bi < 0) {
                    throw new ArgumentOutOfRangeException(paramName: (ai < 0) ? nameof(ai) : nameof(bi),
                        message: "Both element index values must be 0 or more.");
                }
                ai++;
                bi++;
            } else if (ai < 1 || bi < 1) {
                throw new ArgumentOutOfRangeException(paramName: (ai < 1) ? nameof(ai) : nameof(bi),
                    message: "Both element index values must be 1 or more.");
            }

            long index, previousIndex;
            checked {
                long sum = ai + bi;

                if (sum <= MaxSumRange1) {
                    previousIndex = sum - 2;
                    index = (previousIndex % 2 == 0 ? (previousIndex / 2) * (previousIndex + 1)
                            : (((previousIndex - 1) / 2) * previousIndex) + previousIndex)
                        + ai;
                } else if (sum <= MaxSumRange2) {
                    index = MaxIndexRange1
                        + ((sum - (MaxSumRange1 + 1)) * LowerLength)
                        + (LengthOfA < LengthOfB ? ai : (LengthOfB + 1) - bi);
                } else if (sum <= MaxSumRange3) {
                    previousIndex = MaxSumRange3 - sum + 1;
                    index = MaxIndexRange3
                        - (previousIndex % 2 == 0 ? (previousIndex / 2) * (previousIndex + 1)
                            : (((previousIndex - 1) / 2) * previousIndex) + previousIndex)
                        + (MaxIndexRange3 < 2 ? ai : (LengthOfB + 1) - bi);
                } else {
                    throw new ArgumentOutOfRangeException($"Sum of both the element index values must not be greater than {MaxSumRange3}");
                }
            }

            if (ZeroBasedIndex) {
                index--;
            }

            return index;
        }

        /// <summary>
        /// Get the combination pair for given index value.
        /// </summary>
        /// <param name="index">Index value of combination pair.</param>
        /// <returns>combination pair</returns>
        /// <exception cref="ArgumentOutOfRangeException">
        /// if <paramref name="index" /> has invalid value.
        /// </exception>
        /// <exception cref="OverflowException">
        /// if arithmetic operation results in an overflow.
        /// </exception>
        public (long, long) GetElementsAtIndex(long index) {
            if (ZeroBasedIndex) {
                if (index < 0) {
                    throw new ArgumentOutOfRangeException(paramName: nameof(index),
                        message: "Index value must be 0 or more.");
                }
                index++;
            } else if (index < 1) {
                throw new ArgumentOutOfRangeException(paramName: nameof(index),
                    message: "Index value must be 1 or more.");
            }

            long ai, bi;
            long previousIndex, sum;

            checked {
                if (index <= MaxIndexRange1) {
                    sum = Convert.ToInt64(Math.Ceiling((Math.Sqrt((index * 8) + 1) + 1) / 2));
                    ai = index - ((sum - 1) * (sum - 2) / 2);
                    bi = sum - ai;
                } else if (index <= MaxIndexRange2) {
                    sum = MaxSumRange1
                        + ((index - MaxIndexRange1) / LowerLength)
                        - (((index - MaxIndexRange1) % LowerLength == 0) ? 1 : 0)
                        + 1;
                    previousIndex = MaxIndexRange1 + ((sum - 1 - MaxSumRange1) * LowerLength);
                    if (LengthOfA >= LengthOfB) {
                        bi = (LengthOfB + 1) - (index - previousIndex);
                        ai = sum - bi;
                    } else {
                        ai = index - previousIndex;
                        bi = sum - ai;
                    }
                } else if (index <= MaxIndexRange3) {
                    long generic_maxSumRange3 = MaxSumRange3 - (MaxSumRange2 == 0 ? MaxSumRange1 : MaxSumRange2);
                    long generic_index = index - (MaxIndexRange2 == 0 ? MaxIndexRange1 : MaxIndexRange2);
                    long b = (2 * generic_maxSumRange3) + 1;
                    long generic_Sum = Convert.ToInt64(Math.Ceiling((b - Math.Sqrt(b * b - 8 * generic_index)) / 2));
                    sum = (MaxSumRange2 == 0 ? MaxSumRange1 : MaxSumRange2) + generic_Sum;
                    previousIndex = (MaxIndexRange2 == 0 ? MaxIndexRange1 : MaxIndexRange2)
                        + (generic_Sum == 1 ? 0 : ((generic_Sum - 1) * (b - generic_Sum + 1)) / 2);
                    if (MaxIndexRange3 >= 2) {
                        bi = (LengthOfB + 1) - (index - previousIndex);
                        ai = sum - bi;
                    } else {
                        ai = index - previousIndex;
                        bi = sum - ai;
                    }
                } else {
                    throw new ArgumentOutOfRangeException(paramName: nameof(index),
                        message: $"Index value must not be greater than {MaxIndexRange3}");
                }
            }

            if (ZeroBasedIndex) {
                ai--;
                bi--;
            }

            return (ai, bi);
        }
    }
}
