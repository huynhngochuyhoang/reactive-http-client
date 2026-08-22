package io.github.huynhngochuyhoang.httpstarter.annotation;

import java.lang.annotation.*;

/** Disables response caching for one method when its client selects a cache policy. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CacheDisabled {
}
