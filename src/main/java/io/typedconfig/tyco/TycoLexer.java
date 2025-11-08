package io.typedconfig.tyco;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lexer/loader responsible for turning Tyco source into structured attributes.
 * Closely follows the Python reference implementation.
 */
public class TycoLexer {

    private static final String IRE = "((?!\\d)\\w+)";
    private static final Pattern INCLUDE_REGEX = Pattern.compile("^#include\\s+(\\S.*)$");
    private static final Pattern GLOBAL_SCHEMA_REGEX = Pattern.compile("^([?])?" + IRE + "(\\[\\])?\\s+" + IRE + "\\s*:");
    private static final Pattern STRUCT_BLOCK_REGEX = Pattern.compile("^" + IRE + ":");
    private static final Pattern STRUCT_SCHEMA_REGEX = Pattern.compile("^\\s+([*?])?" + IRE + "(\\[\\])?\\s+" + IRE + "\\s*:");
    private static final Pattern STRUCT_DEFAULTS_REGEX = Pattern.compile("^\\s+" + IRE + "\\s*:");
    private static final Pattern STRUCT_INSTANCE_REGEX = Pattern.compile("^\\s*-");
    private static final Pattern IDENTIFIER_COLON_REGEX = Pattern.compile("^" + IRE + "\\s*:\\s*");
    private static final String EOL = "\n";

    private final TycoContext context;
    private final Deque<String> lines;
    private final String path;
    private final int numLines;
    private final Map<String, Map<String, TycoAttribute>> defaults = new HashMap<>();

