package io.github.huynhngochuyhoang.httpstarter.core;

import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Renders sanitized reactive HTTP client diagnostics for support artifacts.
 *
 * <p>The collection overloads mark provider-only strict validation flags as unknown
 * because {@link ReactiveHttpClientDiagnosticsProvider.ClientSummary} does not
 * carry those values; provider overloads render them from internal sanitized
 * entries. It intentionally
 * does not include base URL values, header values, proxy credentials, auth
 * provider bean names, request bodies, or response bodies. Schema version 1 is
 * additive within the 3.x line. Rendering rejects snapshots beyond the documented
 * client, endpoint, text-field, and output-size limits instead of truncating them.
 */
public final class ReactiveHttpClientDiagnosticsSnapshot {

    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_CLIENTS = 256;
    private static final int MAX_ENDPOINTS = 10_000;
    private static final int MAX_TEXT_LENGTH = 512;
    private static final int MAX_RENDERED_BYTES = 1_048_576;
    private static final String POM_PROPERTIES =
            "META-INF/maven/io.github.huynhngochuyhoang/reactive-http-client-starter/pom.properties";

    private ReactiveHttpClientDiagnosticsSnapshot() {
    }

    public static String toMarkdown(ReactiveHttpClientDiagnosticsProvider provider) {
        return toMarkdownEntries(snapshotClients(provider));
    }

    private static List<SnapshotClient> snapshotClients(ReactiveHttpClientDiagnosticsProvider provider) {
        if (usesInternalSnapshotEntries(provider)) {
            return provider.clientSnapshotEntries().stream()
                    .map(ReactiveHttpClientDiagnosticsSnapshot::snapshotClient)
                    .toList();
        }
        return provider.clientSummaries().stream()
                .map(ReactiveHttpClientDiagnosticsSnapshot::snapshotClient)
                .toList();
    }

    private static boolean usesInternalSnapshotEntries(ReactiveHttpClientDiagnosticsProvider provider) {
        try {
            Class<?> providerClass = ClassUtils.getUserClass(provider);
            java.lang.reflect.Method method = providerClass.getMethod("clientSummaries");
            return method.getDeclaringClass() == ReactiveHttpClientDiagnosticsProvider.class;
        }
        catch (NoSuchMethodException ex) {
            return false;
        }
    }

    public static String toMarkdown(Collection<ReactiveHttpClientDiagnosticsProvider.ClientSummary> summaries) {
        return toMarkdownEntries(summaries.stream()
                .map(ReactiveHttpClientDiagnosticsSnapshot::snapshotClient)
                .toList());
    }

    private static String toMarkdownEntries(Collection<SnapshotClient> entries) {
        List<SnapshotClient> clients = sortedEntries(entries);
        StringBuilder out = new StringBuilder(512 + clients.size() * 224);
        out.append("# Reactive HTTP Client Diagnostics Snapshot\n\n");
        out.append("| Field | Value |\n");
        out.append("|---|---|\n");
        out.append("| Schema version | `").append(SCHEMA_VERSION).append("` |\n");
        out.append("| Project version | `").append(markdown(projectVersion())).append("` |\n");
        out.append("| Client count | `").append(clients.size()).append("` |\n");
        out.append("| Endpoint count | `").append(endpointCountEntries(clients)).append("` |\n");
        out.append("| Inherited endpoint count | `").append(inheritedEndpointCountEntries(clients)).append("` |\n\n");
        out.append("| Client | Interface | Base URL source | Pool | Timeout | Resilience | Strict retry validation | Strict body-signing validation | Auth mode | Redirects | Endpoints | Inherited endpoints |\n");
        out.append("|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (SnapshotClient entry : clients) {
            ReactiveHttpClientDiagnosticsProvider.ClientSummary client = entry.summary();
            out.append("| `").append(markdown(client.clientName())).append("` ");
            out.append("| `").append(markdown(client.clientInterface())).append("` ");
            out.append("| `").append(markdown(client.baseUrlSource())).append("` ");
            out.append("| `").append(markdown(pool(entry.pool()))).append("` ");
            out.append("| `").append(markdown(timeout(client.timeout()))).append("` ");
            out.append("| `").append(markdown(resilience(client.resilience()))).append("` ");
            out.append("| `").append(strictFlag(entry.strictUnsafeRetryValidation())).append("` ");
            out.append("| `").append(strictFlag(entry.strictBodySigningValidation())).append("` ");
            out.append("| `").append(markdown(client.authMode())).append("` ");
            out.append("| `").append(client.followRedirects()).append("` ");
            out.append("| `").append(client.endpointCount()).append("` ");
            out.append("| `").append(client.inheritedEndpointCount()).append("` |\n");
        }
        return boundedOutput(out, "Markdown");
    }

