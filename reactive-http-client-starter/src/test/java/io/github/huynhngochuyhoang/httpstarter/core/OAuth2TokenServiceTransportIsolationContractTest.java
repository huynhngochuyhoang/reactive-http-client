package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProviderFactory;
import io.github.huynhngochuyhoang.httpstarter.auth.OAuth2ClientCredentialsAuthProviderFactory;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.ConnectionProvider;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2TokenServiceTransportIsolationContractTest {

    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void builtInTokenTransportDoesNotInheritBusinessFiltersAndOwnsItsPool() throws Exception {
        try (TokenAndApiServer server = new TokenAndApiServer()) {
            ReactiveHttpClientProperties properties = properties(server, 1);
            ReactiveHttpClientFactoryBean<OAuthClient> factory = factory(properties);

            OAuthClient client = factory.getObject();
            List<String> results = Flux.range(0, 8)
                    .flatMap(index -> client.call())
                    .collectList()
                    .block(BLOCK_TIMEOUT);

            assertThat(results).containsOnly("ok");
            assertThat(server.tokenRequests()).isEqualTo(1);
            assertThat(server.tokenBusinessHeaders()).containsExactly("<absent>");
            assertThat(server.apiBusinessHeaders()).containsOnly("business-filter");
            assertThat(server.apiAuthorizationHeaders()).containsOnly("Bearer token-1");

            ConnectionProvider tokenPool = tokenPool(factory);
            assertThat(tokenPool.isDisposed()).isFalse();
            factory.destroy();
            assertThat(tokenPool.isDisposed()).isTrue();
        }
    }

    @Test
    void hiddenUnauthorizedReplayInvalidatesTokenOnceWithoutBusinessRetryPolicy() {
        try (TokenAndApiServer server = new TokenAndApiServer()) {
            server.rejectFirstToken();
            ReactiveHttpClientFactoryBean<OAuthClient> factory = factory(properties(server, 1));
            try {
                assertThat(factory.getObject().call().block(BLOCK_TIMEOUT)).isEqualTo("ok");
                assertThat(server.tokenRequests()).isEqualTo(2);
                assertThat(server.apiAuthorizationHeaders())
                        .containsExactly("Bearer token-1", "Bearer token-2");
            } finally {
                factory.destroy();
            }
        }
    }

    @Test
    void configuredTokenRetryIsIndependentFromBusinessRequestCount() {
        try (TokenAndApiServer server = new TokenAndApiServer()) {
            server.failTokenRequests(2);
            ReactiveHttpClientFactoryBean<OAuthClient> factory = factory(properties(server, 3));
            try {
                assertThat(factory.getObject().call().block(BLOCK_TIMEOUT)).isEqualTo("ok");
                assertThat(server.tokenRequests()).isEqualTo(3);
                assertThat(server.apiRequests()).isEqualTo(1);
            } finally {
                factory.destroy();
            }
        }
    }

    private static ReactiveHttpClientProperties properties(TokenAndApiServer server, int retryMaxAttempts) {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig client = new ReactiveHttpClientProperties.ClientConfig();
        client.setBaseUrl(server.baseUrl());
        ReactiveHttpClientProperties.AuthConfig auth = new ReactiveHttpClientProperties.AuthConfig();
        auth.setType(OAuth2ClientCredentialsAuthProviderFactory.TYPE);
        auth.getOauth2ClientCredentials().setTokenUri(server.baseUrl() + "/token");
        auth.getOauth2ClientCredentials().setClientId("client-id");
        auth.getOauth2ClientCredentials().setClientSecret("client-secret");
        auth.getOauth2ClientCredentials().getTokenService().setRetryMaxAttempts(retryMaxAttempts);
        auth.getOauth2ClientCredentials().getTokenService().setRetryBackoffMs(0);
        auth.getOauth2ClientCredentials().getTokenService().setRequestTimeoutMs(1_000);
        client.setAuth(auth);
        properties.getClients().put("oauth-isolation", client);
        return properties;
    }

    private static ReactiveHttpClientFactoryBean<OAuthClient> factory(
            ReactiveHttpClientProperties properties) {
        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("properties", properties);
        context.getBeanFactory().registerSingleton("oauthFactory",
                (AuthProviderFactory) new OAuth2ClientCredentialsAuthProviderFactory());
        context.getBeanFactory().registerSingleton("webClientBuilder", WebClient.builder()
                .filter((request, next) -> next.exchange(org.springframework.web.reactive.function.client.ClientRequest
                        .from(request)
                        .header("X-Business-Only", "business-filter")
                        .build())));
        context.refresh();

        ReactiveHttpClientFactoryBean<OAuthClient> factory = new ReactiveHttpClientFactoryBean<>();
        factory.setType(OAuthClient.class);
        factory.setApplicationContext(context);
        return factory;
    }

    private static ConnectionProvider tokenPool(ReactiveHttpClientFactoryBean<?> factory) throws Exception {
        Field field = ReactiveHttpClientFactoryBean.class.getDeclaredField("tokenServiceConnectionProvider");
        field.setAccessible(true);
        return (ConnectionProvider) field.get(factory);
    }

    @ReactiveHttpClient(name = "oauth-isolation")
    interface OAuthClient {
        @GET("/call")
        Mono<String> call();
    }

    private static final class TokenAndApiServer implements AutoCloseable {
        private final AtomicInteger tokenRequests = new AtomicInteger();
        private final AtomicInteger apiRequests = new AtomicInteger();
        private final List<String> tokenBusinessHeaders = new CopyOnWriteArrayList<>();
        private final List<String> apiBusinessHeaders = new CopyOnWriteArrayList<>();
        private final List<String> apiAuthorizationHeaders = new CopyOnWriteArrayList<>();
        private final DisposableServer server;
        private volatile int failingTokenRequests;
        private volatile boolean rejectFirstToken;

        private TokenAndApiServer() {
            this.server = HttpServer.create()
                    .port(0)
                    .handle((request, response) -> {
                        if (request.uri().startsWith("/token")) {
                            int attempt = tokenRequests.incrementAndGet();
                            tokenBusinessHeaders.add(java.util.Objects.toString(
                                    request.requestHeaders().get("X-Business-Only"), "<absent>"));
                            if (attempt <= failingTokenRequests) {
                                return response.status(503)
                                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                                        .sendString(Mono.just("{\"error\":\"temporarily_unavailable\"}"));
                            }
                            return Mono.delay(Duration.ofMillis(40))
                                    .then(response.header(HttpHeaders.CONTENT_TYPE, "application/json")
                                            .sendString(Mono.just("{\"access_token\":\"token-" + attempt
                                                    + "\",\"expires_in\":3600}"))
                                            .then());
                        }
                        apiRequests.incrementAndGet();
                        String authorization = request.requestHeaders().get(HttpHeaders.AUTHORIZATION);
                        apiAuthorizationHeaders.add(authorization);
                        apiBusinessHeaders.add(request.requestHeaders().get("X-Business-Only"));
                        if (rejectFirstToken && "Bearer token-1".equals(authorization)) {
                            return response.status(401).send();
                        }
                        return response.sendString(Mono.just("ok"));
                    })
                    .bindNow();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.port();
        }

        private void failTokenRequests(int count) {
            this.failingTokenRequests = count;
        }

        private void rejectFirstToken() {
            this.rejectFirstToken = true;
        }

        private int tokenRequests() {
            return tokenRequests.get();
        }

        private int apiRequests() {
            return apiRequests.get();
        }

        private List<String> tokenBusinessHeaders() {
            return tokenBusinessHeaders;
        }

        private List<String> apiBusinessHeaders() {
            return apiBusinessHeaders;
        }

        private List<String> apiAuthorizationHeaders() {
            return apiAuthorizationHeaders;
        }

        @Override
        public void close() {
            server.disposeNow();
        }
    }
}
