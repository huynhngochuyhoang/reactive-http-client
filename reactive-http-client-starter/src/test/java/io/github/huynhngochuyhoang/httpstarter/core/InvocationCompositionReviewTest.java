package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.CacheResponse;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.LogHttpExchange;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthRequest;
import io.github.huynhngochuyhoang.httpstarter.auth.InvalidatableAuthProvider;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientCacheOutcome;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
class InvocationCompositionReviewTest {
    enum Path { ORDINARY, MISS, HIT, RETRY, AUTH_REPLAY, AUTH_REPLAY_THEN_RETRY }

    @ParameterizedTest
    @EnumSource(Path.class)
    void factorySeparatesProbeOuterAttemptAndAuthReplayWithoutAMeterRegistry(Path path) {
        try (var fixture = new Fixture(path, true)) {
            if (path == Path.HIT) {
                assertThat(fixture.client.cached().block(Duration.ofSeconds(5))).isEqualTo("ok");
                fixture.trace.clear();
                fixture.records.clear();
            }
            Mono<String> call = path == Path.ORDINARY ? fixture.client.ordinary() : fixture.client.cached();
            assertThat(fixture.trace).isEmpty();
            assertThat(call.block(Duration.ofSeconds(5))).isEqualTo("ok");

            List<String> expected = new ArrayList<>();
            if (path != Path.ORDINARY) {
                expected.addAll(List.of("default", "upstream", "auth", "downstream"));
            }
            if (path != Path.HIT) {
                expected.addAll(List.of("default", "upstream"));
                if (path == Path.ORDINARY) { expected.add("auth"); }
                expected.addAll(List.of("downstream", "exchange"));
            }
            if (path == Path.AUTH_REPLAY || path == Path.AUTH_REPLAY_THEN_RETRY) {
                expected.addAll(List.of("invalidate", "auth", "downstream", "exchange"));
            }
            boolean retried = path == Path.RETRY || path == Path.AUTH_REPLAY_THEN_RETRY;
            if (retried) {
                expected.addAll(List.of("default", "upstream", "auth", "downstream", "exchange"));
            }
            assertThat(fixture.trace).containsExactlyElementsOf(expected);
            int attempts = path == Path.HIT ? 0 : retried ? 2 : 1;
            fixture.records.assertTerminal(attempts, path == Path.ORDINARY ? null
                    : path == Path.HIT ? HttpClientCacheOutcome.FRESH_HIT : HttpClientCacheOutcome.MISS_LOADER);
            assertThat(fixture.exchanges).hasValue(path == Path.AUTH_REPLAY_THEN_RETRY ? 3
                    : retried || path == Path.AUTH_REPLAY ? 2 : 1);

            if (path != Path.ORDINARY) {
                assertThat(fixture.factory.responseCacheSnapshot().currentSize()).isEqualTo(1);
                fixture.trace.clear();
                fixture.records.clear();
                assertThat(fixture.client.cached().block(Duration.ofSeconds(5))).isEqualTo("ok");
                assertThat(fixture.trace).containsExactly("default", "upstream", "auth", "downstream");
                fixture.records.assertTerminal(0, HttpClientCacheOutcome.FRESH_HIT);
            }
        }
    }

    @Test
    void unauthenticatedHitStillRunsBuilderAndDownstreamMutationsWithoutExchange() {
        try (var fixture = new Fixture(Path.MISS, false)) {
            assertThat(fixture.client.cached().block(Duration.ofSeconds(5))).isEqualTo("ok");
            assertThat(fixture.trace).containsExactly("default", "upstream", "downstream",
                    "default", "upstream", "downstream", "exchange");
            fixture.trace.clear();
            fixture.records.clear();
            assertThat(fixture.client.cached().block(Duration.ofSeconds(5))).isEqualTo("ok");
            assertThat(fixture.trace).containsExactly("default", "upstream", "downstream");
            assertThat(fixture.exchanges).hasValue(1);
            fixture.records.assertTerminal(0, HttpClientCacheOutcome.FRESH_HIT);
        }
    }

    @ReactiveHttpClient(name = "composition-review")
    @LogHttpExchange(logger = Records.class)
    interface Client {
        @GET("/read") Mono<String> ordinary();
        @GET("/read") @CacheResponse("read") Mono<String> cached();
    }

    static final class Records implements HttpClientObserver, ReactiveHttpClientLifecycleHook, HttpExchangeLogger {
        final List<HttpClientObserverEvent> events = new CopyOnWriteArrayList<>();
        final List<ReactiveHttpClientLifecycleContext> terminals = new CopyOnWriteArrayList<>();
        final List<HttpExchangeLogContext> logs = new CopyOnWriteArrayList<>();
        final AtomicInteger starts = new AtomicInteger();
        @Override public void record(HttpClientObserverEvent event) { events.add(event); }
        @Override public void log(HttpExchangeLogContext context) { logs.add(context); }
        @Override public void onStart(ReactiveHttpClientLifecycleContext context) { starts.incrementAndGet(); }
        @Override public void onRetryAttempt(ReactiveHttpClientLifecycleContext context) { starts.incrementAndGet(); }
        @Override public void onSuccess(ReactiveHttpClientLifecycleContext context) { terminals.add(context); }
        @Override public void onError(ReactiveHttpClientLifecycleContext context) { terminals.add(context); }
        @Override public void onCancel(ReactiveHttpClientLifecycleContext context) { terminals.add(context); }

