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

mvn -q -f "$ROOT_DIR/.github/api-compatibility-fixtures/pom.xml" \
  -Dold.jar="$WORK_DIR/old.jar" \
  -Dnew.jar="$WORK_DIR/additive.jar" \
  verify

if mvn -q -f "$ROOT_DIR/.github/api-compatibility-fixtures/pom.xml" \
    -Dold.jar="$WORK_DIR/old.jar" \
    -Dnew.jar="$WORK_DIR/breaking.jar" \
    verify \
    > "$WORK_DIR/breaking.log" 2>&1; then
  echo "Expected constructor-removal fixture to fail binary compatibility check" >&2
  exit 1
fi

if mvn -q -f "$ROOT_DIR/.github/api-compatibility-fixtures/pom.xml" \
    -Dold.jar="$WORK_DIR/old.jar" \
    -Dnew.jar="$WORK_DIR/breaking-nested.jar" \
    verify \
    > "$WORK_DIR/breaking-nested.log" 2>&1; then
  echo "Expected nested public method removal fixture to fail binary compatibility check" >&2
  exit 1
fi

if mvn -q -f "$ROOT_DIR/.github/api-compatibility-fixtures/pom.xml" \
    -Dold.jar="$WORK_DIR/old.jar" \
    -Dnew.jar="$WORK_DIR/breaking-enum.jar" \
    verify \
    > "$WORK_DIR/breaking-enum.log" 2>&1; then
  echo "Expected public enum constant removal fixture to fail binary compatibility check" >&2
  exit 1
fi

if mvn -q -f "$ROOT_DIR/.github/api-compatibility-fixtures/pom.xml" \
    -Dold.jar="$WORK_DIR/old.jar" \
    -Dnew.jar="$WORK_DIR/source-breaking.jar" \
    verify \
    > "$WORK_DIR/source-breaking.log" 2>&1; then
  echo "Expected checked-exception fixture to fail source compatibility check" >&2
  exit 1
fi

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

echo "API compatibility fixtures passed: additive API accepted; source-only checked exception plus constructor, nested method, enum constant removal, and report-only incompatible additions are detected."
