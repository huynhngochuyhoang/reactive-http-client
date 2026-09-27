package io.github.huynhngochuyhoang.httpstarter.config;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

// AOT refresh installs merged-definition processors before running initialization
// AOT processors. Observe creation here, including targets outside the singleton cache.
final class PropertiesBindingLifecycle implements MergedBeanDefinitionPostProcessor, SmartInitializingSingleton, DisposableBean {
    private final AbstractBeanFactory factory;
    private final ArrayList<Observation> observations = new ArrayList<>();
    private volatile boolean tracking = true;

    PropertiesBindingLifecycle(AbstractBeanFactory factory) {
        this.factory = factory;
    }

    @Override
    public void postProcessMergedBeanDefinition(RootBeanDefinition definition, Class<?> type, String name) {
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String name) {
        if (tracking) observe(bean);
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String name) {
        if (tracking) observe(bean);
        return bean;
    }

    private synchronized void observe(Object bean) {
        if (!tracking || !(bean instanceof ReactiveHttpClientProperties) || observation(bean) != null) return;
        var delegates = new ArrayList<WeakReference<ConfigurationPropertiesBindingPostProcessor>>();
        for (var processor : factory.getBeanPostProcessors()) {
            if (processor instanceof ConfigurationPropertiesBindingPostProcessor delegate) {
                delegates.add(new WeakReference<>(delegate));
            }
        }
        observations.add(new Observation(new WeakReference<>(bean), delegates));
    }

    synchronized Boolean wasBound(Object bean, ConfigurationPropertiesBindingPostProcessor delegate) {
        if (!tracking) return null;
        var observation = observation(bean);
        return observation == null ? null : observation.delegates.stream().anyMatch(ref -> ref.get() == delegate);
    }

    synchronized void bound(Object bean, ConfigurationPropertiesBindingPostProcessor delegate) {
        if (!tracking || bean == null) return;
        observe(bean);
        var observation = observation(bean);
        if (observation != null && observation.delegates.stream().noneMatch(ref -> ref.get() == delegate)) {
            observation.delegates.add(new WeakReference<>(delegate));
        }
    }

    private Observation observation(Object bean) {
        observations.removeIf(observation -> observation.bean.get() == null);
        return observations.stream().filter(observation -> observation.bean.get() == bean).findFirst().orElse(null);
    }

    @Override
    public void afterSingletonsInstantiated() {
        // Normal refresh reaches this boundary; AOT refresh deliberately does not.
        stopTracking();
    }

    @Override
    public void destroy() {
        stopTracking();
    }

    private synchronized void stopTracking() {
        tracking = false;
        observations.clear();
        observations.trimToSize();
    }

    private record Observation(WeakReference<Object> bean,
                               List<WeakReference<ConfigurationPropertiesBindingPostProcessor>> delegates) {
    }
}
