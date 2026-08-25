// Copyright 2017 Pranavkumar Patel
// Licensed under the Apache License, Version 2.0

import { GPCError } from './GPCError';
import { Table } from './Table';

/**
 * Version 1 decoding, kept so that every code ever issued still resolves.
 *
 * Version 1 is a base-27 numeral over a different alphabet, eleven characters
 * long, and it carries no locality guarantee: two codes sharing four
 * characters can be nineteen thousand kilometres apart. It is frozen. There is
 * no version 1 encoder here and there will not be one -- the format is
 * readable, not writable, and anyone who still needs to mint version 1 codes
 * pins 1.1.x.
 *
 * Nothing in this file is reached by a version 2 code. It is entered only when
 * `GPC.decode` sees eleven characters, or when a caller asks for `decodeV1`
 * outright. Appendix B of SPEC.md describes the format.
 */
export class V1 {
    /** base27, letters first, so it is not in ASCII order. */
    public static readonly CHARACTERS = 'CDFGHJKLMNPRTVWXY0123456789';
    public static readonly CODE_LENGTH = 11;

    private static readonly MIN_POINT = 10_000_000_000n;
    private static readonly MAX_POINT = 648_009_999_999_999n;
    /** The offset that makes every code exactly eleven characters. */
    private static readonly ELEVEN = 205_881_132_094_649n;
    private static readonly LatLongTable = new Table(180, 360, true);

    /**
     * Upper-case and drop the separators. Version 1 has no alias table.
     * @param gridPointCode Formatted or unformatted code.
     * @returns The bare characters.
     */
    public static clean(gridPointCode: string): string {
        return gridPointCode.replace(/[\s#-]/g, '').toUpperCase();
    }

    /**
     * The version 1 presentation, `#XXXX-XXXX-XXX`.
     * @param code Unformatted eleven-character code.
     * @returns The formatted code.
     */
    public static format(code: string): string {
        return `#${code.slice(0, 4)}-${code.slice(4, 8)}-${code.slice(8, 11)}`;
    }

    /**
     * Decode an eleven-character version 1 code to its cell's corner.
     *
     * Version 1 returns the corner, not the centre. That differs from version 2
     * by design: the value is the one every version 1 release has returned, and
     * changing it would move every code ever issued.
     * @param gridPointCode Formatted or unformatted code.
     * @returns Tuple [latitude, longitude] in decimal degrees.
     * @throws GPCError if the code is null, malformed, or outside the grid.
     */
    public static decode(gridPointCode: string): [number, number] {
        if (!gridPointCode || gridPointCode.trim() === '') throw new GPCError('GPC_NULL');

        const code = this.clean(gridPointCode);

        let [valid, message] = this.validateCode(code);
        if (!valid) throw new GPCError(message);

        const point = this.toPoint(code) - this.ELEVEN;

        [valid, message] = this.validatePoint(point);
        if (!valid) throw new GPCError(message);

        return this.toCoordinates(point);
    }

    /**
     * Whether a string is a version 1 code, and why not when it is not.
     * @param gridPointCode Formatted or unformatted code.
     * @returns [true, ""] if valid, otherwise [false, reason].
     */
    public static isValid(gridPointCode: string): [boolean, string] {
        if (gridPointCode === null || gridPointCode === undefined) return [false, 'GPC_NULL'];
        const code = this.clean(gridPointCode);
        if (!code) return [false, 'GPC_NULL'];

        const [valid, message] = this.validateCode(code);
        if (!valid) return [false, message];

        return this.validatePoint(this.toPoint(code) - this.ELEVEN);
    }

    /** Length and alphabet, on an already cleaned code. */
    private static validateCode(code: string): [boolean, string] {
        if (code.length !== this.CODE_LENGTH) return [false, 'GPC_LENGTH'];
        for (const character of code) {
            if (!this.CHARACTERS.includes(character)) return [false, 'GPC_CHAR'];
        }
        return [true, ''];
    }

    /** Whether a decoded point falls inside the version 1 grid. */
    private static validatePoint(point: bigint): [boolean, string] {
        if (point < this.MIN_POINT || point > this.MAX_POINT) return [false, 'GPC_RANGE'];
        return [true, ''];
    }

    /** The base-27 value of an eleven-character code. */
    private static toPoint(code: string): bigint {
        let point = 0n;
        for (let i = 0; i < this.CODE_LENGTH; i++) {
            point *= 27n;
            point += BigInt(this.CHARACTERS.indexOf(code[i]));
        }
        return point;
    }

    /** Split a point back into latitude and longitude. */
    private static toCoordinates(point: bigint): [number, number] {
        const latLongIndex = Number(point / 10_000_000_000n);
        const fractional = point - BigInt(latLongIndex) * 10_000_000_000n;

        const [lat7, long7] = this.splitTo7(latLongIndex, fractional);

        let power = 0;
        let tempLat = 0,
            tempLong = 0;

        for (let i = 6; i >= 1; i--) {
            tempLat += lat7[i] * Math.pow(10, power);
            tempLong += long7[i] * Math.pow(10, power++);
        }

        return [(tempLat / Math.pow(10, 5)) * lat7[0], (tempLong / Math.pow(10, 5)) * long7[0]];
    }

    /**
     * Sign, whole degrees and five decimals, for each axis.
     *
     * The whole degrees come back through the combination table, which pairs an
     * index with two doubled-and-offset whole values. The ten decimal digits of
     * `fractional` alternate, latitude first.
     */
    private static splitTo7(index: number, fractional: bigint): [number[], number[]] {
        const lat7 = new Array(7);
        const long7 = new Array(7);

        const [tLat, tLong] = this.LatLongTable.GetElementsAtIndex(index - 1);

        lat7[0] = tLat % 2 !== 0 ? -1 : 1;
        lat7[1] = lat7[0] === -1 ? (tLat - 1) / 2 : tLat / 2;

        long7[0] = tLong % 2 !== 0 ? -1 : 1;
        long7[1] = long7[0] === -1 ? (tLong - 1) / 2 : tLong / 2;

        let power = 9;
        for (let i = 2; i <= 6; i++) {
            lat7[i] = Number((fractional / BigInt(Math.pow(10, power--))) % 10n);
            long7[i] = Number((fractional / BigInt(Math.pow(10, power--))) % 10n);
        }

        return [lat7, long7];
    }
}
