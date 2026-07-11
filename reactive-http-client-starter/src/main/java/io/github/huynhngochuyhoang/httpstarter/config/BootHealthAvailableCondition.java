package io.github.huynhngochuyhoang.httpstarter.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;

final class BootHealthAvailableCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        ClassLoader classLoader = context.getClassLoader();
        return ClassUtils.isPresent("org.springframework.boot.actuate.health.HealthIndicator", classLoader)
                || ClassUtils.isPresent("org.springframework.boot.health.contributor.HealthIndicator", classLoader);
    }
}
