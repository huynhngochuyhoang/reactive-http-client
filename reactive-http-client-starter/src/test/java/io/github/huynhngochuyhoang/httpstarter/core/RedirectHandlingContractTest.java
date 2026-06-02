package io.github.huynhngochuyhoang.httpstarter.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedirectHandlingContractTest {

    @Test
    void visibleRedirectPassesThroughProxyWithoutInvokingErrorMappers() {
        AtomicInteger mapperInvocations = new AtomicInteger();
        AtomicReference<HttpClientObserverEvent> observed = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://redirect.test")
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, "/final")
                        .body("redirect")
                        .build()))
                .build();

        RedirectClient client = createClient(webClient, mapperInvocations, observed::set);

        StepVerifier.create(client.get())
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
                    assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo("/final");
                    assertThat(response.getBody()).isEqualTo("redirect");
                })
                .verifyComplete();

        assertThat(mapperInvocations).hasValue(0);
        assertThat(observed.get().getStatusCode()).isEqualTo(HttpStatus.FOUND.value());
    }

    @Test
    void configuredReactorNettyTransportCanFollowRedirectBeforeProxyReceivesResponse() {
        AtomicInteger requests = new AtomicInteger();
        AtomicInteger mapperInvocations = new AtomicInteger();
        DisposableServer server = HttpServer.create()
                .port(0)
                .route(routes -> routes
                        .get("/start", (request, response) -> {
                            requests.incrementAndGet();
                            return response.status(HttpStatus.FOUND.value())
                                    .addHeader(HttpHeaders.LOCATION, "/final")
                                    .send();
                        })
                        .get("/final", (request, response) -> {
                            requests.incrementAndGet();
                            return response.sendString(Mono.just("followed"));
                        }))
                .bindNow();

        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.port())
                    .clientConnector(new ReactorClientHttpConnector(HttpClient.create().followRedirect(true)))
                    .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                    .build();
            RedirectClient client = createClient(webClient, mapperInvocations, event -> { });

            StepVerifier.create(client.get())
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(response.getBody()).isEqualTo("followed");
                    })
                    .verifyComplete();

            assertThat(requests).hasValue(2);
            assertThat(mapperInvocations).hasValue(0);
        } finally {
            server.disposeNow(Duration.ofSeconds(5));
        }
    }

    @SuppressWarnings("unchecked")
    private static RedirectClient createClient(
            WebClient webClient,
            AtomicInteger mapperInvocations,
            HttpClientObserver observer) {
        ErrorResponseMapper mapper = context -> {
            mapperInvocations.incrementAndGet();
            return Optional.empty();
        };
        ApplicationContext context = mock(ApplicationContext.class);
        ObjectProvider<HttpClientObserver> observerProvider = mock(ObjectProvider.class);
        when(context.getBeanProvider(HttpClientObserver.class)).thenReturn(observerProvider);
        when(observerProvider.orderedStream()).thenAnswer(invocation -> java.util.stream.Stream.of(observer));

        ObjectProvider<ReactiveHttpClientLifecycleHook> lifecycleProvider = mock(ObjectProvider.class);
        when(context.getBeanProvider(ReactiveHttpClientLifecycleHook.class)).thenReturn(lifecycleProvider);
        when(lifecycleProvider.orderedStream()).thenReturn(java.util.stream.Stream.empty());

        ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder("redirect-client", List.of(mapper)),
                new ReactiveHttpClientProperties.ClientConfig(),
                "redirect-client",
                context,
                new NoopResilienceOperatorApplier(),
                new ObjectMapper(),
                new ReactiveHttpClientProperties.ObservabilityConfig());
        return (RedirectClient) Proxy.newProxyInstance(
                RedirectClient.class.getClassLoader(),
                new Class<?>[]{RedirectClient.class},
                handler);
    }

    interface RedirectClient {
        @GET("/start")
        Mono<ResponseEntity<String>> get();
    }
}
