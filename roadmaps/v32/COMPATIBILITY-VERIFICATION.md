# V32 Compatibility and Targeted Verification

> **Status:** complete, 2026-09-17; native compile and executable passed
> **Accepted scope:** V32-F004 + V32-F005; F001-F003 remain deferred
> **Release scope:** unselected

## Source and Scope

Verification uses clean reachable commit
`c8f6a527450ea512ed6837bd8d09191921d4dd48` in an isolated local clone.
Root clean builds cannot erase the original workspace's earlier evidence.
Reactor version is `4.5.0-SNAPSHOT`, published baseline is `4.4.0`, and the
supported minimum is Java 21. Completion notes and their documentation guard
are a subsequent documentation/test-only patch, not a different runtime build.

The [scope decision](ARCHITECTURE-DECISION.md) approves exactly F004/F005.
The [implementation record](ACCEPTED-IMPROVEMENTS.md) describes their bounded
changes. Comparing main sources with `v4.4.0` finds only the local construction
failure guard in `ReactiveClientInvocationHandler`: 14 added and three removed
lines. No dependency, public signature, policy/default, request hot path or
native-fixture change is introduced by this verification.

## Risk to Evidence

| Boundary | Required witness |
|---|---|
| F004 failed assembly | All 17 `ResourceOwnershipReviewTest` cases: repeated auth rejection, telemetry on/off, overlapping owners, later runtime/error failures, cleanup suppression, missing Caffeine, successful ownership transfer and application-owned connector survival |
| F005 ordinary test portability | All 79 affected ordinary scenarios plus the 17 construction cases with explicit GC disabled; no collection-dependent assertion in these ordinary paths |
| F005 reachability | Separate 16-case controlled cache lane; existing V31 handoff lane remains separate and unchanged |
| Shared runtime/defaults | Full starter, helper and OTel suites on both supported Boot dependency rows; cache/auth/replay/timeout, diagnostics, selection and optional absence contracts remain applicable |
| Public consumption | Independent strict starter comparison plus strict root comparisons; assembled full/minimal consumers, all V32 extension cases and application-owned connector teardown |
| Bean creation/lifecycle | Existing native fixture's JVM, regenerated AOT JVM and fresh native executable, including cache creation, shutdown and context recreation |

API compatibility is not behavioral equivalence. F004 deliberately changes only
abandoned-resource cleanup. Desired-behavior unit witnesses inspect the meter
leases and preserve the original exception; assembled consumers and the native
fixture exercise successful construction, composition and teardown. Native
does not reproduce the private meter-owner inspection or controlled collection
test. No reflective test-only access or extra public API is added to native.
F005 changes test execution conditions, not consumer behavior or configuration.
Deferred selection and metadata characterizations still assert the documented
F001-F003 gaps/workarounds; a passing suite does not mean those findings are fixed.

## Results

| Fresh lane | Result |
|---|---|
| Boot 4.0.0 full reactor | 1,788 starter + 78 helper + 62 OTel; three default consumer cases; strict root API/provenance passed |
| Boot 4.1.0 full reactor | 1,788 starter + 78 helper + 62 OTel; strict root API/provenance passed; three script consumer cases used mixed versions, as explained below |
| Isolated current consumer | 74 mock + 28 full consumer + three minimal consumer cases; classpath/provenance checks passed |
| Property-only upper consumer attempt | 28 cases passed, but resolved Boot 4.0.0 with Spring 7.0.8; not Boot 4.1 consumer certification |
| Explicit Boot 4.1 consumer overlay | 28 cases passed; Boot 4.1 artifacts and assembled JAR-only classpath verified |
| Independent starter API | Strict source/binary comparison and fresh Central provenance passed |
| Ordinary explicit-GC-disabled contracts | 96 cases: 79 affected ownership/admission plus 17 construction cases |
| Controlled cache/V31 reachability | 16 cache cases and five unchanged V31 handoff cases, separate fork/report directories |
| Controlled-lane negative prerequisites | Three separate runs each fail one prerequisite assertion as intended: explicit GC disabled, G1 instead of Serial GC, and 256 MiB instead of the 128 MiB heap bound |
| API/baseline negative fixture guards | Both scripts passed, including source-only breaks, missing/locally installed artifacts and self-comparison rejection |
| JVM/AOT | Six fixture tests passed; packaged JVM and regenerated AOT JVM executables exited zero |
| Native | Fresh post-restart 6 GiB compile passed six fixture tests and generated the executable; smoke execution exited zero. Earlier heap-exhaustion/watchdog failures remain recorded below |
| Documentation guard | 63 cases passed after correcting one stale status assertion; final evidence-note rerun also passed 63 cases |

