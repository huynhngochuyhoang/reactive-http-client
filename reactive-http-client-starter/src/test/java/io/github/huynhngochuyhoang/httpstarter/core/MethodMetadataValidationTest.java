package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class MethodMetadataValidationTest {

    @Test
    void shouldRejectNonReactiveReturnType() throws Exception {
        Method method = InvalidReturnTypeClient.class.getMethod("call");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new MethodMetadataCache().get(method));
        assertTrue(ex.getMessage().contains("must return Mono<T> or Flux<T>"));
    }

    @Test
    void shouldParsePatchAnnotation() throws Exception {
        Method method = PatchClient.class.getMethod("patch");
        MethodMetadata metadata = new MethodMetadataCache().get(method);

        assertEquals("PATCH", metadata.getHttpMethod());
        assertEquals("/items/1", metadata.getPathTemplate());
    }

    @Test
    void shouldFreezeMetadataCollectionsAfterParsing() throws Exception {
        Method method = PatchClient.class.getMethod("patch");
        MethodMetadata metadata = new MethodMetadataCache().get(method);

        assertThrows(UnsupportedOperationException.class, () -> metadata.getPathVars().put(0, "id"));
        assertThrows(UnsupportedOperationException.class, () -> metadata.getQueryParams().put(0, "q"));
        assertThrows(UnsupportedOperationException.class, () -> metadata.getHeaderParams().put(0, "X-Test"));
        assertThrows(UnsupportedOperationException.class, () -> metadata.getHeaderMapParams().add(0));
    }

    @Test
    void shouldBuildImmutableRequestPlanAfterParsing() throws Exception {
        Method method = RequestPlanClient.class.getMethod("create", String.class, String.class, String.class, String.class);
        MethodMetadata metadata = new MethodMetadataCache().get(method);
        RequestPlan plan = metadata.getRequestPlan();

        assertEquals(method, plan.method());
        assertEquals("create", plan.apiName());
        assertEquals("POST", plan.httpMethod());
        assertEquals("/items/{id}", plan.pathTemplate());
        assertEquals(0, plan.pathVars().get(0).argumentIndex());
        assertEquals("id", plan.pathVars().get(0).name());
        assertEquals(1, plan.queryParams().get(0).argumentIndex());
        assertEquals("locale", plan.queryParams().get(0).name());
        assertEquals(2, plan.headerParams().get(0).argumentIndex());
        assertEquals("X-Tenant", plan.headerParams().get(0).name());
        assertEquals(3, plan.bodyIndex());

        assertThrows(UnsupportedOperationException.class,
                () -> plan.pathVars().add(new RequestPlan.NamedArgumentBinding(9, "other")));
        assertThrows(UnsupportedOperationException.class,
                () -> plan.headerMapParams().add(9));
    }

    @Test
    void shouldParseMethodLevelLogHttpExchange() throws Exception {
        Method method = MethodLoggedClient.class.getMethod("call");
        MethodMetadata metadata = new MethodMetadataCache().get(method);

        assertTrue(metadata.isHttpExchangeLoggingEnabled());
        assertEquals(OverrideTestExchangeLogger.class, metadata.getHttpExchangeLoggerClass());
    }

    @Test
    void shouldNotParseClientLevelLogHttpExchangeIntoSharedMethodMetadata() throws Exception {
        Method method = ClassLevelLoggedClient.class.getMethod("call");
        MethodMetadata metadata = new MethodMetadataCache().get(method);

        assertFalse(metadata.isHttpExchangeLoggingEnabled());
    }

    @Test
    void shouldParseApiRefAnnotation() throws Exception {
        Method method = ApiRefClient.class.getMethod("call");
        MethodMetadata metadata = new MethodMetadataCache().get(method);

        assertEquals("user.getById", metadata.getApiRefName());
        assertEquals("user.getById", metadata.getApiName());
        assertNull(metadata.getHttpMethod());
    }

    @Test
    void shouldPreferApiNameOverApiRefForObservabilityName() throws Exception {
        Method method = ApiNameAndRefClient.class.getMethod("call");
        MethodMetadata metadata = new MethodMetadataCache().get(method);

        assertEquals("user.getById", metadata.getApiRefName());
        assertEquals("user.lookup", metadata.getApiName());
    }

    @Test
    void shouldRejectApiRefCombinedWithHttpVerb() throws Exception {
        Method method = InvalidApiRefClient.class.getMethod("call");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new MethodMetadataCache().get(method));
        assertTrue(ex.getMessage().contains("@ApiRef cannot be combined"));
    }

    interface InvalidReturnTypeClient {
        @GET("/items")
        String call();
    }

    interface PatchClient {
        @PATCH("/items/1")
        Mono<String> patch();
    }

    interface RequestPlanClient {
        @POST("/items/{id}")
        Mono<String> create(
                @PathVar("id") String id,
                @QueryParam("locale") String locale,
                @HeaderParam("X-Tenant") String tenant,
                @Body String body);
    }

    @LogHttpExchange(logger = DefaultHttpExchangeLogger.class)
    interface ClassLevelLoggedClient {
        @GET("/items")
        Mono<String> call();
    }

    interface MethodLoggedClient {
        @GET("/items/override")
        @LogHttpExchange(logger = OverrideTestExchangeLogger.class)
        Mono<String> call();
    }

    interface ApiRefClient {
        @ApiRef("user.getById")
        Mono<String> call();
    }

    interface ApiNameAndRefClient {
        @ApiName("user.lookup")
        @ApiRef("user.getById")
        Mono<String> call();
    }

    interface InvalidApiRefClient {
        @GET("/items")
        @ApiRef("user.getById")
        Mono<String> call();
    }

    static final class OverrideTestExchangeLogger implements HttpExchangeLogger {
        @Override
        public void log(HttpExchangeLogContext context) {
            // no-op
        }
    }
}
