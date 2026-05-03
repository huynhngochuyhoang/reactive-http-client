package io.github.huynhngochuyhoang.httpstarter.core;

import org.springframework.web.reactive.function.client.WebClient;

/**
 * Strategy interface for customizing a specific reactive HTTP client's
 * {@link WebClient.Builder} before the client is built.
 *
 * <p>Register implementations as Spring beans. During proxy construction,
 * {@link ReactiveHttpClientFactoryBean} collects all {@code ReactiveHttpClientCustomizer}
 * beans from the application context (respecting {@link org.springframework.core.annotation.Order}
 * / {@link org.springframework.core.Ordered}), filters them through
 * {@link #supports(String)}, and applies the remaining ones to the builder in order.
 *
 * <p>A typical use-case is adding a client-specific {@link org.springframework.web.reactive.function.client.ExchangeFilterFunction}
 * without having to recreate a raw {@link WebClient}:
 *
 * <pre>{@code
 * @Component
 * public class AuditFilterCustomizer implements ReactiveHttpClientCustomizer {
 *
 *     @Override
 *     public boolean supports(String clientName) {
 *         return "order-service".equals(clientName);
 *     }
 *
 *     @Override
 *     public void customize(WebClient.Builder builder) {
 *         builder.filter((request, next) -> {
 *             // audit logic here
 *             return next.exchange(request);
 *         });
 *     }
 * }
 * }</pre>
 *
 * <p>To apply the customizer to <em>all</em> clients, simply omit the
 * {@link #supports(String)} override — the default implementation returns {@code true}
 * for every client name.
 */
@FunctionalInterface
public interface ReactiveHttpClientCustomizer {

    /**
     * Whether this customizer should be applied to the client identified by
     * {@code clientName} (the value of {@link io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient#name()}).
     *
     * <p>The default implementation returns {@code true}, meaning the customizer
     * is applied to every reactive HTTP client.
     *
     * @param clientName the logical name of the reactive HTTP client being built
     * @return {@code true} to apply this customizer, {@code false} to skip it
     */
    default boolean supports(String clientName) {
        return true;
    }

    /**
     * Customize the {@link WebClient.Builder} for the given reactive HTTP client.
     *
     * <p>Called after all built-in filters (correlation-ID propagation, outbound auth,
     * exchange logging) have been applied, so custom filters added here run after
     * the built-in ones in the filter chain.
     *
     * @param builder the builder for the reactive HTTP client being constructed
     */
    void customize(WebClient.Builder builder);
}
