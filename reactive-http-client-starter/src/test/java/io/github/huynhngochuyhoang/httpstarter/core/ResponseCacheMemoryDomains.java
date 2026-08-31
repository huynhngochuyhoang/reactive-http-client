package io.github.huynhngochuyhoang.httpstarter.core;

import com.sun.management.HotSpotDiagnosticMXBean;

import java.io.IOException;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

final class ResponseCacheMemoryDomains {

    static final long UNAVAILABLE = -1;
    private static final Duration GC_OBSERVATION_TIMEOUT = Duration.ofSeconds(1);
    private static final List<String> RELEVANT_JVM_ARGUMENT_PREFIXES = List.of(
            "-Xms",
            "-Xmx",
            "-Xss",
            "-XX:MaxDirectMemorySize=",
            "-XX:InitialRAMPercentage=",
            "-XX:MaxRAMPercentage=",
            "-XX:MinRAMPercentage=",
            "-XX:+Use",
            "-XX:-Use",
            "-XX:+AlwaysPreTouch",
            "-XX:+DisableExplicitGC",
            "-XX:NativeMemoryTracking=",
            "-XX:HeapDumpPath=");
    private static RepositoryState cachedRepositoryState;

    private ResponseCacheMemoryDomains() {
    }

    static Environment environment(Path projectRoot, String transport, String allocator) {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        OptionalLong configuredDirectLimit = configuredDirectMemoryLimit();
        RepositoryState repository = repositoryState(projectRoot);
        return new Environment(
                System.getProperty("java.version", "unknown"),
                System.getProperty("java.vm.name", "unknown"),
                relevantJvmArguments(),
                heap.getMax(),
                configuredDirectLimit.orElse(UNAVAILABLE),
                configuredDirectLimit.isPresent() ? "explicit-jvm-flag" : "ergonomic-or-unavailable",
                containerMemoryLimit(),
                System.getProperty("os.name", "unknown"),
                System.getProperty("os.version", "unknown"),
                System.getProperty("os.arch", "unknown"),
                transport,
                allocator,
                repository.commit(),
                repository.dirty());
    }

