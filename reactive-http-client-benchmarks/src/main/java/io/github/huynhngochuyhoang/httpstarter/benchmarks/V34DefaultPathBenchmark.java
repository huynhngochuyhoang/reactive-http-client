package io.github.huynhngochuyhoang.httpstarter.benchmarks;

import org.openjdk.jmh.annotations.*;
import reactor.core.publisher.Mono;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class V34DefaultPathBenchmark {
    @State(Scope.Thread)
    public static class Warm {
        @Param({"MINIMAL", "AUTO_NO_REGISTRY", "AUTO_REGISTRY", "RESILIENCE_ENABLED_ONLY", "OBSERVER", "HOOK"})
        public String profile;
        @Param({"GET", "TARGET"}) public String scenario;
        V34WorkloadFixture fixture;
        Mono<?> cold;

        @Setup public void setup() {
            fixture = new V34WorkloadFixture(V34WorkloadFixture.Profile.valueOf(profile),
                    V34WorkloadFixture.Scenario.valueOf(scenario), false);
            fixture.verify();
            cold = fixture.publisher();
        }

        @TearDown public void close() {
            try { if (fixture != null) fixture.verifyTotals(); }
            finally { if (fixture != null) fixture.close(); }
        }
    }

    @State(Scope.Thread)
    public static class Loopback {
        @Param({"MINIMAL", "AUTO_NO_REGISTRY", "AUTO_REGISTRY"}) public String profile;
        @Param({"GET", "TARGET", "STRING", "JSON", "ENTITY", "EMPTY", "ERROR4", "ERROR5"}) public String scenario;
        V34WorkloadFixture fixture;

        @Setup public void setup() {
            fixture = new V34WorkloadFixture(V34WorkloadFixture.Profile.valueOf(profile),
                    V34WorkloadFixture.Scenario.valueOf(scenario), true);
            fixture.verify();
        }

        @TearDown public void close() {
            try { if (fixture != null) fixture.verifyTotals(); }
            finally { if (fixture != null) fixture.close(); }
        }
    }

    @Benchmark public Mono<?> defaultV34NoNetworkWarmPublisher(Warm state) { return state.fixture.publisher(); }
    @Benchmark public Object defaultV34NoNetworkWarmSubscription(Warm state) { return state.fixture.subscribe(state.cold); }
    @Benchmark public Object defaultV34LoopbackCall(Loopback state) { return state.fixture.subscribe(state.fixture.publisher()); }
}
