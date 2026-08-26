#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

normalize_report() {
  local module="$1"
  local report="$2"
  sed -n -E "s;^[[:space:]]*((---|\\+\\+\\+|\\*\\*\\*)![[:space:]].*)$;$module|\\1;p" \
    "$report"
}

if [[ "${1:-}" == "--normalize-report" ]]; then
  [[ "$#" == 3 ]] || {
    echo "Usage: $0 --normalize-report MODULE REPORT" >&2
    exit 2
  }
  normalize_report "$2" "$3"
  exit 0
fi

MAVEN_SETTINGS="${API_COMPATIBILITY_MAVEN_SETTINGS:-$ROOT_DIR/.mvn/maven-central-settings.xml}"
[[ "$MAVEN_SETTINGS" = /* ]] || MAVEN_SETTINGS="$ROOT_DIR/$MAVEN_SETTINGS"
MAVEN=(mvn -q -s "$MAVEN_SETTINGS" -f "$ROOT_DIR/pom.xml")
if [[ -n "${API_COMPATIBILITY_BASELINE_REPOSITORY:-}" ]]; then
  MAVEN+=("-Dmaven.repo.local=$API_COMPATIBILITY_BASELINE_REPOSITORY")
fi

PROJECT_VERSION="$("${MAVEN[@]}" -DforceStdout help:evaluate -Dexpression=project.version)"
BASELINE_VERSION="$("${MAVEN[@]}" -DforceStdout help:evaluate -Dexpression=api.compatibility.baseline.version)"
WORK_DIR="$ROOT_DIR/target/v27-major-api-delta"
REVIEWED_DELTA="$ROOT_DIR/config/api-delta-3.6.0-to-4.0.0.txt"
MODULES=(reactive-http-client-starter reactive-http-client-test reactive-http-client-otel)

[[ "$PROJECT_VERSION" == "4.0.0-SNAPSHOT" || "$PROJECT_VERSION" == "4.0.0" ]] || {
  echo "V27 API delta guard requires candidate 4.0.0[-SNAPSHOT], found $PROJECT_VERSION" >&2
  exit 1
}
[[ "$BASELINE_VERSION" == "3.6.0" ]] || {
  echo "V27 API delta guard requires published baseline 3.6.0, found $BASELINE_VERSION" >&2
  exit 1
}
[[ -f "$REVIEWED_DELTA" ]] || {
  echo "Missing reviewed V27 API delta: $REVIEWED_DELTA" >&2
  exit 1
}

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"
ACTUAL="$WORK_DIR/actual-incompatible-lines.txt"
EXPECTED="$WORK_DIR/reviewed-incompatible-lines.txt"
: > "$ACTUAL"

for module in "${MODULES[@]}"; do
  report="$ROOT_DIR/$module/target/japicmp/compare-public-api.diff"
  [[ -f "$report" ]] || {
    echo "Missing $report; run the V27 report-only api-compatibility build first" >&2
    exit 1
  }
  normalize_report "$module" "$report" >> "$ACTUAL"
done

sed -E '/^[[:space:]]*(#|$)/d' "$REVIEWED_DELTA" > "$EXPECTED"
if ! diff -u "$EXPECTED" "$ACTUAL"; then
  echo "Unreviewed V27 cross-major API change detected; classify it before changing the 4.0.0 surface" >&2
  exit 1
fi

BASELINE_REPOSITORY="${API_COMPATIBILITY_BASELINE_REPOSITORY:-$("${MAVEN[@]}" -DforceStdout help:evaluate -Dexpression=settings.localRepository)}"
for module in "${MODULES[@]}"; do
  module_dir="$BASELINE_REPOSITORY/io/github/huynhngochuyhoang/$module/$BASELINE_VERSION"
  module_jar="$module_dir/$module-$BASELINE_VERSION.jar"
  module_pom="$module_dir/$module-$BASELINE_VERSION.pom"
  marker="$module_dir/_remote.repositories"
  [[ -f "$module_jar" && -f "$module_pom" && -f "$marker" ]] || {
    echo "Published baseline POM, JAR, or remote marker is missing for $module:$BASELINE_VERSION" >&2
    exit 1
  }
  grep -Eq "^$module-$BASELINE_VERSION\\.jar>[^=]+=$" "$marker" || {
    echo "Baseline $module JAR was not resolved from a release repository" >&2
    exit 1
  }
  grep -Eq "^$module-$BASELINE_VERSION\\.pom>[^=]+=$" "$marker" || {
    echo "Baseline $module POM was not resolved from a release repository" >&2
    exit 1
  }
done

echo "Reviewed V27 API delta passed for published $BASELINE_VERSION -> candidate $PROJECT_VERSION."
