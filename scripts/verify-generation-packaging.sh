#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_VERSION="${1:-$(mvn -q -f "$ROOT_DIR/pom.xml" -DforceStdout help:evaluate -Dexpression=project.version)}"
WORK_DIR="$ROOT_DIR/target/generation-packaging"

MODULES=(reactive-http-client-starter reactive-http-client-test reactive-http-client-otel)

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"

fail() {
  echo "Generation packaging check failed: $*" >&2
  exit 1
}

assert_entry_count() {
  local jar_file="$1"
  local entry="$2"
  local expected="$3"
  local count
  count="$(jar tf "$jar_file" | awk -v entry="$entry" '$0 == entry { count++ } END { print count + 0 }')"
  [[ "$count" == "$expected" ]] || fail "$jar_file contains $entry $count times; expected $expected"
}

for module in "${MODULES[@]}"; do
  if find "$ROOT_DIR/$module/src" -type d \( -name boot3 -o -name boot4 \) | grep -q .; then
    fail "$module contains an obsolete generation-specific source root"
  fi
  if grep -Rql 'org\.springframework\.boot\.web\.reactive\.function\.client' \
      "$ROOT_DIR/$module/src/main" "$ROOT_DIR/$module/src/test"; then
    fail "$module main or test sources reference the Spring Boot 3 WebClient package"
  fi

  binary_jar="$ROOT_DIR/$module/target/$module-$PROJECT_VERSION.jar"
  sources_jar="$ROOT_DIR/$module/target/$module-$PROJECT_VERSION-sources.jar"
  javadoc_jar="$ROOT_DIR/$module/target/$module-$PROJECT_VERSION-javadoc.jar"

  for artifact in "$binary_jar" "$sources_jar" "$javadoc_jar"; do
    [[ -f "$artifact" ]] || fail "missing artifact $artifact; run mvn clean verify before this guard"

    entries_file="$WORK_DIR/$(basename "$artifact").entries"
    jar tf "$artifact" > "$entries_file"
    duplicates="$(sort "$entries_file" | uniq -d)"
    [[ -z "$duplicates" ]] || fail "$artifact contains duplicate entries: $duplicates"

    if grep -Eqi '(^|/)(boot3|src/boot[34])(/|$)|Boot3[^/]*\.(class|java|html)$' "$entries_file"; then
      fail "$artifact contains a stale generation-specific entry"
    fi
  done

  expected_sources="$WORK_DIR/$module.expected-sources"
  packaged_sources="$WORK_DIR/$module.packaged-sources"
  find "$ROOT_DIR/$module/src/main/java" -type f -name '*.java' \
    -printf '%P\n' | sort > "$expected_sources"
  jar tf "$sources_jar" | grep '\.java$' | sort > "$packaged_sources"
  if ! diff -u "$expected_sources" "$packaged_sources" > "$WORK_DIR/$module.sources.diff"; then
    fail "$sources_jar does not match $module/src/main/java; see $WORK_DIR/$module.sources.diff"
  fi

  compiler_classes="$ROOT_DIR/$module/target/maven-status/maven-compiler-plugin/compile/default-compile/createdFiles.lst"
  [[ -f "$compiler_classes" ]] || fail "missing current compiler output list $compiler_classes"
  expected_classes="$WORK_DIR/$module.expected-classes"
  packaged_classes="$WORK_DIR/$module.packaged-classes"
  grep '\.class$' "$compiler_classes" | sort > "$expected_classes"
  jar tf "$binary_jar" | grep '\.class$' | sort > "$packaged_classes"
  if ! diff -u "$expected_classes" "$packaged_classes" > "$WORK_DIR/$module.classes.diff"; then
    fail "$binary_jar contains classes outside the current compile output; see $WORK_DIR/$module.classes.diff"
  fi

  assert_entry_count "$binary_jar" \
    "io/github/huynhngochuyhoang/httpstarter/observability/HttpClientHealthIndicator.class" 0

  resources_dir="$ROOT_DIR/$module/src/main/resources"
  if [[ -d "$resources_dir" ]]; then
    while IFS= read -r resource; do
      assert_entry_count "$binary_jar" "$resource" 1
      assert_entry_count "$sources_jar" "$resource" 1
    done < <(find "$resources_dir" -type f -printf '%P\n' | sort)
  fi

  assert_entry_count "$javadoc_jar" "index.html" 1
  assert_entry_count "$binary_jar" "META-INF/MANIFEST.MF" 1
done

STARTER_JAR="$ROOT_DIR/reactive-http-client-starter/target/reactive-http-client-starter-$PROJECT_VERSION.jar"
OTEL_JAR="$ROOT_DIR/reactive-http-client-otel/target/reactive-http-client-otel-$PROJECT_VERSION.jar"

assert_entry_count "$STARTER_JAR" "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports" 1
assert_entry_count "$STARTER_JAR" "META-INF/spring-configuration-metadata.json" 1
assert_entry_count "$STARTER_JAR" "META-INF/additional-spring-configuration-metadata.json" 1
assert_entry_count "$STARTER_JAR" "io/github/huynhngochuyhoang/httpstarter/config/ReactiveHttpClientRuntimeHints.class" 1
assert_entry_count "$STARTER_JAR" "io/github/huynhngochuyhoang/httpstarter/config/Boot4WebClientCustomizers.class" 1

assert_entry_count "$OTEL_JAR" "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports" 1
assert_entry_count "$OTEL_JAR" "META-INF/additional-spring-configuration-metadata.json" 1

echo "Generation packaging checks passed for binary, source, and Javadoc artifacts at version $PROJECT_VERSION."
