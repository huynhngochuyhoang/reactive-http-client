# V35 Cross-Path and Lifecycle Verification

> **Status:** Priority 9 complete, 2026-10-11
> **Delivered production IDs:** none
> **Release scope:** unselected

## Evidence Recovery Boundary

On 2026-10-10 the first Priority 9 runner incorrectly invoked reactor-root
`clean install` with its evidence and dependency repositories under the same
root `target/` directory. Maven deleted the local ignored evidence, including
historical V35 bundles and the just-completed native run. The controller was
interrupted; its logs were also deleted, so its Maven exit is unavailable.
The reported native success from that attempt is **not retained evidence**.
Tracked source and historical documents were not changed by the deletion.

On 2026-10-11 the maintainer confirmed no backup exists and requested fresh
verification. This record supersedes the lost Priority 9 attempt, not the
historical measurement records. Deleted performance samples, profiler recordings
and old checksum inventories are **unavailable**, not reconstructed or rehashed.
Priorities 1-8 describe their historical runs; they are not new immutable bundles.
Priority 10 must regenerate required matched cost/API evidence before acceptance.
No performance pass, release approval or production-memory conclusion follows
from this verification.

## Safe Reproduction

The [V35 runner](../../scripts/verify-v35-parity.py) uses fresh detached checkouts
and separate sibling evidence/repository directories outside the user's reactor
and every `target/` directory. It rejects overlapping paths, including symlink
aliases, and refuses root execution. Full-suite stages use `install` in fresh
per-row checkouts without root `clean`. The existing
[parity runner](../../scripts/verify-v33-parity.py) creates fixture overlays with
the actual Boot parent changed, rather than only overriding a dependency property.
Fixture-local `clean` cannot remove the sibling evidence or Maven repository.
Each stage requires a new output directory and preserves command logs, exits,
timestamps, fresh report XML and failure summaries. A failed stage is not retried
under the same name or counted as passing evidence.

Source for the replacement runs:
`f38fb8d22486d35401a7af9cb9e80f5e5e8c75ef`, tree
`f1b8ceea8319587d4736c4a88bad57ce4588e0b3`.
Native uses a separate clean checkout of that reachable commit. Closure changes
are limited to verification tooling, documentation and its guard, not production,
dependency or consumer/native-fixture inputs.

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64
export PATH="$JAVA_HOME/bin:$HOME/.sdkman/candidates/maven/current/bin:$PATH"
WORK=/tmp/v35-parity-new
python3 -B scripts/verify-v35-parity.py init --work "$WORK" \
  --ref f38fb8d22486d35401a7af9cb9e80f5e5e8c75ef \
  --seed-repository "$HOME/.m2/repository"
for stage in full40 consumer40 minimal40 jvm40 cache-reachability \
    handoff-reachability benchmark-reachability full41 consumer41 minimal41 jvm41; do
  python3 -B scripts/verify-v35-parity.py "$stage" --work "$WORK" || break
done
```

Stop on failure and inspect the failed stage; an interrupted loop is not a pass.
Seeded repositories are copied read-only from a local cache into a new writable
directory. These are freshly installed current-reactor artifacts, **not Central
consumption** of `4.4.2`. Full ordinary suites disable explicit GC. The separate
cache, handoff and benchmark reachability profiles use Serial GC, a bounded heap
and explicit GC enabled; only those profiles support collection claims.

Run native separately, without competing Maven builds:

```bash
python3 -B scripts/verify-v33-native.py \
  --ref f38fb8d22486d35401a7af9cb9e80f5e5e8c75ef \
  --java-home "$HOME/.sdkman/candidates/java/25.0.3-graal" \
  --work-root /tmp/v35-native-new --seed-repository "$HOME/.m2/repository"
