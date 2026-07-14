#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_VERSION="$(mvn -q -f "$ROOT_DIR/pom.xml" -DforceStdout help:evaluate -Dexpression=project.version)"
BASELINE_VERSION="$(mvn -q -f "$ROOT_DIR/pom.xml" -DforceStdout help:evaluate -Dexpression=api.compatibility.baseline.version)"
WORK_DIR="$ROOT_DIR/target/major-api-delta"

[[ "$PROJECT_VERSION" == "3.0.0" ]] || {
  echo "Major API delta guard is frozen for candidate 3.0.0, found $PROJECT_VERSION" >&2
  exit 1
}
[[ "$BASELINE_VERSION" == "2.14.1" ]] || {
  echo "Major API delta guard requires published baseline 2.14.1, found $BASELINE_VERSION" >&2
  exit 1
}

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"

MODULES=(reactive-http-client-starter reactive-http-client-test reactive-http-client-otel)
ACTUAL="$WORK_DIR/actual-reviewed-lines.txt"
EXPECTED="$WORK_DIR/expected-reviewed-lines.txt"

for module in "${MODULES[@]}"; do
  report="$ROOT_DIR/$module/target/japicmp/compare-public-api.diff"
  [[ -f "$report" ]] || {
    echo "Missing $report; run the report-only api-compatibility build first" >&2
    exit 1
  }
  sed -n -E "s;^[[:space:]]*((---|\\*\\*\\*)!.*)$;$module|\\1;p" "$report" >> "$ACTUAL"
done

cat > "$EXPECTED" <<'EOF'
reactive-http-client-starter|---! REMOVED CLASS: PUBLIC(-) FINAL(-) io.github.huynhngochuyhoang.httpstarter.core.Jackson2ReactiveHttpClientJsonCodec  (not serializable)
reactive-http-client-starter|---! REMOVED INTERFACE: io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientJsonCodec
reactive-http-client-starter|---! REMOVED SUPERCLASS: java.lang.Object
reactive-http-client-starter|---! REMOVED CONSTRUCTOR: PUBLIC(-) Jackson2ReactiveHttpClientJsonCodec(com.fasterxml.jackson.databind.ObjectMapper)
reactive-http-client-starter|***! MODIFIED CLASS: PUBLIC io.github.huynhngochuyhoang.httpstarter.core.ProblemDetailErrorResponseMapper  (not serializable)
reactive-http-client-starter|---! REMOVED CONSTRUCTOR: PUBLIC(-) ProblemDetailErrorResponseMapper(com.fasterxml.jackson.databind.ObjectMapper)
reactive-http-client-starter|---! REMOVED CLASS: PUBLIC(-) io.github.huynhngochuyhoang.httpstarter.observability.HttpClientHealthIndicator  (not serializable)
reactive-http-client-starter|---! REMOVED SUPERCLASS: java.lang.Object
reactive-http-client-starter|---! REMOVED CONSTRUCTOR: PUBLIC(-) HttpClientHealthIndicator(io.micrometer.core.instrument.MeterRegistry, io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties$ObservabilityConfig)
reactive-http-client-starter|---! REMOVED METHOD: PUBLIC(-) org.springframework.boot.actuate.health.Health health()
reactive-http-client-test|***! MODIFIED CLASS: PUBLIC STATIC FINAL io.github.huynhngochuyhoang.httpstarter.test.MockReactiveHttpClient$Builder  (not serializable)
reactive-http-client-test|---! REMOVED METHOD: PUBLIC(-) io.github.huynhngochuyhoang.httpstarter.test.MockReactiveHttpClient$Builder<T> objectMapper(com.fasterxml.jackson.databind.ObjectMapper)
EOF

if ! diff -u "$EXPECTED" "$ACTUAL"; then
  echo "Unreviewed cross-major API change detected; classify it before changing the 3.0.0 surface" >&2
  exit 1
fi

DEFAULT_LOCAL_REPOSITORY="$(mvn -q -f "$ROOT_DIR/pom.xml" -DforceStdout help:evaluate -Dexpression=settings.localRepository)"
BASELINE_REPOSITORY="${API_COMPATIBILITY_BASELINE_REPOSITORY:-$DEFAULT_LOCAL_REPOSITORY}"
for module in "${MODULES[@]}"; do
  old_module_dir="$BASELINE_REPOSITORY/io/github/huynhngochuyhoang/$module/$BASELINE_VERSION"
  old_module_jar="$old_module_dir/$module-$BASELINE_VERSION.jar"
  old_module_marker="$old_module_dir/_remote.repositories"
  [[ -f "$old_module_jar" && -f "$old_module_marker" ]] || {
    echo "Published baseline artifact or remote marker is missing for $module:$BASELINE_VERSION" >&2
    exit 1
  }
  grep -Eq "^$module-$BASELINE_VERSION\\.jar>[^=]+=$" "$old_module_marker" || {
    echo "Baseline $module was installed locally rather than resolved from a release repository" >&2
    exit 1
  }
done

OLD_STARTER_DIR="$BASELINE_REPOSITORY/io/github/huynhngochuyhoang/reactive-http-client-starter/$BASELINE_VERSION"
OLD_STARTER="$OLD_STARTER_DIR/reactive-http-client-starter-$BASELINE_VERSION.jar"
NEW_STARTER="$ROOT_DIR/reactive-http-client-starter/target/reactive-http-client-starter-$PROJECT_VERSION.jar"
OLD_HEALTH="io/github/huynhngochuyhoang/httpstarter/observability/HttpClientHealthIndicator.class"
NEW_HEALTH="io/github/huynhngochuyhoang/httpstarter/observability/Boot4HttpClientHealthIndicator.class"

[[ -f "$OLD_STARTER" ]] || {
  echo "Published baseline jar is missing: $OLD_STARTER" >&2
  exit 1
}
[[ -f "$NEW_STARTER" ]] || {
  echo "Candidate starter jar is missing: $NEW_STARTER" >&2
  exit 1
}
jar tf "$OLD_STARTER" | grep -Fxq "$OLD_HEALTH" || {
  echo "Published baseline does not contain the reviewed Boot 3 health type" >&2
  exit 1
}
if jar tf "$NEW_STARTER" | grep -Fxq "$OLD_HEALTH"; then
  echo "Candidate unexpectedly retains the Boot 3 health type" >&2
  exit 1
fi
jar tf "$NEW_STARTER" | grep -Fxq "$NEW_HEALTH" || {
  echo "Candidate does not contain the reviewed Boot 4 health replacement" >&2
  exit 1
}

echo "Reviewed major API delta passed for published $BASELINE_VERSION -> candidate $PROJECT_VERSION."
