package ca.pranavpatel.algo.gridpointcode;

/**
 * Provides static methods to validate geographic coordinates and Grid Point Codes.
 */
public class Validation {
    /**
     * Indicates whether the validation result is valid.
     */
    public final boolean IsValid;

    /**
     * Contains a message describing the validation result.
     */
    public final String Message;
    /**
     * <p>Constructor for Validation.</p>
     *
     * @param isValid a boolean.
     * @param message a {@link java.lang.String} object.
     */
    public Validation(boolean isValid, String message) {
        this.IsValid = isValid;
        this.Message = message;
    }
}
