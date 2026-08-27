package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientAutoConfiguration;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Rejects selected caching when builder behavior has no explicit cache-safety decision. */
final class CacheCustomizationValidator {

    private static final String STARTER_BUILDER_BEAN = "starterWebClientBuilder";

    private CacheCustomizationValidator() {
    }

    static void validate(ListableBeanFactory context,
                         Class<?> clientInterface,
                         String clientName,
                         MethodMetadataCache metadataCache,
                         ReactiveHttpClientProperties.ClientConfig clientConfig) {
        ReactiveHttpClientProperties.ClientConfig resolvedConfig = clientConfig != null
                ? clientConfig
                : new ReactiveHttpClientProperties.ClientConfig();
        Method selectedMethod = selectedMethod(clientInterface, metadataCache, resolvedConfig);
        if (selectedMethod == null) {
            return;
        }

        ReactiveHttpClientProperties.CacheConfig cache = resolvedConfig.getCache();
        Map<String, ReactiveHttpClientProperties.CacheCustomizationSafety> classifications =
                cache != null && cache.getCustomizations() != null
                        ? cache.getCustomizations()
                        : Map.of();
        Set<String> applicable = new LinkedHashSet<>();
        for (String beanName : beanNames(context, WebClientCustomizer.class)) {
            applicable.add(beanName);
        }
        for (String beanName : beanNames(context, ReactiveHttpClientCustomizer.class)) {
            ReactiveHttpClientCustomizer customizer = context.getBean(beanName, ReactiveHttpClientCustomizer.class);
            if (customizer.supports(clientName)) {
                applicable.add(beanName);
            }
        }
        for (String beanName : beanNames(context, WebClient.Builder.class)) {
            if (!isStarterManagedBuilder(context, beanName)) {
                applicable.add(beanName);
            }
        }

        for (String beanName : applicable) {
            ReactiveHttpClientProperties.CacheCustomizationSafety safety = classifications.get(beanName);
            if (safety != ReactiveHttpClientProperties.CacheCustomizationSafety.SAFE) {
                String reason = safety == null
                        ? "has no cache-safety classification"
                        : "is classified " + safety;
                throw new IllegalStateException(context(
                        clientInterface, clientName, selectedMethod, beanName, reason));
            }
        }
    }

    private static boolean isStarterManagedBuilder(ListableBeanFactory beanFactory, String beanName) {
        if (!STARTER_BUILDER_BEAN.equals(beanName)
                || !(beanFactory instanceof ConfigurableListableBeanFactory configurable)
                || !configurable.containsBeanDefinition(beanName)) {
            return false;
        }
        BeanDefinition builderDefinition = configurable.getMergedBeanDefinition(beanName);
        String factoryBeanName = builderDefinition.getFactoryBeanName();
        if (!STARTER_BUILDER_BEAN.equals(builderDefinition.getFactoryMethodName())
                || factoryBeanName == null
                || !configurable.containsBeanDefinition(factoryBeanName)) {
            return false;
        }
        BeanDefinition factoryDefinition = configurable.getMergedBeanDefinition(factoryBeanName);
        return ReactiveHttpClientAutoConfiguration.class.getName().equals(factoryDefinition.getBeanClassName());
    }

    private static Method selectedMethod(Class<?> clientInterface,
                                         MethodMetadataCache metadataCache,
                                         ReactiveHttpClientProperties.ClientConfig clientConfig) {
        for (Method method : clientInterface.getMethods()) {
            if (method.isDefault() || !java.lang.reflect.Modifier.isAbstract(method.getModifiers())) {
                continue;
            }
            RequestPlan plan = RequestPlan.from(metadataCache.get(method), clientInterface);
            String httpMethod = EffectiveCachePolicy.effectiveHttpMethod(plan, clientConfig);
            if (EffectiveCachePolicy.decide(plan, clientConfig, httpMethod).cacheable()) {
                return method;
            }
        }
        return null;
    }

    private static String[] beanNames(ListableBeanFactory beanFactory, Class<?> type) {
        return BeanFactoryUtils.beanNamesForTypeIncludingAncestors(beanFactory, type, true, false);
    }

    private static String context(Class<?> clientInterface,
                                  String clientName,
                                  Method method,
                                  String beanName,
                                  String reason) {
        return "Reactive HTTP client '" + clientName + "' concreteClient=" + clientInterface.getName()
                + " method=" + method.toGenericString() + " selects response caching, but WebClient customization bean '"
                + beanName + "' " + reason + ". Set reactive.http.clients." + clientName
                + ".cache.customizations." + beanName + "=SAFE only after verifying that the complete builder mutation "
                + "cannot bypass a per-caller gate or change cache key/value semantics.";
    }
}
