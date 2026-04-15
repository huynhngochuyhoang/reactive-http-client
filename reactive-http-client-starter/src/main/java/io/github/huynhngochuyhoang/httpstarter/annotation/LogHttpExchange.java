package io.github.huynhngochuyhoang.httpstarter.annotation;

import io.github.huynhngochuyhoang.httpstarter.core.DefaultHttpExchangeLogger;
import io.github.huynhngochuyhoang.httpstarter.core.HttpExchangeLogger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables request/response logging for a reactive client method.
 *
 * <p>By default, {@link DefaultHttpExchangeLogger} is used. Consumers can
 * provide a custom logger implementation to inspect headers, record metrics,
 * or forward events to observability systems.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogHttpExchange {

    Class<? extends HttpExchangeLogger> logger() default DefaultHttpExchangeLogger.class;
}
