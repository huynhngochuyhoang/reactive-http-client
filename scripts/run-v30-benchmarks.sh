#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
MODE="${1:-}"
BASE="$ROOT/target/release-evidence/v30/priority12"
REPOSITORY="$ROOT/target/published-baseline-repositories/benchmark-v30-release-4.2.0"
SHARED='cacheDisabledProxyInvocation(CreatesPublisher|Subscription)|cacheV29NoNetwork(Unweighted(PublisherCreation|Subscription)|WeightedMetricsDisabled(PublisherCreation|Subscription)|WeightedHit|MeteredAccountingPublication)'
CURRENT=".*($SHARED|cacheV30.*)$"
PUBLISHED=".*($SHARED)$"
COMMIT="$(git rev-parse HEAD)"

fail() { printf '%s\n' "$*" >&2; exit 1; }
[[ "$MODE" == current || "$MODE" == baseline || "$MODE" == compare ]] \
  || fail "Usage: bash scripts/run-v30-benchmarks.sh current|baseline|compare"
[[ -z "$(git status --porcelain)" ]] || fail "Commit the reviewed benchmark tree before release runs."
mkdir -p "$BASE"

if [[ "$MODE" == compare ]]; then
  [[ "$(cat "$BASE/current/exit-status.txt")" == 0 && "$(cat "$BASE/baseline/exit-status.txt")" == 0 ]] \
    || fail "Both release runs must have completed successfully."
  [[ "$(cat "$BASE/current/commit.txt")" == "$COMMIT" && "$(cat "$BASE/baseline/commit.txt")" == "$COMMIT" ]] \
    || fail "Both runs must use this exact harness commit."
  python3 scripts/verify-v30-benchmark-results.py current "$BASE/current/release-jmh.json"
  python3 scripts/verify-v30-benchmark-results.py baseline "$BASE/baseline/release-jmh.json"
  [[ -s "$BASE/baseline/provenance/project-artifact-sha256.txt" ]] || fail "Missing Central provenance."
  # Clean only the module: a reactor-root clean would erase the retained evidence.
  mvn -B -ntp -f reactive-http-client-benchmarks/pom.xml clean
  mvn -B -ntp -Pbenchmarks,benchmark-compare -pl reactive-http-client-benchmarks -am verify \
    -DskipTests \
    -Dbenchmark.compare.current="$BASE/current/release-jmh.json" \
    -Dbenchmark.compare.baseline="$BASE/baseline/release-jmh.json" \
    -Dbenchmark.compare.output="$BASE/benchmark-comparison.md" > "$BASE/comparison.log" 2>&1
  grep -F 'Matched benchmark/mode rows: **16**' "$BASE/benchmark-comparison.md"
  grep -F 'Current-only rows: **46**' "$BASE/benchmark-comparison.md"
  grep -F 'Baseline-only rows: **0**' "$BASE/benchmark-comparison.md"
  [[ "$(git rev-parse HEAD)" == "$COMMIT" && -z "$(git status --porcelain)" ]] \
    || fail "Tree changed during comparison."
  find "$BASE/current" "$BASE/baseline" -type f -exec sha256sum {} + > "$BASE/release-sha256.txt"
  sha256sum "$BASE/benchmark-comparison.md" >> "$BASE/release-sha256.txt"
  printf 'Comparison ready for review: %s\n' "$BASE/benchmark-comparison.md"
  exit 0
fi

OUTPUT="$BASE/$MODE"
[[ ! -e "$OUTPUT" ]] || fail "Refusing to overwrite $OUTPUT; retain or move earlier evidence before rerunning."
if [[ "$MODE" == baseline ]]; then
  [[ ! -e "$REPOSITORY" ]] || fail "The published baseline requires a previously absent repository: $REPOSITORY"
fi
mkdir -p "$OUTPUT"
printf '%s\n' "$COMMIT" > "$OUTPUT/commit.txt"
git status --porcelain > "$OUTPUT/git-status-before.txt"
date -u +%FT%TZ > "$OUTPUT/started-at.txt"
mvn -version > "$OUTPUT/toolchain.txt" 2>&1
uname -a > "$OUTPUT/machine.txt"
lscpu >> "$OUTPUT/machine.txt"
free -b >> "$OUTPUT/machine.txt"
trap 'status=$?; printf "%s\n" "$status" > "$OUTPUT/exit-status.txt"; date -u +%FT%TZ > "$OUTPUT/ended-at.txt"' EXIT

if [[ "$MODE" == current ]]; then
  mvn -B -ntp -f reactive-http-client-benchmarks/pom.xml clean > "$OUTPUT/clean.log" 2>&1
  mvn -B -ntp -Pbenchmarks,benchmark-release -pl reactive-http-client-benchmarks -am verify \
    -DskipTests -Dbenchmark.commit="$COMMIT" -Dbenchmark.include="$CURRENT" \
    -Dbenchmark.result.dir="$OUTPUT" > "$OUTPUT/run.log" 2>&1
else
  mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local="$REPOSITORY" \
    -Pbenchmarks,benchmark-release,benchmark-published-baseline,benchmark-published-baseline-v30-source-exclusion \
    -pl reactive-http-client-benchmarks clean verify -DskipTests \
    -Dbenchmark.starter.version=4.2.0 -Dbenchmark.commit="$COMMIT" \
    -Dbenchmark.include="$PUBLISHED" -Dbenchmark.result.dir="$OUTPUT" > "$OUTPUT/run.log" 2>&1
  scripts/verify-published-baseline-provenance.sh benchmark-v30-release 4.2.0 \
    "$OUTPUT/provenance" reactive-http-client-starter
fi
python3 scripts/verify-v30-benchmark-results.py "$MODE" "$OUTPUT/release-jmh.json" > "$OUTPUT/coverage.json"
cp reactive-http-client-benchmarks/target/benchmarks.jar "$OUTPUT/benchmarks.jar"
sha256sum "$OUTPUT/benchmarks.jar" > "$OUTPUT/benchmarks.jar.sha256"
git status --porcelain > "$OUTPUT/git-status-after.txt"
[[ "$(git rev-parse HEAD)" == "$COMMIT" && ! -s "$OUTPUT/git-status-after.txt" ]] \
  || fail "Tree changed during benchmark; do not promote these results."
printf 'Completed %s. Retain the environment sidecar, JSON, Markdown, log, and jar: %s\n' "$MODE" "$OUTPUT"
