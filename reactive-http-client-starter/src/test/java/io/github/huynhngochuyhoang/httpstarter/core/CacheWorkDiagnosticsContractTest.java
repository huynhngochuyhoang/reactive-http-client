package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.CacheResponse;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CacheWorkDiagnosticsContractTest {
    @Test
    void lazyOwnersRemainUnknownAndMixedCountsDescribeOnlyLimitedPolicies() {
        var config = CacheWorkPolicyEnforcementTest.config(true);
        var unbounded = new ReactiveHttpClientProperties.CachePolicyConfig();
        unbounded.setTtlMs(1000L);
        unbounded.setMaximumSize(10L);
        unbounded.setSharedResponse(true);
        config.getCache().getPolicies().put("unbounded", unbounded);
        var beans = new DefaultListableBeanFactory();
        var provider = provider(beans, config);
        var row = client(provider);
        assertThat(row).containsEntry("cacheWorkSelection", "mixed")
                .containsEntry("cacheWorkState", "uninitialized")
                .containsEntry("cacheWorkLimitedPolicyCount", 1)
                .containsEntry("cacheWorkMaximumConcurrentCallers", 2L)
                .containsEntry("cacheWorkMaximumConcurrentLoads", 1L)
                .containsEntry("cacheWorkMaximumConcurrentRefreshes", 1L)
                .containsEntry("cacheWorkActiveCallers", null)
                .containsEntry("cacheWorkActiveLoads", null)
                .containsEntry("cacheWorkActiveRefreshes", null);
        assertThat(beans.containsSingleton("work")).isFalse();
        assertJsonParity(provider);
        assertThat(ReactiveHttpClientDiagnosticsSnapshot.toMarkdown(provider))
                .contains("limitedPolicyWork=", "cacheWorkSelection=mixed", "cacheWorkState=uninitialized");
        try (var manager = LocalResponseCacheManager.createForClient(Client.class, "work",
                new MethodMetadataCache(), config, getClass().getClassLoader())) {
            var factory = mock(ReactiveHttpClientFactoryBean.class);
            when(factory.getObjectType()).thenReturn(Client.class);
            when(factory.responseCacheSnapshot()).thenAnswer(ignored -> manager.snapshot());
            when(factory.responseCacheWorkSnapshot()).thenAnswer(ignored -> manager.workSnapshot());
            beans.registerSingleton("work", factory);
            var caller = manager.callerAdmission().acquire("work");
            assertThat(client(provider)).containsEntry("cacheWorkState", "open")
                    .containsEntry("cacheWorkActiveCallers", 1L).containsEntry("cacheWorkActiveLoads", 0L);
            manager.close();
            // Close prevents new admission, but an externally held reservation remains visible until release.
            assertThat(client(provider)).containsEntry("cacheWorkState", "closed")
                    .containsEntry("cacheWorkActiveCallers", 1L).containsEntry("cacheWorkMaximumConcurrentCallers", 2L);
            caller.complete();
            assertThat(client(provider)).containsEntry("cacheWorkActiveCallers", 0L);
            assertJsonParity(provider);
        }
    }

    @Test
    void summaryAndReplacementPathsKeepEveryWorkFactUnknown() {
        var config = CacheWorkPolicyEnforcementTest.config(true);
        config.getCache().getPolicies().put("unbounded", config.getCache().getPolicies().get("work"));
        var beans = new DefaultListableBeanFactory();
        var provider = provider(beans, config);
        var summary = provider.clientSummaries().getFirst();
        var collection = ReactiveHttpClientDiagnosticsSnapshot.toMap(List.of(summary));
        assertThat(firstClient(collection).entrySet().stream().filter(e -> e.getKey().startsWith("cacheWork")))
                .allSatisfy(e -> assertThat(e.getValue()).isNull());
        var replacement = new RootBeanDefinition(ForeignFactory.class);
        replacement.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, Client.class);
        beans.registerBeanDefinition("work", replacement);
        assertThat(client(provider).entrySet().stream().filter(e -> e.getKey().startsWith("cacheWork")))
                .allSatisfy(e -> assertThat(e.getValue()).isNull());
        assertThat(beans.containsSingleton("work")).isFalse();
    }

    @Test
    void unselectedWorkIsAbsentAndNonRefreshingPolicyDoesNotInventRefreshCounts() {
        var config = CacheWorkPolicyEnforcementTest.config(true);
        var policy = config.getCache().getPolicies().get("work");
        config.getCache().getPolicies().put("unbounded", policy);
        policy.setWork(null);
        var provider = provider(new DefaultListableBeanFactory(), config);
        assertThat(client(provider)).containsEntry("cacheWorkSelection", "absent")
                .containsEntry("cacheWorkState", "absent")
                .containsEntry("cacheWorkActiveCallers", null);
        policy.setWork(CacheWorkPolicyEnforcementTest.work());
        policy.setRefreshAfterMs(null);
        policy.setRefreshTimeoutMs(null);
        policy.getWork().setMaximumConcurrentRefreshes(null);
        assertThat(client(provider)).containsEntry("cacheWorkMaximumConcurrentCallers", 4L)
                .containsEntry("cacheWorkMaximumConcurrentRefreshes", null)
                .containsEntry("cacheWorkActiveRefreshes", null);
        assertJsonParity(provider);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 3, 17})
    void rejectsInvalidCountsOnEveryRenderingPath(int value) {
        var work = new CacheWorkSnapshot("selected", "open", value == 17 ? value : 1,
                2L, 1L, null, (long) value, 0L, null);
        var provider = withWork(work);
        assertThatThrownBy(() -> ReactiveHttpClientDiagnosticsSnapshot.toMap(provider))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReactiveHttpClientDiagnosticsSnapshot.toJson(provider))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReactiveHttpClientDiagnosticsSnapshot.toMarkdown(provider))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retainsTextListUtf16AndUtf8RenderingBounds() {
        var provider = withWork(new CacheWorkSnapshot("selected", "open", 1, 2L, 1L, null, 1L, 1L, null));
        var original = provider.clientSnapshotEntries().getFirst();
        var cache = original.cache();
        var tooMany = new ReactiveHttpClientDiagnosticsProvider.CacheSummary(cache.phase(), cache.policyCount(),
                cache.ttlMs(), cache.refreshAfterMs(), cache.singleFlight(), cache.maximumSize(),
                cache.maximumTotalDecodedResponseBytes(), cache.retainedDecodedResponseBytes(),
                cache.entryCount(), cache.evictions(), cache.metricsEnabled(),
                Collections.nCopies(17, "method"), cache.httpMethods(), cache.semanticReadAcknowledged(), cache.work());
        var invalid = entryProvider(new ReactiveHttpClientDiagnosticsProvider.ClientSnapshotEntry(original.summary(),
                false, false, original.pool(), tooMany, 0, false, 2));
        assertThatThrownBy(() -> ReactiveHttpClientDiagnosticsSnapshot.toMap(invalid))
                .hasMessageContaining("16 value limit");
        var summary = original.summary();
        String supplementary = new String(Character.toChars(0x1F600)).repeat(257);
        var oversized = new ReactiveHttpClientDiagnosticsProvider.ClientSummary(supplementary, summary.clientInterface(),
                summary.baseUrlSource(), summary.timeout(), summary.resilience(), summary.authMode(), false, 1, 0);
        assertThatThrownBy(() -> ReactiveHttpClientDiagnosticsSnapshot.toMap(List.of(oversized)))
                .hasMessageContaining("512 character");
        String text = "\u0800".repeat(512);
        var longSummary = new ReactiveHttpClientDiagnosticsProvider.ClientSummary(text, text, text,
                new ReactiveHttpClientDiagnosticsProvider.TimeoutSummary(text, 1),
                new ReactiveHttpClientDiagnosticsProvider.ResilienceSummary(true, text, text, text, text),
                text, false, 1, 0);
        var large = Collections.nCopies(256, longSummary);
        assertThatThrownBy(() -> ReactiveHttpClientDiagnosticsSnapshot.toMap(large)).hasMessageContaining("byte limit");
        assertThatThrownBy(() -> ReactiveHttpClientDiagnosticsSnapshot.toJson(large)).hasMessageContaining("byte limit");
        assertThatThrownBy(() -> ReactiveHttpClientDiagnosticsSnapshot.toMarkdown(large)).hasMessageContaining("byte limit");
    }

    private static ReactiveHttpClientDiagnosticsProvider withWork(CacheWorkSnapshot work) {
        var config = CacheWorkPolicyEnforcementTest.config(true);
        config.getCache().getPolicies().put("unbounded", config.getCache().getPolicies().get("work"));
        var original = provider(new DefaultListableBeanFactory(), config).clientSnapshotEntries().getFirst();
        var c = original.cache();
        var cache = new ReactiveHttpClientDiagnosticsProvider.CacheSummary(c.phase(), c.policyCount(), c.ttlMs(),
                c.refreshAfterMs(), c.singleFlight(), c.maximumSize(), c.maximumTotalDecodedResponseBytes(),
                c.retainedDecodedResponseBytes(), c.entryCount(), c.evictions(), c.metricsEnabled(),
                c.policySources(), c.httpMethods(), c.semanticReadAcknowledged(), work);
        return entryProvider(new ReactiveHttpClientDiagnosticsProvider.ClientSnapshotEntry(original.summary(),
                false, false, original.pool(), cache, 0, false, 2));
    }

    private static ReactiveHttpClientDiagnosticsProvider entryProvider(
            ReactiveHttpClientDiagnosticsProvider.ClientSnapshotEntry entry) {
        return new ReactiveHttpClientDiagnosticsProvider(new DefaultListableBeanFactory(),
                new ReactiveHttpClientProperties(), new MethodMetadataCache()) {
            @Override List<ClientSnapshotEntry> clientSnapshotEntries() { return List.of(entry); }
        };
    }

    private static ReactiveHttpClientDiagnosticsProvider provider(DefaultListableBeanFactory beans,
                                                                  ReactiveHttpClientProperties.ClientConfig config) {
        var definition = new RootBeanDefinition(ReactiveHttpClientFactoryBean.class);
        definition.setLazyInit(true);
        definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, Client.class);
        definition.getPropertyValues().add("type", Client.class);
        beans.registerBeanDefinition("work", definition);
        var properties = new ReactiveHttpClientProperties();
        properties.getClients().put("work", config);
        return new ReactiveHttpClientDiagnosticsProvider(beans, properties, new MethodMetadataCache());
    }

    private static void assertJsonParity(ReactiveHttpClientDiagnosticsProvider provider) {
        var mapper = new ObjectMapper();
        assertThat(mapper.readTree(ReactiveHttpClientDiagnosticsSnapshot.toJson(provider)).toString())
                .isEqualTo(mapper.valueToTree(ReactiveHttpClientDiagnosticsSnapshot.toMap(provider)).toString());
    }

    private static Map<String, Object> client(ReactiveHttpClientDiagnosticsProvider provider) {
        return firstClient(ReactiveHttpClientDiagnosticsSnapshot.toMap(provider));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstClient(Map<String, Object> map) {
        return ((List<Map<String, Object>>) map.get("clients")).getFirst();
    }

    @ReactiveHttpClient(name = "work")
    interface Client {
        @GET("/limited") Mono<String> limited();
        @GET("/other") @CacheResponse("unbounded") Mono<String> other();
    }

    static class ForeignFactory implements FactoryBean<Client> {
        @Override public Client getObject() { throw new AssertionError("must not initialize"); }
        @Override public Class<?> getObjectType() { return Client.class; }
    }
}
