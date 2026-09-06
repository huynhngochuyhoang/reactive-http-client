#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EVIDENCE_DIR="$ROOT_DIR/target/release-evidence/v29/priority2/characterization"
CLASSPATH_FILE="$EVIDENCE_DIR/test-classpath.txt"
REPETITIONS="${V29_MEMORY_REPETITIONS:-5}"

case "$EVIDENCE_DIR" in
  "$ROOT_DIR"/target/*) ;;
  *)
    echo "Refusing to write characterization data outside the reactor target directory" >&2
    exit 1
    ;;
esac

mkdir -p "$EVIDENCE_DIR"

STARTER_COMMIT="$(git -C "$ROOT_DIR" rev-parse --verify HEAD)"
SOURCE_TREE_DIRTY=false
if [[ -n "$(git -C "$ROOT_DIR" status --porcelain --untracked-files=normal)" ]]; then
  SOURCE_TREE_DIRTY=true
fi

mvn -B -ntp -pl reactive-http-client-starter \
  -DskipTests \
  -Dmdep.outputFile="$CLASSPATH_FILE" \
  -Dmdep.pathSeparator=: \
  test-compile dependency:build-classpath

TEST_CLASSPATH="$ROOT_DIR/reactive-http-client-starter/target/test-classes:$ROOT_DIR/reactive-http-client-starter/target/classes:$(<"$CLASSPATH_FILE")"

java \
  -Dv29.starter.commit="$STARTER_COMMIT" \
  -Dv29.starter.dirty="$SOURCE_TREE_DIRTY" \
  -cp "$TEST_CLASSPATH" \
  io.github.huynhngochuyhoang.httpstarter.core.ResponseCacheMemoryCharacterization \
  "$EVIDENCE_DIR" "$REPETITIONS"

test -s "$EVIDENCE_DIR/run.properties"
test -s "$EVIDENCE_DIR/samples.tsv"
test -s "$EVIDENCE_DIR/summary.tsv"

printf 'metadata=%s\nsamples=%s\nsummary=%s\n' \
  "$EVIDENCE_DIR/run.properties" \
  "$EVIDENCE_DIR/samples.tsv" \
  "$EVIDENCE_DIR/summary.tsv"
