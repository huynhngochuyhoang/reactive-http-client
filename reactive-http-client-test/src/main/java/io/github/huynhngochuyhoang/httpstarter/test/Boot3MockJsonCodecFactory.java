package io.github.huynhngochuyhoang.httpstarter.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.huynhngochuyhoang.httpstarter.core.Jackson2ReactiveHttpClientJsonCodec;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientJsonCodec;

final class MockJsonCodecFactory {
    ReactiveHttpClientJsonCodec create() {
        return new Jackson2ReactiveHttpClientJsonCodec(new ObjectMapper());
    }
}
