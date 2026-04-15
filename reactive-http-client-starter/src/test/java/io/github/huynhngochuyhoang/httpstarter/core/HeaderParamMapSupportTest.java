package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.HeaderParam;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeaderParamMapSupportTest {

    @Test
    void shouldResolveHeaderMapEntries() {
        MethodMetadata meta = new MethodMetadata();
        meta.getHeaderMapParams().add(0);
        meta.getHeaderParams().put(1, "X-Tenant");

        Map<String, Object> dynamicHeaders = new LinkedHashMap<>();
        dynamicHeaders.put("X-Trace-Id", "trace-123");
        dynamicHeaders.put("", "blank-key");
        dynamicHeaders.put("X-Null", null);
        dynamicHeaders.put(null, "null-key");

        RequestArgumentResolver resolver = new RequestArgumentResolver();
        RequestArgumentResolver.ResolvedArgs resolved = resolver.resolve(meta, new Object[]{dynamicHeaders, "tenant-a"});

        assertEquals(2, resolved.headers().size());
        assertEquals("trace-123", resolved.headers().get("X-Trace-Id"));
        assertEquals("tenant-a", resolved.headers().get("X-Tenant"));
        assertFalse(resolved.headers().containsKey(""));
        assertFalse(resolved.headers().containsKey("X-Null"));
        assertTrue(resolved.headers().keySet().stream().noneMatch(Objects::isNull));
    }

    @Test
    void shouldParseHeaderParamMapMetadata() throws Exception {
        Method method = ValidClient.class.getMethod("get", Map.class, String.class);
        MethodMetadata meta = new MethodMetadataCache().get(method);

        assertTrue(meta.getHeaderMapParams().contains(0));
        assertEquals("X-Tenant", meta.getHeaderParams().get(1));
    }

    @Test
    void shouldRejectBlankHeaderNameForScalarParam() throws Exception {
        Method method = InvalidClient.class.getMethod("get", String.class);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new MethodMetadataCache().get(method));
        assertTrue(ex.getMessage().contains("must not be blank"));
    }

    interface ValidClient {
        @GET("/users")
        Mono<String> get(@HeaderParam Map<String, String> headers, @HeaderParam("X-Tenant") String tenant);
    }

    interface InvalidClient {
        @GET("/users")
        Mono<String> get(@HeaderParam String tenant);
    }
}
