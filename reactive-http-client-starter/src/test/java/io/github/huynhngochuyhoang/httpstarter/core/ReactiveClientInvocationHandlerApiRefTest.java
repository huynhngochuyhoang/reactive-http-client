package io.github.huynhngochuyhoang.httpstarter.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ReactiveClientInvocationHandlerApiRefTest {

    @Test
    void shouldResolveMethodAndPathFromApiMap() throws Exception {
        ReactiveHttpClientProperties.ClientConfig clientConfig = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.ApiConfig api = new ReactiveHttpClientProperties.ApiConfig();
        api.setMethod(" get ");
        api.setPath("/users/{id}");
        api.setTimeoutMs(1200);
        clientConfig.setApis(Map.of("user.getById", api));

        ReactiveClientInvocationHandler handler = createHandler(clientConfig);
        Method method = ApiRefClient.class.getMethod("call");
        MethodMetadata metadata = new MethodMetadataCache().get(method);

        Object effectiveApi = resolveEffectiveApi(handler, method, metadata);
        assertEquals("GET", invokeAccessor(effectiveApi, "httpMethod"));
        assertEquals("/users/{id}", invokeAccessor(effectiveApi, "pathTemplate"));
        assertEquals(1200L, invokeAccessor(effectiveApi, "timeoutMs"));
    }

    @Test
    void shouldRejectMissingApiMapEntryForApiRef() throws Exception {
        ReactiveHttpClientProperties.ClientConfig clientConfig = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveClientInvocationHandler handler = createHandler(clientConfig);
        Method method = ApiRefClient.class.getMethod("call");
        MethodMetadata metadata = new MethodMetadataCache().get(method);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> resolveEffectiveApi(handler, method, metadata));
        assertTrue(ex.getMessage().contains("is not configured."));
    }

    @Test
    void shouldUseMethodAnnotationsWhenApiRefIsAbsent() throws Exception {
        ReactiveClientInvocationHandler handler = createHandler(new ReactiveHttpClientProperties.ClientConfig());
        Method method = AnnotationClient.class.getMethod("call");
        MethodMetadata metadata = new MethodMetadataCache().get(method);

        Object effectiveApi = resolveEffectiveApi(handler, method, metadata);
        assertEquals("GET", invokeAccessor(effectiveApi, "httpMethod"));
        assertEquals("/legacy", invokeAccessor(effectiveApi, "pathTemplate"));
    }

    @Test
    void inheritedApiRefResolvesAgainstEachConcreteClientApiMap() throws Exception {
        ReactiveHttpClientProperties.ClientConfig internalConfig = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.ApiConfig internalApi = new ReactiveHttpClientProperties.ApiConfig();
        internalApi.setMethod("GET");
        internalApi.setPath("/internal-users/{id}");
        internalApi.setTimeoutMs(1000);
        internalConfig.setApis(Map.of("user.lookup", internalApi));

        ReactiveHttpClientProperties.ClientConfig partnerConfig = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.ApiConfig partnerApi = new ReactiveHttpClientProperties.ApiConfig();
        partnerApi.setMethod("DELETE");
        partnerApi.setPath("/partner-users/{id}");
        partnerApi.setTimeoutMs(2000);
        partnerConfig.setApis(Map.of("user.lookup", partnerApi));

        Method internalMethod = InternalInheritedApiRefClient.class.getMethod("lookup", String.class);
        Method partnerMethod = PartnerInheritedApiRefClient.class.getMethod("lookup", String.class);
        MethodMetadataCache cache = new MethodMetadataCache();

        Object internalEffectiveApi = resolveEffectiveApi(
                createHandler(internalConfig, "internal-inherited-api-ref-client"),
                internalMethod,
                cache.get(internalMethod));
        Object partnerEffectiveApi = resolveEffectiveApi(
                createHandler(partnerConfig, "partner-inherited-api-ref-client"),
                partnerMethod,
                cache.get(partnerMethod));

        assertEquals("GET", invokeAccessor(internalEffectiveApi, "httpMethod"));
        assertEquals("/internal-users/{id}", invokeAccessor(internalEffectiveApi, "pathTemplate"));
        assertEquals(1000L, invokeAccessor(internalEffectiveApi, "timeoutMs"));
        assertEquals("DELETE", invokeAccessor(partnerEffectiveApi, "httpMethod"));
        assertEquals("/partner-users/{id}", invokeAccessor(partnerEffectiveApi, "pathTemplate"));
        assertEquals(2000L, invokeAccessor(partnerEffectiveApi, "timeoutMs"));
    }

    @Test
    void inheritedApiRefKeepsApiNamePrecedenceForObservability() throws Exception {
        Method apiRefMethod = InternalInheritedApiRefClient.class.getMethod("lookup", String.class);
        Method namedMethod = NamedInheritedApiRefClient.class.getMethod("lookup", String.class);
        MethodMetadataCache cache = new MethodMetadataCache();

        MethodMetadata apiRefMetadata = cache.get(apiRefMethod);
        MethodMetadata namedMetadata = cache.get(namedMethod);

        assertEquals("user.lookup", apiRefMetadata.getApiRefName());
        assertEquals("user.lookup", apiRefMetadata.getApiName());
        assertEquals("user.lookup", namedMetadata.getApiRefName());
        assertEquals("user.lookup.explicit", namedMetadata.getApiName());
    }

    private static Object resolveEffectiveApi(ReactiveClientInvocationHandler handler, Method method, MethodMetadata metadata) throws Exception {
        Method resolve = ReactiveClientInvocationHandler.class.getDeclaredMethod("resolveEffectiveApi", Method.class, MethodMetadata.class);
        resolve.setAccessible(true);
        try {
            return resolve.invoke(handler, method, metadata);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    private static Object invokeAccessor(Object target, String accessorName) throws Exception {
        Method accessor = target.getClass().getDeclaredMethod(accessorName);
        accessor.setAccessible(true);
        return accessor.invoke(target);
    }

    private static ReactiveClientInvocationHandler createHandler(ReactiveHttpClientProperties.ClientConfig clientConfig) {
        return createHandler(clientConfig, "test-client");
    }

    private static ReactiveClientInvocationHandler createHandler(
            ReactiveHttpClientProperties.ClientConfig clientConfig,
            String clientName) {
        return new ReactiveClientInvocationHandler(
                WebClient.builder().baseUrl("http://localhost").build(),
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                clientConfig,
                clientName,
                mock(ApplicationContext.class),
                new NoopResilienceOperatorApplier(),
                mock(ObjectMapper.class),
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );
    }

    interface ApiRefClient {
        @ApiRef("user.getById")
        Mono<String> call();
    }

    interface AnnotationClient {
        @GET("/legacy")
        Mono<String> call();
    }

    interface InheritedApiRefOperations {
        @ApiRef("user.lookup")
        Mono<String> lookup(@PathVar("id") String id);
    }

    @ReactiveHttpClient(name = "internal-inherited-api-ref-client")
    interface InternalInheritedApiRefClient extends InheritedApiRefOperations {
    }

    @ReactiveHttpClient(name = "partner-inherited-api-ref-client")
    interface PartnerInheritedApiRefClient extends InheritedApiRefOperations {
    }

    interface NamedInheritedApiRefOperations {
        @ApiName("user.lookup.explicit")
        @ApiRef("user.lookup")
        Mono<String> lookup(@PathVar("id") String id);
    }

    @ReactiveHttpClient(name = "named-inherited-api-ref-client")
    interface NamedInheritedApiRefClient extends NamedInheritedApiRefOperations {
    }

}
