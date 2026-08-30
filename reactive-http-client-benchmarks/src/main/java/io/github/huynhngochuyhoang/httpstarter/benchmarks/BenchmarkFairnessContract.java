package io.github.huynhngochuyhoang.httpstarter.benchmarks;

import org.openjdk.jmh.runner.BenchmarkList;
import org.openjdk.jmh.runner.BenchmarkListEntry;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class BenchmarkFairnessContract {

    private static final String CLIENT_SIDE_PREFIX = "clientSideOverhead";
    private static final String LOOPBACK_BENCHMARK = LoopbackClientComparisonBenchmark.class.getName();
    private static final Set<String> CACHE_BENCHMARKS = Set.of(
            "io.github.huynhngochuyhoang.httpstarter.core.V27CachePerformanceBenchmark",
            "io.github.huynhngochuyhoang.httpstarter.core.V28SemanticReadCachePerformanceBenchmark");
    private static final String SEMANTIC_POST_CACHE_BENCHMARK =
            "io.github.huynhngochuyhoang.httpstarter.core.V28SemanticReadCachePerformanceBenchmark";
    private static final List<String> CLIENT_SURFACES = List.of(
            "RawWebClient",
            "SpringHttpExchange",
            "Starter");

    private BenchmarkFairnessContract() {
    }

    static void validateDiscoveredBenchmarks() {
        validate(discoveredBenchmarks());
    }

    static List<BenchmarkMethod> discoveredBenchmarks() {
        LinkedHashSet<BenchmarkMethod> methods = new LinkedHashSet<>();
        try {
            var resources = Thread.currentThread().getContextClassLoader()
                    .getResources(BenchmarkList.BENCHMARK_LIST.substring(1));
            while (resources.hasMoreElements()) {
                try (var input = resources.nextElement().openStream()) {
                    BenchmarkList.readBenchmarkList(input).stream()
                            .map(BenchmarkFairnessContract::descriptor)
                            .forEach(methods::add);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read the generated JMH benchmark list", ex);
        }
        return List.copyOf(methods);
    }

    static void validate(List<BenchmarkMethod> methods) {
        if (methods.isEmpty()) {
            throw new IllegalStateException("No JMH benchmark methods were discovered");
        }

        Map<String, Map<String, Integer>> comparisonScenarios = new LinkedHashMap<>();
        for (BenchmarkMethod method : methods) {
            BenchmarkMarkdownReport.validateClassification(method.name());
            boolean loopback = LOOPBACK_BENCHMARK.equals(method.owner());
            boolean loopbackClassification = method.name().startsWith(CLIENT_SIDE_PREFIX)
                    || method.name().startsWith("starterFeature")
                    || method.name().startsWith("starterErrorMapping");
            if (loopback != loopbackClassification) {
                throw new IllegalStateException("Benchmark method [" + method.owner() + "#" + method.name()
                        + "] mixes loopback and no-network classification");
            }
            if (method.name().startsWith("cacheLoopbackStarter")
                    && !CACHE_BENCHMARKS.contains(method.owner())) {
                throw new IllegalStateException("Cache loopback benchmark [" + method.owner() + "#"
                        + method.name() + "] must be owned by a cache benchmark fixture");
            }
            if (method.name().startsWith("cacheSemanticPostNoNetwork")
                    && !SEMANTIC_POST_CACHE_BENCHMARK.equals(method.owner())) {
                throw new IllegalStateException("Semantic POST no-network benchmark [" + method.owner() + "#"
                        + method.name() + "] must be owned by the V28 semantic-read cache fixture");
            }
            if (!method.name().startsWith(CLIENT_SIDE_PREFIX)) {
                continue;
            }

            String remainder = method.name().substring(CLIENT_SIDE_PREFIX.length());
            String surface = CLIENT_SURFACES.stream()
                    .filter(remainder::startsWith)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Unknown client-side benchmark surface [" + remainder + "]"));
            String scenario = remainder.substring(surface.length());
            comparisonScenarios.computeIfAbsent(scenario, ignored -> new LinkedHashMap<>())
                    .merge(surface, 1, Integer::sum);
        }

        comparisonScenarios.forEach((scenario, surfaces) -> {
            boolean complete = surfaces.keySet().equals(new LinkedHashSet<>(CLIENT_SURFACES))
                    && surfaces.values().stream().allMatch(count -> count == 1);
            if (!complete) {
                throw new IllegalStateException("Client-side benchmark scenario [" + scenario
                        + "] must contain exactly one method for each surface " + CLIENT_SURFACES
                        + " but found " + surfaces);
            }
        });
    }

    private static BenchmarkMethod descriptor(BenchmarkListEntry entry) {
        String owner = entry.getUserClassQName();
        String qualifiedName = entry.getUsername();
        String ownerPrefix = owner + ".";
        if (!qualifiedName.startsWith(ownerPrefix)) {
            throw new IllegalStateException("Invalid generated benchmark name [" + qualifiedName + "]");
        }
        return new BenchmarkMethod(owner, qualifiedName.substring(ownerPrefix.length()));
    }

    record BenchmarkMethod(String owner, String name) {
    }
}
