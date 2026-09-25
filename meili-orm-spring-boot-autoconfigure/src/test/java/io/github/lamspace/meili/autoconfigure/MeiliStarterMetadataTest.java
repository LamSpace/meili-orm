package io.github.lamspace.meili.autoconfigure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Packaging-contract tests: the auto-configuration registration file carries exactly the
 * three contributed configurations (and every listed class actually loads), and the generated
 * configuration metadata registers every {@code meili.*} property with value hints for the
 * two enums. These guard against silent drift between code, registration and IDE metadata.
 */
class MeiliStarterMetadataTest {

    /** Classloader resource path of the auto-configuration registration file. */
    private static final String IMPORTS =
            "/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    /** Classloader resource path of the merged configuration metadata. */
    private static final String METADATA = "/META-INF/spring-configuration-metadata.json";

    @Test
    void importsFileListsExactlyThreeLoadableConfigurations() throws Exception {
        assertThat(getClass().getResource(IMPORTS)).isNotNull();
        List<String> lines;
        try (var stream = getClass().getResourceAsStream(IMPORTS);
             var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            lines = reader.lines().filter(line -> !line.isBlank()).toList();
        }
        assertThat(lines).containsExactly(
                "io.github.lamspace.meili.autoconfigure.MeiliClientAutoConfiguration",
                "io.github.lamspace.meili.autoconfigure.MeiliDataAutoConfiguration",
                "io.github.lamspace.meili.autoconfigure.MeiliInitializationAutoConfiguration");
        for (String line : lines) {
            assertThatCode(() -> Class.forName(line)).doesNotThrowAnyException();
        }
    }

    @Test
    void metadataRegistersAllMeiliProperties() throws Exception {
        JsonNode root = readJson(METADATA);
        List<String> names = new ArrayList<>();
        root.path("properties").forEach(node -> names.add(node.path("name").asText()));
        assertThat(names).contains(
                "meili.enabled", "meili.url", "meili.api-key", "meili.wait-task",
                "meili.wait-timeout", "meili.index.auto-init", "meili.index.on-settings-drift");
    }

    @Test
    void enumPropertiesCarryValueHints() throws Exception {
        JsonNode root = readJson(METADATA);
        assertHintValues(root, "meili.index.auto-init", "none", "create-if-missing", "sync-settings");
        assertHintValues(root, "meili.index.on-settings-drift", "warn", "apply", "fail");
    }

    /**
     * Asserts that the metadata's top-level hints array carries all expected kebab-case values
     * for one property (the shape IDE completion consumes).
     *
     * @param root     metadata document
     * @param name     property name
     * @param expected expected hint values, order-insensitive
     */
    private static void assertHintValues(JsonNode root, String name, String... expected) {
        List<String> values = new ArrayList<>();
        for (JsonNode hint : root.path("hints")) {
            if (name.equals(hint.path("name").asText())) {
                hint.path("values").forEach(value -> values.add(value.path("value").asText()));
            }
        }
        assertThat(values).containsExactlyInAnyOrder(expected);
    }

    /**
     * Reads a JSON classloader resource.
     *
     * @param path resource path
     * @return parsed document
     * @throws Exception on I/O or parse failure
     */
    private static JsonNode readJson(String path) throws Exception {
        assertThat(MeiliStarterMetadataTest.class.getResource(path)).isNotNull();
        try (InputStream in = MeiliStarterMetadataTest.class.getResourceAsStream(path)) {
            return new ObjectMapper().readTree(in);
        }
    }
}
