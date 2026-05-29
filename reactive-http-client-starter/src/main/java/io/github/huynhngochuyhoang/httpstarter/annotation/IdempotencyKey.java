package io.github.huynhngochuyhoang.httpstarter.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Adds an outbound idempotency key without requiring raw header plumbing.
 *
 * <p>On a parameter, the non-null argument value is written as the idempotency
 * header. On a method, the starter generates one key per invocation when no
 * caller-supplied, default, or context idempotency key is already present.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.PARAMETER})
public @interface IdempotencyKey {

    /**
     * Header name used for the idempotency key.
     */
    String value() default "Idempotency-Key";
}
