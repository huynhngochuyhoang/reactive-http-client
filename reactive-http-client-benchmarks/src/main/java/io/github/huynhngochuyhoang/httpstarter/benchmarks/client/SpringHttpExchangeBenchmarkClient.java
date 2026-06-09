package io.github.huynhngochuyhoang.httpstarter.benchmarks.client;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Mono;

@HttpExchange
public interface SpringHttpExchangeBenchmarkClient {

    @GetExchange("/users/current")
    Mono<BenchmarkUser> currentUser();

    @GetExchange("/users/current")
    Mono<ResponseEntity<BenchmarkUser>> currentUserEntity();

    @GetExchange("/users/{id}")
    Mono<BenchmarkUser> findUser(
            @PathVariable("id") String id,
            @RequestParam("expand") String expand,
            @RequestHeader("X-Tenant") String tenant);

    @GetExchange("/users/{id}")
    Mono<ResponseEntity<BenchmarkUser>> findUserEntity(
            @PathVariable("id") String id,
            @RequestParam("expand") String expand,
            @RequestHeader("X-Tenant") String tenant);

    @PostExchange("/users")
    Mono<BenchmarkUser> createUser(@RequestBody CreateUserRequest request);

    @PostExchange("/users")
    Mono<ResponseEntity<BenchmarkUser>> createUserEntity(@RequestBody CreateUserRequest request);

    @GetExchange("/errors/client")
    Mono<BenchmarkUser> clientError();

    @GetExchange("/errors/server")
    Mono<BenchmarkUser> serverError();
}
