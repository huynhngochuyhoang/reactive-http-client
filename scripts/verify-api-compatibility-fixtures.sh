#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="$ROOT_DIR/target/api-compatibility-fixtures"
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"

compile_fixture() {
  local fixture="$1"
  local classes="$WORK_DIR/$fixture-classes"
  mkdir -p "$classes"
  javac -d "$classes" "$ROOT_DIR/.github/api-compatibility-fixtures/$fixture/compatibility/fixture/PublicApi.java"
  jar --create --file "$WORK_DIR/$fixture.jar" -C "$classes" .
}

compile_fixture old
compile_fixture additive
compile_fixture breaking
compile_fixture breaking-nested
compile_fixture breaking-enum
compile_fixture source-breaking

mvn -q -s "$ROOT_DIR/.mvn/maven-central-settings.xml" -f "$ROOT_DIR/.github/api-compatibility-fixtures/pom.xml" \
  -Dold.jar="$WORK_DIR/old.jar" \
  -Dnew.jar="$WORK_DIR/additive.jar" \
  verify

if mvn -q -s "$ROOT_DIR/.mvn/maven-central-settings.xml" -f "$ROOT_DIR/.github/api-compatibility-fixtures/pom.xml" \
    -Dold.jar="$WORK_DIR/old.jar" \
    -Dnew.jar="$WORK_DIR/breaking.jar" \
    verify \
    > "$WORK_DIR/breaking.log" 2>&1; then
  echo "Expected constructor-removal fixture to fail binary compatibility check" >&2
  exit 1
fi

if mvn -q -s "$ROOT_DIR/.mvn/maven-central-settings.xml" -f "$ROOT_DIR/.github/api-compatibility-fixtures/pom.xml" \
    -Dold.jar="$WORK_DIR/old.jar" \
    -Dnew.jar="$WORK_DIR/breaking-nested.jar" \
    verify \
    > "$WORK_DIR/breaking-nested.log" 2>&1; then
  echo "Expected nested public method removal fixture to fail binary compatibility check" >&2
  exit 1
fi

if mvn -q -s "$ROOT_DIR/.mvn/maven-central-settings.xml" -f "$ROOT_DIR/.github/api-compatibility-fixtures/pom.xml" \
    -Dold.jar="$WORK_DIR/old.jar" \
    -Dnew.jar="$WORK_DIR/breaking-enum.jar" \
    verify \
    > "$WORK_DIR/breaking-enum.log" 2>&1; then
  echo "Expected public enum constant removal fixture to fail binary compatibility check" >&2
  exit 1
fi

if mvn -q -s "$ROOT_DIR/.mvn/maven-central-settings.xml" -f "$ROOT_DIR/.github/api-compatibility-fixtures/pom.xml" \
    -Dold.jar="$WORK_DIR/old.jar" \
    -Dnew.jar="$WORK_DIR/source-breaking.jar" \
    verify \
    > "$WORK_DIR/source-breaking.log" 2>&1; then
  echo "Expected checked-exception fixture to fail source compatibility check" >&2
  exit 1
fi

ANNOTATION_OLD_CLASSES="$WORK_DIR/annotation-default-old-classes"
ANNOTATION_NEW_CLASSES="$WORK_DIR/annotation-default-new-classes"
ANNOTATION_OLD_CONSUMER_CLASSES="$WORK_DIR/annotation-default-old-consumer-classes"
ANNOTATION_CURRENT_CONSUMER_CLASSES="$WORK_DIR/annotation-default-current-consumer-classes"
ANNOTATION_RUNNER_CLASSES="$WORK_DIR/annotation-default-runner-classes"
mkdir -p "$ANNOTATION_OLD_CLASSES" "$ANNOTATION_NEW_CLASSES" \
  "$ANNOTATION_OLD_CONSUMER_CLASSES" "$ANNOTATION_CURRENT_CONSUMER_CLASSES" \
  "$ANNOTATION_RUNNER_CLASSES"

javac -d "$ANNOTATION_OLD_CLASSES" \
  "$ROOT_DIR/.github/api-compatibility-fixtures/annotation-default-old/io/github/huynhngochuyhoang/httpstarter/annotation/CacheResponse.java"
javac -d "$ANNOTATION_NEW_CLASSES" \
  "$ROOT_DIR/.github/api-compatibility-fixtures/annotation-default-new/io/github/huynhngochuyhoang/httpstarter/annotation/CacheResponse.java"
jar --create --file "$WORK_DIR/annotation-default-old.jar" -C "$ANNOTATION_OLD_CLASSES" .
jar --create --file "$WORK_DIR/annotation-default-new.jar" -C "$ANNOTATION_NEW_CLASSES" .

javac -cp "$WORK_DIR/annotation-default-old.jar" \
  -d "$ANNOTATION_OLD_CONSUMER_CLASSES" \
  "$ROOT_DIR/.github/api-compatibility-fixtures/annotation-default-consumer/compatibility/fixture/LegacyCacheClient.java"
javac -cp "$WORK_DIR/annotation-default-new.jar" \
  -d "$ANNOTATION_CURRENT_CONSUMER_CLASSES" \
  "$ROOT_DIR/.github/api-compatibility-fixtures/annotation-default-consumer/compatibility/fixture/LegacyCacheClient.java"
javac -cp "$WORK_DIR/annotation-default-new.jar:$ANNOTATION_OLD_CONSUMER_CLASSES" \
  -d "$ANNOTATION_RUNNER_CLASSES" \
  "$ROOT_DIR/.github/api-compatibility-fixtures/annotation-default-consumer/compatibility/fixture/AnnotationDefaultCompatibility.java"
java -cp "$WORK_DIR/annotation-default-new.jar:$ANNOTATION_OLD_CONSUMER_CLASSES:$ANNOTATION_RUNNER_CLASSES" \
  compatibility.fixture.AnnotationDefaultCompatibility

mvn -q -s "$ROOT_DIR/.mvn/maven-central-settings.xml" -f "$ROOT_DIR/.github/api-compatibility-fixtures/pom.xml" \
  -Dold.jar="$WORK_DIR/annotation-default-old.jar" \
  -Dnew.jar="$WORK_DIR/annotation-default-new.jar" \
  verify

cat > "$WORK_DIR/report-only.diff" <<'EOF'
+++  NEW METHOD: PUBLIC(+) void compatibleAddition()
	+++! NEW EXCEPTION: java.io.IOException
***! MODIFIED INTERFACE: PUBLIC ABSTRACT compatibility.fixture.PublicApi
---! REMOVED METHOD: PUBLIC(-) void removed()
EOF
cat > "$WORK_DIR/expected-major-delta.txt" <<'EOF'
fixture|+++! NEW EXCEPTION: java.io.IOException
fixture|***! MODIFIED INTERFACE: PUBLIC ABSTRACT compatibility.fixture.PublicApi
fixture|---! REMOVED METHOD: PUBLIC(-) void removed()
EOF
bash "$ROOT_DIR/scripts/verify-v27-major-api-delta.sh" \
  --normalize-report fixture "$WORK_DIR/report-only.diff" \
  > "$WORK_DIR/actual-major-delta.txt"
diff -u "$WORK_DIR/expected-major-delta.txt" "$WORK_DIR/actual-major-delta.txt"

echo "API compatibility fixtures passed: additive and defaulted annotation APIs accepted; existing annotation uses remain source and binary compatible; source-only checked exception plus constructor, nested method, enum constant removal, and report-only incompatible additions are detected."
