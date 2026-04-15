package io.github.huynhngochuyhoang.httpstarter.annotation;

import java.lang.annotation.*;

/** Marks a method parameter as the request body (serialised to JSON). */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Body {
}
