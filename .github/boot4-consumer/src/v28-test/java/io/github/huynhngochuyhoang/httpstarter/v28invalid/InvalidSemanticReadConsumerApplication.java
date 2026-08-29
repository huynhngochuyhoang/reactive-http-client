package io.github.huynhngochuyhoang.httpstarter.v28invalid;

import io.github.huynhngochuyhoang.httpstarter.annotation.CacheResponse;
import io.github.huynhngochuyhoang.httpstarter.annotation.POST;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.enable.EnableReactiveHttpClients;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import reactor.core.publisher.Mono;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableReactiveHttpClients(basePackageClasses = InvalidSemanticReadConsumerApplication.class)
public class InvalidSemanticReadConsumerApplication {
}

@ReactiveHttpClient(name = "invalid-semantic")
interface InvalidSemanticReadClient {
    @POST("/query")
    @CacheResponse("selected")
    Mono<String> query();
}
