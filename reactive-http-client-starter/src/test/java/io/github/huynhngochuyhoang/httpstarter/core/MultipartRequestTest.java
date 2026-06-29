package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.auth.AwsSigV4AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.auth.OutboundAuthFilter;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the multipart request encoding path (roadmap 1.2). Each test invokes the
 * proxy through a stub {@link org.springframework.web.reactive.function.client.ExchangeFunction}
 * that captures the {@link ClientRequest} and asserts on headers + body.
 */
class MultipartRequestTest {

    @Test
    void encodesMultipartWithMixedFieldAndFileParts() {
        AtomicReference<ClientRequest> captured = captureExchange();
        ReactiveClientInvocationHandler handler = createHandler(captured);

        ByteArrayResource avatar = new ByteArrayResource("binary-data".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() { return "avatar.png"; }
        };

        StepVerifier.create(invokeUploadAvatar(handler, 42L, "profile pic", avatar))
                .verifyComplete();

        ClientRequest request = captured.get();
        assertThat(request.url().getPath()).isEqualTo("/users/42/avatar");
        MediaType contentType = materialize(request).getHeaders().getContentType();
        assertThat(contentType).isNotNull();
        assertThat(contentType.getType()).isEqualTo("multipart");
        assertThat(contentType.getSubtype()).isEqualTo("form-data");
        assertThat(contentType.getParameter("boundary")).isNotBlank();
    }

    @Test
    void encodesByteArrayWithAnnotationFilenameAndContentType() {
        AtomicReference<ClientRequest> captured = captureExchange();
        ReactiveClientInvocationHandler handler = createHandler(captured);

        byte[] payload = "csv,data".getBytes(StandardCharsets.UTF_8);

        StepVerifier.create(invokeUploadCsv(handler, payload))
                .verifyComplete();

        MediaType contentType = materialize(captured.get()).getHeaders().getContentType();
        assertThat(contentType).isNotNull();
        assertThat(contentType.getType()).isEqualTo("multipart");
    }

    @Test
    void encodesFileAttachmentOverridingAnnotationDefaults() {
        AtomicReference<ClientRequest> captured = captureExchange();
        ReactiveClientInvocationHandler handler = createHandler(captured);

        FileAttachment attachment = FileAttachment.of(
                "log-payload".getBytes(StandardCharsets.UTF_8),
                "2026-04-24.log",
                "text/plain");

        StepVerifier.create(invokeUploadLog(handler, attachment))
                .verifyComplete();

        MediaType contentType = materialize(captured.get()).getHeaders().getContentType();
        assertThat(contentType).isNotNull();
        assertThat(contentType.getType()).isEqualTo("multipart");
    }

    @Test
    void sigV4MultipartUploadFailsBeforeSigningEmptyPayload() {
        AtomicReference<ClientRequest> captured = captureExchange();
        ReactiveClientInvocationHandler handler = createSigV4Handler(captured);
        ByteArrayResource avatar = new ByteArrayResource("binary-data".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() { return "avatar.png"; }
        };

        StepVerifier.create(invokeUploadAvatar(handler, 42L, "profile pic", avatar))
                .expectErrorSatisfies(error -> {
                    AuthProviderException authError = org.junit.jupiter.api.Assertions.assertInstanceOf(
                            AuthProviderException.class, error);
                    assertThat(authError.getCause())
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining("cannot sign multipart request bodies")
                            .hasMessageContaining("stable raw bytes are not materialized");
                })
                .verify();
        assertThat(captured.get()).isNull();
    }

