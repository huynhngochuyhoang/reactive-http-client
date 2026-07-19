package io.github.huynhngochuyhoang.httpstarter.auth;

import io.github.huynhngochuyhoang.httpstarter.core.SensitiveHeaders;
import io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.CodecException;
import org.springframework.core.codec.Decoder;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * {@link AccessTokenProvider} implementing the OAuth 2.0 Client Credentials grant
 * (<a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4.4">RFC 6749 §4.4</a>).
 *
 * <p>Intended to be composed with {@link RefreshingBearerAuthProvider} to provide
 * caching + single-in-flight-refresh semantics:
 *
 * <pre>{@code
 * @Bean("userServiceAuthProvider")
 * AuthProvider userServiceAuthProvider(WebClient.Builder builder) {
 *     OAuth2ClientCredentialsTokenProvider tokenProvider =
 *             OAuth2ClientCredentialsTokenProvider.builder(builder.build())
 *                     .tokenUri("https://auth.example.com/oauth/token")
 *                     .clientId("user-service")
 *                     .clientSecret("...")
 *                     .scope("read:users")
 *                     .build();
 *     return new RefreshingBearerAuthProvider(tokenProvider);
 * }
 * }</pre>
 *
 * <p>Supports the two standard client-authentication schemes:
 * <ul>
 *   <li><b>HTTP Basic</b> (default) — {@code client_id:client_secret} in the
 *       {@code Authorization} header.</li>
 *   <li><b>Form post</b> — {@code client_id} / {@code client_secret} as form
 *       parameters; enable via {@code authStyle(AuthStyle.FORM_POST)}.</li>
 * </ul>
 *
 * <p>Optional {@code scope} and {@code audience} parameters are sent as form
 * fields when configured.
 *
 * <p>The resulting {@link AccessToken#expiresAt()} is derived from the server's
 * {@code expires_in} (seconds). When the server omits it, the token is treated
 * as non-expiring. A configurable {@code expiryLeeway} is subtracted from the
 * server's value to refresh slightly early (default 30 s).
 */
public final class OAuth2ClientCredentialsTokenProvider implements AccessTokenProvider {

    private static final int MAX_TOKEN_ERROR_BODY_CHARS = 1024;
    private static final Pattern JSON_SECRET_FIELD = Pattern.compile(
            "(?i)(\\x22(?:access_token|refresh_token|id_token|client_secret)\\x22\\s*:\\s*\\x22)((?:\\\\.|[^\\x22\\\\])*)(\\x22)");
    private static final Pattern NESTED_JSON_SECRET_FIELD = Pattern.compile(
            "(?i)(\\\\\\x22(?:access_token|refresh_token|id_token|client_secret)\\\\\\x22\\s*:\\s*\\\\\\x22)((?:\\\\\\.|[^\\\\\\x22\\\\\\\\])*)(\\\\\\x22)");
    private static final Pattern FORM_SECRET_FIELD = Pattern.compile(
            "(?i)((?:access_token|refresh_token|id_token|client_secret)=)([^&\s]+)");
    private static final Pattern COLON_SECRET_FIELD = Pattern.compile(
            "(?i)((?<![A-Za-z0-9_])(?:access_token|refresh_token|id_token|client_secret)\\s*:\\s*)"
                    + "(?:\\x22(?:\\\\.|[^\\x22\\\\])*\\x22|[^\\s,;<>]+)");
    private static final Pattern BASIC_AUTHORIZATION_FIELD = Pattern.compile(
            "(?i)(Authorization\\s*[:=]\\s*Basic\\s+)([^\\s,;<>]+)");
    private static final Pattern URL_ENCODED_SECRET_FIELD = Pattern.compile(
            "(?i)((?:access_token|refresh_token|id_token|client_secret)%3D)([^&\\s]+)");
    private static final Pattern URL_ENCODED_JSON_SECRET_FIELD = Pattern.compile(
            "(?i)(%22(?:access_token|refresh_token|id_token|client_secret)%22"
                    + "(?:%(?:20|09|0A|0D)|\\+)*%3A(?:%(?:20|09|0A|0D)|\\+)*%22)"
                    + "((?:(?:%5C%22)|(?!%22).)*)(%22)");
    /** Where the client credentials are carried in the token request. */
    public enum AuthStyle {
        /** {@code Authorization: Basic base64(client_id:client_secret)}. */
        BASIC_AUTH,
        /** {@code client_id} + {@code client_secret} as form-urlencoded body fields. */
        FORM_POST
    }

    private final WebClient webClient;
    private final AtomicReference<ExchangeStrategies> exchangeStrategies;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private final String audience;
    private final AuthStyle authStyle;
    private final Duration expiryLeeway;
    private final String diagnosticClientName;
    private final Pattern clientSecretPattern;

    private OAuth2ClientCredentialsTokenProvider(Builder b) {
        WebClient configuredWebClient = Objects.requireNonNull(b.webClient, "webClient");
        this.exchangeStrategies = new AtomicReference<>(ExchangeStrategies.withDefaults());
        this.webClient = configuredWebClient.mutate()
                .filter((request, next) -> next.exchange(request)
                        .doOnNext(response -> exchangeStrategies.set(response.strategies())))
                .build();
        this.tokenUri = requireNonBlank(b.tokenUri, "tokenUri");
        this.clientId = requireNonBlank(b.clientId, "clientId");
        this.clientSecret = requireNonBlank(b.clientSecret, "clientSecret");
        this.clientSecretPattern = jsonEscapedLiteralPattern(clientSecret);
        this.scope = b.scope;
        this.audience = b.audience;
        this.authStyle = b.authStyle != null ? b.authStyle : AuthStyle.BASIC_AUTH;
        this.diagnosticClientName = StringUtils.hasText(b.clientName) ? b.clientName : null;
        this.expiryLeeway = b.expiryLeeway != null ? b.expiryLeeway : Duration.ofSeconds(30);
        if (this.expiryLeeway.isNegative()) {
            throw new IllegalArgumentException("expiryLeeway must not be negative");
        }
    }

    public static Builder builder(WebClient webClient) {
        return new Builder(webClient);
    }

    @Override
    public Mono<AccessToken> fetchToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        if (StringUtils.hasText(scope)) {
            form.add("scope", scope);
        }
        if (StringUtils.hasText(audience)) {
            form.add("audience", audience);
        }

        WebClient.RequestHeadersSpec<?> spec;
        if (authStyle == AuthStyle.FORM_POST) {
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            spec = webClient.post().uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromFormData(form));
        } else {
            spec = webClient.post().uri(tokenUri)
                    .headers(h -> h.setBasicAuth(clientId, clientSecret))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromFormData(form));
        }

        return spec.retrieve()
                .bodyToMono(TokenResponse.class)
                .map(this::toAccessToken)
                .switchIfEmpty(Mono.defer(() -> Mono.error(missingAccessTokenFailure())))
                .onErrorMap(WebClientResponseException.class, this::tokenEndpointException)
                .onErrorMap(this::isTokenResponseDecodeFailure, this::malformedTokenResponseException);
    }

    private RuntimeException tokenEndpointException(WebClientResponseException responseException) {
        StringBuilder message = new StringBuilder("OAuth2 token endpoint returned HTTP ")
                .append(responseException.getStatusCode().value());
        String sanitizedBody = sanitizedBody(responseException.getResponseBodyAsString());
        String diagnosticBody = boundedDiagnosticBody(sanitizedBody);
        if (StringUtils.hasText(diagnosticBody)) {
            message.append("; responseBody=").append(diagnosticBody);
        }
        String diagnostic = message.toString();
        WebClientResponseException sanitizedCause = sanitizedResponseException(responseException, sanitizedBody);
        return authHttpFailure(diagnostic, sanitizedCause);
    }

    private RuntimeException malformedTokenResponseException(Throwable cause) {
        String safeCauseMessage = cause instanceof UnsupportedMediaTypeException
                ? "Unsupported OAuth2 token response content type"
                : "OAuth2 token response decoding failed";
        return authFailure(
                "OAuth2 token endpoint returned malformed JSON token response",
                new IllegalStateException(safeCauseMessage));
    }

    private boolean isTokenResponseDecodeFailure(Throwable error) {
        return error instanceof CodecException || error instanceof UnsupportedMediaTypeException;
    }

    private RuntimeException authFailure(String message) {
        return authFailure(message, null);
    }

    private RuntimeException authFailure(String message, Throwable cause) {
        if (StringUtils.hasText(diagnosticClientName)) {
            return cause == null
                    ? new AuthProviderException(diagnosticClientName, message)
                    : new AuthProviderException(diagnosticClientName, message, cause);
        }
        return cause == null ? new OAuth2TokenFailureException(message) : new OAuth2TokenFailureException(message, cause);
    }

    private RuntimeException authHttpFailure(String message, WebClientResponseException cause) {
        if (StringUtils.hasText(diagnosticClientName)) {
            return new AuthProviderException(diagnosticClientName, message, cause);
        }
        return new SanitizedOAuth2HttpFailure(message, cause);
    }

    private WebClientResponseException sanitizedResponseException(WebClientResponseException source,
                                                                  String sanitizedBody) {
        byte[] body = StringUtils.hasText(sanitizedBody)
                ? sanitizedBody.getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        HttpHeaders sanitizedHeaders = new HttpHeaders();
        source.getHeaders().forEach((name, values) -> {
            if (isBodyMetadataHeader(name)) {
                return;
            }
            if (isSensitiveTokenHeader(name)) {
                sanitizedHeaders.set(name, "<redacted>");
            } else {
                values.forEach(value -> sanitizedHeaders.add(name, sanitizedBody(value)));
            }
        });
        MediaType contentType = source.getHeaders().getContentType();
        if (contentType != null) {
            sanitizedHeaders.setContentType(
                    new MediaType(contentType, StandardCharsets.UTF_8));
        }
        if (source.getHeaders().get(HttpHeaders.CONTENT_LENGTH) != null) {
            sanitizedHeaders.setContentLength(body.length);
        }
        WebClientResponseException sanitized = WebClientResponseException.create(
                source.getStatusCode(),
                source.getStatusText(),
                sanitizedHeaders,
                body,
                StandardCharsets.UTF_8,
                null);
        ExchangeStrategies strategies = exchangeStrategies.get();
        sanitized.setBodyDecodeFunction(targetType -> decodeSanitizedBody(sanitized, targetType, strategies));
        return sanitized;
    }

    private static boolean isBodyMetadataHeader(String name) {
        return HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)
                || HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name);
    }

    private static boolean isSensitiveTokenHeader(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).replace('-', '_');
        return SensitiveHeaders.isSensitive(name)
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("credential");
    }

    private String sanitizedBody(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return "";
        }
        String sanitized = normalizeJsonFieldNameEscapes(responseBody);
        String basicCredential = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.ISO_8859_1));
        sanitized = sanitized.replace(basicCredential, "<redacted>");
        String encodedBasicCredential = UriUtils.encode(basicCredential, StandardCharsets.UTF_8);
        sanitized = percentEncodedLiteralPattern(encodedBasicCredential)
                .matcher(sanitized)
                .replaceAll("<redacted>");
        sanitized = BASIC_AUTHORIZATION_FIELD.matcher(sanitized).replaceAll("$1<redacted>");
        sanitized = URL_ENCODED_SECRET_FIELD.matcher(sanitized).replaceAll("$1<redacted>");
        sanitized = URL_ENCODED_JSON_SECRET_FIELD.matcher(sanitized).replaceAll("$1<redacted>$3");
        sanitized = NESTED_JSON_SECRET_FIELD.matcher(sanitized).replaceAll("$1<redacted>$3");
        sanitized = JSON_SECRET_FIELD.matcher(sanitized).replaceAll("$1<redacted>$3");
        sanitized = COLON_SECRET_FIELD.matcher(sanitized).replaceAll("$1<redacted>");
        sanitized = clientSecretPattern.matcher(sanitized).replaceAll("<redacted>");
        sanitized = FORM_SECRET_FIELD.matcher(sanitized).replaceAll("$1<redacted>");
        return sanitized.replace("\r", " ").replace("\n", " ").strip();
    }

    private String boundedDiagnosticBody(String sanitizedBody) {
        if (sanitizedBody.length() <= MAX_TOKEN_ERROR_BODY_CHARS) {
            return sanitizedBody;
        }
        return sanitizedBody.substring(0, MAX_TOKEN_ERROR_BODY_CHARS) + "...(truncated)";
    }

    private static Pattern percentEncodedLiteralPattern(String encodedValue) {
        StringBuilder regex = new StringBuilder(encodedValue.length() * 2);
        for (int i = 0; i < encodedValue.length();) {
            if (encodedValue.charAt(i) == '%' && i + 2 < encodedValue.length()
                    && Character.digit(encodedValue.charAt(i + 1), 16) >= 0
                    && Character.digit(encodedValue.charAt(i + 2), 16) >= 0) {
                regex.append("%(?i:")
                        .append(encodedValue, i + 1, i + 3)
                        .append(')');
                i += 3;
            } else {
                regex.append(Pattern.quote(String.valueOf(encodedValue.charAt(i))));
                i++;
            }
        }
        return Pattern.compile(regex.toString());
    }

    private static Pattern jsonEscapedLiteralPattern(String value) {
        StringBuilder regex = new StringBuilder(value.length() * 20);
        regex.append("(?<![A-Za-z0-9_])");
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            regex.append("(?:")
                    .append(Pattern.quote(String.valueOf(character)))
                    .append("|\\\\u(?i:");
            appendFourDigitHex(regex, character);
            regex.append("))");
        }
        regex.append("(?![A-Za-z0-9_])");
        return Pattern.compile(regex.toString());
    }

    private static void appendFourDigitHex(StringBuilder target, char character) {
        for (int shift = 12; shift >= 0; shift -= 4) {
            target.append(Character.forDigit(character >> shift & 0xf, 16));
        }
    }

    private static String normalizeJsonFieldNameEscapes(String value) {
        StringBuilder normalized = new StringBuilder(value.length());
        int cursor = 0;
        while (cursor < value.length()) {
            int openingQuote = value.indexOf('"', cursor);
            if (openingQuote < 0) {
                normalized.append(value, cursor, value.length());
                break;
            }
            normalized.append(value, cursor, openingQuote + 1);
            int closingQuote = jsonStringEnd(value, openingQuote + 1);
            if (closingQuote < 0) {
                normalized.append(value, openingQuote + 1, value.length());
                break;
            }
            String stringValue = value.substring(openingQuote + 1, closingQuote);
            int next = closingQuote + 1;
            while (next < value.length() && Character.isWhitespace(value.charAt(next))) {
                next++;
            }
            normalized.append(next < value.length() && value.charAt(next) == ':'
                    ? normalizeSafeJsonUnicodeEscapes(stringValue, 1)
                    : normalizeNestedJsonFieldNameEscapes(stringValue));
            normalized.append('"');
            cursor = closingQuote + 1;
        }
        return normalized.toString();
    }

    private static String normalizeNestedJsonFieldNameEscapes(String value) {
        StringBuilder normalized = new StringBuilder(value.length());
        int cursor = 0;
        while (cursor < value.length()) {
            int openingQuote = nestedJsonQuote(value, cursor);
            if (openingQuote < 0) {
                normalized.append(value, cursor, value.length());
                break;
            }
            normalized.append(value, cursor, openingQuote + 1);
            int closingQuote = nestedJsonQuote(value, openingQuote + 1);
            if (closingQuote < 0) {
                normalized.append(value, openingQuote + 1, value.length());
                break;
            }
            String stringValue = value.substring(openingQuote + 1, closingQuote);
            int next = closingQuote + 1;
            while (next < value.length() && Character.isWhitespace(value.charAt(next))) {
                next++;
            }
            normalized.append(next < value.length() && value.charAt(next) == ':'
                    ? normalizeSafeJsonUnicodeEscapes(stringValue, 2)
                    : stringValue);
            normalized.append('"');
            cursor = closingQuote + 1;
        }
        return normalized.toString();
    }

    private static int nestedJsonQuote(String value, int start) {
        for (int i = start; i < value.length(); i++) {
            if (value.charAt(i) != '"') {
                continue;
            }
            int slashCount = 0;
            for (int j = i - 1; j >= 0 && value.charAt(j) == '\\'; j--) {
                slashCount++;
            }
            if (slashCount % 4 == 1) {
                return i;
            }
        }
        return -1;
    }

    private static int jsonStringEnd(String value, int start) {
        boolean escaped = false;
        for (int i = start; i < value.length(); i++) {
            char current = value.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == '"') {
                return i;
            }
        }
        return -1;
    }

    private static String normalizeSafeJsonUnicodeEscapes(String value, int slashUnit) {
        StringBuilder normalized = new StringBuilder(value.length());
        int cursor = 0;
        while (cursor < value.length()) {
            if (value.charAt(cursor) != '\\') {
                normalized.append(value.charAt(cursor++));
                continue;
            }
            int escapeStart = cursor;
            while (cursor < value.length() && value.charAt(cursor) == '\\') {
                cursor++;
            }
            // Each containing JSON string doubles the slashes that encode a nested Unicode escape.
            int slashCount = cursor - escapeStart;
            boolean unicodeEscape = slashCount % (slashUnit * 2) == slashUnit
                    && cursor + 5 <= value.length()
                    && value.charAt(cursor) == 'u';
            int decoded = unicodeEscape ? parseFourDigitHex(value, cursor + 1) : -1;
            boolean safeFieldCharacter = decoded == '_' || decoded >= 'A' && decoded <= 'Z'
                    || decoded >= 'a' && decoded <= 'z';
            if (safeFieldCharacter) {
                normalized.append(value, escapeStart, cursor - slashUnit).append((char) decoded);
                cursor += 5;
            } else {
                normalized.append(value, escapeStart, cursor);
            }
        }
        return normalized.toString();
    }

    private static int parseFourDigitHex(String value, int start) {
        int decoded = 0;
        for (int i = start; i < start + 4; i++) {
            int digit = Character.digit(value.charAt(i), 16);
            if (digit < 0) {
                return -1;
            }
            decoded = decoded << 4 | digit;
        }
        return decoded;
    }

    private AccessToken toAccessToken(TokenResponse response) {
        String value = response.access_token;
        if (!StringUtils.hasText(value)) {
            throw missingAccessTokenFailure();
        }
        Instant expiresAt = null;
        if (response.expires_in != null && response.expires_in > 0) {
            long effectiveSeconds = Math.max(0L, response.expires_in - expiryLeeway.getSeconds());
            expiresAt = Instant.now().plusSeconds(effectiveSeconds);
        }
        return new AccessToken(value, expiresAt);
    }

    private RuntimeException missingAccessTokenFailure() {
        return authFailure(
                "OAuth2 token endpoint returned no access_token",
                new IllegalStateException("missing access_token"));
    }

    private static String requireNonBlank(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static Object decodeSanitizedBody(WebClientResponseException response,
                                              ResolvableType targetType,
                                              ExchangeStrategies exchangeStrategies) {
        byte[] body = response.getResponseBodyAsByteArray();
        if (body.length == 0) {
            return null;
        }
        MediaType contentType = response.getHeaders().getContentType();
        for (HttpMessageReader<?> reader : exchangeStrategies.messageReaders()) {
            if (reader.canRead(targetType, contentType)
                    && reader instanceof DecoderHttpMessageReader<?> decoderReader) {
                Decoder<?> decoder = decoderReader.getDecoder();
                return decoder.decode(
                        DefaultDataBufferFactory.sharedInstance.wrap(body),
                        targetType,
                        contentType,
                        Collections.emptyMap());
            }
        }
        throw new IllegalStateException("No suitable decoder");
    }

    private static final class SanitizedOAuth2HttpFailure extends IllegalStateException
            implements SanitizedAuthProviderFailure {

        private SanitizedOAuth2HttpFailure(String message, WebClientResponseException cause) {
            super(message, cause);
        }

        @Override
        public String sanitizedAuthMessage() {
            return getMessage();
        }

        @Override
        public Throwable sanitizedAuthCause() {
            return getCause();
        }
    }

    /**
     * Shape of a successful OAuth2 token response. Field names intentionally
     * mirror the on-wire JSON keys.
     */
    @SuppressWarnings({"checkstyle:MemberName", "unused"})
    static final class TokenResponse {
        public String access_token;
        public String token_type;
        public Long expires_in;
        public String scope;
    }

    private static final class OAuth2TokenFailureException extends IllegalStateException
            implements SanitizedAuthProviderFailure {

        private OAuth2TokenFailureException(String message) {
            super(message);
        }

        private OAuth2TokenFailureException(String message, Throwable cause) {
            super(message, cause);
        }

        @Override
        public String sanitizedAuthMessage() {
            return getMessage();
        }

        @Override
        public Throwable sanitizedAuthCause() {
            return this;
        }
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static final class Builder {
        private final WebClient webClient;
        private String tokenUri;
        private String clientId;
        private String clientSecret;
        private String scope;
        private String audience;
        private AuthStyle authStyle;
        private String clientName;
        private Duration expiryLeeway;

        private Builder(WebClient webClient) {
            this.webClient = webClient;
        }

        public Builder tokenUri(String tokenUri) { this.tokenUri = tokenUri; return this; }
        public Builder clientId(String clientId) { this.clientId = clientId; return this; }
        public Builder clientSecret(String clientSecret) { this.clientSecret = clientSecret; return this; }
        public Builder scope(String scope) { this.scope = scope; return this; }
        public Builder audience(String audience) { this.audience = audience; return this; }
        public Builder clientName(String clientName) { this.clientName = clientName; return this; }
        public Builder authStyle(AuthStyle authStyle) { this.authStyle = authStyle; return this; }

        /** How much time to subtract from the server's {@code expires_in} when setting {@link AccessToken#expiresAt()}. */
        public Builder expiryLeeway(Duration expiryLeeway) { this.expiryLeeway = expiryLeeway; return this; }

        public OAuth2ClientCredentialsTokenProvider build() {
            return new OAuth2ClientCredentialsTokenProvider(this);
        }

        /**
         * Convenience factory for tests — lets a test supply a pre-built
         * {@code Map<String, Object>} response via a stub WebClient.
         */
        public Map<String, Object> debugConfig() {
            return Map.of(
                    "tokenUri", tokenUri,
                    "clientId", clientId,
                    "scope", scope == null ? "" : scope,
                    "audience", audience == null ? "" : audience,
                    "authStyle", authStyle == null ? AuthStyle.BASIC_AUTH : authStyle
            );
        }
    }
}
interface SanitizedAuthProviderFailure {
    String sanitizedAuthMessage();
    Throwable sanitizedAuthCause();
}
