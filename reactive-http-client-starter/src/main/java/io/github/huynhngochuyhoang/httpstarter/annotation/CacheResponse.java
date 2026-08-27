package io.github.huynhngochuyhoang.httpstarter.annotation;

import java.lang.annotation.*;

/**
 * Selects a named response-cache policy for one declarative method.
 *
 * <p>The policy must be declared under
 * {@code reactive.http.clients.<name>.cache.policies}. Method selection takes
 * precedence over the client-wide {@code cache.policy} selection.
 *
 * <p>{@code GET} methods need no additional intent declaration. A selected
 * non-{@code GET} method must set {@link #semanticRead()} to {@code true}. That
 * flag is an application guarantee that returning a cached response, and
 * therefore omitting a downstream invocation, cannot omit a required side
 * effect. It does not declare write idempotency, retry safety, replay safety,
 * or cache invalidation behavior.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CacheResponse {

    String value();

    /**
     * Acknowledges that this specific non-{@code GET} method is a semantic read.
     *
     * <p>The default is {@code false}. Client-wide cache selection cannot set
     * this method-specific guarantee.
     */
    boolean semanticRead() default false;
}
