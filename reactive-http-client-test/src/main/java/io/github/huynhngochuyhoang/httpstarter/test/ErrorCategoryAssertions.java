package io.github.huynhngochuyhoang.httpstarter.test;

import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategories;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.github.huynhngochuyhoang.httpstarter.exception.HttpClientException;
import io.github.huynhngochuyhoang.httpstarter.exception.RemoteServiceException;
import org.assertj.core.api.AbstractThrowableAssert;
import reactor.core.publisher.Mono;

/**
 * Fluent assertion helpers tailored for the published error-category contract.
 * It bridges AssertJ's {@code AbstractThrowableAssert} with the library's
 * {@link ErrorCategory} so tests can write:
 *
 * <pre>{@code
 * ErrorCategoryAssertions.assertThatFails(client.getUser(42))
 *     .hasErrorCategory(ErrorCategory.RATE_LIMITED)
 *     .hasStatusCode(429);
 * }</pre>
 */
public final class ErrorCategoryAssertions {

    private ErrorCategoryAssertions() {}

    /**
     * Blocks until the publisher terminates with an error and returns a fluent
     * assertion anchored on that error. If the publisher completes successfully
     * (or emits any value) the assertion fails.
     */
    public static ErrorAssert assertThatFails(Mono<?> publisher) {
        Throwable captured = captureError(publisher);
        return new ErrorAssert(captured);
    }

    private static Throwable captureError(Mono<?> publisher) {
        java.util.concurrent.atomic.AtomicReference<Throwable> ref = new java.util.concurrent.atomic.AtomicReference<>();
        try {
            publisher.onErrorResume(t -> {
                ref.set(t);
                return Mono.empty();
            }).block();
        } catch (Throwable t) {
            ref.set(t);
        }
        Throwable captured = ref.get();
        if (captured == null) {
            throw new AssertionError("Expected the publisher to terminate with an error, but it completed successfully.");
        }
        return captured;
    }

    public static final class ErrorAssert extends AbstractThrowableAssert<ErrorAssert, Throwable> {
        ErrorAssert(Throwable actual) {
            super(actual, ErrorAssert.class);
        }

        /** Asserts that the error resolves to the expected published error category. */
        public ErrorAssert hasErrorCategory(ErrorCategory expected) {
            isNotNull();
            ErrorCategory actualCategory = extractCategory(actual);
            if (actualCategory != expected) {
                throw new AssertionError(
                        "expected errorCategory " + expected + " but was " + actualCategory + " on " + actual);
            }
            return myself;
        }

        /** Asserts that the error carries the given HTTP status code. */
        public ErrorAssert hasStatusCode(int expected) {
            isNotNull();
            int actualStatus = extractStatusCode(actual);
            if (actualStatus != expected) {
                throw new AssertionError(
                        "expected HTTP status " + expected + " but was " + actualStatus + " on " + actual);
            }
            return myself;
        }

        private static ErrorCategory extractCategory(Throwable t) {
            return ErrorCategories.from(t);
        }

        private static int extractStatusCode(Throwable t) {
            if (t instanceof HttpClientException http) return http.getStatusCode();
            if (t instanceof RemoteServiceException remote) return remote.getStatusCode();
            throw new AssertionError(
                    "expected HttpClientException or RemoteServiceException carrying statusCode, but was "
                            + t.getClass().getName());
        }
    }
}
