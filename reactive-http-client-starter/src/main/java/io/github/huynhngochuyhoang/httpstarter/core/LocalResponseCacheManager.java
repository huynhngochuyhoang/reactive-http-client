package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ClassUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Owns the optional local caches for one reactive client factory. */
final class LocalResponseCacheManager implements AutoCloseable {

    private static final String CAFFEINE_CLASS = "com.github.benmanes.caffeine.cache.Caffeine";
    private static final Set<String> REPRESENTATION_HEADERS = Set.of(
            HttpHeaders.CONTENT_TYPE.toLowerCase(Locale.ROOT),
            HttpHeaders.CONTENT_LANGUAGE.toLowerCase(Locale.ROOT),
            HttpHeaders.CONTENT_ENCODING.toLowerCase(Locale.ROOT),
            HttpHeaders.ETAG.toLowerCase(Locale.ROOT),
            HttpHeaders.LAST_MODIFIED.toLowerCase(Locale.ROOT),
            HttpHeaders.CACHE_CONTROL.toLowerCase(Locale.ROOT),
            HttpHeaders.EXPIRES.toLowerCase(Locale.ROOT),
            HttpHeaders.VARY.toLowerCase(Locale.ROOT));
    private static final Set<String> NON_CACHEABLE_RESPONSE_HEADERS = Set.of(
            HttpHeaders.WWW_AUTHENTICATE.toLowerCase(Locale.ROOT),
            HttpHeaders.PROXY_AUTHENTICATE.toLowerCase(Locale.ROOT));
    private static final int MAX_CACHED_RESPONSE_HEADER_VALUES = 32;
    private static final int MAX_CACHED_RESPONSE_HEADER_BYTES = 16 * 1024;

    private final ClassLoader classLoader;
    private final LongSupplier ticker;
    private final Scheduler refreshScheduler;
    private final Map<PolicyBounds, LocalResponseCache> caches = new LinkedHashMap<>();
    private final Map<FlightKey, InFlightLoad> inFlightLoads = new HashMap<>();
    private final Map<FlightKey, InFlightRefresh> inFlightRefreshes = new HashMap<>();
    private final Sinks.Empty<Void> shutdown = Sinks.empty();
    private final AtomicBoolean closed = new AtomicBoolean();

    private LocalResponseCacheManager(ClassLoader classLoader, LongSupplier ticker, Scheduler refreshScheduler) {
        this.classLoader = classLoader;
        this.ticker = ticker;
        this.refreshScheduler = refreshScheduler;
    }

    static LocalResponseCacheManager lazy(ClassLoader classLoader) {
        return new LocalResponseCacheManager(classLoader, System::nanoTime, Schedulers.parallel());
    }

    static LocalResponseCacheManager createForClient(
            Class<?> clientInterface,
            String clientName,
            MethodMetadataCache metadataCache,
            ReactiveHttpClientProperties.ClientConfig clientConfig,
            ClassLoader classLoader) {
        LocalResponseCacheManager manager = lazy(classLoader);
        for (Method method : clientInterface.getMethods()) {
            if (method.isDefault() || !Modifier.isAbstract(method.getModifiers())) {
                continue;
            }
            RequestPlan plan = RequestPlan.from(metadataCache.get(method), clientInterface);
            EffectiveCachePolicy.Selection selection = EffectiveCachePolicy.resolve(plan, clientConfig);
            if (selection.enabled()) {
                manager.cache(selection, clientName);
            }
        }
        return manager;
    }

    static LocalResponseCacheManager testing(LongSupplier ticker) {
        return new LocalResponseCacheManager(LocalResponseCacheManager.class.getClassLoader(), ticker, Schedulers.parallel());
    }

    static LocalResponseCacheManager testing(LongSupplier ticker, Scheduler refreshScheduler) {
        return new LocalResponseCacheManager(
                LocalResponseCacheManager.class.getClassLoader(), ticker, refreshScheduler);
    }

    Mono<?> getOrLoad(EffectiveCachePolicy.Selection selection,
                      CacheKeyContract.OpaqueKey key,
                      Supplier<Mono<?>> loader) {
        return getOrLoad(selection, key, loader, ResponseMetadata::successWithoutHeaders);
    }

