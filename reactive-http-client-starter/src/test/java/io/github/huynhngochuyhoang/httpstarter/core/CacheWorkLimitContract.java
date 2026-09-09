package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.core.CacheWorkPolicy.Limits;
import io.github.huynhngochuyhoang.httpstarter.core.CacheWorkPolicy.Snapshot;

import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/** Decision-table fixtures; selection and normalization delegate to the production contract. */
final class CacheWorkLimitContract {
    static final long MAXIMUM = CacheWorkPolicy.MAXIMUM;

    record Input(Long maximumConcurrentCallers, Long maximumConcurrentLoads, Long maximumConcurrentRefreshes) {
    }

    static Snapshot freeze(Class<?> client, String name, MethodMetadataCache metadata,
                           ReactiveHttpClientProperties.ClientConfig config, Map<String, Input> inputs) {
        config.getCache().getPolicies().forEach((policyName, policy) ->
                policy.setWork(properties(inputs.get(policyName))));
        for (var method : client.getMethods()) {
            if (method.isDefault() || !Modifier.isAbstract(method.getModifiers())) {
                continue;
            }
            RequestPlan plan = RequestPlan.from(metadata.get(method), client);
            EffectiveCachePolicy.validateDecision(
                    client, name, plan, config, EffectiveCachePolicy.effectiveHttpMethod(plan, config));
        }
        return CacheWorkPolicy.freeze(client, name, metadata, config);
    }

    static Limits normalize(Input input, boolean refreshEnabled) {
        return CacheWorkPolicy.normalize(properties(input), refreshEnabled);
    }

    private static ReactiveHttpClientProperties.CacheWorkConfig properties(Input input) {
        if (input == null) {
            return null;
        }
        var result = new ReactiveHttpClientProperties.CacheWorkConfig();
        result.setMaximumConcurrentCallers(input.maximumConcurrentCallers());
        result.setMaximumConcurrentLoads(input.maximumConcurrentLoads());
        result.setMaximumConcurrentRefreshes(input.maximumConcurrentRefreshes());
        return result;
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
