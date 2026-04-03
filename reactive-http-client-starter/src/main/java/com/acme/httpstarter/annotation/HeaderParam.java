package com.acme.httpstarter.annotation;

import java.lang.annotation.*;

/** Adds a request header with the given name. Null values are omitted. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HeaderParam {
    String value();
}
