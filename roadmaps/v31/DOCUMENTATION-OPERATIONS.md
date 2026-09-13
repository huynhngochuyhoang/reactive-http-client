# V31 Documentation and Operations Review

Date: 2026-09-13. Scope: Priority 8 only. Based on reachable commit
`83bb48ef14845e4986e1a53761452167924b0307` plus this documentation/test change
(dirty-tree implementation evidence, not an immutable release cut).

## Delivered Guidance

- [Context guide](../../docs/09-correlation-id.md): standalone published `4.3.0`
  fixed-name workaround and candidate `4.4.0-SNAPSHOT` named-reader example.
  Both reject absence, multiplicity, empty/redacted/oversized data before caller
  parsing. The example size limit is application-owned, not a starter default;
  schema/depth limits and ingress trust remain application responsibilities.
- [Exchange logging](../../docs/13-exchange-logging.md),
  [mock helpers](../../docs/14-test-helpers.md) and
  [production checklist](../../docs/16-production-checklist.md) use the same
  filtering, exact-map/case-insensitive-reader, explicit handoff and retention
  contract. Manual writes are not implicitly sanitized. Queue examples check
  rejected offers; independent subscribers need isolated restoration targets.
- [Operations triage](../../docs/30-operations-troubleshooting.md#inbound-header-and-context-triage)
  distinguishes case mismatch, omitted/redacted fields, empty/multiple values,
  filter order/registration, malformed context and independent subscriptions.
  Scheduler changes alone do not imply context loss. WebFlux capture is not a
  Spring MVC, MDC, automatic forwarding or queue propagation integration.
- [Support capture](../../docs/26-support-bundles.md#inbound-context-capture)
  and its [fixture](../../docs/fixtures/support-bundle-inbound-context.json)
  compare one request/field at capture and read boundaries. Closed field sets,
  fixed aliases/enums, bounded versions/counts and UTC window invariants prohibit
  arbitrary names, values, identities, targets and payloads. Unknown is explicit;
  counts cannot prove value equality, trust, or a rewriting proxy.

## Privacy and Scope Decision

No additional meter or public diagnostics field is needed: temporary local
capture/read presence and counts distinguish these failures. The JSON fixture
is an application support format, not an Actuator schema extension or an
arbitrary-response sanitizer. Alias mappings must not enter the bundle; retain
no exchanges, raw maps/values, hashes or arbitrary error messages. A literal
redaction marker can also be sender-supplied and is never an identity.

The lookup examples are extracted from Markdown, compiled for Java 21, and
executed by `InboundContextDocumentationContractTest`. Consumer and mock cases
exercise the same capture/restore, absence/multiplicity, immutable values,
redaction, non-forwarding and replacement-bean boundaries. Application header-DTO
parsing and its native hints are deliberately not supplied by the starter.

HTTP/1.1, cleartext HTTP/2 and TLS HTTP/2 checks are loopback evidence only.
Optional manual mesh inspection is labeled as deployment-specific work. No
Kubernetes, Istio/Envoy, new native, performance or release claim is made.
No runtime/API/configuration/version changes or V1-V30 archive edits were made.

## Verification

Toolchain: Maven 3.9.9, GraalVM JDK 25.0.3, Java target 21, Linux amd64;
Boot 4.0.0 / Spring 7.0.1 / Reactor 3.8.0. Settings:
`.mvn/maven-central-settings.xml`. Logs, selected XML reports, source checksums
and completion provenance: `target/release-evidence/v31/priority8/`.

| Check | Result |
|---|---|
| Generated docs, Markdown links, archive/version/support guards | 49 passed |
| New Markdown examples and recursive support-fixture guards | 29 passed |
| Named readers, snapshots, filter, handoff/ownership, feature composition | 97 passed |
| Wire protocols and filter registration | 30 passed |
| Mock inbound-context parity | 5 passed |
| Assembled Boot 4 consumer, default/replacement beans | 2 passed |
| Cache-disabled assembled consumer, default/replacement beans | 2 passed |
| Existing support fixture and copyable capture sanitizers | 9 Python tests passed |

All final listed tests had zero failures, errors or skips. The first compile
failed in the new test's generic `Map.of` inference; an explicit type witness
fixed it. The next 78-case docs run had one wording-guard failure because the
triage row did not spell the literal redaction marker; the row was corrected.
That failed log/XML is retained separately. The successful 175-case run includes
the corrected 78 documentation cases. The completed checklist/review is checked
again by the final documentation rerun; prior failures are not final evidence.

Reproduction (run at repository root; Maven logs use separate `-l` files):

```bash
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter -Dtest=InboundContextDocumentationContractTest,DocumentationReleaseArtifactTest,RequestContextHeaderAccessTest,RequestContextSnapshotTest,InboundHeadersWebFilterTest,ExplicitAsyncHandoffContractTest,AsyncHandoffOwnershipContractTest,InboundContextCompositionContractTest test
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter,reactive-http-client-test -am -Dtest=InboundHeadersWireContractTest,InboundHeadersAutoConfigurationTest,MockInboundContextParityTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B -ntp -s .mvn/maven-central-settings.xml -DskipTests -Dmaven.javadoc.skip=true install
mvn -B -ntp -s .mvn/maven-central-settings.xml -f .github/boot4-consumer/pom.xml -Dreactive-http-client.version=4.4.0-SNAPSHOT -Dconsumer.v31.parity=true -Dtest=Boot4InboundContextConsumerTest test
mvn -B -ntp -s .mvn/maven-central-settings.xml -f .github/boot4-cache-disabled-consumer/pom.xml -Dreactive-http-client.version=4.4.0-SNAPSHOT -Dconsumer.v31.parity=true -Dtest=Boot4InboundContextConsumerTest test
python3 scripts/verify-cache-work-support.py
git diff --check
```

Consumer checks use freshly installed current reactor artifacts in the existing
local Maven repository, outside reactor test-classpath wiring; this is not a
fresh Central published-consumer verification. Full release compatibility,
benchmark disposition and release gates remain in Priorities 9 and 10. Priority
7 native evidence remains at its recorded fixture revision, not reattributed to
this documentation-only change.

## Review Follow-Up

2026-09-13, based on `198c2e5e842875cb85cbaa419179324ad86304e3` plus uncommitted
documentation/test fixes. Only the singleton `inboundHeader` rejects duplicate
values; `inboundHeaderValues` returns duplicates and case aliases unchanged.
The support validator now cross-checks known policy decisions at capture:
omitted names have zero counts, and each captured denied name has one marker
and no empty values. Unknown decisions and later read-boundary changes remain
representable; a not-denied field may contain a sender-supplied literal marker.

The regression-first run recorded three expected failures (wording, omitted
capture, denied capture). After the fixes, 127 cases passed: 32 documentation
contract, 49 release-artifact, 39 named-reader and 7 inbound-filter cases, with
zero failures/errors/skips. The earlier totals above remain their original-run
evidence, not counts for this revision. Logs are in
`target/release-evidence/v31/priority8/policy-review/` (`red.log`, `red.xml`,
`green.log`, and final `completion.log`). No runtime or fixture data changed.

```bash
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter -Dtest=InboundContextDocumentationContractTest,DocumentationReleaseArtifactTest,RequestContextHeaderAccessTest,InboundHeadersWebFilterTest test
git diff --check
```
