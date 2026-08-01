package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientFactoryBean;
import io.github.huynhngochuyhoang.httpstarter.enable.EnableReactiveHttpClients;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.factory.support.FactoryBeanRegistrySupport;

import java.lang.reflect.Modifier;

/**
 * Runtime hints for the starter's annotation and configuration-property model.
 */
public class ReactiveHttpClientRuntimeHints implements RuntimeHintsRegistrar {

    static final String POM_PROPERTIES_RESOURCE =
            "META-INF/maven/io.github.huynhngochuyhoang/reactive-http-client-starter/pom.properties";

    private static final Class<?>[] ANNOTATION_TYPES = {
            ReactiveHttpClient.class,
            EnableReactiveHttpClients.class,
            GET.class,
            POST.class,
            PUT.class,
            DELETE.class,
            PATCH.class,
            HEAD.class,
            OPTIONS.class,
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
            ReactiveHttpClientProperties.LogPreset.class,
            ReactiveHttpClientProperties.AuthConfig.class,
            ReactiveHttpClientProperties.OAuth2ClientCredentialsAuthConfig.class,
            ReactiveHttpClientProperties.OAuth2TokenServiceConfig.class,
            ReactiveHttpClientProperties.AwsSigV4AuthConfig.class,
            ReactiveHttpClientProperties.ApiConfig.class,
            ReactiveHttpClientProperties.ResilienceConfig.class,
            ReactiveHttpClientProperties.ObservabilityConfig.class,
            ReactiveHttpClientProperties.HistogramConfig.class,
            ReactiveHttpClientProperties.HealthConfig.class,
            ReactiveHttpClientProperties.DiagnosticsEndpointConfig.class,
            ReactiveHttpClientProperties.CorrelationIdConfig.class,
            ReactiveHttpClientProperties.InboundHeadersConfig.class
    };

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        for (Class<?> annotationType : ANNOTATION_TYPES) {
            registerDeclaredMethods(hints, annotationType);
        }
        for (Class<?> configurationType : CONFIGURATION_TYPES) {
            registerPublicMembers(hints, configurationType);
        }
        registerPublicMembers(hints, ReactiveHttpClientFactoryBean.class);
        registerFactoryBeanCacheLookup(hints);
        hints.resources().registerPattern(POM_PROPERTIES_RESOURCE);
    }

    private static void registerFactoryBeanCacheLookup(RuntimeHints hints) {
        try {
            hints.reflection().registerMethod(
                    FactoryBeanRegistrySupport.class.getDeclaredMethod(
                            "getCachedObjectForFactoryBean", String.class),
                    ExecutableMode.INVOKE);
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void registerPublicMembers(RuntimeHints hints, Class<?> type) {
        for (var constructor : type.getDeclaredConstructors()) {
            if (Modifier.isPublic(constructor.getModifiers())) {
                hints.reflection().registerConstructor(constructor, ExecutableMode.INVOKE);
            }
        }
        registerDeclaredMethods(hints, type);
    }

    private static void registerDeclaredMethods(RuntimeHints hints, Class<?> type) {
        hints.reflection().registerType(type, typeHint -> {});
        for (var method : type.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                hints.reflection().registerMethod(method, ExecutableMode.INVOKE);
            }
        }
    }
}
