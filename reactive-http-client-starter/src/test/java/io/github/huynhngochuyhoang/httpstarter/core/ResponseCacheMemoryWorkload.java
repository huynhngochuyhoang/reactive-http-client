package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.netty.buffer.ByteBufAllocatorMetricProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.ConnectionPoolMetrics;
import reactor.netty.resources.ConnectionProvider;

import java.lang.reflect.Field;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

final class ResponseCacheMemoryWorkload {

    static final int PAYLOAD_BYTES = 4 * 1024;
    static final int KEY_CARDINALITY = 8;
    static final int CONCURRENCY = 8;
    static final int WARMUP_OPERATIONS = 2;
    static final int MEASURED_OPERATIONS = 8;
    static final long MAXIMUM_SIZE = 8;
    static final long PRESSURE_MAXIMUM_SIZE = 4;
    static final long TTL_MILLIS = 1_000;
    static final long REFRESH_AFTER_MILLIS = 500;
    static final long REFRESH_TIMEOUT_MILLIS = 5_000;
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(5);

    private ResponseCacheMemoryWorkload() {
    }

    static List<Evidence> runAll() throws Exception {
        List<Evidence> evidence = new ArrayList<>();
        for (Scenario scenario : Scenario.values()) {
            evidence.add(run(scenario));
        }
        return List.copyOf(evidence);
    }

    static Evidence run(Scenario scenario) throws Exception {
        return run(scenario, false);
    }

    static Evidence runCharacterization(Scenario scenario) throws Exception {
        return run(scenario, true);
    }

    private static Evidence run(Scenario scenario, boolean characterization) throws Exception {
        Fixture fixture = new Fixture(scenario, characterization);
        try {
            fixture.warmUp();
            fixture.checkpoint("baseline-after-explicit-gc", true);
            switch (scenario) {
                case CACHE_DISABLED -> cacheDisabled(fixture);
                case COLD_MISS -> coldMiss(fixture);
                case WARM_HIT -> warmHit(fixture);
                case MAXIMUM_SIZE_PRESSURE -> maximumSizePressure(fixture);
                case TTL_EXPIRY -> ttlExpiry(fixture);
                case EXPLICIT_EVICTION -> explicitEviction(fixture);
                case DUPLICATE_MISS -> duplicateMiss(fixture);
                case SINGLE_FLIGHT -> singleFlight(fixture);
                case REFRESH -> refresh(fixture);
                case CANCELLATION -> cancellation(fixture);
                case FACTORY_CLOSE -> factoryClose(fixture);
            }
            fixture.characterizationCheckpoint("steady-after-explicit-gc");
        }
        finally {
            fixture.close();
        }
        return fixture.evidence();
    }

    private static void cacheDisabled(Fixture fixture) {
        for (int index = 0; index < MEASURED_OPERATIONS; index++) {
            fixture.requirePayload(fixture.call(index, Operation.MEASURED).block(AWAIT_TIMEOUT));
        }
        fixture.checkpoint("after-operations");
    }

    private static void coldMiss(Fixture fixture) {
        for (int index = 0; index < MEASURED_OPERATIONS; index++) {
            fixture.requirePayload(fixture.call(index, Operation.MEASURED).block(AWAIT_TIMEOUT));
        }
        fixture.checkpoint("after-cold-misses");
    }

    private static void warmHit(Fixture fixture) {
        fixture.fill(Operation.SETUP);
        fixture.checkpoint("after-fill");
        for (int index = 0; index < MEASURED_OPERATIONS; index++) {
            fixture.requirePayload(fixture.call(index, Operation.MEASURED).block(AWAIT_TIMEOUT));
        }
        fixture.checkpoint("after-warm-hits");
    }

    private static void maximumSizePressure(Fixture fixture) {
        for (int index = 0; index < MEASURED_OPERATIONS; index++) {
            fixture.requirePayload(fixture.call(index, Operation.MEASURED).block(AWAIT_TIMEOUT));
        }
        fixture.checkpoint("after-capacity-pressure");
    }

    private static void ttlExpiry(Fixture fixture) {
        fixture.fill(Operation.SETUP);
        fixture.checkpoint("after-fill");
        fixture.tickerNanos.addAndGet(Duration.ofMillis(TTL_MILLIS).toNanos());
        fixture.checkpoint("after-expiry");
        fixture.characterizationCheckpoint("expired-after-explicit-gc");
        for (int index = 0; index < MEASURED_OPERATIONS; index++) {
            fixture.requirePayload(fixture.call(index, Operation.MEASURED).block(AWAIT_TIMEOUT));
        }
        fixture.checkpoint("after-reload");
    }