        void clear() { events.clear(); terminals.clear(); logs.clear(); starts.set(0); }

        void assertTerminal(int attempts, HttpClientCacheOutcome outcome) {
            assertThat(events).singleElement().satisfies(event -> {
                assertThat(event.getAttemptCount()).isEqualTo(attempts);
                assertThat(event.getCacheOutcome()).isEqualTo(outcome);
                assertThat(event.getError()).isNull();
                assertThat(event.getStatusCode()).isEqualTo(attempts == 0 ? null : 200);
            });
            assertThat(terminals).singleElement().satisfies(context -> {
                assertThat(context.attemptNumber()).isEqualTo(attempts);
                assertThat(context.cacheOutcome()).isEqualTo(outcome);
            });
            assertThat(logs).singleElement().satisfies(context -> {
                assertThat(context.subscriptionAttemptCount()).isEqualTo(attempts);
                assertThat(context.cacheOutcome()).isEqualTo(outcome);
            });
            assertThat(starts).hasValue(attempts);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final GenericApplicationContext context = new GenericApplicationContext();
        final List<String> trace = new CopyOnWriteArrayList<>();
        final AtomicInteger exchanges = new AtomicInteger();
        final Records records = new Records();
        final Client client;
        final ReactiveHttpClientFactoryBean<?> factory;

        Fixture(Path path, boolean authenticated) {
            var properties = new ReactiveHttpClientProperties();
            properties.getObservability().getCache().setEnabled(true);
            var config = new ReactiveHttpClientProperties.ClientConfig();
            config.setBaseUrl("http://localhost");
            var policy = new ReactiveHttpClientProperties.CachePolicyConfig();
            policy.setTtlMs(60_000L);
            policy.setMaximumSize(16L);
            policy.setSharedResponse(true);
            policy.setWork(new ReactiveHttpClientProperties.CacheWorkConfig());
            policy.getWork().setMaximumConcurrentCallers(2L);
            policy.getWork().setMaximumConcurrentLoads(1L);
            config.getCache().getPolicies().put("read", policy);
            config.getCache().getCustomizations().put("builder", ReactiveHttpClientProperties.CacheCustomizationSafety.SAFE);
            config.getCache().getCustomizations().put("customizer", ReactiveHttpClientProperties.CacheCustomizationSafety.SAFE);
            config.getResilience().setEnabled(true);
            config.getResilience().setRetry("read");
            if (authenticated) { config.setAuthProvider("auth"); }
            properties.getClients().put("composition-review", config);
            context.registerBean(ReactiveHttpClientProperties.class, () -> properties);
            context.registerBean(MethodMetadataCache.class, MethodMetadataCache::new);
            context.registerBean("records", Records.class, () -> records);
            context.registerBean(RetryRegistry.class, () -> RetryRegistry.of(
                    RetryConfig.custom().maxAttempts(2).waitDuration(Duration.ZERO).build()));
            context.registerBean("auth", InvalidatableAuthProvider.class, () -> new InvalidatableAuthProvider() {
                @Override public Mono<AuthContext> getAuth(AuthRequest request) {
                    trace.add("auth");
                    assertThat(request.request().headers().getFirst("X-Default")).isEqualTo("fixed");
                    assertThat(request.request().headers().getFirst("X-Upstream")).isEqualTo("fixed");
                    return Mono.just(AuthContext.empty());
                }
                @Override public Mono<Void> invalidate() { trace.add("invalidate"); return Mono.empty(); }
            });
            // A replacement builder models already-applied Boot customization; factory assembly is real.
            context.registerBean("builder", WebClient.Builder.class, () -> WebClient.builder()
                    .defaultRequest(request -> { trace.add("default"); request.header("X-Default", "fixed"); })
                    .filter((request, next) -> {
                        trace.add("upstream");
                        return next.exchange(ClientRequest.from(request).header("X-Upstream", "fixed").build());
                    })
                    .exchangeFunction(request -> {
                        trace.add("exchange");
                        assertThat(request.url().getPath()).isEqualTo("/final");
                        int dispatch = exchanges.incrementAndGet();
                        HttpStatus status = dispatch == 1 && (path == Path.AUTH_REPLAY || path == Path.AUTH_REPLAY_THEN_RETRY)
                                ? HttpStatus.UNAUTHORIZED : dispatch == 1 && path == Path.RETRY
                                || dispatch == 2 && path == Path.AUTH_REPLAY_THEN_RETRY
                                ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK;
                        return Mono.just(ClientResponse.create(status).header("Content-Type", "text/plain").body("ok").build());
                    }));
            context.registerBean("customizer", ReactiveHttpClientCustomizer.class, () -> builder -> builder.filter((request, next) -> {
                trace.add("downstream");
                return next.exchange(ClientRequest.from(request).url(URI.create("http://localhost/final")).build());
            }));
            var definition = new RootBeanDefinition(ReactiveHttpClientFactoryBean.class);
            definition.getPropertyValues().add("type", Client.class);
            definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, Client.class);
            context.registerBeanDefinition("client", definition);
            context.refresh();
            client = context.getBean(Client.class);
            factory = (ReactiveHttpClientFactoryBean<?>) context.getBean("&client");
        }

        @Override public void close() { context.close(); }
    }
}
