package io.github.huynhngochuyhoang.httpstarter.nativesmoke;

import io.github.huynhngochuyhoang.httpstarter.core.RequestContext;
import io.github.huynhngochuyhoang.httpstarter.core.RequestContextSnapshot;
import io.github.huynhngochuyhoang.httpstarter.filter.InboundHeadersWebFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServer;
import reactor.util.context.Context;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Real WebFlux capture plus independent subscription restoration, without header DTO reflection. */
final class NativeInboundContextScenario {
    static void run(InboundHeadersWebFilter filter) throws Exception {
        var snapshots = new ArrayBlockingQueue<RequestContextSnapshot>(2);
        var captures = new AtomicInteger();
        var rejected = new AtomicInteger();
        var handler = WebHttpHandlerBuilder.webHandler(exchange -> Mono.deferContextual(ctx -> {
            captures.incrementAndGet();
            if (RequestContext.inboundHeader(ctx, "X-SCOPE").isEmpty()) {
                rejected.incrementAndGet();
                exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
                return exchange.getResponse().setComplete();
            }
            require(RequestContext.inboundHeader(ctx, "X-Empty").orElseThrow().isEmpty(), "empty value lost");
            require(RequestContext.inboundHeader(ctx, "X-Omitted").isEmpty(), "allowlist was bypassed");
            require(RequestContext.inboundHeader(ctx, "Authorization").orElseThrow().equals("[REDACTED]"),
                    "redaction was bypassed");
            require(RequestContext.inboundHeaderValues(ctx, "X-MANY").equals(List.of("one", "one")),
                    "duplicate values changed");
            boolean ambiguous = false;
            try { RequestContext.inboundHeader(ctx, "x-many"); }
            catch (IllegalStateException expected) { ambiguous = true; }
            require(ambiguous, "single-value access accepted duplicates");
            require(snapshots.offer(RequestContextSnapshot.capture(ctx)), "handoff queue overflow");
            return exchange.getResponse().setComplete();
        })).filter(filter).build();
        var server = HttpServer.create().host("127.0.0.1").port(0)
                .handle(new ReactorHttpHandlerAdapter(handler)).bindNow(Duration.ofSeconds(5));
        try (var worker = Executors.newSingleThreadExecutor()) {
            var web = WebClient.create("http://127.0.0.1:" + server.port());
            for (String value : List.of("first", "second")) {
                web.get().uri("/capture").header("x-scope", value).header("x-empty", "")
                        .header("x-many", "one", "one").header("authorization", "not-retained")
                        .header("x-omitted", "not-retained").retrieve().toBodilessEntity()
                        .block(Duration.ofSeconds(5));
            }
            Mono<String> restoredRead = Mono.deferContextual(ctx -> Mono.just(
                    RequestContext.inboundHeader(ctx, "X-Scope")
                            .orElseThrow(() -> new IllegalStateException("required scope absent"))));
            for (String expected : List.of("first", "second")) {
                var snapshot = snapshots.poll(5, TimeUnit.SECONDS);
                require(snapshot != null, "missing captured envelope");
                require(expected.equals(worker.submit(() -> {
                    require(RequestContext.inboundHeader(Context.empty(), "X-Scope").isEmpty(),
                            "independent worker inherited context");
                    return restoredRead.contextWrite(snapshot::writeTo).block(Duration.ofSeconds(5));
                }).get(10, TimeUnit.SECONDS)), "restored caller isolation failed");
            }
            var status = web.get().uri("/missing").exchangeToMono(response ->
                    response.releaseBody().thenReturn(response.statusCode())).block(Duration.ofSeconds(5));
            require(HttpStatus.BAD_REQUEST.equals(status) && rejected.get() == 1, "required-header gate failed");
            require(captures.get() == 3 && snapshots.isEmpty(), "unexpected capture or retained envelope");
            require(RequestContext.inboundHeader(Context.empty(), "X-Scope").isEmpty(), "caller context escaped");
            System.out.println("V31 inbound context: capture=3 restored=2 rejected=1; parity passed");
        } finally { server.disposeNow(Duration.ofSeconds(5)); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }
}
