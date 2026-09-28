package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.HeaderParam;
import io.github.huynhngochuyhoang.httpstarter.annotation.PathVar;
import io.github.huynhngochuyhoang.httpstarter.annotation.QueryParam;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;

/** Identical, valid 4.4.1/current workloads; no transport or response cache. */
@State(Scope.Thread)
public class V33PlanningCostBenchmark {
    private Method method;
    private MethodMetadata metadata;
    private Client client;
    private GenericApplicationContext context;
    private int exchanges;

    @Setup
    public void setup() throws Exception {
        method = Client.class.getMethod("read", String.class, String.class, String.class);
        metadata = new MethodMetadataCache().get(method);
        context = new GenericApplicationContext();
        context.refresh();
        var web = WebClient.builder().baseUrl("http://benchmark.invalid")
                .exchangeFunction(request -> {
                    require("GET".equals(request.method().name()), "method");
                    require("/items/a%2Fb?view=summary".equals(request.url().getRawPath()
                            + "?" + request.url().getRawQuery()), "target");
                    require("scope".equals(request.headers().getFirst("X-Scope")), "header");
                    exchanges++;
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", "text/plain").body("value").build());
                }).build();
        var handler = ReactiveClientInvocationHandler.create(web, new MethodMetadataCache(),
                new RequestArgumentResolver(), new DefaultErrorDecoder(),
                new ReactiveHttpClientProperties.ClientConfig(), "v33-cost", Client.class,
                context, new NoopResilienceOperatorApplier(), new Jackson3ReactiveHttpClientJsonCodec(new ObjectMapper()),
                new ReactiveHttpClientProperties.ObservabilityConfig());
        client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class}, handler);
        verifyPlan(metadataColdConcretePlan());
        verifyPlan(metadataColdParsingAndPlan());
        Mono<String> cold = proxyInvocationV33WarmPublisher();
        require(exchanges == 0, "publisher dispatched before subscription");
        require("value".equals(cold.block(Duration.ofSeconds(5))), "decoded value");
        require("value".equals(cold.block(Duration.ofSeconds(5))) && exchanges == 2, "cold resubscription");
        proxyInvocationV33WarmSubscription();
    }

    @Benchmark
    public RequestPlan metadataColdConcretePlan() {
        return RequestPlan.from(metadata, Client.class);
    }

    @Benchmark
    public RequestPlan metadataColdParsingAndPlan() {
        return RequestPlan.from(new MethodMetadataCache().get(method), Client.class);
    }

    @Benchmark
    public Mono<String> proxyInvocationV33WarmPublisher() {
        return client.read("a/b", "summary", "scope");
    }

    @Benchmark
    public String proxyInvocationV33WarmSubscription() {
        int before = exchanges;
        String value = proxyInvocationV33WarmPublisher().block(Duration.ofSeconds(5));
        require("value".equals(value) && exchanges == before + 1, "one exchange per subscribed call");
        return value;
    }

    @TearDown
    public void close() {
        context.close();
    }

    private void verifyPlan(RequestPlan plan) {
        require(method.equals(plan.method()) && "GET".equals(plan.httpMethod())
                && "/items/{id}".equals(plan.pathTemplate()) && plan.staticEffectiveApi() != null
                && !plan.returnsFlux() && plan.responseType() == String.class
                && plan.pathVars().size() == 1 && plan.queryParams().size() == 1
                && plan.headerParams().size() == 1, "plan shape");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    interface Client {
        @GET("/items/{id}")
        Mono<String> read(@PathVar("id") String id, @QueryParam("view") String view,
                          @HeaderParam("X-Scope") String scope);
    }
}
