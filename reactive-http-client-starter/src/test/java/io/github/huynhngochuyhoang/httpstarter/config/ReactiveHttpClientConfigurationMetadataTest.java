package io.github.huynhngochuyhoang.httpstarter.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class ReactiveHttpClientConfigurationMetadataTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void documentsImportantConfigurationProperties() throws IOException {
        JsonNode metadata = metadata();

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
                "reactive.http.clients.[name].log-exchange",
                "reactive.http.clients.[name].log-preset",
                "reactive.http.clients.[name].request-timeout-ms",
                "reactive.http.clients.[name].default-headers",
                "reactive.http.clients.[name].default-query-params",
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
                "reactive.http.correlation-id.max-length",
                "reactive.http.inbound-headers.allow-list"
        );
    }

    @Test
    void documentsDefaultsForHighValueProperties() throws IOException {
        JsonNode metadata = metadata();

        assertDefaultValue(metadata, "reactive.http.network.connect-timeout-ms", 2000);
        assertDefaultValue(metadata, "reactive.http.network.network-read-timeout-ms", 60000);
        assertDefaultValue(metadata, "reactive.http.network.network-write-timeout-ms", 60000);
        assertDefaultValue(metadata, "reactive.http.network.connection-pool.max-connections", 200);
        assertDefaultValue(metadata, "reactive.http.network.connection-pool.pending-acquire-timeout-ms", 5000);
        assertDefaultValue(metadata, "reactive.http.clients.[name].codec-max-in-memory-size-mb", 2);
        assertDefaultValue(metadata, "reactive.http.clients.[name].compression-enabled", false);
        assertDefaultValue(metadata, "reactive.http.clients.[name].http2-enabled", false);
        assertDefaultValue(metadata, "reactive.http.clients.[name].log-exchange", false);
        assertDefaultValue(metadata, "reactive.http.clients.[name].log-preset", "metadata-only");
        assertDefaultValue(metadata, "reactive.http.clients.[name].request-timeout-ms", 0);
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
        assertDefaultValue(metadata, "reactive.http.correlation-id.max-length", 128);
    }

    private static JsonNode metadata() throws IOException {
        try (InputStream input = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("META-INF/additional-spring-configuration-metadata.json")) {
            assertThat(input).as("configuration metadata resource").isNotNull();
            return OBJECT_MAPPER.readTree(input);
        }
    }

    private static Set<String> propertyNames(JsonNode metadata) {
        Set<String> names = new TreeSet<>();
        for (JsonNode property : metadata.path("properties")) {
            names.add(property.path("name").asText());
        }
        return names;
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

    private static JsonNode findProperty(JsonNode metadata, String propertyName) {
        for (JsonNode property : metadata.path("properties")) {
            if (propertyName.equals(property.path("name").asText())) {
                return property;
            }
        }
        throw new AssertionError("Missing metadata for " + propertyName);
    }
}
