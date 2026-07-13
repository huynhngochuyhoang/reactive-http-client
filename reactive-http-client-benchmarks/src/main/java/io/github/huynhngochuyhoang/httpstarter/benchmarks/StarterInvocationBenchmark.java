package io.github.huynhngochuyhoang.httpstarter.benchmarks;

import io.github.huynhngochuyhoang.httpstarter.benchmarks.client.BenchmarkUser;
import io.github.huynhngochuyhoang.httpstarter.benchmarks.client.StarterBenchmarkClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.core.DefaultErrorDecoder;
import io.github.huynhngochuyhoang.httpstarter.core.MethodMetadata;
import io.github.huynhngochuyhoang.httpstarter.core.MethodMetadataCache;
import io.github.huynhngochuyhoang.httpstarter.core.NoopResilienceOperatorApplier;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveClientInvocationHandler;
import io.github.huynhngochuyhoang.httpstarter.core.RequestArgumentResolver;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

@State(Scope.Benchmark)
public class StarterInvocationBenchmark {

    private MethodMetadataCache metadataCache;
    private Method findUserMethod;
    private StarterBenchmarkClient starterClient;
    private GenericApplicationContext applicationContext;

    @Setup
    public void setup() throws NoSuchMethodException {
        metadataCache = new MethodMetadataCache();
        findUserMethod = StarterBenchmarkClient.class.getMethod(
                "findUser", String.class, String.class, String.class);
        applicationContext = new GenericApplicationContext();
        applicationContext.refresh();

        WebClient webClient = WebClient.builder()
                .baseUrl("http://benchmark.local")
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"id\":\"42\",\"name\":\"benchmark-user\"}")
                        .build()))
                .build();

        ReactiveClientInvocationHandler handler = ReactiveClientInvocationHandler.create(
                webClient,
                metadataCache,
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                new ReactiveHttpClientProperties.ClientConfig(),
                "benchmark-starter",
                StarterBenchmarkClient.class,
                applicationContext,
                new NoopResilienceOperatorApplier(),
                new BenchmarkJsonCodecFactory().create(),
                new ReactiveHttpClientProperties.ObservabilityConfig());

        starterClient = (StarterBenchmarkClient) Proxy.newProxyInstance(
                StarterBenchmarkClient.class.getClassLoader(),
                new Class<?>[]{StarterBenchmarkClient.class},
                handler);
    }

    @TearDown
    public void tearDown() {
        applicationContext.close();
    }

    @Benchmark
    public MethodMetadata metadataLookup() {
        return metadataCache.get(findUserMethod);
    }

    @Benchmark
    public Mono<BenchmarkUser> proxyInvocationCreatesPublisher() {
        return starterClient.findUser("42", "summary", "benchmark");
    }

    @Benchmark
    public BenchmarkUser proxyInvocationWithMockExchange() {
        return starterClient.findUser("42", "summary", "benchmark").block();
    }
}
