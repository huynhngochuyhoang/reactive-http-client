package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.exception.CacheWorkRejectedException;

import java.util.Map;

/** Caller gate with structural local rejection, independent of source capacity. */
final class CacheCallerAdmission extends CacheWorkAdmission {
    CacheCallerAdmission(Map<String, Integer> maximums) {
        super(maximums, () -> new CacheWorkRejectedException(CacheWorkRejectedException.Reason.CALLER_CAPACITY));
    }
}
