package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/** V30 executable specification only. Not packaged, bound to application properties, or enforcing work. */
final class CacheWorkLimitContract {
    static final long MAXIMUM = 1_000_000;

    record Input(Long maximumConcurrentCallers, Long maximumConcurrentLoads, Long maximumConcurrentRefreshes) {
        boolean absent() {
            return maximumConcurrentCallers == null && maximumConcurrentLoads == null
                    && maximumConcurrentRefreshes == null;
        }
    }

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
    }

    static Snapshot freeze(Class<?> client, String name, MethodMetadataCache metadata,
                           ReactiveHttpClientProperties.ClientConfig config, Map<String, Input> inputs) {
        Map<Method, Selection> methods = new LinkedHashMap<>();
        Map<String, Limits> policies = new LinkedHashMap<>();
        for (Method method : client.getMethods()) {
            if (method.isDefault() || !Modifier.isAbstract(method.getModifiers())) {
                continue;
            }
            RequestPlan plan = RequestPlan.from(metadata.get(method), client);
            EffectiveCachePolicy.Decision decision = EffectiveCachePolicy.validateDecision(
                    client, name, plan, config, EffectiveCachePolicy.effectiveHttpMethod(plan, config));
            var selection = decision.selection();
            Limits limits = decision.cacheable()
                    ? normalize(inputs.get(selection.policyName()), selection.policy().isRefreshEnabled()) : null;
            if (limits != null) {
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

    static Limits normalize(Input input, boolean refreshEnabled) {
        if (input == null || input.absent()) {
            return null;
        }
        int callers = positive("maximum-concurrent-callers", input.maximumConcurrentCallers());
        int loads = positive("maximum-concurrent-loads", input.maximumConcurrentLoads());
        if (!refreshEnabled && input.maximumConcurrentRefreshes() != null) {
            throw new IllegalArgumentException("maximum-concurrent-refreshes requires selected refresh");
        }
        Integer refreshes = refreshEnabled
                ? positive("maximum-concurrent-refreshes", input.maximumConcurrentRefreshes()) : null;
        return new Limits(callers, loads, refreshes);
    }

    private static int positive(String property, Long value) {
        if (value == null || value < 1 || value > MAXIMUM) {
            throw new IllegalArgumentException(property + " must be an integer in [1, " + MAXIMUM + "]");
        }
        return Math.toIntExact(value);
    }

    enum Lookup { FRESH, STALE, MISS }
    enum Action { ADMIT_CALLER, HIT, JOIN, START_LOAD, REJECT_CALLER, REJECT_LOAD }
    enum RefreshAction { NOT_ELIGIBLE, ALREADY_RUNNING, START, SKIP_CAPACITY }

    // Pure decision tables, not reservation counters or a substitute for atomic runtime transitions.
    static Action caller(Limits limits, int active) {
        return limits != null && active >= limits.maximumConcurrentCallers()
                ? Action.REJECT_CALLER : Action.ADMIT_CALLER;
    }

    static Action load(Limits limits, Lookup lookup, boolean currentFlight, int active) {
        if (lookup != Lookup.MISS) {
            return Action.HIT;
        }
        if (currentFlight) {
            return Action.JOIN;
        }
        return limits != null && active >= limits.maximumConcurrentLoads()
                ? Action.REJECT_LOAD : Action.START_LOAD;
    }

    static RefreshAction refresh(Limits limits, boolean eligible, boolean currentRefresh, int active) {
        if (!eligible) {
            return RefreshAction.NOT_ELIGIBLE;
        }
        if (currentRefresh) {
            return RefreshAction.ALREADY_RUNNING;
        }
        return limits != null && limits.maximumConcurrentRefreshes() != null
                && active >= limits.maximumConcurrentRefreshes()
                ? RefreshAction.SKIP_CAPACITY : RefreshAction.START;
    }

    enum Rejection {
        CALLER_CAPACITY("CALLER_REJECTED", "Response cache caller capacity exhausted"),
        LOAD_CAPACITY("LOAD_REJECTED", "Response cache foreground load capacity exhausted");

        final String cacheOutcome;
        final String message;

        Rejection(String cacheOutcome, String message) {
            this.cacheOutcome = cacheOutcome;
            this.message = message;
        }

        Map<String, Object> terminalFacts() {
            Map<String, Object> facts = new LinkedHashMap<>();
            facts.put("reason", name());
            facts.put("message", message);
            facts.put("errorCategory", "CACHE_ADMISSION_ERROR");
            facts.put("cacheOutcome", cacheOutcome);
            facts.put("attemptCount", 0);
            facts.put("requestDispatched", false);
            facts.put("statusCode", null);
            facts.put("failureStage", null);
            return facts;
        }
    }
}
