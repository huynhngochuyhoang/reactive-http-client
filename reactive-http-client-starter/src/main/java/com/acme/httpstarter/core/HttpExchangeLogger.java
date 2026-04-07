package com.acme.httpstarter.core;

/**
 * Extension point for request/response logging of reactive HTTP client calls.
 */
public interface HttpExchangeLogger {

    void log(HttpExchangeLogContext context);
}
