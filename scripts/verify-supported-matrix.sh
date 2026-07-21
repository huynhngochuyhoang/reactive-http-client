#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SETTINGS="$ROOT_DIR/.mvn/maven-central-settings.xml"
JAVA_VERSION="$(java -version 2>&1 | head -n1)"
JAVA_MAJOR="$(java -XshowSettings:properties -version 2>&1 \
  | awk -F= '/java.specification.version/ { gsub(/[[:space:]]/, "", $2); print $2; exit }')"

fail() {
  echo "Supported-matrix verification failed: $*" >&2
  exit 1
}

[[ "$JAVA_MAJOR" == "21" ]] \
  || fail "Java 21 is required for the supported minimum; found $JAVA_VERSION"
command -v javadoc >/dev/null \
  || fail "a complete JDK 21 with the javadoc tool is required"

PROJECT_VERSION="$(mvn -q -s "$SETTINGS" -DforceStdout help:evaluate -Dexpression=project.version)"
BASELINE_VERSION="$(mvn -q -s "$SETTINGS" -DforceStdout help:evaluate -Dexpression=api.compatibility.baseline.version)"
COMMIT="$(git -C "$ROOT_DIR" rev-parse HEAD)"
ROWS=(4.0.0 4.1.0)
MODULES=(reactive-http-client-starter reactive-http-client-test reactive-http-client-otel)
FINAL_EVIDENCE_ROOT="$ROOT_DIR/target/release-evidence/v22-priority11"
WORK_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/reactive-http-client-matrix.XXXXXX")"
EVIDENCE_ROOT="$WORK_ROOT/evidence"
REPOSITORY_ROOT="$WORK_ROOT/repositories"

[[ "$PROJECT_VERSION" == "3.3.0-SNAPSHOT" ]] \
  || fail "expected current development version 3.3.0-SNAPSHOT, found $PROJECT_VERSION"
[[ "$BASELINE_VERSION" == "3.2.0" ]] \
  || fail "expected published API baseline 3.2.0, found $BASELINE_VERSION"
[[ ! -e "$FINAL_EVIDENCE_ROOT" ]] \
  || fail "fresh evidence directory required: $FINAL_EVIDENCE_ROOT"

CURRENT_EVIDENCE=""
CURRENT_API_STAGING=""
CURRENT_ROW_MARKER=""
CONSUMER_STARTED=false

collect_current_evidence() {
  [[ -n "$CURRENT_EVIDENCE" && -d "$CURRENT_EVIDENCE" ]] || return
  mkdir -p "$CURRENT_EVIDENCE/surefire-reports"
  find "$ROOT_DIR" -path '*/target/surefire-reports/*.xml' \
    -not -path '*/reactive-http-client-benchmarks/*' \
    -not -path '*/.github/boot4-consumer/*' \
    -newer "$CURRENT_ROW_MARKER" \
    -exec cp {} "$CURRENT_EVIDENCE/surefire-reports/" \;
  if [[ "$CONSUMER_STARTED" == true ]]; then
    for report in "$ROOT_DIR/.github/boot4-consumer/target/surefire-reports/"*.xml; do
      [[ -f "$report" ]] || continue
      [[ "$report" -nt "$CURRENT_ROW_MARKER" ]] || continue
      cp "$report" "$CURRENT_EVIDENCE/surefire-reports/consumer-$(basename "$report")"
    done
  fi
  for module in "${MODULES[@]}"; do
    if [[ -d "$ROOT_DIR/$module/target/japicmp" ]]; then
      mkdir -p "$CURRENT_EVIDENCE/api-compatibility/japicmp/$module"
      find "$ROOT_DIR/$module/target/japicmp" -type f -newer "$CURRENT_ROW_MARKER" \
        -exec cp {} "$CURRENT_EVIDENCE/api-compatibility/japicmp/$module/" \;
    fi
  done
  if [[ -n "$CURRENT_API_STAGING" && -d "$CURRENT_API_STAGING" ]]; then
    mkdir -p "$CURRENT_EVIDENCE/api-compatibility/published-baseline"
    cp -R "$CURRENT_API_STAGING/." \
      "$CURRENT_EVIDENCE/api-compatibility/published-baseline/"
  fi
}

