package io.github.huynhngochuyhoang.httpstarter.benchmarks;

import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

final class BenchmarkHttpConnector {

    private BenchmarkHttpConnector() {
    }

    static ReactorClientHttpConnector create(ConnectionProvider provider) {
        return new ReactorClientHttpConnector(HttpClient.create(provider).compress(false));
    }
}
