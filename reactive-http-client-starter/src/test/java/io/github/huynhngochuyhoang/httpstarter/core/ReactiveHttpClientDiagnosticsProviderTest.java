package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.PathVar;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.annotation.Retry;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProviderFactory;
import io.github.huynhngochuyhoang.httpstarter.auth.AwsSigV4AuthProviderFactory;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.core.Ordered;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReactiveHttpClientDiagnosticsProviderTest {

    private static final Set<String> ROOT_SCHEMA_FIELDS = Set.of(
            "schemaVersion", "projectVersion", "clientCount", "endpointCount",
            "inheritedEndpointCount", "clients");
    private static final Set<String> CLIENT_SCHEMA_FIELDS = Set.of(
            "clientName", "clientInterface", "baseUrlSource", "poolSource",
            "poolMaxConnections", "poolPendingAcquireTimeoutMs", "poolMetricsEnabled",
            "poolProtocol", "poolCapacityBasis", "poolMaxConcurrentStreams", "timeoutSource", "timeoutMs", "logicalCallTimeoutMs",
            "compressionEnabled", "codecMaxInMemorySizeMb",
            "resilienceConfigured", "retry", "rateLimiter",
            "circuitBreaker", "bulkhead", "strictUnsafeRetryValidation",
            "strictBodySigningValidation", "authMode", "followRedirects", "endpointCount",
            "inheritedEndpointCount");

    @Test
    void reportsSanitizedSummariesForRegisteredClients() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(ReactiveHttpClientFactoryBean.class);
        definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, DiagnosticClient.class);
        beanFactory.registerBeanDefinition("diagnosticClient", definition);

        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setBaseUrl("https://user:token@example.com");
        config.setRequestTimeoutMs(500);
        config.setLogicalCallTimeoutMs(2_000);
        config.setAuthProvider("secretAuthProviderBean");
        config.setDefaultHeaders(Map.of("Authorization", "Bearer secret-token"));
        config.setFollowRedirects(true);
        ReactiveHttpClientProperties.ResilienceConfig resilience = new ReactiveHttpClientProperties.ResilienceConfig();
        resilience.setEnabled(true);
        config.setResilience(resilience);
        properties.setClients(Map.of("diagnostic-client", config));

        ReactiveHttpClientDiagnosticsProvider provider = new ReactiveHttpClientDiagnosticsProvider(
                beanFactory, properties, new MethodMetadataCache());

        List<ReactiveHttpClientDiagnosticsProvider.ClientSummary> summaries = provider.clientSummaries();

        assertThat(summaries).hasSize(1);
        ReactiveHttpClientDiagnosticsProvider.ClientSummary summary = summaries.get(0);
        assertThat(summary.clientName()).isEqualTo("diagnostic-client");
        assertThat(summary.clientInterface()).isEqualTo(DiagnosticClient.class.getName());
        assertThat(summary.baseUrlSource()).isEqualTo("property");
        assertThat(summary.timeout()).isEqualTo(new ReactiveHttpClientDiagnosticsProvider.TimeoutSummary("client", 500));
        assertThat(summary.resilience().configured()).isTrue();
        assertThat(summary.resilience().retry()).isEqualTo("unavailable");
        assertThat(summary.authMode()).isEqualTo("provider-bean");
        assertThat(summary.followRedirects()).isTrue();
        assertThat(summary.endpointCount()).isEqualTo(2);
        assertThat(summary.inheritedEndpointCount()).isEqualTo(1);
        assertThat(summary.toString())
                .doesNotContain("user:token")
                .doesNotContain("secretAuthProviderBean")
                .doesNotContain("secret-token")
                .doesNotContain("Authorization");
    }

    @Test
    void discoversClientFromAotPreservedFactoryTypeProperty() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(ReactiveHttpClientFactoryBean.class);
        definition.getPropertyValues().add("type", DiagnosticClient.class);
        beanFactory.registerBeanDefinition("diagnosticClient", definition);

        ReactiveHttpClientDiagnosticsProvider provider = new ReactiveHttpClientDiagnosticsProvider(
                beanFactory, new ReactiveHttpClientProperties(), new MethodMetadataCache());

        assertThat(provider.clientSummaries())
                .singleElement()
                .extracting(ReactiveHttpClientDiagnosticsProvider.ClientSummary::clientInterface)
                .isEqualTo(DiagnosticClient.class.getName());
    }

    @Test
    void ignoresTypePropertyOnUnrelatedBeanDefinition() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(Object.class);
        definition.getPropertyValues().add("type", DiagnosticClient.class);
        beanFactory.registerBeanDefinition("unrelatedBean", definition);

        ReactiveHttpClientDiagnosticsProvider provider = new ReactiveHttpClientDiagnosticsProvider(
                beanFactory, new ReactiveHttpClientProperties(), new MethodMetadataCache());

        assertThat(provider.clientSummaries()).isEmpty();
    }

    @Test
    void clientSummariesDoNotResolveStrictAuthProviders() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(ReactiveHttpClientFactoryBean.class);
        definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, DiagnosticClient.class);
        beanFactory.registerBeanDefinition("diagnosticClient", definition);
        beanFactory.registerSingleton("throwingAuthProviderFactory", new ThrowingAwsSigV4Factory());

        ReactiveHttpClientProperties.ClientConfig config = sensitiveClientConfig();
        config.setAuthProvider(null);
        ReactiveHttpClientProperties.AuthConfig auth = new ReactiveHttpClientProperties.AuthConfig();
        auth.setType(AwsSigV4AuthProviderFactory.TYPE);
        auth.getAwsSigV4().setStrictBodySigningValidation(true);
        config.setAuth(auth);
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.setClients(Map.of("diagnostic-client", config));
        ReactiveHttpClientDiagnosticsProvider provider = new ReactiveHttpClientDiagnosticsProvider(
                beanFactory, properties, new MethodMetadataCache());

        List<ReactiveHttpClientDiagnosticsProvider.ClientSummary> summaries = provider.clientSummaries();

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).authMode()).isEqualTo("aws-sigv4");
    }

    @Test
    void providerSnapshotsDoNotResolveStrictAuthProviders() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(ReactiveHttpClientFactoryBean.class);
        definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, DiagnosticClient.class);
        beanFactory.registerBeanDefinition("diagnosticClient", definition);
        beanFactory.registerSingleton("throwingAuthProviderFactory", new ThrowingAwsSigV4Factory());

        ReactiveHttpClientProperties.ClientConfig config = sensitiveClientConfig();
        config.setAuthProvider(null);
        ReactiveHttpClientProperties.AuthConfig auth = new ReactiveHttpClientProperties.AuthConfig();
        auth.setType(AwsSigV4AuthProviderFactory.TYPE);
        auth.getAwsSigV4().setStrictBodySigningValidation(true);
        config.setAuth(auth);
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.setClients(Map.of("diagnostic-client", config));
        ReactiveHttpClientDiagnosticsProvider provider = new ReactiveHttpClientDiagnosticsProvider(
                beanFactory, properties, new MethodMetadataCache());

        Map<String, Object> snapshot = ReactiveHttpClientDiagnosticsSnapshot.toMap(provider);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> clients = (List<Map<String, Object>>) snapshot.get("clients");
        assertThat(clients).hasSize(1);
        assertThat(clients.get(0))
                .containsEntry("authMode", "aws-sigv4")
                .containsEntry("strictBodySigningValidation", false);
    }

    @Test
    void providerSnapshotsDoNotInstantiateLazyClientFactories() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(ReactiveHttpClientFactoryBean.class);
        definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, DiagnosticClient.class);
        definition.setLazyInit(true);
        beanFactory.registerBeanDefinition("diagnosticClient", definition);
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.setClients(Map.of("diagnostic-client", new ReactiveHttpClientProperties.ClientConfig()));
        ReactiveHttpClientDiagnosticsProvider provider = new ReactiveHttpClientDiagnosticsProvider(
                beanFactory, properties, new MethodMetadataCache());

        Map<String, Object> snapshot = ReactiveHttpClientDiagnosticsSnapshot.toMap(provider);

        assertThat(snapshot).containsEntry("clientCount", 1);
        assertThat(beanFactory.containsSingleton("diagnosticClient")).isFalse();
    }

    @Test
    void providerSnapshotsUseDefaultPoolWhenNetworkConfigurationIsNull() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(ReactiveHttpClientFactoryBean.class);
        definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, DiagnosticClient.class);
        beanFactory.registerBeanDefinition("diagnosticClient", definition);

        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.setNetwork(null);
        properties.setClients(Map.of("diagnostic-client", new ReactiveHttpClientProperties.ClientConfig()));
        ReactiveHttpClientDiagnosticsProvider provider = new ReactiveHttpClientDiagnosticsProvider(
                beanFactory, properties, new MethodMetadataCache());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> clients = (List<Map<String, Object>>)
                ReactiveHttpClientDiagnosticsSnapshot.toMap(provider).get("clients");

        assertThat(clients).singleElement().satisfies(client -> assertThat(client)
                .containsEntry("poolSource", "global")
                .containsEntry("poolMaxConnections", 200)
                .containsEntry("poolPendingAcquireTimeoutMs", 5000L)
                .containsEntry("poolMetricsEnabled", false)
                .containsEntry("poolProtocol", "HTTP/1.1")
                .containsEntry("poolCapacityBasis", "connections")
                .containsEntry("poolMaxConcurrentStreams", null));
    }

    @Test
    void reportsHttp2ConnectionAndPeerStreamCapacityWithoutInventingALimit() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(ReactiveHttpClientFactoryBean.class);
        definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, DiagnosticClient.class);
        beanFactory.registerBeanDefinition("diagnosticClient", definition);

        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setHttp2Enabled(true);
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.setClients(Map.of("diagnostic-client", config));
        ReactiveHttpClientDiagnosticsProvider provider = new ReactiveHttpClientDiagnosticsProvider(
                beanFactory, properties, new MethodMetadataCache());

        Map<String, Object> client = firstClient(ReactiveHttpClientDiagnosticsSnapshot.toMap(provider));

        assertThat(client)
                .containsEntry("poolProtocol", "HTTP/2")
                .containsEntry("poolCapacityBasis", "connections-and-peer-streams")
                .containsEntry("poolMaxConcurrentStreams", null);
        assertThat(client.toString()).doesNotContain("example.com", "127.0.0.1");
    }

    @Test
    void rendersSanitizedDiagnosticsSnapshot() {
        ReactiveHttpClientDiagnosticsProvider provider = sensitiveDiagnosticsProvider();

        String markdown = ReactiveHttpClientDiagnosticsSnapshot.toMarkdown(provider);
        String json = ReactiveHttpClientDiagnosticsSnapshot.toJson(provider);

        assertThat(markdown)
                .startsWith("# Reactive HTTP Client Diagnostics Snapshot")
                .contains("| Schema version | `1` |")
                .contains("| Project version | `")
                .contains("| Client count | `1` |")
                .contains("| Endpoint count | `2` |")
                .contains("| Inherited endpoint count | `1` |")
                .contains("Strict retry validation")
                .contains("Strict body-signing validation")
                .contains("| `diagnostic-client` | `" + DiagnosticClient.class.getName() + "` | `property` | `global:maxConnections=200, pendingAcquireTimeoutMs=5000, metrics=false, protocol=HTTP/1.1, capacity=connections, maxConcurrentStreams=unknown` | `client:500` |")
                .contains("configured=true, retry=unavailable")
                .contains("| `provider-bean` | `true` | `2` | `1` |");
        assertThat(json)
                .contains("\"schemaVersion\": 1")
                .contains("\"projectVersion\":")
                .contains("\"clientCount\": 1")
                .contains("\"endpointCount\": 2")
                .contains("\"inheritedEndpointCount\": 1")
                .contains("\"clientName\": \"diagnostic-client\"")
                .contains("\"clientInterface\": \"" + DiagnosticClient.class.getName() + "\"")
                .contains("\"baseUrlSource\": \"property\"")
                .contains("\"timeoutSource\": \"client\"")
                .contains("\"timeoutMs\": 500")
                .contains("\"strictUnsafeRetryValidation\": false")
                .contains("\"strictBodySigningValidation\": false")
                .contains("\"authMode\": \"provider-bean\"")
                .contains("\"followRedirects\": true");
        assertThat(markdown + json)
                .doesNotContain("https://user:token@example.com")
                .doesNotContain("user:token")
                .doesNotContain("secretAuthProviderBean")
                .doesNotContain("secret-token")
                .doesNotContain("Authorization");
    }

    @Test
    void rendersDeterministicSupportBundleDiagnosticsFixture() throws Exception {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(ReactiveHttpClientFactoryBean.class);
        definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, SupportBundleClient.class);
        beanFactory.registerBeanDefinition("supportBundleClient", definition);

        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setBaseUrl("https://inventory-api.example.invalid");
        config.setRequestTimeoutMs(750);
        config.setCompressionEnabled(true);
        config.setCodecMaxInMemorySizeMb(4);
        config.setAuthProvider("internalSupportAuthProvider");
        config.setDefaultHeaders(Map.of(
                "Authorization", "Bearer raw-token",
                "Cookie", "session=raw-cookie"));
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.setClients(Map.of("support-inventory", config));
        ReactiveHttpClientDiagnosticsProvider provider = new ReactiveHttpClientDiagnosticsProvider(
                beanFactory, properties, new MethodMetadataCache());

        String json = ReactiveHttpClientDiagnosticsSnapshot.toJson(provider)
                .replaceFirst("\"projectVersion\": \"[^\"]+\"", "\"projectVersion\": \"<project-version>\"")
                .replace(SupportBundleClient.class.getName(), "<client-interface>");

        assertThat(json).isEqualTo("""
                {
                  "schemaVersion": 1,
                  "projectVersion": "<project-version>",
                  "clientCount": 1,
                  "endpointCount": 2,
                  "inheritedEndpointCount": 1,
                  "clients": [
                    {
                      "clientName": "support-inventory",
                      "clientInterface": "%s",
                      "baseUrlSource": "property",
                      "poolSource": "global",
                      "poolMaxConnections": 200,
                      "poolPendingAcquireTimeoutMs": 5000,
                      "poolMetricsEnabled": false,
                      "poolProtocol": "HTTP/1.1",
                      "poolCapacityBasis": "connections",
                      "poolMaxConcurrentStreams": null,
                      "timeoutSource": "client",
                      "timeoutMs": 750,
                      "logicalCallTimeoutMs": 0,
                      "compressionEnabled": true,
                      "codecMaxInMemorySizeMb": 4,
                      "resilienceConfigured": false,
                      "retry": "disabled",
                      "rateLimiter": "disabled",
                      "circuitBreaker": "disabled",
                      "bulkhead": "disabled",
                      "strictUnsafeRetryValidation": false,
                      "strictBodySigningValidation": false,
                      "authMode": "provider-bean",
                      "followRedirects": false,
                      "endpointCount": 2,
                      "inheritedEndpointCount": 1
                    }
                  ]
                }
                """.formatted("<client-interface>"));
        Path fixture = Path.of("..", "docs", "fixtures", "rhttpclients-schema-v1.json");
        String fixtureJson = Files.readString(fixture);
        assertThat(json).isEqualTo(fixtureJson);
        assertThat(fixtureJson)
                .doesNotContain("Bearer", "Authorization", "Cookie", "client-secret")
                .doesNotContain("/home/", "/Users/", "/workspace/", "/tmp/", "C:\\Users\\");
        assertThat(json)
                .doesNotContain("inventory-api.example.invalid")
                .doesNotContain("internalSupportAuthProvider")
                .doesNotContain("raw-token")
                .doesNotContain("raw-cookie")
                .doesNotContain("Authorization")
                .doesNotContain("Cookie");
    }

    @Test
    void keepsPublishedSchemaV1KeysAndJsonValueKindsCompatible() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode published = mapper.readTree(Path.of("..", "docs", "fixtures",
                "rhttpclients-schema-v1-3.2.0.json").toFile());
        JsonNode current = mapper.readTree(Path.of("..", "docs", "fixtures",
                "rhttpclients-schema-v1.json").toFile());

        assertCompatibleSchema(published, current, "root");
    }

    @Test
    void keepsProviderCollectionJsonAndMarkdownOnSchemaV1() throws Exception {
        ReactiveHttpClientDiagnosticsProvider provider = sensitiveDiagnosticsProvider();
        Map<String, Object> providerMap = ReactiveHttpClientDiagnosticsSnapshot.toMap(provider);
        Map<String, Object> collectionMap = ReactiveHttpClientDiagnosticsSnapshot.toMap(provider.clientSummaries());
        ObjectMapper mapper = new ObjectMapper();
        String providerJson = mapper.readTree(
                ReactiveHttpClientDiagnosticsSnapshot.toJson(provider)).toString();
        String providerMapJson = mapper.valueToTree(providerMap).toString();

        assertThat(providerJson).isEqualTo(providerMapJson);
        assertThat(providerMap.keySet()).containsExactlyInAnyOrderElementsOf(ROOT_SCHEMA_FIELDS);
        assertThat(collectionMap.keySet()).containsExactlyInAnyOrderElementsOf(ROOT_SCHEMA_FIELDS);

        Map<String, Object> providerClient = firstClient(providerMap);
        Map<String, Object> collectionClient = firstClient(collectionMap);
        assertThat(providerClient.keySet()).containsExactlyInAnyOrderElementsOf(CLIENT_SCHEMA_FIELDS);
        assertThat(collectionClient.keySet()).containsExactlyInAnyOrderElementsOf(CLIENT_SCHEMA_FIELDS);
        assertThat(providerClient)
                .containsEntry("poolSource", "global")
                .containsEntry("poolMaxConnections", 200)
                .containsEntry("poolPendingAcquireTimeoutMs", 5000L)
                .containsEntry("poolMetricsEnabled", false)
                .containsEntry("poolProtocol", "HTTP/1.1")
                .containsEntry("poolCapacityBasis", "connections")
                .containsEntry("poolMaxConcurrentStreams", null)
                .containsEntry("logicalCallTimeoutMs", 2_000L)
                .containsEntry("compressionEnabled", false)
                .containsEntry("codecMaxInMemorySizeMb", 2)
                .containsEntry("strictUnsafeRetryValidation", false)
                .containsEntry("strictBodySigningValidation", false);
        assertThat(collectionClient)
                .containsEntry("poolSource", "unknown")
                .containsEntry("poolMaxConnections", null)
                .containsEntry("poolPendingAcquireTimeoutMs", null)
                .containsEntry("poolMetricsEnabled", null)
                .containsEntry("poolProtocol", "unknown")
                .containsEntry("poolCapacityBasis", "unknown")
                .containsEntry("poolMaxConcurrentStreams", null)
                .containsEntry("logicalCallTimeoutMs", null)
                .containsEntry("compressionEnabled", null)
                .containsEntry("codecMaxInMemorySizeMb", null)
                .containsEntry("strictUnsafeRetryValidation", null)
                .containsEntry("strictBodySigningValidation", null)
                .containsEntry("timeoutMs", 500L)
                .containsEntry("followRedirects", true);

        String markdown = ReactiveHttpClientDiagnosticsSnapshot.toMarkdown(provider.clientSummaries());
        assertThat(markdown)
                .contains("| Schema version | `1` |")
                .contains("| Client count | `1` |")
                .contains("| Endpoint count | `2` |")
                .contains("| Inherited endpoint count | `1` |")
                .contains("| Client | Interface | Base URL source | Pool | Response timeout | Logical-call budget | Compression | Decoded aggregate limit | Resilience | Strict retry validation | Strict body-signing validation | Auth mode | Redirects | Endpoints | Inherited endpoints |")
                .contains("| `diagnostic-client` | `" + DiagnosticClient.class.getName()
                        + "` | `property` | `unknown` | `client:500` | `unknown` | `unknown` | `unknown` |")
                .contains("| `unknown` | `unknown` | `provider-bean` | `true` | `2` | `1` |");
    }

    @Test
    void reportsStrictRetryValidationOnlyWhenRetryOperatorIsAvailable() {
        ReactiveHttpClientProperties.ClientConfig config = sensitiveClientConfig();
        ReactiveHttpClientProperties.ResilienceConfig resilience = new ReactiveHttpClientProperties.ResilienceConfig();
        resilience.setStrictUnsafeRetryValidation(true);
        config.setResilience(resilience);

        Map<String, Object> dormantClient = snapshotClient(config);

        assertThat(dormantClient)
                .containsEntry("resilienceConfigured", false)
                .containsEntry("strictUnsafeRetryValidation", false);

        resilience.setEnabled(true);
        Map<String, Object> noRetryOperatorClient = snapshotClient(config);

        assertThat(noRetryOperatorClient)
                .containsEntry("resilienceConfigured", true)
                .containsEntry("strictUnsafeRetryValidation", false);

        RetryRegistry singleAttemptRetryRegistry = RetryRegistry.of(
                RetryConfig.custom().maxAttempts(1).build());
        singleAttemptRetryRegistry.retry("default");
        Map<String, Object> singleAttemptClient = snapshotClient(config, singleAttemptRetryRegistry);

        assertThat(singleAttemptClient)
                .containsEntry("resilienceConfigured", true)
                .containsEntry("strictUnsafeRetryValidation", false);

        RetryRegistry retryRegistry = RetryRegistry.ofDefaults();
        retryRegistry.retry("default");
        Map<String, Object> retryOperatorClient = snapshotClient(config, retryRegistry);

        assertThat(retryOperatorClient)
                .containsEntry("resilienceConfigured", true)
                .containsEntry("strictUnsafeRetryValidation", true);
    }

    @Test
    void strictRetryDiagnosticsDoNotCreateMissingRetryInstances() {
        ReactiveHttpClientProperties.ClientConfig config = sensitiveClientConfig();
        ReactiveHttpClientProperties.ResilienceConfig resilience = new ReactiveHttpClientProperties.ResilienceConfig();
        resilience.setEnabled(true);
        resilience.setStrictUnsafeRetryValidation(true);
        config.setResilience(resilience);
        RetryRegistry retryRegistry = RetryRegistry.ofDefaults();

        Map<String, Object> client = snapshotClient(
                "diagnostic-missing-retry", DiagnosticMissingRetryClient.class, config, retryRegistry);

        assertThat(client)
                .containsEntry("clientName", "diagnostic-missing-retry")
                .containsEntry("strictUnsafeRetryValidation", null);
        assertThat(retryRegistry.find("ghost-retry")).isEmpty();
    }

    @Test
    void strictRetryDiagnosticsDoNotCreateDefaultRetryInstances() {
        ReactiveHttpClientProperties.ClientConfig config = sensitiveClientConfig();
        ReactiveHttpClientProperties.ResilienceConfig resilience = new ReactiveHttpClientProperties.ResilienceConfig();
        resilience.setEnabled(true);
        resilience.setStrictUnsafeRetryValidation(true);
        config.setResilience(resilience);
        RetryRegistry retryRegistry = RetryRegistry.ofDefaults();

        Map<String, Object> client = snapshotClient(config, retryRegistry);

        assertThat(client).containsEntry("strictUnsafeRetryValidation", null);
        assertThat(retryRegistry.getAllRetries()).isEmpty();
    }

    @Test
    void ignoresObjectAuthStrictBodySigningWhenNamedProviderIsSelected() {
        ReactiveHttpClientProperties.ClientConfig config = sensitiveClientConfig();
        config.setAuthProvider("customSigner");
        ReactiveHttpClientProperties.AuthConfig auth = new ReactiveHttpClientProperties.AuthConfig();
        auth.setType("aws-sigv4");
        auth.getAwsSigV4().setStrictBodySigningValidation(true);
        auth.getAwsSigV4().setAccessKeyId("access-key");
        auth.getAwsSigV4().setSecretAccessKey("secret-key");
        auth.getAwsSigV4().setRegion("us-east-1");
        auth.getAwsSigV4().setService("execute-api");
        config.setAuth(auth);

        Map<String, Object> client = snapshotClient(config);

        assertThat(client)
                .containsEntry("authMode", "provider-bean")
                .containsEntry("strictBodySigningValidation", false);
    }

    @Test
    void reportsStrictBodySigningOnlyForSelectedBuiltInAwsSigV4Factory() {
        ReactiveHttpClientProperties.ClientConfig config = sensitiveClientConfig();
        config.setAuthProvider(null);
        ReactiveHttpClientProperties.AuthConfig auth = new ReactiveHttpClientProperties.AuthConfig();
        auth.setType(AwsSigV4AuthProviderFactory.TYPE);
        auth.getAwsSigV4().setStrictBodySigningValidation(true);
        auth.getAwsSigV4().setAccessKeyId("access-key");
        auth.getAwsSigV4().setSecretAccessKey("secret-key");
        auth.getAwsSigV4().setRegion("us-east-1");
        auth.getAwsSigV4().setService("execute-api");
        config.setAuth(auth);

        Map<String, Object> customFactoryClient = snapshotClient(
                config, null, new OrderedCustomAwsSigV4Factory(), new AwsSigV4AuthProviderFactory());

        assertThat(customFactoryClient)
                .containsEntry("authMode", "aws-sigv4")
                .containsEntry("strictBodySigningValidation", false);

        Map<String, Object> delegatingFactoryClient = snapshotClient(
                config, null, new OrderedDelegatingAwsSigV4Factory(), new AwsSigV4AuthProviderFactory());

        assertThat(delegatingFactoryClient)
                .containsEntry("authMode", "aws-sigv4")
                .containsEntry("strictBodySigningValidation", false);

        Map<String, Object> builtInFactoryClient = snapshotClient(config, null, new AwsSigV4AuthProviderFactory());

        assertThat(builtInFactoryClient)
                .containsEntry("authMode", "aws-sigv4")
                .containsEntry("strictBodySigningValidation", true);
    }

    @Test
    void providerSnapshotsKeepStrictFlagsForClassBasedProviderProxies() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(ReactiveHttpClientFactoryBean.class);
        definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, DiagnosticClient.class);
        beanFactory.registerBeanDefinition("diagnosticClient", definition);
        RetryRegistry retryRegistry = RetryRegistry.ofDefaults();
        retryRegistry.retry("default");
        beanFactory.registerSingleton("retryRegistry", retryRegistry);

        ReactiveHttpClientProperties.ClientConfig config = sensitiveClientConfig();
        ReactiveHttpClientProperties.ResilienceConfig resilience = new ReactiveHttpClientProperties.ResilienceConfig();
        resilience.setEnabled(true);
        resilience.setStrictUnsafeRetryValidation(true);
        config.setResilience(resilience);
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.setClients(Map.of("diagnostic-client", config));
        ReactiveHttpClientDiagnosticsProvider target = new ReactiveHttpClientDiagnosticsProvider(
                beanFactory, properties, new MethodMetadataCache());
        ReactiveHttpClientDiagnosticsProvider proxy = classBasedProxy(target);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> clients = (List<Map<String, Object>>) ReactiveHttpClientDiagnosticsSnapshot
                .toMap(proxy)
                .get("clients");

        assertThat(clients).hasSize(1);
        assertThat(clients.get(0))
                .containsEntry("strictUnsafeRetryValidation", true)
                .containsEntry("strictBodySigningValidation", false);
    }

    @Test
    void rendersUnknownStrictValidationFlagsForSummaryOnlySnapshots() {
        ReactiveHttpClientDiagnosticsProvider.ClientSummary summary = summary("summary-client", "com.example.SummaryClient");

        String markdown = ReactiveHttpClientDiagnosticsSnapshot.toMarkdown(List.of(summary));
        String json = ReactiveHttpClientDiagnosticsSnapshot.toJson(List.of(summary));
        Map<String, Object> snapshot = ReactiveHttpClientDiagnosticsSnapshot.toMap(List.of(summary));

        assertThat(markdown).contains("| `summary-client` | `com.example.SummaryClient` | `property` | `unknown` | `disabled:0` | `unknown` | `unknown` | `unknown` | `configured=false, retry=disabled, rateLimiter=disabled, circuitBreaker=disabled, bulkhead=disabled` | `unknown` | `unknown` |");
        assertThat(json)
                .contains("\"logicalCallTimeoutMs\": null")
                .contains("\"compressionEnabled\": null")
                .contains("\"codecMaxInMemorySizeMb\": null")
                .contains("\"strictUnsafeRetryValidation\": null")
                .contains("\"strictBodySigningValidation\": null")
                .doesNotContain("\"strictUnsafeRetryValidation\": false")
                .doesNotContain("\"strictBodySigningValidation\": false");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> clients = (List<Map<String, Object>>) snapshot.get("clients");
        assertThat(clients.get(0).get("compressionEnabled")).isNull();
        assertThat(clients.get(0).get("codecMaxInMemorySizeMb")).isNull();
        assertThat(clients.get(0).get("strictUnsafeRetryValidation")).isNull();
        assertThat(clients.get(0).get("strictBodySigningValidation")).isNull();
    }

    @Test
    void providerSnapshotsRespectOverriddenClientSummaries() {
        ReactiveHttpClientDiagnosticsProvider provider = new ReactiveHttpClientDiagnosticsProvider(
                new DefaultListableBeanFactory(), new ReactiveHttpClientProperties(), new MethodMetadataCache()) {
            @Override
            public List<ClientSummary> clientSummaries() {
                return List.of(summary("custom-client", "com.example.CustomClient"));
            }
        };

        Map<String, Object> snapshot = ReactiveHttpClientDiagnosticsSnapshot.toMap(provider);
        String json = ReactiveHttpClientDiagnosticsSnapshot.toJson(provider);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> clients = (List<Map<String, Object>>) snapshot.get("clients");
        assertThat(clients).hasSize(1);
        assertThat(clients.get(0))
                .containsEntry("clientName", "custom-client")
                .containsEntry("clientInterface", "com.example.CustomClient")
                .containsEntry("strictUnsafeRetryValidation", null)
                .containsEntry("strictBodySigningValidation", null);
        assertThat(json)
                .contains("\"clientName\": \"custom-client\"")
                .contains("\"strictUnsafeRetryValidation\": null")
                .doesNotContain("diagnostic-client");
    }

    @Test
    void proxiedCustomProvidersPreserveSummaryOnlyUnknownSemantics() {
        ReactiveHttpClientDiagnosticsProvider target = new CustomDiagnosticsProvider(
                new DefaultListableBeanFactory(), new ReactiveHttpClientProperties(), new MethodMetadataCache());
        ReactiveHttpClientDiagnosticsProvider proxy = classBasedProxy(target);

        Map<String, Object> snapshot = ReactiveHttpClientDiagnosticsSnapshot.toMap(proxy);

        Map<String, Object> client = firstClient(snapshot);
        assertThat(client)
                .containsEntry("clientName", "custom-client")
                .containsEntry("strictUnsafeRetryValidation", null)
                .containsEntry("strictBodySigningValidation", null)
                .containsEntry("compressionEnabled", null)
                .containsEntry("codecMaxInMemorySizeMb", null);
    }

    @Test
    void rejectsSnapshotsBeyondDocumentedCardinalityAndSizeLimits() {
        ReactiveHttpClientDiagnosticsProvider.ClientSummary normal = summary("client", "Client");
        assertThatThrownBy(() -> ReactiveHttpClientDiagnosticsSnapshot.toMap(
                Collections.nCopies(257, normal)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("256 client limit");

        ReactiveHttpClientDiagnosticsProvider.ClientSummary tooManyEndpoints =
                new ReactiveHttpClientDiagnosticsProvider.ClientSummary(
                        "client", "Client", "property",
                        new ReactiveHttpClientDiagnosticsProvider.TimeoutSummary("disabled", 0),
                        new ReactiveHttpClientDiagnosticsProvider.ResilienceSummary(
                                false, "disabled", "disabled", "disabled", "disabled"),
                        "none", false, 10_001, 0);
        assertThatThrownBy(() -> ReactiveHttpClientDiagnosticsSnapshot.toMap(List.of(tooManyEndpoints)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10000 endpoint limit");

        assertThatThrownBy(() -> ReactiveHttpClientDiagnosticsSnapshot.toMap(
                List.of(summary("x".repeat(513), "Client"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clientName")
                .hasMessageContaining("512 character limit");

        String maximumText = "x".repeat(512);
        ReactiveHttpClientDiagnosticsProvider.ClientSummary verbose =
                new ReactiveHttpClientDiagnosticsProvider.ClientSummary(
                        maximumText, maximumText, maximumText,
                        new ReactiveHttpClientDiagnosticsProvider.TimeoutSummary(maximumText, 0),
                        new ReactiveHttpClientDiagnosticsProvider.ResilienceSummary(
                                false, maximumText, maximumText, maximumText, maximumText),
                        maximumText, false, 1, 0);
        assertThatThrownBy(() -> ReactiveHttpClientDiagnosticsSnapshot.toJson(
                Collections.nCopies(256, verbose)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON diagnostics snapshot")
                .hasMessageContaining("1048576 byte limit");
        assertThatThrownBy(() -> ReactiveHttpClientDiagnosticsSnapshot.toMap(
                Collections.nCopies(256, verbose)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON diagnostics snapshot")
                .hasMessageContaining("1048576 byte limit");

        String maximumMultibyteText = "界".repeat(512);
        ReactiveHttpClientDiagnosticsProvider.ClientSummary multibyteVerbose =
                new ReactiveHttpClientDiagnosticsProvider.ClientSummary(
                        maximumMultibyteText, maximumMultibyteText, maximumMultibyteText,
                        new ReactiveHttpClientDiagnosticsProvider.TimeoutSummary(maximumMultibyteText, 0),
                        new ReactiveHttpClientDiagnosticsProvider.ResilienceSummary(
                                false, maximumMultibyteText, maximumMultibyteText,
                                maximumMultibyteText, maximumMultibyteText),
                        maximumMultibyteText, false, 1, 0);
        assertThatThrownBy(() -> ReactiveHttpClientDiagnosticsSnapshot.toMap(
                Collections.nCopies(128, multibyteVerbose)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON diagnostics snapshot")
                .hasMessageContaining("1048576 byte limit");
        assertThatThrownBy(() -> ReactiveHttpClientDiagnosticsSnapshot.toMarkdown(
                Collections.nCopies(128, multibyteVerbose)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Markdown diagnostics snapshot")
                .hasMessageContaining("1048576 byte limit");
    }

    @Test
    void rendersDiagnosticsSnapshotInDeterministicOrder() {
        ReactiveHttpClientDiagnosticsProvider.ClientSummary zClient = summary("z-client", "com.example.ZClient");
        ReactiveHttpClientDiagnosticsProvider.ClientSummary aClient = summary("a-client", "com.example.AClient");

        String markdown = ReactiveHttpClientDiagnosticsSnapshot.toMarkdown(List.of(zClient, aClient));
        String json = ReactiveHttpClientDiagnosticsSnapshot.toJson(List.of(zClient, aClient));

        assertThat(markdown.indexOf("`a-client`")).isLessThan(markdown.indexOf("`z-client`"));
        assertThat(json.indexOf("\"clientName\": \"a-client\""))
                .isLessThan(json.indexOf("\"clientName\": \"z-client\""));
    }

    @Test
    void readsProjectVersionFromPackagedPomProperties(@TempDir Path tempDir) throws Exception {
        Path pomProperties = tempDir.resolve(
                "META-INF/maven/io.github.huynhngochuyhoang/reactive-http-client-starter/pom.properties");
        Files.createDirectories(pomProperties.getParent());
        Files.writeString(pomProperties, "version=9.8.7\n");
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()}, previous)) {
            Thread.currentThread().setContextClassLoader(classLoader);

            String json = ReactiveHttpClientDiagnosticsSnapshot.toJson(List.of(summary("client", "Client")));

            assertThat(json).contains("\"projectVersion\": \"9.8.7\"");
        }
        finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private static void assertCompatibleSchema(JsonNode published, JsonNode current, String path) {
        assertThat(current)
                .as("missing diagnostics schema path %s", path)
                .isNotNull();
        assertThat(current.getNodeType())
                .as("diagnostics JSON value kind changed at %s", path)
                .isEqualTo(published.getNodeType());
        if (published.isObject()) {
            for (Entry<String, JsonNode> property : published.properties()) {
                assertCompatibleSchema(property.getValue(), current.get(property.getKey()),
                        path + "." + property.getKey());
            }
        }
        else if (published.isArray()) {
            assertThat(current.size())
                    .as("diagnostics array became shorter at %s", path)
                    .isGreaterThanOrEqualTo(published.size());
            for (int i = 0; i < published.size(); i++) {
                assertCompatibleSchema(published.get(i), current.get(i), path + "[" + i + "]");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstClient(Map<String, Object> snapshot) {
        return ((List<Map<String, Object>>) snapshot.get("clients")).get(0);
    }

    private static ReactiveHttpClientDiagnosticsProvider classBasedProxy(ReactiveHttpClientDiagnosticsProvider target) {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(target.getClass());
        enhancer.setCallback((MethodInterceptor) (object, method, args, methodProxy) ->
                method.invoke(target, args != null ? args : new Object[0]));
        return (ReactiveHttpClientDiagnosticsProvider) enhancer.create(
                new Class<?>[]{
                        ConfigurableListableBeanFactory.class,
                        ReactiveHttpClientProperties.class,
                        MethodMetadataCache.class},
                new Object[]{
                        new DefaultListableBeanFactory(),
                        new ReactiveHttpClientProperties(),
                        new MethodMetadataCache()});
    }

    private static Map<String, Object> snapshotClient(ReactiveHttpClientProperties.ClientConfig config) {
        return snapshotClient(config, null);
    }

    private static Map<String, Object> snapshotClient(ReactiveHttpClientProperties.ClientConfig config,
                                                      RetryRegistry retryRegistry,
                                                      AuthProviderFactory... authProviderFactories) {
        return snapshotClient("diagnostic-client", DiagnosticClient.class, config, retryRegistry, authProviderFactories);
    }

    private static Map<String, Object> snapshotClient(String clientName,
                                                      Class<?> clientInterface,
                                                      ReactiveHttpClientProperties.ClientConfig config,
                                                      RetryRegistry retryRegistry,
                                                      AuthProviderFactory... authProviderFactories) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(ReactiveHttpClientFactoryBean.class);
        definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, clientInterface);
        beanFactory.registerBeanDefinition("diagnosticClient", definition);
        if (retryRegistry != null) {
            beanFactory.registerSingleton("retryRegistry", retryRegistry);
        }
        for (int i = 0; i < authProviderFactories.length; i++) {
            beanFactory.registerSingleton("authProviderFactory" + i, authProviderFactories[i]);
        }

        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.setClients(Map.of(clientName, config));
        ReactiveHttpClientDiagnosticsProvider provider = new ReactiveHttpClientDiagnosticsProvider(
                beanFactory, properties, new MethodMetadataCache());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> clients = (List<Map<String, Object>>) ReactiveHttpClientDiagnosticsSnapshot
                .toMap(provider)
                .get("clients");
        return clients.get(0);
    }

    private static ReactiveHttpClientDiagnosticsProvider sensitiveDiagnosticsProvider() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(ReactiveHttpClientFactoryBean.class);
        definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, DiagnosticClient.class);
        beanFactory.registerBeanDefinition("diagnosticClient", definition);

        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.setClients(Map.of("diagnostic-client", sensitiveClientConfig()));

        return new ReactiveHttpClientDiagnosticsProvider(
                beanFactory, properties, new MethodMetadataCache());
    }

    private static ReactiveHttpClientProperties.ClientConfig sensitiveClientConfig() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setBaseUrl("https://user:token@example.com");
        config.setRequestTimeoutMs(500);
        config.setLogicalCallTimeoutMs(2_000);
        config.setAuthProvider("secretAuthProviderBean");
        config.setDefaultHeaders(Map.of("Authorization", "Bearer secret-token"));
        config.setFollowRedirects(true);
        ReactiveHttpClientProperties.ResilienceConfig resilience = new ReactiveHttpClientProperties.ResilienceConfig();
        resilience.setEnabled(true);
        config.setResilience(resilience);
        return config;
    }

    private static ReactiveHttpClientDiagnosticsProvider.ClientSummary summary(String clientName,
                                                                              String clientInterface) {
        return new ReactiveHttpClientDiagnosticsProvider.ClientSummary(
                clientName,
                clientInterface,
                "property",
                new ReactiveHttpClientDiagnosticsProvider.TimeoutSummary("disabled", 0),
                new ReactiveHttpClientDiagnosticsProvider.ResilienceSummary(
                        false, "disabled", "disabled", "disabled", "disabled"),
                "none",
                false,
                1,
                0);
    }

    static class CustomDiagnosticsProvider extends ReactiveHttpClientDiagnosticsProvider {

        CustomDiagnosticsProvider(ConfigurableListableBeanFactory beanFactory,
                                  ReactiveHttpClientProperties properties,
                                  MethodMetadataCache metadataCache) {
            super(beanFactory, properties, metadataCache);
        }

        @Override
        public List<ClientSummary> clientSummaries() {
            return List.of(summary("custom-client", "com.example.CustomClient"));
        }
    }

    static class ThrowingAwsSigV4Factory implements AuthProviderFactory {

        @Override
        public boolean supports(String type) {
            return AwsSigV4AuthProviderFactory.TYPE.equalsIgnoreCase(type);
        }

        @Override
        public AuthProvider create(String clientName,
                                   ReactiveHttpClientProperties.AuthConfig config,
                                   WebClient.Builder webClientBuilder) {
            throw new IllegalStateException("Auth provider should not be resolved for client summaries");
        }
    }

    static class OrderedCustomAwsSigV4Factory implements AuthProviderFactory, Ordered {

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public boolean supports(String type) {
            return AwsSigV4AuthProviderFactory.TYPE.equalsIgnoreCase(type);
        }

        @Override
        public AuthProvider create(String clientName,
                                   ReactiveHttpClientProperties.AuthConfig config,
                                   WebClient.Builder webClientBuilder) {
            return request -> Mono.empty();
        }
    }

    static class OrderedDelegatingAwsSigV4Factory implements AuthProviderFactory, Ordered {

        private final AwsSigV4AuthProviderFactory delegate = new AwsSigV4AuthProviderFactory();

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public boolean supports(String type) {
            return delegate.supports(type);
        }

        @Override
        public AuthProvider create(String clientName,
                                   ReactiveHttpClientProperties.AuthConfig config,
                                   WebClient.Builder webClientBuilder) {
            return delegate.create(clientName, config, webClientBuilder);
        }
    }

    interface SupportBundleSharedOperations {

        @GET("/health")
        Mono<String> health();
    }

    @ReactiveHttpClient(name = "support-inventory")
    interface SupportBundleClient extends SupportBundleSharedOperations {

        @GET("/orders/{orderId}")
        Mono<String> order(@PathVar("orderId") String orderId);
    }

    interface SharedOperations {

        @GET("/shared")
        Mono<String> shared();
    }

    @ReactiveHttpClient(name = "diagnostic-missing-retry")
    interface DiagnosticMissingRetryClient {

        @Retry("ghost-retry")
        @GET("/missing-retry")
        Mono<String> missingRetry();
    }

    @ReactiveHttpClient(name = "diagnostic-client")
    interface DiagnosticClient extends SharedOperations {

        @GET("/direct")
        Mono<String> direct();
    }
}
