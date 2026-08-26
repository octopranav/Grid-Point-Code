package ca.pranavpatel.algo.gridpointcode;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * What screening a code found. Section 17.
 *
 * <p>Advisory, and non-normative. Nothing in this package refuses to encode,
 * decode or validate because of what is here.
 *
 * <p>The version comes back whether or not anything matched, because a caller
 * has to be able to tell "clean under this list" from "never screened".
 */
public class Screening {
    /** The version of the advisory list this result came from. */
    public final String Version;

    /**
     * The matched substrings, ordered by position and then by length.
     *
     * <p>Spans may overlap, and every match is reported. Empty when the code
     * matched nothing, which is a result rather than an absence.
     */
    public final List<Span> Spans;

    /**
     * <p>Constructor for Screening.</p>
     *
     * @param version a {@link java.lang.String} object.
     * @param spans a {@link java.util.List} of {@link ca.pranavpatel.algo.gridpointcode.Span}.
     */
    public Screening(String version, List<Span> spans) {
        this.Version = version;
        this.Spans = Collections.unmodifiableList(spans);
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

        Screening other = (Screening)obj;
        return Objects.equals(this.Version, other.Version)
                && Objects.equals(this.Spans, other.Spans);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(this.Version, this.Spans);
    }
}
