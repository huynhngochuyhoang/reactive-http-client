package io.github.huynhngochuyhoang.httpstarter.exception;

import java.util.Objects;

/** Local, non-queued cache work admission failure. No transport request was started. */
public final class CacheWorkRejectedException extends RuntimeException {
    public enum Reason { CALLER_CAPACITY, LOAD_CAPACITY }

    private final Reason reason;

    public CacheWorkRejectedException(Reason reason) {
        super(switch (Objects.requireNonNull(reason, "reason")) {
            case CALLER_CAPACITY -> "Response cache caller capacity exhausted";
            case LOAD_CAPACITY -> "Response cache foreground load capacity exhausted";
        }, null, false, false);
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
