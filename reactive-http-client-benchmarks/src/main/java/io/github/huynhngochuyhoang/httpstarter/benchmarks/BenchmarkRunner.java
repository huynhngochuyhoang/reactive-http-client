package io.github.huynhngochuyhoang.httpstarter.benchmarks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.Main;
import org.springframework.core.SpringVersion;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

public final class BenchmarkRunner {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private BenchmarkRunner() {
    }

    public static void main(String[] args) throws Exception {
        BenchmarkFairnessContract.validateDiscoveredBenchmarks();
        writeEnvironmentMetadata(args);
        Main.main(args);
        validateSelectedBenchmarksCompleted(args);
        BenchmarkMarkdownReport.writeIfResultFilePresent(args);
    }

    static void validateSelectedBenchmarksCompleted(String[] args) throws IOException {
        Path resultFile = resultFile(args);
        if (resultFile == null || !Files.exists(resultFile) || args.length == 0) {
            return;
        }
        Pattern include = Pattern.compile(args[0]);
        Set<String> expected = new TreeSet<>();
        for (BenchmarkFairnessContract.BenchmarkMethod method
                : BenchmarkFairnessContract.discoveredBenchmarks()) {
            String name = method.owner() + "." + method.name();
            if (include.matcher(name).find()) {
                expected.add(name);
            }
        }
        JsonNode results = OBJECT_MAPPER.readTree(resultFile.toFile());
        Set<String> completed = new TreeSet<>();
        if (results.isArray()) {
            results.forEach(result -> completed.add(result.path("benchmark").asText()));
        }
        expected.removeAll(completed);
        if (!expected.isEmpty()) {
            throw new IllegalStateException("JMH did not produce results for selected benchmarks " + expected
                    + "; inspect the benchmark output for setup or execution failures");
        }
    }

    private static void writeEnvironmentMetadata(String[] args) throws IOException {
        Path resultFile = resultFile(args);
        if (resultFile == null) {
            return;
        }
        Files.createDirectories(resultFile.getParent());
        Path metadataFile = resultFile.resolveSibling(resultFile.getFileName() + ".environment.properties");

        Properties properties = new Properties();
        properties.setProperty("generatedAt", Instant.now().toString());
        properties.setProperty("projectVersion", System.getProperty("benchmark.project.version", "unknown"));
        properties.setProperty("starterVersion", System.getProperty("benchmark.starter.version",
                properties.getProperty("projectVersion", "unknown")));
        properties.setProperty("apiCompatibilityBaselineVersion",
                System.getProperty("benchmark.api.compatibility.baseline.version", "unknown"));
        properties.setProperty("benchmarkCommit", System.getProperty("benchmark.commit", "unknown"));
        properties.setProperty("springBootVersion", System.getProperty("benchmark.spring-boot.version", "unknown"));
        properties.setProperty("stackContext", System.getProperty("benchmark.stack.context", "unknown"));
        properties.setProperty("comparisonPolicy", "same-stack only; cross-stack results are migration context");
        properties.setProperty("springWebFluxVersion", System.getProperty("benchmark.spring-webflux.version",
                artifactVersion("benchmark.spring-webflux.artifact", "spring-webflux")
                        .or(() -> loadedSpringVersion())
                        .or(() -> mavenVersion("org.springframework", "spring-webflux"))
                        .orElseGet(() -> packageVersion(WebClient.class))));
        properties.setProperty("reactorNettyVersion", System.getProperty("benchmark.reactor-netty.version",
                artifactVersion("benchmark.reactor-netty.artifact", "reactor-netty-http")
                        .or(() -> loadedReactorNettyVersion())
                        .or(() -> mavenVersion("io.projectreactor.netty", "reactor-netty-http"))
                        .orElseGet(() -> packageVersion(HttpClient.class))));
        properties.setProperty("springFrameworkVersion", properties.getProperty("springWebFluxVersion", "unknown"));
        properties.setProperty("nettyVersion", artifactVersion("benchmark.netty.artifact", "netty-codec-http")
                .or(() -> mavenVersion("io.netty", "netty-codec-http"))
                .orElse("unknown"));
        properties.setProperty("jacksonVersion", artifactVersion("benchmark.jackson.artifact", "jackson-databind")
                .or(() -> mavenVersion("tools.jackson.core", "jackson-databind"))
                .or(() -> mavenVersion("com.fasterxml.jackson.core", "jackson-databind"))
                .orElse("unknown"));
        properties.setProperty("micrometerVersion", artifactVersion("benchmark.micrometer.artifact", "micrometer-core")
                .or(() -> mavenVersion("io.micrometer", "micrometer-core"))
                .orElse("unknown"));
        properties.setProperty("openTelemetryVersion", artifactVersion("benchmark.opentelemetry.artifact", "opentelemetry-api")
                .or(() -> mavenVersion("io.opentelemetry", "opentelemetry-api"))
                .orElse("unknown"));
        properties.setProperty("baselineSpringWebFluxVersion", properties.getProperty("springWebFluxVersion", "unknown"));
        properties.setProperty("baselineReactorNettyVersion", properties.getProperty("reactorNettyVersion", "unknown"));
        properties.setProperty("dependencyManagement",
                "spring-boot-dependencies:" + properties.getProperty("springBootVersion", "unknown"));
        properties.setProperty("javaVersion", System.getProperty("java.version", "unknown"));
        properties.setProperty("javaVm", System.getProperty("java.vm.name", "unknown"));
        properties.setProperty("osName", System.getProperty("os.name", "unknown"));
        properties.setProperty("osArch", System.getProperty("os.arch", "unknown"));
        properties.setProperty("availableProcessors", String.valueOf(Runtime.getRuntime().availableProcessors()));
        properties.setProperty("jvmInputArguments", ManagementFactory.getRuntimeMXBean().getInputArguments().toString());
        properties.setProperty("resultFile", resultFile.toString());
        properties.setProperty("smokeOnly", String.valueOf(resultFile.getFileName().toString().contains("smoke-only")));

        try (OutputStream output = Files.newOutputStream(metadataFile)) {
            properties.store(output, "reactive-http-client benchmark environment");
        }
    }

