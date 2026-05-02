package io.github.huynhngochuyhoang.httpstarter.core;

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
        Map<String, String> requestHeaders = redactRequestHeaders(context.requestHeaders());
        Map<String, List<String>> responseHeaders = redactResponseHeaders(context.responseHeaders());
        Object requestBody = shouldLogBodies() ? context.requestBody() : OMITTED;
        Object responseBody = shouldLogBodies() ? context.responseBody() : OMITTED;
        log.info("[{}] {} {} inboundHeaders={} reqHeaders={} reqBody={} respStatus={} respHeaders={} respBody={} duration={}ms",
                context.clientName(),
                context.httpMethod(),
                context.pathTemplate(),
                context.inboundHeaders(),
                requestHeaders,
                requestBody,
                context.responseStatus(),
                responseHeaders,
                responseBody,
                context.durationMs());
    }

    private void logError(HttpExchangeLogContext context) {
        Map<String, String> requestHeaders = redactRequestHeaders(context.requestHeaders());
        Map<String, List<String>> responseHeaders = redactResponseHeaders(context.responseHeaders());
        Object requestBody = shouldLogBodies() ? context.requestBody() : OMITTED;
        Object responseBody = shouldLogBodies() ? context.responseBody() : OMITTED;
        log.warn("[{}] {} {} inboundHeaders={} reqHeaders={} reqBody={} respStatus={} respHeaders={} respBody={} duration={}ms error={}",
                context.clientName(),
                context.httpMethod(),
                context.pathTemplate(),
                context.inboundHeaders(),
                requestHeaders,
                requestBody,
                context.responseStatus(),
                responseHeaders,
                responseBody,
                context.durationMs(),
                context.error().toString());
    }

    private boolean shouldLogBodies() {
        return log.isDebugEnabled();
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
