# V28 Semantic-Read Baseline

Recorded on 2026-08-27 against the published `4.0.0` contract and the
`4.1.0-SNAPSHOT` V28 implementation.

## Published `4.0.0` Behavior

- Cache policy definitions are inert. A method is selected only by a non-blank
  client `cache.policy` or method-level `@CacheResponse`; `@CacheDisabled` wins.
- Selected `GET` methods pass the verb gate. Selected `POST`, `PUT`, `PATCH`,
  `DELETE`, `HEAD`, `OPTIONS`, and non-GET `@ApiRef` methods fail startup before
  cache allocation, auth, body acquisition, or dispatch.
- Method policy selection overrides client selection. Inherited methods,
  overloads, and configured `@ApiRef` methods use their concrete resolved verb.
- Effective contracts expose policy source and bounds. Diagnostics, AOT
  processing, and `MockReactiveHttpClient` use the same startup validation.
- Only finite `Mono<T>` and `Mono<ResponseEntity<T>>` values can proceed. The
  existing streaming, unresolved, multipart, application-owned body, key,
  auth, and customization-safety rejections remain independent of the verb.

## V28 Additive Contract

The selected spelling is:

```java
@POST("/catalog/search")
@CacheResponse(value = "catalog-read", semanticRead = true)
Mono<CatalogItem> search(@QueryParam("sku") String sku);
```

`semanticRead` defaults to `false` and belongs to one method annotation. A
client-wide policy cannot set it. On a non-GET method it is an application
guarantee that a cache hit may omit downstream dispatch without omitting a
required side effect. It does not imply idempotency, retry/replay safety, or
write invalidation.

The acknowledgement applies only after the effective HTTP method resolves. A
body-bearing semantic non-`GET` read must select its `@Body @CacheKey` label in
`vary-by-parameters`, which keys the prepared wire bytes; `shared-response`
cannot waive this request identity.

The V28 selection matrix is therefore:

| Selection | Resolved verb | Method acknowledgement | Result |
|---|---|---|---|
| none or `@CacheDisabled` | any | any | ordinary request path |
| client or method | `GET` | absent/false | existing cache eligibility checks |
| client | non-`GET` | absent | startup rejection |
| method | non-`GET` | absent/false | startup rejection |
| method | non-`GET` | `semanticRead = true` | all existing cache eligibility checks |

Startup errors identify the client, concrete and declaring method, resolved
verb, policy name/source, and the method-specific correction without printing
request values.
