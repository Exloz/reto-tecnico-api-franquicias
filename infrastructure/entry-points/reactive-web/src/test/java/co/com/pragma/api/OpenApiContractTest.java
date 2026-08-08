package co.com.pragma.api;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiContractTest {
    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "patch", "delete");

    @Test
    void definesAllVersionOneOperations() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/static/openapi/franchise-api.yaml")) {
            assertThat(input).isNotNull();
            Map<String, Object> document = new Yaml().load(input);
            Map<String, Map<String, Object>> paths = value(document, "paths");
            int operations = 0;
            for (Map<String, Object> path : paths.values()) {
                for (String key : path.keySet()) {
                    operations += HTTP_METHODS.contains(key) ? 1 : 0;
                }
            }

            assertThat(document.get("openapi")).isEqualTo("3.1.0");
            assertThat(paths).hasSize(8);
            assertThat(operations).isEqualTo(9);

            List<String> references = new ArrayList<>();
            collectReferences(document, references);
            for (String reference : references) {
                assertThat(resolve(document, reference)).as(reference).isNotNull();
            }
        }
    }

    private void collectReferences(Object value, List<String> references) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if ("$ref".equals(entry.getKey())) {
                    references.add(entry.getValue().toString());
                }
                collectReferences(entry.getValue(), references);
            }
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                collectReferences(item, references);
            }
        }
    }

    private Object resolve(Map<String, Object> document, String reference) {
        Object value = document;
        for (String segment : reference.substring(2).split("/")) {
            value = ((Map<?, ?>) value).get(segment);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private <T> T value(Map<String, Object> values, String key) {
        return (T) values.get(key);
    }
}
