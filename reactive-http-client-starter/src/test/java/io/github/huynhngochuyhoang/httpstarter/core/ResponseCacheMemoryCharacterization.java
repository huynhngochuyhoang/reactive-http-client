package io.github.huynhngochuyhoang.httpstarter.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

public final class ResponseCacheMemoryCharacterization {

    private static final Duration CHILD_TIMEOUT = Duration.ofSeconds(45);
    private static final String SAMPLE_HEADER = String.join("\t",
            "scenario", "repetition",
            "baselineHeapBytes", "steadyHeapBytes", "releaseHeapBytes", "closedHeapBytes",
            "baselineRssBytes", "steadyRssBytes", "releaseRssBytes", "closedRssBytes",
            "baselineJdkDirectBytes", "steadyJdkDirectBytes", "closedJdkDirectBytes",
            "baselineNettyDirectBytes", "steadyNettyDirectBytes", "closedNettyDirectBytes",
            "baselineThreads", "steadyThreads", "closedThreads",
            "steadyEntries", "releaseEntries", "closedEntries",
            "steadyInFlightLoads", "steadyInFlightRefreshes",
            "closedInFlightLoads", "closedInFlightRefreshes", "closedPoolConnections",
            "payloadAllocatedBytes", "serverDispatches", "loadSubscriptions", "cacheEvictions");

