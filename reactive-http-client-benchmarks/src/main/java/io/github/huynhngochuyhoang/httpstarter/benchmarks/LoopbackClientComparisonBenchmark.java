package io.github.huynhngochuyhoang.httpstarter.benchmarks;

import io.github.huynhngochuyhoang.httpstarter.benchmarks.client.BenchmarkUser;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class LoopbackClientComparisonBenchmark {

    private static final LoopbackBenchmarkServer.Scenario GET_USER = LoopbackBenchmarkServer.GET_USER_SCENARIO;

    private LoopbackBenchmarkServer server;
    private BenchmarkClients clients;

    @Setup
    public void setup() {
        server = LoopbackBenchmarkServer.start();
        clients = BenchmarkClients.create(server.baseUrl());
        assertScenarioParity();
    }

    @TearDown
    public void tearDown() {
        try {
            if (server != null) {
                server.assertNoFairnessViolations();
            }
        } finally {
            if (clients != null) {
                clients.close();
            }
            if (server != null) {
                server.close();
            }
        }
    }

    @Benchmark
    public BenchmarkUser rawWebClientGet() {
        return validateGetResponse("raw WebClient", rawWebClientGetEntity());
    }

    @Benchmark
    public BenchmarkUser springHttpExchangeGet() {
        return validateGetResponse("Spring HTTP Interface", springHttpExchangeGetEntity());
    }

    @Benchmark
    public BenchmarkUser starterGet() {
        return validateGetResponse("starter", starterGetEntity());
    }

    private void assertScenarioParity() {
        validateGetResponse("raw WebClient setup", rawWebClientGetEntity());
        validateGetResponse("Spring HTTP Interface setup", springHttpExchangeGetEntity());
        validateGetResponse("starter setup", starterGetEntity());
    }

    private ResponseEntity<BenchmarkUser> rawWebClientGetEntity() {
        return clients.rawWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/users/{id}")
                        .queryParam("expand", GET_USER.expand())
                        .build(GET_USER.userId()))
                .header("X-Tenant", GET_USER.tenant())
                .retrieve()
                .toEntity(BenchmarkUser.class)
                .block();
    }

    private ResponseEntity<BenchmarkUser> springHttpExchangeGetEntity() {
        return clients.httpExchangeClient
                .findUserEntity(GET_USER.userId(), GET_USER.expand(), GET_USER.tenant())
                .block();
    }

    private ResponseEntity<BenchmarkUser> starterGetEntity() {
        return clients.starterClient
                .findUserEntity(GET_USER.userId(), GET_USER.expand(), GET_USER.tenant())
                .block();
    }

    private static BenchmarkUser validateGetResponse(String clientName, ResponseEntity<BenchmarkUser> response) {
        if (response == null) {
            throw new IllegalStateException(clientName + " did not produce a response");
        }
        if (response.getStatusCode() != HttpStatus.OK) {
            throw new IllegalStateException(clientName + " returned status " + response.getStatusCode()
                    + " instead of " + HttpStatus.OK);
        }
        MediaType contentType = response.getHeaders().getContentType();
        if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            throw new IllegalStateException(clientName + " returned Content-Type " + contentType
                    + " instead of application/json");
        }
        String scenario = response.getHeaders().getFirst(LoopbackBenchmarkServer.SCENARIO_HEADER);
        if (!GET_USER.name().equals(scenario)) {
            throw new IllegalStateException(clientName + " returned " + LoopbackBenchmarkServer.SCENARIO_HEADER
                    + " [" + scenario + "] instead of [" + GET_USER.name() + "]");
        }
        String bodyShape = response.getHeaders().getFirst(LoopbackBenchmarkServer.BODY_SHAPE_HEADER);
        if (!"BenchmarkUser{id,name}".equals(bodyShape)) {
            throw new IllegalStateException(clientName + " returned " + LoopbackBenchmarkServer.BODY_SHAPE_HEADER
                    + " [" + bodyShape + "] instead of [BenchmarkUser{id,name}]");
        }
        BenchmarkUser body = response.getBody();
        if (!GET_USER.expectedUser().equals(body)) {
            throw new IllegalStateException(clientName + " returned body " + body
                    + " instead of " + GET_USER.expectedUser());
        }
        return body;
    }
}