    public static String toJson(ReactiveHttpClientDiagnosticsProvider provider) {
        return toJsonEntries(snapshotClients(provider));
    }

    public static Map<String, Object> toMap(ReactiveHttpClientDiagnosticsProvider provider) {
        return toMapEntries(snapshotClients(provider));
    }

    public static Map<String, Object> toMap(Collection<ReactiveHttpClientDiagnosticsProvider.ClientSummary> summaries) {
        return toMapEntries(summaries.stream()
                .map(ReactiveHttpClientDiagnosticsSnapshot::snapshotClient)
                .toList());
    }

    private static Map<String, Object> toMapEntries(Collection<SnapshotClient> entries) {
        List<SnapshotClient> clients = sortedEntries(entries);
        renderJsonEntries(clients);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", SCHEMA_VERSION);
        snapshot.put("projectVersion", projectVersion());
        snapshot.put("clientCount", clients.size());
        snapshot.put("endpointCount", endpointCountEntries(clients));
        snapshot.put("inheritedEndpointCount", inheritedEndpointCountEntries(clients));
        List<Map<String, Object>> clientMaps = new ArrayList<>(clients.size());
        for (SnapshotClient entry : clients) {
            ReactiveHttpClientDiagnosticsProvider.ClientSummary client = entry.summary();
            Map<String, Object> clientMap = new LinkedHashMap<>();
            clientMap.put("clientName", client.clientName());
            clientMap.put("clientInterface", client.clientInterface());
            clientMap.put("baseUrlSource", client.baseUrlSource());
            clientMap.put("poolSource", poolSource(entry.pool()));
            clientMap.put("poolMaxConnections", poolMaxConnections(entry.pool()));
            clientMap.put("poolPendingAcquireTimeoutMs", poolPendingAcquireTimeoutMs(entry.pool()));
            clientMap.put("poolMetricsEnabled", poolMetricsEnabled(entry.pool()));
            clientMap.put("timeoutSource", client.timeout().source());
            clientMap.put("timeoutMs", client.timeout().timeoutMs());
            clientMap.put("resilienceConfigured", client.resilience().configured());
            clientMap.put("retry", client.resilience().retry());
            clientMap.put("rateLimiter", client.resilience().rateLimiter());
            clientMap.put("circuitBreaker", client.resilience().circuitBreaker());
            clientMap.put("bulkhead", client.resilience().bulkhead());
            clientMap.put("strictUnsafeRetryValidation", entry.strictUnsafeRetryValidation());
            clientMap.put("strictBodySigningValidation", entry.strictBodySigningValidation());
            clientMap.put("authMode", client.authMode());
            clientMap.put("followRedirects", client.followRedirects());
            clientMap.put("endpointCount", client.endpointCount());
            clientMap.put("inheritedEndpointCount", client.inheritedEndpointCount());
            clientMaps.add(clientMap);
        }
        snapshot.put("clients", clientMaps);
        return snapshot;
    }

    public static String toJson(Collection<ReactiveHttpClientDiagnosticsProvider.ClientSummary> summaries) {
        return toJsonEntries(summaries.stream()
                .map(ReactiveHttpClientDiagnosticsSnapshot::snapshotClient)
                .toList());
    }

