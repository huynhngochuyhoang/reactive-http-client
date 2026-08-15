package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.auth.AuthProviderFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.MethodParameter;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.*;

final class AuthProviderFactoryCandidates {

    private static final Method AUTH_PROVIDER_FACTORY_LOOKUP_METHOD = Objects.requireNonNull(
            ReflectionUtils.findMethod(AuthProviderFactoryCandidates.class,
                    "authProviderFactoryLookup", AuthProviderFactory.class));

    private AuthProviderFactoryCandidates() {
    }

    static void sort(List<Candidate> candidates, ConfigurableListableBeanFactory rootFactory) {
        Comparator<Object> dependencyComparator = rootFactory instanceof DefaultListableBeanFactory defaultFactory
                ? defaultFactory.getDependencyComparator()
                : null;
        OrderComparator comparator = dependencyComparator instanceof OrderComparator orderComparator
                ? orderComparator
                : OrderComparator.INSTANCE;
        Comparator<Object> sourceAware = comparator.withSourceProvider(
                AuthProviderFactoryCandidates::orderSource);
        candidates.sort((left, right) -> {
            boolean leftPriority = left.value() instanceof PriorityOrdered;
            boolean rightPriority = right.value() instanceof PriorityOrdered;
            if (leftPriority != rightPriority) {
                return leftPriority ? -1 : 1;
            }
            return sourceAware.compare(left, right);
        });
    }

    static boolean isAutowireCandidate(ConfigurableListableBeanFactory rootFactory,
                                       String beanName) {
        return rootFactory.isAutowireCandidate(
                beanName, new DependencyDescriptor(
                        new TypeOnlyMethodParameter(AUTH_PROVIDER_FACTORY_LOOKUP_METHOD, 0), false));
    }

    static Set<String> withLocalBeanNames(ConfigurableListableBeanFactory factory,
                                          Set<String> shadowedBeanNames) {
        Set<String> localNames = new LinkedHashSet<>();
        localNames.addAll(Arrays.asList(factory.getBeanDefinitionNames()));
        localNames.addAll(Arrays.asList(factory.getSingletonNames()));
        Set<String> names = new LinkedHashSet<>(shadowedBeanNames);
        names.addAll(localNames);
        for (String beanName : localNames) {
            names.addAll(Arrays.asList(factory.getAliases(beanName)));
        }
        return Set.copyOf(names);
    }

    private static Object orderSource(Object candidate) {
        if (!(candidate instanceof Candidate existing)) {
            return null;
        }
        List<Object> sources = new ArrayList<>(4);
        BeanDefinition definition = beanDefinition(existing.beanFactory(), existing.beanName());
        if (definition != null) {
            Object order = definition.getAttribute(AbstractBeanDefinition.ORDER_ATTRIBUTE);
            if (order instanceof Integer orderValue) {
                sources.add((Ordered) () -> orderValue);
            }
            if (definition instanceof RootBeanDefinition rootDefinition) {
                Method factoryMethod = rootDefinition.getResolvedFactoryMethod();
                if (factoryMethod != null) {
                    sources.add(factoryMethod);
                }
                Class<?> targetType = rootDefinition.getTargetType();
                if (targetType != null && targetType != existing.value().getClass()) {
                    sources.add(targetType);
                }
            }
        }
        sources.add(existing.value());
        return sources.toArray();
    }

    private static BeanDefinition beanDefinition(ConfigurableListableBeanFactory factory,
                                                 String beanName) {
        return factory.containsBeanDefinition(beanName)
                ? factory.getMergedBeanDefinition(beanName)
                : null;
    }

    record Candidate(
            ConfigurableListableBeanFactory beanFactory,
            String beanName,
            AuthProviderFactory value) {
    }

    @SuppressWarnings("unused")
    private static void authProviderFactoryLookup(AuthProviderFactory authProviderFactory) {
    }

    private static final class TypeOnlyMethodParameter extends MethodParameter {

        private TypeOnlyMethodParameter(Method method, int parameterIndex) {
            super(method, parameterIndex);
        }

        @Override
        public String getParameterName() {
            return null;
        }
    }
}
