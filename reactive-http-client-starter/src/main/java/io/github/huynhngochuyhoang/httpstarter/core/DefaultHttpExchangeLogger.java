package io.github.huynhngochuyhoang.httpstarter.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation that logs both request and response details.
 */
public class DefaultHttpExchangeLogger implements HttpExchangeLogger {

    private static final Logger log = LoggerFactory.getLogger(DefaultHttpExchangeLogger.class);

    @Override
    public void log(HttpExchangeLogContext context) {
        if (!log.isInfoEnabled()) {
            return;
        }

        if (context.error() == null) {
            log.info("[{}] {} {} reqHeaders={} reqBody={} respStatus={} respHeaders={} respBody={} duration={}ms",
                    context.clientName(),
                    context.httpMethod(),
                    context.pathTemplate(),
                    context.requestHeaders(),
                    context.requestBody(),
                    context.responseStatus(),
                    context.responseHeaders(),
                    context.responseBody(),
                    context.durationMs());
            return;
        }

        log.warn("[{}] {} {} reqHeaders={} reqBody={} respStatus={} respHeaders={} duration={}ms error={}",
                context.clientName(),
                context.httpMethod(),
                context.pathTemplate(),
                context.requestHeaders(),
                context.requestBody(),
                context.responseStatus(),
                context.responseHeaders(),
                context.durationMs(),
                context.error().toString());
    }
}
