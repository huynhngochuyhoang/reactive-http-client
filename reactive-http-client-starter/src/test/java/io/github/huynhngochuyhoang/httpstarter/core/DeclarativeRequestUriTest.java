package io.github.huynhngochuyhoang.httpstarter.core;

import org.junit.jupiter.api.Test;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeclarativeRequestUriTest {

    @Test
    void preservesBasePathAndStructuredQueryOrder() {
        Map<String, List<Object>> query = new LinkedHashMap<>();
        query.put("configured", List.of("first", "second"));
        query.put("method", List.of("raw /+%#?&="));
        RequestArgumentResolver.ResolvedArgs resolved = new RequestArgumentResolver.ResolvedArgs(
                Map.of("id", "raw /+%#?", "template", "a%2Fb"), query, Map.of(), null);

        URI uri = DeclarativeRequestUri.build(
                new DefaultUriBuilderFactory("http://localhost/base").builder(),
                "/items/{id}?literal=yes&repeat=one&repeat=two&empty=&flag&template={template}",
                resolved,
                "Test method");

        assertThat(uri.toASCIIString()).isEqualTo(
                "http://localhost/base/items/raw%20%2F%2B%25%23%3F"
                        + "?literal=yes&repeat=one&repeat=two&empty=&flag&template=a%252Fb"
                        + "&configured=first&configured=second&method=raw%20/+%25%23?%26%3D");
    }

    @Test
    void freezesEmptyRelativeAndLeadingSlashPathJoining() {
        RequestArgumentResolver.ResolvedArgs empty = new RequestArgumentResolver.ResolvedArgs(
                Map.of(), Map.of(), Map.of(), null);

        assertThat(build("http://localhost/base", "", empty).getRawPath()).isEqualTo("/base");
        assertThat(build("http://localhost/base", "items", empty).getRawPath()).isEqualTo("/baseitems");
        assertThat(build("http://localhost/base", "/items", empty).getRawPath()).isEqualTo("/base/items");
        assertThat(build("http://localhost/base/", "items", empty).getRawPath()).isEqualTo("/base/items");
    }

    @Test
    void rejectsAuthorityAndFragmentWithoutEchoingCredentials() {
        assertThatThrownBy(() -> DeclarativeRequestUri.validate(
                "https://user:secret-value@example.test/items/{id}", Set.of("id"), "Client method"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Client method")
                .hasMessageContaining("scheme or authority")
                .hasMessageNotContaining("secret-value");

        assertThatThrownBy(() -> DeclarativeRequestUri.validate(
                "//user:secret-value@example.test/items/{id}", Set.of("id"), "Client method"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("scheme or authority")
                .hasMessageNotContaining("secret-value");

        assertThatThrownBy(() -> DeclarativeRequestUri.validate(
                "/items/{id}#secret-value", Set.of("id"), "Client method"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fragment")
                .hasMessageNotContaining("secret-value");
    }

    @Test
    void rejectsMalformedTemplatesWithSanitizedContext() {
        assertThatThrownBy(() -> DeclarativeRequestUri.validate(
                "/items/{secret-value", Set.of("secret-value"), "Client method"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Client method")
                .hasMessageContaining("malformed")
                .hasMessageNotContaining("secret-value");
    }

    private static URI build(String baseUrl,
                             String template,
                             RequestArgumentResolver.ResolvedArgs resolved) {
        return DeclarativeRequestUri.build(
                new DefaultUriBuilderFactory(baseUrl).builder(), template, resolved, "Test method");
    }
}
