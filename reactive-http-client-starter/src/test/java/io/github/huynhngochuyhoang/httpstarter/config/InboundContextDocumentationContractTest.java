package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.core.RequestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.util.context.Context;
import reactor.util.context.ContextView;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import javax.tools.ToolProvider;
import java.io.StringWriter;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InboundContextDocumentationContractTest {
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();
    private static final List<String> COUNTS = List.of(
            "matchingNameCount", "valueCount", "emptyValueCount", "redactedValueCount");

    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(strings = {"PublishedInboundHeaderExample", "CandidateInboundHeaderExample"})
    void documentedRequiredValueExamplesCompileAndRejectUnsafeParsingInputs(String className) throws Exception {
        String guide = Files.readString(root().resolve("docs/09-correlation-id.md"));
        var blocks = Pattern.compile("```java\\R(.*?)```", Pattern.DOTALL).matcher(guide);
        String source = null;
        while (blocks.find()) {
            if (blocks.group(1).contains("public final class " + className + " {")) {
                assertThat(source).isNull();
                source = blocks.group(1);
            }
        }
        assertThat(source).isNotNull();
        if (className.startsWith("Published")) {
            assertThat(source).doesNotContain("RequestContext.inboundHeader(", "RequestContext.inboundHeaderValues(");
        }
        Path file = temporary.resolve(className + ".java");
        Files.writeString(file, source);
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();
        var errors = new StringWriter();
        try (var files = compiler.getStandardFileManager(null, null, null)) {
            assertThat(compiler.getTask(errors, files, null, List.of("--release", "21", "-classpath",
                            System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                            "-d", temporary.toString()), null, files.getJavaFileObjects(file)).call())
                    .as(errors.toString()).isTrue();
        }
        try (var loader = new URLClassLoader(new java.net.URL[]{temporary.toUri().toURL()}, getClass().getClassLoader())) {
            var read = loader.loadClass(className).getMethod("requiredFixtureData", ContextView.class);
            assertThat(read.invoke(null, captured(Map.of("x-fixture-data", List.of("EXAMPLE_data")))))
                    .isEqualTo("EXAMPLE_data");
            for (Context ctx : List.of(Context.empty(), captured(Map.of("X-Fixture-Data", List.of())))) {
                assertThatThrownBy(() -> read.invoke(null, ctx)).hasCauseInstanceOf(IllegalArgumentException.class);
            }
            for (String unusable : List.of("", "[REDACTED]", "x".repeat(4097))) {
                assertThatThrownBy(() -> read.invoke(null, captured(Map.of("X-Fixture-Data", List.of(unusable)))))
                        .hasCauseInstanceOf(IllegalArgumentException.class);
            }
            for (Object malformed : List.of("not-a-map",
                    Map.of("X-Fixture-Data", List.of("same", "same")),
                    Map.of("X-Fixture-Data", List.of("same"), "x-fixture-data", List.of("same")),
                    Map.of("X-Fixture-Data", List.of("ok"), "unrelated", 1),
                    Map.of("bad name", List.of("not-retained")))) {
                assertThatThrownBy(() -> read.invoke(null, captured(malformed)))
                        .hasCauseInstanceOf(IllegalStateException.class);
            }
        }
    }

    @Test
    void guidesShareTheVersionedContractAndStructuralTriageLinks() throws Exception {
        for (String path : List.of("09-correlation-id", "13-exchange-logging", "14-test-helpers",
                "16-production-checklist", "26-support-bundles", "30-operations-troubleshooting")) {
            String guide = Files.readString(root().resolve("docs/" + path + ".md"));
            assertThat(guide).as(path).contains("4.3.0", "4.4.0-SNAPSHOT", "[REDACTED]");
        }
        String correlation = Files.readString(root().resolve("docs/09-correlation-id.md"));
        assertThat(correlation).doesNotContain("first(headers,", ").getFirst()");
        String operations = Files.readString(root().resolve("docs/30-operations-troubleshooting.md"));
        assertThat(operations).contains("Case mismatch, not evidence of lost context",
                "Optional manual mesh", "independent subscriber", "malformed");
        String support = Files.readString(root().resolve("docs/26-support-bundles.md"));
        assertThat(support).contains("fixtures/support-bundle-inbound-context.json",
                "not** a new diagnostics", "No new meter or public diagnostic field");
        String logging = Files.readString(root().resolve("docs/13-exchange-logging.md"));
        assertThat(logging).contains("Only `RequestContext.inboundHeader` rejects multiplicity",
                "`RequestContext.inboundHeaderValues` returns duplicates and case-alias values unchanged",
                "exposed map-iteration and per-list order", "Arbitrary maps may expose unstable order");
    }

    @Test
    void fixtureIsBoundedAndShowsTheSameRequestWithoutRawFieldMaterial() throws Exception {
        JsonNode fixture = fixture();
        validate(fixture);
        for (JsonNode observation : fixture.path("observations")) {
            assertThat(observation.path("contextState").asText()).isEqualTo("map");
            assertThat(observation.path("exactNamePresent").booleanValue()).isFalse();
            assertThat(observation.path("matchingNameCount").intValue()).isEqualTo(1);
            assertThat(observation.path("valueCount").intValue()).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"schemaVersion\":\"EXAMPLE_SECRET\",\"schemaVersion\":1}",
            "{\"versions\":{\"starter\":\"EXAMPLE_SECRET\",\"starter\":\"4.3.0\"}}",
            "{\"observations\":[{\"fieldAlias\":\"EXAMPLE_SECRET\",\"fieldAlias\":\"field-1\"}]}",
            "{\"observations\":[{\"field\\u0041lias\":\"EXAMPLE_SECRET\",\"fieldAlias\":\"field-1\"}]}"})
    void fixtureParserRejectsDuplicatePropertiesBeforeTreeValidation(String rawJson) {
        assertThatThrownBy(() -> JSON.readTree(rawJson))
                .isInstanceOf(StreamReadException.class)
                .hasMessageContaining("Duplicate");
    }

    @ParameterizedTest
    @ValueSource(strings = {"requestHeaders", "headerName", "headerValue", "token", "userId",
            "accountId", "principal", "subject", "requestPath", "queryParameters", "payload", "sample"})
    void fixtureRejectsUnknownFieldsAtEveryObjectBoundary(String field) throws Exception {
        JsonNode fixture = fixture();
        var pointers = new ArrayList<String>();
        objectPointers(fixture, "", pointers);
        for (String pointer : pointers) {
            JsonNode invalid = fixture.deepCopy();
            ((ObjectNode) invalid.at(pointer)).put(field, "not-for-export");
            assertThatThrownBy(() -> validate(invalid)).as(pointer + "/" + field).isInstanceOf(AssertionError.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Authorization", "customer-123", "/orders/42?debug", "?debug", "*",
            "PROPFIND /orders/42 HTTP/1.1", "internal.example:443", "file:///private/path"})
    void fixtureRejectsSensitiveTextEvenUnderAllowedNames(String text) throws Exception {
        JsonNode fixture = fixture();
        var pointers = new ArrayList<String>();
        objectPointers(fixture, "", pointers);
        for (String pointer : pointers) {
            for (var property : fixture.at(pointer).properties()) {
                if (!property.getValue().isTextual()) { continue; }
                JsonNode invalid = fixture.deepCopy();
                ((ObjectNode) invalid.at(pointer)).put(property.getKey(), text);
                assertThatThrownBy(() -> validate(invalid))
                        .as(pointer + "/" + property.getKey()).isInstanceOf(AssertionError.class);
            }
        }
    }

    @Test
    void fixtureRejectsMissingFieldsCoercionAndInconsistentCountsOrBoundaries() throws Exception {
        JsonNode fixture = fixture();
        var pointers = new ArrayList<String>();
        objectPointers(fixture, "", pointers);
        for (String pointer : pointers) {
            for (var property : fixture.at(pointer).properties()) {
                JsonNode missing = fixture.deepCopy();
                ((ObjectNode) missing.at(pointer)).remove(property.getKey());
                assertThatThrownBy(() -> validate(missing)).isInstanceOf(AssertionError.class);
                if (!property.getValue().isNumber() && !property.getValue().isBoolean()) { continue; }
                JsonNode coerced = fixture.deepCopy();
                ((ObjectNode) coerced.at(pointer)).put(property.getKey(), property.getValue().asText());
                assertThatThrownBy(() -> validate(coerced)).isInstanceOf(AssertionError.class);
            }
        }
        for (String count : COUNTS) {
            for (int value : List.of(-1, 65)) {
                JsonNode invalid = fixture.deepCopy();
                ((ObjectNode) invalid.at("/observations/1")).put(count, value);
                assertThatThrownBy(() -> validate(invalid)).isInstanceOf(AssertionError.class);
            }
        }
        for (Map.Entry<String, JsonNode> mutation : Map.<String, JsonNode>of(
                "/window/durationSeconds", JSON.valueToTree(299),
                "/observations/1/boundary", JSON.valueToTree("capture"),
                "/observations/1/observedAt", JSON.valueToTree("2000-01-01T00:00:30Z"),
                "/observations/1/valueCount", JSON.valueToTree(0),
                "/observations/1/emptyValueCount", JSON.valueToTree(2),
                "/observations/1/matchingNameCount", JSON.valueToTree(0),
                "/observations/1/contextState", JSON.valueToTree("absent")).entrySet()) {
            JsonNode invalid = fixture.deepCopy();
            String pointer = mutation.getKey();
            ((ObjectNode) invalid.at(pointer.substring(0, pointer.lastIndexOf('/'))))
                    .set(pointer.substring(pointer.lastIndexOf('/') + 1), mutation.getValue());
            // A matching empty list is valid; make the zero-value case inconsistent with its subsets.
            if (pointer.endsWith("/valueCount")) {
                ((ObjectNode) invalid.at("/observations/1")).put("redactedValueCount", 1);
            }
            assertThatThrownBy(() -> validate(invalid)).as(pointer).isInstanceOf(AssertionError.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent", "malformed", "unknown", "map"})
    void fixturePreservesUnknownAbsentAndMatchingEmptyListStates(String state) throws Exception {
        JsonNode fixture = fixture();
        var read = (ObjectNode) fixture.at("/observations/1");
        read.put("contextState", state);
        boolean known = state.equals("absent") || state.equals("map");
        for (String count : COUNTS) {
            if (known) { read.put(count, 0); } else { read.putNull(count); }
        }
        if (known) { read.put("exactNamePresent", false); } else { read.putNull("exactNamePresent"); }
        if (state.equals("map")) { read.put("matchingNameCount", 1); }
        validate(fixture);
    }

    @Test
    void fixtureRejectsOmittedNamesAtTheCaptureBoundaryEvenWithEmptyLists() throws Exception {
        for (int values : List.of(0, 1)) {
            JsonNode fixture = fixture();
            ((ObjectNode) fixture.path("capturePolicy")).put("allowDecision", "omitted");
            ((ObjectNode) fixture.at("/observations/0")).put("valueCount", values);
            assertThatThrownBy(() -> validate(fixture)).isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void fixtureRejectsDeniedCaptureCountsWithoutExactlyOneMarkerPerName() throws Exception {
        for (int[] counts : List.of(
                new int[]{1, 1, 0, 0}, new int[]{1, 0, 0, 0}, new int[]{1, 2, 0, 2},
                new int[]{1, 2, 1, 1}, new int[]{2, 1, 0, 1}, new int[]{2, 2, 0, 1})) {
            JsonNode fixture = fixture();
            ((ObjectNode) fixture.path("capturePolicy")).put("denyDecision", "denied");
            ObjectNode capture = (ObjectNode) fixture.at("/observations/0");
            for (int i = 0; i < COUNTS.size(); i++) { capture.put(COUNTS.get(i), counts[i]); }
            assertThatThrownBy(() -> validate(fixture)).isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void fixturePreservesOmissionPrecedenceUnknownPolicyAndLaterReadChanges() throws Exception {
        for (String allow : List.of("selected", "omitted", "unknown")) {
            for (String deny : List.of("denied", "not-denied", "unknown")) {
                JsonNode fixture = fixture();
                ((ObjectNode) fixture.path("capturePolicy")).put("allowDecision", allow).put("denyDecision", deny);
                ObjectNode capture = (ObjectNode) fixture.at("/observations/0");
                if (allow.equals("omitted")) {
                    COUNTS.forEach(count -> capture.put(count, 0));
                } else if (deny.equals("denied")) {
                    capture.put("matchingNameCount", 2).put("valueCount", 2).put("redactedValueCount", 2);
                } else {
                    // A literal marker can be supplied by the sender, not just by the deny-list.
                    capture.put("redactedValueCount", 1);
                }
                // The read record deliberately remains populated and unredacted.
                validate(fixture);
                capture.put("contextState", "unknown").putNull("exactNamePresent");
                COUNTS.forEach(capture::putNull);
                validate(fixture);
            }
        }
    }

    private static void validate(JsonNode fixture) {
        fields(fixture, "schemaVersion", "versions", "window", "webStack", "protocolHops", "capturePolicy", "observations");
        assertThat(integer(fixture.path("schemaVersion"), 1, 1)).isEqualTo(1);
        fields(fixture.path("versions"), "starter", "springBoot", "springFramework", "reactor", "mesh");
        fixture.path("versions").properties().forEach(entry ->
                text(entry.getValue(), "(?:[0-9]{1,3}\\.){2}[0-9]{1,3}(?:-SNAPSHOT)?|unknown"));
        fields(fixture.path("window"), "startedAt", "endedAt", "durationSeconds");
        Instant start = timestamp(fixture.at("/window/startedAt"));
        Instant end = timestamp(fixture.at("/window/endedAt"));
        assertThat(Duration.between(start, end).getSeconds())
                .isEqualTo(integer(fixture.at("/window/durationSeconds"), 1, 300));
        text(fixture.path("webStack"), "webflux|mvc|unknown");
        JsonNode hops = fixture.path("protocolHops");
        assertThat(hops.isArray()).isTrue();
        assertThat(hops.size()).isBetween(1, 4);
        var names = new java.util.HashSet<String>();
        for (JsonNode hop : hops) {
            fields(hop, "hop", "protocol");
            text(hop.path("hop"), "hop-[1-4]");
            assertThat(names.add(hop.path("hop").asText())).isTrue();
            text(hop.path("protocol"), "HTTP/1\\.1|HTTP/2|unknown");
        }
        fields(fixture.path("capturePolicy"), "allowDecision", "denyDecision");
        text(fixture.at("/capturePolicy/allowDecision"), "selected|omitted|unknown");
        text(fixture.at("/capturePolicy/denyDecision"), "denied|not-denied|unknown");
        JsonNode observations = fixture.path("observations");
        assertThat(observations.isArray()).isTrue();
        assertThat(observations.size()).isEqualTo(2);
        Instant previous = start;
        for (int i = 0; i < 2; i++) {
            JsonNode observation = observations.get(i);
            fields(observation, "boundary", "observedAt", "requestAlias", "fieldAlias", "subscription",
                    "liveFieldPresent", "contextState", "exactNamePresent", "matchingNameCount", "valueCount",
                    "emptyValueCount", "redactedValueCount");
            text(observation.path("boundary"), i == 0 ? "capture" : "read");
            Instant instant = timestamp(observation.path("observedAt"));
            assertThat(instant).isBetween(previous, end);
            previous = instant;
            text(observation.path("requestAlias"), "request-1");
            text(observation.path("fieldAlias"), "field-1");
            text(observation.path("subscription"), "ingress|independent|unknown");
            JsonNode live = observation.path("liveFieldPresent");
            assertThat(live.isBoolean() || live.isNull()).isTrue();
            text(observation.path("contextState"), "absent|map|malformed|unknown");
            String state = observation.path("contextState").asText();
            if (Set.of("malformed", "unknown").contains(state)) {
                assertThat(observation.path("exactNamePresent").isNull()).isTrue();
                COUNTS.forEach(count -> assertThat(observation.path(count).isNull()).isTrue());
                continue;
            }
            assertThat(observation.path("exactNamePresent").isBoolean()).isTrue();
            COUNTS.forEach(count -> integer(observation.path(count), 0, state.equals("absent") ? 0 : 64));
            int matching = observation.path("matchingNameCount").intValue();
            int values = observation.path("valueCount").intValue();
            if (matching == 0) {
                assertThat(observation.path("exactNamePresent").booleanValue()).isFalse();
                assertThat(values).isZero();
            }
            assertThat(observation.path("emptyValueCount").intValue()
                    + observation.path("redactedValueCount").intValue()).isLessThanOrEqualTo(values);
            if (i == 0) {
                if (fixture.at("/capturePolicy/allowDecision").asText().equals("omitted")) {
                    assertThat(matching).isZero();
                }
                if (fixture.at("/capturePolicy/denyDecision").asText().equals("denied")) {
                    assertThat(values).isEqualTo(matching);
                    assertThat(observation.path("redactedValueCount").intValue()).isEqualTo(matching);
                    assertThat(observation.path("emptyValueCount").intValue()).isZero();
                }
            }
        }
    }

    private static void fields(JsonNode node, String... names) {
        assertThat(node.isObject()).isTrue();
        assertThat(node.properties().stream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder(names);
    }

    private static long integer(JsonNode node, long min, long max) {
        assertThat(node.isIntegralNumber() && node.canConvertToLong()).isTrue();
        assertThat(node.longValue()).isBetween(min, max);
        return node.longValue();
    }

    private static void text(JsonNode node, String pattern) {
        assertThat(node.isTextual()).isTrue();
        assertThat(node.asText()).matches(pattern);
    }

    private static Instant timestamp(JsonNode node) {
        text(node, "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z");
        return Instant.parse(node.asText());
    }

    private static void objectPointers(JsonNode node, String pointer, List<String> pointers) {
        if (node.isObject()) {
            pointers.add(pointer);
            node.properties().forEach(entry -> objectPointers(entry.getValue(), pointer + "/" + entry.getKey(), pointers));
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) { objectPointers(node.get(i), pointer + "/" + i, pointers); }
        }
    }

    private static JsonNode fixture() throws Exception {
        Path file = root().resolve("docs/fixtures/support-bundle-inbound-context.json");
        assertThat(Files.size(file)).isLessThanOrEqualTo(4096);
        return JSON.readTree(Files.readString(file));
    }

    private static Context captured(Object fields) {
        return Context.of(RequestContext.INBOUND_HEADERS_CONTEXT_KEY, fields);
    }

    private static Path root() {
        Path path = Path.of("").toAbsolutePath();
        while (!Files.isDirectory(path.resolve("roadmaps/v31"))) { path = path.getParent(); }
        return path;
    }
}
