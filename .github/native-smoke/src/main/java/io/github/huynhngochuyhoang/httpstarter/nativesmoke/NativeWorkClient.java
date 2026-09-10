package io.github.huynhngochuyhoang.httpstarter.nativesmoke;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import reactor.core.publisher.Mono;

@ReactiveHttpClient(name = "native-work")
public interface NativeWorkClient {
    @GET("/work/{id}") @CacheResponse("count")
    Mono<String> get(@PathVar("id") String id);

    @POST("/search") @CacheResponse(value = "weighted", semanticRead = true)
    Mono<String> search(@Body @CacheKey("body") String body);

    @GET("/refresh/{id}") @CacheResponse("refresh")
    Mono<String> refresh(@PathVar("id") String id);
}
