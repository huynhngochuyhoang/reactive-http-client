package io.github.huynhngochuyhoang.httpstarter.core;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class ReactiveHttpClientFactoryBeanDiagnosticsTest {

    @Test
    void debugStartupSummaryIncludesResolvedClientConfigurationAndRedactsSecrets(CapturedOutput output) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ReactiveHttpClientFactoryBean.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        config.setHttp2Enabled(true);
        config.setLogExchange(true);
        ReactiveHttpClientProperties.ConnectionPoolConfig pool = new ReactiveHttpClientProperties.ConnectionPoolConfig();
        pool.setMaxConnections(42);
        pool.setPendingAcquireTimeoutMs(1234);
        config.setPool(pool);
        ReactiveHttpClientProperties.ProxyConfig proxy = new ReactiveHttpClientProperties.ProxyConfig();
        proxy.setHost("proxy.example");
        proxy.setPort(3128);
        proxy.setUsername("proxy-user");
        proxy.setPassword("proxy-secret");
        config.setProxy(proxy);
        ReactiveHttpClientProperties.TlsConfig tls = new ReactiveHttpClientProperties.TlsConfig();
        tls.setInsecureTrustAll(true);
        config.setTls(tls);
        config.setRequestTimeoutMs(2500);
        config.getResilience().setEnabled(true);
        config.getResilience().setRetry("diagnostic-retry");
        properties.getClients().put("diagnostic-client", config);

        ReactiveHttpClientFactoryBean<DiagnosticClient> factoryBean = buildFactoryBean(properties);
        try {
            factoryBean.getObject();
            assertThat(output.getOut())
                    .contains("Reactive HTTP client [diagnostic-client] startup configuration")
                    .contains("source=property")
                    .contains("protocol=HTTP/2")
                    .contains("poolSource=client")
                    .contains("proxy=enabled")
                    .contains("credentials=[REDACTED]")
                    .contains("tls=custom")
                    .contains("resilience=enabled")
                    .contains("retryMethods=")
                    .contains("operatorOrder=retry -> rate-limiter -> circuit-breaker -> bulkhead")
                    .contains("exchangeLogging=enabled")
                    .contains("logPreset=metadata-only")
                    .doesNotContain("proxy-secret");
        } finally {
            logger.setLevel(previousLevel);
            factoryBean.destroy();
        }
    }

    @Test
    void debugStartupSummaryUsesDiagnosticsProviderFieldsAndSanitizedValues(CapturedOutput output) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ReactiveHttpClientFactoryBean.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("https://secret-base.example.com");
        config.setAuthProvider("namedAuthProvider");
        config.setDefaultHeaders(Map.of("Authorization", "Bearer secret-token"));
        config.setFollowRedirects(true);
        config.setRequestTimeoutMs(1000);
        config.getResilience().setEnabled(true);
        properties.getObservability().setEnabled(false);
        properties.getClients().put("internal-policy-client", config);

        ReactiveHttpClientFactoryBean<InternalPolicyClient> factoryBean =
                buildFactoryBean(properties, InternalPolicyClient.class);
        try {
            factoryBean.getObject();

            ReactiveHttpClientDiagnosticsProvider.ClientSummary expected = diagnosticsSummary(
                    properties, InternalPolicyClient.class, "internal-policy-client");
            assertThat(output.getOut().lines()
                    .filter(line -> line.contains("Reactive HTTP client [internal-policy-client] startup summary"))
                    .findFirst())
                    .hasValueSatisfying(line -> assertThat(line)
                            .contains("interface=" + expected.clientInterface())
                            .contains("endpoints=" + expected.endpointCount())
                            .contains("inheritedEndpoints=" + expected.inheritedEndpointCount())
                            .contains("baseUrlSource=" + expected.baseUrlSource())
                            .contains("timeout=" + expected.timeout().source() + ":" + expected.timeout().timeoutMs() + "ms")
                            .contains("resilience=configured=" + expected.resilience().configured())
                            .contains("retry=" + expected.resilience().retry())
                            .contains("auth=" + expected.authMode())
                            .contains("redirects=follow")
                            .contains("observability=disabled")
                            .doesNotContain("https://secret-base.example.com")
                            .doesNotContain("namedAuthProvider")
                            .doesNotContain("secret-token")
                            .doesNotContain("Authorization"));
        } finally {
            logger.setLevel(previousLevel);
            factoryBean.destroy();
        }
    }

    @Test
    void annotationBaseUrlTakesPrecedenceOverConfiguredBaseUrl(CapturedOutput output) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ReactiveHttpClientFactoryBean.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.getClients().put("annotation-url-client", clientConfig("http://property.example"));

        ReactiveHttpClientFactoryBean<AnnotationBaseUrlClient> factoryBean =
                buildFactoryBean(properties, AnnotationBaseUrlClient.class);
        try {
            factoryBean.getObject();

            assertThat(output.getOut())
                    .contains("Reactive HTTP client [annotation-url-client] startup configuration")
                    .contains("baseUrl=http://annotation.example (source=annotation)")
                    .doesNotContain("baseUrl=http://property.example (source=property)");
        } finally {
            logger.setLevel(previousLevel);
            factoryBean.destroy();
        }
    }

    @Test
    void debugStartupDiagnosticsReportMethodPolicyPerConcreteClient(CapturedOutput output) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ReactiveHttpClientFactoryBean.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig internal = clientConfig("http://internal.example");
        internal.setRequestTimeoutMs(1000);
        properties.getClients().put("internal-policy-client", internal);
        ReactiveHttpClientProperties.ClientConfig partner = clientConfig("http://partner.example");
        partner.setRequestTimeoutMs(2000);
        properties.getClients().put("partner-policy-client", partner);

        ReactiveHttpClientFactoryBean<InternalPolicyClient> internalFactory =
                buildFactoryBean(properties, InternalPolicyClient.class);
        ReactiveHttpClientFactoryBean<PartnerPolicyClient> partnerFactory =
                buildFactoryBean(properties, PartnerPolicyClient.class);
        try {
            internalFactory.getObject();
            partnerFactory.getObject();

            assertThat(output.getOut())
                    .contains("Reactive HTTP client [internal-policy-client] method policy")
                    .contains("method=[InheritedPolicyOperations#getUser]")
                    .contains("declaredBy=" + InheritedPolicyOperations.class.getName())
                    .contains("concreteClient=" + InternalPolicyClient.class.getName())
                    .contains("inherited=true")
                    .contains("baseUrl=http://internal.example (source=property)")
                    .contains("requestTimeout=client request-timeout-ms(1000ms)")
                    .contains("Reactive HTTP client [partner-policy-client] method policy")
                    .contains("concreteClient=" + PartnerPolicyClient.class.getName())
                    .contains("baseUrl=http://partner.example (source=property)")
                    .contains("requestTimeout=client request-timeout-ms(2000ms)");
        } finally {
            logger.setLevel(previousLevel);
            internalFactory.destroy();
            partnerFactory.destroy();
        }
    }

    @Test
    void methodPolicyDiagnosticsUseStableFieldsForDirectAndInheritedMethods(CapturedOutput output) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ReactiveHttpClientFactoryBean.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig direct = clientConfig("http://direct-policy.example");
        properties.getClients().put("diagnostic-client", direct);
        ReactiveHttpClientProperties.ClientConfig inherited = clientConfig("http://inherited-policy.example");
        inherited.setFollowRedirects(true);
        inherited.setRequestTimeoutMs(1000);
        properties.getClients().put("internal-policy-client", inherited);

        ReactiveHttpClientFactoryBean<DiagnosticClient> directFactory =
                buildFactoryBean(properties, DiagnosticClient.class);
        ReactiveHttpClientFactoryBean<InternalPolicyClient> inheritedFactory =
                buildFactoryBean(properties, InternalPolicyClient.class);
        try {
            directFactory.getObject();
            inheritedFactory.getObject();

            assertThat(output.getOut().lines()
                    .filter(line -> line.contains("Reactive HTTP client [diagnostic-client] method policy"))
                    .findFirst())
                    .hasValueSatisfying(line -> assertThat(line)
                            .contains("method=[DiagnosticClient#ping]")
                            .contains("declaredBy=" + DiagnosticClient.class.getName())
                            .contains("concreteClient=" + DiagnosticClient.class.getName())
                            .contains("inherited=false")
                            .contains("apiRef=none")
                            .contains("httpMethod=GET")
                            .contains("pathTemplate=/ping")
                            .contains("baseUrl=http://direct-policy.example (source=property)")
                            .contains("requestTimeout=disabled")
                            .contains("redirectPolicy=manual")
                            .contains("retrySafety=SAFE_METHOD")
                            .contains("bodyRepeatability=NONE"));
            assertThat(output.getOut().lines()
                    .filter(line -> line.contains("Reactive HTTP client [internal-policy-client] method policy"))
                    .findFirst())
                    .hasValueSatisfying(line -> assertThat(line)
                            .contains("method=[InheritedPolicyOperations#getUser]")
                            .contains("declaredBy=" + InheritedPolicyOperations.class.getName())
                            .contains("concreteClient=" + InternalPolicyClient.class.getName())
                            .contains("inherited=true")
                            .contains("apiRef=none")
                            .contains("httpMethod=GET")
                            .contains("pathTemplate=/users/{id}")
                            .contains("baseUrl=http://inherited-policy.example (source=property)")
                            .contains("requestTimeout=client request-timeout-ms(1000ms)")
                            .contains("redirectPolicy=follow")
                            .contains("retrySafety=SAFE_METHOD")
                            .contains("bodyRepeatability=NONE"));
        } finally {
            logger.setLevel(previousLevel);
            directFactory.destroy();
            inheritedFactory.destroy();
        }
    }

    @Test
    void debugStartupDiagnosticsReportMethodTimeoutAsWinningSource(CapturedOutput output) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ReactiveHttpClientFactoryBean.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://method-timeout.property");
        config.setRequestTimeoutMs(2500);
        properties.getClients().put("method-timeout-policy-client", config);

        ReactiveHttpClientFactoryBean<MethodTimeoutPolicyClient> factoryBean =
                buildFactoryBean(properties, MethodTimeoutPolicyClient.class);
        try {
            factoryBean.getObject();

            assertThat(output.getOut())
                    .contains("Reactive HTTP client [method-timeout-policy-client] method policy")
                    .contains("baseUrl=http://method-timeout.annotation (source=annotation)")
                    .contains("method=[MethodTimeoutPolicyOperations#getUser]")
                    .contains("requestTimeout=method @TimeoutMs(350ms)")
                    .doesNotContain("requestTimeout=client request-timeout-ms(2500ms)");
        } finally {
            logger.setLevel(previousLevel);
            factoryBean.destroy();
        }
    }

    @Test
    void debugStartupDiagnosticsReportRemainingTimeoutSources(CapturedOutput output) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ReactiveHttpClientFactoryBean.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig apiRef = clientConfig("http://api-ref-policy.example");
        ReactiveHttpClientProperties.ApiConfig api = new ReactiveHttpClientProperties.ApiConfig();
        api.setMethod("GET");
        api.setPath("/api-ref-users/{id}");
        api.setTimeoutMs(1200);
        apiRef.setApis(Map.of("user.lookup", api));
        apiRef.setRequestTimeoutMs(2500);
        properties.getClients().put("api-ref-policy-client", apiRef);

        ReactiveHttpClientProperties.ClientConfig deprecatedAlias = clientConfig("http://deprecated-policy.example");
        deprecatedAlias.getResilience().setTimeoutMs(1800);
        properties.getClients().put("deprecated-timeout-policy-client", deprecatedAlias);
        properties.getClients().put("disabled-timeout-policy-client", clientConfig("http://disabled-policy.example"));

        ReactiveHttpClientFactoryBean<ApiRefPolicyClient> apiRefFactory =
                buildFactoryBean(properties, ApiRefPolicyClient.class);
        ReactiveHttpClientFactoryBean<DeprecatedTimeoutPolicyClient> deprecatedFactory =
                buildFactoryBean(properties, DeprecatedTimeoutPolicyClient.class);
        ReactiveHttpClientFactoryBean<DisabledTimeoutPolicyClient> disabledFactory =
                buildFactoryBean(properties, DisabledTimeoutPolicyClient.class);
        try {
            apiRefFactory.getObject();
            deprecatedFactory.getObject();
            disabledFactory.getObject();

            assertThat(output.getOut())
                    .contains("Reactive HTTP client [api-ref-policy-client] method policy")
                    .contains("apiRef=user.lookup")
                    .contains("httpMethod=GET")
                    .contains("pathTemplate=/api-ref-users/{id}")
                    .contains("requestTimeout=@ApiRef timeout-ms(1200ms)")
                    .contains("Reactive HTTP client [deprecated-timeout-policy-client] method policy")
                    .contains("requestTimeout=deprecated resilience.timeout-ms(1800ms)")
                    .contains("Reactive HTTP client [disabled-timeout-policy-client] method policy")
                    .contains("requestTimeout=disabled");
        } finally {
            logger.setLevel(previousLevel);
            apiRefFactory.destroy();
            deprecatedFactory.destroy();
            disabledFactory.destroy();
        }
    }

    @Test
    void methodPolicyDiagnosticsStayOutOfInfoLogs(CapturedOutput output) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ReactiveHttpClientFactoryBean.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);

        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://info-policy.example");
        config.setRequestTimeoutMs(1000);
        properties.getClients().put("internal-policy-client", config);

        ReactiveHttpClientFactoryBean<InternalPolicyClient> factoryBean =
                buildFactoryBean(properties, InternalPolicyClient.class);
        try {
            factoryBean.getObject();

            assertThat(output.getOut())
                    .doesNotContain("method policy")
                    .doesNotContain("startup summary");
        } finally {
            logger.setLevel(previousLevel);
            factoryBean.destroy();
        }
    }

    @Test
    void debugStartupSummaryIncludesPerMethodResilienceDiagnostics(CapturedOutput output) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ReactiveHttpClientFactoryBean.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        config.getResilience().setEnabled(true);
        config.getResilience().setRetryMethods(java.util.Set.of("GET", "POST", "PUT"));
        properties.getClients().put("diagnostic-client", config);

        ReactiveHttpClientFactoryBean<ResilienceDiagnosticClient> factoryBean =
                buildFactoryBean(properties, ResilienceDiagnosticClient.class);
        try {
            factoryBean.getObject();

            assertThat(output.getOut())
                    .contains("method [ResilienceDiagnosticClient#read] resilience")
                    .contains("retrySafety=SAFE_METHOD")
                    .contains("method [ResilienceDiagnosticClient#replace] resilience")
                    .contains("retrySafety=SAFE_METHOD")
                    .contains("method [ResilienceDiagnosticClient#write] resilience")
                    .contains("retrySafety=EXPLICIT_IDEMPOTENCY_KEY")
                    .contains("operatorOrder=retry -> rate-limiter -> circuit-breaker -> bulkhead");
        } finally {
            logger.setLevel(previousLevel);
            factoryBean.destroy();
        }
    }

    @Test
    void debugStartupDiagnosticsReflectDefaultIdempotencyKey(CapturedOutput output) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ReactiveHttpClientFactoryBean.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        config.setDefaultHeaders(Map.of("Idempotency-Key", "configured-key"));
        config.getResilience().setEnabled(true);
        config.getResilience().setRetryMethods(java.util.Set.of("POST"));
        properties.getClients().put("diagnostic-client", config);

        ReactiveHttpClientFactoryBean<DefaultIdempotencyKeyDiagnosticClient> factoryBean =
                buildFactoryBean(properties, DefaultIdempotencyKeyDiagnosticClient.class);
        try {
            factoryBean.getObject();

            assertThat(output.getOut())
                    .contains("method [DefaultIdempotencyKeyDiagnosticClient#create] resilience")
                    .contains("retrySafety=EXPLICIT_IDEMPOTENCY_KEY");
        } finally {
            logger.setLevel(previousLevel);
            factoryBean.destroy();
        }
    }

    @Test
    void debugStartupDiagnosticsDisableUnavailableOperators(CapturedOutput output) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ReactiveHttpClientFactoryBean.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        config.getResilience().setEnabled(true);
        config.getResilience().setRetryMethods(java.util.Set.of("POST"));
        properties.getClients().put("diagnostic-client", config);

        ReactiveHttpClientFactoryBean<DefaultIdempotencyKeyDiagnosticClient> factoryBean =
                buildFactoryBean(properties, DefaultIdempotencyKeyDiagnosticClient.class);
        try {
            factoryBean.getObject();

            assertThat(output.getOut())
                    .contains("method [DefaultIdempotencyKeyDiagnosticClient#create] resilience: httpMethod=POST, "
                            + "retry=disabled, rateLimiter=disabled, circuitBreaker=disabled, bulkhead=disabled");
        } finally {
            logger.setLevel(previousLevel);
            factoryBean.destroy();
        }
    }

    @Test
    void failsFastWhenProxyPortIsSetWithoutHost() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        ReactiveHttpClientProperties.ProxyConfig proxy = new ReactiveHttpClientProperties.ProxyConfig();
        proxy.setPort(3128);
        config.setProxy(proxy);
        properties.getClients().put("diagnostic-client", config);

        assertThatThrownBy(() -> buildFactoryBean(properties).getObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Proxy port is set but host is blank");
    }

    @Test
    void failsFastWhenTlsTrustStorePasswordIsSetWithoutTrustStore() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        ReactiveHttpClientProperties.TlsConfig tls = new ReactiveHttpClientProperties.TlsConfig();
        tls.setTrustStorePassword("secret");
        config.setTls(tls);
        properties.getClients().put("diagnostic-client", config);

        assertThatThrownBy(() -> buildFactoryBean(properties).getObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trust-store-password is set but trust-store is blank")
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain("secret"));
    }

    @Test
    void failsFastWhenConfiguredAuthProviderBeanIsMissing() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        config.setAuthProvider("missingAuthProvider");
        properties.getClients().put("diagnostic-client", config);

        assertThatThrownBy(() -> buildFactoryBean(properties).getObject())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No AuthProvider bean named 'missingAuthProvider'")
                .hasMessageContaining("diagnostic-client");
    }

    @Test
    void rejectsInvalidConfiguredClientName() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.getClients().put("bad name", clientConfig("http://localhost:8080"));

        assertThatThrownBy(() -> buildFactoryBean(properties).getObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reactive.http.clients client name 'bad name' is invalid")
                .hasMessageContaining(ClientNameValidator.ALLOWED_PATTERN_DESCRIPTION);
    }

    @Test
    void failsFastWhenDefaultHeaderNameIsInvalid() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        config.setDefaultHeaders(Map.of("Bad Header", "value"));
        properties.getClients().put("diagnostic-client", config);

        assertThatThrownBy(() -> buildFactoryBean(properties).getObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid header name");
    }

    @Test
    void failsFastWhenDefaultHeaderValueIsNull() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        Map<String, String> defaultHeaders = new HashMap<>();
        defaultHeaders.put("X-Tenant", null);
        config.setDefaultHeaders(defaultHeaders);
        properties.getClients().put("diagnostic-client", config);

        assertThatThrownBy(() -> buildFactoryBean(properties).getObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Default header 'X-Tenant' for client 'diagnostic-client' must not be null");
    }

    @Test
    void failsFastWhenDefaultHeaderValueContainsControlCharacter() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        config.setDefaultHeaders(Map.of("X-Tenant", "public\nadmin"));
        properties.getClients().put("diagnostic-client", config);

        assertThatThrownBy(() -> buildFactoryBean(properties).getObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid header value for 'X-Tenant'");
    }

    @Test
    void warnsWhenDefaultHeaderNameLooksSensitive(CapturedOutput output) throws Exception {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        config.setDefaultHeaders(Map.of("Authorization", "Bearer secret"));
        properties.getClients().put("diagnostic-client", config);

        ReactiveHttpClientFactoryBean<DiagnosticClient> factoryBean = buildFactoryBean(properties);
        try {
            factoryBean.getObject();

            assertThat(output.getOut())
                    .contains("default header [Authorization] looks sensitive")
                    .doesNotContain("Bearer secret");
        } finally {
            factoryBean.destroy();
        }
    }

    @Test
    void failsFastWhenDefaultQueryParamNameIsBlank() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        config.setDefaultQueryParams(Map.of("", List.of("en-US")));
        properties.getClients().put("diagnostic-client", config);

        assertThatThrownBy(() -> buildFactoryBean(properties).getObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Default query parameter name must not be blank");
    }

    @Test
    void failsFastWhenDefaultQueryParamValueContainsControlCharacter() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        config.setDefaultQueryParams(Map.of("locale", List.of("en\nUS")));
        properties.getClients().put("diagnostic-client", config);

        assertThatThrownBy(() -> buildFactoryBean(properties).getObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid default query parameter value for 'locale'");
    }

    @Test
    void warnsWhenDefaultQueryParamNameLooksSensitive(CapturedOutput output) throws Exception {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        config.setDefaultQueryParams(Map.of("api_key", List.of("secret")));
        properties.getClients().put("diagnostic-client", config);

        ReactiveHttpClientFactoryBean<DiagnosticClient> factoryBean = buildFactoryBean(properties);
        try {
            factoryBean.getObject();

            assertThat(output.getOut())
                    .contains("default query parameter [api_key] looks sensitive")
                    .doesNotContain("api_key=secret");
        } finally {
            factoryBean.destroy();
        }
    }

    @Test
    void authProviderBeanNameTakesPrecedenceOverObjectAuth(CapturedOutput output) throws Exception {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        config.setAuthProvider("namedAuthProvider");
        ReactiveHttpClientProperties.AuthConfig auth = new ReactiveHttpClientProperties.AuthConfig();
        auth.setType("oauth2-client-credentials");
        config.setAuth(auth);
        properties.getClients().put("diagnostic-client", config);

        ReactiveHttpClientFactoryBean<DiagnosticClient> factoryBean = buildFactoryBean(properties);
        try {
            factoryBean.getObject();

            assertThat(output.getOut())
                    .contains("has both auth-provider and auth.type configured")
                    .contains("Using auth-provider bean [namedAuthProvider]")
                    .contains("ignoring object-style auth [oauth2-client-credentials]");
        } finally {
            factoryBean.destroy();
        }
    }

    @Test
    void requestTimeoutTakesPrecedenceOverDeprecatedResilienceTimeoutAlias(CapturedOutput output) throws Exception {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        config.setRequestTimeoutMs(1500);
        config.getResilience().setTimeoutMs(3000);
        properties.getClients().put("diagnostic-client", config);

        ReactiveHttpClientFactoryBean<DiagnosticClient> factoryBean = buildFactoryBean(properties);
        try {
            factoryBean.getObject();

            assertThat(output.getOut())
                    .contains("has both request-timeout-ms and deprecated resilience.timeout-ms configured")
                    .contains("Using request-timeout-ms [1500]")
                    .contains("ignoring resilience.timeout-ms [3000]");
        } finally {
            factoryBean.destroy();
        }
    }

    @Test
    void failsFastWhenAbstractMethodHasNoEndpointMetadata() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.getClients().put("invalid-contract-client", clientConfig("http://localhost:8080"));

        assertThatThrownBy(() -> buildFactoryBean(properties, MissingEndpointFactoryClient.class).getObject())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must declare an HTTP verb annotation or @ApiRef")
                .hasMessageContaining("missing");
    }

    @Test
    void allowsDefaultHelperMethodWithoutEndpointMetadata() throws Exception {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.getClients().put("helper-client", clientConfig("http://localhost:8080"));

        ReactiveHttpClientFactoryBean<DefaultHelperClient> factoryBean =
                buildFactoryBean(properties, DefaultHelperClient.class);
        try {
            assertThat(factoryBean.getObject().helper()).isEqualTo("ok");
        } finally {
            factoryBean.destroy();
        }
    }

    @Test
    void validatesInheritedAbstractMethodsDuringProxyConstruction() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.getClients().put("invalid-inherited-client", clientConfig("http://localhost:8080"));

        assertThatThrownBy(() -> buildFactoryBean(properties, InvalidInheritedClient.class).getObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Inherited method")
                .hasMessageContaining("InvalidInheritedParent")
                .hasMessageContaining("@ReactiveHttpClient(\"invalid-inherited-client\")")
                .hasMessageContaining("InvalidInheritedClient")
                .hasMessageContaining("@PathVar value must not be blank")
                .hasMessageContaining("get");
    }

    @Test
    void inheritedStaticPathTemplateReportsConcreteClientWhenPathVarIsMissing() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.getClients().put("missing-inherited-pathvar-client", clientConfig("http://localhost:8080"));

        assertThatThrownBy(() -> buildFactoryBean(properties, MissingInheritedPathVarClient.class).getObject())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Inherited method")
                .hasMessageContaining("InheritedMissingPathVarParent")
                .hasMessageContaining("@ReactiveHttpClient(\"missing-inherited-pathvar-client\")")
                .hasMessageContaining("MissingInheritedPathVarClient")
                .hasMessageContaining("URI template variables [id]")
                .hasMessageContaining("without matching @PathVar parameters");
    }

    @Test
    void inheritedStaticPathTemplateReportsConcreteClientWhenPathVarIsUnused() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.getClients().put("unused-inherited-pathvar-client", clientConfig("http://localhost:8080"));

        assertThatThrownBy(() -> buildFactoryBean(properties, UnusedInheritedPathVarClient.class).getObject())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Inherited method")
                .hasMessageContaining("InheritedUnusedPathVarParent")
                .hasMessageContaining("@ReactiveHttpClient(\"unused-inherited-pathvar-client\")")
                .hasMessageContaining("UnusedInheritedPathVarClient")
                .hasMessageContaining("@PathVar parameters [id]")
                .hasMessageContaining("not used by the path template");
    }

    @Test
    void failsFastWhenNonMapHeaderParamNameIsBlank() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.getClients().put("invalid-header-client", clientConfig("http://localhost:8080"));

        assertThatThrownBy(() -> buildFactoryBean(properties, BlankHeaderParamClient.class).getObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@HeaderParam value must not be blank for non-Map parameter")
                .hasMessageContaining("call");
    }

    @Test
    void failsFastWhenGeneratedIdempotencyKeyHeaderNameIsBlank() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.getClients().put("invalid-idempotency-client", clientConfig("http://localhost:8080"));

        assertThatThrownBy(() -> buildFactoryBean(properties, BlankIdempotencyKeyClient.class).getObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@IdempotencyKey value must not be blank")
                .hasMessageContaining("call");
    }

    @Test
    void failsFastWhenPropertyBaseUrlIsMalformed() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.getClients().put("diagnostic-client", clientConfig("http://bad host"));

        assertThatThrownBy(() -> buildFactoryBean(properties).getObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid baseUrl")
                .hasMessageContaining("diagnostic-client")
                .hasMessageContaining("source=property");
    }

    @Test
    void failsFastWhenAnnotationBaseUrlIsMalformed() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();

        assertThatThrownBy(() -> buildFactoryBean(properties, MalformedAnnotationBaseUrlClient.class).getObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid baseUrl")
                .hasMessageContaining("annotation-url-malformed-client")
                .hasMessageContaining("source=annotation");
    }

    @Test
    void failsFastWhenApiRefMethodIsUnsupported() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        ReactiveHttpClientProperties.ApiConfig api = new ReactiveHttpClientProperties.ApiConfig();
        api.setMethod("FETCH");
        api.setPath("/users/{id}");
        config.setApis(Map.of("user.lookup", api));
        properties.getClients().put("api-contract-client", config);

        assertThatThrownBy(() -> buildFactoryBean(properties, ApiRefContractClient.class).getObject())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@ApiRef(\"user.lookup\")")
                .hasMessageContaining("method [FETCH] is not supported");
    }

    @Test
    void failsFastWhenStaticPathTemplateMissesPathVarBinding() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.getClients().put("missing-pathvar-client", clientConfig("http://localhost:8080"));

        assertThatThrownBy(() -> buildFactoryBean(properties, MissingPathVarClient.class).getObject())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("URI template variables [id]")
                .hasMessageContaining("without matching @PathVar parameters")
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("Inherited method"));
    }

    @Test
    void failsFastWhenStaticPathVarBindingIsUnused() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.getClients().put("unused-pathvar-client", clientConfig("http://localhost:8080"));

        assertThatThrownBy(() -> buildFactoryBean(properties, UnusedPathVarClient.class).getObject())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@PathVar parameters [id]")
                .hasMessageContaining("not used by the path template");
    }

    @Test
    void failsFastWhenStaticPathVarBindingIsDuplicated() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.getClients().put("duplicate-pathvar-client", clientConfig("http://localhost:8080"));

        assertThatThrownBy(() -> buildFactoryBean(properties, DuplicatePathVarClient.class).getObject())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate @PathVar(\"id\") bindings");
    }

    @Test
    void failsFastWhenApiRefPathTemplateMissesPathVarBinding() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        ReactiveHttpClientProperties.ApiConfig api = new ReactiveHttpClientProperties.ApiConfig();
        api.setMethod("GET");
        api.setPath("/users/{userId}");
        config.setApis(Map.of("user.lookup", api));
        properties.getClients().put("api-contract-client", config);

        assertThatThrownBy(() -> buildFactoryBean(properties, ApiRefContractClient.class).getObject())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@ApiRef(\"user.lookup\")")
                .hasMessageContaining("URI template variables [userId]")
                .hasMessageContaining("without matching @PathVar parameters");
    }

    @Test
    void allowsLiteralQueryStringInStaticPathTemplate() throws Exception {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.getClients().put("literal-query-client", clientConfig("http://localhost:8080"));

        ReactiveHttpClientFactoryBean<LiteralQueryClient> factoryBean =
                buildFactoryBean(properties, LiteralQueryClient.class);
        try {
            assertThat(factoryBean.getObject()).isNotNull();
        } finally {
            factoryBean.destroy();
        }
    }

    @Test
    void allowsLiteralQueryStringInApiRefPathTemplate() throws Exception {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        ReactiveHttpClientProperties.ApiConfig api = new ReactiveHttpClientProperties.ApiConfig();
        api.setMethod("GET");
        api.setPath("/users/{id}?projection=literal");
        config.setApis(Map.of("user.lookup", api));
        properties.getClients().put("api-contract-client", config);

        ReactiveHttpClientFactoryBean<ApiRefContractClient> factoryBean =
                buildFactoryBean(properties, ApiRefContractClient.class);
        try {
            assertThat(factoryBean.getObject()).isNotNull();
        } finally {
            factoryBean.destroy();
        }
    }

    @Test
    void allowsStaticQueryTemplateVariablesBoundByPathVar() throws Exception {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.getClients().put("query-pathvar-client", clientConfig("http://localhost:8080"));

        ReactiveHttpClientFactoryBean<QueryPathVarClient> factoryBean =
                buildFactoryBean(properties, QueryPathVarClient.class);
        try {
            assertThat(factoryBean.getObject()).isNotNull();
        } finally {
            factoryBean.destroy();
        }
    }

    @Test
    void allowsApiRefQueryTemplateVariablesBoundByPathVar() throws Exception {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        ReactiveHttpClientProperties.ApiConfig api = new ReactiveHttpClientProperties.ApiConfig();
        api.setMethod("GET");
        api.setPath("/users/{id}?projection={projection}");
        config.setApis(Map.of("user.queryLookup", api));
        properties.getClients().put("api-query-pathvar-client", config);

        ReactiveHttpClientFactoryBean<ApiRefQueryPathVarClient> factoryBean =
                buildFactoryBean(properties, ApiRefQueryPathVarClient.class);
        try {
            assertThat(factoryBean.getObject()).isNotNull();
        } finally {
            factoryBean.destroy();
        }
    }

    @Test
    void allowsInheritedApiRefMethodsToUseEachConcreteClientApiMap() throws Exception {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig internal = clientConfig("http://localhost:8080");
        ReactiveHttpClientProperties.ApiConfig internalApi = new ReactiveHttpClientProperties.ApiConfig();
        internalApi.setMethod("GET");
        internalApi.setPath("/internal-users/{id}");
        internalApi.setTimeoutMs(1000);
        internal.setApis(Map.of("user.lookup", internalApi));
        properties.getClients().put("internal-inherited-api-ref-client", internal);

        ReactiveHttpClientProperties.ClientConfig partner = clientConfig("http://localhost:8081");
        ReactiveHttpClientProperties.ApiConfig partnerApi = new ReactiveHttpClientProperties.ApiConfig();
        partnerApi.setMethod("DELETE");
        partnerApi.setPath("/partner-users/{id}");
        partnerApi.setTimeoutMs(2000);
        partner.setApis(Map.of("user.lookup", partnerApi));
        properties.getClients().put("partner-inherited-api-ref-client", partner);

        ReactiveHttpClientFactoryBean<InternalInheritedApiRefClient> internalFactory =
                buildFactoryBean(properties, InternalInheritedApiRefClient.class);
        ReactiveHttpClientFactoryBean<PartnerInheritedApiRefClient> partnerFactory =
                buildFactoryBean(properties, PartnerInheritedApiRefClient.class);
        try {
            assertThat(internalFactory.getObject()).isNotNull();
            assertThat(partnerFactory.getObject()).isNotNull();
        } finally {
            internalFactory.destroy();
            partnerFactory.destroy();
        }
    }

    @Test
    void failsFastWhenInheritedApiRefMappingIsMissingForConcreteClient() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.getClients().put("missing-inherited-api-ref-client", clientConfig("http://localhost:8080"));

        assertThatThrownBy(() -> buildFactoryBean(properties, MissingInheritedApiRefClient.class).getObject())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Inherited method")
                .hasMessageContaining("InheritedApiRefOperations")
                .hasMessageContaining("@ReactiveHttpClient(\"missing-inherited-api-ref-client\")")
                .hasMessageContaining("MissingInheritedApiRefClient")
                .hasMessageContaining("@ApiRef(\"user.lookup\")")
                .hasMessageContaining("reactive.http.clients.missing-inherited-api-ref-client.apis[user.lookup]")
                .hasMessageContaining("is not configured");
    }

    @Test
    void failsFastWhenInheritedApiRefMethodIsUnsupportedForConcreteClient() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        ReactiveHttpClientProperties.ApiConfig api = new ReactiveHttpClientProperties.ApiConfig();
        api.setMethod("FETCH");
        api.setPath("/users/{id}");
        config.setApis(Map.of("user.lookup", api));
        properties.getClients().put("malformed-inherited-api-ref-client", config);

        assertThatThrownBy(() -> buildFactoryBean(properties, MalformedInheritedApiRefClient.class).getObject())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Inherited method")
                .hasMessageContaining("InheritedApiRefOperations")
                .hasMessageContaining("@ReactiveHttpClient(\"malformed-inherited-api-ref-client\")")
                .hasMessageContaining("MalformedInheritedApiRefClient")
                .hasMessageContaining("@ApiRef(\"user.lookup\")")
                .hasMessageContaining("reactive.http.clients.malformed-inherited-api-ref-client.apis[user.lookup].method")
                .hasMessageContaining("method [FETCH] is not supported");
    }

    @Test
    void validatesInheritedApiRefPathVariablesAgainstConcreteClientApiMap() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = clientConfig("http://localhost:8080");
        ReactiveHttpClientProperties.ApiConfig api = new ReactiveHttpClientProperties.ApiConfig();
        api.setMethod("GET");
        api.setPath("/users/{userId}");
        config.setApis(Map.of("user.lookup", api));
        properties.getClients().put("pathvar-inherited-api-ref-client", config);

        assertThatThrownBy(() -> buildFactoryBean(properties, PathVarInheritedApiRefClient.class).getObject())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Inherited method")
                .hasMessageContaining("InheritedApiRefOperations")
                .hasMessageContaining("@ReactiveHttpClient(\"pathvar-inherited-api-ref-client\")")
                .hasMessageContaining("PathVarInheritedApiRefClient")
                .hasMessageContaining("@ApiRef(\"user.lookup\")")
                .hasMessageContaining("URI template variables [userId]")
                .hasMessageContaining("without matching @PathVar parameters");
    }

    @Test
    void allowsBlankPathTemplateDuringStartup() throws Exception {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.getClients().put("blank-path-client", clientConfig("http://localhost:8080"));

        ReactiveHttpClientFactoryBean<BlankPathStartupClient> factoryBean =
                buildFactoryBean(properties, BlankPathStartupClient.class);
        try {
            assertThat(factoryBean.getObject()).isNotNull();
        } finally {
            factoryBean.destroy();
        }
    }

    private ReactiveHttpClientDiagnosticsProvider.ClientSummary diagnosticsSummary(
            ReactiveHttpClientProperties properties, Class<?> clientInterface, String clientName) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(ReactiveHttpClientFactoryBean.class);
        definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, clientInterface);
        beanFactory.registerBeanDefinition(clientName, definition);

        return new ReactiveHttpClientDiagnosticsProvider(beanFactory, properties, new MethodMetadataCache())
                .clientSummaries()
                .get(0);
    }

    @SuppressWarnings("unchecked")
    private ReactiveHttpClientFactoryBean<DiagnosticClient> buildFactoryBean(ReactiveHttpClientProperties properties) {
        return buildFactoryBean(properties, DiagnosticClient.class);
    }

    @SuppressWarnings("unchecked")
    private <T> ReactiveHttpClientFactoryBean<T> buildFactoryBean(ReactiveHttpClientProperties properties, Class<T> type) {
        ApplicationContext ctx = mock(ApplicationContext.class);

        ObjectProvider<Object> defaultProvider = mock(ObjectProvider.class);
        when(defaultProvider.getIfAvailable()).thenReturn(null);
        lenient().when(defaultProvider.getIfAvailable(any(Supplier.class)))
                .thenAnswer(inv -> inv.getArgument(0, Supplier.class).get());
        lenient().when(defaultProvider.orderedStream()).thenReturn(Stream.empty());
        when(ctx.getBeanProvider(any(Class.class))).thenReturn((ObjectProvider) defaultProvider);

        ObjectProvider<ReactiveHttpClientProperties> propsProvider = mock(ObjectProvider.class);
        when(propsProvider.getIfAvailable(any(Supplier.class))).thenReturn(properties);
        when(ctx.getBeanProvider(ReactiveHttpClientProperties.class)).thenReturn(propsProvider);

        ObjectProvider<MethodMetadataCache> cacheProvider = mock(ObjectProvider.class);
        when(cacheProvider.getIfAvailable(any(Supplier.class))).thenReturn(new MethodMetadataCache());
        when(ctx.getBeanProvider(MethodMetadataCache.class)).thenReturn(cacheProvider);

        ObjectProvider<DefaultErrorDecoder> errorProvider = mock(ObjectProvider.class);
        when(errorProvider.getIfAvailable(any(Supplier.class))).thenReturn(new DefaultErrorDecoder());
        when(ctx.getBeanProvider(DefaultErrorDecoder.class)).thenReturn(errorProvider);

        ObjectProvider<WebClient.Builder> builderProvider = mock(ObjectProvider.class);
        when(builderProvider.getIfAvailable(any(Supplier.class))).thenReturn(WebClient.builder());
        when(ctx.getBeanProvider(WebClient.Builder.class)).thenReturn(builderProvider);

        ObjectProvider<ReactiveHttpClientCustomizer> customizerProvider = mock(ObjectProvider.class);
        when(customizerProvider.orderedStream()).thenReturn(Stream.empty());
        when(ctx.getBeanProvider(ReactiveHttpClientCustomizer.class)).thenReturn(customizerProvider);

        when(ctx.getBean("missingAuthProvider", AuthProvider.class))
                .thenThrow(new NoSuchBeanDefinitionException(AuthProvider.class, "missingAuthProvider"));
        AuthProvider namedAuthProvider = request -> Mono.just(AuthContext.empty());
        when(ctx.getBean("namedAuthProvider", AuthProvider.class)).thenReturn(namedAuthProvider);

        ObjectProvider<ObjectMapper> objectMapperProvider = mock(ObjectProvider.class);
        when(objectMapperProvider.getIfAvailable()).thenReturn(null);
        when(ctx.getBeanProvider(ObjectMapper.class)).thenReturn(objectMapperProvider);

        ReactiveHttpClientFactoryBean<T> factoryBean = new ReactiveHttpClientFactoryBean<>();
        factoryBean.setType(type);
        factoryBean.setApplicationContext(ctx);
        return factoryBean;
    }

    private ReactiveHttpClientProperties.ClientConfig clientConfig(String baseUrl) {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setBaseUrl(baseUrl);
        return config;
    }

    @ReactiveHttpClient(name = "diagnostic-client")
    interface DiagnosticClient {
        @GET("/ping")
        Mono<String> ping();
    }

    @ReactiveHttpClient(name = "diagnostic-client")
    interface ResilienceDiagnosticClient {
        @GET("/read")
        Mono<String> read();

        @POST("/write")
        Mono<String> write(@HeaderParam("Idempotency-Key") String idempotencyKey);

        @PUT("/replace")
        Mono<String> replace();
    }

    @ReactiveHttpClient(name = "diagnostic-client")
    interface DefaultIdempotencyKeyDiagnosticClient {
        @POST("/create")
        Mono<String> create();
    }


    @ReactiveHttpClient(name = "annotation-url-malformed-client", baseUrl = "http://bad host")
    interface MalformedAnnotationBaseUrlClient {
        @GET("/ping")
        Mono<String> ping();
    }

    @ReactiveHttpClient(name = "api-contract-client")
    interface ApiRefContractClient {
        @ApiRef("user.lookup")
        Mono<String> lookup(@PathVar("id") String id);
    }

    @ReactiveHttpClient(name = "missing-pathvar-client")
    interface MissingPathVarClient {
        @GET("/users/{id}")
        Mono<String> lookup();
    }

    @ReactiveHttpClient(name = "unused-pathvar-client")
    interface UnusedPathVarClient {
        @GET("/users")
        Mono<String> lookup(@PathVar("id") String id);
    }

    @ReactiveHttpClient(name = "duplicate-pathvar-client")
    interface DuplicatePathVarClient {
        @GET("/users/{id}")
        Mono<String> lookup(@PathVar("id") String id, @PathVar("id") String duplicateId);
    }

    @ReactiveHttpClient(name = "literal-query-client")
    interface LiteralQueryClient {
        @GET("/users?projection=literal")
        Mono<String> lookup();
    }

    @ReactiveHttpClient(name = "query-pathvar-client")
    interface QueryPathVarClient {
        @GET("/users/{id}?projection={projection}")
        Mono<String> lookup(@PathVar("id") String id, @PathVar("projection") String projection);
    }

    @ReactiveHttpClient(name = "api-query-pathvar-client")
    interface ApiRefQueryPathVarClient {
        @ApiRef("user.queryLookup")
        Mono<String> lookup(@PathVar("id") String id, @PathVar("projection") String projection);
    }

    @ReactiveHttpClient(name = "blank-path-client")
    interface BlankPathStartupClient {
        @GET("")
        Mono<String> root();
    }

    @ReactiveHttpClient(name = "invalid-contract-client")
    interface MissingEndpointFactoryClient {
        Mono<String> missing();
    }

    @ReactiveHttpClient(name = "helper-client")
    interface DefaultHelperClient {
        @GET("/ping")
        Mono<String> ping();

        default String helper() {
            return "ok";
        }
    }

    interface InvalidInheritedParent {
        @GET("/items/{id}")
        Mono<String> get(@PathVar(" ") String id);
    }

    @ReactiveHttpClient(name = "invalid-inherited-client")
    interface InvalidInheritedClient extends InvalidInheritedParent {
    }

    interface InheritedMissingPathVarParent {
        @GET("/items/{id}")
        Mono<String> get();
    }

    @ReactiveHttpClient(name = "missing-inherited-pathvar-client")
    interface MissingInheritedPathVarClient extends InheritedMissingPathVarParent {
    }

    interface InheritedUnusedPathVarParent {
        @GET("/items")
        Mono<String> get(@PathVar("id") String id);
    }

    @ReactiveHttpClient(name = "unused-inherited-pathvar-client")
    interface UnusedInheritedPathVarClient extends InheritedUnusedPathVarParent {
    }

    interface InheritedPolicyOperations {
        @GET("/users/{id}")
        Mono<String> getUser(@PathVar("id") String id);
    }

    @ReactiveHttpClient(name = "internal-policy-client")
    interface InternalPolicyClient extends InheritedPolicyOperations {
    }

    @ReactiveHttpClient(name = "partner-policy-client")
    interface PartnerPolicyClient extends InheritedPolicyOperations {
    }

    interface MethodTimeoutPolicyOperations {
        @TimeoutMs(350)
        @GET("/users/{id}")
        Mono<String> getUser(@PathVar("id") String id);
    }

    @ReactiveHttpClient(name = "method-timeout-policy-client", baseUrl = "http://method-timeout.annotation")
    interface MethodTimeoutPolicyClient extends MethodTimeoutPolicyOperations {
    }

    @ReactiveHttpClient(name = "api-ref-policy-client")
    interface ApiRefPolicyClient extends InheritedApiRefOperations {
    }

    @ReactiveHttpClient(name = "deprecated-timeout-policy-client")
    interface DeprecatedTimeoutPolicyClient extends InheritedPolicyOperations {
    }

    @ReactiveHttpClient(name = "disabled-timeout-policy-client")
    interface DisabledTimeoutPolicyClient extends InheritedPolicyOperations {
    }

    @ReactiveHttpClient(name = "invalid-header-client")
    interface BlankHeaderParamClient {
        @GET("/ping")
        Mono<String> call(@HeaderParam("") String tenant);
    }

    @ReactiveHttpClient(name = "invalid-idempotency-client")
    interface BlankIdempotencyKeyClient {
        @POST("/create")
        @IdempotencyKey(" ")
        Mono<String> call();
    }

    @ReactiveHttpClient(name = "annotation-url-client", baseUrl = "http://annotation.example")
    interface AnnotationBaseUrlClient {
        @GET("/ping")
        Mono<String> ping();
    }

    interface InheritedApiRefOperations {
        @ApiRef("user.lookup")
        Mono<String> lookup(@PathVar("id") String id);
    }

    @ReactiveHttpClient(name = "internal-inherited-api-ref-client")
    interface InternalInheritedApiRefClient extends InheritedApiRefOperations {
    }

    @ReactiveHttpClient(name = "partner-inherited-api-ref-client")
    interface PartnerInheritedApiRefClient extends InheritedApiRefOperations {
    }

    @ReactiveHttpClient(name = "missing-inherited-api-ref-client")
    interface MissingInheritedApiRefClient extends InheritedApiRefOperations {
    }

    @ReactiveHttpClient(name = "malformed-inherited-api-ref-client")
    interface MalformedInheritedApiRefClient extends InheritedApiRefOperations {
    }

    @ReactiveHttpClient(name = "pathvar-inherited-api-ref-client")
    interface PathVarInheritedApiRefClient extends InheritedApiRefOperations {
    }

}
