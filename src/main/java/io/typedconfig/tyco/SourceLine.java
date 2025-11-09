package io.typedconfig.tyco;

public final class SourceLine {
    private final String text;
    private final SourceLocation location;

    public SourceLine(String text, SourceLocation location) {
        this.text = text;
        this.location = location;
    }

    public String getText() {
        return text;
    }

    public SourceLocation getLocation() {
        return location;
    }

    public SourceLine sliceFrom(int start) {
        return slice(start, text.length());
    }

    public SourceLine slice(int start, int end) {
        int safeStart = Math.max(0, Math.min(start, text.length()));
        int safeEnd = Math.max(safeStart, Math.min(end, text.length()));
        return new SourceLine(text.substring(safeStart, safeEnd), location.advance(safeStart));
    }

    public SourceLine trimLeadingWhitespace() {
        int idx = 0;
        while (idx < text.length()) {
            char ch = text.charAt(idx);
            if (ch == ' ' || ch == '\t') {
                idx++;
            } else {
                break;
            }
        }
        if (idx == 0) {
            return this;
        }
        return sliceFrom(idx);
    }
}
