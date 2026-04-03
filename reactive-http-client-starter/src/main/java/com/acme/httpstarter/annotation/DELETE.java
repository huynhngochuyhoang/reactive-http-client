package com.acme.httpstarter.annotation;

import java.lang.annotation.*;

/** Maps a method to an HTTP DELETE request. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DELETE {
    String value();
}
