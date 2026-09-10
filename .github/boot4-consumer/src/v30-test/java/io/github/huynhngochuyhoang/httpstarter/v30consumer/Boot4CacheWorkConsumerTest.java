package io.github.huynhngochuyhoang.httpstarter.v30consumer;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.enable.EnableReactiveHttpClients;
import io.github.huynhngochuyhoang.httpstarter.exception.CacheWorkRejectedException;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.*;

@org.junit.jupiter.api.Timeout(30)
class Boot4CacheWorkConsumerTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void countAndWeightedPoliciesPreserveWorkOwnership(boolean post) throws Exception {
        var calls = new ConcurrentHashMap<String, AtomicInteger>();
        var gate = Sinks.<String>one();
        var refreshGate = Sinks.<String>one();
        var total = new AtomicInteger();
        var server = HttpServer.create().host("127.0.0.1").port(0).handle((request, response) -> {
            total.incrementAndGet();
            return request.receive().aggregate().asString().defaultIfEmpty("").flatMap(body -> {
                String key = request.uri() + body;
                int count = calls.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
                Mono<String> result = key.contains("busy") ? gate.asMono()
                        : key.contains("refresh") && count > 1 ? refreshGate.asMono() : Mono.just("value");
                return response.header("Content-Type", "text/plain").sendString(result).then();
            });
        }).bindNow();
        MeterRegistry meters = null;
        try (var context = new SpringApplicationBuilder(Application.class).web(WebApplicationType.NONE)
                .properties(properties(server.port())).run()) {
            var client = context.getBean(Client.class);
            meters = context.getBean(MeterRegistry.class);
            final MeterRegistry registry = meters;
            String policy = post ? "weighted" : "count";
            var leader = call(client, post, "busy").toFuture();
            await(() -> total.get() == 1);
            reject(call(client, post, "other"), CacheWorkRejectedException.Reason.LOAD_CAPACITY);
            var waiter = call(client, post, "busy").toFuture();
            await(() -> registry.find("reactive.http.client.cache.coalesced").counters().stream()
                    .mapToDouble(c -> c.count()).sum() == 1);
            reject(call(client, post, "third"), CacheWorkRejectedException.Reason.CALLER_CAPACITY);
            Thread.sleep(100);
            assertThat(total).hasValue(1);
            assertThat(gauge(registry, policy, "callers")).isEqualTo(2);
            assertThat(gauge(registry, policy, "loads")).isEqualTo(1);
            gate.tryEmitValue("loaded").orThrow();
            assertThat(leader.get(5, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("loaded");
            assertThat(waiter.get(5, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("loaded");
            await(() -> gauge(registry, policy, "callers") == 0 && gauge(registry, policy, "loads") == 0);
            assertThat(call(client, post, "busy").block()).isEqualTo("loaded");
            assertThat(call(client, post, "next").block()).isEqualTo("value");
            assertThat(total).hasValue(2);

            assertThat(client.refresh("a").block()).isEqualTo("value");
            assertThat(client.refresh("b").block()).isEqualTo("value");
            Thread.sleep(120);
            assertThat(client.refresh("a").block()).isEqualTo("value");
            await(() -> gauge(registry, "refresh", "refreshes") == 1);
            assertThat(client.refresh("b").block()).isEqualTo("value");
            assertThat(registry.get("reactive.http.client.cache.refresh.skips")
                    .tags("client.name", "work-consumer", "cache.policy", "refresh", "reason", "capacity")
                    .counter().count()).isEqualTo(1);
            refreshGate.tryEmitValue("updated").orThrow();
            await(() -> gauge(registry, "refresh", "refreshes") == 0);
        } finally { server.disposeNow(); }
        assertThat(meters.getMeters()).noneMatch(m -> m.getId().getName().startsWith("reactive.http.client.cache."));
    }

    private static Mono<String> call(Client client, boolean post, String id) {
        return post ? client.search(id) : client.get(id);
    }
    private static void reject(Mono<?> call, CacheWorkRejectedException.Reason reason) {
        assertThatThrownBy(() -> call.block(Duration.ofSeconds(5))).isInstanceOfSatisfying(
                CacheWorkRejectedException.class, error -> assertThat(error.getReason()).isEqualTo(reason));
    }
    private static double gauge(MeterRegistry meters, String policy, String dimension) {
        return meters.get("reactive.http.client.cache.work.active." + dimension)
                .tags("client.name", "work-consumer", "cache.policy", policy).gauge().value();
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(1); }
        assertThat(condition.getAsBoolean()).isTrue();
    }
    private static String[] properties(int port) {
        var values = new ArrayList<String>();
        values.add("reactive.http.clients.work-consumer.base-url=http://127.0.0.1:" + port);
        values.add("reactive.http.observability.cache.enabled=true");
        for (String policy : new String[]{"count", "weighted", "refresh"}) {
            String prefix = "reactive.http.clients.work-consumer.cache.policies." + policy + ".";
            values.add(prefix + "ttl-ms=60000");
            values.add(prefix + "maximum-size=10");
            values.add(prefix + "shared-response=true");
            values.add(prefix + "single-flight=true");
            values.add(prefix + "work.maximum-concurrent-callers=2");
            values.add(prefix + "work.maximum-concurrent-loads=1");
            if (policy.equals("weighted")) {
                values.add(prefix + "maximum-total-decoded-response-bytes=1024");
                values.add(prefix + "vary-by-parameters[0]=body");
            }
            if (policy.equals("refresh")) {
                values.add(prefix + "refresh-after-ms=100");
                values.add(prefix + "refresh-timeout-ms=30000");
                values.add(prefix + "work.maximum-concurrent-refreshes=1");
            }
        }
        for (String name : new String[]{"exchangeStrategiesCustomizer", "webClientHttpConnectorCustomizer",
                "observationWebClientCustomizer", "webClientBuilder"}) {
            values.add("reactive.http.clients.work-consumer.cache.customizations." + name + "=SAFE");
        }
        return values.toArray(String[]::new);
    }
    @ReactiveHttpClient(name = "work-consumer")
    interface Client {
        @GET("/get/{id}") @CacheResponse("count") Mono<String> get(@PathVar("id") String id);
        @POST("/search") @CacheResponse(value = "weighted", semanticRead = true)
        Mono<String> search(@Body @CacheKey("body") String body);
        @GET("/refresh/{id}") @CacheResponse("refresh") Mono<String> refresh(@PathVar("id") String id);
    }
    @SpringBootConfiguration @EnableAutoConfiguration
    @EnableReactiveHttpClients(basePackageClasses = Client.class)
    static class Application { }
}
