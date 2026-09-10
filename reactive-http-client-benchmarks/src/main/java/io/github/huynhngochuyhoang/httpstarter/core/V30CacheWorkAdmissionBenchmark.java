package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.exception.CacheWorkRejectedException;
import org.openjdk.jmh.annotations.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Isolates the production reservation/rejection primitive, not a metered logical call. */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class V30CacheWorkAdmissionBenchmark {
    CacheCallerAdmission admission;
    CacheWorkAdmission.Reservation held;

    @Setup public void setup() {
        admission = new CacheCallerAdmission(Map.of("work", 1));
        held = admission.acquire("work");
    }

    @TearDown public void close() {
        V30CacheWorkPerformanceBenchmark.require(admission.active("work") == 1, "only fixed owner survives contention");
        held.complete();
        V30CacheWorkPerformanceBenchmark.require(admission.active("work") == 0, "release fixed owner");
        admission.close();
    }

    @Benchmark @Threads(4)
    public CacheWorkRejectedException.Reason cacheV30NoNetworkContendedReservationRejection() {
        try {
            admission.acquire("work");
            throw new IllegalStateException("Saturated reservation unexpectedly acquired");
        } catch (CacheWorkRejectedException rejected) {
            return rejected.getReason();
        }
    }
}
