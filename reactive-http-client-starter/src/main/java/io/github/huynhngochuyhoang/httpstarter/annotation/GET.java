package io.github.huynhngochuyhoang.httpstarter.annotation;

import java.lang.annotation.*;

/** Maps a method to an HTTP GET request. The value is the path template (may contain {@code {variable}} placeholders). */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GET {
    String value();
}
