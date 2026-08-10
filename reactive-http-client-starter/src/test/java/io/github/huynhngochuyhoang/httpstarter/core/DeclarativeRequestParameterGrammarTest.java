package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class DeclarativeRequestParameterGrammarTest {

    private final MethodMetadataCache metadataCache = new MethodMetadataCache();

    @Test
    void acceptsEverySupportedRoleAndRepeatableMultipartNames() throws Exception {
        assertThatCode(() -> metadataCache.validateDeclarativeRequestParameters(
                ValidParameterClient.class, "valid-parameters"))
                .doesNotThrowAnyException();

        RequestPlan multipart = RequestPlan.from(
                metadataCache.get(ValidParameterClient.class.getMethod(
                        "multipart", String.class, String.class, byte[].class, Resource.class)),
                ValidParameterClient.class);
        assertThat(multipart.formFields())
                .extracting(RequestPlan.FormFieldBinding::name)
                .containsExactly("tag", "tag");
        assertThat(multipart.formFiles())
                .extracting(binding -> binding.annotation().value())
                .containsExactly("file", "file");
    }

    @Test
    void rejectsConflictingRolesWithConcreteInheritedMethodAndParameterFacts() {
        assertThatThrownBy(() -> metadataCache.validateDeclarativeRequestParameters(
                ConflictingChild.class, "conflicting-child"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Reactive HTTP client 'conflicting-child'")
                .hasMessageContaining("concreteClient=" + ConflictingChild.class.getName())
                .hasMessageContaining("declaringInterface=" + ConflictingParent.class.getName())
                .hasMessageContaining("method=public abstract reactor.core.publisher.Mono")
                .hasMessageContaining("parameterIndex=0")
                .hasMessageContaining("resolvedParameterType=java.lang.String")
                .hasMessageContaining("@PathVar(\"id\")")
                .hasMessageContaining("@QueryParam(\"id\")");

        assertInvalid(ConflictingMultipartClient.class, "conflicting-multipart")
                .hasMessageContaining("parameterIndex=0")
                .hasMessageContaining("@Body")
                .hasMessageContaining("@FormFile(\"file\")");
    }

    @Test
    void rejectsDuplicatePathQueryAndCaseInsensitiveHeaderNames() {
        assertInvalid(DuplicatePathClient.class, "duplicate-path")
                .hasMessageContaining("duplicate @PathVar(\"id\") bindings")
                .hasMessageContaining("parameter index 0");
        assertInvalid(DuplicateQueryClient.class, "duplicate-query")
                .hasMessageContaining("duplicate @QueryParam(\"tag\") bindings")
                .hasMessageContaining("parameter index 0");
        assertInvalid(DuplicateHeaderClient.class, "duplicate-header")
                .hasMessageContaining("duplicate header/idempotency-key name 'x-request-id'")
                .hasMessageContaining("parameter index 0");
        assertInvalid(DuplicateIdempotencyClient.class, "duplicate-idempotency")
                .hasMessageContaining("duplicate header/idempotency-key name 'IDEMPOTENCY-KEY'")
                .hasMessageContaining("parameter index 0");
    }

    @Test
    void resolvesMultiLevelGenericHeaderMapAndFormFileTypes() throws Exception {
        assertThatCode(() -> metadataCache.validateDeclarativeRequestParameters(
                ConcreteGenericParameterClient.class, "generic-valid"))
                .doesNotThrowAnyException();

        RequestPlan plan = RequestPlan.from(
                metadataCache.get(ConcreteGenericParameterClient.class.getMethod("upload", Object.class, Object.class)),
                ConcreteGenericParameterClient.class);
        assertThat(plan.parameterTypes().get(0).getTypeName())
                .isEqualTo("java.util.Map<java.lang.String, java.lang.String>");
        assertThat(plan.parameterTypes().get(1)).isEqualTo(ByteArrayResource.class);

        assertInvalid(InvalidGenericFileClient.class, "generic-invalid")
                .hasMessageContaining("resolvedParameterType=java.lang.String")
                .hasMessageContaining("@FormFile supports only byte[], Resource, or FileAttachment");
    }

    @Test
    void rejectsHeaderMapRolesThatDoNotMatchResolvedGenericType() {
        assertInvalid(InvalidGenericHeaderMapClient.class, "invalid-header-map")
                .hasMessageContaining("resolvedParameterType=java.lang.String")
                .hasMessageContaining("@HeaderParam without a name requires a Map parameter");
    }

    @Test
    void effectiveContractExportUsesRequestParameterGrammarForApiRefs() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.ApiConfig api = new ReactiveHttpClientProperties.ApiConfig();
        api.setMethod("GET");
        api.setPath("/items");
        config.getApis().put("items.find", api);

        assertThatThrownBy(() -> EffectiveHttpClientContractExporter.export(
                InvalidApiRefParameterClient.class,
                "api-ref-parameters",
                config,
                metadataCache))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conflicting request-binding roles")
                .hasMessageContaining("@HeaderParam(\"X-Item\")")
                .hasMessageContaining("@QueryParam(\"item\")");
    }

    @Test
    void unannotatedParametersRemainIgnoredAndWarnOnce(CapturedOutput output) {
        metadataCache.validateDeclarativeRequestParameters(UnannotatedClient.class, "unannotated");
        metadataCache.validateDeclarativeRequestParameters(UnannotatedClient.class, "unannotated");

        assertThat(output.getOut())
                .contains("is unannotated and is ignored for compatibility")
                .contains("Parameter index 0")
                .containsOnlyOnce("is unannotated and is ignored for compatibility");
    }

    private org.assertj.core.api.AbstractThrowableAssert<?, ? extends Throwable> assertInvalid(
            Class<?> clientInterface, String clientName) {
        return assertThatThrownBy(() -> metadataCache.validateDeclarativeRequestParameters(
                clientInterface, clientName));
    }

    interface ValidParameterClient {
        @POST("/items/{id}")
        Mono<String> request(
                @PathVar("id") String id,
                @QueryParam("tag") List<String> tags,
                @HeaderParam("X-Request-ID") String requestId,
                @HeaderParam Map<String, Object> headers,
                @IdempotencyKey String key,
                @Body String body);

        @POST("/multipart")
        @MultipartBody
        Mono<Void> multipart(
                @FormField("tag") String first,
                @FormField("tag") String second,
                @FormFile("file") byte[] bytes,
                @FormFile("file") Resource resource);
    }

    interface ConflictingParent<T> {
        @GET("/items/{id}")
        Mono<String> find(@PathVar("id") @QueryParam("id") T id);
    }

    interface ConflictingChild extends ConflictingParent<String> {
    }

    interface ConflictingMultipartClient {
        @POST("/upload")
        @MultipartBody
        Mono<Void> upload(@Body @FormFile("file") byte[] file);
    }

    interface DuplicatePathClient {
        @GET("/items/{id}")
        Mono<String> find(@PathVar("id") String first, @PathVar("id") String second);
    }

    interface DuplicateQueryClient {
        @GET("/items")
        Mono<String> find(@QueryParam("tag") String first, @QueryParam("tag") String second);
    }

    interface DuplicateHeaderClient {
        @GET("/items")
        Mono<String> find(@HeaderParam("X-Request-ID") String first,
                          @HeaderParam("x-request-id") String second);
    }

    interface DuplicateIdempotencyClient {
        @POST("/items")
        Mono<String> create(@HeaderParam("Idempotency-Key") String first,
                            @IdempotencyKey("IDEMPOTENCY-KEY") String second);
    }

    interface GenericParameterParent<H, F> {
        @POST("/upload")
        @MultipartBody
        Mono<Void> upload(@HeaderParam H headers, @FormFile("file") F file);
    }

    interface GenericParameterMiddle<H, F> extends GenericParameterParent<H, F> {
    }

    interface ConcreteGenericParameterClient
            extends GenericParameterMiddle<Map<String, String>, ByteArrayResource> {
    }

    interface InvalidGenericFileClient extends GenericParameterMiddle<Map<String, String>, String> {
    }

    interface GenericHeaderMapParent<H> {
        @GET("/headers")
        Mono<String> headers(@HeaderParam H headers);
    }

    interface InvalidGenericHeaderMapClient extends GenericHeaderMapParent<String> {
    }

    interface InvalidApiRefParameterClient {
        @ApiRef("items.find")
        Mono<String> find(@HeaderParam("X-Item") @QueryParam("item") String item);
    }

    interface UnannotatedClient {
        @GET("/items")
        Mono<String> find(String ignored);
    }
}
