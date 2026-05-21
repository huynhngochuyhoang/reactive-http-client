package io.github.huynhngochuyhoang.httpstarter.config.smoke;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import reactor.core.publisher.Mono;

@ReactiveHttpClient(name = "release-smoke", baseUrl = "http://release-smoke.test")
public interface AotSmokeClient {

    @GET("/ping")
    Mono<String> ping();
}
