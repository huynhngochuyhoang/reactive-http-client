package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** One immutable, per-factory selection for the three independent work owners. */
final class CacheWorkPolicy {
    static final long MAXIMUM = 1_000_000;

    record Limits(int maximumConcurrentCallers, int maximumConcurrentLoads, Integer maximumConcurrentRefreshes) { }

    record Selection(EffectiveCachePolicy.Eligibility eligibility, EffectiveCachePolicy.Source source,
                     String policyName, boolean refreshEnabled, Limits work) { }

    record Snapshot(Map<Method, Selection> methods, Map<String, Limits> policies) {
        Snapshot {
            methods = Map.copyOf(methods);
            policies = Map.copyOf(policies);
        }

        void requireUnchanged(Snapshot candidate) {
            if (!equals(candidate)) {
                throw new IllegalStateException("Cache work selection changed after startup; recreate the client factory");
            }
        }

        Map<String, Integer> maximums(Function<Limits, Integer> dimension) {
            Map<String, Integer> values = new LinkedHashMap<>();
            policies.forEach((name, limits) -> {
                Integer maximum = dimension.apply(limits);
                if (maximum != null) {
                    values.put(name, maximum);
                }
            });
            return Map.copyOf(values);
        }
    }

    static Snapshot freeze(Class<?> client, String name, MethodMetadataCache metadata,
                           ReactiveHttpClientProperties.ClientConfig config) {
        Map<Method, Selection> methods = new LinkedHashMap<>();
        Map<String, Limits> policies = new LinkedHashMap<>();
        if (client == null) {
            return new Snapshot(methods, policies);
        }
        for (Method method : client.getMethods()) {
            if (method.isDefault() || !Modifier.isAbstract(method.getModifiers())) {
                continue;
            }
            RequestPlan plan = RequestPlan.from(metadata.get(method), client);
            var decision = EffectiveCachePolicy.decide(
                    plan, config, EffectiveCachePolicy.effectiveHttpMethod(plan, config));
            var selection = decision.selection();
            Limits limits = decision.cacheable() ? normalize(selection.policy()) : null;
            if (limits != null) {
                // Complete cache grammar remains authoritative, including custom metadata and API refs.
                EffectiveCachePolicy.validateDecision(client, name, plan, config,
                        EffectiveCachePolicy.effectiveHttpMethod(plan, config));
                Limits existing = policies.putIfAbsent(selection.policyName(), limits);
                if (existing != null) {
                    if (!existing.equals(limits)) {
                        throw new IllegalStateException("Cache work selection changed during startup");
                    }
                    limits = existing;
                }
            }
            methods.put(method, new Selection(decision.eligibility(), selection.source(), selection.policyName(),
                    decision.cacheable() && selection.policy().isRefreshEnabled(), limits));
        }
        return new Snapshot(methods, policies);
    }

    static Limits normalize(ReactiveHttpClientProperties.CachePolicyConfig policy) {
        return policy == null ? null : normalize(policy.getWork(), policy.isRefreshEnabled());
    }

    static Runnable validator(Snapshot snapshot, Class<?> client, MethodMetadataCache metadata,
                              ReactiveHttpClientProperties.ClientConfig config) {
        Map<Method, RequestPlan> plans = new LinkedHashMap<>();
        snapshot.methods().keySet().forEach(method ->
                plans.put(method, RequestPlan.from(metadata.get(method), client)));
        return () -> {
            for (var entry : snapshot.methods().entrySet()) {
                RequestPlan plan = plans.get(entry.getKey());
                var decision = EffectiveCachePolicy.decide(
                        plan, config, EffectiveCachePolicy.effectiveHttpMethod(plan, config));
                var selected = decision.selection();
                var expected = entry.getValue();
                if (expected.eligibility() != decision.eligibility()
                        || expected.source() != selected.source()
                        || !java.util.Objects.equals(expected.policyName(), selected.policyName())
                        || expected.refreshEnabled() != (decision.cacheable() && selected.policy().isRefreshEnabled())
                        || !java.util.Objects.equals(expected.work(),
                                decision.cacheable() ? normalize(selected.policy()) : null)) {
                    throw new IllegalStateException("Cache work selection changed after startup; recreate the client factory");
                }
            }
        };
    }

    static Limits normalize(ReactiveHttpClientProperties.CacheWorkConfig input, boolean refreshEnabled) {
        if (input == null || input.getMaximumConcurrentCallers() == null
                && input.getMaximumConcurrentLoads() == null && input.getMaximumConcurrentRefreshes() == null) {
            return null;
        }
        int callers = positive("maximum-concurrent-callers", input.getMaximumConcurrentCallers());
        int loads = positive("maximum-concurrent-loads", input.getMaximumConcurrentLoads());
        if (!refreshEnabled && input.getMaximumConcurrentRefreshes() != null) {
            throw new IllegalArgumentException("maximum-concurrent-refreshes requires selected refresh");
        }
        Integer refreshes = refreshEnabled
                ? positive("maximum-concurrent-refreshes", input.getMaximumConcurrentRefreshes()) : null;
        return new Limits(callers, loads, refreshes);
    }

    private static int positive(String property, Long value) {
        if (value == null || value < 1 || value > MAXIMUM) {
            throw new IllegalArgumentException(property + " must be an integer in [1, " + MAXIMUM + "]");
        }
        return Math.toIntExact(value);
    }
}
