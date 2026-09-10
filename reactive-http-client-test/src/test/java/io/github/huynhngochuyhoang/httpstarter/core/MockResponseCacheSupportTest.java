package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.CacheResponse;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.test.MockReactiveHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

class MockResponseCacheSupportTest {
    @Test
    void closureAfterWorkSnapshotCannotChangeItsCapturedState() {
        try (var mock = MockReactiveHttpClient.forClient(Client.class).clientConfig(config(true)).build()) {
            var control = (MockResponseCacheSupport.Control) ReflectionTestUtils.getField(mock, "cacheControl");
            var manager = spy((LocalResponseCacheManager) ReflectionTestUtils.getField(control, "manager"));
            ReflectionTestUtils.setField(control, "manager", manager);
            doAnswer(invocation -> {
                CacheWorkSnapshot captured = (CacheWorkSnapshot) invocation.callRealMethod();
                manager.close();
                return captured;
            }).when(manager).workSnapshot();

            var captured = mock.cacheWorkSnapshot();
            assertThat(captured.state()).isEqualTo("open");
            assertThat(captured.closed()).isFalse();
            assertThat(manager.snapshot().closed()).isTrue();
            var next = mock.cacheWorkSnapshot();
            assertThat(next.state()).isEqualTo("closed");
            assertThat(next.closed()).isTrue();
        }
    }

    @Test
    void absentWorkStillReportsManagerClosure() {
        try (var mock = MockReactiveHttpClient.forClient(Client.class).clientConfig(config(false)).build()) {
            assertThat(mock.cacheWorkSnapshot().state()).isEqualTo("absent");
            assertThat(mock.cacheWorkSnapshot().closed()).isFalse();
            mock.close();
            var snapshot = mock.cacheWorkSnapshot();
            assertThat(snapshot.state()).isEqualTo("absent");
            assertThat(snapshot.closed()).isTrue();
            assertThat(snapshot.activeCallers()).isNull();
        }
    }

    private static ReactiveHttpClientProperties.ClientConfig config(boolean limited) {
        var config = new ReactiveHttpClientProperties.ClientConfig();
        var policy = new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(60_000L);
        policy.setMaximumSize(10L);
        policy.setSharedResponse(true);
        if (limited) {
            var work = new ReactiveHttpClientProperties.CacheWorkConfig();
            work.setMaximumConcurrentCallers(2L);
            work.setMaximumConcurrentLoads(1L);
            policy.setWork(work);
        }
        config.getCache().getPolicies().put("work", policy);
        return config;
    }

    interface Client {
        @GET("/value") @CacheResponse("work") Mono<String> get();
    }
}
