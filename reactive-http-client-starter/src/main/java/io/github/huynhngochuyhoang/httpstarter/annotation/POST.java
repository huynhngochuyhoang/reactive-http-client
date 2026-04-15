package io.github.huynhngochuyhoang.httpstarter.annotation;

import java.lang.annotation.*;

/** Maps a method to an HTTP POST request. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface POST {
    String value();
}
