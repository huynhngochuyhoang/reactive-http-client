package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.exception.HttpClientException;
import io.github.huynhngochuyhoang.httpstarter.exception.RemoteServiceException;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

/**
 * Translates HTTP error responses into domain exceptions.
 * <ul>
 *   <li>4xx → {@link HttpClientException}</li>
 *   <li>5xx → {@link RemoteServiceException}</li>
 * </ul>
 */
public class DefaultErrorDecoder {

    /**
     * Returns a {@code Mono} that immediately signals an appropriate exception for the
     * given error response, or an empty Mono if the status code is not an error.
     */
    public Mono<? extends Throwable> decode(ClientResponse response) {
        int code = response.statusCode().value();
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> {
                    if (code >= 400 && code < 500) {
                        return (Throwable) new HttpClientException(code, body);
                    }
                    return new RemoteServiceException(code, body);
                });
    }
}
