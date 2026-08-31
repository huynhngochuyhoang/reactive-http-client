# V29 Retained-Weight Contract Decision

> **Decision:** GO with decoded response representation bytes as one optional,
> deterministic, non-heap cache weight unit.

Recorded on 2026-08-31 against commit
`3a103ea6968a7dd160392498134812b23bf5c7af` and the
`4.2.0-SNAPSHOT` response-cache implementation.

## Scope of the Decision

Priority 4 evaluated whether V29 may proceed to an optional aggregate cache
weight bound. This decision selects the unit and permits Priority 5 to implement
it. It does not approve a property name, public API, storage implementation,
meter, diagnostics field, or release. Those remain implementation and evidence
work.

The selected unit is **decoded response representation bytes**:

- count the readable body bytes consumed by the final successful unary response
  decoder after transport decompression and before conversion to the cached Java
  value; and
- for a cached `ResponseEntity<T>`, add the UTF-8 byte length of each retained
  header name once and of each retained header value after the existing cache
  allowlist and rejection rules have produced the stored entity.

The unit measures the decoded input representation retained by policy, not the
Java object graph produced from it. It is not Java heap, shallow object size,
direct memory, RSS, container memory, compressed wire size, or a prediction of
any of those quantities. User-facing configuration, meters, and diagnostics
must name the representation-byte unit explicitly and preserve this distinction.

## Alternatives Considered

| Alternative | Decision | Reason |
|---|---|---|
| Decoded response representation bytes | **Selected** | The starter can observe the final unary body stream with checked, constant retained accounting and without inspecting or serializing the decoded value. The same byte unit covers every currently cacheable unary body shape. |
| Generic publication-time value inspection | Rejected | Only a few values have an O(1) byte measure. DTOs, records, collections, maps, sharing, cycles, and custom values have no common bounded unit. |
| Starter-owned key and metadata bytes | Rejected | It excludes the response body representation and therefore does not provide a useful response-retention admission bound. |
| Application-supplied weigher | Deferred, not selected for V29 | It can express a domain estimate, but the application owns its unit, execution cost, and correctness. Selecting it would add an SPI and arbitrary subscriber-thread code before a starter-controlled contract has been proved. |
| `Content-Length` | Rejected | It may be absent, compressed, stale, or inconsistent and does not describe bytes consumed by the decoder. |
| Compressed wire bytes | Rejected | It measures transport work before decompression and can differ substantially for the same decoded response. |
| JSON reserialization | Rejected | It duplicates the response, may invoke application serializers or getters, and can differ from the received representation. |
| Reflection graph walking or JVM `Instrumentation` | Rejected | These approaches are incomplete or unbounded, have cycle/shared-owner ambiguity, and do not fit ordinary JVM, AOT, and native consumers. |

The detailed candidate analysis remains in
[`RETAINED-WEIGHT-CANDIDATES.md`](RETAINED-WEIGHT-CANDIDATES.md). Admission and
accounting behavior remains in
[`RETAINED-WEIGHT-ADMISSION.md`](RETAINED-WEIGHT-ADMISSION.md).

## Evidence and Measurements

The Priority 2 characterization used fixed 4 KiB decoded `byte[]` responses and
entry-count bounds of four or eight. Occupancy obeyed those bounds, but the
GC-stable heap means did not show a stable four-versus-eight ratio and closed RSS
remained a separate JVM/transport accounting category. That result supports only
the need for an additional explicit representation bound; it does not calibrate
representation bytes to heap or prove that a byte budget will control process
memory by the same amount.

At the candidate boundary, body measurement is the checked sum of readable bytes
actually observed by the final unary decoder. Header measurement is the checked
sum of UTF-8 lengths for each retained field name and value. Both are
exact in their named unit and require constant retained counter state. No
production counter or overhead measurement exists yet; Priority 5 must prove that
implementation and its disabled-policy cost before release.

## Measurement Contract

The implementation admitted by this decision must satisfy all of these rules:

1. Counting begins only for a cache-selected unary load or refresh whose policy
   explicitly selects the future aggregate representation-byte budget.
2. The body count observes the exact `DataBuffer` sequence presented to the
   final decoder. It uses checked `long` arithmetic and does not copy, join,
   retain, re-read, or reserialize the body.
3. A body count is `known` only when that observed body stream completes
   successfully and decoding produces the candidate. If a custom decoder emits
   and cancels the remaining body, the count is `unknown` and the successful
   value bypasses storage.
4. Decode error, cancellation, empty completion, response rejection, request-
   identity mismatch, and close cannot publish a count or entry.
