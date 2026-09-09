package io.github.huynhngochuyhoang.httpstarter.core;

import org.reactivestreams.Subscription;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.*;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Shared frame-aware reservations for independently owned cache work dimensions. */
class CacheWorkAdmission {
    static final Object CONTEXT_KEY = new Object();
    private final Map<String, Capacity> policies;
    private boolean closed;
    private final Supplier<? extends RuntimeException> rejection;

    CacheWorkAdmission(Map<String, Integer> maximums, Supplier<? extends RuntimeException> rejection) {
        this.rejection = rejection;
        Map<String, Capacity> capacities = new LinkedHashMap<>();
        maximums.forEach((name, maximum) -> {
            if (maximum == null || maximum < 1 || maximum > 1_000_000) {
                throw new IllegalArgumentException("Work capacity must be in [1, 1000000]");
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
            throw rejection.get();
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

    static ExchangeFilterFunction preparingFilter(ExchangeFilterFunction filter) {
        return (request, next) -> Mono.deferContextual(context -> subscribePreparation(
                // Capture the owner guard for next.exchange calls from asynchronous filter continuations.
                preparing(context, () -> filter.filter(request, updatedRequest -> subscribePreparation(
                        preparing(context, () -> next.exchange(updatedRequest)))))));
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

    static <T> Mono<T> own(Mono<T> source, Reservation reservation, Consumer<SignalType> cleanup) {
        AtomicBoolean finished = new AtomicBoolean();
        Consumer<SignalType> finish = signal -> {
            if (finished.compareAndSet(false, true)) {
                try {
                    cleanup.accept(signal);
                } finally {
                    reservation.complete();
                }
            }
        };
        return Mono.<T>create(sink -> {
            BaseSubscriber<T> subscriber = new BaseSubscriber<>() {
                private T pendingValue;

                @Override public Context currentContext() { return Context.of(sink.contextView()); }
                @Override protected synchronized void hookOnNext(T value) {
                    if (!isDisposed()) {
                        pendingValue = value;
                    }
                }
                @Override protected void hookOnComplete() {
                    T value = takeValue();
                    finish.accept(SignalType.ON_COMPLETE);
                    sink.success(value);
                }
                @Override protected void hookOnError(Throwable error) {
                    takeValue();
                    finish.accept(SignalType.ON_ERROR);
                    sink.error(error);
                }
                @Override protected void hookOnCancel() {
                    Operators.onDiscard(takeValue(), currentContext());
                }

                private synchronized T takeValue() {
                    T value = pendingValue;
                    pendingValue = null;
                    return value;
                }
            };
            sink.onCancel(() -> {
                // The cancel frame must unwind even if success/error wins the terminal race.
                boolean entered = reservation.enter();
                try {
                    finish.accept(SignalType.CANCEL);
                    subscriber.dispose();
                } finally {
                    if (entered) {
                        reservation.exit();
                    }
                }
            });
            subscribePreparation(source).subscribe(subscriber);
        }).contextWrite(context -> context.put(CONTEXT_KEY, reservation));
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
        private CacheWorkAdmission owner;
        private final Capacity capacity;
        private int callbacks;
        private boolean terminal;

        private Reservation(CacheWorkAdmission owner, Capacity capacity) {
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
}
