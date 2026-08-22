package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the per-method resilience overrides from roadmap 1.9.
 */
class PerMethodResilienceTest {

    @Test
    void perMethodAnnotationsAreCapturedOnMethodMetadata() throws Exception {
        MethodMetadataCache cache = new MethodMetadataCache();
        Method method = SampleClient.class.getMethod("hotPath");

        MethodMetadata meta = cache.get(method);

        assertThat(meta.getRetryInstanceName()).isEqualTo("hot-retry");
        assertThat(meta.getCircuitBreakerInstanceName()).isEqualTo("hot-cb");
        assertThat(meta.getBulkheadInstanceName()).isEqualTo("hot-bulkhead");
        assertThat(meta.getRateLimiterInstanceName()).isEqualTo("hot-rate-limiter");
    }

    @Test
    void perMethodOverrideWinsOverClientLevelInstance() {
        // method asks for "hot-retry"; client-level config says "default-retry"
        String resolved = invokeResolveResilienceInstanceName("hot-retry", "default-retry");
        assertThat(resolved).isEqualTo("hot-retry");
    }

    @Test
    void clientLevelInstanceIsUsedWhenNoMethodOverride() {
        String resolved = invokeResolveResilienceInstanceName(null, "default-retry");
        assertThat(resolved).isEqualTo("default-retry");
    }

    @Test
    void blankAnnotationValueIsRejectedAtParse() {
        MethodMetadataCache cache = new MethodMetadataCache();
        assertThatThrownBy(() -> cache.get(InvalidClient.class.getMethod("blankRetry")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@Retry");
    }

    @Test
    void noopApplierReportsAllInstancesConfigured() {
        ResilienceOperatorApplier applier = new NoopResilienceOperatorApplier();

        assertThat(applier.isInstanceConfigured(ResilienceOperatorApplier.InstanceType.RETRY, "anything")).isTrue();
        assertThat(applier.isInstanceConfigured(ResilienceOperatorApplier.InstanceType.CIRCUIT_BREAKER, "anything")).isTrue();
        assertThat(applier.isInstanceConfigured(ResilienceOperatorApplier.InstanceType.BULKHEAD, "anything")).isTrue();
        assertThat(applier.isInstanceConfigured(ResilienceOperatorApplier.InstanceType.RATE_LIMITER, "anything")).isTrue();
    }

    @Test
    void resilience4jApplierReportsConfiguredVsMissingInstances() {
        RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.ofDefaults());
        retryRegistry.retry("explicitly-configured");
        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();
        cbRegistry.circuitBreaker("explicit-cb");
        BulkheadRegistry bhRegistry = BulkheadRegistry.of(BulkheadConfig.ofDefaults());
        bhRegistry.bulkhead("explicit-bh");
        RateLimiterRegistry rateLimiterRegistry = RateLimiterRegistry.of(RateLimiterConfig.ofDefaults());
        rateLimiterRegistry.rateLimiter("explicit-rl");

        Resilience4jOperatorApplier applier = new Resilience4jOperatorApplier(
                cbRegistry, retryRegistry, bhRegistry, rateLimiterRegistry);

        assertThat(applier.isInstanceConfigured(ResilienceOperatorApplier.InstanceType.RETRY, "explicitly-configured")).isTrue();
        assertThat(applier.isInstanceConfigured(ResilienceOperatorApplier.InstanceType.RETRY, "missing")).isFalse();

        assertThat(applier.isInstanceConfigured(ResilienceOperatorApplier.InstanceType.CIRCUIT_BREAKER, "explicit-cb")).isTrue();
        assertThat(applier.isInstanceConfigured(ResilienceOperatorApplier.InstanceType.CIRCUIT_BREAKER, "missing")).isFalse();

        assertThat(applier.isInstanceConfigured(ResilienceOperatorApplier.InstanceType.BULKHEAD, "explicit-bh")).isTrue();
        assertThat(applier.isInstanceConfigured(ResilienceOperatorApplier.InstanceType.BULKHEAD, "missing")).isFalse();

        assertThat(applier.isInstanceConfigured(ResilienceOperatorApplier.InstanceType.RATE_LIMITER, "explicit-rl")).isTrue();
        assertThat(applier.isInstanceConfigured(ResilienceOperatorApplier.InstanceType.RATE_LIMITER, "missing")).isFalse();
    }

    @Test
    void blankInstanceNameAlwaysReportsConfigured() {
        Resilience4jOperatorApplier applier = new Resilience4jOperatorApplier(
                CircuitBreakerRegistry.ofDefaults(), null, null, null);
        assertThat(applier.isInstanceConfigured(ResilienceOperatorApplier.InstanceType.CIRCUIT_BREAKER, null)).isTrue();
        assertThat(applier.isInstanceConfigured(ResilienceOperatorApplier.InstanceType.CIRCUIT_BREAKER, "")).isTrue();
    }

    @Test
    void factoryBeanFailsFastOnMissingPerMethodInstance() {
        // Registry has only "real-retry"; the test interface references "ghost-retry"
        RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.ofDefaults());
        retryRegistry.retry("real-retry");
        RateLimiterRegistry rateLimiterRegistry = RateLimiterRegistry.of(RateLimiterConfig.ofDefaults());
        rateLimiterRegistry.rateLimiter("real-rate-limiter");

        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.getBeanFactory().registerSingleton("retryRegistry", retryRegistry);
        ctx.getBeanFactory().registerSingleton("rateLimiterRegistry", rateLimiterRegistry);
        ctx.refresh();

        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig clientConfig = new ReactiveHttpClientProperties.ClientConfig();
        clientConfig.setBaseUrl("http://test.local");
        ReactiveHttpClientProperties.ResilienceConfig resilienceConfig = new ReactiveHttpClientProperties.ResilienceConfig();
        resilienceConfig.setEnabled(true);
        resilienceConfig.setRetryMethods(java.util.Set.of("POST"));
        resilienceConfig.setStrictUnsafeRetryValidation(true);
        clientConfig.setResilience(resilienceConfig);
        properties.getClients().put("ghost-client", clientConfig);
        ctx.getBeanFactory().registerSingleton("reactiveHttpClientProperties", properties);

        ReactiveHttpClientFactoryBean<MissingInstanceClient> factory = new ReactiveHttpClientFactoryBean<>();
        factory.setApplicationContext(ctx);
        factory.setType(MissingInstanceClient.class);

        assertThatThrownBy(factory::getObject)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("undefined Resilience4j instances")
                .hasMessageContaining("@Retry(\"ghost-retry\")")
                .hasMessageContaining("@RateLimiter(\"ghost-rate-limiter\")")
                .hasMessageNotContaining("strict unsafe retry validation");

        ctx.close();
    }

