# V33 Mock, Consumer and AOT Parity

> **Status:** 7.1 and 7.2 verified; Native evidence pending (7.3).
> **Release scope:** unselected.

Measured 2026-09-27 from base commit
`fefe218583a321e530f412c99c3672e5feade938` plus the recorded fixture diff.
This is installed current-reactor evidence, not Central consumption or immutable
native release evidence. No production implementation changed in this priority.

## Witnesses

- The external `example.v32.ExtensionScenariosTest` now selects its programmatic
  properties as a non-primary, non-fallback candidate. The ordinary registered
  properties bean remains as a fallback. The fresh public metadata test checks
  the selected object and no dispatch during AOT processing, then verifies actual
  GET targets, decoded values, observers and lifecycle events. Existing tests
  cover starter-builder ownership, required SAFE classifications, application
  customizers, tenant/auth partitioning and application-owned resources.
- The mock lane uses public metadata replacement and selected cache policies,
  including repeated cold subscriptions and distinct path identities. It is
  helper evidence, not Spring preference or TCP evidence.
- The minimal consumer has resilience enabled with no operator selection. Its
  catch-all server counts requests and only accepts GET `/value`. Both classpaths
  physically omit Caffeine, Resilience4j, OTel and the mock helper.
- The native smoke fixture's `getCachedOrder` annotation deliberately names an
  unusable path. A replacement `MethodMetadataCache` returns a fresh public
  metadata object selecting GET `/api/cached-order` and `native-cache`. That policy
  exists only in `nativeProperties`, not the environment. The selected bean is
  explicitly checked as non-primary; other smoke settings bind from the
  environment before the programmatic policy is added. A separate binding prefix
  prevents a later environment binding pass from replacing that policy map.
- The smoke retains method-specific routes plus a counted catch-all, decoded
  record assertions, miss/hit/refresh dispatch counts, lazy diagnostics, close,
  queued-work cancellation and same-tag recreation checks. It does not classify
  `starterWebClientBuilder` as SAFE. Existing DTO binding hints remain explicitly
  application-supplied; processor tests separately verify selected-policy record
  hints and non-instantiation, invalid-selected and environment controls.

## Results

| Lane | Boot | Result |
| --- | --- | --- |
| Public mock helper, four classes | 4.0.0 | 76 passed |
| Assembled external consumer | 4.0.0 / 4.1.0 | 29 passed each |
| Physical optional-dependency absence | 4.0.0 / 4.1.0 | 1 passed each |
| AOT selection/metadata/hints/cross-path, five classes | 4.0.0 / 4.1.0 | 283 passed each |
| Smoke fixture unit tests | 4.0.0 / 4.1.0 | 6 passed each |
| Ordinary smoke JVM execution | 4.0.0 / 4.1.0 | exit 0 each |
| AOT generation and AOT-enabled JVM execution | 4.0.0 / 4.1.0 | exit 0 each |
| Documentation release-artifact guard | 4.0.0 | 75 passed |

All listed test results have zero failures, errors and skips. Ordinary focused
tests disable explicit GC. Java is Oracle 21.0.8; verification uses Maven 3.9.9
(the initial successful reactor install used system Maven 3.8.7). The repository
is the existing local Maven cache, not a fresh isolated download repository.
The full Priority 8 compatibility/regression lane is not claimed here.

## Reproduction and Artifacts

Run from the project root with Java 21 and Maven on PATH:

```bash
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
for stage in install mock consumer40 consumer41 minimal40 minimal41 aot-tests aot-tests41 jvm40 jvm41 docs; do
  python3 scripts/verify-v33-parity.py "$stage" --evidence /tmp/v33-parity-new || break
done
```

Each stage requires a new output directory. `--repository` can select a fresh
repository; the recorded run used the existing default repository. The script
copies tracked fixture sources without target outputs and parses the POM to set
both the actual parent and (where present) Boot property. Every resolved Boot
artifact must match its row. Consumer classpaths must contain only JARs and the
assembled starter artifact; no reactor classes directory is accepted.

