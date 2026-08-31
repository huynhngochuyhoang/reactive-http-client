# V29 Retained-Weight Candidate Evaluation

Recorded on 2026-08-31 against commit
`fd762589f038fd41ee85856dd78576d02cd1a23e` and the
`4.2.0-SNAPSHOT` response-cache implementation.

## Scope and Current Boundary

This is the Priority 4.1 design spike, not the Priority 4.3 go/no-go decision.
It adds no property, SPI, meter, diagnostic field, or production value traversal.

The current successful unary path is:

```text
final response body DataBuffer stream
  -> WebClient codec
    -> decoded Mono value
      -> LocalResponseCacheManager.cacheCandidate(...)
        -> optional sanitized ResponseEntity reconstruction
          -> LocalResponseCache.publish(...)
            -> Caffeine CachedEntry(value, writtenNanos)
```

The cache retains decoded object identity. It does not retain the response bytes
from which an arbitrary DTO was decoded. `ResponseEntity<T>` entries additionally
retain only the allowlisted representation headers copied by `cacheCandidate`.
An empty completion creates no entry; a non-null empty value is an ordinary
entry.

## Candidate Matrix

| Candidate boundary | Measurement and unit | Known | Deterministic cost and owner | Overflow/unknown behavior required by a later contract | Relationship to retained value | 4.1 result |
|---|---|---|---|---|---|---|
| Response decode | Count readable bytes consumed by the final successful unary codec path; for a retained `ResponseEntity`, add a canonical UTF-8 count of the representation-header names and values that are actually copied. Unit: decoded representation bytes. | Body count is final when unary decoding completes; retained-header count is final after `cacheCandidate` sanitizes the entity and before publication. | One checked addition per body buffer and bounded header text; constant retained counter state, no body copy, content inspection, or value traversal. Starter owns the counter only through terminal publication. | Checked `long` arithmetic must make overflow explicit. Decode failure, cancellation, and empty completion publish nothing. An unavailable count must remain unknown, never zero or a constant estimate. | Exact for the consumed decoded representation, but not JVM heap, object-graph size, RSS, or allocator overhead. It correlates with input representation rather than arbitrary decoder expansion. | **Survives** as a generic non-heap candidate, subject to proving that the count can be attached without response duplication or changing codec/filter behavior. |
| Cache publication/value inspection | Inspect the decoded candidate immediately before `publish`; possible units include array bytes, characters, collection elements, or type-specific logical units. | After `cacheCandidate` has accepted or reconstructed the final stored value. | O(1) only for a few shapes such as `byte[].length`; strings require a chosen encoding, and object/collection graphs require traversal or a supplied estimate. | Unsupported shapes must be unknown. Mixed type-specific units cannot share one aggregate budget. Checked arithmetic is still required. | Exact payload length for `byte[]`, but no general relationship exists for strings, DTOs, records, collections, shared graphs, or custom values. | **Rejected as a generic built-in unit.** It remains useful only for an explicitly narrowed supported-value contract. |
| Starter-owned bytes | Count the 32-byte opaque digest plus bounded textual bytes copied for retained response headers and any other representation the starter itself keeps. Unit: starter-owned logical bytes. | At key derivation and accepted-candidate reconstruction. | Deterministic, bounded, and starter-owned; no decoded-value inspection. | Checked addition is straightforward; all supported entries are measurable. | Omits the usually dominant decoded body graph, so a 1-byte DTO and a 100 MiB DTO can receive nearly the same weight. It also is not exact Java heap because array/object overhead is excluded. | **Rejected as a response-retention budget.** It can describe cache metadata overhead only. |
| Application-supplied weigher | Invoke a narrowly scoped application function on the accepted stored candidate and return a non-negative application-defined weight in one policy-specific unit. | After response/header eligibility and `ResponseEntity` sanitization, before generation-checked publication or refresh replacement. | Application owns calculation semantics and cost. A future contract would have to prohibit blocking, serialization, reflection walking, mutation, and unbounded work on the subscriber thread. | Exceptions, negative values, unknown values, and checked overflow need explicit admission semantics in 4.2. The starter cannot silently repair them. | Can model an application-known retained estimate for custom DTOs and sanitized entity headers, but it is not independently verifiable as heap or bytes by the starter. | **Survives only as an explicitly selected application-owned estimate**, never as the default or as a heap claim. |

The two surviving candidates are alternatives, not a combined design decision.
Priority 4.2 must define admission and accounting semantics before Priority 4.3
can select either candidate or record a no-go.

