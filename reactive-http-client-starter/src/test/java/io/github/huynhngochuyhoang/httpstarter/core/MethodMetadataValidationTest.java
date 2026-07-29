package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.channels.ReadableByteChannel;
import java.util.List;

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
    void shouldParseHeadAndOptionsAnnotations() throws Exception {
        Method head = HeadOptionsClient.class.getMethod("head", String.class);
        Method options = HeadOptionsClient.class.getMethod("options");

        MethodMetadata headMetadata = new MethodMetadataCache().get(head);
        MethodMetadata optionsMetadata = new MethodMetadataCache().get(options);

        assertEquals("HEAD", headMetadata.getHttpMethod());
        assertEquals("/objects/{id}", headMetadata.getPathTemplate());
        assertEquals("HEAD", headMetadata.getRequestPlan().httpMethod());
        assertEquals("id", headMetadata.getRequestPlan().pathVars().get(0).name());
        assertEquals(250, headMetadata.getTimeoutMs());
        assertEquals("head-retry", headMetadata.getRetryInstanceName());
        assertTrue(headMetadata.isHttpExchangeLoggingEnabled());

        assertEquals("OPTIONS", optionsMetadata.getHttpMethod());
        assertEquals("/objects", optionsMetadata.getPathTemplate());
        assertEquals("OPTIONS", optionsMetadata.getRequestPlan().httpMethod());
        assertEquals(300, optionsMetadata.getTimeoutMs());
        assertEquals("options-retry", optionsMetadata.getRetryInstanceName());
        assertEquals("options-cb", optionsMetadata.getCircuitBreakerInstanceName());
        assertTrue(optionsMetadata.isHttpExchangeLoggingEnabled());
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

    @Test
    void shouldRejectApiRefCombinedWithHeadOrOptions() throws Exception {
        Method head = InvalidHeadApiRefClient.class.getMethod("call");
        Method options = InvalidOptionsApiRefClient.class.getMethod("call");

        IllegalStateException headEx = assertThrows(IllegalStateException.class, () -> new MethodMetadataCache().get(head));
        IllegalStateException optionsEx = assertThrows(IllegalStateException.class, () -> new MethodMetadataCache().get(options));

        assertTrue(headEx.getMessage().contains("@ApiRef cannot be combined with @HEAD"));
        assertTrue(optionsEx.getMessage().contains("@ApiRef cannot be combined with @OPTIONS"));
    }


    @Test
    void shouldRejectMissingEndpointAnnotation() throws Exception {
        Method method = MissingEndpointClient.class.getMethod("call");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new MethodMetadataCache().get(method));

        assertTrue(ex.getMessage().contains("must declare an HTTP verb annotation or @ApiRef"));
        assertTrue(ex.getMessage().contains(method.toString()));
    }

    @Test
    void shouldRejectBlankPathVarName() throws Exception {
        Method method = BlankPathVarClient.class.getMethod("call", String.class);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new MethodMetadataCache().get(method));

        assertTrue(ex.getMessage().contains("@PathVar value must not be blank"));
        assertTrue(ex.getMessage().contains(method.toString()));
    }

    @Test
    void shouldRejectBlankQueryParamName() throws Exception {
        Method method = BlankQueryParamClient.class.getMethod("call", String.class);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new MethodMetadataCache().get(method));

        assertTrue(ex.getMessage().contains("@QueryParam value must not be blank"));
        assertTrue(ex.getMessage().contains(method.toString()));
    }

    @Test
    void shouldRejectBlankApiName() throws Exception {
        Method method = BlankApiNameClient.class.getMethod("call");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new MethodMetadataCache().get(method));

        assertTrue(ex.getMessage().contains("@ApiName value must not be blank"));
        assertTrue(ex.getMessage().contains(method.toString()));
    }

    @Test
    void shouldRejectMultipleHttpVerbAnnotations() throws Exception {
        Method method = AmbiguousVerbClient.class.getMethod("call");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new MethodMetadataCache().get(method));

        assertTrue(ex.getMessage().contains("Multiple HTTP verb annotations"));
        assertTrue(ex.getMessage().contains("@GET"));
        assertTrue(ex.getMessage().contains("@POST"));
        assertTrue(ex.getMessage().contains(method.toString()));
    }

    @Test
    void shouldRejectMultipleBodyParameters() throws Exception {
        Method method = MultipleBodyClient.class.getMethod("create", String.class, String.class);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new MethodMetadataCache().get(method));

        assertTrue(ex.getMessage().contains("Multiple @Body parameters"));
        assertTrue(ex.getMessage().contains(method.toString()));
        assertTrue(ex.getMessage().contains("indexes 0 and 1"));
    }

    @Test
    void shouldRejectInvalidMetadataDeterministicallyOnRepeatedParse() throws Exception {
        Method method = MultipleBodyClient.class.getMethod("create", String.class, String.class);
        MethodMetadataCache cache = new MethodMetadataCache();

        IllegalStateException first = assertThrows(IllegalStateException.class, () -> cache.get(method));
        IllegalStateException second = assertThrows(IllegalStateException.class, () -> cache.get(method));

        assertEquals(first.getMessage(), second.getMessage());
    }

    @Test
    void shouldCacheValidInheritedAndOverloadedMethodsIndependently() throws Exception {
        Method inherited = ChildOverloadedClient.class.getMethod("get", String.class);
        Method overloaded = ChildOverloadedClient.class.getMethod("get", Long.class);
        MethodMetadataCache cache = new MethodMetadataCache();

        MethodMetadata inheritedMetadata = cache.get(inherited);
        MethodMetadata overloadedMetadata = cache.get(overloaded);

        assertSame(inheritedMetadata, cache.get(inherited));
        assertSame(overloadedMetadata, cache.get(overloaded));
        assertNotSame(inheritedMetadata, overloadedMetadata);
        assertEquals("/parent/{id}", inheritedMetadata.getPathTemplate());
        assertEquals("/child/{id}", overloadedMetadata.getPathTemplate());
    }

    @Test
    void shouldClassifyUncertainAndStreamBodyDeclarationsAsApplicationOwned() throws Exception {
        MethodMetadataCache cache = new MethodMetadataCache();

        for (String methodName : List.of("objectBody", "inputStreamBody", "readerBody", "channelBody")) {
            Method method = java.util.Arrays.stream(BodyOwnershipClient.class.getMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow();
            assertEquals(RequestBodyRepeatability.APPLICATION_OWNED,
                    RequestPlan.from(cache.get(method), BodyOwnershipClient.class).bodyRepeatability());
        }
        Method generic = GenericBodyClient.class.getMethod("genericBody", Object.class);
        assertEquals(RequestBodyRepeatability.APPLICATION_OWNED,
                RequestPlan.from(cache.get(generic), GenericBodyClient.class).bodyRepeatability());
    }

    @Test
    void shouldResolveInheritedGenericEndpointTypesForConcreteClient() throws Exception {
        MethodMetadataCache cache = new MethodMetadataCache();
        Method busGet = BusApiOperators.class.getMethod("getOrder", String.class);
        Method trainGet = TrainApiOperators.class.getMethod("getOrder", String.class);

        MethodMetadata busMetadata = cache.get(busGet);
        MethodMetadata trainMetadata = cache.get(trainGet);

        assertSame(busMetadata, trainMetadata);
        assertEquals(BusResponse.class, RequestPlan.from(busMetadata, BusApiOperators.class).responseType());
        assertEquals(TrainResponse.class, RequestPlan.from(trainMetadata, TrainApiOperators.class).responseType());
    }

    @Test
    void shouldResolveInheritedGenericEnvelopeAndBodyTypesForConcreteClient() throws Exception {
        MethodMetadataCache cache = new MethodMetadataCache();

        RequestPlan busFlux = RequestPlan.from(
                cache.get(BusApiOperators.class.getMethod("listOrders")),
                BusApiOperators.class);
        assertEquals(BusResponse.class, busFlux.responseType());

        RequestPlan busEntity = RequestPlan.from(
                cache.get(BusApiOperators.class.getMethod("getOrderEntity")),
                BusApiOperators.class);
        ParameterizedType entityType = assertInstanceOf(ParameterizedType.class, busEntity.responseType());
        assertEquals(ResponseEntity.class, entityType.getRawType());
        assertEquals(BusResponse.class, entityType.getActualTypeArguments()[0]);

        RequestPlan trainNested = RequestPlan.from(
                cache.get(TrainApiOperators.class.getMethod("listOrderEnvelope")),
                TrainApiOperators.class);
        ParameterizedType listType = assertInstanceOf(ParameterizedType.class, trainNested.responseType());
        assertEquals(List.class, listType.getRawType());
        assertEquals(TrainResponse.class, listType.getActualTypeArguments()[0]);

        RequestPlan busSubmit = RequestPlan.from(
                cache.get(BusApiOperators.class.getMethod("submit", BaseResponse.class)),
                BusApiOperators.class);
        assertEquals(BusResponse.class, busSubmit.bodyType());
        assertEquals(RequestBodyRepeatability.REPEATABLE, busSubmit.bodyRepeatability());
    }

    @Test
    void resolvedGenericArraysUseStructuralGenericArrayTypeEquality() throws Exception {
        MethodMetadataCache cache = new MethodMetadataCache();
        Method inheritedMethod = ParameterizedArrayClient.class.getMethod("load");
        GenericArrayType resolvedType = assertInstanceOf(GenericArrayType.class,
                RequestPlan.from(cache.get(inheritedMethod), ParameterizedArrayClient.class).responseType());

        ParameterizedType explicitReturnType = (ParameterizedType)
                ExplicitParameterizedArrayClient.class.getMethod("load").getGenericReturnType();
        Type equivalentType = explicitReturnType.getActualTypeArguments()[0];
        GenericArrayType equivalentArrayType = assertInstanceOf(GenericArrayType.class, equivalentType);

        assertEquals(equivalentArrayType, resolvedType);
        assertEquals(resolvedType, equivalentArrayType);
        assertEquals(equivalentArrayType.hashCode(), resolvedType.hashCode());
    }

    interface InvalidReturnTypeClient {
        @GET("/items")
        String call();
    }

    interface PatchClient {
        @PATCH("/items/1")
        Mono<String> patch();
    }

    interface HeadOptionsClient {
        @HEAD("/objects/{id}")
        @TimeoutMs(250)
        @Retry("head-retry")
        @LogHttpExchange(logger = OverrideTestExchangeLogger.class)
        Mono<Void> head(@PathVar("id") String id);

        @OPTIONS("/objects")
        @TimeoutMs(300)
        @Retry("options-retry")
        @CircuitBreaker("options-cb")
        @LogHttpExchange(logger = OverrideTestExchangeLogger.class)
        Mono<String> options();
    }

    interface BodyOwnershipClient {
        @POST("/object")
        Mono<String> objectBody(@Body Object body);

        @POST("/input-stream")
        Mono<String> inputStreamBody(@Body InputStream body);

        @POST("/reader")
        Mono<String> readerBody(@Body Reader body);

        @POST("/channel")
        Mono<String> channelBody(@Body ReadableByteChannel body);
    }

    interface GenericBodyClient<T> {
        @POST("/generic")
        Mono<String> genericBody(@Body T body);
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

    interface InvalidHeadApiRefClient {
        @HEAD("/items")
        @ApiRef("user.getById")
        Mono<String> call();
    }

    interface InvalidOptionsApiRefClient {
        @OPTIONS("/items")
        @ApiRef("user.getById")
        Mono<String> call();
    }


    interface MissingEndpointClient {
        Mono<String> call();
    }

    interface BlankPathVarClient {
        @GET("/items/{id}")
        Mono<String> call(@PathVar(" ") String id);
    }

    interface BlankQueryParamClient {
        @GET("/items")
        Mono<String> call(@QueryParam("") String query);
    }

    interface BlankApiNameClient {
        @GET("/items")
        @ApiName(" ")
        Mono<String> call();
    }

    interface AmbiguousVerbClient {
        @GET("/items")
        @POST("/items")
        Mono<String> call();
    }

    interface MultipleBodyClient {
        @POST("/items")
        Mono<String> create(@Body String first, @Body String second);
    }

    interface ParentOverloadedClient {
        @GET("/parent/{id}")
        Mono<String> get(@PathVar("id") String id);
    }

    interface ChildOverloadedClient extends ParentOverloadedClient {
        @GET("/child/{id}")
        Mono<String> get(@PathVar("id") Long id);
    }

    interface ApiOperators<T extends BaseResponse> {
        @GET("/api/order")
        Mono<T> getOrder(@QueryParam("orderId") String orderId);

        @GET("/api/orders")
        Flux<T> listOrders();

        @GET("/api/order/entity")
        Mono<ResponseEntity<T>> getOrderEntity();

        @GET("/api/order/envelope")
        Mono<List<T>> listOrderEnvelope();

        @POST("/api/order")
        Mono<T> submit(@Body T body);
    }

    interface BusApiOperators extends ApiOperators<BusResponse> {
    }

    interface TrainApiOperators extends ApiOperators<TrainResponse> {
    }

    interface GenericArrayOperations<T> {
        @GET("/array")
        Mono<T[]> load();
    }

    interface ParameterizedArrayClient extends GenericArrayOperations<List<String>> {
    }

    interface ExplicitParameterizedArrayClient {
        @GET("/array")
        Mono<List<String>[]> load();
    }

    static class BaseResponse {
        String code;
    }

    static class BusResponse extends BaseResponse {
        String message;
    }

    static class TrainResponse extends BaseResponse {
        String bookingCode;
    }

    static final class OverrideTestExchangeLogger implements HttpExchangeLogger {
        @Override
        public void log(HttpExchangeLogContext context) {
            // no-op
        }
    }
}