    private ResponseCacheMemoryCharacterization() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--sample".equals(args[0])) {
            runSample(args);
            return;
        }
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected <target-output-directory> <repetitions>");
        }
        runFreshJvmCharacterization(verifiedTargetPath(Path.of(args[0])), Integer.parseInt(args[1]));
    }

    private static void runSample(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException("Expected --sample <scenario> <repetition> <output-file>");
        }
        ResponseCacheMemoryWorkload.Scenario scenario = ResponseCacheMemoryWorkload.Scenario.valueOf(args[1]);
        int repetition = Integer.parseInt(args[2]);
        Path output = verifiedTargetPath(Path.of(args[3]));
        Files.createDirectories(output.getParent());
        ResponseCacheMemoryWorkload.Evidence evidence =
                ResponseCacheMemoryWorkload.runCharacterization(scenario);
        Files.writeString(output, ResponseCacheMemoryWorkload.render(List.of(evidence))
                + "repetition=" + repetition + "\n");
    }

    private static void runFreshJvmCharacterization(Path outputDirectory, int repetitions) throws Exception {
        if (repetitions < 3) {
            throw new IllegalArgumentException("At least three fresh-JVM repetitions are required");
        }
        Path rawDirectory = outputDirectory.resolve("raw");
        Path logDirectory = outputDirectory.resolve("logs");
        Files.createDirectories(rawDirectory);
        Files.createDirectories(logDirectory);

        List<Sample> samples = new ArrayList<>();
        for (ResponseCacheMemoryWorkload.Scenario scenario : ResponseCacheMemoryWorkload.Scenario.values()) {
            for (int repetition = 1; repetition <= repetitions; repetition++) {
                Path rawFile = rawDirectory.resolve(scenario.id() + "-" + repetition + ".properties");
                Path logFile = logDirectory.resolve(scenario.id() + "-" + repetition + ".log");
                Files.deleteIfExists(rawFile);
                Files.deleteIfExists(logFile);
                runChild(scenario, repetition, rawFile, logFile);
                Sample sample = Sample.read(rawFile, scenario, repetition);
                verifyStructuralContract(sample);
                samples.add(sample);
                System.err.printf(Locale.ROOT, "completed %s repetition %d/%d%n",
                        scenario.id(), repetition, repetitions);
            }
        }

        Files.writeString(outputDirectory.resolve("samples.tsv"), renderSamples(samples));
        Files.writeString(outputDirectory.resolve("summary.tsv"), renderSummary(samples));
        Files.writeString(outputDirectory.resolve("run.properties"), renderRunMetadata(samples, repetitions));
    }

    private static void runChild(
            ResponseCacheMemoryWorkload.Scenario scenario,
            int repetition,
            Path rawFile,
            Path logFile) throws Exception {
        List<String> command = new ArrayList<>(List.of(
                javaExecutable(),
                "-Xms128m",
                "-Xmx128m",
                "-XX:MaxDirectMemorySize=64m",
                "-XX:+UseG1GC"));
        addProperty(command, "v29.starter.commit");
        addProperty(command, "v29.starter.dirty");
        command.addAll(List.of(
                "-cp", System.getProperty("java.class.path"),
                ResponseCacheMemoryCharacterization.class.getName(),
                "--sample", scenario.name(), Integer.toString(repetition), rawFile.toString()));

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile())
                .start();
        if (!process.waitFor(CHILD_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Timed out running " + scenario.id()
                    + " repetition " + repetition + "; see " + logFile);
        }
        if (process.exitValue() != 0 || !Files.isRegularFile(rawFile)) {
            throw new IllegalStateException("Fresh JVM failed for " + scenario.id()
                    + " repetition " + repetition + "; see " + logFile);
        }
    }

    private static void addProperty(List<String> command, String name) {
        String value = System.getProperty(name);
        if (value != null) {
            command.add("-D" + name + "=" + value);
        }
    }

    private static void verifyStructuralContract(Sample sample) {
        require(sample.closedEntries == 0, sample, "closed cache entries");
        require(sample.closedInFlightLoads == 0, sample, "closed in-flight loads");
        require(sample.closedInFlightRefreshes == 0, sample, "closed refreshes");
        require(sample.closedPoolConnections == 0, sample, "closed pool connections");
        require(sample.baselineHeapBytes > 0 && sample.steadyHeapBytes > 0 && sample.closedHeapBytes > 0,
                sample, "heap checkpoints");

        switch (sample.scenario) {
            case CACHE_DISABLED -> require(sample.steadyEntries == 0, sample, "disabled cache entries");
            case MAXIMUM_SIZE_PRESSURE -> require(
                    sample.steadyEntries == ResponseCacheMemoryWorkload.PRESSURE_MAXIMUM_SIZE,
                    sample, "maximum-size plateau");
            case TTL_EXPIRY, EXPLICIT_EVICTION -> require(
                    sample.releaseEntries == 0, sample, "release checkpoint entries");
            case DUPLICATE_MISS -> {
                require(sample.loadSubscriptions == ResponseCacheMemoryWorkload.CONCURRENCY,
                        sample, "duplicate miss subscriptions");
                require(sample.serverDispatches == ResponseCacheMemoryWorkload.CONCURRENCY,
                        sample, "duplicate miss dispatches");
            }
            default -> {
                // The common close checks above are the structural contract for this scenario.
            }
        }
    }

    private static void require(boolean condition, Sample sample, String description) {
        if (!condition) {
            throw new IllegalStateException("Invalid " + description + " for "
                    + sample.scenario.id() + " repetition " + sample.repetition);
        }
    }

    private static String renderSamples(List<Sample> samples) {
        StringBuilder output = new StringBuilder(SAMPLE_HEADER).append('\n');
        samples.forEach(sample -> output.append(sample.toTsv()).append('\n'));
        return output.toString();
    }

    private static String renderSummary(List<Sample> samples) {
        List<Sample> control = samples.stream()
                .filter(sample -> sample.scenario == ResponseCacheMemoryWorkload.Scenario.CACHE_DISABLED)
                .toList();
        long controlClosedHeap = mean(control, sample -> sample.closedHeapBytes);
        long controlClosedRss = mean(control, sample -> sample.closedRssBytes);
        StringBuilder output = new StringBuilder(String.join("\t",
                "scenario", "samples", "baselineHeapMean", "steadyHeapMean", "steadyHeapDeltaMean",
                "releaseHeapMean", "closedHeapMean", "closedVsControlHeapMean",
                "baselineRssMean", "steadyRssMean", "closedRssMean", "closedVsControlRssMean",
                "baselineNettyDirectMean", "steadyNettyDirectMean", "closedNettyDirectMean",
                "steadyEntriesMin", "steadyEntriesMax", "releaseEntriesMax", "closedEntriesMax",
                "closedLoadsMax", "closedRefreshesMax", "closedPoolConnectionsMax",
                "payloadAllocatedMean")).append('\n');
        for (ResponseCacheMemoryWorkload.Scenario scenario : ResponseCacheMemoryWorkload.Scenario.values()) {
            List<Sample> group = samples.stream().filter(sample -> sample.scenario == scenario).toList();
            long baselineHeap = mean(group, sample -> sample.baselineHeapBytes);
            long steadyHeap = mean(group, sample -> sample.steadyHeapBytes);
            long closedHeap = mean(group, sample -> sample.closedHeapBytes);
            long baselineRss = mean(group, sample -> sample.baselineRssBytes);
            long steadyRss = mean(group, sample -> sample.steadyRssBytes);
            long closedRss = mean(group, sample -> sample.closedRssBytes);
            output.append(scenario.id()).append('\t').append(group.size())
                    .append('\t').append(baselineHeap)
                    .append('\t').append(steadyHeap)
                    .append('\t').append(steadyHeap - baselineHeap)
                    .append('\t').append(mean(group, sample -> sample.releaseHeapBytes))
                    .append('\t').append(closedHeap)
                    .append('\t').append(difference(closedHeap, controlClosedHeap))
                    .append('\t').append(baselineRss)
                    .append('\t').append(steadyRss)
                    .append('\t').append(closedRss)
                    .append('\t').append(difference(closedRss, controlClosedRss))
                    .append('\t').append(mean(group, sample -> sample.baselineNettyDirectBytes))
                    .append('\t').append(mean(group, sample -> sample.steadyNettyDirectBytes))
                    .append('\t').append(mean(group, sample -> sample.closedNettyDirectBytes))
                    .append('\t').append(min(group, sample -> sample.steadyEntries))
                    .append('\t').append(max(group, sample -> sample.steadyEntries))
                    .append('\t').append(max(group, sample -> sample.releaseEntries))
                    .append('\t').append(max(group, sample -> sample.closedEntries))
                    .append('\t').append(max(group, sample -> sample.closedInFlightLoads))
                    .append('\t').append(max(group, sample -> sample.closedInFlightRefreshes))
                    .append('\t').append(max(group, sample -> sample.closedPoolConnections))
                    .append('\t').append(mean(group, sample -> sample.payloadAllocatedBytes))
                    .append('\n');
        }
        return output.toString();
    }

    private static String renderRunMetadata(List<Sample> samples, int repetitions) {
        return "format=v29-response-cache-memory-characterization-v1\n"
                + "freshJvmPerSample=true\n"
                + "repetitionsPerScenario=" + repetitions + "\n"
                + "scenarioCount=" + ResponseCacheMemoryWorkload.Scenario.values().length + "\n"
                + "sampleCount=" + samples.size() + "\n"
                + "jvmFlags=-Xms128m,-Xmx128m,-XX:MaxDirectMemorySize=64m,-XX:+UseG1GC\n"
                + "starterCommit=" + System.getProperty("v29.starter.commit", "unavailable") + "\n"
                + "sourceTreeDirty=" + System.getProperty("v29.starter.dirty", "unknown") + "\n";
    }

    private static long difference(long value, long baseline) {
        return value < 0 || baseline < 0 ? ResponseCacheMemoryDomains.UNAVAILABLE : value - baseline;
    }

    private static long mean(List<Sample> samples, LongValue value) {
        long sum = 0;
        int count = 0;
        for (Sample sample : samples) {
            long current = value.get(sample);
            if (current >= 0) {
                sum += current;
                count++;
            }
        }
        return count == 0 ? ResponseCacheMemoryDomains.UNAVAILABLE : Math.round((double) sum / count);
    }

    private static long min(List<Sample> samples, LongValue value) {
        return samples.stream().mapToLong(value::get).min().orElseThrow();
    }

    private static long max(List<Sample> samples, LongValue value) {
        return samples.stream().mapToLong(value::get).max().orElseThrow();
    }

    private static Path verifiedTargetPath(Path path) throws IOException {
        Path requested = path.toAbsolutePath().normalize();
        Path target = projectRoot().resolve("target").toRealPath();
        if (!requested.startsWith(target)) {
            throw new IllegalArgumentException("Memory characterization output must remain under target");
        }
        return requested;
    }

    private static Path projectRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        return Files.exists(cwd.resolve("README.md")) ? cwd : cwd.getParent();
    }

    private static String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    @FunctionalInterface
    private interface LongValue {
        long get(Sample sample);
    }

    private record Sample(
            ResponseCacheMemoryWorkload.Scenario scenario,
            int repetition,
            long baselineHeapBytes,
            long steadyHeapBytes,
            long releaseHeapBytes,
            long closedHeapBytes,
            long baselineRssBytes,
            long steadyRssBytes,
            long releaseRssBytes,
            long closedRssBytes,
            long baselineJdkDirectBytes,
            long steadyJdkDirectBytes,
            long closedJdkDirectBytes,
            long baselineNettyDirectBytes,
            long steadyNettyDirectBytes,
            long closedNettyDirectBytes,
            long baselineThreads,
            long steadyThreads,
            long closedThreads,
            long steadyEntries,
            long releaseEntries,
            long closedEntries,
            long steadyInFlightLoads,
            long steadyInFlightRefreshes,
            long closedInFlightLoads,
            long closedInFlightRefreshes,
            long closedPoolConnections,
            long payloadAllocatedBytes,
            long serverDispatches,
            long loadSubscriptions,
            long cacheEvictions) {

        private static Sample read(
                Path file,
                ResponseCacheMemoryWorkload.Scenario scenario,
                int repetition) throws IOException {
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(file)) {
                properties.load(input);
            }
            String prefix = "scenario." + scenario.id();
            int baseline = checkpoint(properties, prefix, "baseline-after-explicit-gc");
            int steady = checkpoint(properties, prefix, "steady-after-explicit-gc");
            int release = switch (scenario) {
                case TTL_EXPIRY -> checkpoint(properties, prefix, "expired-after-explicit-gc");
                case EXPLICIT_EVICTION -> checkpoint(properties, prefix, "evicted-after-explicit-gc");
                default -> steady;
            };
            int closed = checkpoint(properties, prefix, "fixture-closed-after-explicit-gc");
            return new Sample(
                    scenario, repetition,
                    value(properties, prefix, baseline, "usedHeapBytes"),
                    value(properties, prefix, steady, "usedHeapBytes"),
                    value(properties, prefix, release, "usedHeapBytes"),
                    value(properties, prefix, closed, "usedHeapBytes"),
                    value(properties, prefix, baseline, "processRssBytes"),
                    value(properties, prefix, steady, "processRssBytes"),
                    value(properties, prefix, release, "processRssBytes"),
                    value(properties, prefix, closed, "processRssBytes"),
                    value(properties, prefix, baseline, "directBufferMemoryUsedBytes"),
                    value(properties, prefix, steady, "directBufferMemoryUsedBytes"),
                    value(properties, prefix, closed, "directBufferMemoryUsedBytes"),
                    value(properties, prefix, baseline, "nettyAllocatorDirectMemoryUsedBytes"),
                    value(properties, prefix, steady, "nettyAllocatorDirectMemoryUsedBytes"),
                    value(properties, prefix, closed, "nettyAllocatorDirectMemoryUsedBytes"),
                    value(properties, prefix, baseline, "liveThreadCount"),
                    value(properties, prefix, steady, "liveThreadCount"),
                    value(properties, prefix, closed, "liveThreadCount"),
                    value(properties, prefix, steady, "cacheEntries"),
                    value(properties, prefix, release, "cacheEntries"),
                    value(properties, prefix, closed, "cacheEntries"),
                    value(properties, prefix, steady, "inFlightLoads"),
                    value(properties, prefix, steady, "inFlightRefreshes"),
                    value(properties, prefix, closed, "inFlightLoads"),
                    value(properties, prefix, closed, "inFlightRefreshes"),
                    value(properties, prefix, closed, "poolTotalConnections"),
                    value(properties, prefix, steady, "applicationPayloadAllocatedBytes"),
                    value(properties, prefix, steady, "serverDispatches"),
                    value(properties, prefix, steady, "loadSubscriptions"),
                    value(properties, prefix, steady, "cacheEvictions"));
        }

        private static int checkpoint(Properties properties, String prefix, String name) {
            int count = Integer.parseInt(required(properties, prefix + ".checkpointCount"));
            for (int index = 0; index < count; index++) {
                if (name.equals(required(properties, prefix + ".checkpoint." + index + ".name"))) {
                    return index;
                }
            }
            throw new IllegalArgumentException("Missing checkpoint " + name + " in " + prefix);
        }

        private static long value(Properties properties, String prefix, int checkpoint, String name) {
            return Long.parseLong(required(properties, prefix + ".checkpoint." + checkpoint + "." + name));
        }

        private static String required(Properties properties, String key) {
            String value = properties.getProperty(key);
            if (value == null) {
                throw new IllegalArgumentException("Missing property " + key);
            }
            return value;
        }

        private String toTsv() {
            return String.join("\t", Arrays.stream(new Object[]{
                            scenario.id(), repetition,
                            baselineHeapBytes, steadyHeapBytes, releaseHeapBytes, closedHeapBytes,
                            baselineRssBytes, steadyRssBytes, releaseRssBytes, closedRssBytes,
                            baselineJdkDirectBytes, steadyJdkDirectBytes, closedJdkDirectBytes,
                            baselineNettyDirectBytes, steadyNettyDirectBytes, closedNettyDirectBytes,
                            baselineThreads, steadyThreads, closedThreads,
                            steadyEntries, releaseEntries, closedEntries,
                            steadyInFlightLoads, steadyInFlightRefreshes,
                            closedInFlightLoads, closedInFlightRefreshes, closedPoolConnections,
                            payloadAllocatedBytes, serverDispatches, loadSubscriptions, cacheEvictions
                    }).map(String::valueOf).toArray(String[]::new));
        }
    }
}
