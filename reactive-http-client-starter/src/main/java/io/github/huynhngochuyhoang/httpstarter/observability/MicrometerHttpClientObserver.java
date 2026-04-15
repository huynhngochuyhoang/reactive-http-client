package io.github.huynhngochuyhoang.httpstarter.observability;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * {@link HttpClientObserver} implementation that records Micrometer metrics for
 * every HTTP exchange performed by a {@code @ReactiveHttpClient} proxy.
 *
 * <h3>Metrics produced</h3>
 * <table border="1">
 *   <tr><th>Metric</th><th>Type</th><th>Tags</th></tr>
 *   <tr>
 *     <td>{@code http.client.requests} (configurable)</td>
 *     <td>Timer (also exposes count + sum)</td>
 *     <td>client.name, api.name, http.method, uri, http.status_code, outcome, exception</td>
 *   </tr>
 * </table>
 *
 * <h3>Tag semantics</h3>
 * <ul>
 *   <li><b>client.name</b> – logical name from {@code @ReactiveHttpClient(name = ...)}.</li>
 *   <li><b>api.name</b> – logical API name from {@code @ApiName} (or Java method name by default).</li>
 *   <li><b>http.method</b> – uppercase HTTP verb (GET, POST, …).</li>
 *   <li><b>uri</b> – path template (e.g. {@code /users/{id}}) when
 *       {@code reactive.http.observability.include-url-path=true} (default); {@code NONE} otherwise.</li>
 *   <li><b>http.status_code</b> – numeric status (200, 404, …) or {@code CLIENT_ERROR} / {@code SERVER_ERROR}
 *       when the response was never received.</li>
 *   <li><b>outcome</b> – one of SUCCESS, CLIENT_ERROR, SERVER_ERROR, UNKNOWN.</li>
 *   <li><b>exception</b> – simple class name of the error, or {@code none}.</li>
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

    public MicrometerHttpClientObserver(MeterRegistry meterRegistry,
                                        ReactiveHttpClientProperties.ObservabilityConfig config) {
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    @Override
    public void record(HttpClientObserverEvent event) {
        if (!config.isEnabled()) {
            return;
        }
        try {
            Tags tags = buildTags(event);
            // meterRegistry.timer() is idempotent – returns existing timer for the same
            // name+tags combination, avoiding repeated Timer.builder() allocation overhead.
            meterRegistry.timer(config.getMetricName(), tags)
                    .record(event.getDurationMs(), TimeUnit.MILLISECONDS);

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

    private Tags buildTags(HttpClientObserverEvent event) {
        String uri = config.isIncludeUrlPath() && event.getUriPath() != null
                ? event.getUriPath()
                : "NONE";

        String statusCode = event.getStatusCode() != null
                ? String.valueOf(event.getStatusCode())
                : (event.isError() ? "CLIENT_ERROR" : "UNKNOWN");

        String outcome = deriveOutcome(event);
        String exception = event.getError() != null
                ? event.getError().getClass().getSimpleName()
                : "none";

        return Tags.of(
                Tag.of("client.name", event.getClientName()),
                Tag.of("api.name", event.getApiName() != null ? event.getApiName() : "UNKNOWN"),
                Tag.of("http.method", event.getHttpMethod() != null ? event.getHttpMethod() : "UNKNOWN"),
                Tag.of("uri", uri),
                Tag.of("http.status_code", statusCode),
                Tag.of("outcome", outcome),
                Tag.of("exception", exception)
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