Each passing matrix row has zero failures, errors and skips. API packaging uses
`-DskipTests`; it is not an additional test run. Repeated executions across lanes
are reported separately, not added together as unique coverage.

The three expected prerequisite failures are not passing scenario counts or
skips. API/baseline fixture scripts also retain their intentionally rejected
inputs. Native compile failed with `OutOfMemoryError: Java heap space` after
method compilation at the 4 GiB build-heap limit. The failed command, full log,
exit status and six passing JVM fixture tests are retained, not replaced by old
native evidence. Neither failed attempt produced a verified native executable.
The available-memory check after failure showed about 3.4 GiB RAM and 385 MiB
free swap. At the maintainer's request, a second clean compile used 6 GiB and
two workers. It passed the six JVM cases again, then the Native Image watchdog
aborted during universe building after reporting no activity (native exit 30,
Maven exit 1). During that run, only 295 MiB RAM and 108 KiB swap were available.
The watchdog thread dump is retained. Memory pressure was observed, but the
watchdog output alone does not establish its root cause or a runtime deadlock.
Those attempts did not close the native gate.

After the host restart, a fresh isolated clone and Maven repository rebuilt the
same clean commit with about 10 GiB available RAM and unused swap. The source
archive is byte-identical to the earlier archive. GraalVM 25.0.3, target Java 21,
Boot 4.0.0, a 6 GiB build heap and two workers produced a successful compile:
six fixture tests, zero failures/errors/skips; Maven exited zero at
`2026-09-17T08:12:05Z` after 354 seconds. Native Image reported 5.40 GB peak RSS.
The freshly generated executable exited zero at `2026-09-17T08:12:24Z` after
18.8 seconds, within its 180-second external bound. This closes Priority 10.2.
No source, fixture or timeout relaxation was needed. The broader lanes above
are reused from the same clean commit, not claimed as rerun after the restart.

Native binary SHA-256:
`49332e7ff709fc9295c675ca54d176e52b40bc5262573afab37b2b0dc1d85d4f`.
The native build and smoke run had an empty Git status both before and after.

The resolved Boot rows respectively use Spring 7.0.1/7.0.8, Reactor Netty
1.3.0/1.3.6, Netty 4.2.7.Final/4.2.15.Final, Jackson 3.0.2/3.1.4, Micrometer
1.16.0/1.17.0 and Caffeine 3.2.3/3.2.4. Resilience4j remains 2.4.0. Exact
dependency trees and effective POMs, including test and OTel versions, accompany
each row. These are resolved test inputs, not new supported-baseline promises.

The artifact audit caught a consumer-fixture limitation: overriding only
`spring-boot.version` did not replace its fixed Boot 4.0.0 parent. Both the matrix
script's three upper-row consumer tests and the separate 28-case property-only
run still resolved Boot 4.0.0 artifacts with Spring 7.0.8. They are retained as
mixed-version results, not labeled Boot 4.1 consumption. The reactor's 1,928
module tests did resolve Boot 4.1.0. A disposable copy of the consumer fixture
sets both parent version and `spring-boot.version` to 4.1.0 for a corrected run;
the overlay POM and byte-identical test sources are archived separately. No
production source or tracked consumer POM is changed. This limitation must be
accounted for when reusing the unmodified matrix script's consumer results.

## Reproduction

Run on a clean checkout of the source commit above. Use fresh evidence and
repository directories; preserve existing evidence before root clean. The matrix
script performs full reactor installation, its default assembled consumer and
strict root comparison for Boot 4.0.0 and 4.1.0 using separate fresh repositories.

