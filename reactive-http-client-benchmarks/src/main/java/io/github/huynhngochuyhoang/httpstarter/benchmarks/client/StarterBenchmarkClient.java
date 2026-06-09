package io.github.huynhngochuyhoang.httpstarter.benchmarks.client;

import io.github.huynhngochuyhoang.httpstarter.annotation.Body;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.HeaderParam;
import io.github.huynhngochuyhoang.httpstarter.annotation.POST;
import io.github.huynhngochuyhoang.httpstarter.annotation.PathVar;
import io.github.huynhngochuyhoang.httpstarter.annotation.QueryParam;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

@ReactiveHttpClient(name = "benchmark-starter")
public interface StarterBenchmarkClient {

    @GET("/users/current")
    Mono<BenchmarkUser> currentUser();

    @GET("/users/current")
    Mono<ResponseEntity<BenchmarkUser>> currentUserEntity();

    @GET("/users/{id}")
    Mono<BenchmarkUser> findUser(
            @PathVar("id") String id,
            @QueryParam("expand") String expand,
            @HeaderParam("X-Tenant") String tenant);

    @GET("/users/{id}")
    Mono<ResponseEntity<BenchmarkUser>> findUserEntity(
            @PathVar("id") String id,
            @QueryParam("expand") String expand,
            @HeaderParam("X-Tenant") String tenant);

    @POST("/users")
    Mono<BenchmarkUser> createUser(@Body CreateUserRequest request);

    @POST("/users")
    Mono<ResponseEntity<BenchmarkUser>> createUserEntity(@Body CreateUserRequest request);

    @GET("/errors/client")
    Mono<BenchmarkUser> clientError();

    @GET("/errors/server")
    Mono<BenchmarkUser> serverError();

    @GET("/errors/problem")
    Mono<BenchmarkUser> problemDetailError();
}