    public static TycoLexer fromPath(TycoContext context, String filePath) {
        TycoLexer cached = context.getCachedLexer(filePath);
        if (cached != null) {
            return cached;
        }
        Path path = Path.of(filePath);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new TycoParseException("Can only load path if it is a regular file: " + filePath);
        }
        try {
            List<String> fileLines = Files.readAllLines(path);
            List<String> withNewlines = new ArrayList<>(fileLines.size());
            for (String line : fileLines) {
                withNewlines.add(line + EOL);
            }
            TycoLexer lexer = new TycoLexer(context, withNewlines, filePath);
            lexer.process();
            context.cacheLexer(filePath, lexer);
            return lexer;
        } catch (IOException e) {
            throw new TycoParseException("Cannot read file: " + filePath, e);
        }
    }

    public TycoLexer(TycoContext context, List<String> lines, String path) {
        this.context = context;
        this.lines = new ArrayDeque<>(lines);
        this.path = path;
        this.numLines = lines.size();
    }

    public void process() {
        while (!lines.isEmpty()) {
            String line = popLine();
            if (line == null) {
                break;
            }

            Matcher includeMatcher = INCLUDE_REGEX.matcher(rstrip(line));
            if (includeMatcher.matches()) {
                String includePath = includeMatcher.group(1).trim();
                if (!Path.of(includePath).isAbsolute()) {
                    Path relDir = path != null ? Path.of(path).getParent() : Path.of(System.getProperty("user.dir"));
                    includePath = relDir.resolve(includePath).normalize().toString();
                }
                TycoLexer lexer = TycoLexer.fromPath(context, includePath);
                lexer.process();
                for (Map.Entry<String, Map<String, TycoAttribute>> entry : lexer.defaults.entrySet()) {
                    if (this.defaults.containsKey(entry.getKey())) {
                        throw new TycoParseException("Duplicate struct defaults for " + entry.getKey());
                    }
                    this.defaults.put(entry.getKey(), new HashMap<>(entry.getValue()));
                }
                continue;
            }

            Matcher globalMatcher = GLOBAL_SCHEMA_REGEX.matcher(line);
            if (globalMatcher.find()) {
                loadGlobal(line, globalMatcher);
                continue;
            }

            Matcher structMatcher = STRUCT_BLOCK_REGEX.matcher(line);
            if (structMatcher.find()) {
                String typeName = structMatcher.group(1);
                TycoStruct struct = context.getStruct(typeName);
                if (struct == null) {
                    struct = context.addStruct(typeName);
                    loadSchema(struct);
                }
                loadLocalDefaultsAndInstances(struct);
                continue;
            }

            if (TycoUtils.stripComments(line).isEmpty()) {
                continue;
            }

            throw new TycoParseException("Malformatted config file: " + line);
        }
    }

    private void loadGlobal(String line, Matcher match) {
        String option = match.group(1);
        String typeName = match.group(2);
        String arrayFlag = match.group(3);
        String attrName = match.group(4);
        boolean isArray = "[]".equals(arrayFlag);
        boolean isNullable = "?".equals(option);

        String defaultText = line.substring(match.end()).stripLeading();
        if (defaultText.isEmpty()) {
            throw new TycoParseException("Must provide a value when setting globals");
        }

        pushFront(defaultText);
        AttrResult result = loadTycoAttr(List.of(EOL), List.of(), true, attrName);
        result.attribute.applySchemaInfo(typeName, attrName, isNullable, isArray);
        context.setGlobalAttr(attrName, result.attribute);
    }

    private void loadSchema(TycoStruct struct) {
        if (defaults.containsKey(struct.getTypeName())) {
            throw new TycoParseException("Duplicate defaults for struct " + struct.getTypeName());
        }
        defaults.put(struct.getTypeName(), new HashMap<>());

        while (!lines.isEmpty()) {
            String peek = lines.peekFirst();
            if (peek == null) {
                break;
            }
            String content = TycoUtils.stripComments(peek);
            if (content.isEmpty()) {
                popLine();
                continue;
            }

            Matcher matcher = STRUCT_SCHEMA_REGEX.matcher(peek);
            if (!matcher.find()) {
                if (content.matches("^\\s+\\w+\\s+\\w+")) {
                    throw new TycoParseException("Schema attribute missing trailing colon: " + content);
                }
                break;
            }

            String line = popLine();
            String option = matcher.group(1);
            String typeName = matcher.group(2);
            String arrayFlag = matcher.group(3);
            String attrName = matcher.group(4);

            if (struct.hasAttribute(attrName)) {
                throw new TycoParseException("Duplicate attribute " + attrName + " in " + struct.getTypeName());
            }

            boolean isArray = "[]".equals(arrayFlag);
            boolean isNullable = "?".equals(option);
            boolean isPrimary = "*".equals(option);

            struct.addAttribute(attrName, typeName, isPrimary, isNullable, isArray);

            String defaultText = line.substring(matcher.end()).stripLeading();
            if (!TycoUtils.stripComments(defaultText).isEmpty()) {
                pushFront(defaultText);
                AttrResult attrResult = loadTycoAttr(List.of(EOL), List.of(), true, attrName);
                defaults.get(struct.getTypeName()).put(attrName, attrResult.attribute);
            }
        }
    }

    private void loadLocalDefaultsAndInstances(TycoStruct struct) {
        while (!lines.isEmpty()) {
            String peek = lines.peekFirst();
            if (peek == null) {
                break;
            }
            String content = TycoUtils.stripComments(peek);
            if (content.isEmpty()) {
                popLine();
                continue;
            }
            if (!peek.startsWith(" ") && !peek.startsWith("\t")) {
                if (!STRUCT_INSTANCE_REGEX.matcher(peek).find()) {
                    break;
                }
            }
            if (peek.startsWith("#include ")) {
                break;
            }

            if (STRUCT_SCHEMA_REGEX.matcher(peek).find()) {
                throw new TycoParseException("Cannot add schema attributes after initial construction");
            }

            Matcher defaultMatcher = STRUCT_DEFAULTS_REGEX.matcher(peek);
            if (defaultMatcher.find()) {
                popLine();
                String attrName = defaultMatcher.group(1);
                if (!struct.hasAttribute(attrName)) {
                    throw new TycoParseException("Setting invalid default of " + attrName + " for " + struct.getTypeName());
                }
                String defaultText = peek.substring(defaultMatcher.end()).stripLeading();
                if (!TycoUtils.stripComments(defaultText).isEmpty()) {
                    pushFront(defaultText);
                    AttrResult attrResult = loadTycoAttr(List.of(EOL), List.of(), true, attrName);
                    defaults.get(struct.getTypeName()).put(attrName, attrResult.attribute);
                } else {
                    defaults.get(struct.getTypeName()).remove(attrName);
                }
                continue;
            }

            if (STRUCT_INSTANCE_REGEX.matcher(peek).find()) {
                String line = popLine();
                Matcher instanceMatcher = STRUCT_INSTANCE_REGEX.matcher(line);
                if (!instanceMatcher.find()) {
                    throw new TycoParseException("Invalid struct instance line: " + line);
                }
                String remainder = line.substring(instanceMatcher.end()).stripLeading();
                pushFront(remainder);

                List<TycoAttribute> instArgs = new ArrayList<>();
                while (true) {
                    if (lines.isEmpty()) {
                        break;
                    }
                    String instContent = TycoUtils.stripComments(lines.peekFirst());
                    if (instContent.isEmpty()) {
                        popLine();
                        break;
                    }

                    if ("\\".equals(instContent)) {
                        popLine();
                        if (!lines.isEmpty()) {
                            String next = lines.peekFirst();
                            String trimmed = lstrip(next);
                            replaceCurrentLine(trimmed);
                        }
                        continue;
                    }

                    AttrResult attrResult = loadTycoAttr(List.of(",", EOL), List.of(), false, null);
                    instArgs.add(attrResult.attribute);
                    if (EOL.equals(attrResult.delimiter)) {
                        break;
                    }
                }

                struct.createInstance(instArgs, defaults.get(struct.getTypeName()));
                continue;
            }

            break;
        }
    }

    private AttrResult loadTycoAttr(Collection<String> goodDelim, Collection<String> badDelim,
                                    boolean popEmptyLines, String attrName) {
        Set<String> good = new HashSet<>(goodDelim);
        Set<String> bad = new HashSet<>(badDelim);
        bad.add("(");
        bad.add(")");
        bad.add("[");
        bad.add("]");
        bad.add(",");
        bad.removeAll(good);
        return loadTycoAttrWithSets(good, bad, popEmptyLines, attrName);
    }

    private AttrResult loadTycoAttrWithSets(Set<String> goodDelim, Set<String> badDelim,
                                            boolean popEmptyLines, String attrName) {
        if (lines.isEmpty()) {
            throw new TycoParseException("Syntax error: no content found");
        }

        String current = lines.peekFirst();
        String lstripped = lstrip(current);
        if (!lstripped.equals(current)) {
            replaceCurrentLine(lstripped);
            current = lstripped;
        }
        Matcher colonMatch = IDENTIFIER_COLON_REGEX.matcher(current);
        if (colonMatch.find()) {
            if (attrName != null) {
                throw new TycoParseException("Colon found inside content - wrap string in quotes: " + colonMatch.group(1));
            }
            attrName = colonMatch.group(1);
            String remainder = current.substring(colonMatch.end());
            replaceCurrentLine(remainder);
            return loadTycoAttrWithSets(goodDelim, badDelim, popEmptyLines, attrName);
        }

        TycoAttribute attr;
        String delim;
        current = lines.peekFirst();
        if (current == null || current.isEmpty()) {
            throw new TycoParseException("Unexpected empty line when parsing attribute");
        }
        char ch = current.charAt(0);

        if (ch == '[') {
            replaceCurrentLine(current.substring(1));
            List<TycoAttribute> arrayContent = loadArray(']');
            attr = new TycoArray(context, arrayContent);
            delim = stripNextDelim(goodDelim);
        } else if (Character.isLetterOrDigit(ch) || ch == '_') {
            Matcher instMatcher = Pattern.compile("^(\\w+)\\(").matcher(current);
            if (instMatcher.find()) {
                String typeName = instMatcher.group(1);
                replaceCurrentLine(current.substring(instMatcher.end()));
                List<TycoAttribute> instArgs = loadArray(')');
                TycoStruct struct = context.getStruct(typeName);
                if (struct == null || !struct.getPrimaryKeys().isEmpty()) {
                    attr = new TycoReference(context, instArgs, typeName);
                } else {
                    Map<String, TycoAttribute> defaultKwargs = defaults.getOrDefault(typeName, new HashMap<>());
                    attr = struct.createInlineInstance(instArgs, defaultKwargs);
                }
                delim = stripNextDelim(goodDelim);
            } else {
                AttrResult next = stripNextAttrAndDelim(goodDelim, badDelim);
                attr = next.attribute;
                delim = next.delimiter;
            }
        } else if (ch == '"' || ch == '\'') {
            String triple = String.valueOf(ch).repeat(3);
            if (current.startsWith(triple)) {
                String tripleString = loadTripleString(triple);
                attr = new TycoValue(context, tripleString);
            } else {
                String singleString = loadSingleString(String.valueOf(ch));
                attr = new TycoValue(context, singleString);
            }
            delim = stripNextDelim(goodDelim);
        } else {
            AttrResult next = stripNextAttrAndDelim(goodDelim, badDelim);
            attr = next.attribute;
            delim = next.delimiter;
        }

        attr.applySchemaInfo(null, attrName, null, null);
        return new AttrResult(attr, delim);
    }

    private List<TycoAttribute> loadArray(char closingChar) {
        List<TycoAttribute> array = new ArrayList<>();
        List<String> goodDelims = List.of(String.valueOf(closingChar), ",");
        List<String> badDelims = closingChar == ']' ? List.of(")") : List.of("]");

        while (true) {
            if (lines.isEmpty()) {
                throw new TycoParseException("Could not find " + closingChar);
            }

            String peek = lines.peekFirst();
            if (TycoUtils.stripComments(peek).isEmpty()) {
                popLine();
                continue;
            }

            if (peek.startsWith(String.valueOf(closingChar))) {
                replaceCurrentLine(peek.substring(1));
                break;
            }

            AttrResult attrResult = loadTycoAttr(goodDelims, badDelims, true, null);
            array.add(attrResult.attribute);
            if (String.valueOf(closingChar).equals(attrResult.delimiter)) {
                break;
            }
        }

        return array;
    }

    private String loadTripleString(String triple) {
        boolean isLiteral = "'''".equals(triple);
        int start = 3;
        List<String> contents = new ArrayList<>();

        while (true) {
            if (lines.isEmpty()) {
                throw new TycoParseException("Unclosed triple quote");
            }
            String line = popLine();
            int end = line.indexOf(triple, start);

            if (end != -1) {
                int endIdx = end + 3;
                String content = line.substring(0, endIdx);
                String remainder = line.substring(endIdx);
                contents.add(content);

                char tripleChar = triple.charAt(0);
                for (int i = 0; i < 2; i++) {
                    if (!remainder.isEmpty() && remainder.charAt(0) == tripleChar) {
                        int lastIdx = contents.size() - 1;
                        contents.set(lastIdx, contents.get(lastIdx) + tripleChar);
                        remainder = remainder.substring(1);
                    } else {
                        break;
                    }
                }

                pushFront(remainder);
                break;
            } else {
                if (!isLiteral && line.endsWith("\\" + EOL)) {
                    int cut = line.length() - (1 + EOL.length());
                    if (cut < 0) {
                        cut = 0;
                    }
                    contents.add(line.substring(0, cut));
                    while (!lines.isEmpty()) {
                        String next = lines.peekFirst();
                        String trimmed = lstrip(next);
                        replaceCurrentLine(trimmed);
                        if (trimmed.isEmpty()) {
                            popLine();
                        } else {
                            break;
                        }
                    }
                } else {
                    contents.add(line);
                }
            }

            start = 0;
        }

        StringBuilder finalContent = new StringBuilder();
        for (String part : contents) {
            finalContent.append(part);
        }
        for (int i = 0; i < finalContent.length(); i++) {
            char ch = finalContent.charAt(i);
            if (TycoUtils.ILLEGAL_STR_CHARS_MULTILINE.contains(ch)) {
                throw new TycoParseException("Invalid characters found in literal multiline string: " + ch);
            }
        }
        return finalContent.toString();
    }

    private String loadSingleString(String quote) {
        boolean isLiteral = "'".equals(quote);
        int start = 1;
        String line = popLine();

        while (true) {
            int end = line.indexOf(quote, start);
            if (end == -1) {
                throw new TycoParseException("Unclosed single-line string for " + quote + ": " + line);
            }

            if (isLiteral || line.charAt(end - 1) != '\\') {
                String finalContent = line.substring(0, end + 1);
                String remainder = line.substring(end + 1);
                for (int i = 0; i < finalContent.length(); i++) {
                    char ch = finalContent.charAt(i);
                    if (TycoUtils.ILLEGAL_STR_CHARS.contains(ch)) {
                        throw new TycoParseException("Invalid characters found in literal string: " + ch);
                    }
                }
                pushFront(remainder);
                return finalContent;
            }

            start = end + 1;
        }
    }

    private AttrResult stripNextAttrAndDelim(Set<String> goodDelim, Set<String> badDelim) {
        String current = lines.peekFirst();
        if (current == null) {
            throw new TycoParseException("Unexpected end of input");
        }

        String searchSpace = current;
        int commentIdx = current.indexOf('#');
        if (commentIdx >= 0) {
            searchSpace = current.substring(0, commentIdx);
        }

        Set<String> allDelims = union(goodDelim, badDelim);
        int bestIndex = -1;
        String bestDelim = null;
        for (String delim : allDelims) {
            int idx;
            if (EOL.equals(delim)) {
                idx = searchSpace.length();
            } else {
                idx = searchSpace.indexOf(delim);
            }
            if (idx >= 0 && (bestIndex == -1 || idx < bestIndex)) {
                bestIndex = idx;
                bestDelim = delim;
            }
        }

        if (bestDelim == null) {
            throw new TycoParseException("Should have found some delimiter " + allDelims + ": " + current);
        }
        if (badDelim.contains(bestDelim)) {
            throw new TycoParseException("Bad delimiter encountered: " + bestDelim);
        }

        String text = searchSpace.substring(0, bestIndex).trim();
        TycoValue attr = new TycoValue(context, text);
        if (EOL.equals(bestDelim)) {
            lines.pollFirst();
        } else {
            int advance = bestIndex + bestDelim.length();
            if (advance > current.length()) {
                advance = current.length();
            }
            replaceCurrentLine(current.substring(advance));
        }
        return new AttrResult(attr, bestDelim);
    }

    private String stripNextDelim(Set<String> goodDelim) {
        if (lines.isEmpty()) {
            throw new TycoParseException("Unexpected end of input looking for delimiters " + goodDelim);
        }
        String current = lines.peekFirst();
        for (String delim : goodDelim) {
            if (current.startsWith(delim)) {
                replaceCurrentLine(current.substring(delim.length()));
                return delim;
            }
        }

        if (goodDelim.contains(EOL) && TycoUtils.stripComments(current).isEmpty()) {
            popLine();
            return EOL;
        }

        throw new TycoParseException("Should have found next delimiter " + goodDelim + ": " + current);
    }

    private static String lstrip(String value) {
        int idx = 0;
        while (idx < value.length() && (value.charAt(idx) == ' ' || value.charAt(idx) == '\t')) {
            idx++;
        }
        return value.substring(idx);
    }

    private static String rstrip(String value) {
        int end = value.length();
        while (end > 0) {
            char ch = value.charAt(end - 1);
            if (ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n') {
                end--;
            } else {
                break;
            }
        }
        return value.substring(0, end);
    }

    private String popLine() {
        return lines.pollFirst();
    }

    private void pushFront(String line) {
        if (line != null) {
            lines.addFirst(line);
        }
    }

    private void replaceCurrentLine(String line) {
        lines.pollFirst();
        lines.addFirst(line);
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> result = new HashSet<>(a);
        result.addAll(b);
        return result;
    }

    private static final class AttrResult {
        final TycoAttribute attribute;
        final String delimiter;

        AttrResult(TycoAttribute attribute, String delimiter) {
            this.attribute = Objects.requireNonNull(attribute);
            this.delimiter = delimiter;
        }
    }
}
