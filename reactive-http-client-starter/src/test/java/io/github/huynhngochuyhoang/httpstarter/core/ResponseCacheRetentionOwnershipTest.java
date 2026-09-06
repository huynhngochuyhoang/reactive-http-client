package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.auth.OutboundAuthFilter;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.RequestSerializationException;
import io.github.huynhngochuyhoang.httpstarter.observability.ReactiveHttpClientDiagnosticsEndpoint;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.test.scheduler.VirtualTimeScheduler;
import reactor.util.context.Context;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(30)
class ResponseCacheRetentionOwnershipTest {

    private static final Duration COLLECTION_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void terminalOutcomesReleaseTransientOwnersWhileTheManagerRemainsOpen() throws Exception {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
        EffectiveCachePolicy.Selection selection = selection("terminal", 60_000, 10, false);

        TerminalReferences references = createTerminalReferences(manager, selection, queue);

        assertThat(manager.workloadSnapshotForTesting().inFlightLoads()).isZero();
        assertThat(manager.snapshot().currentSize()).isEqualTo(1);
        assertRetained(references.cachedValue());
        assertCollected(queue, references.transientOwners());

        manager.evictAllForTesting();
        assertThat(manager.snapshot().currentSize()).isZero();
        assertGenerationOwners(manager, 0);
        assertCollected(queue, List.of(references.cachedValue()));
        manager.close();
    }

    @Test
    void expiryCapacityAndRefreshTransitionsReleaseDisplacedOwners() throws Exception {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        AtomicLong ticker = new AtomicLong();
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(ticker::get);

        TrackedReference expired = loadTrackedValue(
                manager, selection("expiry", 100, 10, false), key("expiry"), "expired", queue);
        ticker.addAndGet(Duration.ofMillis(100).toNanos());
        assertThat(manager.snapshot().currentSize()).isZero();
        assertCollected(queue, List.of(expired));

        EffectiveCachePolicy.Selection capacity = selection("capacity", 60_000, 1, false);
        List<TrackedReference> capacityValues = List.of(
                loadTrackedValue(manager, capacity, key("capacity-one"), "capacity-one", queue),
                loadTrackedValue(manager, capacity, key("capacity-two"), "capacity-two", queue));
        assertThat(manager.snapshot().currentSize()).isEqualTo(1);
        assertEventuallyCollectedCount(queue, capacityValues, 1);

        RefreshReferences refresh = createRefreshReferences(manager, ticker, queue);
        assertCollected(queue, List.of(refresh.displacedValue(), refresh.failedRefreshOwner()));
        assertRetained(refresh.currentValue());

        manager.evictAllForTesting();
        assertThat(refresh.refreshCancellations()).hasValue(1);
        assertThat(manager.workloadSnapshotForTesting().inFlightRefreshes()).isZero();
        assertGenerationOwners(manager, 0);
        assertCollected(queue, List.of(refresh.currentValue(), refresh.cancelledRefreshOwner()));
        assertCollected(queue, capacityValues);

        manager.close();
    }

    @Test
    void runtimePolicyBoundsMutationIsRejectedWithoutCreatingAnotherCache() throws Exception {
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
        EffectiveCachePolicy.Selection selection = selection("stable-bounds", 60_000, 10, false);

        assertThat(load(manager, selection, key("initial"), "initial")).isEqualTo("initial");
        selection.policy().setMaximumSize(20L);

        assertThatThrownBy(() -> load(manager, selection, key("mutated"), "mutated"))
                .hasMessageContaining("changed after its local response cache was created")
                .hasMessageContaining("runtime mutation")
                .hasMessageContaining("maximum-size");
        assertThat(caches(manager)).hasSize(1);
        assertThat(manager.snapshot()).isEqualTo(
                new LocalResponseCacheManager.Snapshot(1, 10, 1, 0, false));
        manager.close();
    }

    @Test
    void independentLoadRemainsCallerOwnedAfterManagerCloseAndReleasesAtCallerTerminal() {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        IndependentLoadAfterClose active = startIndependentLoadThenClose(queue);

        assertThat(active.manager().workloadSnapshotForTesting().inFlightLoads()).isZero();
        assertThat(active.manager().snapshot().closed()).isTrue();
        assertRetained(active.loadOwner());

        active.source().tryEmitEmpty().orThrow();
        assertThat(active.result().join()).isEqualTo("late-value");
        assertThat(active.manager().snapshot()).isEqualTo(
                new LocalResponseCacheManager.Snapshot(0, 0, 0, 0, true));
        assertCollected(queue, List.of(active.loadOwner()));
    }

