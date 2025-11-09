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
    private final Deque<SourceLine> lines;
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
            List<SourceLine> sourceLines = new ArrayList<>(fileLines.size());
            for (int i = 0; i < fileLines.size(); i++) {
                String raw = fileLines.get(i);
                String withNewline = raw + EOL;
                SourceLocation location = new SourceLocation(filePath, i + 1, 1, raw);
                sourceLines.add(new SourceLine(withNewline, location));
            }
            TycoLexer lexer = new TycoLexer(context, sourceLines, filePath);
            lexer.process();
            context.cacheLexer(filePath, lexer);
            return lexer;
        } catch (IOException e) {
            throw new TycoParseException("Cannot read file: " + filePath, e);
        }
    }

    public TycoLexer(TycoContext context, List<SourceLine> lines, String path) {
        this.context = context;
        this.lines = new ArrayDeque<>(lines);
        this.path = path;
        this.numLines = lines.size();
    }

    public void process() {
        while (!lines.isEmpty()) {
            SourceLine lineEntry = popLineEntry();
            if (lineEntry == null) {
                break;
            }
            String line = lineEntry.getText();
            SourceLocation lineLocation = lineEntry.getLocation();

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
                        throw new TycoParseException("Duplicate struct defaults for " + entry.getKey(), lineLocation);
                    }
                    this.defaults.put(entry.getKey(), new HashMap<>(entry.getValue()));
                }
                continue;
            }

            Matcher globalMatcher = GLOBAL_SCHEMA_REGEX.matcher(line);
            if (globalMatcher.find()) {
                loadGlobal(lineEntry, globalMatcher);
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

            if (TycoUtils.stripComments(line, lineLocation).isEmpty()) {
                continue;
            }

            throw new TycoParseException("Malformatted config file", lineLocation);
        }
    }

    private void loadGlobal(SourceLine lineEntry, Matcher match) {
        String line = lineEntry.getText();
        String option = match.group(1);
        String typeName = match.group(2);
        String arrayFlag = match.group(3);
        String attrName = match.group(4);
        boolean isArray = "[]".equals(arrayFlag);
        boolean isNullable = "?".equals(option);

        SourceLine defaultSlice = lineEntry.sliceFrom(match.end()).trimLeadingWhitespace();
        if (defaultSlice.getText().isEmpty()) {
            throw new TycoParseException("Must provide a value when setting globals", defaultSlice.getLocation());
        }

        pushFront(defaultSlice);
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
            SourceLine peekEntry = peekLineEntry();
            if (peekEntry == null) {
                break;
            }
            String peek = peekEntry.getText();
            SourceLocation peekLocation = peekEntry.getLocation();
            String content = TycoUtils.stripComments(peek, peekLocation);
            if (content.isEmpty()) {
                popLineEntry();
                continue;
            }

            Matcher matcher = STRUCT_SCHEMA_REGEX.matcher(peek);
            if (!matcher.find()) {
                if (content.matches("^\\s+\\w+\\s+\\w+")) {
                    throw new TycoParseException("Schema attribute missing trailing colon: " + content, peekLocation);
                }
                break;
            }

            SourceLine lineEntry = popLineEntry();
            if (lineEntry == null) {
                break;
            }

            String option = matcher.group(1);
            String typeName = matcher.group(2);
            String arrayFlag = matcher.group(3);
            String attrName = matcher.group(4);

            if (struct.hasAttribute(attrName)) {
                throw new TycoParseException("Duplicate attribute " + attrName + " in " + struct.getTypeName(), lineEntry.getLocation());
            }

            boolean isArray = "[]".equals(arrayFlag);
            boolean isNullable = "?".equals(option);
            boolean isPrimary = "*".equals(option);

            struct.addAttribute(attrName, typeName, isPrimary, isNullable, isArray);

            SourceLine defaultSlice = lineEntry.sliceFrom(matcher.end()).trimLeadingWhitespace();
            if (!TycoUtils.stripComments(defaultSlice.getText(), defaultSlice.getLocation()).isEmpty()) {
                pushFront(defaultSlice);
                AttrResult attrResult = loadTycoAttr(List.of(EOL), List.of(), true, attrName);
                defaults.get(struct.getTypeName()).put(attrName, attrResult.attribute);
            }
        }
    }

    private void loadLocalDefaultsAndInstances(TycoStruct struct) {
        while (!lines.isEmpty()) {
            SourceLine peekEntry = peekLineEntry();
            if (peekEntry == null) {
                break;
            }
            String peek = peekEntry.getText();
            SourceLocation peekLocation = peekEntry.getLocation();
            String content = TycoUtils.stripComments(peek, peekLocation);
            if (content.isEmpty()) {
                popLineEntry();
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
                throw new TycoParseException("Cannot add schema attributes after initial construction", peekLocation);
            }

            Matcher defaultMatcher = STRUCT_DEFAULTS_REGEX.matcher(peek);
            if (defaultMatcher.find()) {
                SourceLine lineEntry = popLineEntry();
                if (lineEntry == null) {
                    break;
                }
                String attrName = defaultMatcher.group(1);
                if (!struct.hasAttribute(attrName)) {
                    throw new TycoParseException("Setting invalid default of " + attrName + " for " + struct.getTypeName(), lineEntry.getLocation());
                }
                SourceLine defaultSlice = lineEntry.sliceFrom(defaultMatcher.end()).trimLeadingWhitespace();
                if (!TycoUtils.stripComments(defaultSlice.getText(), defaultSlice.getLocation()).isEmpty()) {
                    pushFront(defaultSlice);
                    AttrResult attrResult = loadTycoAttr(List.of(EOL), List.of(), true, attrName);
                    defaults.get(struct.getTypeName()).put(attrName, attrResult.attribute);
                } else {
                    defaults.get(struct.getTypeName()).remove(attrName);
                }
                continue;
            }

            if (STRUCT_INSTANCE_REGEX.matcher(peek).find()) {
                SourceLine lineEntry = popLineEntry();
                if (lineEntry == null) {
                    break;
                }
                String line = lineEntry.getText();
                Matcher instanceMatcher = STRUCT_INSTANCE_REGEX.matcher(line);
                if (!instanceMatcher.find()) {
                    throw new TycoParseException("Invalid struct instance line: " + line, lineEntry.getLocation());
                }
                SourceLine remainder = lineEntry.sliceFrom(instanceMatcher.end()).trimLeadingWhitespace();
                pushFront(remainder);

                List<TycoAttribute> instArgs = new ArrayList<>();
                while (true) {
                    if (lines.isEmpty()) {
                        break;
                    }
                    SourceLine instEntry = peekLineEntry();
                    if (instEntry == null) {
                        break;
                    }
                    String instContent = TycoUtils.stripComments(instEntry.getText(), instEntry.getLocation());
                    if (instContent.isEmpty()) {
                        popLineEntry();
                        break;
                    }

                    if ("\\".equals(instContent)) {
                        popLineEntry();
                        if (!lines.isEmpty()) {
                            SourceLine nextEntry = peekLineEntry();
                            if (nextEntry != null) {
                                SourceLine trimmed = nextEntry.trimLeadingWhitespace();
                                if (trimmed != nextEntry) {
                                    replaceCurrentLine(trimmed);
                                }
                            }
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

        SourceLine currentEntry = peekLineEntry();
        if (currentEntry == null) {
            throw new TycoParseException("Syntax error: no content found");
        }
        SourceLine lstrippedEntry = currentEntry.trimLeadingWhitespace();
        if (lstrippedEntry != currentEntry) {
            replaceCurrentLine(lstrippedEntry);
            currentEntry = lstrippedEntry;
        }
        String current = currentEntry.getText();
        SourceLocation currentLocation = currentEntry.getLocation();

        Matcher colonMatch = IDENTIFIER_COLON_REGEX.matcher(current);
        if (colonMatch.find()) {
            if (attrName != null) {
                SourceLocation errorLocation = currentLocation.advance(colonMatch.start());
                throw new TycoParseException("Colon found inside content - wrap string in quotes: " + colonMatch.group(1), errorLocation);
            }
            attrName = colonMatch.group(1);
            SourceLine remainder = currentEntry.sliceFrom(colonMatch.end());
            replaceCurrentLine(remainder);
            return loadTycoAttrWithSets(goodDelim, badDelim, popEmptyLines, attrName);
        }

        TycoAttribute attr;
        String delim;
        currentEntry = peekLineEntry();
        if (currentEntry == null) {
            throw new TycoParseException("Unexpected empty line when parsing attribute");
        }
        current = currentEntry.getText();
        currentLocation = currentEntry.getLocation();
        if (current.isEmpty()) {
            throw new TycoParseException("Unexpected empty line when parsing attribute", currentLocation);
        }
        char ch = current.charAt(0);

        if (ch == '[') {
            replaceCurrentLine(currentEntry.sliceFrom(1));
            List<TycoAttribute> arrayContent = loadArray(']');
            attr = new TycoArray(context, arrayContent);
            attr.setLocation(currentLocation);
            delim = stripNextDelim(goodDelim);
        } else if (Character.isLetterOrDigit(ch) || ch == '_') {
            Matcher instMatcher = Pattern.compile("^(\\w+)\\(").matcher(current);
            if (instMatcher.find()) {
                String typeName = instMatcher.group(1);
                replaceCurrentLine(currentEntry.sliceFrom(instMatcher.end()));
                List<TycoAttribute> instArgs = loadArray(')');
                TycoStruct struct = context.getStruct(typeName);
                if (struct == null || !struct.getPrimaryKeys().isEmpty()) {
                    attr = new TycoReference(context, instArgs, typeName);
                } else {
                    Map<String, TycoAttribute> defaultKwargs = defaults.getOrDefault(typeName, new HashMap<>());
                    attr = struct.createInlineInstance(instArgs, defaultKwargs);
                }
                attr.setLocation(currentLocation);
                delim = stripNextDelim(goodDelim);
            } else {
                AttrResult next = stripNextAttrAndDelim(goodDelim, badDelim);
                attr = next.attribute;
                delim = next.delimiter;
            }
        } else if (ch == '"' || ch == '\'') {
            String triple = String.valueOf(ch).repeat(3);
            if (current.startsWith(triple)) {
                String tripleString = loadTripleString(triple, currentLocation);
                attr = new TycoValue(context, tripleString);
            } else {
                String singleString = loadSingleString(String.valueOf(ch), currentLocation);
                attr = new TycoValue(context, singleString);
            }
            attr.setLocation(currentLocation);
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
                throw new TycoParseException("Could not find " + closingChar, peekLineLocation());
            }

            SourceLine peekEntry = peekLineEntry();
            if (peekEntry == null) {
                throw new TycoParseException("Could not find " + closingChar, SourceLocation.unknown());
            }
            String peek = peekEntry.getText();
            SourceLocation peekLocation = peekEntry.getLocation();
            if (TycoUtils.stripComments(peek, peekLocation).isEmpty()) {
                popLineEntry();
                continue;
            }

            if (peek.startsWith(String.valueOf(closingChar))) {
                SourceLine remainder = peekEntry.sliceFrom(1);
                replaceCurrentLine(remainder);
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

    private String loadTripleString(String triple, SourceLocation startLocation) {
        boolean isLiteral = "'''".equals(triple);
        int start = 3;
        List<String> contents = new ArrayList<>();

        while (true) {
            if (lines.isEmpty()) {
                throw new TycoParseException("Unclosed triple quote", startLocation);
            }
            SourceLine lineEntry = popLineEntry();
            if (lineEntry == null) {
                throw new TycoParseException("Unclosed triple quote", startLocation);
            }
            String line = lineEntry.getText();
            int end = line.indexOf(triple, start);

            if (end != -1) {
                int endIdx = end + 3;
                String content = line.substring(0, endIdx);
                contents.add(content);

                SourceLine remainderEntry = lineEntry.sliceFrom(endIdx);
                String remainder = remainderEntry.getText();
                char tripleChar = triple.charAt(0);
                int consumed = 0;
                for (int i = 0; i < 2; i++) {
                    if (!remainder.isEmpty() && remainder.charAt(0) == tripleChar) {
                        int lastIdx = contents.size() - 1;
                        contents.set(lastIdx, contents.get(lastIdx) + tripleChar);
                        remainder = remainder.substring(1);
                        consumed++;
                    } else {
                        break;
                    }
                }
                if (consumed > 0) {
                    remainderEntry = remainderEntry.sliceFrom(consumed);
                }
                if (!remainderEntry.getText().isEmpty()) {
                    pushFront(remainderEntry);
                }
                break;
            } else {
                if (!isLiteral && line.endsWith("\\" + EOL)) {
                    int cut = line.length() - (1 + EOL.length());
                    if (cut < 0) {
                        cut = 0;
                    }
                    contents.add(line.substring(0, cut));
                    while (!lines.isEmpty()) {
                        SourceLine nextEntry = peekLineEntry();
                        if (nextEntry == null) {
                            break;
                        }
                        SourceLine trimmed = nextEntry.trimLeadingWhitespace();
                        if (trimmed != nextEntry) {
                            replaceCurrentLine(trimmed);
                            nextEntry = trimmed;
                        }
                        if (nextEntry.getText().isEmpty()) {
                            popLineEntry();
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
                throw new TycoParseException("Invalid characters found in literal multiline string: " + ch, startLocation);
            }
        }
        return finalContent.toString();
    }

    private String loadSingleString(String quote, SourceLocation startLocation) {
        boolean isLiteral = "'".equals(quote);
        int start = 1;
        SourceLine lineEntry = popLineEntry();
        if (lineEntry == null) {
            throw new TycoParseException("Unclosed single-line string for " + quote, startLocation);
        }
        String line = lineEntry.getText();

        while (true) {
            int end = line.indexOf(quote, start);
            if (end == -1) {
                throw new TycoParseException("Unclosed single-line string for " + quote + ": " + line, startLocation);
            }

            if (isLiteral || line.charAt(end - 1) != '\\') {
                String finalContent = line.substring(0, end + 1);
                for (int i = 0; i < finalContent.length(); i++) {
                    char ch = finalContent.charAt(i);
                    if (TycoUtils.ILLEGAL_STR_CHARS.contains(ch)) {
                        throw new TycoParseException("Invalid characters found in literal string: " + ch, startLocation);
                    }
                }
                SourceLine remainderEntry = lineEntry.sliceFrom(end + 1);
                if (!remainderEntry.getText().isEmpty()) {
                    pushFront(remainderEntry);
                }
                return finalContent;
            }

            start = end + 1;
        }
    }

    private AttrResult stripNextAttrAndDelim(Set<String> goodDelim, Set<String> badDelim) {
        SourceLine currentEntry = peekLineEntry();
        if (currentEntry == null) {
            throw new TycoParseException("Unexpected end of input");
        }
        String current = currentEntry.getText();
        SourceLocation baseLocation = currentEntry.getLocation();

        String searchSpace = current;
        int commentIdx = current.indexOf('#');
        if (commentIdx >= 0) {
            searchSpace = current.substring(0, commentIdx);
        }

        Set<String> allDelims = union(goodDelim, badDelim);
        int bestIndex = -1;
        String bestDelim = null;
        for (String delim : allDelims) {
            int idx = EOL.equals(delim) ? searchSpace.length() : searchSpace.indexOf(delim);
            if (idx >= 0 && (bestIndex == -1 || idx < bestIndex)) {
                bestIndex = idx;
                bestDelim = delim;
            }
        }

        if (bestDelim == null) {
            throw new TycoParseException("Should have found some delimiter " + allDelims + ": " + current, baseLocation);
        }
        if (badDelim.contains(bestDelim)) {
            throw new TycoParseException("Bad delimiter encountered: " + bestDelim, baseLocation.advance(bestIndex));
        }

        String rawText = searchSpace.substring(0, bestIndex);
        int leading = 0;
        while (leading < rawText.length() && Character.isWhitespace(rawText.charAt(leading))) {
            leading++;
        }
        int trailing = rawText.length();
        while (trailing > leading && Character.isWhitespace(rawText.charAt(trailing - 1))) {
            trailing--;
        }
        String text = rawText.substring(leading, trailing);
        SourceLocation valueLocation = baseLocation.advance(leading);

        TycoValue attr = new TycoValue(context, text);
        attr.setLocation(valueLocation);

        if (EOL.equals(bestDelim)) {
            lines.pollFirst();
        } else {
            int advance = bestIndex + bestDelim.length();
            if (advance > current.length()) {
                advance = current.length();
            }
            SourceLine remainder = currentEntry.sliceFrom(advance);
            replaceCurrentLine(remainder);
        }
        return new AttrResult(attr, bestDelim);
    }

    private String stripNextDelim(Set<String> goodDelim) {
        if (lines.isEmpty()) {
            throw new TycoParseException("Unexpected end of input looking for delimiters " + goodDelim);
        }
        SourceLine currentEntry = peekLineEntry();
        if (currentEntry == null) {
            throw new TycoParseException("Unexpected end of input looking for delimiters " + goodDelim);
        }
        String current = currentEntry.getText();
        SourceLocation location = currentEntry.getLocation();
        for (String delim : goodDelim) {
            if (current.startsWith(delim)) {
                SourceLine remainder = currentEntry.sliceFrom(delim.length());
                replaceCurrentLine(remainder);
                return delim;
            }
        }

        if (goodDelim.contains(EOL) && TycoUtils.stripComments(current, location).isEmpty()) {
            popLineEntry();
            return EOL;
        }

        throw new TycoParseException("Should have found next delimiter " + goodDelim + ": " + current, location);
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

    private SourceLine popLineEntry() {
        return lines.pollFirst();
    }

    private void pushFront(SourceLine line) {
        if (line != null) {
            lines.addFirst(line);
        }
    }

    private void replaceCurrentLine(SourceLine line) {
        lines.pollFirst();
        if (line != null) {
            lines.addFirst(line);
        }
    }

    private SourceLine peekLineEntry() {
        return lines.peekFirst();
    }

    private String peekLineText() {
        SourceLine entry = lines.peekFirst();
        return entry != null ? entry.getText() : null;
    }

    private SourceLocation peekLineLocation() {
        SourceLine entry = lines.peekFirst();
        return entry != null ? entry.getLocation() : null;
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