    private static void explicitEviction(Fixture fixture) {
        fixture.fill(Operation.SETUP);
        fixture.checkpoint("after-fill");
        fixture.manager.evictAllForTesting();
        fixture.checkpoint("after-eviction");
        fixture.characterizationCheckpoint("evicted-after-explicit-gc");
        for (int index = 0; index < MEASURED_OPERATIONS; index++) {
            fixture.requirePayload(fixture.call(index, Operation.MEASURED).block(AWAIT_TIMEOUT));
        }
        fixture.checkpoint("after-reload");
    }

    private static void duplicateMiss(Fixture fixture) throws Exception {
        ResponseGate gate = fixture.server.gateNextResponse();
        List<CompletableFuture<byte[]>> callers = fixture.concurrentCallers(0);
        gate.awaitDispatch();
        fixture.await(() -> fixture.loadSubscriptions.get() == CONCURRENCY,
                "duplicate miss subscriptions");
        fixture.checkpoint("duplicate-loads-gated");

        gate.release();
        for (CompletableFuture<byte[]> caller : callers) {
            fixture.requirePayload(caller.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
        }
        fixture.await(() -> fixture.server.completedDispatches.get() == CONCURRENCY,
                "duplicate miss completion");
        fixture.checkpoint("after-duplicate-misses");
    }

    private static void singleFlight(Fixture fixture) throws Exception {
        ResponseGate gate = fixture.server.gateNextResponse();
        List<CompletableFuture<byte[]>> callers = fixture.concurrentCallers(0);
        gate.awaitDispatch();
        fixture.awaitSnapshot(snapshot -> snapshot.inFlightLoads() == 1
                && snapshot.coalescedWaiters() == CONCURRENCY - 1, "single-flight waiters");
        fixture.checkpoint("load-gated");

        gate.release();
        for (CompletableFuture<byte[]> caller : callers) {
            fixture.requirePayload(caller.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
        }
        fixture.awaitSnapshot(snapshot -> snapshot.inFlightLoads() == 0, "single-flight completion");
        fixture.checkpoint("after-shared-load");
    }

    private static void refresh(Fixture fixture) throws Exception {
        byte[] initial = fixture.call(0, Operation.SETUP).block(AWAIT_TIMEOUT);
        fixture.requirePayload(initial);
        fixture.tickerNanos.addAndGet(Duration.ofMillis(REFRESH_AFTER_MILLIS).toNanos());
        ResponseGate gate = fixture.server.gateNextResponse();

        for (int index = 0; index < MEASURED_OPERATIONS; index++) {
            byte[] stale = fixture.call(0, Operation.MEASURED).block(AWAIT_TIMEOUT);
            if (stale != initial) {
                throw new IllegalStateException("Refresh workload did not return the retained stale value");
            }
        }
        gate.awaitDispatch();
        fixture.awaitSnapshot(snapshot -> snapshot.inFlightRefreshes() == 1, "hidden refresh");
        fixture.checkpoint("refresh-gated");

        gate.release();
        fixture.awaitSnapshot(snapshot -> snapshot.inFlightRefreshes() == 0, "refresh completion");
        fixture.await(() -> fixture.server.completedDispatches.get() == 2, "refresh response completion");
        byte[] refreshed = fixture.call(0, Operation.VERIFICATION).block(AWAIT_TIMEOUT);
        fixture.requirePayload(refreshed);
        if (refreshed == initial || refreshed[0] == initial[0]) {
            throw new IllegalStateException("Refresh workload did not publish the replacement value");
        }
        fixture.checkpoint("after-refresh");
    }

    private static void cancellation(Fixture fixture) throws Exception {
        ResponseGate gate = fixture.server.gateNextResponse();
        List<Disposable> callers = fixture.concurrentSubscriptions(0);
        gate.awaitDispatch();
        fixture.awaitSnapshot(snapshot -> snapshot.inFlightLoads() == 1
                && snapshot.coalescedWaiters() == CONCURRENCY - 1, "cancellation waiters");
        fixture.checkpoint("load-gated");

        callers.forEach(Disposable::dispose);
        fixture.await(() -> fixture.loadCancellations.get() == 1, "cancelled loader");
        fixture.awaitSnapshot(snapshot -> snapshot.inFlightLoads() == 0, "cancelled flight cleanup");
        fixture.await(() -> fixture.server.activeDispatches.get() == 0, "cancelled server dispatch");
        fixture.checkpoint("after-cancellation");
    }

    private static void factoryClose(Fixture fixture) throws Exception {
        fixture.requirePayload(fixture.call(0, Operation.SETUP).block(AWAIT_TIMEOUT));
        ResponseGate gate = fixture.server.gateNextResponse();
        List<CompletableFuture<byte[]>> callers = fixture.concurrentCallers(1);
        gate.awaitDispatch();
        fixture.awaitSnapshot(snapshot -> snapshot.inFlightLoads() == 1
                && snapshot.coalescedWaiters() == CONCURRENCY - 1, "factory-close waiters");
        fixture.checkpoint("before-factory-close");

        fixture.closeFactory();
        fixture.await(() -> callers.stream().allMatch(CompletableFuture::isDone), "factory-close callers");
        fixture.await(() -> fixture.loadCancellations.get() == 1, "factory-close loader cancellation");
        fixture.await(() -> fixture.server.activeDispatches.get() == 0, "factory-close server dispatch");
        fixture.checkpoint("after-factory-close");
    }

    enum Scenario {
        CACHE_DISABLED,
        COLD_MISS,
        WARM_HIT,
        MAXIMUM_SIZE_PRESSURE,
        TTL_EXPIRY,
        EXPLICIT_EVICTION,
        DUPLICATE_MISS,
        SINGLE_FLIGHT,
        REFRESH,
        CANCELLATION,
        FACTORY_CLOSE;

        String id() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    record Definition(
            String payloadShape,
            int payloadBytes,
            int keyCardinality,
            int concurrency,
            int warmupOperations,
            int measuredOperations,
            long ttlMillis,
            long maximumSize,
            boolean cacheEnabled,
            boolean singleFlight,
            long refreshAfterMillis,
            long refreshTimeoutMillis) {
    }

    record PoolSnapshot(
            int registeredPools,
            int totalConnections,
            int activeConnections,
            int idleConnections,
            int pendingAcquires,
            int maximumConnections,
            boolean disposed) {
    }

    record ApplicationPayloadSnapshot(long allocations, long allocatedBytes) {
    }

    record Checkpoint(
            String name,
            long tickerNanos,
            int measuredCallers,
            int setupCallers,
            int verificationCallers,
            int callerTerminals,
            int callerCancellations,
            int loadSubscriptions,
            int loadSuccesses,
            int loadFailures,
            int loadCancellations,
            int serverDispatches,
            int completedDispatches,
            int cancelledDispatches,
            int activeDispatches,
            LocalResponseCacheManager.WorkloadSnapshot cache,
            PoolSnapshot connectionPool,
            ApplicationPayloadSnapshot applicationPayload,
            ResponseCacheMemoryDomains.Snapshot memory,
            int contextsCreated,
            int contextsClosed,
            int factoriesCreated,
            int factoriesClosed,
            int serversStarted,
            int serversStopped) {
    }

    record Evidence(
            Scenario scenario,
            Definition definition,
            ResponseCacheMemoryDomains.Environment environment,
            List<Checkpoint> checkpoints) {
        Evidence {
            checkpoints = List.copyOf(checkpoints);
        }

        Checkpoint checkpoint(String name) {
            return checkpoints.stream()
                    .filter(checkpoint -> checkpoint.name().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No checkpoint '" + name + "' for " + scenario.id()));
        }

        Checkpoint lastCheckpoint() {
            return checkpoints.getLast();
        }
    }

    static String render(List<Evidence> evidence) {
        StringBuilder output = new StringBuilder();
        output.append("format=v29-response-cache-memory-workload-v2\n");
        for (Evidence item : evidence) {
            Definition definition = item.definition();
            String prefix = "scenario." + item.scenario().id();
            output.append(prefix).append(".payloadShape=").append(definition.payloadShape()).append('\n');
            output.append(prefix).append(".payloadBytes=").append(definition.payloadBytes()).append('\n');
            output.append(prefix).append(".keyCardinality=").append(definition.keyCardinality()).append('\n');
            output.append(prefix).append(".concurrency=").append(definition.concurrency()).append('\n');
            output.append(prefix).append(".warmupOperations=").append(definition.warmupOperations()).append('\n');
            output.append(prefix).append(".measuredOperations=").append(definition.measuredOperations()).append('\n');
            output.append(prefix).append(".ttlMillis=").append(definition.ttlMillis()).append('\n');
            output.append(prefix).append(".maximumSize=").append(definition.maximumSize()).append('\n');
            output.append(prefix).append(".cacheEnabled=").append(definition.cacheEnabled()).append('\n');
            output.append(prefix).append(".singleFlight=").append(definition.singleFlight()).append('\n');
            output.append(prefix).append(".refreshAfterMillis=").append(definition.refreshAfterMillis()).append('\n');
            output.append(prefix).append(".refreshTimeoutMillis=").append(definition.refreshTimeoutMillis()).append('\n');
            appendEnvironment(output, prefix, item.environment());
            output.append(prefix).append(".checkpointCount=").append(item.checkpoints().size()).append('\n');
            for (int index = 0; index < item.checkpoints().size(); index++) {
                Checkpoint checkpoint = item.checkpoints().get(index);
                String checkpointPrefix = prefix + ".checkpoint." + index;
                output.append(checkpointPrefix).append(".name=").append(checkpoint.name()).append('\n');
                output.append(checkpointPrefix).append(".measuredCallers=").append(checkpoint.measuredCallers()).append('\n');
                output.append(checkpointPrefix).append(".setupCallers=").append(checkpoint.setupCallers()).append('\n');
                output.append(checkpointPrefix).append(".verificationCallers=").append(checkpoint.verificationCallers()).append('\n');
                output.append(checkpointPrefix).append(".callerTerminals=").append(checkpoint.callerTerminals()).append('\n');
                output.append(checkpointPrefix).append(".callerCancellations=").append(checkpoint.callerCancellations()).append('\n');
                output.append(checkpointPrefix).append(".loadSubscriptions=").append(checkpoint.loadSubscriptions()).append('\n');
                output.append(checkpointPrefix).append(".loadSuccesses=").append(checkpoint.loadSuccesses()).append('\n');
                output.append(checkpointPrefix).append(".loadFailures=").append(checkpoint.loadFailures()).append('\n');
                output.append(checkpointPrefix).append(".loadCancellations=").append(checkpoint.loadCancellations()).append('\n');
                output.append(checkpointPrefix).append(".serverDispatches=").append(checkpoint.serverDispatches()).append('\n');
                output.append(checkpointPrefix).append(".completedDispatches=").append(checkpoint.completedDispatches()).append('\n');
                output.append(checkpointPrefix).append(".cancelledDispatches=").append(checkpoint.cancelledDispatches()).append('\n');
                output.append(checkpointPrefix).append(".activeDispatches=").append(checkpoint.activeDispatches()).append('\n');
                output.append(checkpointPrefix).append(".cacheEntries=").append(checkpoint.cache().cache().currentSize()).append('\n');
                output.append(checkpointPrefix).append(".cacheEvictions=").append(checkpoint.cache().cache().evictions()).append('\n');
                output.append(checkpointPrefix).append(".inFlightLoads=").append(checkpoint.cache().inFlightLoads()).append('\n');
                output.append(checkpointPrefix).append(".coalescedWaiters=").append(checkpoint.cache().coalescedWaiters()).append('\n');
                output.append(checkpointPrefix).append(".inFlightRefreshes=").append(checkpoint.cache().inFlightRefreshes()).append('\n');
                output.append(checkpointPrefix).append(".cacheClosed=").append(checkpoint.cache().cache().closed()).append('\n');
                appendPool(output, checkpointPrefix, checkpoint.connectionPool());
                appendApplicationPayload(output, checkpointPrefix, checkpoint.applicationPayload());
                appendMemory(output, checkpointPrefix, checkpoint.memory());
                output.append(checkpointPrefix).append(".contextsCreated=").append(checkpoint.contextsCreated()).append('\n');
                output.append(checkpointPrefix).append(".contextsClosed=").append(checkpoint.contextsClosed()).append('\n');
                output.append(checkpointPrefix).append(".factoriesCreated=").append(checkpoint.factoriesCreated()).append('\n');
                output.append(checkpointPrefix).append(".factoriesClosed=").append(checkpoint.factoriesClosed()).append('\n');
                output.append(checkpointPrefix).append(".serversStarted=").append(checkpoint.serversStarted()).append('\n');
                output.append(checkpointPrefix).append(".serversStopped=").append(checkpoint.serversStopped()).append('\n');
            }
        }
        return output.toString();
    }

    private static void appendEnvironment(
            StringBuilder output,
            String prefix,
            ResponseCacheMemoryDomains.Environment environment) {
        output.append(prefix).append(".javaVersion=").append(environment.javaVersion()).append("\n");
        output.append(prefix).append(".javaVm=").append(environment.javaVm()).append("\n");
        output.append(prefix).append(".jvmFlags=").append(environment.jvmFlags()).append("\n");
        output.append(prefix).append(".maximumHeapBytes=").append(environment.maximumHeapBytes()).append("\n");
        output.append(prefix).append(".configuredDirectMemoryLimitBytes=")
                .append(environment.configuredDirectMemoryLimitBytes()).append("\n");
        output.append(prefix).append(".directMemoryLimitSource=")
                .append(environment.directMemoryLimitSource()).append("\n");
        output.append(prefix).append(".containerMemoryLimitBytes=")
                .append(environment.containerMemoryLimitBytes()).append("\n");
        output.append(prefix).append(".osName=").append(environment.osName()).append("\n");
        output.append(prefix).append(".osVersion=").append(environment.osVersion()).append("\n");
        output.append(prefix).append(".osArchitecture=").append(environment.osArchitecture()).append("\n");
        output.append(prefix).append(".transport=").append(environment.transport()).append("\n");
        output.append(prefix).append(".allocator=").append(environment.allocator()).append("\n");
        output.append(prefix).append(".starterCommit=").append(environment.starterCommit()).append("\n");
        output.append(prefix).append(".sourceTreeDirty=").append(environment.sourceTreeDirty()).append("\n");
    }

    private static void appendPool(StringBuilder output, String prefix, PoolSnapshot pool) {
        output.append(prefix).append(".poolRegistered=").append(pool.registeredPools()).append("\n");
        output.append(prefix).append(".poolTotalConnections=").append(pool.totalConnections()).append("\n");
        output.append(prefix).append(".poolActiveConnections=").append(pool.activeConnections()).append("\n");
        output.append(prefix).append(".poolIdleConnections=").append(pool.idleConnections()).append("\n");
        output.append(prefix).append(".poolPendingAcquires=").append(pool.pendingAcquires()).append("\n");
        output.append(prefix).append(".poolMaximumConnections=").append(pool.maximumConnections()).append("\n");
        output.append(prefix).append(".poolDisposed=").append(pool.disposed()).append("\n");
    }

    private static void appendApplicationPayload(
            StringBuilder output,
            String prefix,
            ApplicationPayloadSnapshot payload) {
        output.append(prefix).append(".applicationPayloadAllocations=")
                .append(payload.allocations()).append("\n");
        output.append(prefix).append(".applicationPayloadAllocatedBytes=")
                .append(payload.allocatedBytes()).append("\n");
    }

    private static void appendMemory(
            StringBuilder output,
            String prefix,
            ResponseCacheMemoryDomains.Snapshot memory) {
        output.append(prefix).append(".explicitGcRequested=").append(memory.explicitGcRequested()).append("\n");
        output.append(prefix).append(".explicitGcObserved=").append(memory.explicitGcObserved()).append("\n");
        output.append(prefix).append(".processRssBytes=").append(memory.processRssBytes()).append("\n");
        output.append(prefix).append(".usedHeapBytes=").append(memory.usedHeapBytes()).append("\n");
        output.append(prefix).append(".committedHeapBytes=").append(memory.committedHeapBytes()).append("\n");
        output.append(prefix).append(".maximumHeapBytes=").append(memory.maximumHeapBytes()).append("\n");
        output.append(prefix).append(".directBufferCount=").append(memory.directBufferCount()).append("\n");
        output.append(prefix).append(".directBufferMemoryUsedBytes=")
                .append(memory.directBufferMemoryUsedBytes()).append("\n");
        output.append(prefix).append(".directBufferTotalCapacityBytes=")
                .append(memory.directBufferTotalCapacityBytes()).append("\n");
        output.append(prefix).append(".nettyAllocatorDirectMemoryUsedBytes=")
                .append(memory.nettyAllocatorDirectMemoryUsedBytes()).append("\n");
        output.append(prefix).append(".liveThreadCount=").append(memory.liveThreadCount()).append("\n");
        output.append(prefix).append(".daemonThreadCount=").append(memory.daemonThreadCount()).append("\n");
        output.append(prefix).append(".peakThreadCount=").append(memory.peakThreadCount()).append("\n");
    }

    private enum Operation {
        MEASURED,
        SETUP,
        VERIFICATION
    }

    private static final class Fixture implements AutoCloseable {
        private final Scenario scenario;
        private final boolean characterization;
        private final AtomicLong tickerNanos = new AtomicLong();
        private final Scheduler refreshScheduler;
        private final LoopbackServer server;
        private final PoolMetricsRecorder poolMetrics;
        private final ConnectionProvider connectionProvider;
        private final AnnotationConfigApplicationContext context;
        private final LocalResponseCacheManager manager;
        private final ReactiveHttpClientFactoryBean<Object> factory;
        private final WebClient webClient;
        private final EffectiveCachePolicy.Selection selection;
        private final ResponseCacheMemoryDomains.Environment environment;
        private final AtomicInteger measuredCallers = new AtomicInteger();
        private final AtomicInteger setupCallers = new AtomicInteger();
        private final AtomicInteger verificationCallers = new AtomicInteger();
        private final AtomicInteger callerTerminals = new AtomicInteger();
        private final AtomicInteger callerCancellations = new AtomicInteger();
        private final AtomicInteger loadSubscriptions = new AtomicInteger();
        private final AtomicInteger loadSuccesses = new AtomicInteger();
        private final AtomicInteger loadFailures = new AtomicInteger();
        private final AtomicInteger loadCancellations = new AtomicInteger();
        private final AtomicLong applicationPayloadAllocations = new AtomicLong();
        private final AtomicLong applicationPayloadAllocatedBytes = new AtomicLong();
        private final List<Checkpoint> checkpoints = new ArrayList<>();
        private final AtomicInteger contextsCreated = new AtomicInteger(1);
        private final AtomicInteger contextsClosed = new AtomicInteger();
        private final AtomicInteger factoriesCreated = new AtomicInteger(1);
        private final AtomicInteger factoriesClosed = new AtomicInteger();
        private final AtomicInteger serversStarted = new AtomicInteger(1);
        private final AtomicInteger serversStopped = new AtomicInteger();
        private boolean closed;
        private boolean factoryClosed;

        private Fixture(Scenario scenario, boolean characterization) throws Exception {
            this.scenario = Objects.requireNonNull(scenario, "scenario");
            this.characterization = characterization;
            this.refreshScheduler = Schedulers.newSingle("v29-memory-refresh-" + scenario.id());
            this.server = new LoopbackServer();
            this.poolMetrics = new PoolMetricsRecorder();
            this.connectionProvider = ConnectionProvider.builder("v29-memory-" + scenario.id())
                    .maxConnections(1)
                    .pendingAcquireMaxCount(CONCURRENCY)
                    .metrics(true, () -> poolMetrics)
                    .build();
            this.context = new AnnotationConfigApplicationContext();
            context.refresh();
            this.manager = LocalResponseCacheManager.testing(tickerNanos::get, refreshScheduler);
            this.factory = new ReactiveHttpClientFactoryBean<>();
            attachManager(factory, manager);
            this.webClient = WebClient.builder()
                    .baseUrl(server.baseUrl())
                    .clientConnector(new ReactorClientHttpConnector(HttpClient.create(connectionProvider)))
                    .build();
            this.selection = scenario == Scenario.CACHE_DISABLED ? null : selection(scenario);
            this.environment = ResponseCacheMemoryDomains.environment(
                    projectRoot(), server.transport(), server.allocator());
        }

        private void warmUp() {
            for (int index = 0; index < WARMUP_OPERATIONS; index++) {
                requirePayload(rawLoad(index).block(AWAIT_TIMEOUT));
            }
            await(() -> server.activeDispatches.get() == 0, "warmup dispatch completion");
            server.resetCounters();
            loadSubscriptions.set(0);
            loadSuccesses.set(0);
            loadFailures.set(0);
            loadCancellations.set(0);
            applicationPayloadAllocations.set(0);
            applicationPayloadAllocatedBytes.set(0);
        }

        private void fill(Operation operation) {
            for (int index = 0; index < KEY_CARDINALITY; index++) {
                requirePayload(call(index, operation).block(AWAIT_TIMEOUT));
            }
        }

        @SuppressWarnings("unchecked")
        private Mono<byte[]> call(int keyIndex, Operation operation) {
            return Mono.defer(() -> {
                        switch (operation) {
                            case MEASURED -> measuredCallers.incrementAndGet();
                            case SETUP -> setupCallers.incrementAndGet();
                            case VERIFICATION -> verificationCallers.incrementAndGet();
                        }
                        Mono<byte[]> result = scenario == Scenario.CACHE_DISABLED
                                ? rawLoad(keyIndex)
                                : (Mono<byte[]>) manager.getOrLoad(
                                        selection, key(keyIndex), () -> rawLoad(keyIndex));
                        return result;
                    })
                    .doFinally(signal -> {
                        callerTerminals.incrementAndGet();
                        if (signal == SignalType.CANCEL) {
                            callerCancellations.incrementAndGet();
                        }
                    });
        }

        private Mono<byte[]> rawLoad(int keyIndex) {
            return Mono.defer(() -> {
                loadSubscriptions.incrementAndGet();
                return webClient.get()
                        .uri("/memory/{key}", "synthetic-" + Math.floorMod(keyIndex, KEY_CARDINALITY))
                        .retrieve()
                        .bodyToMono(byte[].class)
                        .doOnNext(payload -> {
                            applicationPayloadAllocations.incrementAndGet();
                            applicationPayloadAllocatedBytes.addAndGet(payload.length);
                        })
                        .doOnSuccess(ignored -> loadSuccesses.incrementAndGet())
                        .doOnError(ignored -> loadFailures.incrementAndGet())
                        .doOnCancel(loadCancellations::incrementAndGet);
            });
        }

        private List<CompletableFuture<byte[]>> concurrentCallers(int keyIndex) {
            List<CompletableFuture<byte[]>> callers = new ArrayList<>();
            for (int index = 0; index < CONCURRENCY; index++) {
                callers.add(call(keyIndex, Operation.MEASURED).toFuture());
            }
            return callers;
        }

        private List<Disposable> concurrentSubscriptions(int keyIndex) {
            List<Disposable> callers = new ArrayList<>();
            for (int index = 0; index < CONCURRENCY; index++) {
                callers.add(call(keyIndex, Operation.MEASURED).subscribe());
            }
            return callers;
        }

        private void awaitSnapshot(
                java.util.function.Predicate<LocalResponseCacheManager.WorkloadSnapshot> condition,
                String description) {
            await(() -> condition.test(manager.workloadSnapshotForTesting()), description);
        }

        private void await(BooleanSupplier condition, String description) {
            long deadline = System.nanoTime() + AWAIT_TIMEOUT.toNanos();
            while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            if (!condition.getAsBoolean()) {
                throw new IllegalStateException("Timed out waiting for " + description);
            }
        }

        private void requirePayload(byte[] payload) {
            if (payload == null || payload.length != PAYLOAD_BYTES) {
                throw new IllegalStateException("Loopback workload returned an invalid synthetic payload shape");
            }
        }

        private void checkpoint(String name) {
            checkpoint(name, false);
        }

        private void checkpoint(String name, boolean requestExplicitGc) {
            LocalResponseCacheManager.WorkloadSnapshot cache = manager.workloadSnapshotForTesting();
            checkpoints.add(new Checkpoint(
                    name,
                    tickerNanos.get(),
                    measuredCallers.get(),
                    setupCallers.get(),
                    verificationCallers.get(),
                    callerTerminals.get(),
                    callerCancellations.get(),
                    loadSubscriptions.get(),
                    loadSuccesses.get(),
                    loadFailures.get(),
                    loadCancellations.get(),
                    server.dispatches.get(),
                    server.completedDispatches.get(),
                    server.cancelledDispatches.get(),
                    server.activeDispatches.get(),
                    cache,
                    poolMetrics.snapshot(connectionProvider),
                    new ApplicationPayloadSnapshot(
                            applicationPayloadAllocations.get(),
                            applicationPayloadAllocatedBytes.get()),
                    ResponseCacheMemoryDomains.capture(
                            name, requestExplicitGc, server.allocatorDirectMemoryUsedBytes()),
                    contextsCreated.get(),
                    contextsClosed.get(),
                    factoriesCreated.get(),
                    factoriesClosed.get(),
                    serversStarted.get(),
                    serversStopped.get()));
        }

        private void characterizationCheckpoint(String name) {
            if (characterization) {
                checkpoint(name, true);
            }
        }

        private Evidence evidence() {
            long maximumSize = scenario == Scenario.MAXIMUM_SIZE_PRESSURE
                    ? PRESSURE_MAXIMUM_SIZE
                    : MAXIMUM_SIZE;
            return new Evidence(
                    scenario,
                    new Definition(
                            "synthetic-byte-array",
                            PAYLOAD_BYTES,
                            KEY_CARDINALITY,
                            CONCURRENCY,
                            WARMUP_OPERATIONS,
                            MEASURED_OPERATIONS,
                            TTL_MILLIS,
                            maximumSize,
                            scenario != Scenario.CACHE_DISABLED,
                            scenario == Scenario.SINGLE_FLIGHT
                                    || scenario == Scenario.CANCELLATION
                                    || scenario == Scenario.FACTORY_CLOSE,
                            scenario == Scenario.REFRESH ? REFRESH_AFTER_MILLIS : 0,
                            scenario == Scenario.REFRESH ? REFRESH_TIMEOUT_MILLIS : 0),
                    environment,
                    checkpoints);
        }

        private void closeFactory() {
            if (!factoryClosed) {
                factoryClosed = true;
                factory.destroy();
                factoriesClosed.incrementAndGet();
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            closeFactory();
            context.close();
            contextsClosed.incrementAndGet();
            connectionProvider.disposeLater().block(AWAIT_TIMEOUT);
            server.close();
            serversStopped.incrementAndGet();
            refreshScheduler.dispose();
            checkpoint(characterization
                    ? "fixture-closed-after-explicit-gc"
                    : "fixture-closed", characterization);
        }

        private static void attachManager(
                ReactiveHttpClientFactoryBean<?> factory,
                LocalResponseCacheManager manager) throws Exception {
            Field field = ReactiveHttpClientFactoryBean.class.getDeclaredField("responseCacheManager");
            field.setAccessible(true);
            field.set(factory, manager);
        }
    }

    private static final class PoolMetricsRecorder implements ConnectionProvider.MeterRegistrar {
        private final Map<PoolKey, ConnectionPoolMetrics> pools = new ConcurrentHashMap<>();

        @Override
        public void registerMetrics(
                String poolName,
                String id,
                SocketAddress remoteAddress,
                ConnectionPoolMetrics metrics) {
            pools.put(new PoolKey(poolName, id, remoteAddress), metrics);
        }

        @Override
        public void deRegisterMetrics(String poolName, String id, SocketAddress remoteAddress) {
            pools.remove(new PoolKey(poolName, id, remoteAddress));
        }

        private PoolSnapshot snapshot(ConnectionProvider provider) {
            int total = 0;
            int active = 0;
            int idle = 0;
            int pending = 0;
            List<ConnectionPoolMetrics> current = List.copyOf(pools.values());
            for (ConnectionPoolMetrics metrics : current) {
                total += metrics.allocatedSize();
                active += metrics.acquiredSize();
                idle += metrics.idleSize();
                pending += metrics.pendingAcquireSize();
            }
            return new PoolSnapshot(
                    current.size(),
                    total,
                    active,
                    idle,
                    pending,
                    provider.maxConnections(),
                    provider.isDisposed());
        }

        private record PoolKey(String poolName, String id, SocketAddress remoteAddress) {
        }
    }

    private static EffectiveCachePolicy.Selection selection(Scenario scenario) {
        ReactiveHttpClientProperties.CachePolicyConfig policy =
                new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(TTL_MILLIS);
        policy.setMaximumSize(scenario == Scenario.MAXIMUM_SIZE_PRESSURE
                ? PRESSURE_MAXIMUM_SIZE
                : MAXIMUM_SIZE);
        if (scenario == Scenario.SINGLE_FLIGHT
                || scenario == Scenario.CANCELLATION
                || scenario == Scenario.FACTORY_CLOSE) {
            policy.setSingleFlight(true);
        }
        if (scenario == Scenario.REFRESH) {
            policy.setRefreshAfterMs(REFRESH_AFTER_MILLIS);
            policy.setRefreshTimeoutMs(REFRESH_TIMEOUT_MILLIS);
        }
        return new EffectiveCachePolicy.Selection(
                true, EffectiveCachePolicy.Source.CLIENT, "memory-policy", policy);
    }

    private static Path projectRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        return Files.exists(cwd.resolve("README.md")) ? cwd : cwd.getParent();
    }

    private static CacheKeyContract.OpaqueKey key(int keyIndex) {
        return CacheKeyContract.OpaqueKey.from(
                ("synthetic-key-" + Math.floorMod(keyIndex, KEY_CARDINALITY))
                        .getBytes(StandardCharsets.UTF_8));
    }

    private static final class LoopbackServer implements AutoCloseable {
        private final AtomicInteger dispatchSequence = new AtomicInteger();
        private final AtomicInteger dispatches = new AtomicInteger();
        private final AtomicInteger completedDispatches = new AtomicInteger();
        private final AtomicInteger cancelledDispatches = new AtomicInteger();
        private final AtomicInteger activeDispatches = new AtomicInteger();
        private final AtomicReference<ResponseGate> nextGate = new AtomicReference<>();
        private final DisposableServer server;

        private LoopbackServer() {
            server = HttpServer.create()
                    .host("127.0.0.1")
                    .port(0)
                    .handle((request, response) -> {
                        int sequence = dispatchSequence.incrementAndGet();
                        dispatches.incrementAndGet();
                        activeDispatches.incrementAndGet();
                        ResponseGate gate = nextGate.getAndSet(null);
                        if (gate != null) {
                            gate.dispatched.countDown();
                        }
                        Mono<Void> release = gate != null ? gate.release.asMono() : Mono.empty();
                        return release.then(response
                                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                                        .sendByteArray(Mono.just(payload(sequence)))
                                        .then())
                                .doOnSuccess(ignored -> completedDispatches.incrementAndGet())
                                .doOnCancel(cancelledDispatches::incrementAndGet)
                                .doFinally(ignored -> activeDispatches.decrementAndGet());
                    })
                    .bindNow(AWAIT_TIMEOUT);
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.port();
        }

        private String transport() {
            return server.channel().getClass().getName();
        }

        private String allocator() {
            return server.channel().alloc().getClass().getName();
        }

        private long allocatorDirectMemoryUsedBytes() {
            return server.channel().alloc() instanceof ByteBufAllocatorMetricProvider provider
                    ? provider.metric().usedDirectMemory()
                    : ResponseCacheMemoryDomains.UNAVAILABLE;
        }

        private ResponseGate gateNextResponse() {
            ResponseGate gate = new ResponseGate();
            if (!nextGate.compareAndSet(null, gate)) {
                throw new IllegalStateException("A loopback response gate is already pending");
            }
            return gate;
        }

        private void resetCounters() {
            if (activeDispatches.get() != 0 || nextGate.get() != null) {
                throw new IllegalStateException("Cannot reset active loopback counters");
            }
            dispatchSequence.set(0);
            dispatches.set(0);
            completedDispatches.set(0);
            cancelledDispatches.set(0);
        }

        @Override
        public void close() {
            server.disposeNow(AWAIT_TIMEOUT);
        }

        private static byte[] payload(int sequence) {
            byte[] payload = new byte[PAYLOAD_BYTES];
            Arrays.fill(payload, (byte) ('A' + Math.floorMod(sequence, 26)));
            return payload;
        }
    }

    private static final class ResponseGate {
        private final CountDownLatch dispatched = new CountDownLatch(1);
        private final Sinks.One<Void> release = Sinks.one();

        private void awaitDispatch() throws InterruptedException {
            if (!dispatched.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Timed out waiting for the gated loopback dispatch");
            }
        }

        private void release() {
            release.tryEmitEmpty().orThrow();
        }
    }
}
