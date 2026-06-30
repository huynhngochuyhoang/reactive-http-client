package io.github.huynhngochuyhoang.httpstarter.benchmarks;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.huynhngochuyhoang.httpstarter.benchmarks.client.BenchmarkUser;
import io.github.huynhngochuyhoang.httpstarter.benchmarks.client.StarterBenchmarkClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.core.DefaultErrorDecoder;
import io.github.huynhngochuyhoang.httpstarter.core.DefaultHttpExchangeLogger;
import io.github.huynhngochuyhoang.httpstarter.core.MethodMetadataCache;
import io.github.huynhngochuyhoang.httpstarter.core.NoopResilienceOperatorApplier;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveClientInvocationHandler;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientDiagnosticsProvider;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientFactoryBean;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientLifecycleContext;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientLifecycleHook;
import io.github.huynhngochuyhoang.httpstarter.core.RequestArgumentResolver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import io.github.huynhngochuyhoang.httpstarter.observability.MicrometerHttpClientObserver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@State(Scope.Benchmark)
public class StarterDiagnosticsOverheadBenchmark {

    private static final String CLIENT_NAME = "benchmark-starter";
    private static final BenchmarkUser CURRENT_USER = new BenchmarkUser("current", "current-user");

    private final List<GenericApplicationContext> contexts = new ArrayList<>();

    private StarterBenchmarkClient diagnosticsDisabledClient;
    private StarterBenchmarkClient exchangeLoggingClient;
    private StarterBenchmarkClient micrometerClient;
    private StarterBenchmarkClient oneObserverClient;
    private StarterBenchmarkClient multipleObserversClient;
    private StarterBenchmarkClient oneLifecycleHookClient;
    private StarterBenchmarkClient multipleLifecycleHooksClient;
    private ReactiveHttpClientDiagnosticsProvider diagnosticsProvider;

    @Setup
    public void setup() {
        diagnosticsDisabledClient = createClient(config -> {}, context -> {});
        exchangeLoggingClient = createClient(config -> {
            config.setLogExchange(true);
            config.setLogPreset(ReactiveHttpClientProperties.LogPreset.METADATA_ONLY);
        }, context -> context.registerBean(DefaultHttpExchangeLogger.class, DefaultHttpExchangeLogger::new));
        micrometerClient = createClient(config -> {}, context -> context.registerBean(HttpClientObserver.class, () ->
                new MicrometerHttpClientObserver(new SimpleMeterRegistry(), new ReactiveHttpClientProperties.ObservabilityConfig())));
        oneObserverClient = createClient(config -> {}, context ->
                context.registerBean("noopObserver", HttpClientObserver.class, NoopObserver::new));
        multipleObserversClient = createClient(config -> {}, context -> {
            context.registerBean("noopObserverOne", HttpClientObserver.class, NoopObserver::new);
            context.registerBean("noopObserverTwo", HttpClientObserver.class, NoopObserver::new);
            context.registerBean("noopObserverThree", HttpClientObserver.class, NoopObserver::new);
        });
        oneLifecycleHookClient = createClient(config -> {}, context ->
                context.registerBean("noopLifecycleHook", ReactiveHttpClientLifecycleHook.class, () -> new NoopLifecycleHook(0)));
        multipleLifecycleHooksClient = createClient(config -> {}, context -> {
            context.registerBean("noopLifecycleHookOne", ReactiveHttpClientLifecycleHook.class, () -> new NoopLifecycleHook(0));
            context.registerBean("noopLifecycleHookTwo", ReactiveHttpClientLifecycleHook.class, () -> new NoopLifecycleHook(1));
            context.registerBean("noopLifecycleHookThree", ReactiveHttpClientLifecycleHook.class, () -> new NoopLifecycleHook(2));
        });
        diagnosticsProvider = createDiagnosticsProvider();
    }

    @TearDown
    public void tearDown() {
        for (GenericApplicationContext context : contexts) {
            context.close();
        }
        contexts.clear();
    }

    @Benchmark
    public BenchmarkUser diagnosticsDisabledGetNoBody() {
        return validateUser(diagnosticsDisabledClient.currentUser().block());
    }

    @Benchmark
    public BenchmarkUser metadataOnlyExchangeLoggingGetNoBody() {
        return validateUser(exchangeLoggingClient.currentUser().block());
    }

