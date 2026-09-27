package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.core.MethodMetadataCache;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientFactoryBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AotMetadataSelectionContractTest {
    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void unresolvedMetadataFactoryFailsExplicitlyUntilItsProductTypeIsSupplied(boolean parent, boolean knownFallback) {
        var runtime = new Fixture(parent, knownFallback);
        assertThat(runtime.factory.getBeanProvider(MethodMetadataCache.class).getObject()).isSameAs(runtime.metadata);
        assertThat(runtime.factories).hasValue(1);
        assertThat(runtime.products).hasValue(1);
        var aot = new Fixture(parent, knownFallback);
        var processor = new ReactiveHttpClientBeanFactoryInitializationAotProcessor();

        assertThatThrownBy(() -> processor.processAheadOfTime(aot.factory))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MethodMetadataCache")
                .hasMessageContaining("metadataReplacement").hasMessageContaining("factoryBeanObjectType");

        assertThat(aot.factories).hasValue(0);
        assertThat(aot.products).hasValue(0);
        assertThat(aot.metadata.validations).hasValue(0);
        assertThat(aot.factory.containsSingleton("client")).isFalse();
        aot.owner.getBeanDefinition("metadataReplacement")
                .setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, TrackingMetadata.class);
        aot.owner.clearMetadataCache();
        assertThat(processor.processAheadOfTime(aot.factory)).isNotNull();
        assertThat(aot.metadata.validations).hasValue(1);
        assertThat(aot.factories).hasValue(1);
        assertThat(aot.products).hasValue(1);
        assertThat(aot.factory.containsSingleton("client")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void previouslyInitializedRawMetadataFactoryIsSelectedWithoutAnotherFactory(boolean parent) {
        var fixture = new Fixture(parent, true);
        fixture.owner.getBean("&metadataReplacement");
        assertThat(fixture.factories).hasValue(1);
        assertThat(fixture.products).hasValue(0);

        assertThat(new ReactiveHttpClientBeanFactoryInitializationAotProcessor()
                .processAheadOfTime(fixture.factory)).isNotNull();

        assertThat(fixture.factories).hasValue(1);
        assertThat(fixture.products).hasValue(1);
        assertThat(fixture.metadata.validations).hasValue(1);
    }

    @Test
    void localMetadataSelectionDoesNotInspectAnUnusedParentFactory() {
        var fixture = new Fixture(true, false);
        var local = new TrackingMetadata();
        fixture.factory.registerSingleton("localMetadata", local);

        assertThat(new ReactiveHttpClientBeanFactoryInitializationAotProcessor()
                .processAheadOfTime(fixture.factory)).isNotNull();

        assertThat(local.validations).hasValue(1);
        assertThat(fixture.factories).hasValue(0);
        assertThat(fixture.products).hasValue(0);
    }

    private static final class Fixture {
        final DefaultListableBeanFactory owner = new DefaultListableBeanFactory();
        final DefaultListableBeanFactory factory;
        final AtomicInteger factories = new AtomicInteger();
        final AtomicInteger products = new AtomicInteger();
        final TrackingMetadata metadata = new TrackingMetadata();

        Fixture(boolean parent, boolean knownFallback) {
            factory = parent ? new DefaultListableBeanFactory(owner) : owner;
            var replacement = new RootBeanDefinition(RawMetadataFactory.class, () -> {
                factories.incrementAndGet();
                return new RawMetadataFactory(metadata, products);
            });
            replacement.setPrimary(true);
            replacement.setLazyInit(true);
            owner.registerBeanDefinition("metadataReplacement", replacement);
            if (knownFallback) owner.registerSingleton("fallbackMetadata", new MethodMetadataCache());
            var client = new RootBeanDefinition(ReactiveHttpClientFactoryBean.class, () -> {
                throw new AssertionError("AOT must not create the client factory");
            });
            client.getPropertyValues().add("type", Client.class);
            client.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, Client.class);
            factory.registerBeanDefinition("client", client);
        }
    }

    @SuppressWarnings("rawtypes")
    static class RawMetadataFactory implements FactoryBean {
        private final MethodMetadataCache metadata;
        private final AtomicInteger products;
        RawMetadataFactory(MethodMetadataCache metadata, AtomicInteger products) {
            this.metadata = metadata;
            this.products = products;
        }
        @Override public Object getObject() { products.incrementAndGet(); return metadata; }
        @Override public Class<?> getObjectType() { return TrackingMetadata.class; }
    }

    static class TrackingMetadata extends MethodMetadataCache {
        final AtomicInteger validations = new AtomicInteger();
        @Override public void validateDeclarativeRequestParameters(Class<?> type, String name) {
            validations.incrementAndGet();
            super.validateDeclarativeRequestParameters(type, name);
        }
    }

    @ReactiveHttpClient(name = "metadata-selection", baseUrl = "http://localhost")
    interface Client { @GET("/read") Mono<String> read(); }
}
