# Outbound Auth Providers

Every registered client can have its own auth provider that injects credentials into outbound requests automatically via a WebClient filter. Use `reactive.http.clients.<name>.auth-provider` to reference a custom `AuthProvider` bean by name, or use the object-style `reactive.http.clients.<name>.auth` block for built-in providers.

If both are configured, `auth-provider` wins and the object-style `auth` block is ignored. The starter logs a startup warning so the precedence is visible.

---

## `AuthProvider` interface

```java
@FunctionalInterface
public interface AuthProvider {
    Mono<AuthContext> getAuth(AuthRequest request);
}
```

`AuthContext` carries headers and query parameters to add to the outbound request:

```java
AuthContext.builder()
    .header("Authorization", "Bearer " + token)
    .queryParam("api_key", key)
    .build();
```

`AuthRequest` exposes the outgoing `ClientRequest` and the raw request body bytes (when available) for signing use cases.

---

## Simple bearer-token provider

Register any lambda or class as a Spring bean and reference it by name:

```yaml
reactive:
  http:
    clients:
      user-service:
        auth-provider: userServiceAuthProvider
```

```java
@Bean("userServiceAuthProvider")
AuthProvider userServiceAuthProvider(TokenService tokenService) {
    return request -> tokenService.getAccessToken()
            .map(token -> AuthContext.builder()
                    .header("Authorization", "Bearer " + token)
                    .build());
}
```

---

## `RefreshingBearerAuthProvider` — cached token with auto-refresh

`RefreshingBearerAuthProvider` wraps any `AccessTokenProvider` and adds:

- A cached token value, refreshed only when it enters the refresh window
- Deduplication of concurrent refresh calls (single in-flight token fetch)
- Cache invalidation on HTTP 401 — the outbound auth filter calls `invalidate()` and retries once
- Support for non-expiring tokens (`expiresAt = null`)
- A configurable failure cooldown to avoid hammering a failing token endpoint

```java
@Bean("userServiceAuthProvider")
AuthProvider userServiceAuthProvider(TokenService tokenService) {
    return new RefreshingBearerAuthProvider(
            () -> tokenService.getAccessToken()
                    .map(resp -> new AccessToken(
                            resp.accessToken(),
                            Instant.now().plusSeconds(resp.expiresInSeconds())
                    )),
            Duration.ofSeconds(60)   // refresh 60 s before expiry
    );
}
```

### Customizing refresh skew and failure cooldown

```java
new RefreshingBearerAuthProvider(
        accessTokenProvider,
        Duration.ofSeconds(30),   // refreshSkew: refresh when < 30 s remain
        Duration.ofSeconds(10)    // failureCooldown: wait 10 s before retrying a failed refresh
)
```

---

## `OAuth2ClientCredentialsTokenProvider` — standard OAuth 2.0 client credentials

For standard OAuth 2.0 client-credentials flows, use the built-in object-style provider:

```yaml
reactive:
  http:
    clients:
      payment-service:
        base-url: https://api.example.com
        auth:
          type: oauth2-client-credentials
          oauth2-client-credentials:
            token-uri: https://auth.example.com/oauth/token
            client-id: ${PAYMENT_CLIENT_ID}
            client-secret: ${PAYMENT_CLIENT_SECRET}
            scope: payments:read payments:write
            audience: https://api.example.com/
            auth-style: form-post    # basic-auth by default; use form-post when the token server requires body credentials
            expiry-leeway-ms: 60000  # subtract 60s from expires_in before caching
```

`auth-style: basic-auth` sends credentials in the token request `Authorization`
header. `auth-style: form-post` sends `client_id` and `client_secret` as
form-encoded token request fields for providers that require RFC 6749 body
credentials.

The token provider maps `expires_in` to an `AccessToken` expiry timestamp after
subtracting `expiry-leeway-ms`. `RefreshingBearerAuthProvider` caches the token,
deduplicates concurrent refreshes, and refreshes before the cached token enters
its refresh window. If a downstream API returns `401` and the auth provider is
invalidatable, the outbound auth filter invalidates the cached bearer token and
retries the request once with a fresh token.

Token endpoint failures are reported as `AuthProviderException`. HTTP 4xx/5xx
responses include the status and a bounded, redacted response-body snippet.
Malformed token JSON and missing `access_token` responses use fixed diagnostic
messages. Client secrets, access tokens, and refresh tokens are not included in
exception messages.

For manual bean wiring, compose `OAuth2ClientCredentialsTokenProvider` with `RefreshingBearerAuthProvider`:

