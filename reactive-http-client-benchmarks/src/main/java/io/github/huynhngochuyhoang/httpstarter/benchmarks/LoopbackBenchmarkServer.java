package io.github.huynhngochuyhoang.httpstarter.benchmarks;

import io.github.huynhngochuyhoang.httpstarter.benchmarks.client.BenchmarkUser;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

final class LoopbackBenchmarkServer implements AutoCloseable {

    static final String SCENARIO_HEADER = "X-Benchmark-Scenario";
    static final String BODY_SHAPE_HEADER = "X-Benchmark-Body-Shape";
    static final Scenario GET_USER_SCENARIO = new Scenario(
            "get-user",
            "42",
            "summary",
            "benchmark",
            new BenchmarkUser("42", "benchmark-user"));

    private static final String USER_JSON = "{\"id\":\"42\",\"name\":\"benchmark-user\"}";
    private static final String BODY_SHAPE = "BenchmarkUser{id,name}";

    private final DisposableServer server;
    private final Stats stats;

    private LoopbackBenchmarkServer(DisposableServer server, Stats stats) {
        this.server = server;
        this.stats = stats;
    }

    static LoopbackBenchmarkServer start() {
        Stats stats = new Stats();
        DisposableServer server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes
                        .get("/users/{id}", (request, response) -> handleGetUser(stats, request, response))
                        .post("/users", (request, response) -> request.receive()
                                .aggregate()
                                .asString()
                                .defaultIfEmpty("")
                                .then(response.status(HttpResponseStatus.CREATED)
                                        .header(HttpHeaderNames.CONTENT_TYPE, "application/json")
                                        .header(SCENARIO_HEADER, "create-user")
                                        .header(BODY_SHAPE_HEADER, BODY_SHAPE)
                                        .sendString(Mono.just(USER_JSON))
                                        .then())))
                .bindNow();
        return new LoopbackBenchmarkServer(server, stats);
    }

    private static Mono<Void> handleGetUser(Stats stats, HttpServerRequest request, HttpServerResponse response) {
        int activeRequests = stats.startRequest();
        try {
            String error = GET_USER_SCENARIO.validate(request);
            if (error != null) {
                stats.recordInvalidRequest(error);
                return response.status(HttpResponseStatus.BAD_REQUEST)
                        .header(HttpHeaderNames.CONTENT_TYPE, "text/plain")
                        .sendString(Mono.just(error))
                        .then()
                        .doFinally(signal -> stats.finishRequest());
            }
            return response.header(HttpHeaderNames.CONTENT_TYPE, "application/json")
                    .header(SCENARIO_HEADER, GET_USER_SCENARIO.name())
                    .header(BODY_SHAPE_HEADER, BODY_SHAPE)
                    .header("X-Benchmark-Server-In-Flight", String.valueOf(activeRequests))
                    .sendString(Mono.just(USER_JSON))
                    .then()
                    .doFinally(signal -> stats.finishRequest());
        } catch (RuntimeException ex) {
            stats.finishRequest();
            throw ex;
        }
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.port();
    }

    void assertNoFairnessViolations() {
        long invalidRequests = stats.invalidRequests.sum();
        if (invalidRequests > 0) {
            throw new IllegalStateException("Loopback benchmark received " + invalidRequests
                    + " request(s) that did not match the shared scenario. First mismatch: "
                    + stats.firstInvalidRequest + "; totalRequests=" + stats.totalRequests.sum()
                    + "; maxConcurrentRequests=" + stats.maxConcurrentRequests.get());
        }
    }

    @Override
    public void close() {
        server.disposeNow();
    }

    record Scenario(String name,
                    String userId,
                    String expand,
                    String tenant,
                    BenchmarkUser expectedUser) {
        String validate(HttpServerRequest request) {
            String id = request.param("id");
            if (!userId.equals(id)) {
                return "expected path id [" + userId + "] but got [" + id + "]";
            }
            String actualExpand = queryParam(request.uri(), "expand");
            if (!expand.equals(actualExpand)) {
                return "expected query expand [" + expand + "] but got [" + actualExpand + "]";
            }
            String actualTenant = request.requestHeaders().get("X-Tenant");
            if (!tenant.equals(actualTenant)) {
                return "expected X-Tenant [" + tenant + "] but got [" + actualTenant + "]";
            }
            return null;
        }
    }

    private static String queryParam(String uri, String name) {
        String query = URI.create(uri).getRawQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int separator = pair.indexOf('=');
            String key = separator >= 0 ? pair.substring(0, separator) : pair;
            if (name.equals(decode(key))) {
                String value = separator >= 0 ? pair.substring(separator + 1) : "";
                return decode(value);
            }
        }
        return null;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static final class Stats {
        private final AtomicInteger inFlightRequests = new AtomicInteger();
        private final AtomicInteger maxConcurrentRequests = new AtomicInteger();
        private final LongAdder totalRequests = new LongAdder();
        private final LongAdder invalidRequests = new LongAdder();
        private volatile String firstInvalidRequest = "none";

        int startRequest() {
            totalRequests.increment();
            int active = inFlightRequests.incrementAndGet();
            maxConcurrentRequests.accumulateAndGet(active, Math::max);
            return active;
        }

        void finishRequest() {
            inFlightRequests.decrementAndGet();
        }

        void recordInvalidRequest(String message) {
            if ("none".equals(firstInvalidRequest)) {
                firstInvalidRequest = message;
            }
            invalidRequests.increment();
        }
    }
}
