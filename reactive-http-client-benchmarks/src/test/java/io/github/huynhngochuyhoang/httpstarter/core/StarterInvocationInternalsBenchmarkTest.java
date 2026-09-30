package io.github.huynhngochuyhoang.httpstarter.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StarterInvocationInternalsBenchmarkTest {
    @Test
    void helperRowsResolveTheSameConcreteRequest() throws Exception {
        var benchmark = new StarterInvocationInternalsBenchmark();
        benchmark.setup();
        assertThat(benchmark.cachedMethodMetadataLookup()).isSameAs(benchmark.cachedMethodMetadataLookup());
        RequestPlan plan = benchmark.cachedRequestPlanLookup();
        assertThat(plan).isSameAs(benchmark.cachedRequestPlanLookup());
        assertThat(plan.httpMethod()).isEqualTo("GET");
        assertThat(plan.pathTemplate()).isEqualTo("/users/{id}");
        var resolved = benchmark.argumentResolutionPathQueryHeaderFromPlan();
        assertThat(resolved).isEqualTo(benchmark.argumentResolutionPathQueryHeaderFromMetadata());
        assertThat(resolved.pathVars()).containsExactlyEntriesOf(java.util.Map.of("id", "42"));
        assertThat(resolved.queryParams()).containsExactlyEntriesOf(java.util.Map.of("expand", List.of("summary")));
        assertThat(resolved.flattenedHeaders()).containsExactlyEntriesOf(java.util.Map.of("X-Tenant", "benchmark"));
        assertThat(resolved.body()).isNull();
    }
}
