package io.github.huynhngochuyhoang.httpstarter.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Default implementation that logs both request and response details.
 */
public class DefaultHttpExchangeLogger implements HttpExchangeLogger {

    private static final Logger log = LoggerFactory.getLogger(DefaultHttpExchangeLogger.class);
    private static final String REDACTED = "[REDACTED]";
    private static final String OMITTED = "[OMITTED]";
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization",
            "cookie",
            "set-cookie",
            "proxy-authorization",
            "x-api-key"
    );

    @Override
    public void log(HttpExchangeLogContext context) {
        if (!log.isInfoEnabled()) {
            return;
        }
        Map<String, String> requestHeaders = redactRequestHeaders(context.requestHeaders());
        Map<String, List<String>> responseHeaders = redactResponseHeaders(context.responseHeaders());
        Object requestBody = shouldLogBodies() ? context.requestBody() : OMITTED;
        Object responseBody = shouldLogBodies() ? context.responseBody() : OMITTED;

        if (context.error() == null) {
            log.info("[{}] {} {} reqHeaders={} reqBody={} respStatus={} respHeaders={} respBody={} duration={}ms",
                    context.clientName(),
                    context.httpMethod(),
                    context.pathTemplate(),
                    requestHeaders,
                    requestBody,
                    context.responseStatus(),
                    responseHeaders,
                    responseBody,
                    context.durationMs());
            return;
        }

        log.warn("[{}] {} {} reqHeaders={} reqBody={} respStatus={} respHeaders={} duration={}ms error={}",
                context.clientName(),
                context.httpMethod(),
                context.pathTemplate(),
                requestHeaders,
                requestBody,
                context.responseStatus(),
                responseHeaders,
                context.durationMs(),
                context.error().toString());
    }

    private boolean shouldLogBodies() {
        return log.isDebugEnabled();
    }

    private Map<String, String> redactRequestHeaders(Map<String, String> headers) {
        Map<String, String> redacted = new LinkedHashMap<>();
        headers.forEach((name, value) -> redacted.put(name, isSensitive(name) ? REDACTED : value));
        return redacted;
    }

    private Map<String, List<String>> redactResponseHeaders(Map<String, List<String>> headers) {
        Map<String, List<String>> redacted = new LinkedHashMap<>();
        headers.forEach((name, values) -> redacted.put(name, isSensitive(name) ? List.of(REDACTED) : values));
        return redacted;
    }

    private boolean isSensitive(String headerName) {
        return headerName != null && SENSITIVE_HEADERS.contains(headerName.toLowerCase(Locale.ROOT));
    }
}
