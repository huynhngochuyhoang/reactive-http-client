package io.github.huynhngochuyhoang.httpstarter.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface CacheResponse {
    String value() default "";

    boolean semanticRead() default false;
}
