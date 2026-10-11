# V35 Final Matched Cost and Compatibility Acceptance

> **Plan frozen:** 2026-10-11, before fresh measurement
> **Status:** 10.1 and 10.3 complete; 10.2 cost acceptance pending
> **Production changes:** none retained
> **Release scope:** unselected

This executes Priority 10 of [CHECKLIST.md](CHECKLIST.md). The starting clean
source is `beb8a237215529c9c8c7ddc285295c9564e4bce0`. Deleted historical
samples remain unavailable. All results here must come from new artifacts and
measurements, not reconstructed historical scores or old checksum identities.

## Frozen Schedule

Use the same 60 primary rows and untimed semantic witnesses as
[V34's workload contract](../v34/WORKLOAD-CONTRACT.md): 54 V34 profile/scenario
rows and six planning/resolution helpers. No additional scored workload or
production candidate is selected. Build both artifacts from this committed
harness, with Central `4.4.2` and current `4.5.0-SNAPSHOT` respectively. Verify
source hashes, ordered classpaths, non-starter dependencies and shaded classes
before measurement. Retain both benchmark correctness suites and all failures.

Oracle Java 21.0.8, Maven 3.9.9, JMH 1.37 and Boot 4.0.0 remain fixed. Use one
thread, average time in ns/op, two forks, five one-second warmups and five
one-second measurements, `-prof gc`, `-foe true`, a fixed 512 MiB heap,
`ActiveProcessorCount=2` and WARN logging in launcher and fork. Keep all samples
and intervals. Run published then current. Confirm the union of every primary
flag and the historical enabled-only GET publisher once, current then published.
The confirmation selection is saved before that run; it is not expanded or
repeated to obtain favorable results. A primary invocation is bounded to 60
minutes, each confirmation row to five minutes.

The unchanged review triggers are **>20% latency** and
**>max(32 B/op, 5%) allocation** growth. Triggers require review, not automatic
acceptance or automatic attribution to production code. Missing samples, semantic
failure, failed provenance, OOM or mismatched inputs stop the affected lane.
No best-fork selection, threshold changes, JVM tuning or public speedup is allowed.

After scoring, regenerate the Priority 2 same-JAR controls and diagnostics
against these newly built JARs: its frozen 46-fork schedule, three profiles in
both orders, compiler traces, escape-analysis/inline interventions and allocation
sampling. This independent, preselected diagnostic schedule replaces missing raw
attribution evidence, not the primary scores. Preserve product-JVM diagnostic
unavailability and profiler perturbation. Export only allocation events from JFR;
quarantine original recordings outside the bundle. If traces do not explain an
observed mode or a new flag, keep it unresolved rather than rerun until explained.

No agent-started Maven, tests, native build or separate profiler may overlap
scoring. Capture CPU/affinity/cgroup/pressure/memory before and after invocations;
require at least 2 GiB available RAM before starting. This is a shared workstation,
not a dedicated latency lab; namespace-limited observations do not prove complete
host exclusivity. B/op is neither retained heap nor application/pod RSS.

## Compatibility and Evidence Boundary

Run strict root and independent starter source/binary comparisons using separate
fresh repositories seeded with third-party artifacts only. Resolve project
baseline artifacts from Central; never install current artifacts into these
repositories. Run the starter lane even if root fails. Keep both exits, effective
POMs, reports, dependency provenance and negative-fixture results. No report-only
or relaxed compatibility mode is permitted. Verify binary/source/Javadoc and
generated metadata packaging on both supported Boot rows.

[Priority 9](VERIFICATION.md) supplies fresh complete module, assembled consumer,
physical-absence, JVM/AOT, native and controlled ownership evidence. Reuse requires
rehashing its manifest and native binary and comparing production, dependency,
fixture and runner inputs with its clean source. It is not another native run or
another full-suite execution. Source and API identity alone do not establish
behavioral compatibility; retain those witnesses and their limits.

No production change survived Priorities 4-8. Do not claim an optimization benefit
for rolled-back candidates, restore them, or recreate their old scores. Individual
candidate decisions remain historical no-change outcomes; final acceptance applies
only to the current combined artifact and fresh controls. Priority 11 must still
reconcile the full ledger, and Priority 12 still requires a maintainer decision.

Use fresh detached checkouts and external sibling repositories/evidence, never
reactor-root `clean`. Preserve every failed attempt and copy sealed evidence to
`verification-evidence/` outside Maven output before a conventional `target/` copy.
Tracked reproduction commands and actual results follow when execution completes.

## Fresh Results

Recorded 2026-10-11. All planned stages completed with exit 0, without a scoring
retry or a changed threshold. There are **60 primary rows per artifact**, four
reverse-confirmation rows per artifact and the preselected 46-fork allocation
investigation: **302 forks total** (240 primary + 16 confirmation + 46 controls).
The original command logs, JSON, samples, intervals and review flags are retained.

| Lane | Result |
|---|---|
| Primary pair | 60 matched rows; zero latency flags, three allocation flags |
| Reverse confirmation | Four matched rows, including historical enabled-only GET; zero flags |
| Same-JAR controls | MINIMAL, AUTO_REGISTRY and enabled-only GET, both orders; zero mean review flags, enabled-only fork modes still vary |
| Separate diagnostics | Eight trace forks, twelve intervention forks, two JFR forks; all completed |
| Matched benchmark correctness | 75 cases per artifact, zero failures/errors/skips |
| Strict root API | Passed against Central 4.4.2; starter, helper and OTel report `No changes.` |
| Independent starter API | Passed in its separate repository; `No changes.` |
| Packaging | Binary/source/Javadoc, generated metadata and resource guards passed on Boot 4.0.0 and 4.1.0 |
| API/provenance negative fixtures | Passed; expected incompatible/local/mixed-artifact failures preserved |

The 39 harness/POM inputs and 120 non-starter JARs match. All **24,224 shaded
class entries**, including 310 starter classes, are identical between artifacts.
Only the starter's embedded Maven POM and properties differ. Classpath order
matches after replacing the starter coordinate with a common placeholder.
This excludes a production-code/dependency delta; it does not, by itself, explain
allocation variation or pass the cost gate.

| Artifact | SHA-256 |
|---|---|
| Central starter 4.4.2 | `fb8646ce2f6ed172598ff446cdfa9da2f348c0fd56a2b4c9a02e7ac1dae803c1` |
| Current scored starter | `c3746166dc136fe56235fe5aea2f2b603ddc0d2781c00b06ad8e9f670e57076f` |
| Baseline benchmark JAR | `e0de82156b03f42c986f10fd9a66229956df64189e5de605b345affccdd48d6c` |
| Current benchmark JAR | `b9fd5b42980c03cd8c0415f68101aef553e266354b672ea4f3b3ca992a3376b9` |

The reachable source tree is `2cc00be41a12749d74d508e6681e51dc648da3f7`.
Builds used clean detached checkouts. The invoking checkout added only the
verification runner, its tests and this record before measurement; later changes
are documentation/tooling and a documentation guard. No production, harness,
dependency, payload, fixture or JVM setting changed between compared runs.
Boundary available RAM was 7.17-7.32 GiB. CPU/cgroup/affinity/pressure snapshots
remain available, with their namespace and shared-host limitations.

### Primary Flags Remain Visible

All three flags are GET warm-publisher assembly, not transport latency. Values
are B/op fork means rounded to three decimals, with the primary JMH mean/error
in the last column. The full unrounded samples remain authoritative.

| Profile | Baseline forks | Current forks | Current mean +/- error |
|---|---|---|---|
| AUTO_NO_REGISTRY | 832.013 / 832.013 | 832.012 / 1088.015 | 960.014 +/- 203.988 |
| AUTO_REGISTRY | 1744.021 / 1744.020 | 2000.024 / 1744.021 | 1872.022 +/- 203.988 |
| HOOK | 1744.022 / 1744.022 | 2000.023 / 1744.022 | 1872.022 +/- 203.987 |

Each current artifact has a high/low split of approximately 256 B/op, giving a
128 B/op mean increase over its two low baseline forks. Reverse confirmation
observed only the low mode in these three profiles. The historical enabled-only
row was also confirmed even though its primary means did not cross a trigger.
**Zero reverse flags does not erase primary history or establish attribution.**
These were the three pending profiles before the approved follow-up below.

### Regenerated Historical Attribution

Fresh enabled-only compiler traces reproduce both modes on **both** artifacts:

| Artifact / fork | B/op | Validator compile ID / start | Selection allocation |
|---|---:|---|---|
| Baseline / 1 | 1392.017 | 2559 / 1.073 s | Constructor not inlined: `unloaded signature classes`; Decision alone eliminated |
| Baseline / 2 | 1136.016 | 2620 / 1.040 s | Selection and Decision eliminated |
| Current / 3 | 1392.017 | 2568 / 1.066 s | Same unloaded-signature failure; Decision alone eliminated |
| Current / 4 | 1136.016 | 2575 / 1.001 s | Selection and Decision eliminated |

The other four trace forks are low mode and also eliminate Selection. Class-load
records identify the actual saved JAR and the signature type's load time. Both
artifacts reach approximately 2032 B/op with escape analysis disabled, 1392 B/op
with resolver inlining prohibited, and 1136 B/op in the inline-request controls.
These are diagnostic interventions, not recommended runtime flags or score
replacements. Allocation-sampled JFR forks measured about 967 and 1127 B/op,
showing perturbation; they are not exact object counts or benefit evidence.

This directly regenerates the historical enabled-only mechanism: eight endpoint
checks can materialize eight disabled Selection objects, depending on constructor
inlining and scalar replacement. The new primary profiles share the same 256-byte
pattern and validator path, making that mechanism a plausible explanation, **not
a demonstrated cause for those profiles**. Their untraced primary forks cannot
retroactively supply compiler evidence. Existing controls do not justify a
blanket extension of the enabled-only conclusion.

The frozen 46-fork investigation is exhausted. On 2026-10-11 the maintainer
explicitly approved one additional bounded compiler-only follow-up: four trace
forks and two resolver-dontinline forks per artifact/profile, **36 forks** total.
Run AUTO_NO_REGISTRY, AUTO_REGISTRY and HOOK, in that order; for each profile run
trace baseline/current, then dontinline baseline/current. Preserve the existing
JARs, JVM/workload settings and all 302 earlier forks. Freeze the schedule and
input hashes before starting, retaining all compiler/class-load logs and samples.
No JFR, rescoring, extra fork, tuning or favorable-run selection is authorized.
Use the existing 2 GiB start bound and a five-minute timeout per invocation.

This follow-up tests whether each profile's high/low mode coincides with the
disabled Selection constructor's inlining/scalar replacement, and whether
prohibiting resolver inlining reproduces its high mode on both artifacts. If
either mode is absent or traces/interventions do not explain a profile, retain
that limitation and leave its acceptance pending. Diagnostic scores never
replace the primary or confirmation scores. **10.2 remains pending** until
review; no reduction of required scope or release approval is implied.

### Approved Follow-Up Results

All **36 forks** completed with exit 0, with no retry, additional profile or
change to the earlier evidence. Total execution is **338 forks** (the original
302 plus 36 diagnostics). Every invocation retains before/after host snapshots;
the original scored results and their three flags are unchanged.

| Profile | Ordinary trace forks, baseline / current (B/op) | Dontinline controls, both artifacts | Disposition |
|---|---|---|---|
| AUTO_NO_REGISTRY | 1088, 1088, 832, 832 / 832, 1088, 1088, 1088 | Four forks at approximately 1088 | Observed modes reproduced and attributed |
| AUTO_REGISTRY | Four at approximately 1744 / four at approximately 1744 | Four forks at approximately 2000 | Pending: ordinary high mode absent |
| HOOK | Four at approximately 1744 / four at approximately 1744 | Four forks at approximately 2000 | Pending: ordinary high mode absent |

AUTO_NO_REGISTRY's baseline trace fork 1 compiles the validator as task 2473 at
1.029 s, before the signature class loads at 1.040 s. Its Selection constructor
fails inlining with `unloaded signature classes`; Decision alone is eliminated,
and allocation is 1088.015 B/op. Baseline fork 3 compiles task 2476 at 0.958 s,
after the class loads at 0.937 s; both objects are eliminated, giving 832.013 B/op.
Current fork 1 also eliminates both (task 2553, 1.070 s; class load 1.068 s), while
current forks 2-4 reproduce the unloaded-signature failure and higher mode.
The resolver intervention confirms `disallowed by CompileCommand` and the same
256 B/op increment on both artifacts. This validates that profile's observed
mechanism, without reconstructing an untraced primary fork's compilation history.

All sixteen ordinary AUTO_REGISTRY/HOOK trace forks eliminate Selection. Their
eight intervention forks materialize it and add 256 B/op, but no ordinary high
trace connects those profiles' original flags to an unloaded-signature failure.
The shared mechanism remains plausible, not established under the frozen stop
rule. **AUTO_REGISTRY and HOOK remain unresolved/blocking for 10.2.**
The approved schedule is exhausted; no additional run or scope reduction is
implied. Owner: performance maintainer. The next decision must explicitly select
any further bounded investigation or disposition of the missing ordinary high-mode
evidence. No favorable confirmation, byte identity or intervened score supplies
a blanket performance pass.

## Compatibility and Reuse Audit

Both strict profiles retain binary/source failure switches and
`ignoreMissingClasses=false`; the existing optional-dependency handling is not
broadened. Baseline repositories contain only Central-resolved project version
4.4.2 and are separate from the current installation repository. Reports,
effective POMs, markers, exact exits and compared artifacts are retained.

The original root API check passed, but the later Boot 4.1 packaging build replaced
its target JARs before archival. A fresh strict root check after **all** measurement
completed passed again and retained its exact binary/source/Javadoc artifacts.
The original root reports and exits remain; the independent starter's original
JARs were still present and are retained without claiming a second starter run.
The tracked runner now copies compared artifacts before rebuilding another Boot
row. Its original frozen version is preserved beside the updated source; this
bookkeeping correction caused no benchmark or diagnostic rerun.

Priority 9 reuse verifies all **1,811 manifest entries**, its clean source
`f38fb8d22486d35401a7af9cb9e80f5e5e8c75ef`, unchanged production/POM/settings/
fixture/runner inputs and native binary SHA-256
`66de72017d7c8e8a33e1a4a593f55a0736813ffb479d88ccd3bbd7ec77bd7271`.
Its 2,446 complete-module cases per Boot row, 29 selected-consumer plus one
physical-absence case per row, JVM/AOT executions, native execution and separate
16/5/2 reachability cases are reused, **not added to fresh P10 test totals**.
That evidence retains dynamic extensions, mutation checks, auth/cache/retry,
body/context/terminal semantics and application-versus-starter ownership.

There is no retained optimization whose benefit could be accepted or advertised.
Candidate rollbacks remain historical decisions; fresh class identity and
untimed controls verify the final no-change artifact without recreating deleted
candidate scores. No new default, SPI, dependency, feature, version or release
selection has been made. Unexplained allocation still blocks final combination
acceptance even though API and correctness evidence passes.

Final tooling verification passes **56 Python cases** and **103 documentation
cases per Boot row**, with zero failures/errors/skips. The earlier 55-case
tooling and 103-case documentation runs remain in the bundle; these overlapping
reruns are not added to unique totals. The new follow-up schedule check accounts
for the one added Python case. `git diff --check` passes. Generated readiness
retains active V35, `4.5.0-SNAPSHOT`, published/API baseline `4.4.2`, an unselected
release lane and null planned final version.

## Reproduction and Preservation

The [runner](../../scripts/verify-v35-acceptance.py) refuses existing output
directories and uses separate external workspaces. From the repository root:

```bash
export PATH="$HOME/.sdkman/candidates/maven/current/bin:$PATH"
WORK=/tmp/v35-acceptance-new
python3 -B scripts/verify-v35-acceptance.py prepare --work "$WORK" \
  --ref beb8a237215529c9c8c7ddc285295c9564e4bce0 \
  --seed-repository "$HOME/.m2/repository"
for stage in api primary confirm allocation; do
  python3 -B scripts/verify-v35-acceptance.py "$stage" --work "$WORK" || break
done
```

Stop at a failed stage; an interrupted loop is not a pass. The new benchmark JARs
are sealed before execution. The Central-only baseline guard checks actual remote
markers and versions; third-party cache seeding never copies the project's group.
API lanes run independently and preserve failures. JFR originals remain under the
private workspace outside the exported evidence; only scrubbed allocation events
and their hashes are exported. The diagnostic reviewer validates that boundary.

The separately approved follow-up uses the same sealed pair, with a fresh output
directory and no new scored comparison:

```bash
python3 -B scripts/investigate-v35-final-profiles.py \
  --pair "$WORK/evidence" --output "$WORK/evidence/profile-followup"
```

The [follow-up runner](../../scripts/investigate-v35-final-profiles.py) freezes
its schedule, plan and input hashes, checks the prior evidence is unchanged, and
retains per-fork metrics plus compiler and class-load records. Successful execution
is not automatic attribution or acceptance; preserve absent modes as missing
evidence rather than expanding the schedule.

Fresh evidence originates at `/tmp/rhttp-v35-p10-20261011/evidence/`. A durable
copy and archive are kept under `verification-evidence/`, outside build output,
with a conventional copy under `target/release-evidence/v35/priority10/`.
Whole-bundle checksums are anchored in the checklist after source copies are
sealed. The source-copy seal excludes its own external checksum anchor.
The archive is local evidence, not a published benchmark report or release.
