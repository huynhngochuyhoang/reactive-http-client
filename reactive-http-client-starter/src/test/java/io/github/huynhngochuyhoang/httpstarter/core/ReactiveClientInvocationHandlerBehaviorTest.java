package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthRequest;
import io.github.huynhngochuyhoang.httpstarter.auth.AwsSigV4AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.auth.OutboundAuthFilter;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReactiveClientInvocationHandlerBehaviorTest {

    @Test
    void diagnosticsDisabledUnaryRequestDoesNotInstallSubscriptionReportingState() {
        AtomicReference<Boolean> reportingStatePresent = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> Mono.deferContextual(context -> {
                    reportingStatePresent.set(context.stream()
                            .anyMatch(entry -> entry.getValue() instanceof SubscriptionReportingState));
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("ok")
                            .build());
                }))
                .build();

        StepVerifier.create(invokeGet(createHandler(webClient), null))
                .expectNext("ok")
                .verifyComplete();

        assertEquals(Boolean.FALSE, reportingStatePresent.get());
    }

    @Test
    void unselectedPostDoesNotEnterTheCacheDecisionPath() throws Throwable {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                        .body("ok")
                        .build()))
                .build();
        ReactiveClientInvocationHandler handler = createHandler(
                webClient,
                new ReactiveHttpClientProperties.ClientConfig(),
                TestJsonCodecs.jsonCodec(),
                UnselectedPostClient.class);
        Method method = UnselectedPostClient.class.getMethod("post", String.class);

        @SuppressWarnings("unchecked")
        Mono<String> result = (Mono<String>) handler.invoke(null, method, new Object[]{"query"});

        StepVerifier.create(result).expectNext("ok").verifyComplete();
        Field decisions = ReactiveClientInvocationHandler.class.getDeclaredField("cacheDecisionCache");
        decisions.setAccessible(true);
        assertTrue(((Map<?, ?>) decisions.get(handler)).isEmpty());
    }

    @Test
    void nullRequiredPathVarFailsBeforeAuthBodyLifecycleOrDispatch() throws Exception {
        AtomicInteger authFilters = new AtomicInteger();
        AtomicInteger bodySubscriptions = new AtomicInteger();
        AtomicInteger lifecycleLookups = new AtomicInteger();
        AtomicInteger dispatches = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .filter((request, next) -> {
                    authFilters.incrementAndGet();
                    return next.exchange(request);
                })
                .exchangeFunction(request -> {
                    dispatches.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build());
                })
                .build();
        ApplicationContext context = mock(ApplicationContext.class);
        ObjectProvider<HttpClientObserver> observers = mock(ObjectProvider.class);
        ObjectProvider<ReactiveHttpClientLifecycleHook> hooks = mock(ObjectProvider.class);
        when(context.getBeanProvider(HttpClientObserver.class)).thenReturn(observers);
        when(context.getBeanProvider(ReactiveHttpClientLifecycleHook.class)).thenReturn(hooks);
        when(hooks.orderedStream()).thenAnswer(invocation -> {
            lifecycleLookups.incrementAndGet();
            return java.util.stream.Stream.empty();
        });
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setAuthProvider("test-auth");
        ReactiveClientInvocationHandler handler = ReactiveClientInvocationHandler.create(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "required-path",
                RequiredPathClient.class,
                context,
                new NoopResilienceOperatorApplier(),
                TestJsonCodecs.jsonCodec(),
                new ReactiveHttpClientProperties.ObservabilityConfig());
        Flux<DataBuffer> body = Flux.defer(() -> {
            bodySubscriptions.incrementAndGet();
            return Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(new byte[]{1}));
        });
        Method method = RequiredPathClient.class.getMethod("upload", String.class, Flux.class);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> handler.invoke(null, method, new Object[]{null, body}));
        assertTrue(error.getMessage().contains("Required @PathVar(\"id\")"));
        assertTrue(error.getMessage().contains("parameter index 0"));
        assertTrue(error.getMessage().contains(RequiredPathClient.class.getName()));

        assertEquals(0, authFilters.get());
        assertEquals(0, bodySubscriptions.get());
        assertEquals(0, lifecycleLookups.get());
        assertEquals(0, dispatches.get());
    }

    @Test
    void shouldNotForceDefaultAcceptWhenUserProvidedOne() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("ok")
                            .build());
                })
                .build();

        ReactiveClientInvocationHandler handler = createHandler(webClient, "authProvider", TestJsonCodecs.jsonCodec());
        StepVerifier.create(invokeGet(handler, "application/xml"))
                .expectNext("ok")
                .verifyComplete();

        assertEquals("application/xml", captured.get().headers().getFirst(HttpHeaders.ACCEPT));
        assertFalse(captured.get().headers().get(HttpHeaders.ACCEPT).contains("application/json"));
    }

    @Test
    void shouldApplyDefaultHeadersToEveryRequest() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("ok")
                            .build());
                })
                .build();
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setDefaultHeaders(Map.of("X-Tenant", "public", "X-Client-Version", "v1"));

        ReactiveClientInvocationHandler handler = createHandler(webClient, config);
        StepVerifier.create(invokeGet(handler, null))
                .expectNext("ok")
                .verifyComplete();

        assertEquals("public", captured.get().headers().getFirst("X-Tenant"));
        assertEquals("v1", captured.get().headers().getFirst("X-Client-Version"));
    }

    @Test
    void shouldDecodeInheritedGenericEndpointAsConcreteChildResponseType() {
        BusApiOperators busClient = createProxy(BusApiOperators.class, jsonResponseWebClient(
                "{\"code\":\"0\",\"message\":\"boarding\"}"));
        TrainApiOperators trainClient = createProxy(TrainApiOperators.class, jsonResponseWebClient(
                "{\"code\":\"0\",\"bookingCode\":\"TR-9\"}"));

        StepVerifier.create(busClient.getOrder("bus-1"))
                .assertNext(response -> {
                    assertInstanceOf(BusResponse.class, response);
                    assertEquals("0", response.code);
                    assertEquals("boarding", response.message);
                })
                .verifyComplete();

        StepVerifier.create(trainClient.getOrder("train-1"))
                .assertNext(response -> {
                    assertInstanceOf(TrainResponse.class, response);
                    assertEquals("0", response.code);
                    assertEquals("TR-9", response.bookingCode);
                })
                .verifyComplete();
    }

    @Test
    void shouldLetHeaderParamOverrideDefaultHeaderIgnoringCase() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("ok")
                            .build());
                })
                .build();
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setDefaultHeaders(Map.of("accept", "application/json"));

        ReactiveClientInvocationHandler handler = createHandler(webClient, config);
        StepVerifier.create(invokeGet(handler, "application/xml"))
                .expectNext("ok")
                .verifyComplete();

        assertEquals("application/xml", captured.get().headers().getFirst(HttpHeaders.ACCEPT));
        assertFalse(captured.get().headers().get(HttpHeaders.ACCEPT).contains("application/json"));
    }


    @Test
    void shouldLetMultiValueHeaderParamOverrideDefaultHeaderIgnoringCase() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = captureRequestWebClient(captured);
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setDefaultHeaders(Map.of("x-tag", "default"));

        ReactiveClientInvocationHandler handler = createHandler(webClient, config);
        StepVerifier.create(invokeMultiHeader(handler, List.of("alpha", "beta")))
                .expectNext("ok")
                .verifyComplete();

        assertEquals(List.of("alpha", "beta"), captured.get().headers().get("X-Tag"));
        assertFalse(captured.get().headers().get("X-Tag").contains("default"));
    }

    @Test
    void shouldApplyDefaultQueryParamsToRequestsWithoutMethodQuery() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = captureRequestWebClient(captured);
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setDefaultQueryParams(Map.of(
                "locale", List.of("en-US"),
                "tag", List.of("public", "stable")));

        ReactiveClientInvocationHandler handler = createHandler(webClient, config);
        StepVerifier.create(invokeGet(handler, null))
                .expectNext("ok")
                .verifyComplete();

        var queryParams = UriComponentsBuilder.fromUri(captured.get().url()).build().getQueryParams();
        assertEquals("en-US", queryParams.getFirst("locale"));
        assertEquals(List.of("public", "stable"), queryParams.get("tag"));
    }

    @Test
    void shouldLetQueryParamOverrideDefaultQueryParam() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = captureRequestWebClient(captured);
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setDefaultQueryParams(Map.of(
                "locale", List.of("en-US"),
                "tag", List.of("public")));

        ReactiveClientInvocationHandler handler = createHandler(webClient, config);
        StepVerifier.create(invokeSearch(handler, "vi-VN", List.of("runtime", "sale")))
                .expectNext("ok")
                .verifyComplete();

        var queryParams = UriComponentsBuilder.fromUri(captured.get().url()).build().getQueryParams();
        assertEquals("vi-VN", queryParams.getFirst("locale"));
        assertEquals(List.of("runtime", "sale"), queryParams.get("tag"));
        assertFalse(queryParams.get("tag").contains("public"));
    }

    @Test
    void shouldEncodePathVariablesAsRawValues() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = captureRequestWebClient(captured);
        ReactiveClientInvocationHandler handler = createHandler(webClient);

        StepVerifier.create(invokeFile(handler, "reports/2026 Q1+draft"))
                .expectNext("ok")
                .verifyComplete();

        assertEquals("/files/reports%2F2026%20Q1%2Bdraft", captured.get().url().getRawPath());
    }

    @Test
    void shouldSendHeadAndResolvePathVariables() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());
                })
                .build();
        ReactiveClientInvocationHandler handler = createHandler(webClient);

        StepVerifier.create(invokeHead(handler, "object 42"))
                .verifyComplete();

        assertEquals(HttpMethod.HEAD, captured.get().method());
        assertEquals("/objects/object%2042", captured.get().url().getRawPath());
    }

    @Test
    void shouldSendOptions() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = captureRequestWebClient(captured);
        ReactiveClientInvocationHandler handler = createHandler(webClient);

        StepVerifier.create(invokeOptions(handler))
                .expectNext("ok")
                .verifyComplete();

        assertEquals(HttpMethod.OPTIONS, captured.get().method());
        assertEquals("/objects", captured.get().url().getRawPath());
    }

    @Test
    void shouldEncodeQueryParamValuesAndKeepEmptyValues() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = captureRequestWebClient(captured);
        ReactiveClientInvocationHandler handler = createHandler(webClient);

        StepVerifier.create(invokeComplexSearch(handler, "a b&c=1", List.of("red/blue", "x y"), ""))
                .expectNext("ok")
                .verifyComplete();

        URI uri = captured.get().url();
        assertTrue(uri.getRawQuery().contains("q=a%20b%26c%3D1"));
        assertTrue(uri.getRawQuery().contains("tag=red/blue"));
        assertTrue(uri.getRawQuery().contains("tag=x%20y"));
        assertTrue(uri.getRawQuery().contains("empty="));
    }

    @Test
    void shouldPreserveTemplateQueryStringAndAppendConfiguredAndMethodQueryParams() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = captureRequestWebClient(captured);
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setDefaultQueryParams(Map.of(
                "locale", List.of("en-US"),
                "tag", List.of("default")));
        ReactiveClientInvocationHandler handler = createHandler(webClient, config);

        StepVerifier.create(invokeTemplateQuerySearch(handler, "1", "runtime"))
                .expectNext("ok")
                .verifyComplete();

        var queryParams = UriComponentsBuilder.fromUri(captured.get().url()).build().getQueryParams();
        assertEquals("yes", queryParams.getFirst("fixed"));
        assertEquals("true", queryParams.getFirst("fromTemplate"));
        assertEquals("en-US", queryParams.getFirst("locale"));
        assertEquals("1", queryParams.getFirst("page"));
        assertEquals(List.of("from-template", "runtime"), queryParams.get("tag"));
    }

    @Test
    void shouldApplyApiRefPathTemplateWithQueryString() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = captureRequestWebClient(captured);
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.ApiConfig api = new ReactiveHttpClientProperties.ApiConfig();
        api.setMethod("GET");
        api.setPath("/users/{id}?expand=profile details");
        config.setApis(Map.of("lookup", api));
        ReactiveClientInvocationHandler handler = createHandler(webClient, config);

        StepVerifier.create(invokeApiRefLookup(handler, "a/b", "vi-VN"))
                .expectNext("ok")
                .verifyComplete();

        URI uri = captured.get().url();
        assertEquals("/users/a%2Fb", uri.getRawPath());
        assertTrue(uri.getRawQuery().contains("expand=profile%20details"));
        assertTrue(uri.getRawQuery().contains("lang=vi-VN"));
    }

    @Test
    void shouldNotForceDefaultContentTypeWhenUserProvidedOne() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("ok")
                            .build());
                })
                .build();

        ReactiveClientInvocationHandler handler = createHandler(webClient, "authProvider", TestJsonCodecs.jsonCodec());
        StepVerifier.create(invokePost(handler, "text/plain", "payload"))
                .expectNext("ok")
                .verifyComplete();

        assertEquals("text/plain", captured.get().headers().getFirst(HttpHeaders.CONTENT_TYPE));
        assertFalse(captured.get().headers().get(HttpHeaders.CONTENT_TYPE).contains("application/json"));
    }

    @Test
    void shouldUseProxyIdentityForObjectMethods() {
        ReactiveClientInvocationHandler handler = createHandler(WebClient.builder().baseUrl("http://test.local").build());
        Object proxy1 = Proxy.newProxyInstance(
                ClientWithHeaders.class.getClassLoader(),
                new Class<?>[]{ClientWithHeaders.class},
                handler
        );
        Object proxy2 = Proxy.newProxyInstance(
                ClientWithHeaders.class.getClassLoader(),
                new Class<?>[]{ClientWithHeaders.class},
                handler
        );

        assertTrue(proxy1.equals(proxy1));
        assertFalse(proxy1.equals(proxy2));
        assertEquals(System.identityHashCode(proxy1), proxy1.hashCode());
        assertTrue(proxy1.toString().contains("test-client"));
    }

    @Test
    void shouldProvideRawBodyForJsonContentTypeEvenWhenHeaderExplicitlyProvided() {
        AtomicReference<byte[]> capturedRawBody = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    capturedRawBody.set((byte[]) request.attribute(AuthRequest.REQUEST_RAW_BODY_ATTRIBUTE).orElse(null));
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("ok")
                            .build());
                })
                .build();

        ReactiveClientInvocationHandler handler = createHandler(webClient, "authProvider", TestJsonCodecs.jsonCodec());
        StepVerifier.create(invokePostJson(handler, "application/json", Map.of("id", 1)))
                .expectNext("ok")
                .verifyComplete();

        assertNotNull(capturedRawBody.get());
        assertTrue(new String(capturedRawBody.get(), StandardCharsets.UTF_8).contains("\"id\":1"));
    }

    @Test
    void shouldProvideRawBodyForStringBody() {
        AtomicReference<byte[]> capturedRawBody = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    capturedRawBody.set((byte[]) request.attribute(AuthRequest.REQUEST_RAW_BODY_ATTRIBUTE).orElse(null));
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("ok")
                            .build());
                })
                .build();

        ReactiveClientInvocationHandler handler = createHandler(webClient, "authProvider", TestJsonCodecs.jsonCodec());
        StepVerifier.create(invokePost(handler, "text/plain", "payload"))
                .expectNext("ok")
                .verifyComplete();

        assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8), capturedRawBody.get());
    }

    @Test
    void shouldProvideRawBodyForByteArrayBody() {
        AtomicReference<byte[]> capturedRawBody = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    capturedRawBody.set((byte[]) request.attribute(AuthRequest.REQUEST_RAW_BODY_ATTRIBUTE).orElse(null));
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("ok")
                            .build());
                })
                .build();

        ReactiveClientInvocationHandler handler = createHandler(webClient, "authProvider", TestJsonCodecs.jsonCodec());
        byte[] payload = "binary-data".getBytes(StandardCharsets.UTF_8);
        StepVerifier.create(invokePostBytes(handler, "application/octet-stream", payload))
                .expectNext("ok")
                .verifyComplete();

        assertArrayEquals(payload, capturedRawBody.get());
    }

    @Test
    void shouldSignJsonBodyUsingSerializedBytesOnWire() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = sigV4WebClient(captured);

        ReactiveClientInvocationHandler handler = createHandler(webClient, "awsSigV4", TestJsonCodecs.jsonCodec());
        StepVerifier.create(invokePostJson(handler, "application/json", Map.of("id", 1)))
                .expectNext("ok")
                .verifyComplete();

        assertSignedHashMatchesBody(captured.get());
    }

    @Test
    void shouldSignStringBodyUsingBytesOnWire() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = sigV4WebClient(captured);

        ReactiveClientInvocationHandler handler = createHandler(webClient, "awsSigV4", TestJsonCodecs.jsonCodec());
        StepVerifier.create(invokePost(handler, "text/plain", "payload"))
                .expectNext("ok")
                .verifyComplete();

        assertSignedHashMatchesBody(captured.get());
    }

    @Test
    void shouldSignStringBodyUsingContentTypeCharset() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = sigV4WebClient(captured);

        ReactiveClientInvocationHandler handler = createHandler(webClient, "awsSigV4", TestJsonCodecs.jsonCodec());
        StepVerifier.create(invokePost(handler, "text/plain;charset=ISO-8859-1", "café"))
                .expectNext("ok")
                .verifyComplete();

        assertEquals(sha256Hex("café".getBytes(StandardCharsets.ISO_8859_1)),
                captured.get().headers().getFirst("x-amz-content-sha256"));
    }

    @Test
    void shouldSignByteArrayBodyUsingBytesOnWire() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = sigV4WebClient(captured);

        ReactiveClientInvocationHandler handler = createHandler(webClient, "awsSigV4", TestJsonCodecs.jsonCodec());
        StepVerifier.create(invokePostBytes(handler, "application/octet-stream", "binary-data".getBytes(StandardCharsets.UTF_8)))
                .expectNext("ok")
                .verifyComplete();

        assertSignedHashMatchesBody(captured.get());
    }

    @Test
    void shouldSignEmptyBodyUsingEmptyPayloadHash() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = sigV4WebClient(captured);

        ReactiveClientInvocationHandler handler = createHandler(webClient, "awsSigV4", TestJsonCodecs.jsonCodec());
        StepVerifier.create(invokeGet(handler, null))
                .expectNext("ok")
                .verifyComplete();

        assertEquals(sha256Hex(new byte[0]), captured.get().headers().getFirst("x-amz-content-sha256"));
    }

    @Test
    void shouldRejectSigV4PublisherBodyBeforeStreamingUploadIsSubscribed() {
        AtomicInteger subscriptions = new AtomicInteger();
        Flux<String> body = Flux.just("payload").doOnSubscribe(subscription -> subscriptions.incrementAndGet());
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .filter(new OutboundAuthFilter("test-client", sigV4Provider()))
                .exchangeFunction(request -> Mono.error(new AssertionError("request must not be sent")))
                .build();

        ReactiveClientInvocationHandler handler = createHandler(webClient, "awsSigV4", TestJsonCodecs.jsonCodec());

        StepVerifier.create(invokePublisherBody(handler, body))
                .expectErrorSatisfies(error -> {
                    AuthProviderException authError = assertInstanceOf(AuthProviderException.class, error);
                    assertInstanceOf(IllegalArgumentException.class, authError.getCause());
                    assertTrue(authError.getCause().getMessage().contains("cannot sign Publisher request bodies"));
                })
                .verify();
        assertEquals(0, subscriptions.get());
    }

    @Test
    void shouldProvideRawBodyForCustomJsonContentType() {
        AtomicReference<byte[]> capturedRawBody = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    capturedRawBody.set((byte[]) request.attribute(AuthRequest.REQUEST_RAW_BODY_ATTRIBUTE).orElse(null));
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("ok")
                            .build());
                })
                .build();

        ReactiveClientInvocationHandler handler = createHandler(webClient, "authProvider", TestJsonCodecs.jsonCodec());
        StepVerifier.create(invokePostJson(handler, "application/problem+json", Map.of("type", "validation-error")))
                .expectNext("ok")
                .verifyComplete();

        assertNotNull(capturedRawBody.get());
        assertTrue(new String(capturedRawBody.get(), StandardCharsets.UTF_8).contains("\"type\":\"validation-error\""));
    }

    @Test
    void shouldSupportDefaultMethodsOnReactiveClientInterfaces() {
        ReactiveClientInvocationHandler handler = createHandler(WebClient.builder().baseUrl("http://test.local").build());
        ClientWithDefaultMethod proxy = (ClientWithDefaultMethod) Proxy.newProxyInstance(
                ClientWithDefaultMethod.class.getClassLoader(),
                new Class<?>[]{ClientWithDefaultMethod.class},
                handler
        );

        assertEquals("prefix-value", proxy.helper("value"));
    }

    @Test
    void shouldSkipJsonSerializationWhenAuthProviderIsNotConfigured() throws Exception {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .body("ok")
                        .build()))
                .build();
        ReactiveHttpClientJsonCodec jsonCodec = mock(ReactiveHttpClientJsonCodec.class);
        when(jsonCodec.write(any())).thenThrow(new IllegalStateException("must not serialize"));

        ReactiveClientInvocationHandler handler = createHandler(webClient, (String) null, jsonCodec);

        StepVerifier.create(invokePostJson(handler, "application/json", Map.of("id", 1)))
                .expectNext("ok")
                .verifyComplete();

        verify(jsonCodec, never()).write(any());
    }

    @Test
    void shouldBypassJsonAuthSerializationForPublisherBodyWithoutContentType() throws Exception {
        AtomicReference<Object> capturedBody = new AtomicReference<>();
        AtomicReference<Object> capturedRawBody = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    capturedBody.set(request.attribute(AuthRequest.REQUEST_BODY_ATTRIBUTE).orElse(null));
                    capturedRawBody.set(request.attribute(AuthRequest.REQUEST_RAW_BODY_ATTRIBUTE).orElse(null));
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("ok")
                            .build());
                })
                .build();
        ReactiveHttpClientJsonCodec jsonCodec = mock(ReactiveHttpClientJsonCodec.class);
        when(jsonCodec.write(any())).thenThrow(new IllegalStateException("must not serialize publisher"));
        Flux<String> body = Flux.just("one", "two");

        ReactiveClientInvocationHandler handler = createHandler(webClient, "authProvider", jsonCodec);

        StepVerifier.create(invokePublisherBody(handler, body))
                .expectNext("ok")
                .verifyComplete();

        assertSame(body, capturedBody.get());
        assertNull(capturedRawBody.get());
        verify(jsonCodec, never()).write(any());
    }

    @Test
    void shouldDefaultPublisherDtoBodyToJsonWhenContentTypeIsMissing() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("ok")
                            .build());
                })
                .build();

        ReactiveClientInvocationHandler handler = createHandler(webClient);

        StepVerifier.create(invokeDtoPublisherBody(handler, Flux.just(new PublisherDto("one"))))
                .expectNext("ok")
                .verifyComplete();

        MockClientHttpRequest request = materialize(captured.get());
        assertEquals(MediaType.APPLICATION_JSON, request.getHeaders().getContentType());
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invokePublisherBody(ReactiveClientInvocationHandler handler, Flux<String> body) {
        try {
            var method = ClientWithPublisherBody.class.getMethod("post", Flux.class);
            return (Mono<String>) handler.invoke(null, method, new Object[]{body});
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invokeDtoPublisherBody(ReactiveClientInvocationHandler handler, Flux<PublisherDto> body) {
        try {
            var method = ClientWithDtoPublisherBody.class.getMethod("post", Flux.class);
            return (Mono<String>) handler.invoke(null, method, new Object[]{body});
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    private static MockClientHttpRequest materialize(ClientRequest request) {
        MockClientHttpRequest mock = new MockClientHttpRequest(request.method(), URI.create(request.url().toString()));
        request.writeTo(mock, ExchangeStrategies.withDefaults()).block();
        return mock;
    }

    private static void assertSignedHashMatchesBody(ClientRequest request) {
        assertNotNull(request);
        assertNotNull(request.headers().getFirst("Authorization"));
        assertEquals(sha256Hex(materializedBody(request)), request.headers().getFirst("x-amz-content-sha256"));
    }

    private static byte[] materializedBody(ClientRequest request) {
        String body = materialize(request).getBodyAsString().block();
        return body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
    }

    private static WebClient sigV4WebClient(AtomicReference<ClientRequest> captured) {
        return WebClient.builder()
                .baseUrl("http://test.local")
                .filter(new OutboundAuthFilter("test-client", sigV4Provider()))
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("ok")
                            .build());
                })
                .build();
    }

    private static AwsSigV4AuthProvider sigV4Provider() {
        return AwsSigV4AuthProvider.builder()
                .accessKeyId("AKIAIOSFODNN7EXAMPLE")
                .secretAccessKey("wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY")
                .region("us-east-1")
                .service("execute-api")
                .clock(Clock.fixed(Instant.parse("2026-05-13T12:00:00Z"), ZoneOffset.UTC))
                .build();
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to calculate SHA-256", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invokeGet(ReactiveClientInvocationHandler handler, String accept) {
        try {
            var method = ClientWithHeaders.class.getMethod("get", String.class);
            return (Mono<String>) handler.invoke(null, method, new Object[]{accept});
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }


    @SuppressWarnings("unchecked")
    private static Mono<String> invokeMultiHeader(ReactiveClientInvocationHandler handler, List<String> tags) {
        try {
            var method = ClientWithMultiHeaders.class.getMethod("get", List.class);
            return (Mono<String>) handler.invoke(null, method, new Object[]{tags});
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invokePost(ReactiveClientInvocationHandler handler, String contentType, String body) {
        try {
            var method = ClientWithBodyHeaders.class.getMethod("post", String.class, String.class);
            return (Mono<String>) handler.invoke(null, method, new Object[]{contentType, body});
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invokePostJson(ReactiveClientInvocationHandler handler, String contentType, Map<String, Object> body) {
        try {
            var method = ClientWithJsonBodyHeaders.class.getMethod("post", String.class, Map.class);
            return (Mono<String>) handler.invoke(null, method, new Object[]{contentType, body});
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invokePostBytes(ReactiveClientInvocationHandler handler, String contentType, byte[] body) {
        try {
            var method = ClientWithByteArrayBodyHeaders.class.getMethod("post", String.class, byte[].class);
            return (Mono<String>) handler.invoke(null, method, new Object[]{contentType, body});
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invokeSearch(ReactiveClientInvocationHandler handler, String locale, List<String> tags) {
        try {
            var method = ClientWithQueryParams.class.getMethod("search", String.class, List.class);
            return (Mono<String>) handler.invoke(null, method, new Object[]{locale, tags});
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invokeFile(ReactiveClientInvocationHandler handler, String key) {
        try {
            var method = ClientWithPathVar.class.getMethod("file", String.class);
            return (Mono<String>) handler.invoke(null, method, new Object[]{key});
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Mono<Void> invokeHead(ReactiveClientInvocationHandler handler, String id) {
        try {
            var method = ClientWithHead.class.getMethod("head", String.class);
            return (Mono<Void>) handler.invoke(null, method, new Object[]{id});
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invokeOptions(ReactiveClientInvocationHandler handler) {
        try {
            var method = ClientWithOptions.class.getMethod("options");
            return (Mono<String>) handler.invoke(null, method, null);
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invokeComplexSearch(
            ReactiveClientInvocationHandler handler, String query, List<String> tags, String empty) {
        try {
            var method = ClientWithComplexQueryParams.class.getMethod("search", String.class, List.class, String.class);
            return (Mono<String>) handler.invoke(null, method, new Object[]{query, tags, empty});
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invokeTemplateQuerySearch(
            ReactiveClientInvocationHandler handler, String page, String tag) {
        try {
            var method = ClientWithTemplateQueryParams.class.getMethod("search", String.class, String.class);
            return (Mono<String>) handler.invoke(null, method, new Object[]{page, tag});
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invokeApiRefLookup(ReactiveClientInvocationHandler handler, String id, String lang) {
        try {
            var method = ClientWithApiRefPathQuery.class.getMethod("lookup", String.class, String.class);
            return (Mono<String>) handler.invoke(null, method, new Object[]{id, lang});
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    private static WebClient jsonResponseWebClient(String body) {
        return WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(body)
                        .build()))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static <T> T createProxy(Class<T> clientInterface, WebClient webClient) {
        ReactiveClientInvocationHandler handler = createHandler(
                webClient,
                new ReactiveHttpClientProperties.ClientConfig(),
                TestJsonCodecs.jsonCodec(),
                clientInterface);
        return (T) Proxy.newProxyInstance(
                clientInterface.getClassLoader(),
                new Class<?>[]{clientInterface},
                handler);
    }

    private static WebClient captureRequestWebClient(AtomicReference<ClientRequest> captured) {
        return WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("ok")
                            .build());
                })
                .build();
    }

    @SuppressWarnings("unchecked")
    private static ReactiveClientInvocationHandler createHandler(WebClient webClient) {
        return createHandler(webClient, (String) null, TestJsonCodecs.jsonCodec());
    }

    private static ReactiveClientInvocationHandler createHandler(
            WebClient webClient,
            ReactiveHttpClientProperties.ClientConfig config) {
        return createHandler(webClient, config, TestJsonCodecs.jsonCodec());
    }

    @SuppressWarnings("unchecked")
    private static ReactiveClientInvocationHandler createHandler(
            WebClient webClient,
            String authProviderName,
            ReactiveHttpClientJsonCodec jsonCodec) {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setAuthProvider(authProviderName);
        return createHandler(webClient, config, jsonCodec);
    }

    @SuppressWarnings("unchecked")
    private static ReactiveClientInvocationHandler createHandler(
            WebClient webClient,
            ReactiveHttpClientProperties.ClientConfig config,
            ReactiveHttpClientJsonCodec jsonCodec) {
        return createHandler(webClient, config, jsonCodec, null);
    }

    @SuppressWarnings("unchecked")
    private static ReactiveClientInvocationHandler createHandler(
            WebClient webClient,
            ReactiveHttpClientProperties.ClientConfig config,
            ReactiveHttpClientJsonCodec jsonCodec,
            Class<?> clientInterface) {
        ApplicationContext appCtx = mock(ApplicationContext.class);
        ObjectProvider<HttpClientObserver> observerProvider = mock(ObjectProvider.class);
        when(appCtx.getBeanProvider(HttpClientObserver.class)).thenReturn(observerProvider);
        when(observerProvider.getIfAvailable()).thenReturn(null);

        return new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "test-client",
                clientInterface,
                appCtx,
                new NoopResilienceOperatorApplier(),
                jsonCodec,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );
    }

    interface ClientWithHeaders {
        @GET("/headers")
        Mono<String> get(@HeaderParam("Accept") String accept);
    }


    interface ClientWithMultiHeaders {
        @GET("/headers")
        Mono<String> get(@HeaderParam("X-Tag") List<String> tags);
    }

    interface ClientWithBodyHeaders {
        @POST("/body")
        Mono<String> post(@HeaderParam("Content-Type") String contentType, @Body String body);
    }

    interface ClientWithJsonBodyHeaders {
        @POST("/body")
        Mono<String> post(@HeaderParam("Content-Type") String contentType, @Body Map<String, Object> body);
    }

    interface ClientWithByteArrayBodyHeaders {
        @POST("/body")
        Mono<String> post(@HeaderParam("Content-Type") String contentType, @Body byte[] body);
    }

    interface ClientWithPublisherBody {
        @POST("/body")
        Mono<String> post(@Body Flux<String> body);
    }

    interface ClientWithDtoPublisherBody {
        @POST("/body")
        Mono<String> post(@Body Flux<PublisherDto> body);
    }

    record PublisherDto(String name) {}

    interface ClientWithQueryParams {
        @GET("/search")
        Mono<String> search(@QueryParam("locale") String locale, @QueryParam("tag") List<String> tags);
    }

    interface ClientWithPathVar {
        @GET("/files/{key}")
        Mono<String> file(@PathVar("key") String key);
    }

    interface RequiredPathClient {
        @POST("/objects/{id}")
        Mono<String> upload(@PathVar("id") String id, @Body Flux<DataBuffer> body);
    }

    interface UnselectedPostClient {
        @POST("/query")
        Mono<String> post(@Body String query);
    }

    interface ClientWithHead {
        @HEAD("/objects/{id}")
        Mono<Void> head(@PathVar("id") String id);
    }

    interface ClientWithOptions {
        @OPTIONS("/objects")
        Mono<String> options();
    }

    interface ClientWithComplexQueryParams {
        @GET("/search")
        Mono<String> search(
                @QueryParam("q") String query,
                @QueryParam("tag") List<String> tags,
                @QueryParam("empty") String empty);
    }

    interface ClientWithTemplateQueryParams {
        @GET("/search?fixed=yes&tag=from-template&fromTemplate=true")
        Mono<String> search(@QueryParam("page") String page, @QueryParam("tag") String tag);
    }

    interface ClientWithApiRefPathQuery {
        @ApiRef("lookup")
        Mono<String> lookup(@PathVar("id") String id, @QueryParam("lang") String lang);
    }

    interface ApiOperators<T extends BaseResponse> {
        @GET("/api/order")
        Mono<T> getOrder(@QueryParam("orderId") String orderId);
    }

    interface BusApiOperators extends ApiOperators<BusResponse> {
    }

    interface TrainApiOperators extends ApiOperators<TrainResponse> {
    }

    static class BaseResponse {
        public String code;
    }

    static class BusResponse extends BaseResponse {
        public String message;
    }

    static class TrainResponse extends BaseResponse {
        public String bookingCode;
    }

    interface ClientWithDefaultMethod {
        default String helper(String value) {
            return "prefix-" + value;
        }
    }
}
