package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.filter.CorrelationIdWebFilter;
import io.github.huynhngochuyhoang.httpstarter.filter.InboundHeadersWebFilter;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestContextSnapshotTest {

    @Test
    void capturesEmptyContext() {
        RequestContextSnapshot snapshot = RequestContextSnapshot.capture(Context.empty());

        assertThat(snapshot).isSameAs(RequestContextSnapshot.empty());
        assertThat(snapshot.isEmpty()).isTrue();

        StepVerifier.create(Mono.deferContextual(ctx -> Mono.just(ctx.hasKey(CorrelationIdWebFilter.CORRELATION_ID_CONTEXT_KEY)))
                        .contextWrite(snapshot::writeTo))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void capturesAndRestoresCorrelationIdAndInboundHeaders() {
        Map<String, List<String>> inboundHeaders = new LinkedHashMap<>();
        inboundHeaders.put("X-Request-Id", new ArrayList<>(List.of("req-7")));
        inboundHeaders.put("Authorization", new ArrayList<>(List.of("[REDACTED]")));

        RequestContextSnapshot snapshot = RequestContextSnapshot.capture(Context.of(
                CorrelationIdWebFilter.CORRELATION_ID_CONTEXT_KEY, "cid-1",
                InboundHeadersWebFilter.INBOUND_HEADERS_CONTEXT_KEY, inboundHeaders));

        inboundHeaders.get("X-Request-Id").add("mutated");
        inboundHeaders.put("X-After", List.of("after"));

        assertThat(snapshot.correlationId()).isEqualTo("cid-1");
        assertThat(snapshot.inboundHeaders()).containsOnlyKeys("X-Request-Id", "Authorization");
        assertThat(snapshot.inboundHeaders().get("X-Request-Id")).containsExactly("req-7");
        assertThatThrownBy(() -> snapshot.inboundHeaders().put("X-New", List.of("new")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.inboundHeaders().get("X-Request-Id").add("mutated"))
                .isInstanceOf(UnsupportedOperationException.class);

        StepVerifier.create(restoredContextValues().contextWrite(snapshot::writeTo))
                .expectNext("cid-1|req-7|[REDACTED]")
                .verifyComplete();
    }

    @Test
    void restoresSnapshotOverEmptySubscriberContext() {
        RequestContextSnapshot snapshot = new RequestContextSnapshot("snapshot-cid",
                Map.of("X-Request-Id", List.of("snapshot-req")));

        StepVerifier.create(restoredContextValues().contextWrite(snapshot::writeTo))
                .expectNext("snapshot-cid|snapshot-req|null")
                .verifyComplete();
    }

    @Test
    void snapshotRestoreOverridesExistingSubscriberContextValues() {
        RequestContextSnapshot snapshot = new RequestContextSnapshot("snapshot-cid",
                Map.of("X-Request-Id", List.of("snapshot-req")));

        StepVerifier.create(restoredContextValues()
                        .contextWrite(ctx -> snapshot.writeTo(RequestContext.withInboundHeaders(
                                RequestContext.withCorrelationId(ctx, "subscriber-cid"),
                                Map.of("X-Request-Id", List.of("subscriber-req"))))))
                .expectNext("snapshot-cid|snapshot-req|null")
                .verifyComplete();
    }

    @Test
    void typedHelpersPreserveExistingStringKeyCompatibility() {
        Context helperContext = RequestContext.withInboundHeaders(
                RequestContext.withCorrelationId(Context.empty(), "helper-cid"),
                Map.of("X-Request-Id", List.of("helper-req")));

        assertThat(helperContext.<String>get(CorrelationIdWebFilter.CORRELATION_ID_CONTEXT_KEY))
                .isEqualTo("helper-cid");
        assertThat(helperContext.<Map<String, List<String>>>get(InboundHeadersWebFilter.INBOUND_HEADERS_CONTEXT_KEY))
                .containsEntry("X-Request-Id", List.of("helper-req"));
        assertThat(RequestContext.correlationId(helperContext)).contains("helper-cid");
        assertThat(RequestContext.inboundHeaders(helperContext))
                .containsEntry("X-Request-Id", List.of("helper-req"));

        RequestContextSnapshot legacySnapshot = RequestContextSnapshot.capture(Context.of(
                CorrelationIdWebFilter.CORRELATION_ID_CONTEXT_KEY, "legacy-cid",
                InboundHeadersWebFilter.INBOUND_HEADERS_CONTEXT_KEY, Map.of("X-Legacy", List.of("legacy-value"))));

        assertThat(legacySnapshot.correlationId()).isEqualTo("legacy-cid");
        assertThat(legacySnapshot.inboundHeaders()).containsEntry("X-Legacy", List.of("legacy-value"));
    }

    @Test
    void sinkSubscriberDoesNotSeeEmitterContextByDefault() {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        StepVerifier.create(sink.asFlux()
                        .take(1)
                        .flatMap(value -> Mono.deferContextual(ctx -> Mono.just(
                                ctx.getOrDefault(CorrelationIdWebFilter.CORRELATION_ID_CONTEXT_KEY, "missing")))))
                .then(() -> Mono.deferContextual(ctx -> {
                            sink.tryEmitNext("event").orThrow();
                            return Mono.empty();
                        })
                        .contextWrite(Context.of(CorrelationIdWebFilter.CORRELATION_ID_CONTEXT_KEY, "emitter-cid"))
                        .block())
                .expectNext("missing")
                .verifyComplete();
    }

    @Test
    void explicitSnapshotHandoffRestoresContextForSinkSubscriber() {
        Sinks.Many<EventEnvelope> sink = Sinks.many().unicast().onBackpressureBuffer();
        Map<String, List<String>> inboundHeaders = Map.of("X-Request-Id", List.of("req-7"));

        StepVerifier.create(sink.asFlux()
                        .take(1)
                        .flatMap(envelope -> restoredContextValues().contextWrite(envelope.snapshot()::writeTo)))
                .then(() -> Mono.deferContextual(ctx -> {
                            sink.tryEmitNext(new EventEnvelope(RequestContextSnapshot.capture(ctx))).orThrow();
                            return Mono.empty();
                        })
                        .contextWrite(Context.of(
                                CorrelationIdWebFilter.CORRELATION_ID_CONTEXT_KEY, "emitter-cid",
                                InboundHeadersWebFilter.INBOUND_HEADERS_CONTEXT_KEY, inboundHeaders))
                        .block())
                .expectNext("emitter-cid|req-7|null")
                .verifyComplete();
    }

    @Test
    void restoredSnapshotSurvivesPublishOn() {
        RequestContextSnapshot snapshot = new RequestContextSnapshot("cid-publish",
                Map.of("X-Request-Id", List.of("req-publish")));

        StepVerifier.create(restoredContextValues()
                        .publishOn(Schedulers.boundedElastic())
                        .contextWrite(snapshot::writeTo))
                .expectNext("cid-publish|req-publish|null")
                .verifyComplete();
    }

    @Test
    void restoredSnapshotSurvivesSubscribeOn() {
        RequestContextSnapshot snapshot = new RequestContextSnapshot("cid-subscribe",
                Map.of("X-Request-Id", List.of("req-subscribe")));

        StepVerifier.create(restoredContextValues()
                        .subscribeOn(Schedulers.boundedElastic())
                        .contextWrite(snapshot::writeTo))
                .expectNext("cid-subscribe|req-subscribe|null")
                .verifyComplete();
    }

    private static Mono<String> restoredContextValues() {
        return Mono.deferContextual(ctx -> {
            String correlationId = ctx.getOrDefault(CorrelationIdWebFilter.CORRELATION_ID_CONTEXT_KEY, null);
            @SuppressWarnings("unchecked")
            Map<String, List<String>> headers = ctx.getOrDefault(
                    InboundHeadersWebFilter.INBOUND_HEADERS_CONTEXT_KEY, Map.of());
            String requestId = firstHeader(headers, "X-Request-Id");
            String authorization = firstHeader(headers, "Authorization");
            return Mono.just(correlationId + "|" + requestId + "|" + authorization);
        });
    }

    private static String firstHeader(Map<String, List<String>> headers, String name) {
        List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private record EventEnvelope(RequestContextSnapshot snapshot) {
    }
}