    static Snapshot capture(
            String checkpointName,
            boolean requestExplicitGc,
            long nettyAllocatorDirectMemoryUsedBytes) {
        if (requestExplicitGc && !checkpointName.contains("explicit-gc")) {
            throw new IllegalArgumentException(
                    "Explicit GC is restricted to checkpoints named with 'explicit-gc'");
        }

        long collectionsBefore = collectionCount();
        if (requestExplicitGc) {
            ManagementFactory.getMemoryMXBean().gc();
            awaitCollectionAfter(collectionsBefore);
        }

        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        BufferPoolMXBean direct = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class).stream()
                .filter(pool -> "direct".equals(pool.getName()))
                .findFirst()
                .orElse(null);
        var threads = ManagementFactory.getThreadMXBean();
        return new Snapshot(
                requestExplicitGc,
                requestExplicitGc && collectionCount() > collectionsBefore,
                processRssBytes(),
                heap.getUsed(),
                heap.getCommitted(),
                heap.getMax(),
                direct != null ? direct.getCount() : UNAVAILABLE,
                direct != null ? direct.getMemoryUsed() : UNAVAILABLE,
                direct != null ? direct.getTotalCapacity() : UNAVAILABLE,
                nettyAllocatorDirectMemoryUsedBytes,
                threads.getThreadCount(),
                threads.getDaemonThreadCount(),
                threads.getPeakThreadCount());
    }

    private static void awaitCollectionAfter(long collectionsBefore) {
        if (collectionsBefore == UNAVAILABLE) {
            return;
        }
        long deadline = System.nanoTime() + GC_OBSERVATION_TIMEOUT.toNanos();
        while (collectionCount() <= collectionsBefore && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }

    private static long collectionCount() {
        long total = 0;
        boolean available = false;
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = collector.getCollectionCount();
            if (count >= 0) {
                available = true;
                total += count;
            }
        }
        return available ? total : UNAVAILABLE;
    }

    private static String relevantJvmArguments() {
        String arguments = ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(argument -> RELEVANT_JVM_ARGUMENT_PREFIXES.stream().anyMatch(argument::startsWith))
                .map(ResponseCacheMemoryDomains::sanitizeJvmArgument)
                .reduce((left, right) -> left + "," + right)
                .orElse("none");
        return oneLine(arguments);
    }

    private static String sanitizeJvmArgument(String argument) {
        return argument.startsWith("-XX:HeapDumpPath=")
                ? "-XX:HeapDumpPath=<target-only>"
                : argument;
    }

    private static OptionalLong configuredDirectMemoryLimit() {
        try {
            HotSpotDiagnosticMXBean bean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
            if (bean == null) {
                return OptionalLong.empty();
            }
            long value = Long.parseLong(bean.getVMOption("MaxDirectMemorySize").getValue());
            return value > 0 ? OptionalLong.of(value) : OptionalLong.empty();
        }
        catch (RuntimeException ignored) {
            return OptionalLong.empty();
        }
    }

    private static long processRssBytes() {
        Path status = Path.of("/proc/self/status");
        if (!Files.isRegularFile(status)) {
            return UNAVAILABLE;
        }
        try {
            for (String line : Files.readAllLines(status)) {
                if (line.startsWith("VmRSS:")) {
                    String value = line.substring("VmRSS:".length()).trim();
                    String[] parts = value.split("\\s+");
                    return Math.multiplyExact(Long.parseLong(parts[0]), 1024L);
                }
            }
        }
        catch (IOException | ArithmeticException | NumberFormatException ignored) {
            return UNAVAILABLE;
        }
        return UNAVAILABLE;
    }

    private static long containerMemoryLimit() {
        for (Path candidate : List.of(
                Path.of("/sys/fs/cgroup/memory.max"),
                Path.of("/sys/fs/cgroup/memory/memory.limit_in_bytes"))) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try {
                String value = Files.readString(candidate).trim();
                if (value.equals("max")) {
                    return UNAVAILABLE;
                }
                long limit = Long.parseLong(value);
                return limit > 0 && limit < Long.MAX_VALUE / 2 ? limit : UNAVAILABLE;
            }
            catch (IOException | NumberFormatException ignored) {
                // Try the next known cgroup location.
            }
        }
        return UNAVAILABLE;
    }

    private static synchronized RepositoryState repositoryState(Path projectRoot) {
        if (cachedRepositoryState == null) {
            String suppliedCommit = System.getProperty("v29.starter.commit");
            String suppliedDirty = System.getProperty("v29.starter.dirty");
            if (suppliedCommit != null && suppliedDirty != null) {
                cachedRepositoryState = new RepositoryState(
                        oneLine(suppliedCommit), Boolean.parseBoolean(suppliedDirty));
                return cachedRepositoryState;
            }
            String commit = command(projectRoot, "git", "rev-parse", "--verify", "HEAD");
            String status = command(projectRoot, "git", "status", "--porcelain", "--untracked-files=normal");
            cachedRepositoryState = new RepositoryState(
                    commit != null ? commit : "unavailable", status == null || !status.isBlank());
        }
        return cachedRepositoryState;
    }

    private static String command(Path directory, String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .directory(directory.toFile())
                    .redirectErrorStream(true)
                    .start();
            byte[] output = process.getInputStream().readNBytes(16 * 1024);
            if (!process.waitFor(2, TimeUnit.SECONDS) || process.exitValue() != 0) {
                return null;
            }
            return oneLine(new String(output, java.nio.charset.StandardCharsets.UTF_8).trim());
        }
        catch (IOException | InterruptedException ignored) {
            if (ignored instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
        finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static String oneLine(String value) {
        return value.replace('\n', ' ').replace('\r', ' ');
    }

    record Environment(
            String javaVersion,
            String javaVm,
            String jvmFlags,
            long maximumHeapBytes,
            long configuredDirectMemoryLimitBytes,
            String directMemoryLimitSource,
            long containerMemoryLimitBytes,
            String osName,
            String osVersion,
            String osArchitecture,
            String transport,
            String allocator,
            String starterCommit,
            boolean sourceTreeDirty) {
    }

    record Snapshot(
            boolean explicitGcRequested,
            boolean explicitGcObserved,
            long processRssBytes,
            long usedHeapBytes,
            long committedHeapBytes,
            long maximumHeapBytes,
            long directBufferCount,
            long directBufferMemoryUsedBytes,
            long directBufferTotalCapacityBytes,
            long nettyAllocatorDirectMemoryUsedBytes,
            int liveThreadCount,
            int daemonThreadCount,
            int peakThreadCount) {
    }

    private record RepositoryState(String commit, boolean dirty) {
    }
}