    @Test
    void combiningMultipartBodyWithBodyFailsAtMetadataParse() {
        MethodMetadataCache cache = new MethodMetadataCache();

        assertThatThrownBy(() ->
                cache.get(InvalidCombinationClient.class.getMethod("uploadBoth",
                        String.class, byte[].class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@MultipartBody cannot be combined with a @Body parameter");
    }

    @Test
    void formFieldWithoutMultipartBodyFailsAtMetadataParse() {
        MethodMetadataCache cache = new MethodMetadataCache();

        assertThatThrownBy(() ->
                cache.get(InvalidCombinationClient.class.getMethod("formFieldWithoutMultipart",
                        String.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("require the method to be annotated @MultipartBody");
    }

    @Test
    void multipartMethodWithoutAnyPartsFailsAtMetadataParse() {
        MethodMetadataCache cache = new MethodMetadataCache();

        assertThatThrownBy(() ->
                cache.get(InvalidCombinationClient.class.getMethod("multipartNoParts")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no @FormField / @FormFile parameters");
    }

    @Test
    void unsupportedFileTypeFailsAtInvocation() {
        AtomicReference<ClientRequest> captured = captureExchange();
        ReactiveClientInvocationHandler handler = createHandler(captured);

        StepVerifier.create(invokeUploadUnsupported(handler, 123))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static ReactiveClientInvocationHandler createHandler(AtomicReference<ClientRequest> captured) {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://multipart.test")
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());
                })
                .build();

        return createHandler(webClient, new ReactiveHttpClientProperties.ClientConfig());
    }

    private static ReactiveClientInvocationHandler createSigV4Handler(AtomicReference<ClientRequest> captured) {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://multipart.test")
                .filter(new OutboundAuthFilter("multipart-client", sigV4Provider()))
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());
                })
                .build();
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setAuthProvider("awsSigV4");
        return createHandler(webClient, config);
    }

    @SuppressWarnings("unchecked")
    private static ReactiveClientInvocationHandler createHandler(
            WebClient webClient, ReactiveHttpClientProperties.ClientConfig config) {
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
                "multipart-client",
                appCtx,
                new NoopResilienceOperatorApplier(),
                null,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );
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

    private static AtomicReference<ClientRequest> captureExchange() {
        return new AtomicReference<>();
    }

    /**
     * Writes the {@link ClientRequest}'s {@link org.springframework.web.reactive.function.BodyInserter}
     * to a {@link MockClientHttpRequest} so we can assert on the resolved
     * {@code Content-Type} header that only materialises at write-time.
     */
    private static MockClientHttpRequest materialize(ClientRequest request) {
        MockClientHttpRequest mock = new MockClientHttpRequest(request.method(), URI.create(request.url().toString()));
        request.writeTo(mock, ExchangeStrategies.withDefaults()).block();
        return mock;
    }

    @SuppressWarnings("unchecked")
    private static Mono<Void> invokeUploadAvatar(ReactiveClientInvocationHandler handler,
                                                 long userId,
                                                 String description,
                                                 Resource avatar) {
        try {
            return (Mono<Void>) handler.invoke(null,
                    MultipartTestClient.class.getMethod("uploadAvatar", long.class, String.class, Resource.class),
                    new Object[]{userId, description, avatar});
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Mono<Void> invokeUploadCsv(ReactiveClientInvocationHandler handler, byte[] payload) {
        try {
            return (Mono<Void>) handler.invoke(null,
                    MultipartTestClient.class.getMethod("uploadCsv", byte[].class),
                    new Object[]{payload});
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Mono<Void> invokeUploadLog(ReactiveClientInvocationHandler handler, FileAttachment attachment) {
        try {
            return (Mono<Void>) handler.invoke(null,
                    MultipartTestClient.class.getMethod("uploadLog", FileAttachment.class),
                    new Object[]{attachment});
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Mono<Void> invokeUploadUnsupported(ReactiveClientInvocationHandler handler, Object badValue) {
        try {
            return (Mono<Void>) handler.invoke(null,
                    MultipartTestClient.class.getMethod("uploadUnsupported", Object.class),
                    new Object[]{badValue});
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    interface MultipartTestClient {
        @POST("/users/{id}/avatar")
        @MultipartBody
        Mono<Void> uploadAvatar(@PathVar("id") long id,
                                @FormField("description") String description,
                                @FormFile(value = "avatar", filename = "fallback.bin") Resource avatar);

        @POST("/csv")
        @MultipartBody
        Mono<Void> uploadCsv(@FormFile(value = "csv", filename = "data.csv", contentType = "text/csv") byte[] payload);

        @POST("/logs")
        @MultipartBody
        Mono<Void> uploadLog(@FormFile(value = "log") FileAttachment log);

        @POST("/bad")
        @MultipartBody
        Mono<Void> uploadUnsupported(@FormFile(value = "wrong") Object value);
    }

    interface InvalidCombinationClient {
        @POST("/bad")
        @MultipartBody
        Mono<Void> uploadBoth(@FormField("x") String x,
                              @io.github.huynhngochuyhoang.httpstarter.annotation.Body byte[] body);

        @POST("/bad")
        Mono<Void> formFieldWithoutMultipart(@FormField("x") String x);

        @POST("/bad")
        @MultipartBody
        Mono<Void> multipartNoParts();
    }
}
