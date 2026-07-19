package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        assertThat(contract.authMode()).isEqualTo("none");
        assertThat(contract.bodyRepeatability()).isEqualTo(RequestBodyRepeatability.REPEATABLE);
    }

    @Test
    void requestPlanExportDiagnosticsAndSnapshotShareEffectivePolicy() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setBaseUrl("https://contract.example");
        config.setFollowRedirects(true);
        config.setAuthProvider("namedAuthProvider");
        config.getResilience().setEnabled(true);
        config.getResilience().setRetryMethods(Set.of("POST"));
        config.getResilience().setRetry("client-retry");

        MethodMetadata metadata = metadataCache.get(DirectClient.class.getMethod("create", String.class));
        RequestPlan plan = RequestPlan.from(metadata, DirectClient.class);
        EffectiveHttpClientContract contract = onlyContract(DirectClient.class, config);
        ReactiveHttpClientDiagnosticsProvider.ClientSummary summary =
                ReactiveHttpClientDiagnosticsProvider.clientSummary(
                        DirectClient.class, "diagnostic-client", config, metadataCache, null);
        String snapshot = ReactiveHttpClientContractSnapshot.markdown()
                .client(DirectClient.class, "diagnostic-client", config)
                .render();

        assertThat(contract.httpMethod()).isEqualTo(plan.httpMethod());
        assertThat(contract.pathTemplate()).isEqualTo(plan.pathTemplate());
        assertThat(contract.responseType()).isEqualTo(EffectiveHttpClientContractExporter.typeName(plan.responseType()));
        assertThat(contract.bodyType()).isEqualTo(EffectiveHttpClientContractExporter.typeName(plan.bodyType()));
        assertThat(contract.authMode()).isEqualTo(summary.authMode()).isEqualTo("provider-bean");
        assertThat(contract.redirectPolicy()).isEqualTo(summary.followRedirects() ? "follow" : "manual");
        assertThat(contract.timeout().source()).isEqualTo(summary.timeout().source());
        assertThat(contract.timeout().timeoutMs()).isEqualTo(summary.timeout().timeoutMs());
        assertThat(snapshot)
                .contains("| Auth |")
                .contains("| follow | provider-bean | REPEATABLE |");
    }

    @Test
    void exportsApplicationOwnedForUncertainBodyDeclarations() {
        List<EffectiveHttpClientContract> contracts = EffectiveHttpClientContractExporter.export(
                UncertainBodyClient.class, "diagnostic-client",
                new ReactiveHttpClientProperties.ClientConfig(), metadataCache);
        String snapshot = ReactiveHttpClientContractSnapshot.markdown()
                .client(UncertainBodyClient.class, "diagnostic-client",
                        new ReactiveHttpClientProperties.ClientConfig())
                .render();

        assertThat(contracts).hasSize(2)
                .allSatisfy(contract -> assertThat(contract.bodyRepeatability())
                        .isEqualTo(RequestBodyRepeatability.APPLICATION_OWNED));
        assertThat(snapshot).contains("java.lang.Object", "java.io.InputStream", "APPLICATION_OWNED");
    }

    @Test
    void identifiesInheritedMethodOnConcreteClient() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setRequestTimeoutMs(500L);

        EffectiveHttpClientContract contract = onlyContract(ChildClient.class, config);

        assertThat(contract.concreteClientInterface()).isEqualTo(ChildClient.class.getName());
        assertThat(contract.declaringInterface()).isEqualTo(ParentClient.class.getName());
        assertThat(contract.inherited()).isTrue();
        assertThat(contract.genericBindings()).isEqualTo("none");
        assertThat(contract.responseType()).isEqualTo(String.class.getName());
        assertThat(contract.bodyType()).isEqualTo("none");
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
    void exportsResolvedGenericTypesForInheritedContracts() {
        List<EffectiveHttpClientContract> contracts = EffectiveHttpClientContractExporter.export(
                GenericBusClient.class, "generic-bus", new ReactiveHttpClientProperties.ClientConfig(), metadataCache);

        EffectiveHttpClientContract get = contracts.stream()
                .filter(contract -> contract.javaMethodSignature().startsWith("getOrder"))
                .findFirst()
                .orElseThrow();
        EffectiveHttpClientContract submit = contracts.stream()
                .filter(contract -> contract.javaMethodSignature().startsWith("submit"))
                .findFirst()
                .orElseThrow();

        assertThat(get.declaringInterface()).isEqualTo(GenericOperations.class.getName());
        assertThat(get.concreteClientInterface()).isEqualTo(GenericBusClient.class.getName());
        assertThat(get.inherited()).isTrue();
        assertThat(get.genericBindings()).isEqualTo("T=" + GenericBusResponse.class.getName());
        assertThat(get.responseType()).isEqualTo(GenericBusResponse.class.getName());
        assertThat(get.bodyType()).isEqualTo("none");
        assertThat(submit.genericBindings()).isEqualTo("T=" + GenericBusResponse.class.getName());
        assertThat(submit.responseType()).isEqualTo(GenericBusResponse.class.getName());
        assertThat(submit.bodyType()).isEqualTo(GenericBusResponse.class.getName());
    }

    @Test
    void exportsNestedResolvedGenericBindingsForInheritedContracts() {
        EffectiveHttpClientContract contract = onlyContract(ConcreteWrappedClient.class,
                new ReactiveHttpClientProperties.ClientConfig());

        assertThat(contract.declaringInterface()).isEqualTo(WrappedOperations.class.getName());
        assertThat(contract.concreteClientInterface()).isEqualTo(ConcreteWrappedClient.class.getName());
        assertThat(contract.genericBindings()).isEqualTo("T=java.util.List<java.lang.String>");
        assertThat(contract.responseType()).isEqualTo("java.util.List<java.lang.String>");
        assertThat(contract.bodyType()).isEqualTo("none");
    }

    @Test
    void exportsMisboundGenericClientAsDeclaredJavaContract() {
        EffectiveHttpClientContract contract = EffectiveHttpClientContractExporter.export(
                        GenericTrainMisboundClient.class, "generic-train",
                        new ReactiveHttpClientProperties.ClientConfig(), metadataCache)
                .stream()
                .filter(candidate -> candidate.javaMethodSignature().startsWith("getOrder"))
                .findFirst()
                .orElseThrow();

        assertThat(contract.concreteClientInterface()).isEqualTo(GenericTrainMisboundClient.class.getName());
        assertThat(contract.declaringInterface()).isEqualTo(GenericOperations.class.getName());
        assertThat(contract.genericBindings()).isEqualTo("T=" + GenericBusResponse.class.getName());
        assertThat(contract.responseType()).isEqualTo(GenericBusResponse.class.getName());
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
    void exportsDeprecatedTimeoutWhenItIsTheOnlyConfiguredSource() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.getResilience().setTimeoutMs(900);

        EffectiveHttpClientContract contract = onlyContract(NoTimeoutClient.class, config);

        assertThat(contract.timeout())
                .isEqualTo(new EffectiveHttpClientContract.TimeoutPolicy("deprecated-resilience", 900));
    }

    @Test
    void reportsConfiguredObjectAuthWithoutExportingCredentials() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.AuthConfig auth = new ReactiveHttpClientProperties.AuthConfig();
        auth.setType("oauth2-client-credentials");
        auth.getOauth2ClientCredentials().setClientSecret("secret");
        config.setAuth(auth);

        EffectiveHttpClientContract contract = onlyContract(NoTimeoutClient.class, config);

        assertThat(contract.authMode()).isEqualTo("oauth2-client-credentials");
        assertThat(contract.toString()).doesNotContain("secret");
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
    void reportsResilienceUnavailableWhenOperatorsAreUnavailable() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.ResilienceConfig resilience = config.getResilience();
        resilience.setEnabled(true);
        resilience.setRetry("client-retry");
        resilience.setRetryMethods(Set.of("POST"));
        resilience.setRateLimiter("client-rate-limiter");
        resilience.setCircuitBreaker("client-circuit-breaker");
        resilience.setBulkhead("client-bulkhead");

        EffectiveHttpClientContract contract = onlyContract(DirectClient.class, config,
                new NoopResilienceOperatorApplier());

        assertThat(contract.resilience()).isEqualTo(new EffectiveHttpClientContract.ResiliencePolicy(
                "unavailable", "unavailable", "unavailable", "unavailable"));
    }

    @Test
    void failsExportWhenMethodLevelResilienceInstanceIsMissing() {
        ResilienceOperatorApplier applier = mock(ResilienceOperatorApplier.class);
        when(applier.isInstanceConfigured(ResilienceOperatorApplier.InstanceType.RETRY, "method-retry"))
                .thenReturn(false);

        ReactiveHttpClientProperties.ClientConfig config = enabledResilienceConfig();
        assertThatThrownBy(() -> EffectiveHttpClientContractExporter.export(
                DirectClient.class, "diagnostic-client", config, metadataCache, applier))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("undefined Resilience4j instance")
                .hasMessageContaining("(\"method-retry\")")
                .hasMessageContaining("DirectClient#create");
        assertThatThrownBy(() -> ReactiveHttpClientContractSnapshot.markdown()
                .client(DirectClient.class, "diagnostic-client", config)
                .resilienceOperatorApplier(applier)
                .render())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("undefined Resilience4j instance");
    }

    @Test
    void exportDoesNotIncludeConfiguredSecretValues() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setBaseUrl("https://user:token@example.com");
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

        EffectiveHttpClientContract contract = onlyContract(DirectClient.class, config);
        String exported = contract.toString();

        assertThat(contract.baseUrl()).isEqualTo("https://REDACTED@example.com");
        assertThat(exported)
                .doesNotContain("user:token")
                .doesNotContain("secret-token")
                .doesNotContain("tenant-a")
                .doesNotContain("secretAuthProviderBean")
                .doesNotContain("oauth-secret")
                .doesNotContain("proxy.internal")
                .doesNotContain("proxy-user")
                .doesNotContain("proxy-secret");
    }

    @Test
    void failsDirectPathTemplateWithoutMatchingPathVar() {
        assertThatThrownBy(() -> EffectiveHttpClientContractExporter.export(
                DirectMissingPathVarClient.class, "diagnostic-client",
                new ReactiveHttpClientProperties.ClientConfig(), metadataCache))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("URI template variables [id]")
                .hasMessageContaining("without matching @PathVar parameters");
    }

    @Test
    void failsApiRefPathTemplateWithoutMatchingPathVar() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.ApiConfig api = new ReactiveHttpClientProperties.ApiConfig();
        api.setMethod("GET");
        api.setPath("/configured/{id}");
        config.setApis(Map.of("item.lookup", api));

        assertThatThrownBy(() -> EffectiveHttpClientContractExporter.export(
                ApiRefWithoutPathVarClient.class, "diagnostic-client", config, metadataCache))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@ApiRef(\"item.lookup\")")
                .hasMessageContaining("URI template variables [id]")
                .hasMessageContaining("without matching @PathVar parameters");
    }

    private static ReactiveHttpClientProperties.ClientConfig enabledResilienceConfig() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.getResilience().setEnabled(true);
        return config;
    }

    private EffectiveHttpClientContract onlyContract(Class<?> clientInterface,
                                                     ReactiveHttpClientProperties.ClientConfig config) {
        return onlyContract(clientInterface, config, null);
    }

    private EffectiveHttpClientContract onlyContract(Class<?> clientInterface,
                                                     ReactiveHttpClientProperties.ClientConfig config,
                                                     ResilienceOperatorApplier resilienceOperatorApplier) {
        List<EffectiveHttpClientContract> contracts = EffectiveHttpClientContractExporter.export(
                clientInterface, "diagnostic-client", config, metadataCache, resilienceOperatorApplier);
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

    interface UncertainBodyClient {
        @POST("/object")
        Mono<String> objectBody(@Body Object body);

        @POST("/stream")
        Mono<String> streamBody(@Body InputStream body);
    }

    interface ParentClient {

        @GET("/shared/{id}")
        Mono<String> get(@PathVar("id") String id);
    }

    interface ChildClient extends ParentClient {
    }

    interface WrappedOperations<T> {

        @GET("/wrapped")
        Mono<T> getWrapped();
    }

    interface StringListOperations<R> extends WrappedOperations<List<R>> {
    }

    interface ConcreteWrappedClient extends StringListOperations<String> {
    }

    interface GenericOperations<T extends GenericBaseResponse> {

        @GET("/api/order")
        Mono<T> getOrder();

        @POST("/api/order")
        Mono<T> submit(@Body T body);
    }

    interface GenericBusClient extends GenericOperations<GenericBusResponse> {
    }

    interface GenericTrainMisboundClient extends GenericOperations<GenericBusResponse> {
    }

    static class GenericBaseResponse {
        String code;
    }

    static class GenericBusResponse extends GenericBaseResponse {
        String message;
    }

    interface NoTimeoutClient {

        @GET("/health")
        Mono<String> health();
    }

    interface PostWithoutRetryMethodClient {

        @POST("/items")
        Mono<String> create();
    }

    interface DirectMissingPathVarClient {

        @GET("/items/{id}")
        Mono<String> get();
    }

    interface ApiRefWithoutPathVarClient {

        @ApiRef("item.lookup")
        Mono<String> lookup();
    }

    interface ApiRefClient {

        @ApiRef("item.update")
        Mono<String> update(@PathVar("id") String id);
    }
}