```

Boot 4.1 evidence is JVM/AOT, not a second native image. Keep an external archive
of completed bundles before copying them into the conventional
`target/release-evidence/v35/priority9/regenerated-20261011/` location; that location is disposable build
output and must never be the only copy. The durable local archive lives in the
ignored `verification-evidence/` directory, outside all Maven build output;
the `/tmp` workspaces alone are not durable backups. Native build time/RSS are build-resource
facts, not application performance or retained-memory measurements.

## Contract Coverage

| Boundary | Regression witnesses and limits |
|---|---|
| Public handler, Spring factory and mock | `V33CrossPathContractTest`, public metadata tests and complete helper suite retain fresh metadata, selected properties, cold/repeated calls and supported extensions. Mock exchanges are synthetic, not TCP or pooled-buffer evidence |
| Late discovery and mutable selection | `DefaultPathCostOwnershipTest`, `OptionalPreparationContractTest` and planning tests retain per-invocation provider discovery, prototype ordering, whole-interface mutation checks, excluded siblings and legacy late selection. No rolled-back candidate is restored |
| Body, context and terminal ownership | Streaming upload, multipart wire, request-context, explicit handoff and ownership suites cover real peer bytes, one-shot/replayable resources, cancellation acknowledgement, discarded values and per-subscription terminal state. Structural assertions do not prove collection |
| Cache/auth/retry/redirect/deadline | `CacheWorkCompositionContractTest`, semantic-read and replay suites keep hit authorization, finalized identity, retry eligibility, replayed body bytes, independent caller budgets and shared-load/refresh ownership distinct |
| Metrics and optional boundaries | `LocalResponseCacheObservabilityTest`, work telemetry, OTel and assembled V26 assertions use real API names, not an `unknown` convenience path. Hits/coalesced callers retain zero transport evidence; hidden refresh is separate from caller termination. An ordinary request timer is not an exact wire-dispatch counter |
| Resource shutdown | `ResourceOwnershipReviewTest`, cache/work ownership and diagnostics suites cover rejected construction, no lazy acquisition by diagnostics, same-tag replacement, entered cleanup/discard and closure. Supplied connectors, pools, schedulers, registries and SDK ownership do not transfer to the starter |
| Runtime/AOT lifecycle | Properties/metadata selection, lifecycle, effective-selection and smoke suites retain binding/awareness/init order, scopes/aliases/proxies, processor restoration and selected record hints. Ordinary runtime tracking stops; AOT history ends with its factory, not a global cache |

Independent non-single-flight loads remain caller-owned and can outlive manager
close; their terminal/cancel signal releases their captures, while late cache
publication remains rejected. Manager-owned flights and refreshes terminate at
shutdown. Removed meters cannot report post-close counter deltas: use explicit
cancel/discard acknowledgements, final ownership snapshots and bounded no-dispatch
windows. Controlled reachability separately checks collection of displaced values,
transient owners and detached callers, not a general application-heap or RSS bound.
The unchanged OTel adapter has no SDK disposal hook; that SDK ownership statement
is also source-reviewed, not a claim that these tests prove arbitrary SDK lifetime
or garbage collection. The application's container can still close its own beans.

The selected consumers run all V27-V32 profiles with V26 observability assertions
enabled. Their external-package cases check actual methods/targets/results,
auth/tenant isolation, fresh metadata, SAFE customization and application connector
ownership. The work and extension catch-all servers record received dispatches;
method-specific routes reject mismatched verbs. The minimal consumer counts every
request and accepts only GET `/value`: resilience is enabled with no operator
selected, exactly two requests succeed, and Caffeine, all four Resilience4j
registries, MeterRegistry, OTel API and the mock helper are physically absent.
Its single smoke case does not claim the optional sibling handoff source is copied;
handoff coverage belongs to the selected consumer and helper suites.

Native smoke retains method-specific routes plus a counted catch-all, a bounded
quiet period after open-circuit rejection, decoded records, cache miss/hit/refresh,
weighted/work bounds, explicit retry and context restoration. Shutdown checks
active cancellation, queued caller termination and no late dispatch before
same-tag recreation. The main server expects 20 dispatches before close and one
more after recreation; separate work/context servers have their own counters.
These finite witnesses cannot rule out arbitrarily delayed application-owned work.

## Fresh Native Results

The replacement compiled and executed successfully on 2026-10-11 local time
(2026-10-10 UTC), with no retry or assertion change. This is a new binary, not
reuse of the deleted run. All 61 native inventory entries rehash successfully.

| Fact | Result |
|---|---|
| Source / tree | `f38fb8d22486d35401a7af9cb9e80f5e5e8c75ef` / `f1b8ceea8319587d4736c4a88bad57ce4588e0b3` |
| Source state | Clean before and after compilation/execution |
| Reactor / Boot | `4.5.0-SNAPSHOT` / `4.0.0` |
| Toolchain | GraalVM/native-image `25.0.3`, Maven `3.9.9`, Java target `21`, GCC `13.3.0` |
| Build limits | `-H:+SharedArenaSupport`, `-J-Xmx6g`, `--parallelism=2` |
| Starting resources | Approximately 6.8 GiB available RAM, 1.5 GiB free swap |
| Compile | Exit `0`, 17:15:24-17:21:26 UTC; native-image generation 5m 46s |
| Execute | Exit `0`, 17:21:26-17:21:45 UTC |
| Fixture tests | 6 passed; zero failures/errors/skips |
| Binary SHA-256 | `66de72017d7c8e8a33e1a4a593f55a0736813ffb479d88ccd3bbd7ec77bd7271` |

The native bundle is retained at `verification-evidence/v35-priority9/native/`
and in `/tmp/rhttp-v35-p9-native-20261011/native-3qjtycjz/evidence/`.
It includes the binary, toolchains, source identity, all command/exit records,
fixture XML, effective POM, dependency tree and classpath. Expected shutdown
connection-close warnings and existing framework/native-access/meter warnings
remain in the logs; they were not suppressed to obtain a passing exit.

## Fresh JVM Results

| Lane | Boot | Cases |
|---|---|---:|
| Complete starter suite | 4.0.0 / 4.1.0 | 2,229 each |
| Complete test-helper suite | 4.0.0 / 4.1.0 | 80 each |
| Complete OTel suite | 4.0.0 / 4.1.0 | 62 each |
| Complete ordinary benchmark-contract suite | 4.0.0 / 4.1.0 | 75 each |
| Full-suite total | 4.0.0 / 4.1.0 | 2,446 each |
| Assembled selected-feature consumer | 4.0.0 / 4.1.0 | 29 each |
| Physical optional-absence consumer | 4.0.0 / 4.1.0 | 1 each |
| Smoke fixture tests | 4.0.0 / 4.1.0 | 6 each |
| Ordinary and AOT-enabled smoke execution | 4.0.0 / 4.1.0 | exit 0 for all four executions |
| Controlled cache / handoff / benchmark reachability | 4.0.0 | 16 / 5 / 2 |
| V33-V35 Python verification tooling | N/A | 50 |
| Final documentation guard | 4.0.0 / 4.1.0 | 102 each; overlapping full-suite documentation cases |

The full-suite totals include runtime/AOT selection, hints, ownership and all
ordinary mock contracts; focused subsets are not added a second time. The same
cases on two Boot stacks are executions, not twice as many unique definitions.
All listed cases have zero failures, errors and skips. Ordinary tests use explicit
GC disabled; controlled collection uses its separate profiles. These are untimed
correctness runs, not a performance pass or new benchmark scores.

JVM lanes use Oracle JDK `21.0.8`, Maven `3.9.9`, a 512 MiB Maven heap and two
active processors. The native row uses GraalVM separately. Full-suite XML predates
the closure documentation guard; its final focused rerun is overlapping evidence,
not another full-suite execution. Production and fixture inputs remain unchanged.

### Resolved Consumer Stacks

| Component | Boot 4.0.0 | Boot 4.1.0 |
|---|---|---|
| Spring Framework | 7.0.1 | 7.0.8 |
| Reactor Netty HTTP | 1.3.0 | 1.3.6 |
| Netty HTTP codec | 4.2.7.Final | 4.2.15.Final |
| Jackson databind | 3.0.2 | 3.1.4 |
| Micrometer | 1.16.0 | 1.17.0 |
| OpenTelemetry API | 1.55.0 | 1.62.0 |
| Resilience4j retry | 2.4.0 | 2.4.0 |
| Caffeine | 3.2.3 | 3.2.4 |

Every Boot JAR matches its actual parent row. All consumer classpath entries are
JARs; starter/helper/OTel hashes match the saved artifacts from that row's install.
Full report XML and effective POM/dependency/classpath records remain in the bundle.
The full suites include 243 properties-selection, 7 metadata-selection, 27 AOT
smoke, 4 effective-selection and 2 cross-path cases per row. These five subsets
are included in the starter total, not extra executions.

## Closure and Limits

The replacement runs have no failed test/build stage and no scoring retries.
The lost first attempt remains explicitly excluded above; a lost exit or log has
not been invented. The replacement audit validates every sealed stage inventory,
the native binary, Boot parents/classpaths and assembled artifact identities.
No runtime, dependency or fixture changes occurred after the clean source used
by native and full-module verification. Later edits only add the runner, path
safety/bookkeeping tests, archive ignore rule and closure documentation/guard.

Durable local bundle: `verification-evidence/v35-priority9/`, with a separate
archive beside it. A copy is placed at
`target/release-evidence/v35/priority9/regenerated-20261011/`.
The first packaging attempt refused to overwrite interrupted-attempt remnants
in the conventional parent directory. Those remnants are preserved but excluded
from accepted totals; the copy now uses this fresh child directory. The initial
inventory and packaging script remain in the bundle, followed by the final
packaging addendum. No verification stage was rerun or relabeled because of
that copy refusal.
The per-stage and whole-bundle `SHA256SUMS`, command/exit records, source copies,
test totals, dependency provenance and `audit.json` distinguish fresh evidence
from missing history. Checksums are anchored in the checklist after sealing;
that final anchor does not alter tested production or fixture inputs.

This completes Priority 9 only. All seven finding dispositions remain no-change;
there is no delivered optimization, public API addition or release selection.
Priorities 10-12 remain unchecked. Required historical cost/API evidence must be
regenerated for later acceptance, not inferred from these correctness passes.
