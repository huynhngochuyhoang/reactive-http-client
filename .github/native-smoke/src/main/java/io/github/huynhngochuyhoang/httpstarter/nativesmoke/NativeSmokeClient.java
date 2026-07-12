package io.github.huynhngochuyhoang.httpstarter.nativesmoke;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import reactor.core.publisher.Mono;

@ReactiveHttpClient(name = "native-smoke")
public interface NativeSmokeClient extends NativeSmokeOperations<NativeOrderResponse> {
}

interface NativeSmokeOperations<T> {

    @GET("/api/order")
    Mono<T> getOrder();

    @GET("/api/problem")
    Mono<T> getProblem();
}

record NativeOrderResponse(String code, String message) {
}