    @Benchmark
    public BenchmarkUser micrometerObserverGetNoBody() {
        return validateUser(micrometerClient.currentUser().block());
    }

    @Benchmark
    public BenchmarkUser starterFeatureOneObserverGetNoBody() {
        return validateUser(oneObserverClient.currentUser().block());
    }

    @Benchmark
    public BenchmarkUser starterFeatureMultipleObserversGetNoBody() {
        return validateUser(multipleObserversClient.currentUser().block());
    }

    @Benchmark
    public BenchmarkUser starterFeatureOneLifecycleHookGetNoBody() {
        return validateUser(oneLifecycleHookClient.currentUser().block());
    }

    @Benchmark
    public BenchmarkUser starterFeatureMultipleLifecycleHooksGetNoBody() {
        return validateUser(multipleLifecycleHooksClient.currentUser().block());
    }

    @Benchmark
    public List<ReactiveHttpClientDiagnosticsProvider.ClientSummary> runtimeDiagnosticsProviderClientSummaries() {
        List<ReactiveHttpClientDiagnosticsProvider.ClientSummary> summaries = diagnosticsProvider.clientSummaries();
        if (summaries.size() != 1 || summaries.get(0).endpointCount() == 0) {
            throw new IllegalStateException("Diagnostics provider did not report the benchmark client");
        }
        return summaries;
    }

    private StarterBenchmarkClient createClient(ClientConfigCustomizer configCustomizer,
                                                ContextCustomizer contextCustomizer) {
        ReactiveHttpClientProperties.ClientConfig clientConfig = new ReactiveHttpClientProperties.ClientConfig();
        configCustomizer.customize(clientConfig);

        GenericApplicationContext context = new GenericApplicationContext();
        contextCustomizer.customize(context);
        context.refresh();
        contexts.add(context);

        ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                benchmarkWebClient(),
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                clientConfig,
                CLIENT_NAME,
                context,
                new NoopResilienceOperatorApplier(),
                new ObjectMapper(),
                new ReactiveHttpClientProperties.ObservabilityConfig());

        return (StarterBenchmarkClient) Proxy.newProxyInstance(
                StarterBenchmarkClient.class.getClassLoader(),
                new Class<?>[]{StarterBenchmarkClient.class},
                handler);
    }

    private ReactiveHttpClientDiagnosticsProvider createDiagnosticsProvider() {
        GenericApplicationContext context = new GenericApplicationContext();
        RootBeanDefinition definition = new RootBeanDefinition(ReactiveHttpClientFactoryBean.class);
        definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, StarterBenchmarkClient.class);
        context.registerBeanDefinition("benchmarkStarterClient", definition);
        context.refresh();
        contexts.add(context);

        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig clientConfig = new ReactiveHttpClientProperties.ClientConfig();
        clientConfig.setBaseUrl("http://benchmark.local");
        properties.setClients(Map.of(CLIENT_NAME, clientConfig));
        return new ReactiveHttpClientDiagnosticsProvider(context.getBeanFactory(), properties, new MethodMetadataCache());
    }

    private static WebClient benchmarkWebClient() {
        return WebClient.builder()
                .baseUrl("http://benchmark.local")
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .header(HttpHeaders.CONTENT_LENGTH, "38")
                        .body("{\"id\":\"current\",\"name\":\"current-user\"}")
                        .build()))
                .build();
    }

    private static BenchmarkUser validateUser(BenchmarkUser user) {
        if (!CURRENT_USER.equals(user)) {
            throw new IllegalStateException("Expected " + CURRENT_USER + " but got " + user);
        }
        return user;
    }

    private static final class NoopObserver implements HttpClientObserver {
        @Override
        public void record(HttpClientObserverEvent event) {
        }
    }

    private static final class NoopLifecycleHook implements ReactiveHttpClientLifecycleHook, Ordered {

        private final int order;

        private NoopLifecycleHook(int order) {
            this.order = order;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public void onStart(ReactiveHttpClientLifecycleContext context) {
        }

        @Override
        public void onSuccess(ReactiveHttpClientLifecycleContext context) {
        }
    }

    @FunctionalInterface
    private interface ClientConfigCustomizer {
        void customize(ReactiveHttpClientProperties.ClientConfig config);
    }

    @FunctionalInterface
    private interface ContextCustomizer {
        void customize(GenericApplicationContext context);
    }
}
