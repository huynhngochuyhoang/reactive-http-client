package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.exception.CacheWorkRejectedException;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.util.context.Context;

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
class V30CacheWorkPerformanceBenchmarkTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rowsExerciseProductionCallerSourceAndMetricPaths(boolean metered) throws Exception {
        var benchmark = new V30CacheWorkPerformanceBenchmark();
        benchmark.metered = metered;
        benchmark.setup();
        try {
            var plain = benchmark.plain;
            long before = plain.dispatches.get();
            assertThat(benchmark.cacheV30NoNetworkPublisherCreation()).isNotNull();
            assertThat(plain.dispatches).hasValue(before);
            assertThat(benchmark.cacheV30NoNetworkHit()).isEqualTo("value");
            assertThat(benchmark.cacheV30NoNetworkMiss()).isEqualTo("value");
            assertThat(benchmark.cacheV30NoNetworkWeightedPublication()).isEqualTo("value");
            assertThat(benchmark.weighted.manager.retainedDecodedResponseBytesForTesting()).isEqualTo(5);
            assertThat(benchmark.cacheV30NoNetworkCallerRejection())
                    .isEqualTo(CacheWorkRejectedException.Reason.CALLER_CAPACITY);
            assertThat(benchmark.cacheV30NoNetworkLoadRejection())
                    .isEqualTo(CacheWorkRejectedException.Reason.LOAD_CAPACITY);
            assertThat(benchmark.cacheV30NoNetworkSingleFlightJoin()).containsExactly("value", "value");
            assertThat(benchmark.cacheV30LoopbackSingleFlightJoin()).containsExactly("value", "value");
            assertThat(benchmark.cacheV30NoNetworkRefreshStart()).isEqualTo(10);
            assertThat(benchmark.cacheV30NoNetworkRefreshCapacitySkip()).isEqualTo(10);
            assertThat(benchmark.cacheV30NoNetworkReleaseAndReuse()).isEqualTo("value");
            if (metered) {
                assertThat(plain.counter(".callers", "outcome", "CALLER_REJECTED")).isEqualTo(1);
                assertThat(plain.counter(".callers", "outcome", "LOAD_REJECTED")).isEqualTo(2);
                assertThat(plain.counter(".callers", "outcome", "COALESCED_WAITER")).isEqualTo(2);
                assertThat(plain.counter(".callers", "outcome", "FRESH_HIT")).isEqualTo(1);
                assertThat(plain.counter(".loads", "outcome", "cancellation")).isEqualTo(3);
                assertThat(benchmark.refresh.counter(".refreshes", "outcome", "success")).isEqualTo(2);
            } else {
                assertThat(plain.registry.getMeters()).isEmpty();
            }
            var contention = new V30CacheWorkAdmissionBenchmark();
            contention.setup();
            try (var workers = Executors.newFixedThreadPool(4)) {
                List<java.util.concurrent.Callable<Void>> tasks = new ArrayList<>();
                for (int thread = 0; thread < 4; thread++) {
                    tasks.add(() -> {
                        for (int attempt = 0; attempt < 1000; attempt++) {
                            assertThat(contention.cacheV30NoNetworkContendedReservationRejection())
                                    .isEqualTo(CacheWorkRejectedException.Reason.CALLER_CAPACITY);
                        }
                        return null;
                    });
                }
                for (var future : workers.invokeAll(tasks)) { future.get(5, TimeUnit.SECONDS); }
            } finally {
                contention.close();
            }
        } finally {
            benchmark.tearDown();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void saturatedCallersDoNotRetainRejectedContextsWhileAdmittedSourceStaysLive(boolean metered) {
        try (var fixture = new V30CacheWorkPerformanceBenchmark.Fixture(metered, true, false, false)) {
            var gate = fixture.hold();
            var call = fixture.client.read("held");
            var leader = call.toFuture();
            var waiter = call.toFuture();
            List<WeakReference<byte[]>> references;
            try {
                fixture.joined();
                references = rejectBatch(fixture);
                assertThat(fixture.dispatches).hasValue(1);
                assertThat(fixture.manager.workSnapshot().activeCallers()).isEqualTo(2);
                assertThat(fixture.manager.workSnapshot().activeLoads()).isEqualTo(1);
                assertThat(fixture.manager.workloadSnapshotForTesting().coalescedWaiters()).isEqualTo(1);
                assertThat(fixture.manager.snapshot().currentSize()).isZero();
                assertCollected(references);
                assertThat(leader).isNotDone();
                assertThat(waiter).isNotDone();
            } finally {
                leader.cancel(true);
                waiter.cancel(true);
                fixture.release(gate);
            }
            fixture.verify(0, 1);
            assertThat(fixture.client.read("reuse").block(Duration.ofSeconds(5))).isEqualTo("value");
            fixture.verify(1, 1);
        }
    }

    private static List<WeakReference<byte[]>> rejectBatch(V30CacheWorkPerformanceBenchmark.Fixture fixture) {
        List<WeakReference<byte[]>> references = new ArrayList<>();
        for (int attempt = 0; attempt < 2000; attempt++) {
            byte[] requestContext = new byte[1024];
            if (attempt < 32) { references.add(new WeakReference<>(requestContext)); }
            assertThat(V30CacheWorkPerformanceBenchmark.rejected(
                    fixture.client.read("rejected").contextWrite(Context.of("retention-probe", requestContext))))
                    .isEqualTo(CacheWorkRejectedException.Reason.CALLER_CAPACITY);
        }
        return references;
    }

    private static void assertCollected(List<WeakReference<byte[]>> references) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (references.stream().anyMatch(reference -> reference.get() != null)
                && System.nanoTime() < deadline) {
            System.gc();
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
        assertThat(references).allSatisfy(reference -> assertThat(reference.get()).isNull());
    }
}
