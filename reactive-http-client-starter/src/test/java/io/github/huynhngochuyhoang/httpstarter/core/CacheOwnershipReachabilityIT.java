package io.github.huynhngochuyhoang.httpstarter.core;

import com.sun.management.HotSpotDiagnosticMXBean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.management.ManagementFactory;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in via -Pv32-cache-reachability; ordinary suites exercise the same cleanup without requesting GC. */
@Timeout(30)
class CacheOwnershipReachabilityIT {

    @BeforeAll
    static void requireControlledCollector() {
        var vm = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        assertThat(vm.getVMOption("UseSerialGC").getValue()).as("reachability lane requires Serial GC").isEqualTo("true");
        assertThat(vm.getVMOption("DisableExplicitGC").getValue()).as("reachability lane requires explicit GC").isEqualTo("false");
        assertThat(Runtime.getRuntime().maxMemory()).as("reachability lane uses a bounded test heap")
                .isLessThanOrEqualTo(128L * 1024 * 1024);
    }

    @Test
    void terminalOutcomesReleaseTransientOwnersWhileTheManagerRemainsOpen() throws Exception {
        new ResponseCacheRetentionOwnershipTest().terminalOutcomesReleaseTransientOwnersWhileTheManagerRemainsOpen(true);
    }

    @Test
    void expiryCapacityAndRefreshTransitionsReleaseDisplacedOwners() throws Exception {
        new ResponseCacheRetentionOwnershipTest().expiryCapacityAndRefreshTransitionsReleaseDisplacedOwners(true);
    }

    @Test
    void independentLoadRemainsCallerOwnedAfterManagerCloseAndReleasesAtCallerTerminal() throws Exception {
        new ResponseCacheRetentionOwnershipTest().independentLoadRemainsCallerOwnedAfterManagerCloseAndReleasesAtCallerTerminal(true);
    }

    @Test
    void detachedWaiterReleasesItsArgumentsContextAndStateBeforeTheLeaderEnds() throws Exception {
        new ResponseCacheRetentionOwnershipTest().detachedWaiterReleasesItsArgumentsContextAndStateBeforeTheLeaderEnds(true);
    }

    @Test
    void detachedLeaderReleasesItsCallerStateWhileAWaiterKeepsTheLoadAlive() throws Exception {
        new ResponseCacheRetentionOwnershipTest().detachedLeaderReleasesItsCallerStateWhileAWaiterKeepsTheLoadAlive(true);
    }

    @Test
    void hiddenRefreshTerminalPathsReleaseTheirCapturedState() throws Exception {
        new ResponseCacheRetentionOwnershipTest().hiddenRefreshTerminalPathsReleaseTheirCapturedState(true);
    }

    @Test
    void preparedBodyAuthContextFrozenArgumentsAndResponseMetadataEndAtPublication() throws Exception {
        new ResponseCacheRetentionOwnershipTest().preparedBodyAuthContextFrozenArgumentsAndResponseMetadataEndAtPublication(true);
    }

    @Test
    void closeRemovesMeterRootsAndRejectsLatePublication() throws Exception {
        new ResponseCacheRetentionOwnershipTest().closeRemovesMeterRootsAndRejectsLatePublication(true);
    }

    @Test
    void retainedDiagnosticsMapDoesNotOwnFactoryManagerCacheOrValue() throws Exception {
        new ResponseCacheRetentionOwnershipTest().retainedDiagnosticsMapDoesNotOwnFactoryManagerCacheOrValue(true);
    }

    @Test
    void rejectedAndSkippedClosuresCollectWhileAdmittedOwnersRemainAlive() throws Exception {
        new CacheWorkOwnershipContractTest().rejectedAndSkippedClosuresCollectWhileAdmittedOwnersRemainAlive(true);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void detachedCallerCollectsWhileSourceRetainsItsOwnState(boolean detachLeader) throws Exception {
        new CacheWorkOwnershipContractTest().detachedCallerCollectsWhileSourceRetainsItsOwnState(detachLeader, true);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void evictionReleasesValuesBeforeCloseWithoutReleasingRunningLoad(boolean single) throws Exception {
        new CacheWorkOwnershipContractTest().evictionReleasesValuesBeforeCloseWithoutReleasingRunningLoad(single, true);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void terminalPreparationReleasesArgumentsContextAndAuthWhileManagerStaysOpen(boolean success) throws Exception {
        new CacheCallerAdmissionContractTest()
                .terminalPreparationReleasesArgumentsContextAndAuthWhileManagerStaysOpen(success, true);
    }

    static void assertCollected(ReferenceQueue<Object> queue, List<? extends WeakReference<?>> references) {
        assertCollectedCount(queue, references, references.size());
    }

    static void assertRetained(WeakReference<?> reference) {
        for (int attempt = 0; attempt < 3; attempt++) { System.gc(); }
        assertThat(reference.get()).as("active owner survives controlled collection").isNotNull();
    }

    static void assertCollectedCount(ReferenceQueue<Object> queue,
                                    List<? extends WeakReference<?>> references, int expectedCollected) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        do {
            if (queue != null) {
                while (queue.poll() != null) { /* drain diagnostic references */ }
            }
            if (references.stream().filter(reference -> reference.get() == null).count() >= expectedCollected) {
                return;
            }
            System.gc();
            try {
                Thread.sleep(25);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while awaiting reference release", error);
            }
        } while (System.nanoTime() < deadline);
        assertThat(references.stream().filter(reference -> reference.get() == null).count())
                .as("released references in controlled JVM: %s", references)
                .isGreaterThanOrEqualTo(expectedCollected);
    }
}
