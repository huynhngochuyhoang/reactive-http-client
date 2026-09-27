package io.github.huynhngochuyhoang.httpstarter.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.SimpleThreadScope;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.ref.WeakReference;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PropertiesBindingLifecycleTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void normalRefreshStopsTrackingLivePrototypeAndScopedInstances(boolean customScope) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(ReactiveHttpClientAutoConfiguration.class);
            var scope = new SimpleThreadScope();
            context.getBeanFactory().registerScope("test", scope);
            context.registerBean("selectedProperties", ReactiveHttpClientProperties.class,
                    ReactiveHttpClientProperties::new, definition -> {
                        definition.setPrimary(true);
                        definition.setScope(customScope ? "test" : "prototype");
                    });
            context.refresh();
            var lifecycle = context.getBean(PropertiesBindingLifecycle.class);
            var liveValues = new java.util.ArrayList<ReactiveHttpClientProperties>();
            var binder = context.getBean(ConfigurationPropertiesBindingPostProcessor.BEAN_NAME,
                    ConfigurationPropertiesBindingPostProcessor.class);
            for (int i = 0; i < 100; i++) {
                if (customScope) scope.remove("selectedProperties");
                var properties = context.getBean("selectedProperties", ReactiveHttpClientProperties.class);
                liveValues.add(properties);
                lifecycle.bound(properties, binder);
            }

            assertThat(liveValues).hasSize(100);
            assertThat((List<?>) ReflectionTestUtils.getField(lifecycle, "observations")).isEmpty();
            assertThat(lifecycle.wasBound(liveValues.getFirst(), binder)).isNull();
        }
    }

    @Test
    void aotRefreshKeepsCreationHistoryUntilItsContextCloses() {
        PropertiesBindingLifecycle lifecycle;
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(ReactiveHttpClientAutoConfiguration.class);
            context.refreshForAotProcessing(new RuntimeHints());
            lifecycle = context.getBean(PropertiesBindingLifecycle.class);
            var early = context.getBean(ReactiveHttpClientProperties.class);
            var binder = context.getBean(ConfigurationPropertiesBindingPostProcessor.BEAN_NAME,
                    ConfigurationPropertiesBindingPostProcessor.class);
            assertThat(lifecycle.wasBound(early, binder)).isFalse();
            assertThat((List<?>) ReflectionTestUtils.getField(lifecycle, "observations")).isNotEmpty();
        }
        assertThat((List<?>) ReflectionTestUtils.getField(lifecycle, "observations")).isEmpty();
        lifecycle.postProcessBeforeInitialization(new ReactiveHttpClientProperties(), "afterClose");
        assertThat((List<?>) ReflectionTestUtils.getField(lifecycle, "observations")).isEmpty();
    }

    @Test
    void recordsTheDelegateInstalledDuringEachInstancesCreation() {
        var factory = new DefaultListableBeanFactory();
        var lifecycle = new PropertiesBindingLifecycle(factory);
        var binder = new ConfigurationPropertiesBindingPostProcessor();
        var early = new EqualProperties();
        var later = new EqualProperties();
        lifecycle.postProcessBeforeInitialization(early, "properties");
        lifecycle.postProcessAfterInitialization(early, "properties");
        factory.addBeanPostProcessor(binder);
        lifecycle.postProcessBeforeInitialization(later, "properties");
        lifecycle.postProcessAfterInitialization(later, "properties");

        assertThat(lifecycle.wasBound(early, binder)).isFalse();
        assertThat(lifecycle.wasBound(later, binder)).isTrue();
        assertThat(lifecycle.wasBound(new EqualProperties(), binder)).isNull();
        lifecycle.bound(early, binder);
        assertThat(lifecycle.wasBound(early, binder)).isTrue();
        assertThat(lifecycle.wasBound(early, new ConfigurationPropertiesBindingPostProcessor())).isFalse();
    }

    @Test
    void tracksReturnedWrappersWithoutOverwritingEarlierObservations() {
        var factory = new DefaultListableBeanFactory();
        var lifecycle = new PropertiesBindingLifecycle(factory);
        var binder = new ConfigurationPropertiesBindingPostProcessor();
        factory.addBeanPostProcessor(binder);
        var original = new ReactiveHttpClientProperties();
        var replacement = new ReactiveHttpClientProperties();
        lifecycle.postProcessBeforeInitialization(original, "properties");
        lifecycle.postProcessAfterInitialization(replacement, "properties");
        factory.getBeanPostProcessors().remove(binder);
        lifecycle.postProcessAfterInitialization(original, "properties");

        assertThat(lifecycle.wasBound(original, binder)).isTrue();
        assertThat(lifecycle.wasBound(replacement, binder)).isTrue();
    }

    @Test
    void observationsHoldOnlyWeakBeanAndDelegateReferencesAndPruneClearedBeans() {
        var factory = new DefaultListableBeanFactory();
        var lifecycle = new PropertiesBindingLifecycle(factory);
        var binder = new ConfigurationPropertiesBindingPostProcessor();
        factory.addBeanPostProcessor(binder);
        var properties = new ReactiveHttpClientProperties();
        lifecycle.postProcessBeforeInitialization(properties, "properties");
        var observations = (List<?>) ReflectionTestUtils.getField(lifecycle, "observations");
        assertThat(observations).hasSize(1);
        var observation = observations.getFirst();
        var beanReference = (WeakReference<?>) ReflectionTestUtils.getField(observation, "bean");
        var delegates = (List<?>) ReflectionTestUtils.getField(observation, "delegates");
        assertThat(beanReference.get()).isSameAs(properties);
        assertThat(delegates).hasSize(1);
        assertThat(((WeakReference<?>) delegates.getFirst()).get()).isSameAs(binder);
        // Exercise cleared-reference cleanup deterministically, without requiring System.gc().
        beanReference.clear();
        assertThat(lifecycle.wasBound(properties, binder)).isNull();
        assertThat(observations).isEmpty();
    }

    private static final class EqualProperties extends ReactiveHttpClientProperties {
        @Override public boolean equals(Object other) { return other instanceof EqualProperties; }
        @Override public int hashCode() { return 1; }
    }
}
