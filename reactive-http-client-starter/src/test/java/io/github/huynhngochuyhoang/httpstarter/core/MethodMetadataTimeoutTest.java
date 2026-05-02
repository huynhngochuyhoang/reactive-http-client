package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.TimeoutMs;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MethodMetadataTimeoutTest {

    @Test
    void shouldParseMethodTimeoutOverride() throws Exception {
        Method method = TimeoutClient.class.getMethod("getUser");

        MethodMetadata metadata = new MethodMetadataCache().get(method);

        assertEquals(1500, metadata.getTimeoutMs());
    }

    @Test
    void shouldUseUnsetTimeoutWhenAnnotationIsAbsent() throws Exception {
        Method method = DefaultClient.class.getMethod("getUser");

        MethodMetadata metadata = new MethodMetadataCache().get(method);

        assertEquals(MethodMetadata.TIMEOUT_NOT_SET, metadata.getTimeoutMs());
    }

    @Test
    void shouldRejectNegativeTimeout() throws Exception {
        Method method = InvalidClient.class.getMethod("getUser");

        assertThrows(IllegalArgumentException.class, () -> new MethodMetadataCache().get(method));
    }

    @Test
    void shouldRejectTimeoutAboveThirtyMinutes() throws Exception {
        Method method = ExcessiveTimeoutClient.class.getMethod("getUser");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new MethodMetadataCache().get(method));
        assertTrue(ex.getMessage().contains("30 minutes"),
                "Error message should mention the 30-minute maximum");
    }

    @Test
    void shouldAcceptTimeoutAtExactThirtyMinutes() throws Exception {
        Method method = MaxTimeoutClient.class.getMethod("getUser");

        MethodMetadata metadata = new MethodMetadataCache().get(method);

        assertEquals(30L * 60 * 1000, metadata.getTimeoutMs());
    }

    interface TimeoutClient {
        @GET("/users/1")
        @TimeoutMs(1500)
        Mono<String> getUser();
    }

    interface DefaultClient {
        @GET("/users/1")
        Mono<String> getUser();
    }

    interface InvalidClient {
        @GET("/users/1")
        @TimeoutMs(-1)
        Mono<String> getUser();
    }

    interface ExcessiveTimeoutClient {
        @GET("/users/1")
        @TimeoutMs(Long.MAX_VALUE)
        Mono<String> getUser();
    }

    interface MaxTimeoutClient {
        @GET("/users/1")
        @TimeoutMs(30L * 60 * 1000)
        Mono<String> getUser();
    }
}
