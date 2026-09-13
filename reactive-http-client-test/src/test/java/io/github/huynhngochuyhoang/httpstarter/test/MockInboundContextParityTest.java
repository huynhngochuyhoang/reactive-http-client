package io.github.huynhngochuyhoang.httpstarter.test;

import io.github.huynhngochuyhoang.httpstarter.annotation.CacheResponse;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.core.RequestContext;
import io.github.huynhngochuyhoang.httpstarter.core.RequestContextSnapshot;
import io.github.huynhngochuyhoang.httpstarter.filter.InboundHeadersWebFilter;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.context.Context;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(20)
class MockInboundContextParityTest {
    @ParameterizedTest
    @ValueSource(strings = {"x-scope", "X-Scope", "x-ScOpE"})
    void recordingsKeepSpellingWhileNamedAccessIsCaseInsensitive(String spelling) {
        var snapshot = capture(spelling, "scope-one");
        try (var mock = MockReactiveHttpClient.forClient(PlainClient.class)
                .respondToPath("/value", ignored -> MockReactiveHttpClient.text(200, "ok")).build()) {
            assertThat(mock.proxy().get().contextWrite(snapshot::writeTo).block()).isEqualTo("ok");
            var recording = mock.lastExchange();
            RecordedExchangeAssertions.assertThat(recording)
                    .hasInboundHeader(spelling, "scope-one")
                    .hasInboundHeader("x-empty", "")
                    .hasInboundHeaderValues("x-many", "one", "one")
                    .hasRedactedInboundHeader("authorization")
                    .doesNotHaveInboundHeader("x-omitted");
            String otherSpelling = "X-SCOPE";
            assertThatThrownBy(() -> RecordedExchangeAssertions.assertThat(recording)
                    .hasInboundHeader(otherSpelling, "scope-one")).isInstanceOf(AssertionError.class);
            RecordedExchangeAssertions.assertThat(recording).doesNotHaveInboundHeader(otherSpelling);
            var restored = recording.requestContextSnapshot().writeTo(Context.empty());
            assertThat(RequestContext.inboundHeader(restored, "X-SCOPE")).contains("scope-one");
            assertThat(RequestContext.inboundHeader(restored, "X-EMPTY")).contains("");
            assertThat(RequestContext.inboundHeader(restored, "X-OMITTED")).isEmpty();
            assertThat(RequestContext.inboundHeaderValues(restored, "X-MANY")).containsExactly("one", "one");
            assertThatThrownBy(() -> RequestContext.inboundHeader(restored, "x-many"))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(RequestContext.inboundHeader(restored, "Authorization")).contains("[REDACTED]");
            assertThat(recording.headers().headerNames())
                    .doesNotContain(spelling, "x-empty", "x-many", "authorization");
            assertThatThrownBy(() -> recording.inboundHeaders().get(spelling).add("changed"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void independentHandoffRepeatSubscriptionsAndCloseUseEitherCacheClock(boolean deterministic) {
        var policy = new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(60000L);
        policy.setMaximumSize(4L);
        policy.setSingleFlight(true);
        policy.setVaryByContext(List.of(RequestContext.INBOUND_HEADERS_CONTEXT_KEY));
        policy.setVaryByHeaders(List.of("Idempotency-Key"));
        var config = new ReactiveHttpClientProperties.ClientConfig();
        config.getCache().getPolicies().put("context", policy);
        var body = Sinks.<String>one();
        var cancellations = new AtomicInteger();
        var builder = MockReactiveHttpClient.forClient(CachedClient.class).clientConfig(config)
                .respondToPath("/value", ignored -> ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "text/plain")
                        .body(body.asMono().doOnCancel(cancellations::incrementAndGet)
                                .<org.springframework.core.io.buffer.DataBuffer>map(value -> org.springframework.core.io.buffer.DefaultDataBufferFactory.sharedInstance
                                        .wrap(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))).flux()).build());
        if (deterministic) { builder.withDeterministicCacheTime(); }
        try (var mock = builder.build()) {
            var first = capture("x-scope", "first");
            var second = capture("x-scope", "second");
            Mono<String> cold = Mono.deferContextual(ctx -> {
                RequestContext.inboundHeader(ctx, "X-Scope")
                        .orElseThrow(() -> new IllegalStateException("required scope absent"));
                return mock.proxy().get();
            });
            assertThatThrownBy(() -> cold.block()).hasMessage("required scope absent");
            assertThat(mock.exchanges()).isEmpty();
            var one = cold.contextWrite(first::writeTo).toFuture();
            var two = cold.contextWrite(second::writeTo).toFuture();
            assertThat(mock.exchanges()).hasSize(2);
            assertThat(mock.cacheSnapshot().inFlightLoadCount()).isEqualTo(2);
            assertThat(mock.exchanges().get(0).inboundHeaders()).isEqualTo(first.inboundHeaders());
            assertThat(mock.exchanges().get(1).inboundHeaders()).isEqualTo(second.inboundHeaders());
            mock.close();
            assertThat(one).isDone();
            assertThat(two).isDone();
            assertThat(cancellations).hasValue(2);
            assertThat(mock.cacheSnapshot().closed()).isTrue();
            assertThat(mock.cacheSnapshot().inFlightLoadCount()).isZero();
            assertThat(mock.cacheEntryCount()).isZero();
            // Recordings intentionally survive mock close for assertions, without automatic restoration.
            assertThat(RequestContext.inboundHeader(Context.empty(), "x-scope")).isEmpty();
            assertThat(RequestContext.inboundHeader(mock.exchanges().get(1).requestContextSnapshot()
                    .writeTo(Context.empty()), "X-SCOPE")).contains("second");
        }
    }

    private static RequestContextSnapshot capture(String name, String value) {
        var config = new ReactiveHttpClientProperties.InboundHeadersConfig();
        config.setAllowList(java.util.Set.of("x-scope", "x-empty", "x-many", "authorization"));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/capture")
                .header(name, value).header("x-empty", "").header("x-many", "one", "one")
                .header("authorization", "not-retained").header("x-omitted", "not-retained"));
        var captured = new AtomicReference<RequestContextSnapshot>();
        new InboundHeadersWebFilter(config).filter(exchange, ignored -> Mono.deferContextual(ctx -> {
            captured.set(RequestContextSnapshot.capture(ctx));
            return Mono.empty();
        })).block(Duration.ofSeconds(5));
        return captured.get();
    }

    @ReactiveHttpClient(name = "plain-context")
    interface PlainClient { @GET("/value") Mono<String> get(); }

    @ReactiveHttpClient(name = "cached-context")
    interface CachedClient { @GET("/value") @CacheResponse("context") Mono<String> get(); }
}
