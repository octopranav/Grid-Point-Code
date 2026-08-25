package ca.pranavpatel.algo.gridpointcode;

/**
 * What a string turns out to be once it has been normalised.
 *
 * <p>No encoded code begins with {@code X}, so that space is reserved rather
 * than wasted. A reserved code is well formed and names no cell; it is not a
 * typing error, and the two are kept apart from the first release because a
 * caller that cannot tell them apart today cannot be taught the difference
 * tomorrow.
 */
public enum CodeClass {
    /** Not a code: empty, the wrong length, outside the alphabet, or a check that does not hold. */
    INVALID,

    /** Well formed, begins with X, names no cell. Reserved for a future version of the format. */
    RESERVED,

    /** A code that names a cell of the grid. */
    GEOMETRIC
}
