package ca.pranavpatel.algo.gridpointcode;

import java.util.Objects;

/**
 * What a string classified as, and why, when the answer is
 * {@link ca.pranavpatel.algo.gridpointcode.CodeClass#INVALID}.
 */
public class Classification {
    /** The class the string falls into. */
    public final CodeClass Kind;

    /** The reason code, empty for anything that is not INVALID. */
    public final String Reason;

    /**
     * <p>Constructor for Classification.</p>
     *
     * @param kind a {@link ca.pranavpatel.algo.gridpointcode.CodeClass} object.
     * @param reason a {@link java.lang.String} object.
     */
    public Classification(CodeClass kind, String reason) {
        this.Kind = kind;
        this.Reason = reason;
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

        Classification other = (Classification)obj;
        return this.Kind == other.Kind && this.Reason.equals(other.Reason);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(this.Kind, this.Reason);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return this.Kind + (this.Reason.isEmpty() ? "" : ", " + this.Reason);
    }
}