    private static String toJsonEntries(Collection<SnapshotClient> entries) {
        List<SnapshotClient> clients = sortedEntries(entries);
        return renderJsonEntries(clients);
    }

    private static String renderJsonEntries(List<SnapshotClient> clients) {
        StringBuilder out = new StringBuilder(512 + clients.size() * 288);
        out.append("{\n");
        field(out, 1, "schemaVersion", SCHEMA_VERSION, true);
        field(out, 1, "projectVersion", projectVersion(), true);
        field(out, 1, "clientCount", clients.size(), true);
        field(out, 1, "endpointCount", endpointCountEntries(clients), true);
        field(out, 1, "inheritedEndpointCount", inheritedEndpointCountEntries(clients), true);
        indent(out, 1).append("\"clients\": [");
        if (!clients.isEmpty()) {
            out.append('\n');
        }
        for (int i = 0; i < clients.size(); i++) {
            SnapshotClient entry = clients.get(i);
            ReactiveHttpClientDiagnosticsProvider.ClientSummary client = entry.summary();
            indent(out, 2).append("{\n");
            field(out, 3, "clientName", client.clientName(), true);
            field(out, 3, "clientInterface", client.clientInterface(), true);
            field(out, 3, "baseUrlSource", client.baseUrlSource(), true);
            field(out, 3, "poolSource", poolSource(entry.pool()), true);
            nullableField(out, 3, "poolMaxConnections", poolMaxConnections(entry.pool()), true);
            nullableField(out, 3, "poolPendingAcquireTimeoutMs", poolPendingAcquireTimeoutMs(entry.pool()), true);
            field(out, 3, "poolMetricsEnabled", poolMetricsEnabled(entry.pool()), true);
            field(out, 3, "timeoutSource", client.timeout().source(), true);
            field(out, 3, "timeoutMs", client.timeout().timeoutMs(), true);
            field(out, 3, "resilienceConfigured", client.resilience().configured(), true);
            field(out, 3, "retry", client.resilience().retry(), true);
            field(out, 3, "rateLimiter", client.resilience().rateLimiter(), true);
            field(out, 3, "circuitBreaker", client.resilience().circuitBreaker(), true);
            field(out, 3, "bulkhead", client.resilience().bulkhead(), true);
            field(out, 3, "strictUnsafeRetryValidation", entry.strictUnsafeRetryValidation(), true);
            field(out, 3, "strictBodySigningValidation", entry.strictBodySigningValidation(), true);
            field(out, 3, "authMode", client.authMode(), true);
            field(out, 3, "followRedirects", client.followRedirects(), true);
            field(out, 3, "endpointCount", client.endpointCount(), true);
            field(out, 3, "inheritedEndpointCount", client.inheritedEndpointCount(), false);
            indent(out, 2).append('}');
            if (i + 1 < clients.size()) {
                out.append(',');
            }
            out.append('\n');
        }
        if (!clients.isEmpty()) {
            indent(out, 1);
        }
        out.append("]\n");
        out.append("}\n");
        return boundedOutput(out, "JSON");
    }

    private static SnapshotClient snapshotClient(ReactiveHttpClientDiagnosticsProvider.ClientSummary summary) {
        return new SnapshotClient(summary, null, null, null);
    }

    private static SnapshotClient snapshotClient(ReactiveHttpClientDiagnosticsProvider.ClientSnapshotEntry entry) {
        return new SnapshotClient(
                entry.summary(),
                entry.strictUnsafeRetryValidation(),
                entry.strictBodySigningValidation(),
                entry.pool());
    }

