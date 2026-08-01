#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PUBLISHED_VERSION="${1:-}"
[[ -n "$PUBLISHED_VERSION" ]] || {
  echo "Usage: $0 <published-version>" >&2
  exit 2
}

GROUP_PATH="io/github/huynhngochuyhoang"
MODULES=(reactive-http-client-starter reactive-http-client-test reactive-http-client-otel)
LOCAL_REPOSITORY="$ROOT_DIR/target/published-baseline-repositories/consumer-$PUBLISHED_VERSION"
EVIDENCE_DIR="$ROOT_DIR/target/release-evidence/published-consumer/published-$PUBLISHED_VERSION"
EFFECTIVE_POMS="$EVIDENCE_DIR/effective-poms"
REPORTS="$EVIDENCE_DIR/surefire-reports"
FIXTURE_POM="$ROOT_DIR/.github/boot4-consumer/pom.xml"
SETTINGS="$ROOT_DIR/.mvn/maven-central-settings.xml"

fail() {
  echo "Published consumer check failed: $*" >&2
  exit 1
}

[[ ! -e "$LOCAL_REPOSITORY" ]] || fail "fresh repository required; remove $LOCAL_REPOSITORY"
[[ ! -e "$EVIDENCE_DIR" ]] || fail "fresh evidence directory required; remove $EVIDENCE_DIR"
mkdir -p "$LOCAL_REPOSITORY" "$EFFECTIVE_POMS" "$REPORTS"
REPORT_START_MARKER="$EVIDENCE_DIR/report-start.marker"
touch "$REPORT_START_MARKER"
stage="setup"

copy_consumer_reports() {
  for report in "$ROOT_DIR/.github/boot4-consumer/target/surefire-reports/"*.xml; do
    [[ -f "$report" && "$report" -nt "$REPORT_START_MARKER" ]] \
      && cp "$report" "$REPORTS/"
  done
}

preserve_evidence() {
  local status=$?
  trap - EXIT
  set +e
  copy_consumer_reports
  working_tree=clean
  [[ -z "$(git -C "$ROOT_DIR" status --porcelain)" ]] || working_tree=dirty
  {
    echo "publishedVersion=$PUBLISHED_VERSION"
    echo "fixtureCommit=$(git -C "$ROOT_DIR" rev-parse HEAD)"
    echo "fixtureWorkingTree=$working_tree"
    echo "repository=$LOCAL_REPOSITORY"
    echo "settings=.mvn/maven-central-settings.xml"
    echo "fixture=.github/boot4-consumer/pom.xml"
    echo "source=Maven Central"
    echo "completedStage=$stage"
    echo "exitStatus=$status"
  } > "$EVIDENCE_DIR/provenance.properties"
  exit "$status"
}
trap preserve_evidence EXIT

MAVEN=(mvn -q -s "$SETTINGS" -Dmaven.repo.local="$LOCAL_REPOSITORY"
  -f "$FIXTURE_POM" -Dreactive-http-client.version="$PUBLISHED_VERSION")

"${MAVEN[@]}" help:effective-pom -Doutput="$EFFECTIVE_POMS/boot4-published-consumer.xml"
stage="effective-pom"
"${MAVEN[@]}" clean test
stage="consumer-tests"
copy_consumer_reports
"${MAVEN[@]}" dependency:tree -DoutputFile="$EVIDENCE_DIR/dependency-tree.txt"
stage="dependency-tree"
"${MAVEN[@]}" dependency:build-classpath -Dmdep.outputFile="$EVIDENCE_DIR/classpath.txt"
stage="classpath"

parent_dir="$LOCAL_REPOSITORY/$GROUP_PATH/reactive-http-client/$PUBLISHED_VERSION"
parent_pom="$parent_dir/reactive-http-client-$PUBLISHED_VERSION.pom"
[[ -f "$parent_pom" ]] || fail "missing published parent POM"
stage="parent-artifact"

for module in "${MODULES[@]}"; do
  module_dir="$LOCAL_REPOSITORY/$GROUP_PATH/$module/$PUBLISHED_VERSION"
  jar="$module_dir/$module-$PUBLISHED_VERSION.jar"
  pom="$module_dir/$module-$PUBLISHED_VERSION.pom"
  [[ -f "$jar" && -f "$pom" ]] || fail "missing published jar or POM for $module"
  mvn -q -s "$SETTINGS" -Dmaven.repo.local="$LOCAL_REPOSITORY" -f "$pom" \
    help:effective-pom -Doutput="$EFFECTIVE_POMS/$module.xml"
  stage="module-effective-pom-$module"
done

"$ROOT_DIR/scripts/verify-published-baseline-provenance.sh" \
  consumer "$PUBLISHED_VERSION" "$EVIDENCE_DIR/published-baseline-provenance" \
  reactive-http-client "${MODULES[@]}"
stage="published-provenance"

if grep -Eq "$ROOT_DIR/reactive-http-client-(starter|test|otel)/target/(test-)?classes" \
    "$EVIDENCE_DIR/classpath.txt" "$EVIDENCE_DIR/dependency-tree.txt"; then
  fail "published consumer resolved reactor output directories"
fi
stage="reactor-leakage-checked"

for module in "${MODULES[@]}"; do
  grep -q "$LOCAL_REPOSITORY/$GROUP_PATH/$module/$PUBLISHED_VERSION/$module-$PUBLISHED_VERSION.jar" \
    "$EVIDENCE_DIR/classpath.txt" || fail "$module jar is absent from the isolated consumer classpath"
  stage="artifact-classpath-$module"
done
stage="evidence-verified"

echo "Published Boot 4 consumer passed against Maven Central artifacts $PUBLISHED_VERSION."
