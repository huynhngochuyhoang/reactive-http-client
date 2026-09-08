package io.github.huynhngochuyhoang.httpstarter.core;

import java.util.Map;

/** Internal foreground-source gate, independent from caller and hidden refresh ownership. */
final class CacheLoadAdmission extends CacheWorkAdmission {
    CacheLoadAdmission(Map<String, Integer> maximums) {
        super(maximums, Rejected::new);
    }

    static final class Rejected extends RuntimeException {
        Rejected() {
            super("Response cache foreground load capacity exhausted", null, false, false);
        }
    }
}
