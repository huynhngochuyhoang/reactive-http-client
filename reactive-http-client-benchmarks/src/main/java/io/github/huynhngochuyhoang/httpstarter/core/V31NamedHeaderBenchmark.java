package io.github.huynhngochuyhoang.httpstarter.core;

import org.openjdk.jmh.annotations.*;
import reactor.util.context.Context;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Candidate-only rows; no substitute helper is measured on 4.3. */
@State(Scope.Thread)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class V31NamedHeaderBenchmark {
    @Param({"0", "8", "32"})
    public int headerCount;
    Context source;

    @Setup public void setup() { source = V31ContextSnapshotBenchmark.context(headerCount); }

    @Benchmark public List<String> contextV31NamedMultiValueLookup() {
        return RequestContext.inboundHeaderValues(source, "X-Fixture-0");
    }

    @Benchmark public List<String> contextV31NamedAbsentLookup() {
        return RequestContext.inboundHeaderValues(source, "X-Absent");
    }

    @Benchmark public Optional<String> contextV31NamedSingletonLookup() {
        return RequestContext.inboundHeader(source, "X-Fixture-1");
    }

    @Benchmark public Object contextV31NamedSingletonRejection() {
        try {
            return RequestContext.inboundHeader(source, "X-Fixture-0");
        } catch (IllegalStateException ambiguous) {
            return ambiguous.getClass();
        }
    }
}
