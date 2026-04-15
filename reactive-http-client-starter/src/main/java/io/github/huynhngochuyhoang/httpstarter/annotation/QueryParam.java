package io.github.huynhngochuyhoang.httpstarter.annotation;

import java.lang.annotation.*;

/** Binds a method parameter to a URL query parameter. Null values are omitted. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface QueryParam {
    String value();
}
