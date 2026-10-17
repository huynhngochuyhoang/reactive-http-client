# V36 Evidence Preservation and Maintainer Guidance

> **Recorded:** 2026-10-16
> **Scope:** Priority 9; evidence and guidance only
> **Runtime:** published `4.4.2` unchanged; development `4.5.0-SNAPSHOT`
> **Release and closure:** unselected; Priority 10 remains pending

This is the dated Priority 9 checkpoint. [V36-P10-1](RELEASE-DECISION.md) later
approved reduced-scope review-only/no-release closure, excluding A001/A002 from
closure obligations without resolving them. That decision supersedes pending
lifecycle statements here; the evidence, scope limits and findings are unchanged.

This record reconciles the [audit](EVIDENCE-AUDIT.md),
[ordinary capture](ORDINARY-CAPTURE.md), [intervention](INTERVENTION-RESULTS.md),
[no-production-change decision](CORRECTION-DECISION.md),
[cost non-acceptance](COST-ACCEPTANCE.md) and [contract verification](VERIFICATION.md).
Priority 9 does not grant another experiment, scope exclusion, closure or release.
The starting source is clean `e0a041bc1b52bdd2a4ca631a08a7f2ae6e5e81b2`, tree
`8598c2025f64f43538898fd16711c88b1e19c836`; this evidence uses that reachable base
plus the preserved tooling/test/documentation patch, not an invented clean commit.

## Dispositions and Owners

| Finding | Disposition and owner | Supported alternative / reopening trigger |
|---|---|---|
| A001 / AUTO_REGISTRY | **unresolved/blocking**; performance maintainer with metrics-integration review. Ordinary high-mode attribution is still missing; forced non-inlining and a clear reverse run do not explain the original flag. | Keep the required registry/observer behavior and all original scores. A new investigation requires a separately approved bounded plan capable of distinguishing ordinary compiler modes without substituting traced scores. |
| A002 / HOOK | **unresolved/blocking**; performance maintainer with observer/hook review. Ordinary high mode occurred on both artifacts, but not in the traced lane; A001 or AUTO_NO_REGISTRY explanations cannot be transferred to HOOK. | Preserve real hook invocation, ordering, lifecycle and terminal state. Reopen measurement only with explicit budget/stop rules and independent ordinary high-mode attribution; do not remove required hooks to imitate MINIMAL. |
| E001 / evidence reliability | **verified for new capture within limits**; benchmark tooling maintainer. Process/artifact/profile association and bounded compiler histories are corrected. Legacy pairing, exact executing-code identity and individual internal-probe census remain unverified. | Use the tracked verifier and per-worker records, retain incomplete/contrary results. Changed recorder/parser/JMH inputs or a reproduced association failure require new synthetic controls and explicitly approved affected capture before stronger claims. |

The three permitted finding outcomes remain **explained without production
change**, **fixed and verified**, or **unresolved/blocking**. A001 and A002 are
the third outcome independently and combined. E001's tooling correction is not
a runtime optimization or proof of the missing compiler facts. V36-P6-1 approves
no production change; C001-C005 and both C004 subrows retain V35's no-change
outcomes. No rejected candidate is reinstated.

All **48/48 worker slots are exhausted**. The 194-invocation accounting includes
96 inferred internal probes, not an individually observed process census. The
maintainer's P4 accounting exception and P5 amendment remain part of the record.
No new worker, pilot, preflight, probe, tuning trial or scoring retry is authorized.
Current correctness verification is not a performance pass. There is **no scope
reduction proposed or approved by Priority 9**. Both allocation findings still
block full-scope closure; Priority 10 must request an explicit decision if an
exclusion is proposed. Review-only/no release by itself clears neither finding.

## Intentional Cost and Operations

Published `4.4.2` behavior remains the supported application baseline. The
`4.5.0-SNAPSHOT` coordinate is not a selected final candidate or a snapshot-upgrade
remedy. There is no new configuration, meter, public API or migration requirement.
Dynamic provider discovery, supports/order checks, fresh public metadata, policy
mutation validation and subscription-local terminal state remain intentional.
Reuse supported proxy lifetimes, not resolved requests or mutable caller state.

AUTO_REGISTRY and HOOK exercise application-visible observability and callbacks;
MINIMAL is not an equivalent substitute when those capabilities are required.
Separate starter discovery/composition from application callback, codec, connector
and downstream work. Application-supplied pools, schedulers, registries and SDKs
remain application-owned; the starter closes only the resources it owns. The
independent non-single-flight load remains caller-owned and can outlive manager
close while late cache publication is rejected. Removed meters cannot establish
post-close work completion. Use the existing lifecycle/cleanup acknowledgements.

