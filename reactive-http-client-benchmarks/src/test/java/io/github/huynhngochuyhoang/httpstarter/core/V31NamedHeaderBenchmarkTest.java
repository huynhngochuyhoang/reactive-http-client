package io.github.huynhngochuyhoang.httpstarter.core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class V31NamedHeaderBenchmarkTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 8, 32})
    void lookupRowsExerciseAbsenceMultiplicitiesAndImmutableResults(int count) {
        var benchmark = new V31NamedHeaderBenchmark();
        benchmark.headerCount = count;
        benchmark.setup();
        assertThat(benchmark.contextV31NamedAbsentLookup()).isEmpty();
        assertThat(benchmark.contextV31NamedSingletonLookup())
                .isEqualTo(count == 0 ? Optional.empty() : Optional.of("bounded-single"));
        var values = benchmark.contextV31NamedMultiValueLookup();
        assertThat(values).hasSize(count == 0 ? 0 : 2);
        assertThatThrownBy(() -> values.add("mutation")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(benchmark.contextV31NamedSingletonRejection())
                .isEqualTo(count == 0 ? Optional.empty() : IllegalStateException.class);
    }
}
