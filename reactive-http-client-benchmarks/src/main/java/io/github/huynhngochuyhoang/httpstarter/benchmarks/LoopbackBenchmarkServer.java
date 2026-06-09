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
    static final String BODY_SHAPE = "BenchmarkUser{id,name}";
    static final BenchmarkUser CURRENT_USER = new BenchmarkUser("current", "current-user");
    static final BenchmarkUser BENCHMARK_USER = new BenchmarkUser("42", "benchmark-user");
    static final Scenario GET_USER_SCENARIO = new Scenario(
            "get-user-path-query-header",
            "42",
            "summary",
            "benchmark",
            BENCHMARK_USER);
    static final String CLIENT_ERROR_BODY = "bounded client error body";
    static final String SERVER_ERROR_BODY = "bounded server error body";
    static final String PROBLEM_DETAIL_BODY = "{\"type\":\"https://example.com/problems/benchmark\","
            + "\"title\":\"Benchmark problem\","
            + "\"status\":400,"
            + "\"detail\":\"bounded problem detail body\"}";

    private static final String CURRENT_USER_JSON = "{\"id\":\"current\",\"name\":\"current-user\"}";
    private static final String USER_JSON = "{\"id\":\"42\",\"name\":\"benchmark-user\"}";
    private static final String CREATE_USER_REQUEST_JSON = "{\"name\":\"benchmark-user\"}";

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
                        .get("/users/current", (request, response) -> handleFixedSuccess(
                                stats, response, "get-no-body", CURRENT_USER_JSON, HttpResponseStatus.OK))
                        .get("/users/{id}", (request, response) -> handleGetUser(stats, request, response))
                        .post("/users", (request, response) -> handleCreateUser(stats, request, response))
                        .get("/errors/client", (request, response) -> handleError(
                                stats, response, "client-error-small-body", HttpResponseStatus.NOT_FOUND,
                                "text/plain", CLIENT_ERROR_BODY))
                        .get("/errors/server", (request, response) -> handleError(
                                stats, response, "server-error-small-body", HttpResponseStatus.INTERNAL_SERVER_ERROR,
                                "text/plain", SERVER_ERROR_BODY))
                        .get("/errors/problem", (request, response) -> handleError(
                                stats, response, "problem-detail-error-mapping", HttpResponseStatus.BAD_REQUEST,
                                "application/problem+json", PROBLEM_DETAIL_BODY)))
                .bindNow();
        return new LoopbackBenchmarkServer(server, stats);
    }

    private static Mono<Void> handleFixedSuccess(
            Stats stats,
            HttpServerResponse response,
            String scenario,
            String body,
            HttpResponseStatus status) {
        int activeRequests = stats.startRequest();
        return response.status(status)
                .header(HttpHeaderNames.CONTENT_TYPE, "application/json")
                .header(SCENARIO_HEADER, scenario)
                .header(BODY_SHAPE_HEADER, BODY_SHAPE)
                .header("X-Benchmark-Server-In-Flight", String.valueOf(activeRequests))
                .sendString(Mono.just(body))
                .then()
                .doFinally(signal -> stats.finishRequest());
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
            return response.status(HttpResponseStatus.OK)
                    .header(HttpHeaderNames.CONTENT_TYPE, "application/json")
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

    private static Mono<Void> handleCreateUser(Stats stats, HttpServerRequest request, HttpServerResponse response) {
        int activeRequests = stats.startRequest();
        return request.receive()
                .aggregate()
                .asString()
                .defaultIfEmpty("")
                .flatMap(body -> {
                    if (!CREATE_USER_REQUEST_JSON.equals(body)) {
                        String error = "expected create-user JSON body [" + CREATE_USER_REQUEST_JSON + "] but got [" + body + "]";
                        stats.recordInvalidRequest(error);
                        return response.status(HttpResponseStatus.BAD_REQUEST)
                                .header(HttpHeaderNames.CONTENT_TYPE, "text/plain")
                                .sendString(Mono.just(error))
                                .then();
                    }
                    return response.status(HttpResponseStatus.CREATED)
                            .header(HttpHeaderNames.CONTENT_TYPE, "application/json")
                            .header(SCENARIO_HEADER, "post-json")
                            .header(BODY_SHAPE_HEADER, BODY_SHAPE)
                            .header("X-Benchmark-Server-In-Flight", String.valueOf(activeRequests))
                            .sendString(Mono.just(USER_JSON))
                            .then();
                })
                .doFinally(signal -> stats.finishRequest());
    }

    private static Mono<Void> handleError(
            Stats stats,
            HttpServerResponse response,
            String scenario,
            HttpResponseStatus status,
            String contentType,
            String body) {
        stats.startRequest();
        stats.recordExpectedError();
        return response.status(status)
                .header(HttpHeaderNames.CONTENT_TYPE, contentType)
                .header(SCENARIO_HEADER, scenario)
                .sendString(Mono.just(body))
                .then()
                .doFinally(signal -> stats.finishRequest());
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
                    + "; expectedErrorResponses=" + stats.expectedErrorResponses.sum()
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
        private final LongAdder expectedErrorResponses = new LongAdder();
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

        void recordExpectedError() {
            expectedErrorResponses.increment();
        }

        void recordInvalidRequest(String message) {
            if ("none".equals(firstInvalidRequest)) {
                firstInvalidRequest = message;
            }
            invalidRequests.increment();
        }
    }
}
