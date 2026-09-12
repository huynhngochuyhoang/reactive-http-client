package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.core.RequestContext;
import io.github.huynhngochuyhoang.httpstarter.filter.InboundHeadersWebFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InboundHeadersAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner reactiveRunner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ReactiveHttpClientAutoConfiguration.class));

    @Test
    void reactiveApplicationRegistersCaptureUsingBoundHeaderSelection() {
        reactiveRunner.withPropertyValues(
                        "reactive.http.inbound-headers.allow-list=X-Fixture,X-Private",
                        "reactive.http.inbound-headers.deny-list=x-private")
                .run(context -> {
                    assertThat(context).hasSingleBean(InboundHeadersWebFilter.class);
                    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
                            .header("x-fixture", "synthetic")
                            .header("X-PRIVATE", "synthetic-private")
                            .header("X-Dropped", "synthetic-dropped"));
                    StepVerifier.create(context.getBean(InboundHeadersWebFilter.class)
                                    .filter(exchange, ex -> Mono.deferContextual(ctx -> {
                                        assertThat(ctx.hasKey(RequestContext.INBOUND_HEADERS_CONTEXT_KEY)).isTrue();
                                        assertThat(RequestContext.inboundHeaders(ctx))
                                                .containsOnlyKeys("x-fixture", "X-PRIVATE")
                                                .containsEntry("x-fixture", List.of("synthetic"))
                                                .containsEntry("X-PRIVATE", List.of("[REDACTED]"));
                                        return Mono.empty();
                                    })))
                            .expectComplete().verify(Duration.ofSeconds(5));
                });
    }

    @Test
    void webClientAvailabilityAloneDoesNotRegisterInboundCapture() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ReactiveHttpClientAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(WebClient.Builder.class);
                    assertThat(context).doesNotHaveBean(InboundHeadersWebFilter.class);
                });
    }

    @Test
    void servletApplicationWithWebClientDoesNotRegisterReactiveCaptureBridge() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ReactiveHttpClientAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getServletContext()).isNotNull();
                    assertThat(context).hasSingleBean(WebClient.Builder.class);
                    assertThat(context).doesNotHaveBean(InboundHeadersWebFilter.class);
                    assertThat(context.getBeansOfType(jakarta.servlet.Filter.class)).isEmpty();
                    assertThat(context.getBeansOfType(WebFilter.class)).isEmpty();
                });
    }

    @Test
    void captureDoesNotDeclareAnOrderOverApplicationSecurityFilters() throws Exception {
        assertThat(Ordered.class.isAssignableFrom(InboundHeadersWebFilter.class)).isFalse();
        assertThat(InboundHeadersWebFilter.class.getAnnotation(Order.class)).isNull();
        assertThat(ReactiveHttpClientAutoConfiguration.class
                .getMethod("inboundHeadersWebFilter", ReactiveHttpClientProperties.class)
                .getAnnotation(Order.class)).isNull();
    }

    @Test
    void replacementFilterOwnsWhetherTheContextKeyIsWritten() {
        var replacement = new PassThroughInboundFilter();
        reactiveRunner.withBean("applicationInboundFilter", PassThroughInboundFilter.class, () -> replacement)
                .run(context -> {
                    assertThat(context).hasSingleBean(InboundHeadersWebFilter.class);
                    assertThat(context).doesNotHaveBean("inboundHeadersWebFilter");
                    assertThat(context.getBean(InboundHeadersWebFilter.class)).isSameAs(replacement);
                    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
                            .header("X-Fixture", "synthetic"));
                    StepVerifier.create(context.getBean(InboundHeadersWebFilter.class)
                                    .filter(exchange, ex -> Mono.deferContextual(ctx -> {
                                        assertThat(ex.getRequest().getHeaders().containsHeader("X-Fixture")).isTrue();
                                        assertThat(ctx.hasKey(RequestContext.INBOUND_HEADERS_CONTEXT_KEY)).isFalse();
                                        assertThat(RequestContext.inboundHeaders(ctx)).isEmpty();
                                        return Mono.empty();
                                    })))
                            .expectComplete().verify(Duration.ofSeconds(5));
                    assertThat(replacement.calls).hasValue(1);
                });
    }

    @Test
    void unrelatedWebFilterDoesNotReplaceInboundCapture() {
        reactiveRunner.withBean("applicationFilter", WebFilter.class, () -> (exchange, chain) -> chain.filter(exchange))
                .run(context -> assertThat(context).hasSingleBean(InboundHeadersWebFilter.class));
    }

    static class PassThroughInboundFilter extends InboundHeadersWebFilter {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
            calls.incrementAndGet();
            return chain.filter(exchange);
        }
    }
}
