package io.github.huynhngochuyhoang.httpstarter.v31consumer;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.core.MethodMetadataCache;
import io.github.huynhngochuyhoang.httpstarter.core.RequestContext;
import io.github.huynhngochuyhoang.httpstarter.core.RequestContextSnapshot;
import io.github.huynhngochuyhoang.httpstarter.enable.EnableReactiveHttpClients;
import io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException;
import io.github.huynhngochuyhoang.httpstarter.filter.InboundHeadersWebFilter;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
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

import static org.assertj.core.api.Assertions.*;

@Timeout(40)
class Boot4InboundContextConsumerTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void assembledHelpersCaptureRestoreAndValidateWithReplacementBeans(boolean replacement) throws Exception {
        var dispatches = new AtomicInteger();
        var downstream = HttpServer.create().host("127.0.0.1").port(0).handle((request, response) -> {
            dispatches.incrementAndGet();
            assertThat(request.requestHeaders().contains("x-scope")).isFalse();
            return response.header("Content-Type", "text/plain").sendString(Mono.just("ok"));
        }).bindNow(Duration.ofSeconds(5));
        var application = new SpringApplicationBuilder(Application.class).web(WebApplicationType.REACTIVE)
                .properties("server.port=0", "spring.main.banner-mode=off",
                        "reactive.http.inbound-headers.allow-list=x-scope,x-many,x-empty,authorization",
                        "reactive.http.clients.inbound-consumer.base-url=http://127.0.0.1:" + downstream.port(),
                        "reactive.http.clients.inbound-consumer.auth-provider=requiredScope");
        if (replacement) { application.sources(Replacements.class); }
        try (var context = application.run(); var worker = Executors.newSingleThreadExecutor()) {
            var filter = context.getBean(InboundHeadersWebFilter.class);
            assertThat(context.getBeansOfType(InboundHeadersWebFilter.class)).hasSize(1);
            if (replacement) {
                assertThat(filter).isSameAs(context.getBean("replacementCapture"));
                assertThat(context.getBean(MethodMetadataCache.class)).isSameAs(context.getBean("replacementMetadata"));
                assertThat(context.getBean(ReactiveHttpClientProperties.class)).isSameAs(context.getBean("replacementProperties"));
            }
            var queue = new ArrayBlockingQueue<RequestContextSnapshot>(2);
            var handler = WebHttpHandlerBuilder.webHandler(exchange -> Mono.deferContextual(ctx -> {
                assertThat(RequestContext.inboundHeader(ctx, "X-SCOPE")).isPresent();
                assertThat(RequestContext.inboundHeader(ctx, "X-EMPTY")).contains("");
                assertThat(RequestContext.inboundHeader(ctx, "X-ABSENT")).isEmpty();
                assertThat(RequestContext.inboundHeaderValues(ctx, "X-MANY")).containsExactly("one", "two");
                assertThatThrownBy(() -> RequestContext.inboundHeader(ctx, "x-many"))
                        .isInstanceOf(IllegalStateException.class);
                assertThat(RequestContext.inboundHeader(ctx, "Authorization")).contains("[REDACTED]");
                assertThat(queue.offer(RequestContextSnapshot.capture(ctx))).isTrue();
                return exchange.getResponse().setComplete();
            })).filter(filter).build();
            var ingress = HttpServer.create().host("127.0.0.1").port(0)
                    .handle(new ReactorHttpHandlerAdapter(handler)).bindNow(Duration.ofSeconds(5));
            try {
                for (String value : List.of("first", "second")) {
                    WebClient.create("http://127.0.0.1:" + ingress.port()).get().uri("/capture")
                            .header("x-scope", value).header("x-empty", "").header("x-many", "one", "two")
                            .header("authorization", "not-retained").retrieve().toBodilessEntity()
                            .block(Duration.ofSeconds(5));
                }
                var client = context.getBean(Client.class);
                Mono<String> cold = client.get();
                assertThatThrownBy(() -> cold.block(Duration.ofSeconds(5))).isInstanceOf(AuthProviderException.class);
                assertThat(dispatches).hasValue(0);
                for (String expected : List.of("first", "second")) {
                    var snapshot = queue.poll(5, TimeUnit.SECONDS);
                    assertThat(snapshot).isNotNull();
                    assertThat(worker.submit(() -> {
                        assertThat(RequestContext.inboundHeader(Context.empty(), "x-scope")).isEmpty();
                        return Mono.deferContextual(ctx -> {
                            assertThat(RequestContext.inboundHeader(ctx, "X-Scope")).contains(expected);
                            return cold;
                        }).contextWrite(snapshot::writeTo).block(Duration.ofSeconds(5));
                    }).get(10, TimeUnit.SECONDS)).isEqualTo("ok");
                }
                assertThat(dispatches).hasValue(2);
                assertThat(queue).isEmpty();
            } finally { ingress.disposeNow(Duration.ofSeconds(5)); }
        } finally { downstream.disposeNow(Duration.ofSeconds(5)); }
    }

    @ReactiveHttpClient(name = "inbound-consumer")
    interface Client { @GET("/value") Mono<String> get(); }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableReactiveHttpClients(basePackageClasses = Client.class)
    static class Application {
        @Bean AuthProvider requiredScope() {
            return request -> Mono.deferContextual(ctx -> {
                RequestContext.inboundHeader(ctx, "X-Scope")
                        .orElseThrow(() -> new IllegalStateException("required scope absent"));
                return Mono.just(AuthContext.empty());
            });
        }
    }

    static class Replacements {
        @Bean @Primary ReactiveHttpClientProperties replacementProperties(Environment environment) {
            return Binder.get(environment).bind("reactive.http", Bindable.of(ReactiveHttpClientProperties.class))
                    .orElseThrow(IllegalStateException::new);
        }
        @Bean MethodMetadataCache replacementMetadata() { return new MethodMetadataCache(); }
        @Bean InboundHeadersWebFilter replacementCapture(ReactiveHttpClientProperties properties) {
            return new InboundHeadersWebFilter(properties.getInboundHeaders());
        }
    }
}
