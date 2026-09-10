package io.github.huynhngochuyhoang.httpstarter.nativesmoke;

import io.github.huynhngochuyhoang.httpstarter.exception.LogicalCallTimeoutException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NativeCacheWorkScenarioTest {
    @Test
    void emptyShutdownCompletionIsAccepted() {
        assertDoesNotThrow(() -> NativeCacheWorkScenario.verifyShutdownCaller(
                CompletableFuture.completedFuture(null)));
    }

    @Test
    void shutdownFailureIsAccepted() {
        assertDoesNotThrow(() -> NativeCacheWorkScenario.verifyShutdownCaller(
                CompletableFuture.failedFuture(new IllegalStateException("closed"))));
    }

    @Test
    void ordinaryValueIsNotShutdownEvidence() {
        assertThrows(IllegalStateException.class, () -> NativeCacheWorkScenario.verifyShutdownCaller(
                CompletableFuture.completedFuture("value")));
    }

    @Test
    void logicalTimeoutIsNotShutdownEvidence() {
        assertThrows(IllegalStateException.class, () -> NativeCacheWorkScenario.verifyShutdownCaller(
                CompletableFuture.failedFuture(new LogicalCallTimeoutException(10_000, null))));
    }

    @Test
    void pendingCallerIsNotShutdownEvidence() {
        assertThrows(IllegalStateException.class, () -> NativeCacheWorkScenario.verifyShutdownCaller(
                new CompletableFuture<>()));
    }
}
