package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.ApiRef;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class DeclarativeReturnTypeGrammarTest {

    private final MethodMetadataCache metadataCache = new MethodMetadataCache();

    @Test
    void acceptsLegacyUnaryEnvelopeFluxAndRawStreamingShapes() {
        assertThatCode(() -> metadataCache.validateDeclarativeReturnTypes(
                ValidShapesClient.class, "valid-shapes"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNestedPublishersAndUnsupportedEnvelopes() {
        assertUnsupported(NestedMonoClient.class, "nested-mono")
                .contains("resolvedResponseType=reactor.core.publisher.Mono<java.lang.String>")
                .contains("a Mono element type cannot contain another reactive Publisher");
        assertUnsupported(NestedFluxClient.class, "nested-flux")
                .contains("resolvedResponseType=reactor.core.publisher.Flux<java.lang.String>")
                .contains("a Flux element type cannot contain another reactive Publisher");
        assertUnsupported(NestedPublisherContainerClient.class, "nested-container")
                .contains("java.util.List<reactor.core.publisher.Flux<java.lang.String>>")
                .contains("a Mono element type cannot contain another reactive Publisher");
        assertUnsupported(UnsupportedStreamingEnvelopeClient.class, "typed-streaming-envelope")
                .contains("ResponseEntity<reactor.core.publisher.Flux<java.lang.String>>")
                .contains("the only reactive ResponseEntity body supported is Flux<DataBuffer>");
        assertUnsupported(FluxEnvelopeClient.class, "flux-envelope")
                .contains("ResponseEntity envelopes are supported only inside Mono");
        assertUnsupported(RawResponseEntityClient.class, "raw-envelope")
                .contains("resolvedResponseType=org.springframework.http.ResponseEntity<T>")
                .contains("ResponseEntity must declare a resolvable body type");
    }

    @Test
    void rejectsTypeVariableOuterPublisherAndUnresolvedFluxElements() {
        assertUnsupported(TypeVariableOuterPublisherClient.class, "outer-variable")
                .contains("outer reactive return type must not be an unresolved type variable");
        assertUnsupported(UnresolvedFluxClient.class, "unresolved-flux")
                .contains("resolvedResponseType=T")
                .contains("reactive element type must resolve against the concrete client interface");
        assertUnsupported(RawInheritedFluxClient.class, "raw-inherited-flux")
                .contains("resolvedResponseType=T")
                .contains("reactive element type must resolve against the concrete client interface");
    }

    @Test
    void rejectsBoundedUnresolvedVariablesButAcceptsConcreteBindings() {
        assertUnsupported(BoundedMethodVariableClient.class, "bounded-method-variable")
                .contains("resolvedResponseType=" + BaseDto.class.getName())
                .contains("reactive element type must resolve against the concrete client interface");
        assertUnsupported(UnresolvedBoundedClient.class, "bounded-client-variable")
                .contains("reactive element type must resolve against the concrete client interface");
        assertUnsupported(RawBoundedClient.class, "raw-bounded-client")
                .contains("reactive element type must resolve against the concrete client interface");

        assertThatCode(() -> metadataCache.validateDeclarativeReturnTypes(
                ConcreteBoundedClient.class, "concrete-bounded-client"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsReifiablePublisherArrays() {
        assertUnsupported(PublisherArrayClient.class, "publisher-array")
                .contains("resolvedResponseType=reactor.core.publisher.Flux[]")
                .contains("a Mono element type cannot contain another reactive Publisher");
    }

    @Test
    void resolvesMultiLevelInheritedGenericBindingsBeforeValidationAndExport() {
        assertThatCode(() -> metadataCache.validateDeclarativeReturnTypes(
                ConcreteListClient.class, "concrete-list"))
                .doesNotThrowAnyException();

        EffectiveHttpClientContract contract = EffectiveHttpClientContractExporter.export(
                        ConcreteListClient.class,
                        "concrete-list",
                        new ReactiveHttpClientProperties.ClientConfig(),
                        metadataCache)
                .get(0);

        assertThat(contract.responseType()).isEqualTo("java.util.List<java.lang.String>");
        assertThat(contract.genericBindings()).isEqualTo("T=java.util.List<java.lang.String>");
    }

    @Test
    void resolvesInheritedGenericArraysAndWildcardBounds() {
        assertThatCode(() -> metadataCache.validateDeclarativeReturnTypes(
                ConcreteArrayClient.class, "concrete-array"))
                .doesNotThrowAnyException();

        List<EffectiveHttpClientContract> contracts = EffectiveHttpClientContractExporter.export(
                ConcreteArrayClient.class,
                "concrete-array",
                new ReactiveHttpClientProperties.ClientConfig(),
                metadataCache);

        assertThat(contracts)
                .filteredOn(contract -> contract.apiName().equals("array"))
                .extracting(EffectiveHttpClientContract::responseType)
                .containsExactly("java.lang.String[]");
        assertThat(contracts)
                .filteredOn(contract -> contract.apiName().equals("wildcards"))
                .extracting(EffectiveHttpClientContract::responseType)
                .containsExactly("java.util.List<? extends java.lang.String>");
        assertThat(contracts)
                .filteredOn(contract -> contract.apiName().equals("lowerWildcards"))
                .extracting(EffectiveHttpClientContract::responseType)
                .containsExactly("java.util.List<? super java.lang.String>");
    }

    @Test
    void resolvesParameterizedOwnerBindingsAndRejectsUnresolvedOwners() {
        assertUnsupported(UnresolvedOwnerClient.class, "unresolved-owner")
                .contains("reactive element type must resolve against the concrete client interface");

        assertThatCode(() -> metadataCache.validateDeclarativeReturnTypes(
                ConcreteOwnerClient.class, "concrete-owner"))
                .doesNotThrowAnyException();
        EffectiveHttpClientContract contract = EffectiveHttpClientContractExporter.export(
                        ConcreteOwnerClient.class,
                        "concrete-owner",
                        new ReactiveHttpClientProperties.ClientConfig(),
                        metadataCache)
                .get(0);

        assertThat(contract.responseType()).isEqualTo(
                DeclarativeReturnTypeGrammarTest.class.getName()
                        + "$GenericOuter<java.lang.String>$Body");
    }

    @Test
    void rejectsInheritedGenericPublisherBindingWithConcreteAndDeclaringContext() {
        assertUnsupported(InheritedPublisherClient.class, "inherited-publisher")
                .contains("concreteClient=" + InheritedPublisherClient.class.getName())
                .contains("declaringInterface=" + GenericOperations.class.getName())
                .contains("method=public abstract reactor.core.publisher.Mono<T> "
                        + GenericOperations.class.getName() + ".load()")
                .contains("resolvedResponseType=reactor.core.publisher.Flux<java.lang.String>")
                .contains("Supported return shapes:");
    }

    @Test
    void rejectsApiRefMethodBeforeConfigurationOrDispatch() {
        assertUnsupported(InvalidApiRefClient.class, "api-ref-client")
                .contains("declaringInterface=" + InvalidApiRefClient.class.getName())
                .contains("resolvedResponseType=reactor.core.publisher.Mono<java.lang.String>");
    }

    @Test
    void exporterAndDiagnosticsUseTheSameReturnGrammar() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();

        assertThatThrownBy(() -> EffectiveHttpClientContractExporter.export(
                NestedMonoClient.class, "diagnostic-client", config, metadataCache))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported declarative return type")
                .hasMessageContaining("resolvedResponseType=reactor.core.publisher.Mono<java.lang.String>");
        assertThatThrownBy(() -> ReactiveHttpClientDiagnosticsProvider.clientSummary(
                NestedMonoClient.class, "diagnostic-client", config, metadataCache, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported declarative return type")
                .hasMessageContaining("resolvedResponseType=reactor.core.publisher.Mono<java.lang.String>");
    }

    private AbstractStringAssert<?> assertUnsupported(Class<?> clientInterface, String clientName) {
        Throwable failure = catchThrowable(() ->
                metadataCache.validateDeclarativeReturnTypes(clientInterface, clientName));
        assertThat(failure)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reactive HTTP client '" + clientName + "'")
                .hasMessageContaining("concreteClient=" + clientInterface.getName())
                .hasMessageContaining("Supported return shapes:");
        return assertThat(failure.getMessage());
    }

    @SuppressWarnings("rawtypes")
    interface ValidShapesClient {
        @GET("/raw-mono")
        Mono rawMono();

        @GET("/raw-flux")
        Flux rawFlux();

        @GET("/void")
        Mono<Void> voidMono();

        @GET("/value")
        Mono<String> value();

        @GET("/entity")
        Mono<ResponseEntity<String>> entity();

        @GET("/void-entity")
        Mono<ResponseEntity<Void>> voidEntity();

        @GET("/values")
        Flux<String> values();

        @GET("/buffers")
        Flux<DataBuffer> buffers();

        @GET("/streaming-entity")
        Mono<ResponseEntity<Flux<DataBuffer>>> streamingEntity();
    }

    interface NestedMonoClient {
        @GET("/nested")
        Mono<Mono<String>> nested();
    }

    interface NestedFluxClient {
        @GET("/nested")
        Flux<Flux<String>> nested();
    }

    interface NestedPublisherContainerClient {
        @GET("/nested")
        Mono<List<Flux<String>>> nested();
    }

    interface UnsupportedStreamingEnvelopeClient {
        @GET("/stream")
        Mono<ResponseEntity<Flux<String>>> stream();
    }

    interface FluxEnvelopeClient {
        @GET("/entities")
        Flux<ResponseEntity<String>> entities();
    }

    @SuppressWarnings("rawtypes")
    interface RawResponseEntityClient {
        @GET("/entity")
        Mono<ResponseEntity> entity();
    }

    interface GenericOperations<T> {
        @GET("/generic")
        Mono<T> load();
    }

    interface ListOperations<R> extends GenericOperations<List<R>> {
    }

    interface ConcreteListClient extends ListOperations<String> {
    }

    interface InheritedPublisherClient extends GenericOperations<Flux<String>> {
    }

    interface InvalidApiRefClient {
        @ApiRef("nested.lookup")
        Mono<Mono<String>> lookup();
    }

    interface TypeVariableOuterPublisherClient {
        @GET("/outer-variable")
        <R extends Mono<String>> R load();
    }

    interface UnresolvedFluxClient<T> {
        @GET("/unresolved")
        Flux<T> values();
    }

    interface GenericFluxOperations<T> {
        @GET("/raw-inherited")
        Flux<T> values();
    }

    @SuppressWarnings("rawtypes")
    interface RawInheritedFluxClient extends GenericFluxOperations {
    }

    interface GenericArrayOperations<T> {
        @GET("/array")
        Mono<T[]> array();

        @GET("/wildcards")
        Mono<List<? extends T>> wildcards();

        @GET("/lower-wildcards")
        Mono<List<? super T>> lowerWildcards();
    }

    interface ConcreteArrayClient extends GenericArrayOperations<String> {
    }

    static class BaseDto {
    }

    static final class SpecialDto extends BaseDto {
    }

    interface BoundedMethodVariableClient {
        @GET("/bounded-method")
        <R extends BaseDto> Mono<R> load();
    }

    interface BoundedOperations<T extends BaseDto> {
        @GET("/bounded")
        Mono<T> load();
    }

    interface UnresolvedBoundedClient<T extends BaseDto> extends BoundedOperations<T> {
    }

    @SuppressWarnings("rawtypes")
    interface RawBoundedClient extends BoundedOperations {
    }

    interface ConcreteBoundedClient extends BoundedOperations<SpecialDto> {
    }

    @SuppressWarnings("rawtypes")
    interface PublisherArrayClient {
        @GET("/publisher-array")
        Mono<Flux[]> load();
    }

    static class GenericOuter<T> {
        class Body {
        }
    }

    interface OwnerOperations<T> {
        @GET("/owner")
        Mono<GenericOuter<T>.Body> load();
    }

    interface UnresolvedOwnerClient<T> extends OwnerOperations<T> {
    }

    interface ConcreteOwnerClient extends OwnerOperations<String> {
    }
}
