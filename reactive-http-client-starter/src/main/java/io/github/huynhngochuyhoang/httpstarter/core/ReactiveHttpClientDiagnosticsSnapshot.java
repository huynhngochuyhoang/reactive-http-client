package io.github.huynhngochuyhoang.httpstarter.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Renders sanitized reactive HTTP client diagnostics for support artifacts.
 *
 * <p>The snapshot only renders fields already exposed by
 * {@link ReactiveHttpClientDiagnosticsProvider.ClientSummary}. It intentionally
 * does not include base URL values, header values, proxy credentials, auth
 * provider bean names, request bodies, or response bodies.
 */
public final class ReactiveHttpClientDiagnosticsSnapshot {

    private static final String POM_PROPERTIES =
            "META-INF/maven/io.github.huynhngochuyhoang/reactive-http-client-starter/pom.properties";

    private ReactiveHttpClientDiagnosticsSnapshot() {
    }

    public static String toMarkdown(ReactiveHttpClientDiagnosticsProvider provider) {
        return toMarkdown(provider.clientSummaries());
    }

    public static String toMarkdown(Collection<ReactiveHttpClientDiagnosticsProvider.ClientSummary> summaries) {
        List<ReactiveHttpClientDiagnosticsProvider.ClientSummary> clients = sorted(summaries);
        StringBuilder out = new StringBuilder(512 + clients.size() * 192);
        out.append("# Reactive HTTP Client Diagnostics Snapshot\n\n");
        out.append("| Field | Value |\n");
        out.append("|---|---|\n");
        out.append("| Project version | `").append(markdown(projectVersion())).append("` |\n");
        out.append("| Client count | `").append(clients.size()).append("` |\n");
        out.append("| Endpoint count | `").append(endpointCount(clients)).append("` |\n");
        out.append("| Inherited endpoint count | `").append(inheritedEndpointCount(clients)).append("` |\n\n");
        out.append("| Client | Interface | Base URL source | Timeout | Resilience | Auth mode | Redirects | Endpoints | Inherited endpoints |\n");
        out.append("|---|---|---|---|---|---|---|---|---|\n");
        for (ReactiveHttpClientDiagnosticsProvider.ClientSummary client : clients) {
            out.append("| `").append(markdown(client.clientName())).append("` ");
            out.append("| `").append(markdown(client.clientInterface())).append("` ");
            out.append("| `").append(markdown(client.baseUrlSource())).append("` ");
            out.append("| `").append(markdown(timeout(client.timeout()))).append("` ");
            out.append("| `").append(markdown(resilience(client.resilience()))).append("` ");
            out.append("| `").append(markdown(client.authMode())).append("` ");
            out.append("| `").append(client.followRedirects()).append("` ");
            out.append("| `").append(client.endpointCount()).append("` ");
            out.append("| `").append(client.inheritedEndpointCount()).append("` |\n");
        }
        return out.toString();
    }

    public static String toJson(ReactiveHttpClientDiagnosticsProvider provider) {
        return toJson(provider.clientSummaries());
    }

    public static Map<String, Object> toMap(ReactiveHttpClientDiagnosticsProvider provider) {
        return toMap(provider.clientSummaries());
    }

    public static Map<String, Object> toMap(Collection<ReactiveHttpClientDiagnosticsProvider.ClientSummary> summaries) {
        List<ReactiveHttpClientDiagnosticsProvider.ClientSummary> clients = sorted(summaries);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("projectVersion", projectVersion());
        snapshot.put("clientCount", clients.size());
        snapshot.put("endpointCount", endpointCount(clients));
        snapshot.put("inheritedEndpointCount", inheritedEndpointCount(clients));
        List<Map<String, Object>> clientMaps = new ArrayList<>(clients.size());
        for (ReactiveHttpClientDiagnosticsProvider.ClientSummary client : clients) {
            Map<String, Object> clientMap = new LinkedHashMap<>();
            clientMap.put("clientName", client.clientName());
            clientMap.put("clientInterface", client.clientInterface());
            clientMap.put("baseUrlSource", client.baseUrlSource());
            clientMap.put("timeoutSource", client.timeout().source());
            clientMap.put("timeoutMs", client.timeout().timeoutMs());
            clientMap.put("resilienceConfigured", client.resilience().configured());
            clientMap.put("retry", client.resilience().retry());
            clientMap.put("rateLimiter", client.resilience().rateLimiter());
            clientMap.put("circuitBreaker", client.resilience().circuitBreaker());
            clientMap.put("bulkhead", client.resilience().bulkhead());
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
        List<ReactiveHttpClientDiagnosticsProvider.ClientSummary> clients = sorted(summaries);
        StringBuilder out = new StringBuilder(512 + clients.size() * 256);
        out.append("{\n");
        field(out, 1, "projectVersion", projectVersion(), true);
        field(out, 1, "clientCount", clients.size(), true);
        field(out, 1, "endpointCount", endpointCount(clients), true);
        field(out, 1, "inheritedEndpointCount", inheritedEndpointCount(clients), true);
        indent(out, 1).append("\"clients\": [");
        if (!clients.isEmpty()) {
            out.append('\n');
        }
        for (int i = 0; i < clients.size(); i++) {
            ReactiveHttpClientDiagnosticsProvider.ClientSummary client = clients.get(i);
            indent(out, 2).append("{\n");
            field(out, 3, "clientName", client.clientName(), true);
            field(out, 3, "clientInterface", client.clientInterface(), true);
            field(out, 3, "baseUrlSource", client.baseUrlSource(), true);
            field(out, 3, "timeoutSource", client.timeout().source(), true);
            field(out, 3, "timeoutMs", client.timeout().timeoutMs(), true);
            field(out, 3, "resilienceConfigured", client.resilience().configured(), true);
            field(out, 3, "retry", client.resilience().retry(), true);
            field(out, 3, "rateLimiter", client.resilience().rateLimiter(), true);
            field(out, 3, "circuitBreaker", client.resilience().circuitBreaker(), true);
            field(out, 3, "bulkhead", client.resilience().bulkhead(), true);
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
        return out.toString();
    }

    private static List<ReactiveHttpClientDiagnosticsProvider.ClientSummary> sorted(
            Collection<ReactiveHttpClientDiagnosticsProvider.ClientSummary> summaries) {
        return summaries.stream()
                .sorted(Comparator.comparing(ReactiveHttpClientDiagnosticsProvider.ClientSummary::clientName)
                        .thenComparing(ReactiveHttpClientDiagnosticsProvider.ClientSummary::clientInterface))
                .toList();
    }

    private static int endpointCount(List<ReactiveHttpClientDiagnosticsProvider.ClientSummary> summaries) {
        return summaries.stream()
                .mapToInt(ReactiveHttpClientDiagnosticsProvider.ClientSummary::endpointCount)
                .sum();
    }

    private static int inheritedEndpointCount(List<ReactiveHttpClientDiagnosticsProvider.ClientSummary> summaries) {
        return summaries.stream()
                .mapToInt(ReactiveHttpClientDiagnosticsProvider.ClientSummary::inheritedEndpointCount)
                .sum();
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
