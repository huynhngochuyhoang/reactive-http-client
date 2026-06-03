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
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
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
                .hasMessageContaining("@PathVar value must not be blank")
                .hasMessageContaining("get");
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
                .hasMessageContaining("without matching @PathVar parameters");
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
}
