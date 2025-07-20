// Copyright 2017 Pranavkumar Patel
// Licensed under the Apache License, Version 2.0

import { Table } from '@pranavpatel.ca/algo-kombin';

/**
 * The GPC (Grid Point Code) class provides methods for encoding geographic
 * coordinates (latitude and longitude) into a compact alphanumeric string,
 * and decoding such strings back to coordinates.
 *
 * This implementation ensures consistency, precision, and compactness
 * using a custom base-27 encoding and a fixed 11-character format.
 */
export class GPC {
    private static readonly MIN_LAT = -90;
    private static readonly MAX_LAT = 90;
    private static readonly MIN_LONG = -180;
    private static readonly MAX_LONG = 180;
    private static readonly MAX_POINT = 648_009_999_999_999n;
    private static readonly ELEVEN = 205_881_132_094_649n;
    private static readonly CHARACTERS = "CDFGHJKLMNPRTVWXY0123456789";
    private static readonly GPC_LENGTH = 11;
    private static readonly LatLongTable = new Table(180, 360, true);

    /**
     * Encodes a latitude and longitude into a Grid Point Code (GPC).
     * @param latitude Latitude in decimal degrees.
     * @param longitude Longitude in decimal degrees.
     * @param formatted Whether to format the GPC with separators (default: true).
     * @returns A formatted or raw 11-character GPC string.
     * @throws RangeError if coordinates are out of valid range.
     */
    public static encode(latitude: number, longitude: number, formatted: boolean = true): string {
        const [valid, message] = this.isValidCoordinates(latitude, longitude);
        if (!valid) throw new RangeError(`${message.toUpperCase()}: value out of valid range.`);

        const point = this.getPoint(latitude, longitude);
        let gridPointCode = this.encodePoint(point + this.ELEVEN);

        if (formatted) {
            gridPointCode = this.formatGPC(gridPointCode);
        }
        return gridPointCode;
    }

