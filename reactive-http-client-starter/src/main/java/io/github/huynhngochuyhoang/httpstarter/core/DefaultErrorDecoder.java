package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.exception.HttpClientException;
import io.github.huynhngochuyhoang.httpstarter.exception.RemoteServiceException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Translates HTTP error responses into domain exceptions.
 * <ul>
 *   <li>4xx → {@link HttpClientException}</li>
 *   <li>5xx → {@link RemoteServiceException}</li>
 * </ul>
 */
public class DefaultErrorDecoder {

    private static final int MAX_ERROR_BODY_BYTES = 4096;

    /**
     * Returns a {@code Mono} that immediately signals an appropriate exception for the
     * given error response, or an empty Mono if the status code is not an error.
     */
    public Mono<? extends Throwable> decode(ClientResponse response) {
        int code = response.statusCode().value();
        return readBodyWithCap(response, MAX_ERROR_BODY_BYTES)
                .defaultIfEmpty("")
                .map(body -> {
                    if (code >= 400 && code < 500) {
                        return (Throwable) new HttpClientException(code, body);
                    }
                    return new RemoteServiceException(code, body);
                });
    }

    private Mono<String> readBodyWithCap(ClientResponse response, int maxBytes) {
        return response.bodyToFlux(DataBuffer.class)
                .handle(new java.util.function.BiConsumer<>() {
                    private int remaining = maxBytes;
                    private final ByteArrayOutputStream output = new ByteArrayOutputStream(maxBytes);

                    @Override
                    public void accept(DataBuffer dataBuffer, reactor.core.publisher.SynchronousSink<String> sink) {
                        try {
                            if (remaining > 0) {
                                int toRead = Math.min(remaining, dataBuffer.readableByteCount());
                                if (toRead > 0) {
                                    byte[] chunk = new byte[toRead];
                                    dataBuffer.read(chunk, 0, toRead);
                                    output.write(chunk, 0, toRead);
                                    remaining -= toRead;
                                }
                            }
                            if (remaining <= 0) {
                                sink.next(output.toString(StandardCharsets.UTF_8));
                                sink.complete();
                            }
                        } finally {
                            DataBufferUtils.release(dataBuffer);
                        }
                    }
                })
                .next()
                .switchIfEmpty(Mono.fromSupplier(() -> ""));
    }
}
