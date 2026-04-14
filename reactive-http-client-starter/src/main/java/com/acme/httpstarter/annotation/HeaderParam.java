package com.acme.httpstarter.annotation;

import java.lang.annotation.*;

/** Adds request headers. Null values are omitted. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HeaderParam {
    /**
     * Header name for scalar values. For {@code Map}-typed parameters this value is ignored.
     */
    String value() default "";
}
