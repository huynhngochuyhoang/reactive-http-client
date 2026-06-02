package io.github.huynhngochuyhoang.httpstarter.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.HeaderParam;
import io.github.huynhngochuyhoang.httpstarter.annotation.PathVar;
import io.github.huynhngochuyhoang.httpstarter.annotation.QueryParam;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.HttpClientException;
import io.github.huynhngochuyhoang.httpstarter.exception.RemoteServiceException;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiagnosticContextContractTest {

    @Test
    void terminalContextsExposeTheirDocumentedSuccessFields() throws Throwable {
        AtomicReference<HttpExchangeLogContext> logged = new AtomicReference<>();
        AtomicReference<HttpClientObserverEvent> observed = new AtomicReference<>();
        AtomicReference<ReactiveHttpClientLifecycleContext> succeeded = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://initial.local")
                .filter((request, next) -> next.exchange(ClientRequest.from(request)
                        .url(URI.create("http://final.local:8081/items/42?view=full"))
                        .header("X-Final", "customizer")
                        .header(HttpHeaders.AUTHORIZATION, "secret")
                        .build()))
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.CREATED)
                        .header("X-Response", "created")
                        .body("ok")
                        .build()))
                .build();

        StepVerifier.create(invoke(createHandler(webClient, new DefaultErrorDecoder(), new RecordingLogger(logged),
                        observed::set, successHook(succeeded)), "42", "full", "declared"))
                .expectNext("ok")
                .verifyComplete();

        HttpExchangeLogContext logContext = logged.get();
        assertThat(logContext.requestUrl()).hasToString("http://final.local:8081/items/42?view=full");
        assertThat(logContext.requestHeaders())
                .containsEntry("X-Final", "customizer")
                .containsEntry(HttpHeaders.AUTHORIZATION, "secret");
        assertThat(logContext.responseStatus()).isEqualTo(201);
        assertThat(logContext.responseHeaders()).containsEntry("X-Response", List.of("created"));
        assertThat(logContext.responseBody()).isEqualTo("ok");
        assertThat(logContext.durationMs()).isGreaterThanOrEqualTo(0);
        assertThat(logContext.subscriptionAttemptCount()).isEqualTo(1);

        HttpClientObserverEvent observerEvent = observed.get();
        assertThat(observerEvent.getRequestUrl()).isEqualTo("http://final.local:8081/items/42?view=full");
        assertThat(observerEvent.getRequestHeaders())
                .containsEntry("X-Final", "customizer")
                .containsEntry(HttpHeaders.AUTHORIZATION, "secret");
        assertThat(observerEvent.getStatusCode()).isEqualTo(201);
        assertThat(observerEvent.getResponseBody()).isEqualTo("ok");
        assertThat(observerEvent.getDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(observerEvent.getAttemptCount()).isEqualTo(1);

        ReactiveHttpClientLifecycleContext lifecycleContext = succeeded.get();
        assertThat(lifecycleContext.requestUrl()).hasToString("http://final.local:8081/items/42?view=full");
        assertThat(lifecycleContext.headers())
                .containsEntry("X-Declared", "declared")
                .doesNotContainKey("X-Final");
        assertThat(lifecycleContext.statusCode()).isEqualTo(201);
        assertThat(lifecycleContext.attemptNumber()).isEqualTo(1);
    }

    @Test
    void mapperReceivesBoundedErrorBodyAndReadOnlyResponseHeaders() {
        AtomicReference<ErrorResponseContext> mapped = new AtomicReference<>();
        DefaultErrorDecoder decoder = new DefaultErrorDecoder("test-client", List.of(context -> {
            mapped.set(context);
            return Optional.empty();
        }));

        StepVerifier.create(decoder.decode(ClientResponse.create(HttpStatus.BAD_GATEWAY)
                        .header("X-Error", "upstream")
                        .body("failure")
                        .build()))
                .assertNext(error -> assertThat(error).isInstanceOf(RemoteServiceException.class))
                .verifyComplete();

        ErrorResponseContext context = mapped.get();
        assertThat(context.statusCode()).isEqualTo(502);
        assertThat(context.responseHeaders().getFirst("X-Error")).isEqualTo("upstream");
        assertThat(context.responseBody()).isEqualTo("failure");
        assertThat(context.responseBodyTruncated()).isFalse();
        assertThat(context.retainedResponseBodyBytes()).isEqualTo(7);
        assertThat(context.responseHeaders()).isSameAs(HttpHeaders.readOnlyHttpHeaders(context.responseHeaders()));
    }

    @Test
    void errorBodyIsExposedToMapperButNotTerminalSuccessBodyFields() throws Throwable {
        AtomicReference<ErrorResponseContext> mapped = new AtomicReference<>();
        AtomicReference<HttpExchangeLogContext> logged = new AtomicReference<>();
        AtomicReference<HttpClientObserverEvent> observed = new AtomicReference<>();
        DefaultErrorDecoder decoder = new DefaultErrorDecoder("test-client", List.of(context -> {
            mapped.set(context);
            return Optional.empty();
        }));
        WebClient webClient = WebClient.builder()
                .baseUrl("http://initial.local")
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST)
                        .header("X-Error", "upstream")
                        .body("failure")
                        .build()))
                .build();

        StepVerifier.create(invoke(createHandler(webClient, decoder, new RecordingLogger(logged),
                        observed::set, successHook(new AtomicReference<>())), "42", "full", "declared"))
                .expectError(HttpClientException.class)
                .verify();

        assertThat(mapped.get().responseBody()).isEqualTo("failure");
        assertThat(logged.get().responseBody()).isNull();
        assertThat(observed.get().getResponseBody()).isNull();
    }

    @Test
    void lifecycleHooksAndObserversDoNotExposeResponseHeaders() {
        assertThat(Arrays.stream(ReactiveHttpClientLifecycleContext.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .doesNotContain("responseHeaders");
        assertThat(Arrays.stream(HttpClientObserverEvent.class.getMethods())
                .map(Method::getName))
                .doesNotContain("getResponseHeaders");
    }

    private static ReactiveHttpClientLifecycleHook successHook(
            AtomicReference<ReactiveHttpClientLifecycleContext> succeeded) {
        return new ReactiveHttpClientLifecycleHook() {
            @Override
            public void onSuccess(ReactiveHttpClientLifecycleContext context) {
                succeeded.set(context);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invoke(
            ReactiveClientInvocationHandler handler,
            String id,
            String view,
            String declaredHeader) throws Throwable {
        Method method = ContractClient.class.getMethod("get", String.class, String.class, String.class);
        return (Mono<String>) handler.invoke(null, method, new Object[]{id, view, declaredHeader});
    }

    @SuppressWarnings("unchecked")
    private static ReactiveClientInvocationHandler createHandler(
            WebClient webClient,
            DefaultErrorDecoder errorDecoder,
            DefaultHttpExchangeLogger exchangeLogger,
            HttpClientObserver observer,
            ReactiveHttpClientLifecycleHook lifecycleHook) {
        ApplicationContext context = mock(ApplicationContext.class);
        ObjectProvider<HttpClientObserver> observerProvider = mock(ObjectProvider.class);
        when(context.getBeanProvider(HttpClientObserver.class)).thenReturn(observerProvider);
        when(observerProvider.orderedStream()).thenAnswer(invocation -> java.util.stream.Stream.of(observer));

        ObjectProvider<ReactiveHttpClientLifecycleHook> lifecycleProvider = mock(ObjectProvider.class);
        when(context.getBeanProvider(ReactiveHttpClientLifecycleHook.class)).thenReturn(lifecycleProvider);
        when(lifecycleProvider.orderedStream()).thenAnswer(invocation -> java.util.stream.Stream.of(lifecycleHook));

        ObjectProvider<DefaultHttpExchangeLogger> loggerProvider = mock(ObjectProvider.class);
        when(context.getBeanProvider(DefaultHttpExchangeLogger.class)).thenReturn(loggerProvider);
        when(loggerProvider.getIfAvailable()).thenReturn(exchangeLogger);

        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setExchangeLoggingEnabled(true);
        ReactiveHttpClientProperties.ObservabilityConfig observability =
                new ReactiveHttpClientProperties.ObservabilityConfig();
        observability.setLogResponseBody(true);
        return new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                errorDecoder,
                config,
                "test-client",
                context,
                new NoopResilienceOperatorApplier(),
                new ObjectMapper(),
                observability);
    }

    private static final class RecordingLogger extends DefaultHttpExchangeLogger {
        private final AtomicReference<HttpExchangeLogContext> logged;

        private RecordingLogger(AtomicReference<HttpExchangeLogContext> logged) {
            this.logged = logged;
        }

        @Override
        public void log(HttpExchangeLogContext context) {
            logged.set(context);
        }
    }

    interface ContractClient {
        @GET("/items/{id}")
        Mono<String> get(
                @PathVar("id") String id,
                @QueryParam("view") String view,
                @HeaderParam("X-Declared") String declaredHeader);
    }
}
