#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SETTINGS="$ROOT_DIR/.mvn/maven-central-settings.xml"
PROJECT_VERSION="$(mvn -q -s "$SETTINGS" -DforceStdout help:evaluate -Dexpression=project.version)"
GROUP_PATH="io/github/huynhngochuyhoang"
MODULES=(reactive-http-client-starter reactive-http-client-test reactive-http-client-otel)
LOCAL_REPOSITORY="$ROOT_DIR/target/current-reactor-repositories/consumer-$PROJECT_VERSION"
EVIDENCE_DIR="$ROOT_DIR/target/release-evidence/current-consumer/current-$PROJECT_VERSION"
MOCK_REPORTS="$EVIDENCE_DIR/mock-surefire-reports"
CONSUMER_REPORTS="$EVIDENCE_DIR/consumer-surefire-reports"
FIXTURE_POM="$ROOT_DIR/.github/boot4-consumer/pom.xml"

fail() {
  echo "Current consumer check failed: $*" >&2
  exit 1
}

[[ ! -e "$LOCAL_REPOSITORY" ]] || fail "fresh repository required; remove $LOCAL_REPOSITORY"
[[ ! -e "$EVIDENCE_DIR" ]] || fail "fresh evidence directory required; remove $EVIDENCE_DIR"
mkdir -p "$LOCAL_REPOSITORY" "$EVIDENCE_DIR/effective-poms" "$MOCK_REPORTS" "$CONSUMER_REPORTS"
REPORT_START_MARKER="$EVIDENCE_DIR/report-start.marker"
touch "$REPORT_START_MARKER"
stage="setup"

copy_mock_reports() {
  for report in "$ROOT_DIR/reactive-http-client-test/target/surefire-reports/"*.xml; do
    [[ -f "$report" && "$report" -nt "$REPORT_START_MARKER" ]] \
      && cp "$report" "$MOCK_REPORTS/"
  done
}

copy_consumer_reports() {
  for report in "$ROOT_DIR/.github/boot4-consumer/target/surefire-reports/"*.xml; do
    [[ -f "$report" && "$report" -nt "$REPORT_START_MARKER" ]] \
      && cp "$report" "$CONSUMER_REPORTS/"
  done
}

preserve_reports() {
  local status=$?
  trap - EXIT
  set +e
  copy_mock_reports
  copy_consumer_reports
  working_tree=clean
  [[ -z "$(git -C "$ROOT_DIR" status --porcelain)" ]] || working_tree=dirty
  {
    echo "projectVersion=$PROJECT_VERSION"
    echo "commit=$(git -C "$ROOT_DIR" rev-parse HEAD)"
    echo "workingTree=$working_tree"
    echo "repository=$LOCAL_REPOSITORY"
    echo "settings=.mvn/maven-central-settings.xml"
    echo "fixture=.github/boot4-consumer/pom.xml"
    echo "source=current reactor installed into a fresh target-local repository"
    echo "completedStage=$stage"
    echo "exitStatus=$status"
  } > "$EVIDENCE_DIR/provenance.properties"
  exit "$status"
}
trap preserve_reports EXIT

MAVEN=(mvn -B -ntp -s "$SETTINGS" -Dmaven.repo.local="$LOCAL_REPOSITORY")

"${MAVEN[@]}" -f "$ROOT_DIR/pom.xml" -pl reactive-http-client-starter,reactive-http-client-test,reactive-http-client-otel clean
stage="reactor-clean"
"${MAVEN[@]}" -f "$ROOT_DIR/pom.xml" -DskipTests -Dmaven.javadoc.skip=true install
stage="reactor-install"
"${MAVEN[@]}" -f "$ROOT_DIR/reactive-http-client-test/pom.xml" \
  -Dtest=MockReactiveHttpClientTest,Boot4MockReactiveHttpClientTest clean test
stage="mock-tests"
copy_mock_reports

"${MAVEN[@]}" -f "$FIXTURE_POM" -Dreactive-http-client.version="$PROJECT_VERSION" \
  -Dconsumer.v26.observability=true -Dconsumer.v27.parity=true -Dconsumer.v28.parity=true clean test
stage="consumer-tests"
copy_consumer_reports
"${MAVEN[@]}" -f "$FIXTURE_POM" -Dreactive-http-client.version="$PROJECT_VERSION" \
  -Dconsumer.v27.parity=true -Dconsumer.v28.parity=true \
  help:effective-pom -Doutput="$EVIDENCE_DIR/effective-poms/boot4-current-consumer.xml"
stage="consumer-effective-pom"
"${MAVEN[@]}" -f "$FIXTURE_POM" -Dreactive-http-client.version="$PROJECT_VERSION" \
  -Dconsumer.v27.parity=true -Dconsumer.v28.parity=true \
  dependency:tree -DoutputFile="$EVIDENCE_DIR/dependency-tree.txt"
stage="dependency-tree"
"${MAVEN[@]}" -f "$FIXTURE_POM" -Dreactive-http-client.version="$PROJECT_VERSION" \
  -Dconsumer.v27.parity=true -Dconsumer.v28.parity=true \
  dependency:build-classpath -Dmdep.outputFile="$EVIDENCE_DIR/classpath.txt"
stage="classpath"

if grep -Eq "$ROOT_DIR/reactive-http-client-(starter|test|otel)/target/(test-)?classes" \
    "$EVIDENCE_DIR/classpath.txt" "$EVIDENCE_DIR/dependency-tree.txt"; then
  fail "assembled consumer resolved reactor output directories"
fi
grep -q "/com/github/ben-manes/caffeine/caffeine/" "$EVIDENCE_DIR/classpath.txt" \
  || fail "cache-enabled mock consumer did not receive transitive Caffeine storage"
stage="reactor-leakage-checked"

CHECKSUMS="$EVIDENCE_DIR/project-artifact-sha256.txt"
parent_pom="$LOCAL_REPOSITORY/$GROUP_PATH/reactive-http-client/$PROJECT_VERSION/reactive-http-client-$PROJECT_VERSION.pom"
[[ -f "$parent_pom" ]] || fail "missing installed reactor parent POM"
sha256sum "$parent_pom" >> "$CHECKSUMS"
stage="parent-artifact"
for module in "${MODULES[@]}"; do
  module_dir="$LOCAL_REPOSITORY/$GROUP_PATH/$module/$PROJECT_VERSION"
  jar="$module_dir/$module-$PROJECT_VERSION.jar"
  pom="$module_dir/$module-$PROJECT_VERSION.pom"
  [[ -f "$jar" && -f "$pom" ]] || fail "missing installed reactor jar or POM for $module"
  grep -q "$jar" "$EVIDENCE_DIR/classpath.txt" \
    || fail "$module jar is absent from the isolated consumer classpath"
  sha256sum "$pom" "$jar" >> "$CHECKSUMS"
  stage="artifact-$module"
done
stage="evidence-verified"

echo "Current reactor mock and Boot 4 consumer parity passed for $PROJECT_VERSION."
