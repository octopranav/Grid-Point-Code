// Copyright 2017 Pranavkumar Patel
// Licensed under the Apache License, Version 2.0

/**
 * The one error this package throws.
 *
 * The reason code is the part to branch on. `GPC_RESERVED` is deliberately
 * distinct from every invalid reason: a reserved code is well formed and may
 * one day mean something, while an invalid one is a typing error.
 *
 * Reasons are `LATITUDE` and `LONGITUDE` for coordinates, and `GPC_NULL`,
 * `GPC_LENGTH`, `GPC_CHAR`, `GPC_CHECK`, `GPC_RESERVED` and `GPC_RANGE` for
 * codes. The last belongs to version 1 only.
 */
export class GPCError extends Error {
    public readonly reason: string;

    constructor(reason: string, message?: string) {
        super(message ?? `${reason}: Invalid GPC.`);
        this.name = 'GPCError';
        this.reason = reason;
        // Extending a built-in loses the prototype chain when the output is
        // downlevelled, so instanceof would stop working without this.
        Object.setPrototypeOf(this, GPCError.prototype);
    }
}
