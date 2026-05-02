package io.github.huynhngochuyhoang.httpstarter.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the <b>per-request response timeout</b> (in milliseconds) for a specific
 * client API method. Applied via {@code HttpClientRequest.responseTimeout()} on every
 * attempt, so each retry gets the full allowance.
 *
 * <p>This is independent of — and orthogonal to — the Netty channel-level safety-net
 * timeouts {@code reactive.http.network.network-read-timeout-ms} and
 * {@code network-write-timeout-ms} (default 60 s each). Size the safety nets above
 * the largest {@code @TimeoutMs} you expect; whichever fires first wins.
 *
 * <p>Value rules:
 * <ul>
 *   <li>{@code > 0}: apply this timeout for this method; accepted range is 1 to 1 800 000 ms
 *       (30 minutes). Values above the cap are rejected at parse time with
 *       {@link IllegalArgumentException}.</li>
 *   <li>{@code = 0}: disable the per-request timeout for this method (safety nets still apply)</li>
 *   <li>{@code < 0}: rejected at parse time with {@link IllegalArgumentException}</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TimeoutMs {
    long value();
}
