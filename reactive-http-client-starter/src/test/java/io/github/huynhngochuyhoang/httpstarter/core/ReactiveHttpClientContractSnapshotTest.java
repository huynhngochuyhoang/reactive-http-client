package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReactiveHttpClientContractSnapshotTest {

    @Test
    void rendersDeterministicMarkdownForSharedApiRefContracts() {
        ReactiveHttpClientProperties.ClientConfig internal = clientConfig(
                "http://internal.example", 1000, "GET", "/internal-users/{id}");
        ReactiveHttpClientProperties.ClientConfig partner = clientConfig(
                "http://partner.example", 2000, "GET", "/partner-users/{id}");

        String snapshot = ReactiveHttpClientContractSnapshot.markdown()
                .client(PartnerUserClient.class, partner)
                .client(InternalUserClient.class, internal)
                .filterMethod("getUser")
                .render();

        assertThat(snapshot).isEqualTo("""
                | Client | Interface | Declared By | Inherited | Method | Generic Bindings | Response Type | Body Type | HTTP | Path | Base URL | Base URL Source | API Name | API Ref | Response Timeout | Logical-Call Budget | Resilience | Cache | Redirect | Auth | Body |
                |---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
                | internal-users | %s | %s | true | getUser(java.lang.String) | none | java.lang.String | none | GET | /internal-users/{id} | http://internal.example | property | users.get | users.get | client:1000ms | 0ms | retry=disabled, rateLimiter=disabled, circuitBreaker=disabled, bulkhead=disabled | disabled | manual | none | NONE |
                | partner-users | %s | %s | true | getUser(java.lang.String) | none | java.lang.String | none | GET | /partner-users/{id} | http://partner.example | property | users.get | users.get | client:2000ms | 0ms | retry=disabled, rateLimiter=disabled, circuitBreaker=disabled, bulkhead=disabled | disabled | manual | none | NONE |
                """.formatted(
                InternalUserClient.class.getName(), SharedUserOperations.class.getName(),
                PartnerUserClient.class.getName(), SharedUserOperations.class.getName()));
    }

    @Test
    void rendersResolvedGenericBindingsAndTypesForInheritedContracts() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setBaseUrl("https://generic.example");

        String snapshot = ReactiveHttpClientContractSnapshot.markdown()
                .client(GenericBusClient.class, config)
                .client(GenericTrainMisboundClient.class, config)
                .render();

        assertThat(snapshot)
                .contains("| generic-bus | " + GenericBusClient.class.getName()
                        + " | " + ApiOperators.class.getName() + " | true | getOrder() | T="
                        + BusResponse.class.getName() + " | " + BusResponse.class.getName()
                        + " | none | GET | /api/order |")
                .contains("| generic-bus | " + GenericBusClient.class.getName()
                        + " | " + ApiOperators.class.getName() + " | true | submit(T) | T="
                        + BusResponse.class.getName() + " | " + BusResponse.class.getName()
                        + " | " + BusResponse.class.getName() + " | POST | /api/order |")
                .contains("| generic-train | " + GenericTrainMisboundClient.class.getName()
                        + " | " + ApiOperators.class.getName() + " | true | getOrder() | T="
                        + BusResponse.class.getName() + " | " + BusResponse.class.getName()
                        + " | none | GET | /api/order |")
                .doesNotContain(TrainResponse.class.getName());
    }

    @Test
    void filtersByClientNameAndMethodName() {
        ReactiveHttpClientProperties.ClientConfig internal = clientConfig(
                "http://internal.example", 1000, "GET", "/internal-users/{id}");
        ReactiveHttpClientProperties.ClientConfig partner = clientConfig(
                "http://partner.example", 2000, "GET", "/partner-users/{id}");

        String snapshot = ReactiveHttpClientContractSnapshot.markdown()
                .client(InternalUserClient.class, internal)
                .client(PartnerUserClient.class, partner)
                .filterClient("partner-users")
                .filterMethod("getUser")
                .render();

        assertThat(snapshot)
                .contains("| partner-users |")
                .contains("| getUser(java.lang.String) |")
                .doesNotContain("| internal-users |")
                .doesNotContain("| listUsers() |");
    }

    @Test
    void canRenderAnnotatedDirectClientWithoutSpringContext() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setBaseUrl("http://direct.example");

        String snapshot = ReactiveHttpClientContractSnapshot.markdown()
                .client(DirectClient.class, config)
                .render();

        assertThat(snapshot)
                .contains("| direct-client |")
                .contains("| ping() | none | java.lang.String | none | GET | /ping | http://direct.example | property |");
    }

    @Test
    void rendersUrlOnlyAnnotatedClientWithBlankRuntimeName() {
        String snapshot = ReactiveHttpClientContractSnapshot.markdown()
                .client(UrlOnlyClient.class, new ReactiveHttpClientProperties.ClientConfig())
                .render();

        assertThat(snapshot)
                .contains("|  | " + UrlOnlyClient.class.getName())
                .contains("| ping() | none | java.lang.String | none | GET | /ping | https://url-only.example | annotation |");
    }

    @Test
    void methodFilterAvoidsValidatingUnselectedMethods() {
        String snapshot = ReactiveHttpClientContractSnapshot.markdown()
                .client(FocusedClient.class, "focused-client", new ReactiveHttpClientProperties.ClientConfig())
                .filterMethod("good")
                .render();

        assertThat(snapshot)
                .contains("| good() | none | java.lang.String | none | GET | /good |")
                .doesNotContain("bad()");
    }

    @Test
    void redactsCredentialsEmbeddedInBaseUrl() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setBaseUrl("https://user:token@example.com");

        String snapshot = ReactiveHttpClientContractSnapshot.markdown()
                .client(DirectClient.class, config)
                .render();

        assertThat(snapshot)
                .contains("https://REDACTED@example.com")
                .doesNotContain("user:token");
    }

    @Test
    void rendersNormalizedCacheIsolationPolicy() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.CachePolicyConfig policy =
                new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(5_000L);
        policy.setMaximumSize(25L);
        policy.setVaryByParameters(java.util.List.of(" tenant "));
        policy.setVaryByHeaders(java.util.List.of(" X-Tenant "));
        policy.setVaryByContext(java.util.List.of(" region ", "locale"));
        policy.setSharedResponse(true);
        config.getCache().setPolicy("selected");
        config.getCache().getPolicies().put("selected", policy);
        config.setDefaultHeaders(Map.of("X-Tenant", "public"));

        String snapshot = ReactiveHttpClientContractSnapshot.markdown()
                .client(CacheSnapshotClient.class, "cache-snapshot", config)
                .render();

        assertThat(snapshot).contains(
                "client:ttl=5000ms,max=25,varyParameters=[tenant],varyHeaders=[x-tenant],"
                        + "varyContext=[locale, region],sharedResponse=true");
    }

    private ReactiveHttpClientProperties.ClientConfig clientConfig(String baseUrl,
                                                                  long timeoutMs,
                                                                  String method,
                                                                  String path) {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setBaseUrl(baseUrl);
        config.setRequestTimeoutMs(timeoutMs);
        ReactiveHttpClientProperties.ApiConfig api = new ReactiveHttpClientProperties.ApiConfig();
        api.setMethod(method);
        api.setPath(path);
        config.setApis(Map.of("users.get", api));
        return config;
    }

    interface SharedUserOperations {

        @ApiRef("users.get")
        Mono<String> getUser(@PathVar("id") String id);

        @GET("/users")
        Mono<String> listUsers();
    }

    interface CacheSnapshotClient {
        @GET("/cache")
        Mono<String> get(@CacheKey("tenant") String tenant);
    }

    @ReactiveHttpClient(name = "internal-users")
    interface InternalUserClient extends SharedUserOperations {
    }

    @ReactiveHttpClient(name = "partner-users")
    interface PartnerUserClient extends SharedUserOperations {
    }

    interface ApiOperators<T extends BaseResponse> {

        @GET("/api/order")
        Mono<T> getOrder();

        @POST("/api/order")
        Mono<T> submit(@Body T body);
    }

    @ReactiveHttpClient(name = "generic-bus")
    interface GenericBusClient extends ApiOperators<BusResponse> {
    }

    @ReactiveHttpClient(name = "generic-train")
    interface GenericTrainMisboundClient extends ApiOperators<BusResponse> {
    }

    static class BaseResponse {
        String code;
    }

    static class BusResponse extends BaseResponse {
        String message;
    }

    static class TrainResponse extends BaseResponse {
        String bookingCode;
    }

    @ReactiveHttpClient(name = "direct-client")
    interface DirectClient {

        @GET("/ping")
        Mono<String> ping();
    }

    @ReactiveHttpClient(name = "", baseUrl = "https://url-only.example")
    interface UrlOnlyClient {

        @GET("/ping")
        Mono<String> ping();
    }

    interface FocusedClient {

        @GET("/good")
        Mono<String> good();

        @GET("/bad/{id}")
        Mono<String> bad();
    }
}