    private static Path resultFile(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("-rff".equals(args[i])) {
                return Path.of(args[i + 1]);
            }
        }
        return null;
    }

    private static Optional<String> loadedSpringVersion() {
        return usableVersion(SpringVersion.getVersion());
    }

    private static Optional<String> loadedReactorNettyVersion() {
        try {
            java.lang.reflect.Method method = HttpClient.class.getDeclaredMethod("reactorNettyVersion");
            method.setAccessible(true);
            Object version = method.invoke(null);
            return version instanceof String value ? usableVersion(value) : Optional.empty();
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<String> artifactVersion(String propertyName, String artifactId) {
        String artifactPath = System.getProperty(propertyName);
        if (artifactPath == null || artifactPath.isBlank()) {
            return Optional.empty();
        }
        String fileName = Path.of(artifactPath).getFileName().toString();
        String prefix = artifactId + "-";
        if (!fileName.startsWith(prefix) || !fileName.endsWith(".jar")) {
            return Optional.empty();
        }
        return usableVersion(fileName.substring(prefix.length(), fileName.length() - ".jar".length()));
    }

    private static Optional<String> usableVersion(String version) {
        if (version == null || version.isBlank() || "unknown".equalsIgnoreCase(version) || "dev".equalsIgnoreCase(version)) {
            return Optional.empty();
        }
        return Optional.of(version);
    }

    private static String packageVersion(Class<?> type) {
        Package packageInfo = type.getPackage();
        String version = packageInfo != null ? packageInfo.getImplementationVersion() : null;
        return usableVersion(version).orElse("unknown");
    }

    private static Optional<String> mavenVersion(String groupId, String artifactId) {
        String resource = "META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties";
        try (InputStream input = BenchmarkRunner.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                return Optional.empty();
            }
            Properties properties = new Properties();
            properties.load(input);
            return usableVersion(properties.getProperty("version"));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }
}
