package io.github.huynhngochuyhoang.httpstarter.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ReactiveHttpClientConfigurationMetadataTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern REACTIVE_HTTP_PROPERTY = Pattern.compile(
            "reactive\\.http\\.[A-Za-z0-9_.<>\\[\\]{}*-]+(?:\\[[^]\\s`)]*])?(?:\\.[A-Za-z0-9_.<>\\[\\]{}*-]+)*");
    private static final Set<String> CONFIGURATION_EXAMPLE_LANGUAGES = Set.of("properties", "yaml", "yml");
    private static final Set<String> METRIC_NAME_PREFIXES = Set.of(
            "reactive.http.client.requests");

    @Test
    void documentsImportantConfigurationProperties() throws IOException {
        JsonNode metadata = starterMetadata();

        assertThat(propertyNames(metadata)).contains(
                "reactive.http.network.connect-timeout-ms",
                "reactive.http.network.network-read-timeout-ms",
                "reactive.http.network.network-write-timeout-ms",
                "reactive.http.network.connection-pool.max-connections",
                "reactive.http.network.connection-pool.pending-acquire-timeout-ms",
                "reactive.http.clients.[name].base-url",
                "reactive.http.clients.[name].codec-max-in-memory-size-mb",
                "reactive.http.clients.[name].compression-enabled",
                "reactive.http.clients.[name].http2-enabled",
                "reactive.http.clients.[name].follow-redirects",
                "reactive.http.clients.[name].log-exchange",
                "reactive.http.clients.[name].log-preset",
                "reactive.http.clients.[name].request-timeout-ms",
                "reactive.http.clients.[name].default-headers",
                "reactive.http.clients.[name].default-query-params",
                "reactive.http.clients.[name].apis.[api-name].method",
                "reactive.http.clients.[name].apis.[api-name].path",
                "reactive.http.clients.[name].apis.[api-name].timeout-ms",
                "reactive.http.clients.[name].auth-provider",
                "reactive.http.clients.[name].auth.type",
                "reactive.http.clients.[name].resilience.enabled",
                "reactive.http.clients.[name].resilience.retry",
                "reactive.http.clients.[name].resilience.circuit-breaker",
                "reactive.http.clients.[name].resilience.bulkhead",
                "reactive.http.clients.[name].resilience.rate-limiter",
                "reactive.http.clients.[name].resilience.timeout-ms",
                "reactive.http.clients.[name].pool.max-connections",
                "reactive.http.clients.[name].proxy.host",
                "reactive.http.clients.[name].tls.trust-store",
                "reactive.http.observability.enabled",
                "reactive.http.observability.metric-name",
                "reactive.http.observability.health.enabled",
                "reactive.http.observability.histogram.enabled",
                "reactive.http.correlation-id.max-length",
                "reactive.http.inbound-headers.allow-list"
        );
    }

    @Test
    void allConfigurationMetadataEntriesHaveDescriptions() throws IOException {
        List<String> missingDescriptions = new ArrayList<>();
        for (Path metadataFile : metadataFiles(projectRoot())) {
            JsonNode metadata = metadata(metadataFile);
            for (JsonNode property : metadata.path("properties")) {
                if (property.path("description").asText().isBlank()) {
                    missingDescriptions.add(projectRoot().relativize(metadataFile) + " -> "
                            + property.path("name").asText());
                }
            }
        }

        assertThat(missingDescriptions).as("metadata properties without descriptions").isEmpty();
    }

    @Test
    void configurationMetadataGroupsDoNotUseScalarValueTypes() throws IOException {
        List<String> scalarGroups = new ArrayList<>();
        for (Path metadataFile : metadataFiles(projectRoot())) {
            JsonNode metadata = metadata(metadataFile);
            for (JsonNode group : metadata.path("groups")) {
                if ("java.lang.Boolean".equals(group.path("type").asText())) {
                    scalarGroups.add(projectRoot().relativize(metadataFile) + " -> "
                            + group.path("name").asText());
                }
            }
        }

        assertThat(scalarGroups).as("metadata groups typed as scalar values").isEmpty();
    }

    @Test
    void configurationMetadataGroupSourceMethodsResolve() throws IOException {
        List<String> invalidSourceMethods = new ArrayList<>();
        for (Path metadataFile : metadataFiles(projectRoot())) {
            JsonNode metadata = metadata(metadataFile);
            for (JsonNode group : metadata.path("groups")) {
                String sourceType = group.path("sourceType").asText("");
                String sourceMethod = group.path("sourceMethod").asText("");
                if (sourceType.isBlank() || sourceMethod.isBlank()) {
                    continue;
                }
                String methodName = sourceMethod.replaceFirst("\\(.*\\)$", "");
                try {
                    Class.forName(sourceType).getMethod(methodName);
                }
                catch (ReflectiveOperationException ex) {
                    invalidSourceMethods.add(projectRoot().relativize(metadataFile) + " -> "
                            + group.path("name").asText() + " uses " + sourceType + "#" + sourceMethod);
                }
            }
        }

        assertThat(invalidSourceMethods).as("metadata group source methods that cannot be resolved").isEmpty();
    }

    @Test
    void documentedReactiveHttpPropertiesExistInGeneratedMetadata() throws IOException {
        Set<String> metadataNames = allMetadataNames(projectRoot());
        Map<String, Set<String>> missingByFile = new TreeMap<>();

        try (Stream<Path> markdown = currentDocumentation(projectRoot())) {
            for (Path file : markdown.toList()) {
                Set<String> missing = new TreeSet<>();
                Matcher matcher = REACTIVE_HTTP_PROPERTY.matcher(Files.readString(file));
                while (matcher.find()) {
                    String documented = normalizeDocumentedProperty(matcher.group());
                    if (documented == null || metadataNames.contains(documented)) {
                        continue;
                    }
                    missing.add(documented);
                }
                if (!missing.isEmpty()) {
                    missingByFile.put(projectRoot().relativize(file).toString(), missing);
                }
            }
        }

        assertThat(missingByFile).as("documented reactive.http.* properties missing from metadata").isEmpty();
    }

    @Test
    void documentedConfigurationExamplesUseMetadataPropertyNames() throws IOException {
        Set<String> metadataNames = allMetadataNames(projectRoot());
        Map<String, Set<String>> missingByFile = new TreeMap<>();
        Set<String> documentedExampleProperties = new TreeSet<>();

        try (Stream<Path> markdown = currentDocumentation(projectRoot())) {
            for (Path file : markdown.toList()) {
                Set<String> exampleProperties = configurationExampleProperties(Files.readAllLines(file));
                documentedExampleProperties.addAll(exampleProperties);
                Set<String> missing = new TreeSet<>(exampleProperties);
                missing.removeAll(metadataNames);
                if (!missing.isEmpty()) {
                    missingByFile.put(projectRoot().relativize(file).toString(), missing);
                }
            }
        }

        assertThat(missingByFile).as("reactive.http.* configuration example properties missing from metadata").isEmpty();
        assertThat(documentedExampleProperties).contains(
                "reactive.http.network.connect-timeout-ms",
                "reactive.http.network.network-read-timeout-ms",
                "reactive.http.network.network-write-timeout-ms",
                "reactive.http.clients.[name].request-timeout-ms",
                "reactive.http.clients.[name].auth-provider",
                "reactive.http.clients.[name].auth.oauth2-client-credentials.token-uri",
                "reactive.http.clients.[name].auth.aws-sig-v4.region",
                "reactive.http.clients.[name].resilience.enabled",
                "reactive.http.clients.[name].resilience.retry",
                "reactive.http.clients.[name].proxy.host",
                "reactive.http.clients.[name].tls.trust-store",
                "reactive.http.clients.[name].follow-redirects",
                "reactive.http.clients.[name].default-headers",
                "reactive.http.clients.[name].apis.[api-name].method",
                "reactive.http.clients.[name].apis.[api-name].path",
                "reactive.http.clients.[name].apis.[api-name].timeout-ms"
        );
    }

    @Test
    void deprecatedAliasesDeclareReplacementMetadata() throws IOException {
        JsonNode metadata = starterMetadata();

        assertDeprecation(metadata,
                "reactive.http.network.read-timeout-ms",
                "reactive.http.network.network-read-timeout-ms");
        assertDeprecation(metadata,
                "reactive.http.network.write-timeout-ms",
                "reactive.http.network.network-write-timeout-ms");
        assertDeprecation(metadata,
                "reactive.http.clients.[name].resilience.timeout-ms",
                "reactive.http.clients.[name].request-timeout-ms");
    }

    @Test
    void documentsDefaultsForHighValueProperties() throws IOException {
        JsonNode metadata = starterMetadata();

        assertDefaultValue(metadata, "reactive.http.network.connect-timeout-ms", 2000);
        assertDefaultValue(metadata, "reactive.http.network.network-read-timeout-ms", 60000);
        assertDefaultValue(metadata, "reactive.http.network.network-write-timeout-ms", 60000);
        assertDefaultValue(metadata, "reactive.http.network.connection-pool.max-connections", 200);
        assertDefaultValue(metadata, "reactive.http.network.connection-pool.pending-acquire-timeout-ms", 5000);
        assertDefaultValue(metadata, "reactive.http.network.connection-pool.max-idle-time-ms", 0);
        assertDefaultValue(metadata, "reactive.http.network.connection-pool.max-life-time-ms", 0);
        assertDefaultValue(metadata, "reactive.http.network.connection-pool.evict-in-background-ms", 0);
        assertDefaultValue(metadata, "reactive.http.network.connection-pool.metrics-enabled", false);
        assertDefaultValue(metadata, "reactive.http.network.proxy.type", "HTTP");
        assertDefaultValue(metadata, "reactive.http.network.tls.trust-store-type", "PKCS12");
        assertDefaultValue(metadata, "reactive.http.network.tls.key-store-type", "PKCS12");
        assertDefaultValue(metadata, "reactive.http.network.tls.insecure-trust-all", false);
        assertDefaultValue(metadata, "reactive.http.clients.[name].codec-max-in-memory-size-mb", 2);
        assertDefaultValue(metadata, "reactive.http.clients.[name].compression-enabled", false);
        assertDefaultValue(metadata, "reactive.http.clients.[name].http2-enabled", false);
        assertDefaultValue(metadata, "reactive.http.clients.[name].follow-redirects", false);
        assertDefaultValue(metadata, "reactive.http.clients.[name].log-exchange", false);
        assertDefaultValue(metadata, "reactive.http.clients.[name].log-preset", "metadata-only");
        assertDefaultValue(metadata, "reactive.http.clients.[name].request-timeout-ms", 0);
        assertDefaultValue(metadata, "reactive.http.clients.[name].apis.[api-name].timeout-ms", -1);
        assertDefaultValue(metadata, "reactive.http.clients.[name].auth.oauth2-client-credentials.auth-style", "basic-auth");
        assertDefaultValue(metadata, "reactive.http.clients.[name].auth.oauth2-client-credentials.expiry-leeway-ms", 30000);
        assertDefaultValue(metadata, "reactive.http.clients.[name].resilience.enabled", false);
        assertDefaultValue(metadata, "reactive.http.clients.[name].resilience.retry", "default");
        assertDefaultValue(metadata, "reactive.http.clients.[name].resilience.circuit-breaker", "default");
        assertDefaultValue(metadata, "reactive.http.clients.[name].resilience.bulkhead", "default");
        assertDefaultValue(metadata, "reactive.http.clients.[name].resilience.rate-limiter", "default");
        assertDefaultValue(metadata, "reactive.http.clients.[name].resilience.retry-methods", List.of("GET", "HEAD"));
        assertDefaultValue(metadata, "reactive.http.clients.[name].resilience.timeout-ms", 0);
        assertDefaultValue(metadata, "reactive.http.observability.enabled", true);
        assertDefaultValue(metadata, "reactive.http.observability.metric-name", "reactive.http.client.requests");
        assertDefaultValue(metadata, "reactive.http.observability.include-url-path", false);
        assertDefaultValue(metadata, "reactive.http.observability.include-server-address", false);
        assertDefaultValue(metadata, "reactive.http.observability.log-request-body", false);
        assertDefaultValue(metadata, "reactive.http.observability.log-response-body", false);
        assertDefaultValue(metadata, "reactive.http.observability.health.enabled", true);
        assertDefaultValue(metadata, "reactive.http.observability.health.error-rate-threshold", 0.5);
        assertDefaultValue(metadata, "reactive.http.observability.health.min-samples", 10);
        assertDefaultValue(metadata, "reactive.http.observability.histogram.enabled", false);
        assertDefaultValue(metadata, "reactive.http.observability.histogram.slo-boundaries-ms", List.of(50, 100, 200, 500, 1000, 2000, 5000));
        assertDefaultValue(metadata, "reactive.http.correlation-id.max-length", 128);
        assertDefaultValue(metadata, "reactive.http.correlation-id.mdc-keys", List.of("correlationId", "X-Correlation-Id", "traceId"));
    }

    private static JsonNode starterMetadata() throws IOException {
        try (InputStream input = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("META-INF/additional-spring-configuration-metadata.json")) {
            assertThat(input).as("configuration metadata resource").isNotNull();
            return OBJECT_MAPPER.readTree(input);
        }
    }

    private static JsonNode metadata(Path metadata) throws IOException {
        return OBJECT_MAPPER.readTree(metadata.toFile());
    }

    private static Set<String> allMetadataNames(Path root) throws IOException {
        Set<String> names = new TreeSet<>();
        for (Path metadataFile : allMetadataFiles(root)) {
            JsonNode node = metadata(metadataFile);
            names.addAll(propertyNames(node));
            names.addAll(groupNames(node));
        }
        return names;
    }

    private static List<Path> allMetadataFiles(Path root) {
        return List.of(starterMetadataFile(root),
                root.resolve("reactive-http-client-otel/src/main/resources/META-INF/additional-spring-configuration-metadata.json"));
    }

    private static List<Path> metadataFiles(Path root) {
        return List.of(starterMetadataFile(root));
    }

    private static Path starterMetadataFile(Path root) {
        return root.resolve("reactive-http-client-starter/src/main/resources/META-INF/additional-spring-configuration-metadata.json");
    }

    private static Stream<Path> currentDocumentation(Path root) throws IOException {
        return Stream.concat(Stream.of(root.resolve("README.md")), Files.walk(root.resolve("docs")))
                .filter(path -> path.toString().endsWith(".md"));
    }

    private static Set<String> propertyNames(JsonNode metadata) {
        Set<String> names = new TreeSet<>();
        for (JsonNode property : metadata.path("properties")) {
            names.add(property.path("name").asText());
        }
        return names;
    }

    private static Set<String> groupNames(JsonNode metadata) {
        Set<String> names = new TreeSet<>();
        for (JsonNode group : metadata.path("groups")) {
            names.add(group.path("name").asText());
        }
        return names;
    }

    private static String normalizeDocumentedProperty(String raw) {
        String value = raw
                .replace("{", "")
                .replace("}", "")
                .replaceAll("[.,:;]+$", "");
        if (value.equals("reactive.http.*") || hasPrefix(value, METRIC_NAME_PREFIXES)) {
            return null;
        }
        value = value.replaceAll("reactive\\.http\\.clients\\.(?:<name>|<client>|\\*|[A-Za-z0-9_-]+)(?=\\.|$)",
                "reactive.http.clients.[name]");
        value = value.replaceAll("\\.apis\\[[^]]+](?=\\.)", ".apis.[api-name]");
        value = value.replaceAll("\\.apis\\[[^]]+]$", ".apis");
        value = value.replaceAll("\\.apis\\.<api>(?=\\.|$)", ".apis.[api-name]");
        value = value.replaceAll("\\.apis\\.\\[[^]]+](?=\\.)", ".apis.[api-name]");
        value = value.replaceAll("\\.apis\\.\\[[^]]+]$", ".apis");
        value = value.replaceAll("\\.apis\\.[^.]+(?=\\.)", ".apis.[api-name]");
        value = value.replaceAll("\\.apis\\.[^.]+$", ".apis");
        value = value.replaceAll("\\.default-headers\\..+$", ".default-headers");
        value = value.replaceAll("\\.default-query-params\\..+$", ".default-query-params");
        return value;
    }

    private static Set<String> configurationExampleProperties(List<String> lines) {
        Set<String> properties = new TreeSet<>();
        String language = "";
        List<String> block = new ArrayList<>();
        boolean inFence = false;

        for (String line : lines) {
            if (line.startsWith("```")) {
                if (inFence) {
                    properties.addAll(configurationExampleProperties(language, block));
                    block.clear();
                    language = "";
                    inFence = false;
                }
                else {
                    language = line.substring(3).trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
                    inFence = true;
                }
                continue;
            }
            if (inFence) {
                block.add(line);
            }
        }
        return properties;
    }

    private static Set<String> configurationExampleProperties(String language, List<String> lines) {
        if (!CONFIGURATION_EXAMPLE_LANGUAGES.contains(language)) {
            return Set.of();
        }
        if ("properties".equals(language)) {
            return propertiesExampleProperties(lines);
        }
        return yamlExampleProperties(lines);
    }

    private static Set<String> propertiesExampleProperties(List<String> lines) {
        Set<String> properties = new TreeSet<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#")) {
                continue;
            }
            int separator = firstSeparator(trimmed);
            if (separator < 0) {
                continue;
            }
            addConfigurationExampleProperty(properties, trimmed.substring(0, separator).trim());
        }
        return properties;
    }

    private static Set<String> yamlExampleProperties(List<String> lines) {
        Set<String> properties = new TreeSet<>();
        List<YamlPath> stack = new ArrayList<>();
        for (String line : lines) {
            String withoutComment = stripYamlComment(line);
            if (withoutComment.trim().isBlank()) {
                continue;
            }
            int indent = leadingSpaces(withoutComment);
            String trimmed = withoutComment.trim();
            if (trimmed.startsWith("- ")) {
                continue;
            }
            int separator = trimmed.indexOf(":");
            if (separator < 0) {
                continue;
            }
            String key = unquoteYamlKey(trimmed.substring(0, separator).trim());
            if (key.isBlank()) {
                continue;
            }
            while (!stack.isEmpty() && stack.get(stack.size() - 1).indent() >= indent) {
                stack.remove(stack.size() - 1);
            }
            String path = stack.isEmpty() ? key : stack.get(stack.size() - 1).path() + "." + key;
            addConfigurationExampleProperty(properties, path);
            if (trimmed.substring(separator + 1).trim().isBlank()) {
                stack.add(new YamlPath(indent, path));
            }
        }
        return properties;
    }

    private static void addConfigurationExampleProperty(Set<String> properties, String raw) {
        if (!raw.startsWith("reactive.http")) {
            return;
        }
        String normalized = normalizeDocumentedProperty(raw);
        if (normalized != null) {
            properties.add(normalized);
        }
    }

    private static int firstSeparator(String value) {
        int equals = value.indexOf("=");
        int colon = value.indexOf(":");
        if (equals < 0) {
            return colon;
        }
        if (colon < 0) {
            return equals;
        }
        return Math.min(equals, colon);
    }

    private static String stripYamlComment(String line) {
        int comment = line.indexOf(" #");
        return comment < 0 ? line : line.substring(0, comment);
    }

    private static int leadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.substring(count, count + 1).equals(" ")) {
            count++;
        }
        return count;
    }

    private static String unquoteYamlKey(String key) {
        if ((key.startsWith("\"") && key.endsWith("\"")) || (key.length() >= 2 && key.charAt(0) == 39 && key.charAt(key.length() - 1) == 39)) {
            return key.substring(1, key.length() - 1);
        }
        return key;
    }

    private record YamlPath(int indent, String path) {
    }

    private static boolean hasPrefix(String value, Set<String> prefixes) {
        return prefixes.stream().anyMatch(value::startsWith);
    }

    private static void assertDefaultValue(JsonNode metadata, String propertyName, Object expected) {
        JsonNode property = findProperty(metadata, propertyName);

        assertThat(property.has("defaultValue"))
                .as("%s defaultValue", propertyName)
                .isTrue();
        assertThat(property.get("defaultValue"))
                .as("%s defaultValue", propertyName)
                .isEqualTo(OBJECT_MAPPER.valueToTree(expected));
    }

    private static void assertDeprecation(JsonNode metadata, String propertyName, String replacement) {
        JsonNode deprecation = findProperty(metadata, propertyName).path("deprecation");

        assertThat(deprecation.isMissingNode())
                .as("%s deprecation metadata", propertyName)
                .isFalse();
        assertThat(deprecation.path("level").asText())
                .as("%s deprecation level", propertyName)
                .isEqualTo("warning");
        assertThat(deprecation.path("replacement").asText())
                .as("%s deprecation replacement", propertyName)
                .isEqualTo(replacement);
    }

    private static JsonNode findProperty(JsonNode metadata, String propertyName) {
        for (JsonNode property : metadata.path("properties")) {
            if (propertyName.equals(property.path("name").asText())) {
                return property;
            }
        }
        throw new AssertionError("Missing metadata for " + propertyName);
    }

    private static Path projectRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (Files.exists(cwd.resolve("README.md")) && Files.isDirectory(cwd.resolve("docs"))) {
            return cwd;
        }
        return cwd.getParent();
    }
}