## Supported-Shape Comparison

| Existing cacheable result | Decode representation bytes | Publication inspection | Application weigher | Retained-state note |
|---|---|---|---|---|
| Plain `byte[]` | Exact consumed decoded body bytes | Exact array payload length | May use array length or another declared estimate | Neither count includes Java array/header overhead. |
| `String`, scalar, enum, record, DTO, collection, map, or other finite value | Exact consumed decoded representation bytes, independent of Java type | No common unit without encoding or graph traversal | May use a bounded domain estimate | Decoder expansion, sharing, compact strings, collection capacity, and nested objects make representation bytes differ from heap. |
| `ResponseEntity<T>` | Body representation bytes plus canonical bytes for only the copied representation headers | Body has the same limitations as plain `T`; headers can be counted only after sanitization | Must receive the sanitized candidate if headers contribute | Status and Java container overhead remain outside a logical-byte unit. Sensitive, custom non-cacheable, redirect, or oversized-header responses are not stored. |
| Empty completion | No entry and no admission weight | No value exists to inspect | Must not invoke a weigher | The load still has terminal metrics but contributes no retained entry. |
| Present empty value such as `""`, an empty collection, or `Optional.empty()` | Counts the actual representation consumed; it can legitimately be zero for a custom codec | Type-specific inspection can report zero, but zero-entry semantics remain undecided | Weigher result follows the future zero-weight rule | The decoded object is still retained even when its semantic content is empty. |
| Refresh replacement | Count the replacement response independently before the atomic swap | Inspect the accepted replacement candidate | Invoke once for the accepted replacement candidate | Old value/weight must remain authoritative until replacement publication succeeds. Accounting is Priority 4.2 work. |
| Unknown or custom finite value | Still measurable when it is produced from the final body stream | Unknown without a narrowed built-in contract | Measurable only when the selected weigher supports it | Unknown must not silently become zero or a constant. |

## Rejected Measurement Sources

### `Content-Length`

`Content-Length` is advertised framing metadata, not consumed decoded bytes. It
can be absent for chunked responses, describe compressed bytes, disagree with
the body, or be changed by an intermediate filter. It excludes retained headers
and says nothing about decoder expansion. It is rejected as either a cache
weight or a fallback for an unknown count.

### Compressed wire bytes

TLS records, HTTP framing, and compressed payload bytes measure transport work.
Reactor Netty decompression occurs before WebClient's decoded unary aggregate.
The same decoded value can have very different compressed lengths, and a small
compressed body can expand substantially. Wire bytes are rejected as retained
response weight.

### Arbitrary JSON reserialization

Serializing a decoded value solely to weigh it allocates another complete
representation, can run application serializers or getters, can differ from the
received representation, and does not cover non-JSON/custom-codec values. It can
block or consume unbounded CPU on a subscriber or event-loop thread. It is
rejected even when `ReactiveHttpClientJsonCodec` is available.

### Reflection-based graph walking

A generic walker must handle cycles, shared references, inaccessible JDK fields,
native-image metadata, custom collections, proxies, lazy values, and concurrent
mutation. A bounded walk would be an estimate with shape-dependent truncation;
an unbounded walk violates the spike's cost requirement. Arbitrary reflection
and recursive graph walking are rejected.

### JVM `Instrumentation`

`Instrumentation.getObjectSize` requires an agent and reports shallow size only.
Following references reintroduces the graph-walking problems, while counting
shared objects creates ownership ambiguity. Agent requirements are incompatible
with ordinary JVM, AOT, and native consumers. Instrumentation is rejected.

## 4.1 Constraints Carried Forward

Any candidate considered by Priority 4.2 must:

- be known before generation-checked publication or refresh replacement;
- use one named unit per policy and never claim to equal heap or RSS;
- perform bounded, non-blocking work without full response duplication,
  reserialization, arbitrary reflection, or recursive graph inspection;
- distinguish no entry, a present zero-weight value, unknown, invalid, and
  arithmetic overflow;
- include only headers retained in the cached `ResponseEntity`, never discarded
  or sensitive response headers; and
- release measurement state on success, failure, empty completion,
  cancellation, admission bypass, replacement, eviction, and close.

This evaluation does not yet choose whether an individually over-budget success
is returned uncached, how zero/unknown weights behave, or how aggregate weight is
transferred atomically. Those decisions belong to Priority 4.2.
