package ca.pranavpatel.algo.gridpointcode;

import java.util.Objects;

/**
 * How big a cell is at one level of the grid. Section 18.4.
 *
 * <p>The north-south figure holds at every latitude. The east-west figure is the
 * value at the equator and is documented as such: it shrinks with the cosine of
 * latitude, and the format leaves that multiplication to the caller rather than
 * taking a position on which latitude is worth quoting.
 */
public class Dimensions {
    /** The span in degrees of latitude. */
    public final double LatitudeSpan;

    /** The span in degrees of longitude. */
    public final double LongitudeSpan;

    /** The height in metres, at every latitude. */
    public final double NorthSouth;

    /** The width in metres, at the equator. */
    public final double EastWest;

    /**
     * <p>Constructor for Dimensions.</p>
     *
     * @param latitudeSpan a double.
     * @param longitudeSpan a double.
     * @param northSouth a double.
     * @param eastWest a double.
     */
    public Dimensions(double latitudeSpan, double longitudeSpan, double northSouth, double eastWest) {
        this.LatitudeSpan = latitudeSpan;
        this.LongitudeSpan = longitudeSpan;
        this.NorthSouth = northSouth;
        this.EastWest = eastWest;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        Dimensions other = (Dimensions)obj;
        return (Double.compare(this.LatitudeSpan, other.LatitudeSpan) == 0
                && Double.compare(this.LongitudeSpan, other.LongitudeSpan) == 0
                && Double.compare(this.NorthSouth, other.NorthSouth) == 0
                && Double.compare(this.EastWest, other.EastWest) == 0);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(this.LatitudeSpan, this.LongitudeSpan, this.NorthSouth, this.EastWest);
    }
}