    Mono<?> getOrLoad(EffectiveCachePolicy.Selection selection,
                      CacheKeyContract.OpaqueKey key,
                      Supplier<Mono<?>> loader,
                      Supplier<ResponseMetadata> responseMetadata) {
        return getOrLoad(selection, key, ignored -> loader.get(), responseMetadata, null, null);
    }

    Mono<?> getOrLoad(EffectiveCachePolicy.Selection selection,
                      CacheKeyContract.OpaqueKey key,
                      Function<SubscriptionReportingState, Mono<?>> loader,
                      Supplier<ResponseMetadata> responseMetadata,
                      SubscriptionReportingState callerState,
                      SubscriptionReportingState proposedLoadState) {
        return Mono.deferContextual(context -> {
            LocalResponseCache cache = cache(selection, "unknown");
            if (selection.policy().isSingleFlight()) {
                return coalescedLoad(
                        selection, cache, key, loader, responseMetadata, callerState, proposedLoadState, context);
            }
            LocalResponseCache.Lookup lookup = cache.lookup(key);
            if (lookup.hit()) {
                return cachedHit(selection, cache, key, lookup, loader, responseMetadata, proposedLoadState, context);
            }
            return load(cache, lookup.loadToken(), () -> loader.apply(null), responseMetadata);
        });
    }

    private Mono<?> coalescedLoad(EffectiveCachePolicy.Selection selection,
                                  LocalResponseCache cache,
                                  CacheKeyContract.OpaqueKey key,
                                  Function<SubscriptionReportingState, Mono<?>> loader,
                                  Supplier<ResponseMetadata> responseMetadata,
                                  SubscriptionReportingState callerState,
                                  SubscriptionReportingState proposedLoadState,
                                  ContextView context) {
        FlightKey flightKey = new FlightKey(cache, key);
        InFlightLoad flight = null;
        FlightMember member = null;
        LocalResponseCache.Lookup cachedLookup = null;
        boolean created = false;
        synchronized (inFlightLoads) {
            if (closed.get()) {
                return Mono.error(new IllegalStateException("The local response cache has been closed"));
            }
            LocalResponseCache.Lookup lookup = cache.lookup(key);
            if (lookup.hit()) {
                cachedLookup = lookup;
            }
            else {
                LocalResponseCache.LoadToken token = lookup.loadToken();
                InFlightLoad existing = inFlightLoads.get(flightKey);
                if (existing != null) {
                    cache.finish(token);
                    flight = existing;
                }
                else {
                    flight = new InFlightLoad(flightKey, cache, token, proposedLoadState);
                    inFlightLoads.put(flightKey, flight);
                    created = true;
                }
                member = flight.reserve(callerState);
            }
        }

        if (cachedLookup != null) {
            return cachedHit(selection, cache, key, cachedLookup, loader, responseMetadata,
                    proposedLoadState, context);
        }
        if (created) {
            startFlight(flight, loader, responseMetadata, context);
        }
        return flight.publisher(member);
    }

    private Mono<?> cachedHit(EffectiveCachePolicy.Selection selection,
                              LocalResponseCache cache,
                              CacheKeyContract.OpaqueKey key,
                              LocalResponseCache.Lookup lookup,
                              Function<SubscriptionReportingState, Mono<?>> loader,
                              Supplier<ResponseMetadata> responseMetadata,
                              SubscriptionReportingState refreshState,
                              ContextView context) {
        ReactiveHttpClientProperties.CachePolicyConfig policy = selection.policy();
        if (policy.isRefreshEnabled()
                && lookup.ageNanos() >= TimeUnit.MILLISECONDS.toNanos(policy.getRefreshAfterMs())) {
            triggerRefresh(cache, key, lookup, loader, responseMetadata, refreshState, context, policy);
        }
        return Mono.just(lookup.value());
    }