The saved V35 60-row primary comparison and four reverse rows per artifact remain
the scored evidence, including all three original allocation flags. V36 O/M/T/I
observations are diagnostics, not public scores or replacements for those rows.
No public performance number is promoted. Ordinary high mode missing from a trace
is missing evidence, not proof that the regression vanished.

B/op is allocation per operation, not retained heap or RSS. Use time-aligned
heap/direct/native memory, pool, thread and lifecycle evidence under
[performance troubleshooting](../../docs/25-performance-troubleshooting.md#default-path-cost-and-ownership)
and [memory triage](../../docs/30-operations-troubleshooting.md#cache-memory-triage-420).
The synthetic local workload does not diagnose Kubernetes, Istio or Envoy; there
is no mesh diagnosis or pod-memory all-clear. Match construction, first use,
publisher assembly, subscription and transport phases before comparing costs.

## Evidence Inventory

These are local bundles, not files shipped in a clean clone. Each directory is
under `verification-evidence/`; its archive is `<bundle>-<date>.tar.gz` alongside
it. Counts exclude `SHA256SUMS`. The hashes below were already recorded in the
original checklists/seals; verification checks both exact file coverage and every
archived file, not just the compressed archive hash. Original bundles stay read-only.

| Bundle | Date | Files | Manifest SHA-256 | Archive SHA-256 |
|---|---|---:|---|---|
| v35-priority9 | 20261011 | 1811 | e1f1ee5bb1004963a1c2aca7a4faa28da67193b8101af9ab522e80b0340599c0 | ca124e7ee2c18e999ba5c089b8cc2a488d71997dca49fc93e62fc81e42e0b8ba |
| v35-priority10 | 20261011 | 931 | 62e4375ebb17f9d4591449b86b67f16795ab126d4419c24616af37435bb33ba4 | ee8f62205902af433aeb3da5b8d08203c22e0026af2664c9c6b89150a8e109e0 |
| v35-priority11 | 20261011 | 210 | b8d580e78d3bcf8c24b8b14371d7a80310f688dd49731f1727c4efdbc6ee3af0 | 5ebf81ad9e39de30167d76a2d0c533be34725f22eb8237770291566060aa97bf |
| v35-priority12-preparation | 20261011 | 109 | 4570d4129101658cf2c54747cf63e23ea021d1023c65962b21d54d06c78077e6 | 26b04de5e085e0b1d439e239099a1874c9fbd34ec6217f785914b2ab985a3a3c |
| v35-priority12 | 20261011 | 213 | 073353a4aa26c735f8af4e1678480f250f4adab1b16d17c741614a3c542358a9 | 9a0a5554993d4178b68161a777f0f67028d5620679ff83c7acc897c92f54674a |
| v36-priority1 | 20261012 | 143 | f97eed03840851470b58d060a1fd08d5b30d2b7d685ef9ba77924a128937dc02 | f556547626e006dda88796ddfb09e563952cbb0e965387a8116005f38a0f1e4b |
| v36-priority2 | 20261013 | 131 | 402a0f0c837eab3e6f0fe35dfd46a4cd17c866f53b4559c74b2b8e882ffb2967 | b6d06ef27e939a1adcb478aeed948b3b6ca8675bfa5fbd775ee711c4f506512c |
| v36-priority3 | 20261013 | 59 | 2ce190c822d2f64936038b3c051ea14b65a1701dd9111570100930a2d19edd1c | 461b1920e9351050b009342e5093b53ed3509ac013821e37e279cb7e41257d69 |
| v36-priority4 | 20261014 | 597 | 07a1785de010946808ebefb68eee512754b96383301d48a475a041b7b8cec44b | 13b35a8f51bb899e9624c37c2344d691b3afba7f5fcc25d915448c8a0ed10b47 |
| v36-priority5 | 20261014 | 201 | b1b6a7ec0e180f959feb3fd4901ef9cc4ec65c7abc499e5ec43ecb6e92c4dc54 | d963c7f7e8eddcc04c63bc72f61fc530310685b099feec826d0d6bef0952e905 |
| v36-priority6 | 20261014 | 87 | 4ff957a173bda3af19e191695d4f1f0c7ae4b6b85a0a50d606ad9507cdddcce7 | 244bce01667664bfb60d069381b79753da22c3e1d28644dbec394d80c71e9cc8 |
| v36-priority7 | 20261014 | 63 | c11a1e2854189d77e89757815c4f0e7c92c9da2094305dbb78f0836f2af76dfa | 6ed0851e9fa0983a7258c06534540508fbe57aa0deb693cd4d35e433d2389516 |
| v36-priority8 | 20261014 | 203 | 8d69e9d56fffe322df1313b929743c6b272d584f4214922b9d97005ebf035e85 | 13f223432e297407b34a144d6fabfa8f4ff6e95dd5a36044fb510b117f7132f6 |

V35 P9 holds regenerated runtime/consumer/native reports and binaries; P10 holds
raw primary/reverse samples, artifacts, toolchains/dependencies, API/packaging
and diagnostic attempts. P11/P12 retain guidance, pre-approval preparation and
the explicit reduced-scope closure. V36 P1-P3 retain inventories, parser audits,
failing fixtures, approvals and frozen inputs; P4/P5 hold all new worker records
and traces; P6-P8 hold decisions, semantic guards and unchanged-input reuse audits.
Their smaller audit archives do not replace the referenced raw bundles.

Preserve failure/interruption records alongside successes: the old missing V35
attempt is not recreated; P2's reader failure, P3's report-copy failure, P4's
probe-accounting exception, P5's limits, missing-record guards, P8's read-only
Maven-cache failure, report-layout audit failure and case-sensitive guard failure
remain identifiable. A checksum proves preservation, not that a failed attempt
passed or that every required measurement exists.

Lost V35 P2-P8 raw recordings and the earlier lost P9 attempt remain unavailable.
V35 P10's execution object is locally available but not an ancestor; reachable
input reconciliation does not rename its execution provenance. The original V33
publication bundle is absent; saved Central starter provenance does not replace
all-module publication/signature evidence. Temporary repositories/drivers may
disappear after restart; the P8 sealed drivers remain available, but their old
`/tmp` dependency paths are not portable installations. New dependency resolution
is new verification, never the historical classpath. Missing inputs must fail
verification, not be silently regenerated under an old run identity.

## Reproduction and Privacy

Run from the repository root with Python 3.11 or newer. Obtain the referenced
bundles from the maintainer, compare trusted tracked hashes, then verify before
cleanup or analysis. The verifier never extracts an archive or launches Java:

```bash
python3 -B scripts/verify-v36-evidence.py \
  verification-evidence/v36-priority8 \
  verification-evidence/v36-priority8-20261014.tar.gz \
  --manifest-sha256 8d69e9d56fffe322df1313b929743c6b272d584f4214922b9d97005ebf035e85 \
  --archive-sha256 13f223432e297407b34a144d6fabfa8f4ff6e95dd5a36044fb510b117f7132f6
python3 -B -m unittest discover -s scripts -p 'test_*v3[3456]*.py' -v
```

Repeat verification using each row's exact directory/archive/hash pair. The
synthetic negatives reject missing, corrupt, incomplete or unlisted content,
changed seals, duplicate/unsafe paths, links and truncated archives. No original
evidence is damaged to test rejection. Integrity alone does not validate the
scientific conclusion; use the linked applicability/acceptance records as well.

Keep source or reachable base-plus-patch, exact commands/exits, failed attempts,
raw reports, toolchain/effective dependencies and hashes together. Never run root
`mvn clean` against the only evidence copy. Keep the verified copy and archive
outside Maven output; an additional off-machine backup is not claimed here.

All capture remains bounded synthetic/sanitized evidence. No production bodies,
headers, identities, cache keys, request targets, arbitrary exception text or
credentials belong in shared records. The verifier is read-only, not a sanitizer
or an untrusted-archive extraction tool. The existing
[support-bundle rules](../../docs/26-support-bundles.md#performance-investigations)
still apply; raw compiler logs are local synthetic artifacts, not safe production
attachments. No new production capture or experiment is authorized by these commands.

## Verification and Remaining Gates

Fresh checks pass **92 Python tests** and **170 Java cases per Boot row**
(4.0.0/4.1.0): 117 documentation/readiness/link, 18 metadata/example and 35
property cases. Explicit GC is disabled; Java 21.0.8/Maven 3.9.9 and current
dependency inventories are retained. The audit verifies **4,758 files and 13
archives**, plus 392 unchanged runtime/build/fixture inputs. Initial failures for
unfinished Priority 9 status and a stale Priority 8 pending-status assertion are
preserved separately from successful reruns. No runtime or scoring change was
needed. The external seal is recorded in [CHECKLIST.md](CHECKLIST.md).
The current patch and verification drivers, commands/exits, reports/readiness,
toolchain/dependency inventories and historical archive audit are preserved under
`target/release-evidence/v36/priority9/`, with a verified durable copy at
`verification-evidence/v36-priority9/` and dated archive. V1-V35 history remains
unchanged. Runtime/API/consumer/AOT/native results remain labeled as reused under
Priority 8, not fresh executions. No benchmark, signing or publication is performed.

Readiness remains `activeRoadmap=v36`, `plannedFinalVersion=null`,
`releaseLane=unselected`. Both A001/A002 remain unresolved/blocking. Priority 10
is pending; completing preservation and guidance neither excludes their scope
nor closes V36.
