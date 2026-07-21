#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PUBLISHED_VERSION="${1:-}"
[[ -n "$PUBLISHED_VERSION" ]] || {
  echo "Usage: $0 <published-version>" >&2
  exit 2
}

LANE="release-artifacts"
REPOSITORY="$ROOT_DIR/target/published-baseline-repositories/$LANE-$PUBLISHED_VERSION"
EVIDENCE_DIR="$ROOT_DIR/target/release-evidence/published-baselines/$LANE-$PUBLISHED_VERSION"
SETTINGS="$ROOT_DIR/.mvn/maven-central-settings.xml"
GROUP_ID="io.github.huynhngochuyhoang"
MODULES=(reactive-http-client-starter reactive-http-client-test reactive-http-client-otel)

fail() {
  echo "Published release artifacts check failed: $*" >&2
  exit 1
}

[[ ! -e "$REPOSITORY" ]] || fail "fresh repository required; remove $REPOSITORY"
[[ ! -e "$EVIDENCE_DIR" ]] || fail "fresh evidence directory required; remove $EVIDENCE_DIR"
mkdir -p "$REPOSITORY"

MAVEN=(mvn -B -ntp -s "$SETTINGS" -f "$ROOT_DIR/.github/boot4-consumer/pom.xml"
  -Dmaven.repo.local="$REPOSITORY")
"${MAVEN[@]}" dependency:get \
  -Dartifact="$GROUP_ID:reactive-http-client:$PUBLISHED_VERSION:pom" -Dtransitive=false

for module in "${MODULES[@]}"; do
  "${MAVEN[@]}" dependency:get \
    -Dartifact="$GROUP_ID:$module:$PUBLISHED_VERSION:pom" -Dtransitive=false
  "${MAVEN[@]}" dependency:get \
    -Dartifact="$GROUP_ID:$module:$PUBLISHED_VERSION" -Dtransitive=false
  "${MAVEN[@]}" dependency:get \
    -Dartifact="$GROUP_ID:$module:$PUBLISHED_VERSION:jar:sources" -Dtransitive=false
  "${MAVEN[@]}" dependency:get \
    -Dartifact="$GROUP_ID:$module:$PUBLISHED_VERSION:jar:javadoc" -Dtransitive=false
done

"$ROOT_DIR/scripts/verify-published-baseline-provenance.sh" \
  "$LANE" "$PUBLISHED_VERSION" "$EVIDENCE_DIR" --release-artifacts \
  reactive-http-client "${MODULES[@]}"

echo "Published release bundle passed against Maven Central artifacts $PUBLISHED_VERSION."
