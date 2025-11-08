package io.typedconfig.tyco;

/**
 * Exception thrown when there are errors parsing Tyco configuration files
 */
public class TycoParseException extends RuntimeException {
    private final int lineNumber;
    private final String line;

    /**
     * Create a parse exception with line information
     * @param message Error message
     * @param lineNumber Line number where error occurred (1-based)
     * @param line The line content that caused the error
     */
    public TycoParseException(String message, int lineNumber, String line) {
        super(String.format("%s at line %d: %s", message, lineNumber, line));
        this.lineNumber = lineNumber;
        this.line = line;
    }

    /**
     * Create a parse exception with just a message
     * @param message Error message
     */
    public TycoParseException(String message) {
        super(message);
        this.lineNumber = -1;
        this.line = null;
    }
    
    /**
     * Create a parse exception with message and cause
     * @param message Error message
     * @param cause Underlying exception
     */
    public TycoParseException(String message, Throwable cause) {
        super(message, cause);
        this.lineNumber = -1;
        this.line = null;
    }



    public int getLineNumber() {
        return lineNumber;
    }

    public String getLine() {
        return line;
    }
}