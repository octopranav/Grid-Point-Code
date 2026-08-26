package ca.pranavpatel.algo.gridpointcode;

import java.util.Objects;

/**
 * One matched substring of a code. Section 17.4.
 *
 * <p>The position counts from 1, so it names the character of the ten-character
 * code as a person reading it would.
 */
public class Span {
    /** Where the match starts, counting from 1. */
    public final int Position;

    /** How many characters it covers. */
    public final int Length;

    /**
     * <p>Constructor for Span.</p>
     *
     * @param position an int, counting from 1.
     * @param length an int.
     */
    public Span(int position, int length) {
        this.Position = position;
        this.Length = length;
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

        Span other = (Span)obj;
        return this.Position == other.Position && this.Length == other.Length;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(this.Position, this.Length);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return this.Position + ":" + this.Length;
    }
}