    private void triggerRefresh(LocalResponseCache cache,
                                CacheKeyContract.OpaqueKey key,
                                LocalResponseCache.Lookup lookup,
                                Function<SubscriptionReportingState, Mono<?>> loader,
                                Supplier<ResponseMetadata> responseMetadata,
                                SubscriptionReportingState refreshState,
                                ContextView context,
                                ReactiveHttpClientProperties.CachePolicyConfig policy) {
        LocalResponseCache.RefreshToken token;
        try {
            token = cache.beginRefresh(lookup.entryToken());
        }
        catch (IllegalStateException ignored) {
            return;
        }
        if (token == null) {
            return;
        }

        FlightKey refreshKey = new FlightKey(cache, key);
        InFlightRefresh refresh = new InFlightRefresh(refreshKey, cache, token, refreshState);
        boolean rejected;
        synchronized (inFlightRefreshes) {
            rejected = closed.get() || inFlightRefreshes.containsKey(refreshKey);
            if (!rejected) {
                inFlightRefreshes.put(refreshKey, refresh);
            }
        }
        if (rejected) {
            cache.finishRefresh(token);
            return;
        }
        if (!cache.isRefreshCurrent(token)) {
            cancelRefresh(refreshKey);
            return;
        }
        startRefresh(refresh, loader, responseMetadata, context, policy.getRefreshTimeoutMs());
    }

    @SuppressWarnings("unchecked")
    private void startRefresh(InFlightRefresh refresh,
                              Function<SubscriptionReportingState, Mono<?>> loader,
                              Supplier<ResponseMetadata> responseMetadata,
                              ContextView context,
                              long refreshTimeoutMs) {
        long deadlineNanos = Math.min(
                TimeUnit.MILLISECONDS.toNanos(refreshTimeoutMs),
                refresh.cache.hardExpiryRemainingNanos(refresh.refreshToken));
        if (deadlineNanos <= 0) {
            cancelRefresh(refresh.key);
            return;
        }

        Mono<Object> source;
        try {
            source = (Mono<Object>) loader.apply(refresh.loadState);
        }
        catch (Throwable ignored) {
            finishRefresh(refresh);
            return;
        }
        boolean cancelled;
        synchronized (inFlightRefreshes) {
            cancelled = refresh.terminal;
        }
        if (cancelled) {
            refresh.cache.finishRefresh(refresh.refreshToken);
            return;
        }

        Disposable subscription;
        try {
            subscription = source
                    .doOnSuccess(value -> {
                        if (value != null) {
                            cacheCandidate(value, responseMetadata.get())
                                    .ifPresent(candidate -> refresh.cache.publishRefresh(
                                            refresh.refreshToken, candidate));
                        }
                    })
                    .timeout(Duration.ofNanos(deadlineNanos), refreshScheduler)
                    .takeUntilOther(shutdown.asMono())
                    .doFinally(ignored -> finishRefresh(refresh))
                    .subscribe(ignored -> { }, ignored -> { }, () -> { }, Context.of(context));
        }
        catch (Throwable ignored) {
            finishRefresh(refresh);
            return;
        }

        boolean dispose;
        synchronized (inFlightRefreshes) {
            dispose = refresh.terminal;
            if (!dispose) {
                refresh.sourceSubscription = subscription;
            }
        }
        if (dispose) {
            subscription.dispose();
        }
    }

    private void finishRefresh(InFlightRefresh refresh) {
        synchronized (inFlightRefreshes) {
            if (refresh.terminal) {
                return;
            }
            refresh.terminal = true;
            inFlightRefreshes.remove(refresh.key, refresh);
        }
        refresh.cache.finishRefresh(refresh.refreshToken);
    }

    private void cancelRefresh(FlightKey key) {
        InFlightRefresh refresh;
        synchronized (inFlightRefreshes) {
            refresh = inFlightRefreshes.remove(key);
            if (refresh == null || refresh.terminal) {
                return;
            }
            refresh.terminal = true;
        }
        if (refresh.sourceSubscription != null) {
            refresh.sourceSubscription.dispose();
        }
        refresh.cache.finishRefresh(refresh.refreshToken);
    }

    private void cancelRefreshForRemoval(
            LocalResponseCache cache, CacheKeyContract.OpaqueKey key) {
        cancelRefresh(new FlightKey(cache, key));
    }

    private Mono<?> load(LocalResponseCache cache,
                         LocalResponseCache.LoadToken token,
                         Supplier<Mono<?>> loader,
                         Supplier<ResponseMetadata> responseMetadata) {
        return Mono.defer(() -> {
            Mono<?> source;
            try {
                source = loader.get();
            } catch (Throwable error) {
                cache.finish(token);
                return Mono.error(error);
            }
            return source
                    .doOnSuccess(value -> {
                        if (value != null) {
                            cacheCandidate(value, responseMetadata.get())
                                    .ifPresent(candidate -> cache.publish(token, candidate));
                        }
                    })
                    .doFinally(ignored -> cache.finish(token));
        });
    }

