package io.github.huynhngochuyhoang.httpstarter.config;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentationReleaseArtifactTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern MARKDOWN_LINK = Pattern.compile("!?\\[[^]]*]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern UNIX_ABSOLUTE_PATH = Pattern.compile("(^|[\\s`\\\"=:(])(/(?!/)[^\\s`|)]+)");
    private static final Pattern WINDOWS_ABSOLUTE_PATH = Pattern.compile("(^|[\\s`\\\"=:(])([A-Za-z]:[\\\\\\/][^\\s`|)]+)");
    private static final Pattern PROJECT_VERSION_SNIPPET = Pattern.compile(
            "<groupId>io\\.github\\.huynhngochuyhoang</groupId>\\s*"
                    + "<artifactId>reactive-http-client-[^<]+</artifactId>\\s*"
                    + "<version>([^<]+)</version>",
            Pattern.DOTALL);

    @Test
    void localMarkdownLinksResolve() throws IOException {
        assertThat(brokenLocalMarkdownLinks(projectRoot())).as("broken local Markdown links").isEmpty();
    }

    @Test
    void readmeAndQuickStartVersionsMatchProjectVersion() throws Exception {
        String projectVersion = projectVersion(projectRoot().resolve("pom.xml"));

        assertVersionSnippets(projectRoot().resolve("README.md"), projectVersion);
        assertVersionSnippets(projectRoot().resolve("docs/01-quick-start.md"), projectVersion);
    }

    @Test
    void apiCompatibilityBaselineGuardIsDynamicAndProfileScoped() throws IOException {
        String pom = Files.readString(projectRoot().resolve("pom.xml"));
        int profileStart = pom.indexOf("<id>api-compatibility</id>");
        int profileEnd = pom.indexOf("</profile>", profileStart);

        assertThat(pom)
                .doesNotContain("api-compatibility-baseline-current-version-guard")
                .doesNotContain("ERROR-api.compatibility.baseline.version")
                .doesNotContain("<name>api.compatibility.baseline.version</name>");
        assertThat(profileStart).as("api-compatibility profile").isNotNegative();
        assertThat(profileEnd).as("api-compatibility profile end").isGreaterThan(profileStart);
        assertThat(pom.substring(profileStart, profileEnd))
                .contains("<id>reject-current-api-baseline</id>")
                .doesNotContain("<inherited>false</inherited>")
                .contains("<phase>validate</phase>")
                .contains("<equals arg1=\"${api.compatibility.baseline.version}\" arg2=\"${project.version}\"/>");
    }

    @Test
    void apiCompatibilityBaselineReleaseDocsStayAlignedWithPom() throws Exception {
        Path root = projectRoot();
        String pomXml = Files.readString(root.resolve("pom.xml"));
        String releaseDocs = Files.readString(root.resolve("docs/20-native-release-compatibility.md"));
        String benchmarkDocs = Files.readString(root.resolve("docs/22-benchmarks.md"));
        JsonNode manifest = OBJECT_MAPPER.valueToTree(releaseEvidenceManifest(root.resolve("pom.xml")));

        assertThat(projectVersion(root.resolve("pom.xml"))).isEqualTo("3.0.0");
        assertThat(pomProperty(pomXml, "api.compatibility.baseline.version")).isEqualTo("2.14.1");
        assertThat(pomProperty(pomXml, "spring-boot.version")).isEqualTo("4.0.0");
        assertThat(pomProperty(pomXml, "resilience4j.version")).isEqualTo("2.4.0");
        assertThat(releaseDocs)
                .contains("### V20 default Spring Boot 4 reactor")
                .contains("default reactor now declares `3.0.0`")
                .contains("uses published `2.14.1` as its cross-major compatibility baseline")
                .contains("mvn -s .mvn/maven-central-settings.xml verify")
                .contains("immutable Boot 3.5 maintenance reconstruction point remains `v2.14.1`");
        assertThat(benchmarkDocs)
                .contains("-Dbenchmark.starter.version=2.14.1")
                .contains("-Dbenchmark.commit=2.14.1")
                .contains("published-starter-2.14.1/release-jmh.md");
        assertThat(manifest.path("publishedBaselineArtifacts"))
                .extracting(artifact -> artifact.path("resolutionCommand").asText())
                .containsExactly(
                        "mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-starter:2.14.1",
                        "mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-test:2.14.1",
                        "mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-otel:2.14.1");
    }

    @Test
    void boot4AssembledConsumerFixtureStaysVersionAlignedAndDocumented() throws IOException {
        Path root = projectRoot();
        String fixturePom = Files.readString(root.resolve(".github/boot4-consumer/pom.xml"));
        String workflow = Files.readString(root.resolve(".github/workflows/ci.yml"));
        String fixtureTest = Files.readString(root.resolve(
                ".github/boot4-consumer/src/test/java/io/github/huynhngochuyhoang/httpstarter/boot4consumer/Boot4ConsumerApplicationTest.java"));
        String releaseDocs = Files.readString(root.resolve("docs/20-native-release-compatibility.md"));

        assertThat(fixturePom)
                .contains("<version>4.0.0</version>")
                .contains("<artifactId>reactive-http-client-starter</artifactId>")
                .contains("<artifactId>reactive-http-client-test</artifactId>")
                .contains("<artifactId>reactive-http-client-otel</artifactId>")
                .contains("<artifactId>spring-boot-webclient</artifactId>")
                .contains("<artifactId>spring-boot-jackson</artifactId>")
                .contains("<groupId>tools.jackson.core</groupId>")
                .contains("<artifactId>spring-boot-starter-actuator</artifactId>");
        assertThat(workflow)
                .contains("boot4-consumer:")
                .contains("-Dmaven.javadoc.skip=true install")
                .contains("-Dtest=Boot4MockReactiveHttpClientTest")
                .contains("-f .github/boot4-consumer/pom.xml")
                .contains("-Dreactive-http-client.version=\"$PROJECT_VERSION\"");
        assertThat(fixtureTest)
                .contains("extends SharedOrders<OrderResponse>")
                .contains("@ApiRef(\"configured\")")
                .contains("repeatedHeaders(List.of(\"first\", \"second\"))")
                .contains("follow-redirects=true")
                .contains("Mono<ResponseEntity<Flux<DataBuffer>>> streaming()")
                .contains("ProblemDetailHttpClientException.class")
                .contains("ErrorCategory.TIMEOUT")
                .contains("Boot4HttpClientHealthIndicator.class")
                .contains("openTelemetryHttpClientObserver");
        assertThat(releaseDocs)
                .contains("### Boot 4 assembled consumer fixture")
                .contains("real inherited-generic and configured")
                .contains("OAuth2, SigV4 raw-body signing")
                .contains("no dual-generation")
                .contains("no dual-generation helper");
    }

    @Test
    void boot4BenchmarkBaselineStaysSameStackAndSmokeOnly() throws Exception {
        Path root = projectRoot();
        String benchmarkPom = Files.readString(root.resolve("reactive-http-client-benchmarks/pom.xml"));
        String benchmarkDocs = Files.readString(root.resolve("docs/22-benchmarks.md"));

        String codecFactory = Files.readString(root.resolve(
                "reactive-http-client-benchmarks/src/main/java/io/github/huynhngochuyhoang/httpstarter/benchmarks/BenchmarkJsonCodecFactory.java"));
        assertThat(benchmarkPom)
                .contains("Spring Boot 4 release baseline")
                .contains("benchmark.netty.artifact")
                .contains("benchmark.jackson.artifact")
                .contains("benchmark.micrometer.artifact")
                .contains("benchmark.opentelemetry.artifact")
                .contains("META-INF/*.SF")
                .doesNotContain("<id>boot4-spike</id>");
        assertThat(codecFactory)
                .contains("ReactiveHttpClientJsonCodec")
                .contains("tools.jackson.databind.ObjectMapper")
                .doesNotContain("Jackson3ReactiveHttpClientJsonCodec");
        assertThat(benchmarkDocs)
                .contains("### Spring Boot 4 migration baseline")
                .contains("-Pbenchmarks,benchmark-smoke")
                .doesNotContain("-Pboot4-spike,benchmarks")
                .contains("Boot 3 versus Boot 4 movement is migration context")
                .contains("Review thresholds remain manual signals");
    }

    @Test
    void boot4MajorMigrationEvidenceIsCompleteAndReportOnly() throws IOException {
        Path root = projectRoot();
        String pomXml = Files.readString(root.resolve("pom.xml"));
        String guide = Files.readString(root.resolve("docs/28-spring-boot-4-jackson-migration.md"));
        String report = Files.readString(root.resolve("docs/api-report-2.14.1-to-3.0.0.md"));
        String workflow = Files.readString(root.resolve(".github/workflows/ci.yml"));

        assertThat(pomXml)
                .contains("<id>major-api-report</id>")
                .contains("<api.compatibility.break-on-binary-incompatible>true</api.compatibility.break-on-binary-incompatible>")
                .contains("<api.compatibility.ignore-missing-classes>false</api.compatibility.ignore-missing-classes>")
                .contains("<api.compatibility.break-on-binary-incompatible>false</api.compatibility.break-on-binary-incompatible>")
                .contains("<api.compatibility.ignore-missing-classes>true</api.compatibility.ignore-missing-classes>");
        assertThat(workflow)
                .contains("mvn -B -ntp -Papi-compatibility,major-api-report -DskipTests verify")
                .doesNotContain("mvn -B -ntp -Papi-compatibility -DskipTests verify");
        assertThat(guide)
                .contains("<version>3.5.16</version>")
                .contains("<reactive-http-client.version>2.14.1</reactive-http-client.version>")
                .contains("[2.14.1 to 3.0.0 API Report](api-report-2.14.1-to-3.0.0.md)")
                .contains("<version>4.0.0</version>")
                .contains("<reactive-http-client.version>3.0.0</reactive-http-client.version>")
                .contains("org.springframework.boot.webclient.WebClientCustomizer")
                .contains("org.springframework.boot.health.contributor")
                .contains("tools.jackson.databind.ObjectMapper")
                .contains("MockReactiveHttpClient.Builder.jsonCodec")
                .contains("No reactive.http property was renamed for Boot 4")
                .contains("Before, Boot 3.5 and starter 2.x")
                .contains("After, Boot 4 and starter 3.x")
                .contains("GraalVM Java 25")
                .contains("requires no\nconfiguration-metadata entry or reflection hint");
        assertThat(report)
                .contains("published 2.14.1", "Frozen baseline surface")
                .contains("Required by Boot 4")
                .contains("Jackson 3 codec boundary")
                .contains("Accidental or unrelated breaks")
                .contains("None. There are no changes")
                .contains("HttpClientHealthIndicator")
                .contains("Boot4HttpClientHealthIndicator")
                .contains("-Papi-compatibility,major-api-report")
                .doesNotContain("boot4-spike");
    }

    @Test
    void documentedPublicSurfaceMapMatchesApiCompatibilityIncludes() throws IOException {
        Path root = projectRoot();
        String pomXml = Files.readString(root.resolve("pom.xml"));
        String releaseDocs = Files.readString(root.resolve("docs/20-native-release-compatibility.md"));
        List<PublicSurfaceRow> documentedRows = documentedPublicSurfaceRows(releaseDocs);
        List<String> documentedIncludes = documentedRows.stream()
                .map(PublicSurfaceRow::includePattern)
                .toList();
        List<String> apiIncludes = apiCompatibilityIncludes(pomXml);

        assertThat(documentedIncludes)
                .as("documented public API compatibility map")
                .isNotEmpty()
                .doesNotHaveDuplicates()
                .contains(
                        "io.github.huynhngochuyhoang.httpstarter.annotation",
                        "io.github.huynhngochuyhoang.httpstarter.auth",
                        "io.github.huynhngochuyhoang.httpstarter.exception",
                        "io.github.huynhngochuyhoang.httpstarter.core.HttpExchangeLogger",
                        "io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientLifecycleHook",
                        "io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientDiagnosticsProvider*",
                        "io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientDiagnosticsSnapshot",
                        "io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientContractSnapshot*",
                        "io.github.huynhngochuyhoang.httpstarter.core.SensitiveHeaders",
                        "io.github.huynhngochuyhoang.httpstarter.core.MethodMetadataCache",
                        "io.github.huynhngochuyhoang.httpstarter.core.MethodMetadata*",
                        "io.github.huynhngochuyhoang.httpstarter.core.ResilienceOperatorApplier*",
                        "io.github.huynhngochuyhoang.httpstarter.test",
                        "io.github.huynhngochuyhoang.httpstarter.otel");
        assertThat(documentedRows)
                .extracting(PublicSurfaceRow::supportStatus)
                .containsOnly("Supported");
        assertThat(apiIncludes)
                .as("api-compatibility japicmp includes")
                .containsExactlyInAnyOrderElementsOf(documentedIncludes);
        assertThat(releaseDocs)
                .contains("The `3.0.0` migration removes `Jackson2ReactiveHttpClientJsonCodec`")
                .contains("No other\ncompatibility-covered type is reserved for removal")
                .contains("### Constructor and mutable model policy")
                .contains("The `MethodMetadata` no-arg constructor")
                .contains("canonical\n  record constructors")
                .contains("Provider overloads may include provider-only fields")
                .contains("builder methods, and\n  rendered table columns")
                .contains("Package-private\n  constructors remain internal")
                .contains("public nested enum")
                .contains("constructor, nested fluent method, or public enum constant fail")
                .contains("`MockReactiveHttpClient`, `RecordedExchange`, `RecordedExchangeAssertions`")
                .contains("`ErrorCategoryAssertions`, `MockHttpServer`, and `MockHttpServerExtension`")
                .contains("`OpenTelemetryHttpClientObserver`, `OpenTelemetryContextWebFilter`")
                .contains("`OpenTelemetryContextExchangeFilter`, and `OpenTelemetryHttpClientAutoConfiguration`")
                .contains("When documenting a new public helper")
                .contains("Prefer the narrowest include\npattern")
                .contains("Keep implementation\ninternals excluded")
                .contains("mvn -Papi-compatibility,major-api-report -DskipTests verify")
                .contains("mvn -pl reactive-http-client-starter -Papi-compatibility,major-api-report -DskipTests verify")
                .contains("bash scripts/verify-api-compatibility-fixtures.sh");
    }

    @Test
    void mockExchangeLoggerRegistrationIsDocumentedAndCompatibilityCovered() throws IOException {
        Path root = projectRoot();
        String exchangeLogging = Files.readString(root.resolve("docs/13-exchange-logging.md"));
        String releaseDocs = Files.readString(root.resolve("docs/20-native-release-compatibility.md"));
        String pomXml = Files.readString(root.resolve("pom.xml"));

        assertThat(exchangeLogging)
                .contains("MockReactiveHttpClient` uses an isolated application context")
                .contains(".withExchangeLogger(logger)")
                .contains("either `@Bean` or component scanning, not\nboth");
        assertThat(releaseDocs).contains("exchange-logger, and assertion methods");
        assertThat(pomXml).contains("<include>io.github.huynhngochuyhoang.httpstarter.test</include>");
    }

    @Test
    void v19NoGoDecisionKeepsBoot4BlockersAndMaintenanceLaneVisible() throws IOException {
        Path root = projectRoot();
        String decision = Files.readString(root.resolve("docs/29-v19-release-decision.md"));
        String roadmap = Files.readString(root.resolve("roadmaps/v19/ROADMAP.md"));
        String readme = Files.readString(root.resolve("README.md"));

        assertThat(decision)
                .contains("**Decision:** no-go for publishing `3.0.0`")
                .contains("| `3.x` minimum candidate | `4.0.0`")
                .contains("| `3.x` current candidate | `4.1.0`")
                .contains("Artifact identity is not `3.0.0`")
                .contains("Boot 4 release packaging fails")
                .contains("Benchmark report promotion is explicitly deferred")
                .contains("## `2.x` Maintenance Lane");
        assertThat(roadmap)
                .contains("completed with a `3.0.0` no-go decision")
                .contains("[V19 release decision](../../docs/29-v19-release-decision.md)");
        assertThat(readme).contains("[V19 3.0.0 Release Decision](docs/29-v19-release-decision.md)");
    }

    @Test
    void v20MaintenanceLaneUsesPublishedReleaseTagWithoutSelfComparison() throws Exception {
        Path root = projectRoot();
        String releaseDocs = Files.readString(root.resolve("docs/20-native-release-compatibility.md"));
        String pomXml = Files.readString(root.resolve("pom.xml"));

        assertThat(releaseDocs)
                .contains("immutable Boot 3.5 maintenance reconstruction point remains `v2.14.1`")
                .contains("Create a dedicated maintenance branch from that tag")
                .contains("do not compile Boot 3 adapters into the `3.x` artifacts");
        assertThat(projectVersion(root.resolve("pom.xml"))).isEqualTo("3.0.0");
        assertThat(pomXml)
                .contains("<spring-boot.version>4.0.0</spring-boot.version>")
                .contains("<api.compatibility.baseline.version>2.14.1</api.compatibility.baseline.version>");
    }

    @Test
    void v16ToV17AdoptionGuideDocumentsDiagnosticsFirstStrictValidationRollout() throws IOException {
        Path root = projectRoot();
        String readme = Files.readString(root.resolve("README.md"));
        String guide = Files.readString(root.resolve("docs/27-v16-to-v17-adoption.md"));

        assertThat(readme)
                .contains("[V16 to V17 Adoption Guide](docs/27-v16-to-v17-adoption.md)");
        assertThat(guide)
                .startsWith("# V16 to V17 Adoption Guide")
                .contains("Capture a diagnostics snapshot or `rhttpclients` endpoint response before")
                .contains("Production Support Bundle")
                .contains("Health details show recent Micrometer error-rate status")
                .contains("Startup summaries show sanitized client configuration at DEBUG")
                .contains("The `rhttpclients` endpoint shows sanitized configured-client diagnostics")
                .contains("strict-unsafe-retry-validation: true")
                .contains("one client")
                .contains("Reactor context")
                .contains("@HeaderParam")
                .contains("@IdempotencyKey` parameters")
                .contains("runtime contracts rather than startup-provable contracts")
                .contains("strict-body-signing-validation: true")
                .contains("Do not enable it for clients\nthat send publisher, multipart, resource, Java stream, erased `Object`, or\ndynamic non-JSON body shapes")
                .contains("Named `auth-provider` beans and custom `AuthProviderFactory` selections own\ntheir own signing contract")
                .contains("[Quick Start](01-quick-start.md)")
                .contains("[Outbound Auth Providers](06-auth-providers.md)")
                .contains("[Resilience4j Integration](07-resilience4j.md)")
                .contains("[Observability](08-observability.md)")
                .contains("[Production Support Bundles](26-support-bundles.md)")
                .contains("[Benchmarks](22-benchmarks.md)")
                .contains("[Native Image and Release Compatibility](20-native-release-compatibility.md)");
    }

    @Test
    void benchmarkModuleUsesStarterDependencyManagement() throws IOException {
        String benchmarkPom = Files.readString(projectRoot().resolve("reactive-http-client-benchmarks/pom.xml"));

        assertThat(benchmarkPom)
                .contains("<benchmark.starter.version>")
                .contains("benchmark.starter.version")
                .contains("benchmark.include")
                .contains("${benchmark.include}")
                .contains("benchmark-compare")
                .contains("benchmark.compare.current")
                .contains("BenchmarkReportComparator")
                .contains("benchmark.spring-webflux.artifact")
                .contains("benchmark.reactor-netty.artifact");
        assertThat(dependencyBlock(benchmarkPom, "spring-webflux")).doesNotContain("<version>");
        assertThat(dependencyBlock(benchmarkPom, "reactor-netty-http")).doesNotContain("<version>");
        assertThat(dependencyBlock(benchmarkPom, "micrometer-core")).doesNotContain("<version>");
        assertThat(dependencyBlock(benchmarkPom, "resilience4j-reactor")).doesNotContain("<version>");
        assertThat(dependencyBlock(benchmarkPom, "resilience4j-circuitbreaker")).doesNotContain("<version>");
        assertThat(dependencyBlock(benchmarkPom, "resilience4j-retry")).doesNotContain("<version>");
    }

    @Test
    void moduleDependenciesStayManagedAndOptionalIntegrationsStayOptional() throws Exception {
        Path root = projectRoot();
        String starterPom = Files.readString(root.resolve("reactive-http-client-starter/pom.xml"));
        String testPom = Files.readString(root.resolve("reactive-http-client-test/pom.xml"));
        String otelPom = Files.readString(root.resolve("reactive-http-client-otel/pom.xml"));
        String releaseDocs = Files.readString(root.resolve("docs/20-native-release-compatibility.md"));

        for (String artifactId : List.of("spring-boot-autoconfigure", "spring-webflux", "reactor-netty-http",
                "jackson-databind", "micrometer-core", "spring-boot-actuator", "slf4j-api")) {
            assertThat(dependencyBlock(starterPom, artifactId)).doesNotContain("<version>");
        }
        for (String artifactId : List.of("resilience4j-reactor", "resilience4j-circuitbreaker",
                "resilience4j-retry", "resilience4j-bulkhead", "resilience4j-ratelimiter",
                "resilience4j-micrometer", "micrometer-core", "spring-boot-actuator")) {
            assertThat(dependencyBlock(starterPom, artifactId))
                    .doesNotContain("<version>")
                    .contains("<optional>true</optional>");
        }
        for (String artifactId : List.of("spring-webflux", "reactor-core", "spring-test", "assertj-core",
                "junit-jupiter-api")) {
            assertThat(dependencyBlock(testPom, artifactId)).doesNotContain("<version>");
        }
        for (String artifactId : List.of("spring-boot-autoconfigure", "opentelemetry-api",
                "spring-boot-configuration-processor", "opentelemetry-sdk", "opentelemetry-sdk-testing",
                "micrometer-core")) {
            assertThat(dependencyBlock(otelPom, artifactId)).doesNotContain("<version>");
        }

        assertThat(releaseDocs)
                .contains("### V18 dependency patch review")
                .contains("| Spring WebFlux | `6.2.7` | `6.2.19` |")
                .contains("| Reactor Netty HTTP | `1.2.6` | `1.2.18` |")
                .contains("| Micrometer Core | `1.15.0` | `1.15.12` |")
                .contains("| OpenTelemetry API | `1.49.0` | `1.49.0` |")
                .contains("The candidate is **deferred**")
                .contains("Resilience4j remains independently managed by")
                .contains("its `2.2.0` BOM and optional");

        JsonNode review = OBJECT_MAPPER.valueToTree(releaseEvidenceManifest(root.resolve("pom.xml")))
                .path("dependencyBaselineReview");
        assertThat(review.path("reviewedAt").asText()).isEqualTo("2026-07-10");
        assertThat(review.path("minimumTestedSpringBootVersion").asText()).isEqualTo("3.5.0");
        assertThat(review.path("springBootPatchCandidate").asText()).isEqualTo("3.5.16");
        assertThat(review.path("candidateDecision").asText()).isEqualTo("adopted-in-2.14.1");
        assertThat(review.path("candidateManagedVersions").path("springWebFlux").asText()).isEqualTo("6.2.19");
        assertThat(review.path("candidateManagedVersions").path("reactorNettyHttp").asText()).isEqualTo("1.2.18");
        assertThat(review.path("candidateManagedVersions").path("nettyHttpCodec").asText()).isEqualTo("4.1.135.Final");
        assertThat(review.path("candidateManagedVersions").path("micrometerCore").asText()).isEqualTo("1.15.12");
        assertThat(review.path("candidateManagedVersions").path("openTelemetryApi").asText()).isEqualTo("1.49.0");
        assertThat(review.path("candidateManagedVersions").path("jacksonDatabind").asText()).isEqualTo("2.21.4");
        assertThat(review.path("candidateManagedVersions").path("junitJupiter").asText()).isEqualTo("5.12.2");
        assertThat(review.path("candidateManagedVersions").path("mockitoCore").asText()).isEqualTo("5.17.0");
    }

    @Test
    void defaultBoot4ReactorIsPublishableAndRejectsBoot3Leakage() throws Exception {
        Path root = projectRoot();
        String pomXml = Files.readString(root.resolve("pom.xml"));
        String starterPom = Files.readString(root.resolve("reactive-http-client-starter/pom.xml"));
        String testHelperPom = Files.readString(root.resolve("reactive-http-client-test/pom.xml"));
        String workflow = Files.readString(root.resolve(".github/workflows/ci.yml"));
        String publishWorkflow = Files.readString(root.resolve(".github/workflows/publish-maven-central.yml"));
        String packagingGuard = Files.readString(root.resolve("scripts/verify-generation-packaging.sh"));
        String settings = Files.readString(root.resolve(".mvn/maven-central-settings.xml"));

        assertThat(pomXml)
                .contains("<version>3.0.0</version>")
                .contains("<spring-boot.version>4.0.0</spring-boot.version>")
                .doesNotContain("<id>boot4-spike</id>")
                .doesNotContain("<maven.deploy.skip>true</maven.deploy.skip>")
                .doesNotContain("<skipPublishing>true</skipPublishing>");
        assertThat(starterPom)
                .contains("<artifactId>spring-boot-webclient</artifactId>")
                .contains("<artifactId>spring-boot-jackson</artifactId>")
                .contains("<groupId>tools.jackson.core</groupId>");
        assertThat(starterPom).doesNotContain("<groupId>com.fasterxml.jackson.core</groupId>");
        assertThat(testHelperPom).doesNotContain("<groupId>com.fasterxml.jackson.core</groupId>");
        assertThat(root.resolve("reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/Jackson2ReactiveHttpClientJsonCodec.java"))
                .doesNotExist();
        assertThat(starterPom)
                .doesNotContain("spring-boot-starter-classic")
                .doesNotContain("build-helper-maven-plugin");
        assertThat(workflow)
                .doesNotContain("-Pboot4-spike")
                .contains("mvn -B -ntp clean verify")
                .contains("bash scripts/verify-generation-packaging.sh")
                .contains("spring-boot: ['4.0.0', '4.1.0']");
        int publishPackagingGuard = publishWorkflow.indexOf("bash scripts/verify-generation-packaging.sh");
        int centralDeploy = publishWorkflow.indexOf("mvn -B -ntp -Prelease -DskipTests -DautoPublish=true deploy");
        assertThat(publishWorkflow)
                .contains("mvn -B -ntp clean verify")
                .contains("mvn -B -ntp clean -Prelease -DskipTests verify")
                .contains("MAVEN_GPG_PASSPHRASE: ${{ secrets.MAVEN_GPG_PASSPHRASE }}");
        assertThat(publishPackagingGuard).isGreaterThanOrEqualTo(0).isLessThan(centralDeploy);
        assertThat(packagingGuard)
                .contains("src/main/java")
                .contains("-sources.jar")
                .contains("-javadoc.jar")
                .contains("createdFiles.lst")
                .contains("contains classes outside the current compile output")
                .contains("assert_entry_count \"$sources_jar\" \"$resource\" 1")
                .contains("Boot3")
                .contains("AutoConfiguration.imports")
                .contains("ReactiveHttpClientRuntimeHints.class");
        assertThat(settings)
                .contains("<id>maven-central</id>")
                .contains("<url>https://repo.maven.apache.org/maven2</url>");
        assertThat(root.resolve("reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/Boot3JsonCodecAutoConfiguration.java")).doesNotExist();
        assertThat(root.resolve("reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/Boot3WebClientCustomizers.java")).doesNotExist();
        assertThat(root.resolve("reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/observability/HttpClientHealthIndicator.java")).doesNotExist();
    }


    @Test
    void boot4RuntimeAndNativeContractsStayReleaseReady() throws IOException {
        Path root = projectRoot();
        String autoConfiguration = Files.readString(root.resolve(
                "reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/ReactiveHttpClientAutoConfiguration.java"));
        String runtimeHints = Files.readString(root.resolve(
                "reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/ReactiveHttpClientRuntimeHints.java"));
        String nativeClient = Files.readString(root.resolve(
                ".github/native-smoke/src/main/java/io/github/huynhngochuyhoang/httpstarter/nativesmoke/NativeSmokeClient.java"));
        String nativeApplication = Files.readString(root.resolve(
                ".github/native-smoke/src/main/java/io/github/huynhngochuyhoang/httpstarter/nativesmoke/NativeSmokeApplication.java"));
        String nativePom = Files.readString(root.resolve(".github/native-smoke/pom.xml"));
        String nativeWorkflow = Files.readString(root.resolve(".github/workflows/native-smoke.yml"));
        String releaseDocs = Files.readString(root.resolve("docs/20-native-release-compatibility.md"));

        assertThat(autoConfiguration).contains(
                "org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration",
                "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration",
                "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
                "org.springframework.boot.health.autoconfigure.registry.HealthContributorRegistryAutoConfiguration");
        assertThat(runtimeHints).contains(
                "ReactiveHttpClientProperties.DiagnosticsEndpointConfig.class",
                "ReactiveHttpClientProperties.HealthConfig.class",
                "POM_PROPERTIES_RESOURCE");
        assertThat(nativeClient).contains(
                "extends NativeSmokeOperations<NativeOrderResponse>",
                "@ApiRef(\"native-problem\")");
        assertThat(nativeApplication).contains(
                "apis.native-problem.method",
                "ProblemDetailRemoteServiceException",
                "reactiveHttpClientDiagnosticsEndpoint",
                "reactiveHttpClientHealthIndicator",
                "reactive.http.client.requests");
        assertThat(nativePom).contains("-J-Xmx6g", "-H:NumberOfThreads=4", "-H:+SharedArenaSupport");
        assertThat(nativeWorkflow).contains(
                "set -o pipefail",
                "target/release-evidence/v20-priority6/native-provenance.txt",
                "native-smoke-provenance",
                "actions/upload-artifact@v4");
        assertThat(releaseDocs).contains(
                "configured inherited",
                "@ApiRef",
                "6 GiB",
                "native-smoke-provenance");
    }

    @Test
    void publishableModulePomsAndStagedConsumerGuardStayReleaseReady() throws IOException {
        Path root = projectRoot();
        String parentPom = Files.readString(root.resolve("pom.xml"));
        String starterPom = Files.readString(root.resolve("reactive-http-client-starter/pom.xml"));
        String testPom = Files.readString(root.resolve("reactive-http-client-test/pom.xml"));
        String otelPom = Files.readString(root.resolve("reactive-http-client-otel/pom.xml"));
        String benchmarkPom = Files.readString(root.resolve("reactive-http-client-benchmarks/pom.xml"));
        String stagingGuard = Files.readString(root.resolve("scripts/verify-publishable-artifacts.sh"));
        String publishWorkflow = Files.readString(root.resolve(".github/workflows/publish-maven-central.yml"));

        assertThat(parentPom)
                .contains("<issueManagement>")
                .contains("<system>GitHub Issues</system>")
                .contains("<autoPublish>false</autoPublish>")
                .contains("<artifactId>reactive-http-client-starter</artifactId>")
                .contains("<artifactId>reactive-http-client-test</artifactId>")
                .contains("<artifactId>reactive-http-client-otel</artifactId>")
                .doesNotContain("<maven.deploy.skip>true</maven.deploy.skip>");
        assertThat(starterPom)
                .contains("<url>https://github.com/huynhngochuyhoang/reactive-http-client</url>")
                .contains("scm:git:https://github.com/huynhngochuyhoang/reactive-http-client.git");
        assertThat(testPom).contains("<url>https://github.com/huynhngochuyhoang/reactive-http-client</url>");
        assertThat(otelPom).contains("<url>https://github.com/huynhngochuyhoang/reactive-http-client</url>");
        assertThat(dependencyBlock(testPom, "reactive-http-client-starter"))
                .doesNotContain("<version>");
        assertThat(dependencyBlock(otelPom, "reactive-http-client-starter"))
                .doesNotContain("<version>");
        assertThat(benchmarkPom)
                .contains("<artifactId>maven-deploy-plugin</artifactId>")
                .contains("<skip>true</skip>");

        assertThat(stagingGuard)
                .contains("PUBLISHABLE_MODULES=(reactive-http-client-starter reactive-http-client-test reactive-http-client-otel)")
                .contains("help:effective-pom")
                .contains("maven-deploy-plugin:3.1.4:deploy-file")
                .contains("assert_signed")
                .contains("sha256sum")
                .contains("consumer-repository")
                .contains("_remote.repositories")
                .contains("benchmark artifacts must not be staged");
        int signedBuild = publishWorkflow.indexOf("mvn -B -ntp clean -Prelease -DskipTests verify");
        int stagedConsumer = publishWorkflow.indexOf("bash scripts/verify-publishable-artifacts.sh");
        int centralDeploy = publishWorkflow.indexOf("mvn -B -ntp -Prelease -DskipTests -DautoPublish=true deploy");
        assertThat(signedBuild).isGreaterThanOrEqualTo(0).isLessThan(stagedConsumer);
        assertThat(stagedConsumer).isLessThan(centralDeploy);
    }

    @Test
    void benchmarkDocumentationScopesPerformanceClaimsToReleaseQualityReports() throws Exception {
        Path root = projectRoot();
        String projectVersion = projectVersion(root.resolve("pom.xml"));
        String baselineVersion = pomProperty(Files.readString(root.resolve("pom.xml")), "api.compatibility.baseline.version");
        String promotedReportVersion = latestPromotedBenchmarkReportVersion(root, projectVersion);
        Path promotedReport = root.resolve("docs/benchmark-report-" + promotedReportVersion + ".md");
        String readmeDocs = Files.readString(root.resolve("README.md"));
        String changelogDocs = Files.readString(root.resolve("CHANGELOG.md"));
        String currentReleaseNotes = currentReleaseChangelogSection(changelogDocs, projectVersion);
        String benchmarkDocs = Files.readString(root.resolve("docs/22-benchmarks.md"));
        String releaseNoteBenchmarkBlock = releaseNoteBenchmarkBlock(benchmarkDocs);
        String benchmarkConsumerDocs = Files.readString(root.resolve("docs/24-benchmark-consumer-examples.md"));
        String promotedReportDocs = Files.readString(promotedReport);
        String performanceSummaryDocs = Files.readString(root.resolve("docs/23-performance-summary.md"));
        String performanceTroubleshootingDocs = Files.readString(root.resolve("docs/25-performance-troubleshooting.md"));
        String releaseCompatibilityDocs = Files.readString(root.resolve("docs/20-native-release-compatibility.md"));

        assertThat(readmeDocs)
                .contains("[Benchmarks](docs/22-benchmarks.md)")
                .contains("[Benchmark Report " + promotedReportVersion + "](docs/benchmark-report-" + promotedReportVersion + ".md)")
                .contains("[Performance Summary](docs/23-performance-summary.md)");

        assertThat(changelogDocs)
                .contains("[Benchmark Report " + promotedReportVersion + "](docs/benchmark-report-" + promotedReportVersion + ".md)")
                .contains("release-quality evidence for starter `" + promotedReportVersion + "` benchmark scenarios")
                .contains("scenario names, and release-quality report links")
                .contains("smoke-only reports from being")
                .doesNotContain("near zero overhead")
                .doesNotContain("always faster")
                .doesNotContain("same performance as raw `WebClient`");

        assertThat(benchmarkDocs)
                .contains("## Methodology and Limits")
                .contains("## Comparison Model")
                .contains("| Raw `WebClient` |")
                .contains("| Spring HTTP Interface |")
                .contains("| Starter |")
                .contains("## Publishing Performance Claims")
                .contains("release-quality report version")
                .contains("exact JMH scenario name")
                .contains("Avoid broad wording such as")
                .contains("smoke-only report links")
                .contains("Benchmark Report " + promotedReportVersion)
                .contains("Performance Summary")
                .contains("Benchmark Consumer Examples")
                .contains("24-benchmark-consumer-examples.md")
                .contains("## Release-Note Benchmark Evidence")
                .contains("Benchmark evidence:")
                .contains("Promoted report: `docs/benchmark-report-<version>.md` after the release-quality report is generated and promoted")
                .contains("paths relative\nto the repository root")
                .contains("Current candidate command")
                .contains("clean committed tree")
                .contains("git status --short")
                .contains("missing `benchmarkCommit`")
                .contains("benchmarkCommit=unknown")
                .contains("commit value containing `dirty`")
                .contains("short Git SHA")
                .contains("Generated target-only reports")
                .contains("machine-local absolute paths")
                .contains("source-controlled promoted reports must not")
                .contains("Published baseline command")
                .contains("Current candidate report")
                .contains("Published baseline report")
                .contains("Scenarios cited")
                .contains("## Current vs Published Baseline Pairing")
                .contains("current candidate report and published-baseline report as a pair")
                .contains("published-starter-<version>/release-jmh.md")
                .contains("resolve every published baseline artifact")
                .contains("benchmark-compare")
                .contains("benchmark.compare.current")
                .contains("benchmark.compare.baseline")
                .contains("benchmark-comparison.md")
                .contains("fail-on-review")
                .contains("exits successfully by default")
                .contains("do not label\ncurrent candidate numbers as baseline numbers")
                .contains("benchmark.include")
                .contains("clientSideOverhead.*")
                .contains("GetPathQueryHeader")
                .contains("ResponseEntity")
                .contains("ClientErrorSmallBody")
                .contains("ServerErrorSmallBody")
                .contains("starterErrorMappingProblemDetailSmallBody")
                .contains("target/benchmark-reports/release-note")
                .contains("generated release evidence manifest is not enough")
                .contains("reactive-http-client-benchmark-evidence.md")
                .contains("target/release-evidence")
                .contains("same manifest data")
                .contains("## Optional Diagnostics Overhead")
                .contains("endpoint rendering is also support-path work")
                .contains("dedicated endpoint-rendering row")
                .contains("Strict unsafe-retry validation and strict built-in SigV4 body-signing validation")
                .contains("startup/proxy-construction checks")
                .contains("avoid\noptimizing strict-mode code without a repeatable named row")
                .contains("## Release-Maintainer Performance Claim Checklist")
                .contains("Before adding or approving a public performance claim")
                .contains("not a generated\n  `target/benchmark-reports` file and not a smoke-only report")
                .contains("exact scenario or scenario group")
                .contains("compared surfaces")
                .contains("allocation per operation")
                .contains("review-trigger movement is rerun on the same machine")
                .contains("Broad claims such as");

        assertThat(promotedReport).exists();
        assertPromotedBenchmarkProvenance(promotedReport.toString(), promotedReportDocs);
        assertThat(releaseNoteBenchmarkBlock)
                .contains("Promoted report: `docs/benchmark-report-<version>.md` after the release-quality report is generated and promoted")
                .doesNotContain("docs/benchmark-report-" + promotedReportVersion + ".md")
                .doesNotContain("[Benchmark Report " + promotedReportVersion + "]");
        assertThat(currentReleasePerformanceWordingViolations(root, currentReleaseNotes, projectVersion))
                .as("current changelog release-note performance wording")
                .isEmpty();
        assertThat(benchmarkDocs)
                .contains("When a release has no public performance claim")
                .contains("Do not mention\nfaster/slower movement, overhead reductions, latency changes, throughput changes,")
                .contains("source-controlled promoted report for that release version");

        assertThat(promotedReportDocs)
                .startsWith("# Reactive HTTP Client Benchmark Report")
                .contains("## Promotion Metadata")
                .contains("## Interpretation")
                .contains("## Report Pairing")
                .contains("## Environment")
                .contains("## Comparison Summary")
                .contains("## Starter-Only and Optional Feature Rows")
                .contains("## Raw Results")
                .contains("## Promotion Notes")
                .contains("Report version: `" + promotedReportVersion + "`")
                .contains("Starter version under test: `" + promotedReportVersion + "`")
                .contains("Evidence level: **Release-quality**, not smoke evidence.")
                .contains("| `starterVersion` | " + promotedReportVersion + " |")
                .contains("| `benchmarkCommit` |")
                .contains("| `javaVersion` |")
                .contains("| `springBootVersion` |")
                .contains("| `availableProcessors` |")
                .contains("## Comparison Summary")
                .contains("## Report Pairing")
                .contains("Current candidate: this promoted report measures starter `" + promotedReportVersion + "`")
                .contains("Published baseline: the paired baseline report path is `reactive-http-client-benchmarks/target/benchmark-reports/published-starter-")
                .contains("Numeric rows in this promoted report are current-candidate `" + promotedReportVersion + "` rows");

        assertThat(performanceSummaryDocs)
                .contains("## Methodology First")
                .contains("Quick benchmark output is smoke-only")
                .contains("Raw `WebClient`")
                .contains("Spring HTTP Interface")
                .contains("Starter default path")
                .contains("Starter optional features")
                .contains("Starter error mapping")
                .contains("`Get No Body`")
                .contains("`Post Json`")
                .contains("Proxy dispatch")
                .contains("metadata and request-plan lookup")
                .contains("Annotation argument resolution")
                .contains("Diagnostics hook checks")
                .contains("Resilience wrapper selection")
                .contains("Response envelope handling")
                .contains("starter-only error-mapping overhead")
                .contains("## Diagnostics and Strict-Mode Audit")
                .contains("The V17 audit did not add new benchmark methods")
                .contains("runtimeDiagnosticsProviderClientSummaries")
                .contains("No-network starter invocation")
                .contains("Actuator diagnostics endpoint JSON rendering is support-path work")
                .contains("separate endpoint-rendering benchmark")
                .contains("Strict unsafe-retry validation and strict built-in SigV4 body-signing")
                .contains("startup/proxy construction")
                .contains("no request-path optimization was\n  attempted without a dedicated startup benchmark row")
                .contains("starter `" + promotedReportVersion + "`")
                .contains("Current-vs-baseline comparisons should use the paired report paths")
                .contains("published-starter-" + baselineVersion + "/release-jmh.md")
                .contains("Resolve the published baseline artifacts before promoting a comparison")
                .contains("Performance Troubleshooting")
                .contains("25-performance-troubleshooting.md")
                .doesNotContain("near zero overhead")
                .doesNotContain("always faster")
                .doesNotContain("same performance as raw `WebClient`");

        assertThat(benchmarkConsumerDocs)
                .startsWith("# Benchmark Consumer Examples")
                .contains("[benchmark methodology](22-benchmarks.md#methodology-and-limits)")
                .contains("[" + promotedReportVersion + " promoted report](benchmark-report-" + promotedReportVersion + ".md)")
                .contains("## Equivalent Success Path")
                .contains("## Raw WebClient")
                .contains("webClient.get()")
                .contains(".queryParam(\"expand\", \"summary\")")
                .contains(".header(\"X-Tenant\", \"benchmark\")")
                .contains("## Spring HTTP Interface")
                .contains("@HttpExchange")
                .contains("@GetExchange(\"/users/{id}\")")
                .contains("## Starter Interface")
                .contains("@ReactiveHttpClient(name = \"benchmark-starter\")")
                .contains("@GET(\"/users/{id}\")")
                .contains("Same local loopback server and base URL")
                .contains("Same HTTP method, path variable, query parameter, and request header")
                .contains("Same response-body type and terminal consumption")
                .contains("## Starter-Only Rows")
                .contains("Optional feature rows are starter-only unless the baseline client performs the")
                .contains("same extra work")
                .contains("Problem Detail rows are also starter-only unless the baseline installs an")
                .contains("equivalent `application/problem+json` mapper")
                .doesNotContain("near zero overhead")
                .doesNotContain("always faster")
                .doesNotContain("same performance as raw WebClient");

        assertThat(performanceTroubleshootingDocs)
                .startsWith("# Performance Troubleshooting")
                .contains("[Benchmarks](22-benchmarks.md)")
                .contains("[Performance Summary](23-performance-summary.md)")
                .contains("[Observability](08-observability.md)")
                .contains("[Exchange Logging](13-exchange-logging.md)")
                .contains("[Lifecycle Hooks](19-lifecycle-hooks.md)")
                .contains("## Locate the Time")
                .contains("Starter client abstraction overhead")
                .contains("Downstream service latency")
                .contains("Network latency")
                .contains("Application serialization and body processing")
                .contains("## Inspect Metadata First")
                .contains("metadata-only")
                .contains("METADATA_ONLY")
                .contains("## Use Observability Signals")
                .contains("Keep tag cardinality bounded")
                .contains("## Read Lifecycle Attempts Carefully")
                .contains("logical subscription attempts")
                .contains("## Check Timeout Source")
                .contains("Timeout diagnostics")
                .contains("lifecycle hooks and observer events do not expose response\n  headers")
                .contains("## Account for Body Size")
                .contains("JSON request bodies")
                .contains("Error body capture is bounded")
                .contains("Streaming responses")
                .contains("## Compare Workload Shape")
                .contains("Does the baseline client perform the same optional work?")
                .contains("## Investigation Checklist")
                .doesNotContain("near zero overhead")
                .doesNotContain("always faster")
                .doesNotContain("same performance as raw WebClient");

        assertThat(releaseCompatibilityDocs)
                .contains("promote the release-quality report into")
                .contains("docs/benchmark-report-<version>.md")
                .contains("do not link generated `target/` reports directly")
                .contains("current candidate and published-baseline reports at the distinct paths")
                .contains("resolve the listed baseline artifacts before report\npromotion")
                .doesNotContain("reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.md` in");

        List<String> invalidReportLinks = new ArrayList<>();
        List<String> promotedReportLinks = new ArrayList<>();
        try (Stream<Path> files = publicMarkdownFiles(root)) {
            for (Path markdown : files.toList()) {
                Matcher matcher = MARKDOWN_LINK.matcher(Files.readString(markdown));
                while (matcher.find()) {
                    String target = URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
                    if (target.contains("benchmark-reports/") || target.contains("smoke-only-jmh.md")) {
                        invalidReportLinks.add(root.relativize(markdown) + " -> " + target);
                    }
                    if (target.contains("benchmark-report-")) {
                        promotedReportLinks.add(target);
                    }
                }
            }
        }

        assertThat(invalidReportLinks)
                .as("public docs must link promoted release-quality benchmark reports, not target or smoke reports")
                .isEmpty();
        assertThat(promotedReportLinks)
                .as("public docs should link the promoted current-version benchmark report")
                .contains("docs/benchmark-report-" + promotedReportVersion + ".md",
                        "benchmark-report-" + promotedReportVersion + ".md");
    }

    @Test
    void currentReleasePerformanceWordingGuardRejectsStaleMissingAndTargetBenchmarkEvidence() throws Exception {
        Path root = projectRoot();
        String noClaim = """
                ## [Unreleased]

                - **Post-release baseline transition.** API compatibility and published-baseline benchmark evidence compare against the last published artifacts.
                """;
        String staleClaim = """
                ## [Unreleased]

                - **Performance evidence.** Current release notes cite [Benchmark Report 2.12.0](docs/benchmark-report-2.12.0.md).
                """;
        String missingClaim = """
                ## [Unreleased]

                - **Performance evidence.** Current release notes cite [Benchmark Report 9.9.9](docs/benchmark-report-9.9.9.md).
                """;
        String targetLink = """
                ## [Unreleased]

                - **Performance evidence.** Current release notes cite [release-jmh](reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.md).
                """;
        String targetInline = """
                ## [Unreleased]

                - **Performance evidence.** Current release notes cite [Benchmark Report 9.9.9](docs/benchmark-report-9.9.9.md) and `reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.md`.
                """;
        String allocationClaim = """
                ## [Unreleased]

                - **Default path.** Reduced allocations for default starter calls.
                """;
        String percentileClaim = """
                ## [Unreleased]

                - **Default path.** Lower p99 for Get No Body.
                """;
        String averageTimeClaim = """
                ## [Unreleased]

                - **Default path.** Improved average time for default starter calls.
                """;
        String unrelatedNoClaim = """
                ## [Unreleased]

                - **Docs.** Documented performance troubleshooting guidance.
                - **Logging.** Reduced log noise in support examples.
                """;

        assertThat(currentReleasePerformanceWordingViolations(root, noClaim, "9.9.9"))
                .isEmpty();
        assertThat(currentReleasePerformanceWordingViolations(root, unrelatedNoClaim, "9.9.9"))
                .isEmpty();
        assertThat(currentReleasePerformanceWordingViolations(root, staleClaim, "9.9.9"))
                .anySatisfy(violation -> assertThat(violation).contains("current release performance claim"));
        assertThat(currentReleasePerformanceWordingViolations(root, missingClaim, "9.9.9"))
                .anySatisfy(violation -> assertThat(violation).contains("missing benchmark report"));
        assertThat(currentReleasePerformanceWordingViolations(root, targetLink, "9.9.9"))
                .anySatisfy(violation -> assertThat(violation).contains("target-only benchmark path"));
        assertThat(currentReleasePerformanceWordingViolations(root, targetInline, "9.9.9"))
                .anySatisfy(violation -> assertThat(violation).contains("target-only benchmark path"));
        assertThat(currentReleasePerformanceWordingViolations(root, allocationClaim, "9.9.9"))
                .anySatisfy(violation -> assertThat(violation).contains("current release performance claim"));
        assertThat(currentReleasePerformanceWordingViolations(root, percentileClaim, "9.9.9"))
                .anySatisfy(violation -> assertThat(violation).contains("current release performance claim"));
        assertThat(currentReleasePerformanceWordingViolations(root, averageTimeClaim, "9.9.9"))
                .anySatisfy(violation -> assertThat(violation).contains("current release performance claim"));
    }

    @Test
    void promotedBenchmarkProvenanceRejectsMissingUnknownDirtyMalformedAndLocalPathReports() {
        assertPromotedBenchmarkProvenance("clean", benchmarkReportWithCommit("4ec8c6a"));
        assertPromotedBenchmarkProvenance("relative-path-and-url", benchmarkReportWithCommit("4ec8c6a")
                + "Result file: `reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.json`\n"
                + "Problem URL: https://example.com/problems/benchmark\n");

        assertThatThrownBy(() -> assertPromotedBenchmarkProvenance("missing", "# Report\n"))
                .hasMessageContaining("benchmarkCommit");
        assertThatThrownBy(() -> assertPromotedBenchmarkProvenance("unknown", benchmarkReportWithCommit("unknown")))
                .hasMessageContaining("unknown");
        assertThatThrownBy(() -> assertPromotedBenchmarkProvenance("dirty", benchmarkReportWithCommit("4ec8c6a-dirty")))
                .hasMessageContaining("dirty");
        assertThatThrownBy(() -> assertPromotedBenchmarkProvenance("malformed", benchmarkReportWithCommit("release-candidate")))
                .hasMessageContaining("short Git SHA");
        assertThatThrownBy(() -> assertPromotedBenchmarkProvenance("local-path", benchmarkReportWithCommit("4ec8c6a")
                + "Generated from /home/runner/reactive-http-client\n"))
                .hasMessageContaining("machine-local absolute paths");
        assertThatThrownBy(() -> assertPromotedBenchmarkProvenance("workspace-path", benchmarkReportWithCommit("4ec8c6a")
                + "Generated from /workspace/reactive-http-client/target/benchmark-reports/release-jmh.md\n"))
                .hasMessageContaining("machine-local absolute paths");
        assertThatThrownBy(() -> assertPromotedBenchmarkProvenance("tmp-path", benchmarkReportWithCommit("4ec8c6a")
                + "Generated from /tmp/reactive-http-client/target/benchmark-reports/release-jmh.md\n"))
                .hasMessageContaining("machine-local absolute paths");
        assertThatThrownBy(() -> assertPromotedBenchmarkProvenance("windows-path", benchmarkReportWithCommit("4ec8c6a")
                + "Generated from C:\\Users\\runner\\reactive-http-client\\target\\benchmark-reports\\release-jmh.md\n"))
                .hasMessageContaining("machine-local absolute paths");
    }

    @Test
    void promotedBenchmarkReportVersionsMatchReleaseDocumentation() throws Exception {
        Path root = projectRoot();
        String projectVersion = projectVersion(root.resolve("pom.xml"));
        String promotedReportVersion = latestPromotedBenchmarkReportVersion(root, projectVersion);
        Path promotedReport = root.resolve("docs/benchmark-report-" + promotedReportVersion + ".md");

        assertPromotedReportMetadata(promotedReport, promotedReportVersion);

        assertCurrentBenchmarkReportReferences(root.resolve("README.md"), promotedReportVersion);
        assertCurrentBenchmarkReportReferences(root.resolve("docs/22-benchmarks.md"), promotedReportVersion);
        assertCurrentBenchmarkReportReferences(root.resolve("docs/23-performance-summary.md"), promotedReportVersion);
        assertCurrentBenchmarkReportReferences(root.resolve("docs/24-benchmark-consumer-examples.md"), promotedReportVersion);

        String changelog = Files.readString(root.resolve("CHANGELOG.md"));
        String releaseSection = changelogSection(changelog, promotedReportVersion);
        assertThat(releaseSection)
                .contains("[Benchmark Report " + promotedReportVersion + "](docs/benchmark-report-" + promotedReportVersion + ".md)");
        assertBenchmarkReportReferences("CHANGELOG.md release " + promotedReportVersion, releaseSection, promotedReportVersion);

        try (Stream<Path> reports = Files.list(root.resolve("docs"))) {
            for (Path report : reports
                    .filter(path -> path.getFileName().toString().matches("benchmark-report-\\d+\\.\\d+\\.\\d+\\.md"))
                    .toList()) {
                String fileName = report.getFileName().toString();
                String reportVersion = fileName.substring("benchmark-report-".length(),
                        fileName.length() - ".md".length());
                assertPromotedReportMetadata(report, reportVersion);
            }
        }
    }

    @Test
    void generatedConfigurationReferenceMatchesMetadata() throws IOException {
        Path reference = projectRoot().resolve("docs/configuration-properties.md");

        assertThat(Files.readString(reference))
                .isEqualTo(configurationReferenceMarkdown(configurationMetadata(projectRoot())));
    }

    @Test
    void releaseEvidenceManifestIsGeneratedUnderTarget() throws Exception {
        Path root = projectRoot();
        Path manifest = root.resolve("target/release-evidence/reactive-http-client-release-evidence.json");
        Path benchmarkEvidenceSnippet = root.resolve("target/release-evidence/reactive-http-client-benchmark-evidence.md");
        String pomXml = Files.readString(root.resolve("pom.xml"));
        Files.createDirectories(manifest.getParent());
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(manifest.toFile(), releaseEvidenceManifest(root.resolve("pom.xml")));

        JsonNode generated = OBJECT_MAPPER.readTree(manifest.toFile());
        Files.writeString(benchmarkEvidenceSnippet, releaseNoteBenchmarkEvidenceMarkdown(generated));
        String benchmarkEvidenceMarkdown = Files.readString(benchmarkEvidenceSnippet);

        assertThat(manifest.normalize()).startsWith(root.resolve("target"));
        assertThat(benchmarkEvidenceSnippet.normalize()).startsWith(root.resolve("target"));
        assertThat(generated.path("projectVersion").asText()).isEqualTo(projectVersion(root.resolve("pom.xml")));
        assertThat(generated.path("apiCompatibilityBaselineVersion").asText())
                .isEqualTo(pomProperty(pomXml, "api.compatibility.baseline.version"));
        assertThat(generated.path("apiCompatibilityBaselineMatchesProjectVersion").asBoolean()).isFalse();
        JsonNode readiness = generated.path("readiness");
        assertThat(readiness.path("projectVersion").asText()).isEqualTo(generated.path("projectVersion").asText());
        assertThat(readiness.path("apiCompatibilityBaselineVersion").asText())
                .isEqualTo(generated.path("apiCompatibilityBaselineVersion").asText());
        assertThat(readiness.path("apiCompatibilityBaselineMatchesProjectVersion").asBoolean()).isFalse();
        assertThat(readiness.path("generatedTestEvidence").path("status").asText()).isEqualTo("pass");
        assertThat(readiness.path("manualReleaseEvidence").path("status").asText()).isEqualTo("pending");
        List<String> pendingReleaseCommands = streamText(readiness.path("manualReleaseEvidence").path("pendingCommands"));
        assertThat(pendingReleaseCommands).contains("mvn -Papi-compatibility,major-api-report -DskipTests verify");
        assertThat(pendingReleaseCommands).contains(
                "mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-starter:"
                        + pomProperty(pomXml, "api.compatibility.baseline.version"),
                "mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-test:"
                        + pomProperty(pomXml, "api.compatibility.baseline.version"),
                "mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-otel:"
                        + pomProperty(pomXml, "api.compatibility.baseline.version"));
        assertThat(pendingReleaseCommands)
                .anySatisfy(command -> assertThat(command)
                        .contains("benchmark-release")
                        .contains("benchmark.commit=$(git rev-parse --short HEAD)"));
        assertThat(pendingReleaseCommands)
                .contains(generated.path("benchmarkEvidence").path("publishedStarterCommand").asText());
        assertThat(readiness.path("manualBenchmarkEvidence").path("status").asText()).isEqualTo("pending");
        List<String> pendingBenchmarkCommands = streamText(readiness.path("manualBenchmarkEvidence").path("pendingCommands"));
        assertThat(pendingBenchmarkCommands)
                .contains("mvn -Pbenchmarks,benchmark-smoke -pl reactive-http-client-benchmarks -am verify");
        assertThat(pendingBenchmarkCommands)
                .anySatisfy(command -> assertThat(command)
                        .contains("benchmark-release")
                        .contains("benchmark.commit=$(git rev-parse --short HEAD)"));
        assertThat(pendingBenchmarkCommands)
                .contains(generated.path("benchmarkEvidence").path("publishedStarterCommand").asText());
        assertThat(readiness.path("manualCompatibilityEvidence").path("status").asText()).isEqualTo("pending");
        assertThat(readiness.path("manualCompatibilityEvidence").path("pendingCommands"))
                .extracting(JsonNode::asText)
                .containsExactly("mvn -Papi-compatibility,major-api-report -DskipTests verify",
                        "mvn -pl reactive-http-client-starter -Papi-compatibility,major-api-report -DskipTests verify",
                        "bash scripts/verify-api-compatibility-fixtures.sh");
        String expectedPromotedReport = "docs/benchmark-report-" + generated.path("projectVersion").asText() + ".md";
        assertThat(readiness.path("promotedBenchmarkReport").path("path").asText())
                .isEqualTo(expectedPromotedReport);
        assertThat(readiness.path("promotedBenchmarkReport").path("status").asText())
                .isEqualTo(Files.exists(root.resolve(expectedPromotedReport)) ? "present" : "missing");
        assertThat(readiness.path("configurationReference").path("status").asText()).isEqualTo("current");
        assertThat(readiness.path("markdownLinks").path("status").asText()).isEqualTo("pass");
        assertThat(readiness.path("staleBenchmarkReportLinks").path("status").asText()).isEqualTo("pass");
        assertThat(readiness.path("releaseEvidenceDirectory").asText()).isEqualTo("target/release-evidence/");
        assertThat(readiness.path("targetOnlyEvidence").path("sourceControlled").asBoolean()).isFalse();
        assertThat(readiness.path("targetOnlyEvidence").path("commitGeneratedEvidence").asBoolean()).isFalse();

        JsonNode releasePrepChecklist = generated.path("releasePrepChecklist");
        assertThat(releasePrepChecklist.path("status").asText()).isEqualTo("pending");
        assertThat(releasePrepChecklist.path("projectVersion").asText()).isEqualTo(generated.path("projectVersion").asText());
        assertThat(releasePrepChecklist.path("apiCompatibilityBaselineVersion").asText())
                .isEqualTo(generated.path("apiCompatibilityBaselineVersion").asText());
        assertThat(streamText(releasePrepChecklist.path("manualCommands")))
                .containsAll(pendingReleaseCommands);
        Map<String, JsonNode> releasePrepItems = new LinkedHashMap<>();
        releasePrepChecklist.path("items").forEach(item -> releasePrepItems.put(item.path("id").asText(), item));
        assertThat(releasePrepItems.keySet()).containsExactly(
                "changelog-section",
                "version-snippets",
                "published-baseline-artifacts",
                "api-compatibility",
                "benchmark-evidence",
                "promoted-benchmark-report",
                "generated-docs-and-links",
                "target-only-evidence");
        assertThat(releasePrepItems.get("changelog-section").path("status").asText()).isEqualTo("current");
        String manifestProjectVersion = generated.path("projectVersion").asText();
        String unreleasedCompareVersion = Files.readString(root.resolve("CHANGELOG.md"))
                .contains("## [" + manifestProjectVersion + "] - ")
                ? manifestProjectVersion
                : generated.path("apiCompatibilityBaselineVersion").asText();
        assertThat(releasePrepItems.get("changelog-section").path("expectedUnreleasedCompareLink").asText())
                .contains("v" + unreleasedCompareVersion + "...HEAD");
        assertThat(releasePrepItems.get("version-snippets").path("status").asText()).isEqualTo("current");
        assertThat(releasePrepItems.get("version-snippets").path("expectedVersion").asText())
                .isEqualTo(generated.path("projectVersion").asText());
        assertThat(streamText(releasePrepItems.get("published-baseline-artifacts").path("commands")))
                .containsExactly(
                        "mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-starter:"
                                + pomProperty(pomXml, "api.compatibility.baseline.version"),
                        "mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-test:"
                                + pomProperty(pomXml, "api.compatibility.baseline.version"),
                        "mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-otel:"
                                + pomProperty(pomXml, "api.compatibility.baseline.version"));
        assertThat(streamText(releasePrepItems.get("api-compatibility").path("commands")))
                .containsExactly("mvn -Papi-compatibility,major-api-report -DskipTests verify",
                        "mvn -pl reactive-http-client-starter -Papi-compatibility,major-api-report -DskipTests verify",
                        "bash scripts/verify-api-compatibility-fixtures.sh");
        assertThat(streamText(releasePrepItems.get("benchmark-evidence").path("commands")))
                .contains("mvn -Pbenchmarks,benchmark-smoke -pl reactive-http-client-benchmarks -am verify",
                        generated.path("benchmarkEvidence").path("currentWorkspaceCommand").asText(),
                        generated.path("benchmarkEvidence").path("publishedStarterCommand").asText());
        assertThat(releasePrepItems.get("promoted-benchmark-report").path("path").asText())
                .isEqualTo(expectedPromotedReport);
        assertThat(releasePrepItems.get("promoted-benchmark-report").path("status").asText())
                .isEqualTo(Files.exists(root.resolve(expectedPromotedReport)) ? "present" : "missing");
        assertThat(releasePrepItems.get("generated-docs-and-links").path("status").asText()).isEqualTo("pass");
        assertThat(releasePrepItems.get("generated-docs-and-links").path("configurationReference").asText())
                .isEqualTo("current");
        assertThat(releasePrepItems.get("generated-docs-and-links").path("markdownLinks").asText())
                .isEqualTo("pass");
        assertThat(releasePrepItems.get("target-only-evidence").path("status").asText()).isEqualTo("pass");
        assertThat(releasePrepItems.get("target-only-evidence").path("sourceControlled").asBoolean()).isFalse();

        JsonNode dependencyBaselineReview = generated.path("dependencyBaselineReview");
        assertThat(dependencyBaselineReview.path("javaBaseline").asText())
                .isEqualTo(pomProperty(pomXml, "java.version"));
        assertThat(dependencyBaselineReview.path("springBootBaseline").asText())
                .isEqualTo(pomProperty(pomXml, "spring-boot.version"));
        assertThat(dependencyBaselineReview.path("resilience4jVersion").asText())
                .isEqualTo(pomProperty(pomXml, "resilience4j.version"));
        assertThat(dependencyBaselineReview.path("jmhVersion").asText())
                .isEqualTo(pomProperty(pomXml, "jmh.version"));
        assertThat(dependencyBaselineReview.path("springWebFluxVersionSource").asText())
                .contains("spring-boot-dependencies:" + pomProperty(pomXml, "spring-boot.version"));
        assertThat(dependencyBaselineReview.path("micrometerVersionSource").asText())
                .contains("spring-boot-dependencies:" + pomProperty(pomXml, "spring-boot.version"));
        assertThat(dependencyBaselineReview.path("openTelemetryVersionSource").asText())
                .contains("spring-boot-dependencies:" + pomProperty(pomXml, "spring-boot.version"));
        assertThat(dependencyBaselineReview.path("testDependencyVersionSource").asText())
                .contains("spring-boot-dependencies:" + pomProperty(pomXml, "spring-boot.version"))
                .contains("explicit test-only pins");
        assertThat(dependencyBaselineReview.path("compatibilityNeutralUpgrades"))
                .extracting(JsonNode::asText)
                .contains("Spring Boot patch upgrades within the documented baseline line",
                        "benchmark harness updates that keep metadata recording intact");
        assertThat(dependencyBaselineReview.path("minorReleaseUpgrades"))
                .extracting(JsonNode::asText)
                .contains("raising the Java baseline", "adding a new Spring Boot minor line");
        assertThat(dependencyBaselineReview.path("baselineUpgradePolicy").asText())
                .contains("Do not mix dependency-baseline upgrades with unrelated feature work");
        assertThat(generated.path("benchmarkDependencyManagement").path("springBootVersion").asText())
                .isEqualTo(pomProperty(pomXml, "spring-boot.version"));
        assertThat(generated.path("benchmarkDependencyManagement").path("reactorNettyVersionSource").asText())
                .contains("spring-boot-dependencies");
        assertThat(generated.path("benchmarkDependencyManagement").path("springWebFluxVersionSource").asText())
                .contains("spring-boot-dependencies");
        assertThat(generated.path("benchmarkDependencyManagement").path("micrometerVersionSource").asText())
                .contains("spring-boot-dependencies");
        assertThat(generated.path("benchmarkDependencyManagement").path("resilience4jVersion").asText())
                .isEqualTo(pomProperty(pomXml, "resilience4j.version"));
        assertThat(generated.path("benchmarkDependencyManagement").path("jmhVersion").asText())
                .isEqualTo(pomProperty(pomXml, "jmh.version"));
        assertThat(generated.path("publishedBaselineArtifacts"))
                .extracting(artifact -> artifact.path("artifact").asText())
                .containsExactly(
                        "io.github.huynhngochuyhoang:reactive-http-client-starter:"
                                + pomProperty(pomXml, "api.compatibility.baseline.version"),
                        "io.github.huynhngochuyhoang:reactive-http-client-test:"
                                + pomProperty(pomXml, "api.compatibility.baseline.version"),
                        "io.github.huynhngochuyhoang:reactive-http-client-otel:"
                                + pomProperty(pomXml, "api.compatibility.baseline.version"));
        assertThat(generated.path("publishedBaselineArtifacts"))
                .extracting(artifact -> artifact.path("status").asText())
                .containsOnly("pending");
        assertThat(generated.path("checks"))
                .extracting(check -> check.path("command").asText())
                .containsExactly(
                        "mvn test",
                        "mvn -Papi-compatibility,major-api-report -DskipTests verify",
                        "mvn -pl reactive-http-client-starter -Papi-compatibility,major-api-report -DskipTests verify",
                        "bash scripts/verify-api-compatibility-fixtures.sh",
                        "git diff --check",
                        "mvn -Pbenchmarks -pl reactive-http-client-benchmarks -am package",
                        "mvn -Pbenchmarks,benchmark-smoke -pl reactive-http-client-benchmarks -am verify",
                        "mvn -Pbenchmarks,benchmark-release -pl reactive-http-client-benchmarks -am verify -Dbenchmark.commit=$(git rev-parse --short HEAD)");
        JsonNode benchmarkEvidence = generated.path("benchmarkEvidence");
        assertThat(benchmarkEvidence.path("manualOrProfileGated").asBoolean()).isTrue();
        assertThat(benchmarkEvidence.path("currentWorkspaceCommand").asText())
                .contains("benchmark-release")
                .contains("-am verify");
        assertThat(benchmarkEvidence.path("publishedStarterCommand").asText())
                .contains("-Pbenchmarks,benchmark-release,benchmark-published-baseline")
                .contains("-Dbenchmark.starter.version=" + pomProperty(pomXml, "api.compatibility.baseline.version"))
                .contains(" clean verify ")
                .doesNotContain(" -am ");
        assertThat(benchmarkEvidence.path("reportDirectory").asText())
                .isEqualTo("reactive-http-client-benchmarks/target/benchmark-reports/");
        assertThat(benchmarkEvidence.path("releaseReport").asText())
                .isEqualTo("reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.md");
        assertThat(benchmarkEvidence.path("publishedStarterReleaseReport").asText())
                .isEqualTo("reactive-http-client-benchmarks/target/benchmark-reports/published-starter-"
                        + pomProperty(pomXml, "api.compatibility.baseline.version") + "/release-jmh.md");
        assertThat(benchmarkEvidence.path("promotedReport").asText())
                .isEqualTo("docs/benchmark-report-" + generated.path("projectVersion").asText() + ".md");
        assertThat(benchmarkEvidence.path("currentCandidateReport").asText())
                .isEqualTo("reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.md");
        assertThat(benchmarkEvidence.path("publishedBaselineReport").asText())
                .isEqualTo("reactive-http-client-benchmarks/target/benchmark-reports/published-starter-"
                        + pomProperty(pomXml, "api.compatibility.baseline.version") + "/release-jmh.md");
        JsonNode comparisonPair = benchmarkEvidence.path("comparisonPair");
        assertThat(comparisonPair.path("currentStarterVersion").asText())
                .isEqualTo(generated.path("projectVersion").asText());
        assertThat(comparisonPair.path("publishedBaselineStarterVersion").asText())
                .isEqualTo(pomProperty(pomXml, "api.compatibility.baseline.version"));
        assertThat(comparisonPair.path("currentCandidateReport").asText())
                .isEqualTo(benchmarkEvidence.path("currentCandidateReport").asText());
        assertThat(comparisonPair.path("publishedBaselineReport").asText())
                .isEqualTo(benchmarkEvidence.path("publishedBaselineReport").asText());
        assertThat(comparisonPair.path("reportsSharePath").asBoolean()).isFalse();
        assertThat(comparisonPair.path("baselineArtifactsMustResolveBeforePromotion").asBoolean()).isTrue();
        assertThat(benchmarkEvidence.path("releaseNoteScenarioNames"))
                .extracting(JsonNode::asText)
                .containsExactly(
                        "Get No Body",
                        "Get Path Query Header",
                        "Post Json",
                        "Response Entity",
                        "Client Error Small Body",
                        "Server Error Small Body",
                        "Problem Detail Small Body");
        assertThat(benchmarkEvidence.path("releaseNoteScenarioRerunCommand").asText())
                .contains("benchmark-release")
                .contains("benchmark.include")
                .contains("clientSideOverhead.*")
                .contains("GetNoBody")
                .contains("GetPathQueryHeader")
                .contains("PostJson")
                .contains("ResponseEntity")
                .contains("ClientErrorSmallBody")
                .contains("ServerErrorSmallBody")
                .contains("starterErrorMappingProblemDetailSmallBody")
                .contains("target/benchmark-reports/release-note")
                .doesNotContain("reactive-http-client-benchmarks/target/benchmarks.jar");
        assertThat(benchmarkEvidence.path("requiresPromotedReportForPerformanceClaims").asBoolean()).isTrue();
        assertThat(benchmarkEvidence.path("pendingEvidenceCanSupportPerformanceClaims").asBoolean()).isFalse();
        JsonNode reviewTriggers = benchmarkEvidence.path("reviewTriggers");
        assertThat(reviewTriggers.path("hardGate").asBoolean()).isFalse();
        assertThat(reviewTriggers.path("normalCiRunsBenchmarks").asBoolean()).isFalse();
        assertThat(reviewTriggers.path("rerunBeforeClaimingTrend").asBoolean()).isTrue();
        assertThat(reviewTriggers.path("latencyPercentChange").asInt()).isEqualTo(20);
        assertThat(reviewTriggers.path("allocationPercentChange").asInt()).isEqualTo(15);
        assertThat(reviewTriggers.path("allocationBytesPerOperationChange").asInt()).isEqualTo(4096);
        assertThat(reviewTriggers.path("optionalFeaturePercentChange").asInt()).isEqualTo(25);
        assertThat(reviewTriggers.path("dimensions"))
                .extracting(JsonNode::asText)
                .containsExactly("average time", "p50", "p95", "p99", "allocation per operation");
        assertThat(reviewTriggers.path("instruction").asText())
                .contains("review trigger")
                .contains("not an automatic release blocker");
        assertThat(benchmarkEvidence.path("refreshRequiredWhen"))
                .extracting(JsonNode::asText)
                .containsExactly(
                        "request construction changes",
                        "observability changes",
                        "resilience wrapping changes",
                        "transport or client-builder changes",
                        "public performance claims");
        assertThat(benchmarkEvidenceMarkdown)
                .startsWith("Benchmark evidence:\n")
                .contains("Promoted report: [Benchmark Report " + generated.path("projectVersion").asText()
                        + "](docs/benchmark-report-" + generated.path("projectVersion").asText() + ".md)")
                .contains("Current candidate command: `" + benchmarkEvidence.path("currentWorkspaceCommand").asText() + "`")
                .contains("Published baseline command: `" + benchmarkEvidence.path("publishedStarterCommand").asText() + "`")
                .contains("Current candidate report: `" + benchmarkEvidence.path("currentCandidateReport").asText() + "`")
                .contains("Published baseline report: `" + benchmarkEvidence.path("publishedBaselineReport").asText() + "`")
                .contains("Scenarios cited: `Get No Body`, `Get Path Query Header`, `Post Json`, `Response Entity`, `Client Error Small Body`, `Server Error Small Body`, `Problem Detail Small Body`")
                .contains("Paste this block only after the promoted report exists")
                .contains("starter `" + generated.path("projectVersion").asText() + "`")
                .contains("published baseline `" + generated.path("apiCompatibilityBaselineVersion").asText() + "`")
                .doesNotContain("smoke-only-jmh")
                .doesNotContain("smokeReport");
    }

    private static List<String> streamText(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    private static String releaseNoteBenchmarkEvidenceMarkdown(JsonNode manifest) {
        JsonNode evidence = manifest.path("benchmarkEvidence");
        String projectVersion = manifest.path("projectVersion").asText();
        String baselineVersion = manifest.path("apiCompatibilityBaselineVersion").asText();
        String scenarios = inlineCodeList(evidence.path("releaseNoteScenarioNames"));

        return "Benchmark evidence:\n"
                + "- Promoted report: [Benchmark Report " + projectVersion + "]("
                + evidence.path("promotedReport").asText() + ")\n"
                + "- Current candidate command: `" + evidence.path("currentWorkspaceCommand").asText() + "`\n"
                + "- Published baseline command: `" + evidence.path("publishedStarterCommand").asText() + "`\n"
                + "- Current candidate report: `" + evidence.path("currentCandidateReport").asText() + "`\n"
                + "- Published baseline report: `" + evidence.path("publishedBaselineReport").asText() + "`\n"
                + "- Scenarios cited: " + scenarios + "\n"
                + "- Note: Paste this block only after the promoted report exists for starter `"
                + projectVersion + "` and published baseline `" + baselineVersion + "`.\n";
    }

    private static String inlineCodeList(JsonNode values) {
        List<String> rendered = new ArrayList<>();
        values.forEach(value -> rendered.add("`" + value.asText() + "`"));
        return String.join(", ", rendered);
    }

    private static void assertPromotedBenchmarkProvenance(String source, String reportDocs) {
        assertThat(machineLocalAbsolutePaths(reportDocs))
                .as(source + " machine-local absolute paths")
                .isEmpty();

        String prefix = "| `benchmarkCommit` |";
        String commitLine = reportDocs.lines()
                .filter(line -> line.startsWith(prefix))
                .findFirst()
                .orElse("");
        assertThat(commitLine)
                .as(source + " benchmarkCommit metadata")
                .isNotBlank();

        String commit = commitLine.substring(prefix.length()).trim();
        if (commit.endsWith("|")) {
            commit = commit.substring(0, commit.length() - 1).trim();
        }
        String normalized = commit.toLowerCase(Locale.ROOT);
        assertThat(normalized)
                .as(source + " benchmarkCommit")
                .doesNotContain("unknown")
                .doesNotContain("dirty");
        assertThat(firstToken(commit))
                .as(source + " benchmarkCommit short Git SHA")
                .matches("[0-9a-fA-F]{7,40}");
    }

    private static List<String> machineLocalAbsolutePaths(String text) {
        List<String> paths = new ArrayList<>();
        appendMatches(UNIX_ABSOLUTE_PATH, text, paths);
        appendMatches(WINDOWS_ABSOLUTE_PATH, text, paths);
        return paths;
    }

    private static void appendMatches(Pattern pattern, String text, List<String> paths) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            paths.add(matcher.group(2));
        }
    }

    private static String firstToken(String value) {
        int end = 0;
        while (end < value.length() && !Character.isWhitespace(value.charAt(end))) {
            end++;
        }
        return value.substring(0, end);
    }

    private static String benchmarkReportWithCommit(String commit) {
        return "# Reactive HTTP Client Benchmark Report\n"
                + "## Environment\n"
                + "| Key | Value |\n"
                + "| --- | --- |\n"
                + "| `benchmarkCommit` | " + commit + " |\n";
    }

    private static String currentReleaseChangelogSection(String changelog, String projectVersion) {
        if (changelog.contains("## [" + projectVersion + "]")) {
            return changelogSection(changelog, projectVersion);
        }
        return changelogSection(changelog, "Unreleased");
    }

    private static List<String> currentReleasePerformanceWordingViolations(Path root,
                                                                           String releaseNotes,
                                                                           String projectVersion) throws IOException {
        List<String> violations = new ArrayList<>();
        if (releaseNotes.contains("benchmark-reports/") || releaseNotes.contains("smoke-only-jmh.md")) {
            violations.add("target-only benchmark path in release notes");
        }
        Matcher linkMatcher = MARKDOWN_LINK.matcher(releaseNotes);
        while (linkMatcher.find()) {
            String target = URLDecoder.decode(linkMatcher.group(1), StandardCharsets.UTF_8);
            if (target.contains("benchmark-reports/") || target.contains("smoke-only-jmh.md")) {
                violations.add("target-only benchmark link: " + target);
            }
            if (target.contains("benchmark-report-")) {
                String pathOnly = target.split("#", 2)[0];
                if (!isExternal(pathOnly) && !Files.exists(root.resolve(pathOnly).normalize())) {
                    violations.add("missing benchmark report: " + target);
                }
            }
        }

        List<String> reportVersions = benchmarkReportVersions(releaseNotes);
        for (String reportVersion : reportVersions) {
            if (!projectVersion.equals(reportVersion)) {
                violations.add("current release performance claim must cite benchmark-report-"
                        + projectVersion + ".md, not benchmark-report-" + reportVersion + ".md");
            }
        }

        if (containsPublicPerformanceClaim(releaseNotes)) {
            String currentReport = "docs/benchmark-report-" + projectVersion + ".md";
            if (!releaseNotes.contains(currentReport)) {
                violations.add("current release performance claim must cite " + currentReport);
            }
            if (!Files.exists(root.resolve(currentReport))) {
                violations.add("missing benchmark report: " + currentReport);
            }
        }
        return violations;
    }

    private static boolean containsPublicPerformanceClaim(String releaseNotes) {
        String normalized = releaseNotes.toLowerCase(Locale.ROOT);
        if (normalized.contains("benchmark report")
                || normalized.contains("performance evidence")
                || normalized.contains("release-quality evidence")
                || normalized.contains("benchmark scenarios")) {
            return true;
        }
        String metric = "\\b(performance|latency|throughput|allocations?|overhead|p50|p95|p99|average time)\\b";
        String movement = "\\b(faster|slower|improv\\w*|regress\\w*|reduc\\w*|lower\\w*|higher\\w*|same)\\b";
        Pattern claim = Pattern.compile(metric + ".*" + movement + "|" + movement + ".*" + metric);
        return normalized.lines().anyMatch(line -> claim.matcher(line).find());
    }

    private static void assertPromotedReportMetadata(Path report, String expectedVersion) throws IOException {
        String reportDocs = Files.readString(report);

        assertThat(report).exists();
        assertThat(report.getFileName().toString())
                .as("promoted benchmark report filename")
                .isEqualTo("benchmark-report-" + expectedVersion + ".md");
        assertThat(reportDocs)
                .contains("- Report version: `" + expectedVersion + "`.")
                .contains("- Starter version under test: `" + expectedVersion + "`.")
                .contains("| `projectVersion` | " + expectedVersion + " |")
                .contains("| `starterVersion` | " + expectedVersion + " |");
    }

    private static void assertCurrentBenchmarkReportReferences(Path markdown, String projectVersion) throws IOException {
        assertBenchmarkReportReferences(markdown.toString(), Files.readString(markdown), projectVersion);
    }

    private static void assertBenchmarkReportReferences(String source, String text, String projectVersion) {
        List<String> versions = benchmarkReportVersions(text);

        assertThat(versions)
                .as(source + " benchmark report references")
                .isNotEmpty()
                .containsOnly(projectVersion);
    }

    private static List<String> benchmarkReportVersions(String text) {
        Matcher matcher = Pattern.compile("benchmark-report-(\\d+\\.\\d+\\.\\d+)\\.md").matcher(text);
        List<String> versions = new ArrayList<>();
        while (matcher.find()) {
            versions.add(matcher.group(1));
        }
        return versions;
    }

    private static String latestPromotedBenchmarkReportVersion(Path root, String projectVersion) throws IOException {
        if (Files.exists(root.resolve("docs/benchmark-report-" + projectVersion + ".md"))) {
            return projectVersion;
        }
        try (Stream<Path> reports = Files.list(root.resolve("docs"))) {
            return reports
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("benchmark-report-\\d+\\.\\d+\\.\\d+\\.md"))
                    .map(name -> name.substring("benchmark-report-".length(), name.length() - ".md".length()))
                    .max(DocumentationReleaseArtifactTest::compareVersions)
                    .orElseThrow(() -> new IllegalStateException("Missing promoted benchmark report"));
        }
    }

    private static int compareVersions(String left, String right) {
        int[] leftParts = versionParts(left);
        int[] rightParts = versionParts(right);
        for (int i = 0; i < leftParts.length; i++) {
            int compared = Integer.compare(leftParts[i], rightParts[i]);
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    private static int[] versionParts(String version) {
        String[] parts = version.split("\\.");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }

    private static List<String> staleBenchmarkReportReferences(Path root, String projectVersion) throws IOException {
        List<Path> releaseDocs;
        try (Stream<Path> docs = Files.walk(root.resolve("docs"))) {
            releaseDocs = Stream.concat(Stream.of(root.resolve("README.md"), root.resolve("CHANGELOG.md")), docs)
                    .filter(path -> path.toString().endsWith(".md"))
                    .sorted()
                    .toList();
        }
        Pattern pattern = Pattern.compile("benchmark-report-(\\d+\\.\\d+\\.\\d+)\\.md");
        List<String> stale = new ArrayList<>();
        for (Path releaseDoc : releaseDocs) {
            String contents = Files.readString(releaseDoc);
            if ("CHANGELOG.md".equals(root.relativize(releaseDoc).toString())) {
                contents = changelogSection(contents, projectVersion);
            }
            Matcher matcher = pattern.matcher(contents);
            while (matcher.find()) {
                if (!projectVersion.equals(matcher.group(1))) {
                    stale.add(root.relativize(releaseDoc) + " -> " + matcher.group());
                }
            }
        }
        return stale;
    }

    private static String changelogSection(String changelog, String version) {
        String heading = "## [" + version + "]";
        int start = changelog.indexOf(heading);
        if (start < 0) {
            throw new IllegalStateException("Missing changelog section for " + version);
        }
        int end = changelog.indexOf("\n## [", start + heading.length());
        return end < 0 ? changelog.substring(start) : changelog.substring(start, end);
    }

    private static List<String> brokenLocalMarkdownLinks(Path root) throws IOException {
        List<String> brokenLinks = new ArrayList<>();
        try (Stream<Path> files = markdownFiles(root)) {
            for (Path markdown : files.toList()) {
                Matcher matcher = MARKDOWN_LINK.matcher(markdownWithoutFencedCode(Files.readString(markdown)));
                while (matcher.find()) {
                    String target = matcher.group(1);
                    if (isExternal(target)) {
                        continue;
                    }
                    String[] parts = target.split("#", 2);
                    String pathOnly = parts[0];
                    String anchor = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
                    Path resolved = pathOnly.isBlank()
                            ? markdown
                            : markdown.getParent()
                                    .resolve(URLDecoder.decode(pathOnly, StandardCharsets.UTF_8))
                                    .normalize();
                    if (!Files.exists(resolved)) {
                        brokenLinks.add(root.relativize(markdown) + " -> " + target);
                        continue;
                    }
                    if (!anchor.isBlank() && !markdownAnchors(resolved).contains(anchor)) {
                        brokenLinks.add(root.relativize(markdown) + " -> " + target + " (missing anchor)");
                    }
                }
            }
        }
        return brokenLinks;
    }

    private static Stream<Path> markdownFiles(Path root) throws IOException {
        return Stream.of(
                        Stream.of(root.resolve("README.md"), root.resolve("CHANGELOG.md")),
                        Files.walk(root.resolve("docs")),
                        Files.walk(root.resolve("roadmaps")))
                .flatMap(stream -> stream)
                .filter(path -> path.toString().endsWith(".md"));
    }

    private static Stream<Path> publicMarkdownFiles(Path root) throws IOException {
        return Stream.concat(
                        Stream.of(root.resolve("README.md"), root.resolve("CHANGELOG.md")),
                        Files.walk(root.resolve("docs")))
                .filter(path -> path.toString().endsWith(".md"));
    }

    private static Set<String> markdownAnchors(Path markdown) throws IOException {
        Set<String> anchors = new HashSet<>();
        Map<String, Integer> occurrences = new HashMap<>();
        for (String line : Files.readAllLines(markdown)) {
            if (!line.startsWith("#")) {
                continue;
            }
            String heading = line.replaceFirst("^#{1,6}\\s+", "").replaceFirst("\\s+#*$", "");
            if (heading.isBlank()) {
                continue;
            }
            String anchor = markdownAnchor(heading);
            int duplicate = occurrences.merge(anchor, 1, Integer::sum);
            anchors.add(duplicate == 1 ? anchor : anchor + "-" + (duplicate - 1));
        }
        return anchors;
    }

    private static String markdownAnchor(String heading) {
        String normalized = heading.toLowerCase(Locale.ROOT);
        StringBuilder anchor = new StringBuilder();
        boolean previousDash = false;
        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            if (Character.isLetterOrDigit(current)) {
                anchor.append(current);
                previousDash = false;
            }
            else if (Character.isWhitespace(current) || current == '-') {
                if (!previousDash && anchor.length() > 0) {
                    anchor.append('-');
                    previousDash = true;
                }
            }
        }
        while (anchor.length() > 0 && anchor.charAt(anchor.length() - 1) == '-') {
            anchor.setLength(anchor.length() - 1);
        }
        return anchor.toString();
    }

    private static String releaseNoteBenchmarkBlock(String markdown) {
        int heading = markdown.indexOf("## Release-Note Benchmark Evidence");
        if (heading < 0) {
            throw new IllegalStateException("Missing release-note benchmark evidence section");
        }
        int fenceStart = markdown.indexOf("```markdown", heading);
        if (fenceStart < 0) {
            throw new IllegalStateException("Missing release-note benchmark evidence block");
        }
        int blockStart = markdown.indexOf('\n', fenceStart) + 1;
        int fenceEnd = markdown.indexOf("```", blockStart);
        if (fenceEnd < 0) {
            throw new IllegalStateException("Unclosed release-note benchmark evidence block");
        }
        return markdown.substring(blockStart, fenceEnd);
    }

    private static String markdownWithoutFencedCode(String markdown) {
        StringBuilder out = new StringBuilder(markdown.length());
        boolean fenced = false;
        for (String line : markdown.split("\\R", -1)) {
            if (line.startsWith("```")) {
                fenced = !fenced;
                out.append('\n');
                continue;
            }
            if (!fenced) {
                out.append(line);
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static boolean isExternal(String target) {
        return target.startsWith("http://")
                || target.startsWith("https://")
                || target.startsWith("mailto:");
    }

    private static void assertVersionSnippets(Path markdown, String projectVersion) throws IOException {
        Matcher matcher = PROJECT_VERSION_SNIPPET.matcher(Files.readString(markdown));
        List<String> versions = new ArrayList<>();
        while (matcher.find()) {
            versions.add(matcher.group(1));
        }

        assertThat(versions)
                .as("%s reactive-http-client dependency snippets", markdown.getFileName())
                .isNotEmpty()
                .containsOnly(projectVersion);
    }

    private static String dependencyBlock(String pom, String artifactId) {
        Matcher matcher = Pattern.compile("<dependency>\\s*(?:(?!</dependency>).)*?<artifactId>"
                        + Pattern.quote(artifactId) + "</artifactId>(?:(?!</dependency>).)*?</dependency>",
                Pattern.DOTALL)
                .matcher(pom);
        if (!matcher.find()) {
            throw new IllegalStateException("Missing benchmark dependency: " + artifactId);
        }
        return matcher.group();
    }

    private static String projectVersion(Path pom) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var document = factory.newDocumentBuilder().parse(pom.toFile());
        return document.getElementsByTagName("version").item(0).getTextContent();
    }

    private static Map<String, Object> releaseEvidenceManifest(Path pom) throws Exception {
        String pomXml = Files.readString(pom);
        String projectVersion = projectVersion(pom);
        String baselineVersion = pomProperty(pomXml, "api.compatibility.baseline.version");
        LinkedHashMap<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("projectVersion", projectVersion);
        manifest.put("apiCompatibilityBaselineVersion", baselineVersion);
        manifest.put("apiCompatibilityBaselineMatchesProjectVersion", projectVersion.equals(baselineVersion));
        manifest.put("javaVersion", System.getProperty("java.version"));
        manifest.put("javaBaseline", pomProperty(pomXml, "java.version"));
        manifest.put("springBootBaseline", pomProperty(pomXml, "spring-boot.version"));
        manifest.put("dependencyBaselineReview", dependencyBaselineReview(pomXml));
        Map<String, Object> benchmarkEvidence = benchmarkEvidence(projectVersion, baselineVersion);
        List<Map<String, String>> publishedBaselineArtifacts = publishedBaselineArtifacts(baselineVersion);
        List<Map<String, String>> checks = List.of(
                check("mvn test", "pass", "Generated by DocumentationReleaseArtifactTest during the current test run."),
                check("mvn -Papi-compatibility,major-api-report -DskipTests verify", "pending", "Run before release."),
                check("mvn -pl reactive-http-client-starter -Papi-compatibility,major-api-report -DskipTests verify", "pending",
                        "Run before release to exercise module-scoped compatibility guard."),
                check("bash scripts/verify-api-compatibility-fixtures.sh", "pending", "Run before release."),
                check("git diff --check", "pending", "Run before release."),
                check("mvn -Pbenchmarks -pl reactive-http-client-benchmarks -am package", "pending",
                        "Lightweight benchmark compile check; does not run JMH."),
                check("mvn -Pbenchmarks,benchmark-smoke -pl reactive-http-client-benchmarks -am verify", "pending",
                        "Harness smoke only; do not publish these numbers."),
                check("mvn -Pbenchmarks,benchmark-release -pl reactive-http-client-benchmarks -am verify -Dbenchmark.commit=$(git rev-parse --short HEAD)", "pending",
                        "Run when request-path behavior changed or release notes make performance claims."));
        Map<String, Object> readiness = releaseReadiness(pom.getParent(), projectVersion, baselineVersion, benchmarkEvidence,
                publishedBaselineArtifacts, checks);
        manifest.put("readiness", readiness);
        manifest.put("releasePrepChecklist", releasePrepChecklist(pom.getParent(), projectVersion, baselineVersion, readiness,
                benchmarkEvidence, publishedBaselineArtifacts, checks));
        manifest.put("benchmarkDependencyManagement", benchmarkDependencyManagement(pomXml));
        manifest.put("publishedBaselineArtifacts", publishedBaselineArtifacts);
        manifest.put("benchmarkEvidence", benchmarkEvidence);
        manifest.put("checks", checks);
        return manifest;
    }

    private static Map<String, Object> releaseReadiness(Path root,
                                                        String projectVersion,
                                                        String baselineVersion,
                                                        Map<String, Object> benchmarkEvidence,
                                                        List<Map<String, String>> publishedBaselineArtifacts,
                                                        List<Map<String, String>> checks) throws IOException {
        String promotedReport = (String) benchmarkEvidence.get("promotedReport");
        String promotedReportVersion = latestPromotedBenchmarkReportVersion(root, projectVersion);
        List<String> pendingManualCommands = new ArrayList<>(checks.stream()
                .filter(check -> "pending".equals(check.get("status")))
                .map(check -> check.get("command"))
                .toList());
        publishedBaselineArtifacts.stream()
                .filter(artifact -> "pending".equals(artifact.get("status")))
                .map(artifact -> artifact.get("resolutionCommand"))
                .forEach(pendingManualCommands::add);
        pendingManualCommands.add((String) benchmarkEvidence.get("publishedStarterCommand"));
        List<String> pendingBenchmarkCommands = new ArrayList<>(pendingManualCommands.stream()
                .filter(command -> command.contains("benchmark"))
                .toList());
        List<String> pendingCompatibilityCommands = pendingManualCommands.stream()
                .filter(command -> command.contains("api-compatibility")
                        || command.contains("verify-api-compatibility-fixtures"))
                .toList();
        boolean configurationReferenceCurrent = Files.readString(root.resolve("docs/configuration-properties.md"))
                .equals(configurationReferenceMarkdown(configurationMetadata(root)));
        List<String> brokenLinks = brokenLocalMarkdownLinks(root);
        List<String> staleBenchmarkLinks = staleBenchmarkReportReferences(root, promotedReportVersion);

        LinkedHashMap<String, Object> readiness = new LinkedHashMap<>();
        readiness.put("projectVersion", projectVersion);
        readiness.put("apiCompatibilityBaselineVersion", baselineVersion);
        readiness.put("apiCompatibilityBaselineMatchesProjectVersion", projectVersion.equals(baselineVersion));
        readiness.put("generatedTestEvidence", readinessStatus("pass",
                "Generated by DocumentationReleaseArtifactTest in target/release-evidence/."));
        readiness.put("manualReleaseEvidence", readinessManualStatus(pendingManualCommands));
        readiness.put("manualBenchmarkEvidence", readinessManualStatus(pendingBenchmarkCommands));
        readiness.put("manualCompatibilityEvidence", readinessManualStatus(pendingCompatibilityCommands));
        readiness.put("promotedBenchmarkReport", readinessPathStatus(promotedReport,
                Files.exists(root.resolve(promotedReport)) ? "present" : "missing"));
        readiness.put("configurationReference", readinessPathStatus("docs/configuration-properties.md",
                configurationReferenceCurrent ? "current" : "stale"));
        readiness.put("markdownLinks", readinessListStatus(brokenLinks.isEmpty() ? "pass" : "fail", "brokenLinks", brokenLinks));
        readiness.put("staleBenchmarkReportLinks", readinessListStatus(
                staleBenchmarkLinks.isEmpty() ? "pass" : "fail", "references", staleBenchmarkLinks));
        readiness.put("releaseEvidenceDirectory", "target/release-evidence/");
        readiness.put("targetOnlyEvidence", Map.of(
                "directory", "target/release-evidence/",
                "sourceControlled", false,
                "commitGeneratedEvidence", false));
        return readiness;
    }

    private static Map<String, Object> releasePrepChecklist(Path root,
                                                            String projectVersion,
                                                            String baselineVersion,
                                                            Map<String, Object> readiness,
                                                            Map<String, Object> benchmarkEvidence,
                                                            List<Map<String, String>> publishedBaselineArtifacts,
                                                            List<Map<String, String>> checks) throws IOException {
        String changelog = Files.readString(root.resolve("CHANGELOG.md"));
        String unreleasedCompareVersion = changelog.contains("## [" + projectVersion + "] - ")
                ? projectVersion
                : baselineVersion;
        String expectedUnreleasedCompareLink = "[Unreleased]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v"
                + unreleasedCompareVersion + "...HEAD";
        boolean changelogCurrent = changelog.contains("## [Unreleased]")
                && changelog.contains(expectedUnreleasedCompareLink);
        boolean versionSnippetsCurrent = versionSnippetsMatch(root.resolve("README.md"), projectVersion)
                && versionSnippetsMatch(root.resolve("docs/01-quick-start.md"), projectVersion);

        List<String> publishedBaselineCommands = publishedBaselineArtifacts.stream()
                .map(artifact -> artifact.get("resolutionCommand"))
                .toList();
        List<String> compatibilityCommands = checks.stream()
                .map(check -> check.get("command"))
                .filter(command -> command.contains("api-compatibility")
                        || command.contains("verify-api-compatibility-fixtures"))
                .toList();
        List<String> benchmarkCommands = new ArrayList<>(checks.stream()
                .map(check -> check.get("command"))
                .filter(command -> command.contains("benchmark"))
                .toList());
        benchmarkCommands.add((String) benchmarkEvidence.get("publishedStarterCommand"));

        String configurationReferenceStatus = readinessNestedStatus(readiness, "configurationReference");
        String markdownLinksStatus = readinessNestedStatus(readiness, "markdownLinks");
        String staleBenchmarkLinksStatus = readinessNestedStatus(readiness, "staleBenchmarkReportLinks");
        String promotedReportStatus = readinessNestedStatus(readiness, "promotedBenchmarkReport");
        boolean generatedDocsAndLinksPass = "current".equals(configurationReferenceStatus)
                && "pass".equals(markdownLinksStatus)
                && "pass".equals(staleBenchmarkLinksStatus);

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(checklistItem("changelog-section", "Changelog section",
                changelogCurrent ? "current" : "stale", Map.of(
                        "path", "CHANGELOG.md",
                        "expectedUnreleasedCompareLink", expectedUnreleasedCompareLink)));
        items.add(checklistItem("version-snippets", "README and quick-start version snippets",
                versionSnippetsCurrent ? "current" : "stale", Map.of(
                        "paths", List.of("README.md", "docs/01-quick-start.md"),
                        "expectedVersion", projectVersion)));
        items.add(checklistItem("published-baseline-artifacts", "Published baseline artifact resolution",
                "pending", Map.of("commands", publishedBaselineCommands)));
        items.add(checklistItem("api-compatibility", "API compatibility evidence",
                "pending", Map.of("commands", compatibilityCommands)));
        items.add(checklistItem("benchmark-evidence", "Benchmark evidence",
                "pending", Map.of(
                        "commands", benchmarkCommands,
                        "currentCandidateReport", benchmarkEvidence.get("currentCandidateReport"),
                        "publishedBaselineReport", benchmarkEvidence.get("publishedBaselineReport"))));
        items.add(checklistItem("promoted-benchmark-report", "Promoted benchmark report",
                promotedReportStatus, Map.of("path", benchmarkEvidence.get("promotedReport"))));
        items.add(checklistItem("generated-docs-and-links", "Generated docs and Markdown links",
                generatedDocsAndLinksPass ? "pass" : "fail", Map.of(
                        "configurationReference", configurationReferenceStatus,
                        "markdownLinks", markdownLinksStatus,
                        "staleBenchmarkReportLinks", staleBenchmarkLinksStatus)));
        items.add(checklistItem("target-only-evidence", "Target-only release evidence",
                "pass", Map.of(
                        "directory", "target/release-evidence/",
                        "sourceControlled", false,
                        "commitGeneratedEvidence", false)));

        LinkedHashMap<String, Object> checklist = new LinkedHashMap<>();
        checklist.put("status", "pending");
        checklist.put("projectVersion", projectVersion);
        checklist.put("apiCompatibilityBaselineVersion", baselineVersion);
        checklist.put("items", items);
        checklist.put("manualCommands", readinessPendingCommands(readiness, "manualReleaseEvidence"));
        checklist.put("note", "Generated summary only; run the listed manual commands before release.");
        return checklist;
    }

    private static Map<String, Object> checklistItem(String id, String title, String status, Map<String, Object> details) {
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("title", title);
        item.put("status", status);
        item.putAll(details);
        return item;
    }

    private static String readinessNestedStatus(Map<String, Object> readiness, String key) {
        Object value = readiness.get(key);
        if (value instanceof Map<?, ?> map && map.get("status") != null) {
            return String.valueOf(map.get("status"));
        }
        return "unknown";
    }

    private static List<String> readinessPendingCommands(Map<String, Object> readiness, String key) {
        Object value = readiness.get(key);
        if (!(value instanceof Map<?, ?> map) || !(map.get("pendingCommands") instanceof List<?> commands)) {
            return List.of();
        }
        return commands.stream().map(String::valueOf).toList();
    }

    private static boolean versionSnippetsMatch(Path markdown, String projectVersion) throws IOException {
        Matcher matcher = PROJECT_VERSION_SNIPPET.matcher(Files.readString(markdown));
        boolean found = false;
        while (matcher.find()) {
            found = true;
            if (!projectVersion.equals(matcher.group(1))) {
                return false;
            }
        }
        return found;
    }

    private static Map<String, Object> readinessStatus(String status, String note) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("status", status);
        value.put("note", note);
        return value;
    }

    private static Map<String, Object> readinessManualStatus(List<String> pendingCommands) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("status", pendingCommands.isEmpty() ? "complete" : "pending");
        value.put("pendingCommands", pendingCommands);
        return value;
    }

    private static Map<String, Object> readinessPathStatus(String path, String status) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("path", path);
        value.put("status", status);
        return value;
    }

    private static Map<String, Object> readinessListStatus(String status, String field, List<String> values) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("status", status);
        value.put(field, values);
        return value;
    }

    private static Map<String, Object> benchmarkEvidence(String projectVersion, String baselineVersion) {
        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("manualOrProfileGated", true);
        evidence.put("currentWorkspaceCommand",
                "mvn -Pbenchmarks,benchmark-release -pl reactive-http-client-benchmarks -am verify -Dbenchmark.commit=$(git rev-parse --short HEAD)");
        evidence.put("publishedStarterCommand",
                "mvn -Pbenchmarks,benchmark-release,benchmark-published-baseline -pl reactive-http-client-benchmarks clean verify -Dbenchmark.starter.version="
                        + baselineVersion + " -Dbenchmark.commit=" + baselineVersion);
        evidence.put("reportDirectory", "reactive-http-client-benchmarks/target/benchmark-reports/");
        evidence.put("smokeReport", "reactive-http-client-benchmarks/target/benchmark-reports/smoke-only-jmh.md");
        evidence.put("releaseReport", "reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.md");
        evidence.put("publishedStarterReleaseReport", "reactive-http-client-benchmarks/target/benchmark-reports/published-starter-"
                + baselineVersion + "/release-jmh.md");
        evidence.put("promotedReport", "docs/benchmark-report-" + projectVersion + ".md");
        evidence.put("currentCandidateReport", "reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.md");
        evidence.put("publishedBaselineReport", "reactive-http-client-benchmarks/target/benchmark-reports/published-starter-"
                + baselineVersion + "/release-jmh.md");
        evidence.put("comparisonPair", comparisonPair(projectVersion, baselineVersion,
                (String) evidence.get("currentCandidateReport"),
                (String) evidence.get("publishedBaselineReport"),
                (String) evidence.get("promotedReport")));
        evidence.put("releaseNoteScenarioNames", List.of(
                "Get No Body",
                "Get Path Query Header",
                "Post Json",
                "Response Entity",
                "Client Error Small Body",
                "Server Error Small Body",
                "Problem Detail Small Body"));
        evidence.put("releaseNoteScenarioRerunCommand",
                "mvn -Pbenchmarks,benchmark-release -pl reactive-http-client-benchmarks -am verify "
                        + "-Dbenchmark.commit=$(git rev-parse --short HEAD) "
                        + "-Dbenchmark.include='.*(clientSideOverhead.*(GetNoBody|"
                        + "GetPathQueryHeader|PostJson|ResponseEntity|"
                        + "ClientErrorSmallBody|ServerErrorSmallBody)|"
                        + "starterErrorMappingProblemDetailSmallBody).*' "
                        + "-Dbenchmark.result.dir=target/benchmark-reports/release-note");
        evidence.put("requiresPromotedReportForPerformanceClaims", true);
        evidence.put("pendingEvidenceCanSupportPerformanceClaims", false);
        evidence.put("reviewTriggers", benchmarkReviewTriggers());
        evidence.put("refreshRequiredWhen", List.of(
                "request construction changes",
                "observability changes",
                "resilience wrapping changes",
                "transport or client-builder changes",
                "public performance claims"));
        evidence.put("releaseNotesInstruction",
                "Attach or link the promoted report when publishing performance claims; never publish smoke-only numbers.");
        return evidence;
    }

    private static Map<String, Object> benchmarkReviewTriggers() {
        LinkedHashMap<String, Object> triggers = new LinkedHashMap<>();
        triggers.put("hardGate", false);
        triggers.put("normalCiRunsBenchmarks", false);
        triggers.put("rerunBeforeClaimingTrend", true);
        triggers.put("latencyPercentChange", 20);
        triggers.put("allocationPercentChange", 15);
        triggers.put("allocationBytesPerOperationChange", 4096);
        triggers.put("optionalFeaturePercentChange", 25);
        triggers.put("dimensions", List.of("average time", "p50", "p95", "p99", "allocation per operation"));
        triggers.put("instruction",
                "Treat threshold crossings as review triggers, not an automatic release blocker; rerun current and baseline reports on the same machine before publishing performance claims.");
        return triggers;
    }

    private static Map<String, Object> comparisonPair(String projectVersion, String baselineVersion,
                                                      String currentReport, String baselineReport,
                                                      String promotedReport) {
        LinkedHashMap<String, Object> pair = new LinkedHashMap<>();
        pair.put("currentStarterVersion", projectVersion);
        pair.put("publishedBaselineStarterVersion", baselineVersion);
        pair.put("currentCandidateReport", currentReport);
        pair.put("publishedBaselineReport", baselineReport);
        pair.put("promotedReport", promotedReport);
        pair.put("reportsSharePath", currentReport.equals(baselineReport));
        pair.put("baselineArtifactsMustResolveBeforePromotion", true);
        pair.put("comparisonNote", "Compare starter " + projectVersion + " current-candidate report with starter "
                + baselineVersion + " published-baseline report; do not reuse one report for both labels.");
        return pair;
    }

    private static Map<String, Object> dependencyBaselineReview(String pomXml) {
        String springBootVersion = pomProperty(pomXml, "spring-boot.version");
        String resilience4jVersion = pomProperty(pomXml, "resilience4j.version");
        LinkedHashMap<String, Object> review = new LinkedHashMap<>();
        review.put("javaBaseline", pomProperty(pomXml, "java.version"));
        review.put("springBootBaseline", springBootVersion);
        review.put("springWebFluxVersionSource", "spring-boot-dependencies:" + springBootVersion);
        review.put("reactorNettyVersionSource", "spring-boot-dependencies:" + springBootVersion);
        review.put("micrometerVersionSource", "spring-boot-dependencies:" + springBootVersion);
        review.put("openTelemetryVersionSource", "spring-boot-dependencies:" + springBootVersion);
        review.put("resilience4jVersion", resilience4jVersion);
        review.put("resilience4jVersionSource", "resilience4j-bom:" + resilience4jVersion);
        review.put("testDependencyVersionSource", "spring-boot-dependencies:" + springBootVersion
                + "; explicit test-only pins stay local to module POMs");
        review.put("jmhVersion", pomProperty(pomXml, "jmh.version"));
        review.put("reviewedAt", "2026-07-10");
        review.put("minimumTestedSpringBootVersion", "3.5.0");
        review.put("springBootPatchCandidate", "3.5.16");
        review.put("candidateDecision", "adopted-in-2.14.1");
        review.put("candidateManagedVersions", Map.of(
                "springWebFlux", "6.2.19",
                "reactorNettyHttp", "1.2.18",
                "nettyHttpCodec", "4.1.135.Final",
                "micrometerCore", "1.15.12",
                "openTelemetryApi", "1.49.0",
                "jacksonDatabind", "2.21.4",
                "junitJupiter", "5.12.2",
                "mockitoCore", "5.17.0"));
        review.put("compatibilityNeutralUpgrades", List.of(
                "Spring Boot patch upgrades within the documented baseline line",
                "managed WebFlux, Reactor Netty, Micrometer, and OpenTelemetry patch movement from that Boot line",
                "Resilience4j patch-compatible updates with operator tests",
                "test-only dependency updates that do not change published helper APIs",
                "benchmark harness updates that keep metadata recording intact"));
        review.put("minorReleaseUpgrades", List.of(
                "raising the Java baseline",
                "adding a new Spring Boot minor line",
                "making optional integrations required runtime dependencies",
                "behavior-changing Resilience4j baseline updates",
                "runtime Reactor Netty, Micrometer, or OpenTelemetry upgrades outside the managed Spring Boot baseline"));
        review.put("baselineUpgradePolicy",
                "Do not mix dependency-baseline upgrades with unrelated feature work; update release docs, generated evidence, configuration metadata, and benchmark metadata together.");
        return review;
    }

    private static Map<String, Object> benchmarkDependencyManagement(String pomXml) {
        LinkedHashMap<String, Object> dependencyManagement = new LinkedHashMap<>();
        dependencyManagement.put("source", "root spring-boot-dependencies BOM");
        dependencyManagement.put("springBootVersion", pomProperty(pomXml, "spring-boot.version"));
        dependencyManagement.put("springWebFluxVersionSource", "resolved from spring-boot-dependencies");
        dependencyManagement.put("reactorNettyVersionSource", "resolved from spring-boot-dependencies");
        dependencyManagement.put("micrometerVersionSource", "resolved from spring-boot-dependencies");
        dependencyManagement.put("resilience4jVersion", pomProperty(pomXml, "resilience4j.version"));
        dependencyManagement.put("resilience4jVersionSource", "resolved from resilience4j-bom");
        dependencyManagement.put("jmhVersion", pomProperty(pomXml, "jmh.version"));
        dependencyManagement.put("benchmarkModuleUsesStarterParent", true);
        return dependencyManagement;
    }

    private static List<Map<String, String>> publishedBaselineArtifacts(String baselineVersion) {
        return List.of(
                baselineArtifact("reactive-http-client-starter", baselineVersion),
                baselineArtifact("reactive-http-client-test", baselineVersion),
                baselineArtifact("reactive-http-client-otel", baselineVersion));
    }

    private static Map<String, String> baselineArtifact(String artifactId, String baselineVersion) {
        String gav = "io.github.huynhngochuyhoang:" + artifactId + ":" + baselineVersion;
        LinkedHashMap<String, String> artifact = new LinkedHashMap<>();
        artifact.put("artifact", gav);
        artifact.put("status", "pending");
        artifact.put("resolutionCommand", "mvn dependency:get -Dartifact=" + gav);
        artifact.put("note", "Run before release; unresolved published artifacts are release blockers.");
        return artifact;
    }

    private static Map<String, String> check(String command, String status, String note) {
        LinkedHashMap<String, String> check = new LinkedHashMap<>();
        check.put("command", command);
        check.put("status", status);
        check.put("note", note);
        return check;
    }

    private static List<String> apiCompatibilityIncludes(String pomXml) {
        int profileStart = pomXml.indexOf("<id>api-compatibility</id>");
        if (profileStart < 0) {
            throw new IllegalStateException("Missing api-compatibility profile");
        }
        int includesStart = pomXml.indexOf("<includes>", profileStart);
        int includesEnd = pomXml.indexOf("</includes>", includesStart);
        if (includesStart < 0 || includesEnd < includesStart) {
            throw new IllegalStateException("Missing api-compatibility includes");
        }
        Matcher matcher = Pattern.compile("<include>([^<]+)</include>")
                .matcher(pomXml.substring(includesStart, includesEnd));
        List<String> includes = new ArrayList<>();
        while (matcher.find()) {
            includes.add(matcher.group(1));
        }
        return includes;
    }

    private record PublicSurfaceRow(String includePattern, String supportStatus) {
    }

    private static List<PublicSurfaceRow> documentedPublicSurfaceRows(String markdown) {
        String section = markdownSection(markdown, "### Documented public surface map",
                "### Constructor and mutable model policy");
        List<PublicSurfaceRow> rows = new ArrayList<>();
        for (String line : section.split("\\R")) {
            if (!line.startsWith("| `")) {
                continue;
            }
            String[] columns = line.split("\\|", -1);
            if (columns.length < 6) {
                throw new IllegalStateException("Malformed public surface row: " + line);
            }
            rows.add(new PublicSurfaceRow(stripBackticks(columns[1].trim()), columns[4].trim()));
        }
        return rows;
    }

    private static String stripBackticks(String value) {
        if (value.startsWith("`") && value.endsWith("`")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String markdownSection(String markdown, String heading, String nextHeading) {
        int start = markdown.indexOf(heading);
        if (start < 0) {
            throw new IllegalStateException("Missing Markdown heading: " + heading);
        }
        int end = markdown.indexOf(nextHeading, start + heading.length());
        if (end < 0) {
            throw new IllegalStateException("Missing Markdown heading: " + nextHeading);
        }
        return markdown.substring(start, end);
    }

    private static String pomProperty(String pomXml, String property) {
        Matcher matcher = Pattern.compile("<" + Pattern.quote(property) + ">([^<]+)</" + Pattern.quote(property) + ">")
                .matcher(pomXml);
        if (!matcher.find()) {
            throw new IllegalStateException("Missing Maven property: " + property);
        }
        return matcher.group(1);
    }

    private static List<JsonNode> configurationMetadata(Path root) throws IOException {
        return List.of(
                metadata(root, "reactive-http-client-starter/src/main/resources/META-INF/additional-spring-configuration-metadata.json"),
                metadata(root, "reactive-http-client-otel/src/main/resources/META-INF/additional-spring-configuration-metadata.json"));
    }

    private static JsonNode metadata(Path root, String path) throws IOException {
        return OBJECT_MAPPER.readTree(root.resolve(path).toFile());
    }

    private static String configurationReferenceMarkdown(List<JsonNode> metadataFiles) throws IOException {
        StringWriter out = new StringWriter();
        out.append("# Configuration Properties\n\n");
        out.append("> Generated from:\n");
        out.append("> - `reactive-http-client-starter/src/main/resources/META-INF/additional-spring-configuration-metadata.json`\n");
        out.append("> - `reactive-http-client-otel/src/main/resources/META-INF/additional-spring-configuration-metadata.json`\n");
        out.append("> `DocumentationReleaseArtifactTest` fails when this file drifts from metadata.\n\n");
        out.append("| Property | Type | Default | Description | Deprecated |\n");
        out.append("|---|---|---|---|---|\n");

        List<JsonNode> properties = new ArrayList<>();
        for (JsonNode metadata : metadataFiles) {
            metadata.path("properties").forEach(properties::add);
        }
        properties.sort(Comparator.comparing(property -> property.path("name").asText()));

        for (JsonNode property : properties) {
            out.append("| `").append(escapeCell(property.path("name").asText())).append("` ");
            out.append("| `").append(escapeCell(property.path("type").asText())).append("` ");
            out.append("| ").append(escapeCell(defaultValue(property))).append(" ");
            out.append("| ").append(escapeCell(property.path("description").asText())).append(" ");
            out.append("| ").append(escapeCell(deprecation(property))).append(" |\n");
        }
        return out.toString();
    }

    private static String defaultValue(JsonNode property) throws IOException {
        if (!property.has("defaultValue")) {
            return "";
        }
        return "`" + OBJECT_MAPPER.writeValueAsString(property.get("defaultValue")) + "`";
    }

    private static String deprecation(JsonNode property) {
        JsonNode deprecation = property.path("deprecation");
        if (deprecation.isMissingNode()) {
            return "";
        }
        String replacement = deprecation.path("replacement").asText("");
        if (replacement.isBlank()) {
            return deprecation.path("level").asText("warning");
        }
        return deprecation.path("level").asText("warning") + "; replacement: `" + replacement + "`";
    }

    private static String escapeCell(String value) {
        return value
                .replace("\r", "")
                .replace("\n", " ")
                .replace("|", "\\|");
    }

    private static Path projectRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (Files.exists(cwd.resolve("README.md")) && Files.isDirectory(cwd.resolve("docs"))) {
            return cwd;
        }
        return cwd.getParent();
    }
}
