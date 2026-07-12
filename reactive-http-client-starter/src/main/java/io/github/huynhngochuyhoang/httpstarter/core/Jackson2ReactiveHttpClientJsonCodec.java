package io.github.huynhngochuyhoang.httpstarter.core;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

/** @deprecated Jackson 2 compatibility adapter for the Spring Boot 3 line. */
@Deprecated(since = "3.0.0", forRemoval = false)
public final class Jackson2ReactiveHttpClientJsonCodec implements ReactiveHttpClientJsonCodec {

    private final ObjectMapper objectMapper;

    public Jackson2ReactiveHttpClientJsonCodec(ObjectMapper objectMapper) {
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
