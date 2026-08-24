// Copyright 2020 Pranavkumar Patel
// Licensed under the Apache License, Version 2.0
//
// Vendored from KombiN 1.0.3 so that Grid Point Code carries no external
// dependencies. Internal to this package; not exported from the entry point.

/**
 * Table provides O(1) bijective mapping between ordered pairs from two finite sets and a unique linear index.
 * All possible combination pairs are sorted by their weight (sum of indices) in ascending order.
 * Supports both zero-based and one-based indexing.
 */
export class Table {
    /**
     * Initializes a new instance of the Table class.
     * @param lengthOfA Number of elements in the first set.
     * @param lengthOfB Number of elements in the second set.
     * @param zeroBasedIndex True for zero-based indexing, false for one-based indexing.
     * @throws Error if lengthOfA or lengthOfB is 0 or less.
     */
    constructor(lengthOfA: number, lengthOfB: number, zeroBasedIndex: boolean) {
        if (lengthOfA < 1 || lengthOfB < 1) {
            throw new Error('Length of both sets must be greater than 0.');
        }
        this.LengthOfA = lengthOfA;
        this.LengthOfB = lengthOfB;
        this.ZeroBasedIndex = zeroBasedIndex;
        this.MaxIndexRange1 = this.MaxIndexRange2 = this.MaxIndexRange3 = 0;
        this.MaxSumRange1 = this.MaxSumRange2 = this.MaxSumRange3 = 0;
        this.Abstract();
    }

    /** Length of Set A */
    LengthOfA: number;
    /** Length of Set B */
    LengthOfB: number;
    /** Lower length of both sets */
    LowerLength!: number;
    /** Maximum Sum for Range 1 */
    MaxSumRange1: number;
    /** Maximum Sum for Range 2 */
    MaxSumRange2: number;
    /** Maximum Sum for Range 3 */
    MaxSumRange3: number;
    /** Maximum Index for Range 1 */
    MaxIndexRange1: number;
    /** Maximum Index for Range 2 */
    MaxIndexRange2: number;
    /** Maximum Index for Range 3 */
    MaxIndexRange3: number;
    /** Zero based indexing or not */
    ZeroBasedIndex: boolean;

    /**
     * Computes and sets internal values used for index and pair calculations.
     */
    private Abstract(): void {
        this.LowerLength = this.LengthOfA < this.LengthOfB ? this.LengthOfA : this.LengthOfB;
        const higherLength: number = this.LengthOfA > this.LengthOfB ? this.LengthOfA : this.LengthOfB;
        const difference: number = higherLength - this.LowerLength;
        const product: number = higherLength * this.LowerLength;
        const sum: number = higherLength + this.LowerLength;
        this.MaxSumRange1 = this.LowerLength + 1;
        if (difference == 0) {
            this.MaxIndexRange1 = (product * this.MaxSumRange1) / sum;
        } else if (difference == 1) {
            this.MaxIndexRange1 = product / 2;
        } else if (difference >= 2) {
            this.MaxSumRange2 = higherLength;
            this.MaxIndexRange1 = (product - this.LowerLength * (sum - 1 - 2 * this.LowerLength)) / 2;
            this.MaxIndexRange2 = (product + this.LowerLength * (sum - 1 - 2 * this.LowerLength)) / 2;
        }

        if (product >= 2) {
            this.MaxSumRange3 = sum;
            this.MaxIndexRange3 = product;
        }
    }

