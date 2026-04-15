package io.github.huynhngochuyhoang.httpstarter.annotation;

import java.lang.annotation.*;

/** Maps a method to an HTTP PUT request. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PUT {
    String value();
}
