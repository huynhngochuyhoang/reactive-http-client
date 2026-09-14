# V32 Architecture Finding Register

> **Status:** open for review; no confirmed findings or accepted implementation
> **Baseline:** [verified scope and evidence](BASELINE-SCOPE.md)
> **Decision owner:** maintainer, through [Priority 8.3](CHECKLIST.md)

Priority 1 establishes this structure only. There are no populated finding
rows yet; zero rows is not a claim that the architecture has no defects.
Reported hypotheses and proposed extension scenarios remain in the baseline
record until reproduced or bounded by the later review.

## Required Finding Fields

Assign stable `V32-F001`-style IDs when recording an investigated issue; do not
renumber or reuse IDs after disposition. Keep one section per ID with these
fields rather than duplicating its evidence across roadmaps.

| Field | Required content |
|---|---|
| Need and origin | Reported application workflow or explicitly exploratory scenario; affected user and impact |
| Classification | Verified behavior, confirmed gap, documented constraint, intentional non-goal, or unresolved question |
| Contract and owner | Existing promise, source/module, decision owner, lifecycle, supported SPI versus internal cooperation |
| Evidence | Reachable reviewed revision and dirty/clean state; source/test references, deterministic reproducer, actual commands/results/hashes and missing observations |
| Alternatives | No change, guidance, existing helper/SPI, local correction, or narrowly justified new boundary |
| Tradeoffs | Correctness/security, binary/source/behavior compatibility, optional dependencies, concurrency, retention and hot-path cost |
| Priority and dependencies | Safety first, reproduced supported-extension blocker next, evidenced maintenance cost after; dependency order |
| Disposition | Unresolved, fix now, document, retain intentionally, defer with owner/trigger, or reject; dated rationale |
| Acceptance and rollback | Observable assertions, required consumer/native/performance lanes, failure conditions and bounded rollback |
| Decision reference | Maintainer approval, date, selected alternative/scope and verification budget, or explicitly not selected |

Separate observations from hypotheses. A large class, historical fix count,
unchecked proposal box, absent test, or smoke benchmark flag alone is not a
confirmed defect. Do not store real headers, request targets, bodies, cache
keys, credentials or identities in source-controlled reproductions.

## Decision Gate

No production change is authorized by baseline completion or by creating a
finding. Priority 8.3 must record the maintainer's selected IDs, alternatives,
scope, compatibility/dependencies, verification and rollback in the eventual
architecture decision record. An urgent confirmed defect needs an explicit
expedited decision too. Until then implementation and release remain unselected.

No-change conclusions are valid. Deferred findings require an owner or concrete
reconsideration trigger and named missing evidence; deferral is not completion
of an implementation. Accepted blocking work must be resolved or explicitly
removed from scope before Priority 12 closes the review. Review-only/no-release
is a supported final outcome, not a failed release.
