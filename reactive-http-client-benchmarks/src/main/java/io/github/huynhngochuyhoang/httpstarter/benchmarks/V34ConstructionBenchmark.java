package io.github.huynhngochuyhoang.httpstarter.benchmarks;

import org.openjdk.jmh.annotations.*;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

/** Invocation fixtures are outside latency timing, but their allocations remain in GC-profiler totals. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class V34ConstructionBenchmark {
    @State(Scope.Thread)
    public static class Construction {
        @Param({"AUTO_NO_REGISTRY", "AUTO_REGISTRY"}) public String profile;
        V34WorkloadFixture fixture;

        @TearDown(Level.Invocation) public void close() {
            try {
                V34WorkloadFixture.require(fixture.dispatches.get() == 0, "construction dispatched");
            } finally { fixture.close(); }
        }
    }

    @State(Scope.Thread)
    public static class First {
        @Param({"AUTO_NO_REGISTRY", "AUTO_REGISTRY"}) public String profile;
        V34WorkloadFixture fixture;

        @Setup(Level.Invocation) public void setup() {
            fixture = new V34WorkloadFixture(V34WorkloadFixture.Profile.valueOf(profile),
                    V34WorkloadFixture.Scenario.GET, false);
        }
        @TearDown(Level.Invocation) public void close() {
            try { fixture.verifyTotals(); }
            finally { fixture.close(); }
        }
    }

    @Benchmark public Object defaultV34ConstructionContextAndProxy(Construction state) {
        state.fixture = new V34WorkloadFixture(V34WorkloadFixture.Profile.valueOf(state.profile),
                V34WorkloadFixture.Scenario.GET, false);
        return state.fixture.client;
    }
    @Benchmark public Mono<?> defaultV34ConstructionFirstPublisher(First state) { return state.fixture.publisher(); }
    @Benchmark public Object defaultV34ConstructionFirstCall(First state) { return state.fixture.subscribe(state.fixture.publisher()); }
}
