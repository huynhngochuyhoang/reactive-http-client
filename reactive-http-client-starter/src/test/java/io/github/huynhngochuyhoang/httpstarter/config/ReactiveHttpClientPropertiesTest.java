package io.github.huynhngochuyhoang.httpstarter.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ReactiveHttpClientPropertiesTest {

    @Test
    void shouldUseExpectedClientDefaults() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();

        assertEquals(2000, config.getConnectTimeoutMs());
        assertEquals(5000, config.getReadTimeoutMs());
        assertFalse(config.getResilience().isEnabled());
        assertEquals(0, config.getResilience().getTimeoutMs());
        assertEquals(2, config.getCodecMaxInMemorySizeMb());
        assertFalse(config.isCompressionEnabled());
        assertFalse(config.isLogBody());
    }
}
