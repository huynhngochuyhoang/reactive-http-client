package io.github.huynhngochuyhoang.httpstarter.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.ref.WeakReference;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PropertiesBindingLifecycleTest {
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
