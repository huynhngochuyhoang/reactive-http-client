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
import java.time.Duration;
import java.time.Instant;
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
    private static final Set<String> SENSITIVE_SUPPORT_FIXTURE_FIELD_FRAGMENTS = Set.of(
            "argument", "header", "body", "bodies", "url", "identity", "identities",
            "authorization", "credential", "tenant", "cookie", "secret", "token",
            "exception", "message",
            "key", "digest", "value", "payload",
            "path", "query", "uri", "requesttarget", "requestvariant");
    private static final Pattern SUPPORT_FIXTURE_REQUEST_TARGET_VALUE = Pattern.compile(
            "(?i)^(?:\\*|[a-z][a-z0-9+.-]*:\\S+|(?:/|\\./|\\.\\./)\\S*"
                    + "|\\S*\\?[A-Za-z0-9_.%~-]+(?:=[^\\s&]*)?(?:&[^\\s]*)?)$");
    private static final Pattern SUPPORT_FIXTURE_EMBEDDED_HTTP_REQUEST_LINE = Pattern.compile(
            "(?i)(?:^|\\s)(?:GET|HEAD|POST|PUT|PATCH|DELETE|OPTIONS|TRACE|CONNECT)"
                    + "\\s+\\S+\\s+HTTP/[0-9](?:\\.[0-9])?(?:$|\\s)");
    private static final Pattern SUPPORT_FIXTURE_QUERY_VALUE = Pattern.compile(
            "^(?:\\?[A-Za-z0-9_.%~-]+(?:=[^\\s&]*)?"
                    + "(?:&[A-Za-z0-9_.%~-]+(?:=[^\\s&]*)?)*"
                    + "|[A-Za-z0-9_.%~-]+=[^\\s&]*"
                    + "(?:&[A-Za-z0-9_.%~-]+(?:=[^\\s&]*)?)*)$");
    private static final Pattern SUPPORT_FIXTURE_AUTHORITY_VALUE = Pattern.compile(
            "^(?:[A-Za-z0-9._~-]+@)?(?:[A-Za-z0-9.-]+|\\[[0-9A-Fa-f:]+]):[0-9]{1,5}$");
    private static final Pattern SUPPORT_FIXTURE_ROOTLESS_PATH_VALUE = Pattern.compile(
            "^[A-Za-z0-9._~!$&'()*+,;=:@%-]+"
                    + "(?:/[A-Za-z0-9._~!$&'()*+,;=:@%/?-]+)+$");
    private static final Set<String> SUPPORT_FIXTURE_ALLOWED_SLASH_VALUES =
            Set.of("HTTP/1.1", "HTTP/2");
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
    void roadmapArchiveIsContiguousLinkedAndStatusConsistent() throws IOException {
        Path archive = projectRoot().resolve("roadmaps");
        String index = Files.readString(archive.resolve("README.md"));
        List<Integer> versions;
        try (Stream<Path> paths = Files.list(archive)) {
            versions = paths
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("v\\d+"))
                    .map(name -> Integer.parseInt(name.substring(1)))
                    .sorted()
                    .toList();
        }

        List<Integer> expectedVersions = new ArrayList<>();
        for (int version = 1; version <= 29; version++) {
            expectedVersions.add(version);
        }
        assertThat(versions).as("contiguous V1-V29 roadmap directories").isEqualTo(expectedVersions);
        assertThat(index)
                .contains("acceptance boxes preserve the proposal")
                .contains("V2 predates the separate execution-checklist convention")
                .contains("V1-V28 are completed release records. V29 is the only active")
                .contains("execution roadmap.");

        for (int version : versions) {
            Path directory = archive.resolve("v" + version);
            Path roadmap = directory.resolve("ROADMAP.md");
            Path checklist = directory.resolve("CHECKLIST.md");
            String roadmapTarget = "(v" + version + "/ROADMAP.md)";
            String checklistTarget = "(v" + version + "/CHECKLIST.md)";
            String indexRow = index.lines()
                    .filter(line -> line.startsWith("| V" + version + " |"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing V" + version + " roadmap index row"));

            assertThat(roadmap).as("V%s roadmap", version).exists();
            assertThat(indexRow).contains(roadmapTarget);

            String roadmapStatus = Files.readString(roadmap).lines()
                    .filter(line -> line.startsWith("> **Status:**"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing V" + version + " roadmap status"));
            if (version == 29) {
                assertThat(roadmapStatus).isEqualTo("> **Status:** active");
                assertThat(indexRow).contains(checklistTarget).endsWith("| Active |");
                assertThat(checklist).exists();
                assertThat(Files.readString(checklist)).contains("[`ROADMAP.md`](ROADMAP.md)");
                continue;
            }
            if (version == 19) {
                assertThat(roadmapStatus).containsIgnoringCase("no-go");
                assertThat(indexRow).containsIgnoringCase("no-go");
            }
            else {
                assertThat(roadmapStatus).containsAnyOf("completed", "released");
                assertThat(indexRow).contains("Completed");
            }

            if (version == 2) {
                assertThat(checklist).doesNotExist();
                assertThat(indexRow).contains("pre-convention");
            }
            else {
                assertThat(checklist).as("V%s checklist", version).exists();
                assertThat(indexRow).contains(checklistTarget);
                assertThat(Files.readString(checklist))
                        .as("V%s checklist sibling roadmap link", version)
                        .contains("(ROADMAP.md)");
            }
        }
    }

    @Test
    void readmeAndQuickStartVersionsUseLatestPublishedRelease() throws Exception {
        Path root = projectRoot();
        String pomXml = Files.readString(root.resolve("pom.xml"));
        String publishedVersion = pomProperty(pomXml, "latest.published.version");

        assertVersionSnippets(root.resolve("README.md"), publishedVersion);
        assertVersionSnippets(root.resolve("docs/01-quick-start.md"), publishedVersion);
    }

    @Test
    void responseCacheDocumentationKeepsIsolationAndNativeConstraintsExplicit() throws IOException {
        String caching = Files.readString(projectRoot().resolve("docs/32-response-caching.md"));

        assertThat(caching)
                .contains("vary-by-headers: [Accept-Language, Idempotency-Key]")
                .contains("RequestContext.withIdempotencyKey(...)")
                .contains("must either vary\nby its effective idempotency header")
                .contains("effective idempotency header alone is not an\nauthenticated identity partition")
                .contains("record components consume that budget")
                .contains("count one depth level per nested container or\nrecord")
                .contains("A top-level query array is supported and\nexpands to ordered query values")
                .contains("Arrays used as path values or nested inside\nquery elements are rejected")
                .contains("Incompatible concrete or\ncovariant runtime array components fail before dispatch")
                .contains("bounded structural string snapshot")
                .contains("request-target projection each have a cumulative 1 MiB byte limit")
                .contains("Caller-created records are retained without\nrerunning their canonical constructors")
                .contains("`BigInteger`/`BigDecimal` encoded magnitude length are checked")
                .contains("URI text length")
                .contains("actual iterated list, set, and map members")
                .contains("equal-by-value elements from identity-based\nsets")
                .contains("every iterated identity-map entry")
                .contains("A selected body is serialized once through `ReactiveHttpClientJsonCodec`")
                .contains("An absent body has a distinct\nkey marker from a present zero-length body")
                .contains("overrides `toString()` are also rejected")
                .contains("selected String body length")
                .contains("URI\nvariants retain their non-normalized text")
                .contains("### Native context record values")
                .contains("@ImportRuntimeHints(CacheContextRuntimeHints.class)")
                .contains("SalesRegion.class.getRecordComponents()")
                .contains("component.getAccessor(), ExecutableMode.INVOKE")
                .contains("Native applications that do not\nregister a context-only record");
    }

    @Test
    void v27MigrationAndCacheOperationsDocumentationAreComplete() throws IOException {
        Path root = projectRoot();
        String migration = Files.readString(root.resolve("docs/31-3x-to-4x-resilience-migration.md"));
        String quickStart = Files.readString(root.resolve("docs/01-quick-start.md"));
        String resilience = Files.readString(root.resolve("docs/07-resilience4j.md"));
        String caching = Files.readString(root.resolve("docs/32-response-caching.md"));
        String production = Files.readString(root.resolve("docs/16-production-checklist.md"));
        String observability = Files.readString(root.resolve("docs/08-observability.md"));
        String operations = Files.readString(root.resolve("docs/30-operations-troubleshooting.md"));
        String supportBundles = Files.readString(root.resolve("docs/26-support-bundles.md"));
        String examples = Files.readString(root.resolve("docs/examples/effective-configuration.md"));
        String changelog = Files.readString(root.resolve("CHANGELOG.md"));
        Path fixturePath = root.resolve("docs/fixtures/support-bundle-response-cache.json");
        JsonNode fixture = OBJECT_MAPPER.readTree(fixturePath.toFile());

        assertThat(migration)
                .contains("## Explicit single-operator examples")
                .contains("### Retry only")
                .contains("### CircuitBreaker only")
                .contains("### Bulkhead only")
                .contains("### RateLimiter only")
                .contains("### All four published defaults")
                .contains("## Method precedence and validation")
                .contains("Blank method annotation values fail startup")
                .contains("Missing active method-annotation instances fail proxy construction")
                .contains("Client-level instance properties are not registry-membership fail-fast checks")
                .contains("Client-level names may be absent from")
                .contains("`resilience4j.*.instances`; Resilience4j")
                .contains("strict-unsafe-retry-validation")
                .doesNotContain("named Resilience4j instance must also exist");
        assertThat(quickStart)
                .contains("## Resilience configuration in `4.0.0`")
                .contains("`enabled: true` alone selects no\noperator")
                .contains("[Response Caching](32-response-caching.md)");
        assertThat(resilience)
                .contains("Start with one operator")
                .contains("`enabled: true` alone selects no operator");
        assertThat(caching)
                .contains("## Local-only consistency and invalidation")
                .contains("does not coordinate entries between application instances")
                .contains("An unselected")
                .contains("or explicitly disabled `POST`, `PUT`, `PATCH`, or `DELETE`")
                .contains("A selected non-`GET` method fails proxy construction unless that exact method is")
                .contains("declared as a semantic read")
                .contains("does not invalidate related cached reads")
                .contains("not a distributed-cache abstraction");
        assertThat(production)
                .contains("## Response caching (`4.0.0+`)")
                .contains("Do not enable `single-flight`, refresh, or cache telemetry implicitly")
                .contains("per-instance divergence")
                .contains("Empty completions and failures are never stored")
                .doesNotContain("Empty values and failures are never stored");
        assertThat(examples)
                .contains("## Explicit Local Response Cache")
                .contains("policy: catalog-read")
                .contains("maximum-size: 10000")
                .contains("cache:\n        enabled: true")
                .contains("inventory every applicable")
                .contains("Boot `WebClientCustomizer`, matching `ReactiveHttpClientCustomizer`")
                .contains("For each reviewed customization, add its exact Spring bean")
                .contains("name under `cache.customizations` with `SAFE`")
                .contains("Missing and `INCOMPATIBLE`")
                .contains("classifications reject proxy construction")
                .contains("Caffeine dependency instructions")
                .contains("With that dependency present");
        assertThat(observability)
                .contains("### Cache miss and load rate (events per second)")
                .contains("Terminal miss-load work:")
                .contains("therefore does not")
                .contains("prove that a transport dispatch occurred")
                .contains("ordinary request metrics")
                .contains("recipes below preserve")
                .contains("scrape-target labels")
                .contains("sum without (result)")
                .contains("sum without (outcome)")
                .contains("sum without (cause)")
                .doesNotContain("sum by (client_name, api_name) (\n    rate(reactive_http_client_cache_")
                .doesNotContain("sum by (client_name, cache_policy) (\n    rate(reactive_http_client_cache_evictions_")
                .contains("zero branch retains every cache-selected API")
                .contains("A zero series does not prove that refresh is configured or active")
                .doesNotContain("zero branch retains refresh-enabled groups")
                .contains("### Cache eviction pressure (evictions per second)")
                .contains("### Cache capacity pressure (dimensionless)")
                .contains("reactive.http.client.cache.retained.decoded.response.bytes")
                .contains("reactive.http.client.cache.maximum.decoded.response.bytes")
                .contains("reactive.http.client.cache.admissions")
                .contains("decoded response representation bytes")
                .contains("current occupancy/capacity signals")
                .contains("cumulative terminal event histories")
                .contains("reactive_http_client_cache_entries\n  /\n  clamp_min(\n"
                        + "    reactive_http_client_cache_maximum_entries, 1")
                .contains("per scrape target before aggregation")
                .contains("zero-valued branch keeps an idle selected cache")
                .contains("cause=\"size\"")
                .contains("cause=\"ttl\"");
        assertThat(operations)
                .contains("| Unexpected stale value")
                .contains("[Response cache behavior (4.0.0+)](#response-cache-behavior-400)")
                .contains("## Response cache behavior (4.0.0+)")
                .contains("per-instance divergence is expected")
                .contains("does not imply distributed coherence");
        assertThat(supportBundles)
                .contains("[Aggregate response-cache incident](fixtures/support-bundle-response-cache.json)")
                .contains("bounded aggregate cache facts")
                .contains("one sanitized caller terminal record");
        assertThat(changelog)
                .contains("**V27 migration and operations documentation.");

        assertThat(fixture.path("schemaVersion").isInt()).isTrue();
        assertThat(fixture.path("window").path("startedAt").isTextual()).isTrue();
        assertThat(fixture.path("window").path("endedAt").isTextual()).isTrue();
        assertThat(fixture.path("window").path("duration").isInt()).isTrue();
        assertThat(fixture.path("window").path("unit").asText()).isEqualTo("seconds");
        assertThat(fixture.path("cache").path("cachePhase").asText()).isEqualTo("refresh-on-access");
        assertThat(fixture.path("cache").path("cachePolicyCount").isInt()).isTrue();
        assertThat(fixture.path("cache").path("cacheMaximumSize").isInt()).isTrue();
        assertThat(fixture.path("cache").path("cacheMaximumTotalDecodedResponseBytes").isIntegralNumber())
                .isTrue();
        assertThat(fixture.path("cache").path("cacheEntryCount").isInt()).isTrue();
        assertThat(fixture.path("cache").path("cachePolicySources").isArray()).isTrue();
        assertThat(fixture.path("cache").path("cachePolicySources").get(0).asText()).isEqualTo("method");
        assertThat(fixture.path("cache").path("cacheHttpMethods").isArray()).isTrue();
        assertThat(fixture.path("cache").path("cacheHttpMethods").get(0).asText()).isEqualTo("POST");
        assertThat(fixture.path("cache").has("cacheSemanticReadAcknowledged")).isTrue();
        assertThat(fixture.path("cache").path("cacheSemanticReadAcknowledged").isBoolean()).isTrue();
        assertThat(fixture.path("cache").path("cacheSemanticReadAcknowledged").asBoolean()).isTrue();
        assertThat(fixture.path("lookups").path("hits").isInt()).isTrue();
        assertThat(fixture.path("lookups").path("misses").isInt()).isTrue();
        assertThat(fixture.path("loads").path("success").isInt()).isTrue();
        assertThat(fixture.path("refreshes").path("failure").isInt()).isTrue();
        assertThat(fixture.path("evictions").path("size").isInt()).isTrue();
        assertThat(fixture.path("evictions").path("ttl").isInt()).isTrue();

        JsonNode terminalCaller = fixture.path("terminalCaller");
        assertThat(terminalCaller.isObject()).isTrue();
        assertThat(terminalCaller.path("cacheOutcome").asText()).isEqualTo("STALE_HIT");
        assertThat(terminalCaller.has("subscriptionAttemptCount")).isTrue();
        assertThat(terminalCaller.path("subscriptionAttemptCount").isIntegralNumber()).isTrue();
        assertThat(terminalCaller.path("subscriptionAttemptCount").asInt()).isZero();
        assertThat(terminalCaller.has("requestDispatched")).isTrue();
        assertThat(terminalCaller.path("requestDispatched").isBoolean()).isTrue();
        assertThat(terminalCaller.path("requestDispatched").asBoolean()).isFalse();
        assertThat(terminalCaller.has("terminalRecordCreated")).isTrue();
        assertThat(terminalCaller.path("terminalRecordCreated").isBoolean()).isTrue();
        assertThat(terminalCaller.path("terminalRecordCreated").asBoolean()).isTrue();
        assertThat(terminalCaller.has("cancellation")).isTrue();
        assertThat(terminalCaller.path("cancellation").isBoolean()).isTrue();
        assertThat(terminalCaller.path("cancellation").asBoolean()).isFalse();
        assertThat(terminalCaller.has("responseStatus")).isTrue();
        assertThat(terminalCaller.path("responseStatus").isIntegralNumber()).isTrue();
        assertThat(terminalCaller.path("responseStatus").asInt()).isEqualTo(200);
        assertThat(terminalCaller.has("errorType")).isTrue();
        assertThat(terminalCaller.path("errorType").isNull()).isTrue();
        assertThat(terminalCaller.has("errorCategory")).isTrue();
        assertThat(terminalCaller.path("errorCategory").isTextual()).isTrue();
        assertThat(terminalCaller.path("errorCategory").asText()).isEqualTo("NONE");
        assertThat(terminalCaller.has("failureStage")).isTrue();
        assertThat(terminalCaller.path("failureStage").isNull()).isTrue();

        assertThat(sensitiveSupportFixtureFieldNames(fixture))
                .as("sensitive response-cache support fixture field names")
                .isEmpty();

        String fixtureText = Files.readString(fixturePath);
        assertThat(fixtureText)
                .doesNotContainIgnoringCase("cacheKey")
                .doesNotContainIgnoringCase("keyDigest")
                .doesNotContainIgnoringCase("cachedValue")
                .doesNotContainIgnoringCase("authorization")
                .doesNotContainIgnoringCase("credential")
                .doesNotContainIgnoringCase("tenant")
                .doesNotContain("http://", "https://");
    }

    @Test
    void responseCacheSupportFixtureGuardRejectsSensitiveFieldNames() throws IOException {
        JsonNode unsafeFixture = OBJECT_MAPPER.readTree("""
                {
                  "requestPath": "/customers/123",
                  "nested": {
                    "queryParameters": "customer=123",
                    "requestVariant": "tenant-a",
                    "requestTarget": "/customers/{id}",
                    "uri": "/customers/123",
                    "entryKey": "opaque-entry",
                    "cacheDigest": "opaque-digest",
                    "responseValue": "cached-response",
                    "payload": "cached-payload",
                    "requestHeaders": "present",
                    "responseBodies": "present",
                    "callerIdentity": "present",
                    "exceptionMessage": "unsafe"
                  }
                }
                """);

        assertThat(sensitiveSupportFixtureFieldNames(unsafeFixture))
                .containsExactlyInAnyOrder(
                        "requestPath", "queryParameters", "requestVariant", "requestTarget", "uri",
                        "entryKey", "cacheDigest", "responseValue", "payload", "requestHeaders",
                        "responseBodies", "callerIdentity", "exceptionMessage");

        JsonNode unsafeTextFixture = OBJECT_MAPPER.readTree("""
                {
                  "sample": "/orders/42?account=123",
                  "detail": "account=123&region=west",
                  "endpoint": "internal.example:443",
                  "route": "orders/42",
                  "option": "?debug",
                  "target": "*",
                  "remoteArchive": "ftp://internal-host/resource",
                  "localResource": "file:///private/path",
                  "sampleLine": "request failed: GET /orders/42?debug HTTP/1.1 after dispatch"
                }
                """);
        assertThat(sensitiveSupportFixtureFieldNames(unsafeTextFixture)).isEmpty();
        assertThat(sensitiveSupportFixtureTextValues(unsafeTextFixture))
                .containsExactlyInAnyOrder(
                        "/orders/42?account=123",
                        "account=123&region=west",
                        "internal.example:443",
                        "orders/42",
                        "?debug",
                        "*",
                        "ftp://internal-host/resource",
                        "file:///private/path",
                        "request failed: GET /orders/42?debug HTTP/1.1 after dispatch");
    }

    @Test
    void v29CacheMemoryOperationsEvidenceIsVersionScopedBoundedAndSanitized() throws IOException {
        Path root = projectRoot();
        String operations = Files.readString(root.resolve("docs/30-operations-troubleshooting.md"));
        String supportBundles = Files.readString(root.resolve("docs/26-support-bundles.md"));
        String reviewableBundleFixture = markdownSection(
                supportBundles, "## Reviewable Bundle Fixture", "## Diagnostics Snapshot");
        String normalizedOperations = operations.replaceAll("\\s+", " ");
        String normalizedSupportBundles = supportBundles.replaceAll("\\s+", " ");
        Path fixturePath = root.resolve("docs/fixtures/support-bundle-cache-memory.json");
        JsonNode fixture = OBJECT_MAPPER.readTree(fixturePath.toFile());

        assertThat(normalizedOperations)
                .contains("Cache-memory triage (V29 snapshot only)")
                .contains("Published `4.1.0` exposes the entry-count and cache-activity signals")
                .contains("it does not expose V29's decoded-response-byte capacity/occupancy")
                .contains("starter version and deployment change")
                .contains("how many policies the client selects")
                .contains("configuration source, safe bounded name, `maximum-size`, TTL, and entry occupancy")
                .contains("`cacheMetricsEnabled` selection")
                .contains("API-tagged hit/miss, caller outcome, coalesced-waiter, load, and refresh")
                .contains("policy-tagged occupancy, size/TTL/weight eviction")
                .contains("Java heap used/committed, process RSS, container working set, direct memory")
                .contains("live thread count")
                .contains("protocol, total/idle physical connections")
                .contains("applicable active/pending connection or stream gauges")
                .contains("generation records, completed load tokens")
                .contains("coalesced-waiter deltas rise in the same bounded window")
                .contains("compare the recorded before/after terminal-load counter snapshots "
                        + "across a bounded quiet window")
                .contains("Record the cumulative success, failure, and cancellation terminal-load counters")
                .contains("while those meters remain registered")
                .contains("no delta narrows the observation but does not prove retained flight ownership")
                .contains("not to read removed cache meters")
                .contains("stale-hit callers continue across consecutive bounded windows")
                .contains("terminal refresh totals do not advance")
                .contains("terminal-only counters cannot prove an active refresh by themselves")
                .contains("Meter count or old gauge suppliers grow across context restart")
                .contains("RSS and container working set are not Java heap")
                .contains("response wire size is not the decoded object graph retained by a cache entry");
        assertThat(normalizedSupportBundles)
                .contains("Cache-memory capture (V29 snapshot only)")
                .contains("Published `4.1.0` incidents use the explicitly enumerated published fields")
                .contains("do not include the two V29 snapshot-only decoded-response-byte diagnostics fields")
                .contains("[cache-memory fixture](fixtures/support-bundle-cache-memory.json)")
                .contains("one bounded client name and one sanitized process-instance ordinal")
                .contains("API-tagged lookup, caller outcome, coalesced, stale, terminal load, and refresh")
                .contains("cumulative API terminal-load counters sampled at both boundaries")
                .contains("traffic was stopped and the factory remained open")
                .contains("policy-tagged TTL/size/weight eviction and weighted-admission")
                .contains("timestamped, phase-labeled post-GC memory checkpoints")
                .contains("HTTP/2 stream gauges")
                .contains("factory start/close, context restart")
                .contains("nullable refresh-after/refresh-timeout bounds")
                .contains("Record `cacheMetricsEnabled` for the affected client")
                .contains("disabled or unavailable integration uses `null`, not fabricated zeros")
                .contains("For an unweighted policy")
                .contains("weight eviction, and admissions are `null`")
                .contains("at most 16 policy records")
                .contains("at most 64 API records")
                .contains("at most 128 characters per name")
                .contains("Heap dumps and JFR recordings can contain")
                .contains("separately approved, encrypted, access-controlled process")
                .contains("always write the HTTP status to a bundle file")
                .contains("quarantined `*.raw.json` files outside the bundle")
                .contains("sets `umask 077` before creating capture files")
                .contains("newly created quarantined bodies use mode `0600`")
                .contains("Retain it only after the shared validation/sanitization step")
                .contains("verifies that curl reported a successful transfer")
                .contains("--slurpfile schema")
                .contains("expected recursive leaf types")
                .contains("documented nullable unknown states")
                .contains("two V29 decoded-response byte fields are optional only when `projectVersion` "
                        + "identifies a published `4.1.x` response")
                .contains("A V29 `4.2.0-SNAPSHOT` response must include both fields")
                .contains("retained decoded-response bytes cannot exceed the configured aggregate maximum")
                .contains("$httpStatus | test(\"^2[0-9][0-9]$\")")
                .contains("nullable_number($field)")
                .contains("nullable_boolean($field)")
                .contains("nullable_array($field)")
                .contains("published_4_1($version)")
                .contains("optional_field($projectVersion; $field)")
                .contains("optional_field($projectVersion; .)")
                .contains("keep_shape($schema[0]; \"root\"; $projectVersion)")
                .contains("valid_leaf($field; $shape)")
                .contains("(length <= 512)")
                .contains("($required - keys) | length")
                .contains("tojson | utf8bytelength) <= 1048576")
                .contains("status: (if $detail.status == \"DOWN\" then \"DOWN\" else \"UP\" end)")
                .contains("unexpected reactive HTTP client health response")
                .contains("Never attach the raw files");

        assertThat(fixture.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(fixture.path("captureScope").asText()).isEqualTo("cache-memory");
        assertThat(fixture.path("signalAvailability").asText()).isEqualTo("4.2.0-SNAPSHOT-v29");
        assertThat(fixture.path("window").path("startedAt").isTextual()).isTrue();
        assertThat(fixture.path("window").path("endedAt").isTextual()).isTrue();
        assertThat(fixture.path("window").path("duration").asInt()).isEqualTo(300);
        assertThat(fixture.path("window").path("unit").asText()).isEqualTo("seconds");
        assertThat(fixture.path("clientName").isTextual()).isTrue();
        assertThat(fixture.path("clientName").asText().length()).isLessThanOrEqualTo(128);
        assertThat(fixture.path("processInstance").isTextual()).isTrue();
        assertThat(fixture.path("processInstance").asText().length()).isLessThanOrEqualTo(128);

        JsonNode configuration = fixture.path("configuration");
        JsonNode policies = configuration.path("policies");
        assertThat(policies.isArray()).isTrue();
        assertThat(policies.size()).isEqualTo(2).isLessThanOrEqualTo(16);
        assertThat(configuration.path("selectedPolicyCount").asInt()).isEqualTo(policies.size());
        assertThat(configuration.path("cacheMetricsEnabled").isBoolean()).isTrue();
        assertThat(configuration.path("cacheMetricsEnabled").asBoolean()).isTrue();
        Map<String, Boolean> weightedPolicies = new HashMap<>();
        Map<String, Boolean> refreshEnabledPolicies = new HashMap<>();
        Map<String, Long> policyTtlMs = new HashMap<>();
        policies.forEach(policy -> {
            assertThat(policy.path("name").isTextual()).isTrue();
            assertThat(policy.path("name").asText().length()).isLessThanOrEqualTo(128);
            assertThat(policy.path("source").isTextual()).isTrue();
            assertThat(policy.path("ttlMs").isIntegralNumber()).isTrue();
            policyTtlMs.put(policy.path("name").asText(), policy.path("ttlMs").asLong());
            boolean refreshEnabled = policy.path("refreshAfterMs").isIntegralNumber();
            refreshEnabledPolicies.put(policy.path("name").asText(), refreshEnabled);
            assertThat(policy.path("refreshAfterMs").isNull()).isEqualTo(!refreshEnabled);
            assertThat(policy.path("refreshTimeoutMs").isIntegralNumber()).isEqualTo(refreshEnabled);
            assertThat(policy.path("refreshTimeoutMs").isNull()).isEqualTo(!refreshEnabled);
            if (refreshEnabled) {
                assertThat(policy.path("refreshAfterMs").asLong()).isPositive()
                        .isLessThan(policy.path("ttlMs").asLong());
                assertThat(policy.path("refreshTimeoutMs").asLong()).isPositive();
            }
            assertThat(policy.path("maximumEntries").isIntegralNumber()).isTrue();
            assertThat(policy.path("weightedAdmission").isBoolean()).isTrue();
            boolean weighted = policy.path("weightedAdmission").asBoolean();
            weightedPolicies.put(policy.path("name").asText(), weighted);
            assertThat(policy.path("maximumDecodedResponseBytes").isIntegralNumber())
                    .isEqualTo(weighted);
            assertThat(policy.path("maximumDecodedResponseBytes").isNull())
                    .isEqualTo(!weighted);
        });
        assertThat(weightedPolicies).containsEntry("catalog-read", true)
                .containsEntry("profile-summary", false);
        assertThat(refreshEnabledPolicies).containsEntry("catalog-read", true)
                .containsEntry("profile-summary", false);

        JsonNode apiActivity = fixture.path("apiActivity");
        assertThat(apiActivity.isArray()).isTrue();
        assertThat(apiActivity.size()).isEqualTo(2).isLessThanOrEqualTo(64);
        Map<String, Long> successfulLoadsByPolicy = new HashMap<>();
        Map<String, Long> successfulRefreshesByPolicy = new HashMap<>();
        Map<String, JsonNode> apiActivityByName = new HashMap<>();
        apiActivity.forEach(api -> {
            assertThat(api.path("apiName").isTextual()).isTrue();
            assertThat(api.path("apiName").asText().length()).isLessThanOrEqualTo(128);
            apiActivityByName.put(api.path("apiName").asText(), api);
            assertThat(weightedPolicies).containsKey(api.path("selectedPolicy").asText());
            assertThat(api.path("lookups").path("hits").isIntegralNumber()).isTrue();
            assertThat(api.path("lookups").path("misses").isIntegralNumber()).isTrue();
            assertThat(api.path("callers").path("freshHit").isIntegralNumber()).isTrue();
            assertThat(api.path("callers").path("missLoader").isIntegralNumber()).isTrue();
            assertThat(api.path("callers").path("coalescedWaiter").isIntegralNumber()).isTrue();
            assertThat(api.path("callers").path("staleHit").isIntegralNumber()).isTrue();
            assertThat(api.path("lookups").path("hits").asLong()).isEqualTo(
                    api.path("callers").path("freshHit").asLong()
                            + api.path("callers").path("staleHit").asLong());
            assertThat(api.path("lookups").path("misses").asLong()).isEqualTo(
                    api.path("callers").path("missLoader").asLong()
                            + api.path("callers").path("coalescedWaiter").asLong());
            assertThat(api.path("coalesced").asInt())
                    .isEqualTo(api.path("callers").path("coalescedWaiter").asInt());
            assertThat(api.path("loads").path("success").isIntegralNumber()).isTrue();
            successfulLoadsByPolicy.merge(
                    api.path("selectedPolicy").asText(),
                    api.path("loads").path("success").asLong(),
                    Long::sum);
            successfulRefreshesByPolicy.merge(
                    api.path("selectedPolicy").asText(),
                    api.path("refreshes").path("success").asLong(),
                    Long::sum);
            assertThat(api.path("refreshes").path("failure").isIntegralNumber()).isTrue();
        });
        assertThat(apiActivity.get(1).path("lookups").path("hits").asInt()).isZero();
        assertThat(apiActivity.get(1).path("coalesced").asInt()).isZero();
        for (String outcome : List.of("success", "failure", "cancellation")) {
            assertThat(apiActivity.get(1).path("loads").path(outcome).asLong()).isZero();
            assertThat(apiActivity.get(1).path("refreshes").path(outcome).asLong()).isZero();
        }

        JsonNode policyActivity = fixture.path("policyActivity");
        assertThat(policyActivity.isArray()).isTrue();
        assertThat(policyActivity.size()).isEqualTo(policies.size());
        Map<String, Long> evictionsByPolicy = new HashMap<>();
        Map<String, Long> ttlEvictionsByPolicy = new HashMap<>();
        policyActivity.forEach(activity -> {
            String policyName = activity.path("policy").asText();
            assertThat(weightedPolicies).containsKey(policyName);
            assertThat(activity.path("evictions").path("ttl").isIntegralNumber()).isTrue();
            ttlEvictionsByPolicy.put(
                    policyName, activity.path("evictions").path("ttl").asLong());
            assertThat(activity.path("evictions").path("size").isIntegralNumber()).isTrue();
            assertThat(activity.path("evictions").path("size").asLong()).isZero();
            boolean weighted = weightedPolicies.get(policyName);
            assertThat(activity.path("evictions").path("weight").isIntegralNumber())
                    .isEqualTo(weighted);
            assertThat(activity.path("evictions").path("weight").isNull())
                    .isEqualTo(!weighted);
            assertThat(activity.path("admissions").isObject()).isEqualTo(weighted);
            assertThat(activity.path("admissions").isNull()).isEqualTo(!weighted);
            evictionsByPolicy.put(
                    policyName,
                    activity.path("evictions").path("ttl").asLong()
                            + activity.path("evictions").path("size").asLong()
                            + (weighted
                            ? activity.path("evictions").path("weight").asLong()
                            : 0L));
        });

        JsonNode checkpoints = fixture.path("checkpoints");
        assertThat(checkpoints.isArray()).isTrue();
        assertThat(checkpoints).hasSize(3);
        assertThat(checkpoints).extracting(checkpoint -> checkpoint.path("phase").asText())
                .containsExactly("before-load-post-gc", "after-load-post-gc", "after-close-post-gc");
        assertThat(checkpoints.get(0).path("capturedAt").asText())
                .isEqualTo(fixture.path("window").path("startedAt").asText());
        assertThat(checkpoints.get(2).path("capturedAt").asText())
                .isEqualTo(fixture.path("window").path("endedAt").asText());
        Map<String, Long> entriesBeforeLoad = new HashMap<>();
        Map<String, Long> entriesAfterLoad = new HashMap<>();
        checkpoints.get(0).path("policyState").forEach(state ->
                entriesBeforeLoad.put(state.path("policy").asText(), state.path("entries").asLong()));
        checkpoints.get(1).path("policyState").forEach(state -> {
            String policyName = state.path("policy").asText();
            entriesAfterLoad.put(policyName, state.path("entries").asLong());
            assertThat(state.path("entries").asLong()).isEqualTo(
                    entriesBeforeLoad.get(policyName)
                            + successfulLoadsByPolicy.getOrDefault(policyName, 0L)
                            - evictionsByPolicy.get(policyName));
        });
        checkpoints.forEach(checkpoint -> {
            assertThat(checkpoint.path("capturedAt").isTextual()).isTrue();
            assertThat(checkpoint.path("phase").isTextual()).isTrue();
            JsonNode memory = checkpoint.path("memory");
            for (String field : List.of("processRssBytes", "containerWorkingSetBytes",
                    "javaHeapUsedAfterGcBytes", "javaHeapCommittedBytes",
                    "directMemoryUsedBytes", "liveThreadCount")) {
                assertThat(memory.path(field).isIntegralNumber()).as(field).isTrue();
            }
            assertThat(checkpoint.path("cacheStateAvailable").isBoolean()).isTrue();
            assertThat(checkpoint.path("transportStateAvailable").isBoolean()).isTrue();
            if (checkpoint.path("cacheStateAvailable").asBoolean()) {
                JsonNode policyState = checkpoint.path("policyState");
                assertThat(policyState.isArray()).isTrue();
                assertThat(policyState.size()).isEqualTo(policies.size());
                policyState.forEach(state -> {
                    String policyName = state.path("policy").asText();
                    boolean weighted = weightedPolicies.get(policyName);
                    assertThat(state.path("entries").isIntegralNumber()).isTrue();
                    assertThat(state.path("maximumEntries").isIntegralNumber()).isTrue();
                    assertThat(state.path("retainedDecodedResponseBytes").isIntegralNumber())
                            .isEqualTo(weighted);
                    assertThat(state.path("retainedDecodedResponseBytes").isNull())
                            .isEqualTo(!weighted);
                    assertThat(state.path("maximumDecodedResponseBytes").isIntegralNumber())
                            .isEqualTo(weighted);
                    assertThat(state.path("maximumDecodedResponseBytes").isNull())
                            .isEqualTo(!weighted);
                });
            }
            else {
                assertThat(checkpoint.path("policyState").isArray()).isTrue();
                assertThat(checkpoint.path("policyState").isEmpty()).isTrue();
            }
            if (checkpoint.path("transportStateAvailable").asBoolean()) {
                JsonNode transport = checkpoint.path("transport");
                assertThat(transport.path("protocol").asText()).isEqualTo("HTTP/2");
                for (String field : List.of("poolTotalConnections", "poolIdleConnections",
                        "poolActiveStreams", "poolPendingStreams", "poolMaximumConnections")) {
                    assertThat(transport.path(field).isIntegralNumber()).as(field).isTrue();
                }
            }
            else {
                assertThat(checkpoint.path("transport").isNull()).isTrue();
            }
        });
        long profileElapsedMs = Duration.between(
                Instant.parse(checkpoints.get(0).path("capturedAt").asText()),
                Instant.parse(checkpoints.get(1).path("capturedAt").asText())).toMillis();
        assertThat(profileElapsedMs).isGreaterThan(policyTtlMs.get("profile-summary"));
        assertThat(entriesBeforeLoad).containsEntry("profile-summary", 50L);
        assertThat(entriesAfterLoad).containsEntry("profile-summary", 0L);
        assertThat(ttlEvictionsByPolicy).containsEntry("profile-summary", 50L);
        long catalogElapsedMs = Duration.between(
                Instant.parse(checkpoints.get(0).path("capturedAt").asText()),
                Instant.parse(checkpoints.get(1).path("capturedAt").asText())).toMillis();
        assertThat(catalogElapsedMs).isGreaterThan(policyTtlMs.get("catalog-read"));
        assertThat(entriesBeforeLoad).containsEntry("catalog-read", 200L);
        assertThat(successfulRefreshesByPolicy).containsEntry("catalog-read", 14L);
        assertThat(ttlEvictionsByPolicy).containsEntry("catalog-read", 186L);
        assertThat(entriesAfterLoad).containsEntry("catalog-read", 45L);
        assertThat(entriesAfterLoad.get("catalog-read")).isLessThanOrEqualTo(
                successfulLoadsByPolicy.get("catalog-read")
                        + successfulRefreshesByPolicy.get("catalog-read"));

        JsonNode quietWindow = fixture.path("quietWindow");
        assertThat(quietWindow.path("trafficStopped").isBoolean()).isTrue();
        assertThat(quietWindow.path("trafficStopped").asBoolean()).isTrue();
        assertThat(quietWindow.path("factoryOpen").isBoolean()).isTrue();
        assertThat(quietWindow.path("factoryOpen").asBoolean()).isTrue();
        assertThat(quietWindow.path("startedAt").isTextual()).isTrue();
        assertThat(quietWindow.path("endedAt").isTextual()).isTrue();
        assertThat(quietWindow.path("startedAt").asText())
                .isEqualTo(checkpoints.get(1).path("capturedAt").asText());
        assertThat(quietWindow.path("endedAt").asText())
                .isGreaterThan(quietWindow.path("startedAt").asText())
                .isLessThan(fixture.path("lifecycle").path("events").get(1)
                        .path("capturedAt").asText());
        JsonNode counterSnapshots = quietWindow.path("counterSnapshots");
        assertThat(counterSnapshots.isArray()).isTrue();
        assertThat(counterSnapshots).hasSize(2);
        assertThat(counterSnapshots).extracting(snapshot -> snapshot.path("phase").asText())
                .containsExactly("before-quiet", "after-quiet");
        assertThat(counterSnapshots.get(0).path("capturedAt").asText())
                .isEqualTo(quietWindow.path("startedAt").asText());
        assertThat(counterSnapshots.get(1).path("capturedAt").asText())
                .isEqualTo(quietWindow.path("endedAt").asText());
        Map<String, Long> beforeTerminalLoads = new HashMap<>();
        Map<String, Long> afterTerminalLoads = new HashMap<>();
        for (int snapshotIndex = 0; snapshotIndex < counterSnapshots.size(); snapshotIndex++) {
            boolean afterSnapshot = snapshotIndex == 1;
            JsonNode terminalLoads = counterSnapshots.get(snapshotIndex).path("terminalLoads");
            assertThat(terminalLoads.isArray()).isTrue();
            assertThat(terminalLoads).hasSize(apiActivityByName.size());
            Map<String, Long> totals = afterSnapshot ? afterTerminalLoads : beforeTerminalLoads;
            terminalLoads.forEach(loads -> {
                assertThat(loads.path("apiName").isTextual()).isTrue();
                String apiName = loads.path("apiName").asText();
                assertThat(apiActivityByName).containsKey(apiName);
                long total = 0;
                for (String outcome : List.of("success", "failure", "cancellation")) {
                    assertThat(loads.path(outcome).isIntegralNumber()).isTrue();
                    assertThat(loads.path(outcome).asLong()).isNotNegative();
                    total += loads.path(outcome).asLong();
                    if (afterSnapshot) {
                        assertThat(loads.path(outcome).asLong()).isEqualTo(
                                apiActivityByName.get(apiName).path("loads").path(outcome).asLong());
                    }
                }
                totals.put(apiName, total);
            });
            assertThat(totals).hasSize(apiActivityByName.size());
        }
        assertThat(afterTerminalLoads.get("catalog.search")
                - beforeTerminalLoads.get("catalog.search")).isEqualTo(1L);
        assertThat(afterTerminalLoads.get("profile.get")
                - beforeTerminalLoads.get("profile.get")).isZero();

        JsonNode lifecycle = fixture.path("lifecycle");
        assertThat(lifecycle.path("events").isArray()).isTrue();
        assertThat(lifecycle.path("events")).hasSize(2);
        lifecycle.path("events").forEach(event -> {
            assertThat(event.path("capturedAt").isTextual()).isTrue();
            assertThat(event.path("type").isTextual()).isTrue();
        });
        assertThat(lifecycle.path("events").get(0).path("type").asText())
                .isEqualTo("factory-start");
        assertThat(lifecycle.path("events").get(0).path("capturedAt").asText())
                .isLessThan(checkpoints.get(0).path("capturedAt").asText());
        JsonNode deploymentChanges = lifecycle.path("deploymentChanges");
        assertThat(deploymentChanges.isArray()).isTrue();
        assertThat(deploymentChanges).hasSize(1);
        JsonNode deploymentChange = deploymentChanges.get(0);
        assertThat(deploymentChange.path("capturedAt").isTextual()).isTrue();
        assertThat(deploymentChange.path("capturedAt").asText())
                .isLessThan(fixture.path("window").path("startedAt").asText());
        assertThat(deploymentChange.path("type").asText()).isEqualTo("starter-version");
        assertThat(deploymentChange.path("beforeVersion").asText()).isEqualTo("4.1.0");
        assertThat(deploymentChange.path("afterVersion").asText()).isEqualTo("4.2.0-SNAPSHOT");

        assertThat(sensitiveSupportFixtureFieldNames(fixture))
                .as("sensitive cache-memory support fixture field names")
                .isEmpty();
        assertThat(sensitiveSupportFixtureTextValues(fixture))
                .as("request-target or query material in cache-memory support fixture values")
                .isEmpty();

        List<String> captureCurlCommands = supportBundles.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("if curl -sS "))
                .toList();
        assertThat(captureCurlCommands).hasSize(6)
                .allMatch(line -> line.contains("--connect-timeout 5"))
                .allMatch(line -> line.contains("--max-time 30"))
                .allMatch(line -> line.contains("--max-filesize 1048576"))
                .allMatch(line -> line.contains("-w '%{http_code}\\n'"))
                .allMatch(line -> line.contains(".raw.json"))
                .allMatch(line -> line.contains("-http-status.txt"))
                .noneMatch(line -> line.contains(" -o support-bundle/"));
        assertThat(supportBundles)
                .contains("mv rhttpclients.sanitized.json support-bundle/diagnostics/rhttpclients.json")
                .contains("mv reactive-http-client-health.sanitized.json support-bundle/health/health.json")
                .contains("test \"$(cat support-bundle/diagnostics/"
                        + "rhttpclients-curl-exit-status.txt)\" = \"0\" &&\n"
                        + "  test -f rhttpclients.raw.json &&\n"
                        + "  test \"$(wc -c < rhttpclients.raw.json)\" -le 1048576 &&\n"
                        + "  jq --slurp \\\n"
                        + "  --arg httpStatus")
                .contains("test \"$(cat support-bundle/health/"
                        + "reactive-http-client-health-curl-exit-status.txt)\" = \"0\" &&\n"
                        + "  test -f reactive-http-client-health.raw.json &&\n"
                        + "  test \"$(wc -c < reactive-http-client-health.raw.json)\" -le 1048576 &&\n"
                        + "  jq --slurp \\\n"
                        + "  --arg httpStatus \"$(cat support-bundle/health/"
                        + "reactive-http-client-health-http-status.txt)\" \\\n"
                        + "  --arg client")
                .contains("if length == 1 then .[0]\n"
                        + "  else error(\"expected exactly one diagnostics JSON value\")")
                .contains("else error(\"expected exactly one health JSON value\")")
                .contains("$httpStatus | test(\"^5[0-9][0-9]$\")")
                .contains("and length <= 16\n"
                        + "            and all(.[]; type == \"string\" and length <= 512)")
                .contains("all(.clients[]; .inheritedEndpointCount <= .endpointCount)")
                .contains(".cacheRetainedDecodedResponseBytes\n"
                        + "            <= .cacheMaximumTotalDecodedResponseBytes")
                .contains("def nonnegative_integer:")
                .contains("def rate_matches($detail):")
                .contains("$detail.errors / $detail.samples")
                .contains("($detail.samples == 0) or rate_matches($detail)")
                .contains("preserves omission of\n`errorRate` when the selected client has zero samples")
                .contains("| if $detail.samples == 0 then .\n"
                        + "          else . + {errorRate: $detail.errorRate}")
                .contains("$detail.samples == $detail.sampleCount")
                .contains("$detail.reason == \"NO_SAMPLES\"")
                .contains("$detail.reason == \"ERROR_RATE_ABOVE_THRESHOLD\"")
                .contains("If `curl` reports any nonzero transfer status")
                .contains("including a connection timeout,")
                .contains("total-transfer timeout, transfer-bound,")
                .contains("truncation, or connection-reset failure")
                .contains("a raw-size check fails")
                .contains("an input does not contain exactly one JSON value")
                .contains("an HTTP\nstatus is ineligible")
                .contains("keep the HTTP and curl exit-status files");
        assertThat(reviewableBundleFixture)
                .contains("diagnostics/rhttpclients-curl-exit-status.txt")
                .contains("health/reactive-http-client-health-curl-exit-status.txt");
        assertThat(supportBundles)
                .doesNotContain("minSamples, errorRateThreshold, errorRate, status, reason");
        for (String exitStatusPath : List.of(
                "support-bundle/diagnostics/rhttpclients-curl-exit-status.txt",
                "support-bundle/health/reactive-http-client-health-curl-exit-status.txt")) {
            long writeCount = supportBundles.lines()
                    .map(String::trim)
                    .filter(line -> line.startsWith("printf "))
                    .filter(line -> line.endsWith("> " + exitStatusPath))
                    .count();
            assertThat(writeCount).as(exitStatusPath).isEqualTo(6);
        }
        long staleFinalCaptureRemovalCount = supportBundles.lines()
                .map(String::trim)
                .filter(line -> line.equals(
                        "rm -f support-bundle/diagnostics/rhttpclients.json "
                                + "support-bundle/health/health.json"))
                .count();
        assertThat(staleFinalCaptureRemovalCount).isEqualTo(3);
        long staleCurlStatusRemovalCount = supportBundles.lines()
                .map(String::trim)
                .filter(line -> line.equals(
                        "rm -f support-bundle/diagnostics/rhttpclients-curl-exit-status.txt "
                                + "support-bundle/health/"
                                + "reactive-http-client-health-curl-exit-status.txt"))
                .count();
        assertThat(staleCurlStatusRemovalCount).isEqualTo(3);
        long staleRawCaptureRemovalCount = supportBundles.lines()
                .map(String::trim)
                .filter(line -> line.equals(
                        "rm -f rhttpclients.raw.json reactive-http-client-health.raw.json"))
                .count();
        assertThat(staleRawCaptureRemovalCount).isEqualTo(3);
        long privateCaptureUmaskCount = supportBundles.lines()
                .map(String::trim)
                .filter(line -> line.equals("umask 077"))
                .count();
        assertThat(privateCaptureUmaskCount).isEqualTo(3);
        String kubernetesCapture = markdownSection(
                supportBundles, "### Kubernetes-Style Capture", "## Health Details");
        for (String assignment : List.of(
                "EXAMPLE_NAMESPACE=\"example-namespace\"",
                "EXAMPLE_POD=\"example-app-pod\"",
                "EXAMPLE_CONTAINER=\"example-app-container\"",
                "EXAMPLE_LOCAL_PORT=\"18080\"",
                "EXAMPLE_MANAGEMENT_PORT=\"<management-port>\"",
                "EXAMPLE_SANITIZED_CONFIG_IN_POD=\"/path/in/pod/sanitized-reactive-http-client.yml\"")) {
            long assignmentCount = kubernetesCapture.lines()
                    .map(String::trim)
                    .filter(line -> line.equals(assignment))
                    .count();
            assertThat(assignmentCount).as(assignment).isEqualTo(2);
        }
        List<String> kubectlCommands = supportBundles.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("kubectl "))
                .toList();
        assertThat(kubectlCommands).noneMatch(line -> line.contains(" cp "));
        assertThat(supportBundles)
                .contains("kubectl -n \"$EXAMPLE_NAMESPACE\" exec")
                .contains("-- cat \"$EXAMPLE_SANITIZED_CONFIG_IN_POD\"")
                .contains("does not require `tar` in the application image");
    }

    @Test
    void v28SemanticReadSecurityAndOperationsGuidanceIsCompleteAndBounded() throws IOException {
        Path root = projectRoot();
        String caching = Files.readString(root.resolve("docs/32-response-caching.md"));
        String safetyReview = markdownSection(
                caching, "## Application safety review", "## Phase-one runtime behavior");
        String normalizedSafetyReview = safetyReview.replaceAll("\\s+", " ");
        int commandBlockStart = normalizedSafetyReview.indexOf("```java");
        int commandBlockEnd = normalizedSafetyReview.indexOf(
                "```", commandBlockStart + "```java".length());
        assertThat(commandBlockStart).isNotNegative();
        assertThat(commandBlockEnd).isGreaterThan(commandBlockStart);
        String commandExamples = normalizedSafetyReview.substring(commandBlockStart, commandBlockEnd);
        String operations = Files.readString(root.resolve("docs/30-operations-troubleshooting.md"));
        String supportBundles = Files.readString(root.resolve("docs/26-support-bundles.md"));
        String examples = Files.readString(root.resolve("docs/examples/effective-configuration.md"));
        String production = Files.readString(root.resolve("docs/16-production-checklist.md"));
        String boot4Migration = Files.readString(
                root.resolve("docs/28-spring-boot-4-jackson-migration.md"));
        String releaseNotes = markdownSection(Files.readString(root.resolve("CHANGELOG.md")),
                "## [Unreleased]", "## [4.0.0]");
        JsonNode fixture = OBJECT_MAPPER.readTree(
                root.resolve("docs/fixtures/support-bundle-response-cache.json").toFile());

        assertThat(caching)
                .contains("Existing explicit `GET` selection remains the\ncache-friendly path; `GET` is not cached automatically")
                .contains("### Semantic-read examples")
                .contains("@POST(\"/catalog/search\")")
                .contains("@ApiRef(\"report-query\")")
                .contains("vary-by-parameters: [criteria]")
                .contains("vary-by-headers: [Idempotency-Key, X-Tenant-Scope]")
                .contains("vary-by-context: [principalScope]")
                .contains("## Compatibility and related contracts")
                .contains("existing compiled and\nsource `4.0.0` cache clients")
                .contains("[Resilience4j](07-resilience4j.md)")
                .contains("[Outbound Auth Providers](06-auth-providers.md)")
                .contains("[Redirect Responses](03-error-handling.md#redirect-responses)")
                .contains("[Timeouts](04-timeouts.md)")
                .contains("[Production Checklist](16-production-checklist.md)")
                .contains("[Operations Troubleshooting](30-operations-troubleshooting.md)")
                .contains("[Support Bundles](26-support-bundles.md#response-cache-incidents)")
                .contains("[Spring Boot 4 and Starter 4.x Migration](28-spring-boot-4-jackson-migration.md)");
        assertThat(boot4Migration)
                .contains("explicit `GET` response caching on published\n`4.0.0` remain source and binary compatible")
                .contains("`CacheResponse.semanticRead()` is an additive, false-defaulted member")
                .contains("client-wide cache policy does not supply\nthat acknowledgement");
        assertThat(releaseNotes)
                .contains("`4.1.0` published release")
                .contains("Existing explicit `GET` behavior remains unchanged")
                .contains("each selected non-`GET`\n  method requires its own `semanticRead = true` acknowledgement")
                .contains("Ordinary writes remain unselected")
                .contains("excludes distributed caching, automatic invalidation")
                .contains("public performance claims")
                .doesNotContain("all POST", "all PUT", "all PATCH", "all DELETE",
                        "suppresses duplicate writes");

        assertThat(normalizedSafetyReview)
                .contains("endpoint owner must approve")
                .contains("A false declaration can suppress a required action")
                .contains("share one caller's response with another caller")
                .contains("Side effects")
                .contains("Body determinism")
                .contains("Response variants")
                .contains("Auth and tenant partition")
                .contains("TTL and hard expiry")
                .contains("Refresh")
                .contains("Invalidation owner")
                .contains("Idempotency does not authorize local response reuse")
                .contains("Retry configuration does not authorize local response reuse")
                .contains("`Cache-Control` does not authorize local response reuse")
                .contains("Ordinary writes, payments, job submissions, commands, and mutations stay unselected");
        assertThat(commandExamples)
                .contains("@POST(\"/payments\") @CacheDisabled Mono<PaymentReceipt> submitPayment")
                .contains("@POST(\"/jobs\") @CacheDisabled Mono<JobReceipt> submitJob")
                .contains("@PUT(\"/customers/{id}\") @CacheDisabled Mono<Customer> updateCustomer")
                .contains("@POST(\"/commands\") @CacheDisabled Mono<CommandReceipt> executeCommand")
                .doesNotContain("@CacheResponse", "semanticRead");
        assertThat(operations.replaceAll("\\s+", " "))
                .contains("### Dispatch suppression and duplicate diagnosis")
                .contains("Cache hit")
                .contains("Single-flight waiter")
                .contains("Hidden refresh")
                .contains("Resilience4j Retry")
                .contains("Automatic redirect")
                .contains("One-time auth replay")
                .contains("Reactor Netty transport retry")
                .contains("Downstream duplicate handling")
                .contains("rolling configuration differences")
                .contains("hard expiry")
                .contains("refresh failure")
                .contains("capacity pressure")
                .contains("no distributed coherence")
                .contains("no write-through or write-behind behavior");
        assertThat(supportBundles.replaceAll("\\s+", " "))
                .contains("resolved HTTP verb")
                .contains("bounded semantic-read acknowledgement")
                .contains("cache outcome")
                .contains("subscription-attempt count")
                .contains("request-dispatch evidence");
        assertThat(examples)
                .contains("## Semantic Read Cache Examples")
                .contains("https://catalog-search.example.invalid")
                .contains("https://reporting-rpc.example.invalid")
                .contains("${EXAMPLE_CATALOG_CLIENT_ID}")
                .contains("${EXAMPLE_CATALOG_CLIENT_SECRET}")
                .contains("<groupId>com.github.ben-manes.caffeine</groupId>")
                .contains("<artifactId>caffeine</artifactId>")
                .contains("catalogTracingCustomizer: SAFE")
                .contains("reportingTracingCustomizer: SAFE")
                .contains("vary-by-parameters: [criteria]")
                .contains("vary-by-headers: [Idempotency-Key, X-Tenant-Scope]")
                .contains("vary-by-headers: [Idempotency-Key]")
                .contains("vary-by-context: [principalScope]")
                .contains("observability:\n      enabled: true\n      cache:\n        enabled: true")
                .contains("@CacheResponse(value = \"catalog-search\", semanticRead = true)")
                .contains("@CacheResponse(value = \"reporting-query\", semanticRead = true)")
                .doesNotContain("client-secret: EXAMPLE_");
        assertThat(production.replaceAll("\\s+", " "))
                .contains("endpoint-owner approval")
                .contains("payments, job submissions, commands, or mutations");

        JsonNode terminalCaller = fixture.path("terminalCaller");
        assertThat(terminalCaller.path("resolvedHttpMethod").isTextual()).isTrue();
        assertThat(terminalCaller.path("resolvedHttpMethod").asText()).isEqualTo("POST");
        assertThat(terminalCaller.path("cacheSemanticReadAcknowledged").isBoolean()).isTrue();
        assertThat(terminalCaller.path("cacheSemanticReadAcknowledged").asBoolean()).isTrue();
        assertThat(terminalCaller.path("cacheOutcome").isTextual()).isTrue();
        assertThat(terminalCaller.path("subscriptionAttemptCount").isIntegralNumber()).isTrue();
        assertThat(terminalCaller.path("requestDispatched").isBoolean()).isTrue();
        assertThat(sensitiveSupportFixtureFieldNames(fixture)).isEmpty();
    }

    @Test
    void v23OperationsGuidanceIsAlignedBoundedDiscoverableAndVersionScoped() throws Exception {
        Path root = projectRoot();
        String pomXml = Files.readString(root.resolve("pom.xml"));
        String publishedVersion = pomProperty(pomXml, "latest.published.version");
        String projectVersion = projectVersion(root.resolve("pom.xml"));
        String operations = Files.readString(root.resolve("docs/30-operations-troubleshooting.md"));
        String currentScope = markdownSection(operations, "## Current release scope", "## First response");
        String supportBundles = Files.readString(root.resolve("docs/26-support-bundles.md"));
        String timeoutContract = Files.readString(root.resolve("docs/04-timeouts.md"));
        String authContract = Files.readString(root.resolve("docs/06-auth-providers.md"));
        String streamingContract = Files.readString(root.resolve("docs/11-streaming.md"));
        String compressionContract = Files.readString(root.resolve("docs/12-proxy-tls.md"));
        String diagnosticsContexts = Files.readString(root.resolve("docs/21-diagnostic-contexts.md"));
        String productionChecklist = Files.readString(root.resolve("docs/16-production-checklist.md"));
        String readme = Files.readString(root.resolve("README.md"));

        assertThat(currentScope)
                .contains("published starter `" + publishedVersion + "`")
                .contains("Historical benchmark reports, API reports, migration decisions, and changelog")
                .contains("Do not update their\nversions")
                .doesNotContain("`" + projectVersion + "`");
        assertThat(operations)
                .contains("## Protocol and framing")
                .contains("## Compression")
                .contains("## Pool saturation")
                .contains("## Pre-response transport failures")
                .contains("## Timeout phases")
                .contains("## Streaming ownership")
                .contains("## OAuth2 refresh")
                .contains("## Failure attribution")
                .contains("## Evidence boundary")
                .contains("one affected client, one logical-call\nwindow")
                .contains("Use `.example.invalid` hosts and `EXAMPLE_` placeholders")
                .contains("do not collect request or response payloads by default")
                .contains("A missing status or failure stage means\n   unknown")
                .contains("GET /bad-request HTTP/1.0")
                .contains("DNS_RESOLUTION")
                .contains("PROXY_CONNECT")
                .contains("TLS_HANDSHAKE")
                .contains("POOL_ACQUIRE")
                .contains("Auth-provider failures are a hard boundary")
                .contains("arbitrary custom-filter wrappers\nwithout final-request dispatch evidence stay unattributed")
                .contains("Each real retry, body-preserving redirect, or one-time 401 refresh")
                .contains("Shared refresh is single-flight")
                .contains("Provider diagnostics\ndescribe configured clients");
        assertThat(supportBundles)
                .contains("published starter `" + publishedVersion + "`")
                .contains("## Protocol and Compression Incidents")
                .contains("## DNS, Proxy, Connect, and TLS Incidents")
                .contains("## Failure Attribution Incidents")
                .contains("do not report only the synthetic")
                .contains("Do not include request or response payloads by default")
                .contains("A missing stage\nis unknown")
                .contains("[Operations Troubleshooting](30-operations-troubleshooting.md)")
                .contains("codecMaxInMemorySizeMb")
                .contains("framing-complete truncated gzip member");
        assertThat(timeoutContract)
                .contains("## End-to-end logical-call budget")
                .contains("opt-in and subscription-local")
                .contains("stay inside that same deadline")
                .contains("Dispatch evidence is reset for every resilience retry");
        assertThat(authContract)
                .contains("owns a separate Reactor Netty connection pool")
                .contains("business Resilience4j operators")
                .contains("payments-api.example.invalid")
                .contains("identity.example.invalid")
                .contains("${EXAMPLE_PAYMENT_CLIENT_SECRET}")
                .doesNotContain("https://api.example.com")
                .doesNotContain("https://auth.example.com")
                .doesNotContain(".clientSecret(\"...\")");
        assertThat(streamingContract)
                .contains("The transport owns framing")
                .contains("application-owned")
                .contains("A retry, a body-preserving redirect, or the built-in one-time 401 auth refresh")
                .contains("closes or releases it exactly once");
        assertThat(compressionContract)
                .contains("| Encoded wire bytes |")
                .contains("Decoded unary value")
                .contains("Bodiless result")
                .contains("Direct or envelope `Flux<DataBuffer>`")
                .contains("size is **unknown**, not zero")
                .contains("closes the affected pooled");
        assertThat(diagnosticsContexts)
                .contains("`compressionEnabled`")
                .contains("`codecMaxInMemorySizeMb`")
                .contains("collection overloads render those provider-only values as `null`");
        assertThat(productionChecklist)
                .contains("Leave `compression-enabled: false`")
                .contains("[Operations Troubleshooting](30-operations-troubleshooting.md)");
        assertThat(readme)
                .contains("[Operations Troubleshooting](docs/30-operations-troubleshooting.md)");
    }

    @Test
    void v24DocumentationConsolidatesContractsAndHistoricalEvidence() throws IOException {
        Path root = projectRoot();
        String productionChecklist = Files.readString(root.resolve("docs/16-production-checklist.md"));
        String resilience = Files.readString(root.resolve("docs/07-resilience4j.md"));
        String transport = Files.readString(root.resolve("docs/12-proxy-tls.md"));
        String operations = Files.readString(root.resolve("docs/30-operations-troubleshooting.md"));
        String supportBundles = Files.readString(root.resolve("docs/26-support-bundles.md"));
        String readme = Files.readString(root.resolve("README.md"));

        assertThat(productionChecklist)
                .contains("### Supported return shapes")
                .contains("| `Mono<Void>` |")
                .contains("| `Mono<ResponseEntity<Flux<DataBuffer>>>` |")
                .contains("### Replay-safety decision path")
                .contains("actual nonblank `Idempotency-Key`")
                .contains("cold replayable publisher or reopenable resource")
                .contains("one logical-call timeout around one bulkhead")
                .contains("`HTTP` uses `CONNECT` for HTTP and HTTPS targets")
                .contains("GOAWAY alone never proves replay safety");
        assertThat(resilience)
                .contains("retry -> rate-limiter -> circuit-breaker -> bulkhead")
                .contains("One-time `401` auth refresh")
                .contains("Automatic `307`/`308` redirect");
        assertThat(transport)
                .contains("uses HTTP `CONNECT` for both `http://` and `https://` targets")
                .contains("Deprecated compatibility alias for `HTTP`")
                .contains("When the pool has spare physical capacity")
                .contains("verify it over HTTP/1.1 and TLS\nH2");
        assertThat(operations)
                .contains("canonical current first-response index")
                .contains("Use `.example.invalid` hosts and `EXAMPLE_` placeholders")
                .contains("[Production Support Bundles](26-support-bundles.md#baseline-bundle)");
        assertThat(supportBundles)
                .contains("canonical current capture procedure")
                .contains("[Operations Troubleshooting](30-operations-troubleshooting.md)")
                .contains("`.example.invalid`")
                .contains("EXAMPLE_");
        assertThat(readme)
                .contains("Current return-shape, replay-safety, transport, and production checks")
                .contains("Canonical current incident-capture procedure")
                .contains("Immutable historical promoted benchmark evidence")
                .contains("Current Boot 3-to-Boot 4 migration guidance");

        Map<String, String> historicalEvidence = Map.of(
                "docs/27-v16-to-v17-adoption.md", "Immutable historical migration evidence",
                "docs/29-v19-release-decision.md", "Immutable historical release evidence",
                "docs/api-report-2.14.0-to-3.0.0-candidate.md", "Immutable historical API evidence",
                "docs/api-report-2.14.1-to-3.0.0.md", "Immutable historical API evidence",
                "docs/benchmark-report-2.9.0.md", "Immutable historical benchmark evidence",
                "docs/benchmark-report-2.10.0.md", "Immutable historical benchmark evidence",
                "docs/benchmark-report-2.11.0.md", "Immutable historical benchmark evidence",
                "docs/benchmark-report-2.12.0.md", "Immutable historical benchmark evidence");
        for (Map.Entry<String, String> entry : historicalEvidence.entrySet()) {
            assertThat(Files.readString(root.resolve(entry.getKey())))
                    .as(entry.getKey())
                    .contains(entry.getValue());
        }
        assertThat(Files.readString(root.resolve("docs/28-spring-boot-4-jackson-migration.md")))
                .contains("**Current migration guide.**")
                .contains("immutable\n> historical evidence");
    }

    @Test
    void v25DocumentationConsolidatesRequestAndOperationsContracts() throws IOException {
        Path root = projectRoot();
        String annotations = Files.readString(root.resolve("docs/02-annotations.md"));
        String multipart = Files.readString(root.resolve("docs/10-multipart.md"));
        String pool = Files.readString(root.resolve("docs/05-connection-pool.md"));
        String mock = Files.readString(root.resolve("docs/14-test-helpers.md"));
        String production = Files.readString(root.resolve("docs/16-production-checklist.md"));
        String supportBundles = Files.readString(root.resolve("docs/26-support-bundles.md"));
        String operations = Files.readString(root.resolve("docs/30-operations-troubleshooting.md"));
        String readme = Files.readString(root.resolve("README.md"));

        assertThat(annotations)
                .contains("## Parameter annotations")
                .contains("| Path | `@PathVar(\"name\")` |")
                .contains("| Header map | `@HeaderParam Map<?, ?>` |")
                .contains("Startup validation checks the declaration")
                .contains("Per-invocation validation checks values unavailable at")
                .contains("The no-body status contract is the same over HTTP/1.1 and H2/H2C")
                .contains("The configured client base URL is the only declarative authority");
        assertThat(multipart)
                .contains("Parts are written in method-parameter declaration order")
                .contains("each opened stream is closed once")
                .contains("List order is the global wire part order");
        assertThat(pool)
                .contains("disables Reactor Netty's one-time connection-reset")
                .contains("but replacement capacity is not\nrequest replay");
        assertThat(mock)
                .contains("Recorded URIs are in-process resolved request facts")
                .contains("does not negotiate an HTTP protocol or TLS")
                .contains("These records do not\nprove HTTP/1.1 framing");
        assertThat(production)
                .contains("[request-parameter grammar](02-annotations.md#parameter-annotations)")
                .contains("[wire-order and resource-ownership contract](10-multipart.md#wire-order-and-framing)");
        assertThat(supportBundles)
                .contains("## Stale Connection Recovery Incidents")
                .contains("Do not merge the replacement URL, status, headers, error, or failure stage")
                .contains("complete first Reactor Netty decoder exception")
                .contains("EXAMPLE_MANAGEMENT_URL")
                .contains(".example.invalid");
        assertThat(operations)
                .contains("GET /bad-request HTTP/1.0")
                .contains("[stale-connection support bundle](26-support-bundles.md#stale-connection-recovery-incidents)")
                .contains("one-time `401` auth invalidation and\nrefresh replay");
        assertThat(readme)
                .contains("[Annotation Reference](docs/02-annotations.md)")
                .contains("[Multipart Uploads](docs/10-multipart.md)")
                .contains("[Streaming Requests and Responses](docs/11-streaming.md)")
                .contains("[Production Checklist](docs/16-production-checklist.md)")
                .contains("[Operations Troubleshooting](docs/30-operations-troubleshooting.md)");

        Pattern remoteUrlHost = Pattern.compile("https?://([^\\s/\\\"'`)>]+)");
        Pattern configuredHost = Pattern.compile("(?m)^\\s*(?:host|server-name):\\s*[\\\"']?([^\\s#\\\"']+)");
        Pattern unsupportedExampleSuffix = Pattern.compile("\\.example(?!\\.invalid)");
        Set<String> publicDocumentationHosts = Set.of(
                "github.com", "img.shields.io", "opentelemetry.io", "search.maven.org");
        List<Path> documentationPaths = new ArrayList<>();
        documentationPaths.add(root.resolve("README.md"));
        try (Stream<Path> paths = Files.walk(root.resolve("docs"))) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .forEach(documentationPaths::add);
        }

        for (Path documentationPath : documentationPaths) {
            String path = root.relativize(documentationPath).toString();
            String source = Files.readString(documentationPath);
            assertThat(unsupportedExampleSuffix.matcher(source).find())
                    .as("%s contains a non-.example.invalid placeholder", path)
                    .isFalse();

            List<String> documentedHosts = new ArrayList<>();
            Matcher urlMatcher = remoteUrlHost.matcher(source);
            while (urlMatcher.find()) {
                documentedHosts.add(urlMatcher.group(1));
            }
            Matcher hostMatcher = configuredHost.matcher(source);
            while (hostMatcher.find()) {
                documentedHosts.add(hostMatcher.group(1));
            }

            for (String host : documentedHosts) {
                String normalized = host.toLowerCase(Locale.ROOT).replaceFirst(":\\d+$", "");
                boolean safe = normalized.endsWith(".example.invalid")
                        || normalized.equals("localhost")
                        || normalized.equals("127.0.0.1")
                        || host.contains("EXAMPLE_")
                        || host.startsWith("<")
                        || publicDocumentationHosts.contains(normalized);
                assertThat(safe)
                        .as("%s contains an unapproved copyable remote host: %s", path, host)
                        .isTrue();
            }
        }
    }

    @Test
    void supportBundleTerminalFixturesStaySanitizedAndStructurallyAccurate() throws IOException {
        Path root = projectRoot();
        String supportBundles = Files.readString(root.resolve("docs/26-support-bundles.md"));
        String validationText = Files.readString(
                root.resolve("docs/fixtures/support-bundle-request-validation.json"));
        String staleRecoveryText = Files.readString(
                root.resolve("docs/fixtures/support-bundle-stale-connection-recovery.json"));
        String terminalOutcomesText = Files.readString(
                root.resolve("docs/fixtures/support-bundle-terminal-outcomes.json"));
        String healthText = Files.readString(
                root.resolve("docs/fixtures/support-bundle-health.json"));
        JsonNode validation = OBJECT_MAPPER.readTree(validationText);
        JsonNode staleRecovery = OBJECT_MAPPER.readTree(staleRecoveryText);
        JsonNode terminalOutcomes = OBJECT_MAPPER.readTree(terminalOutcomesText);
        JsonNode health = OBJECT_MAPPER.readTree(healthText);

        assertThat(supportBundles)
                .contains("(fixtures/support-bundle-request-validation.json)")
                .contains("(fixtures/support-bundle-stale-connection-recovery.json)")
                .contains("(fixtures/support-bundle-terminal-outcomes.json)")
                .contains("(fixtures/support-bundle-health.json)")
                .contains("illustrative sanitized records, not raw logger output");
        assertThat(validation.path("incidentType").asText()).isEqualTo("request-validation");
        assertThat(validation.has("terminalRecordCreated")).isTrue();
        assertThat(validation.path("terminalRecordCreated").isBoolean()).isTrue();
        assertThat(validation.path("terminalRecordCreated").asBoolean()).isFalse();
        assertThat(validation.has("subscriptionAttemptCount")).isTrue();
        assertThat(validation.path("subscriptionAttemptCount").isIntegralNumber()).isTrue();
        assertThat(validation.path("subscriptionAttemptCount").asInt()).isZero();
        assertThat(validation.has("requestDispatched")).isTrue();
        assertThat(validation.path("requestDispatched").isBoolean()).isTrue();
        assertThat(validation.path("requestDispatched").asBoolean()).isFalse();
        assertThat(validation.path("failureStage").isNull()).isTrue();
        assertThat(validation.path("responseStatus").isNull()).isTrue();
        assertThat(validation.path("responseHeaders").isEmpty()).isTrue();

        JsonNode captureWindow = staleRecovery.path("captureWindow");
        assertThat(captureWindow.path("startedAt").asText()).isEqualTo("EXAMPLE_WINDOW_START");
        assertThat(captureWindow.path("durationMs").isIntegralNumber()).isTrue();
        assertThat(captureWindow.path("durationMs").asInt()).isPositive();
        assertThat(staleRecovery.path("httpProtocol").asText()).isEqualTo("HTTP/1.1");

        JsonNode downstreamEvidence = staleRecovery.path("downstreamEvidence");
        assertThat(downstreamEvidence.path("requestCount").isIntegralNumber()).isTrue();
        assertThat(downstreamEvidence.path("requestCount").asInt()).isEqualTo(2);
        assertThat(downstreamEvidence.path("connectionSequenceMarkers").size()).isEqualTo(2);

        JsonNode replayPolicy = staleRecovery.path("replayPolicy");
        assertThat(replayPolicy.path("resilienceRetryEnabled").isBoolean()).isTrue();
        assertThat(replayPolicy.path("resilienceRetryEnabled").asBoolean()).isFalse();
        assertThat(replayPolicy.path("authReplayEnabled").isBoolean()).isTrue();
        assertThat(replayPolicy.path("authReplayEnabled").asBoolean()).isFalse();
        assertThat(replayPolicy.path("automaticRedirectsEnabled").isBoolean()).isTrue();
        assertThat(replayPolicy.path("automaticRedirectsEnabled").asBoolean()).isFalse();
        assertThat(replayPolicy.path("idempotencyKeyPresent").isBoolean()).isTrue();
        assertThat(replayPolicy.path("idempotencyKeyPresent").asBoolean()).isFalse();
        assertThat(replayPolicy.path("bodyRepeatability").asText()).isEqualTo("REPEATABLE");

        JsonNode poolGaugeSamples = staleRecovery.path("poolGaugeSamples");
        assertThat(poolGaugeSamples.size()).isEqualTo(3);
        assertThat(poolGaugeSamples.get(0).path("phase").asText()).isEqualTo("before-failure");
        assertThat(poolGaugeSamples.get(1).path("phase").asText()).isEqualTo("replacement-waiting");
        assertThat(poolGaugeSamples.get(2).path("phase").asText()).isEqualTo("after-termination");
        for (JsonNode sample : poolGaugeSamples) {
            assertThat(sample.path("activeConnections").isIntegralNumber()).isTrue();
            assertThat(sample.path("pendingConnections").isIntegralNumber()).isTrue();
            assertThat(sample.path("idleConnections").isIntegralNumber()).isTrue();
            assertThat(sample.path("totalConnections").isIntegralNumber()).isTrue();
        }

        JsonNode records = staleRecovery.path("metadataOnlyExchangeRecords");
        assertThat(records.size()).isEqualTo(2);
        assertThat(records.get(0).path("terminalOffsetMs").isIntegralNumber()).isTrue();
        assertThat(records.get(0).path("terminalOffsetMs").asInt())
                .isLessThanOrEqualTo(captureWindow.path("durationMs").asInt());
        assertThat(records.get(0).path("connectionSequenceMarker").asText())
                .isEqualTo(downstreamEvidence.path("connectionSequenceMarkers").get(0).asText());
        assertThat(records.get(0).path("requestDispatched").isBoolean()).isTrue();
        assertThat(records.get(0).path("requestDispatched").asBoolean()).isTrue();
        assertThat(records.get(0).path("subscriptionAttemptCount").asInt()).isEqualTo(1);
        assertThat(records.get(0).path("errorType").asText()).isEqualTo("PrematureCloseException");
        assertThat(records.get(0).path("causeTypes").get(0).asText()).isEqualTo("PrematureCloseException");
        assertThat(records.get(0).path("errorCategory").asText()).isEqualTo("TIMEOUT");
        assertThat(records.get(0).path("responseStatus").isNull()).isTrue();
        assertThat(records.get(0).path("responseHeaders").isEmpty()).isTrue();
        assertThat(records.get(1).path("terminalOffsetMs").isIntegralNumber()).isTrue();
        assertThat(records.get(1).path("terminalOffsetMs").asInt())
                .isLessThanOrEqualTo(captureWindow.path("durationMs").asInt());
        assertThat(records.get(1).path("connectionSequenceMarker").asText())
                .isEqualTo(downstreamEvidence.path("connectionSequenceMarkers").get(1).asText());
        assertThat(records.get(1).path("requestDispatched").isBoolean()).isTrue();
        assertThat(records.get(1).path("requestDispatched").asBoolean()).isTrue();
        assertThat(records.get(1).path("subscriptionAttemptCount").asInt()).isEqualTo(1);
        assertThat(records.get(1).path("responseStatus").asInt()).isEqualTo(200);
        assertThat(records.get(1).path("errorType").isNull()).isTrue();
        assertThat(records.get(1).path("causeTypes").isEmpty()).isTrue();

        assertThat(terminalOutcomes.path("schemaVersion").isIntegralNumber()).isTrue();
        assertThat(terminalOutcomes.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(terminalOutcomes.path("durationUnit").asText()).isEqualTo("milliseconds");
        JsonNode terminalRecords = terminalOutcomes.path("records");
        assertThat(terminalRecords.size()).isEqualTo(3);
        for (JsonNode record : terminalRecords) {
            for (String field : List.of(
                    "incidentClass", "clientName", "apiName", "httpMethod",
                    "pathTemplate", "outcome", "errorType", "errorCategory")) {
                assertThat(record.has(field)).as("terminal fixture field %s", field).isTrue();
                assertThat(record.path(field).isTextual()).isTrue();
            }
            JsonNode duration = record.path("logicalCallDuration");
            assertThat(duration.isObject()).isTrue();
            assertThat(duration.has("value")).isTrue();
            assertThat(duration.path("value").isIntegralNumber()).isTrue();
            assertThat(duration.path("value").asLong()).isBetween(0L, 60_000L);
            assertThat(duration.has("unit")).isTrue();
            assertThat(duration.path("unit").asText()).isEqualTo("milliseconds");
            assertThat(record.has("subscriptionAttemptCount")).isTrue();
            assertThat(record.path("subscriptionAttemptCount").isIntegralNumber()).isTrue();
            assertThat(record.has("requestDispatched")).isTrue();
            assertThat(record.path("requestDispatched").isBoolean()).isTrue();
            assertThat(record.has("failureStage")).isTrue();
            assertThat(record.has("responseStatus")).isTrue();
            assertThat(record.has("responseHeaders")).isTrue();
            assertThat(record.path("responseHeaders").isObject()).isTrue();
            assertThat(record.path("responseHeaders").isEmpty()).isTrue();
        }

        JsonNode resilienceRejection = terminalRecords.get(0);
        assertThat(resilienceRejection.path("incidentClass").asText())
                .isEqualTo("fast-resilience-rejection");
        assertThat(resilienceRejection.path("subscriptionAttemptCount").asInt()).isZero();
        assertThat(resilienceRejection.path("requestDispatched").asBoolean()).isFalse();
        assertThat(resilienceRejection.path("errorCategory").asText()).isEqualTo("RESILIENCE_ERROR");
        assertThat(resilienceRejection.path("failureStage").isNull()).isTrue();
        assertThat(resilienceRejection.path("responseStatus").isNull()).isTrue();

        JsonNode transportFailure = terminalRecords.get(1);
        assertThat(transportFailure.path("incidentClass").asText()).isEqualTo("transport-failure");
        assertThat(transportFailure.path("subscriptionAttemptCount").asInt()).isEqualTo(1);
        assertThat(transportFailure.path("requestDispatched").asBoolean()).isTrue();
        assertThat(transportFailure.path("errorCategory").asText()).isEqualTo("TIMEOUT");
        assertThat(transportFailure.path("failureStage").asText()).isEqualTo("CONNECT");
        assertThat(transportFailure.path("responseStatus").isNull()).isTrue();

        JsonNode httpFailure = terminalRecords.get(2);
        assertThat(httpFailure.path("incidentClass").asText()).isEqualTo("downstream-http-failure");
        assertThat(httpFailure.path("subscriptionAttemptCount").asInt()).isEqualTo(1);
        assertThat(httpFailure.path("requestDispatched").asBoolean()).isTrue();
        assertThat(httpFailure.path("errorCategory").asText()).isEqualTo("SERVER_ERROR");
        assertThat(httpFailure.path("failureStage").isNull()).isTrue();
        assertThat(httpFailure.path("responseStatus").isIntegralNumber()).isTrue();
        assertThat(httpFailure.path("responseStatus").asInt()).isEqualTo(503);

        assertThat(health.path("status").asText()).isEqualTo("DOWN");
        JsonNode healthDetails = health.path("details");
        assertThat(healthDetails.size()).isEqualTo(3);
        assertThat(healthDetails.path("errorRateThreshold").isNumber()).isTrue();
        assertThat(healthDetails.path("minSamples").isIntegralNumber()).isTrue();
        JsonNode clientHealth = healthDetails.path("inventory-api");
        assertThat(clientHealth.size()).isEqualTo(10);
        for (String field : List.of(
                "samples",
                "errors",
                "sampleCount",
                "errorCount",
                "poolAcquireFailureCount",
                "minSamples")) {
            assertThat(clientHealth.has(field)).as("health fixture field %s", field).isTrue();
            assertThat(clientHealth.path(field).isIntegralNumber()).isTrue();
            assertThat(clientHealth.path(field).asLong()).isGreaterThanOrEqualTo(0L);
        }
        assertThat(clientHealth.path("errorRateThreshold").isNumber()).isTrue();
        assertThat(clientHealth.path("errorRate").isNumber()).isTrue();
        double calculatedErrorRate = (double) clientHealth.path("errors").asLong()
                / (double) clientHealth.path("samples").asLong();
        assertThat(Math.abs(clientHealth.path("errorRate").asDouble() - calculatedErrorRate))
                .isLessThanOrEqualTo(0.000000000001d);
        assertThat(clientHealth.path("status").isTextual()).isTrue();
        assertThat(clientHealth.path("status").asText()).isIn("UP", "DOWN", "INSUFFICIENT_SAMPLES");
        assertThat(clientHealth.path("reason").isTextual()).isTrue();
        assertThat(clientHealth.path("reason").asText()).isIn(
                "NO_SAMPLES", "INSUFFICIENT_SAMPLES",
                "ERROR_RATE_WITHIN_THRESHOLD", "ERROR_RATE_ABOVE_THRESHOLD");

        assertThat(validationText + staleRecoveryText + terminalOutcomesText + healthText)
                .doesNotContain("Authorization", "Cookie", "client-secret", "Bearer ")
                .doesNotContain("http://", "https://", "responseBody", "errorMessage")
                .doesNotContain("/home/", "/Users/", "/workspace/", "/tmp/", "C:\\Users\\");
    }

    @Test
    void releaseVersionContractDistinguishesSnapshotCandidateAndPublishedStates() {
        ReleaseVersionContract snapshot = releaseVersionContract(
                "3.1.0-SNAPSHOT", "3.0.0", "## [Unreleased]\n");
        assertThat(snapshot).isEqualTo(new ReleaseVersionContract(
                "snapshot-development", "3.1.0-SNAPSHOT", "3.0.0", null, "3.0.0"));

        ReleaseVersionContract candidate = releaseVersionContract(
                "3.1.0", "3.0.0", "## [Unreleased]\n");
        assertThat(candidate).isEqualTo(new ReleaseVersionContract(
                "release-candidate", null, "3.0.0", "3.1.0", "3.0.0"));

        ReleaseVersionContract datedCandidate = releaseVersionContract(
                "3.1.0", "3.0.0", "## [3.1.0] - 2026-07-21\n");
        assertThat(datedCandidate).isEqualTo(new ReleaseVersionContract(
                "release-candidate", null, "3.0.0", "3.1.0", "3.0.0"));

        ReleaseVersionContract published = releaseVersionContract(
                "3.1.0", "3.1.0", "## [3.1.0] - 2026-07-15\n");
        assertThat(published).isEqualTo(new ReleaseVersionContract(
                "post-publication", null, "3.1.0", null, "3.1.0"));

        assertThat(majorReleaseCandidate("3.1.0-SNAPSHOT", snapshot))
                .containsEntry("version", "3.1.0")
                .containsEntry("status", "deferred")
                .containsEntry("published", false);
        assertThat(majorReleaseCandidate("3.1.0", candidate))
                .containsEntry("version", "3.1.0")
                .containsEntry("status", "pending-publication")
                .containsEntry("published", false)
                .containsEntry("pendingWork", List.of("publication"));
        assertThat(majorReleaseCandidate("3.1.0", published))
                .containsEntry("version", "3.1.0")
                .containsEntry("status", "published")
                .containsEntry("published", true)
                .containsEntry("pendingWork", List.of());

        assertThat(benchmarkEvidence("3.1.0-SNAPSHOT", "3.0.0", snapshot, false).get("promotedReport")).isNull();
        // A non-snapshot release without the report on disk stays deferred/pending.
        assertThat(benchmarkEvidence("3.1.0", "3.0.0", candidate, false).get("promotedReport")).isNull();
        assertThat(benchmarkEvidence("3.1.0", "3.0.0", published, false).get("promotedReport")).isNull();
        // Once a release-quality report is promoted to docs/, the manifest surfaces it.
        assertThat(benchmarkEvidence("3.1.0", "3.0.0", candidate, true).get("promotedReport"))
                .isEqualTo("docs/benchmark-report-3.1.0.md");
        assertThat(benchmarkEvidence("3.1.0", "3.0.0", published, true).get("promotedReport"))
                .isEqualTo("docs/benchmark-report-3.1.0.md");
    }

    @Test
    void reactorFixturesAndPublicationGuardStayVersionAligned() throws Exception {
        Path root = projectRoot();
        String reactorVersion = projectVersion(root.resolve("pom.xml"));
        String publishWorkflow = Files.readString(root.resolve(".github/workflows/publish-maven-central.yml"));

        assertThat(reactorVersion).isEqualTo("4.2.0-SNAPSHOT");
        assertThat(projectVersion(root.resolve("reactive-http-client-starter/pom.xml"))).isEqualTo(reactorVersion);
        assertThat(projectVersion(root.resolve("reactive-http-client-test/pom.xml"))).isEqualTo(reactorVersion);
        assertThat(projectVersion(root.resolve("reactive-http-client-otel/pom.xml"))).isEqualTo(reactorVersion);
        assertThat(projectVersion(root.resolve("reactive-http-client-benchmarks/pom.xml"))).isEqualTo(reactorVersion);
        assertThat(pomProperty(Files.readString(root.resolve(".github/native-smoke/pom.xml")),
                "reactive-http-client.version")).isEqualTo(reactorVersion);
        assertThat(pomProperty(Files.readString(root.resolve(".github/boot4-consumer/pom.xml")),
                "reactive-http-client.version")).isEqualTo(reactorVersion);
        assertThat(publishWorkflow)
                .contains("Refusing to publish SNAPSHOT version")
                .contains("$GITHUB_REF_TYPE\" != \"tag")
                .contains("$GITHUB_REF_NAME\" != \"v$VERSION")
                .contains("expected tag v$VERSION");
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
        String readme = Files.readString(root.resolve("README.md"));
        String quickStart = Files.readString(root.resolve("docs/01-quick-start.md"));
        String releaseDocs = Files.readString(root.resolve("docs/20-native-release-compatibility.md"));
        String benchmarkDocs = Files.readString(root.resolve("docs/22-benchmarks.md"));
        String majorMigration = Files.readString(root.resolve("docs/31-3x-to-4x-resilience-migration.md"));
        String ciWorkflow = Files.readString(root.resolve(".github/workflows/ci.yml"));
        JsonNode manifest = OBJECT_MAPPER.valueToTree(releaseEvidenceManifest(root.resolve("pom.xml")));

        assertThat(projectVersion(root.resolve("pom.xml"))).isEqualTo("4.2.0-SNAPSHOT");
        assertThat(pomProperty(pomXml, "latest.published.version")).isEqualTo("4.1.0");
        assertThat(pomProperty(pomXml, "api.compatibility.baseline.version")).isEqualTo("4.1.0");
        assertThat(pomProperty(pomXml, "spring-boot.version")).isEqualTo("4.0.0");
        assertThat(pomProperty(pomXml, "resilience4j.version")).isEqualTo("2.4.0");
        assertThat(pomXml)
                .contains("<id>resolve-published-api-baseline-pom</id>")
                .contains("${project.groupId}:${project.artifactId}:${api.compatibility.baseline.version}:pom")
                .contains("<transitive>false</transitive>");
        assertThat(readme)
                .contains("<version>4.1.0</version>")
                .doesNotContain("<version>4.2.0-SNAPSHOT");
        assertThat(quickStart)
                .contains("<version>4.1.0</version>")
                .doesNotContain("<version>4.2.0-SNAPSHOT");
        assertThat(releaseDocs)
                .contains("The published and current `4.x` lines require Java 21")
                .contains("### V20 default Spring Boot 4 reactor\n\n"
                        + "The default reactor now declares `3.7.0-SNAPSHOT`")
                .contains("### V27 major release evidence")
                .contains("reactor was cut as the `4.0.0` release candidate")
                .contains("### Post-`4.0.0` release lane")
                .contains("### Post-`4.1.0` development lane")
                .contains("strictly against published `4.1.0`")
                .contains("report-only `major-api-report` profile is additional classification")
                .contains("Strict mode enables both japicmp binary- and source-incompatibility failures")
                .contains("mvn -s .mvn/maven-central-settings.xml verify")
                .contains("immutable Boot 3.5 maintenance reconstruction point remains `v2.14.1`");
        assertThat(majorMigration)
                .startsWith("# Starter 3.x to 4.x Resilience Migration")
                .contains("`resilience.enabled: true` with no instance properties")
                .contains("`retry: default`")
                .contains("`retry-methods` only")
                .contains("No operator is selected")
                .contains("clean checkout of tag `v4.0.0`")
                .contains("-Dapi.compatibility.baseline.version=3.6.0")
                .contains("-Papi-compatibility -DskipTests verify")
                .contains("-Papi-compatibility,major-api-report -DskipTests verify")
                .contains("strict japicmp failure remains an unresolved release blocker")
                .contains("Latest published and API baseline: `4.1.0`")
                .contains("Released major: `4.0.0` from tag `v4.0.0`")
                .contains("Development continues on `4.2.0-SNAPSHOT`; no `4.2.0` release scope is selected.");
        assertThat(readme)
                .contains("[Starter 3.x to 4.x Resilience Migration](docs/31-3x-to-4x-resilience-migration.md)");
        assertThat(ciWorkflow)
                .contains("api-root-4.1.0")
                .contains("api-starter-4.1.0")
                .contains("- name: Compare starter API to 4.1.0 from a separate repository\n        if: always()")
                .contains("-Papi-compatibility -DskipTests verify")
                .doesNotContain("api-major-report-3.6.0");
        assertThat(benchmarkDocs)
                .contains("-Dbenchmark.starter.version=4.1.0")
                .contains("-Dbenchmark.commit=4.1.0")
                .contains("test ! -e target/published-baseline-repositories/benchmark-4.1.0 && \\\n"
                        + "mvn -s .mvn/maven-central-settings.xml")
                .contains("-Dmaven.repo.local=target/published-baseline-repositories/benchmark-4.1.0")
                .contains("published-starter-4.1.0/release-jmh.md")
                .contains("published-starter-4.1.0/release-jmh.json")
                .doesNotContain("published-starter-2.14.1/release-jmh.json");
        assertThat(manifest.path("publishedBaselineArtifacts"))
                .allSatisfy(artifact -> assertThat(artifact.path("resolutionCommand").asText())
                        .isEqualTo("scripts/verify-published-release-artifacts.sh 4.1.0"));
    }

    @Test
    void boot4AssembledConsumerFixtureStaysVersionAlignedAndDocumented() throws IOException {
        Path root = projectRoot();
        String fixturePom = Files.readString(root.resolve(".github/boot4-consumer/pom.xml"));
        String workflow = Files.readString(root.resolve(".github/workflows/ci.yml"));
        String currentConsumerScript = Files.readString(root.resolve("scripts/verify-current-consumer.sh"));
        String publishedConsumerScript = Files.readString(root.resolve("scripts/verify-published-consumer.sh"));
        String testHelperDocs = Files.readString(root.resolve("docs/14-test-helpers.md"));
        String fixtureTest = Files.readString(root.resolve(
                ".github/boot4-consumer/src/test/java/io/github/huynhngochuyhoang/httpstarter/boot4consumer/Boot4ConsumerApplicationTest.java"));
        String releaseDocs = Files.readString(root.resolve("docs/20-native-release-compatibility.md"));

        assertThat(fixturePom)
                .contains("<reactive-http-client.version>4.2.0-SNAPSHOT</reactive-http-client.version>")
                .contains("<artifactId>reactive-http-client-starter</artifactId>")
                .contains("<artifactId>reactive-http-client-test</artifactId>")
                .contains("<artifactId>reactive-http-client-otel</artifactId>")
                .contains("<artifactId>spring-boot-webclient</artifactId>")
                .contains("<artifactId>spring-boot-jackson</artifactId>")
                .contains("<groupId>tools.jackson.core</groupId>")
                .contains("<artifactId>spring-boot-starter-actuator</artifactId>");
        assertThat(workflow)
                .contains("boot4-consumer:")
                .contains("scripts/verify-current-consumer.sh")
                .contains("target/release-evidence/current-consumer/");
        assertThat(currentConsumerScript)
                .contains("target/current-reactor-repositories/consumer-$PROJECT_VERSION")
                .contains("[[ ! -e \"$LOCAL_REPOSITORY\" ]]")
                .contains("-Dtest=MockReactiveHttpClientTest,Boot4MockReactiveHttpClientTest")
                .contains("-f \"$FIXTURE_POM\"")
                .contains("copy_mock_reports()")
                .contains("copy_consumer_reports()")
                .contains("stage=\"mock-tests\"\ncopy_mock_reports")
                .contains("stage=\"consumer-tests\"\ncopy_consumer_reports")
                .contains("trap preserve_reports EXIT")
                .contains("REPORT_START_MARKER=\"$EVIDENCE_DIR/report-start.marker\"")
                .contains("\"$report\" -nt \"$REPORT_START_MARKER\"")
                .contains("exit \"$status\"")
                .contains("dependency:build-classpath")
                .contains("assembled consumer resolved reactor output directories")
                .contains("project-artifact-sha256.txt")
                .contains("stage=\"consumer-effective-pom\"")
                .contains("stage=\"dependency-tree\"")
                .contains("stage=\"classpath\"")
                .contains("stage=\"reactor-leakage-checked\"")
                .contains("stage=\"artifact-$module\"")
                .contains("completedStage=$stage")
                .contains("exitStatus=$status")
                .contains("provenance.properties");
        assertThat(publishedConsumerScript)
                .contains("copy_consumer_reports()")
                .contains("stage=\"consumer-tests\"\ncopy_consumer_reports")
                .contains("trap preserve_evidence EXIT")
                .contains("REPORT_START_MARKER=\"$EVIDENCE_DIR/report-start.marker\"")
                .contains("\"$report\" -nt \"$REPORT_START_MARKER\"")
                .contains("fixtureCommit=")
                .contains("stage=\"dependency-tree\"")
                .contains("stage=\"classpath\"")
                .contains("stage=\"module-effective-pom-$module\"")
                .contains("stage=\"published-provenance\"")
                .contains("stage=\"reactor-leakage-checked\"")
                .contains("stage=\"artifact-classpath-$module\"")
                .contains("completedStage=$stage")
                .contains("exitStatus=$status")
                .contains("published consumer resolved reactor output directories");
        assertThat(fixtureTest)
                .contains("extends SharedOrders<OrderResponse>")
                .contains("@ApiRef(\"configured\")")
                .contains("repeatedHeaders(List.of(\"first\", \"second\"))")
                .contains("follow-redirects=true")
                .contains("Mono<ResponseEntity<Flux<DataBuffer>>> streaming()")
                .contains("ProblemDetailHttpClientException.class")
                .contains("ErrorCategory.TIMEOUT")
                .contains("Boot4HttpClientHealthIndicator.class")
                .contains("ConsumerExchangeLogger implements HttpExchangeLogger")
                .contains("CapturingAuthProvider implements AuthProvider")
                .contains("PropertyNamingStrategies.SNAKE_CASE")
                .contains("TrackingMethodMetadataCache extends MethodMetadataCache")
                .contains("openTelemetryHttpClientObserver");
        assertThat(releaseDocs)
                .contains("### Boot 4 assembled consumer fixture")
                .contains("scripts/verify-current-consumer.sh")
                .contains("real inherited-generic and configured")
                .contains("OAuth2, SigV4 raw-body signing")
                .contains("Protocol negotiation, TLS, compression wire bytes, pool timing")
                .contains("including when either test stage fails")
                .contains("no dual-generation")
                .contains("no dual-generation helper");
        assertThat(testHelperDocs)
                .contains("### Ownership boundary")
                .contains("final resolved request metadata")
                .contains("does not negotiate an HTTP protocol or TLS")
                .contains("methodMetadataCache(...)")
                .contains("List<RecordedMultipartPart>")
                .contains("hasMultipartPartNames")
                .contains("followRedirects=true");
    }

    @Test
    void post3AdoptionGuidanceUsesPublishedCoordinatesAndAuthoritativeCommands() throws IOException {
        Path root = projectRoot();
        String migration = Files.readString(root.resolve("docs/28-spring-boot-4-jackson-migration.md"));
        String release = Files.readString(root.resolve("docs/20-native-release-compatibility.md"));
        String benchmarks = Files.readString(root.resolve("docs/22-benchmarks.md"));

        assertThat(migration)
                .contains("## Diagnose adoption failures")
                .contains("org.springframework.boot.webclient.WebClientCustomizer")
                .contains("org.springframework.boot.web." + "reactive.function.client.WebClientCustomizer")
                .contains("org.springframework.boot.health.contributor")
                .contains("tools.jackson.*")
                .contains("resilience4j-spring-boot4")
                .contains("reactive-http-client-otel")
                .contains("reactive-http-client-test")
                .contains("[Boot 4 assembled consumer fixture](20-native-release-compatibility.md#boot-4-assembled-consumer-fixture)")
                .contains("[Published Boot 4 consumer baseline](20-native-release-compatibility.md#published-boot-4-consumer-baseline)")
                .contains("starter `4.1.0`")
                .contains("current reactor is the `4.2.0-SNAPSHOT` development line")
                .contains("orders-api.example.invalid")
                .contains("identity.example.invalid")
                .doesNotContain("orders.example.test")
                .doesNotContain("identity.example.test")
                .doesNotContain("PROJECT_VERSION=$(mvn");
        assertThat(release)
                .contains("Sections labeled V18, V19, V20, or V27 preserve release-era")
                .contains("[Public API compatibility](#public-api-compatibility)");
        assertThat(benchmarks)
                .contains("The commands in [Commands](#commands) are authoritative")
                .contains("scope sections preserve V12-V20 evidence");
    }

    @Test
    void publishedBoot4ConsumerUsesFreshCentralArtifactsAndSeparateEvidence() throws IOException {
        Path root = projectRoot();
        String script = Files.readString(root.resolve("scripts/verify-published-consumer.sh"));
        String provenanceScript = Files.readString(root.resolve("scripts/verify-published-baseline-provenance.sh"));
        String releaseArtifactsScript = Files.readString(root.resolve("scripts/verify-published-release-artifacts.sh"));
        String provenanceFixtures = Files.readString(root.resolve("scripts/verify-published-baseline-fixtures.sh"));
        String workflow = Files.readString(root.resolve(".github/workflows/published-consumer-smoke.yml"));
        String fixture = Files.readString(root.resolve(
                ".github/boot4-consumer/src/test/java/io/github/huynhngochuyhoang/httpstarter/boot4consumer/Boot4ConsumerApplicationTest.java"));
        String releaseDocs = Files.readString(root.resolve("docs/20-native-release-compatibility.md"));

        assertThat(script)
                .contains("PUBLISHED_VERSION=\"${1:-}\"")
                .contains("target/published-baseline-repositories/consumer-$PUBLISHED_VERSION")
                .contains("[[ ! -e \"$LOCAL_REPOSITORY\" ]]")
                .contains(".mvn/maven-central-settings.xml")
                .contains("help:effective-pom")
                .contains("verify-published-baseline-provenance.sh")
                .contains("dependency:build-classpath")
                .contains("resolved reactor output directories")
                .contains("published-consumer/published-$PUBLISHED_VERSION");
        assertThat(provenanceScript)
                .contains("target/published-baseline-repositories/$LANE-$BASELINE_VERSION")
                .contains("_remote.repositories")
                .contains("maven-central=")
                .contains("sha256sum")
                .contains("missing published POM or remote marker")
                .contains("declared_pom_version")
                .contains("embedded_jar_version")
                .contains("POM declares a project version other than")
                .contains("jar embeds a Maven version other than")
                .contains("--release-artifacts")
                .contains("missing published $classifier jar")
                .contains("candidate or unrelated project version")
                .contains("evidence must remain under target/");
        assertThat(releaseArtifactsScript)
                .contains("PUBLISHED_VERSION=\"${1:-}\"")
                .contains("LANE=\"release-artifacts\"")
                .contains("$LANE-$PUBLISHED_VERSION")
                .contains(".mvn/maven-central-settings.xml")
                .contains("-f \"$ROOT_DIR/.github/boot4-consumer/pom.xml\"")
                .contains("reactive-http-client:$PUBLISHED_VERSION:pom")
                .contains("$module:$PUBLISHED_VERSION:pom")
                .contains("$PUBLISHED_VERSION:jar:sources")
                .contains("$PUBLISHED_VERSION:jar:javadoc")
                .contains("-Dtransitive=false")
                .contains("--release-artifacts");
        assertThat(provenanceFixtures)
                .contains("fixture-local")
                .contains("fixture-central")
                .contains("Central-marked JAR without its module POM unexpectedly passed")
                .contains("Release bundle without a sources jar unexpectedly passed")
                .contains("Release bundle without a Javadoc jar unexpectedly passed")
                .contains("Release bundle with a mismatched module POM version unexpectedly passed")
                .contains("Release bundle with a matching parent but mismatched project version unexpectedly passed")
                .contains("Release bundle with a mismatched binary version unexpectedly passed")
                .contains("3.1.0-SNAPSHOT")
                .contains("conflicting local candidate unexpectedly passed");
        assertThat(workflow)
                .contains("workflow_dispatch:")
                .contains("latest.published.version")
                .contains("scripts/verify-published-release-artifacts.sh \"$PUBLISHED_VERSION\"")
                .contains("scripts/verify-published-consumer.sh \"$PUBLISHED_VERSION\"")
                .contains("published-consumer-${{ env.PUBLISHED_VERSION }}")
                .contains("published-consumer/published-${{ env.PUBLISHED_VERSION }}")
                .contains("published-baselines/release-artifacts-${{ env.PUBLISHED_VERSION }}")
                .doesNotContain("install current");
        assertThat(fixture)
                .contains("Mono<OrderResponse> direct()")
                .contains("extends SharedOrders<OrderResponse>")
                .contains("@ApiRef(\"configured\")")
                .contains("ProblemDetailHttpClientException.class")
                .contains("ReactiveHttpClientDiagnosticsEndpoint.class")
                .contains("Boot4HttpClientHealthIndicator.class")
                .contains("MockReactiveHttpClient.forClient")
                .contains("RecordedExchangeAssertions.assertThat")
                .contains("propagatedTraceparent.get()).isEqualTo(TRACEPARENT)");
        assertThat(releaseDocs)
                .contains("### Published Boot 4 consumer baseline")
                .contains("scripts/verify-published-release-artifacts.sh 4.1.0")
                .contains("scripts/verify-published-consumer.sh 4.1.0")
                .contains("published parent")
                .contains("source and Javadoc jars")
                .contains("target/release-evidence/published-consumer/published-4.1.0/")
                .contains("target/release-evidence/published-baselines/release-artifacts-4.1.0/")
                .doesNotContain("scripts/verify-published-consumer.sh 3.0.0")
                .contains("current-reactor lane");
    }

    @Test
    void boot4BenchmarkBaselineStaysSameStackAndSmokeOnly() throws Exception {
        Path root = projectRoot();
        String benchmarkPom = Files.readString(root.resolve("reactive-http-client-benchmarks/pom.xml"));
        String benchmarkDocs = Files.readString(root.resolve("docs/22-benchmarks.md"));
        String currentBaselineProfile = benchmarkPom.substring(
                benchmarkPom.indexOf("<id>benchmark-published-baseline</id>"),
                benchmarkPom.indexOf("<id>benchmark-published-baseline-v28-source-exclusion</id>"));
        int currentBaselineCommandStart = benchmarkDocs.indexOf(
                "target/published-baseline-repositories/benchmark-4.1.0 &&");
        String currentBaselineCommand = benchmarkDocs.substring(currentBaselineCommandStart,
                benchmarkDocs.indexOf("scripts/verify-published-baseline-provenance.sh benchmark 4.1.0",
                        currentBaselineCommandStart));

        String codecFactory = Files.readString(root.resolve(
                "reactive-http-client-benchmarks/src/main/java/io/github/huynhngochuyhoang/httpstarter/benchmarks/BenchmarkJsonCodecFactory.java"));
        assertThat(benchmarkPom)
                .contains("Spring Boot 4 release baseline")
                .contains("benchmark.netty.artifact")
                .contains("benchmark.jackson.artifact")
                .contains("benchmark.micrometer.artifact")
                .contains("benchmark.opentelemetry.artifact")
                .contains("META-INF/*.SF")
                .contains("<id>benchmark-published-baseline-v28-source-exclusion</id>")
                .doesNotContain("<id>boot4-spike</id>");
        assertThat(currentBaselineProfile)
                .doesNotContain("V28SemanticReadCachePerformanceBenchmark.java",
                        "V28SemanticReadCachePerformanceBenchmarkTest.java");
        assertThat(currentBaselineCommand)
                .contains("-Pbenchmarks,benchmark-release,benchmark-published-baseline")
                .doesNotContain("benchmark-published-baseline-v28-source-exclusion");
        assertThat(codecFactory)
                .contains("ReactiveHttpClientJsonCodec")
                .contains("tools.jackson.databind.ObjectMapper")
                .doesNotContain("Jackson3ReactiveHttpClientJsonCodec");
        assertThat(benchmarkDocs)
                .contains("### Spring Boot 4 release baseline")
                .contains("-Pbenchmarks,benchmark-smoke")
                .contains("-Dbenchmark.commit=$(git rev-parse --short HEAD)")
                .contains("benchmark-published-baseline-v28-source-exclusion")
                .doesNotContain("-Dbenchmark.commit=$(git rev-parse --short HEAD)-dirty")
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
        String deltaGuard = Files.readString(root.resolve("scripts/verify-major-api-delta.sh"));
        String v27Report = Files.readString(root.resolve("docs/api-report-3.6.0-to-4.0.0.md"));
        String v27Delta = Files.readString(root.resolve("config/api-delta-3.6.0-to-4.0.0.txt"));
        String v27DeltaGuard = Files.readString(root.resolve("scripts/verify-v27-major-api-delta.sh"));
        String fixtureGuard = Files.readString(root.resolve("scripts/verify-api-compatibility-fixtures.sh"));

        assertThat(pomXml)
                .contains("<id>major-api-report</id>")
                .contains("<api.compatibility.break-on-binary-incompatible>true</api.compatibility.break-on-binary-incompatible>")
                .contains("<api.compatibility.break-on-source-incompatible>true</api.compatibility.break-on-source-incompatible>")
                .contains("<api.compatibility.ignore-missing-classes>false</api.compatibility.ignore-missing-classes>")
                .contains("<api.compatibility.break-on-binary-incompatible>false</api.compatibility.break-on-binary-incompatible>")
                .contains("<api.compatibility.break-on-source-incompatible>false</api.compatibility.break-on-source-incompatible>")
                .contains("<breakBuildOnSourceIncompatibleModifications>${api.compatibility.break-on-source-incompatible}</breakBuildOnSourceIncompatibleModifications>")
                .contains("<api.compatibility.ignore-missing-classes>true</api.compatibility.ignore-missing-classes>");
        assertThat(workflow)
                .contains("-Dmaven.repo.local=target/published-baseline-repositories/api-root-4.1.0")
                .contains("-Papi-compatibility -DskipTests verify")
                .contains("bash scripts/verify-published-baseline-fixtures.sh")
                .doesNotContain("api-major-report-3.6.0", "bash scripts/verify-v27-major-api-delta.sh");
        assertThat(v27Report)
                .contains("no binary- or source-incompatible rows")
                .contains("config/api-delta-3.6.0-to-4.0.0.txt")
                .contains("scripts/verify-v27-major-api-delta.sh")
                .contains("-Dmaven.repo.local=target/published-baseline-repositories/api-major-report-3.6.0")
                .contains("-Dmaven.repo.local=target/published-baseline-repositories/api-starter-report-3.6.0");
        assertThat(v27Delta)
                .contains("reviewed 3.6.0 -> 4.0.0 incompatible japicmp delta is empty");
        assertThat(v27DeltaGuard)
                .contains("PROJECT_VERSION\" == \"4.0.0-SNAPSHOT")
                .contains("BASELINE_VERSION\" == \"3.6.0")
                .contains("normalize_report \"$module\" \"$report\"")
                .contains("api-delta-3.6.0-to-4.0.0.txt")
                .contains("module-$BASELINE_VERSION.jar")
                .contains("module-$BASELINE_VERSION.pom");
        assertThat(fixtureGuard)
                .contains("compile_fixture source-breaking")
                .contains("Expected checked-exception fixture to fail source compatibility check")
                .contains("--normalize-report fixture")
                .contains("+++! NEW EXCEPTION")
                .contains("***! MODIFIED INTERFACE")
                .contains("---! REMOVED METHOD");
        assertThat(guide)
                .contains("<version>3.5.16</version>")
                .contains("<reactive-http-client.version>2.14.1</reactive-http-client.version>")
                .contains("[2.14.1 to 3.0.0 API Report](api-report-2.14.1-to-3.0.0.md)")
                .contains("<version>4.0.0</version>")
                .contains("<reactive-http-client.version>4.1.0</reactive-http-client.version>")
                .contains("org.springframework.boot.webclient.WebClientCustomizer")
                .contains("org.springframework.boot.health.contributor")
                .contains("tools.jackson.databind.ObjectMapper")
                .contains("MockReactiveHttpClient.Builder.jsonCodec")
                .contains("No reactive.http property was renamed for Boot 4")
                .contains("Before, Boot 3.5 and starter 2.x")
                .contains("After, Boot 4 and starter 4.x; Retry must be selected explicitly")
                .contains("retry: default")
                .contains("[Starter 3.x to 4.x Resilience Migration](31-3x-to-4x-resilience-migration.md)")
                .doesNotContain("Boot 4 / 3.x", "starter 3.x", "`3.x` artifact")
                .contains("GraalVM Java 25")
                .contains("## Choose the release lane")
                .contains("must remain on Boot 3.5")
                .contains("## Before and after application code")
                .contains("org.springframework.boot.webclient.WebClientCustomizer")
                .contains("include: health,rhttpclients")
                .contains("show-details: when-authorized")
                .contains(".withExchangeLogger(logger)")
                .contains("## Verify the migration")
                .contains("[Boot 4 assembled consumer fixture](20-native-release-compatibility.md#boot-4-assembled-consumer-fixture)")
                .contains("[Published Boot 4 consumer baseline](20-native-release-compatibility.md#published-boot-4-consumer-baseline)")
                .contains("The latter is the adoption check for starter `4.1.0`")
                .contains("requires no\nconfiguration-metadata entry or reflection hint");
        assertThat(report)
                .contains("published 2.14.1", "Frozen baseline surface")
                .contains("Required by Boot 4")
                .contains("Jackson 3 codec boundary")
                .contains("The generated report contains exactly these reviewed incompatible members")
                .contains("Accidental or unrelated breaks")
                .contains("None. There are no changes")
                .contains("HttpClientHealthIndicator")
                .contains("Boot4HttpClientHealthIndicator")
                .contains("-Papi-compatibility,major-api-report")
                .doesNotContain("boot4-spike");
        assertThat(deltaGuard)
                .contains("PROJECT_VERSION\" == \"3.0.0")
                .contains("BASELINE_VERSION\" == \"2.14.1")
                .contains("API_COMPATIBILITY_MAVEN_SETTINGS")
                .contains("mvn -q -s \"$MAVEN_SETTINGS\"")
                .contains("API_COMPATIBILITY_BASELINE_REPOSITORY")
                .contains("(---|\\\\+\\\\+\\\\+|\\\\*\\\\*\\\\*)[!*]")
                .contains("installed locally rather than resolved from a release repository")
                .contains("Jackson2ReactiveHttpClientJsonCodec")
                .contains("ProblemDetailErrorResponseMapper(com.fasterxml.jackson.databind.ObjectMapper)")
                .contains("MockReactiveHttpClient$Builder<T> objectMapper")
                .contains("HttpClientHealthIndicator.class")
                .contains("Boot4HttpClientHealthIndicator.class")
                .contains("Unreviewed cross-major API change detected");
    }

    @Test
    void boot4PublicGuidesPointToMigrationAndCurrentOperationsContracts() throws IOException {
        Path root = projectRoot();
        for (String guide : List.of(
                "README.md",
                "docs/01-quick-start.md",
                "docs/02-annotations.md",
                "docs/06-auth-providers.md",
                "docs/07-resilience4j.md",
                "docs/08-observability.md",
                "docs/20-native-release-compatibility.md",
                "docs/22-benchmarks.md",
                "docs/26-support-bundles.md")) {
            assertThat(Files.readString(root.resolve(guide)))
                    .as(guide)
                    .contains("28-spring-boot-4-jackson-migration.md");
        }

        assertThat(Files.readString(root.resolve("docs/08-observability.md")))
                .contains("Boot4HttpClientHealthIndicator")
                .contains("include: health,rhttpclients")
                .contains("show-details: when-authorized")
                .doesNotContain("auto-registers `HttpClientHealthIndicator`");
        assertThat(Files.readString(root.resolve("docs/07-resilience4j.md")))
                .contains("starter `4.x`\nBoot 4 reactor uses `2.4.0`")
                .doesNotContain("The V19 Boot 4 build");
    }

    @Test
    void observabilityMeterDocsMatchPrometheusExportContract() throws IOException {
        String observabilityDocs = Files.readString(projectRoot().resolve("docs/08-observability.md"));

        assertThat(observabilityDocs)
                .contains("| Always recorded |")
                .contains("| Conditionally recorded |")
                .contains("| Opt-in |")
                .contains("They can remain active when\n"
                        + "`reactive.http.observability.enabled=false`")
                .contains("reactive_http_client_requests_seconds_count")
                .contains("time-window maximum, not a lifetime maximum")
                .contains("`0` means resilience rejected or admission was cancelled")
                .contains("`1` means one subscription attempt, regardless of success")
                .contains("Values greater than `1` mean Resilience4j retry resubscribed")
                .contains("does not enable `publishPercentiles(0.95, 0.99)`")
                .contains("### Request rate (logical calls per second)")
                .contains("### Error ratio (dimensionless)")
                .contains("0 * sum by (client_name, api_name)")
                .contains("so healthy groups remain\nvisible")
                .contains("The attempts summary cannot produce this count truthfully")
                .contains("error_category=\"RESILIENCE_ERROR\"")
                .contains("RateLimiter and Bulkhead meters expose current-state gauges")
                .contains("### p95/p99 logical-call latency (seconds; histogram required)")
                .contains("reactive_http_client_requests_latency_seconds_bucket")
                .contains("when a requested quantile falls into it, `histogram_quantile` returns\n"
                        + "the highest finite boundary rather than the actual tail")
                .contains("### Average subscription attempts (attempts per logical call)")
                .contains("rolling arithmetic mean, not a percentile or retry-event rate")
                .contains("### Pool pressure (gauge counts, not utilization percentages)")
                .contains("reactive_http_client_connection_pool_pending_connections")
                .contains("reactive_http_client_connection_pool_pending_streams")
                .contains("| Starter logical-call Micrometer |")
                .contains("| Resilience4j operator meters |")
                .contains("RateLimiter/Bulkhead current-state gauges")
                .contains("RateLimiter/Bulkhead rejection history; use the starter "
                        + "`RESILIENCE_ERROR` timer instead")
                .contains("| Reactor Netty transport meters |")
                .contains("| OpenTelemetry companion |")
                .doesNotContain("records four meters per exchange")
                .doesNotContain("`1` = succeeded on first try")
                .doesNotContain("A p95 above `1`")
                .doesNotContain("RateLimiter, or Bulkhead rejection\n"
                        + "counters")
                .doesNotContain("| Admission rejection, retry execution/exhaustion, operator state |");
    }

    @Test
    void operationsDocsUseCanonicalObservabilityRecipes() throws IOException {
        Path root = projectRoot();
        String errorDocs = Files.readString(root.resolve("docs/03-error-handling.md"));
        String resilienceDocs = Files.readString(root.resolve("docs/07-resilience4j.md"));
        String productionDocs = Files.readString(root.resolve("docs/16-production-checklist.md"));
        String cardinalityDocs = Files.readString(root.resolve("docs/18-conflict-cardinality-guardrails.md"));
        String contextDocs = Files.readString(root.resolve("docs/21-diagnostic-contexts.md"));
        String supportDocs = Files.readString(root.resolve("docs/26-support-bundles.md"));
        String operationsDocs = Files.readString(root.resolve("docs/30-operations-troubleshooting.md"));

        assertThat(errorDocs).contains(
                "[unit-safe error-ratio recipe](08-observability.md#error-ratio-dimensionless)");
        assertThat(resilienceDocs)
                .contains("attempts summary has no default percentiles")
                .contains("[dashboard recipes](08-observability.md#dashboard-recipes)");
        assertThat(productionDocs)
                .contains("[unit-safe dashboard recipes](08-observability.md#dashboard-recipes)")
                .contains("Do not use attempts `_max` as p95/p99")
                .contains("error_category=\"RESILIENCE_ERROR\"")
                .contains("Reserve Resilience4j counters for CircuitBreaker call history and Retry")
                .contains("RateLimiter and Bulkhead metrics are current-state gauges")
                .doesNotContain("attempts count/sum; use the relevant Resilience4j operator counters");
        assertThat(cardinalityDocs)
                .contains("[telemetry ownership table and dashboard recipes]"
                        + "(08-observability.md#dashboard-recipes)");
        assertThat(contextDocs)
                .contains("timer count deltas")
                .contains("Duration\nsums, time-window maxima, percentiles, and histogram buckets are not health");
        assertThat(supportDocs)
                .contains("Health does not consume duration sums, time-window\nmaxima, percentiles")
                .contains("[unit-safe dashboard recipes](08-observability.md#dashboard-recipes)");
        assertThat(operationsDocs)
                .contains("[unit-safe dashboard recipes](08-observability.md#dashboard-recipes)")
                .contains("do not infer\n   zero-attempt rejection from the attempts summary");
    }

    @Test
    void healthIndicatorDocsMatchCountAndDetailContract() throws IOException {
        Path root = projectRoot();
        String observabilityDocs = Files.readString(root.resolve("docs/08-observability.md"));
        String supportDocs = Files.readString(root.resolve("docs/26-support-bundles.md"));

        assertThat(observabilityDocs)
                .contains("Health reads only the configured main timer's count")
                .contains("histogram buckets do not affect health status")
                .contains("`poolAcquireFailureCount`")
                .contains("Registry resets and meter\nremoval/recreation start a new count baseline")
                .contains("At most 256\nclients with names up to 512 characters")
                .contains("reactiveHttpClientHealthIndicator");
        assertThat(supportDocs)
                .contains("[health fixture](fixtures/support-bundle-health.json)")
                .contains("`sampleCount`, `errorCount`, `poolAcquireFailureCount`");
    }

    @Test
    void openTelemetryDocsMatchLogicalCallAndObserverBodyContracts() throws IOException {
        Path root = projectRoot();
        String observabilityDocs = Files.readString(root.resolve("docs/08-observability.md"));
        String contextDocs = Files.readString(root.resolve("docs/21-diagnostic-contexts.md"));
        String cardinalityDocs = Files.readString(root.resolve("docs/18-conflict-cardinality-guardrails.md"));

        assertThat(observabilityDocs)
                .contains("one terminal `CLIENT` span per logical\nclient call")
                .contains("transport dispatches remain inside\nthat span")
                .contains("current, finite span with attempt `0`")
                .contains("the span describes response-envelope\ncompletion")
                .contains("They do not create\nOpenTelemetry span events")
                .contains("Built-in Micrometer and OpenTelemetry observers ignore both body fields")
                .contains("omits request and response bodies")
                .doesNotContain("include body in span events (PII risk)")
                .doesNotContain("records each outbound exchange as a span");
        assertThat(contextDocs)
                .contains("gate body fields on the\nsingle terminal `HttpClientObserverEvent`")
                .contains("do not add OpenTelemetry span\nevents");
        assertThat(cardinalityDocs)
                .contains("Request and decoded success bodies on custom observer events")
                .doesNotContain("Request and response bodies in spans");
    }

    @Test
    void requestResponseSizeDocsMatchWireContract() throws IOException {
        String observabilityDocs = Files.readString(projectRoot().resolve("docs/08-observability.md"));

        assertThat(observabilityDocs)
                .contains("String measurement uses the charset declared by the final outbound")
                .contains("after auth and client-customizer filters")
                .contains("charset-free value falls back to UTF-8")
                .contains("Other `CharSequence`, POJO")
                .contains("Observability never serializes, subscribes, consumes, reopens, or")
                .contains("An advertised `0` is recorded as zero")
                .contains("drained bodiless responses, `ResponseEntity`")
                .contains("malformed framing with no trustworthy")
                .contains("`String` uses the final outbound declared charset")
                .contains("after auth/client-customizer filters; opaque bodies are absent")
                .doesNotContain("POJO bodies are not measured to avoid double-serialization cost");
    }

    @Test
    void resilienceAdmissionDocsMatchCompositionContract() throws IOException {
        Path root = projectRoot();
        String resilienceDocs = Files.readString(root.resolve("docs/07-resilience4j.md"));
        String observabilityDocs = Files.readString(root.resolve("docs/08-observability.md"));

        assertThat(resilienceDocs)
                .contains("logical-call-timeout -> bulkhead -> circuit-breaker -> rate-limiter -> retry -> request-attempt")
                .contains("| Open circuit | `0` | `http.status_code=NONE`, `outcome=UNKNOWN`")
                .contains("| Exhausted rate limiter | `0` | `http.status_code=NONE`, `outcome=UNKNOWN`")
                .contains("| Saturated zero-wait bulkhead | `0` | `http.status_code=NONE`, `outcome=UNKNOWN`")
                .contains("included once in\nthe logical-call duration")
                .contains("only one terminal starter timer sample")
                .contains("Resilience4j retry | Yes")
                .contains("One-time `401` auth refresh | No")
                .contains("Automatic `307`/`308` redirect | No");
        assertThat(observabilityDocs)
                .contains("Logical subscription attempts (`0` before request subscription")
                .contains("This is not a downstream dispatch count")
                .doesNotContain("| `rhttp.attempt.count` | Total attempts");
    }

    @Test
    void documentedPublicSurfaceMapMatchesApiCompatibilityIncludes() throws IOException {
        Path root = projectRoot();
        String pomXml = Files.readString(root.resolve("pom.xml"));
        String releaseDocs = Files.readString(root.resolve("docs/20-native-release-compatibility.md"));
        String compatibilityRule = Files.readString(
                root.resolve("scripts/japicmp-annotation-default-compatibility.groovy"));
        String includeWorkflow = markdownSection(releaseDocs, "### Compatibility include workflow",
                "### Configuration metadata and native-hint ownership");
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
                .contains("source-only checked\nexception addition")
                .contains("constructor, nested fluent method,\nor public enum constant fail")
                .contains("including `MockReactiveHttpClient`, `RecordedExchange`,\n  `RecordedExchangeAssertions`")
                .contains("`ErrorCategoryAssertions`, `MockHttpServer`, and `MockHttpServerExtension`")
                .contains("`OpenTelemetryHttpClientObserver`, `OpenTelemetryContextWebFilter`")
                .contains("`OpenTelemetryContextExchangeFilter`, and `OpenTelemetryHttpClientAutoConfiguration`")
                .contains("When documenting a new public helper")
                .contains("Prefer the narrowest include\npattern")
                .contains("Keep implementation\ninternals excluded");
        assertThat(releaseDocs)
                .contains("`CacheResponse` including `semanticRead`, `CacheDisabled`, and `CacheKey`")
                .contains("`CacheConfig`, `CachePolicyConfig`, `CacheCustomizationSafety`")
                .contains("cache terminal callback, and `HttpClientCacheOutcome`")
                .contains("deterministic cache clock/policy/outcome/eviction controls")
                .contains("V27 adds no incompatible Java API row relative to published `3.6.0`")
                .contains("V28 adds `CacheResponse.semanticRead()`")
                .contains("`cacheSemanticRead` getter/setter")
                .contains("`MockResponseCacheSupport` is a\npublic, `@hidden` cross-package bridge")
                .contains("No starter public signature exposes Caffeine")
                .contains("`Builder.cachePolicy`, and `Builder.withDeterministicCacheTime`")
                .contains("`METHOD_ABSTRACT_ADDED_TO_CLASS`")
                .contains("only `CacheResponse.semanticRead()`")
                .contains("has an `AnnotationDefault` attribute")
                .contains("Other abstract-method additions remain strict");
        assertThat(pomXml)
                .contains("scripts/japicmp-annotation-default-compatibility.groovy");
        assertThat(compatibilityRule)
                .contains("CacheResponse")
                .contains("semanticRead")
                .contains("METHOD_ABSTRACT_ADDED_TO_CLASS")
                .contains("AnnotationDefaultAttribute.tag")
                .contains("change.binaryCompatible = true")
                .contains("change.sourceCompatible = true");
        assertThat(includeWorkflow)
                .contains("authoritative root and module-scoped commands")
                .contains("[Public API compatibility](#public-api-compatibility)")
                .containsSubsequence(
                        "bash scripts/verify-api-compatibility-fixtures.sh",
                        "-Dtest=DocumentationReleaseArtifactTest test")
                .doesNotContain("target/published-baseline-repositories/api-root-3.6.0")
                .doesNotContain("target/published-baseline-repositories/api-starter-3.6.0");
    }

    @Test
    void v27SupportedMatrixIsResolvedAndReproducible() throws IOException {
        Path root = projectRoot();
        String releaseDocs = Files.readString(root.resolve("docs/20-native-release-compatibility.md"));
        String consumerPom = Files.readString(root.resolve(".github/boot4-consumer/pom.xml"));
        String verifier = Files.readString(root.resolve("scripts/verify-supported-matrix.sh"));
        String workflow = Files.readString(root.resolve(".github/workflows/supported-matrix.yml"));

        assertThat(releaseDocs)
                .contains("### V23 resolved supported matrix")
                .contains("### V24 supported-matrix revalidation")
                .contains("### V27 supported-matrix and cache dependency revalidation")
                .contains("| Spring Framework / WebFlux | `7.0.1` | `7.0.8` |")
                .contains("| Reactor Netty HTTP | `1.3.0` | `1.3.6` |")
                .contains("| Jackson Databind | `3.0.2` | `3.1.4` |")
                .contains("The minimum does not move")
                .contains("Boot `4.0.0` manages Caffeine `3.2.3`")
                .contains("Boot\n`4.1.0` manages `3.2.4`")
                .contains("cache-disabled clients do not gain a\ntransitive cache engine")
                .contains("target/release-evidence/v27-priority14/matrix/");
        assertThat(consumerPom)
                .contains("<spring-boot.version>4.0.0</spring-boot.version>")
                .contains("<artifactId>spring-boot-dependencies</artifactId>")
                .contains("<version>${spring-boot.version}</version>");
        assertThat(verifier)
                .contains("ROWS=(4.0.0 4.1.0)")
                .contains("Java 21 is required for the supported minimum")
                .contains("clean install")
                .contains(".github/boot4-consumer/pom.xml")
                .contains("-Dspring-boot.version=$boot_version\" -Papi-compatibility")
                .contains("-Papi-compatibility -DskipTests verify")
                .contains("verify-published-baseline-provenance.sh")
                .contains("trap preserve_evidence EXIT")
                .contains("starterContextLoadsWhenMicrometerMissing")
                .contains("resilience4jBindersSkippedWhenRegistryBeansMissing")
                .contains("diagnosticsEndpointSkippedWhenActuatorEndpointClassesMissing")
                .contains("autoConfigurationBacksOffWithoutOpenTelemetryApi")
                .contains("userSuppliedOauth2AuthProviderFactoryOverridesBuiltInFactory")
                .contains("optionalImplementationIsRequiredOnlyForSelectedPolicies")
                .contains("caffeine=$(resolve_version")
                .contains("target/release-evidence/v27-priority14/matrix")
                .contains("optional-integration-contracts.properties")
                .contains("Partial matrix evidence preserved under");
        int rowLoop = verifier.indexOf("for boot_version in");
        int rowApiCompatibility = verifier.indexOf(
                "mvn -B -ntp -s \"$SETTINGS\" \"-Dmaven.repo.local=$api_repository\"");
        int rowLoopEnd = verifier.lastIndexOf("\ndone\n");
        assertThat(rowLoop).isGreaterThanOrEqualTo(0).isLessThan(rowApiCompatibility);
        assertThat(rowLoopEnd).isGreaterThan(rowApiCompatibility);
        assertThat(workflow)
                .contains("name: Supported Dependency Matrix")
                .contains("scripts/verify-supported-matrix.sh")
                .contains("target/release-evidence/v27-priority14/matrix/");
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
                .contains("either `@Bean` or component scanning, not\nboth")
                .contains("fields.put(\"errorType\"")
                .contains("fields.put(\"errorCategory\"")
                .contains("fields.put(\"failureStage\"")
                .doesNotContain("fields.put(\"error\", context.error().getMessage())");
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
        assertThat(projectVersion(root.resolve("pom.xml"))).isEqualTo("4.2.0-SNAPSHOT");
        assertThat(pomXml)
                .contains("<spring-boot.version>4.0.0</spring-boot.version>")
                .contains("<api.compatibility.baseline.version>4.1.0</api.compatibility.baseline.version>");
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
                .contains("<version>4.2.0-SNAPSHOT</version>")
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
                .contains("Refusing to publish SNAPSHOT version")
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
        String clientAotProcessor = Files.readString(root.resolve(
                "reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/ReactiveHttpClientBeanFactoryInitializationAotProcessor.java"));
        String nativeClient = Files.readString(root.resolve(
                ".github/native-smoke/src/main/java/io/github/huynhngochuyhoang/httpstarter/nativesmoke/NativeSmokeClient.java"));
        String nativeApplication = Files.readString(root.resolve(
                ".github/native-smoke/src/main/java/io/github/huynhngochuyhoang/httpstarter/nativesmoke/NativeSmokeApplication.java"));
        String nativeProperties = Files.readString(root.resolve(
                ".github/native-smoke/src/main/resources/application.properties"));
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
                "ReactiveHttpClientProperties.OAuth2TokenServiceConfig.class",
                "POM_PROPERTIES_RESOURCE");
        assertThat(runtimeHints)
                .contains("registerConstructor(constructor, ExecutableMode.INVOKE)",
                        "registerMethod(method, ExecutableMode.INVOKE)")
                .contains("registerType(type, typeHint -> {})")
                .doesNotContain("MemberCategory", "ExecutableMode.INTROSPECT");
        assertThat(clientAotProcessor)
                .contains("clientInterface.getMethods()",
                        "registerMethod(method, ExecutableMode.INVOKE)")
                .contains("typeHint.withMethod(method.getName()",
                        "TypeReference.listOf(method.getParameterTypes())")
                .doesNotContain("MemberCategory", "ExecutableMode.INTROSPECT");
        assertThat(nativeClient).contains(
                "extends NativeSmokeOperations<NativeOrderResponse>",
                "@ApiRef(\"native-problem\")",
                "@GET(\"/api/compressed-order\")");
        assertThat(nativeProperties).contains(
                "apis.native-problem.method",
                "compression-enabled");
        assertThat(nativeApplication).contains(
                "Content-Encoding\", \"gzip",
                "compression negotiation header did not reach loopback server",
                "ProblemDetailRemoteServiceException",
                "logicalCallTimeoutMs",
                "reactiveHttpClientDiagnosticsEndpoint",
                "reactiveHttpClientHealthIndicator",
                "reactive.http.client.requests");
        assertThat(nativePom).contains(
                "<reactive-http-client.version>4.2.0-SNAPSHOT</reactive-http-client.version>",
                "-J-Xmx6g",
                "-H:NumberOfThreads=4",
                "-H:+SharedArenaSupport");
        assertThat(nativeWorkflow).contains(
                "set -o pipefail",
                "test -z \"$(git status --porcelain)\"",
                "sourceState=clean",
                "-U -s .mvn/maven-central-settings.xml",
                "sha256sum .github/native-smoke/target/reactive-http-client-native-smoke",
                "executableStatus=passed",
                "target/release-evidence/native-smoke/native-provenance.txt",
                "native-smoke-provenance",
                "actions/upload-artifact@v4");
        assertThat(releaseDocs).contains(
                "configured inherited",
                "@ApiRef",
                "transparent JSON response decompression",
                "6 GiB",
                "-Dreactive-http-client.version=4.2.0-SNAPSHOT native:compile",
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
        assertThat(generated.path("releaseState").asText()).isEqualTo("snapshot-development");
        assertThat(generated.path("developmentVersion").asText()).isEqualTo("4.2.0-SNAPSHOT");
        assertThat(generated.path("latestPublishedConsumerVersion").asText()).isEqualTo("4.1.0");
        assertThat(generated.path("plannedFinalVersion").isNull()).isTrue();
        assertThat(generated.path("apiCompatibilityBaselineVersion").asText())
                .isEqualTo(pomProperty(pomXml, "api.compatibility.baseline.version"));
        assertThat(generated.path("apiCompatibilityBaselineMatchesProjectVersion").asBoolean()).isFalse();
        JsonNode readiness = generated.path("readiness");
        assertThat(readiness.path("projectVersion").asText()).isEqualTo(generated.path("projectVersion").asText());
        assertThat(readiness.path("apiCompatibilityBaselineVersion").asText())
                .isEqualTo(generated.path("apiCompatibilityBaselineVersion").asText());
        assertThat(readiness.path("apiCompatibilityBaselineMatchesProjectVersion").asBoolean()).isFalse();
        assertThat(readiness.path("activeRoadmap").asText()).isEqualTo("v29");
        assertThat(readiness.path("releaseLane").asText()).isEqualTo("additive-minor");
        assertThat(readiness.path("releaseCandidate").path("version").asText()).isEqualTo("4.2.0");
        assertThat(readiness.path("releaseCandidate").path("status").asText()).isEqualTo("deferred");
        assertThat(readiness.path("releaseCandidate").path("published").asBoolean()).isFalse();
        assertThat(readiness.path("releaseCandidate").path("scopeStatus").asText()).isEqualTo("selected");
        assertThat(readiness.path("releaseCandidate").path("scope").asText())
                .isEqualTo("optional decoded-response-representation-byte cache admission and eviction");
        assertThat(readiness.path("releaseCandidate").path("weightContractDecision").asText()).isEqualTo("go");
        assertThat(readiness.path("releaseCandidate").path("weightUnit").asText())
                .isEqualTo("decoded-response-representation-bytes");
        assertThat(readiness.path("releaseCandidate").path("decisionDocument").asText())
                .isEqualTo("roadmaps/v29/RETAINED-WEIGHT-DECISION.md");
        assertThat(readiness.path("releaseCandidate").path("migrationReport").isMissingNode()).isTrue();
        assertThat(readiness.path("releaseCandidate").path("pendingWork"))
                .extracting(JsonNode::asText)
                .containsExactly("weighted admission implementation", "API compatibility", "assembled consumers",
                        "benchmarks", "AOT", "native image", "publication");
        assertThat(readiness.path("generatedTestEvidence").path("status").asText()).isEqualTo("pass");
        assertThat(readiness.path("manualReleaseEvidence").path("status").asText()).isEqualTo("pending");
        List<String> pendingReleaseCommands = streamText(readiness.path("manualReleaseEvidence").path("pendingCommands"));
        assertThat(pendingReleaseCommands)
                .anySatisfy(command -> assertThat(command)
                        .contains("api-root-" + pomProperty(pomXml, "api.compatibility.baseline.version"))
                        .contains("verify-published-baseline-provenance.sh"));
        assertThat(pendingReleaseCommands)
                .filteredOn(command -> command.contains("verify-published-release-artifacts.sh"))
                .containsExactly("scripts/verify-published-release-artifacts.sh 4.1.0");
        assertThat(pendingReleaseCommands)
                .contains("scripts/verify-published-consumer.sh 4.1.0");
        assertThat(pendingReleaseCommands)
                .anySatisfy(command -> assertThat(command)
                        .contains("benchmark-release")
                        .contains("benchmark.commit=$(git rev-parse --short HEAD)"));
        assertThat(pendingReleaseCommands)
                .contains(generated.path("benchmarkEvidence").path("publishedStarterCommand").asText());
        assertThat(pendingReleaseCommands)
                .anySatisfy(command -> assertThat(command).contains("native:compile")
                        .contains("reactive-http-client-native-smoke"))
                .anySatisfy(command -> assertThat(command).contains("verify-publishable-artifacts.sh")
                        .contains("verify-generation-packaging.sh"));
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
                .hasSize(4)
                .contains("bash scripts/verify-api-compatibility-fixtures.sh", "bash scripts/verify-published-baseline-fixtures.sh")
                .anySatisfy(command -> assertThat(command).contains("api-root-4.1.0"))
                .anySatisfy(command -> assertThat(command).contains("api-starter-4.1.0"));
        assertThat(readiness.path("manualConsumerEvidence").path("status").asText()).isEqualTo("pending");
        assertThat(streamText(readiness.path("manualConsumerEvidence").path("pendingCommands")))
                .containsExactly("scripts/verify-published-consumer.sh 4.1.0");
        assertThat(readiness.path("manualNativeEvidence").path("status").asText()).isEqualTo("pending");
        assertThat(streamText(readiness.path("manualNativeEvidence").path("pendingCommands")))
                .singleElement()
                .satisfies(command -> assertThat(command)
                        .contains("native:compile", "reactive-http-client-native-smoke"));
        assertThat(readiness.path("manualPublicationEvidence").path("status").asText())
                .isEqualTo("deferred-until-release-cut");
        assertThat(readiness.path("manualPublicationEvidence").path("workflow").asText())
                .isEqualTo(".github/workflows/publish-maven-central.yml");
        assertThat(streamText(readiness.path("manualPublicationEvidence").path("preflightCommands")))
                .singleElement()
                .satisfies(command -> assertThat(command)
                        .contains("verify-publishable-artifacts.sh", "verify-generation-packaging.sh"));
        assertThat(readiness.path("promotedBenchmarkReport").path("path").isNull()).isTrue();
        assertThat(readiness.path("promotedBenchmarkReport").path("status").asText())
                .isEqualTo("deferred-until-release-cut");
        assertThat(readiness.path("configurationReference").path("status").asText()).isEqualTo("current");
        assertThat(readiness.path("markdownLinks").path("status").asText()).isEqualTo("pass");
        assertThat(readiness.path("staleBenchmarkReportLinks").path("status").asText()).isEqualTo("pass");
        assertThat(readiness.path("releaseEvidenceDirectory").asText()).isEqualTo("target/release-evidence/");
        assertThat(readiness.path("targetOnlyEvidence").path("sourceControlled").asBoolean()).isFalse();
        assertThat(readiness.path("targetOnlyEvidence").path("commitGeneratedEvidence").asBoolean()).isFalse();

        JsonNode releasePrepChecklist = generated.path("releasePrepChecklist");
        assertThat(releasePrepChecklist.path("status").asText()).isEqualTo("pending");
        assertThat(releasePrepChecklist.path("releaseState").asText()).isEqualTo("snapshot-development");
        assertThat(releasePrepChecklist.path("latestPublishedConsumerVersion").asText()).isEqualTo("4.1.0");
        assertThat(releasePrepChecklist.path("plannedFinalVersion").isNull()).isTrue();
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
                "major-candidate",
                "published-baseline-artifacts",
                "api-compatibility",
                "published-consumer",
                "native-evidence",
                "publication-readiness",
                "benchmark-evidence",
                "promoted-benchmark-report",
                "generated-docs-and-links",
                "target-only-evidence");
        assertThat(releasePrepItems.get("changelog-section").path("status").asText()).isEqualTo("current");
        String manifestProjectVersion = generated.path("projectVersion").asText();
        // Public snippets always document the latest published consumer version, even for a
        // release candidate that is not yet resolvable from Maven Central.
        String expectedConsumerVersion = generated.path("latestPublishedConsumerVersion").asText();
        String unreleasedCompareVersion = Files.readString(root.resolve("CHANGELOG.md"))
                .contains("## [" + manifestProjectVersion + "] - ")
                ? manifestProjectVersion
                : generated.path("apiCompatibilityBaselineVersion").asText();
        assertThat(releasePrepItems.get("changelog-section").path("expectedUnreleasedCompareLink").asText())
                .contains("v" + unreleasedCompareVersion + "...HEAD");
        assertThat(releasePrepItems.get("version-snippets").path("status").asText()).isEqualTo("current");
        assertThat(releasePrepItems.get("version-snippets").path("expectedVersion").asText())
                .isEqualTo(expectedConsumerVersion);
        assertThat(releasePrepItems.get("major-candidate").path("status").asText()).isEqualTo("deferred");
        assertThat(releasePrepItems.get("major-candidate").path("version").asText()).isEqualTo("4.2.0");
        assertThat(releasePrepItems.get("major-candidate").path("published").asBoolean()).isFalse();
        assertThat(releasePrepItems.get("major-candidate").path("scopeStatus").asText())
                .isEqualTo("selected");
        assertThat(releasePrepItems.get("major-candidate").path("weightContractDecision").asText())
                .isEqualTo("go");
        assertThat(streamText(releasePrepItems.get("published-baseline-artifacts").path("commands")))
                .containsExactly("scripts/verify-published-release-artifacts.sh 4.1.0");
        assertThat(streamText(releasePrepItems.get("api-compatibility").path("commands")))
                .hasSize(4)
                .contains("bash scripts/verify-api-compatibility-fixtures.sh", "bash scripts/verify-published-baseline-fixtures.sh")
                .anySatisfy(command -> assertThat(command).contains("api-root-4.1.0"))
                .anySatisfy(command -> assertThat(command).contains("api-starter-4.1.0"));
        assertThat(streamText(releasePrepItems.get("published-consumer").path("commands")))
                .containsExactly("scripts/verify-published-consumer.sh 4.1.0");
        assertThat(streamText(releasePrepItems.get("native-evidence").path("commands")))
                .singleElement()
                .satisfies(command -> assertThat(command)
                        .contains("native:compile", "reactive-http-client-native-smoke"));
        assertThat(releasePrepItems.get("publication-readiness").path("status").asText())
                .isEqualTo("deferred-until-release-cut");
        assertThat(streamText(releasePrepItems.get("publication-readiness").path("preflightCommands")))
                .singleElement()
                .satisfies(command -> assertThat(command)
                        .contains("verify-publishable-artifacts.sh", "verify-generation-packaging.sh"));
        assertThat(streamText(releasePrepItems.get("benchmark-evidence").path("commands")))
                .contains("mvn -Pbenchmarks,benchmark-smoke -pl reactive-http-client-benchmarks -am verify",
                        generated.path("benchmarkEvidence").path("currentWorkspaceCommand").asText(),
                        generated.path("benchmarkEvidence").path("publishedStarterCommand").asText());
        assertThat(releasePrepItems.get("promoted-benchmark-report").path("path").isNull()).isTrue();
        assertThat(releasePrepItems.get("promoted-benchmark-report").path("status").asText())
                .isEqualTo("deferred-until-release-cut");
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
                .hasSize(12)
                .contains(
                        "mvn test",
                        "scripts/verify-published-consumer.sh 4.1.0",
                        "bash scripts/verify-api-compatibility-fixtures.sh",
                        "bash scripts/verify-published-baseline-fixtures.sh",
                        "git diff --check",
                        "mvn -Pbenchmarks -pl reactive-http-client-benchmarks -am package",
                        "mvn -Pbenchmarks,benchmark-smoke -pl reactive-http-client-benchmarks -am verify",
                        "mvn -Pbenchmarks,benchmark-release -pl reactive-http-client-benchmarks -am verify -Dbenchmark.commit=$(git rev-parse --short HEAD)")
                .anySatisfy(command -> assertThat(command).contains("api-root-4.1.0"))
                .anySatisfy(command -> assertThat(command).contains("api-starter-4.1.0"));
        JsonNode benchmarkEvidence = generated.path("benchmarkEvidence");
        assertThat(benchmarkEvidence.path("manualOrProfileGated").asBoolean()).isTrue();
        assertThat(benchmarkEvidence.path("currentWorkspaceCommand").asText())
                .contains("benchmark-release")
                .contains("-am verify");
        assertThat(benchmarkEvidence.path("publishedStarterCommand").asText())
                .startsWith("test ! -e target/published-baseline-repositories/benchmark-")
                .contains("-Pbenchmarks,benchmark-release,benchmark-published-baseline")
                .contains("-Dmaven.repo.local=target/published-baseline-repositories/benchmark-"
                        + pomProperty(pomXml, "api.compatibility.baseline.version"))
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
        assertThat(benchmarkEvidence.path("promotableReportAvailable").asBoolean()).isFalse();
        assertThat(benchmarkEvidence.path("promotedReport").isNull()).isTrue();
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
                .contains("Promoted report: pending explicit release-cut version")
                .contains("Current candidate command: `" + benchmarkEvidence.path("currentWorkspaceCommand").asText() + "`")
                .contains("Published baseline command: `" + benchmarkEvidence.path("publishedStarterCommand").asText() + "`")
                .contains("Current candidate report: `" + benchmarkEvidence.path("currentCandidateReport").asText() + "`")
                .contains("Published baseline report: `" + benchmarkEvidence.path("publishedBaselineReport").asText() + "`")
                .contains("Scenarios cited: `Get No Body`, `Get Path Query Header`, `Post Json`, `Response Entity`, `Client Error Small Body`, `Server Error Small Body`, `Problem Detail Small Body`")
                .contains("Run the release-cut transition before promoting a report")
                .doesNotContain("benchmark-report-3.1.0-SNAPSHOT.md")
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
        boolean promotableReportAvailable = evidence.path("promotableReportAvailable").asBoolean();
        String promotedReportLine = promotableReportAvailable
                ? "- Promoted report: [Benchmark Report " + manifest.path("plannedFinalVersion").asText()
                        + "](" + evidence.path("promotedReport").asText() + ")\n"
                : "- Promoted report: pending explicit release-cut version\n";
        String note = promotableReportAvailable
                ? "Paste this block only after the promoted report exists for starter `" + projectVersion + "`"
                : "Run the release-cut transition before promoting a report for development version `"
                        + projectVersion + "`";

        return "Benchmark evidence:\n"
                + promotedReportLine
                + "- Current candidate command: `" + evidence.path("currentWorkspaceCommand").asText() + "`\n"
                + "- Published baseline command: `" + evidence.path("publishedStarterCommand").asText() + "`\n"
                + "- Current candidate report: `" + evidence.path("currentCandidateReport").asText() + "`\n"
                + "- Published baseline report: `" + evidence.path("publishedBaselineReport").asText() + "`\n"
                + "- Scenarios cited: " + scenarios + "\n"
                + "- Note: " + note + " and published baseline `" + baselineVersion + "`.\n";
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
        String latestPublishedVersion = pomProperty(pomXml, "latest.published.version");
        ReleaseVersionContract versionContract = releaseVersionContract(
                projectVersion, latestPublishedVersion, Files.readString(pom.getParent().resolve("CHANGELOG.md")));
        LinkedHashMap<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("projectVersion", projectVersion);
        manifest.put("releaseState", versionContract.releaseState());
        manifest.put("developmentVersion", versionContract.developmentVersion());
        manifest.put("latestPublishedConsumerVersion", versionContract.latestPublishedConsumerVersion());
        manifest.put("plannedFinalVersion", versionContract.plannedFinalVersion());
        manifest.put("apiCompatibilityBaselineVersion", baselineVersion);
        manifest.put("apiCompatibilityBaselineMatchesProjectVersion", projectVersion.equals(baselineVersion));
        manifest.put("javaVersion", System.getProperty("java.version"));
        manifest.put("javaBaseline", pomProperty(pomXml, "java.version"));
        manifest.put("springBootBaseline", pomProperty(pomXml, "spring-boot.version"));
        manifest.put("dependencyBaselineReview", dependencyBaselineReview(pomXml));
        Map<String, Object> benchmarkEvidence = benchmarkEvidence(projectVersion, baselineVersion, versionContract,
                Files.exists(pom.getParent().resolve("docs/benchmark-report-" + projectVersion + ".md")));
        List<Map<String, String>> publishedBaselineArtifacts = publishedBaselineArtifacts(baselineVersion);
        List<Map<String, String>> checks = List.of(
                check("mvn test", "pass", "Generated by DocumentationReleaseArtifactTest during the current test run."),
                check(apiCompatibilityCommand("api-root", baselineVersion, null), "pending", "Run before release."),
                check(apiCompatibilityCommand("api-starter", baselineVersion, "reactive-http-client-starter"), "pending",
                        "Run before release to exercise module-scoped compatibility guard."),
                check("bash scripts/verify-api-compatibility-fixtures.sh", "pending", "Run before release."),
                check("bash scripts/verify-published-baseline-fixtures.sh", "pending",
                        "Run before release to reject local and candidate-contaminated baselines."),
                check("scripts/verify-published-consumer.sh " + latestPublishedVersion, "pending",
                        "Run the assembled consumer against the latest published release."),
                check("git diff --check", "pending", "Run before release."),
                check("mvn -Pbenchmarks -pl reactive-http-client-benchmarks -am package", "pending",
                        "Lightweight benchmark compile check; does not run JMH."),
                check("mvn -Pbenchmarks,benchmark-smoke -pl reactive-http-client-benchmarks -am verify", "pending",
                        "Harness smoke only; do not publish these numbers."),
                check("mvn -Pbenchmarks,benchmark-release -pl reactive-http-client-benchmarks -am verify -Dbenchmark.commit=$(git rev-parse --short HEAD)", "pending",
                        "Run when request-path behavior changed or release notes make performance claims."),
                check("mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.javadoc.skip=true install && "
                                + "mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-test -am "
                                + "-Dtest=Boot4MockReactiveHttpClientTest -Dsurefire.failIfNoSpecifiedTests=false test && "
                                + "mvn -B -ntp -s .mvn/maven-central-settings.xml -f .github/native-smoke/pom.xml "
                                + "-Pnative -Dreactive-http-client.version=" + projectVersion + " native:compile && "
                                + ".github/native-smoke/target/reactive-http-client-native-smoke",
                        "pending", "Run the supported native-image smoke before release."),
                check("mvn -B -ntp clean -Prelease -DskipTests verify && "
                                + "bash scripts/verify-publishable-artifacts.sh && "
                                + "bash scripts/verify-generation-packaging.sh",
                        "pending", "Run publication preflight from the final release candidate."));
        Map<String, Object> readiness = releaseReadiness(pom.getParent(), projectVersion, baselineVersion,
                versionContract, benchmarkEvidence, publishedBaselineArtifacts, checks);
        manifest.put("readiness", readiness);
        manifest.put("releasePrepChecklist", releasePrepChecklist(pom.getParent(), projectVersion, baselineVersion,
                versionContract, readiness, benchmarkEvidence, publishedBaselineArtifacts, checks));
        manifest.put("benchmarkDependencyManagement", benchmarkDependencyManagement(pomXml));
        manifest.put("publishedBaselineArtifacts", publishedBaselineArtifacts);
        manifest.put("benchmarkEvidence", benchmarkEvidence);
        manifest.put("checks", checks);
        return manifest;
    }

    private static ReleaseVersionContract releaseVersionContract(String projectVersion,
                                                                  String latestPublishedVersion,
                                                                  String changelog) {
        if (projectVersion.endsWith("-SNAPSHOT")) {
            return new ReleaseVersionContract(
                    "snapshot-development", projectVersion, latestPublishedVersion, null, latestPublishedVersion);
        }
        boolean published = projectVersion.equals(latestPublishedVersion)
                && changelog.contains("## [" + projectVersion + "] - ");
        // Public README/quick-start snippets always track the latest published release, so a
        // release candidate that is not yet on Maven Central keeps documenting the prior version.
        return new ReleaseVersionContract(
                published ? "post-publication" : "release-candidate",
                null,
                latestPublishedVersion,
                published ? null : projectVersion,
                latestPublishedVersion);
    }

    private static Map<String, Object> releaseReadiness(Path root,
                                                        String projectVersion,
                                                        String baselineVersion,
                                                        ReleaseVersionContract versionContract,
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
                .distinct()
                .forEach(pendingManualCommands::add);
        pendingManualCommands.add((String) benchmarkEvidence.get("publishedStarterCommand"));
        List<String> pendingBenchmarkCommands = new ArrayList<>(pendingManualCommands.stream()
                .filter(command -> command.contains("benchmark"))
                .toList());
        List<String> pendingCompatibilityCommands = pendingManualCommands.stream()
                .filter(command -> command.contains("api-compatibility")
                        || command.contains("verify-api-compatibility-fixtures")
                        || command.contains("verify-published-baseline-fixtures"))
                .toList();
        List<String> pendingConsumerCommands = pendingManualCommands.stream()
                .filter(command -> command.contains("verify-published-consumer.sh"))
                .toList();
        List<String> pendingNativeCommands = pendingManualCommands.stream()
                .filter(command -> command.contains("native:compile"))
                .toList();
        List<String> pendingPublicationCommands = pendingManualCommands.stream()
                .filter(command -> command.contains("verify-publishable-artifacts"))
                .toList();
        boolean configurationReferenceCurrent = Files.readString(root.resolve("docs/configuration-properties.md"))
                .equals(configurationReferenceMarkdown(configurationMetadata(root)));
        List<String> brokenLinks = brokenLocalMarkdownLinks(root);
        List<String> staleBenchmarkLinks = staleBenchmarkReportReferences(root, promotedReportVersion);
        String currentReleaseNotes = currentReleaseChangelogSection(
                Files.readString(root.resolve("CHANGELOG.md")), projectVersion);
        String missingPromotedReportStatus = projectVersion.endsWith("-SNAPSHOT")
                ? "deferred-until-release-cut"
                : containsPublicPerformanceClaim(currentReleaseNotes)
                        ? "missing"
                        : "not-required-no-public-claim";

        LinkedHashMap<String, Object> readiness = new LinkedHashMap<>();
        readiness.put("projectVersion", projectVersion);
        readiness.put("apiCompatibilityBaselineVersion", baselineVersion);
        readiness.put("apiCompatibilityBaselineMatchesProjectVersion", projectVersion.equals(baselineVersion));
        readiness.put("activeRoadmap", "v29");
        readiness.put("releaseLane", "additive-minor");
        readiness.put("releaseCandidate", majorReleaseCandidate(projectVersion, versionContract));
        readiness.put("generatedTestEvidence", readinessStatus("pass",
                "Generated by DocumentationReleaseArtifactTest in target/release-evidence/."));
        readiness.put("manualReleaseEvidence", readinessManualStatus(pendingManualCommands));
        readiness.put("manualBenchmarkEvidence", readinessManualStatus(pendingBenchmarkCommands));
        readiness.put("manualCompatibilityEvidence", readinessManualStatus(pendingCompatibilityCommands));
        readiness.put("manualConsumerEvidence", readinessManualStatus(pendingConsumerCommands));
        readiness.put("manualNativeEvidence", readinessManualStatus(pendingNativeCommands));
        LinkedHashMap<String, Object> publicationEvidence = new LinkedHashMap<>();
        publicationEvidence.put("status", projectVersion.endsWith("-SNAPSHOT")
                ? "deferred-until-release-cut" : "pending");
        publicationEvidence.put("workflow", ".github/workflows/publish-maven-central.yml");
        publicationEvidence.put("preflightCommands", pendingPublicationCommands);
        publicationEvidence.put("note", "Publish only from a version-matched final release tag after preflight passes.");
        readiness.put("manualPublicationEvidence", publicationEvidence);
        readiness.put("promotedBenchmarkReport", promotedReport == null
                ? readinessPathStatus(null, missingPromotedReportStatus)
                : readinessPathStatus(promotedReport, Files.exists(root.resolve(promotedReport)) ? "present" : "missing"));
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

    private static Map<String, Object> majorReleaseCandidate(String projectVersion,
                                                              ReleaseVersionContract versionContract) {
        String candidateVersion = projectVersion.endsWith("-SNAPSHOT")
                ? projectVersion.substring(0, projectVersion.length() - "-SNAPSHOT".length())
                : projectVersion;
        String status = switch (versionContract.releaseState()) {
            case "snapshot-development" -> "deferred";
            case "release-candidate" -> "pending-publication";
            case "post-publication" -> "published";
            default -> throw new IllegalStateException("Unsupported release state: " + versionContract.releaseState());
        };
        List<String> pendingWork = switch (versionContract.releaseState()) {
            case "snapshot-development" -> "4.1.0".equals(candidateVersion)
                    ? List.of("immutable release evidence", "go/no-go decision", "publication")
                    : "4.2.0".equals(candidateVersion)
                            ? List.of("weighted admission implementation", "API compatibility",
                                    "assembled consumers", "benchmarks", "AOT", "native image", "publication")
                            : List.of("release scope", "API compatibility", "assembled consumers", "benchmarks",
                                    "AOT", "native image", "publication");
            case "release-candidate" -> List.of("publication");
            case "post-publication" -> List.of();
            default -> throw new IllegalStateException(
                    "Unsupported release state: " + versionContract.releaseState());
        };
        LinkedHashMap<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("version", candidateVersion);
        candidate.put("status", status);
        candidate.put("published", "post-publication".equals(versionContract.releaseState()));
        if ("4.1.0".equals(candidateVersion)) {
            candidate.put("scopeStatus", "selected");
            candidate.put("scope", "additive method-specific semantic-read response caching");
        }
        if ("4.2.0".equals(candidateVersion)) {
            candidate.put("scopeStatus", "selected");
            candidate.put("scope", "optional decoded-response-representation-byte cache admission and eviction");
            candidate.put("weightContractDecision", "go");
            candidate.put("weightUnit", "decoded-response-representation-bytes");
            candidate.put("decisionDocument", "roadmaps/v29/RETAINED-WEIGHT-DECISION.md");
        }
        if ("4.0.0".equals(candidateVersion)) {
            candidate.put("migrationReport", "docs/31-3x-to-4x-resilience-migration.md");
        }
        candidate.put("pendingWork", pendingWork);
        return candidate;
    }

    private static Map<String, Object> releasePrepChecklist(Path root,
                                                            String projectVersion,
                                                            String baselineVersion,
                                                            ReleaseVersionContract versionContract,
                                                            Map<String, Object> readiness,
                                                            Map<String, Object> benchmarkEvidence,
                                                            List<Map<String, String>> publishedBaselineArtifacts,
                                                            List<Map<String, String>> checks) throws IOException {
        String changelog = Files.readString(root.resolve("CHANGELOG.md"));
        String unreleasedCompareVersion = changelog.contains("## [" + projectVersion + "] - ")
                ? projectVersion
                : versionContract.latestPublishedConsumerVersion();
        String expectedUnreleasedCompareLink = "[Unreleased]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v"
                + unreleasedCompareVersion + "...HEAD";
        boolean changelogCurrent = changelog.contains("## [Unreleased]")
                && changelog.contains(expectedUnreleasedCompareLink);
        String documentedConsumerVersion = versionContract.documentedConsumerVersion();
        boolean versionSnippetsCurrent = versionSnippetsMatch(root.resolve("README.md"), documentedConsumerVersion)
                && versionSnippetsMatch(root.resolve("docs/01-quick-start.md"), documentedConsumerVersion);

        List<String> publishedBaselineCommands = publishedBaselineArtifacts.stream()
                .map(artifact -> artifact.get("resolutionCommand"))
                .distinct()
                .toList();
        List<String> compatibilityCommands = checks.stream()
                .map(check -> check.get("command"))
                .filter(command -> command.contains("api-compatibility")
                        || command.contains("verify-api-compatibility-fixtures")
                        || command.contains("verify-published-baseline-fixtures"))
                .toList();
        List<String> consumerCommands = checks.stream()
                .map(check -> check.get("command"))
                .filter(command -> command.contains("verify-published-consumer.sh"))
                .toList();
        List<String> benchmarkCommands = new ArrayList<>(checks.stream()
                .map(check -> check.get("command"))
                .filter(command -> command.contains("benchmark"))
                .toList());
        benchmarkCommands.add((String) benchmarkEvidence.get("publishedStarterCommand"));
        List<String> nativeCommands = checks.stream()
                .map(check -> check.get("command"))
                .filter(command -> command.contains("native:compile"))
                .toList();
        List<String> publicationCommands = checks.stream()
                .map(check -> check.get("command"))
                .filter(command -> command.contains("verify-publishable-artifacts"))
                .toList();

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
                        "expectedVersion", documentedConsumerVersion)));
        @SuppressWarnings("unchecked")
        Map<String, Object> majorCandidate = (Map<String, Object>) readiness.get("releaseCandidate");
        items.add(checklistItem("major-candidate", "Next release candidate",
                (String) majorCandidate.get("status"), majorCandidate));
        items.add(checklistItem("published-baseline-artifacts", "Published baseline artifact resolution",
                "pending", Map.of("commands", publishedBaselineCommands)));
        items.add(checklistItem("api-compatibility", "API compatibility evidence",
                "pending", Map.of("commands", compatibilityCommands)));
        items.add(checklistItem("published-consumer", "Published assembled-consumer evidence",
                "pending", Map.of("commands", consumerCommands)));
        items.add(checklistItem("native-evidence", "Native-image evidence",
                "pending", Map.of("commands", nativeCommands)));
        items.add(checklistItem("publication-readiness", "Publication readiness",
                "snapshot-development".equals(versionContract.releaseState())
                        ? "deferred-until-release-cut" : "pending", Map.of(
                        "workflow", ".github/workflows/publish-maven-central.yml",
                        "preflightCommands", publicationCommands)));
        items.add(checklistItem("benchmark-evidence", "Benchmark evidence",
                "pending", Map.of(
                        "commands", benchmarkCommands,
                        "currentCandidateReport", benchmarkEvidence.get("currentCandidateReport"),
                        "publishedBaselineReport", benchmarkEvidence.get("publishedBaselineReport"))));
        LinkedHashMap<String, Object> promotedReportDetails = new LinkedHashMap<>();
        promotedReportDetails.put("path", benchmarkEvidence.get("promotedReport"));
        items.add(checklistItem("promoted-benchmark-report", "Promoted benchmark report",
                promotedReportStatus, promotedReportDetails));
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
        checklist.put("releaseState", versionContract.releaseState());
        checklist.put("developmentVersion", versionContract.developmentVersion());
        checklist.put("latestPublishedConsumerVersion", versionContract.latestPublishedConsumerVersion());
        checklist.put("plannedFinalVersion", versionContract.plannedFinalVersion());
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

    private static Map<String, Object> benchmarkEvidence(String projectVersion,
                                                         String baselineVersion,
                                                         ReleaseVersionContract versionContract,
                                                         boolean promotedReportExists) {
        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("manualOrProfileGated", true);
        evidence.put("currentWorkspaceCommand",
                "mvn -Pbenchmarks,benchmark-release -pl reactive-http-client-benchmarks -am verify -Dbenchmark.commit=$(git rev-parse --short HEAD)");
        evidence.put("publishedStarterCommand",
                "test ! -e " + publishedBaselineRepository("benchmark", baselineVersion)
                        + " && mvn -s .mvn/maven-central-settings.xml -Dmaven.repo.local="
                        + publishedBaselineRepository("benchmark", baselineVersion)
                        + " -Pbenchmarks,benchmark-release,benchmark-published-baseline -pl reactive-http-client-benchmarks clean verify -Dbenchmark.starter.version="
                        + baselineVersion + " -Dbenchmark.commit=" + baselineVersion
                        + " && scripts/verify-published-baseline-provenance.sh benchmark " + baselineVersion
                        + " target/release-evidence/published-baselines/benchmark-" + baselineVersion
                        + " reactive-http-client-starter");
        evidence.put("reportDirectory", "reactive-http-client-benchmarks/target/benchmark-reports/");
        evidence.put("smokeReport", "reactive-http-client-benchmarks/target/benchmark-reports/smoke-only-jmh.md");
        evidence.put("releaseReport", "reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.md");
        evidence.put("publishedStarterReleaseReport", "reactive-http-client-benchmarks/target/benchmark-reports/published-starter-"
                + baselineVersion + "/release-jmh.md");
        // Promotion is gated on the report actually existing so a non-snapshot release
        // that intentionally defers benchmark evidence stays honestly "pending".
        boolean promotable = !"snapshot-development".equals(versionContract.releaseState())
                && promotedReportExists;
        String promotedReport = promotable
                ? "docs/benchmark-report-" + projectVersion + ".md"
                : null;
        evidence.put("promotableReportAvailable", promotedReport != null);
        evidence.put("promotedReport", promotedReport);
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

    private record ReleaseVersionContract(
            String releaseState,
            String developmentVersion,
            String latestPublishedConsumerVersion,
            String plannedFinalVersion,
            String documentedConsumerVersion) {
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
        artifact.put("resolutionCommand", "scripts/verify-published-release-artifacts.sh " + baselineVersion);
        artifact.put("note", "Run before release; unresolved published artifacts are release blockers.");
        return artifact;
    }

    private static String apiCompatibilityCommand(String lane, String baselineVersion, String module) {
        String repository = publishedBaselineRepository(lane, baselineVersion);
        String modules = module == null
                ? "reactive-http-client reactive-http-client-starter reactive-http-client-test reactive-http-client-otel"
                : module;
        String projectSelection = module == null ? "" : " -pl " + module;
        return "test ! -e " + repository
                + " && mvn -s .mvn/maven-central-settings.xml -Dmaven.repo.local=" + repository
                + projectSelection + " -Papi-compatibility -DskipTests verify"
                + " && scripts/verify-published-baseline-provenance.sh " + lane + " " + baselineVersion
                + " target/release-evidence/published-baselines/" + lane + "-" + baselineVersion
                + " " + modules;
    }

    private static String publishedBaselineRepository(String lane, String baselineVersion) {
        return "target/published-baseline-repositories/" + lane + "-" + baselineVersion;
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

    private static List<String> sensitiveSupportFixtureFieldNames(JsonNode node) {
        List<String> sensitiveNames = new ArrayList<>();
        collectSensitiveSupportFixtureFieldNames(node, sensitiveNames);
        return sensitiveNames;
    }

    private static void collectSensitiveSupportFixtureFieldNames(
            JsonNode node, List<String> sensitiveNames) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> property : node.properties()) {
                String normalizedName = property.getKey()
                        .replaceAll("[^A-Za-z0-9]", "")
                        .toLowerCase(Locale.ROOT);
                if (SENSITIVE_SUPPORT_FIXTURE_FIELD_FRAGMENTS.stream()
                        .anyMatch(normalizedName::contains)) {
                    sensitiveNames.add(property.getKey());
                }
                collectSensitiveSupportFixtureFieldNames(property.getValue(), sensitiveNames);
            }
        }
        else if (node.isArray()) {
            node.forEach(child -> collectSensitiveSupportFixtureFieldNames(child, sensitiveNames));
        }
    }

    private static List<String> sensitiveSupportFixtureTextValues(JsonNode node) {
        List<String> sensitiveValues = new ArrayList<>();
        collectSensitiveSupportFixtureTextValues(node, sensitiveValues);
        return sensitiveValues;
    }

    private static void collectSensitiveSupportFixtureTextValues(
            JsonNode node, List<String> sensitiveValues) {
        if (node.isTextual()) {
            String value = node.asText().trim();
            if (SUPPORT_FIXTURE_REQUEST_TARGET_VALUE.matcher(value).matches()
                    || SUPPORT_FIXTURE_EMBEDDED_HTTP_REQUEST_LINE.matcher(value).find()
                    || SUPPORT_FIXTURE_QUERY_VALUE.matcher(value).matches()
                    || SUPPORT_FIXTURE_AUTHORITY_VALUE.matcher(value).matches()
                    || (!SUPPORT_FIXTURE_ALLOWED_SLASH_VALUES.contains(value)
                            && SUPPORT_FIXTURE_ROOTLESS_PATH_VALUE.matcher(value).matches())) {
                sensitiveValues.add(node.asText());
            }
            return;
        }
        if (node.isObject()) {
            node.properties().forEach(
                    property -> collectSensitiveSupportFixtureTextValues(
                            property.getValue(), sensitiveValues));
        }
        else if (node.isArray()) {
            node.forEach(child -> collectSensitiveSupportFixtureTextValues(child, sensitiveValues));
        }
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
