package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.Body;
import io.github.huynhngochuyhoang.httpstarter.annotation.POST;
import io.github.huynhngochuyhoang.httpstarter.annotation.PUT;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class Framework7TransportCorrectnessTest {

    @Test
    void starterPostThenPutUsesTransportFramingOnOnePooledHttp11Connection() {
        List<CapturedRequest> requests = new CopyOnWriteArrayList<>();
        DisposableServer server = HttpServer.create()
                .port(0)
                .handle((request, response) -> request.receive().aggregate().asString()
                        .defaultIfEmpty("")
                        .flatMap(body -> {
                            request.withConnection(connection -> requests.add(new CapturedRequest(
                                    connection.channel().id().asLongText(),
                                    request.method().name(),
                                    request.uri(),
                                    body,
                                    request.requestHeaders().get("Content-Length"),
                                    request.requestHeaders().get("Transfer-Encoding"),
                                    request.requestHeaders().get("Host"))));
                            return response.sendString(Mono.just("ok")).then();
                        }))
                .bindNow();
        ReactiveHttpClientFactoryBean<TransportClient> factory = null;
        StaticApplicationContext context = new StaticApplicationContext();
        try {
            ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
            ReactiveHttpClientProperties.ClientConfig client = new ReactiveHttpClientProperties.ClientConfig();
            client.setBaseUrl("http://127.0.0.1:" + server.port());
            ReactiveHttpClientProperties.ConnectionPoolConfig pool =
                    new ReactiveHttpClientProperties.ConnectionPoolConfig();
            pool.setMaxConnections(1);
            client.setPool(pool);
            properties.getClients().put("transport-client", client);

            context.getBeanFactory().registerSingleton("reactiveHttpClientProperties", properties);
            context.getBeanFactory().registerSingleton("starterWebClientBuilder", WebClient.builder());
            context.getBeanFactory().registerSingleton("reactiveHttpClientJsonCodec", TestJsonCodecs.jsonCodec());

            factory = new ReactiveHttpClientFactoryBean<>();
            factory.setType(TransportClient.class);
            factory.setApplicationContext(context);
            TransportClient transportClient = factory.getObject();

            assertThat(transportClient.create("{}".getBytes(StandardCharsets.UTF_8))
                    .block(Duration.ofSeconds(5))).isEqualTo("ok");
            assertThat(transportClient.update("update".getBytes(StandardCharsets.UTF_8))
                    .block(Duration.ofSeconds(5))).isEqualTo("ok");

            assertThat(requests).hasSize(2);
            assertThat(requests.get(0)).satisfies(request -> {
                assertThat(request.method()).isEqualTo("POST");
                assertThat(request.uri()).isEqualTo("/orders");
                assertThat(request.body()).isEqualTo("{}");
                assertThat(request.contentLength()).isEqualTo("2");
                assertThat(request.transferEncoding()).isNull();
                assertThat(request.host()).isEqualTo("127.0.0.1:" + server.port());
            });
            assertThat(requests.get(1)).satisfies(request -> {
                assertThat(request.method()).isEqualTo("PUT");
                assertThat(request.uri()).isEqualTo("/orders/1");
                assertThat(request.body()).isEqualTo("update");
                assertThat(request.contentLength()).isEqualTo("6");
                assertThat(request.transferEncoding()).isNull();
                assertThat(request.channelId()).isEqualTo(requests.get(0).channelId());
            });
        } finally {
            if (factory != null) {
                factory.destroy();
            }
            context.close();
            server.disposeNow(Duration.ofSeconds(5));
        }
    }

    @Test
    void malformedContentLengthNeverReachesTheApplicationEndpoint() throws Exception {
        String malformedWireRequest =
                "POST /orders HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: invalid\r\n\r\n{}";
        List<CapturedDecoderRequest> routedRequests = new CopyOnWriteArrayList<>();
        CountDownLatch routedRequestRecorded = new CountDownLatch(1);
        DisposableServer server = HttpServer.create()
                .port(0)
                .handle((request, response) -> {
                    request.withConnection(connection -> routedRequests.add(new CapturedDecoderRequest(
                            connection.channel().id().asLongText(),
                            request.method().name(),
                            request.uri(),
                            request.version().text())));
                    routedRequestRecorded.countDown();
                    return response.sendString(Mono.just("application-handler")).then();
                })
                .bindNow();
        try (Socket socket = new Socket("127.0.0.1", server.port());
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))) {
            socket.setSoTimeout(5000);
            writer.write(malformedWireRequest);
            writer.flush();

            String statusLine = reader.readLine();
            assertThat(statusLine).contains("400");
            assertThat(malformedWireRequest).contains("POST /orders HTTP/1.1", "Content-Length: invalid");
            assertThat(routedRequestRecorded.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(routedRequests).hasSize(1);
            assertThat(routedRequests).noneMatch(request -> request.method().equals("POST"));
            assertThat(routedRequests).allSatisfy(request -> {
                assertThat(request.channelId()).isNotBlank();
                assertThat(request.method()).isEqualTo("GET");
                assertThat(request.uri()).isEqualTo("/bad-request");
                assertThat(request.protocol()).isEqualTo("HTTP/1.0");
            });
        } finally {
            server.disposeNow(Duration.ofSeconds(5));
        }
    }

    @Test
    void clearTextHttp2NegotiatesH2c() {
        DisposableServer server = HttpServer.create()
                .protocol(HttpProtocol.H2C)
                .port(0)
                .handle((request, response) -> response.sendString(Mono.just(request.version().text())).then())
                .bindNow();
        try {
            String version = HttpClient.create()
                    .protocol(HttpProtocol.H2C)
                    .get()
                    .uri("http://127.0.0.1:" + server.port() + "/protocol")
                    .responseSingle((response, bytes) -> bytes.asString())
                    .block(Duration.ofSeconds(5));

            assertThat(version).isEqualTo("HTTP/2.0");
        } finally {
            server.disposeNow(Duration.ofSeconds(5));
        }
    }

    @ReactiveHttpClient(name = "transport-client")
    interface TransportClient {
        @POST("/orders")
        Mono<String> create(@Body byte[] body);

        @PUT("/orders/1")
        Mono<String> update(@Body byte[] body);
    }

    private record CapturedRequest(String channelId,
                                   String method,
                                   String uri,
                                   String body,
                                   String contentLength,
                                   String transferEncoding,
                                   String host) { }

    private record CapturedDecoderRequest(String channelId, String method, String uri, String protocol) { }
}
