package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class ReactiveClientInvocationHandlerRetrySafetyTest {

    @Test
    void retryEnabledGetDoesNotWarn(CapturedOutput output) {
        AtomicInteger attempts = new AtomicInteger();
        ReactiveClientInvocationHandler handler = createHandler(flakyThenOk(attempts), true, Set.of("GET", "POST"));

        StepVerifier.create(invoke(handler, "getSafe"))
                .expectNext("ok")
                .verifyComplete();

        assertThat(attempts).hasValue(2);
        assertThat(output.getOut()).doesNotContain("Unsafe retry configured");
    }

    @Test
    void retryEnabledPostWithIdempotencyKeyDoesNotWarn(CapturedOutput output) {
        AtomicInteger attempts = new AtomicInteger();
        ReactiveClientInvocationHandler handler = createHandler(flakyThenOk(attempts), true, Set.of("POST"));

        StepVerifier.create(invoke(handler, "createWithIdempotencyKey", "idem-1"))
                .expectNext("ok")
                .verifyComplete();

        assertThat(attempts).hasValue(2);
        assertThat(output.getOut()).doesNotContain("Unsafe retry configured");
    }

    @Test
    void retryEnabledPostWithNullIdempotencyKeyWarnsButKeepsCompatibility(CapturedOutput output) {
        AtomicInteger attempts = new AtomicInteger();
        ReactiveClientInvocationHandler handler = createHandler(flakyThenOk(attempts), true, Set.of("POST"));

        StepVerifier.create(invoke(handler, "createWithIdempotencyKey", (Object) null))
                .expectNext("ok")
                .verifyComplete();

        assertThat(attempts).hasValue(2);
        assertThat(output.getOut())
                .contains("Unsafe retry configured for reactive HTTP client [test-client]")
                .contains("RetrySafetyClient#createWithIdempotencyKey")
                .contains("HTTP [POST]")
                .contains("Idempotency-Key");
    }

    @Test
    void retryEnabledIdempotentWriteMethodsDoNotWarn(CapturedOutput output) {
        AtomicInteger putAttempts = new AtomicInteger();
        ReactiveClientInvocationHandler putHandler = createHandler(flakyThenOk(putAttempts), true, Set.of("PUT", "DELETE"));

        StepVerifier.create(invoke(putHandler, "replaceIdempotently"))
                .expectNext("ok")
                .verifyComplete();

        AtomicInteger deleteAttempts = new AtomicInteger();
        ReactiveClientInvocationHandler deleteHandler = createHandler(flakyThenOk(deleteAttempts), true, Set.of("PUT", "DELETE"));

        StepVerifier.create(invoke(deleteHandler, "deleteIdempotently"))
                .expectNext("ok")
                .verifyComplete();

        assertThat(putAttempts).hasValue(2);
        assertThat(deleteAttempts).hasValue(2);
        assertThat(output.getOut()).doesNotContain("Unsafe retry configured");
    }

    @Test
    void retryEnabledPostWithoutIdempotencyKeyWarnsButKeepsCompatibility(CapturedOutput output) {
        AtomicInteger attempts = new AtomicInteger();
        ReactiveClientInvocationHandler handler = createHandler(flakyThenOk(attempts), true, Set.of("POST"));

        StepVerifier.create(invoke(handler, "createWithoutIdempotencyKey"))
                .expectNext("ok")
                .verifyComplete();

        assertThat(attempts).hasValue(2);
        assertThat(output.getOut())
                .contains("Unsafe retry configured for reactive HTTP client [test-client]")
                .contains("RetrySafetyClient#createWithoutIdempotencyKey")
                .contains("HTTP [POST]")
                .contains("retry instance [default]")
                .contains("retry-methods [POST]")
                .contains("Idempotency-Key");
    }

    @Test
    void retryEnabledRepeatableJsonBodyDoesNotWarnAboutBodyRepeatability(CapturedOutput output) {
        AtomicInteger attempts = new AtomicInteger();
        ReactiveClientInvocationHandler handler = createHandler(flakyThenOk(attempts), true, Set.of("POST"));

        StepVerifier.create(invoke(handler, "createJson", Map.of("name", "alice")))
                .expectNext("ok")
                .verifyComplete();

        assertThat(attempts).hasValue(2);
        assertThat(output.getOut())
                .doesNotContain("with non-repeatable request body")
                .doesNotContain("with application-owned request body");
    }

    @Test
    void retryEnabledMultipartFormBodyDoesNotWarnAboutBodyRepeatability(CapturedOutput output) {
        AtomicInteger attempts = new AtomicInteger();
        ReactiveClientInvocationHandler handler = createHandler(flakyThenOk(attempts), true, Set.of("POST"));

        StepVerifier.create(invoke(handler, "submitForm", "alice"))
                .expectNext("ok")
                .verifyComplete();

        assertThat(attempts).hasValue(2);
        assertThat(output.getOut()).doesNotContain("request body [default]");
    }

    @Test
    void retryEnabledMultipartResourceBodyWarnsAboutApplicationOwnedBody(CapturedOutput output) {
        AtomicInteger attempts = new AtomicInteger();
        ReactiveClientInvocationHandler handler = createHandler(flakyThenOk(attempts), true, Set.of("POST"));
        Resource resource = new ByteArrayResource("content".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "upload.txt";
            }
        };

        StepVerifier.create(invoke(handler, "uploadResource", resource))
                .expectNext("ok")
                .verifyComplete();

        assertThat(attempts).hasValue(2);
        assertThat(output.getOut())
                .contains("Retry configured for reactive HTTP client [test-client]")
                .contains("RetrySafetyClient#uploadResource")
                .contains("with application-owned request body [default]");
    }

    @Test
    void retryEnabledUncertainDeclaredBodiesWarnAboutApplicationOwnership(CapturedOutput output) {
        ReactiveClientInvocationHandler objectHandler = createHandler(
                flakyThenOk(new AtomicInteger()), true, Set.of("POST"));
        ReactiveClientInvocationHandler streamHandler = createHandler(
                flakyThenOk(new AtomicInteger()), true, Set.of("POST"));

        StepVerifier.create(invoke(objectHandler, "uploadObject", Map.of("name", "alice")))
                .expectNext("ok")
                .verifyComplete();
        StepVerifier.create(invoke(streamHandler, "uploadInputStream",
                        new ByteArrayInputStream("payload".getBytes(StandardCharsets.UTF_8))))
                .expectNext("ok")
                .verifyComplete();

        assertThat(output.getOut())
                .contains("RetrySafetyClient#uploadObject(java.lang.Object)")
                .contains("RetrySafetyClient#uploadInputStream(java.io.InputStream)")
                .contains("with application-owned request body [default]");
    }

    @Test
    void retryEnabledRawPublisherBodyWarnsAboutNonRepeatableBody(CapturedOutput output) {
        AtomicInteger attempts = new AtomicInteger();
        ReactiveClientInvocationHandler handler = createHandler(flakyThenOk(attempts), true, Set.of("POST"));
        DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();
        Flux<org.springframework.core.io.buffer.DataBuffer> body = Flux.defer(() ->
                Flux.just(bufferFactory.wrap("payload".getBytes(StandardCharsets.UTF_8))));

        StepVerifier.create(invoke(handler, "uploadPublisher", body))
                .expectNext("ok")
                .verifyComplete();

        assertThat(attempts).hasValue(2);
        assertThat(output.getOut())
                .contains("Retry configured for reactive HTTP client [test-client]")
                .contains("RetrySafetyClient#uploadPublisher")
                .contains("with non-repeatable request body [default]");
    }


    @Test
    void unsafeRetryWarningDoesNotLogWhenRetryOperatorIsNoop(CapturedOutput output) {
        AtomicInteger attempts = new AtomicInteger();
        ReactiveClientInvocationHandler handler = createHandler(
                flakyThenOk(attempts), true, Set.of("POST"), new NoopResilienceOperatorApplier());

        StepVerifier.create(invoke(handler, "createWithoutIdempotencyKey"))
                .expectError()
                .verify();

        assertThat(attempts).hasValue(1);
        assertThat(output.getOut()).doesNotContain("Unsafe retry configured");
    }

    @Test
    void unsafeRetryWarningDedupKeyIncludesOverloadedMethodSignature(CapturedOutput output) {
        ReactiveClientInvocationHandler handler = createHandler(ok(new AtomicInteger()), true, Set.of("POST"));

        StepVerifier.create(invokeOverloadedCreate(handler, String.class, "one"))
                .expectNext("ok")
                .verifyComplete();
        StepVerifier.create(invokeOverloadedCreate(handler, Integer.class, 1))
                .expectNext("ok")
                .verifyComplete();

        assertThat(output.getOut())
                .contains("RetrySafetyClient#create(java.lang.String)")
                .contains("RetrySafetyClient#create(java.lang.Integer)");
    }


    @Test
    void unsafeRetryWarningDedupKeyUsesQualifiedParameterTypeNames(CapturedOutput output) {
        ReactiveClientInvocationHandler handler = createHandler(ok(new AtomicInteger()), true, Set.of("POST"));

        StepVerifier.create(invokeOverloadedCreate(handler,
                        io.github.huynhngochuyhoang.httpstarter.core.fixture.alpha.Id.class,
                        new io.github.huynhngochuyhoang.httpstarter.core.fixture.alpha.Id("one")))
                .expectNext("ok")
                .verifyComplete();
        StepVerifier.create(invokeOverloadedCreate(handler,
                        io.github.huynhngochuyhoang.httpstarter.core.fixture.beta.Id.class,
                        new io.github.huynhngochuyhoang.httpstarter.core.fixture.beta.Id("two")))
                .expectNext("ok")
                .verifyComplete();

        assertThat(output.getOut())
                .contains("RetrySafetyClient#create(io.github.huynhngochuyhoang.httpstarter.core.fixture.alpha.Id)")
                .contains("RetrySafetyClient#create(io.github.huynhngochuyhoang.httpstarter.core.fixture.beta.Id)");
    }

    @Test
    void retrySafetyLogicDoesNotRunWhenResilienceIsDisabled(CapturedOutput output) {
        AtomicInteger attempts = new AtomicInteger();
        ReactiveClientInvocationHandler handler = createHandler(ok(attempts), false, Set.of("POST"));

        StepVerifier.create(invoke(handler, "createWithoutIdempotencyKey"))
                .expectNext("ok")
                .verifyComplete();

        assertThat(attempts).hasValue(1);
        assertThat(output.getOut()).doesNotContain("Unsafe retry configured");
    }

    private static WebClient flakyThenOk(AtomicInteger attempts) {
        return WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    if (attempts.incrementAndGet() == 1) {
                        return Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).body("boom").build());
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build());
                })
                .build();
    }

    private static WebClient ok(AtomicInteger attempts) {
        return WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    attempts.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build());
                })
                .build();
    }

    private static ReactiveClientInvocationHandler createHandler(WebClient webClient,
                                                                 boolean resilienceEnabled,
                                                                 Set<String> retryMethods) {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.ResilienceConfig resilience = new ReactiveHttpClientProperties.ResilienceConfig();
        resilience.setEnabled(resilienceEnabled);
        resilience.setRetry("default");
        resilience.setRetryMethods(retryMethods);
        config.setResilience(resilience);

        ApplicationContext appCtx = mock(ApplicationContext.class);
        ObjectProvider<io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver> observerProvider =
                mock(ObjectProvider.class);
        when(appCtx.getBeanProvider(io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver.class))
                .thenReturn(observerProvider);
        when(observerProvider.getIfAvailable()).thenReturn(null);

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ZERO)
                .build();

        return createHandler(
                webClient,
                resilienceEnabled,
                retryMethods,
                new Resilience4jOperatorApplier(null, RetryRegistry.of(retryConfig), null, null));
    }

    private static ReactiveClientInvocationHandler createHandler(WebClient webClient,
                                                                 boolean resilienceEnabled,
                                                                 Set<String> retryMethods,
                                                                 ResilienceOperatorApplier resilienceOperatorApplier) {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.ResilienceConfig resilience = new ReactiveHttpClientProperties.ResilienceConfig();
        resilience.setEnabled(resilienceEnabled);
        resilience.setRetry("default");
        resilience.setRetryMethods(retryMethods);
        config.setResilience(resilience);

        ApplicationContext appCtx = mock(ApplicationContext.class);
        ObjectProvider<io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver> observerProvider =
                mock(ObjectProvider.class);
        when(appCtx.getBeanProvider(io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver.class))
                .thenReturn(observerProvider);
        when(observerProvider.getIfAvailable()).thenReturn(null);

        return new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "test-client",
                appCtx,
                resilienceOperatorApplier,
                null,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invokeOverloadedCreate(ReactiveClientInvocationHandler handler, Class<?> parameterType, Object arg) {
        try {
            Method method = RetrySafetyClient.class.getMethod("create", parameterType);
            return (Mono<String>) handler.invoke(null, method, new Object[]{arg});
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invoke(ReactiveClientInvocationHandler handler, String methodName, Object... args) {
        try {
            Method method = switch (methodName) {
                case "createWithIdempotencyKey", "submitForm" -> RetrySafetyClient.class.getMethod(methodName, String.class);
                case "createJson" -> RetrySafetyClient.class.getMethod(methodName, Map.class);
                case "uploadPublisher" -> RetrySafetyClient.class.getMethod(methodName, Flux.class);
                case "uploadResource" -> RetrySafetyClient.class.getMethod(methodName, Resource.class);
                case "uploadObject" -> RetrySafetyClient.class.getMethod(methodName, Object.class);
                case "uploadInputStream" -> RetrySafetyClient.class.getMethod(methodName, InputStream.class);
                default -> RetrySafetyClient.class.getMethod(methodName);
            };
            return (Mono<String>) handler.invoke(null, method, args);
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    interface RetrySafetyClient {
        @GET("/safe")
        Mono<String> getSafe();

        @POST("/unsafe")
        Mono<String> createWithoutIdempotencyKey();

        @POST("/safe-write")
        Mono<String> createWithIdempotencyKey(@HeaderParam("Idempotency-Key") String idempotencyKey);

        @POST("/json")
        Mono<String> createJson(@Body Map<String, Object> body);

        @POST("/form")
        @MultipartBody
        Mono<String> submitForm(@FormField("name") String name);

        @POST("/resource")
        @MultipartBody
        Mono<String> uploadResource(@FormFile("file") Resource resource);

        @POST("/publisher")
        Mono<String> uploadPublisher(@Body Flux<org.springframework.core.io.buffer.DataBuffer> body);

        @POST("/object")
        Mono<String> uploadObject(@Body Object body);

        @POST("/input-stream")
        Mono<String> uploadInputStream(@Body InputStream body);

        @PUT("/replace")
        Mono<String> replaceIdempotently();

        @DELETE("/delete")
        Mono<String> deleteIdempotently();

        @POST("/create-string")
        Mono<String> create(String name);

        @POST("/create-integer")
        Mono<String> create(Integer id);

        @POST("/create-alpha-id")
        Mono<String> create(io.github.huynhngochuyhoang.httpstarter.core.fixture.alpha.Id id);

        @POST("/create-beta-id")
        Mono<String> create(io.github.huynhngochuyhoang.httpstarter.core.fixture.beta.Id id);
    }
}
