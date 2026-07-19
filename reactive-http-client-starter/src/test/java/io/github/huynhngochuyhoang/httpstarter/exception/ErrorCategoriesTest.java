package io.github.huynhngochuyhoang.httpstarter.exception;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.codec.DecodingException;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCategoriesTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("categoryCases")
    void extractsPublishedErrorCategoryContract(String label, Throwable error, Integer statusCode, ErrorCategory expected) {
        assertThat(ErrorCategories.from(error, statusCode)).isEqualTo(expected);
    }

    @Test
    void usesStatusCodeWhenThrowableDoesNotCarryCategory() {
        assertThat(ErrorCategories.from(new RuntimeException("boom"), 404))
                .isEqualTo(ErrorCategory.CLIENT_ERROR);
        assertThat(ErrorCategories.from(new RuntimeException("boom"), 429))
                .isEqualTo(ErrorCategory.RATE_LIMITED);
        assertThat(ErrorCategories.from(new RuntimeException("boom"), 500))
                .isEqualTo(ErrorCategory.SERVER_ERROR);
    }

    @Test
    void reportsDecodeErrorForSuccessfulResponseDecodeFailure() {
        assertThat(ErrorCategories.from(new RuntimeException(new DecodingException("bad json")), 200))
                .isEqualTo(ErrorCategory.RESPONSE_DECODE_ERROR);
    }

    @Test
    void exposesStatusOnlyAndPredicateHelpers() {
        assertThat(ErrorCategories.fromStatusCode(503)).isEqualTo(ErrorCategory.SERVER_ERROR);
        assertThat(ErrorCategories.fromStatusCode(204)).isNull();
        assertThat(ErrorCategories.is(new HttpClientException(429, ""), ErrorCategory.RATE_LIMITED)).isTrue();
    }

    @Test
    void returnsUnknownForUnclassifiedErrorsAndNullForNoError() {
        assertThat(ErrorCategories.from(new RuntimeException("boom"))).isEqualTo(ErrorCategory.UNKNOWN);
        assertThat(ErrorCategories.from(null)).isNull();
        assertThat(ErrorCategories.from(null, null)).isNull();
    }

    @Test
    void preservesNearestActionableCategoryThroughWrappers() {
        AuthProviderException authTimeout = new AuthProviderException(
                "payments-client", ReadTimeoutException.INSTANCE);

        assertThat(ErrorCategories.from(new RuntimeException("retry exhausted", authTimeout)))
                .isEqualTo(ErrorCategory.AUTH_PROVIDER_ERROR);
        assertThat(ErrorCategories.from(new RuntimeException(
                "retry exhausted", new HttpClientException(429, "rate limited"))))
                .isEqualTo(ErrorCategory.RATE_LIMITED);
    }

    @Test
    void stopsCauseTraversalAtPublishedBound() {
        Throwable withinBound = wrapped(new UnknownHostException("missing.local"), 15);
        Throwable beyondBound = wrapped(new UnknownHostException("missing.local"), 16);

        assertThat(ErrorCategories.from(withinBound)).isEqualTo(ErrorCategory.UNKNOWN_HOST);
        assertThat(ErrorCategories.from(beyondBound)).isEqualTo(ErrorCategory.UNKNOWN);
    }

    @Test
    void doesNotLinkOptionalResilience4jClassesDirectly() throws IOException {
        String classFile = ErrorCategories.class.getSimpleName() + ".class";
        try (InputStream input = ErrorCategories.class.getResourceAsStream(classFile)) {
            assertThat(input).isNotNull();
            assertThat(new String(input.readAllBytes(), StandardCharsets.ISO_8859_1))
                    .doesNotContain("io/github/resilience4j");
        }
    }

    private static Throwable wrapped(Throwable cause, int layers) {
        Throwable current = cause;
        for (int i = 0; i < layers; i++) {
            current = new RuntimeException("layer " + i, current);
        }
        return current;
    }

    private static Stream<Object[]> categoryCases() {
        return Stream.of(
                new Object[]{"HTTP 429", new HttpClientException(429, ""), null, ErrorCategory.RATE_LIMITED},
                new Object[]{"HTTP 4xx", new HttpClientException(400, ""), null, ErrorCategory.CLIENT_ERROR},
                new Object[]{"HTTP 5xx", new RemoteServiceException(503, ""), null, ErrorCategory.SERVER_ERROR},
                new Object[]{"status-only 429", new RuntimeException("boom"), 429, ErrorCategory.RATE_LIMITED},
                new Object[]{"status-only 4xx", new RuntimeException("boom"), 404, ErrorCategory.CLIENT_ERROR},
                new Object[]{"status-only 5xx", new RuntimeException("boom"), 500, ErrorCategory.SERVER_ERROR},
                new Object[]{"decode on 2xx", new RuntimeException(new DecodingException("bad json")), 200, ErrorCategory.RESPONSE_DECODE_ERROR},
                new Object[]{"timeout", new RuntimeException(new TimeoutException()), null, ErrorCategory.TIMEOUT},
                new Object[]{"netty read timeout", new RuntimeException(ReadTimeoutException.INSTANCE), null, ErrorCategory.TIMEOUT},
                new Object[]{"cancelled", new RuntimeException(new CancellationException()), null, ErrorCategory.CANCELLED},
                new Object[]{"auth provider", new RuntimeException(new AuthProviderException("test-client", "boom")), null, ErrorCategory.AUTH_PROVIDER_ERROR},
                new Object[]{"DNS", new RuntimeException(new UnknownHostException("missing.local")), null, ErrorCategory.UNKNOWN_HOST},
                new Object[]{"connect", new RuntimeException(new ConnectException("refused")), null, ErrorCategory.CONNECT_ERROR},
                new Object[]{"TLS", new RuntimeException(new SSLHandshakeException("bad cert")), null, ErrorCategory.TLS_ERROR},
                new Object[]{"circuit breaker", new RuntimeException(CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("orders"))), null, ErrorCategory.RESILIENCE_ERROR},
                new Object[]{"bulkhead", new RuntimeException(BulkheadFullException.createBulkheadFullException(Bulkhead.ofDefaults("orders"))), null, ErrorCategory.RESILIENCE_ERROR},
                new Object[]{"rate limiter", new RuntimeException(RequestNotPermitted.createRequestNotPermitted(RateLimiter.ofDefaults("orders"))), null, ErrorCategory.RESILIENCE_ERROR}
        );
    }
}
