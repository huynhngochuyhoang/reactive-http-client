package io.github.huynhngochuyhoang.httpstarter.core;

import tools.jackson.databind.ObjectMapper;

final class TestJsonCodecs {

    private TestJsonCodecs() {
    }

    static ReactiveHttpClientJsonCodec jsonCodec() {
        return new Jackson3ReactiveHttpClientJsonCodec(new ObjectMapper());
    }
}
