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

Native evidence pending: commit the final fixture and implementation, verify a
clean reachable HEAD, then install that revision and compile/run with GraalVM.
Do not reuse an older binary or mark 7.3 complete from the JVM results above.
Record toolchain, available RAM/swap, command exits, full compile/run logs and
binary SHA-256; preserve failures separately. Example build after that gate:

```bash
test -z "$(git status --porcelain)"
git rev-parse HEAD
java -version
native-image --version
free -h
mvn -B -ntp -s .mvn/maven-central-settings.xml -DskipTests -Dmaven.javadoc.skip=true install
mvn -B -ntp -s .mvn/maven-central-settings.xml -f .github/native-smoke/pom.xml \
  -Pnative '-DbuildArgs=-H:+SharedArenaSupport,-J-Xmx6g,--parallelism=2' clean native:compile
timeout 180 .github/native-smoke/target/reactive-http-client-native-smoke
sha256sum .github/native-smoke/target/reactive-http-client-native-smoke
```

See [checklist](CHECKLIST.md), [cross-path regressions](CROSS-PATH-REGRESSIONS.md)
and [AOT selection](AOT-PROPERTIES-SELECTION.md). Relevant later source changes
invalidate native evidence and require another clean-source run.
