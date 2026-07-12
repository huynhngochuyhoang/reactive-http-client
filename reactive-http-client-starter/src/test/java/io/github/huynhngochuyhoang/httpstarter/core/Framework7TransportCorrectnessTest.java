package io.github.huynhngochuyhoang.httpstarter.core;

import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;

class Framework7TransportCorrectnessTest {

    @Test
    void sequentialPostThenPutUsesOnePersistentHttp11ConnectionWithoutLeakedBytes() throws Exception {
        List<String> requests = new CopyOnWriteArrayList<>();
        DisposableServer server = HttpServer.create()
                .port(0)
                .handle((request, response) -> request.receive().aggregate().asString()
                        .defaultIfEmpty("")
                        .flatMap(body -> {
                            request.withConnection(connection -> requests.add(
                                    connection.channel().id().asLongText() + " "
                                            + request.method().name() + " " + request.uri() + " " + body));
                            return response.header("Content-Length", "2").sendString(Mono.just("ok")).then();
                        }))
                .bindNow();
        try (Socket socket = new Socket("127.0.0.1", server.port());
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))) {
            socket.setSoTimeout(5000);

            writeRequest(writer, "POST", "/orders", "{}");
            assertThat(readResponse(reader)).isEqualTo("ok");
            writeRequest(writer, "PUT", "/orders/1", "update");
            assertThat(readResponse(reader)).isEqualTo("ok");

            assertThat(requests).hasSize(2);
            assertThat(requests.get(0)).contains(" POST /orders {}");
            assertThat(requests.get(1)).contains(" PUT /orders/1 update");
            assertThat(requests.get(1).substring(0, requests.get(1).indexOf(" ")))
                    .isEqualTo(requests.get(0).substring(0, requests.get(0).indexOf(" ")));
        } finally {
            server.disposeNow(Duration.ofSeconds(5));
        }
    }

    @Test
    void malformedContentLengthNeverReachesTheApplicationEndpoint() throws Exception {
        List<String> routedRequests = new CopyOnWriteArrayList<>();
        DisposableServer server = HttpServer.create()
                .port(0)
                .handle((request, response) -> {
                    routedRequests.add(request.method().name() + " " + request.uri());
                    return response.sendString(Mono.just("application-handler")).then();
                })
                .bindNow();
        try (Socket socket = new Socket("127.0.0.1", server.port());
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))) {
            socket.setSoTimeout(5000);
            writer.write("POST /orders HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: invalid\r\n\r\n{}");
            writer.flush();

            String statusLine = reader.readLine();
            assertThat(statusLine).contains("400");
            assertThat(routedRequests).noneMatch("POST /orders"::equals);
            assertThat(routedRequests).allMatch(request -> request.equals("GET /bad-request"));
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

    private static void writeRequest(BufferedWriter writer, String method, String path, String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.US_ASCII);
        writer.write(method + " " + path + " HTTP/1.1\r\n");
        writer.write("Host: 127.0.0.1\r\n");
        writer.write("Content-Type: application/json\r\n");
        writer.write("Content-Length: " + bytes.length + "\r\n");
        writer.write("Connection: keep-alive\r\n\r\n");
        writer.write(body);
        writer.flush();
    }

    private static String readResponse(BufferedReader reader) throws Exception {
        assertThat(reader.readLine()).contains("200");
        int contentLength = -1;
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            if (line.regionMatches(true, 0, "Content-Length:", 0, "Content-Length:".length())) {
                contentLength = Integer.parseInt(line.substring(line.indexOf(58) + 1).trim());
            }
        }
        assertThat(contentLength).isGreaterThanOrEqualTo(0);
        char[] body = new char[contentLength];
        int offset = 0;
        while (offset < body.length) {
            int read = reader.read(body, offset, body.length - offset);
            assertThat(read).isPositive();
            offset += read;
        }
        return new String(body);
    }
}
