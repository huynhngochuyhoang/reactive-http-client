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

echo "API compatibility fixtures passed: additive API accepted; constructor removal rejected."
