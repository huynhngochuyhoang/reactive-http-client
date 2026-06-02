package io.github.huynhngochuyhoang.httpstarter.nativesmoke;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import reactor.core.publisher.Mono;

@ReactiveHttpClient(name = "native-smoke", baseUrl = "http://127.0.0.1:9")
public interface NativeSmokeClient extends NativeSmokeOperations {
}

interface NativeSmokeOperations {

    @GET("/ping")
    Mono<String> ping();
}
