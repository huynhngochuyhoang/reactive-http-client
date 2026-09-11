package io.github.huynhngochuyhoang.httpstarter.core;

/** Aggregates only policies with selected work limits, never unbounded policies. */
record CacheWorkSnapshot(String selection, String state, int limitedPolicyCount,
                         Long maximumCallers, Long maximumLoads, Long maximumRefreshes,
                         Long activeCallers, Long activeLoads, Long activeRefreshes) {
    static CacheWorkSnapshot configured(CacheWorkPolicy.Snapshot policy) {
        int limited = policy.policies().size();
        long selected = policy.methods().values().stream()
                .filter(method -> method.eligibility() == EffectiveCachePolicy.Eligibility.GET_FRIENDLY_SELECTED
                        || method.eligibility() == EffectiveCachePolicy.Eligibility.SEMANTIC_READ_SELECTED)
                .map(CacheWorkPolicy.Selection::policyName).distinct().count();
        if (limited == 0) {
            return new CacheWorkSnapshot("absent", "absent", 0, null, null, null, null, null, null);
        }
        long callers = 0, loads = 0, refreshes = 0;
        for (CacheWorkPolicy.Limits limits : policy.policies().values()) {
            callers = Math.addExact(callers, limits.maximumConcurrentCallers());
            loads = Math.addExact(loads, limits.maximumConcurrentLoads());
            if (limits.maximumConcurrentRefreshes() != null) {
                refreshes = Math.addExact(refreshes, limits.maximumConcurrentRefreshes());
            }
        }
        return new CacheWorkSnapshot(limited == selected ? "selected" : "mixed", "uninitialized",
                limited, callers, loads, refreshes == 0 ? null : refreshes, null, null, null);
    }

    CacheWorkSnapshot live(boolean closed, long callers, long loads, long refreshes) {
        if (limitedPolicyCount == 0) { return this; }
        return new CacheWorkSnapshot(selection, closed ? "closed" : "open", limitedPolicyCount,
                maximumCallers, maximumLoads, maximumRefreshes,
                callers, loads, maximumRefreshes == null ? null : refreshes);
    }
}
