package io.github.huynhngochuyhoang.httpstarter.core;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResponseCacheMemoryWorkloadTest {

    @Test
    void allScenariosExposeDeterministicBoundedStructuralEvidence() throws Exception {
        Recording recording = startOptionalRecording();
        List<ResponseCacheMemoryWorkload.Evidence> evidence;
        try {
            evidence = ResponseCacheMemoryWorkload.runAll();
        }
        finally {
            finishOptionalRecording(recording);
        }
        Map<ResponseCacheMemoryWorkload.Scenario, ResponseCacheMemoryWorkload.Evidence> byScenario =
                new EnumMap<>(ResponseCacheMemoryWorkload.Scenario.class);
        evidence.forEach(item -> byScenario.put(item.scenario(), item));

        assertThat(byScenario).containsOnlyKeys(ResponseCacheMemoryWorkload.Scenario.values());
        assertThat(evidence).allSatisfy(item -> {
            assertThat(item.definition().payloadShape()).isEqualTo("synthetic-byte-array");
            assertThat(item.definition().payloadBytes()).isEqualTo(ResponseCacheMemoryWorkload.PAYLOAD_BYTES);
            assertThat(item.definition().keyCardinality()).isEqualTo(ResponseCacheMemoryWorkload.KEY_CARDINALITY);
            assertThat(item.definition().concurrency()).isEqualTo(ResponseCacheMemoryWorkload.CONCURRENCY);
            assertThat(item.definition().warmupOperations()).isEqualTo(ResponseCacheMemoryWorkload.WARMUP_OPERATIONS);
            assertThat(item.definition().measuredOperations()).isEqualTo(ResponseCacheMemoryWorkload.MEASURED_OPERATIONS);
            assertThat(item.definition().cacheEnabled())
                    .isEqualTo(item.scenario() != ResponseCacheMemoryWorkload.Scenario.CACHE_DISABLED);
            assertThat(item.environment().javaVersion()).isNotBlank();
            assertThat(item.environment().javaVm()).isNotBlank();
            assertThat(item.environment().jvmFlags()).doesNotContain("\n", "\r");
            assertThat(item.environment().maximumHeapBytes()).isPositive();
            assertThat(item.environment().configuredDirectMemoryLimitBytes()).isGreaterThanOrEqualTo(-1);
            assertThat(item.environment().directMemoryLimitSource()).isNotBlank();
            assertThat(item.environment().containerMemoryLimitBytes()).isGreaterThanOrEqualTo(-1);
            assertThat(item.environment().osName()).isNotBlank();
            assertThat(item.environment().osVersion()).isNotBlank();
            assertThat(item.environment().osArchitecture()).isNotBlank();
            assertThat(item.environment().transport()).startsWith("io.netty.channel.");
            assertThat(item.environment().allocator()).contains("ByteBufAllocator");
            assertThat(item.environment().starterCommit()).isNotBlank();
            assertThat(item.checkpoints()).hasSizeBetween(3, 5);
            assertThat(item.checkpoints()).allSatisfy(checkpoint -> {
                assertThat(checkpoint.cache().inFlightLoads()).isBetween(0, 1);
                assertThat(checkpoint.cache().coalescedWaiters())
                        .isBetween(0, ResponseCacheMemoryWorkload.CONCURRENCY - 1);
                assertThat(checkpoint.cache().inFlightRefreshes()).isBetween(0, 1);
                assertThat(checkpoint.activeDispatches()).isBetween(0, 1);
                assertThat(checkpoint.connectionPool().registeredPools()).isGreaterThanOrEqualTo(0);
                assertThat(checkpoint.connectionPool().totalConnections()).isGreaterThanOrEqualTo(0);
                assertThat(checkpoint.connectionPool().activeConnections()).isGreaterThanOrEqualTo(0);
                assertThat(checkpoint.connectionPool().idleConnections()).isGreaterThanOrEqualTo(0);
                assertThat(checkpoint.connectionPool().pendingAcquires()).isGreaterThanOrEqualTo(0);
                assertThat(checkpoint.connectionPool().maximumConnections()).isEqualTo(1);
                assertThat(checkpoint.applicationPayload().allocations()).isGreaterThanOrEqualTo(0);
                assertThat(checkpoint.applicationPayload().allocatedBytes())
                        .isEqualTo(checkpoint.applicationPayload().allocations()
                                * ResponseCacheMemoryWorkload.PAYLOAD_BYTES);
                assertThat(checkpoint.memory().processRssBytes()).isGreaterThanOrEqualTo(-1);
                assertThat(checkpoint.memory().usedHeapBytes()).isGreaterThanOrEqualTo(0);
                assertThat(checkpoint.memory().committedHeapBytes())
                        .isGreaterThanOrEqualTo(checkpoint.memory().usedHeapBytes());
                assertThat(checkpoint.memory().maximumHeapBytes())
                        .isGreaterThanOrEqualTo(checkpoint.memory().committedHeapBytes());
                assertThat(checkpoint.memory().directBufferCount()).isGreaterThanOrEqualTo(-1);
                assertThat(checkpoint.memory().directBufferMemoryUsedBytes()).isGreaterThanOrEqualTo(-1);
                assertThat(checkpoint.memory().directBufferTotalCapacityBytes()).isGreaterThanOrEqualTo(-1);
                assertThat(checkpoint.memory().nettyAllocatorDirectMemoryUsedBytes()).isGreaterThanOrEqualTo(-1);
                assertThat(checkpoint.memory().liveThreadCount()).isPositive();
                assertThat(checkpoint.memory().daemonThreadCount()).isGreaterThanOrEqualTo(0);
                assertThat(checkpoint.memory().peakThreadCount())
                        .isGreaterThanOrEqualTo(checkpoint.memory().liveThreadCount());
            });
            ResponseCacheMemoryWorkload.Checkpoint baseline = item.checkpoint("baseline-after-explicit-gc");
            assertThat(baseline.memory().explicitGcRequested()).isTrue();
            assertThat(item.checkpoints().stream()
                    .filter(checkpoint -> checkpoint.memory().explicitGcRequested()))
                    .allSatisfy(checkpoint -> assertThat(checkpoint.name()).contains("explicit-gc"));
            ResponseCacheMemoryWorkload.Checkpoint closed = item.lastCheckpoint();
            assertThat(closed.measuredCallers())
                    .isEqualTo(ResponseCacheMemoryWorkload.MEASURED_OPERATIONS);
            assertThat(closed.name()).isEqualTo("fixture-closed");
            assertThat(closed.contextsCreated()).isEqualTo(1);
            assertThat(closed.contextsClosed()).isEqualTo(1);
            assertThat(closed.factoriesCreated()).isEqualTo(1);
            assertThat(closed.factoriesClosed()).isEqualTo(1);
            assertThat(closed.serversStarted()).isEqualTo(1);
            assertThat(closed.serversStopped()).isEqualTo(1);
            assertThat(closed.cache().cache().closed()).isTrue();
            assertThat(closed.cache().cache().currentSize()).isZero();
            assertThat(closed.cache().inFlightLoads()).isZero();
            assertThat(closed.cache().coalescedWaiters()).isZero();
            assertThat(closed.cache().inFlightRefreshes()).isZero();
            assertThat(closed.connectionPool().disposed()).isTrue();
            assertThat(closed.connectionPool().registeredPools()).isZero();
            assertThat(closed.connectionPool().totalConnections()).isZero();
        });

        assertThat(checkpoint(byScenario, ResponseCacheMemoryWorkload.Scenario.CACHE_DISABLED,
                "after-operations").serverDispatches()).isEqualTo(ResponseCacheMemoryWorkload.MEASURED_OPERATIONS);
        assertThat(checkpoint(byScenario, ResponseCacheMemoryWorkload.Scenario.CACHE_DISABLED,
                "after-operations").cache().cache().policyCount()).isZero();

        assertThat(checkpoint(byScenario, ResponseCacheMemoryWorkload.Scenario.COLD_MISS,
                "after-cold-misses").cache().cache().currentSize())
                .isEqualTo(ResponseCacheMemoryWorkload.KEY_CARDINALITY);
        assertThat(checkpoint(byScenario, ResponseCacheMemoryWorkload.Scenario.WARM_HIT,
                "after-warm-hits").serverDispatches())
                .isEqualTo(ResponseCacheMemoryWorkload.KEY_CARDINALITY);

        ResponseCacheMemoryWorkload.Checkpoint pressure = checkpoint(
                byScenario, ResponseCacheMemoryWorkload.Scenario.MAXIMUM_SIZE_PRESSURE,
                "after-capacity-pressure");
        assertThat(pressure.cache().cache().currentSize())
                .isEqualTo(ResponseCacheMemoryWorkload.PRESSURE_MAXIMUM_SIZE);
        assertThat(pressure.cache().cache().evictions())
                .isEqualTo(ResponseCacheMemoryWorkload.KEY_CARDINALITY
                        - ResponseCacheMemoryWorkload.PRESSURE_MAXIMUM_SIZE);

        assertThat(checkpoint(byScenario, ResponseCacheMemoryWorkload.Scenario.TTL_EXPIRY,
                "after-expiry").cache().cache().currentSize()).isZero();
        assertThat(checkpoint(byScenario, ResponseCacheMemoryWorkload.Scenario.EXPLICIT_EVICTION,
                "after-eviction").cache().cache().currentSize()).isZero();

        ResponseCacheMemoryWorkload.Checkpoint duplicateGated = checkpoint(
                byScenario, ResponseCacheMemoryWorkload.Scenario.DUPLICATE_MISS, "duplicate-loads-gated");
        assertThat(duplicateGated.serverDispatches()).isEqualTo(1);
        assertThat(duplicateGated.loadSubscriptions()).isEqualTo(ResponseCacheMemoryWorkload.CONCURRENCY);
        assertThat(duplicateGated.cache().inFlightLoads()).isZero();
        ResponseCacheMemoryWorkload.Checkpoint duplicateComplete = checkpoint(
                byScenario, ResponseCacheMemoryWorkload.Scenario.DUPLICATE_MISS, "after-duplicate-misses");
        assertThat(duplicateComplete.serverDispatches()).isEqualTo(ResponseCacheMemoryWorkload.CONCURRENCY);
        assertThat(duplicateComplete.cache().cache().currentSize()).isEqualTo(1);

        ResponseCacheMemoryWorkload.Checkpoint shared = checkpoint(
                byScenario, ResponseCacheMemoryWorkload.Scenario.SINGLE_FLIGHT, "load-gated");
        assertThat(shared.serverDispatches()).isEqualTo(1);
        assertThat(shared.loadSubscriptions()).isEqualTo(1);
        assertThat(shared.cache().inFlightLoads()).isEqualTo(1);
        assertThat(shared.cache().coalescedWaiters())
                .isEqualTo(ResponseCacheMemoryWorkload.CONCURRENCY - 1);

        ResponseCacheMemoryWorkload.Checkpoint refresh = checkpoint(
                byScenario, ResponseCacheMemoryWorkload.Scenario.REFRESH, "refresh-gated");
        assertThat(refresh.cache().inFlightRefreshes()).isEqualTo(1);
        assertThat(refresh.serverDispatches()).isEqualTo(2);

        ResponseCacheMemoryWorkload.Checkpoint cancelled = checkpoint(
                byScenario, ResponseCacheMemoryWorkload.Scenario.CANCELLATION, "after-cancellation");
        assertThat(cancelled.loadCancellations()).isEqualTo(1);
        assertThat(cancelled.cache().inFlightLoads()).isZero();
        assertThat(cancelled.cache().cache().currentSize()).isZero();

        ResponseCacheMemoryWorkload.Checkpoint factoryClosed = checkpoint(
                byScenario, ResponseCacheMemoryWorkload.Scenario.FACTORY_CLOSE, "after-factory-close");
        assertThat(factoryClosed.factoriesClosed()).isEqualTo(1);
        assertThat(factoryClosed.loadCancellations()).isEqualTo(1);
        assertThat(factoryClosed.cache().cache().closed()).isTrue();
        assertThat(factoryClosed.cache().cache().currentSize()).isZero();

        Path report = projectRoot().resolve(
                "target/release-evidence/v29/priority2/deterministic-workload.properties");
        Files.createDirectories(report.getParent());
        Files.writeString(report, ResponseCacheMemoryWorkload.render(evidence));
        assertThat(Files.readString(report))
                .contains("format=v29-response-cache-memory-workload-v2")
                .contains("scenario.single-flight.jvmFlags=")
                .contains("scenario.single-flight.checkpoint.1.processRssBytes=")
                .contains("scenario.single-flight.checkpoint.1.usedHeapBytes=")
                .contains("scenario.single-flight.checkpoint.1.committedHeapBytes=")
                .contains("scenario.single-flight.checkpoint.1.directBufferMemoryUsedBytes=")
                .contains("scenario.single-flight.checkpoint.1.nettyAllocatorDirectMemoryUsedBytes=")
                .contains("scenario.single-flight.checkpoint.1.cacheEntries=")
                .contains("scenario.single-flight.checkpoint.1.poolTotalConnections=")
                .contains("scenario.single-flight.checkpoint.1.applicationPayloadAllocatedBytes=")
                .contains("scenario.single-flight.checkpoint.1.coalescedWaiters=7")
                .doesNotContain("http://")
                .doesNotContain("Authorization")
                .doesNotContain("synthetic-key-");
    }

    @Test
    void explicitGcRequiresANamedDiagnosticCheckpoint() {
        assertThatThrownBy(() -> ResponseCacheMemoryDomains.capture("ordinary-checkpoint", true, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explicit-gc");

        assertThat(ResponseCacheMemoryDomains.capture("diagnostic-explicit-gc", false, 0)
                .explicitGcRequested()).isFalse();
    }

    @Test
    void characterizationAddsGcStableReleaseAndCloseCheckpoints() throws Exception {
        ResponseCacheMemoryWorkload.Evidence evidence = ResponseCacheMemoryWorkload.runCharacterization(
                ResponseCacheMemoryWorkload.Scenario.TTL_EXPIRY);

        assertThat(evidence.checkpoint("expired-after-explicit-gc").cache().cache().currentSize()).isZero();
        assertThat(evidence.checkpoint("steady-after-explicit-gc").cache().cache().currentSize())
                .isEqualTo(ResponseCacheMemoryWorkload.KEY_CARDINALITY);
        ResponseCacheMemoryWorkload.Checkpoint closed =
                evidence.checkpoint("fixture-closed-after-explicit-gc");
        assertThat(closed.memory().explicitGcRequested()).isTrue();
        assertThat(closed.cache().cache().currentSize()).isZero();
        assertThat(closed.cache().inFlightLoads()).isZero();
        assertThat(closed.cache().inFlightRefreshes()).isZero();
        assertThat(closed.connectionPool().totalConnections()).isZero();
    }

    private static Recording startOptionalRecording() throws Exception {
        String configuredPath = System.getProperty("v29.memory.jfr");
        if (configuredPath == null) {
            return null;
        }
        Path recordingPath = Path.of(configuredPath).toAbsolutePath().normalize();
        Path target = projectRoot().resolve("target").toAbsolutePath().normalize();
        if (!recordingPath.startsWith(target)) {
            throw new IllegalArgumentException("V29 profiling output must remain under target");
        }
        Files.createDirectories(recordingPath.getParent());
        Recording recording = new Recording(Configuration.getConfiguration("profile"));
        recording.setMaxSize(64L * 1024 * 1024);
        recording.setToDisk(true);
        recording.start();
        return recording;
    }

    private static void finishOptionalRecording(Recording recording) throws Exception {
        if (recording == null) {
            return;
        }
        try (recording) {
            recording.stop();
            recording.dump(Path.of(System.getProperty("v29.memory.jfr")));
        }
    }

    private static ResponseCacheMemoryWorkload.Checkpoint checkpoint(
            Map<ResponseCacheMemoryWorkload.Scenario, ResponseCacheMemoryWorkload.Evidence> evidence,
            ResponseCacheMemoryWorkload.Scenario scenario,
            String name) {
        return evidence.get(scenario).checkpoint(name);
    }

    private static Path projectRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        return Files.exists(cwd.resolve("README.md")) ? cwd : cwd.getParent();
    }
}
