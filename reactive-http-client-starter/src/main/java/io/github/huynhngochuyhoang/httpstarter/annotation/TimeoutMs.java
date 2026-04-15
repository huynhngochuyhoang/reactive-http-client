package io.github.huynhngochuyhoang.httpstarter.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides timeout (in milliseconds) for a specific client API method.
 *
 * <p>Value rules:
 * <ul>
 *   <li>{@code > 0}: apply this timeout for this method</li>
 *   <li>{@code = 0}: disable timeout for this method</li>
 *   <li>{@code < 0}: invalid</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TimeoutMs {
    long value();
}
