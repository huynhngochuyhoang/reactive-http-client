# V30 Release Review

## Decision

**2026-09-11: GO to the `4.3.0` release cut; publication is not claimed.**
At the user's direction, this supersedes the local-signing scope hold and follows
the [V29 release boundary](../v29/CHECKLIST.md#x-134-select-release-scope-and-close-v29):
scope approval can precede credentialed workflow signing. The enforced additive
opt-in work limits and reviewed evidence justify the minor release, not a
patch-only scope. Checklist 13.3 is complete; all of 13.4 remains open.

Reviewed clean release-preparation commit:
`2d688b034ce47388da897fcaa8db4bdd30f88d38`. The final tag must include the
reviewed documentation/verification follow-up as well. Runtime, compatibility,
consumer, native and candidate checks below pass. Local signing failed with
`No pinentry`; that failure is retained. This GO is not a signing pass or
permission to upload unsigned artifacts. Successful signing, staged-consumer
verification and generation packaging remain mandatory before deployment.

## Scope

Candidate: `4.3.0`, additive minor; published/API/consumer/benchmark baseline:
`4.2.0`. Scope is explicitly selected per-factory/per-policy caller,
foreground-load and refresh ownership limits, enforcement, fixed local errors,
optional live telemetry, diagnostics and test-helper parity. Omitted limits
preserve the published behavior. The [surface freeze](../../docs/20-native-release-compatibility.md#v30-additive-surface-freeze)
defines supported additions.

No automatic queue, retry, refresh selection, timeout, heap/RSS guarantee,
distributed limit, or public cache-engine SPI is introduced.

## Evidence Review

Clean source under review: `d911226e478769f084bc46702587e004187c18d1`,
with reactor `4.3.0-SNAPSHOT`. A separate local clone remained clean before
and after verification. The `4.3.0` candidate is that source plus the
version/documentation/verification patch; it changes no production Java.
Those candidate runs were explicitly uncommitted, not evidence attributed to
a nonexistent final release commit. The clean-commit follow-up below records
the subsequent committed review without relabeling the earlier evidence.

Evidence root: `target/release-evidence/v30/priority13/`. Each command has
timestamps, exit status and output; copied XML, API reports, dependency trees,
effective POMs, remote markers and artifact inventories retain their own input
versions. Generated evidence is local/ignored, not automatically an uploaded
release artifact. Preserve this directory when archiving or moving the review.

### Clean-source results

| Check | Result | Evidence below the root |
|---|---|---|
| Complete reactor `clean verify` | 1,692 tests: 1,563 starter, 73 helper, 56 OTel; zero failures/errors/skips | `clean/reactor/` |
| Generation/package and support capture | Pass; eight Python support unittest methods | `clean/generation/`, `clean/support/` |
| Strict root and starter-only API comparison | Pass in independent fresh Central repositories against `4.2.0`; additive starter/helper changes, no OTel API delta | `clean/api-root/`, `clean/api-starter/` |
| Negative API/provenance guards | Pass, including source-only checked-exception and binary removals, defaulted annotation compatibility, non-Central/mixed/wrong-version/missing-artifact rejection | `clean/api-fixtures/`, `clean/baseline-fixtures/` |
| Current assembled consumer | 69 selected helper, nine consumer and one no-Caffeine tests; verified assembled classpath | `clean/current-consumer/` |
| Published `4.2.0` consumer/artifacts | Four tests and all 13 parent/module POM/JAR/source/Javadoc artifacts; fresh Central-only repositories | `clean/published-consumer/`, `clean/published-artifacts/` |
| Java 21 / Boot `4.0.0` and `4.1.0` matrix | Each row: 1,692 reactor plus three consumer tests, optional-integration guards and strict API comparison pass; separate fresh dependency/API repositories | `recovery/matrix/rows/` |
| Native fixture and executable | Five fixture tests; clean-source native compile and executable pass, including V30 shutdown/no-late-dispatch assertions | `clean/native-fixture-tests/`, `recovery/native-compile/`, `recovery/native-run/` |
| Benchmark smoke | 62 rows / 20 methods; no release-quality inference | `clean/benchmark-smoke/` |

Maven `3.9.9`, Linux amd64. The normal/native runs use GraalVM JDK and
native-image `25.0.3`, Java bytecode target `21`, default Boot `4.0.0`.
The matrix uses a complete Oracle JDK `21.0.8` and records actual versions
per row: Framework `7.0.1/7.0.8`, Reactor Netty `1.3.0/1.3.6`, Netty
`4.2.7.Final/4.2.15.Final`, Jackson `3.0.2/3.1.4`, Micrometer
`1.16.0/1.17.0`, OTel `1.55.0/1.62.0`, Caffeine `3.2.3/3.2.4`,
Resilience4j `2.4.0`.

The first matrix attempt lacked the JDK 21 Javadoc executable; its partial
reports are retained. The first native compile exhausted a 3 GiB compiler heap.
The successful retry preserves shared-arena support and uses `-J-Xmx4g` with
`--parallelism=2`. These are environment retries, not passing initial runs
or changes to production defaults. [Native commands and hash](CHECKLIST.md#native-gate-closure-2026-09-11)
identify the exact successful executable.

### Candidate results

`candidate/` identifies the dirty release-preparation patch separately from
the clean snapshot runs. All commands terminated before this review:

| Check | Result | Evidence below `candidate/` |
|---|---|---|
| Complete reactor `verify` after module cleanup | 1,693 tests: 1,564 starter, 73 helper, 56 OTel; zero failures/errors/skips | `reactor/` |
| Support/documentation/metadata | Eight Python methods and 49 documentation tests pass; reactor includes 35 properties and 18 starter metadata tests | `support/`, `readiness/`, `reactor/` |
| Release-profile unsigned packaging | All 13 `4.3.0` artifacts retained; embedded versions and generation/package checks pass | `unsigned-packaging/`, `artifacts/`, `generation/` |
| Signed packaging | **Failed: No pinentry**; signed staged-consumer preflight was not run | `signing/` |
| Strict root/starter-only API | Both pass against separate fresh Central `4.2.0` repositories; provenance verified | `api-root/`, `api-starter/` |
| Current assembled consumer | 69 helper, nine consumer, one no-Caffeine tests; provenance reports `evidence-verified` and dirty candidate source | `current-consumer/` |
| AOT package/JVM execution | Five fixture tests and generated-AOT JVM execution pass, including capacity/shutdown assertions | `aot-package/`, `aot-jvm/` |
| Benchmark package/smoke | 11 selected harness/report tests; 62 rows / 20 methods at final coordinates, explicitly smoke-only | `benchmark-package/`, `benchmark-smoke/` |
| Generated readiness | `release-candidate`, `plannedFinalVersion=4.3.0`, `published=false`, published/API baseline `4.2.0` | `readiness/` |

The subsequent unrestricted benchmark test run passes **all 27 harness/report
cases**, zero failures/errors/skips, including the 16 Markdown report tests
omitted by the earlier selected-test command. Its command and copied XML are
under `final-validation/benchmark-tests/`; it does not replace smoke or manual
measurements.

The test-generated manual command list is conservative: it does not ingest
external command results or prove a clean final commit. This review supplies
the actual provenance and retains signing, the clean release commit/tag,
publication, Central verification and published-consumer adoption as gates.
Final documentation-only validation is retained separately in `final-review/`;
it does not overwrite the candidate command outputs or hashes.

### Benchmark disposition

Priority 12 retains a **no-public-performance-claim** disposition. Its manual
measurements used `ad87b60fa4daa144b6a01fa258932747f4288284`; that original
provenance is not rewritten or described as a new final-coordinate measurement.
That measured revision is not an ancestor of the squashed review commit. Local
object comparison shows identical runtime, benchmark, fixture, build and script
content; only three review documents differ. The empty
`benchmark-runtime-source.diff` and `benchmark-review-documents.txt`
retain this comparison. A portable archive of the reachable review source is
also retained; it can be regenerated from the reviewed history:

```bash
git archive --format=tar d911226e478769f084bc46702587e004187c18d1 | sha256sum
```

The original 62 current / 16 baseline rows and 16 review flags remain qualified
evidence, not final-version numbers. Same-source smoke proves harness operation,
not improved latency or allocation. See the [audit](PERFORMANCE-ALLOCATION-AUDIT.md).

### Integrity anchors

SHA-256 values, relative to the evidence root:

| Artifact | SHA-256 |
|---|---|
| `reviewed-source-d911226e.tar` | `338c3567f6f7541d9799b7d7ae041d107a228b49f3f8a94e1a7a103fbda23866` |
| `clean/sha256.txt` | `4bd5c59809d02df53a55d1bc7ae90e05ebd48f93992fa0cd3459b1d2309867a9` |
| `recovery/sha256.txt` | `a7ea131befdd0394690f9b417f693d8b8ded0cb8fb1c661c8bceda7d75bbf423` |
| `candidate/sha256.txt` | `cb997f88bf11cdcb5f45653d10286243091e25e295746c501b980f7fe42eb14b` |
| `final-validation/sha256.txt` | `b1d94a15b594ccbe18678d9ad5cb83f4191cd6fa2e30b6dd4bb8cdf4d9a33b7e` |
| Native executable | `84177bc6d5def1a607fee3678675541a86b02ced4bbbbc6411c3db6facb5101b` |

Priority 12's original report hashes and `release-sha256.txt` remain unchanged.

## Clean-Commit Follow-Up

On 2026-09-11, reviewed clean release-preparation commit
`2d688b034ce47388da897fcaa8db4bdd30f88d38` with final coordinates `4.3.0`.
There are no production Java changes relative to the previously reviewed
`d911226e` runtime. Before/after status files are empty. The clean-source
readiness rerun passes **49 tests**, zero failures/errors/skips.

Retried `mvn -B -ntp -s .mvn/maven-central-settings.xml -Prelease -DskipTests verify`
in a terminal session with `GPG_TTY` set. Signing still exits nonzero with
`gpg: signing failed: No pinentry`; no signed artifact was produced and the
signed staged-consumer check was not run. Committing resolves the source-state
gate, not this external signing failure. That retry changed no key/agent
configuration. A subsequent local repair explicitly selected the installed
terminal pinentry backend and reloaded the agent. A non-secret confirmation
test proves the agent can launch it. Inspection of the installed GPG plugin
`3.2.4` identifies the actual blocker: Maven `-B` sets non-interactive mode,
which selects `--pinentry-mode error` without supplied signing credentials.
The local command and readiness generator now omit `-B`; CI keeps its
credentialed batch-mode command. Signing must succeed before publication, either
locally or in the credentialed workflow; interactive local signing is not a
scope-selection prerequisite. Prompt configuration validation is not signing
evidence.

Separate evidence:
`target/release-evidence/v30/priority13-3-2d688b03/` contains the clean commit,
toolchain, commands, timestamps, signing output/exit status, readiness JSON/XML
and inventories. SHA-256 of `sha256.txt`:
`560cb2728aa49ed3fa2d514219d378bfe6fd6c3b44e3f561680582dddde3f58a`;
SHA-256 of `readiness-sha256.txt`:
`6eb86b25b928c1f8f513cf39cf87c81ba11e0bd47bc10605bff690d77b0e8be2`.
Earlier evidence remains unchanged. The subsequent user-approved scope GO closes
13.3 and assigns signed preflight to 13.4 alongside publication, tagging and
archive actions. The generated readiness list still names those pending gates.

The documentation/verification follow-up is recorded separately under
`target/release-evidence/v30/priority13-3-scope-go-final/`, with the base commit,
uncommitted source patch, command, test XML, readiness manifest and SHA-256
inventory. It does not relabel the clean-commit evidence as validation of this
later patch. The initial broken-link check is retained under
`target/release-evidence/v30/priority13-3-scope-go/`.

## Publication Boundary

No publication or tag was created. The release-preparation source is committed;
review and commit this follow-up before tagging. The existing
`.github/workflows/publish-maven-central.yml` rejects a mismatched tag and then
builds/signs, verifies staged signatures and consumption, and checks generation
packaging before its Central deployment step. It supplies the configured release
credentials; that successful run must provide the 13.4 signing evidence. The
local failure does not justify skipping any of those steps.

An optional local preflight uses the intended release key and working pinentry:

```bash
export GPG_TTY="$(tty)"
mvn -ntp -s .mvn/maven-central-settings.xml -Prelease -DskipTests verify
bash scripts/verify-publishable-artifacts.sh 4.3.0
bash scripts/verify-generation-packaging.sh 4.3.0
```

Preserve this evidence before any root `clean` or repeated verifier run.
The local signing command intentionally omits batch mode so the agent may
prompt for the key passphrase. Do not pass the passphrase on the command line.
These commands package and locally stage artifacts; they do not publish to
Central. Successful signed preflight, locally or in the workflow, is required
before deployment; publish only the reviewed clean final commit/tag through
13.4. After publication,
13.4 must independently verify
all 13 artifacts from fresh Central-only repositories and run the published
assembled consumer before moving baselines or archiving V30.

Remaining risks: work limits bound ownership counts, not retained bytes or
process memory; arbitrary blocking application code can retain ownership until
it unwinds. Smoke/JFR evidence is not an exact memory or performance guarantee.
The 16 benchmark review flags remain acknowledged, with no public performance
claim. Native coverage is limited to the documented smoke scenarios, not every
optional TLS/operator/exporter integration. The copied unsigned artifacts and
snapshot native executable must not be relabeled as signed final-release proof.
