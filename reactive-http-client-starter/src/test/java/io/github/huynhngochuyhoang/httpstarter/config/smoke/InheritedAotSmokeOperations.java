package io.github.huynhngochuyhoang.httpstarter.config.smoke;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import reactor.core.publisher.Mono;

public interface InheritedAotSmokeOperations {

    @GET("/inherited-ping")
    Mono<String> inheritedPing();
}
