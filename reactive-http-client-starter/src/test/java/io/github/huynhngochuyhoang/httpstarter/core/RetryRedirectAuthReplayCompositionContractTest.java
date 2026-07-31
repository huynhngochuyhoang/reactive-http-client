package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.auth.*;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class RetryRedirectAuthReplayCompositionContractTest {

    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(5);
    private static final DefaultDataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();

    @ParameterizedTest
    @ValueSource(ints = {307, 308})
    void retryAndBodyPreservingRedirectKeepReplayStateInsideEachOuterSubscription(int redirectStatus) {
        try (ReplayServer server = new ReplayServer();
             ClientFixture fixture = ClientFixture.create(server, true, true, null)) {
            AtomicInteger outerSubscriptions = new AtomicInteger();
            AtomicInteger bodySubscriptions = new AtomicInteger();
            Mono<String> call = fixture.client()
                    .retryRedirect(redirectStatus, body("redirect-retry", bodySubscriptions))
                    .doOnSubscribe(ignored -> outerSubscriptions.incrementAndGet());

            assertThat(call.block(BLOCK_TIMEOUT)).isEqualTo("ok");
            assertThat(call.block(BLOCK_TIMEOUT)).isEqualTo("ok");

            assertThat(outerSubscriptions).hasValue(2);
            assertThat(fixture.diagnostics().starts).hasValue(2);
            assertThat(fixture.diagnostics().retries).hasValue(2);
            assertThat(server.businessRequests()).hasSize(8);
            assertThat(bodySubscriptions).hasValue(8);
            assertThat(server.businessRequests()).extracting(RequestRecord::body)
                    .containsOnly("redirect-retry");

            Map<String, List<RequestRecord>> requestsByKey = server.businessRequests().stream()
                    .collect(Collectors.groupingBy(RequestRecord::idempotencyKey));
            assertThat(requestsByKey).hasSize(2);
            requestsByKey.values().forEach(requests -> {
                assertThat(requests).extracting(RequestRecord::path)
                        .containsExactly(
                                "/retry-redirect/" + redirectStatus,
                                "/retry-redirect/" + redirectStatus + "/final",
                                "/retry-redirect/" + redirectStatus,
                                "/retry-redirect/" + redirectStatus + "/final");
                assertThat(requests).extracting(RequestRecord::idempotencyKey).doesNotContainNull();
            });
            fixture.diagnostics().assertSuccessfulCalls(2, 2, server.baseUrl() + "/retry-redirect/" + redirectStatus);
        }
    }

    @Test
    void retryAndOAuthRefreshSeparateHiddenReplayFromResilienceAttempts() {
        try (ReplayServer server = new ReplayServer();
             ClientFixture fixture = ClientFixture.createWithOAuth(server, true, false)) {
            AtomicInteger bodySubscriptions = new AtomicInteger();

            assertThat(fixture.client().retryAuth(body("retry-auth", bodySubscriptions)).block(BLOCK_TIMEOUT))
                    .isEqualTo("ok");

            assertThat(server.tokenRequests()).hasValue(2);
            assertThat(fixture.diagnostics().starts).hasValue(1);
            assertThat(fixture.diagnostics().retries).hasValue(1);
            assertThat(bodySubscriptions).hasValue(3);
            assertThat(server.businessRequests()).extracting(RequestRecord::authorization)
                    .containsExactly("Bearer token-1", "Bearer token-2", "Bearer token-2");
            assertSingleIdempotencyKey(server.businessRequests());
            fixture.diagnostics().assertSuccessfulCalls(1, 2, server.baseUrl() + "/retry-auth");
        }
    }

    @Test
    void redirectAndOAuthRefreshReplayTheOriginalRequestWithOneVisibleAttempt() {
        try (ReplayServer server = new ReplayServer();
             ClientFixture fixture = ClientFixture.createWithOAuth(server, false, true)) {
            AtomicInteger bodySubscriptions = new AtomicInteger();

            assertThat(fixture.client().authRedirect(
                    "session=visible", "Basic proxy-visible", body("auth-redirect", bodySubscriptions))
                    .block(BLOCK_TIMEOUT)).isEqualTo("ok");

            assertThat(server.tokenRequests()).hasValue(2);
            assertThat(fixture.diagnostics().starts).hasValue(1);
            assertThat(fixture.diagnostics().retries).hasValue(0);
            assertThat(bodySubscriptions).hasValue(4);
            assertThat(server.businessRequests()).extracting(RequestRecord::path)
                    .containsExactly("/auth-redirect", "/auth-redirect/final", "/auth-redirect", "/auth-redirect/final");
            assertThat(server.businessRequests()).extracting(RequestRecord::authorization)
                    .containsExactly("Bearer token-1", "Bearer token-1", "Bearer token-2", "Bearer token-2");
            assertThat(server.businessRequests()).extracting(RequestRecord::cookie).containsOnly("session=visible");
            assertThat(server.businessRequests()).extracting(RequestRecord::proxyAuthorization)
                    .containsOnly("Basic proxy-visible");
            assertSingleIdempotencyKey(server.businessRequests());
            fixture.diagnostics().assertSuccessfulCalls(1, 1, server.baseUrl() + "/auth-redirect");
        }
    }

    @Test
    void crossAuthorityRedirectRemovesSensitiveHeadersButKeepsReplayIdentity() {
        try (CrossAuthorityTarget target = new CrossAuthorityTarget();
             ReplayServer server = new ReplayServer(target.url());
             ClientFixture fixture = ClientFixture.createWithOAuth(server, false, true)) {
            AtomicInteger bodySubscriptions = new AtomicInteger();

            assertThat(fixture.client().crossAuthorityRedirect(
                    "session=secret", "Basic proxy-secret", body("cross-authority", bodySubscriptions))
                    .block(BLOCK_TIMEOUT)).isEqualTo("ok");

            assertThat(bodySubscriptions).hasValue(2);
            assertThat(server.businessRequests()).singleElement().satisfies(request -> {
                assertThat(request.authorization()).isEqualTo("Bearer token-1");
                assertThat(request.cookie()).isEqualTo("session=secret");
                assertThat(request.proxyAuthorization()).isEqualTo("Basic proxy-secret");
            });
            assertThat(target.requests()).singleElement().satisfies(request -> {
                assertThat(request.authorization()).isNull();
                assertThat(request.cookie()).isNull();
                assertThat(request.proxyAuthorization()).isNull();
                assertThat(request.idempotencyKey()).isEqualTo(
                        server.businessRequests().getFirst().idempotencyKey());
                assertThat(request.body()).isEqualTo("cross-authority");
            });
            fixture.diagnostics().assertSuccessfulCalls(1, 1, server.baseUrl() + "/cross-redirect");
        }
    }

    @Test
    void repeatableBodyBytesRemainStableAcrossRetryAndRedirect() {
        try (ReplayServer server = new ReplayServer();
             ClientFixture fixture = ClientFixture.create(server, true, true, null)) {
            String payload = "repeatable-caf\u00e9";

            assertThat(fixture.client().repeatableRedirect("text/plain;charset=UTF-8", payload)
                    .block(BLOCK_TIMEOUT)).isEqualTo("ok");

            assertThat(server.businessRequests()).hasSize(4)
                    .allSatisfy(request -> {
                        assertThat(request.bodyBytes()).containsExactly(payload.getBytes(StandardCharsets.UTF_8));
                        assertThat(request.idempotencyKey()).isNotBlank();
                    });
            assertSingleIdempotencyKey(server.businessRequests());
        }
    }

    @Test
    void finalPreDispatchAuthFailureDoesNotReusePriorAttemptEvidence() {
        AtomicInteger tokenFetches = new AtomicInteger();
        AuthProvider authProvider = new RefreshingBearerAuthProvider(() -> {
            int call = tokenFetches.incrementAndGet();
            if (call == 3) {
                return Mono.error(new IllegalStateException("token service unavailable"));
            }
            Instant expiry = call == 2 ? Instant.now().plusSeconds(1) : Instant.now().plusSeconds(3_600);
            return Mono.just(new AccessToken("token-" + call, expiry));
        });
        try (ReplayServer server = new ReplayServer();
             ClientFixture fixture = ClientFixture.create(server, true, false, authProvider)) {
            AtomicInteger bodySubscriptions = new AtomicInteger();

            Throwable failure = catchThrowable(() -> fixture.client()
                    .terminalAuthFailure(body("terminal-auth", bodySubscriptions))
                    .block(BLOCK_TIMEOUT));

            assertThat(failure).isInstanceOf(AuthProviderException.class);
            assertThat(failure).hasRootCauseMessage("token service unavailable");
            assertThat(tokenFetches).hasValue(3);
            assertThat(bodySubscriptions).hasValue(2);
            assertThat(server.businessRequests()).hasSize(2);
            assertSingleIdempotencyKey(server.businessRequests());
            fixture.diagnostics().assertPreDispatchFailure(2);
        }
    }

    private static Flux<DataBuffer> body(String value, AtomicInteger subscriptions) {
        return Flux.defer(() -> {
            subscriptions.incrementAndGet();
            return Flux.just(BUFFER_FACTORY.wrap(value.getBytes(StandardCharsets.UTF_8)));
        });
    }

    private static void assertSingleIdempotencyKey(List<RequestRecord> requests) {
        assertThat(requests).extracting(RequestRecord::idempotencyKey)
                .doesNotContainNull()
                .containsOnly(requests.getFirst().idempotencyKey());
    }

    @ReactiveHttpClient(name = "replay-composition")
    @LogHttpExchange(logger = ReplayDiagnostics.class)
    interface ReplayClient {
        @POST("/retry-redirect/{status}")
        @IdempotencyKey
        Mono<String> retryRedirect(@PathVar("status") int status, @Body Flux<DataBuffer> body);

        @POST("/retry-auth")
        @IdempotencyKey
        Mono<String> retryAuth(@Body Flux<DataBuffer> body);

        @POST("/auth-redirect")
        @IdempotencyKey
        Mono<String> authRedirect(
                @HeaderParam("Cookie") String cookie,
                @HeaderParam("Proxy-Authorization") String proxyAuthorization,
                @Body Flux<DataBuffer> body);

        @POST("/cross-redirect")
        @IdempotencyKey
        Mono<String> crossAuthorityRedirect(
                @HeaderParam("Cookie") String cookie,
                @HeaderParam("Proxy-Authorization") String proxyAuthorization,
                @Body Flux<DataBuffer> body);

        @POST("/repeatable-redirect")
        @IdempotencyKey
        Mono<String> repeatableRedirect(
                @HeaderParam("Content-Type") String contentType,
                @Body String body);

        @POST("/terminal-auth")
        @IdempotencyKey
        Mono<String> terminalAuthFailure(@Body Flux<DataBuffer> body);
    }

    private record ClientFixture(
            StaticApplicationContext context,
            ReactiveHttpClientFactoryBean<ReplayClient> factory,
            ReplayClient client,
            ReplayDiagnostics diagnostics) implements AutoCloseable {

        private static ClientFixture create(
                ReplayServer server, boolean retry, boolean followRedirects, AuthProvider authProvider) {
            return create(server, retry, followRedirects, authProvider, false);
        }

        private static ClientFixture createWithOAuth(
                ReplayServer server, boolean retry, boolean followRedirects) {
            return create(server, retry, followRedirects, null, true);
        }

        private static ClientFixture create(
                ReplayServer server,
                boolean retry,
                boolean followRedirects,
                AuthProvider authProvider,
                boolean oauth) {
            StaticApplicationContext context = new StaticApplicationContext();
            ReplayDiagnostics diagnostics = new ReplayDiagnostics();
            ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
            ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
            config.setBaseUrl(server.baseUrl());
            config.setFollowRedirects(followRedirects);

            if (retry) {
                ReactiveHttpClientProperties.ResilienceConfig resilience =
                        new ReactiveHttpClientProperties.ResilienceConfig();
                resilience.setEnabled(true);
                resilience.setRetry("replay-composition");
                resilience.setRetryMethods(Set.of("POST"));
                config.setResilience(resilience);
                context.getBeanFactory().registerSingleton("retryRegistry", RetryRegistry.of(
                        RetryConfig.custom().maxAttempts(2).waitDuration(Duration.ZERO).build()));
            }
            if (oauth) {
                ReactiveHttpClientProperties.AuthConfig auth = new ReactiveHttpClientProperties.AuthConfig();
                auth.setType(OAuth2ClientCredentialsAuthProviderFactory.TYPE);
                auth.getOauth2ClientCredentials().setTokenUri(server.baseUrl() + "/token");
                auth.getOauth2ClientCredentials().setClientId("replay-client");
                auth.getOauth2ClientCredentials().setClientSecret("replay-secret");
                auth.getOauth2ClientCredentials().getTokenService().setRetryMaxAttempts(1);
                config.setAuth(auth);
                context.getBeanFactory().registerSingleton(
                        "oauthFactory", (AuthProviderFactory) new OAuth2ClientCredentialsAuthProviderFactory());
            } else if (authProvider != null) {
                config.setAuthProvider("replayAuthProvider");
                context.getBeanFactory().registerSingleton("replayAuthProvider", authProvider);
            }
            properties.getClients().put("replay-composition", config);

            context.getBeanFactory().registerSingleton("properties", properties);
            context.getBeanFactory().registerSingleton("starterWebClientBuilder", WebClient.builder());
            context.getBeanFactory().registerSingleton("jsonCodec", TestJsonCodecs.jsonCodec());
            context.getBeanFactory().registerSingleton("replayDiagnostics", diagnostics);
            context.refresh();

            ReactiveHttpClientFactoryBean<ReplayClient> factory = new ReactiveHttpClientFactoryBean<>();
            factory.setType(ReplayClient.class);
            factory.setApplicationContext(context);
            return new ClientFixture(context, factory, factory.getObject(), diagnostics);
        }

        @Override
        public void close() {
            factory.destroy();
            context.close();
        }
    }

    static final class ReplayDiagnostics
            implements HttpClientObserver, ReactiveHttpClientLifecycleHook, HttpExchangeLogger {
        private final AtomicInteger starts = new AtomicInteger();
        private final AtomicInteger retries = new AtomicInteger();
        private final AtomicInteger successes = new AtomicInteger();
        private final AtomicInteger errors = new AtomicInteger();
        private final AtomicInteger cancellations = new AtomicInteger();
        private final List<HttpClientObserverEvent> observerEvents = new CopyOnWriteArrayList<>();
        private final List<ReactiveHttpClientLifecycleContext> errorContexts = new CopyOnWriteArrayList<>();
        private final List<HttpExchangeLogContext> exchangeLogs = new CopyOnWriteArrayList<>();

        @Override
        public void record(HttpClientObserverEvent event) {
            observerEvents.add(event);
        }

        @Override
        public void onStart(ReactiveHttpClientLifecycleContext context) {
            starts.incrementAndGet();
        }

        @Override
        public void onRetryAttempt(ReactiveHttpClientLifecycleContext context) {
            retries.incrementAndGet();
        }

        @Override
        public void onSuccess(ReactiveHttpClientLifecycleContext context) {
            successes.incrementAndGet();
        }

        @Override
        public void onError(ReactiveHttpClientLifecycleContext context) {
            errors.incrementAndGet();
            errorContexts.add(context);
        }

        @Override
        public void onCancel(ReactiveHttpClientLifecycleContext context) {
            cancellations.incrementAndGet();
        }

        @Override
        public void log(HttpExchangeLogContext context) {
            exchangeLogs.add(context);
        }

        private void assertSuccessfulCalls(int calls, int attempts, String requestUrl) {
            assertThat(successes).hasValue(calls);
            assertThat(errors).hasValue(0);
            assertThat(cancellations).hasValue(0);
            assertThat(observerEvents).hasSize(calls).allSatisfy(event -> {
                assertThat(event.getAttemptCount()).isEqualTo(attempts);
                assertThat(event.getStatusCode()).isEqualTo(HttpStatus.OK.value());
                assertThat(event.getRequestUrl()).isEqualTo(requestUrl);
                assertThat(event.getFailureStage()).isNull();
            });
            assertThat(exchangeLogs).hasSize(calls).allSatisfy(log -> {
                assertThat(log.subscriptionAttemptCount()).isEqualTo(attempts);
                assertThat(log.responseStatus()).isEqualTo(HttpStatus.OK.value());
                assertThat(log.requestUrl()).hasToString(requestUrl);
                assertThat(log.failureStage()).isNull();
            });
        }

        private void assertPreDispatchFailure(int attempts) {
            assertThat(successes).hasValue(0);
            assertThat(errors).hasValue(1);
            assertThat(cancellations).hasValue(0);
            assertThat(observerEvents).singleElement().satisfies(event -> {
                assertThat(event.getAttemptCount()).isEqualTo(attempts);
                assertThat(event.getStatusCode()).isNull();
                assertThat(event.getRequestUrl()).isNull();
                assertThat(event.getRequestHeaders()).isEmpty();
                assertThat(event.getFailureStage()).isNull();
            });
            assertThat(errorContexts).singleElement().satisfies(context -> {
                assertThat(context.attemptNumber()).isEqualTo(attempts);
                assertThat(context.statusCode()).isNull();
                assertThat(context.requestUrl()).isNull();
                assertThat(context.failureStage()).isNull();
            });
            assertThat(exchangeLogs).singleElement().satisfies(log -> {
                assertThat(log.subscriptionAttemptCount()).isEqualTo(attempts);
                assertThat(log.responseStatus()).isNull();
                assertThat(log.requestUrl()).isNull();
                assertThat(log.failureStage()).isNull();
            });
        }
    }

    private static final class ReplayServer implements AutoCloseable {
        private final List<RequestRecord> businessRequests = new CopyOnWriteArrayList<>();
        private final Map<String, AtomicInteger> finalCallsByKey = new ConcurrentHashMap<>();
        private final AtomicInteger tokenRequests = new AtomicInteger();
        private final String crossAuthorityUrl;
        private final DisposableServer server;

        private ReplayServer() {
            this(null);
        }

        private ReplayServer(String crossAuthorityUrl) {
            this.crossAuthorityUrl = crossAuthorityUrl;
            this.server = HttpServer.create()
                    .port(0)
                    .handle((request, response) -> request.receive().aggregate().asByteArray()
                            .defaultIfEmpty(new byte[0])
                            .flatMap(bytes -> {
                                String path = normalizedPath(request.path());
                                if ("/token".equals(path)) {
                                    int token = tokenRequests.incrementAndGet();
                                    return response.header(HttpHeaders.CONTENT_TYPE, "application/json")
                                            .sendString(Mono.just("{\"access_token\":\"token-" + token
                                                    + "\",\"expires_in\":3600}"))
                                            .then();
                                }

                                RequestRecord record = RequestRecord.from(path, request.requestHeaders(), bytes);
                                businessRequests.add(record);
                                if (path.startsWith("/retry-redirect/") && path.endsWith("/final")) {
                                    return retryOncePerKey(record, response);
                                }
                                if (path.startsWith("/retry-redirect/")) {
                                    int status = Integer.parseInt(path.substring("/retry-redirect/".length()));
                                    return response.status(status).header(HttpHeaders.LOCATION, path + "/final").send();
                                }
                                if ("/repeatable-redirect".equals(path)) {
                                    return response.status(307)
                                            .header(HttpHeaders.LOCATION, "/repeatable-redirect/final").send();
                                }
                                if ("/repeatable-redirect/final".equals(path)) {
                                    return retryOncePerKey(record, response);
                                }
                                if ("/retry-auth".equals(path)) {
                                    if ("Bearer token-1".equals(record.authorization())) {
                                        return response.status(401).send();
                                    }
                                    return retryOncePerKey(record, response);
                                }
                                if ("/auth-redirect".equals(path)) {
                                    return response.status(307).header(HttpHeaders.LOCATION, "/auth-redirect/final").send();
                                }
                                if ("/auth-redirect/final".equals(path)) {
                                    return "Bearer token-1".equals(record.authorization())
                                            ? response.status(401).send()
                                            : ok(response);
                                }
                                if ("/cross-redirect".equals(path)) {
                                    return response.status(307)
                                            .header(HttpHeaders.LOCATION, crossAuthorityUrl + "/cross-target").send();
                                }
                                if ("/terminal-auth".equals(path)) {
                                    return "Bearer token-1".equals(record.authorization())
                                            ? response.status(401).send()
                                            : response.status(503).sendString(Mono.just("retry")).then();
                                }
                                return ok(response);
                            }))
                    .bindNow();
        }

        private Mono<Void> retryOncePerKey(RequestRecord request, reactor.netty.http.server.HttpServerResponse response) {
            int call = finalCallsByKey
                    .computeIfAbsent(request.idempotencyKey(), ignored -> new AtomicInteger())
                    .incrementAndGet();
            return call == 1
                    ? response.status(503).sendString(Mono.just("retry")).then()
                    : ok(response);
        }

        private Mono<Void> ok(reactor.netty.http.server.HttpServerResponse response) {
            return response.sendString(Mono.just("ok")).then();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.port();
        }

        private AtomicInteger tokenRequests() {
            return tokenRequests;
        }

        private List<RequestRecord> businessRequests() {
            return List.copyOf(businessRequests);
        }

        @Override
        public void close() {
            server.disposeNow(BLOCK_TIMEOUT);
        }
    }

    private static final class CrossAuthorityTarget implements AutoCloseable {
        private final List<RequestRecord> requests = new CopyOnWriteArrayList<>();
        private final DisposableServer server = HttpServer.create()
                .port(0)
                .handle((request, response) -> request.receive().aggregate().asByteArray()
                        .defaultIfEmpty(new byte[0])
                        .flatMap(bytes -> {
                            requests.add(RequestRecord.from(
                                    normalizedPath(request.path()), request.requestHeaders(), bytes));
                            return response.sendString(Mono.just("ok")).then();
                        }))
                .bindNow();

        private String url() {
            return "http://127.0.0.1:" + server.port();
        }

        private List<RequestRecord> requests() {
            return List.copyOf(requests);
        }

        @Override
        public void close() {
            server.disposeNow(BLOCK_TIMEOUT);
        }
    }

    private record RequestRecord(
            String path,
            String authorization,
            String cookie,
            String proxyAuthorization,
            String idempotencyKey,
            String body,
            byte[] bodyBytes) {

        private static RequestRecord from(
                String path, io.netty.handler.codec.http.HttpHeaders headers, byte[] bodyBytes) {
            byte[] copied = bodyBytes.clone();
            return new RequestRecord(
                    path,
                    headers.get(HttpHeaders.AUTHORIZATION),
                    headers.get(HttpHeaders.COOKIE),
                    headers.get("Proxy-Authorization"),
                    headers.get("Idempotency-Key"),
                    new String(copied, StandardCharsets.UTF_8),
                    copied);
        }
    }

    private static String normalizedPath(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }
}
