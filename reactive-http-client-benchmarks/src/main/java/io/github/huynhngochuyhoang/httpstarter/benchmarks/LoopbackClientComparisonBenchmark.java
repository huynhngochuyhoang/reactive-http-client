package io.github.huynhngochuyhoang.httpstarter.benchmarks;

import io.github.huynhngochuyhoang.httpstarter.benchmarks.client.BenchmarkUser;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@State(Scope.Benchmark)
public class LoopbackClientComparisonBenchmark {

    private LoopbackBenchmarkServer server;
    private BenchmarkClients clients;

    @Setup
    public void setup() {
        server = LoopbackBenchmarkServer.start();
        clients = BenchmarkClients.create(server.baseUrl());
    }

    @TearDown
    public void tearDown() {
        clients.close();
        server.close();
    }

    @Benchmark
    public BenchmarkUser rawWebClientGet() {
        return clients.rawWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/users/{id}")
                        .queryParam("expand", "summary")
                        .build("42"))
                .header("X-Tenant", "benchmark")
                .retrieve()
                .bodyToMono(BenchmarkUser.class)
                .block();
    }

    @Benchmark
    public BenchmarkUser springHttpExchangeGet() {
        return clients.httpExchangeClient.findUser("42", "summary", "benchmark").block();
    }

    @Benchmark
    public BenchmarkUser starterGet() {
        return clients.starterClient.findUser("42", "summary", "benchmark").block();
    }
}
