package io.github.huynhngochuyhoang.httpstarter.observability;

/** Bounded cache outcome for one logical reactive HTTP client caller. */
public enum HttpClientCacheOutcome {
    FRESH_HIT,
    MISS_LOADER,
    COALESCED_WAITER,
    STALE_HIT
}
