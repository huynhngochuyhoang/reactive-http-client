package io.github.huynhngochuyhoang.httpstarter.nativesmoke;

import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.core.ProblemDetailErrorResponseMapper;
import io.github.huynhngochuyhoang.httpstarter.observability.ReactiveHttpClientDiagnosticsEndpoint;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientDiagnosticsProvider;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientDiagnosticsSnapshot;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientJsonCodec;
import io.github.huynhngochuyhoang.httpstarter.enable.EnableReactiveHttpClients;
import io.github.huynhngochuyhoang.httpstarter.exception.ProblemDetailRemoteServiceException;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@SpringBootApplication
@EnableReactiveHttpClients(basePackageClasses = NativeSmokeClient.class)
@RegisterReflectionForBinding(NativeOrderResponse.class)
public class NativeSmokeApplication {

    private static final String AUTH_TOKEN = "native-secret-token";

    public static void main(String[] args) {
        AtomicReference<String> observedAuth = new AtomicReference<>();
        DisposableServer server = loopbackServer(observedAuth);
        SpringApplication application = new SpringApplication(NativeSmokeApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of(
                "reactive.http.clients.native-smoke.base-url", "http://127.0.0.1:" + server.port(),
                "reactive.http.clients.native-smoke.auth-provider", "nativeAuthProvider",
                "reactive.http.clients.native-smoke.apis.native-problem.method", "GET",
                "reactive.http.clients.native-smoke.apis.native-problem.path", "/api/problem",
                "reactive.http.observability.diagnostics-endpoint.enabled", "true"));
        try (ConfigurableApplicationContext context = application.run(args)) {
            NativeSmokeClient client = context.getBean(NativeSmokeClient.class);
            NativeOrderResponse order = client.getOrder().block(Duration.ofSeconds(5));
            require(order != null && "ok".equals(order.code()) && "native".equals(order.message()),
                    "inherited generic response decoding failed");
            require(AUTH_TOKEN.equals(observedAuth.get()), "auth provider header did not reach loopback server");

            try {
                client.getProblem().block(Duration.ofSeconds(5));
                throw new IllegalStateException("Problem Detail response did not fail");
            } catch (ProblemDetailRemoteServiceException error) {
                require(error.getStatusCode() == 502, "Problem Detail status was not preserved");
                require("native problem".equals(error.getProblemDetail().getTitle()),
                        "Problem Detail payload was not decoded");
            }

            require(context.containsBean("reactiveHttpClientDiagnosticsEndpoint"),
                    "opt-in diagnostics endpoint was not registered");
            require(context.containsBean("reactiveHttpClientHealthIndicator"),
                    "Micrometer health indicator was not registered");
            Map<String, Object> diagnostics = context.getBean(ReactiveHttpClientDiagnosticsEndpoint.class).diagnostics();
            String snapshot = ReactiveHttpClientDiagnosticsSnapshot.toJson(
                    context.getBean(ReactiveHttpClientDiagnosticsProvider.class));
            require(((Number) diagnostics.get("clientCount")).intValue() == 1,
                    "diagnostics did not include the native client");
            require(!snapshot.contains(AUTH_TOKEN) && !snapshot.contains("127.0.0.1"),
                    "diagnostics snapshot exposed sensitive transport data");

            MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);
            require(meterRegistry.find("reactive.http.client.requests").timer() != null,
                    "Micrometer observer did not record the native calls");
        } finally {
            server.disposeNow(Duration.ofSeconds(5));
        }
    }

    @Bean
    AuthProvider nativeAuthProvider() {
        return request -> Mono.just(AuthContext.builder().header("X-Native-Auth", AUTH_TOKEN).build());
    }

    @Bean
    ProblemDetailErrorResponseMapper problemDetailErrorResponseMapper(ReactiveHttpClientJsonCodec codec) {
        return new ProblemDetailErrorResponseMapper(codec);
    }

    private static DisposableServer loopbackServer(AtomicReference<String> observedAuth) {
        return HttpServer.create()
                .port(0)
                .route(routes -> routes
                        .get("/api/order", (request, response) -> {
                            observedAuth.set(request.requestHeaders().get("X-Native-Auth"));
                            return response.header("Content-Type", "application/json")
                                    .sendString(Mono.just("{\"code\":\"ok\",\"message\":\"native\"}"))
                                    .then();
                        })
                        .get("/api/problem", (request, response) -> response.status(502)
                                .header("Content-Type", "application/problem+json")
                                .sendString(Mono.just("{\"status\":502,\"title\":\"native problem\",\"detail\":\"smoke\"}"))
                                .then()))
                .bindNow(Duration.ofSeconds(5));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