    @Test
    void detachedWaiterReleasesItsArgumentsContextAndStateBeforeTheLeaderEnds() {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        ActiveFlight active = createActiveFlightWithCancelledWaiter(queue);

        assertThat(active.manager().hasInFlightLoadWithMembersForTesting(1)).isTrue();
        assertThat(active.sourceCancellations()).hasValue(0);
        assertCollected(queue, active.waiterOwners());

        active.result().tryEmitValue("leader-value").orThrow();
        assertThat(active.leaderValue()).containsExactly("leader-value");
        active.leader().dispose();
        active.manager().close();
    }

    @Test
    void detachedLeaderReleasesItsCallerStateWhileAWaiterKeepsTheLoadAlive() {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        DetachedLeaderFlight active = createActiveFlightWithCancelledLeader(queue);

        assertThat(active.manager().hasInFlightLoadWithMembersForTesting(1)).isTrue();
        assertThat(active.sourceCancellations()).hasValue(0);
        assertCollected(queue, active.leaderOwners());

        active.result().tryEmitValue("waiter-value").orThrow();
        assertThat(active.waiterValue()).containsExactly("waiter-value");
        active.waiter().dispose();
        active.manager().close();
    }

    @Test
    void hiddenRefreshTerminalPathsReleaseTheirCapturedState() {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        RefreshTerminalFixture fixture = exerciseRefreshTerminalPaths(queue);

        assertThat(fixture.openManager().workloadSnapshotForTesting().inFlightRefreshes()).isZero();
        assertThat(fixture.cancellations()).hasValue(4);
        assertCollected(queue, fixture.refreshOwners());

        fixture.openManager().close();
        fixture.scheduler().dispose();
    }

    @Test
    void preparedBodyAuthContextFrozenArgumentsAndResponseMetadataEndAtPublication() {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);

        PreparedInvocationReferences references = invokePreparedCachedRequest(manager, queue);

