package com.acme.httpstarter.annotation;

import java.lang.annotation.*;

/**
 * Marks an interface as a declarative reactive HTTP client.
 * <p>
 * Example:
 * <pre>{@code
 * @ReactiveHttpClient(name = "user-service")
 * public interface UserApiClient { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ReactiveHttpClient {

    /** Service / client name – used to look up config under {@code acme.http.clients.<name>}. */
    String name();

    /** Optional hard-coded base URL. Takes precedence over the property file entry. */
    String baseUrl() default "";
}
