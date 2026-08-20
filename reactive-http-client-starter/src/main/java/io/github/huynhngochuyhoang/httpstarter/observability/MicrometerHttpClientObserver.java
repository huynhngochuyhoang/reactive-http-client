package io.github.huynhngochuyhoang.httpstarter.observability;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * {@link HttpClientObserver} implementation that records Micrometer metrics for
 * every HTTP exchange performed by a {@code @ReactiveHttpClient} proxy.
 *
 * <h3>Metrics produced</h3>
 * <table border="1">
 *   <tr><th>Metric</th><th>Type</th><th>Tags</th></tr>
 *   <tr>
 *     <td>{@code <metricName>} (default: {@code reactive.http.client.requests})</td>
 *     <td>Timer (also exposes count + sum)</td>
 *     <td>client.name, api.name, http.method, uri, http.status_code, outcome, exception, error.category, failure.stage;
 *         optionally server.address and server.port</td>
 *   </tr>
 *   <tr>
 *     <td>{@code <metricName>.attempts}</td>
 *     <td>DistributionSummary (subscription attempts per invocation)</td>
 *     <td>client.name, api.name, http.method, uri</td>
 *   </tr>
 *   <tr>
 *     <td>{@code <metricName>.request.size}</td>
 *     <td>DistributionSummary (application request body bytes before transport content coding;
 *         only recorded when measurable - byte[]/String/null bodies; String uses the effective
 *         declared content-type charset)</td>
 *     <td>client.name, api.name, http.method, uri</td>
 *   </tr>
 *   <tr>
 *     <td>{@code <metricName>.response.size}</td>
 *     <td>DistributionSummary (post-transport advertised response bytes from {@code Content-Length};
 *         skipped for chunked and automatically decompressed responses)</td>
 *     <td>client.name, api.name, http.method, uri</td>
 *   </tr>
 *   <tr>
 *     <td>{@code <metricName>.latency} (when histogram enabled)</td>
 *     <td>Timer with SLO histogram buckets</td>
 *     <td>client.name, api.name, http.method, uri</td>
 *   </tr>
 * </table>
 *
 * <h3>Tag semantics</h3>
 * <ul>
 *   <li><b>client.name</b> – logical name from {@code @ReactiveHttpClient(name = ...)}.</li>
 *   <li><b>api.name</b> – logical API name from {@code @ApiName}, then {@code @ApiRef}, or Java method name by default.</li>
 *   <li><b>http.method</b> – uppercase HTTP verb (GET, POST, …).</li>
 *   <li><b>uri</b> – path template (e.g. {@code /users/{id}}) when
 *       {@code reactive.http.observability.include-url-path=true} (default); {@code NONE} otherwise.</li>
 *   <li><b>http.status_code</b> – numeric status (200, 404, …) or {@code NONE}
 *       when the response was never received.</li>
 *   <li><b>outcome</b> – one of SUCCESS, REDIRECTION, CLIENT_ERROR, SERVER_ERROR, UNKNOWN.</li>
 *   <li><b>exception</b> – simple class name of the error, or {@code none}.</li>
 *   <li><b>error.category</b> – one of {@code RATE_LIMITED}, {@code CLIENT_ERROR}, {@code SERVER_ERROR},
 *       {@code TIMEOUT}, {@code CANCELLED}, {@code AUTH_PROVIDER_ERROR},
 *       {@code RESPONSE_DECODE_ERROR}, {@code UNKNOWN},
 *       or {@code none} for successful calls.</li>
 * </ul>
 *
 * <p>This bean is auto-configured by
 * {@link io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientAutoConfiguration}
 * when {@code micrometer-core} is on the classpath and
 * {@code reactive.http.observability.enabled=true} (the default).
 *
 * <p>To override this bean, declare your own {@link HttpClientObserver} bean:
 * <pre>{@code
 * @Bean
 * public HttpClientObserver customObserver(MeterRegistry registry) {
 *     return event -> { // your logic };
 * }
 * }</pre>
 */
public class MicrometerHttpClientObserver implements HttpClientObserver {

    private static final Logger log = LoggerFactory.getLogger(MicrometerHttpClientObserver.class);

    private final MeterRegistry meterRegistry;
    private final ReactiveHttpClientProperties.ObservabilityConfig config;
    /** Pre-computed SLO boundaries for the latency histogram; {@code null} when histogram is disabled. */
    private final Duration[] sloBoundaries;
    /** Cache of histogram Timer instances keyed by low-cardinality tag set to avoid repeated builder allocation. */
    private final ConcurrentHashMap<Tags, Timer> histogramTimerCache = new ConcurrentHashMap<>();

    public MicrometerHttpClientObserver(MeterRegistry meterRegistry,
                                        ReactiveHttpClientProperties.ObservabilityConfig config) {
        this.meterRegistry = meterRegistry;
        this.config = config;
        this.sloBoundaries = resolveSloBoundaries(config);
    }

    private static Duration[] resolveSloBoundaries(ReactiveHttpClientProperties.ObservabilityConfig config) {
        if (!config.getHistogram().isEnabled()) {
            return null;
        }
        Duration[] boundaries = config.getHistogram().getSloBoundariesMs().stream()
                .filter(ms -> ms != null && ms > 0)
                .distinct()
                .sorted()
                .map(Duration::ofMillis)
                .toArray(Duration[]::new);
        return boundaries.length > 0 ? boundaries : null;
    }

    @Override
    public void record(HttpClientObserverEvent event) {
        if (!config.isEnabled()) {
            return;
        }
        try {
            Tags lowCardinalityTags = buildLowCardinalityTags(event);
            Tags tags = buildTags(event, lowCardinalityTags);
            // meterRegistry.timer() is idempotent – returns existing timer for the same
            // name+tags combination, avoiding repeated Timer.builder() allocation overhead.
            meterRegistry.timer(config.getMetricName(), tags)
                    .record(event.getDurationMs(), TimeUnit.MILLISECONDS);

            meterRegistry.summary(config.getMetricName() + ".attempts", lowCardinalityTags)
                    .record(event.getAttemptCount());

            if (event.getRequestBytes() >= 0) {
                meterRegistry.summary(config.getMetricName() + ".request.size", lowCardinalityTags)
                        .record(event.getRequestBytes());
            }
            if (event.getResponseBytes() >= 0) {
                meterRegistry.summary(config.getMetricName() + ".response.size", lowCardinalityTags)
                        .record(event.getResponseBytes());
            }

            if (sloBoundaries != null) {
                histogramTimerCache.computeIfAbsent(lowCardinalityTags, t ->
                        Timer.builder(config.getMetricName() + ".latency")
                                .tags(t)
                                .serviceLevelObjectives(sloBoundaries)
                                .register(meterRegistry)
                ).record(event.getDurationMs(), TimeUnit.MILLISECONDS);
            }

            if (log.isDebugEnabled()) {
                log.debug("[observability] {} {} {} -> {} ({}ms)",
                        event.getClientName(),
                        event.getHttpMethod(),
                        event.getUriPath(),
                        event.getStatusCode(),
                        event.getDurationMs());
            }
        } catch (Exception e) {
            // Never let observability failures propagate to business logic
            log.warn("Failed to record HTTP client metric: {}", e.getMessage());
        }
    }

    private Tags buildLowCardinalityTags(HttpClientObserverEvent event) {
        return commonTags(event);
    }

    private Tags buildTags(HttpClientObserverEvent event, Tags commonTags) {
        String statusCode = event.getStatusCode() != null
                ? String.valueOf(event.getStatusCode())
                : "NONE";

        String outcome = deriveOutcome(event);
        String exception = event.getError() != null
                ? event.getError().getClass().getSimpleName()
                : "none";
        String errorCategory = event.getErrorCategory() != null
                ? event.getErrorCategory().name()
                : "none";
        String failureStage = event.getFailureStage() != null
                ? event.getFailureStage().name()
                : "none";

        return commonTags.and(
                "http.status_code", statusCode,
                "outcome", outcome,
                "exception", exception,
                "error.category", errorCategory,
                "failure.stage", failureStage
        );
    }

    private Tags commonTags(HttpClientObserverEvent event) {
        String uri = config.isIncludeUrlPath() && event.getUriPath() != null
                ? event.getUriPath()
                : "NONE";
        Tags tags = Tags.of(
                "client.name", event.getClientName() != null ? event.getClientName() : "UNKNOWN",
                "api.name", event.getApiName() != null ? event.getApiName() : "UNKNOWN",
                "http.method", event.getHttpMethod() != null ? event.getHttpMethod() : "UNKNOWN",
                "uri", uri
        );
        if (!config.isIncludeServerAddress()) {
            return tags;
        }
        return tags.and(
                "server.address", event.getServerAddress() != null ? event.getServerAddress() : "UNKNOWN",
                "server.port", event.getServerPort() != null ? String.valueOf(event.getServerPort()) : "UNKNOWN"
        );
    }

    private String deriveOutcome(HttpClientObserverEvent event) {
        if (event.isError() && event.getStatusCode() == null) {
            return "UNKNOWN";
        }
        Integer code = event.getStatusCode();
        if (code == null) return "UNKNOWN";
        if (code >= 200 && code < 300) return "SUCCESS";
        if (code >= 300 && code < 400) return "REDIRECTION";
        if (code >= 400 && code < 500) return "CLIENT_ERROR";
        if (code >= 500) return "SERVER_ERROR";
        return "UNKNOWN";
    }
}
