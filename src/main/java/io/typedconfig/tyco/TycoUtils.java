package io.typedconfig.tyco;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TycoUtils {

    private static final Set<Character> ASCII_CTRL;
    static final Set<Character> ILLEGAL_STR_CHARS;
    static final Set<Character> ILLEGAL_STR_CHARS_MULTILINE;

    private static final Pattern BASIC_STR_ESCAPE_PATTERN;

    static {
        Set<Character> ascii = new HashSet<>();
        for (int i = 0; i < 32; i++) {
            ascii.add((char) i);
        }
        ascii.add((char) 127);
        ASCII_CTRL = Collections.unmodifiableSet(ascii);

        Set<Character> illegal = new HashSet<>(ASCII_CTRL);
        illegal.remove('\t');
        ILLEGAL_STR_CHARS = Collections.unmodifiableSet(illegal);

        Set<Character> multi = new HashSet<>(ASCII_CTRL);
        multi.remove('\r');
        multi.remove('\n');
        multi.remove('\t');
        ILLEGAL_STR_CHARS_MULTILINE = Collections.unmodifiableSet(multi);

        String[] replacements = {"\\\\", "\\\"", "\\b", "\\t", "\\n", "\\f", "\\r"};
        StringBuilder sb = new StringBuilder();
        sb.append("(?:");
        for (int i = 0; i < replacements.length; i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(Pattern.quote(replacements[i]));
        }
        sb.append(')');
        BASIC_STR_ESCAPE_PATTERN = Pattern.compile(sb.toString());
    }

    private TycoUtils() {
    }

    static String stripComments(String line) {
        if (line == null) {
            return "";
        }
        int idx = line.indexOf('#');
        if (idx < 0) {
            return rstrip(line);
        }

        String content = line.substring(0, idx);
        String comment = line.substring(idx + 1).replaceAll("(\\r?\\n)$", "");
        for (int i = 0; i < comment.length(); i++) {
            char ch = comment.charAt(i);
            if (ILLEGAL_STR_CHARS.contains(ch)) {
                throw new TycoParseException("Invalid characters in comments: " + ch);
            }
        }
        return rstrip(content);
    }

    static boolean isWhitespace(String content) {
        return content == null || content.trim().isEmpty();
    }

    private static String rstrip(String content) {
        int end = content.length();
        while (end > 0) {
            char ch = content.charAt(end - 1);
            if (ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n') {
                end--;
            } else {
                break;
            }
        }
        return content.substring(0, end);
    }

    static String subEscapeSequences(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuffer sb = new StringBuffer();
        Matcher matcher = BASIC_STR_ESCAPE_PATTERN.matcher(input);
        while (matcher.find()) {
            String match = matcher.group();
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replaceBasicEscape(match)));
        }
        matcher.appendTail(sb);
        String escaped = sb.toString();

        Matcher unicodeMatcher = Pattern.compile("\\\\u([0-9a-fA-F]{4})|\\\\U([0-9a-fA-F]{8})").matcher(escaped);
        StringBuffer unicodeBuffer = new StringBuffer();
        while (unicodeMatcher.find()) {
            String hex = unicodeMatcher.group(1) != null ? unicodeMatcher.group(1) : unicodeMatcher.group(2);
            char replacement = (char) Integer.parseInt(hex, 16);
            unicodeMatcher.appendReplacement(unicodeBuffer, Matcher.quoteReplacement(Character.toString(replacement)));
        }
        unicodeMatcher.appendTail(unicodeBuffer);
        escaped = unicodeBuffer.toString();

        // remove escaped-newline sequences (backslash newline with surrounding whitespace)
        return escaped.replaceAll("\\\\\\s*\\r?\\n\\s*", "");
    }

    private static String replaceBasicEscape(String token) {
        switch (token) {
            case "\\\\":
                return "\\";
            case "\\\"":
                return "\"";
            case "\\b":
                return "\b";
            case "\\t":
                return "\t";
            case "\\n":
                return "\n";
            case "\\f":
                return "\f";
            case "\\r":
                return "\r";
            default:
                return token;
        }
    }

    static String normalizeTimeLiteral(String value) {
        String trimmed = value.trim();
        Matcher matcher = Pattern.compile("^(\\d{2}:\\d{2}:\\d{2})(\\.(\\d+))?$").matcher(trimmed);
        if (!matcher.matches()) {
            return trimmed;
        }
        String base = matcher.group(1);
        String fraction = matcher.group(3);
        if (fraction == null) {
            return base;
        }
        String padded = padFraction(fraction);
        return base + "." + padded;
    }

    static String normalizeDateTimeLiteral(String value) {
        String normalized = value.trim();
        int spaceIdx = normalized.indexOf(' ');
        if (spaceIdx >= 0) {
            normalized = normalized.substring(0, spaceIdx) + "T" + normalized.substring(spaceIdx + 1);
        }

        String tz = "";
        if (normalized.endsWith("Z")) {
            tz = "+00:00";
            normalized = normalized.substring(0, normalized.length() - 1);
        } else {
            Matcher tzMatcher = Pattern.compile("([+-]\\d{2}:\\d{2})$").matcher(normalized);
            if (tzMatcher.find()) {
                tz = tzMatcher.group(1);
                normalized = normalized.substring(0, normalized.length() - tz.length());
            }
        }

        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex >= 0) {
            String fraction = normalized.substring(dotIndex + 1);
            if (fraction.matches("\\d+")) {
                String padded = padFraction(fraction);
                normalized = normalized.substring(0, dotIndex) + "." + padded;
            }
        }

        return normalized + tz;
    }

    private static String padFraction(String fraction) {
        if (fraction.length() >= 6) {
            return fraction.substring(0, 6);
        }
        StringBuilder builder = new StringBuilder(fraction);
        while (builder.length() < 6) {
            builder.append('0');
        }
        return builder.toString();
    }
}
