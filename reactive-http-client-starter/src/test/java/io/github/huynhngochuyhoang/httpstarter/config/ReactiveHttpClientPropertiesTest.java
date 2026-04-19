package io.github.huynhngochuyhoang.httpstarter.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReactiveHttpClientPropertiesTest {

    @Test
    void shouldUseExpectedClientDefaults() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();

        assertFalse(config.getResilience().isEnabled());
        assertEquals(0, config.getResilience().getTimeoutMs());
        assertEquals(2, config.getCodecMaxInMemorySizeMb());
        assertFalse(config.isCompressionEnabled());
        assertFalse(config.isLogBody());
        assertNull(config.getAuthProvider());
    }

    @Test
    void shouldUseExpectedGlobalNetworkDefaults() {
        ReactiveHttpClientProperties.NetworkConfig network = new ReactiveHttpClientProperties.NetworkConfig();

        assertEquals(2000, network.getConnectTimeoutMs());
        assertEquals(5000, network.getReadTimeoutMs());
        assertEquals(5000, network.getWriteTimeoutMs());
        assertEquals(200, network.getConnectionPool().getMaxConnections());
        assertEquals(5000, network.getConnectionPool().getPendingAcquireTimeoutMs());
    }
}