    @Test
    void factoryBeanFailsFastOnMissingApiRefMapping() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.refresh();

        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig clientConfig = new ReactiveHttpClientProperties.ClientConfig();
        clientConfig.setBaseUrl("http://test.local");
        clientConfig.setApis(Map.of());
        properties.getClients().put("ghost-apiref-client", clientConfig);
        ctx.getBeanFactory().registerSingleton("reactiveHttpClientProperties", properties);

        ReactiveHttpClientFactoryBean<MissingApiRefMappingClient> factory = new ReactiveHttpClientFactoryBean<>();
        factory.setApplicationContext(ctx);
        factory.setType(MissingApiRefMappingClient.class);

        assertThatThrownBy(factory::getObject)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("references @ApiRef(\"user.getById\")")
                .hasMessageContaining("is not configured.");

        ctx.close();
    }

    @Test
    void factoryBeanFailsFastWhenApiRefMethodIsBlank() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.refresh();

        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig clientConfig = new ReactiveHttpClientProperties.ClientConfig();
        clientConfig.setBaseUrl("http://test.local");
        ReactiveHttpClientProperties.ApiConfig apiConfig = new ReactiveHttpClientProperties.ApiConfig();
        apiConfig.setMethod(" ");
        apiConfig.setPath("/users/{id}");
        clientConfig.setApis(Map.of("user.getById", apiConfig));
        properties.getClients().put("ghost-apiref-client", clientConfig);
        ctx.getBeanFactory().registerSingleton("reactiveHttpClientProperties", properties);

        ReactiveHttpClientFactoryBean<MissingApiRefMappingClient> factory = new ReactiveHttpClientFactoryBean<>();
        factory.setApplicationContext(ctx);
        factory.setType(MissingApiRefMappingClient.class);

        assertThatThrownBy(factory::getObject)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reactive.http.clients.ghost-apiref-client.apis[user.getById].method is blank.");

        ctx.close();
    }

    @Test
    void factoryBeanFailsFastWhenApiRefPathIsBlank() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.refresh();

        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig clientConfig = new ReactiveHttpClientProperties.ClientConfig();
        clientConfig.setBaseUrl("http://test.local");
        ReactiveHttpClientProperties.ApiConfig apiConfig = new ReactiveHttpClientProperties.ApiConfig();
        apiConfig.setMethod("GET");
        apiConfig.setPath(" ");
        clientConfig.setApis(Map.of("user.getById", apiConfig));
        properties.getClients().put("ghost-apiref-client", clientConfig);
        ctx.getBeanFactory().registerSingleton("reactiveHttpClientProperties", properties);

        ReactiveHttpClientFactoryBean<MissingApiRefMappingClient> factory = new ReactiveHttpClientFactoryBean<>();
        factory.setApplicationContext(ctx);
        factory.setType(MissingApiRefMappingClient.class);

        assertThatThrownBy(factory::getObject)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reactive.http.clients.ghost-apiref-client.apis[user.getById].path is blank.");

        ctx.close();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Reflectively invokes the package-private static helper
     * {@code resolveResilienceInstanceName} on {@link ReactiveClientInvocationHandler}
     * so we don't have to spin up a full proxy to test the priority rule.
     */
    private static String invokeResolveResilienceInstanceName(String methodLevel, String clientLevel) {
        try {
            Method m = ReactiveClientInvocationHandler.class.getDeclaredMethod(
                    "resolveResilienceInstanceName", String.class, String.class);
            m.setAccessible(true);
            return (String) m.invoke(null, methodLevel, clientLevel);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    interface SampleClient {
        @GET("/hot")
        @Retry("hot-retry")
        @CircuitBreaker("hot-cb")
        @Bulkhead("hot-bulkhead")
        @RateLimiter("hot-rate-limiter")
        Mono<String> hotPath();
    }

    interface InvalidClient {
        @GET("/x")
        @Retry("")
        Mono<String> blankRetry();
    }

    @ReactiveHttpClient(name = "ghost-client", baseUrl = "http://test.local")
    interface MissingInstanceClient {
        @POST("/x")
        @Retry("ghost-retry")
        @RateLimiter("ghost-rate-limiter")
        Mono<String> ghost();
    }

    @ReactiveHttpClient(name = "ghost-apiref-client", baseUrl = "http://test.local")
    interface MissingApiRefMappingClient {
        @ApiRef("user.getById")
        Mono<String> ghost();
    }
}
