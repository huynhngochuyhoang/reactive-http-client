package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.exception.CacheWorkRejectedException;

import java.util.Map;

/** Internal foreground-source gate, independent from caller and hidden refresh ownership. */
final class CacheLoadAdmission extends CacheWorkAdmission {
    CacheLoadAdmission(Map<String, Integer> maximums) {
        super(maximums, () -> new CacheWorkRejectedException(CacheWorkRejectedException.Reason.LOAD_CAPACITY));
    }
}
