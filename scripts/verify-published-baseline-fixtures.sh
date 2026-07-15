#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="fixture"
GROUP_PATH="io/github/huynhngochuyhoang"
ARTIFACT="reactive-http-client-starter"
BASE="$ROOT_DIR/target/published-baseline-repositories"
WORK="$ROOT_DIR/target/published-baseline-provenance-fixtures"

rm -rf "$BASE/fixture-local-$VERSION" "$BASE/fixture-central-$VERSION" "$BASE/fixture-mixed-$VERSION" "$WORK"

seed_artifact() {
  local lane="$1"
  local marker_value="$2"
  local dir="$BASE/$lane-$VERSION/$GROUP_PATH/$ARTIFACT/$VERSION"
  mkdir -p "$dir"
  printf 'fixture jar\n' > "$dir/$ARTIFACT-$VERSION.jar"
  printf '<project/>\n' > "$dir/$ARTIFACT-$VERSION.pom"
  {
    printf '%s.jar>%s\n' "$ARTIFACT-$VERSION" "$marker_value"
    printf '%s.pom>%s\n' "$ARTIFACT-$VERSION" "$marker_value"
  } > "$dir/_remote.repositories"
}

seed_artifact fixture-local "="
if "$ROOT_DIR/scripts/verify-published-baseline-provenance.sh" \
    fixture-local "$VERSION" "$WORK/local" "$ARTIFACT"; then
  echo "Locally installed candidate unexpectedly passed provenance verification" >&2
  exit 1
fi

seed_artifact fixture-central "maven-central="
"$ROOT_DIR/scripts/verify-published-baseline-provenance.sh" \
  fixture-central "$VERSION" "$WORK/central" "$ARTIFACT"
grep -q 'source=Maven Central' "$WORK/central/provenance.properties"
test "$(wc -l < "$WORK/central/project-artifact-sha256.txt")" -eq 2

seed_artifact fixture-mixed "maven-central="
candidate_dir="$BASE/fixture-mixed-$VERSION/$GROUP_PATH/$ARTIFACT/3.1.0-SNAPSHOT"
mkdir -p "$candidate_dir"
printf 'local candidate\n' > "$candidate_dir/$ARTIFACT-3.1.0-SNAPSHOT.jar"
if "$ROOT_DIR/scripts/verify-published-baseline-provenance.sh" \
    fixture-mixed "$VERSION" "$WORK/mixed" "$ARTIFACT"; then
  echo "Repository containing a conflicting local candidate unexpectedly passed" >&2
  exit 1
fi

echo "Published baseline provenance fixtures passed."