    /**
     * Returns the unique index value for the given combination pair (ai, bi).
     * @param ai Element index of set A.
     * @param bi Element index of set B.
     * @returns Index value for the given combination pair.
     * @throws Error if ai or bi has invalid value.
     */
    public GetIndexOfElements(ai: number, bi: number): number {
        if (this.ZeroBasedIndex) {
            if (ai < 0 || bi < 0) {
                throw new Error('Both element index values must be 0 or more.');
            }
            ai++;
            bi++;
        } else if (ai < 1 || bi < 1) {
            throw new Error('Both element index values must be 1 or more.');
        }

        let index: number, previousIndex: number;
        const sum: number = ai + bi;

        if (sum <= this.MaxSumRange1) {
            previousIndex = sum - 2;
            index =
                (previousIndex % 2 == 0
                    ? (previousIndex / 2) * (previousIndex + 1)
                    : ((previousIndex - 1) / 2) * previousIndex + previousIndex) + ai;
        } else if (sum <= this.MaxSumRange2) {
            index =
                this.MaxIndexRange1 +
                (sum - (this.MaxSumRange1 + 1)) * this.LowerLength +
                (this.LengthOfA < this.LengthOfB ? ai : this.LengthOfB + 1 - bi);
        } else if (sum <= this.MaxSumRange3) {
            previousIndex = this.MaxSumRange3 - sum + 1;
            index =
                this.MaxIndexRange3 -
                (previousIndex % 2 == 0
                    ? (previousIndex / 2) * (previousIndex + 1)
                    : ((previousIndex - 1) / 2) * previousIndex + previousIndex) +
                (this.MaxIndexRange3 < 2 ? ai : this.LengthOfB + 1 - bi);
        } else {
            throw new Error(`Sum of both the element index values must not be greater than ${this.MaxSumRange3}`);
        }

        if (this.ZeroBasedIndex) {
            index--;
        }

        return index;
    }

    /**
     * Returns the combination pair corresponding to the given index value.
     * @param index Index value of the combination pair.
     * @returns Tuple [ai, bi] representing the combination pair.
     * @throws Error if index has invalid value.
     */
    public GetElementsAtIndex(index: number): [number, number] {
        if (this.ZeroBasedIndex) {
            if (index < 0) {
                throw new Error('Index value must be 0 or more.');
            }
            index++;
        } else if (index < 1) {
            throw new Error('Index value must be 1 or more.');
        }

        let ai: number, bi: number;
        let previousIndex: number, sum: number;

        if (index <= this.MaxIndexRange1) {
            sum = Math.ceil((Math.sqrt(index * 8 + 1) + 1) / 2);
            ai = index - ((sum - 1) * (sum - 2)) / 2;
            bi = sum - ai;
        } else if (index <= this.MaxIndexRange2) {
            sum =
                this.MaxSumRange1 +
                Math.floor((index - this.MaxIndexRange1) / this.LowerLength) -
                ((index - this.MaxIndexRange1) % this.LowerLength == 0 ? 1 : 0) +
                1;
            previousIndex = this.MaxIndexRange1 + (sum - 1 - this.MaxSumRange1) * this.LowerLength;
            if (this.LengthOfA >= this.LengthOfB) {
                bi = this.LengthOfB + 1 - (index - previousIndex);
                ai = sum - bi;
            } else {
                ai = index - previousIndex;
                bi = sum - ai;
            }
        } else if (index <= this.MaxIndexRange3) {
            const generic_maxSumRange3: number =
                this.MaxSumRange3 - (this.MaxSumRange2 == 0 ? this.MaxSumRange1 : this.MaxSumRange2);
            const generic_index: number =
                index - (this.MaxIndexRange2 == 0 ? this.MaxIndexRange1 : this.MaxIndexRange2);
            const b: number = 2 * generic_maxSumRange3 + 1;
            const generic_Sum: number = Math.ceil((b - Math.sqrt(b * b - 8 * generic_index)) / 2);
            sum = (this.MaxSumRange2 == 0 ? this.MaxSumRange1 : this.MaxSumRange2) + generic_Sum;
            previousIndex =
                (this.MaxIndexRange2 == 0 ? this.MaxIndexRange1 : this.MaxIndexRange2) +
                (generic_Sum == 1 ? 0 : ((generic_Sum - 1) * (b - generic_Sum + 1)) / 2);
            if (this.MaxIndexRange3 >= 2) {
                bi = this.LengthOfB + 1 - (index - previousIndex);
                ai = sum - bi;
            } else {
                ai = index - previousIndex;
                bi = sum - ai;
            }
        } else {
            throw new Error(`Index value must not be greater than ${this.MaxIndexRange3}`);
        }

        if (this.ZeroBasedIndex) {
            ai--;
            bi--;
        }
        return [ai, bi];
    }
}
