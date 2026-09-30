package io.github.huynhngochuyhoang.httpstarter.benchmarks;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.core.*;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import io.netty.buffer.PooledByteBufAllocator;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.SignalType;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class V34WorkloadContractTest {
    static Stream<Arguments> warmRows() {
        return Stream.of(V34WorkloadFixture.Profile.values()).flatMap(profile -> Stream.of("GET", "TARGET")
                .map(scenario -> Arguments.of(profile.name(), scenario)));
    }
    static Stream<Arguments> loopbackRows() {
        return Stream.of("MINIMAL", "AUTO_NO_REGISTRY", "AUTO_REGISTRY")
                .flatMap(profile -> Stream.of(V34WorkloadFixture.Scenario.values())
                        .map(scenario -> Arguments.of(profile, scenario.name())));
    }

    @ParameterizedTest @MethodSource("warmRows")
    void assemblyAndSubscriptionAreSeparateAndTelemetryUsesTheRealApi(String profile, String scenario) {
        var state = new V34DefaultPathBenchmark.Warm();
        state.profile = profile;
        state.scenario = scenario;
        var benchmark = new V34DefaultPathBenchmark();
        try {
            state.setup();
            long before = state.fixture.dispatches.get();
            benchmark.defaultV34NoNetworkWarmPublisher(state);
            assertThat(state.fixture.dispatches).hasValue(before);
            state.fixture.verifyResult(benchmark.defaultV34NoNetworkWarmSubscription(state));
            state.fixture.verifyResult(benchmark.defaultV34NoNetworkWarmSubscription(state));
            assertThat(state.fixture.dispatches).hasValue(before + 2);
        } finally { state.close(); }
    }

    @ParameterizedTest @MethodSource("loopbackRows")
    void loopbackConsumesExactRequestsAndResultsAndClosesItsResources(String profile, String scenario) {
        var state = new V34DefaultPathBenchmark.Loopback();
        state.profile = profile;
        state.scenario = scenario;
        try {
            state.setup();
            state.fixture.checking = true;
            state.fixture.verifyResult(new V34DefaultPathBenchmark().defaultV34LoopbackCall(state));
        } finally { state.close(); }
        assertThat(state.fixture.closed).isTrue();
    }

    @Test
    void constructionAndFirstCallsDoNotWarmEachOther() {
        var benchmark = new V34ConstructionBenchmark();
        for (String profile : List.of("AUTO_NO_REGISTRY", "AUTO_REGISTRY")) {
            var construction = new V34ConstructionBenchmark.Construction();
            construction.profile = profile;
            try { assertThat(benchmark.defaultV34ConstructionContextAndProxy(construction)).isNotNull(); }
            finally { construction.close(); }
            var first = new V34ConstructionBenchmark.First();
            first.profile = profile;
            try {
                first.setup();
                benchmark.defaultV34ConstructionFirstPublisher(first);
                assertThat(first.fixture.dispatches).hasValue(0);
            } finally { first.close(); }
            try {
                first.setup();
                assertThat(benchmark.defaultV34ConstructionFirstCall(first)).isEqualTo("value");
                assertThat(first.fixture.dispatches).hasValue(1);
            } finally { first.close(); }
        }
    }

    @Test
    void requestAndMetricWitnessesRejectIncorrectWork() {
        try (var fixture = new V34WorkloadFixture(V34WorkloadFixture.Profile.AUTO_REGISTRY,
                V34WorkloadFixture.Scenario.GET, false)) {
            fixture.verify();
            assertThatThrownBy(() -> fixture.verifyResult("wrong")).hasMessageContaining("decoded");
            fixture.registry.clear();
            assertThatThrownBy(fixture::verifyTotals).hasMessageContaining("timer count");
            fixture.violation.set("method/target/header");
            assertThatThrownBy(fixture::verifyTotals).hasMessageContaining("method/target/header");
        }
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void streamingTerminalWaitsForAttachmentAndAcknowledgesBodyCleanup(boolean cancel) throws Exception {
        AtomicInteger exchanges = new AtomicInteger();
        AtomicInteger released = new AtomicInteger();
        CountDownLatch attached = new CountDownLatch(1);
        CountDownLatch terminated = new CountDownLatch(1);
        var terminal = new java.util.concurrent.atomic.AtomicReference<SignalType>();
        Sinks.Many<DataBuffer> body = Sinks.many().unicast().onBackpressureBuffer();
        try (var context = new GenericApplicationContext()) {
            context.refresh();
            WebClient web = WebClient.builder().baseUrl("http://benchmark.invalid").exchangeFunction(request -> {
                assertThat(request.method().name()).isEqualTo("GET");
                assertThat(request.url().getPath()).isEqualTo("/stream");
                exchanges.incrementAndGet();
                return reactor.core.publisher.Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/octet-stream")
                        .body(body.asFlux().doOnSubscribe(ignored -> attached.countDown())
                                .doFinally(signal -> { terminal.set(signal); terminated.countDown(); }))
                        .build());
            }).build();
            var handler = ReactiveClientInvocationHandler.create(web, new MethodMetadataCache(),
                    new RequestArgumentResolver(), new DefaultErrorDecoder(), new ReactiveHttpClientProperties.ClientConfig(),
                    "v34-stream", Streaming.class, context, new NoopResilienceOperatorApplier(),
                    new BenchmarkJsonCodecFactory().create(), new ReactiveHttpClientProperties.ObservabilityConfig());
            Streaming client = (Streaming) Proxy.newProxyInstance(Streaming.class.getClassLoader(), new Class<?>[]{Streaming.class}, handler);
            Flux<DataBuffer> publisher = client.stream();
            assertThat(exchanges).hasValue(0);
            var subscriber = new BaseSubscriber<DataBuffer>() {
                @Override protected void hookOnNext(DataBuffer value) {
                    DataBufferUtils.release(value);
                    released.incrementAndGet();
                }
            };
            try {
                publisher.subscribe(subscriber);
                assertThat(attached.await(10, TimeUnit.SECONDS)).isTrue();
                var buffer = new NettyDataBufferFactory(PooledByteBufAllocator.DEFAULT).allocateBuffer(2);
                buffer.write(new byte[]{1, 2});
                assertThat(body.tryEmitNext(buffer)).isEqualTo(Sinks.EmitResult.OK);
                assertThat(released).hasValue(1);
                if (cancel) subscriber.cancel();
                else assertThat(body.tryEmitComplete()).isEqualTo(Sinks.EmitResult.OK);
                assertThat(terminated.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(terminal.get()).isEqualTo(cancel ? SignalType.CANCEL : SignalType.ON_COMPLETE);
                assertThat(buffer.getNativeBuffer().refCnt()).isZero();
                assertThat(exchanges).hasValue(1);
            } finally { subscriber.dispose(); }
        }
    }

    interface Streaming { @GET("/stream") Flux<DataBuffer> stream(); }
}