5. Header weight is calculated only from headers retained in the sanitized
   `ResponseEntity`; discarded, sensitive, redirect, challenge, and custom
   non-cacheable headers are neither counted nor stored.
6. Header text counts each retained field name once and each retained value once;
   order and delimiters add no weight. Checked UTF-8 lengths and checked addition
   combine body and header bytes.
7. A plain cached value contributes only the known body representation count.
   Status and Java object/container overhead are outside the unit.
8. The immutable measured weight is attached to the candidate before the
   generation-current publication transition. The stored entry owns that weight
   until replacement, expiry, eviction, or close.

No implementation may substitute `Content-Length`, zero, one, a type constant,
serialized output, or an application estimate when the selected measurement is
unknown or overflowing.

## Supported Result Shapes

The selected unit applies uniformly to the existing finite unary cache grammar:

- scalar, enum, `String`, `byte[]`, record, DTO, collection, map, and other
  values decoded by the final unary codec;
- the same body shapes inside an eligible `ResponseEntity<T>`, plus only its
  retained representation-header text;
- present empty values, which may have a valid zero body count but still consume
  a mandatory `maximum-size` entry slot; and
- refresh replacements, measured independently while the old entry, weight, and
  hard-expiry deadline remain authoritative.

An empty `Mono` has no candidate or weight. Streaming responses, non-cacheable
status/header outcomes, and every shape already rejected by the response-cache
eligibility grammar remain unsupported; this decision does not broaden that
grammar.

## Admission and Accounting Consequences

The abstract Priority 4.2 semantics now bind `B` and `R` to decoded response
representation bytes:

- a future positive aggregate byte budget is additional to mandatory TTL and
  `maximum-size`;
- a known zero-byte candidate may be retained and still consumes an entry slot;
- unknown, invalid, overflowing, and individually over-budget successful
  candidates are returned uncached without evicting an entry that cannot make
  the candidate fit;
- only an actual generation-current retained-entry transition changes aggregate
  bytes;
- duplicate losers and detached work have no accounting or eviction effect;
- refresh bypass or failure preserves the old entry, weight, and hard expiry;
  and
- expiry, replacement, eviction, explicit invalidation, and close subtract each
  stored weight exactly once.

Priority 5 must prove these transitions against the real cache implementation.
This decision does not select or promise an eviction victim order.

## Migration and Compatibility Impact

The decision is additive and dormant. Until Priority 5 deliberately exposes and
implements a budget, existing behavior is unchanged. After implementation:

- every published `4.0.0`/`4.1.0` policy without the new setting must retain the
  same validation, lookup, identity-return, TTL, size, single-flight, refresh,
  dependency, allocation, and lifecycle behavior;
- no body counter, weighted cache, aggregate state, callback, meter, diagnostic
  field, or support output may activate for an unweighted policy;
- merely declaring optional infrastructure must not instantiate or select it;
- `maximum-size` remains required and continues to mean entry count; and
- any new property or public type must be additive, generated-metadata covered,
  AOT/native safe, and included in compatibility evidence.

There is no automatic migration and no inferred budget. Applications that want
the additional bound must opt in and choose a byte value based on their accepted
response representations. Existing policies need no configuration change.

## Remaining Uncertainty and Required Proof

The GO is for a representation-retention bound, not a memory-leak fix. The V29
workload found expected bounded retention and a separate RSS accounting gap; it
did not establish a stable ratio between response bytes and decoded heap.
Different codecs and data models can expand the same number of bytes into very
different object graphs.

Priority 5 must still prove that:

- wrapping the final unary body stream neither duplicates buffers nor changes
  codec, filter, pooled-buffer release, cancellation, or error behavior;
- early-emitting/custom decoders produce `unknown` unless body completion makes
  the count final;
- body and retained-header arithmetic cannot overflow or allocate in proportion
  to response size;
- Caffeine publication, replacement, and removal maintain the aggregate bound
  atomically under duplicate load, refresh, expiry, eviction, and close races;
- unweighted policies have no measurement or allocation regression; and
- metrics and diagnostics use low-cardinality structural facts and state the
  representation-byte unit without implying heap or RSS.

Failure to prove any of these boundaries returns Priority 5 to no-go or narrows
its supported contract. It must not be repaired with a hidden fallback estimate.

## Outcome

**GO.** One deterministic, bounded, non-heap unit is defensible for all current
finite unary cache shapes: decoded response representation bytes, plus the
checked UTF-8 bytes of retained `ResponseEntity` header names and values. Priority 5
may implement this optional unit under the admission semantics above. Release scope
remains deferred until implementation, compatibility, consumer, benchmark,
AOT, native, shutdown, documentation, and publication evidence are complete.
