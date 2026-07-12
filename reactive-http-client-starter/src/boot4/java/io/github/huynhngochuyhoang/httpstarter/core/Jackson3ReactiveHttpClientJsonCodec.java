package io.github.huynhngochuyhoang.httpstarter.core;

import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

public final class Jackson3ReactiveHttpClientJsonCodec implements ReactiveHttpClientJsonCodec {

    private final ObjectMapper objectMapper;

    public Jackson3ReactiveHttpClientJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public byte[] write(Object value) throws Exception {
        return objectMapper.writeValueAsBytes(value);
    }

    @Override
    public <T> T read(byte[] value, Class<T> type) throws Exception {
        return objectMapper.readValue(value, type);
    }
}
