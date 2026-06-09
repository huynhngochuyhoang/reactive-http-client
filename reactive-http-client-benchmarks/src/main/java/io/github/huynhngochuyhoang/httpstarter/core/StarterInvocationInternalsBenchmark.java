package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.HeaderParam;
import io.github.huynhngochuyhoang.httpstarter.annotation.PathVar;
import io.github.huynhngochuyhoang.httpstarter.annotation.QueryParam;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;

@State(Scope.Benchmark)
public class StarterInvocationInternalsBenchmark {

    private MethodMetadataCache metadataCache;
    private RequestArgumentResolver argumentResolver;
    private Method findUserMethod;
    private MethodMetadata findUserMetadata;
    private RequestPlan findUserPlan;
    private Object[] scalarArgs;

    @Setup
    public void setup() throws NoSuchMethodException {
        metadataCache = new MethodMetadataCache();
        argumentResolver = new RequestArgumentResolver();
        findUserMethod = InternalBenchmarkClient.class.getMethod(
                "findUser", String.class, String.class, String.class);
        findUserMetadata = metadataCache.get(findUserMethod);
        findUserPlan = findUserMetadata.getRequestPlan();
        scalarArgs = new Object[]{"42", "summary", "benchmark"};
    }

    @Benchmark
    public MethodMetadata cachedMethodMetadataLookup() {
        return metadataCache.get(findUserMethod);
    }

    @Benchmark
    public RequestPlan cachedRequestPlanLookup() {
        return metadataCache.get(findUserMethod).getRequestPlan();
    }

    @Benchmark
    public RequestArgumentResolver.ResolvedArgs argumentResolutionPathQueryHeaderFromMetadata() {
        return argumentResolver.resolve(findUserMetadata, scalarArgs);
    }

    @Benchmark
    public RequestArgumentResolver.ResolvedArgs argumentResolutionPathQueryHeaderFromPlan() {
        return argumentResolver.resolve(findUserPlan, scalarArgs);
    }

    interface InternalBenchmarkClient {

        @GET("/users/{id}")
        Mono<String> findUser(
                @PathVar("id") String id,
                @QueryParam("expand") String expand,
                @HeaderParam("X-Tenant") String tenant);
    }
}
