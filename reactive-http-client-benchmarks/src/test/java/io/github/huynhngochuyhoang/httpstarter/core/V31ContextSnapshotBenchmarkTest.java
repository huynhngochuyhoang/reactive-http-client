package io.github.huynhngochuyhoang.httpstarter.core;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
class V31ContextSnapshotBenchmarkTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 8, 32})
    void snapshotAndGatedHandoffRowsUseThePublicRestorationContract(int count) throws Exception {
        var benchmark = new V31ContextSnapshotBenchmark();
        benchmark.headerCount = count;
        benchmark.setup();
        try {
            assertThat(benchmark.contextV31SnapshotCapture().inboundHeaders()).hasSize(count);
            assertThat(RequestContext.inboundHeaders(benchmark.contextV31SnapshotRestore())).hasSize(count);
            for (int iteration = 0; iteration < 10; iteration++) {
                assertThat(benchmark.contextV31ExplicitHandoff()).containsExactly("caller-1", "caller-2");
            }
        } finally {
            benchmark.close();
        }
        assertThat(benchmark.source).isNull();
        assertThat(benchmark.snapshot).isNull();
        assertThat(benchmark.executor.isTerminated()).isTrue();
    }
}
