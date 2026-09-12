package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.filter.InboundHeadersWebFilter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import reactor.test.StepVerifierOptions;
import reactor.util.context.Context;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class InboundHeaderHandoffCharacterizationTest {

    private static final Duration VERIFY_TIMEOUT = Duration.ofSeconds(5);
    private static final String CONTEXT_KEY = RequestContext.INBOUND_HEADERS_CONTEXT_KEY;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void composedSchedulerSwitchPreservesHeadersAtTheReadBoundary(boolean usePublishOn) {
        Scheduler scheduler = Schedulers.newSingle("v31-header-read");
        Thread subscribingThread = Thread.currentThread();
        AtomicReference<Thread> readingThread = new AtomicReference<>();
        try {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
                    .header("x-fixture", "emitter"));
            StepVerifier.create(new InboundHeadersWebFilter().filter(exchange, ex -> {
                Mono<Integer> trigger = Mono.just(1);
                trigger = usePublishOn ? trigger.publishOn(scheduler) : trigger.subscribeOn(scheduler);
                return trigger.flatMap(ignored -> Mono.deferContextual(ctx -> {
                    readingThread.set(Thread.currentThread());
                    assertThat(ctx.hasKey(CONTEXT_KEY)).isTrue();
                    assertThat(RequestContext.inboundHeaders(ctx)).containsEntry("x-fixture", List.of("emitter"));
                    return Mono.empty();
                }));
            })).expectComplete().verify(VERIFY_TIMEOUT);
            assertThat(readingThread.get()).isNotNull().isNotSameAs(subscribingThread);
        } finally {
            scheduler.dispose();
        }
    }

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void independentSinkSubscriberRequiresExplicitHeaderSnapshot(boolean restore, boolean workerHasHeaders) {
        Sinks.Many<RequestContextSnapshot> sink = Sinks.many().unicast().onBackpressureBuffer();
        Context workerContext = workerHasHeaders
                ? RequestContext.withInboundHeaders(Context.empty(), Map.of("x-fixture", List.of("worker")))
                : Context.empty();
        var consumer = sink.asFlux().take(1).flatMap(snapshot -> {
            Mono<HeaderObservation> read = Mono.deferContextual(ctx -> Mono.just(new HeaderObservation(
                    ctx.hasKey(CONTEXT_KEY), RequestContext.inboundHeaders(ctx))));
            return restore ? read.contextWrite(snapshot::writeTo) : read;
        });
        Map<String, List<String>> expectedHeaders = restore ? Map.of("x-fixture", List.of("emitter"))
                : RequestContext.inboundHeaders(workerContext);

        StepVerifier.create(consumer, StepVerifierOptions.create().withInitialContext(workerContext))
                .then(() -> {
                    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
                            .header("x-fixture", "emitter"));
                    new InboundHeadersWebFilter().filter(exchange, ex -> Mono.deferContextual(ctx -> {
                        assertThat(ctx.hasKey(CONTEXT_KEY)).isTrue();
                        assertThat(RequestContext.inboundHeaders(ctx)).containsEntry("x-fixture", List.of("emitter"));
                        sink.tryEmitNext(RequestContextSnapshot.capture(ctx)).orThrow();
                        return Mono.empty();
                    })).block(VERIFY_TIMEOUT);
                })
                .assertNext(observed -> {
                    assertThat(observed.keyPresent()).isEqualTo(restore || workerHasHeaders);
                    assertThat(observed.headers()).isEqualTo(expectedHeaders);
                })
                .expectComplete().verify(VERIFY_TIMEOUT);
    }

    private record HeaderObservation(boolean keyPresent, Map<String, List<String>> headers) {
    }
}