    /**
     * Decodes a Grid Point Code (GPC) into latitude and longitude.
     * @param gridPointCode The encoded GPC string (formatted or raw).
     * @returns A tuple [latitude, longitude] in decimal degrees.
     * @throws Error or RangeError for null, malformed, or invalid GPC strings.
     */
    public static decode(gridPointCode: string): [number, number] {
        if (!gridPointCode || gridPointCode.trim() === '') {
            throw new Error("GPC_NULL: Invalid GPC.");
        }

        gridPointCode = gridPointCode.replace(/[\s#-]/g, '').toUpperCase();
        let [valid, message] = this.validateGPC(gridPointCode);
        if (!valid) throw new RangeError(`${message}: Invalid GPC.`);

        const point = this.decodeToPoint(gridPointCode) - this.ELEVEN;
        [valid, message] = this.validatePoint(point);
        if (!valid) throw new RangeError(`${message}: Invalid GPC.`);

        return this.getCoordinates(point);
    }

    /**
     * Validates the latitude and longitude ranges.
     * @param latitude Latitude in decimal degrees.
     * @param longitude Longitude in decimal degrees.
     * @returns [true, ""] if valid, otherwise [false, "LATITUDE" or "LONGITUDE"].
     */
    public static isValidCoordinates(latitude: number, longitude: number): [boolean, string] {
        if (latitude <= this.MIN_LAT || latitude >= this.MAX_LAT) return [false, "LATITUDE"];
        if (longitude <= this.MIN_LONG || longitude >= this.MAX_LONG) return [false, "LONGITUDE"];
        return [true, ""];
    }

    /**
     * Validates the given Grid Point Code (GPC).
     * @param gridPointCode GPC string to validate.
     * @returns [true, ""] if valid, otherwise [false, error message].
     */
    public static isValid(gridPointCode: string): [boolean, string] {
        gridPointCode = gridPointCode.replace(/[\s#-]/g, '').toUpperCase();
        if (!gridPointCode) return [false, "GPC_NULL"];

        let [valid, message] = this.validateGPC(gridPointCode);
        if (!valid) return [false, message];

        [valid, message] = this.validatePoint(this.decodeToPoint(gridPointCode) - this.ELEVEN);
        if (!valid) return [false, message];

        return [true, ""];
    }

    /**
     * Converts latitude and longitude into a unique numeric point ID.
     * Used internally before encoding to base-27.
     * @param lat Latitude.
     * @param long Longitude.
     * @returns A bigint representing the point.
     */
    private static getPoint(lat: number, long: number): bigint {
        const lat7 = this.splitTo7(lat);
        const long7 = this.splitTo7(long);

        let point = BigInt(
            Math.pow(10, 10) *
            (this.LatLongTable.GetIndexOfElements(
                (lat7[1] * 2) + (lat7[0] === -1 ? 1 : 0),
                (long7[1] * 2) + (long7[0] === -1 ? 1 : 0)
            ) + 1)
        );

        let power = 9;
        for (let i = 2; i <= 6; i++) {
            point += BigInt(Math.pow(10, power--)) * BigInt(lat7[i]);
            point += BigInt(Math.pow(10, power--)) * BigInt(long7[i]);
        }

        return point;
    }

    /**
     * Converts a decimal coordinate to a 7-part array:
     * [sign, integer, fractional_digit1...fractional_digit5]
     * @param coord Coordinate value as number.
     * @returns An array of 7 integers.
     */
    private static splitTo7(coord: number): number[] {
        const result = new Array(7).fill(0);
        
        const coordStr = coord.toFixed(10);
        result[0] = coordStr.startsWith('-') ? -1 : 1;

        const cleaned = coordStr.replace(/^[-+]/, '');
        const [integerPart, fractionalPartRaw = ''] = cleaned.split('.');
        result[1] = parseInt(integerPart, 10);

        const fractionalPart = (fractionalPartRaw + '00000').slice(0, 5);

        for (let i = 0; i < 5; i++) {
            result[i + 2] = parseInt(fractionalPart[i], 10);
        }

        return result;
    }

    /**
     * Encodes a numeric point into a base-27 GPC string.
     * @param point Bigint point number.
     * @returns Encoded GPC string (unformatted).
     */
    private static encodePoint(point: bigint): string {
        let gpc = '';
        const base = BigInt(27);
        while (point > 0) {
            gpc = this.CHARACTERS[Number(point % base)] + gpc;
            point = point / base;
        }
        return gpc;
    }

    /**
     * Formats an 11-character GPC into #XXXX-XXXX-XXX layout.
     * @param gpc Unformatted 11-character GPC string.
     * @returns Formatted GPC string.
     */
    private static formatGPC(gpc: string): string {
        return `#${gpc.slice(0, 4)}-${gpc.slice(4, 8)}-${gpc.slice(8, 11)}`;
    }

    /**
     * Validates the character content and length of a GPC string.
     * @param gpc GPC string to validate.
     * @returns [true, ""] if valid, otherwise [false, error reason].
     */
    private static validateGPC(gpc: string): [boolean, string] {
        if (gpc.length !== this.GPC_LENGTH) return [false, "GPC_LENGTH"];
        for (const char of gpc) {
            if (!this.CHARACTERS.includes(char)) return [false, "GPC_CHAR"];
        }
        return [true, ""];
    }

    /**
     * Validates that the decoded point value is within allowed range.
     * @param point GPC-decoded point.
     * @returns [true, ""] if valid, otherwise [false, "GPC_RANGE"].
     */
    private static validatePoint(point: bigint): [boolean, string] {
        if (point > this.MAX_POINT) return [false, "GPC_RANGE"];
        return [true, ""];
    }

    /**
     * Decodes an 11-character GPC string into a numeric point.
     * @param gpc GPC string (unformatted).
     * @returns Decoded point as bigint.
     */
    private static decodeToPoint(gpc: string): bigint {
        let point = 0n;
        for (let i = 0; i < this.GPC_LENGTH; i++) {
            point *= 27n;
            point += BigInt(this.CHARACTERS.indexOf(gpc[i]));
        }
        return point;
    }

    /**
     * Converts a valid point number into latitude and longitude.
     * @param point Valid GPC-decoded point.
     * @returns Tuple [latitude, longitude] in decimal degrees.
     */
    private static getCoordinates(point: bigint): [number, number] {
        const latLongIndex = Number(point / 10_000_000_000n);
        const fractional = point - BigInt(latLongIndex) * 10_000_000_000n;

        const [lat7, long7] = this.splitPointTo7(latLongIndex, fractional);

        let power = 0;
        let tempLat = 0, tempLong = 0;

        for (let i = 6; i >= 1; i--) {
            tempLat += lat7[i] * Math.pow(10, power);
            tempLong += long7[i] * Math.pow(10, power++);
        }

        return [
            tempLat / Math.pow(10, 5) * lat7[0],
            tempLong / Math.pow(10, 5) * long7[0]
        ];
    }

    /**
     * Reconstructs lat/long 7-part arrays from a point index and fractional component.
     * Used in decoding to rebuild original coordinate precision.
     * @param index Index from table lookup.
     * @param fractional The remaining digits of the point.
     * @returns A tuple of two arrays: [lat7[], long7[]]
     */
    private static splitPointTo7(index: number, fractional: bigint): [number[], number[]] {
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
