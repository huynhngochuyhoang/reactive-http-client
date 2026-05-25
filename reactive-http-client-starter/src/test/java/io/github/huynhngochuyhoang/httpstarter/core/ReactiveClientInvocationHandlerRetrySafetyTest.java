package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.HeaderParam;
import io.github.huynhngochuyhoang.httpstarter.annotation.POST;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.time.Duration;
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

        return new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "test-client",
                appCtx,
                new Resilience4jOperatorApplier(null, RetryRegistry.of(retryConfig), null, null),
                null,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invoke(ReactiveClientInvocationHandler handler, String methodName, Object... args) {
        try {
            Method method = switch (methodName) {
                case "createWithIdempotencyKey" -> RetrySafetyClient.class.getMethod(methodName, String.class);
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
    }
}