Evidence root: `target/release-evidence/v33/priority7/`. Stage directories retain
commands, exit statuses, logs, source commit/diff/status, test XML and totals,
effective POMs, dependency trees, classpaths and dependency hashes. The final
Boot 4.0 smoke evidence is under `retry1/jvm40`; the original `jvm40` records the
failed experiment where environment binding replaced the programmatic map.
It failed ordinary JVM startup with an undefined policy; no AOT/native success
is inferred from that run. `SHA256SUMS` seals the evidence files excluding itself.

## Native Gate

Native evidence pending. At the user's request the first 7.3 procedure is
retired and its checklist checks reset for a fresh run. The implementation and
fixture are committed at `bcb2045ec858efa809602e73eb1a24ebe3567463`; no native
pass or binary hash exists yet. Neither the JVM results nor a successful socket
preflight closes this gate.

### Non-Root Replacement

Use the tracked [native runner](../../scripts/verify-v33-native.py), not the
ignored `retry2-run.py`. Launch it with `python3`; executable permission on the
script is not required. It explicitly refuses root execution. Do not use sudo,
change ownership broadly, or run Maven as root to address a sandbox socket error.

The runner creates a uniquely named workspace under `target/v33-native-runs/`
with private user-owned source, repository and evidence directories. Each run
checks out the selected commit into a clean detached clone; it neither resets
the main worktree nor reuses a previous build or repository. The optional seed
cache is read/copied, never modified, and file ownership/restrictive modes are
not preserved into the new repository. It requires Python 3, Git, Maven,
GraalVM/native-image, Linux coreutils, and permission to open loopback sockets.

From the project root, using the installed GraalVM:

```bash
python3 scripts/verify-v33-native.py \
  --java-home "$HOME/.sdkman/candidates/java/25.0.3-graal" \
  --seed-repository "$HOME/.m2/repository"
```

`--ref` defaults to committed `HEAD`; working-tree changes are explicitly
excluded and recorded. Commit any implementation/fixture corrections before
running. `--work-root` may point to another user-writable directory. Omitting
`--seed-repository` uses an empty repository and requires dependency downloads.
Either mode installs the selected reactor, not Central consumption of the starter.

The compile retains fixture tests and uses a 6 GiB native-image heap with two
build threads. Successful compilation is followed by the executable under a
180-second limit. Each command records its exit status/timestamps and logs;
failures and timeouts remain failures. Timed-out command process groups are
terminated. The printed evidence directory contains toolchains/resources,
source provenance, reports, dependency tree/effective POM/classpath, and, only
after compilation, the binary and its SHA-256. `summary.json` distinguishes
compilation from successful execution; `SHA256SUMS` seals every evidence file
on failure as well as success. No remaining checks can close without both a
successful compile and executable and review of the fixture witnesses above.

### Retired Failures and Verification

Earlier logs remain in `target/release-evidence/v33/priority7-native/`: the first
install failed against the read-only `~/.m2`; the writable-repository retry
passed reactor install but failed a loopback fixture test (five passed, one
error); the later clean-checkout retry failed its socket preflight. All used
the commit above. Native-image compilation was never reached. The old runner
and directories were user-owned, not root-owned; `retry2-run.py` lacked the
executable bit but was readable by `python3`. Those failures are not native
product regressions and must not be presented as passing native evidence.

The replacement runner's first attempted run is retained at
`target/v33-native-runs/native-06lse66k/evidence`: creating its workspace required
no elevation, but the sandbox still denied `socket()` before compilation.
Five standard-library runner tests passed, covering command/log/status capture,
failed commands, missing executables, timeout accounting and refusal of sudo.
Run them with:

```bash
python3 -B -m unittest discover -s scripts -p test_verify_v33_native.py -v
```

See [checklist](CHECKLIST.md), [cross-path regressions](CROSS-PATH-REGRESSIONS.md)
and [AOT selection](AOT-PROPERTIES-SELECTION.md). Relevant later source changes
invalidate native evidence and require another clean-source run.
