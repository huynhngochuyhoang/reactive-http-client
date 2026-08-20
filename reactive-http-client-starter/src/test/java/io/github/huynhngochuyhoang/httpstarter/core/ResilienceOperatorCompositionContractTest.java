package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.LogHttpExchange;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.github.huynhngochuyhoang.httpstarter.exception.LogicalCallTimeoutException;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import io.github.huynhngochuyhoang.httpstarter.observability.MicrometerHttpClientObserver;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.internal.AtomicRateLimiter;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.ConnectionProvider;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ResilienceOperatorCompositionContractTest {

    private static final String INSTANCE = "composition";
    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(5);
    private static final AtomicInteger POOL_SEQUENCE = new AtomicInteger();

    @Test
    void openCircuitRejectsBeforeTheInitialRequestAttempt() {
        OperatorFixture operators = operators(
                Duration.ofMillis(10),
                immediateRateLimiter());
        operators.circuitBreaker().transitionToOpenState();
        AtomicInteger dispatches = new AtomicInteger();

        try (ClientFixture fixture = client(successWebClient(dispatches), config(500, 1_000), operators, null)) {
            Throwable failure = catchThrowable(() -> fixture.client().call().block(BLOCK_TIMEOUT));

            assertThat(findCause(failure, CallNotPermittedException.class)).isNotNull();
            assertThat(dispatches).hasValue(0);
            assertThat(operators.circuitBreaker().getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(1);
            assertThat(operators.rateLimiter().getMetrics().getAvailablePermissions()).isEqualTo(10);
            assertBulkheadReleased(operators.bulkhead());
            fixture.diagnostics().assertZeroAttemptRejection(CallNotPermittedException.class);
        }
    }

    @Test
    void exhaustedRateLimiterRejectsBeforeTheInitialRequestAttempt() {
        OperatorFixture operators = operators(
                Duration.ofMillis(10),
                RateLimiterConfig.custom()
                        .limitForPeriod(1)
                        .limitRefreshPeriod(Duration.ofMinutes(1))
                        .timeoutDuration(Duration.ZERO)
                        .build());
        assertThat(operators.rateLimiter().acquirePermission()).isTrue();
        AtomicInteger dispatches = new AtomicInteger();

        try (ClientFixture fixture = client(successWebClient(dispatches), config(500, 1_000), operators, null)) {
            Throwable failure = catchThrowable(() -> fixture.client().call().block(BLOCK_TIMEOUT));

            assertThat(findCause(failure, RequestNotPermitted.class)).isNotNull();
            assertThat(dispatches).hasValue(0);
            assertThat(operators.circuitBreaker().getMetrics().getNumberOfBufferedCalls()).isEqualTo(1);
            assertBulkheadReleased(operators.bulkhead());
            fixture.diagnostics().assertZeroAttemptRejection(RequestNotPermitted.class);
        }
    }

    @Test
    void saturatedZeroWaitBulkheadRejectsBeforeTheInitialRequestAttempt() {
        OperatorFixture operators = operators(
                Duration.ofMillis(10),
                immediateRateLimiter());
        operators.bulkhead().acquirePermission();
        AtomicInteger dispatches = new AtomicInteger();

        try {
            try (ClientFixture fixture = client(
                    successWebClient(dispatches), config(500, 1_000), operators, null)) {
                Throwable failure = catchThrowable(() -> fixture.client().call().block(BLOCK_TIMEOUT));

                assertThat(findCause(failure, BulkheadFullException.class)).isNotNull();
                assertThat(dispatches).hasValue(0);
                assertThat(operators.circuitBreaker().getMetrics().getNumberOfBufferedCalls()).isZero();
                assertThat(operators.rateLimiter().getMetrics().getAvailablePermissions()).isEqualTo(10);
                fixture.diagnostics().assertZeroAttemptRejection(BulkheadFullException.class);
            }
        } finally {
            operators.bulkhead().releasePermission();
        }
        assertBulkheadReleased(operators.bulkhead());
    }

    @Test
    void delayedRateLimiterAdmissionCountsTowardDurationWithoutAddingAnAttempt() {
        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofMillis(200))
                .timeoutDuration(Duration.ofSeconds(1))
                .build();
        OperatorFixture operators = operators(
                Duration.ofMillis(10),
                rateLimiterConfig);
        AtomicInteger dispatches = new AtomicInteger();

        try (ClientFixture fixture = client(successWebClient(dispatches), config(500, 2_000), operators, null)) {
            RateLimiter rateLimiter = new AtomicRateLimiter(INSTANCE, rateLimiterConfig, () -> 0L);
            assertThat(operators.rateLimiterRegistry().replace(INSTANCE, rateLimiter))
                    .contains(operators.rateLimiter());
            assertThat(rateLimiter.acquirePermission()).isTrue();
            assertThat(fixture.client().call().block(BLOCK_TIMEOUT)).isEqualTo("ok");

            assertThat(dispatches).hasValue(1);
            assertThat(fixture.diagnostics().assertOneTerminalResult(1).getDurationMs())
                    .isGreaterThanOrEqualTo(100L);
        }
    }

    @Test
    void delayedBulkheadAdmissionCountsTowardDurationWithoutAddingAnAttempt() {
        OperatorFixture operators = operators(
                Duration.ofMillis(10),
                immediateRateLimiter(),
                BulkheadConfig.custom()
                        .maxConcurrentCalls(1)
                        .maxWaitDuration(Duration.ofSeconds(1))
                        .build());
        AtomicBoolean heldPermitReleased = new AtomicBoolean();
        AtomicInteger dispatches = new AtomicInteger();

        try (ClientFixture fixture = client(
                successWebClient(dispatches), config(500, 2_000), operators, null)) {
            operators.bulkhead().acquirePermission();
            Disposable release = Mono.delay(Duration.ofMillis(150))
                    .doOnNext(ignored -> {
                        if (heldPermitReleased.compareAndSet(false, true)) {
                            operators.bulkhead().releasePermission();
                        }
                    })
                    .subscribe();
            try {
                assertThat(fixture.client().call().block(BLOCK_TIMEOUT)).isEqualTo("ok");

                assertThat(dispatches).hasValue(1);
                assertThat(fixture.diagnostics().assertOneTerminalResult(1).getDurationMs())
                        .isGreaterThanOrEqualTo(100L);
            } finally {
                release.dispose();
                if (heldPermitReleased.compareAndSet(false, true)) {
                    operators.bulkhead().releasePermission();
                }
            }
        }
        assertBulkheadReleased(operators.bulkhead());
    }

    @Test
    void retryExhaustionIsOneOuterAdmissionWithPerAttemptTimeoutsAndOneTerminalResult() {
        try (TimeoutServer server = new TimeoutServer()) {
            OperatorFixture operators = operators(
                    Duration.ofMillis(10),
                    RateLimiterConfig.custom()
                            .limitForPeriod(10)
                            .limitRefreshPeriod(Duration.ofMinutes(1))
                            .timeoutDuration(Duration.ZERO)
                            .build());
            ReactiveHttpClientProperties.ClientConfig config = config(60, 1_000);
            ConnectionProvider provider = ConnectionProvider.create(
                    "resilience-composition-" + POOL_SEQUENCE.incrementAndGet(), 2);
            WebClient webClient = WebClient.builder()
                    .baseUrl(server.baseUrl())
                    .clientConnector(new ReactorClientHttpConnector(HttpClient.create(provider)))
                    .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                    .build();

            try (ClientFixture fixture = client(webClient, config, operators, provider)) {
                Throwable failure = catchThrowable(() -> fixture.client().timeout().block(BLOCK_TIMEOUT));

                assertThat(findCause(failure, ReadTimeoutException.class)).isNotNull();
                assertThat(server.dispatches()).hasValue(3);
                assertThat(operators.retry().getMetrics().getNumberOfFailedCallsWithRetryAttempt())
                        .isEqualTo(1);
                assertThat(operators.retry().getMetrics().getNumberOfTotalCalls()).isEqualTo(3);
                assertThat(operators.circuitBreaker().getMetrics().getNumberOfBufferedCalls()).isEqualTo(1);
                assertThat(operators.circuitBreaker().getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
                assertThat(operators.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
                assertThat(operators.rateLimiter().getMetrics().getAvailablePermissions()).isEqualTo(9);
                assertBulkheadReleased(operators.bulkhead());

                CompositionDiagnostics diagnostics = fixture.diagnostics();
                assertThat(diagnostics.starts).hasValue(1);
                assertThat(diagnostics.retries).hasValue(2);
                assertThat(diagnostics.errors).hasValue(1);
                assertThat(diagnostics.successes).hasValue(0);
                assertThat(diagnostics.cancellations).hasValue(0);
                diagnostics.assertOneTerminalResult(3);
            }
        }
    }

    @Test
    void callerCancellationDuringRateLimiterAdmissionRemainsAZeroAttemptTerminal() {
        OperatorFixture operators = operators(
                Duration.ofMillis(20),
                RateLimiterConfig.custom()
                        .limitForPeriod(1)
                        .limitRefreshPeriod(Duration.ofSeconds(1))
                        .timeoutDuration(Duration.ofSeconds(2))
                        .build());
        assertThat(operators.rateLimiter().acquirePermission()).isTrue();
        AtomicInteger dispatches = new AtomicInteger();
        WebClient webClient = webClient(request -> Mono.fromSupplier(() -> {
            dispatches.incrementAndGet();
            return ClientResponse.create(HttpStatus.OK).body("ok").build();
        }));

        try (ClientFixture fixture = client(webClient, config(500, 2_000), operators, null)) {
            Disposable subscription = fixture.client().call().subscribe();
            await(() -> operators.bulkhead().getMetrics().getAvailableConcurrentCalls() == 0,
                    "bulkhead admission was not held");

            subscription.dispose();

            await(() -> operators.bulkhead().getMetrics().getAvailableConcurrentCalls() == 1,
                    "bulkhead permit was not released after admission cancellation");
            sleep(Duration.ofMillis(100));
            assertThat(dispatches).hasValue(0);
            assertThat(operators.circuitBreaker().getMetrics().getNumberOfBufferedCalls()).isZero();
            assertThat(fixture.diagnostics().starts).hasValue(0);
            assertThat(fixture.diagnostics().retries).hasValue(0);
            assertThat(fixture.diagnostics().errors).hasValue(0);
            assertThat(fixture.diagnostics().successes).hasValue(0);
            assertThat(fixture.diagnostics().cancellations).hasValue(1);
            fixture.diagnostics().assertOneTerminalResult(0);
        }
    }

    @Test
    void logicalBudgetCancelsRateLimiterAdmissionBeforeTheRequestSourceIsSubscribed() {
        OperatorFixture operators = operators(
                Duration.ofMillis(20),
                RateLimiterConfig.custom()
                        .limitForPeriod(1)
                        .limitRefreshPeriod(Duration.ofMillis(300))
                        .timeoutDuration(Duration.ofSeconds(1))
                        .build());
        assertThat(operators.rateLimiter().acquirePermission()).isTrue();
        AtomicInteger dispatches = new AtomicInteger();
        WebClient webClient = webClient(request -> Mono.fromSupplier(() -> {
            dispatches.incrementAndGet();
            return ClientResponse.create(HttpStatus.OK).body("ok").build();
        }));

        try (ClientFixture fixture = client(webClient, config(500, 80), operators, null)) {
            AtomicReference<Throwable> failure = subscribeForFailure(fixture.client().call());

            await(() -> operators.bulkhead().getMetrics().getAvailableConcurrentCalls() == 0,
                    "bulkhead admission was not held");
            await(() -> failure.get() != null, "logical-call timeout did not terminate admission");
            assertThat(findCause(failure.get(), LogicalCallTimeoutException.class)).isNotNull();
            await(() -> operators.bulkhead().getMetrics().getAvailableConcurrentCalls() == 1,
                    "bulkhead permit was not released after admission timeout");
            sleep(Duration.ofMillis(350));
            assertThat(dispatches).hasValue(0);
            assertThat(operators.circuitBreaker().getMetrics().getNumberOfBufferedCalls()).isZero();
            fixture.diagnostics().assertOneTerminalResult(0);
        }
    }

    @Test
    void logicalBudgetCancelsRetryDelayAndReleasesOuterAdmission() {
        OperatorFixture operators = operators(
                Duration.ofMillis(300),
                RateLimiterConfig.custom()
                        .limitForPeriod(10)
                        .limitRefreshPeriod(Duration.ofMinutes(1))
                        .timeoutDuration(Duration.ZERO)
                        .build());
        AtomicInteger dispatches = new AtomicInteger();
        WebClient webClient = webClient(request -> Mono.defer(() -> {
            dispatches.incrementAndGet();
            return Mono.error(new IOException("retry later"));
        }));

        try (ClientFixture fixture = client(webClient, config(500, 80), operators, null)) {
            AtomicReference<Throwable> failure = subscribeForFailure(fixture.client().call());

            await(() -> dispatches.get() == 1, "first retry source subscription was not observed");
            assertThat(operators.bulkhead().getMetrics().getAvailableConcurrentCalls()).isZero();
            await(() -> failure.get() != null, "logical-call timeout did not cancel retry delay");
            assertThat(findCause(failure.get(), LogicalCallTimeoutException.class)).isNotNull();
            await(() -> operators.bulkhead().getMetrics().getAvailableConcurrentCalls() == 1,
                    "bulkhead permit was not released after retry-delay timeout");
            sleep(Duration.ofMillis(350));
            assertThat(dispatches).hasValue(1);
            assertThat(operators.circuitBreaker().getMetrics().getNumberOfBufferedCalls()).isZero();
            fixture.diagnostics().assertOneTerminalResult(1);
        }
    }

    @Test
    void callerCancellationDuringRetryDelayProducesOneTerminalWithoutLateUpdate() {
        OperatorFixture operators = operators(
                Duration.ofMillis(300),
                immediateRateLimiter());
        AtomicInteger dispatches = new AtomicInteger();
        WebClient webClient = webClient(request -> Mono.defer(() -> {
            dispatches.incrementAndGet();
            return Mono.error(new IOException("retry later"));
        }));

        try (ClientFixture fixture = client(webClient, config(500, 1_000), operators, null)) {
            Disposable subscription = fixture.client().call().subscribe(ignored -> { }, ignored -> { });

            await(() -> dispatches.get() == 1, "first retry source subscription was not observed");
            assertThat(operators.bulkhead().getMetrics().getAvailableConcurrentCalls()).isZero();
            subscription.dispose();

            await(() -> operators.bulkhead().getMetrics().getAvailableConcurrentCalls() == 1,
                    "bulkhead permit was not released after retry-delay cancellation");
            sleep(Duration.ofMillis(400));
            assertThat(dispatches).hasValue(1);
            assertThat(operators.circuitBreaker().getMetrics().getNumberOfBufferedCalls()).isZero();
            assertThat(fixture.diagnostics().cancellations).hasValue(1);
            fixture.diagnostics().assertOneTerminalResult(1);
        }
    }

    @Test
    void callerCancellationDuringExecutionReleasesOuterAdmissionWithoutRetry() {
        OperatorFixture operators = operators(
                Duration.ofMillis(50),
                RateLimiterConfig.custom()
                        .limitForPeriod(10)
                        .limitRefreshPeriod(Duration.ofMinutes(1))
                        .timeoutDuration(Duration.ZERO)
                        .build());
        AtomicInteger dispatches = new AtomicInteger();
        WebClient webClient = webClient(request -> {
            dispatches.incrementAndGet();
            return Mono.never();
        });

        try (ClientFixture fixture = client(webClient, config(500, 1_000), operators, null)) {
            Disposable subscription = fixture.client().call().subscribe();
            await(() -> dispatches.get() == 1, "request execution did not start");
            assertThat(operators.bulkhead().getMetrics().getAvailableConcurrentCalls()).isZero();

            subscription.dispose();

            await(() -> operators.bulkhead().getMetrics().getAvailableConcurrentCalls() == 1,
                    "bulkhead permit was not released after execution cancellation");
            sleep(Duration.ofMillis(100));
            assertThat(dispatches).hasValue(1);
            assertThat(operators.circuitBreaker().getMetrics().getNumberOfBufferedCalls()).isZero();
            assertThat(fixture.diagnostics().cancellations).hasValue(1);
            fixture.diagnostics().assertOneTerminalResult(1);
        }
    }

    @Test
    void callerCancellationDuringResponseConsumptionReleasesOuterAdmissionWithoutRetry() {
        OperatorFixture operators = operators(
                Duration.ofMillis(50),
                RateLimiterConfig.custom()
                        .limitForPeriod(10)
                        .limitRefreshPeriod(Duration.ofMinutes(1))
                        .timeoutDuration(Duration.ZERO)
                        .build());
        AtomicInteger dispatches = new AtomicInteger();
        AtomicInteger bodySubscriptions = new AtomicInteger();
        AtomicInteger bodyCancellations = new AtomicInteger();
        AtomicInteger received = new AtomicInteger();
        DefaultDataBufferFactory buffers = new DefaultDataBufferFactory();
        WebClient webClient = webClient(request -> Mono.fromSupplier(() -> {
            dispatches.incrementAndGet();
            Flux<DataBuffer> body = Flux.defer(() -> {
                bodySubscriptions.incrementAndGet();
                return Flux.concat(
                                Mono.just(buffers.wrap(new byte[]{1})),
                                Mono.<DataBuffer>never())
                        .doOnCancel(bodyCancellations::incrementAndGet);
            });
            return ClientResponse.create(HttpStatus.OK).body(body).build();
        }));

        try (ClientFixture fixture = client(webClient, config(500, 1_000), operators, null)) {
            Disposable subscription = fixture.client().stream().subscribe(buffer -> {
                received.incrementAndGet();
                DataBufferUtils.release(buffer);
            });
            await(() -> received.get() == 1, "response body did not begin");
            assertThat(operators.bulkhead().getMetrics().getAvailableConcurrentCalls()).isZero();

            subscription.dispose();

            await(() -> operators.bulkhead().getMetrics().getAvailableConcurrentCalls() == 1,
                    "bulkhead permit was not released after response cancellation");
            assertThat(dispatches).hasValue(1);
            assertThat(bodySubscriptions).hasValue(1);
            assertThat(bodyCancellations).hasValue(1);
            assertThat(operators.circuitBreaker().getMetrics().getNumberOfBufferedCalls()).isEqualTo(1);
            assertThat(operators.circuitBreaker().getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
            assertThat(fixture.diagnostics().cancellations).hasValue(1);
            fixture.diagnostics().assertOneTerminalResult(1);
        }
    }

    private static WebClient successWebClient(AtomicInteger dispatches) {
        return webClient(request -> Mono.fromSupplier(() -> {
            dispatches.incrementAndGet();
            return ClientResponse.create(HttpStatus.OK).body("ok").build();
        }));
    }

    private static RateLimiterConfig immediateRateLimiter() {
        return RateLimiterConfig.custom()
                .limitForPeriod(10)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build();
    }

    private static WebClient webClient(ExchangeFunction exchangeFunction) {
        return WebClient.builder()
                .baseUrl("http://composition.test")
                .exchangeFunction(exchangeFunction)
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .build();
    }

    private static ReactiveHttpClientProperties.ClientConfig config(
            long requestTimeoutMs, long logicalCallTimeoutMs) {
        ReactiveHttpClientProperties.ClientConfig config =
                new ReactiveHttpClientProperties.ClientConfig();
        config.setRequestTimeoutMs(requestTimeoutMs);
        config.setLogicalCallTimeoutMs(logicalCallTimeoutMs);
        ReactiveHttpClientProperties.ResilienceConfig resilience =
                new ReactiveHttpClientProperties.ResilienceConfig();
        resilience.setEnabled(true);
        resilience.setRetry(INSTANCE);
        resilience.setRateLimiter(INSTANCE);
        resilience.setCircuitBreaker(INSTANCE);
        resilience.setBulkhead(INSTANCE);
        resilience.setRetryMethods(java.util.Set.of("GET"));
        config.setResilience(resilience);
        return config;
    }

    private static OperatorFixture operators(
            Duration retryDelay, RateLimiterConfig rateLimiterConfig) {
        return operators(
                retryDelay,
                rateLimiterConfig,
                BulkheadConfig.custom()
                        .maxConcurrentCalls(1)
                        .maxWaitDuration(Duration.ZERO)
                        .build());
    }

    private static OperatorFixture operators(
            Duration retryDelay,
            RateLimiterConfig rateLimiterConfig,
            BulkheadConfig bulkheadConfig) {
        RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(retryDelay)
                .build());
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(2)
                        .minimumNumberOfCalls(1)
                        .failureRateThreshold(50)
                        .build());
        BulkheadRegistry bulkheadRegistry = BulkheadRegistry.of(bulkheadConfig);
        RateLimiterRegistry rateLimiterRegistry = RateLimiterRegistry.of(rateLimiterConfig);
        Retry retry = retryRegistry.retry(INSTANCE);
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(INSTANCE);
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(INSTANCE);
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(INSTANCE);
        return new OperatorFixture(
                retry,
                circuitBreaker,
                bulkhead,
                rateLimiter,
                rateLimiterRegistry,
                new Resilience4jOperatorApplier(
                        circuitBreakerRegistry, retryRegistry, bulkheadRegistry, rateLimiterRegistry));
    }

    private static ClientFixture client(
            WebClient webClient,
            ReactiveHttpClientProperties.ClientConfig config,
            OperatorFixture operators,
            ConnectionProvider connectionProvider) {
        StaticApplicationContext context = new StaticApplicationContext();
        CompositionDiagnostics diagnostics = new CompositionDiagnostics();
        context.getBeanFactory().registerSingleton("compositionDiagnostics", diagnostics);
        context.refresh();
        ReactiveClientInvocationHandler handler = ReactiveClientInvocationHandler.create(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "composition-client",
                CompositionClient.class,
                context,
                operators.applier(),
                null,
                new ReactiveHttpClientProperties.ObservabilityConfig());
        CompositionClient client = (CompositionClient) Proxy.newProxyInstance(
                CompositionClient.class.getClassLoader(),
                new Class<?>[]{CompositionClient.class},
                handler);
        return new ClientFixture(context, client, diagnostics, connectionProvider);
    }

    private static AtomicReference<Throwable> subscribeForFailure(Mono<String> call) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        call.subscribe(ignored -> {
        }, failure::set);
        return failure;
    }

    private static void assertBulkheadReleased(Bulkhead bulkhead) {
        assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls())
                .isEqualTo(bulkhead.getBulkheadConfig().getMaxConcurrentCalls());
    }

    private static <T extends Throwable> T findCause(Throwable error, Class<T> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause() != current ? current.getCause() : null;
        }
        return null;
    }

    private static void await(BooleanSupplier condition, String message) {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            sleep(Duration.ofMillis(10));
        }
        assertThat(condition.getAsBoolean()).as(message).isTrue();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    @LogHttpExchange(logger = CompositionDiagnostics.class)
    interface CompositionClient {
        @GET("/timeout")
        Mono<String> timeout();

        @GET("/call")
        Mono<String> call();

        @GET("/stream")
        Flux<DataBuffer> stream();
    }

    private record OperatorFixture(
            Retry retry,
            CircuitBreaker circuitBreaker,
            Bulkhead bulkhead,
            RateLimiter rateLimiter,
            RateLimiterRegistry rateLimiterRegistry,
            ResilienceOperatorApplier applier) {
    }

    private record ClientFixture(
            StaticApplicationContext context,
            CompositionClient client,
            CompositionDiagnostics diagnostics,
            ConnectionProvider connectionProvider)
            implements AutoCloseable {
        @Override
        public void close() {
            context.close();
            if (connectionProvider != null) {
                connectionProvider.disposeLater().block(BLOCK_TIMEOUT);
            }
        }
    }

    private static final class CompositionDiagnostics implements
            HttpClientObserver, ReactiveHttpClientLifecycleHook, HttpExchangeLogger {
        private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        private final MicrometerHttpClientObserver metrics = new MicrometerHttpClientObserver(
                meterRegistry, new ReactiveHttpClientProperties.ObservabilityConfig());
        private final List<HttpClientObserverEvent> observerEvents = new CopyOnWriteArrayList<>();
        private final List<HttpExchangeLogContext> exchangeLogs = new CopyOnWriteArrayList<>();
        private final AtomicInteger starts = new AtomicInteger();
        private final AtomicInteger retries = new AtomicInteger();
        private final AtomicInteger successes = new AtomicInteger();
        private final AtomicInteger errors = new AtomicInteger();
        private final AtomicInteger cancellations = new AtomicInteger();

        @Override
        public void record(HttpClientObserverEvent event) {
            observerEvents.add(event);
            metrics.record(event);
        }

        @Override
        public void onStart(ReactiveHttpClientLifecycleContext context) {
            starts.incrementAndGet();
        }

        @Override
        public void onRetryAttempt(ReactiveHttpClientLifecycleContext context) {
            retries.incrementAndGet();
        }

        @Override
        public void onSuccess(ReactiveHttpClientLifecycleContext context) {
            successes.incrementAndGet();
        }

        @Override
        public void onError(ReactiveHttpClientLifecycleContext context) {
            errors.incrementAndGet();
        }

        @Override
        public void onCancel(ReactiveHttpClientLifecycleContext context) {
            cancellations.incrementAndGet();
        }

        @Override
        public void log(HttpExchangeLogContext context) {
            exchangeLogs.add(context);
        }

        private HttpClientObserverEvent assertOneTerminalResult(int attemptCount) {
            await(() -> observerEvents.size() == 1 && exchangeLogs.size() == 1,
                    "terminal diagnostics did not converge");
            HttpClientObserverEvent event = observerEvents.getFirst();
            HttpExchangeLogContext exchange = exchangeLogs.getFirst();
            assertThat(event.getAttemptCount()).isEqualTo(attemptCount);
            assertThat(exchange.subscriptionAttemptCount()).isEqualTo(attemptCount);
            assertThat(event.getDurationMs()).isBetween(0L, BLOCK_TIMEOUT.toMillis() - 1L);
            assertThat(exchange.durationMs()).isEqualTo(event.getDurationMs());
            assertThat(meterRegistry.find("reactive.http.client.requests").timers())
                    .singleElement()
                    .satisfies(timer -> {
                        assertThat(timer.count()).isEqualTo(1);
                        assertThat(timer.totalTime(TimeUnit.MILLISECONDS))
                                .isCloseTo(event.getDurationMs(), org.assertj.core.data.Offset.offset(0.001));
                    });
            assertThat(meterRegistry.find("reactive.http.client.requests.attempts").summaries())
                    .singleElement()
                    .satisfies(summary -> {
                        assertThat(summary.count()).isEqualTo(1);
                        assertThat(summary.totalAmount()).isEqualTo(attemptCount);
                    });
            return event;
        }

        private void assertZeroAttemptRejection(Class<? extends Throwable> errorType) {
            HttpClientObserverEvent event = assertOneTerminalResult(0);
            HttpExchangeLogContext exchange = exchangeLogs.getFirst();
            assertThat(starts).hasValue(0);
            assertThat(retries).hasValue(0);
            assertThat(errors).hasValue(1);
            assertThat(successes).hasValue(0);
            assertThat(cancellations).hasValue(0);
            assertThat(event.getStatusCode()).isNull();
            assertThat(event.getError()).isInstanceOf(errorType);
            assertThat(event.getErrorCategory()).isEqualTo(ErrorCategory.RESILIENCE_ERROR);
            assertThat(event.getFailureStage()).isNull();
            assertThat(event.getRequestUrl()).isNull();
            assertThat(event.getRequestHeaders()).isEmpty();
            assertThat(exchange.responseStatus()).isNull();
            assertThat(exchange.responseHeaders()).isEmpty();
            assertThat(exchange.error()).isInstanceOf(errorType);
            assertThat(exchange.failureStage()).isNull();
            assertThat(exchange.requestUrl()).isNull();
            assertThat(exchange.requestHeaders()).isEmpty();

            var timer = meterRegistry.find("reactive.http.client.requests")
                    .tag("http.status_code", "NONE")
                    .tag("outcome", "UNKNOWN")
                    .tag("exception", errorType.getSimpleName())
                    .tag("error.category", "RESILIENCE_ERROR")
                    .tag("failure.stage", "none")
                    .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }
    }

    private static final class TimeoutServer implements AutoCloseable {
        private final AtomicInteger dispatches = new AtomicInteger();
        private final DisposableServer server = HttpServer.create()
                .port(0)
                .handle((request, response) -> {
                    dispatches.incrementAndGet();
                    return Mono.delay(Duration.ofMillis(200))
                            .then(response.sendString(Mono.just("late")).then());
                })
                .bindNow();

        private String baseUrl() {
            return "http://127.0.0.1:" + server.port();
        }

        private AtomicInteger dispatches() {
            return dispatches;
        }

        @Override
        public void close() {
            server.disposeNow();
        }
    }
}
