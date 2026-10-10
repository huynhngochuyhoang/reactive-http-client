package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.LogHttpExchange;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterfaceLoggingPlanningContractTest {
    private static final Duration WAIT = Duration.ofSeconds(5);

    @LogHttpExchange(logger = ParentLogger.class)
    interface Parent { @GET("/value") Mono<String> value(); }
    @LogHttpExchange(logger = ChildLogger.class)
    interface Child extends Parent {
        @GET("/override") @LogHttpExchange(logger = MethodLogger.class) Mono<String> override();
    }
    @LogHttpExchange(logger = SiblingLogger.class)
    interface Sibling extends Parent {}
    interface Plain extends Parent {}
    interface Bare { @GET("/value") Mono<String> value(); }
    @LogHttpExchange(logger = NeedsBeanLogger.class)
    interface NeedsBean extends Bare {}
    static class Direct implements Child {
        public Mono<String> value() { return Mono.empty(); }
        public Mono<String> override() { return Mono.empty(); }
    }

    enum Shape { SINGLE, LEGACY, NULL_PROXY, NON_PROXY, DIFFERENT, MULTIPLE, DECLARING_FALLBACK }

    @ParameterizedTest
    @EnumSource(Shape.class)
    void warmedConcreteAnnotationNeverLeaksIntoFallbackPaths(Shape shape) throws Throwable {
        try (var fixture = new Fixture(Child.class, shape == Shape.LEGACY)) {
            Method method = Parent.class.getMethod("value");
            fixture.call(proxy(Child.class), method);
            Object target = switch (shape) {
                case SINGLE, LEGACY -> proxy(Child.class);
                case NULL_PROXY -> null;
                case NON_PROXY -> new Direct();
                case DIFFERENT -> proxy(Sibling.class);
                case MULTIPLE -> proxy(Sibling.class, Child.class);
                case DECLARING_FALLBACK -> proxy(Plain.class);
            };
            fixture.call(target, method);
            fixture.call(proxy(Child.class), method);
            String expected = switch (shape) {
                case NULL_PROXY, DECLARING_FALLBACK -> "parent";
                case DIFFERENT, MULTIPLE -> "sibling";
                default -> "child";
            };
            assertThat(fixture.logs).containsExactly("child", expected, "child");
            assertThat(fixture.paths).containsExactly("/value", "/value", "/value");
            assertThat(fixture.metadata.get(method).getResolvedExchangeLogger()).isNull();
        }
    }

    @Test
    void concreteAnnotationAbsenceStillUsesTheDeclaringInterface() throws Throwable {
        try (var fixture = new Fixture(Plain.class, false)) {
            Method method = Parent.class.getMethod("value");
            fixture.call(proxy(Plain.class), method);
            fixture.call(proxy(Plain.class), method);
            assertThat(fixture.logs).containsExactly("parent", "parent");
            assertThat(fixture.metadata.get(method).getResolvedExchangeLogger()).isNull();
        }
    }

    @Test
    void freshMethodMetadataOverridesAnAlreadyWarmInterfaceDecision() throws Throwable {
        var selected = new AtomicReference<Class<? extends HttpExchangeLogger>>();
        var metadata = new MethodMetadataCache() {
            @Override public MethodMetadata get(Method method) {
                var fresh = new MethodMetadataCache().get(method);
                if (selected.get() != null) {
                    fresh.setHttpExchangeLoggingEnabled(true);
                    fresh.setHttpExchangeLoggerClass(selected.get());
                }
                return fresh;
            }
        };
        try (var fixture = new Fixture(Child.class, false, metadata)) {
            Method method = Parent.class.getMethod("value");
            fixture.call(proxy(Child.class), method);
            selected.set(MethodLogger.class);
            fixture.call(proxy(Child.class), method);
            selected.set(SiblingLogger.class);
            fixture.call(proxy(Child.class), method);
            selected.set(null);
            fixture.call(proxy(Child.class), method);
            assertThat(fixture.logs).containsExactly("child", "method", "sibling", "child");
            assertThat(fixture.paths).containsExactly("/value", "/value", "/value", "/value");
        }
    }

    @Test
    void methodPrecedenceSurvivesWarmInterfaceAndLiveClientConfiguration() throws Throwable {
        try (var fixture = new Fixture(Child.class, false)) {
            fixture.config.setExchangeLoggingEnabled(true);
            fixture.call(proxy(Child.class), Parent.class.getMethod("value"));
            fixture.call(proxy(Child.class), Child.class.getMethod("override"));
            fixture.config.setExchangeLoggingEnabled(false);
            fixture.call(proxy(Child.class), Parent.class.getMethod("value"));
            assertThat(fixture.logs).containsExactly("child", "method", "child");
            assertThat(fixture.paths).containsExactly("/value", "/override", "/value");
        }
    }

    @Test
    void knownAnnotationAbsenceDoesNotCacheClientLoggingSelectionOrCreateLoggerEarly() throws Throwable {
        try (var fixture = new Fixture(Bare.class, false)) {
            var creations = new AtomicInteger();
            fixture.context.registerBean(DefaultHttpExchangeLogger.class, () -> {
                creations.incrementAndGet();
                return new DefaultHttpExchangeLogger() {
                    @Override public void log(HttpExchangeLogContext context) { fixture.logs.add("client"); }
                };
            });
            Method method = Bare.class.getMethod("value");
            assertThat(creations).hasValue(0);
            fixture.call(proxy(Bare.class), method);
            assertThat(creations).hasValue(0);
            fixture.config.setExchangeLoggingEnabled(true);
            fixture.call(proxy(Bare.class), method);
            fixture.config.setExchangeLoggingEnabled(false);
            fixture.call(proxy(Bare.class), method);
            fixture.config.setExchangeLoggingEnabled(true);
            fixture.call(proxy(Bare.class), method);
            assertThat(creations).hasValue(1);
            assertThat(fixture.logs).containsExactly("client", "client");
            assertThat(fixture.paths).hasSize(4);
        }
    }

    @Test
    void failedLoggerConstructionDoesNotCacheFailureOrPreventLateBeanSelection() throws Throwable {
        try (var fixture = new Fixture(NeedsBean.class, false)) {
            Method method = Bare.class.getMethod("value");
            assertThatThrownBy(() -> fixture.call(proxy(NeedsBean.class), method))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("Cannot instantiate HttpExchangeLogger");
            assertThat(fixture.paths).isEmpty();
            fixture.context.registerBean(NeedsBeanLogger.class, () -> new NeedsBeanLogger(fixture.logs));
            fixture.call(proxy(NeedsBean.class), method);
            fixture.call(proxy(NeedsBean.class), method);
            assertThat(fixture.logs).containsExactly("late", "late");
            assertThat(fixture.paths).hasSize(2);
        }
    }

    @Test
    void simultaneousFirstInvocationsRemainIndependent() throws Exception {
        try (var fixture = new Fixture(Child.class, false); var executor = Executors.newFixedThreadPool(2)) {
            var start = new CyclicBarrier(2);
            var tasks = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 2; i++) {
                tasks.add(executor.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    try { fixture.call(proxy(Child.class), Parent.class.getMethod("value")); }
                    catch (Throwable failure) { throw new AssertionError(failure); }
                    return null;
                }));
            }
            for (var task : tasks) task.get(5, TimeUnit.SECONDS);
            assertThat(fixture.logs).containsExactly("child", "child");
            assertThat(fixture.paths).containsExactly("/value", "/value");
        }
    }

    @Test
    void dynamicProjectionCopiesContainersButDoesNotDeepFreezeOrdinaryArguments() {
        var metadata = new MethodMetadata();
        metadata.getPathVars().put(0, "id");
        metadata.getQueryParams().put(1, "q");
        metadata.getHeaderParams().put(2, "X-Scope");
        metadata.setBodyIndex(3);
        var path = new StringBuilder("before");
        var queryElement = new StringBuilder("first");
        var query = new ArrayList<>(List.of(queryElement));
        var headers = new ArrayList<>(List.of("one", "two"));
        byte[] body = {1};
        var resolved = new RequestArgumentResolver().resolve(metadata, new Object[]{path, query, headers, body});
        path.append("-after");
        queryElement.append("-after");
        query.clear();
        headers.clear();
        body[0] = 2;
        assertThat(resolved.pathVars().get("id")).isSameAs(path).hasToString("before-after");
        assertThat(resolved.queryParams().get("q")).containsExactly(queryElement);
        assertThat(resolved.headers().get("X-Scope")).containsExactly("one", "two");
        assertThat(resolved.body()).isSameAs(body);
        assertThatThrownBy(() -> resolved.headers().get("X-Scope").add("three"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void projectionRetainsQueryArrayOrderAndRejectsCaseAliasedHeaderSources() {
        var metadata = new MethodMetadata();
        metadata.getQueryParams().put(0, "q");
        metadata.getHeaderParams().put(1, "X-Scope");
        metadata.getHeaderMapParams().add(2);
        var resolver = new RequestArgumentResolver();
        var map = new LinkedHashMap<String, Object>();
        map.put("x-scope", "other");
        assertThatThrownBy(() -> resolver.resolve(metadata, new Object[]{new String[]{"z", "a", ""}, "one", map}))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("case-insensitively");
        map.clear();
        assertThat(resolver.resolve(metadata, new Object[]{new String[]{"z", "a", ""}, null, map}).queryParams().get("q"))
                .containsExactly("z", "a", "");
    }

    private static Object proxy(Class<?>... interfaces) {
        return Proxy.newProxyInstance(interfaces[0].getClassLoader(), interfaces, (p, m, a) -> null);
    }

    public static class ParentLogger implements HttpExchangeLogger {
        final List<String> logs;
        ParentLogger(List<String> logs) { this.logs = logs; }
        @Override public void log(HttpExchangeLogContext context) { logs.add("parent"); }
    }
    public static class ChildLogger extends ParentLogger {
        ChildLogger(List<String> logs) { super(logs); }
        @Override public void log(HttpExchangeLogContext context) { logs.add("child"); }
    }
    public static class SiblingLogger extends ParentLogger {
        SiblingLogger(List<String> logs) { super(logs); }
        @Override public void log(HttpExchangeLogContext context) { logs.add("sibling"); }
    }
    public static class MethodLogger extends ParentLogger {
        MethodLogger(List<String> logs) { super(logs); }
        @Override public void log(HttpExchangeLogContext context) { logs.add("method"); }
    }
    public static class NeedsBeanLogger extends ParentLogger {
        NeedsBeanLogger(List<String> logs) { super(logs); }
        @Override public void log(HttpExchangeLogContext context) { logs.add("late"); }
    }

    private static class Fixture implements AutoCloseable {
        final GenericApplicationContext context = new GenericApplicationContext();
        final ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        final MethodMetadataCache metadata;
        final List<String> logs = new CopyOnWriteArrayList<>();
        final List<String> paths = new CopyOnWriteArrayList<>();
        final ReactiveClientInvocationHandler handler;

        Fixture(Class<?> type, boolean legacy) {
            this(type, legacy, new MethodMetadataCache());
        }

        Fixture(Class<?> type, boolean legacy, MethodMetadataCache metadata) {
            this.metadata = metadata;
            context.registerBean(ParentLogger.class, () -> new ParentLogger(logs), definition -> definition.setPrimary(true));
            context.registerBean(ChildLogger.class, () -> new ChildLogger(logs));
            context.registerBean(SiblingLogger.class, () -> new SiblingLogger(logs));
            context.registerBean(MethodLogger.class, () -> new MethodLogger(logs));
            context.refresh();
            var web = WebClient.builder().baseUrl("http://planning.invalid").exchangeFunction(request -> {
                assertThat(request.method().name()).isEqualTo("GET");
                paths.add(request.url().getRawPath());
                return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "text/plain").body("value").build());
            }).build();
            handler = legacy ? new ReactiveClientInvocationHandler(web, metadata, new RequestArgumentResolver(),
                    new DefaultErrorDecoder(), config, "planning", context, new NoopResilienceOperatorApplier(),
                    TestJsonCodecs.jsonCodec(), new ReactiveHttpClientProperties.ObservabilityConfig())
                    : ReactiveClientInvocationHandler.create(web, metadata, new RequestArgumentResolver(), new DefaultErrorDecoder(),
                    config, "planning", type, context, new NoopResilienceOperatorApplier(), TestJsonCodecs.jsonCodec(),
                    new ReactiveHttpClientProperties.ObservabilityConfig());
        }

        void call(Object proxy, Method method) throws Throwable {
            assertThat(((Mono<?>) handler.invoke(proxy, method, new Object[0])).block(WAIT)).isEqualTo("value");
        }

        @Override public void close() {
            if (handler.responseCacheManager() != null) handler.responseCacheManager().close();
            context.close();
        }
    }
}
