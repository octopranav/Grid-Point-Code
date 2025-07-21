package ca.pranavpatel.algo.gridpointcode;

/**
 * Provides static methods to validate geographic coordinates and Grid Point Codes.
 */
public class Validation {
    public final boolean IsValid;
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
