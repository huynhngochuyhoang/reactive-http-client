package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.annotation.CacheResponse;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.core.MethodMetadataCache;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientFactoryBean;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.OrderComparator;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class EffectiveSelectionAotReviewTest {
    enum Preference { PRIMARY, NON_FALLBACK, PRIORITY, DEFAULT }

    @ParameterizedTest
    @EnumSource(Preference.class)
    void aotPropertiesSelectionMatchesRuntimePreference(Preference preference) {
        try (var context = new GenericApplicationContext()) {
            var secondary = new ReactiveHttpClientProperties();
            var preferred = new ReactiveHttpClientProperties();
            var config = new ReactiveHttpClientProperties.ClientConfig();
            var policy = new ReactiveHttpClientProperties.CachePolicyConfig();
            policy.setTtlMs(60_000L);
            policy.setMaximumSize(16L);
            policy.setSharedResponse(true);
            config.getCache().getPolicies().put("chosen", policy);
            preferred.getClients().put("selection", config);
            var first = new RootBeanDefinition(ReactiveHttpClientProperties.class, () -> secondary);
            var second = new RootBeanDefinition(ReactiveHttpClientProperties.class, () -> preferred);
            switch (preference) {
                case PRIMARY -> second.setPrimary(true);
                case NON_FALLBACK -> first.setFallback(true);
                case DEFAULT -> first.setDefaultCandidate(false);
                case PRIORITY -> context.getDefaultListableBeanFactory().setDependencyComparator(new OrderComparator() {
                    @Override public Integer getPriority(Object candidate) {
                        return candidate == preferred ? 0 : candidate == secondary ? 1 : null;
                    }
                });
            }
            context.registerBeanDefinition("secondary", first);
            context.registerBeanDefinition("preferred", second);
            var client = new RootBeanDefinition(ReactiveHttpClientFactoryBean.class);
            client.setLazyInit(true);
            client.getPropertyValues().add("type", Client.class);
            client.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, Client.class);
            context.registerBeanDefinition("client", client);
            context.refresh();
            assertThat(context.getBeanProvider(ReactiveHttpClientProperties.class).getIfAvailable()).isSameAs(preferred);
            new MethodMetadataCache().validateDeclarativeCachePolicies(Client.class, "selection", config);
            var processor = new ReactiveHttpClientBeanFactoryInitializationAotProcessor(context.getEnvironment());
            assertThat(processor.processAheadOfTime(context.getDefaultListableBeanFactory())).isNotNull();
            assertThat(context.getBeanFactory().containsSingleton("client")).isFalse();
            assertThat(context.getBean(Client.class)).isNotNull();
        }
    }

    @ReactiveHttpClient(name = "selection", baseUrl = "http://localhost")
    interface Client { @GET("/read") @CacheResponse("chosen") Mono<String> get(); }
}
