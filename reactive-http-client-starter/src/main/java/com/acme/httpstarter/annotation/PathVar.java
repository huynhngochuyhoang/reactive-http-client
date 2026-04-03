package com.acme.httpstarter.annotation;

import java.lang.annotation.*;

/** Binds a method parameter to a URI path variable (e.g., {@code /users/{id}}). */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PathVar {
    String value();
}
