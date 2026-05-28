package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientFactoryBean;
import io.github.huynhngochuyhoang.httpstarter.enable.EnableReactiveHttpClients;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Runtime hints for the starter's annotation and configuration-property model.
 */
public class ReactiveHttpClientRuntimeHints implements RuntimeHintsRegistrar {

    private static final Class<?>[] ANNOTATION_TYPES = {
            ReactiveHttpClient.class,
            EnableReactiveHttpClients.class,
            GET.class,
            POST.class,
            PUT.class,
            DELETE.class,
            PATCH.class,
            PathVar.class,
            QueryParam.class,
            HeaderParam.class,
            IdempotencyKey.class,
            Body.class,
            MultipartBody.class,
            FormField.class,
            FormFile.class,
            ApiName.class,
            ApiRef.class,
            TimeoutMs.class,
            Retry.class,
            CircuitBreaker.class,
            Bulkhead.class,
            RateLimiter.class,
            LogHttpExchange.class
    };

    private static final Class<?>[] CONFIGURATION_TYPES = {
            ReactiveHttpClientProperties.class,
            ReactiveHttpClientProperties.NetworkConfig.class,
            ReactiveHttpClientProperties.ConnectionPoolConfig.class,
            ReactiveHttpClientProperties.ProxyConfig.class,
            ReactiveHttpClientProperties.ProxyConfig.Type.class,
            ReactiveHttpClientProperties.TlsConfig.class,
            ReactiveHttpClientProperties.ClientConfig.class,
            ReactiveHttpClientProperties.AuthConfig.class,
            ReactiveHttpClientProperties.OAuth2ClientCredentialsAuthConfig.class,
            ReactiveHttpClientProperties.AwsSigV4AuthConfig.class,
            ReactiveHttpClientProperties.ApiConfig.class,
            ReactiveHttpClientProperties.ResilienceConfig.class,
            ReactiveHttpClientProperties.ObservabilityConfig.class,
            ReactiveHttpClientProperties.HistogramConfig.class,
            ReactiveHttpClientProperties.HealthConfig.class,
            ReactiveHttpClientProperties.CorrelationIdConfig.class,
            ReactiveHttpClientProperties.InboundHeadersConfig.class
    };

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        for (Class<?> annotationType : ANNOTATION_TYPES) {
            hints.reflection().registerType(annotationType, MemberCategory.INTROSPECT_DECLARED_METHODS);
        }
        for (Class<?> configurationType : CONFIGURATION_TYPES) {
            hints.reflection().registerType(configurationType,
                    MemberCategory.INTROSPECT_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INTROSPECT_PUBLIC_METHODS,
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.DECLARED_FIELDS);
        }
        hints.reflection().registerType(ReactiveHttpClientFactoryBean.class,
                MemberCategory.INTROSPECT_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INTROSPECT_PUBLIC_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.DECLARED_FIELDS);
    }
}