preserve_evidence() {
  local status=$?
  trap - EXIT
  collect_current_evidence
  mkdir -p "$FINAL_EVIDENCE_ROOT"
  if [[ -d "$EVIDENCE_ROOT" ]]; then
    cp -R "$EVIDENCE_ROOT/." "$FINAL_EVIDENCE_ROOT/"
  fi
  if [[ $status -eq 0 ]]; then
    rm -rf "$WORK_ROOT"
  else
    echo "Partial matrix evidence preserved under $FINAL_EVIDENCE_ROOT" >&2
  fi
  exit "$status"
}
trap preserve_evidence EXIT

for boot_version in "${ROWS[@]}"; do
  repository="$REPOSITORY_ROOT/boot-$boot_version"
  evidence="$EVIDENCE_ROOT/boot-$boot_version"
  [[ ! -e "$repository" ]] || fail "fresh repository required: $repository"
  [[ ! -e "$evidence" ]] || fail "fresh evidence directory required: $evidence"
  mkdir -p "$repository" "$evidence/effective-poms" "$evidence/surefire-reports"
  CURRENT_EVIDENCE="$evidence"
  CURRENT_API_STAGING=""
  CURRENT_ROW_MARKER="$evidence/row-start.marker"
  CONSUMER_STARTED=false
  touch "$CURRENT_ROW_MARKER"

  maven=(mvn -B -ntp -s "$SETTINGS" "-Dmaven.repo.local=$repository"
    "-Dspring-boot.version=$boot_version")

  cat > "$evidence/commands.txt" <<EOF
mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local=$repository -Dspring-boot.version=$boot_version clean install
mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local=$repository -Dspring-boot.version=$boot_version -f .github/boot4-consumer/pom.xml -Dreactive-http-client.version=$PROJECT_VERSION clean test
mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local=target/published-baseline-repositories/supported-matrix-api-boot-${boot_version//./-}-$BASELINE_VERSION -Dspring-boot.version=$boot_version -Papi-compatibility -DskipTests verify
EOF

  "${maven[@]}" -f "$ROOT_DIR/pom.xml" clean install
  collect_current_evidence

  require_test_case() {
    local test_case="$1"
    grep -R -Fq "name=\"$test_case\"" "$evidence/surefire-reports" \
      || fail "optional-integration contract $test_case did not run for Boot $boot_version"
  }
  optional_integration_tests=(
    starterContextLoadsWhenMicrometerMissing
    resilience4jBindersSkippedWhenRegistryBeansMissing
    diagnosticsEndpointSkippedWhenActuatorEndpointClassesMissing
    autoConfigurationBacksOffWithoutOpenTelemetryBean
    autoConfigurationBacksOffWithoutOpenTelemetryApi
  )
  for test_case in "${optional_integration_tests[@]}"; do
    require_test_case "$test_case"
  done
  printf '%s=passed\n' "${optional_integration_tests[@]}" \
    > "$evidence/optional-integration-contracts.properties"

  for module in "${MODULES[@]}"; do
    "${maven[@]}" -f "$ROOT_DIR/$module/pom.xml" dependency:tree \
      -DoutputFile="$evidence/dependency-tree-$module.txt"
    "${maven[@]}" -f "$ROOT_DIR/$module/pom.xml" help:effective-pom \
      -Doutput="$evidence/effective-poms/$module.xml"
  done

  CONSUMER_STARTED=true
  "${maven[@]}" -f "$ROOT_DIR/.github/boot4-consumer/pom.xml" \
    "-Dreactive-http-client.version=$PROJECT_VERSION" clean test
  for report in "$ROOT_DIR/.github/boot4-consumer/target/surefire-reports/"*.xml; do
    cp "$report" "$evidence/surefire-reports/consumer-$(basename "$report")"
  done
  "${maven[@]}" -f "$ROOT_DIR/.github/boot4-consumer/pom.xml" \
    "-Dreactive-http-client.version=$PROJECT_VERSION" dependency:tree \
    -DoutputFile="$evidence/dependency-tree-consumer.txt"
  "${maven[@]}" -f "$ROOT_DIR/.github/boot4-consumer/pom.xml" \
    "-Dreactive-http-client.version=$PROJECT_VERSION" help:effective-pom \
    -Doutput="$evidence/effective-poms/consumer.xml"

  tree="$evidence/dependency-tree-reactive-http-client-starter.txt"
  otel_tree="$evidence/dependency-tree-reactive-http-client-otel.txt"
  resolve_version() {
    local source="$1"
    local coordinate="$2"
    local version
    version="$(grep -m1 -E "(^|[[:space:]+\\|-])$coordinate:[^:]+:[^:]+:" "$source" \
      | sed -E 's/^[[:space:]+\\|-]*//' | cut -d: -f4)"
    [[ -n "$version" ]] || fail "could not resolve $coordinate for Boot $boot_version"
    printf '%s' "$version"
  }

  resolved_boot="$(resolve_version "$tree" 'org.springframework.boot:spring-boot')"
  [[ "$resolved_boot" == "$boot_version" ]] \
    || fail "requested Boot $boot_version but resolved $resolved_boot"
  {
    echo "springBoot=$resolved_boot"
    echo "springFramework=$(resolve_version "$tree" 'org.springframework:spring-core')"
    echo "springWebFlux=$(resolve_version "$tree" 'org.springframework:spring-webflux')"
    echo "reactorNetty=$(resolve_version "$tree" 'io.projectreactor.netty:reactor-netty-http')"
    echo "netty=$(resolve_version "$tree" 'io.netty:netty-codec-http')"
    echo "jackson=$(resolve_version "$tree" 'tools.jackson.core:jackson-databind')"
    echo "micrometer=$(resolve_version "$tree" 'io.micrometer:micrometer-core')"
    echo "openTelemetry=$(resolve_version "$otel_tree" 'io.opentelemetry:opentelemetry-api')"
    echo "resilience4j=$(resolve_version "$tree" 'io.github.resilience4j:resilience4j-retry')"
    echo "junit=$(resolve_version "$tree" 'org.junit.jupiter:junit-jupiter-api')"
    echo "mockito=$(resolve_version "$tree" 'org.mockito:mockito-core')"
  } > "$evidence/resolved-versions.properties"

  {
    echo "projectVersion=$PROJECT_VERSION"
    echo "springBootVersion=$boot_version"
    echo "javaVersion=$JAVA_VERSION"
    echo "sourceCommit=$COMMIT"
    [[ -z "$(git -C "$ROOT_DIR" status --porcelain)" ]] \
      && echo "sourceState=clean" || echo "sourceState=dirty"
    echo "repositoryMode=fresh-temporary"
    echo "source=Maven Central"
  } > "$evidence/provenance.properties"

  boot_slug="${boot_version//./-}"
  api_lane="supported-matrix-api-boot-$boot_slug"
  api_repository="$ROOT_DIR/target/published-baseline-repositories/$api_lane-$BASELINE_VERSION"
  CURRENT_API_STAGING="$ROOT_DIR/target/release-evidence/$api_lane-$BASELINE_VERSION"
  [[ ! -e "$api_repository" ]] || fail "fresh API repository required: $api_repository"
  [[ ! -e "$CURRENT_API_STAGING" ]] \
    || fail "fresh API evidence directory required: $CURRENT_API_STAGING"
  mvn -B -ntp -s "$SETTINGS" "-Dmaven.repo.local=$api_repository" \
    "-Dspring-boot.version=$boot_version" -Papi-compatibility -DskipTests verify
  "$ROOT_DIR/scripts/verify-published-baseline-provenance.sh" \
    "$api_lane" "$BASELINE_VERSION" "$CURRENT_API_STAGING" \
    reactive-http-client-starter reactive-http-client-test reactive-http-client-otel
  collect_current_evidence
done

echo "Supported matrix passed for Spring Boot ${ROWS[*]} with API baseline $BASELINE_VERSION."
