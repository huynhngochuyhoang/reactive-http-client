package io.github.huynhngochuyhoang.httpstarter.benchmarks;

import io.github.huynhngochuyhoang.httpstarter.benchmarks.client.BenchmarkUser;
import io.github.huynhngochuyhoang.httpstarter.benchmarks.client.CreateUserRequest;
import io.github.huynhngochuyhoang.httpstarter.exception.HttpClientException;
import io.github.huynhngochuyhoang.httpstarter.exception.ProblemDetailHttpClientException;
import io.github.huynhngochuyhoang.httpstarter.exception.RemoteServiceException;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class LoopbackClientComparisonBenchmark {

    private static final LoopbackBenchmarkServer.Scenario GET_USER = LoopbackBenchmarkServer.GET_USER_SCENARIO;
    private static final CreateUserRequest CREATE_USER = new CreateUserRequest("benchmark-user");

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
    public BenchmarkUser clientSideOverheadRawWebClientGetNoBody() {
        return validateUser("raw WebClient get-no-body", rawWebClientCurrentUser(),
                LoopbackBenchmarkServer.CURRENT_USER);
    }

    @Benchmark
    public BenchmarkUser clientSideOverheadSpringHttpExchangeGetNoBody() {
        return validateUser("Spring HTTP Interface get-no-body", clients.httpExchangeClient.currentUser().block(),
                LoopbackBenchmarkServer.CURRENT_USER);
    }

    @Benchmark
    public BenchmarkUser clientSideOverheadStarterGetNoBody() {
        return validateUser("starter get-no-body", clients.starterClient.currentUser().block(),
                LoopbackBenchmarkServer.CURRENT_USER);
    }

    @Benchmark
    public BenchmarkUser clientSideOverheadRawWebClientGetPathQueryHeader() {
        return validateUser("raw WebClient get-path-query-header", rawWebClientFindUser());
    }

    @Benchmark
    public BenchmarkUser clientSideOverheadSpringHttpExchangeGetPathQueryHeader() {
        return validateUser("Spring HTTP Interface get-path-query-header", springHttpExchangeFindUser());
    }

    @Benchmark
    public BenchmarkUser clientSideOverheadStarterGetPathQueryHeader() {
        return validateUser("starter get-path-query-header", starterFindUser());
    }

    @Benchmark
    public BenchmarkUser clientSideOverheadRawWebClientPostJson() {
        return validateUser("raw WebClient post-json", rawWebClientCreateUser());
    }

    @Benchmark
    public BenchmarkUser clientSideOverheadSpringHttpExchangePostJson() {
        return validateUser("Spring HTTP Interface post-json", clients.httpExchangeClient.createUser(CREATE_USER).block(),
                LoopbackBenchmarkServer.BENCHMARK_USER);
    }

    @Benchmark
    public BenchmarkUser clientSideOverheadStarterPostJson() {
        return validateUser("starter post-json", clients.starterClient.createUser(CREATE_USER).block(),
                LoopbackBenchmarkServer.BENCHMARK_USER);
    }

    @Benchmark
    public BenchmarkUser clientSideOverheadRawWebClientResponseEntity() {
        return validateSuccessResponse("raw WebClient response-entity", rawWebClientFindUserEntity(),
                GET_USER.name(), HttpStatus.OK, GET_USER.expectedUser());
    }

    @Benchmark
    public BenchmarkUser clientSideOverheadSpringHttpExchangeResponseEntity() {
        return validateSuccessResponse("Spring HTTP Interface response-entity", springHttpExchangeFindUserEntity(),
                GET_USER.name(), HttpStatus.OK, GET_USER.expectedUser());
    }

    @Benchmark
    public BenchmarkUser clientSideOverheadStarterResponseEntity() {
        return validateSuccessResponse("starter response-entity", starterFindUserEntity(),
                GET_USER.name(), HttpStatus.OK, GET_USER.expectedUser());
    }

    @Benchmark
    public ErrorResult clientSideOverheadRawWebClientClientErrorSmallBody() {
        return validateWebClientError("raw WebClient client-error-small-body", () -> clients.rawWebClient.get()
                .uri("/errors/client")
                .retrieve()
                .bodyToMono(BenchmarkUser.class)
                .block(), HttpStatus.NOT_FOUND, LoopbackBenchmarkServer.CLIENT_ERROR_BODY);
    }

    @Benchmark
    public ErrorResult clientSideOverheadSpringHttpExchangeClientErrorSmallBody() {
        return validateWebClientError("Spring HTTP Interface client-error-small-body",
                () -> clients.httpExchangeClient.clientError().block(),
                HttpStatus.NOT_FOUND,
                LoopbackBenchmarkServer.CLIENT_ERROR_BODY);
    }

    @Benchmark
    public ErrorResult clientSideOverheadStarterClientErrorSmallBody() {
        return validateStarterClientError("starter client-error-small-body",
                () -> clients.starterClient.clientError().block(),
                HttpStatus.NOT_FOUND,
                LoopbackBenchmarkServer.CLIENT_ERROR_BODY);
    }

    @Benchmark
    public ErrorResult clientSideOverheadRawWebClientServerErrorSmallBody() {
        return validateWebClientError("raw WebClient server-error-small-body", () -> clients.rawWebClient.get()
                .uri("/errors/server")
                .retrieve()
                .bodyToMono(BenchmarkUser.class)
                .block(), HttpStatus.INTERNAL_SERVER_ERROR, LoopbackBenchmarkServer.SERVER_ERROR_BODY);
    }

    @Benchmark
    public ErrorResult clientSideOverheadSpringHttpExchangeServerErrorSmallBody() {
        return validateWebClientError("Spring HTTP Interface server-error-small-body",
                () -> clients.httpExchangeClient.serverError().block(),
                HttpStatus.INTERNAL_SERVER_ERROR,
                LoopbackBenchmarkServer.SERVER_ERROR_BODY);
    }

    @Benchmark
    public ErrorResult clientSideOverheadStarterServerErrorSmallBody() {
        return validateStarterServerError("starter server-error-small-body",
                () -> clients.starterClient.serverError().block(),
                HttpStatus.INTERNAL_SERVER_ERROR,
                LoopbackBenchmarkServer.SERVER_ERROR_BODY);
    }

    @Benchmark
    public ErrorResult starterErrorMappingProblemDetailSmallBody() {
        try {
            clients.starterProblemDetailClient.problemDetailError().block();
        } catch (ProblemDetailHttpClientException ex) {
            if (ex.getStatusCode() != HttpStatus.BAD_REQUEST.value()) {
                throw new IllegalStateException("starter problem-detail returned status " + ex.getStatusCode());
            }
            if (!LoopbackBenchmarkServer.PROBLEM_DETAIL_BODY.equals(ex.getResponseBody())) {
                throw new IllegalStateException("starter problem-detail returned unexpected body " + ex.getResponseBody());
            }
            if (ex.getProblemDetail() == null || !"Benchmark problem".equals(ex.getProblemDetail().getTitle())) {
                throw new IllegalStateException("starter problem-detail mapper did not expose the ProblemDetail title");
            }
            return new ErrorResult("starter-problem-detail", ex.getStatusCode(), ex.getResponseBody(),
                    ex.getClass().getSimpleName());
        }
        throw new IllegalStateException("starter problem-detail call completed successfully");
    }

    @Benchmark
    public BenchmarkUser starterFeatureExchangeLoggingMetadataOnlyGetNoBody() {
        return validateUser("starter exchange-logging metadata-only", clients.starterExchangeLoggingClient.currentUser().block(),
                LoopbackBenchmarkServer.CURRENT_USER);
    }

    @Benchmark
    public BenchmarkUser starterFeatureMicrometerObserverGetNoBody() {
        return validateUser("starter Micrometer observer", clients.starterMicrometerClient.currentUser().block(),
                LoopbackBenchmarkServer.CURRENT_USER);
    }

    @Benchmark
    public BenchmarkUser starterFeatureResilienceEnabledOnlyGetNoBody() {
        return validateUser("starter resilience enabled-only",
                clients.starterResilienceEnabledOnlyClient.currentUser().block(),
                LoopbackBenchmarkServer.CURRENT_USER);
    }

    @Benchmark
    public BenchmarkUser starterFeatureRetryWrapperGetNoBody() {
        return validateUser("starter retry wrapper", clients.starterRetryClient.currentUser().block(),
                LoopbackBenchmarkServer.CURRENT_USER);
    }

    @Benchmark
    public BenchmarkUser starterFeatureRateLimiterWrapperGetNoBody() {
        return validateUser("starter rate-limiter wrapper", clients.starterRateLimiterClient.currentUser().block(),
                LoopbackBenchmarkServer.CURRENT_USER);
    }

    @Benchmark
    public BenchmarkUser starterFeatureCircuitBreakerWrapperGetNoBody() {
        return validateUser("starter circuit breaker wrapper", clients.starterCircuitBreakerClient.currentUser().block(),
                LoopbackBenchmarkServer.CURRENT_USER);
    }

    private void assertScenarioParity() {
        validateSuccessResponse("raw WebClient setup get-no-body", rawWebClientCurrentUserEntity(),
                "get-no-body", HttpStatus.OK, LoopbackBenchmarkServer.CURRENT_USER);
        validateSuccessResponse("Spring HTTP Interface setup get-no-body", clients.httpExchangeClient.currentUserEntity().block(),
                "get-no-body", HttpStatus.OK, LoopbackBenchmarkServer.CURRENT_USER);
        validateSuccessResponse("starter setup get-no-body", clients.starterClient.currentUserEntity().block(),
                "get-no-body", HttpStatus.OK, LoopbackBenchmarkServer.CURRENT_USER);

        validateSuccessResponse("raw WebClient setup get-path-query-header", rawWebClientFindUserEntity(),
                GET_USER.name(), HttpStatus.OK, GET_USER.expectedUser());
        validateSuccessResponse("Spring HTTP Interface setup get-path-query-header", springHttpExchangeFindUserEntity(),
                GET_USER.name(), HttpStatus.OK, GET_USER.expectedUser());
        validateSuccessResponse("starter setup get-path-query-header", starterFindUserEntity(),
                GET_USER.name(), HttpStatus.OK, GET_USER.expectedUser());

        validateSuccessResponse("raw WebClient setup post-json", rawWebClientCreateUserEntity(),
                "post-json", HttpStatus.CREATED, LoopbackBenchmarkServer.BENCHMARK_USER);
        validateSuccessResponse("Spring HTTP Interface setup post-json", clients.httpExchangeClient.createUserEntity(CREATE_USER).block(),
                "post-json", HttpStatus.CREATED, LoopbackBenchmarkServer.BENCHMARK_USER);
        validateSuccessResponse("starter setup post-json", clients.starterClient.createUserEntity(CREATE_USER).block(),
                "post-json", HttpStatus.CREATED, LoopbackBenchmarkServer.BENCHMARK_USER);

        clientSideOverheadRawWebClientClientErrorSmallBody();
        clientSideOverheadSpringHttpExchangeClientErrorSmallBody();
        clientSideOverheadStarterClientErrorSmallBody();
        clientSideOverheadRawWebClientServerErrorSmallBody();
        clientSideOverheadSpringHttpExchangeServerErrorSmallBody();
        clientSideOverheadStarterServerErrorSmallBody();
        starterErrorMappingProblemDetailSmallBody();
        starterFeatureExchangeLoggingMetadataOnlyGetNoBody();
        starterFeatureMicrometerObserverGetNoBody();
        starterFeatureResilienceEnabledOnlyGetNoBody();
        starterFeatureRetryWrapperGetNoBody();
        starterFeatureRateLimiterWrapperGetNoBody();
        starterFeatureCircuitBreakerWrapperGetNoBody();
    }

    private BenchmarkUser rawWebClientCurrentUser() {
        return clients.rawWebClient.get()
                .uri("/users/current")
                .retrieve()
                .bodyToMono(BenchmarkUser.class)
                .block();
    }

    private ResponseEntity<BenchmarkUser> rawWebClientCurrentUserEntity() {
        return clients.rawWebClient.get()
                .uri("/users/current")
                .retrieve()
                .toEntity(BenchmarkUser.class)
                .block();
    }

    private BenchmarkUser rawWebClientFindUser() {
        return clients.rawWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/users/{id}")
                        .queryParam("expand", GET_USER.expand())
                        .build(GET_USER.userId()))
                .header("X-Tenant", GET_USER.tenant())
                .retrieve()
                .bodyToMono(BenchmarkUser.class)
                .block();
    }

    private BenchmarkUser springHttpExchangeFindUser() {
        return clients.httpExchangeClient
                .findUser(GET_USER.userId(), GET_USER.expand(), GET_USER.tenant())
                .block();
    }

    private BenchmarkUser starterFindUser() {
        return clients.starterClient
                .findUser(GET_USER.userId(), GET_USER.expand(), GET_USER.tenant())
                .block();
    }

    private ResponseEntity<BenchmarkUser> rawWebClientFindUserEntity() {
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

    private ResponseEntity<BenchmarkUser> springHttpExchangeFindUserEntity() {
        return clients.httpExchangeClient
                .findUserEntity(GET_USER.userId(), GET_USER.expand(), GET_USER.tenant())
                .block();
    }

    private ResponseEntity<BenchmarkUser> starterFindUserEntity() {
        return clients.starterClient
                .findUserEntity(GET_USER.userId(), GET_USER.expand(), GET_USER.tenant())
                .block();
    }

    private BenchmarkUser rawWebClientCreateUser() {
        return clients.rawWebClient.post()
                .uri("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(CREATE_USER)
                .retrieve()
                .bodyToMono(BenchmarkUser.class)
                .block();
    }

    private ResponseEntity<BenchmarkUser> rawWebClientCreateUserEntity() {
        return clients.rawWebClient.post()
                .uri("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(CREATE_USER)
                .retrieve()
                .toEntity(BenchmarkUser.class)
                .block();
    }

    private static BenchmarkUser validateUser(String clientName, BenchmarkUser body) {
        return validateUser(clientName, body, GET_USER.expectedUser());
    }

    private static BenchmarkUser validateUser(String clientName, BenchmarkUser body, BenchmarkUser expected) {
        if (!expected.equals(body)) {
            throw new IllegalStateException(clientName + " returned body " + body + " instead of " + expected);
        }
        return body;
    }

    private static BenchmarkUser validateSuccessResponse(
            String clientName,
            ResponseEntity<BenchmarkUser> response,
            String expectedScenario,
            HttpStatus expectedStatus,
            BenchmarkUser expectedBody) {
        if (response == null) {
            throw new IllegalStateException(clientName + " did not produce a response");
        }
        if (response.getStatusCode().value() != expectedStatus.value()) {
            throw new IllegalStateException(clientName + " returned status " + response.getStatusCode()
                    + " instead of " + expectedStatus);
        }
        MediaType contentType = response.getHeaders().getContentType();
        if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            throw new IllegalStateException(clientName + " returned Content-Type " + contentType
                    + " instead of application/json");
        }
        String scenario = response.getHeaders().getFirst(LoopbackBenchmarkServer.SCENARIO_HEADER);
        if (!expectedScenario.equals(scenario)) {
            throw new IllegalStateException(clientName + " returned " + LoopbackBenchmarkServer.SCENARIO_HEADER
                    + " [" + scenario + "] instead of [" + expectedScenario + "]");
        }
        String bodyShape = response.getHeaders().getFirst(LoopbackBenchmarkServer.BODY_SHAPE_HEADER);
        if (!LoopbackBenchmarkServer.BODY_SHAPE.equals(bodyShape)) {
            throw new IllegalStateException(clientName + " returned " + LoopbackBenchmarkServer.BODY_SHAPE_HEADER
                    + " [" + bodyShape + "] instead of [" + LoopbackBenchmarkServer.BODY_SHAPE + "]");
        }
        BenchmarkUser body = response.getBody();
        if (!expectedBody.equals(body)) {
            throw new IllegalStateException(clientName + " returned body " + body + " instead of " + expectedBody);
        }
        return body;
    }

    private static ErrorResult validateWebClientError(
            String clientName,
            ErrorCall call,
            HttpStatus expectedStatus,
            String expectedBody) {
        try {
            call.run();
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode().value() != expectedStatus.value()) {
                throw new IllegalStateException(clientName + " returned status " + ex.getStatusCode()
                        + " instead of " + expectedStatus);
            }
            String body = ex.getResponseBodyAsString();
            if (!expectedBody.equals(body)) {
                throw new IllegalStateException(clientName + " returned body " + body + " instead of " + expectedBody);
            }
            return new ErrorResult(clientName, ex.getStatusCode().value(), body, ex.getClass().getSimpleName());
        }
        throw new IllegalStateException(clientName + " completed successfully");
    }

    private static ErrorResult validateStarterClientError(
            String clientName,
            ErrorCall call,
            HttpStatus expectedStatus,
            String expectedBody) {
        try {
            call.run();
        } catch (HttpClientException ex) {
            if (ex.getStatusCode() != expectedStatus.value()) {
                throw new IllegalStateException(clientName + " returned status " + ex.getStatusCode()
                        + " instead of " + expectedStatus);
            }
            if (!expectedBody.equals(ex.getResponseBody())) {
                throw new IllegalStateException(clientName + " returned body " + ex.getResponseBody()
                        + " instead of " + expectedBody);
            }
            return new ErrorResult(clientName, ex.getStatusCode(), ex.getResponseBody(), ex.getClass().getSimpleName());
        }
        throw new IllegalStateException(clientName + " completed successfully");
    }

    private static ErrorResult validateStarterServerError(
            String clientName,
            ErrorCall call,
            HttpStatus expectedStatus,
            String expectedBody) {
        try {
            call.run();
        } catch (RemoteServiceException ex) {
            if (ex.getStatusCode() != expectedStatus.value()) {
                throw new IllegalStateException(clientName + " returned status " + ex.getStatusCode()
                        + " instead of " + expectedStatus);
            }
            if (!expectedBody.equals(ex.getResponseBody())) {
                throw new IllegalStateException(clientName + " returned body " + ex.getResponseBody()
                        + " instead of " + expectedBody);
            }
            return new ErrorResult(clientName, ex.getStatusCode(), ex.getResponseBody(), ex.getClass().getSimpleName());
        }
        throw new IllegalStateException(clientName + " completed successfully");
    }

    @FunctionalInterface
    private interface ErrorCall {
        void run();
    }

    public record ErrorResult(String clientName, int statusCode, String responseBody, String exceptionType) {
    }
}
