package io.github.huynhngochuyhoang.httpstarter.core;

import org.springframework.http.HttpStatusCode;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Subscription-local mutable state and its immutable terminal projection. */
final class SubscriptionReportingState {

    private final AtomicReference<String> generatedIdempotencyKey = new AtomicReference<>();
    private final AtomicReference<RequestArgumentResolver.ResolvedArgs> initialResolved;
    private final AtomicInteger attemptCount = new AtomicInteger();
    private final long startedAtNanos;
    private final AtomicBoolean firstAttempt = new AtomicBoolean(true);
    private final AtomicReference<Attempt> activeAttempt = new AtomicReference<>();
    private final AtomicReference<Attempt> latestAttempt = new AtomicReference<>();
    private final AtomicReference<TerminalSnapshot> terminalSnapshot = new AtomicReference<>();

    SubscriptionReportingState(RequestArgumentResolver.ResolvedArgs initialResolved) {
        this.initialResolved = new AtomicReference<>(initialResolved);
        this.startedAtNanos = System.nanoTime();
    }

    void prepareInitialResolved(RequestArgumentResolver.ResolvedArgs resolved) {
        if (attemptCount.get() == 0 && terminalSnapshot.get() == null) {
            initialResolved.set(resolved);
        }
    }

    AtomicReference<String> generatedIdempotencyKey() {
        return generatedIdempotencyKey;
    }

    synchronized Attempt beginAttempt(RequestArgumentResolver.ResolvedArgs preparedResolved) {
        int number = attemptCount.incrementAndGet();
        Attempt attempt = new Attempt(number, preparedResolved);
        latestAttempt.set(attempt);
        activeAttempt.set(attempt);
        return attempt;
    }

    Attempt activeAttempt() {
        return activeAttempt.get();
    }

    int attemptCount() {
        return attemptCount.get();
    }

    long elapsedMillis() {
        long elapsedNanos = System.nanoTime() - startedAtNanos;
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0L, elapsedNanos));
    }

    boolean markFirstAttemptStarted() {
        return firstAttempt.compareAndSet(true, false);
    }

    synchronized void clearActiveAttempt(Attempt attempt) {
        if (attempt != null && activeAttempt.get() == attempt) {
            activeAttempt.set(null);
        }
    }

    synchronized void resetAttemptEvidence(Attempt attempt) {
        if (attempt != null && activeAttempt.get() == attempt && latestAttempt.get() == attempt) {
            attempt.resetEvidence();
        }
    }

    synchronized void clearEvidenceWhenNoAttemptIsActive() {
        if (activeAttempt.get() == null) {
            Attempt attempt = latestAttempt.get();
            if (attempt != null) {
                attempt.resetEvidence();
            }
        }
    }

    synchronized TerminalSnapshot complete(TerminalSignal signal, Object responseBody, Throwable error) {
        Attempt attempt = latestAttempt.get();
        AttemptEvidence evidence = attempt != null ? attempt.evidence() : AttemptEvidence.empty();
        TerminalSnapshot candidate = new TerminalSnapshot(
                signal,
                attempt != null ? attempt.preparedResolved() : initialResolved.get(),
                evidence.requestUrl(),
                evidence.finalRequestObservation(),
                elapsedMillis(),
                evidence.responseStatus(),
                evidence.responseHeaders(),
                responseBody,
                error,
                attemptCount.get());
        return terminalSnapshot.compareAndSet(null, candidate) ? candidate : null;
    }

    TerminalSnapshot terminalSnapshot() {
        return terminalSnapshot.get();
    }

    enum TerminalSignal {
        SUCCESS,
        ERROR,
        CANCEL
    }

    static final class Attempt {
        private final int number;
        private final RequestArgumentResolver.ResolvedArgs preparedResolved;
        private final AtomicReference<AttemptEvidence> evidence = new AtomicReference<>(AttemptEvidence.empty());

        private Attempt(int number, RequestArgumentResolver.ResolvedArgs preparedResolved) {
            this.number = number;
            this.preparedResolved = preparedResolved;
        }

        int number() {
            return number;
        }

        RequestArgumentResolver.ResolvedArgs preparedResolved() {
            return preparedResolved;
        }

        void observeRequestUrl(URI requestUrl) {
            evidence.updateAndGet(current -> current.withRequestUrl(requestUrl));
        }

        void observeFinalRequest(FinalRequestObservation observation) {
            evidence.updateAndGet(current -> current.withFinalRequestObservation(observation));
        }

        HttpStatusCode responseStatus() {
            return evidence.get().responseStatus();
        }

        void recordResponse(HttpStatusCode status, Map<String, List<String>> headers) {
            evidence.updateAndGet(current -> current.withResponse(status, headers));
        }

        private AttemptEvidence evidence() {
            return evidence.get();
        }

        private void resetEvidence() {
            evidence.set(AttemptEvidence.empty());
        }
    }

    private record AttemptEvidence(
            URI requestUrl,
            FinalRequestObservation finalRequestObservation,
            HttpStatusCode responseStatus,
            Map<String, List<String>> responseHeaders) {

        private static AttemptEvidence empty() {
            return new AttemptEvidence(null, null, null, Map.of());
        }

        private AttemptEvidence withRequestUrl(URI requestUrl) {
            return new AttemptEvidence(requestUrl, finalRequestObservation, responseStatus, responseHeaders);
        }

        private AttemptEvidence withFinalRequestObservation(FinalRequestObservation observation) {
            return new AttemptEvidence(requestUrl, observation, responseStatus, responseHeaders);
        }

        private AttemptEvidence withResponse(HttpStatusCode status, Map<String, List<String>> headers) {
            return new AttemptEvidence(
                    requestUrl,
                    finalRequestObservation,
                    status,
                    headers == null || headers.isEmpty() ? Map.of() : Map.copyOf(headers));
        }
    }

    record TerminalSnapshot(
            TerminalSignal signal,
            RequestArgumentResolver.ResolvedArgs preparedResolved,
            URI requestUrl,
            FinalRequestObservation finalRequestObservation,
            long durationMs,
            HttpStatusCode responseStatus,
            Map<String, List<String>> responseHeaders,
            Object responseBody,
            Throwable error,
            int attemptCount) {

        TerminalSnapshot {
            responseHeaders = responseHeaders == null || responseHeaders.isEmpty()
                    ? Map.of()
                    : Map.copyOf(responseHeaders);
        }

        URI finalRequestUrl() {
            return finalRequestObservation != null ? finalRequestObservation.url() : requestUrl;
        }
    }

    record FinalRequestObservation(String httpMethod, URI url, Map<String, String> headers) {
        FinalRequestObservation {
            headers = headers == null || headers.isEmpty() ? Map.of() : Map.copyOf(headers);
        }
    }
}
