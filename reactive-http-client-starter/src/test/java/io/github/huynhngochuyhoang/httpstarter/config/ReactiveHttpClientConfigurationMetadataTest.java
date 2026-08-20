package io.github.huynhngochuyhoang.httpstarter.config;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
            "reactive.http.client.requests",
            "reactive.http.client.connection.pool");

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
                "reactive.http.observability.diagnostics-endpoint.enabled",
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
    void observerBodyOptionsDescribeEventGatesInsteadOfBuiltInSpanEvents() throws IOException {
        JsonNode metadata = starterMetadata();

        assertThat(findProperty(metadata, "reactive.http.observability.log-request-body")
                .path("description").asText())
                .contains("terminal HttpClientObserverEvent", "custom observers")
                .contains("Built-in Micrometer and OpenTelemetry observers do not export it")
                .doesNotContain("body in span events");
        assertThat(findProperty(metadata, "reactive.http.observability.log-response-body")
                .path("description").asText())
                .contains("decoded success response body", "terminal HttpClientObserverEvent")
                .contains("Built-in Micrometer and OpenTelemetry observers do not export it")
                .doesNotContain("body in span events");
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
    void configurationMetadataGroupSourceTypesResolve() throws IOException {
        List<String> invalidSourceTypes = new ArrayList<>();
        for (Path metadataFile : metadataFiles(projectRoot())) {
            JsonNode metadata = metadata(metadataFile);
            for (JsonNode group : metadata.path("groups")) {
                String sourceType = group.path("sourceType").asText("");
                if (sourceType.isBlank()) {
                    continue;
                }
                try {
                    Class.forName(sourceType);
                }
                catch (ReflectiveOperationException ex) {
                    invalidSourceTypes.add(projectRoot().relativize(metadataFile) + " -> "
                            + group.path("name").asText() + " uses " + sourceType);
                }
            }
        }

        assertThat(invalidSourceTypes).as("metadata group source types that cannot be resolved").isEmpty();
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
    void configurationMetadataGroupSourceMethodsReturnDeclaredGroupTypes() throws IOException {
        List<String> mismatchedSourceMethods = new ArrayList<>();
        for (Path metadataFile : metadataFiles(projectRoot())) {
            JsonNode metadata = metadata(metadataFile);
            for (JsonNode group : metadata.path("groups")) {
                String sourceType = group.path("sourceType").asText("");
                String sourceMethod = group.path("sourceMethod").asText("");
                String type = group.path("type").asText("");
                if (sourceType.isBlank() || sourceMethod.isBlank() || type.isBlank()) {
                    continue;
                }
                String methodName = sourceMethod.replaceFirst("\\(.*\\)$", "");
                try {
                    Class<?> declaredType = Class.forName(rawClassName(type));
                    Class<?> returnType = Class.forName(sourceType).getMethod(methodName).getReturnType();
                    if (!declaredType.isAssignableFrom(returnType)) {
                        mismatchedSourceMethods.add(projectRoot().relativize(metadataFile) + " -> "
                                + group.path("name").asText() + " declares " + type
                                + " but " + sourceType + "#" + sourceMethod + " returns " + returnType.getName());
                    }
                }
                catch (ReflectiveOperationException ex) {
                    mismatchedSourceMethods.add(projectRoot().relativize(metadataFile) + " -> "
                            + group.path("name").asText() + " uses " + sourceType + "#" + sourceMethod);
                }
            }
        }

        assertThat(mismatchedSourceMethods).as("metadata group source methods returning unexpected types").isEmpty();
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
    void generatedConfigurationReferenceDocumentsEveryMetadataProperty() throws IOException {
        String reference = Files.readString(projectRoot().resolve("docs/configuration-properties.md"));
        Set<String> missing = new TreeSet<>();

        for (String property : allMetadataPropertyNames(projectRoot())) {
            if (!reference.contains("`" + property + "`")) {
                missing.add(property);
            }
        }

        assertThat(missing).as("metadata properties missing from generated configuration reference").isEmpty();
    }

    @Test
    void documentedConfigurationExamplesUseMetadataPropertyNames() throws IOException {
        Set<String> metadataNames = allMetadataPropertyNames(projectRoot());
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
                "reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.request-timeout-ms",
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
    void effectiveConfigurationExamplesCoverV16ScenariosAndUseStarterMetadata() throws IOException {
        Path examples = projectRoot().resolve("docs/examples/effective-configuration.md");
        Set<String> exampleProperties = configurationExampleProperties(Files.readAllLines(examples));
        Set<String> missing = new TreeSet<>(exampleProperties);
        missing.removeAll(allMetadataPropertyNames(projectRoot()));

        assertThat(missing).as("effective configuration examples missing from metadata").isEmpty();
        assertThat(exampleProperties).contains(
                "reactive.http.clients.[name].base-url",
                "reactive.http.clients.[name].request-timeout-ms",
                "reactive.http.clients.[name].follow-redirects",
                "reactive.http.clients.[name].apis.[api-name].method",
                "reactive.http.clients.[name].apis.[api-name].path",
                "reactive.http.clients.[name].apis.[api-name].timeout-ms",
                "reactive.http.clients.[name].auth.type",
                "reactive.http.clients.[name].auth.oauth2-client-credentials.token-uri",
                "reactive.http.clients.[name].auth.oauth2-client-credentials.client-id",
                "reactive.http.clients.[name].auth.oauth2-client-credentials.client-secret",
                "reactive.http.clients.[name].auth.oauth2-client-credentials.auth-style",
                "reactive.http.clients.[name].auth.oauth2-client-credentials.expiry-leeway-ms",
                "reactive.http.clients.[name].auth.aws-sig-v4.access-key-id",
                "reactive.http.clients.[name].auth.aws-sig-v4.secret-access-key",
                "reactive.http.clients.[name].auth.aws-sig-v4.session-token",
                "reactive.http.clients.[name].auth.aws-sig-v4.region",
                "reactive.http.clients.[name].auth.aws-sig-v4.service",
                "reactive.http.clients.[name].auth.aws-sig-v4.strict-body-signing-validation",
                "reactive.http.clients.[name].default-headers",
                "reactive.http.clients.[name].proxy.type",
                "reactive.http.clients.[name].tls.trust-store",
                "reactive.http.clients.[name].tls.trust-store-password",
                "reactive.http.clients.[name].tls.key-store",
                "reactive.http.clients.[name].tls.key-store-password",
                "reactive.http.clients.[name].tls.protocols",
                "reactive.http.clients.[name].tls.ciphers",
                "reactive.http.clients.[name].resilience.enabled",
                "reactive.http.clients.[name].resilience.retry",
                "reactive.http.clients.[name].resilience.retry-methods",
                "reactive.http.clients.[name].resilience.strict-unsafe-retry-validation",
                "reactive.http.network.proxy.type",
                "reactive.http.network.proxy.host",
                "reactive.http.network.proxy.port",
                "reactive.http.network.proxy.username",
                "reactive.http.network.proxy.password",
                "reactive.http.network.proxy.non-proxy-hosts",
                "reactive.http.network.tls.trust-store",
                "reactive.http.network.tls.trust-store-password",
                "reactive.http.network.tls.trust-store-type",
                "reactive.http.observability.diagnostics-endpoint.enabled"
        );
        assertThat(exampleProperties).noneMatch(property -> property.startsWith("reactive.http.observability.otel"));
    }

    @Test
    void productionPolicyExampleUsesSafePlaceholdersAndStarterMetadata() throws IOException {
        Path examples = projectRoot().resolve("docs/examples/production-policy.md");
        String markdown = Files.readString(examples);
        Set<String> exampleProperties = configurationExampleProperties(Files.readAllLines(examples));
        Set<String> missing = new TreeSet<>(exampleProperties);
        missing.removeAll(allMetadataPropertyNames(projectRoot()));

        assertThat(missing).as("production policy example properties missing from metadata").isEmpty();
        assertThat(exampleProperties).contains(
                "reactive.http.observability.diagnostics-endpoint.enabled",
                "reactive.http.clients.[name].base-url",
                "reactive.http.clients.[name].request-timeout-ms",
                "reactive.http.clients.[name].apis.[api-name].method",
                "reactive.http.clients.[name].apis.[api-name].path",
                "reactive.http.clients.[name].apis.[api-name].timeout-ms",
                "reactive.http.clients.[name].log-exchange",
                "reactive.http.clients.[name].log-preset",
                "reactive.http.clients.[name].auth.type",
                "reactive.http.clients.[name].auth.oauth2-client-credentials.token-uri",
                "reactive.http.clients.[name].auth.oauth2-client-credentials.client-id",
                "reactive.http.clients.[name].auth.oauth2-client-credentials.client-secret",
                "reactive.http.clients.[name].auth.oauth2-client-credentials.auth-style",
                "reactive.http.clients.[name].auth.oauth2-client-credentials.expiry-leeway-ms",
                "reactive.http.clients.[name].resilience.enabled",
                "reactive.http.clients.[name].resilience.retry",
                "reactive.http.clients.[name].resilience.retry-methods",
                "reactive.http.clients.[name].resilience.strict-unsafe-retry-validation",
                "reactive.http.clients.[name].default-headers",
                "reactive.http.clients.[name].auth.aws-sig-v4.access-key-id",
                "reactive.http.clients.[name].auth.aws-sig-v4.secret-access-key",
                "reactive.http.clients.[name].auth.aws-sig-v4.session-token",
                "reactive.http.clients.[name].auth.aws-sig-v4.region",
                "reactive.http.clients.[name].auth.aws-sig-v4.service",
                "reactive.http.clients.[name].auth.aws-sig-v4.strict-body-signing-validation");
        assertThat(markdown)
                .contains("@ReactiveHttpClient(name = \"bus-orders\")")
                .contains("@ReactiveHttpClient(name = \"train-orders\")")
                .contains("@ApiRef(\"orders-get\")")
                .contains("@IdempotencyKey")
                .contains("strict-unsafe-retry-validation: true")
                .contains("strict-body-signing-validation: true")
                .contains("diagnostics/rhttpclients.json")
                .contains("health/health.json")
                .contains("${EXAMPLE_PAYMENT_CLIENT_SECRET}")
                .contains("${EXAMPLE_AWS_SECRET_ACCESS_KEY}")
                .contains(".example.invalid")
                .doesNotContain("api.example.com")
                .doesNotContain("localhost")
                .doesNotContain("Bearer ");
    }

    @Test
    void supportBundleExamplesUseSafePlaceholdersAndStarterMetadata() throws IOException {
        Path supportBundleDocs = projectRoot().resolve("docs/26-support-bundles.md");
        String markdown = Files.readString(supportBundleDocs);
        String fixture = markdownSection(markdown, "## Reviewable Bundle Fixture", "## Diagnostics Snapshot");
        String captureRecipes = markdownSection(markdown, "## Capture Recipes", "## Health Details");
        Set<String> exampleProperties = configurationExampleProperties(Arrays.asList(fixture.split("\\R")));
        Set<String> missing = new TreeSet<>(exampleProperties);
        missing.removeAll(allMetadataPropertyNames(projectRoot()));

        assertThat(missing).as("support-bundle example properties missing from metadata").isEmpty();
        assertThat(exampleProperties).contains(
                "reactive.http.observability.diagnostics-endpoint.enabled",
                "reactive.http.clients.[name].base-url",
                "reactive.http.clients.[name].request-timeout-ms",
                "reactive.http.clients.[name].follow-redirects",
                "reactive.http.clients.[name].log-exchange",
                "reactive.http.clients.[name].log-preset",
                "reactive.http.clients.[name].resilience.enabled",
                "reactive.http.clients.[name].resilience.retry",
                "reactive.http.clients.[name].resilience.retry-methods");
        assertThat(fixture)
                .contains("diagnostics/rhttpclients.json")
                .contains("health/health.json")
                .contains("logs/startup-summary.log")
                .contains("logs/exchange-metadata.log")
                .contains("config/reactive-http-client.yml")
                .contains("performance/benchmark-report-link.txt")
                .contains("inventory-api.example.invalid")
                .contains("docs/benchmark-report-<version>.md")
                .contains("ReactiveHttpClientFactoryBean")
                .contains("DefaultHttpExchangeLogger");
        assertThat(captureRecipes)
                .contains("### Local JVM Capture")
                .contains("### Container Capture")
                .contains("### Kubernetes-Style Capture")
                .contains("EXAMPLE_MANAGEMENT_URL=\"http://<management-host>:<management-port>\"")
                .contains("EXAMPLE_CONTAINER=\"example-app-container\"")
                .contains("EXAMPLE_NAMESPACE=\"example-namespace\"")
                .contains("EXAMPLE_MANAGEMENT_PORT=\"<management-port>\"")
                .contains("$EXAMPLE_LOCAL_PORT:$EXAMPLE_MANAGEMENT_PORT")
                .contains("curl -sS \"$EXAMPLE_MANAGEMENT_URL/actuator/health\"")
                .contains("docker logs \"$EXAMPLE_CONTAINER\" --since 30m")
                .contains("kubectl -n \"$EXAMPLE_NAMESPACE\" logs \"$EXAMPLE_POD\"")
                .contains("kubectl -n \"$EXAMPLE_NAMESPACE\" exec \"$EXAMPLE_POD\"")
                .contains("-- cat \"$EXAMPLE_SANITIZED_CONFIG_IN_POD\"")
                .contains("does not require `tar`")
                .contains("If the image also lacks `cat`")
                .contains("diagnostics/rhttpclients.json")
                .contains("health/health.json")
                .contains("logs/startup-summary.log")
                .contains("logs/exchange-metadata.log")
                .contains("config/reactive-http-client.yml")
                .contains("performance/benchmark-report-link.txt")
                .contains("Which clients and endpoints exist")
                .contains("Whether recent Micrometer samples crossed")
                .contains("Which sanitized client policy was applied")
                .contains("What happened for the affected calls")
                .contains("Which `reactive.http.*` settings")
                .contains("Which promoted source-controlled report supports")
                .contains("Do not merge them into a single free-form log dump")
                .contains("placeholders in shared examples");
        assertThat(fixture)
                .doesNotContain("Authorization")
                .doesNotContain("Cookie")
                .doesNotContain("client-secret");
        assertThat(fixture + captureRecipes)
                .doesNotContain("Bearer ")
                .doesNotContain("access-token")
                .doesNotContain("requestBody")
                .doesNotContain("responseBody")
                .doesNotContain("localhost")
                .doesNotContain(".com")
                .doesNotContain(".net")
                .doesNotContain(".org");
    }


    @Test
    void documentedConfigurationExampleExtractionRejectsGroupsAndMalformedApiMapLeaves() throws IOException {
        Set<String> metadataNames = allMetadataPropertyNames(projectRoot());
        Set<String> exampleProperties = configurationExampleProperties(List.of(
                "```yaml",
                "reactive:",
                "  http:",
                "    clients:",
                "      user-service:",
                "        proxy: http://proxy.example",
                "        apis:",
                "          timeout-ms: 1000",
                "```",
                "```properties",
                "reactive.http.clients.user-service.proxy=http://proxy.example",
                "```"));
        Set<String> missing = new TreeSet<>(exampleProperties);
        missing.removeAll(metadataNames);

        assertThat(missing).contains(
                "reactive.http.clients.[name].proxy",
                "reactive.http.clients.[name].apis.timeout-ms"
        );
    }

    @Test
    void documentedConfigurationExampleExtractionIncludesYamlBlockListProperties() throws IOException {
        Set<String> metadataNames = allMetadataPropertyNames(projectRoot());
        Set<String> exampleProperties = configurationExampleProperties(List.of(
                "```yaml",
                "reactive:",
                "  http:",
                "    network:",
                "      tls:",
                "        ciphers:",
                "          - TLS_AES_256_GCM_SHA384",
                "        cipherz:",
                "          - TLS_AES_128_GCM_SHA256",
                "```"));
        Set<String> missing = new TreeSet<>(exampleProperties);
        missing.removeAll(metadataNames);

        assertThat(exampleProperties).contains("reactive.http.network.tls.ciphers");
        assertThat(missing).contains("reactive.http.network.tls.cipherz");
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
        assertDefaultValue(metadata, "reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.connect-timeout-ms", 2000);
        assertDefaultValue(metadata, "reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.request-timeout-ms", 0);
        assertDefaultValue(metadata, "reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.max-connections", 2);
        assertDefaultValue(metadata, "reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.pending-acquire-timeout-ms", 5000);
        assertDefaultValue(metadata, "reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.retry-max-attempts", 1);
        assertDefaultValue(metadata, "reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.retry-backoff-ms", 100);
        assertDefaultValue(metadata, "reactive.http.clients.[name].auth.aws-sig-v4.strict-body-signing-validation", false);
        assertDefaultValue(metadata, "reactive.http.clients.[name].resilience.enabled", false);
        assertDefaultValue(metadata, "reactive.http.clients.[name].resilience.retry", "default");
        assertDefaultValue(metadata, "reactive.http.clients.[name].resilience.circuit-breaker", "default");
        assertDefaultValue(metadata, "reactive.http.clients.[name].resilience.bulkhead", "default");
        assertDefaultValue(metadata, "reactive.http.clients.[name].resilience.rate-limiter", "default");
        assertDefaultValue(metadata, "reactive.http.clients.[name].resilience.retry-methods", List.of("GET", "HEAD"));
        assertDefaultValue(metadata, "reactive.http.clients.[name].resilience.strict-unsafe-retry-validation", false);
        assertDefaultValue(metadata, "reactive.http.clients.[name].resilience.timeout-ms", 0);
        assertDefaultValue(metadata, "reactive.http.observability.enabled", true);
        assertDefaultValue(metadata, "reactive.http.observability.metric-name", "reactive.http.client.requests");
        assertDefaultValue(metadata, "reactive.http.observability.include-url-path", false);
        assertDefaultValue(metadata, "reactive.http.observability.include-server-address", false);
        assertDefaultValue(metadata, "reactive.http.observability.log-request-body", false);
        assertDefaultValue(metadata, "reactive.http.observability.log-response-body", false);
        assertDefaultValue(metadata, "reactive.http.observability.health.enabled", true);
        assertDefaultValue(metadata, "reactive.http.observability.diagnostics-endpoint.enabled", false);
        assertDefaultValue(metadata, "reactive.http.observability.health.error-rate-threshold", 0.5);
        assertDefaultValue(metadata, "reactive.http.observability.health.min-samples", 10);
        assertDefaultValue(metadata, "reactive.http.observability.histogram.enabled", false);
        assertDefaultValue(metadata, "reactive.http.observability.histogram.slo-boundaries-ms", List.of(50, 100, 200, 500, 1000, 2000, 5000));
        assertDefaultValue(metadata, "reactive.http.correlation-id.max-length", 128);
        assertDefaultValue(metadata, "reactive.http.correlation-id.mdc-keys", List.of("correlationId", "X-Correlation-Id", "traceId"));
    }

    private static String markdownSection(String markdown, String heading, String nextHeading) {
        int start = markdown.indexOf(heading);
        if (start < 0) {
            throw new IllegalStateException("Missing Markdown section " + heading);
        }
        int bodyStart = markdown.indexOf('\n', start);
        if (bodyStart < 0) {
            throw new IllegalStateException("Markdown section has no body " + heading);
        }
        int end = markdown.indexOf(nextHeading, bodyStart);
        if (end < 0) {
            throw new IllegalStateException("Missing next Markdown section " + nextHeading);
        }
        return markdown.substring(bodyStart + 1, end);
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

    private static Set<String> allMetadataPropertyNames(Path root) throws IOException {
        Set<String> names = new TreeSet<>();
        for (Path metadataFile : allMetadataFiles(root)) {
            names.addAll(propertyNames(metadata(metadataFile)));
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

    private static String rawClassName(String type) {
        int genericStart = type.indexOf("<");
        return genericStart < 0 ? type : type.substring(0, genericStart);
    }

    private static String normalizeDocumentedProperty(String raw) {
        return normalizeDocumentedProperty(raw, true);
    }

    private static String normalizeConfigurationExampleProperty(String raw) {
        return normalizeDocumentedProperty(raw, false);
    }

    private static String normalizeDocumentedProperty(String raw, boolean allowApiMapContainer) {
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
        value = value.replaceAll("\\.apis\\.<api>(?=\\.|$)", ".apis.[api-name]");
        value = value.replaceAll("\\.apis\\.\\[[^]]+](?=\\.)", ".apis.[api-name]");
        value = value.replaceAll("\\.apis\\.[^.]+(?=\\.)", ".apis.[api-name]");
        if (allowApiMapContainer) {
            value = value.replaceAll("\\.apis\\[[^]]+]$", ".apis");
            value = value.replaceAll("\\.apis\\.\\[[^]]+]$", ".apis");
            value = value.replaceAll("\\.apis\\.[^.]+$", ".apis");
        }
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
                if (!stack.isEmpty()) {
                    addConfigurationExampleProperty(properties, stack.get(stack.size() - 1).path());
                }
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
            String value = trimmed.substring(separator + 1).trim();
            if (value.isBlank()) {
                stack.add(new YamlPath(indent, path));
            }
            else {
                addConfigurationExampleProperty(properties, path);
            }
        }
        return properties;
    }

    private static void addConfigurationExampleProperty(Set<String> properties, String raw) {
        if (!raw.startsWith("reactive.http")) {
            return;
        }
        String normalized = normalizeConfigurationExampleProperty(raw);
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
