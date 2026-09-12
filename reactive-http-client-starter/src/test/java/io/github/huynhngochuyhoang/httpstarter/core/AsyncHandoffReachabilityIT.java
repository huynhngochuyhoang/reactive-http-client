package io.github.huynhngochuyhoang.httpstarter.core;

import com.sun.management.HotSpotDiagnosticMXBean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.management.ManagementFactory;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in via -Pv31-handoff-reachability; deliberately outside Surefire's default test-name patterns. */
class AsyncHandoffReachabilityIT {

    @BeforeAll
    static void requireControlledCollector() {
        var vm = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        assertThat(vm.getVMOption("UseSerialGC").getValue()).as("reachability lane requires Serial GC").isEqualTo("true");
        assertThat(vm.getVMOption("DisableExplicitGC").getValue()).as("reachability lane requires explicit GC").isEqualTo("false");
        assertThat(Runtime.getRuntime().maxMemory()).as("reachability lane uses a bounded test heap")
                .isLessThanOrEqualTo(128L * 1024 * 1024);
    }

    @ParameterizedTest
    @EnumSource(AsyncHandoffOwnershipContractTest.Terminal.class)
    void terminalStateBecomesUnreachableAfterExternalOwnersReleaseIt(AsyncHandoffOwnershipContractTest.Terminal terminal) throws Exception {
        AsyncHandoffOwnershipContractTest.probeTerminalReachability(terminal);
    }

    @Test
    void retainedSnapshotDoesNotKeepTheOriginalRequestGraphReachable() throws InterruptedException {
        AsyncHandoffOwnershipContractTest.probeSourceReachability();
    }

    static void awaitCollected(ReferenceQueue<Object> queue, List<WeakReference<Object>> references) throws InterruptedException {
        // This bounded diagnostic runs only with the verified collector, not as a general unit-test GC guarantee.
        for (int attempt = 0; attempt < 100; attempt++) {
            if (references.stream().allMatch(reference -> reference.get() == null)) {
                while (queue.poll() != null) { /* drain diagnostic references */ }
                return;
            }
            System.gc();
            queue.remove(100);
        }
        assertThat(references).allSatisfy(reference -> assertThat(reference.get()).isNull());
    }
}
