#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LANE="${1:-}"
BASELINE_VERSION="${2:-}"
EVIDENCE_DIR="${3:-}"
shift $(( $# >= 3 ? 3 : $# ))
ARTIFACTS=("$@")

fail() {
  echo "Published baseline provenance failed: $*" >&2
  exit 1
}

[[ "$LANE" =~ ^[a-z0-9][a-z0-9-]*$ ]] || fail "lane must use lowercase letters, numbers, and hyphens"
[[ -n "$BASELINE_VERSION" ]] || fail "baseline version is required"
[[ -n "$EVIDENCE_DIR" ]] || fail "evidence directory is required"
[[ "$EVIDENCE_DIR" = /* ]] || EVIDENCE_DIR="$ROOT_DIR/$EVIDENCE_DIR"
[[ ${#ARTIFACTS[@]} -gt 0 ]] || fail "at least one project artifact is required"

REPOSITORY="$ROOT_DIR/target/published-baseline-repositories/$LANE-$BASELINE_VERSION"
GROUP_PATH="io/github/huynhngochuyhoang"
[[ -d "$REPOSITORY" ]] || fail "repository does not exist: $REPOSITORY"
[[ "$EVIDENCE_DIR" = "$ROOT_DIR/target/"* ]] || fail "evidence must remain under target/"
[[ ! -e "$EVIDENCE_DIR" ]] || fail "fresh evidence directory required: $EVIDENCE_DIR"

mkdir -p "$EVIDENCE_DIR/remote-markers"
CHECKSUMS="$EVIDENCE_DIR/project-artifact-sha256.txt"

for artifact in "${ARTIFACTS[@]}"; do
  artifact_dir="$REPOSITORY/$GROUP_PATH/$artifact/$BASELINE_VERSION"
  marker="$artifact_dir/_remote.repositories"
  pom="$artifact_dir/$artifact-$BASELINE_VERSION.pom"
  jar="$artifact_dir/$artifact-$BASELINE_VERSION.jar"

  [[ -f "$marker" && -f "$pom" ]] \
    || fail "missing published POM or remote marker for $artifact:$BASELINE_VERSION"
  grep -Eq "^$artifact-$BASELINE_VERSION\\.pom>maven-central=$" "$marker" \
    || fail "$artifact POM was not resolved from Maven Central"
  sha256sum "$pom" >> "$CHECKSUMS"

  if [[ "$artifact" != "reactive-http-client" ]]; then
    [[ -f "$jar" ]] || fail "missing published jar for $artifact:$BASELINE_VERSION"
    grep -Eq "^$artifact-$BASELINE_VERSION\\.jar>maven-central=$" "$marker" \
      || fail "$artifact jar was not resolved from Maven Central"
    sha256sum "$jar" >> "$CHECKSUMS"
  fi

  mkdir -p "$EVIDENCE_DIR/remote-markers/$artifact"
  cp "$marker" "$EVIDENCE_DIR/remote-markers/$artifact/"
done

while IFS= read -r version_dir; do
  [[ "$(basename "$version_dir")" == "$BASELINE_VERSION" ]] \
    || fail "candidate or unrelated project version found in baseline repository: $version_dir"
done < <(find "$REPOSITORY/$GROUP_PATH" -mindepth 2 -maxdepth 2 -type d 2>/dev/null || true)

{
  echo "lane=$LANE"
  echo "baselineVersion=$BASELINE_VERSION"
  echo "repository=$REPOSITORY"
  echo "settings=.mvn/maven-central-settings.xml"
  echo "source=Maven Central"
  printf 'artifacts=%s\n' "${ARTIFACTS[*]}"
} > "$EVIDENCE_DIR/provenance.properties"

echo "Published baseline provenance passed for $LANE at $BASELINE_VERSION."
