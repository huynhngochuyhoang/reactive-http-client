package io.github.huynhngochuyhoang.httpstarter.benchmarks;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

final class LoopbackBenchmarkServer implements AutoCloseable {

    private static final String USER_JSON = "{\"id\":\"42\",\"name\":\"benchmark-user\"}";

    private final DisposableServer server;

    private LoopbackBenchmarkServer(DisposableServer server) {
        this.server = server;
    }

    static LoopbackBenchmarkServer start() {
        DisposableServer server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes
                        .get("/users/{id}", (request, response) -> response
                                .header(HttpHeaderNames.CONTENT_TYPE, "application/json")
                                .sendString(Mono.just(USER_JSON))
                                .then())
                        .post("/users", (request, response) -> request.receive()
                                .aggregate()
                                .asString()
                                .defaultIfEmpty("")
                                .then(response.status(HttpResponseStatus.CREATED)
                                        .header(HttpHeaderNames.CONTENT_TYPE, "application/json")
                                        .sendString(Mono.just(USER_JSON))
                                        .then())))
                .bindNow();
        return new LoopbackBenchmarkServer(server);
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.port();
    }

    @Override
    public void close() {
        server.disposeNow();
    }
}
