package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EffectiveHttpClientContractExporterTest {

    private final MethodMetadataCache metadataCache = new MethodMetadataCache();

    @Test
    void exportsDirectMethodContract() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setFollowRedirects(true);
        ReactiveHttpClientProperties.ResilienceConfig resilience = config.getResilience();
        resilience.setEnabled(true);
        resilience.setRetry("client-retry");
        resilience.setRetryMethods(Set.of("POST"));
        resilience.setRateLimiter("client-rate-limiter");
        resilience.setCircuitBreaker("client-circuit-breaker");
        resilience.setBulkhead("client-bulkhead");

        EffectiveHttpClientContract contract = onlyContract(DirectClient.class, config);

        assertThat(contract.clientName()).isEqualTo("diagnostic-client");
        assertThat(contract.concreteClientInterface()).isEqualTo(DirectClient.class.getName());
        assertThat(contract.declaringInterface()).isEqualTo(DirectClient.class.getName());
        assertThat(contract.inherited()).isFalse();
        assertThat(contract.javaMethodSignature()).isEqualTo("create(java.lang.String)");
        assertThat(contract.httpMethod()).isEqualTo("POST");
        assertThat(contract.pathTemplate()).isEqualTo("/items");
        assertThat(contract.apiName()).isEqualTo("items.create");
        assertThat(contract.apiRef()).isNull();
        assertThat(contract.timeout()).isEqualTo(new EffectiveHttpClientContract.TimeoutPolicy("method", 250));
        assertThat(contract.resilience()).isEqualTo(new EffectiveHttpClientContract.ResiliencePolicy(
                "method-retry", "client-rate-limiter", "client-circuit-breaker", "client-bulkhead"));
        assertThat(contract.redirectPolicy()).isEqualTo("follow");
        assertThat(contract.bodyRepeatability()).isEqualTo(RequestBodyRepeatability.REPEATABLE);
    }

    @Test
    void identifiesInheritedMethodOnConcreteClient() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setRequestTimeoutMs(500L);

        EffectiveHttpClientContract contract = onlyContract(ChildClient.class, config);

        assertThat(contract.concreteClientInterface()).isEqualTo(ChildClient.class.getName());
        assertThat(contract.declaringInterface()).isEqualTo(ParentClient.class.getName());
        assertThat(contract.inherited()).isTrue();
        assertThat(contract.httpMethod()).isEqualTo("GET");
        assertThat(contract.pathTemplate()).isEqualTo("/shared/{id}");
        assertThat(contract.timeout()).isEqualTo(new EffectiveHttpClientContract.TimeoutPolicy("client", 500));
    }

    @Test
    void resolvesApiRefAgainstConcreteClientApiMap() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.ApiConfig api = new ReactiveHttpClientProperties.ApiConfig();
        api.setMethod("patch");
        api.setPath("/configured/{id}");
        api.setTimeoutMs(700);
        config.setApis(Map.of("item.update", api));

        EffectiveHttpClientContract contract = onlyContract(ApiRefClient.class, config);

        assertThat(contract.httpMethod()).isEqualTo("PATCH");
        assertThat(contract.pathTemplate()).isEqualTo("/configured/{id}");
        assertThat(contract.apiName()).isEqualTo("item.update");
        assertThat(contract.apiRef()).isEqualTo("item.update");
        assertThat(contract.timeout()).isEqualTo(new EffectiveHttpClientContract.TimeoutPolicy("api-ref", 700));
    }

    @Test
    void failsMissingApiRefInsteadOfExportingPlaceholder() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();

        assertThatThrownBy(() -> EffectiveHttpClientContractExporter.export(
                ApiRefClient.class, "diagnostic-client", config, metadataCache))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reactive.http.clients.diagnostic-client.apis[item.update]")
                .hasMessageContaining("is not configured");
    }

    @Test
    void exportsDisabledTimeoutAsRuntimeValue() {
        EffectiveHttpClientContract contract = onlyContract(NoTimeoutClient.class,
                new ReactiveHttpClientProperties.ClientConfig());

        assertThat(contract.timeout()).isEqualTo(new EffectiveHttpClientContract.TimeoutPolicy("disabled", 0));
    }

    @Test
    void reportsRetryDisabledForNonRetryableMethods() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.ResilienceConfig resilience = config.getResilience();
        resilience.setEnabled(true);
        resilience.setRetry("client-retry");

        EffectiveHttpClientContract contract = onlyContract(PostWithoutRetryMethodClient.class, config);

        assertThat(contract.resilience().retry()).isEqualTo("disabled");
        assertThat(contract.resilience().rateLimiter()).isEqualTo("default");
    }

    @Test
    void exportDoesNotIncludeConfiguredSecretValues() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setDefaultHeaders(Map.of("Authorization", "Bearer secret-token", "X-Tenant", "tenant-a"));
        config.setAuthProvider("secretAuthProviderBean");
        ReactiveHttpClientProperties.AuthConfig auth = new ReactiveHttpClientProperties.AuthConfig();
        auth.setType("oauth2-client-credentials");
        auth.getOauth2ClientCredentials().setClientSecret("oauth-secret");
        config.setAuth(auth);
        ReactiveHttpClientProperties.ProxyConfig proxy = new ReactiveHttpClientProperties.ProxyConfig();
        proxy.setHost("proxy.internal");
        proxy.setUsername("proxy-user");
        proxy.setPassword("proxy-secret");
        config.setProxy(proxy);

        String exported = onlyContract(DirectClient.class, config).toString();

        assertThat(exported)
                .doesNotContain("secret-token")
                .doesNotContain("tenant-a")
                .doesNotContain("secretAuthProviderBean")
                .doesNotContain("oauth-secret")
                .doesNotContain("proxy.internal")
                .doesNotContain("proxy-user")
                .doesNotContain("proxy-secret");
    }

    private EffectiveHttpClientContract onlyContract(Class<?> clientInterface,
                                                     ReactiveHttpClientProperties.ClientConfig config) {
        List<EffectiveHttpClientContract> contracts = EffectiveHttpClientContractExporter.export(
                clientInterface, "diagnostic-client", config, metadataCache);
        assertThat(contracts).hasSize(1);
        return contracts.get(0);
    }

    interface DirectClient {

        @POST("/items")
        @ApiName("items.create")
        @TimeoutMs(250)
        @Retry("method-retry")
        Mono<String> create(@Body String body);
    }

    interface ParentClient {

        @GET("/shared/{id}")
        Mono<String> get(@PathVar("id") String id);
    }

    interface ChildClient extends ParentClient {
    }

    interface NoTimeoutClient {

        @GET("/health")
        Mono<String> health();
    }

    interface PostWithoutRetryMethodClient {

        @POST("/items")
        Mono<String> create();
    }

    interface ApiRefClient {

        @ApiRef("item.update")
        Mono<String> update(@PathVar("id") String id);
    }
}
