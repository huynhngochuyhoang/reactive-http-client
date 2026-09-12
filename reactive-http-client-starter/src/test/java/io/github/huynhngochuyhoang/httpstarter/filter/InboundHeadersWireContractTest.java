package io.github.huynhngochuyhoang.httpstarter.filter;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientAutoConfiguration;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientFactoryBean;
import io.github.huynhngochuyhoang.httpstarter.core.RequestContext;
import io.github.huynhngochuyhoang.httpstarter.core.RequestContextSnapshot;
import io.netty.channel.Channel;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.resolver.ResolvedAddressTypes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.Http2SslContextSpec;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.LoopResources;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InboundHeadersWireContractTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String JSON = "{\"fixture\":1}";

    @ParameterizedTest
    @EnumSource(WireCase.class)
    void registeredFilterPreservesWireSpellingWhileNamedLookupWorks(WireCase wire) {
        var verified = new CompletableFuture<Void>();
        WebHandler handler = checking(verified, (exchange, ctx) -> {
            assertThat(ctx.hasKey("inboundHeaders")).isTrue();
            Map<String, List<String>> captured = RequestContext.inboundHeaders(ctx);
            assertThat(captured).containsOnlyKeys(wire.field("Fixture"), wire.field("Multi"),
                    wire.field("Empty"), wire.field("Private"));
            assertThat(captured.get(wire.field("Fixture"))).containsExactly(JSON);
            assertThat(captured.get("X-FIXTURE")).isNull();
            assertThat(exchange.getRequest().getHeaders().getFirst("X-FIXTURE")).isEqualTo(JSON);
            assertThat(RequestContext.inboundHeader(ctx, "X-FIXTURE")).contains(JSON);
            assertThat(RequestContext.inboundHeaderValues(ctx, "X-MULTI")).containsExactly("one", "one", "two");
            assertThat(captured.get(wire.field("Multi"))).containsExactly("one", "one", "two");
            assertThatThrownBy(() -> RequestContext.inboundHeader(ctx, "X-MULTI"))
                    .isInstanceOf(IllegalStateException.class).hasMessage("Inbound header has multiple values");
            assertThat(RequestContext.inboundHeader(ctx, "X-EMPTY")).contains("");
            assertThat(RequestContext.inboundHeader(ctx, "X-PRIVATE")).contains("[REDACTED]");
            assertThat(RequestContext.inboundHeader(ctx, "X-DROPPED")).isEmpty();
            assertThat(exchange.getRequest().getHeaders().getFirst("X-DROPPED")).isEqualTo("synthetic-dropped");
            assertThat(RequestContext.inboundHeaderValues(ctx, "X-REQUIRED")).isEmpty();
            assertThat(RequestContext.inboundHeader(ctx, "X-REQUIRED")).isEmpty();
            assertThat(captured.get(wire.field("Required"))).isNull();
            assertThatThrownBy(() -> RequestContext.inboundHeader(ctx, "X-REQUIRED")
                    .orElseThrow(() -> new IllegalArgumentException("Required fixture header is absent")))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Required fixture header is absent");
            assertThatThrownBy(() -> captured.put("x-new", List.of("new")))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> captured.get(wire.field("Multi")).add("new"))
                    .isInstanceOf(UnsupportedOperationException.class);
            Context restored = RequestContextSnapshot.capture(ctx).writeTo(Context.empty());
            assertThat(RequestContext.inboundHeaders(restored)).isEqualTo(captured);
            assertThat(RequestContext.inboundHeader(restored, "X-FIXTURE")).contains(JSON);
            // Application-owned required-field response; no default identity is substituted.
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
        });
        runner().withPropertyValues(
                        "reactive.http.inbound-headers.allow-list=X-FiXtUrE,x-multi,X-EMPTY,x-private,x-required",
                        "reactive.http.inbound-headers.deny-list=X-PRIVATE,x-dropped")
                .withBean("webHandler", WebHandler.class, () -> handler)
                .run(context -> {
                    assertThat(context).hasSingleBean(InboundHeadersWebFilter.class);
                    try (WireServer server = new WireServer(wire,
                            WebHttpHandlerBuilder.applicationContext(context.getSourceApplicationContext()).build())) {
                        Reply reply = server.request();
                        verified.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                        assertThat(reply.status()).isEqualTo(400);
                        assertThat(reply.body()).isEqualTo("verified");
                        server.assertProtocol();
                        assertThat(server.receivedNames.get()).contains(wire.field("Fixture"), wire.field("Multi"));
                    }
                });
    }

    @ParameterizedTest
    @EnumSource(WireCase.class)
    void defaultSelectionAndRedactionRemainUnchangedOnTheWire(WireCase wire) throws Exception {
        var verified = new CompletableFuture<Void>();
        WebHandler handler = checking(verified, (exchange, ctx) -> {
            assertThat(RequestContext.inboundHeader(ctx, "X-Dropped")).contains("synthetic-dropped");
            assertThat(RequestContext.inboundHeader(ctx, "X-Private")).contains("synthetic-private");
            for (String name : List.of("Authorization", "Cookie", "Set-Cookie", "Proxy-Authorization", "X-Api-Key")) {
                assertThat(exchange.getRequest().getHeaders().getFirst(name)).isEqualTo("synthetic-private");
                assertThat(RequestContext.inboundHeader(ctx, name)).contains("[REDACTED]");
            }
        });
        try (WireServer server = new WireServer(wire,
                WebHttpHandlerBuilder.webHandler(handler).filter(new InboundHeadersWebFilter()).build())) {
            Reply reply = server.request();
            verified.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertThat(reply.status()).isEqualTo(200);
            server.assertProtocol();
        }
    }

    @ParameterizedTest
    @MethodSource("mutationCases")
    void onlyMutationsBeforeCaptureChangeTheImmutableSnapshot(WireCase wire, boolean before) throws Exception {
        var verified = new CompletableFuture<Void>();
        var mutableValues = new ArrayList<>(List.of("changed"));
        HttpHeaders mutableHeaders = new HttpHeaders();
        List<String> invocationOrder = new ArrayList<>();
        WebFilter mutate = (exchange, chain) -> {
            invocationOrder.add("mutate");
            mutableHeaders.putAll(exchange.getRequest().getHeaders());
            mutableHeaders.put(wire.field("Fixture"), mutableValues);
            var request = new ServerHttpRequestDecorator(exchange.getRequest()) {
                @Override
                public HttpHeaders getHeaders() {
                    return mutableHeaders;
                }
            };
            return chain.filter(exchange.mutate().request(request).build());
        };
        WebFilter capture = (exchange, chain) -> {
            invocationOrder.add("capture");
            return new InboundHeadersWebFilter().filter(exchange, chain);
        };
        WebHandler handler = checking(verified, (exchange, ctx) -> {
            assertThat(invocationOrder).containsExactlyElementsOf(before
                    ? List.of("mutate", "capture") : List.of("capture", "mutate"));
            assertThat(exchange.getRequest().getHeaders().getFirst("X-Fixture")).isEqualTo("changed");
            String expected = before ? "changed" : JSON;
            assertThat(RequestContext.inboundHeader(ctx, "X-FIXTURE")).contains(expected);
            var snapshot = RequestContextSnapshot.capture(ctx);
            mutableValues.set(0, "after-capture");
            mutableHeaders.set("X-New", "new");
            assertThat(exchange.getRequest().getHeaders().getFirst("X-Fixture")).isEqualTo("after-capture");
            assertThat(RequestContext.inboundHeader(ctx, "X-Fixture")).contains(expected);
            assertThat(RequestContext.inboundHeaderValues(ctx, "X-New")).isEmpty();
            assertThat(RequestContext.inboundHeader(snapshot.writeTo(Context.empty()), "X-Fixture")).contains(expected);
            assertThatThrownBy(() -> RequestContext.inboundHeaders(ctx).get(wire.field("Fixture")).add("new"))
                    .isInstanceOf(UnsupportedOperationException.class);
        });
        WebFilter[] filters = before ? new WebFilter[]{mutate, capture} : new WebFilter[]{capture, mutate};
        try (WireServer server = new WireServer(wire, WebHttpHandlerBuilder.webHandler(handler).filter(filters).build())) {
            Reply reply = server.request();
            verified.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertThat(reply.status()).isEqualTo(200);
            server.assertProtocol();
        }
    }

    static Stream<Arguments> mutationCases() {
        return Stream.of(WireCase.values()).flatMap(wire -> Stream.of(true, false)
                .map(before -> Arguments.of(wire, before)));
    }

    @ParameterizedTest
    @EnumSource(WireCase.class)
    void applicationReplacementControlsCaptureWithoutRegisteringTheDefault(WireCase wire) {
        var calls = new AtomicInteger();
        var verified = new CompletableFuture<Void>();
        var replacement = new InboundHeadersWebFilter() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
                calls.incrementAndGet();
                return chain.filter(exchange);
            }
        };
        runner().withBean("applicationCapture", InboundHeadersWebFilter.class, () -> replacement)
                .withBean("webHandler", WebHandler.class, () -> checking(verified, (exchange, ctx) -> {
                    assertThat(exchange.getRequest().getHeaders().getFirst("X-Fixture")).isEqualTo(JSON);
                    assertThat(ctx.hasKey(RequestContext.INBOUND_HEADERS_CONTEXT_KEY)).isFalse();
                    assertThat(RequestContext.inboundHeader(ctx, "X-Fixture")).isEmpty();
                }))
                .run(context -> {
                    assertThat(context).hasSingleBean(InboundHeadersWebFilter.class);
                    assertThat(context).doesNotHaveBean("inboundHeadersWebFilter");
                    try (WireServer server = new WireServer(wire,
                            WebHttpHandlerBuilder.applicationContext(context.getSourceApplicationContext()).build())) {
                        Reply reply = server.request();
                        verified.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                        assertThat(reply.status()).isEqualTo(200);
                        assertThat(calls).hasValue(1);
                        server.assertProtocol();
                    }
                });
    }

    @ParameterizedTest
    @EnumSource(WireCase.class)
    void capturedHeadersAreNotAutomaticallyForwardedToTheStarterClient(WireCase wire) throws Exception {
        var downstreamVerified = new CompletableFuture<Void>();
        var inboundVerified = new CompletableFuture<Void>();
        try (WireServer downstream = new WireServer(WireCase.HTTP11_LOWER,
                WebHttpHandlerBuilder.webHandler(checking(downstreamVerified, (exchange, ctx) -> {
                    for (String name : List.of("X-Fixture", "X-Private", "X-Dropped", "Authorization", "Cookie")) {
                        assertThat(exchange.getRequest().getHeaders().containsHeader(name)).isFalse();
                    }
                    assertThat(exchange.getRequest().getHeaders().getFirst("X-Correlation-Id")).isEqualTo("fixture-correlation");
                })).build())) {
            runner().withPropertyValues("reactive.http.clients.inbound-wire-downstream.base-url=" + downstream.baseUrl())
                    .run(context -> {
                        var factory = new ReactiveHttpClientFactoryBean<DownstreamClient>();
                        factory.setType(DownstreamClient.class);
                        factory.setApplicationContext(context.getSourceApplicationContext());
                        try {
                            DownstreamClient client = factory.getObject();
                            WebHandler handler = exchange -> Mono.deferContextual(ctx -> {
                                assertThat(RequestContext.inboundHeader(ctx, "X-Fixture")).contains(JSON);
                                assertThat(RequestContext.inboundHeader(ctx, "Authorization")).contains("[REDACTED]");
                                return client.read().flatMap(body -> {
                                    inboundVerified.complete(null);
                                    return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory()
                                            .wrap(body.getBytes(StandardCharsets.UTF_8))));
                                });
                            }).doOnError(inboundVerified::completeExceptionally);
                            try (WireServer inbound = new WireServer(wire, WebHttpHandlerBuilder.webHandler(handler)
                                    .filter(context.getBean(CorrelationIdWebFilter.class),
                                            context.getBean(InboundHeadersWebFilter.class)).build())) {
                                Reply reply = inbound.request();
                                inboundVerified.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                                downstreamVerified.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                                assertThat(reply.status()).isEqualTo(200);
                                assertThat(reply.body()).isEqualTo("verified");
                                inbound.assertProtocol();
                                // This hop uses the starter's HTTP/1.1 default, independent of inbound protocol.
                                assertThat(downstream.serverProtocol.get())
                                        .isEqualTo(new ProtocolEvidence("HTTP/1.1", false, null));
                                assertThat(downstream.requests).hasValue(1);
                            }
                        } finally {
                            factory.destroy();
                        }
                    });
        }
    }

    @ReactiveHttpClient(name = "inbound-wire-downstream")
    interface DownstreamClient {
        @GET("/fixture")
        Mono<String> read();
    }

    private static ReactiveWebApplicationContextRunner runner() {
        return new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ReactiveHttpClientAutoConfiguration.class));
    }

    private static WebHandler checking(CompletableFuture<Void> verified,
                                       BiConsumer<ServerWebExchange, ContextView> assertions) {
        return exchange -> Mono.deferContextual(ctx -> {
            assertions.accept(exchange, ctx);
            verified.complete(null);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory()
                    .wrap("verified".getBytes(StandardCharsets.US_ASCII))));
        }).doOnError(verified::completeExceptionally);
    }

    enum WireCase {
        HTTP11_LOWER(HttpProtocol.HTTP11, false),
        HTTP11_MIXED(HttpProtocol.HTTP11, true),
        H2C_LOWER(HttpProtocol.H2C, false),
        H2_TLS_LOWER(HttpProtocol.H2, false);

        final HttpProtocol protocol;
        final boolean mixed;

        WireCase(HttpProtocol protocol, boolean mixed) {
            this.protocol = protocol;
            this.mixed = mixed;
        }

        String field(String suffix) {
            return mixed ? "X-" + suffix : "x-" + suffix.toLowerCase(Locale.ROOT);
        }
    }

    private record Reply(int status, String body) { }

    private record ProtocolEvidence(String version, boolean streamChannel, String alpn) {
        static ProtocolEvidence capture(String version, Channel channel) {
            boolean stream = channel instanceof Http2StreamChannel;
            Channel transport = stream ? channel.parent() : channel;
            SslHandler ssl = transport.pipeline().get(SslHandler.class);
            return new ProtocolEvidence(version, stream, ssl == null ? null : ssl.applicationProtocol());
        }
    }

    private static final class WireServer implements AutoCloseable {
        private final WireCase wire;
        private final LoopResources loops = LoopResources.create("v31-inbound-wire", 1, true);
        private final AtomicReference<ProtocolEvidence> serverProtocol = new AtomicReference<>();
        private final AtomicReference<ProtocolEvidence> clientProtocol = new AtomicReference<>();
        private final AtomicReference<List<String>> receivedNames = new AtomicReference<>();
        private final AtomicInteger requests = new AtomicInteger();
        private SelfSignedCertificate certificate;
        private DisposableServer server;
        private HttpClient client;

        WireServer(WireCase wire, HttpHandler handler) throws Exception {
            this.wire = wire;
            try {
                var adapter = new ReactorHttpHandlerAdapter(handler);
                HttpServer configured = HttpServer.create().host("127.0.0.1").port(0)
                        .runOn(loops).protocol(wire.protocol)
                        .handle((request, response) -> {
                            requests.incrementAndGet();
                            request.withConnection(connection -> serverProtocol.set(
                                    ProtocolEvidence.capture(request.version().text(), connection.channel())));
                            receivedNames.set(List.copyOf(request.requestHeaders().names()));
                            return adapter.apply(request, response);
                        });
                client = HttpClient.newConnection().runOn(loops).protocol(wire.protocol)
                        .resolver(resolver -> resolver.resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY))
                        .disableRetry(true).responseTimeout(TIMEOUT)
                        .doOnResponse((response, connection) -> clientProtocol.set(
                                ProtocolEvidence.capture(response.version().text(), connection.channel())));
                if (wire.protocol == HttpProtocol.H2) {
                    certificate = new SelfSignedCertificate("localhost");
                    configured = configured.secure(ssl -> ssl.sslContext(
                            Http2SslContextSpec.forServer(certificate.certificate(), certificate.privateKey())));
                    client = client.secure(ssl -> ssl.sslContext(
                            Http2SslContextSpec.forClient().configure(builder -> builder.trustManager(certificate.cert()))));
                }
                server = configured.bindNow(TIMEOUT);
            } catch (Exception | Error error) {
                close();
                throw error;
            }
        }

        Reply request() {
            return client.headers(headers -> {
                        headers.add(wire.field("Fixture"), JSON);
                        headers.add(wire.field("Multi"), List.of("one", "one", "two"));
                        headers.add(wire.field("Empty"), "");
                        headers.add(wire.field("Private"), "synthetic-private");
                        headers.add(wire.field("Dropped"), "synthetic-dropped");
                        headers.add("x-correlation-id", "fixture-correlation");
                        for (String name : List.of("authorization", "cookie", "set-cookie", "proxy-authorization", "x-api-key")) {
                            headers.add(name, "synthetic-private");
                        }
                    })
                    .get().uri(baseUrl() + "/fixture")
                    .responseSingle((response, body) -> body.asString(StandardCharsets.UTF_8)
                            .defaultIfEmpty("").map(text -> new Reply(response.status().code(), text)))
                    .block(TIMEOUT);
        }

        String baseUrl() {
            return (wire.protocol == HttpProtocol.H2 ? "https://localhost:" : "http://127.0.0.1:") + server.port();
        }

        void assertProtocol() {
            boolean h2 = wire.protocol != HttpProtocol.HTTP11;
            ProtocolEvidence expected = new ProtocolEvidence(h2 ? "HTTP/2.0" : "HTTP/1.1", h2,
                    wire.protocol == HttpProtocol.H2 ? "h2" : null);
            assertThat(serverProtocol.get()).isEqualTo(expected);
            assertThat(clientProtocol.get()).isEqualTo(expected);
            assertThat(requests).hasValue(1);
            if (h2) {
                assertThat(receivedNames.get()).allSatisfy(name -> assertThat(name).isEqualTo(name.toLowerCase(Locale.ROOT)));
            }
        }

        @Override
        public void close() {
            try {
                if (server != null) {
                    server.disposeNow(TIMEOUT);
                }
            } finally {
                try {
                    loops.disposeLater(Duration.ZERO, TIMEOUT).block(TIMEOUT);
                } finally {
                    if (certificate != null) {
                        certificate.delete();
                    }
                }
            }
        }
    }
}
