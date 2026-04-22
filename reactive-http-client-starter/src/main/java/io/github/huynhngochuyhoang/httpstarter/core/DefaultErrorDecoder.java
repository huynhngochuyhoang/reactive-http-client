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
                .map(dataBuffer -> {
                    try {
                        byte[] chunk = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(chunk);
                        return chunk;
                    } finally {
                        DataBufferUtils.release(dataBuffer);
                    }
                })
                .reduce(new ByteArrayOutputStream(maxBytes), (output, chunk) -> {
                    int remaining = maxBytes - output.size();
                    if (remaining > 0) {
                        output.write(chunk, 0, Math.min(remaining, chunk.length));
                    }
                    return output;
                })
                .map(output -> output.toString(StandardCharsets.UTF_8))
                .defaultIfEmpty("");
    }
}
