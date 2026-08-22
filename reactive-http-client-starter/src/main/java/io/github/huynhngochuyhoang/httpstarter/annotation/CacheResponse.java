package io.github.huynhngochuyhoang.httpstarter.annotation;

import java.lang.annotation.*;

/**
 * Selects a named response-cache policy for one declarative method.
 *
 * <p>The policy must be declared under
 * {@code reactive.http.clients.<name>.cache.policies}. Method selection takes
 * precedence over the client-wide {@code cache.policy} selection.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CacheResponse {

    String value();
}
