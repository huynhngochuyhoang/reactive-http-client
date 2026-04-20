package io.github.huynhngochuyhoang.httpstarter.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactiveHttpClientPropertiesTest {

    @Test
    void shouldUseExpectedClientDefaults() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();

        assertFalse(config.getResilience().isEnabled());
        assertEquals(0, config.getResilience().getTimeoutMs());
        assertTrue(config.getResilience().getRetryMethods().contains("GET"));
        assertTrue(config.getResilience().getRetryMethods().contains("HEAD"));
        assertEquals(2, config.getCodecMaxInMemorySizeMb());
        assertFalse(config.isCompressionEnabled());
        assertFalse(config.isLogBody());
        assertNull(config.getAuthProvider());
    }

    @Test
    void shouldNormalizeRetryMethodsToUpperCase() {
        ReactiveHttpClientProperties.ResilienceConfig resilienceConfig = new ReactiveHttpClientProperties.ResilienceConfig();
        resilienceConfig.setRetryMethods(java.util.Set.of("get", " Put ", "HEAD"));

        assertTrue(resilienceConfig.getRetryMethods().contains("GET"));
        assertTrue(resilienceConfig.getRetryMethods().contains("PUT"));
        assertTrue(resilienceConfig.getRetryMethods().contains("HEAD"));
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
