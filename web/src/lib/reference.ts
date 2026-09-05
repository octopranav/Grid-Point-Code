// Which landmarks a short form can safely be paired with.
//
// A short form is five characters and a reference: the listener finds the
// reference, and recovery returns the full code. Section 12.3 of the
// specification bounds when that works, and the bound is the reason this file
// exists rather than a distance comparison written inline at the call site.

/**
 * The recovery bound of section 12.3, in degrees.
 *
 * Half a level-5 cell in each axis. This is a BOX IN DEGREES, and the
 * distinction is not pedantry: the longitude bound is a constant number of
 * degrees, so the distance it covers on the ground shrinks with the cosine of
 * the latitude. A landmark three kilometres due east is comfortably inside the
 * latitude bound of 3.999 km and still outside this box from about 60° of
 * latitude upward -- which is Oslo, Stockholm, Helsinki, Saint Petersburg and
 * most of Alaska. Anything that filters these candidates by a radius in metres
 * is wrong there, and wrong silently.
 */
export const RECOVERY = {
    latitude: 0.03598848,
    longitude: 0.04798464,
} as const;

/** A named place a short form can be given as its reference. */
export interface Landmark {
    name: string;
    latitude: number;
    longitude: number;
}

export interface Candidate extends Landmark {
    /** Great-circle metres from the point. For the reader, never for the test. */
    distance: number;
    /** Which way the landmark lies, to the nearest octant. */
    bearing: string;
    /**
     * Where the landmark sits in the recovery box: 0 at the point itself, 1
     * exactly on the boundary, above 1 outside it. Whichever axis is closer to
     * its limit decides, because either one failing is enough.
     */
    tightness: number;
}

// Named here rather than imported from the nudge pad's COMPASS. The two lists
// hold the same eight strings and mean different things -- that one is the
// order the library returns neighbouring cells in, this one is a direction on
// the ground -- and a shared constant would tie them together for no reason.
const OCTANTS = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'];

/** A longitude difference carried into the range that contains the short way round. */
const wrap = (degrees: number) => ((degrees + 540) % 360) - 180;

/**
 * How much of the recovery box a reference uses up, as a fraction.
 *
 * One number rather than two booleans, because the same quantity answers both
 * questions worth asking: whether recovery is guaranteed at all (at most 1),
 * and how much room is left over for the listener's idea of where the landmark
 * is to differ from ours (how far below 1). That second margin is the one that
 * bites in practice. 12.3 is explicit that two services can place the same
 * suburb kilometres apart, and no arithmetic here can rescue that.
 */
export function tightness(
    latitude: number,
    longitude: number,
    referenceLatitude: number,
    referenceLongitude: number,
): number {
    return Math.max(
        Math.abs(referenceLatitude - latitude) / RECOVERY.latitude,
        Math.abs(wrap(referenceLongitude - longitude)) / RECOVERY.longitude,
    );
}

/** Whether recovery against this reference is guaranteed. Section 12.3. */
export const recoverable = (
    latitude: number,
    longitude: number,
    referenceLatitude: number,
    referenceLongitude: number,
) => tightness(latitude, longitude, referenceLatitude, referenceLongitude) <= 1;

const EARTH = 6371008.8;
const rad = (degrees: number) => (degrees * Math.PI) / 180;

/** Great-circle metres. Shown to the reader; never used to decide anything. */
function separation(
    latitude: number,
    longitude: number,
    otherLatitude: number,
    otherLongitude: number,
): number {
    const dLat = rad(otherLatitude - latitude);
    const dLng = rad(wrap(otherLongitude - longitude));
    const a =
        Math.sin(dLat / 2) ** 2 +
        Math.cos(rad(latitude)) * Math.cos(rad(otherLatitude)) * Math.sin(dLng / 2) ** 2;
    return 2 * EARTH * Math.asin(Math.min(1, Math.sqrt(a)));
}

function octant(
    latitude: number,
    longitude: number,
    otherLatitude: number,
    otherLongitude: number,
): string {
    const dLng = rad(wrap(otherLongitude - longitude));
    const y = Math.sin(dLng) * Math.cos(rad(otherLatitude));
    const x =
        Math.cos(rad(latitude)) * Math.sin(rad(otherLatitude)) -
        Math.sin(rad(latitude)) * Math.cos(rad(otherLatitude)) * Math.cos(dLng);
    const degrees = (Math.atan2(y, x) * 180) / Math.PI;
    return OCTANTS[Math.round(((degrees + 360) % 360) / 45) % 8];
}

/**
 * The landmarks a short form may be paired with, nearest first.
 *
 * Filtered by the box and sorted by distance, which are deliberately two
 * different measures: the box is what the specification guarantees, and
 * distance is what a reader understands. Sorting by the box's own fraction
 * instead would put a landmark further away above a nearer one whenever the
 * axes disagree, which is correct and reads as a mistake.
 *
 * Anything not returned here is not merely a worse choice. Outside the box
 * recovery does not fail: it returns a different cell's copy of the same five
 * characters, a plausible place eight or ten kilometres away, with nothing
 * raised and nothing to notice.
 */
export function references(
    latitude: number,
    longitude: number,
    landmarks: readonly Landmark[],
): Candidate[] {
    const out: Candidate[] = [];
    for (const landmark of landmarks) {
        const tight = tightness(latitude, longitude, landmark.latitude, landmark.longitude);
        if (tight > 1) continue;
        out.push({
            ...landmark,
            tightness: tight,
            distance: separation(latitude, longitude, landmark.latitude, landmark.longitude),
            bearing: octant(latitude, longitude, landmark.latitude, landmark.longitude),
        });
    }
    return out.sort((a, b) => a.distance - b.distance);
}
