# Reactive HTTP Client — Roadmap V14

> **Status:** completed after the `2.10.0` release. V12 and V13 added a benchmark
> module, promoted benchmark documentation, release-evidence metadata, and
> measured runtime optimizations. That scope is large enough for a minor release:
> publish it as `2.10.0`, not a patch release, unless all benchmark/docs features
> are intentionally kept out of the public artifact.

V14 assumes `2.10.0` has shipped with the V12/V13 benchmark and optimization
work. The next roadmap should use the new evidence system to make release review
more repeatable and to focus the next engineering cycle on production feedback,
not broad new surface area.

V14 keeps the same three-bucket shape:

1. **Features to add** — release-readiness automation, report comparison helpers,
   and production examples that make the benchmark workflow easier to trust.
2. **Features to optimize** — only optimize paths that V13 evidence or user
   workloads identify as persistent hotspots.
3. **Bugs / correctness to fix** — release-version hygiene, documentation drift,
   and benchmark/report mismatch risks.

Non-goals:

- Do not add hard benchmark gates to normal CI.
- Do not publish performance claims without a promoted release-quality report.
- Do not optimize a code path only because one local focused run moved.
- Do not expand public API for benchmark internals unless a user-facing workflow
  needs it.
- Do not make V14 a large feature release before the `2.10.0` benchmark release
  has production feedback.

---

## 0. Release Assessment for V12/V13

### 0.1 Release as `2.10.0`

**Decision:** V12/V13 should be released as a minor version.

**Why:** The Unreleased scope includes new benchmark module behavior,
source-controlled promoted performance docs, generated release-evidence metadata,
manual benchmark review triggers, and runtime optimizations in default success,
JSON, `ResponseEntity`, Micrometer, and Resilience4j paths. Even if the runtime
changes are source-compatible, the project has gained new user-visible tooling
and release workflow.

**Patch release would fit only if:**

- The benchmark module and public docs were not shipped or promoted.
- The release contained only bug fixes and internal implementation cleanups.
- No new release-evidence contract or benchmark documentation was introduced.

**Release actions before tagging:**

- Bump project/module versions from `2.9.0` to `2.10.0`.
- Keep `api.compatibility.baseline.version` on the last published release,
  `2.9.0`, after `2.9.0` is available as the compatibility baseline.
- Refresh README and quick-start dependency snippets to `2.10.0`.
- Promote or refresh `docs/benchmark-report-2.10.0.md`.
- Update benchmark docs, performance summary, changelog links, and release
  evidence to point at `2.10.0`.
- Run API compatibility against published `2.9.0`, normal tests, diff check,
  benchmark smoke, and the release-quality benchmark when publishing performance
  wording.

**Acceptance:**

- [ ] Changelog has a dated `2.10.0` section.
- [ ] Project versions and documentation snippets agree on `2.10.0`.
- [ ] API compatibility baseline points at `2.9.0`, not the current reactor.
- [ ] Promoted benchmark report and performance summary match the release tag.
- [ ] Release evidence manifest lists current and baseline benchmark paths.

---

## 1. Release Evidence and Report Diff Workflow

### 1.1 Add a benchmark report comparison helper

**Why:** V13 explains how to compare reports, but maintainers still compare rows
manually. A small helper can reduce mistakes without creating CI hard gates.

**What:**

- Add a script or test utility that compares two JMH JSON reports by stable
  benchmark method name and mode.
- Print relative movement for average time, p50, p95, p99, throughput, and
  allocation per operation when data is present.
- Flag review-trigger crossings using the V13 thresholds, but exit successfully
  by default.
- Support explicit non-zero exit only through an opt-in flag for local review.
- Keep generated comparison output under `target/`.

**Acceptance:**

- [ ] Current and baseline JMH JSON reports can be compared with one command.
- [ ] Output includes scenario, mode, current value, baseline value, and delta.
- [ ] Review-trigger rows are clearly marked as "review", not "fail".
- [ ] Normal CI does not run the comparison as a gate.
- [ ] Documentation shows how to attach the comparison to release notes.

---

### 1.2 Generate release-note benchmark evidence from manifest data

**Why:** The release-note evidence block is documented, but still manually copied.
Manual copying is where stale paths and scenario names can creep back in.

**What:**

- Generate a Markdown evidence block from the release evidence manifest.
- Include promoted report path, current candidate command, published baseline
  command, current report path, baseline report path, and scenario names.
- Keep the generated file under `target/release-evidence/`.
- Add docs that instruct maintainers to paste the generated block only after the
  promoted report exists.

**Acceptance:**

- [ ] `mvn test` writes a generated benchmark evidence Markdown snippet.
- [ ] The snippet uses the current project version and baseline version.
- [ ] The snippet never links smoke-only reports as promoted evidence.
- [ ] Documentation tests verify the generated wording contains required fields.

---

## 2. Production-Facing Benchmark Examples

### 2.1 Add a small benchmark consumer example

**Why:** Users trust benchmark data more when they can see the equivalent raw
`WebClient`, Spring HTTP Interface, and starter client shapes.

