package io.typedconfig.tyco;

import java.util.Objects;

public final class SourceLocation {
    private final String source;
    private final int line;
    private final int column;
    private final String lineText;

    public SourceLocation(String source, int line, int column, String lineText) {
        this.source = source;
        this.line = Math.max(line, 1);
        this.column = Math.max(column, 1);
        this.lineText = lineText;
    }

    public String getSource() {
        return source;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public String getLineText() {
        return lineText;
    }

    public SourceLocation advance(int columns) {
        return new SourceLocation(source, line, Math.max(1, column + columns), lineText);
    }

    public static SourceLocation unknown() {
        return new SourceLocation(null, -1, -1, null);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (source != null && !source.isEmpty()) {
            builder.append(source);
        }
        if (line > 0) {
            if (builder.length() > 0) {
                builder.append(':');
            }
            builder.append(line);
            if (column > 0) {
                builder.append(':').append(column);
            }
        }
        return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SourceLocation that = (SourceLocation) o;
        return line == that.line && column == that.column &&
            Objects.equals(source, that.source) &&
            Objects.equals(lineText, that.lineText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, line, column, lineText);
    }
}
