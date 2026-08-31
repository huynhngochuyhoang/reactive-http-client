#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EVIDENCE_DIR="$ROOT_DIR/target/release-evidence/v29/priority2/profiling"
JFR_FILE="$EVIDENCE_DIR/response-cache-memory-domains.jfr"

case "$EVIDENCE_DIR" in
  "$ROOT_DIR"/target/*) ;;
  *)
    echo "Refusing to write profiling data outside the reactor target directory" >&2
    exit 1
    ;;
esac

mkdir -p "$EVIDENCE_DIR"

echo "Profiling output can contain application data; keep $EVIDENCE_DIR target-only." >&2

mvn -B -ntp -pl reactive-http-client-starter \
  -Dtest=ResponseCacheMemoryWorkloadTest \
  -Dv29.memory.jfr="$JFR_FILE" \
  -DargLine="-XX:HeapDumpPath=$EVIDENCE_DIR" \
  test

test -s "$JFR_FILE"
test "$(wc -c < "$JFR_FILE")" -le "$((64 * 1024 * 1024))"

printf 'jfr=%s\nmaxBytes=%s\n' "$JFR_FILE" "$((64 * 1024 * 1024))"
