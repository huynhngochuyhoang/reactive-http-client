package io.github.huynhngochuyhoang.httpstarter.core;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

class V33PlanningCostBenchmarkTest {
    @Test
    void plansAndWarmCallsPreserveTheSameRequestAndDecodedResult() throws Exception {
        var benchmark = new V33PlanningCostBenchmark();
        try {
            benchmark.setup();
            assertThat(benchmark.metadataColdConcretePlan()).isEqualTo(benchmark.metadataColdParsingAndPlan());
            assertThat(benchmark.proxyInvocationV33WarmPublisher().block(Duration.ofSeconds(5))).isEqualTo("value");
            assertThat(benchmark.proxyInvocationV33WarmSubscription()).isEqualTo("value");
        } finally {
            benchmark.close();
        }
    }
}