```bash
SUPPORTED_MATRIX_EVIDENCE_ROOT=/tmp/v32-matrix-evidence \
  scripts/verify-supported-matrix.sh
scripts/verify-current-consumer.sh
mvn -B -ntp -s .mvn/maven-central-settings.xml \
  -Dmaven.repo.local="$PWD/target/published-baseline-repositories/v32-p10-api-starter-4.4.0" \
  -pl reactive-http-client-starter -Papi-compatibility -DskipTests verify
scripts/verify-published-baseline-provenance.sh v32-p10-api-starter 4.4.0 \
  target/release-evidence/v32-p10-api-starter reactive-http-client-starter
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter \
  -DargLine=-XX:+DisableExplicitGC \
  -Dtest=ResourceOwnershipReviewTest,ResponseCacheRetentionOwnershipTest,CacheWorkOwnershipContractTest,CacheCallerAdmissionContractTest test
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter \
  -Pv32-cache-reachability test
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter \
  -Pv31-handoff-reachability test
scripts/verify-api-compatibility-fixtures.sh
scripts/verify-published-baseline-fixtures.sh
```

### Boot 4.1 Consumer Overlay

The following standalone recipe recreates `boot41-consumer-overlay/pom.xml`
from tracked sources; no archived `target/release-evidence` files are needed.
Run from the root of a clean checkout of `c8f6a527450ea512ed6837bd8d09191921d4dd48`,
using Bash, Python 3, Maven and Java 21. It copies the fixture without build
outputs, changes only the parent and version property to 4.1.0, installs the
reactor into a fresh repository and enables every V26-V32 consumer flag. It does
not modify the tracked Boot 4.0 fixture or certify the matrix script's mixed row.
Keep the printed temporary directory for its overlay, logs, XML reports,
effective POM, tree and assembled classpath.

```bash
set -euo pipefail
test "$(git rev-parse HEAD)" = c8f6a527450ea512ed6837bd8d09191921d4dd48
test -z "$(git status --porcelain)"
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
RUN="$(mktemp -d /tmp/v32-boot41-consumer.XXXXXX)"
printf 'Boot 4.1 reproduction: %s\n' "$RUN"
python3 - "$RUN" <<'PY'
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET

fixture = Path(sys.argv[1]) / 'boot41-consumer-overlay'
shutil.copytree('.github/boot4-consumer', fixture, ignore=shutil.ignore_patterns('target'))
namespace = {'m': 'http://maven.apache.org/POM/4.0.0'}
ET.register_namespace('', namespace['m'])
tree = ET.parse(fixture / 'pom.xml')
tree.find('m:parent/m:version', namespace).text = '4.1.0'
tree.find('m:properties/m:spring-boot.version', namespace).text = '4.1.0'
tree.write(fixture / 'pom.xml', encoding='utf-8', xml_declaration=True)
PY
MAVEN=(mvn -B -ntp -s "$PWD/.mvn/maven-central-settings.xml"
  "-Dmaven.repo.local=$RUN/repository")
"${MAVEN[@]}" -DskipTests -Dmaven.javadoc.skip=true install \
  2>&1 | tee "$RUN/reactor-install.log"
CONSUMER=("${MAVEN[@]}" -f "$RUN/boot41-consumer-overlay/pom.xml"
  -Dreactive-http-client.version=4.5.0-SNAPSHOT
  -Dconsumer.v26.observability=true -Dconsumer.v27.parity=true
  -Dconsumer.v28.parity=true -Dconsumer.v29.parity=true
  -Dconsumer.v30.parity=true -Dconsumer.v31.parity=true
  -Dconsumer.v32.extensions=true)
"${CONSUMER[@]}" clean test 2>&1 | tee "$RUN/consumer.log"
"${CONSUMER[@]}" help:effective-pom -Doutput="$RUN/effective-pom.xml"
"${CONSUMER[@]}" dependency:tree -DoutputFile="$RUN/dependency-tree.txt"
"${CONSUMER[@]}" dependency:build-classpath -Dmdep.outputFile="$RUN/classpath.txt"
python3 - "$RUN" <<'PY'
from pathlib import Path
import os
import sys
import xml.etree.ElementTree as ET

run = Path(sys.argv[1])
jars = [Path(value) for value in (run / 'classpath.txt').read_text().strip().split(os.pathsep)]
assert jars and all(jar.is_file() and jar.suffix == '.jar' for jar in jars)
boot = [jar for jar in jars if '/org/springframework/boot/' in jar.as_posix()]
assert boot and all(jar.parent.name == '4.1.0' for jar in boot), boot
for module in ('reactive-http-client-starter', 'reactive-http-client-test', 'reactive-http-client-otel'):
    expected = run / 'repository/io/github/huynhngochuyhoang' / module / '4.5.0-SNAPSHOT' / (module + '-4.5.0-SNAPSHOT.jar')
    assert expected in jars, expected
reports = list((run / 'boot41-consumer-overlay/target/surefire-reports').glob('TEST-*.xml'))
counts = {key: sum(int(ET.parse(report).getroot().get(key, 0)) for report in reports)
          for key in ('tests', 'failures', 'errors', 'skipped')}
assert counts == {'tests': 28, 'failures': 0, 'errors': 0, 'skipped': 0}, counts
print('Verified Boot 4.1.0, isolated reactor JARs and 28 passing consumer cases')
PY
```

