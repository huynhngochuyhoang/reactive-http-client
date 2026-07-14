package io.github.huynhngochuyhoang.httpstarter.test;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.core.Jackson3ReactiveHttpClientJsonCodec;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientLifecycleContext;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientLifecycleHook;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class Boot4MockReactiveHttpClientTest {
    @Test
    void jackson3SigningRetryLifecycleAndFinalMetadataMatchProduction() {
        AtomicInteger served = new AtomicInteger();
        AtomicReference<Object> authBody = new AtomicReference<>();
        List<String> lifecycle = new CopyOnWriteArrayList<>();
        List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();

        MockReactiveHttpClient<SignedClient> mock = MockReactiveHttpClient
                .forClient(SignedClient.class)
                .baseUrl("http://boot4.mock.local:8084")
                .jsonCodec(new Jackson3ReactiveHttpClientJsonCodec(
                        JsonMapper.builder()
                                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                                .build()))
                .withAuthProvider(request -> {
                    authBody.set(request.requestBody());
                    return Mono.just(AuthContext.empty());
                })
                .retry(2, "POST")
                .withLifecycleHook(new RecordingHook("second", 20, lifecycle))
                .withLifecycleHook(new RecordingHook("first", 10, lifecycle))
                .withObserver(observed::add)
                .respondTo(HttpMethod.POST, "/orders", exchange ->
                        served.incrementAndGet() == 1
                                ? MockReactiveHttpClient.json(503, "{\"error\":\"retry\"}")
                                : MockReactiveHttpClient.text(201, "accepted"))
                .build();

        StepVerifier.create(mock.proxy().create(
                        new SignedOrder("order-1"), List.of("a", "b")))
                .expectNext("accepted")
                .verifyComplete();

        String signed = new String((byte[]) authBody.get(), StandardCharsets.UTF_8);
        assertThat(signed).isEqualTo("{\"order_id\":\"order-1\"}");
        assertThat(mock.exchanges()).hasSize(2).allSatisfy(exchange -> {
            assertThat(exchange.bodyAsString()).isEqualTo(signed);
            RecordedExchangeAssertions.assertThat(exchange)
                    .hasHeaderValues("X-Tag", "a", "b")
                    .hasIdempotencyKey();
        });
        assertThat(mock.exchanges().get(1).idempotencyKey())
                .isEqualTo(mock.exchanges().get(0).idempotencyKey());
        RecordedExchangeAssertions.assertThat(mock).hasAttemptCount(2);
        assertThat(lifecycle).containsExactly(
                "first:start:1", "second:start:1",
                "first:retry:2", "second:retry:2",
                "first:success:2", "second:success:2");
        assertThat(observed).singleElement().satisfies(event -> {
            assertThat(event.getAttemptCount()).isEqualTo(2);
            assertThat(event.getStatusCode()).isEqualTo(201);
            assertThat(event.getRequestUrl())
                    .isEqualTo("http://boot4.mock.local:8084/orders");
            assertThat(event.getRequestHeaders()).containsEntry("X-Tag", "a,b");
        });
    }

    @ReactiveHttpClient(name = "boot4-helper")
    interface SignedClient {
        @POST("/orders")
        @IdempotencyKey
        Mono<String> create(@Body SignedOrder order,
                            @HeaderParam("X-Tag") List<String> tags);
    }

    record SignedOrder(String orderId) {
    }

    private static final class RecordingHook
            implements ReactiveHttpClientLifecycleHook, Ordered {
        private final String name;
        private final int order;
        private final List<String> events;

        private RecordingHook(String name, int order, List<String> events) {
            this.name = name;
            this.order = order;
            this.events = events;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public void onStart(ReactiveHttpClientLifecycleContext context) {
            events.add(name + ":start:" + context.attemptNumber());
        }

        @Override
        public void onRetryAttempt(ReactiveHttpClientLifecycleContext context) {
            events.add(name + ":retry:" + context.attemptNumber());
        }

        @Override
        public void onSuccess(ReactiveHttpClientLifecycleContext context) {
            events.add(name + ":success:" + context.attemptNumber());
            assertThat(context.requestUrl()).hasToString(
                    "http://boot4.mock.local:8084/orders");
        }
    }
}
