package io.github.huynhngochuyhoang.httpstarter.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * Marks an interface as a declarative reactive HTTP client.
 *
 * <p>The {@link Component} meta-annotation is present so that Spring IDE plugins
 * (e.g. IntelliJ IDEA's Spring support) recognise any {@code @ReactiveHttpClient}-annotated
 * interface as a managed bean and suppress false-positive "could not autowire" warnings.
 * The actual bean is registered at runtime by
 * {@link io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientsRegistrar} via
 * {@link io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientFactoryBean}.
 *
 * <p>Example:
 * <pre>{@code
 * @ReactiveHttpClient(name = "user-service")
 * public interface UserApiClient { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface ReactiveHttpClient {

    /** Service / client name – used to look up config under {@code reactive.http.clients.<name>}. */
    String name();

    /** Optional hard-coded base URL. Takes precedence over the property file entry. */
    String baseUrl() default "";
}