```java
@Bean("userServiceAuthProvider")
AuthProvider userServiceAuthProvider(WebClient.Builder builder) {
    OAuth2ClientCredentialsTokenProvider tokenProvider =
            OAuth2ClientCredentialsTokenProvider.builder(builder.build())
                    .tokenUri("https://auth.example.com/oauth/token")
                    .clientId("user-service")
                    .clientSecret("...")
                    .scope("read:users")
                    // .audience("https://api.example.com/")        // optional
                    // .authStyle(AuthStyle.FORM_POST)              // default: BASIC_AUTH
                    // .expiryLeeway(Duration.ofSeconds(30))        // refresh slightly early
                    .build();
    return new RefreshingBearerAuthProvider(tokenProvider);
}
```

Supported authentication styles:

| `AuthStyle` | Description |
|---|---|
| `BASIC_AUTH` (default) | Client credentials sent as HTTP Basic auth |
| `FORM_POST` | Client credentials sent as form-encoded body parameters |

---

## AWS SigV4 provider

Use `type: aws-sigv4` to sign requests with AWS Signature Version 4. The provider signs the HTTP method, URI, query string, headers, and raw request body bytes when the starter has serialized them for auth.

Supported body-signing contract:

| Body shape | Built-in SigV4 behavior |
|---|---|
| Empty body | Signs the AWS empty SHA-256 payload hash. |
| `byte[]` | Signs the exact byte array sent by the starter. |
| `String` | Signs bytes using the request `Content-Type` charset when one is declared; otherwise UTF-8. |
| JSON object body | Signs the JSON bytes serialized by the starter auth pipeline with the configured `ObjectMapper`; keep WebClient codecs aligned with that mapper. |
| `Publisher`, streaming upload body, or multipart body | Rejected before the request is sent; the starter does not buffer or subscribe to the stream only for signing. |

`Publisher`, streaming, and multipart request bodies are not signed by the built-in provider because stable raw bytes are not materialized without consuming or re-encoding the body. Use a repeatable `byte[]`, charset-declared `String`, or JSON object body with codecs aligned to the starter `ObjectMapper` for built-in signing, or provide a custom auth provider that implements AWS streaming signatures.

```yaml
reactive:
  http:
    clients:
      inventory-api:
        base-url: https://abc123.execute-api.us-east-1.amazonaws.com/prod
        auth:
          type: aws-sigv4
          aws-sig-v4:
            access-key-id: ${AWS_ACCESS_KEY_ID}
            secret-access-key: ${AWS_SECRET_ACCESS_KEY}
            session-token: ${AWS_SESSION_TOKEN:}
            region: us-east-1
            service: execute-api
```

Out of scope: SigV4a, STS assume-role flow, and pre-signed URLs.

---

## HMAC / request-signing provider

For body-signing use cases, access the raw payload bytes via `AuthRequest.REQUEST_RAW_BODY_ATTRIBUTE`. Fall back to `request.requestBody()` when raw bytes are absent:

```java
@Bean("hmacAuthProvider")
AuthProvider hmacAuthProvider(HmacSigner signer) {
    return request -> Mono.fromSupplier(() -> {
        byte[] payload = request.request()
                .attribute(AuthRequest.REQUEST_RAW_BODY_ATTRIBUTE)
                .map(byte[].class::cast)
                .orElseGet(() ->
                    Objects.toString(request.requestBody(), "")
                           .getBytes(StandardCharsets.UTF_8));
        String signature = signer.sign(payload);
        return AuthContext.builder()
                .header("X-Signature", signature)
                .build();
    });
}
```

---

## API-key provider

```java
@Bean("partnerApiAuthProvider")
AuthProvider partnerApiKeyProvider(@Value("${partner.api-key}") String apiKey) {
    return request -> Mono.just(
            AuthContext.builder()
                    .header("X-Api-Key", apiKey)
                    .build());
}
```

---

## Custom `InvalidatableAuthProvider`

If your provider maintains internal cache state, implement `InvalidatableAuthProvider` so the outbound auth filter can invalidate the cache on a 401 and retry the request once:

```java
public class MyAuthProvider implements InvalidatableAuthProvider {

    @Override
    public Mono<AuthContext> getAuth(AuthRequest request) { ... }

    @Override
    public Mono<Void> invalidate() {
        // clear cached token
        return Mono.empty();
    }
}
```

`RefreshingBearerAuthProvider` already implements this interface.
