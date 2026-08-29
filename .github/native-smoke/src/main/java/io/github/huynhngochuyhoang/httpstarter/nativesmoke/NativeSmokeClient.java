package io.github.huynhngochuyhoang.httpstarter.nativesmoke;

import io.github.huynhngochuyhoang.httpstarter.annotation.ApiRef;
import io.github.huynhngochuyhoang.httpstarter.annotation.Body;
import io.github.huynhngochuyhoang.httpstarter.annotation.CacheKey;
import io.github.huynhngochuyhoang.httpstarter.annotation.CacheResponse;
import io.github.huynhngochuyhoang.httpstarter.annotation.CircuitBreaker;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.POST;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.annotation.Retry;
import reactor.core.publisher.Mono;

@ReactiveHttpClient(name = "native-smoke")
public interface NativeSmokeClient extends NativeSmokeOperations<NativeOrderResponse> {
}

interface NativeSmokeOperations<T> {

    @GET("/api/order")
    Mono<T> getOrder();

    @GET("/api/compressed-order")
    Mono<T> getCompressedOrder();

    @ApiRef("native-problem")
    Mono<T> getProblem();

    @GET("/api/open-circuit")
    @CircuitBreaker("native-open")
    Mono<T> getOpenCircuit();

    @GET("/api/cached-order")
    @CacheResponse("native-cache")
    Mono<T> getCachedOrder();

    @GET("/api/retry-only")
    @Retry("native-retry")
    Mono<T> getRetryOnly();

    @POST("/api/cached-query")
    @CacheResponse(value = "native-query-cache", semanticRead = true)
    Mono<T> query(@Body @CacheKey("body") NativeQueryRequest body);
}

record NativeOrderResponse(String code, String message) {
}

record NativeQueryRequest(String query) {
}