    private static List<SnapshotClient> sortedEntries(Collection<SnapshotClient> entries) {
        Objects.requireNonNull(entries, "entries");
        if (entries.size() > MAX_CLIENTS) {
            throw new IllegalArgumentException("Diagnostics snapshot exceeds the " + MAX_CLIENTS + " client limit");
        }
        List<SnapshotClient> sorted = entries.stream()
                .peek(ReactiveHttpClientDiagnosticsSnapshot::validateEntry)
                .sorted(Comparator.comparing((SnapshotClient entry) -> entry.summary().clientName())
                        .thenComparing(entry -> entry.summary().clientInterface()))
                .toList();
        long endpoints = sorted.stream()
                .map(SnapshotClient::summary)
                .mapToLong(ReactiveHttpClientDiagnosticsProvider.ClientSummary::endpointCount)
                .sum();
        if (endpoints > MAX_ENDPOINTS) {
            throw new IllegalArgumentException("Diagnostics snapshot exceeds the " + MAX_ENDPOINTS + " endpoint limit");
        }
        return sorted;
    }

    private static void validateEntry(SnapshotClient entry) {
        Objects.requireNonNull(entry, "diagnostics snapshot entry");
        ReactiveHttpClientDiagnosticsProvider.ClientSummary summary =
                Objects.requireNonNull(entry.summary(), "diagnostics client summary");
        boundedText("clientName", summary.clientName());
        boundedText("clientInterface", summary.clientInterface());
        boundedText("baseUrlSource", summary.baseUrlSource());
        boundedText("timeoutSource", Objects.requireNonNull(summary.timeout(), "timeout").source());
        ReactiveHttpClientDiagnosticsProvider.ResilienceSummary resilience =
                Objects.requireNonNull(summary.resilience(), "resilience");
        boundedText("retry", resilience.retry());
        boundedText("rateLimiter", resilience.rateLimiter());
        boundedText("circuitBreaker", resilience.circuitBreaker());
        boundedText("bulkhead", resilience.bulkhead());
        boundedText("authMode", summary.authMode());
        if (summary.endpointCount() < 0 || summary.inheritedEndpointCount() < 0
                || summary.inheritedEndpointCount() > summary.endpointCount()) {
            throw new IllegalArgumentException("Diagnostics snapshot contains invalid endpoint counts for client "
                    + summary.clientName());
        }
    }

