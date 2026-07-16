#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LATEST_TAG="v2.14.1"
PREDECESSOR_VERSION="2.14.0"
TAG="${1:-$LATEST_TAG}"
BASELINE_VERSION="${2:-$PREDECESSOR_VERSION}"
VERSION="${TAG#v}"
LANE="maintenance-2x"
WORK_ROOT="$ROOT_DIR/target/maintenance-lane"
WORKTREE="$WORK_ROOT/worktrees/$TAG"
REPOSITORY="$ROOT_DIR/target/published-baseline-repositories/$LANE-$BASELINE_VERSION"
BUILD_REPOSITORY="$WORK_ROOT/repositories/build-$VERSION"
EVIDENCE_DIR="$ROOT_DIR/target/release-evidence/$LANE-$VERSION"
MODULES=(reactive-http-client-starter reactive-http-client-test reactive-http-client-otel)

fail() {
  echo "Maintenance-lane verification failed: $*" >&2
  exit 1
}

[[ "$TAG" == "$LATEST_TAG" ]] || fail "latest immutable 2.x release tag must be $LATEST_TAG"
[[ "$TAG" =~ ^v2\.[0-9]+\.[0-9]+$ ]] || fail "release tag must identify a 2.x release"
[[ "$BASELINE_VERSION" =~ ^2\.[0-9]+\.[0-9]+$ ]] || fail "API baseline must identify a 2.x release"
[[ "$VERSION" != "$BASELINE_VERSION" ]] || fail "API baseline must differ from maintenance release $VERSION"
[[ "$BASELINE_VERSION" == "$PREDECESSOR_VERSION" ]] \
  || fail "$TAG must use published predecessor $PREDECESSOR_VERSION"
git -C "$ROOT_DIR" rev-parse --verify --quiet "$TAG^{commit}" >/dev/null \
  || fail "missing local release tag $TAG"
[[ ! -e "$WORKTREE" ]] || fail "fresh detached worktree required: $WORKTREE"
[[ ! -e "$REPOSITORY" ]] || fail "fresh isolated Maven repository required: $REPOSITORY"
[[ ! -e "$BUILD_REPOSITORY" ]] || fail "fresh maintenance build repository required: $BUILD_REPOSITORY"
[[ ! -e "$EVIDENCE_DIR" ]] || fail "fresh evidence directory required: $EVIDENCE_DIR"

TAG_COMMIT="$(git -C "$ROOT_DIR" rev-parse "$TAG^{commit}")"
REMOTE_COMMIT="$(git -C "$ROOT_DIR" ls-remote origin "refs/tags/$TAG^{}" | awk 'NR == 1 { print $1 }')"
if [[ -z "$REMOTE_COMMIT" ]]; then
  REMOTE_COMMIT="$(git -C "$ROOT_DIR" ls-remote origin "refs/tags/$TAG" | awk 'NR == 1 { print $1 }')"
fi
[[ "$REMOTE_COMMIT" == "$TAG_COMMIT" ]] \
  || fail "local $TAG commit $TAG_COMMIT does not match remote commit ${REMOTE_COMMIT:-missing}"

mkdir -p "$(dirname "$WORKTREE")" "$EVIDENCE_DIR/effective-poms" "$EVIDENCE_DIR/artifact-entries"
cleanup() {
  git -C "$ROOT_DIR" worktree remove --force "$WORKTREE" >/dev/null 2>&1 || true
}
trap cleanup EXIT
git -C "$ROOT_DIR" worktree add --detach "$WORKTREE" "$TAG"
[[ "$(git -C "$WORKTREE" rev-parse --abbrev-ref HEAD)" == "HEAD" ]] \
  || fail "maintenance worktree is not detached"
[[ -z "$(git -C "$WORKTREE" status --porcelain --untracked-files=all)" ]] \
  || fail "maintenance worktree is not clean"

MAVEN=(mvn -B -ntp -s "$ROOT_DIR/.mvn/maven-central-settings.xml"
  "-Dmaven.repo.local=$BUILD_REPOSITORY")
BASELINE_MAVEN=(mvn -B -ntp -s "$ROOT_DIR/.mvn/maven-central-settings.xml"
  "-Dmaven.repo.local=$REPOSITORY")

cat > "$EVIDENCE_DIR/commands.txt" <<EOF
git worktree add --detach target/maintenance-lane/worktrees/$TAG $TAG
mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local=$REPOSITORY dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client:$BASELINE_VERSION:pom -Dtransitive=false
mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local=$REPOSITORY dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-starter:$BASELINE_VERSION:pom -Dtransitive=false
mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local=$REPOSITORY dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-starter:$BASELINE_VERSION:jar -Dtransitive=false
mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local=$REPOSITORY dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-test:$BASELINE_VERSION:pom -Dtransitive=false
mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local=$REPOSITORY dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-test:$BASELINE_VERSION:jar -Dtransitive=false
mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local=$REPOSITORY dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-otel:$BASELINE_VERSION:pom -Dtransitive=false
mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local=$REPOSITORY dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-otel:$BASELINE_VERSION:jar -Dtransitive=false
mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local=$BUILD_REPOSITORY -Papi-compatibility,release -Dgpg.skip=true clean verify
scripts/verify-published-baseline-provenance.sh $LANE $BASELINE_VERSION target/release-evidence/$LANE-$VERSION/published-baseline reactive-http-client reactive-http-client-starter reactive-http-client-test reactive-http-client-otel
mvn -B -ntp -Prelease -DskipTests -DautoPublish=true deploy  # manual only; requires Central and GPG credentials
EOF

