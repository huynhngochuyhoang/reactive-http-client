package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.HeaderParam;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

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
        assertEquals(List.of("trace-123"), resolved.headers().get("X-Trace-Id"));
        assertEquals(List.of("tenant-a"), resolved.headers().get("X-Tenant"));
        assertFalse(resolved.headers().containsKey(""));
        assertFalse(resolved.headers().containsKey("X-Null"));
        assertTrue(resolved.headers().keySet().stream().noneMatch(Objects::isNull));
    }

    @Test
    void shouldResolveMultiValueHeaderParamsAndMapEntriesInOrder() {
        MethodMetadata meta = new MethodMetadata();
        meta.getHeaderParams().put(0, "X-Tag");
        meta.getHeaderParams().put(1, "X-Mode");
        meta.getHeaderMapParams().add(2);

        Map<String, Object> dynamicHeaders = new LinkedHashMap<>();
        dynamicHeaders.put("X-Map", java.util.Arrays.asList("one", null, "two"));
        dynamicHeaders.put("X-Array", new int[]{1, 2});
        dynamicHeaders.put("X-Only-Nulls", java.util.Arrays.asList(null, null));

        RequestArgumentResolver.ResolvedArgs resolved = new RequestArgumentResolver()
                .resolve(meta, new Object[]{java.util.Arrays.asList("alpha", null, "beta"),
                        new String[]{"fast", "safe"}, dynamicHeaders});

        assertEquals(List.of("alpha", "beta"), resolved.headers().get("X-Tag"));
        assertEquals(List.of("fast", "safe"), resolved.headers().get("X-Mode"));
        assertEquals(List.of("one", "two"), resolved.headers().get("X-Map"));
        assertEquals(List.of("1", "2"), resolved.headers().get("X-Array"));
        assertFalse(resolved.headers().containsKey("X-Only-Nulls"));
    }

    @Test
    void shouldRejectControlCharactersInExpandedHeaderValues() {
        MethodMetadata meta = new MethodMetadata();
        meta.getHeaderParams().put(0, "X-Tag");

        RequestArgumentResolver resolver = new RequestArgumentResolver();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(meta, new Object[]{List.of("safe", "bad\nvalue")}));
        assertTrue(ex.getMessage().contains("Invalid header value"));
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

    @Test
    void shouldRejectNamedHeaderParamForMapParameter() throws Exception {
        Method method = InvalidMapClient.class.getMethod("get", Map.class);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new MethodMetadataCache().get(method));
        assertTrue(ex.getMessage().contains("must be blank for Map parameter"));
    }

    @Test
    void shouldRejectHeaderValuesContainingControlCharacters() {
        MethodMetadata meta = new MethodMetadata();
        meta.getHeaderParams().put(0, "Authorization");

        RequestArgumentResolver resolver = new RequestArgumentResolver();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(meta, new Object[]{"Bearer abc\r\nX-Evil: 1"}));
        assertTrue(ex.getMessage().contains("Invalid header value"));
    }

    @Test
    void shouldRejectCaseInsensitiveDynamicHeaderCollisionsInsteadOfOverwriting() throws Exception {
        Method method = DynamicCollisionClient.class.getMethod("get", String.class, Map.class);
        MethodMetadata metadata = new MethodMetadataCache().get(method);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new RequestArgumentResolver().resolve(
                        RequestPlan.from(metadata, DynamicCollisionClient.class),
                        new Object[]{"named", Map.of("x-tenant", "dynamic")}));

        assertTrue(ex.getMessage().contains("Duplicate outbound header 'x-tenant'"));
        assertTrue(ex.getMessage().contains("X-Tenant"));
        assertTrue(ex.getMessage().contains(DynamicCollisionClient.class.getName()));
    }

    interface ValidClient {
        @GET("/users")
        Mono<String> get(@HeaderParam Map<String, String> headers, @HeaderParam("X-Tenant") String tenant);
    }

    interface InvalidClient {
        @GET("/users")
        Mono<String> get(@HeaderParam String tenant);
    }

    interface InvalidMapClient {
        @GET("/users")
        Mono<String> get(@HeaderParam("X-Tenant") Map<String, String> headers);
    }

    interface DynamicCollisionClient {
        @GET("/users")
        Mono<String> get(@HeaderParam("X-Tenant") String tenant,
                         @HeaderParam Map<String, String> headers);
    }
}
