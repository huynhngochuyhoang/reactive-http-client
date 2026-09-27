package io.github.huynhngochuyhoang.httpstarter.config;

import org.springframework.beans.factory.BeanFactoryUtils;
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
        if (tracking) observe(bean, name);
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String name) {
        if (tracking) observe(bean, name);
        return bean;
    }

    private synchronized void observe(Object bean, String name) {
        if (!tracking || !(bean instanceof ReactiveHttpClientProperties) || observation(bean, name) != null) return;
        var delegates = new ArrayList<WeakReference<ConfigurationPropertiesBindingPostProcessor>>();
        for (var processor : factory.getBeanPostProcessors()) {
            if (processor instanceof ConfigurationPropertiesBindingPostProcessor delegate) {
                delegates.add(new WeakReference<>(delegate));
            }
        }
        observations.add(new Observation(new WeakReference<>(bean), canonicalName(name), delegates));
    }

    synchronized Boolean wasBound(Object bean, String name, ConfigurationPropertiesBindingPostProcessor delegate) {
        if (!tracking) return null;
        var observation = observation(bean, name);
        return observation == null ? null : observation.delegates.stream().anyMatch(ref -> ref.get() == delegate);
    }

    synchronized void bound(Object bean, String name, ConfigurationPropertiesBindingPostProcessor delegate) {
        if (!tracking || bean == null) return;
        observe(bean, name);
        var observation = observation(bean, name);
        if (observation != null && observation.delegates.stream().noneMatch(ref -> ref.get() == delegate)) {
            observation.delegates.add(new WeakReference<>(delegate));
        }
    }

    private Observation observation(Object bean, String name) {
        observations.removeIf(observation -> observation.bean.get() == null);
        String canonicalName = canonicalName(name);
        return observations.stream().filter(observation -> observation.bean.get() == bean
                && observation.name.equals(canonicalName)).findFirst().orElse(null);
    }

    private String canonicalName(String name) {
        return factory.canonicalName(BeanFactoryUtils.transformedBeanName(name));
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

    private record Observation(WeakReference<Object> bean, String name,
                               List<WeakReference<ConfigurationPropertiesBindingPostProcessor>> delegates) {
    }
}
