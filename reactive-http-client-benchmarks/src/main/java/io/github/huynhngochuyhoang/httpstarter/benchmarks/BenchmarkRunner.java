package io.github.huynhngochuyhoang.httpstarter.benchmarks;

import org.openjdk.jmh.Main;
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

public final class BenchmarkRunner {

    private BenchmarkRunner() {
    }

    public static void main(String[] args) throws Exception {
        writeEnvironmentMetadata(args);
        Main.main(args);
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
        properties.setProperty("benchmarkCommit", System.getProperty("benchmark.commit", "unknown"));
        properties.setProperty("springBootVersion", System.getProperty("benchmark.spring-boot.version", "unknown"));
        properties.setProperty("springWebFluxVersion", System.getProperty("benchmark.spring-webflux.version",
                mavenVersion("org.springframework", "spring-webflux")
                        .orElseGet(() -> packageVersion(WebClient.class))));
        properties.setProperty("reactorNettyVersion", System.getProperty("benchmark.reactor-netty.version",
                mavenVersion("io.projectreactor.netty", "reactor-netty-http")
                        .orElseGet(() -> packageVersion(HttpClient.class))));
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

    private static String packageVersion(Class<?> type) {
        Package packageInfo = type.getPackage();
        String version = packageInfo != null ? packageInfo.getImplementationVersion() : null;
        return version != null ? version : "unknown";
    }

    private static Optional<String> mavenVersion(String groupId, String artifactId) {
        String resource = "META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties";
        try (InputStream input = BenchmarkRunner.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                return Optional.empty();
            }
            Properties properties = new Properties();
            properties.load(input);
            return Optional.ofNullable(properties.getProperty("version"));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }
}
