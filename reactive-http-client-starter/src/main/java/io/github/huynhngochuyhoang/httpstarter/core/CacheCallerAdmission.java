package io.github.huynhngochuyhoang.httpstarter.core;

import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/** Internal caller gate. Public selection is deferred until all three work dimensions are enforced. */
final class CacheCallerAdmission {
    static final Object CONTEXT_KEY = new Object();
    private final Map<String, Capacity> policies;
    private boolean closed;

    CacheCallerAdmission(Map<String, Integer> maximums) {
        Map<String, Capacity> capacities = new LinkedHashMap<>();
        maximums.forEach((name, maximum) -> {
            if (maximum == null || maximum < 1 || maximum > 1_000_000) {
                throw new IllegalArgumentException("Caller capacity must be in [1, 1000000]");
            }
            capacities.put(name, new Capacity(maximum));
        });
        policies = Map.copyOf(capacities);
    }

    synchronized Reservation acquire(String policy) {
        if (closed) {
            throw new IllegalStateException("Response cache is closed");
        }
        Capacity capacity = policies.get(policy);
        if (capacity == null) {
            return null;
        }
        if (capacity.active == capacity.maximum) {
            throw new Rejected();
        }
        capacity.active++;
        return new Reservation(this, capacity);
    }

    synchronized int active(String policy) {
        Capacity capacity = policies.get(policy);
        return capacity == null ? 0 : capacity.active;
    }

    synchronized void close() {
        closed = true;
    }

    private synchronized void release(Capacity capacity) {
        capacity.active--;
    }

    static <T> Mono<T> preparing(ContextView context, Supplier<Mono<T>> callback) {
        try {
            Mono<T> result = call(context, callback::get);
            return result != null ? result : Mono.empty();
        } catch (RuntimeException | Error error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    static <T> T call(ContextView context, Callable<T> callback) throws Exception {
        Reservation reservation = context.getOrDefault(CONTEXT_KEY, null);
        if (reservation == null) {
            return callback.call();
        }
        if (!reservation.enter()) {
            return null;
        }
        try {
            T result = callback.call();
            return reservation.active() ? result : null;
        } catch (Exception error) {
            if (reservation.active()) {
                throw error;
            }
            return null;
        } finally {
            reservation.exit();
        }
    }

    static <T> Mono<T> subscribePreparation(Mono<T> source) {
        return Mono.deferContextual(context -> {
            Reservation reservation = context.getOrDefault(CONTEXT_KEY, null);
            if (reservation == null) {
                return source;
            }
            return Mono.create(sink -> {
                if (!reservation.enter()) {
                    sink.success();
                    return;
                }
                PreparationSubscriber<T> subscriber = new PreparationSubscriber<>(sink);
                sink.onCancel(subscriber::cancel);
                try {
                    source.subscribe(subscriber);
                } finally {
                    reservation.exit();
                    subscriber.attached();
                }
            });
        });
    }

    // Hold synchronous subscription frames, but deliver their terminal only after the frame exits.
    private static final class PreparationSubscriber<T> implements CoreSubscriber<T> {
        private final MonoSink<T> sink;
        private Subscription subscription;
        private T value;
        private Throwable error;
        private boolean attaching = true;
        private boolean finished;
        private boolean cancelled;

        private PreparationSubscriber(MonoSink<T> sink) {
            this.sink = sink;
        }

        @Override public Context currentContext() { return Context.of(sink.contextView()); }

        @Override public void onSubscribe(Subscription subscription) {
            synchronized (this) {
                if (cancelled) {
                    subscription.cancel();
                    return;
                }
                this.subscription = subscription;
            }
            subscription.request(Long.MAX_VALUE);
        }

        @Override public synchronized void onNext(T value) {
            if (!cancelled) {
                this.value = value;
            }
        }

        @Override public synchronized void onError(Throwable error) {
            if (!cancelled) {
                this.error = error;
                finished = true;
                drain();
            }
        }

        @Override public synchronized void onComplete() {
            finished = true;
            drain();
        }

        private synchronized void attached() {
            attaching = false;
            drain();
        }

        private void drain() {
            if (attaching || !finished || cancelled) {
                return;
            }
            T result = value;
            Throwable failure = error;
            value = null;
            error = null;
            subscription = null;
            cancelled = true;
            if (failure != null) {
                sink.error(failure);
            } else {
                sink.success(result);
            }
        }

        private void cancel() {
            Subscription upstream;
            synchronized (this) {
                cancelled = true;
                value = null;
                error = null;
                upstream = subscription;
                subscription = null;
            }
            if (upstream != null) {
                upstream.cancel();
            }
        }
    }

    static final class Reservation {
        private CacheCallerAdmission owner;
        private final Capacity capacity;
        private int callbacks;
        private boolean terminal;

        private Reservation(CacheCallerAdmission owner, Capacity capacity) {
            this.owner = owner;
            this.capacity = capacity;
        }

        private synchronized boolean enter() {
            if (terminal) {
                return false;
            }
            callbacks++;
            return true;
        }

        private synchronized boolean active() {
            return !terminal;
        }

        private synchronized void exit() {
            callbacks--;
            releaseIfFinished();
        }

        synchronized void complete() {
            terminal = true;
            releaseIfFinished();
        }

        private void releaseIfFinished() {
            if (terminal && callbacks == 0 && owner != null) {
                owner.release(capacity);
                owner = null;
            }
        }
    }

    private static final class Capacity {
        private final int maximum;
        private int active;

        private Capacity(int maximum) {
            this.maximum = maximum;
        }
    }

    // The public reason/category/outcome additions remain gated with public work selection.
    static final class Rejected extends RuntimeException {
        Rejected() {
            super("Response cache caller capacity exhausted", null, false, false);
        }
    }
}
