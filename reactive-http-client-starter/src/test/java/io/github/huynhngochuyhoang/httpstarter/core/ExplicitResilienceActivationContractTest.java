package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class ExplicitResilienceActivationContractTest {

    @Test
    void enabledOnlyMonoSelectsNoOperator() {
        RecordingApplier applier = new RecordingApplier(true);

        try (ClientFixture fixture = client(enabledOnlyConfig(), applier)) {
            applier.clearLookups();
            Mono<String> call = fixture.client().mono();

            assertThat(applier.applications()).isEmpty();
            assertThat(applier.subscriptions()).isEmpty();
            assertThat(applier.lookups()).isEmpty();
            assertThat(call.block()).isEqualTo("ok");
            assertThat(applier.subscriptions()).isEmpty();
            assertThat(applier.lookups()).isEmpty();
            assertThat(fixture.dispatches()).hasValue(1);
        }
    }

    @Test
    void enabledOnlyFluxSelectsNoOperator() {
        RecordingApplier applier = new RecordingApplier(true);

        try (ClientFixture fixture = client(enabledOnlyConfig(), applier)) {
            applier.clearLookups();
            Flux<String> call = fixture.client().flux();

            assertThat(applier.applications()).isEmpty();
            assertThat(applier.subscriptions()).isEmpty();
            assertThat(applier.lookups()).isEmpty();
            assertThat(call.collectList().block()).containsExactly("ok");
            assertThat(applier.subscriptions()).isEmpty();
            assertThat(applier.lookups()).isEmpty();
            assertThat(fixture.dispatches()).hasValue(1);
        }
    }

    @Test
    void disabledMasterGateIgnoresExplicitSelections() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.getResilience().setRetry("default");
        config.getResilience().setRateLimiter("default");
        config.getResilience().setCircuitBreaker("default");
        config.getResilience().setBulkhead("default");
        RecordingApplier applier = new RecordingApplier(true);

        try (ClientFixture fixture = client(config, applier)) {
            assertThat(fixture.client().mono().block()).isEqualTo("ok");

            assertThat(applier.applications()).isEmpty();
            assertThat(applier.subscriptions()).isEmpty();
            assertThat(applier.lookups()).isEmpty();
            assertThat(fixture.dispatches()).hasValue(1);
        }
    }

    @Test
    void retryOnlySelectionAppliesOnlyRetry() {
        assertOnlyOperatorSelected(resilience -> resilience.setRetry("selected"), "retry");
    }

    @Test
    void circuitBreakerOnlySelectionAppliesOnlyCircuitBreaker() {
        assertOnlyOperatorSelected(
                resilience -> resilience.setCircuitBreaker("selected"), "circuit-breaker");
    }

    @Test
    void bulkheadOnlySelectionAppliesOnlyBulkhead() {
        assertOnlyOperatorSelected(resilience -> resilience.setBulkhead("selected"), "bulkhead");
    }

    @Test
    void rateLimiterOnlySelectionAppliesOnlyRateLimiter() {
        assertOnlyOperatorSelected(
                resilience -> resilience.setRateLimiter("selected"), "rate-limiter");
    }

    @Test
    void explicitDefaultSelectionsActivateEveryOperatorInCurrentOrder() {
        ReactiveHttpClientProperties.ClientConfig config = enabledOnlyConfig();
        config.getResilience().setRetry("default");
        config.getResilience().setRateLimiter("default");
        config.getResilience().setCircuitBreaker("default");
        config.getResilience().setBulkhead("default");
        RecordingApplier applier = new RecordingApplier(true);

        try (ClientFixture fixture = client(config, applier)) {
            Mono<String> call = fixture.client().mono();

            assertThat(applier.applications()).containsExactly(
                    "mono:retry=default",
                    "mono:rate-limiter=default",
                    "mono:circuit-breaker=default",
                    "mono:bulkhead=default");
            assertThat(call.block()).isEqualTo("ok");
            assertThat(applier.subscriptions()).containsExactly(
                    "mono:bulkhead=default",
                    "mono:circuit-breaker=default",
                    "mono:rate-limiter=default",
                    "mono:retry=default");
        }
    }

    @Test
    void namedClientLevelSelectionsActivateOnlyNamedOperators() {
        ReactiveHttpClientProperties.ClientConfig config = enabledOnlyConfig();
        config.getResilience().setRetry("client-retry");
        config.getResilience().setBulkhead("client-bulkhead");
        RecordingApplier applier = new RecordingApplier(true);

        try (ClientFixture fixture = client(config, applier)) {
            assertThat(fixture.client().mono().block()).isEqualTo("ok");

            assertThat(applier.applications()).containsExactly(
                    "mono:retry=client-retry",
                    "mono:bulkhead=client-bulkhead");
        }
    }

    @Test
    void methodAnnotationsSelectOperatorsAboveClientLevelConfiguration() {
        ReactiveHttpClientProperties.ClientConfig config = enabledOnlyConfig();
        config.getResilience().setRetry("client-retry");
        config.getResilience().setRateLimiter("client-rate-limiter");
        config.getResilience().setCircuitBreaker("client-circuit-breaker");
        config.getResilience().setBulkhead("client-bulkhead");
        RecordingApplier applier = new RecordingApplier(true);

        try (ClientFixture fixture = client(config, applier)) {
            assertThat(fixture.client().methodOverrides().block()).isEqualTo("ok");

            assertThat(applier.applications()).containsExactly(
                    "mono:retry=method-retry",
                    "mono:rate-limiter=method-rate-limiter",
                    "mono:circuit-breaker=method-circuit-breaker",
                    "mono:bulkhead=method-bulkhead");
        }
    }

    @Test
    void methodAnnotationsSelectOperatorsWhenClientLevelSelectionsAreAbsent() {
        RecordingApplier applier = new RecordingApplier(true);

        try (ClientFixture fixture = client(enabledOnlyConfig(), applier)) {
            assertThat(fixture.client().methodOverrides().block()).isEqualTo("ok");

            assertThat(applier.applications()).containsExactly(
                    "mono:retry=method-retry",
                    "mono:rate-limiter=method-rate-limiter",
                    "mono:circuit-breaker=method-circuit-breaker",
                    "mono:bulkhead=method-bulkhead");
        }
    }

    @Test
    void blankClientLevelNamesSelectNoOperator() {
        ReactiveHttpClientProperties.ClientConfig config = enabledOnlyConfig();
        config.getResilience().setRetry(" ");
        config.getResilience().setRateLimiter("");
        config.getResilience().setCircuitBreaker(" ");
        config.getResilience().setBulkhead("");
        RecordingApplier applier = new RecordingApplier(true);

        try (ClientFixture fixture = client(config, applier)) {
            assertThat(fixture.client().mono().block()).isEqualTo("ok");

            assertThat(applier.applications()).isEmpty();
        }
    }

    @Test
    void retryMethodsAloneDoNotActivateRetry() {
        ReactiveHttpClientProperties.ClientConfig config = enabledOnlyConfig();
        config.getResilience().setRetryMethods(java.util.Set.of("GET", "POST"));
        RecordingApplier applier = new RecordingApplier(true);

        try (ClientFixture fixture = client(config, applier)) {
            assertThat(fixture.client().mono().block()).isEqualTo("ok");
            assertThat(fixture.client().post().block()).isEqualTo("ok");

            assertThat(applier.applications()).isEmpty();
        }
    }

    @Test
    void explicitSelectionsRemainPassThroughWithoutApplyingUnavailableOperators() {
        ReactiveHttpClientProperties.ClientConfig config = enabledOnlyConfig();
        config.getResilience().setRetry("default");
        config.getResilience().setRateLimiter("default");
        config.getResilience().setCircuitBreaker("default");
        config.getResilience().setBulkhead("default");
        RecordingApplier applier = new RecordingApplier(false);

        try (ClientFixture fixture = client(config, applier)) {
            assertThat(fixture.client().mono().block()).isEqualTo("ok");

            assertThat(applier.applications()).isEmpty();
            assertThat(applier.subscriptions()).isEmpty();
            assertThat(fixture.dispatches()).hasValue(1);
        }
    }

    @Test
    void configurationDefaultsLeaveEveryOperatorUnselected() {
        ReactiveHttpClientProperties.ResilienceConfig resilience =
                new ReactiveHttpClientProperties.ResilienceConfig();

        assertThat(resilience.isEnabled()).isFalse();
        assertThat(resilience.getRetry()).isNull();
        assertThat(resilience.getRateLimiter()).isNull();
        assertThat(resilience.getCircuitBreaker()).isNull();
        assertThat(resilience.getBulkhead()).isNull();
        assertThat(resilience.getRetryMethods()).containsExactlyInAnyOrder("GET", "HEAD");
        assertThat(resilience.isStrictUnsafeRetryValidation()).isFalse();
        assertThat(ReactiveClientInvocationHandler.RESILIENCE_OPERATOR_ORDER)
                .isEqualTo("retry -> rate-limiter -> circuit-breaker -> bulkhead");
        assertThat(ReactiveClientInvocationHandler.RESILIENCE_SUBSCRIPTION_ORDER)
                .isEqualTo("logical-call-timeout -> bulkhead -> circuit-breaker -> "
                        + "rate-limiter -> retry -> request-attempt");
    }

    private static ReactiveHttpClientProperties.ClientConfig enabledOnlyConfig() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.getResilience().setEnabled(true);
        return config;
    }

    private static void assertOnlyOperatorSelected(
            Consumer<ReactiveHttpClientProperties.ResilienceConfig> selection,
            String operator) {
        ReactiveHttpClientProperties.ClientConfig config = enabledOnlyConfig();
        selection.accept(config.getResilience());
        RecordingApplier applier = new RecordingApplier(true);

        try (ClientFixture fixture = client(config, applier)) {
            assertThat(fixture.client().mono().block()).isEqualTo("ok");

            assertThat(applier.applications()).containsExactly("mono:" + operator + "=selected");
            assertThat(applier.subscriptions()).containsExactly("mono:" + operator + "=selected");
            assertThat(fixture.dispatches()).hasValue(1);
        }
    }

    private static ClientFixture client(
            ReactiveHttpClientProperties.ClientConfig config,
            RecordingApplier applier) {
        AtomicInteger dispatches = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://implicit-resilience.test")
                .exchangeFunction(request -> Mono.fromSupplier(() -> {
                    dispatches.incrementAndGet();
                    return ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                            .body("ok")
                            .build();
                }))
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .build();
        StaticApplicationContext context = new StaticApplicationContext();
        context.refresh();
        ReactiveClientInvocationHandler handler = ReactiveClientInvocationHandler.create(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "implicit-resilience-client",
                BaselineClient.class,
                context,
                applier,
                null,
                new ReactiveHttpClientProperties.ObservabilityConfig());
        BaselineClient client = (BaselineClient) Proxy.newProxyInstance(
                BaselineClient.class.getClassLoader(),
                new Class<?>[]{BaselineClient.class},
                handler);
        return new ClientFixture(context, client, dispatches);
    }

    interface BaselineClient {
        @GET("/mono")
        Mono<String> mono();

        @GET("/flux")
        Flux<String> flux();

        @POST("/post")
        Mono<String> post();

        @GET("/method-overrides")
        @Retry("method-retry")
        @RateLimiter("method-rate-limiter")
        @CircuitBreaker("method-circuit-breaker")
        @Bulkhead("method-bulkhead")
        Mono<String> methodOverrides();
    }

    private record ClientFixture(
            StaticApplicationContext context,
            BaselineClient client,
            AtomicInteger dispatches) implements AutoCloseable {
        @Override
        public void close() {
            context.close();
        }
    }

    private static final class RecordingApplier implements ResilienceOperatorApplier {
        private final boolean available;
        private final List<String> applications = new ArrayList<>();
        private final List<String> subscriptions = new ArrayList<>();
        private final List<String> lookups = new ArrayList<>();

        private RecordingApplier(boolean available) {
            this.available = available;
        }

        private List<String> applications() {
            return applications;
        }

        private List<String> subscriptions() {
            return subscriptions;
        }

        private List<String> lookups() {
            return lookups;
        }

        private void clearLookups() {
            lookups.clear();
        }

        @Override
        public <T> Mono<T> applyCircuitBreaker(Mono<T> mono, String instanceName) {
            return apply(mono, "circuit-breaker", instanceName);
        }

        @Override
        public <T> Flux<T> applyCircuitBreaker(Flux<T> flux, String instanceName) {
            return apply(flux, "circuit-breaker", instanceName);
        }

        @Override
        public <T> Mono<T> applyRetry(Mono<T> mono, String instanceName) {
            return apply(mono, "retry", instanceName);
        }

        @Override
        public <T> Flux<T> applyRetry(Flux<T> flux, String instanceName) {
            return apply(flux, "retry", instanceName);
        }

        @Override
        public <T> Mono<T> applyBulkhead(Mono<T> mono, String instanceName) {
            return apply(mono, "bulkhead", instanceName);
        }

        @Override
        public <T> Flux<T> applyBulkhead(Flux<T> flux, String instanceName) {
            return apply(flux, "bulkhead", instanceName);
        }

        @Override
        public <T> Mono<T> applyRateLimiter(Mono<T> mono, String instanceName) {
            return apply(mono, "rate-limiter", instanceName);
        }

        @Override
        public <T> Flux<T> applyRateLimiter(Flux<T> flux, String instanceName) {
            return apply(flux, "rate-limiter", instanceName);
        }

        @Override
        public boolean isOperatorAvailable(InstanceType type) {
            lookups.add("available:" + type);
            return available;
        }

        @Override
        public boolean isInstanceConfigured(InstanceType type, String instanceName) {
            lookups.add("configured:" + type + "=" + instanceName);
            return true;
        }

        @Override
        public boolean canRetryMoreThanOnce(String instanceName) {
            lookups.add("retry-capacity:" + instanceName);
            return available;
        }

        private <T> Mono<T> apply(Mono<T> mono, String operator, String instanceName) {
            String observation = "mono:" + operator + "=" + instanceName;
            applications.add(observation);
            if (!available) {
                return mono;
            }
            return Mono.defer(() -> {
                subscriptions.add(observation);
                return mono;
            });
        }

        private <T> Flux<T> apply(Flux<T> flux, String operator, String instanceName) {
            String observation = "flux:" + operator + "=" + instanceName;
            applications.add(observation);
            if (!available) {
                return flux;
            }
            return Flux.defer(() -> {
                subscriptions.add(observation);
                return flux;
            });
        }
    }
}
