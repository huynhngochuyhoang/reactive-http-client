package io.github.huynhngochuyhoang.httpstarter.observability;

import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientDiagnosticsProvider;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientDiagnosticsSnapshot;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.Map;

/**
 * Opt-in Actuator endpoint for sanitized reactive HTTP client diagnostics.
 */
@Endpoint(id = "rhttpclients")
public class ReactiveHttpClientDiagnosticsEndpoint {

    private final ReactiveHttpClientDiagnosticsProvider diagnosticsProvider;

    public ReactiveHttpClientDiagnosticsEndpoint(ReactiveHttpClientDiagnosticsProvider diagnosticsProvider) {
        this.diagnosticsProvider = diagnosticsProvider;
    }

    @ReadOperation
    public Map<String, Object> diagnostics() {
        return ReactiveHttpClientDiagnosticsSnapshot.toMap(diagnosticsProvider);
    }
}
