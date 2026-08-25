package ca.pranavpatel.algo.gridpointcode;

/**
 * Thrown for a coordinate outside the domain or a code that will not decode.
 *
 * <p>The reason code is the part to branch on. {@code GPC_RESERVED} is
 * deliberately distinct from every invalid reason: a reserved code is well
 * formed and may one day mean something, while an invalid one is a typing
 * error.
 *
 * <p>Reasons are {@code LATITUDE} and {@code LONGITUDE} for coordinates, and
 * {@code GPC_NULL}, {@code GPC_LENGTH}, {@code GPC_CHAR}, {@code GPC_CHECK},
 * {@code GPC_RESERVED} and {@code GPC_RANGE} for codes. The last belongs to
 * version 1 only.
 *
 * <p>Extends {@link java.lang.IllegalArgumentException}, which is what version 1
 * threw, so existing handlers keep working.
 */
public class GPCException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    /** The reason code, for a caller that wants to branch on it. */
    private final String reason;

    /**
     * <p>Constructor for GPCException.</p>
     *
     * @param reason one of the reason codes named on this type.
     */
    public GPCException(String reason) {
        super(reason + ": Invalid GPC.");
        this.reason = reason;
    }

    /**
     * <p>Constructor for GPCException.</p>
     *
     * @param reason one of the reason codes named on this type.
     * @param message the message to report.
     */
    public GPCException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    /**
     * The reason code.
     *
     * @return the reason.
     */
    public String getReason() {
        return this.reason;
    }
}
