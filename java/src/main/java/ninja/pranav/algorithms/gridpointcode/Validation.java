package ninja.pranav.algorithms.gridpointcode;

/**
 * <p>Validation class.</p>
 *
 * @author pranav.ninja
 * @version $Id: $Id
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