    private static void boundedText(String field, String value) {
        Objects.requireNonNull(value, field);
        if (value.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Diagnostics snapshot field " + field
                    + " exceeds the " + MAX_TEXT_LENGTH + " character limit");
        }
    }

    private static String boundedOutput(StringBuilder out, String format) {
        String rendered = out.toString();
        if (rendered.getBytes(StandardCharsets.UTF_8).length > MAX_RENDERED_BYTES) {
            throw new IllegalArgumentException(format + " diagnostics snapshot exceeds the "
                    + MAX_RENDERED_BYTES + " byte limit");
        }
        return rendered;
    }

    private record SnapshotClient(
            ReactiveHttpClientDiagnosticsProvider.ClientSummary summary,
            Boolean strictUnsafeRetryValidation,
            Boolean strictBodySigningValidation,
            ReactiveHttpClientDiagnosticsProvider.PoolSummary pool
    ) {
    }

    private static int endpointCountEntries(List<SnapshotClient> entries) {
        return entries.stream()
                .map(SnapshotClient::summary)
                .mapToInt(ReactiveHttpClientDiagnosticsProvider.ClientSummary::endpointCount)
                .sum();
    }

    private static int inheritedEndpointCountEntries(List<SnapshotClient> entries) {
        return entries.stream()
                .map(SnapshotClient::summary)
                .mapToInt(ReactiveHttpClientDiagnosticsProvider.ClientSummary::inheritedEndpointCount)
                .sum();
    }

    private static String pool(ReactiveHttpClientDiagnosticsProvider.PoolSummary pool) {
        if (pool == null) {
            return "unknown";
        }
        return pool.source() + ":maxConnections=" + pool.maxConnections()
                + ", pendingAcquireTimeoutMs=" + pool.pendingAcquireTimeoutMs()
                + ", metrics=" + pool.metricsEnabled();
    }

    private static String poolSource(ReactiveHttpClientDiagnosticsProvider.PoolSummary pool) {
        return pool != null ? pool.source() : "unknown";
    }

    private static Integer poolMaxConnections(ReactiveHttpClientDiagnosticsProvider.PoolSummary pool) {
        return pool != null ? pool.maxConnections() : null;
    }

    private static Long poolPendingAcquireTimeoutMs(ReactiveHttpClientDiagnosticsProvider.PoolSummary pool) {
        return pool != null ? pool.pendingAcquireTimeoutMs() : null;
    }

    private static Boolean poolMetricsEnabled(ReactiveHttpClientDiagnosticsProvider.PoolSummary pool) {
        return pool != null ? pool.metricsEnabled() : null;
    }

    private static String timeout(ReactiveHttpClientDiagnosticsProvider.TimeoutSummary timeout) {
        return timeout.source() + ":" + timeout.timeoutMs();
    }

    private static String resilience(ReactiveHttpClientDiagnosticsProvider.ResilienceSummary resilience) {
        return "configured=" + resilience.configured()
                + ", retry=" + resilience.retry()
                + ", rateLimiter=" + resilience.rateLimiter()
                + ", circuitBreaker=" + resilience.circuitBreaker()
                + ", bulkhead=" + resilience.bulkhead();
    }

    private static String strictFlag(Boolean strictFlag) {
        return strictFlag != null ? strictFlag.toString() : "unknown";
    }

    private static String projectVersion() {
        String version = ReactiveHttpClientDiagnosticsSnapshot.class.getPackage().getImplementationVersion();
        if (version != null && !version.isBlank()) {
            return version;
        }
        version = projectVersionFromPomProperties(Thread.currentThread().getContextClassLoader());
        if (version != null && !version.isBlank()) {
            return version;
        }
        version = projectVersionFromPomProperties(ReactiveHttpClientDiagnosticsSnapshot.class.getClassLoader());
        return version != null && !version.isBlank() ? version : "unknown";
    }

    private static String projectVersionFromPomProperties(ClassLoader classLoader) {
        if (classLoader == null) {
            return null;
        }
        try (InputStream input = classLoader.getResourceAsStream(POM_PROPERTIES)) {
            if (input == null) {
                return null;
            }
            Properties properties = new Properties();
            properties.load(input);
            return properties.getProperty("version");
        }
        catch (IOException ex) {
            return null;
        }
    }

    private static StringBuilder indent(StringBuilder out, int level) {
        return out.append("  ".repeat(level));
    }

    private static void field(StringBuilder out, int indent, String name, String value, boolean comma) {
        indent(out, indent)
                .append('"').append(json(name)).append("\": \"").append(json(value)).append('"');
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    private static void field(StringBuilder out, int indent, String name, int value, boolean comma) {
        indent(out, indent).append('"').append(json(name)).append("\": ").append(value);
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    private static void field(StringBuilder out, int indent, String name, long value, boolean comma) {
        indent(out, indent).append('"').append(json(name)).append("\": ").append(value);
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    private static void nullableField(StringBuilder out, int indent, String name, Number value, boolean comma) {
        indent(out, indent).append("\"").append(json(name)).append("\": ");
        out.append(value != null ? value.toString() : "null");
        if (comma) {
            out.append(",");
        }
        out.append("\n");
    }

    private static void field(StringBuilder out, int indent, String name, Boolean value, boolean comma) {
        indent(out, indent).append('"').append(json(name)).append("\": ");
        out.append(value != null ? value.toString() : "null");
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    private static void field(StringBuilder out, int indent, String name, boolean value, boolean comma) {
        indent(out, indent).append('"').append(json(name)).append("\": ").append(value);
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    private static String markdown(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("|", "\\|")
                .replace("`", "\\`")
                .replace("\r", "")
                .replace("\n", " ");
    }

    private static String json(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    }
                    else {
                        out.append(ch);
                    }
                }
            }
        }
        return out.toString();
    }
}
