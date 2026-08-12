package io.github.huynhngochuyhoang.httpstarter.core;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionReportingStateTest {

    private static final RequestArgumentResolver.ResolvedArgs RESOLVED =
            new RequestArgumentResolver.ResolvedArgs(Map.of(), Map.of(), Map.of(), null);

    @Test
    void priorAttemptCleanupCannotClearFinalAttemptEvidence() {
        SubscriptionReportingState state = new SubscriptionReportingState(RESOLVED);
        SubscriptionReportingState.Attempt first = state.beginAttempt(RESOLVED);
        first.observeRequestUrl(URI.create("http://test.local/first"));
        first.recordResponse(HttpStatus.SERVICE_UNAVAILABLE, Map.of("X-Attempt", List.of("first")));

        SubscriptionReportingState.Attempt second = state.beginAttempt(RESOLVED);
        second.observeRequestUrl(URI.create("http://test.local/second"));
        second.recordResponse(HttpStatus.OK, Map.of("X-Attempt", List.of("second")));

        state.resetAttemptEvidence(first);
        state.clearActiveAttempt(first);

        SubscriptionReportingState.TerminalSnapshot terminal = state.complete(
                SubscriptionReportingState.TerminalSignal.SUCCESS, "done", null);

        assertThat(state.activeAttempt()).isSameAs(second);
        assertThat(terminal).isNotNull();
        assertThat(terminal.attemptCount()).isEqualTo(2);
        assertThat(terminal.requestUrl()).hasToString("http://test.local/second");
        assertThat(terminal.responseStatus()).isEqualTo(HttpStatus.OK);
        assertThat(terminal.responseHeaders()).containsEntry("X-Attempt", List.of("second"));
    }

    @Test
    void exactlyOneCompetingTerminalSignalWinsWithOneImmutableSnapshot() throws InterruptedException {
        SubscriptionReportingState state = new SubscriptionReportingState(RESOLVED);
        SubscriptionReportingState.Attempt attempt = state.beginAttempt(RESOLVED);
        attempt.observeRequestUrl(URI.create("http://test.local/race"));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<SubscriptionReportingState.TerminalSnapshot> errorResult = new AtomicReference<>();
        AtomicReference<SubscriptionReportingState.TerminalSnapshot> cancelResult = new AtomicReference<>();
        RuntimeException error = new RuntimeException("terminal error");
        CancellationException cancellation = new CancellationException("cancelled");

        Thread errorThread = new Thread(() -> {
            ready.countDown();
            await(start);
            errorResult.set(state.complete(SubscriptionReportingState.TerminalSignal.ERROR, null, error));
        });
        Thread cancelThread = new Thread(() -> {
            ready.countDown();
            await(start);
            cancelResult.set(state.complete(SubscriptionReportingState.TerminalSignal.CANCEL, null, cancellation));
        });
        errorThread.start();
        cancelThread.start();
        ready.await();
        start.countDown();
        errorThread.join();
        cancelThread.join();

        assertThat(java.util.stream.Stream.of(errorResult.get(), cancelResult.get())
                .filter(snapshot -> snapshot != null)
                .toList()).containsExactly(state.terminalSnapshot());
        assertThat(state.terminalSnapshot().requestUrl()).hasToString("http://test.local/race");
        assertThat(state.terminalSnapshot().attemptCount()).isEqualTo(1);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }
}
