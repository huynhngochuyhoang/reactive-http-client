# V33 Extension Example (Development Only)

This example targets the current `4.5.0-SNAPSHOT` source, **not published `4.4.1`**.
It exercises the accepted F001/F002/F003 corrections; no next release is selected.
See the [migration table](../../roadmaps/v33/MAINTAINER-GUIDANCE.md#migration-by-finding)
for published workarounds. Do not use reflection to construct internal metadata.

## Prerequisites

Use Java 21 and a supported Spring Boot 4 parent/BOM. Build this checkout with
`mvn -B -ntp -s .mvn/maven-central-settings.xml -DskipTests install` before using
its development coordinates locally; this is not Central consumption. Add
`io.github.huynhngochuyhoang:reactive-http-client-starter:4.5.0-SNAPSHOT` and the
following explicit cache runtime dependency (its version comes from Boot):

```xml
<dependency>
  <groupId>com.github.ben-manes.caffeine</groupId>
  <artifactId>caffeine</artifactId>
</dependency>
```

Import `CatalogExtensionConfiguration` into the application. Replace only the
synthetic base URL with an approved endpoint before a real call. This example
assumes public, identity-independent catalog data, no auth provider, and a
repeatable String response. A tenant/auth-dependent endpoint needs additional
explicit variants and per-caller gates; this example does not authorize sharing it.

Inventory every applicable Boot `WebClientCustomizer`, per-client customizer and
replacement builder by bean name before selecting caching. The example classifies
only its own fixed Accept-header mutation. A full Boot application may contribute
other customizers (for example codec customizers); inspect and classify each
entire mutation or leave caching unselected. Missing or INCOMPATIBLE declarations
must fail startup. Never copy a blanket SAFE list from another application.
The proven starter-owned builder needs no redundant entry in current source;
same-named application replacements and unknown provenance are not exempt.

## Public Metadata and Programmatic Properties

The complete Java block is compiled and exercised by
`V33GuidanceExampleTest`. Its configuration uses only public application APIs.
The test supplies a counted in-process exchange function, not a real HTTP server;
the separate [assembled/native evidence](../../roadmaps/v33/PARITY-EVIDENCE.md)
owns transport and native claims.

```java
package example.v33;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.core.MethodMetadata;
import io.github.huynhngochuyhoang.httpstarter.core.MethodMetadataCache;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientCustomizer;
import io.github.huynhngochuyhoang.httpstarter.enable.EnableReactiveHttpClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
@EnableReactiveHttpClients(basePackageClasses = CatalogExtensionConfiguration.CatalogClient.class)
public class CatalogExtensionConfiguration {
    @ReactiveHttpClient(name = "catalog-extension")
    public interface CatalogClient {
        @GET("/catalog")
        Mono<String> read();
    }

    @Bean
    @Primary
    ReactiveHttpClientProperties catalogProperties() {
        var properties = new ReactiveHttpClientProperties();
        var client = new ReactiveHttpClientProperties.ClientConfig();
        client.setBaseUrl("https://catalog.example.invalid");
        var policy = new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(60000L);
        policy.setMaximumSize(100L);
        policy.setVaryByHeaders(List.of("Idempotency-Key"));
        client.getCache().getPolicies().put("catalog-read", policy);
        client.getCache().setPolicy("catalog-read");
        client.getCache().getCustomizations().put("catalogRepresentation",
                ReactiveHttpClientProperties.CacheCustomizationSafety.SAFE);
        properties.getClients().put("catalog-extension", client);
        return properties;
    }

    @Bean
    ReactiveHttpClientCustomizer catalogRepresentation() {
        return new ReactiveHttpClientCustomizer() {
            @Override
            public boolean supports(String clientName) {
                return "catalog-extension".equals(clientName);
            }

            @Override
            public void customize(org.springframework.web.reactive.function.client.WebClient.Builder builder) {
                builder.filter((request, next) -> next.exchange(ClientRequest.from(request)
                        .headers(headers -> headers.set(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN_VALUE))
                        .build()));
            }
        };
    }

    @Bean
    MethodMetadataCache methodMetadataCache() {
        return new MethodMetadataCache() {
            private final ConcurrentHashMap<Method, MethodMetadata> catalog = new ConcurrentHashMap<>();

            @Override
            public MethodMetadata get(Method method) {
                if (method.getDeclaringClass() != CatalogClient.class) {
                    return super.get(method);
                }
                return catalog.computeIfAbsent(method, selected -> {
                    var metadata = new MethodMetadata();
                    metadata.setMethod(selected);
                    metadata.setApiName("catalog.read");
                    metadata.setHttpMethod("GET");
                    metadata.setPathTemplate("/catalog");
                    metadata.setReturnsMono(true);
                    metadata.setReturnsFlux(false);
                    metadata.setResponseType(String.class);
                    return metadata;
                });
            }
        };
    }
}
```

The no-argument method intentionally needs no parameter binding map. A real
replacement parser must also supply all relevant binding, auth/retry/cache/logging
metadata; constructing a fresh model does not copy annotations automatically.
The client-level policy here supplies cache selection, and the required
Idempotency-Key variant also partitions a context-provided value when present.
Single flight, refresh, cache metrics and resilience operators remain unselected.

`@Primary` is retained as a valid published workaround, not a new V33 requirement.
Current AOT honors Spring's applicable primary/non-fallback/priority/default-candidate
selection instead of first-singleton order. Do not create ambiguous candidates
and expect fallback to a valid inactive bean. Environment binding can override
configuration-properties values; confirm the effective selected configuration.
Keep configuration construction resource-free and the scope available at build
time. For FactoryBean type hints, early binding and scoped-target limits, see
[native guidance](../20-native-release-compatibility.md#post-441-development-lane).

Metadata is mutable only during parsing. Do not edit a cached model after a plan
has consumed it; changing setters is not live reconfiguration. Recreate the
Spring-managed client factory/context for new routing or policies and close the
old owner. Application connectors, executors and credentials keep their own
lifecycle. No new public closeable handler or diagnostics instantiation path is
introduced by these corrections.