The minimal no-Caffeine consumer remains the separate Boot 4.0 fixture rather
than being misrepresented as an upper-row run.

### Native

Native uses GraalVM/native-image 25.0.3, Java target 21 and Boot 4.0.0. Set
`JAVA_HOME`/`PATH` to that installed GraalVM. The successful retry installs the
clean reactor into a fresh isolated repository before compiling the fixture.
Use a sufficiently provisioned host and run the executable only after compilation
succeeds. Earlier 4 GiB and memory-constrained 6 GiB failures remain separate.

```bash
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
mvn -B -ntp -s .mvn/maven-central-settings.xml \
  -Dmaven.repo.local="$PWD/target/native-repository" \
  -DskipTests -Dmaven.javadoc.skip=true install
mvn -B -ntp -s .mvn/maven-central-settings.xml \
  -Dmaven.repo.local="$PWD/target/native-repository" \
  -f .github/native-smoke/pom.xml -Dreactive-http-client.version=4.5.0-SNAPSHOT \
  -Pnative '-DbuildArgs=-H:+SharedArenaSupport,-J-Xmx6g,--parallelism=2' clean native:compile
timeout 180 .github/native-smoke/target/reactive-http-client-native-smoke
sha256sum .github/native-smoke/target/reactive-http-client-native-smoke
```

Ordinary package/execution precedes `spring-boot:process-aot package` and
`java -Dspring.aot.enabled=true -jar ...`; the subsequent native clean build
regenerates AOT output again. Stage records preserve the full commands and
separate JVM test totals.

## Cost and Limitations

No steady-state method, allocation, synchronization or publisher composition
changed. New JMH discovery, timing/allocation measurements, memory profiling
and a published `4.4.0` benchmark rerun are not applicable to this construction-only
patch. No numerical speed, allocation or pod-memory improvement is claimed.
The ordinary tests use deterministic lifecycle witnesses; explicit collection
belongs only to the controlled JVM lanes, not a timing-based concurrency proof.

This verification does not add MVC, mesh, application-specific serialization,
unclassified builder, native OTel, custom collector or arbitrary dependency
support. It does not waive the deferred findings or select a release. Priorities
11 and 12 remain required.

## Evidence Integrity

The original partial bundle is retained unchanged under `target/release-evidence/v32/priority10/`:
clean source archive, stage commands and environments, fresh XML, resolved
dependencies, strict reports, Central remote markers, current artifact hashes,
toolchain hashes, the failed native build, audit results and `SHA256SUMS`. No
native binary is present in that original bundle. Earlier sealed
Priority 1-9 records remain unchanged. Final documentation verification and its
patch are distinct from clean-commit runtime evidence.

The completion bundle is `target/release-evidence/v32/priority10-native/`: fresh
clean-source archive, source/tree IDs, isolated reactor artifacts, native
commands/logs/XML, classpath, dependency tree, effective POM, toolchain hashes,
executable and companion libraries, binary hash, final documentation reports,
completion patch, audit and `SHA256SUMS`. Its provenance links the original
bundle's verified manifest SHA-256:
`db5f5544c250d5992af0e8a6fdae2af6f4edfbb0be72b2744afb1491b75a164b`.
The completion audit supplements rather than rewrites the original partial audit.
