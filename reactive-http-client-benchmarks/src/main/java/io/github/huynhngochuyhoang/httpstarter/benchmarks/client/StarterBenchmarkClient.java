package io.github.huynhngochuyhoang.httpstarter.benchmarks.client;

import io.github.huynhngochuyhoang.httpstarter.annotation.Body;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.HeaderParam;
import io.github.huynhngochuyhoang.httpstarter.annotation.POST;
import io.github.huynhngochuyhoang.httpstarter.annotation.PathVar;
import io.github.huynhngochuyhoang.httpstarter.annotation.QueryParam;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import reactor.core.publisher.Mono;

@ReactiveHttpClient(name = "benchmark-starter")
public interface StarterBenchmarkClient {

    @GET("/users/{id}")
    Mono<BenchmarkUser> findUser(
            @PathVar("id") String id,
            @QueryParam("expand") String expand,
            @HeaderParam("X-Tenant") String tenant);

    @POST("/users")
    Mono<BenchmarkUser> createUser(@Body CreateUserRequest request);
}
