# Spring Boot 4 and Jackson 3 Migration

The future starter `3.x` line uses Jackson 3 for starter-owned JSON
materialization. Spring Boot 3.5 maintenance releases continue to use Jackson 2.

## Codec ownership

Starter extension points use `ReactiveHttpClientJsonCodec`:

```java
@Bean
ReactiveHttpClientJsonCodec reactiveHttpClientJsonCodec(
        tools.jackson.databind.ObjectMapper objectMapper) {
    return new Jackson3ReactiveHttpClientJsonCodec(objectMapper);
}
```

The Boot 4 auto-configuration supplies this adapter from the application
Jackson 3 mapper. Custom naming strategies, Java time support, Kotlin modules,
custom serializers, and unknown-property behavior therefore come from the same
application mapper. Applications may replace the codec bean when they need a
different serialization implementation.

For authenticated JSON requests, the codec materializes one `byte[]`. That
same array is passed to the auth provider as the raw signing body and to
WebClient as the outbound body. SigV4 therefore hashes the bytes sent on the
wire. String bodies use the charset declared by `Content-Type`, defaulting to
UTF-8.

Problem Detail mapping also consumes `ReactiveHttpClientJsonCodec`. OAuth2 token
and sanitized OAuth error decoding continue through the configured WebClient
codecs, preserving typed decoding and response metadata. Diagnostic and
contract snapshots use starter-owned deterministic renderers and do not require
an application Jackson mapper.

## Source migration

The following Jackson 2 entry points remain temporary deprecated compatibility
shims:

- `ProblemDetailErrorResponseMapper(com.fasterxml.jackson.databind.ObjectMapper)`
- `MockReactiveHttpClient.Builder.objectMapper(...)`
- Public `ReactiveClientInvocationHandler` constructors accepting Jackson 2

New code should inject `ReactiveHttpClientJsonCodec`, use
`ProblemDetailErrorResponseMapper(ReactiveHttpClientJsonCodec)`, and configure
mock clients with `jsonCodec(...)`. `Jackson2ReactiveHttpClientJsonCodec` is
available only for Boot 3/source migration and is deprecated. Boot 4 marks its
Jackson 2 dependency optional; default consumers use Jackson 3 and do not need a
Jackson 2 mapper.
