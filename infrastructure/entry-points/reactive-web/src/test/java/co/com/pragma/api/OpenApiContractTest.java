package co.com.pragma.api;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiContractTest {
    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "patch", "delete");
    private static final Pattern ROUTE = Pattern.compile("\\.(GET|POST|PATCH|DELETE)\\(\"([^\"]+)\"");

    @Test
    void isAValidOpenApiDocument() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);
        String location = Objects.requireNonNull(
                getClass().getResource("/static/openapi/franchise-api.yaml")).toExternalForm();

        SwaggerParseResult result = new OpenAPIParser().readLocation(location, null, options);

        assertThat(result.getMessages()).isEmpty();
        assertThat(result.getOpenAPI()).isNotNull();
    }

    @Test
    void definesAllVersionOneOperations() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/static/openapi/franchise-api.yaml")) {
            assertThat(input).isNotNull();
            Map<String, Object> document = new Yaml().load(input);
            Map<String, Map<String, Object>> paths = value(document, "paths");
            List<Map<String, Object>> servers = value(document, "servers");
            String serverUrl = servers.getFirst().get("url").toString();
            Set<String> documentedOperations = new HashSet<>();
            for (Map.Entry<String, Map<String, Object>> path : paths.entrySet()) {
                for (String key : path.getValue().keySet()) {
                    if (HTTP_METHODS.contains(key)) {
                        documentedOperations.add(key.toUpperCase() + " " + serverUrl + path.getKey());
                    }
                }
            }

            assertThat(document.get("openapi")).isEqualTo("3.1.0");
            assertThat(paths).hasSize(8);
            assertThat(documentedOperations).isEqualTo(routerOperations());

            List<String> references = new ArrayList<>();
            collectReferences(document, references);
            for (String reference : references) {
                assertThat(resolve(document, reference)).as(reference).isNotNull();
            }
        }
    }

    private Set<String> routerOperations() throws IOException {
        Path router = Path.of(
                System.getProperty("project.rootDir"),
                "infrastructure/entry-points/reactive-web/src/main/java/co/com/pragma/api/RouterRest.java");
        Matcher matcher = ROUTE.matcher(Files.readString(router));
        Set<String> operations = new HashSet<>();
        while (matcher.find()) {
            operations.add(matcher.group(1) + " " + matcher.group(2));
        }
        return operations;
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
