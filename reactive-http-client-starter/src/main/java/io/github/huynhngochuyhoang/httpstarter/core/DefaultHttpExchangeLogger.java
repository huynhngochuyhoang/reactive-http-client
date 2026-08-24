package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategories;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientFailureStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation that logs both request and response details.
 */
public class DefaultHttpExchangeLogger implements HttpExchangeLogger {

    private static final Logger log = LoggerFactory.getLogger(DefaultHttpExchangeLogger.class);
    private static final String REDACTED = "[REDACTED]";
    private static final String OMITTED = "[OMITTED]";

    @Override
    public void log(HttpExchangeLogContext context) {
        if (context.error() == null) {
            if (log.isInfoEnabled()) {
                logSuccess(context);
            }
        } else {
            if (log.isWarnEnabled()) {
                logError(context);
            }
        }
    }

    private void logSuccess(HttpExchangeLogContext context) {
        boolean logHeaders = shouldLogHeaders(context);
        Map<String, List<String>> inboundHeaders = logHeaders ? context.inboundHeaders() : Map.of();
        Map<String, String> requestHeaders = logHeaders ? redactRequestHeaders(context.requestHeaders()) : Map.of();
        Map<String, List<String>> responseHeaders = logHeaders ? redactResponseHeaders(context.responseHeaders()) : Map.of();
        Object requestBody = shouldLogBodies(context) ? context.requestBody() : OMITTED;
        Object responseBody = shouldLogBodies(context) ? context.responseBody() : OMITTED;
        if (context.cacheOutcome() != null) {
            log.info("[{}] {} {} inboundHeaders={} reqHeaders={} reqBody={} respStatus={} respHeaders={} respBody={} duration={}ms subscriptionAttemptCount={} cacheOutcome={}",
                    context.clientName(), context.httpMethod(), context.pathTemplate(), inboundHeaders,
                    requestHeaders, requestBody, context.responseStatus(), responseHeaders, responseBody,
                    context.durationMs(), context.subscriptionAttemptCount(), context.cacheOutcome().name());
            return;
        }
        log.info("[{}] {} {} inboundHeaders={} reqHeaders={} reqBody={} respStatus={} respHeaders={} respBody={} duration={}ms subscriptionAttemptCount={}",
                context.clientName(),
                context.httpMethod(),
                context.pathTemplate(),
                inboundHeaders,
                requestHeaders,
                requestBody,
                context.responseStatus(),
                responseHeaders,
                responseBody,
                context.durationMs(),
                context.subscriptionAttemptCount());
    }

    private void logError(HttpExchangeLogContext context) {
        boolean logHeaders = shouldLogHeaders(context);
        Map<String, List<String>> inboundHeaders = logHeaders ? context.inboundHeaders() : Map.of();
        Map<String, String> requestHeaders = logHeaders ? redactRequestHeaders(context.requestHeaders()) : Map.of();
        Map<String, List<String>> responseHeaders = logHeaders ? redactResponseHeaders(context.responseHeaders()) : Map.of();
        Object requestBody = shouldLogBodies(context) ? context.requestBody() : OMITTED;
        Object responseBody = shouldLogBodies(context) ? context.responseBody() : OMITTED;
        ErrorCategory errorCategory = ErrorCategories.from(context.error(), context.responseStatus());
        HttpClientFailureStage failureStage = context.failureStage();
        if (context.cacheOutcome() != null) {
            log.warn("[{}] {} {} inboundHeaders={} reqHeaders={} reqBody={} respStatus={} respHeaders={} respBody={} duration={}ms subscriptionAttemptCount={} errorType={} errorCategory={} failureStage={} cacheOutcome={}",
                    context.clientName(), context.httpMethod(), context.pathTemplate(), inboundHeaders,
                    requestHeaders, requestBody, context.responseStatus(), responseHeaders, responseBody,
                    context.durationMs(), context.subscriptionAttemptCount(), context.error().getClass().getName(),
                    errorCategory != null ? errorCategory.name() : "none",
                    failureStage != null ? failureStage.name() : "none", context.cacheOutcome().name());
            return;
        }
        log.warn("[{}] {} {} inboundHeaders={} reqHeaders={} reqBody={} respStatus={} respHeaders={} respBody={} duration={}ms subscriptionAttemptCount={} errorType={} errorCategory={} failureStage={}",
                context.clientName(),
                context.httpMethod(),
                context.pathTemplate(),
                inboundHeaders,
                requestHeaders,
                requestBody,
                context.responseStatus(),
                responseHeaders,
                responseBody,
                context.durationMs(),
                context.subscriptionAttemptCount(),
                context.error().getClass().getName(),
                errorCategory != null ? errorCategory.name() : "none",
                failureStage != null ? failureStage.name() : "none");
    }

    private boolean shouldLogHeaders(HttpExchangeLogContext context) {
        ReactiveHttpClientProperties.LogPreset preset = context.logPreset() != null
                ? context.logPreset()
                : ReactiveHttpClientProperties.LogPreset.METADATA_ONLY;
        return preset == ReactiveHttpClientProperties.LogPreset.HEADERS
                || preset == ReactiveHttpClientProperties.LogPreset.BODIES;
    }

    private boolean shouldLogBodies(HttpExchangeLogContext context) {
        return context.logPreset() == ReactiveHttpClientProperties.LogPreset.BODIES;
    }

    private Map<String, String> redactRequestHeaders(Map<String, String> headers) {
        Map<String, String> redacted = new LinkedHashMap<>();
        headers.forEach((name, value) -> redacted.put(name, SensitiveHeaders.isSensitive(name) ? REDACTED : value));
        return redacted;
    }

    private Map<String, List<String>> redactResponseHeaders(Map<String, List<String>> headers) {
        Map<String, List<String>> redacted = new LinkedHashMap<>();
        headers.forEach((name, values) -> redacted.put(name, SensitiveHeaders.isSensitive(name) ? List.of(REDACTED) : values));
        return redacted;
    }
}