        assertThat(manager.snapshot().currentSize()).isEqualTo(1);
        assertThat(manager.workloadSnapshotForTesting().inFlightLoads()).isZero();
        assertCollected(queue, references.transientOwners());
        manager.close();
    }

    @Test
    void closeRemovesMeterRootsAndRejectsLatePublication() throws Exception {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CloseReferences references = closeMeteredManagerWithLateLoad(registry, queue);

        assertThat(registry.getMeters()).noneMatch(ResponseCacheRetentionOwnershipTest::isCacheMeter);
        assertCollected(queue, references.owners());

        LocalResponseCacheMetrics replacementMetrics =
                LocalResponseCacheMetrics.enabled(registry, "retention-client");
        LocalResponseCacheManager replacement = LocalResponseCacheManager.testing(
                System::nanoTime, Schedulers.parallel(), replacementMetrics, "retention-client");
        assertThat(load(replacement, selection("metered", 60_000, 10, false),
                key("replacement"), "replacement")).isEqualTo("replacement");
        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".entries")
                .tags("client.name", "retention-client", "cache.policy", "metered")
                .gauge().value()).isEqualTo(1.0);
        replacement.close();
    }

    @Test
    void retainedDiagnosticsMapDoesNotOwnFactoryManagerCacheOrValue() throws Exception {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        DiagnosticsReferences references = createAndCloseDiagnosticsFixture(queue);

        assertThat(references.snapshot()).extractingByKey("schemaVersion").isEqualTo(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> client = ((List<Map<String, Object>>) references.snapshot().get("clients")).get(0);
        assertThat(client)
                .containsEntry("cacheEntryCount", 1L)
                .doesNotContainKeys("cacheKey", "cacheValue", "requestBody", "requestHeaders");
        assertCollected(queue, references.owners());
    }

    private static TerminalReferences createTerminalReferences(
            LocalResponseCacheManager manager,
            EffectiveCachePolicy.Selection selection,
            ReferenceQueue<Object> queue) {
        Object cachedValue = new Object();
        String responseMetadataOwner = new String("response-metadata-retention");
        TrackedReference cachedValueReference = track("cached success value", cachedValue, queue);
        TrackedReference responseMetadataReference = track("success response metadata", responseMetadataOwner, queue);
        Mono<?> success = manager.getOrLoad(
                selection,
                key("success"),
                () -> Mono.just(cachedValue),
                () -> new LocalResponseCacheManager.ResponseMetadata(
                        200, Map.of("X-Transient-Metadata", List.of(responseMetadataOwner))));
        assertThat(success.block()).isSameAs(cachedValue);

        Object failureOwner = new Object();
        TrackedReference failureReference = track("failed load owner", failureOwner, queue);
        Mono<?> failure = manager.getOrLoad(selection, key("failure"), () -> Mono.defer(() -> {
            failureOwner.hashCode();
            return Mono.error(new IllegalStateException("load failed"));
        }));
        assertThatThrownBy(failure::block).hasMessage("load failed");

        Object emptyOwner = new Object();
        TrackedReference emptyReference = track("empty load owner", emptyOwner, queue);
        Mono<?> empty = manager.getOrLoad(selection, key("empty"), () -> Mono.defer(() -> {
            emptyOwner.hashCode();
            return Mono.empty();
        }));
        assertThat(empty.block()).isNull();

        Object serializationOwner = new Object();
        TrackedReference serializationReference = track("serialization failure owner", serializationOwner, queue);
        Mono<?> serializationFailure = manager.getOrLoad(selection, key("serialization"), () -> Mono.defer(() -> {
            serializationOwner.hashCode();
            return Mono.error(new RequestSerializationException(
                    "retention-client", new IllegalArgumentException("encoding failed")));
        }));
        assertThatThrownBy(serializationFailure::block)
                .hasRootCauseMessage("encoding failed");

        Object cancellationOwner = new Object();
        TrackedReference cancellationReference = track("cancelled load owner", cancellationOwner, queue);
        AtomicInteger cancellations = new AtomicInteger();
        Mono<?> cancellation = manager.getOrLoad(selection, key("cancelled"), () -> Mono.defer(() -> {
            cancellationOwner.hashCode();
            return Mono.never();
        }).doOnCancel(cancellations::incrementAndGet));
        Disposable disposable = cancellation.subscribe();
        disposable.dispose();
        assertThat(cancellations).hasValue(1);

        return new TerminalReferences(
                cachedValueReference,
                List.of(responseMetadataReference, failureReference, emptyReference,
                        serializationReference, cancellationReference));
    }

    private static TrackedReference loadTrackedValue(
            LocalResponseCacheManager manager,
            EffectiveCachePolicy.Selection selection,
            CacheKeyContract.OpaqueKey key,
            String label,
            ReferenceQueue<Object> queue) {
        Object value = new Object();
        TrackedReference reference = track(label, value, queue);
        assertThat(manager.getOrLoad(selection, key, () -> Mono.just(value)).block()).isSameAs(value);
        return reference;
    }

    private static RefreshReferences createRefreshReferences(
            LocalResponseCacheManager manager,
            AtomicLong ticker,
            ReferenceQueue<Object> queue) {
        EffectiveCachePolicy.Selection selection = refreshSelection("refresh", 60_000, 10, 10, 30_000);
        CacheKeyContract.OpaqueKey key = key("refresh");

        Object initial = new Object();
        TrackedReference initialReference = track("refresh displaced value", initial, queue);
        assertThat(manager.getOrLoad(selection, key, () -> Mono.just(initial)).block()).isSameAs(initial);

        ticker.addAndGet(Duration.ofMillis(10).toNanos());
        Object replacement = new Object();
        TrackedReference replacementReference = track("refresh current value", replacement, queue);
        assertThat(manager.getOrLoad(selection, key, () -> Mono.just(replacement)).block()).isSameAs(initial);
        assertThat(manager.getOrLoad(selection, key, () -> Mono.error(new AssertionError("unexpected load"))).block())
                .isSameAs(replacement);

        ticker.addAndGet(Duration.ofMillis(10).toNanos());
        Object failedOwner = new Object();
        TrackedReference failedReference = track("failed refresh owner", failedOwner, queue);
        assertThat(manager.getOrLoad(selection, key, () -> Mono.defer(() -> {
            failedOwner.hashCode();
            return Mono.error(new IllegalStateException("refresh failed"));
        })).block()).isSameAs(replacement);

        Object cancelledOwner = new Object();
        TrackedReference cancelledReference = track("cancelled refresh owner", cancelledOwner, queue);
        AtomicInteger cancellations = new AtomicInteger();
        assertThat(manager.getOrLoad(selection, key, () -> Mono.defer(() -> {
            cancelledOwner.hashCode();
            return Mono.never();
        }).doOnCancel(cancellations::incrementAndGet)).block()).isSameAs(replacement);
        assertThat(manager.workloadSnapshotForTesting().inFlightRefreshes()).isEqualTo(1);

        return new RefreshReferences(
                initialReference, replacementReference, failedReference, cancelledReference, cancellations);
    }

    private static ActiveFlight createActiveFlightWithCancelledWaiter(ReferenceQueue<Object> queue) {
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
        EffectiveCachePolicy.Selection selection = selection("waiter", 60_000, 10, true);
        CacheKeyContract.OpaqueKey key = key("waiter");
        Sinks.One<String> result = Sinks.one();
        AtomicInteger sourceCancellations = new AtomicInteger();
        List<String> leaderValue = new ArrayList<>();

        SubscriptionReportingState leaderState = state(new Object());
        Disposable leader = manager.getOrLoad(
                        selection, key, "retention.waiter",
                        ignored -> result.asMono().doOnCancel(sourceCancellations::incrementAndGet),
                        LocalResponseCacheManager.ResponseMetadata::successWithoutHeaders,
                        leaderState, new SubscriptionReportingState(emptyResolved()))
                .subscribe(value -> leaderValue.add((String) value));

        Object argumentOwner = new Object();
        Object contextOwner = new Object();
        SubscriptionReportingState waiterState = state(argumentOwner);
        SubscriptionReportingState unusedWaiterLoadState = state(new Object());
        List<TrackedReference> waiterOwners = List.of(
                track("detached waiter argument", argumentOwner, queue),
                track("detached waiter context", contextOwner, queue),
                track("detached waiter reporting state", waiterState, queue),
                track("unused waiter load state", unusedWaiterLoadState, queue));
        Mono<?> waiter = manager.getOrLoad(
                selection, key, "retention.waiter",
                ignored -> Mono.error(new AssertionError("waiter must not load")),
                LocalResponseCacheManager.ResponseMetadata::successWithoutHeaders,
                waiterState, unusedWaiterLoadState);
        Disposable waiterSubscription = waiter
                .contextWrite(Context.of("retention.waiter.context", contextOwner))
                .subscribe();
        waiterSubscription.dispose();

        return new ActiveFlight(
                manager, result, leader, leaderValue, sourceCancellations, waiterOwners);
    }

    private static DetachedLeaderFlight createActiveFlightWithCancelledLeader(ReferenceQueue<Object> queue) {
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
        EffectiveCachePolicy.Selection selection = selection("leader", 60_000, 10, true);
        CacheKeyContract.OpaqueKey key = key("leader");
        Sinks.One<String> result = Sinks.one();
        AtomicInteger sourceCancellations = new AtomicInteger();

        Object argumentOwner = new Object();
        byte[] preparedBodyOwner = "prepared-body".getBytes(StandardCharsets.UTF_8);
        String authStateOwner = new String("Bearer detached-leader");
        Context leaderContext = Context.of("retention.leader.context", new Object());
        SubscriptionReportingState leaderState = new SubscriptionReportingState(
                new RequestArgumentResolver.ResolvedArgs(
                        Map.of("id", argumentOwner),
                        Map.of(),
                        Map.of(HttpHeaders.AUTHORIZATION, List.of(authStateOwner)),
                        preparedBodyOwner));
        List<TrackedReference> leaderOwners = List.of(
                track("detached leader argument", argumentOwner, queue),
                track("detached leader prepared body", preparedBodyOwner, queue),
                track("detached leader auth state", authStateOwner, queue),
                track("detached leader context container", leaderContext, queue),
                track("detached leader reporting state", leaderState, queue));
        Disposable leader = manager.getOrLoad(
                        selection, key, "retention.leader",
                        ignored -> result.asMono().doOnCancel(sourceCancellations::incrementAndGet),
                        LocalResponseCacheManager.ResponseMetadata::successWithoutHeaders,
                        leaderState, new SubscriptionReportingState(emptyResolved()), Context.empty())
                .contextWrite(leaderContext)
                .subscribe();

        List<String> waiterValue = new ArrayList<>();
        Disposable waiter = manager.getOrLoad(
                        selection, key, "retention.leader",
                        ignored -> Mono.error(new AssertionError("waiter must not load")),
                        LocalResponseCacheManager.ResponseMetadata::successWithoutHeaders,
                        new SubscriptionReportingState(emptyResolved()),
                        new SubscriptionReportingState(emptyResolved()))
                .subscribe(value -> waiterValue.add((String) value));
        leader.dispose();

        return new DetachedLeaderFlight(
                manager, result, waiter, waiterValue, sourceCancellations, leaderOwners);
    }

    private static RefreshTerminalFixture exerciseRefreshTerminalPaths(ReferenceQueue<Object> queue) {
        AtomicLong ticker = new AtomicLong();
        VirtualTimeScheduler scheduler = VirtualTimeScheduler.create();
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(ticker::get, scheduler);
        List<TrackedReference> owners = new ArrayList<>();
        AtomicInteger cancellations = new AtomicInteger();
        EffectiveCachePolicy.Selection immediate =
                refreshSelection("refresh-terminal", 60_000, 32, 10, 500);

        owners.add(runTerminalRefresh(
                manager, immediate, ticker, key("refresh-success"), "refresh success", queue,
                Mono.just("replacement")));
        owners.add(runTerminalRefresh(
                manager, immediate, ticker, key("refresh-failure"), "refresh failure", queue,
                Mono.error(new IllegalStateException("refresh failed"))));
        owners.add(runTerminalRefresh(
                manager, immediate, ticker, key("refresh-rejection"), "refresh rejection", queue,
                Mono.error(new IllegalStateException("admission rejected"))));
        owners.add(runTerminalRefresh(
                manager, immediate, ticker, key("refresh-empty"), "refresh empty", queue,
                Mono.empty()));

        EffectiveCachePolicy.Selection timeout =
                refreshSelection("refresh-timeout", 60_000, 32, 10, 50);
        owners.add(startNeverRefresh(
                manager, timeout, ticker, key("refresh-timeout"), "refresh timeout", queue, cancellations));
        scheduler.advanceTimeBy(Duration.ofMillis(50));
        assertThat(manager.workloadSnapshotForTesting().inFlightRefreshes()).isZero();

        EffectiveCachePolicy.Selection hardExpiry =
                refreshSelection("refresh-hard-expiry", 100, 32, 10, 500);
        owners.add(startNeverRefresh(
                manager, hardExpiry, ticker, key("refresh-hard-expiry"),
                "refresh hard expiry", queue, cancellations));
        scheduler.advanceTimeBy(Duration.ofMillis(90));
        assertThat(manager.workloadSnapshotForTesting().inFlightRefreshes()).isZero();

        owners.add(startNeverRefresh(
                manager, immediate, ticker, key("refresh-eviction"),
                "refresh eviction", queue, cancellations));
        manager.evictAllForTesting();
        assertThat(manager.workloadSnapshotForTesting().inFlightRefreshes()).isZero();

        LocalResponseCacheManager closingManager = LocalResponseCacheManager.testing(ticker::get, scheduler);
        owners.add(startNeverRefresh(
                closingManager, immediate, ticker, key("refresh-close"),
                "refresh close", queue, cancellations));
        closingManager.close();
        assertThat(closingManager.workloadSnapshotForTesting().inFlightRefreshes()).isZero();

        return new RefreshTerminalFixture(manager, scheduler, cancellations, owners);
    }

    private static TrackedReference runTerminalRefresh(
            LocalResponseCacheManager manager,
            EffectiveCachePolicy.Selection selection,
            AtomicLong ticker,
            CacheKeyContract.OpaqueKey key,
            String label,
            ReferenceQueue<Object> queue,
            Mono<?> terminal) {
        assertThat(manager.getOrLoad(selection, key, () -> Mono.just("initial")).block())
                .isEqualTo("initial");
        ticker.addAndGet(Duration.ofMillis(selection.policy().getRefreshAfterMs()).toNanos());
        Object owner = new Object();
        TrackedReference reference = track(label, owner, queue);
        assertThat(manager.getOrLoad(selection, key, () -> Mono.defer(() -> {
            owner.hashCode();
            return terminal;
        })).block()).isEqualTo("initial");
        assertThat(manager.workloadSnapshotForTesting().inFlightRefreshes()).isZero();
        return reference;
    }

    private static TrackedReference startNeverRefresh(
            LocalResponseCacheManager manager,
            EffectiveCachePolicy.Selection selection,
            AtomicLong ticker,
            CacheKeyContract.OpaqueKey key,
            String label,
            ReferenceQueue<Object> queue,
            AtomicInteger cancellations) {
        assertThat(manager.getOrLoad(selection, key, () -> Mono.just("initial")).block())
                .isEqualTo("initial");
        ticker.addAndGet(Duration.ofMillis(selection.policy().getRefreshAfterMs()).toNanos());
        Object owner = new Object();
        TrackedReference reference = track(label, owner, queue);
        assertThat(manager.getOrLoad(selection, key, () -> Mono.defer(() -> {
            owner.hashCode();
            return Mono.never();
        }).doOnCancel(cancellations::incrementAndGet)).block()).isEqualTo("initial");
        assertThat(manager.workloadSnapshotForTesting().inFlightRefreshes()).isEqualTo(1);
        return reference;
    }

    private static PreparedInvocationReferences invokePreparedCachedRequest(
            LocalResponseCacheManager manager,
            ReferenceQueue<Object> queue) {
        ReactiveHttpClientProperties.ClientConfig config = preparedRequestConfig();
        List<TrackedReference> references = new ArrayList<>();
        TrackingJsonCodec codec = new TrackingJsonCodec(queue, references);
        AuthProvider authProvider = request -> {
            String header = new String("principal-retention");
            AuthContext authContext = AuthContext.builder().header("X-Principal", header).build();
            references.add(track("auth context", authContext, queue));
            references.add(track("auth header value", header, queue));
            return Mono.just(authContext);
        };
        WebClient webClient = WebClient.builder()
                .baseUrl("http://retention.test")
                .filter(new OutboundAuthFilter("retention-client", authProvider))
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .header("X-Transient-Metadata", "not-retained")
                        .body("cached-response")
                        .build()))
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                    webClient,
                    new MethodMetadataCache(),
                    new RequestArgumentResolver(),
                    new DefaultErrorDecoder(),
                    config,
                    "retention-client",
                    PreparedRetentionClient.class,
                    context,
                    new NoopResilienceOperatorApplier(),
                    codec,
                    new ReactiveHttpClientProperties.ObservabilityConfig(),
                    manager,
                    authProvider,
                    "http://retention.test");
            PreparedRetentionClient client = (PreparedRetentionClient) Proxy.newProxyInstance(
                    ResponseCacheRetentionOwnershipTest.class.getClassLoader(),
                    new Class<?>[]{PreparedRetentionClient.class}, handler);
            String bodyValue = new String("body-retention");
            SelectedBody body = new SelectedBody(bodyValue);
            String contextValue = new String("tenant-retention");
            references.add(track("frozen body argument", body, queue));
            references.add(track("frozen body component", bodyValue, queue));
            references.add(track("prepared context value", contextValue, queue));

            assertThat(client.post(body)
                    .contextWrite(Context.of("tenant.id", contextValue))
                    .block()).isEqualTo("cached-response");
        }
        return new PreparedInvocationReferences(List.copyOf(references));
    }

    private static CloseReferences closeMeteredManagerWithLateLoad(
            SimpleMeterRegistry registry,
            ReferenceQueue<Object> queue) throws Exception {
        LocalResponseCacheMetrics metrics = LocalResponseCacheMetrics.enabled(registry, "retention-client");
        metrics.registerApi("retention.get");
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(
                System::nanoTime, Schedulers.parallel(), metrics, "retention-client");
        EffectiveCachePolicy.Selection selection = selection("metered", 60_000, 10, true);
        Object value = new Object();
        CacheKeyContract.OpaqueKey key = key("metered-value");
        assertThat(manager.getOrLoad(selection, key, () -> Mono.just(value)).block()).isSameAs(value);

        Object lateOwner = new Object();
        Sinks.One<Object> late = Sinks.one();
        Disposable lateSubscriber = manager.getOrLoad(
                        selection,
                        key("metered-late"),
                        () -> late.asMono().map(ignored -> lateOwner))
                .subscribe();
        LocalResponseCache cache = onlyCache(manager);
        ReactiveHttpClientFactoryBean<DiagnosticsRetentionClient> factory =
                new ReactiveHttpClientFactoryBean<>();
        setField(factory, "responseCacheManager", manager);
        List<TrackedReference> references = List.of(
                track("destroyed client factory", factory, queue),
                track("closed manager", manager, queue),
                track("closed cache", cache, queue),
                track("closed cache key", key, queue),
                track("closed cache value", value, queue),
                track("cancelled late publication owner", lateOwner, queue));

        factory.destroy();
        late.tryEmitValue(new Object());
        lateSubscriber.dispose();
        assertThat(manager.snapshot()).isEqualTo(
                new LocalResponseCacheManager.Snapshot(0, 0, 0, 0, true));
        return new CloseReferences(references);
    }

    private static IndependentLoadAfterClose startIndependentLoadThenClose(ReferenceQueue<Object> queue) {
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
        EffectiveCachePolicy.Selection selection = selection("independent-close", 60_000, 10, false);
        Sinks.Empty<Void> source = Sinks.empty();
        Object loadOwner = new Object();
        TrackedReference loadOwnerReference = track("caller-owned independent load", loadOwner, queue);
        java.util.concurrent.CompletableFuture<?> result = manager.getOrLoad(
                        selection,
                        key("independent-close"),
                        () -> Mono.defer(() -> {
                            loadOwner.hashCode();
                            return source.asMono().thenReturn("late-value");
                        }))
                .toFuture();

        manager.close();
        return new IndependentLoadAfterClose(manager, source, result, loadOwnerReference);
    }

    private static DiagnosticsReferences createAndCloseDiagnosticsFixture(
            ReferenceQueue<Object> queue) throws Exception {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(ReactiveHttpClientFactoryBean.class);
        definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, DiagnosticsRetentionClient.class);
        beanFactory.registerBeanDefinition("retentionDiagnosticsClient", definition);

        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
        EffectiveCachePolicy.Selection selection = selection("diagnostics", 60_000, 10, false);
        selection.policy().setSharedResponse(true);
        selection.policy().setVaryByParameters(List.of("body"));
        Object value = new Object();
        assertThat(manager.getOrLoad(selection, key("diagnostics"), () -> Mono.just(value)).block())
                .isSameAs(value);
        LocalResponseCache cache = onlyCache(manager);

        ReactiveHttpClientFactoryBean<DiagnosticsRetentionClient> factory = new ReactiveHttpClientFactoryBean<>();
        setField(factory, "responseCacheManager", manager);
        beanFactory.registerSingleton("retentionDiagnosticsClient", factory);
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.getCache().setPolicy("diagnostics");
        config.getCache().getPolicies().put("diagnostics", selection.policy());
        properties.getClients().put("retention-diagnostics", config);
        ReactiveHttpClientDiagnosticsProvider provider = new ReactiveHttpClientDiagnosticsProvider(
                beanFactory, properties, new MethodMetadataCache());
        ReactiveHttpClientDiagnosticsEndpoint endpoint = new ReactiveHttpClientDiagnosticsEndpoint(provider);
        Map<String, Object> snapshot = endpoint.diagnostics();
        List<TrackedReference> owners = List.of(
                track("diagnostics bean factory", beanFactory, queue),
                track("diagnostics provider", provider, queue),
                track("diagnostics endpoint", endpoint, queue),
                track("diagnostics client factory", factory, queue),
                track("diagnostics cache manager", manager, queue),
                track("diagnostics cache", cache, queue),
                track("diagnostics cached value", value, queue));

        manager.close();
        beanFactory.destroySingletons();
        return new DiagnosticsReferences(snapshot, owners);
    }

    private static void assertGenerationOwners(LocalResponseCacheManager manager, int expected) throws Exception {
        for (LocalResponseCache cache : caches(manager)) {
            Field generations = CaffeineLocalResponseCache.class.getDeclaredField("generations");
            generations.setAccessible(true);
            assertThat((Map<?, ?>) generations.get(cache)).hasSize(expected);
        }
    }

    private static LocalResponseCache onlyCache(LocalResponseCacheManager manager) throws Exception {
        assertThat(caches(manager)).hasSize(1);
        return caches(manager).iterator().next();
    }

    @SuppressWarnings("unchecked")
    private static List<LocalResponseCache> caches(LocalResponseCacheManager manager) throws Exception {
        Field caches = LocalResponseCacheManager.class.getDeclaredField("caches");
        caches.setAccessible(true);
        return List.copyOf(((Map<?, LocalResponseCache>) caches.get(manager)).values());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static SubscriptionReportingState state(Object body) {
        return new SubscriptionReportingState(
                new RequestArgumentResolver.ResolvedArgs(Map.of(), Map.of(), Map.of(), body));
    }

    private static RequestArgumentResolver.ResolvedArgs emptyResolved() {
        return new RequestArgumentResolver.ResolvedArgs(Map.of(), Map.of(), Map.of(), null);
    }

    private static EffectiveCachePolicy.Selection selection(
            String name, long ttlMs, long maximumSize, boolean singleFlight) {
        ReactiveHttpClientProperties.CachePolicyConfig policy = new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(ttlMs);
        policy.setMaximumSize(maximumSize);
        policy.setSingleFlight(singleFlight);
        return new EffectiveCachePolicy.Selection(true, EffectiveCachePolicy.Source.CLIENT, name, policy);
    }

    private static EffectiveCachePolicy.Selection refreshSelection(
            String name,
            long ttlMs,
            long maximumSize,
            long refreshAfterMs,
            long refreshTimeoutMs) {
        EffectiveCachePolicy.Selection selection = selection(name, ttlMs, maximumSize, false);
        selection.policy().setRefreshAfterMs(refreshAfterMs);
        selection.policy().setRefreshTimeoutMs(refreshTimeoutMs);
        return selection;
    }

    private static ReactiveHttpClientProperties.ClientConfig preparedRequestConfig() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setAuthProvider("retention-auth");
        config.setDefaultHeaders(Map.of("X-Principal", "unresolved"));
        ReactiveHttpClientProperties.CachePolicyConfig policy = new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(60_000L);
        policy.setMaximumSize(10L);
        policy.setSingleFlight(true);
        policy.setVaryByParameters(List.of("body"));
        policy.setVaryByHeaders(List.of("X-Principal", "Idempotency-Key"));
        policy.setVaryByContext(List.of("tenant.id"));
        config.getCache().getPolicies().put("prepared", policy);
        return config;
    }

    private static Object load(
            LocalResponseCacheManager manager,
            EffectiveCachePolicy.Selection selection,
            CacheKeyContract.OpaqueKey key,
            Object value) {
        return manager.getOrLoad(selection, key, () -> Mono.just(value)).block();
    }

    private static CacheKeyContract.OpaqueKey key(String value) {
        return CacheKeyContract.OpaqueKey.from(value.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isCacheMeter(io.micrometer.core.instrument.Meter meter) {
        return meter.getId().getName().startsWith(LocalResponseCacheMetrics.PREFIX + ".");
    }

    private static TrackedReference track(
            String label, Object value, ReferenceQueue<Object> queue) {
        return new TrackedReference(label, value, queue);
    }

    private static void assertRetained(TrackedReference reference) {
        for (int attempt = 0; attempt < 3; attempt++) {
            diagnosticGcCheckpoint();
        }
        assertThat(reference.get()).as(reference.label()).isNotNull();
    }

    private static void assertEventuallyCollectedCount(
            ReferenceQueue<Object> queue,
            List<TrackedReference> references,
            int expectedCollected) {
        long deadline = System.nanoTime() + COLLECTION_TIMEOUT.toNanos();
        int collected;
        do {
            drain(queue);
            collected = (int) references.stream().filter(reference -> reference.get() == null).count();
            if (collected >= expectedCollected) {
                return;
            }
            diagnosticGcCheckpoint();
        }
        while (System.nanoTime() < deadline);
        assertThat(collected)
                .as("collected references: %s", labels(references))
                .isGreaterThanOrEqualTo(expectedCollected);
    }

    private static void assertCollected(
            ReferenceQueue<Object> queue,
            List<TrackedReference> references) {
        Set<TrackedReference> pending = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.addAll(references);
        long deadline = System.nanoTime() + COLLECTION_TIMEOUT.toNanos();
        do {
            drain(queue);
            pending.removeIf(reference -> reference.get() == null);
            if (pending.isEmpty()) {
                return;
            }
            diagnosticGcCheckpoint();
        }
        while (System.nanoTime() < deadline);
        assertThat(pending)
                .extracting(TrackedReference::label)
                .as("starter-owned references still reachable after bounded collection attempts")
                .isEmpty();
    }

    private static void diagnosticGcCheckpoint() {
        System.gc();
        byte[][] pressure = new byte[4][];
        for (int index = 0; index < pressure.length; index++) {
            pressure[index] = new byte[256 * 1024];
        }
        try {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while awaiting reference release", error);
        }
    }

    private static void drain(ReferenceQueue<Object> queue) {
        while (queue.poll() != null) {
            // WeakReference#get is the assertion source; draining bounds queue retention.
        }
    }

    private static List<String> labels(List<TrackedReference> references) {
        return references.stream().map(TrackedReference::label).toList();
    }

    private static final class TrackedReference extends WeakReference<Object> {
        private final String label;

        private TrackedReference(String label, Object referent, ReferenceQueue<Object> queue) {
            super(referent, queue);
            this.label = label;
        }

        private String label() {
            return label;
        }
    }

    private static final class TrackingJsonCodec implements ReactiveHttpClientJsonCodec {
        private final ReferenceQueue<Object> queue;
        private final List<TrackedReference> references;

        private TrackingJsonCodec(
                ReferenceQueue<Object> queue,
                List<TrackedReference> references) {
            this.queue = queue;
            this.references = references;
        }

        @Override
        public byte[] write(Object value) {
            return writeBytes();
        }

        @Override
        public byte[] writeBounded(Object value, int maximumBytes) {
            return writeBytes();
        }

        private byte[] writeBytes() {
            byte[] bytes = "{\"value\":\"body-retention\"}".getBytes(StandardCharsets.UTF_8);
            references.add(track("prepared serialized body", bytes, queue));
            return bytes;
        }

        @Override
        public <T> T read(byte[] value, Class<T> type) {
            throw new UnsupportedOperationException("Response decoding is provided by WebClient in this fixture");
        }
    }

    private record TerminalReferences(
            TrackedReference cachedValue,
            List<TrackedReference> transientOwners) {
    }

    private record RefreshReferences(
            TrackedReference displacedValue,
            TrackedReference currentValue,
            TrackedReference failedRefreshOwner,
            TrackedReference cancelledRefreshOwner,
            AtomicInteger refreshCancellations) {
    }

    private record ActiveFlight(
            LocalResponseCacheManager manager,
            Sinks.One<String> result,
            Disposable leader,
            List<String> leaderValue,
            AtomicInteger sourceCancellations,
            List<TrackedReference> waiterOwners) {
    }

    private record DetachedLeaderFlight(
            LocalResponseCacheManager manager,
            Sinks.One<String> result,
            Disposable waiter,
            List<String> waiterValue,
            AtomicInteger sourceCancellations,
            List<TrackedReference> leaderOwners) {
    }

    private record RefreshTerminalFixture(
            LocalResponseCacheManager openManager,
            VirtualTimeScheduler scheduler,
            AtomicInteger cancellations,
            List<TrackedReference> refreshOwners) {
    }

    private record PreparedInvocationReferences(List<TrackedReference> transientOwners) {
    }

    private record CloseReferences(List<TrackedReference> owners) {
    }

    private record IndependentLoadAfterClose(
            LocalResponseCacheManager manager,
            Sinks.Empty<Void> source,
            java.util.concurrent.CompletableFuture<?> result,
            TrackedReference loadOwner) {
    }

    private record DiagnosticsReferences(
            Map<String, Object> snapshot,
            List<TrackedReference> owners) {
    }

    private record SelectedBody(String value) {
    }

    @ReactiveHttpClient(name = "retention-client", baseUrl = "http://retention.test")
    interface PreparedRetentionClient {
        @POST("/prepared")
        @CacheResponse(value = "prepared", semanticRead = true)
        Mono<String> post(@Body @CacheKey("body") SelectedBody body);
    }

    @ReactiveHttpClient(name = "retention-diagnostics", baseUrl = "http://retention.test")
    interface DiagnosticsRetentionClient {
        @POST("/diagnostics")
        @CacheResponse(value = "diagnostics", semanticRead = true)
        Mono<String> post(@Body @CacheKey("body") String body);
    }
}