"${BASELINE_MAVEN[@]}" -f "$WORKTREE/pom.xml" dependency:get \
  -Dartifact=io.github.huynhngochuyhoang:reactive-http-client:"$BASELINE_VERSION":pom \
  -Dtransitive=false

for artifact in "${MODULES[@]}"; do
  "${BASELINE_MAVEN[@]}" -f "$WORKTREE/pom.xml" dependency:get \
    -Dartifact=io.github.huynhngochuyhoang:"$artifact":"$BASELINE_VERSION":pom \
    -Dtransitive=false
  "${BASELINE_MAVEN[@]}" -f "$WORKTREE/pom.xml" dependency:get \
    -Dartifact=io.github.huynhngochuyhoang:"$artifact":"$BASELINE_VERSION":jar \
    -Dtransitive=false
done

"${MAVEN[@]}" -f "$WORKTREE/pom.xml" \
  -Papi-compatibility,release -Dgpg.skip=true clean verify

for module in . "${MODULES[@]}"; do
  name="${module#.}"
  [[ -n "$name" ]] || name="parent"
  pom="$WORKTREE/pom.xml"
  [[ "$module" == "." ]] || pom="$WORKTREE/$module/pom.xml"
  "${MAVEN[@]}" -f "$pom" -Prelease help:effective-pom \
    -Doutput="$EVIDENCE_DIR/effective-poms/$name.xml"
done

"${MAVEN[@]}" -f "$WORKTREE/pom.xml" -pl reactive-http-client-starter -am \
  dependency:tree -DoutputFile="$EVIDENCE_DIR/dependency-tree-starter.txt"
"${MAVEN[@]}" -f "$WORKTREE/pom.xml" -pl reactive-http-client-test -am \
  dependency:tree -DoutputFile="$EVIDENCE_DIR/dependency-tree-test.txt"
"${MAVEN[@]}" -f "$WORKTREE/pom.xml" -pl reactive-http-client-otel -am \
  dependency:tree -DoutputFile="$EVIDENCE_DIR/dependency-tree-otel.txt"

grep -Eq 'org\.springframework\.boot:[^:]+:[^:]+:3\.5\.' "$EVIDENCE_DIR/dependency-tree-starter.txt" \
  || fail "starter dependency tree does not contain Spring Boot 3.5"
grep -Eq 'com\.fasterxml\.jackson\.core:jackson-databind:[^:]+:2\.' "$EVIDENCE_DIR/dependency-tree-starter.txt" \
  || fail "starter dependency tree does not contain Jackson 2 Databind"
if grep -REq 'org\.springframework\.boot:[^:]+:[^:]+:4\.|tools\.jackson\.' \
    "$EVIDENCE_DIR"/dependency-tree-*.txt; then
  fail "maintenance dependency tree contains Boot 4 or Jackson 3"
fi

grep -q '<artifactId>maven-gpg-plugin</artifactId>' "$EVIDENCE_DIR/effective-poms/parent.xml" \
  || fail "release effective POM does not configure artifact signing"
grep -q '<artifactId>central-publishing-maven-plugin</artifactId>' "$EVIDENCE_DIR/effective-poms/parent.xml" \
  || fail "release effective POM does not configure Central publishing"

CHECKSUMS="$EVIDENCE_DIR/maintenance-artifact-sha256.txt"
sha256sum "$WORKTREE/pom.xml" >> "$CHECKSUMS"
for module in "${MODULES[@]}"; do
  binary="$WORKTREE/$module/target/$module-$VERSION.jar"
  sources="$WORKTREE/$module/target/$module-$VERSION-sources.jar"
  javadoc="$WORKTREE/$module/target/$module-$VERSION-javadoc.jar"
  for artifact in "$WORKTREE/$module/pom.xml" "$binary" "$sources" "$javadoc"; do
    [[ -f "$artifact" ]] || fail "missing release artifact $artifact"
    sha256sum "$artifact" >> "$CHECKSUMS"
  done
  jar tf "$binary" > "$EVIDENCE_DIR/artifact-entries/$module-binary.txt"
  jar tf "$sources" > "$EVIDENCE_DIR/artifact-entries/$module-sources.txt"
  if grep -Eqi '(^|/)(boot4|src/boot4)(/|$)|Boot4|Jackson3' \
      "$EVIDENCE_DIR/artifact-entries/$module-"*.txt; then
    fail "$module artifacts contain Boot 4 or Jackson 3 implementation entries"
  fi
done

"$ROOT_DIR/scripts/verify-published-baseline-provenance.sh" \
  "$LANE" "$BASELINE_VERSION" "$EVIDENCE_DIR/published-baseline" \
  reactive-http-client reactive-http-client-starter reactive-http-client-test reactive-http-client-otel

cat > "$EVIDENCE_DIR/maintenance-lane.properties" <<EOF
releaseTag=$TAG
releaseVersion=$VERSION
releaseCommit=$TAG_COMMIT
remoteTagCommit=$REMOTE_COMMIT
apiBaselineVersion=$BASELINE_VERSION
springBootLine=3.5
jacksonLine=2
worktreeMode=detached
signingCheck=release-profile-wiring
centralCheck=publishing-extension-wiring
EOF

echo "2.x maintenance-lane verification passed for $TAG against $BASELINE_VERSION."
