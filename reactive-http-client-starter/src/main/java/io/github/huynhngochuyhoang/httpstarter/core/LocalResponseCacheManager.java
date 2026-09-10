package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.CacheWorkRejectedException;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientCacheOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ClassUtils;
import reactor.core.Disposable;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
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

    private static final Logger log = LoggerFactory.getLogger(LocalResponseCacheManager.class);
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
    private final LocalResponseCacheMetrics metrics;
    private final boolean observabilityEnabled;
    private final String clientName;
    private CacheCallerAdmission callerAdmission;
    private CacheLoadAdmission loadAdmission;
    private CacheWorkAdmission refreshAdmission;
    private CacheWorkPolicy.Snapshot workSelection;
    private Runnable workValidation = () -> { };
    private final Map<PolicyBounds, LocalResponseCache> caches = new LinkedHashMap<>();
    private final Map<FlightKey, InFlightLoad> inFlightLoads = new HashMap<>();
    private final Map<FlightKey, InFlightRefresh> inFlightRefreshes = new HashMap<>();
    private final Sinks.Empty<Void> shutdown = Sinks.empty();
    private final AtomicBoolean closed = new AtomicBoolean();

    private LocalResponseCacheManager(ClassLoader classLoader,
                                      LongSupplier ticker,
                                      Scheduler refreshScheduler,
                                      LocalResponseCacheMetrics metrics,
                                      boolean observabilityEnabled,
                                      String clientName) {
        this(classLoader, ticker, refreshScheduler, metrics, observabilityEnabled, clientName, null);
    }

    private LocalResponseCacheManager(ClassLoader classLoader,
                                      LongSupplier ticker,
                                      Scheduler refreshScheduler,
                                      LocalResponseCacheMetrics metrics,
                                      boolean observabilityEnabled,
                                      String clientName,
                                      CacheCallerAdmission callerAdmission) {
        this(classLoader, ticker, refreshScheduler, metrics, observabilityEnabled, clientName,
                callerAdmission, null);
    }

    private LocalResponseCacheManager(ClassLoader classLoader,
                                      LongSupplier ticker,
                                      Scheduler refreshScheduler,
                                      LocalResponseCacheMetrics metrics,
                                      boolean observabilityEnabled,
                                      String clientName,
                                      CacheCallerAdmission callerAdmission,
                                      CacheLoadAdmission loadAdmission) {
        this(classLoader, ticker, refreshScheduler, metrics, observabilityEnabled, clientName,
                callerAdmission, loadAdmission, null);
    }

    private LocalResponseCacheManager(ClassLoader classLoader, LongSupplier ticker, Scheduler refreshScheduler,
                                      LocalResponseCacheMetrics metrics, boolean observabilityEnabled,
                                      String clientName, CacheCallerAdmission callerAdmission,
                                      CacheLoadAdmission loadAdmission, CacheWorkAdmission refreshAdmission) {
        this.classLoader = classLoader;
        this.ticker = ticker;
        this.refreshScheduler = refreshScheduler;
        this.metrics = metrics;
        this.observabilityEnabled = observabilityEnabled;
        this.clientName = clientName;
        this.callerAdmission = callerAdmission;
        this.loadAdmission = loadAdmission;
        this.refreshAdmission = refreshAdmission;
    }

    static LocalResponseCacheManager lazy(ClassLoader classLoader) {
        return new LocalResponseCacheManager(classLoader, System::nanoTime, Schedulers.parallel(),
                LocalResponseCacheMetrics.disabled(), false, "unknown");
    }

    static LocalResponseCacheManager createForClient(
            Class<?> clientInterface,
            String clientName,
            MethodMetadataCache metadataCache,
            ReactiveHttpClientProperties.ClientConfig clientConfig,
            ClassLoader classLoader) {
        return createForClient(clientInterface, clientName, metadataCache, clientConfig,
                classLoader, null, null);
    }

    static LocalResponseCacheManager createForClient(
            Class<?> clientInterface,
            String clientName,
            MethodMetadataCache metadataCache,
            ReactiveHttpClientProperties.ClientConfig clientConfig,
            ClassLoader classLoader,
            ReactiveHttpClientProperties.ObservabilityConfig observability,
            Object meterRegistry) {
        return createForClient(clientInterface, clientName, metadataCache, clientConfig,
                classLoader, observability, meterRegistry, System::nanoTime, Schedulers.parallel());
    }

    static LocalResponseCacheManager createForClient(
            Class<?> clientInterface,
            String clientName,
            MethodMetadataCache metadataCache,
            ReactiveHttpClientProperties.ClientConfig clientConfig,
            ClassLoader classLoader,
            ReactiveHttpClientProperties.ObservabilityConfig observability,
            Object meterRegistry,
            LongSupplier ticker,
            Scheduler refreshScheduler) {
        if (!hasSelectedCachePolicy(clientInterface, metadataCache, clientConfig)) {
            return null;
        }
        boolean cacheObservabilityEnabled = observability != null
                && observability.isEnabled()
                && observability.getCache() != null
                && observability.getCache().isEnabled();
        return createForClient(
                clientInterface,
                clientName,
                metadataCache,
                clientConfig,
                classLoader,
                ticker,
                refreshScheduler,
                cacheObservabilityEnabled
                        ? LocalResponseCacheMetrics.enabled(meterRegistry, clientName)
                        : LocalResponseCacheMetrics.disabled(),
                cacheObservabilityEnabled);
    }

    private static boolean hasSelectedCachePolicy(
            Class<?> clientInterface,
            MethodMetadataCache metadataCache,
            ReactiveHttpClientProperties.ClientConfig clientConfig) {
        for (Method method : clientInterface.getMethods()) {
            if (method.isDefault() || !Modifier.isAbstract(method.getModifiers())) {
                continue;
            }
            RequestPlan plan = RequestPlan.from(metadataCache.get(method), clientInterface);
            EffectiveCachePolicy.Decision decision = EffectiveCachePolicy.decide(
                    plan, clientConfig, EffectiveCachePolicy.effectiveHttpMethod(plan, clientConfig));
            if (decision.cacheable()) {
                return true;
            }
        }
        return false;
    }

    static LocalResponseCacheManager createForClient(
            Class<?> clientInterface,
            String clientName,
            MethodMetadataCache metadataCache,
            ReactiveHttpClientProperties.ClientConfig clientConfig,
            ClassLoader classLoader,
            LongSupplier ticker,
            Scheduler refreshScheduler,
            LocalResponseCacheMetrics metrics,
            boolean cacheObservabilityEnabled) {
        LocalResponseCacheManager manager = new LocalResponseCacheManager(
                classLoader,
                Objects.requireNonNull(ticker, "ticker"),
                Objects.requireNonNull(refreshScheduler, "refreshScheduler"),
                Objects.requireNonNull(metrics, "metrics"),
                cacheObservabilityEnabled,
                clientName);
        manager.configureWork(CacheWorkPolicy.freeze(clientInterface, clientName, metadataCache, clientConfig));
        manager.validateWorkWith(CacheWorkPolicy.validator(manager.workSelection, clientInterface, metadataCache, clientConfig));
        for (Method method : clientInterface.getMethods()) {
            if (method.isDefault() || !Modifier.isAbstract(method.getModifiers())) {
                continue;
            }
            RequestPlan plan = RequestPlan.from(metadataCache.get(method), clientInterface);
            EffectiveCachePolicy.Decision decision = EffectiveCachePolicy.decide(
                    plan, clientConfig, EffectiveCachePolicy.effectiveHttpMethod(plan, clientConfig));
            if (decision.cacheable()) {
                EffectiveCachePolicy.Selection selection = decision.selection();
                manager.cache(selection, clientName);
                manager.metrics.registerApi(plan.apiName());
            }
        }
        return manager;
    }

    static LocalResponseCacheManager testing(LongSupplier ticker) {
        return new LocalResponseCacheManager(LocalResponseCacheManager.class.getClassLoader(), ticker,
                Schedulers.parallel(), LocalResponseCacheMetrics.disabled(), false, "unknown");
    }

    static LocalResponseCacheManager testing(LongSupplier ticker, Scheduler refreshScheduler) {
        return new LocalResponseCacheManager(
                LocalResponseCacheManager.class.getClassLoader(), ticker, refreshScheduler,
                LocalResponseCacheMetrics.disabled(), false, "unknown");
    }

    static LocalResponseCacheManager testing(LongSupplier ticker, Scheduler refreshScheduler,
                                             Map<String, Integer> callerMaximums) {
        return new LocalResponseCacheManager(
                LocalResponseCacheManager.class.getClassLoader(), ticker, refreshScheduler,
                LocalResponseCacheMetrics.disabled(), false, "unknown",
                new CacheCallerAdmission(callerMaximums));
    }

    CacheCallerAdmission callerAdmission() {
        return callerAdmission;
    }

    synchronized void configureWork(CacheWorkPolicy.Snapshot selection) {
        if (workSelection != null) {
            if (!workSelection.policies().isEmpty() || !selection.policies().isEmpty()) {
                workSelection.requireUnchanged(selection);
            }
            return;
        }
        if (closed.get()) {
            throw new IllegalStateException("Response cache is closed");
        }
        workSelection = selection;
        if (!selection.policies().isEmpty()) {
            callerAdmission = new CacheCallerAdmission(
                    selection.maximums(CacheWorkPolicy.Limits::maximumConcurrentCallers));
            loadAdmission = new CacheLoadAdmission(
                    selection.maximums(CacheWorkPolicy.Limits::maximumConcurrentLoads));
            Map<String, Integer> refreshes = selection.maximums(CacheWorkPolicy.Limits::maximumConcurrentRefreshes);
            refreshAdmission = refreshes.isEmpty() ? null : new CacheWorkAdmission(refreshes, RefreshCapacity::new);
            selection.policies().forEach((name, limits) -> metrics.registerWork(
                    name, limits, callerAdmission, loadAdmission, refreshAdmission));
        }
    }

    void validateWorkWith(Runnable validation) {
        workValidation = validation;
    }

    static LocalResponseCacheManager testing(LongSupplier ticker, Scheduler scheduler,
                                             Map<String, Integer> callerMaximums,
                                             Map<String, Integer> loadMaximums) {
        return new LocalResponseCacheManager(
                LocalResponseCacheManager.class.getClassLoader(), ticker, scheduler,
                LocalResponseCacheMetrics.disabled(), false, "unknown",
                new CacheCallerAdmission(callerMaximums), new CacheLoadAdmission(loadMaximums));
    }

    int activeLoadsForTesting(String policyName) {
        return loadAdmission == null ? 0 : loadAdmission.active(policyName);
    }

    static LocalResponseCacheManager testing(LongSupplier ticker, Scheduler scheduler,
                                             Map<String, Integer> callers, Map<String, Integer> loads,
                                             Map<String, Integer> refreshes) {
        return new LocalResponseCacheManager(LocalResponseCacheManager.class.getClassLoader(), ticker, scheduler,
                LocalResponseCacheMetrics.disabled(), false, "unknown", new CacheCallerAdmission(callers),
                new CacheLoadAdmission(loads), new CacheWorkAdmission(refreshes, RefreshCapacity::new));
    }

    int activeRefreshesForTesting(String policyName) {
        return refreshAdmission == null ? 0 : refreshAdmission.active(policyName);
    }

    private static final class RefreshCapacity extends RuntimeException {
        private RefreshCapacity() { super(null, null, false, false); }
    }

    static LocalResponseCacheManager testing(LongSupplier ticker,
                                             Scheduler refreshScheduler,
                                             LocalResponseCacheMetrics metrics,
                                             String clientName) {
        return new LocalResponseCacheManager(
                LocalResponseCacheManager.class.getClassLoader(), ticker, refreshScheduler,
                metrics, metrics.enabled(), clientName);
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
        return getOrLoad(selection, key, "unknown", loader, responseMetadata, callerState, proposedLoadState);
    }

    Mono<?> getOrLoad(EffectiveCachePolicy.Selection selection,
                      CacheKeyContract.OpaqueKey key,
                      String apiName,
                      Function<SubscriptionReportingState, Mono<?>> loader,
                      Supplier<ResponseMetadata> responseMetadata,
                      SubscriptionReportingState callerState,
                      SubscriptionReportingState proposedLoadState) {
        return getOrLoad(selection, key, apiName, loader, responseMetadata,
                callerState, proposedLoadState, null);
    }

    Mono<?> getOrLoad(EffectiveCachePolicy.Selection selection,
                      CacheKeyContract.OpaqueKey key,
                      String apiName,
                      Function<SubscriptionReportingState, Mono<?>> loader,
                      Supplier<ResponseMetadata> responseMetadata,
                      SubscriptionReportingState callerState,
                      SubscriptionReportingState proposedLoadState,
                      ContextView detachedLoadContext) {
        return Mono.deferContextual(context -> {
            ContextView loadContext = detachedLoadContext != null ? detachedLoadContext : context;
            LocalResponseCache cache = cache(selection, "unknown");
            if (selection.policy().isSingleFlight()) {
                return coalescedLoad(
                        selection, cache, key, apiName, loader, responseMetadata,
                        callerState, proposedLoadState, loadContext);
            }
            LocalResponseCache.Lookup lookup;
            CacheWorkAdmission.Reservation reservation = null;
            if (loadAdmission == null) {
                lookup = cache.lookup(key);
            } else {
                synchronized (inFlightLoads) {
                    lookup = cache.lookup(key);
                    if (!lookup.hit()) {
                        try {
                            reservation = acquireLoad(selection.policyName());
                        } catch (RuntimeException error) {
                            cache.finish(lookup.loadToken());
                            metrics.lookup(apiName, "miss");
                            recordWorkRejection(selection.policyName(), apiName, callerState, error);
                            return Mono.error(error);
                        }
                    }
                }
            }
            if (lookup.hit()) {
                metrics.lookup(apiName, "hit");
                return cachedHit(selection, cache, key, apiName, lookup, loader, responseMetadata,
                        callerState, proposedLoadState, loadContext);
            }
            metrics.lookup(apiName, "miss");
            cacheOutcome(callerState, apiName, HttpClientCacheOutcome.MISS_LOADER);
            followLoad(callerState, proposedLoadState);
            return load(selection.policyName(), selection.policy(), cache, lookup.loadToken(), apiName,
                    () -> loader.apply(proposedLoadState), responseMetadata, reservation);
        });
    }

    private Mono<?> coalescedLoad(EffectiveCachePolicy.Selection selection,
                                  LocalResponseCache cache,
                                  CacheKeyContract.OpaqueKey key,
                                  String apiName,
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
                    CacheWorkAdmission.Reservation reservation;
                    try {
                        reservation = acquireLoad(selection.policyName());
                    } catch (RuntimeException error) {
                        cache.finish(token);
                        metrics.lookup(apiName, "miss");
                        recordWorkRejection(selection.policyName(), apiName, callerState, error);
                        return Mono.error(error);
                    }
                    flight = new InFlightLoad(flightKey, cache, token, proposedLoadState, reservation);
                    inFlightLoads.put(flightKey, flight);
                    created = true;
                }
                member = flight.reserve(callerState);
            }
        }

        if (cachedLookup != null) {
            metrics.lookup(apiName, "hit");
            return cachedHit(selection, cache, key, apiName, cachedLookup, loader, responseMetadata,
                    callerState, proposedLoadState, context);
        }
        metrics.lookup(apiName, "miss");
        if (created) {
            cacheOutcome(callerState, apiName, HttpClientCacheOutcome.MISS_LOADER);
        }
        else {
            cacheOutcome(callerState, apiName, HttpClientCacheOutcome.COALESCED_WAITER);
            metrics.coalesced(apiName);
        }
        InFlightLoad selectedFlight = flight;
        boolean start = created;
        return flight.publisher(member, () -> {
            if (start) {
                startFlight(selection.policyName(), selection.policy(), selectedFlight,
                        apiName, loader, responseMetadata, context);
            }
        });
    }

    private Mono<?> cachedHit(EffectiveCachePolicy.Selection selection,
                              LocalResponseCache cache,
                              CacheKeyContract.OpaqueKey key,
                              String apiName,
                              LocalResponseCache.Lookup lookup,
                              Function<SubscriptionReportingState, Mono<?>> loader,
                              Supplier<ResponseMetadata> responseMetadata,
                              SubscriptionReportingState callerState,
                              SubscriptionReportingState refreshState,
                              ContextView context) {
        ReactiveHttpClientProperties.CachePolicyConfig policy = selection.policy();
        boolean stale = policy.isRefreshEnabled()
                && lookup.ageNanos() >= TimeUnit.MILLISECONDS.toNanos(policy.getRefreshAfterMs());
        cacheOutcome(callerState, apiName, stale
                ? HttpClientCacheOutcome.STALE_HIT
                : HttpClientCacheOutcome.FRESH_HIT);
        if (stale) {
            metrics.stale(apiName);
            triggerRefresh(selection.policyName(), cache, key, apiName, lookup, loader,
                    responseMetadata, refreshState, context, policy);
        }
        return Mono.just(lookup.value());
    }

    private void triggerRefresh(String policyName,
                                LocalResponseCache cache,
                                CacheKeyContract.OpaqueKey key,
                                String apiName,
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
            metrics.refreshSkipped(policyName, LocalResponseCacheMetrics.RefreshSkipReason.ENTRY_UNAVAILABLE);
            return;
        }
        if (token == null) {
            metrics.refreshSkipped(policyName, LocalResponseCacheMetrics.RefreshSkipReason.ENTRY_UNAVAILABLE);
            return;
        }

        FlightKey refreshKey = new FlightKey(cache, key);
        InFlightRefresh refresh = new InFlightRefresh(refreshKey, cache, token, refreshState, apiName);
        boolean rejected;
        LocalResponseCacheMetrics.RefreshSkipReason skip = LocalResponseCacheMetrics.RefreshSkipReason.ENTRY_UNAVAILABLE;
        synchronized (inFlightRefreshes) {
            rejected = closed.get() || inFlightRefreshes.containsKey(refreshKey);
            if (!closed.get() && rejected) {
                skip = LocalResponseCacheMetrics.RefreshSkipReason.ALREADY_REFRESHING;
            }
            if (!rejected && refreshAdmission != null) {
                try {
                    refresh.reservation = refreshAdmission.acquire(policyName);
                } catch (RefreshCapacity ignored) {
                    skip = LocalResponseCacheMetrics.RefreshSkipReason.CAPACITY;
                    rejected = true;
                } catch (IllegalStateException ignored) {
                    rejected = true;
                }
            }
            if (!rejected) {
                inFlightRefreshes.put(refreshKey, refresh);
            }
        }
        if (rejected) {
            cache.finishRefresh(token);
            metrics.refreshSkipped(policyName, skip);
            return;
        }
        if (!cache.isRefreshCurrent(token)) {
            cancelRefresh(refreshKey);
            return;
        }
        startRefresh(policyName, policy, refresh, loader, responseMetadata, context);
    }

    @SuppressWarnings("unchecked")
    private void startRefresh(String policyName,
                              ReactiveHttpClientProperties.CachePolicyConfig policy,
                              InFlightRefresh refresh,
                              Function<SubscriptionReportingState, Mono<?>> loader,
                              Supplier<ResponseMetadata> responseMetadata,
                              ContextView context) {
        long deadlineNanos = Math.min(
                TimeUnit.MILLISECONDS.toNanos(policy.getRefreshTimeoutMs()),
                refresh.cache.hardExpiryRemainingNanos(refresh.refreshToken));
        if (deadlineNanos <= 0) {
            cancelRefresh(refresh.key);
            return;
        }

        if (refresh.loadState != null) {
            refresh.loadState.markHiddenCacheRefresh();
        }
        Mono<Object> source = Mono.deferContextual(owner -> CacheWorkAdmission.preparing(owner, () -> {
            if (!refresh.cache.isRefreshCurrent(refresh.refreshToken)) {
                cancelRefresh(refresh.key);
                return Mono.empty();
            }
            return ((Mono<Object>) loader.apply(refresh.loadState))
                    .flatMap(value -> CacheWorkAdmission.preparing(owner, () -> {
                        ResponseMetadata metadata = responseMetadata.get();
                        Optional<Object> candidate = cacheCandidate(policy, value, metadata);
                        return CacheWorkAdmission.preparing(owner, () -> {
                            candidate.ifPresent(selected -> publishRefreshCandidate(
                                    policyName, policy, refresh.cache, refresh.refreshToken, selected, metadata));
                            return Mono.just(value);
                        });
                    }));
        }));
        source = CacheWorkAdmission.subscribePreparation(source)
                .timeout(Duration.ofNanos(deadlineNanos), refreshScheduler)
                .takeUntilOther(shutdown.asMono())
                .doOnError(ignored -> {
                    // A deadline must terminate the guard even while synchronous preparation unwinds.
                    recordRefreshOnce(refresh, LocalResponseCacheMetrics.WorkOutcome.FAILURE);
                    if (refresh.reservation != null) {
                        refresh.reservation.complete();
                    }
                });
        if (refresh.reservation != null) {
            source = CacheWorkAdmission.own(source, refresh.reservation, signal -> {
                recordRefreshOnce(refresh, workOutcome(signal));
                finishRefresh(refresh);
            });
        } else {
            source = source.doFinally(signal -> {
                recordRefreshOnce(refresh, workOutcome(signal));
                finishRefresh(refresh);
            });
        }
        BaseSubscriber<Object> subscription = new BaseSubscriber<>() {
            @Override public Context currentContext() { return Context.of(context); }
            @Override protected void hookOnError(Throwable ignored) { }
        };
        synchronized (inFlightRefreshes) {
            if (refresh.terminal) {
                recordRefreshOnce(refresh, LocalResponseCacheMetrics.WorkOutcome.CANCELLATION);
                if (refresh.reservation != null) {
                    refresh.reservation.complete();
                }
                return;
            }
            refresh.sourceSubscription = subscription;
        }
        try {
            source.subscribe(subscription);
        } catch (Throwable ignored) {
            recordRefreshOnce(refresh, LocalResponseCacheMetrics.WorkOutcome.FAILURE);
            finishRefresh(refresh);
            if (refresh.reservation != null) {
                refresh.reservation.complete();
            }
        }
    }

    private void recordRefresh(String apiName,
                               LocalResponseCacheMetrics.WorkOutcome outcome,
                               long startedAtNanos) {
        metrics.refresh(apiName, outcome, System.nanoTime() - startedAtNanos);
        if (observabilityEnabled && log.isDebugEnabled()) {
            log.debug("Response-cache refresh client={} api={} outcome={}",
                    clientName, apiName, outcome.tagValue());
        }
    }

    private void recordRefreshOnce(InFlightRefresh refresh,
                                   LocalResponseCacheMetrics.WorkOutcome outcome) {
        synchronized (inFlightRefreshes) {
            if (refresh.outcomeRecorded) {
                return;
            }
            refresh.outcomeRecorded = true;
        }
        recordRefresh(refresh.apiName, outcome, refresh.startedAtNanos);
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
        recordRefreshOnce(refresh, LocalResponseCacheMetrics.WorkOutcome.CANCELLATION);
        if (refresh.sourceSubscription != null) {
            refresh.sourceSubscription.dispose();
        }
        if (refresh.reservation != null) {
            refresh.reservation.complete();
        }
        refresh.cache.finishRefresh(refresh.refreshToken);
    }

    private void cancelRefreshForRemoval(
            LocalResponseCache cache, CacheKeyContract.OpaqueKey key) {
        cancelRefresh(new FlightKey(cache, key));
    }

    private CacheWorkAdmission.Reservation acquireLoad(String policyName) {
        return loadAdmission == null ? null : loadAdmission.acquire(policyName);
    }

    private Mono<?> load(String policyName,
                         ReactiveHttpClientProperties.CachePolicyConfig policy,
                         LocalResponseCache cache,
                         LocalResponseCache.LoadToken token,
                         String apiName,
                         Supplier<Mono<?>> loader,
                         Supplier<ResponseMetadata> responseMetadata,
                         CacheWorkAdmission.Reservation reservation) {
        if (reservation != null) {
            long startedAtNanos = System.nanoTime();
            Mono<?> source = Mono.deferContextual(context -> CacheWorkAdmission.preparing(context, () ->
                    loader.get().flatMap(value -> CacheWorkAdmission.preparing(context, () -> {
                        ResponseMetadata metadata = responseMetadata.get();
                        Optional<Object> candidate = cacheCandidate(policy, value, metadata);
                        return CacheWorkAdmission.preparing(context, () -> {
                            // Serialize publication with lookup/join/reservation decisions.
                            synchronized (inFlightLoads) {
                                candidate.ifPresent(selected -> publishLoadCandidate(
                                        policyName, policy, cache, token, selected, metadata));
                            }
                            return Mono.just(value);
                        });
                    }))));
            return CacheWorkAdmission.own(source, reservation, signal -> {
                cache.finish(token);
                metrics.load(apiName, workOutcome(signal), System.nanoTime() - startedAtNanos);
            });
        }
        return Mono.defer(() -> {
            long startedAtNanos = System.nanoTime();
            Mono<?> source;
            try {
                source = loader.get();
            } catch (Throwable error) {
                cache.finish(token);
                metrics.load(apiName, LocalResponseCacheMetrics.WorkOutcome.FAILURE,
                        System.nanoTime() - startedAtNanos);
                return Mono.error(error);
            }
            return source
                    .doOnSuccess(value -> {
                        if (value != null) {
                            ResponseMetadata metadata = responseMetadata.get();
                            cacheCandidate(policy, value, metadata)
                                    .ifPresent(candidate -> publishLoadCandidate(
                                            policyName, policy, cache, token, candidate, metadata));
                        }
                    })
                    .doFinally(signal -> {
                        metrics.load(apiName, workOutcome(signal), System.nanoTime() - startedAtNanos);
                        cache.finish(token);
                    });
        });
    }

    @SuppressWarnings("unchecked")
    private void startFlight(String policyName,
                             ReactiveHttpClientProperties.CachePolicyConfig policy,
                             InFlightLoad flight,
                             String apiName,
                             Function<SubscriptionReportingState, Mono<?>> loader,
                             Supplier<ResponseMetadata> responseMetadata,
                             ContextView context) {
        synchronized (inFlightLoads) {
            if (flight.terminal) {
                flight.cache.finish(flight.loadToken);
                if (flight.reservation != null) {
                    flight.reservation.complete();
                }
                return;
            }
            if (flight.sourceStarted) {
                return;
            }
            flight.sourceStarted = true;
        }

        Mono<Object> source = (Mono<Object>) load(
                policyName,
                policy,
                flight.cache,
                flight.loadToken,
                apiName,
                () -> loader.apply(flight.loadState),
                responseMetadata, flight.reservation).takeUntilOther(shutdown.asMono());
        BaseSubscriber<Object> subscription = new BaseSubscriber<>() {
            @Override public Context currentContext() { return Context.of(context); }
            @Override protected void hookOnNext(Object value) { completeFlightValue(flight, value); }
            @Override protected void hookOnError(Throwable error) { completeFlightError(flight, error); }
            @Override protected void hookOnComplete() { completeFlightEmpty(flight); }
        };
        synchronized (inFlightLoads) {
            if (flight.terminal) {
                flight.cache.finish(flight.loadToken);
                if (flight.reservation != null) {
                    flight.reservation.complete();
                }
                return;
            }
            flight.sourceSubscription = subscription;
        }
        try {
            source.subscribe(subscription);
        }
        catch (Throwable error) {
            flight.cache.finish(flight.loadToken);
            if (flight.reservation != null) {
                flight.reservation.complete();
            }
            completeFlightError(flight, error);
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
            flight.cache.finish(flight.loadToken);
            if (flight.reservation != null) {
                flight.reservation.complete();
            }
            flight.result.tryEmitEmpty();
        }
    }

    void recordWorkRejection(String policyName, String apiName, SubscriptionReportingState state,
                             RuntimeException error) {
        if (state != null) {
            state.markCacheServed();
        }
        if (error instanceof CacheWorkRejectedException rejected) {
            if (state != null) {
                state.prepareInitialResolved(new RequestArgumentResolver.ResolvedArgs(
                        Map.of(), Map.of(), Map.of(), null));
            }
            metrics.rejection(policyName, rejected.getReason());
            cacheOutcome(state, apiName, rejected.getReason() == CacheWorkRejectedException.Reason.CALLER_CAPACITY
                    ? HttpClientCacheOutcome.CALLER_REJECTED : HttpClientCacheOutcome.LOAD_REJECTED);
        }
    }

    private void cacheOutcome(SubscriptionReportingState state,
                              String apiName,
                              HttpClientCacheOutcome outcome) {
        if (state != null && outcome != HttpClientCacheOutcome.MISS_LOADER) {
            state.markCacheServed();
        }
        if (observabilityEnabled && state != null) {
            if (state.cacheOutcome(outcome)) {
                metrics.caller(apiName, outcome);
            }
        }
    }

    private static void followLoad(SubscriptionReportingState callerState,
                                   SubscriptionReportingState loadState) {
        if (callerState != null && loadState != null) {
            callerState.followAttemptEvidenceFrom(loadState);
        }
    }

    private static LocalResponseCacheMetrics.WorkOutcome workOutcome(SignalType signal) {
        if (signal == SignalType.CANCEL) {
            return LocalResponseCacheMetrics.WorkOutcome.CANCELLATION;
        }
        if (signal == SignalType.ON_ERROR) {
            return LocalResponseCacheMetrics.WorkOutcome.FAILURE;
        }
        return LocalResponseCacheMetrics.WorkOutcome.SUCCESS;
    }

    Snapshot snapshot() {
        if (!closed.get()) {
            workValidation.run();
        }
        synchronized (caches) {
            long capacity = 0;
            long size = 0;
            long evictions = 0;
            long retainedDecodedResponseBytes = 0;
            boolean retainedBytesKnown = true;
            for (Map.Entry<PolicyBounds, LocalResponseCache> entry : caches.entrySet()) {
                capacity = Math.addExact(capacity, entry.getKey().maximumSize);
                size = Math.addExact(size, entry.getValue().estimatedSize());
                evictions = Math.addExact(evictions, entry.getValue().evictionCount());
                if (entry.getValue().maximumDecodedResponseBytes() == null) {
                    retainedBytesKnown = false;
                }
                else if (retainedBytesKnown) {
                    retainedDecodedResponseBytes = Math.addExact(
                            retainedDecodedResponseBytes,
                            entry.getValue().retainedDecodedResponseBytes());
                }
            }
            return new Snapshot(
                    caches.size(), capacity, size, evictions,
                    retainedBytesKnown ? retainedDecodedResponseBytes : null,
                    closed.get());
        }
    }

    synchronized CacheWorkSnapshot workSnapshot() {
        if (!closed.get()) { workValidation.run(); }
        if (workSelection == null) { return null; }
        CacheWorkSnapshot configured = CacheWorkSnapshot.configured(workSelection);
        long callers = 0, loads = 0, refreshes = 0;
        for (String policy : workSelection.policies().keySet()) {
            callers = Math.addExact(callers, callerAdmission.active(policy));
            loads = Math.addExact(loads, loadAdmission.active(policy));
            if (refreshAdmission != null) {
                refreshes = Math.addExact(refreshes, refreshAdmission.active(policy));
            }
        }
        return configured.live(closed.get(), callers, loads, refreshes);
    }

    boolean hasInFlightLoadWithMembersForTesting(int memberCount) {
        synchronized (inFlightLoads) {
            return inFlightLoads.values().stream()
                    .anyMatch(flight -> flight.members.size() == memberCount);
        }
    }

    long retainedDecodedResponseBytesForTesting() {
        synchronized (caches) {
            long retainedBytes = 0;
            for (LocalResponseCache cache : caches.values()) {
                retainedBytes = Math.addExact(retainedBytes, cache.retainedDecodedResponseBytes());
            }
            return retainedBytes;
        }
    }

    WorkloadSnapshot workloadSnapshotForTesting() {
        Snapshot cacheSnapshot = snapshot();
        int loadCount;
        int waiterCount;
        synchronized (inFlightLoads) {
            loadCount = inFlightLoads.size();
            waiterCount = inFlightLoads.values().stream()
                    .mapToInt(flight -> Math.max(0, flight.members.size() - 1))
                    .sum();
        }
        int refreshCount;
        synchronized (inFlightRefreshes) {
            refreshCount = inFlightRefreshes.size();
        }
        return new WorkloadSnapshot(cacheSnapshot, loadCount, waiterCount, refreshCount);
    }

    void evictAllForTesting() {
        if (closed.get()) {
            throw new IllegalStateException(
                    "The local response cache for client '" + clientName + "' has been closed");
        }
        List<FlightKey> refreshKeys;
        synchronized (inFlightRefreshes) {
            refreshKeys = List.copyOf(inFlightRefreshes.keySet());
        }
        refreshKeys.forEach(this::cancelRefresh);
        synchronized (caches) {
            if (closed.get()) {
                throw new IllegalStateException(
                        "The local response cache for client '" + clientName + "' has been closed");
            }
            caches.values().forEach(LocalResponseCache::invalidateAll);
        }
    }

    @Override
    public void close() {
        // Serialize limiter installation with shutdown, not with application cancellation callbacks.
        synchronized (this) {
            if (callerAdmission != null) {
                callerAdmission.close();
            }
            if (loadAdmission != null) {
                loadAdmission.close();
            }
            if (refreshAdmission != null) {
                refreshAdmission.close();
            }
            if (!closed.compareAndSet(false, true)) {
                return;
            }
        }
        metrics.close();
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
                if (flight.reservation != null) {
                    flight.reservation.complete();
                }
            }
            if (flight.sourceSubscription != null) {
                flight.sourceSubscription.dispose();
            }
            flight.result.tryEmitEmpty();
        }
        for (InFlightRefresh refresh : refreshes) {
            recordRefreshOnce(refresh, LocalResponseCacheMetrics.WorkOutcome.CANCELLATION);
            if (refresh.sourceSubscription != null) {
                refresh.sourceSubscription.dispose();
            }
            if (refresh.reservation != null) {
                refresh.reservation.complete();
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
        if (workSelection != null && !Objects.equals(workSelection.policies().get(selection.policyName()),
                CacheWorkPolicy.normalize(policy))) {
            throw new IllegalStateException("Cache work selection changed after startup; recreate the client factory");
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
            Map.Entry<PolicyBounds, LocalResponseCache> existing = caches.entrySet().stream()
                    .filter(candidate -> candidate.getKey().policyName().equals(selection.policyName()))
                    .findFirst()
                    .orElse(null);
            if (existing != null && (!existing.getKey().equals(bounds)
                    || !Objects.equals(existing.getValue().maximumDecodedResponseBytes(),
                    policy.getMaximumTotalDecodedResponseBytes()))) {
                throw new IllegalStateException("Cache policy '" + selection.policyName() + "' for client '"
                        + clientName + "' changed after its local response cache was created; runtime mutation of "
                        + "ttl-ms, maximum-size, maximum-total-decoded-response-bytes, refresh-after-ms, "
                        + "or refresh-timeout-ms is unsupported");
            }
            return caches.computeIfAbsent(bounds, ignored -> newCache(selection.policyName(), clientName, bounds,
                    policy.getMaximumTotalDecodedResponseBytes()));
        }
    }

    private LocalResponseCache newCache(
            String policyName, String clientName, PolicyBounds bounds, Long maximumDecodedResponseBytes) {
        if (!ClassUtils.isPresent(CAFFEINE_CLASS, classLoader)) {
            throw new IllegalStateException("Reactive HTTP client '" + clientName + "' selects response-cache policy '"
                    + policyName + "', but optional dependency com.github.ben-manes.caffeine:caffeine is not available. "
                    + "Add Caffeine at runtime or disable response caching for this client.");
        }
        LocalResponseCache cache = new CaffeineLocalResponseCache(
                bounds.ttlMs, bounds.maximumSize, maximumDecodedResponseBytes, ticker,
                (removedCache, key, reason) -> {
                    cancelRefreshForRemoval(removedCache, key);
                    metrics.eviction(policyName, reason);
                });
        metrics.registerCache(policyName, bounds.maximumSize, cache);
        return cache;
    }

    private void publishLoadCandidate(
            String policyName,
            ReactiveHttpClientProperties.CachePolicyConfig policy,
            LocalResponseCache cache,
            LocalResponseCache.LoadToken token,
            Object candidate,
            ResponseMetadata responseMetadata) {
        Long maximumBytes = policy.getMaximumTotalDecodedResponseBytes();
        if (maximumBytes == null) {
            cache.publish(token, candidate);
            return;
        }
        AdmissionMeasurement measurement = retainedResponseBytes(candidate, responseMetadata, maximumBytes);
        if (measurement.bytes() == null) {
            cache.recordLoadBypassIfCurrent(
                    token, () -> metrics.admission(policyName, measurement.outcome()));
            return;
        }
        recordAdmission(policyName, cache.publishMeasured(token, candidate, measurement.bytes()));
    }

    private void publishRefreshCandidate(
            String policyName,
            ReactiveHttpClientProperties.CachePolicyConfig policy,
            LocalResponseCache cache,
            LocalResponseCache.RefreshToken token,
            Object candidate,
            ResponseMetadata responseMetadata) {
        Long maximumBytes = policy.getMaximumTotalDecodedResponseBytes();
        if (maximumBytes == null) {
            cache.publishRefresh(token, candidate);
            return;
        }
        AdmissionMeasurement measurement = retainedResponseBytes(candidate, responseMetadata, maximumBytes);
        if (measurement.bytes() == null) {
            cache.recordRefreshBypassIfCurrent(
                    token, () -> metrics.admission(policyName, measurement.outcome()));
            return;
        }
        recordAdmission(
                policyName,
                cache.publishRefreshMeasured(token, candidate, measurement.bytes()));
    }

    private AdmissionMeasurement retainedResponseBytes(
            Object candidate, ResponseMetadata responseMetadata, long maximumBytes) {
        Long decodedBodyBytes = responseMetadata != null
                ? responseMetadata.completedDecodedResponseBytes()
                : null;
        if (decodedBodyBytes == null || decodedBodyBytes < 0) {
            if (responseMetadata != null && responseMetadata.decodedResponseBytesExceededMaximum()) {
                return AdmissionMeasurement.bypassed(
                        LocalResponseCacheMetrics.AdmissionOutcome.BYPASSED_OVER_BUDGET);
            }
            return AdmissionMeasurement.bypassed(
                    LocalResponseCacheMetrics.AdmissionOutcome.BYPASSED_UNKNOWN_SIZE);
        }
        if (decodedBodyBytes > maximumBytes) {
            return AdmissionMeasurement.bypassed(
                    LocalResponseCacheMetrics.AdmissionOutcome.BYPASSED_OVER_BUDGET);
        }
        long retainedBytes = decodedBodyBytes;
        if (!(candidate instanceof ResponseEntity<?> entity)) {
            return AdmissionMeasurement.measured(retainedBytes);
        }
        for (Map.Entry<String, java.util.List<String>> header : entity.getHeaders().headerSet()) {
            retainedBytes = addRetainedBytes(retainedBytes, header.getKey(), maximumBytes);
            if (retainedBytes < 0) {
                return AdmissionMeasurement.bypassed(
                        LocalResponseCacheMetrics.AdmissionOutcome.BYPASSED_OVER_BUDGET);
            }
            for (String value : header.getValue()) {
                retainedBytes = addRetainedBytes(retainedBytes, value, maximumBytes);
                if (retainedBytes < 0) {
                    return AdmissionMeasurement.bypassed(
                            LocalResponseCacheMetrics.AdmissionOutcome.BYPASSED_OVER_BUDGET);
                }
            }
        }
        return AdmissionMeasurement.measured(retainedBytes);
    }

    private void recordAdmission(String policyName, LocalResponseCache.PublicationResult result) {
        if (result == LocalResponseCache.PublicationResult.STORED) {
            metrics.admission(policyName, LocalResponseCacheMetrics.AdmissionOutcome.ADMITTED);
        }
        else if (result == LocalResponseCache.PublicationResult.CAPACITY) {
            metrics.admission(policyName, LocalResponseCacheMetrics.AdmissionOutcome.BYPASSED_CAPACITY);
        }
    }

    private long addRetainedBytes(long current, String value, long maximumBytes) {
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        return current > maximumBytes - bytes ? -1 : current + bytes;
    }

    private java.util.Optional<Object> cacheCandidate(
            ReactiveHttpClientProperties.CachePolicyConfig policy,
            Object value,
            ResponseMetadata responseMetadata) {
        if (responseMetadata != null
                && (!responseMetadata.requestIdentityMatches()
                || isRedirect(responseMetadata.statusCode())
                || hasNonCacheableHeaders(policy, responseMetadata.headers()))) {
            return java.util.Optional.empty();
        }
        if (!(value instanceof ResponseEntity<?> entity)) {
            return java.util.Optional.of(value);
        }
        if (isRedirect(entity.getStatusCode().value())
                || hasNonCacheableHeaders(policy, entity.getHeaders())) {
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

    private boolean hasNonCacheableHeaders(
            ReactiveHttpClientProperties.CachePolicyConfig policy,
            Map<String, ? extends java.util.List<String>> headers) {
        return hasNonCacheableHeaderNames(policy, headers.keySet());
    }

    private boolean hasNonCacheableHeaders(
            ReactiveHttpClientProperties.CachePolicyConfig policy, HttpHeaders headers) {
        return hasNonCacheableHeaderNames(policy,
                headers.headerSet().stream().map(Map.Entry::getKey).toList());
    }

    private boolean hasNonCacheableHeaderNames(
            ReactiveHttpClientProperties.CachePolicyConfig policy, Iterable<String> headerNames) {
        java.util.List<String> configured =
                EffectiveCachePolicy.normalizedNonCacheableResponseHeaders(policy);
        for (String headerName : headerNames) {
            String normalized = headerName.toLowerCase(Locale.ROOT);
            if (SensitiveHeaders.isSensitive(headerName)
                    || NON_CACHEABLE_RESPONSE_HEADERS.contains(normalized)
                    || configured.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRedirect(int statusCode) {
        return statusCode >= 300 && statusCode < 400;
    }

    record Snapshot(
            int policyCount,
            long configuredCapacity,
            long currentSize,
            long evictions,
            Long retainedDecodedResponseBytes,
            boolean closed) {

        Snapshot(int policyCount, long configuredCapacity, long currentSize, long evictions, boolean closed) {
            this(policyCount, configuredCapacity, currentSize, evictions,
                    policyCount == 0 ? 0L : null, closed);
        }

        Snapshot(int policyCount, long configuredCapacity, long currentSize, boolean closed) {
            this(policyCount, configuredCapacity, currentSize, 0,
                    policyCount == 0 ? 0L : null, closed);
        }
    }

    private record AdmissionMeasurement(
            Long bytes, LocalResponseCacheMetrics.AdmissionOutcome outcome) {

        private static AdmissionMeasurement measured(long bytes) {
            return new AdmissionMeasurement(bytes, null);
        }

        private static AdmissionMeasurement bypassed(LocalResponseCacheMetrics.AdmissionOutcome outcome) {
            return new AdmissionMeasurement(null, outcome);
        }
    }

    record WorkloadSnapshot(
            Snapshot cache,
            int inFlightLoads,
            int coalescedWaiters,
            int inFlightRefreshes) {
    }

    record ResponseMetadata(
            int statusCode,
            Map<String, java.util.List<String>> headers,
            boolean requestIdentityMatches,
            DecodedResponseBytes decodedResponseBytes) {

        ResponseMetadata(int statusCode, Map<String, java.util.List<String>> headers) {
            this(statusCode, headers, true, null);
        }

        ResponseMetadata(
                int statusCode, Map<String, java.util.List<String>> headers, boolean requestIdentityMatches) {
            this(statusCode, headers, requestIdentityMatches, null);
        }

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

        Long completedDecodedResponseBytes() {
            return decodedResponseBytes != null ? decodedResponseBytes.completedBytes() : null;
        }

        boolean decodedResponseBytesExceededMaximum() {
            return decodedResponseBytes != null && decodedResponseBytes.exceededMaximum();
        }

        ResponseMetadata withRequestIdentityMatches(boolean matches) {
            return new ResponseMetadata(statusCode, headers, matches, decodedResponseBytes);
        }
    }

    static final class DecodedResponseBytes {
        private final long maximumBytes;
        private long bytes;
        private boolean complete;
        private boolean unavailable;
        private boolean overBudget;

        DecodedResponseBytes(long maximumBytes) {
            this.maximumBytes = maximumBytes;
        }

        synchronized void add(long addedBytes) {
            if (complete || unavailable) {
                return;
            }
            if (addedBytes < 0) {
                unavailable = true;
                return;
            }
            if (bytes > maximumBytes - addedBytes) {
                overBudget = true;
                return;
            }
            bytes += addedBytes;
        }

        synchronized void complete() {
            complete = true;
        }

        synchronized void unavailable() {
            if (!complete) {
                unavailable = true;
            }
        }

        synchronized Long completedBytes() {
            return complete && !unavailable && !overBudget ? bytes : null;
        }

        synchronized boolean exceededMaximum() {
            return overBudget;
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
        private final String apiName;
        private final long startedAtNanos = System.nanoTime();
        private Disposable sourceSubscription;
        private boolean terminal;
        private boolean outcomeRecorded;
        private CacheWorkAdmission.Reservation reservation;

        private InFlightRefresh(FlightKey key,
                                LocalResponseCache cache,
                                LocalResponseCache.RefreshToken refreshToken,
                                SubscriptionReportingState loadState,
                                String apiName) {
            this.key = key;
            this.cache = cache;
            this.refreshToken = refreshToken;
            this.loadState = loadState;
            this.apiName = apiName;
        }
    }

    private final class InFlightLoad {
        private final FlightKey key;
        private final LocalResponseCache cache;
        private final LocalResponseCache.LoadToken loadToken;
        private final SubscriptionReportingState loadState;
        private final Sinks.One<Object> result = Sinks.one();
        private final CacheWorkAdmission.Reservation reservation;
        private final Set<FlightMember> members = new LinkedHashSet<>();
        private FlightMember diagnosticOwner;
        private Disposable sourceSubscription;
        private boolean sourceStarted;
        private boolean terminal;
        private boolean diagnosticOwnerAssigned;

        private InFlightLoad(FlightKey key,
                             LocalResponseCache cache,
                             LocalResponseCache.LoadToken loadToken,
                             SubscriptionReportingState loadState,
                             CacheWorkAdmission.Reservation reservation) {
            this.key = key;
            this.cache = cache;
            this.loadToken = loadToken;
            this.loadState = loadState;
            this.reservation = reservation;
        }

        private FlightMember reserve(SubscriptionReportingState callerState) {
            FlightMember member = new FlightMember(callerState);
            members.add(member);
            if (!diagnosticOwnerAssigned) {
                diagnosticOwnerAssigned = true;
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

        private Mono<?> publisher(FlightMember member, Runnable start) {
            return Mono.create(sink -> {
                BaseSubscriber<Object> subscriber = new BaseSubscriber<>() {
                    @Override public Context currentContext() { return Context.of(sink.contextView()); }
                    @Override protected void hookOnNext(Object value) {
                        releaseFlightMember(InFlightLoad.this, member);
                        sink.success(value);
                    }
                    @Override protected void hookOnComplete() {
                        releaseFlightMember(InFlightLoad.this, member);
                        sink.success();
                    }
                    @Override protected void hookOnError(Throwable error) {
                        releaseFlightMember(InFlightLoad.this, member);
                        sink.error(error);
                    }
                };
                sink.onCancel(() -> {
                    releaseFlightMember(this, member);
                    subscriber.dispose();
                });
                result.asMono().subscribe(subscriber);
                // A cancelled first member may still have an interested reserved waiter.
                start.run();
            });
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
