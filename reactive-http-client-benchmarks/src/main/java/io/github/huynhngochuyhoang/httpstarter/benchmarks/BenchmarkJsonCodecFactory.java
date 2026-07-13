package io.github.huynhngochuyhoang.httpstarter.benchmarks;

import io.github.huynhngochuyhoang.httpstarter.core.Jackson3ReactiveHttpClientJsonCodec;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientJsonCodec;
import tools.jackson.databind.ObjectMapper;

final class BenchmarkJsonCodecFactory {
    ReactiveHttpClientJsonCodec create() {
        return new Jackson3ReactiveHttpClientJsonCodec(new ObjectMapper());
    }
}
