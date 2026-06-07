package io.github.huynhngochuyhoang.httpstarter.otel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenTelemetryConfigurationMetadataTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void metadataPropertiesHaveDescriptions() throws IOException {
        JsonNode metadata = metadata();
        List<String> missingDescriptions = new ArrayList<>();

        for (JsonNode property : metadata.path("properties")) {
            if (property.path("description").asText().isBlank()) {
                missingDescriptions.add(property.path("name").asText());
            }
        }

        assertThat(missingDescriptions).as("OTel metadata properties without descriptions").isEmpty();
    }

    @Test
    void metadataGroupsDoNotUseScalarValueTypes() throws IOException {
        JsonNode metadata = metadata();
        List<String> scalarGroups = new ArrayList<>();

        for (JsonNode group : metadata.path("groups")) {
            if ("java.lang.Boolean".equals(group.path("type").asText())) {
                scalarGroups.add(group.path("name").asText());
            }
        }

        assertThat(scalarGroups).as("OTel metadata groups typed as scalar values").isEmpty();
    }

    @Test
    void metadataGroupSourceTypesResolveOnOtelClasspath() throws IOException {
        JsonNode metadata = metadata();
        List<String> invalidSourceTypes = new ArrayList<>();

        for (JsonNode group : metadata.path("groups")) {
            String sourceType = group.path("sourceType").asText("");
            if (sourceType.isBlank()) {
                continue;
            }
            try {
                Class.forName(sourceType);
            }
            catch (ReflectiveOperationException ex) {
                invalidSourceTypes.add(group.path("name").asText() + " uses " + sourceType);
            }
        }

        assertThat(invalidSourceTypes).as("OTel metadata group source types that cannot be resolved").isEmpty();
    }

    @Test
    void metadataDocumentsConditionalDefaults() throws IOException {
        JsonNode metadata = metadata();

        assertDefaultValue(metadata, "reactive.http.observability.otel.enabled", true);
        assertDefaultValue(metadata, "reactive.http.observability.otel.spans.enabled", true);
        assertDefaultValue(metadata, "reactive.http.observability.otel.propagation.enabled", true);
    }

    private static JsonNode metadata() throws IOException {
        try (InputStream input = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("META-INF/additional-spring-configuration-metadata.json")) {
            assertThat(input).as("OTel configuration metadata resource").isNotNull();
            return OBJECT_MAPPER.readTree(input);
        }
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
