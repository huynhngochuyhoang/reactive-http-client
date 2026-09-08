package io.github.huynhngochuyhoang.httpstarter.core;

import java.util.Map;

/** Internal caller gate. Public selection awaits enforcement of all three work dimensions. */
final class CacheCallerAdmission extends CacheWorkAdmission {
    CacheCallerAdmission(Map<String, Integer> maximums) {
        super(maximums, Rejected::new);
    }

    static final class Rejected extends RuntimeException {
        Rejected() {
            super("Response cache caller capacity exhausted", null, false, false);
        }
    }
}
