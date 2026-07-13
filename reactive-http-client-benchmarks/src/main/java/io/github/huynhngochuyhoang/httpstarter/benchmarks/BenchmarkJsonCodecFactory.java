package io.github.huynhngochuyhoang.httpstarter.benchmarks;

import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientJsonCodec;
import tools.jackson.databind.ObjectMapper;

final class BenchmarkJsonCodecFactory {
    ReactiveHttpClientJsonCodec create() {
        ObjectMapper objectMapper = new ObjectMapper();
        return new ReactiveHttpClientJsonCodec() {
            @Override
            public byte[] write(Object value) throws Exception {
                return objectMapper.writeValueAsBytes(value);
            }

            @Override
            public <T> T read(byte[] value, Class<T> type) throws Exception {
                return objectMapper.readValue(value, type);
            }
        };
    }
}
