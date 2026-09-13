# V31 Context Parity Evidence

## Scope and Source

The reviewed starting revision is
`cd75725dae3cab2b8b39d46672820180991d561f`. Priority 7 adds tests, consumer
profile wiring, the native fixture and documentation on top of that revision.
Verification of this working tree is **dirty-tree evidence**, not a clean-commit
native certification. Development remains `4.4.0-SNAPSHOT`; published artifacts
and public/API/consumer baselines remain `4.3.0`.

## Covered Boundaries

- `MockInboundContextParityTest`: five cases preserve the published exact-spelling
  assertion contract while exercising case-insensitive public access through
  recorded snapshots. Captured lowercase/mixed-case, absent, empty-string,
  duplicate and redacted values are distinguished. Normal and deterministic
  cache construction both isolate repeated cold subscriptions and terminate
  owned single-flight work on close. Recordings intentionally survive close for
  assertions; retaining one retains its snapshot. No forced GC is required.
- `Boot4InboundContextConsumerTest`: two cases use the assembled starter, a real
  Boot reactive context and IPv4 loopback WebFlux capture. Optional/all-values
  access, ambiguity and required-header authorization are exercised. A bounded
  envelope queue passes snapshots to independent worker subscriptions; capture
  does not forward inbound headers. The same cases also use replacement
  properties, metadata and capture-filter beans.
- The `consumer.v31.parity=true` profile is candidate-only. The minimal consumer
  compiles the same source with neither Caffeine, OTel nor the test-helper
  artifact. Published consumer source selection never enables this profile.
- `ReactiveHttpClientAotSmokeTest` covers configured metadata/properties/filter
  identity and direct named access without header-DTO reflection. Existing
  primary properties, replacement metadata and foreign-factory tests remain.
- `NativeInboundContextScenario` uses the application-managed capture filter
  over real loopback HTTP, then restores two snapshots on independent
  subscriptions. Three ingress calls include one missing-header rejection;
  duplicates, empty strings, allowlisting and redaction are checked. The JVM
  test and native main execute the same scenario. No additional runtime hints
  or production/helper dependencies are introduced.

## Verification

Evidence is retained under `target/release-evidence/v31/priority7/`.
The local `verify.sh` records commands, toolchain, output, exit status and fresh
Surefire XML per stage, including failures. The assembled-consumer verifier
additionally records effective POMs, dependency trees, classpaths, installed
artifact hashes and its last completed stage in its usual evidence directory.

Passing runs on 2026-09-12:

| Stage | Result |
| --- | --- |
| Fresh assembled consumer | 74 mock + 11 full consumer + 3 minimal consumer tests |
| Targeted reactor regression | 153 starter + 74 mock tests |
| Native fixture JVM tests | 6 tests |
| Packaged JVM application | Exit 0, V31 marker and existing smoke completed |
| Regenerated Spring AOT JVM application | Exit 0, V31 marker and existing smoke completed |

Every listed test run had zero failures, errors or skips. The starter regression
includes 27 AOT tests, 49 documentation tests and the 22 Priority 6 composition
cases. Maven `3.9.9`, GraalVM JDK `25.0.3`, Java target `21`, Boot `4.0.0` and
`.mvn/maven-central-settings.xml` were used. The evidence runner completed through
`aot-jvm` with exit zero. These are targeted parity checks, not a new full-reactor,
strict API, published-consumer or native-executable release certification.

The initial mock compilation failed on a concrete `DataBuffer` generic type;
the next AOT compilation failed on a missing test import. Both were fixture
mistakes, corrected before the first passing focused run (27 AOT + 5 mock
cases). Their logs are retained separately, not counted as successful evidence.

## Native Gate

The final native compile/executable gate remains **pending** until the updated
fixture is committed and the tree is clean. An older binary cannot close it.
After committing the complete fixture, run from that clean checkout:

```bash
git status --short
mvn -B -ntp -s .mvn/maven-central-settings.xml -DskipTests install
mvn -B -ntp -s .mvn/maven-central-settings.xml -f .github/native-smoke/pom.xml \
  -Pnative '-DbuildArgs=-H:+SharedArenaSupport,-J-Xmx4g,--parallelism=2' clean native:compile
.github/native-smoke/target/reactive-http-client-native-smoke
sha256sum .github/native-smoke/target/reactive-http-client-native-smoke
```

Record the reachable fixture commit, clean status, `mvn -version`,
`native-image --version`, exact commands, compile/run exit statuses, full
output and executable hash. The run must contain the V31 capture/restoration
marker and pass the existing cache/resilience/shutdown smoke as well. Rebuild
after any relevant source change. This fixture does not certify Istio, MVC,
arbitrary application header DTO parsing, automatic propagation or native OTel.
