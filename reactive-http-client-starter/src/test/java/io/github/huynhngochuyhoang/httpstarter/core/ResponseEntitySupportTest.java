package io.github.huynhngochuyhoang.httpstarter.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.HttpClientException;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.ConnectionProvider;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ResponseEntitySupportTest {

    @Test
    void monoResponseEntityStringExposesStatusHeadersAndBody() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://entity.test")
                .exchangeFunction(req -> Mono.just(ClientResponse.create(HttpStatus.CREATED)
                        .header(HttpHeaders.LOCATION, "/widgets/123")
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                        .body("created")
                        .build()))
                .build();

        StepVerifier.create(invokeStringEntity(createHandler(webClient, new DefaultErrorDecoder())))
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    assertThat(entity.getHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo("/widgets/123");
                    assertThat(entity.getBody()).isEqualTo("created");
                })
                .verifyComplete();
    }

    @Test
    void monoResponseEntityVoidExposesStatusAndHeaders() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://entity.test")
                .exchangeFunction(req -> Mono.just(ClientResponse.create(HttpStatus.ACCEPTED)
                        .header("X-Request-Id", "abc-123")
                        .build()))
                .build();

        StepVerifier.create(invokeVoidEntity(createHandler(webClient, new DefaultErrorDecoder())))
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
                    assertThat(entity.getHeaders().getFirst("X-Request-Id")).isEqualTo("abc-123");
                    assertThat(entity.getBody()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void monoResponseEntityVoidReleasesUnexpectedResponseBody() {
        ClientResponse response = mockBodilessResponse();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://entity.test")
                .exchangeFunction(req -> Mono.just(response))
                .build();

        StepVerifier.create(invokeVoidEntity(createHandler(webClient, new DefaultErrorDecoder())))
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(entity.getBody()).isNull();
                })
                .verifyComplete();

        verify(response).releaseBody();
        verify(response, never()).bodyToMono(Void.class);
    }

    @Test
    void monoVoidReleasesUnexpectedResponseBody() {
        ClientResponse response = mockBodilessResponse();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://entity.test")
                .exchangeFunction(req -> Mono.just(response))
                .build();

        StepVerifier.create(invokePlainVoid(createHandler(webClient, new DefaultErrorDecoder())))
                .verifyComplete();

        verify(response, atLeastOnce()).releaseBody();
        verify(response, never()).bodyToMono(Void.class);
    }

    @Test
    void pooledConnectionRemainsReusableAfterPlainMonoVoidDrainsUnexpectedBody() {
        List<String> channelIds = new CopyOnWriteArrayList<>();
        DisposableServer server = bodilessBodyServer("/plain-void", channelIds);
        ConnectionProvider provider = singleConnectionProvider("plain-void-drain");
        try {
            ReactiveClientInvocationHandler handler = createHandler(pooledWebClient(server, provider), new DefaultErrorDecoder());

            StepVerifier.create(invokePlainVoid(handler))
                    .verifyComplete();
            StepVerifier.create(invokePlainVoid(handler))
                    .verifyComplete();

            assertThat(channelIds).hasSize(2);
            assertThat(channelIds.get(1)).isEqualTo(channelIds.get(0));
        } finally {
            provider.disposeLater().block(Duration.ofSeconds(5));
            server.disposeNow();
        }
    }

    @Test
    void pooledConnectionRemainsReusableAfterResponseEntityVoidDrainsUnexpectedBody() {
        List<String> channelIds = new CopyOnWriteArrayList<>();
        DisposableServer server = bodilessBodyServer("/void-entity", channelIds);
        ConnectionProvider provider = singleConnectionProvider("void-entity-drain");
        try {
            ReactiveClientInvocationHandler handler = createHandler(pooledWebClient(server, provider), new DefaultErrorDecoder());

            StepVerifier.create(invokeVoidEntity(handler))
                    .assertNext(entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK))
                    .verifyComplete();
            StepVerifier.create(invokeVoidEntity(handler))
                    .assertNext(entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK))
                    .verifyComplete();

            assertThat(channelIds).hasSize(2);
            assertThat(channelIds.get(1)).isEqualTo(channelIds.get(0));
        } finally {
            provider.disposeLater().block(Duration.ofSeconds(5));
            server.disposeNow();
        }
    }

    @Test
    void monoVoidStillUsesDefaultDecoderForNon2xxResponses() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://entity.test")
                .exchangeFunction(req -> Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                        .body("bad request")
                        .build()))
                .build();

        StepVerifier.create(invokePlainVoid(createHandler(webClient, new DefaultErrorDecoder())))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(HttpClientException.class);
                    HttpClientException ex = (HttpClientException) error;
                    assertThat(ex.getStatusCode()).isEqualTo(400);
                    assertThat(ex.getResponseBody()).isEqualTo("bad request");
                })
                .verify();
    }

    @Test
    void monoResponseEntityStillUsesDefaultDecoderForNon2xxResponses() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://entity.test")
                .exchangeFunction(req -> Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                        .body("bad request")
                        .build()))
                .build();

        StepVerifier.create(invokeStringEntity(createHandler(webClient, new DefaultErrorDecoder())))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(HttpClientException.class);
                    HttpClientException ex = (HttpClientException) error;
                    assertThat(ex.getStatusCode()).isEqualTo(400);
                    assertThat(ex.getResponseBody()).isEqualTo("bad request");
                })
                .verify();
    }

    @Test
    void monoResponseEntityStillUsesRegisteredErrorMappersForNon2xxResponses() {
        RuntimeException mapped = new RuntimeException("mapped response");
        ErrorResponseMapper mapper = context -> Optional.of(mapped);
        WebClient webClient = WebClient.builder()
                .baseUrl("http://entity.test")
                .exchangeFunction(req -> Mono.just(ClientResponse.create(HttpStatus.UNPROCESSABLE_ENTITY)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                        .body("mapped body")
                        .build()))
                .build();

        StepVerifier.create(invokeStringEntity(createHandler(webClient, new DefaultErrorDecoder("entity-client", List.of(mapper)))))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(mapped))
                .verify();
    }

    @SuppressWarnings("unchecked")
    private static ReactiveClientInvocationHandler createHandler(WebClient webClient, DefaultErrorDecoder decoder) {
        ApplicationContext appCtx = mock(ApplicationContext.class);
        ObjectProvider<HttpClientObserver> observerProvider = mock(ObjectProvider.class);
        when(appCtx.getBeanProvider(HttpClientObserver.class)).thenReturn(observerProvider);
        when(observerProvider.getIfAvailable()).thenReturn(null);

        return new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                decoder,
                new ReactiveHttpClientProperties.ClientConfig(),
                "entity-client",
                appCtx,
                new NoopResilienceOperatorApplier(),
                new ObjectMapper(),
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );
    }

    private static ClientResponse mockBodilessResponse() {
        ClientResponse response = mock(ClientResponse.class);
        ClientResponse.Headers headers = mock(ClientResponse.Headers.class);
        when(response.statusCode()).thenReturn(HttpStatus.OK);
        when(response.headers()).thenReturn(headers);
        when(headers.asHttpHeaders()).thenReturn(HttpHeaders.EMPTY);
        when(response.releaseBody()).thenReturn(Mono.empty());
        return response;
    }

    private static DisposableServer bodilessBodyServer(String path, List<String> channelIds) {
        return HttpServer.create()
                .port(0)
                .route(routes -> routes.get(path, (request, response) -> {
                    request.withConnection(connection -> channelIds.add(connection.channel().id().asLongText()));
                    return response
                            .status(HttpStatus.OK.value())
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                            .sendString(Mono.just("unexpected-body"))
                            .then();
                }))
                .bindNow();
    }

    private static ConnectionProvider singleConnectionProvider(String name) {
        return ConnectionProvider.builder(name)
                .maxConnections(1)
                .pendingAcquireMaxCount(1)
                .pendingAcquireTimeout(Duration.ofSeconds(2))
                .build();
    }

    private static WebClient pooledWebClient(DisposableServer server, ConnectionProvider provider) {
        return WebClient.builder()
                .baseUrl("http://127.0.0.1:" + server.port())
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create(provider).responseTimeout(Duration.ofSeconds(5))))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Mono<ResponseEntity<String>> invokeStringEntity(ReactiveClientInvocationHandler handler) {
        try {
            Method method = EntityClient.class.getMethod("getStringEntity");
            return (Mono<ResponseEntity<String>>) handler.invoke(null, method, new Object[0]);
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Mono<Void> invokePlainVoid(ReactiveClientInvocationHandler handler) {
        try {
            Method method = EntityClient.class.getMethod("plainVoid");
            return (Mono<Void>) handler.invoke(null, method, new Object[0]);
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Mono<ResponseEntity<Void>> invokeVoidEntity(ReactiveClientInvocationHandler handler) {
        try {
            Method method = EntityClient.class.getMethod("getVoidEntity");
            return (Mono<ResponseEntity<Void>>) handler.invoke(null, method, new Object[0]);
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    interface EntityClient {
        @GET("/string")
        Mono<ResponseEntity<String>> getStringEntity();

        @GET("/plain-void")
        Mono<Void> plainVoid();

        @GET("/void-entity")
        Mono<ResponseEntity<Void>> getVoidEntity();
    }
}
