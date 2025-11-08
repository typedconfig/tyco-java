package io.typedconfig.tyco;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Entry point for parsing Tyco configuration files.
 */
public final class TycoParser {

    private TycoParser() {
        // utility
    }

    /**
     * Load Tyco configuration from a file or directory.
     *
     * @param path path to a .tyco file or directory containing .tyco files
     * @return parsed representation as nested Maps/Lists
     */
    public static Map<String, Object> load(String path) {
        TycoContext context = new TycoContext();
        File file = new File(path);
        List<String> paths = new ArrayList<>();

        try {
            if (file.isDirectory()) {
                try (Stream<Path> stream = Files.walk(Paths.get(path))) {
                    stream.filter(p -> p.toString().endsWith(".tyco"))
                          .sorted(Comparator.naturalOrder())
                          .forEach(p -> paths.add(p.toString()));
                }
            } else {
                paths.add(path);
            }
        } catch (IOException e) {
            throw new TycoParseException("Error reading path: " + path, e);
        }

        for (String filePath : paths) {
            TycoLexer lexer = TycoLexer.fromPath(context, filePath);
            lexer.process();
        }

        context.renderContent();
        return context.toJson();
    }

    /**
     * Load Tyco configuration from raw string content.
     *
     * @param content Tyco configuration text
     * @return parsed representation as nested Maps/Lists
     */
    public static Map<String, Object> loads(String content) {
        TycoContext context = new TycoContext();
        List<String> lines = splitContentIntoLines(content);
        TycoLexer lexer = new TycoLexer(context, lines, null);
        lexer.process();
        context.renderContent();
        return context.toJson();
    }

    /**
     * Convenience method for callers expecting an instance API.
     *
     * @param content Tyco configuration text
     * @return parsed representation as nested Maps/Lists
     */
    public Map<String, Object> parse(String content) {
        return loads(content);
    }

    private static List<String> splitContentIntoLines(String content) {
        String normalized = content.replace("\r\n", "\n");
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < normalized.length(); i++) {
            if (normalized.charAt(i) == '\n') {
                lines.add(normalized.substring(start, i + 1));
                start = i + 1;
            }
        }
        if (start < normalized.length()) {
            lines.add(normalized.substring(start) + "\n");
        } else if (lines.isEmpty()) {
            lines.add("\n");
        }
        return lines;
    }
}
