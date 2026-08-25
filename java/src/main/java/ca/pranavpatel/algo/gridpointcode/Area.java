package ca.pranavpatel.algo.gridpointcode;

import java.util.Objects;

/**
 * The boundaries of the cell a code names.
 *
 * <p>A box is a closed region, so the northern edge of the top row is latitude
 * +90 and the eastern edge of the last column is longitude +180, even though
 * neither value encodes to that cell.
 */
public class Area {
    /** The southern edge, in decimal degrees. */
    public final double South;

    /** The western edge, in decimal degrees. */
    public final double West;

    /** The northern edge, in decimal degrees. */
    public final double North;

    /** The eastern edge, in decimal degrees. */
    public final double East;

    /**
     * <p>Constructor for Area.</p>
     *
     * @param south a double.
     * @param west a double.
     * @param north a double.
     * @param east a double.
     */
    public Area(double south, double west, double north, double east) {
        this.South = south;
        this.West = west;
        this.North = north;
        this.East = east;
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

        Area area = (Area)obj;
        return (Double.compare(this.South, area.South) == 0
                && Double.compare(this.West, area.West) == 0
                && Double.compare(this.North, area.North) == 0
                && Double.compare(this.East, area.East) == 0);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(this.South, this.West, this.North, this.East);
    }
}
