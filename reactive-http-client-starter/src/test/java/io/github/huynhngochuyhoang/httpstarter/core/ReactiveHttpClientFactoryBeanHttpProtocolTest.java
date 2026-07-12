package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;

class ReactiveHttpClientFactoryBeanHttpProtocolTest {

    @Test
    void leavesHttpClientUnchangedWhenHttp2IsDisabled() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        HttpClient original = HttpClient.create();

        HttpClient configured = ReactiveHttpClientFactoryBean.applyHttpProtocol(original, config, null);

        assertSame(original, configured);
    }

    @Test
    void appliesHttp2WhenClientOptsInWithoutBaseUrl() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setHttp2Enabled(true);

        HttpClient configured = ReactiveHttpClientFactoryBean.applyHttpProtocol(HttpClient.create(), config, null);

        assertThat(configured.configuration().protocols()).containsExactly(HttpProtocol.H2);
    }

    @Test
    void appliesTlsHttp2ForHttpsBaseUrl() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setBaseUrl("https://example.com");
        config.setHttp2Enabled(true);

        HttpClient configured = ReactiveHttpClientFactoryBean.applyHttpProtocol(
                HttpClient.create(), config, config.getBaseUrl());

        assertThat(configured.configuration().protocols()).containsExactly(HttpProtocol.H2);
    }

    @Test
    void appliesClearTextHttp2ForHttpBaseUrl() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setBaseUrl("http://example.com");
        config.setHttp2Enabled(true);

        HttpClient configured = ReactiveHttpClientFactoryBean.applyHttpProtocol(
                HttpClient.create(), config, config.getBaseUrl());

        assertThat(configured.configuration().protocols()).containsExactly(HttpProtocol.H2C);
    }

    @Test
    void usesResolvedBaseUrlWhenConfiguredBaseUrlDiffers() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setBaseUrl("https://configured.example.com");
        config.setHttp2Enabled(true);

        HttpClient configured = ReactiveHttpClientFactoryBean.applyHttpProtocol(
                HttpClient.create(), config, "http://annotation.example.com");

        assertThat(configured.configuration().protocols()).containsExactly(HttpProtocol.H2C);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Content-Length", "transfer-encoding", "Connection", "Expect", "Host"})
    void rejectsApplicationSuppliedFramingAndAuthorityHeaders(String headerName) {
        WebClient client = WebClient.builder()
                .filter(ReactiveHttpClientFactoryBean.outboundFramingHeaderFilter())
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK).build()))
                .build();

        StepVerifier.create(client.get().uri("http://example.test/resource")
                        .header(headerName, "unsafe")
                        .exchangeToMono(Mono::just))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining(headerName)
                        .hasMessageContaining("owned by the configured transport"))
                .verify();
    }

    @Test
    void rejectsFramingHeaderAddedByAnEarlierCustomizerFilter() {
        WebClient client = WebClient.builder()
                .filter((request, next) -> next.exchange(ClientRequest.from(request)
                        .header("Host", "wrong-authority.example")
                        .build()))
                .filter(ReactiveHttpClientFactoryBean.outboundFramingHeaderFilter())
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK).build()))
                .build();

        StepVerifier.create(client.get().uri("http://example.test/resource").exchangeToMono(Mono::just))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Host"))
                .verify();
    }

    @Test
    void allowsEndToEndApplicationHeaders() {
        WebClient client = WebClient.builder()
                .filter(ReactiveHttpClientFactoryBean.outboundFramingHeaderFilter())
                .exchangeFunction(request -> {
                    assertThat(request.headers().getFirst("X-Request-ID")).isEqualTo("request-1");
                    return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                })
                .build();

        ClientResponse response = client.get().uri("http://example.test/resource")
                .header("X-Request-ID", "request-1")
                .exchangeToMono(Mono::just)
                .block(java.time.Duration.ofSeconds(1));

        assertThat(response).isNotNull();
    }
}
