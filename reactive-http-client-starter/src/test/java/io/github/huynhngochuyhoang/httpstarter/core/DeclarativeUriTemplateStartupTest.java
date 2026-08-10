package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.ApiRef;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.PathVar;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeclarativeUriTemplateStartupTest {

    private final MethodMetadataCache metadataCache = new MethodMetadataCache();

    @Test
    void validatesPathAndQueryVariablesAgainstTheConcreteInheritedMethod() {
        assertThatCode(() -> metadataCache.validateDeclarativeUriTemplates(
                ValidInheritedClient.class, "valid-uri"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsInheritedAuthorityEscapeWithSanitizedMethodContext() {
        assertThatThrownBy(() -> metadataCache.validateDeclarativeUriTemplates(
                InvalidInheritedClient.class, "invalid-uri"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Inherited method")
                .hasMessageContaining(InvalidInheritedClient.class.getName())
                .hasMessageContaining("invalid-uri")
                .hasMessageContaining("scheme or authority")
                .hasMessageNotContaining("secret-value");
    }

    @Test
    void validatesConfiguredApiRefTemplateAtStartup() {
        ReactiveHttpClientProperties.ApiConfig api = new ReactiveHttpClientProperties.ApiConfig();
        api.setMethod("GET");
        api.setPath("//user:secret-value@example.test/items/{id}");

        assertThatThrownBy(() -> metadataCache.validateDeclarativeUriTemplates(
                ApiRefUriClient.class, "api-ref-uri", Map.of("lookup", api)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@ApiRef(\"lookup\")")
                .hasMessageContaining("scheme or authority")
                .hasMessageNotContaining("secret-value");
    }

    interface ValidParent {
        @GET("/items/{id}?projection={projection}&fixed=yes")
        Mono<String> lookup(@PathVar("id") String id, @PathVar("projection") String projection);
    }

    @ReactiveHttpClient(name = "valid-uri", baseUrl = "http://valid.test/base")
    interface ValidInheritedClient extends ValidParent {
    }

    interface InvalidParent {
        @GET("https://user:secret-value@example.test/items/{id}")
        Mono<String> lookup(@PathVar("id") String id);
    }

    @ReactiveHttpClient(name = "invalid-uri", baseUrl = "http://configured.test/base")
    interface InvalidInheritedClient extends InvalidParent {
    }

    interface ApiRefUriClient {
        @ApiRef("lookup")
        Mono<String> lookup(@PathVar("id") String id);
    }
}
