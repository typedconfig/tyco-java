package io.typedconfig.tyco;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests that replay the shared tyco-test-suite fixtures.
 */
public class TycoParserTest {

    private static final Path INPUT_DIR = Path.of("..", "tyco-test-suite", "inputs");
    private static final Path EXPECTED_DIR = Path.of("..", "tyco-test-suite", "expected");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<>() {};

    @Test
    void parsesAllSharedFixtures() throws IOException {
        List<Path> fixtures = Files.list(INPUT_DIR)
                .filter(path -> path.toString().endsWith(".tyco"))
                .sorted(Comparator.comparing(Path::getFileName))
                .collect(Collectors.toList());

        assertThat(fixtures).isNotEmpty();

        for (Path fixture : fixtures) {
            String name = fixture.getFileName().toString().replace(".tyco", "");
            Path expectedPath = EXPECTED_DIR.resolve(name + ".json");
            if (!Files.exists(expectedPath)) {
                continue; // some inputs are helper files without expectations
            }

            Map<String, Object> actual = TycoParser.load(fixture.toAbsolutePath().toString());
            Map<String, Object> expected = MAPPER.readValue(expectedPath.toFile(), MAP_REF);

            assertThat(actual)
                    .as("Fixture %s", name)
                    .isEqualTo(expected);
        }
    }
}
