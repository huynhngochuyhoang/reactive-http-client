package io.github.huynhngochuyhoang.httpstarter.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.huynhngochuyhoang.httpstarter.exception.ProblemDetailHttpClientException;
import io.github.huynhngochuyhoang.httpstarter.exception.ProblemDetailRemoteServiceException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import java.util.Objects;
import java.util.Optional;

/**
 * Opt-in mapper for RFC 9457 application/problem+json error responses.
 * Register this class as an {@link ErrorResponseMapper} bean to enable it.
 */
public class ProblemDetailErrorResponseMapper implements ErrorResponseMapper {

    private final ObjectMapper objectMapper;

    public ProblemDetailErrorResponseMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public Optional<? extends Throwable> map(ErrorResponseContext context) throws Exception {
        MediaType contentType = context.responseHeaders().getContentType();
        if (contentType == null || !MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(contentType)) {
            return Optional.empty();
        }

        int statusCode = context.statusCode();
        if (statusCode < 400) {
            return Optional.empty();
        }

        ProblemDetail problemDetail = objectMapper.readValue(context.responseBody(), ProblemDetail.class);
        if (statusCode < 500) {
            return Optional.of(new ProblemDetailHttpClientException(
                    statusCode,
                    context.responseBody(),
                    context.requestMethod(),
                    context.requestUrl(),
                    problemDetail));
        }
        return Optional.of(new ProblemDetailRemoteServiceException(
                statusCode,
                context.responseBody(),
                context.requestMethod(),
                context.requestUrl(),
                problemDetail));
    }
}
