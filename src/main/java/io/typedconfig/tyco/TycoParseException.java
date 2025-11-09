package io.typedconfig.tyco;

/**
 * Exception thrown when there are errors parsing Tyco configuration files
 */
public class TycoParseException extends RuntimeException {
    private final SourceLocation location;

    private static String formatMessage(String message, SourceLocation location) {
        if (location == null) {
            return message;
        }
        StringBuilder builder = new StringBuilder();
        String prefix = location.toString();
        if (!prefix.isEmpty()) {
            builder.append(prefix).append(" - ");
        }
        builder.append(message);
        if (location.getLineText() != null && !location.getLineText().isEmpty()) {
            builder.append(System.lineSeparator())
                .append("    ")
                .append(location.getLineText());
        }
        return builder.toString();
    }

    public TycoParseException(String message, SourceLocation location) {
        super(formatMessage(message, location));
        this.location = location;
    }

    public TycoParseException(String message, int lineNumber, String line) {
        this(message, new SourceLocation(null, lineNumber, 1, line));
    }

    public TycoParseException(String message) {
        this(message, (SourceLocation) null);
    }

    public TycoParseException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public TycoParseException(String message, Throwable cause, SourceLocation location) {
        super(formatMessage(message, location), cause);
        this.location = location;
    }

    public SourceLocation getLocation() {
        return location;
    }

    public int getLineNumber() {
        return location != null ? location.getLine() : -1;
    }

    public String getLine() {
        return location != null ? location.getLineText() : null;
    }
}
