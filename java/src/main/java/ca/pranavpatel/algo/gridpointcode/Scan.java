package ca.pranavpatel.algo.gridpointcode;

/**
 * A cursor over degrees-minutes-seconds text. Section 19.1.
 *
 * <p>Small enough to keep the grammar readable, and deliberately strict: every
 * numeric piece carries its unit marker, so no accepted string has two readings.
 */
final class Scan {
    private static final String WHITESPACE = " \t\n" + (char)0x0B + "\f\r";

    private final String text;
    private int at;

    Scan(String text) {
        if (text == null) {
            throw new GPCException("GPC_NULL");
        }
        this.text = text;
        this.at = 0;
    }

    boolean Done() {
        return at >= text.length();
    }

    /**
     * The character under the cursor, or the null character at the end.
     *
     * <p>The end of the text has to be a value no membership test accepts. In
     * two of the other ports it is the empty string, which is a substring of
     * every string and therefore has to be tested for separately; here the null
     * character is in none of the marker sets, so it falls out.
     */
    char Peek() {
        return Done() ? '\0' : text.charAt(at);
    }

    char Take() {
        at++;
        return text.charAt(at - 1);
    }

    void Spaces() {
        while (!Done() && WHITESPACE.indexOf(text.charAt(at)) >= 0) {
            at++;
        }
    }

    private boolean Digit() {
        return !Done() && text.charAt(at) >= '0' && text.charAt(at) <= '9';
    }

    private void Marker(String choices) {
        Spaces();
        char character = Peek();
        if (character == '\0' || choices.indexOf(character) < 0) {
            throw new GPCException("GPC_DMS");
        }
        Take();
    }

    private long Digits() {
        int start = at;
        while (Digit()) {
            at++;
        }
        if (at == start) {
            throw new GPCException("GPC_DMS");
        }
        return Long.parseLong(text.substring(start, at));
    }

    private double Number() {
        int start = at;
        while (Digit()) {
            at++;
        }
        if (!Done() && text.charAt(at) == '.') {
            at++;
            while (Digit()) {
                at++;
            }
        }
        String body = text.substring(start, at);
        if (body.isEmpty() || body.equals(".")) {
            throw new GPCException("GPC_DMS");
        }
        return Double.parseDouble(body);
    }

    /**
     * One axis: an optional sign, degrees and their marker, then optional
     * minutes and seconds with theirs, then an optional hemisphere letter.
     *
     * @param isLatitude True for the first axis, false for the second.
     * @return the value in decimal degrees.
     */
    double Axis(boolean isLatitude) {
        Spaces();
        boolean signed = Peek() == '+' || Peek() == '-';
        double sign = signed && Take() == '-' ? -1.0 : 1.0;

        Spaces();
        long degrees = Digits();
        Marker(GPC.DEGREE_SIGN + "dD");

        long minutes = 0;
        double seconds = 0.0;
        int save = at;
        Spaces();
        if (Digit()) {
            minutes = Digits();
            Marker("'mM");
            if (minutes >= 60) {
                throw new GPCException("GPC_DMS");
            }
            save = at;
            Spaces();
            if (Digit()) {
                seconds = Number();
                Marker("\"sS");
                if (seconds >= 60.0) {
                    throw new GPCException("GPC_DMS");
                }
            } else {
                at = save;
            }
        } else {
            at = save;
        }

        Spaces();
        char letter = Character.toUpperCase(Peek());
        if (letter == 'N' || letter == 'S' || letter == 'E' || letter == 'W') {
            Take();
            if (signed) {                       // a sign and a hemisphere both
                throw new GPCException("GPC_DMS");
            }
            if ((letter == 'N' || letter == 'S') != isLatitude) {
                throw new GPCException("GPC_DMS");   // the wrong axis
            }
            if (letter == 'S' || letter == 'W') {
                sign = -1.0;
            }
        }

        return sign * (degrees + (minutes + (seconds / 60.0)) / 60.0);
    }
}
