package io.github.huynhngochuyhoang.httpstarter.annotation;

import java.lang.annotation.*;

/**
 * Gives one method parameter a stable name for explicit response-cache
 * partitioning.
 *
 * <p>The selected cache policy must include this name in its
 * {@code vary-by-parameters} list. Path and query parameters are always cache
 * key inputs and do not need this annotation.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CacheKey {

    String value();
}
