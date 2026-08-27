# V28 Body-Bearing Request Identity Audit

Recorded on 2026-08-27 against the `4.1.0-SNAPSHOT` V28 implementation.

## Preparation and Wire Ownership

Every acknowledged body-bearing non-`GET` semantic read must select its
`@Body @CacheKey` label. Per subscription, the invocation path freezes the
selected arguments, resolves request/default headers, and prepares one bounded
body representation before authorization or cache lookup:

| Body shape | Prepared representation | Bound |
|---|---|---|
| null | absent-body marker, no bytes | constant |
| `byte[]` | defensive bounded byte copy | cumulative element and 1 MiB limits before copy |
| `String` | bytes encoded with the effective declared charset | encoded length before allocation |
| finite JSON value | `ReactiveHttpClientJsonCodec.writeBounded(...)` bytes | 1 MiB while encoding |

The body identity frame contains body presence, normalized effective
`Content-Type` including charset, and the prepared bytes. The canonical writer
hashes that frame into the opaque key. The same prepared bytes are exposed to
built-in/custom auth through the raw-body attribute and are supplied to the
final WebClient body writer; the value is not serialized again for dispatch.

## Mutation Boundaries

- Method arguments, default headers, and dynamic declarative headers resolve
  before preparation. An absent content type on a present supported body has
  the existing effective `application/json` default; an absent body retains no
  implicit content type.
- Invalid or blank dynamic content types fail before lookup. `String` charsets
  are resolved from the same normalized media type represented in the key.
- A pre-lookup `AuthProvider` may return the already prepared content type but
  cannot replace it. A replacement fails before lookup or dispatch because the
  body identity has already been fixed.
- Boot and per-client builder customizations remain subject to the existing
  explicit `SAFE` classification. That classification is the application
  guarantee that filters, default requests, codecs, connectors, and exchange
  functions do not mutate body/key semantics after preparation.
- A JSON codec that cannot enforce `writeBounded(...)` fails before dispatch.
  Application-owned streams, publishers, resources, data buffers, and multipart
  bodies remain ineligible rather than being aggregated for a key.

## Lifetime and Privacy

Frozen arguments and prepared bytes are subscription-local. A miss leader or
refresh owns them until its load terminates; joined waiters do not create a
second representation. A hit releases its preparation after lookup. Timeout,
cancellation, auth rejection, serialization failure, eviction, and shutdown do
not publish an entry. Completed flights are removed, and cache entries retain
only the SHA-256 opaque key and decoded response value.

Raw body bytes, selected values, and digest text are not exposed through
exceptions, lifecycle records, exchange logs, metrics, traces, diagnostics,
health output, or support fixtures. Effective content type remains ordinary
structural request metadata. The existing cumulative element, depth, and
projection bounds apply while freezing selected arguments, and the serialized
representation is capped before defensive request/key copies.