    @SuppressWarnings("unchecked")
    private void startFlight(InFlightLoad flight,
                             Function<SubscriptionReportingState, Mono<?>> loader,
                             Supplier<ResponseMetadata> responseMetadata,
                             ContextView context) {
        synchronized (inFlightLoads) {
            if (flight.terminal) {
                flight.cache.finish(flight.loadToken);
                return;
            }
            flight.sourceStarted = true;
        }

        Mono<Object> source = (Mono<Object>) load(
                flight.cache,
                flight.loadToken,
                () -> loader.apply(flight.loadState),
                responseMetadata).takeUntilOther(shutdown.asMono());
        Disposable subscription;
        try {
            subscription = source.subscribe(
                    value -> completeFlightValue(flight, value),
                    error -> completeFlightError(flight, error),
                    () -> completeFlightEmpty(flight),
                    Context.of(context));
        }
        catch (Throwable error) {
            flight.cache.finish(flight.loadToken);
            completeFlightError(flight, error);
            return;
        }

        boolean dispose;
        synchronized (inFlightLoads) {
            dispose = flight.terminal;
            if (!dispose) {
                flight.sourceSubscription = subscription;
            }
        }
        if (dispose) {
            subscription.dispose();
        }
    }

    private void completeFlightValue(InFlightLoad flight, Object value) {
        if (finishFlight(flight)) {
            flight.result.tryEmitValue(value);
        }
    }

    private void completeFlightError(InFlightLoad flight, Throwable error) {
        if (finishFlight(flight)) {
            flight.result.tryEmitError(error);
        }
    }

    private void completeFlightEmpty(InFlightLoad flight) {
        if (finishFlight(flight)) {
            flight.result.tryEmitEmpty();
        }
    }

    private boolean finishFlight(InFlightLoad flight) {
        synchronized (inFlightLoads) {
            if (flight.terminal) {
                return false;
            }
            flight.terminal = true;
            inFlightLoads.remove(flight.key, flight);
            flight.freezeDiagnosticOwner(false);
            return true;
        }
    }

    private void releaseFlightMember(InFlightLoad flight, FlightMember member) {
        Disposable sourceToCancel = null;
        boolean abandoned = false;
        synchronized (inFlightLoads) {
            if (!member.released.compareAndSet(false, true)) {
                return;
            }
            flight.members.remove(member);
            if (flight.diagnosticOwner == member) {
                flight.freezeDiagnosticOwner(true);
                flight.diagnosticOwner = null;
                if (!flight.terminal && !flight.members.isEmpty()) {
                    flight.assignDiagnosticOwner(flight.members.iterator().next());
                }
            }
            if (!flight.terminal && flight.members.isEmpty()) {
                flight.terminal = true;
                inFlightLoads.remove(flight.key, flight);
                sourceToCancel = flight.sourceSubscription;
                abandoned = true;
            }
        }
        if (sourceToCancel != null) {
            sourceToCancel.dispose();
        }
        if (abandoned) {
            flight.result.tryEmitEmpty();
        }
    }

