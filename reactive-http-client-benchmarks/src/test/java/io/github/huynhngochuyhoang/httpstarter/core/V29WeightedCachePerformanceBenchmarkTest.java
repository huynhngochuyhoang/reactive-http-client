package io.github.huynhngochuyhoang.httpstarter.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class V29WeightedCachePerformanceBenchmarkTest {

    @Test
    void everyWeightedCacheBenchmarkRowExercisesItsDeclaredPath() {
        V29WeightedCachePerformanceBenchmark benchmark =
                new V29WeightedCachePerformanceBenchmark();
        benchmark.setup();
        try {
            assertThat(benchmark.cacheV29NoNetworkUnweightedPublisherCreation()).isNotNull();
            assertThat(benchmark.cacheV29NoNetworkUnweightedSubscription()).isEqualTo("value");
            assertThat(benchmark.cacheV29NoNetworkWeightedMetricsDisabledPublisherCreation()).isNotNull();
            assertThat(benchmark.cacheV29NoNetworkWeightedMetricsDisabledSubscription()).isEqualTo("value");
            assertThat(benchmark.cacheV29NoNetworkWeightedHit()).isEqualTo("value");
            assertThat(benchmark.cacheV29NoNetworkMissPublication()).isEqualTo("value");
            assertThat(benchmark.cacheV29NoNetworkBypassedAdmission()).isEqualTo("value");
            assertThat(benchmark.cacheV29NoNetworkSizeEviction()).isPositive();
            assertThat(benchmark.cacheV29NoNetworkWeightEviction()).isPositive();
            assertThat(benchmark.cacheV29NoNetworkSingleFlightAttachment())
                    .containsExactly("value", "value");
            assertThat(benchmark.cacheV29NoNetworkRefreshReplacement()).isPositive();
            assertThat(benchmark.cacheV29NoNetworkAccountingSnapshot().currentSize()).isPositive();
            double meteredCallers = benchmark.meteredCallerCount("weighted.accounting");
            assertThat(benchmark.cacheV29NoNetworkMeteredAccountingPublication().currentSize()).isPositive();
            assertThat(benchmark.meteredCallerCount("weighted.accounting"))
                    .isEqualTo(meteredCallers + 1.0);
            assertThat(benchmark.meteredCallerCount("unknown")).isZero();

            assertThat(benchmark.cacheV29LoopbackMissPublication()).isEqualTo("value");
            assertThat(benchmark.cacheV29LoopbackBypassedAdmission()).isEqualTo("oversized");
            assertThat(benchmark.cacheV29LoopbackSizeEviction()).isPositive();
            assertThat(benchmark.cacheV29LoopbackWeightEviction()).isPositive();
            assertThat(benchmark.cacheV29LoopbackSingleFlightAttachment())
                    .containsExactly("value", "value");
            assertThat(benchmark.cacheV29LoopbackRefreshReplacement()).isPositive();
            assertThat(benchmark.cacheV29LoopbackAccountingPublication().currentSize()).isPositive();
        }
        finally {
            benchmark.tearDown();
        }
    }
}