**What:**

- Add a documentation page or example package showing the benchmark client
  contracts side by side.
- Explain why each baseline is equivalent for default client-side overhead
  scenarios.
- Show why optional feature and Problem Detail rows are starter-only unless the
  baseline installs equivalent behavior.
- Link back to the promoted report and benchmark methodology.

**Acceptance:**

- [ ] Docs show raw `WebClient`, Spring HTTP Interface, and starter examples for
      one success path.
- [ ] Docs show a starter-only optional feature row and explain why it is not a
      raw-client comparison.
- [ ] The example avoids broad performance claims.
- [ ] Documentation tests verify links to benchmark docs and promoted reports.

---

### 2.2 Add a performance troubleshooting guide

**Why:** Once benchmark docs exist, users will ask how to interpret their own
latency. The project should separate starter overhead from downstream latency,
network latency, and app-level serialization work.

**What:**

- Add a guide that helps users inspect high outbound latency.
- Cover exchange logging presets, Micrometer tags, lifecycle hooks, retry
  attempts, timeout source diagnostics, and request/response body size.
- Include a short checklist for comparing a user workload with benchmark rows.
- Avoid advising users to enable body logging by default.

**Acceptance:**

- [ ] The guide distinguishes client abstraction overhead from downstream
      service latency.
- [ ] The guide recommends metadata-only diagnostics before body capture.
- [ ] The guide links to observability, exchange logging, lifecycle hooks, and
      benchmark docs.
- [ ] The guide avoids universal performance promises.

---

## 3. Targeted Optimization Follow-Up

### 3.1 Re-audit default success path after `2.10.0`

**Why:** V13 optimized several paths, but benchmark movement should be checked
again after the minor release is cut and the baseline is the published `2.10.0`.

**What:**

- Run the current V14 workspace against published `2.10.0` using the report
  pairing workflow.
- Compare default `Get No Body`, `Get Path Query Header`, `Post Json`, and
  `Response Entity` rows.
- Prioritize only persistent movements that cross review triggers.
- Record before/after evidence for each code change.

**Acceptance:**

- [ ] Published `2.10.0` artifacts resolve before comparison.
- [ ] Current and baseline reports stay in distinct paths.
- [ ] Any optimization is tied to a named benchmark row.
- [ ] No optimization changes diagnostics, lifecycle, retry, or streaming
      contracts without targeted tests.

---

### 3.2 Audit object allocation in request argument expansion

**Why:** Path, query, and header expansion is a common declarative-client cost.
It is also a bounded area where small allocation reductions can be safe if tests
preserve behavior.

**What:**

- Inspect per-call allocation in path/query/header argument resolution.
- Reuse cached request-plan metadata where possible.
- Avoid changing multi-value header behavior, precedence, or validation.
- Add no-network microbenchmarks only if existing rows cannot isolate the path.

**Acceptance:**

- [ ] `Get Path Query Header` evidence exists before any optimization.
- [ ] Multi-value header and URI-template tests still pass.
- [ ] Any added helper remains internal.
- [ ] Public docs do not claim universal raw `WebClient` parity.

---

## 4. Release and Compatibility Hygiene

### 4.1 Guard release version and benchmark report consistency

**Why:** After `2.10.0`, the promoted report, README snippets, changelog links,
and release evidence can drift from the Maven project version.

**What:**

- Extend documentation tests to verify the promoted report filename matches the
  project version for release candidates.
- Verify the promoted report's `projectVersion` and `starterVersion` rows match
  the current project version when the changelog has performance claims.
- Keep source-controlled promoted reports versioned; never update an old report
  in place for a new version.

**Acceptance:**

- [ ] Tests fail when README snippets, promoted report links, or report metadata
      point at different versions.
- [ ] Tests still allow historical reports to remain under `docs/`.
- [ ] Changelog links point at the current promoted report for the release being
      drafted.

---

### 4.2 Keep API compatibility baseline release-aware

**Why:** The API baseline must move only after a version is published, and it must
never compare the current reactor to itself.

**What:**

- Document the exact sequence for cutting `2.10.0` and then preparing the next
  development cycle.
- Keep the baseline guard dynamic and profile-scoped.
- Verify module-scoped API compatibility still runs the guard.
- Keep benchmark published-baseline commands aligned with the same baseline
  version.

**Acceptance:**

- [ ] Release docs explain when to update `api.compatibility.baseline.version`.
- [ ] API compatibility tests still reject self-comparison.
- [ ] Benchmark published-baseline docs use the same baseline version.
- [ ] Release evidence lists the baseline artifacts for every published module.

---

## Suggested Priority Order

1. Release `2.10.0` from completed V12/V13 work.
2. Add benchmark report comparison helper.
3. Generate release-note benchmark evidence from manifest data.
4. Add benchmark consumer example.
5. Add performance troubleshooting guide.
6. Re-audit default success path after `2.10.0`.
7. Audit object allocation in request argument expansion.
8. Guard release version and benchmark report consistency.
9. Keep API compatibility baseline release-aware.