    Snapshot snapshot() {
        synchronized (caches) {
            long capacity = 0;
            long size = 0;
            for (Map.Entry<PolicyBounds, LocalResponseCache> entry : caches.entrySet()) {
                capacity = Math.addExact(capacity, entry.getKey().maximumSize);
                size = Math.addExact(size, entry.getValue().estimatedSize());
            }
            return new Snapshot(caches.size(), capacity, size, closed.get());
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        List<InFlightLoad> flights;
        synchronized (inFlightLoads) {
            flights = List.copyOf(inFlightLoads.values());
            flights.forEach(flight -> {
                flight.terminal = true;
                flight.freezeDiagnosticOwner(true);
            });
            inFlightLoads.clear();
        }
        List<InFlightRefresh> refreshes;
        synchronized (inFlightRefreshes) {
            refreshes = List.copyOf(inFlightRefreshes.values());
            refreshes.forEach(refresh -> refresh.terminal = true);
            inFlightRefreshes.clear();
        }
        shutdown.tryEmitEmpty();
        for (InFlightLoad flight : flights) {
            if (!flight.sourceStarted) {
                flight.cache.finish(flight.loadToken);
            }
            if (flight.sourceSubscription != null) {
                flight.sourceSubscription.dispose();
            }
            flight.result.tryEmitEmpty();
        }
        for (InFlightRefresh refresh : refreshes) {
            if (refresh.sourceSubscription != null) {
                refresh.sourceSubscription.dispose();
            }
            refresh.cache.finishRefresh(refresh.refreshToken);
        }
        synchronized (caches) {
            caches.values().forEach(LocalResponseCache::close);
            caches.clear();
        }
    }

    private LocalResponseCache cache(EffectiveCachePolicy.Selection selection, String clientName) {
        if (closed.get()) {
            throw new IllegalStateException("The local response cache for client '" + clientName + "' has been closed");
        }
        ReactiveHttpClientProperties.CachePolicyConfig policy = selection.policy();
        if (policy == null || policy.getTtlMs() == null || policy.getMaximumSize() == null) {
            throw new IllegalStateException("Cache policy '" + selection.policyName() + "' for client '"
                    + clientName + "' has not passed startup validation");
        }
        PolicyBounds bounds = new PolicyBounds(
                selection.policyName(), policy.getTtlMs(), policy.getMaximumSize(),
                policy.getRefreshAfterMs() != null ? policy.getRefreshAfterMs() : 0,
                policy.getRefreshTimeoutMs() != null ? policy.getRefreshTimeoutMs() : 0);
        synchronized (caches) {
            if (closed.get()) {
                throw new IllegalStateException(
                        "The local response cache for client '" + clientName + "' has been closed");
            }
            return caches.computeIfAbsent(bounds, ignored -> newCache(selection.policyName(), clientName, bounds));
        }
    }

    private LocalResponseCache newCache(String policyName, String clientName, PolicyBounds bounds) {
        if (!ClassUtils.isPresent(CAFFEINE_CLASS, classLoader)) {
            throw new IllegalStateException("Reactive HTTP client '" + clientName + "' selects response-cache policy '"
                    + policyName + "', but optional dependency com.github.ben-manes.caffeine:caffeine is not available. "
                    + "Add Caffeine at runtime or disable response caching for this client.");
        }
        return new CaffeineLocalResponseCache(
                bounds.ttlMs, bounds.maximumSize, ticker, this::cancelRefreshForRemoval);
    }

    private java.util.Optional<Object> cacheCandidate(Object value, ResponseMetadata responseMetadata) {
        if (responseMetadata != null
                && (isRedirect(responseMetadata.statusCode())
                || hasNonCacheableHeaders(responseMetadata.headers()))) {
            return java.util.Optional.empty();
        }
        if (!(value instanceof ResponseEntity<?> entity)) {
            return java.util.Optional.of(value);
        }
        if (isRedirect(entity.getStatusCode().value())
                || hasNonCacheableHeaders(entity.getHeaders())) {
            return java.util.Optional.empty();
        }
        HttpHeaders retained = new HttpHeaders();
        int retainedValues = 0;
        int retainedBytes = 0;
        entity.getHeaders().headerSet().forEach(header -> {
            String name = header.getKey();
            if (REPRESENTATION_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                retained.put(name, java.util.List.copyOf(header.getValue()));
            }
        });
        for (Map.Entry<String, java.util.List<String>> header : retained.headerSet()) {
            retainedValues = Math.addExact(retainedValues, header.getValue().size());
            if (retainedValues > MAX_CACHED_RESPONSE_HEADER_VALUES) {
                return java.util.Optional.empty();
            }
            for (String headerValue : header.getValue()) {
                if (headerValue.length() > MAX_CACHED_RESPONSE_HEADER_BYTES) {
                    return java.util.Optional.empty();
                }
                retainedBytes = Math.addExact(
                        retainedBytes, headerValue.getBytes(StandardCharsets.UTF_8).length);
                if (retainedBytes > MAX_CACHED_RESPONSE_HEADER_BYTES) {
                    return java.util.Optional.empty();
                }
            }
        }
        return java.util.Optional.of(new ResponseEntity<>(entity.getBody(), retained, entity.getStatusCode()));
    }

    private boolean hasNonCacheableHeaders(Map<String, ? extends java.util.List<String>> headers) {
        return hasNonCacheableHeaderNames(headers.keySet());
    }

    private boolean hasNonCacheableHeaders(HttpHeaders headers) {
        return hasNonCacheableHeaderNames(
                headers.headerSet().stream().map(Map.Entry::getKey).toList());
    }

    private boolean hasNonCacheableHeaderNames(Iterable<String> headerNames) {
        for (String headerName : headerNames) {
            String normalized = headerName.toLowerCase(Locale.ROOT);
            if (SensitiveHeaders.isSensitive(headerName)
                    || NON_CACHEABLE_RESPONSE_HEADERS.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRedirect(int statusCode) {
        return statusCode >= 300 && statusCode < 400;
    }

    record Snapshot(int policyCount, long configuredCapacity, long currentSize, boolean closed) {
    }

    record ResponseMetadata(int statusCode, Map<String, java.util.List<String>> headers) {

        ResponseMetadata {
            if (headers == null || headers.isEmpty()) {
                headers = Map.of();
            }
            else {
                Map<String, java.util.List<String>> copied = new LinkedHashMap<>();
                headers.forEach((name, values) -> copied.put(name, java.util.List.copyOf(values)));
                headers = Map.copyOf(copied);
            }
        }

        static ResponseMetadata successWithoutHeaders() {
            return new ResponseMetadata(200, Map.of());
        }
    }

    private record PolicyBounds(
            String policyName, long ttlMs, long maximumSize, long refreshAfterMs, long refreshTimeoutMs) {
    }

    private record FlightKey(LocalResponseCache cache, CacheKeyContract.OpaqueKey key) {
    }

    private static final class InFlightRefresh {
        private final FlightKey key;
        private final LocalResponseCache cache;
        private final LocalResponseCache.RefreshToken refreshToken;
        private final SubscriptionReportingState loadState;
        private Disposable sourceSubscription;
        private boolean terminal;

        private InFlightRefresh(FlightKey key,
                                LocalResponseCache cache,
                                LocalResponseCache.RefreshToken refreshToken,
                                SubscriptionReportingState loadState) {
            this.key = key;
            this.cache = cache;
            this.refreshToken = refreshToken;
            this.loadState = loadState;
        }
    }

    private final class InFlightLoad {
        private final FlightKey key;
        private final LocalResponseCache cache;
        private final LocalResponseCache.LoadToken loadToken;
        private final SubscriptionReportingState loadState;
        private final Sinks.One<Object> result = Sinks.one();
        private final Set<FlightMember> members = new LinkedHashSet<>();
        private FlightMember diagnosticOwner;
        private Disposable sourceSubscription;
        private boolean sourceStarted;
        private boolean terminal;

        private InFlightLoad(FlightKey key,
                             LocalResponseCache cache,
                             LocalResponseCache.LoadToken loadToken,
                             SubscriptionReportingState loadState) {
            this.key = key;
            this.cache = cache;
            this.loadToken = loadToken;
            this.loadState = loadState;
        }

        private FlightMember reserve(SubscriptionReportingState callerState) {
            FlightMember member = new FlightMember(callerState);
            members.add(member);
            if (diagnosticOwner == null) {
                assignDiagnosticOwner(member);
            }
            return member;
        }

        private void assignDiagnosticOwner(FlightMember member) {
            diagnosticOwner = member;
            if (member.callerState != null) {
                member.callerState.followAttemptEvidenceFrom(loadState);
            }
        }

        private void freezeDiagnosticOwner(boolean detached) {
            if (diagnosticOwner != null && diagnosticOwner.callerState != null) {
                if (detached) {
                    diagnosticOwner.callerState.freezeAttemptEvidenceForDetachFrom(loadState);
                }
                else {
                    diagnosticOwner.callerState.freezeAttemptEvidenceFrom(loadState);
                }
            }
        }

        private Mono<?> publisher(FlightMember member) {
            return result.asMono().doFinally(ignored -> releaseFlightMember(this, member));
        }
    }

    private static final class FlightMember {
        private final SubscriptionReportingState callerState;
        private final AtomicBoolean released = new AtomicBoolean();

        private FlightMember(SubscriptionReportingState callerState) {
            this.callerState = callerState;
        }
    }
}
