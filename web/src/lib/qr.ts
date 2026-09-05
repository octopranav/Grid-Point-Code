// A QR code for the link to a place.
//
// The payload is the permalink, not the code and not a `geo:` URI. Scanning it
// opens this playground at that place, which is the thing being shared. The
// coordinate forms are already in "Also written as" for a reader who wants to
// hand them to a map application instead.

import { renderSVG } from 'uqr';

/**
 * Dark on white, in both themes, and that is deliberate.
 *
 * Everything else on this site follows the reader's theme. This does not: the
 * standard specifies dark modules on a light background, and while many
 * scanners cope with an inverted code, this is a thing whose entire purpose is
 * to be read by a phone nobody here has ever seen. It is a machine-readable
 * target rather than decoration, so it looks the same either way and carries
 * its own white plate.
 *
 * Four modules of quiet zone, which is the minimum the standard asks for. It
 * looks like generous padding and is not: without it, a scanner has no way to
 * find the edge of the symbol.
 *
 * Error correction M, fifteen per cent, because a code on a screen gets
 * photographed at an angle, under a reflection, by a camera that is not quite
 * in focus.
 */
export function qrFor(url: string): string {
    return renderSVG(url, {
        ecc: 'M',
        border: 4,
        pixelSize: 1,
        whiteColor: '#FFFFFF',
        blackColor: '#000000',
    });
}
