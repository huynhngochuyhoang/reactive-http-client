package io.github.huynhngochuyhoang.httpstarter.core;

import org.springframework.util.StringUtils;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriTemplate;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

/** Starter-owned parsing, validation, and construction for declarative request URIs. */
final class DeclarativeRequestUri {

    private DeclarativeRequestUri() {
    }

    static void validate(String template, Set<String> declaredPathVars, String context) {
        if (!StringUtils.hasText(template)) {
            return;
        }
        ParsedTemplate parsed = parse(template, context);
        if (parsed.hasAuthority()) {
            throw invalid(context, "must be a path/query template and must not declare a scheme or authority");
        }
        if (parsed.hasFragment()) {
            throw invalid(context, "must not declare a URI fragment");
        }

        Set<String> placeholders;
        try {
            validateTemplateBraces(template);
            placeholders = new LinkedHashSet<>(new UriTemplate(template).getVariableNames());
            if (placeholders.stream().anyMatch(name -> !StringUtils.hasText(name))) {
                throw new IllegalArgumentException("Blank URI variable");
            }
        } catch (IllegalArgumentException ex) {
            throw invalid(context, "contains malformed URI-template syntax");
        }
        Set<String> missing = new LinkedHashSet<>(placeholders);
        missing.removeAll(declaredPathVars);
        if (!missing.isEmpty()) {
            throw new IllegalStateException(context + " has URI template variables " + missing
                    + " without matching @PathVar parameters.");
        }
        Set<String> unused = new LinkedHashSet<>(declaredPathVars);
        unused.removeAll(placeholders);
        if (!unused.isEmpty()) {
            throw new IllegalStateException(context + " declares @PathVar parameters " + unused
                    + " that are not used by the path template.");
        }
    }

    static URI build(UriBuilder uriBuilder,
                     String template,
                     RequestArgumentResolver.ResolvedArgs resolved,
                     String context) {
        ParsedTemplate parsed = parse(template != null ? template : "", context);
        if (parsed.hasAuthority() || parsed.hasFragment()) {
            throw invalid(context, "is not a path/query template for the configured client authority");
        }
        try {
            UriBuilder builder = uriBuilder.path(parsed.path());
            if (parsed.query() != null) {
                builder.query(parsed.query());
            }
            UriBuilder requestBuilder = builder;
            resolved.queryParams().forEach((name, values) ->
                    values.forEach(value -> requestBuilder.queryParam(name, String.valueOf(value))));
            return requestBuilder.build(resolved.pathVars());
        } catch (IllegalArgumentException ex) {
            throw invalid(context, "could not be expanded into a request URI");
        }
    }

    private static ParsedTemplate parse(String template, String context) {
        try {
            UriComponents components = UriComponentsBuilder.fromUriString(template).build();
            return new ParsedTemplate(
                    components.getPath() != null ? components.getPath() : "",
                    components.getQuery(),
                    StringUtils.hasText(components.getScheme())
                            || StringUtils.hasText(components.getHost())
                            || StringUtils.hasText(components.getUserInfo())
                            || components.getPort() >= 0,
                    components.getFragment() != null);
        } catch (IllegalArgumentException ex) {
            throw invalid(context, "contains malformed URI syntax");
        }
    }

    private static void validateTemplateBraces(String template) {
        int level = 0;
        for (int index = 0; index < template.length(); index++) {
            char current = template.charAt(index);
            if (current == '{') {
                level++;
            } else if (current == '}') {
                level--;
                if (level < 0) {
                    throw new IllegalArgumentException("Unopened URI variable");
                }
            }
        }
        if (level != 0) {
            throw new IllegalArgumentException("Unclosed URI variable");
        }
    }

    private static IllegalStateException invalid(String context, String reason) {
        return new IllegalStateException(context + " " + reason + ".");
    }

    private record ParsedTemplate(String path, String query, boolean hasAuthority, boolean hasFragment) {
    }
}
