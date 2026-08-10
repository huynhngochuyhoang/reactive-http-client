#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="fixture"
GROUP_PATH="io/github/huynhngochuyhoang"
ARTIFACT="reactive-http-client-starter"
BASE="$ROOT_DIR/target/published-baseline-repositories"
WORK="$ROOT_DIR/target/published-baseline-provenance-fixtures"

rm -rf "$BASE/fixture-local-$VERSION" "$BASE/fixture-central-$VERSION" \
  "$BASE/fixture-missing-pom-$VERSION" "$BASE/fixture-mixed-$VERSION" \
  "$BASE/fixture-release-$VERSION" "$BASE/fixture-release-missing-sources-$VERSION" \
  "$BASE/fixture-release-missing-javadoc-$VERSION" \
  "$BASE/fixture-release-mixed-pom-$VERSION" \
  "$BASE/fixture-release-mixed-project-version-$VERSION" \
  "$BASE/fixture-release-mixed-jar-$VERSION" "$WORK"

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

seed_artifact fixture-missing-pom "maven-central="
rm "$BASE/fixture-missing-pom-$VERSION/$GROUP_PATH/$ARTIFACT/$VERSION/$ARTIFACT-$VERSION.pom"
if "$ROOT_DIR/scripts/verify-published-baseline-provenance.sh" \
    fixture-missing-pom "$VERSION" "$WORK/missing-pom" "$ARTIFACT"; then
  echo "Central-marked JAR without its module POM unexpectedly passed" >&2
  exit 1
fi

seed_artifact fixture-mixed "maven-central="
candidate_dir="$BASE/fixture-mixed-$VERSION/$GROUP_PATH/$ARTIFACT/3.1.0-SNAPSHOT"
mkdir -p "$candidate_dir"
printf 'local candidate\n' > "$candidate_dir/$ARTIFACT-3.1.0-SNAPSHOT.jar"
if "$ROOT_DIR/scripts/verify-published-baseline-provenance.sh" \
    fixture-mixed "$VERSION" "$WORK/mixed" "$ARTIFACT"; then
  echo "Repository containing a conflicting local candidate unexpectedly passed" >&2
  exit 1
fi

seed_release_bundle() {
  local lane="$1"
  local parent_dir="$BASE/$lane-$VERSION/$GROUP_PATH/reactive-http-client/$VERSION"
  mkdir -p "$parent_dir"
  printf '<project><version>%s</version></project>\n' "$VERSION" \
    > "$parent_dir/reactive-http-client-$VERSION.pom"
  printf 'reactive-http-client-%s.pom>maven-central=\n' "$VERSION" \
    > "$parent_dir/_remote.repositories"

  for module in reactive-http-client-starter reactive-http-client-test reactive-http-client-otel; do
    local dir="$BASE/$lane-$VERSION/$GROUP_PATH/$module/$VERSION"
    local metadata="$WORK/jar-metadata/$lane/$module/META-INF/maven/io.github.huynhngochuyhoang/$module"
    mkdir -p "$dir"
    printf '<project><parent><version>%s</version></parent></project>\n' "$VERSION" \
      > "$dir/$module-$VERSION.pom"
    mkdir -p "$metadata"
    printf 'artifactId=%s\ngroupId=io.github.huynhngochuyhoang\nversion=%s\n' \
      "$module" "$VERSION" > "$metadata/pom.properties"
    jar --create --file "$dir/$module-$VERSION.jar" \
      -C "$WORK/jar-metadata/$lane/$module" META-INF
    printf 'fixture sources\n' > "$dir/$module-$VERSION-sources.jar"
    printf 'fixture javadoc\n' > "$dir/$module-$VERSION-javadoc.jar"
    {
      printf '%s-%s.pom>maven-central=\n' "$module" "$VERSION"
      printf '%s-%s.jar>maven-central=\n' "$module" "$VERSION"
      printf '%s-%s-sources.jar>maven-central=\n' "$module" "$VERSION"
      printf '%s-%s-javadoc.jar>maven-central=\n' "$module" "$VERSION"
    } > "$dir/_remote.repositories"
  done
}

release_artifacts=(reactive-http-client reactive-http-client-starter reactive-http-client-test reactive-http-client-otel)
seed_release_bundle fixture-release
"$ROOT_DIR/scripts/verify-published-baseline-provenance.sh" \
  fixture-release "$VERSION" "$WORK/release" --release-artifacts "${release_artifacts[@]}"
test "$(wc -l < "$WORK/release/project-artifact-sha256.txt")" -eq 13

seed_release_bundle fixture-release-missing-sources
rm "$BASE/fixture-release-missing-sources-$VERSION/$GROUP_PATH/reactive-http-client-test/$VERSION/reactive-http-client-test-$VERSION-sources.jar"
if "$ROOT_DIR/scripts/verify-published-baseline-provenance.sh" \
    fixture-release-missing-sources "$VERSION" "$WORK/release-missing-sources" \
    --release-artifacts "${release_artifacts[@]}"; then
  echo "Release bundle without a sources jar unexpectedly passed" >&2
  exit 1
fi

seed_release_bundle fixture-release-missing-javadoc
rm "$BASE/fixture-release-missing-javadoc-$VERSION/$GROUP_PATH/reactive-http-client-otel/$VERSION/reactive-http-client-otel-$VERSION-javadoc.jar"
if "$ROOT_DIR/scripts/verify-published-baseline-provenance.sh" \
    fixture-release-missing-javadoc "$VERSION" "$WORK/release-missing-javadoc" \
    --release-artifacts "${release_artifacts[@]}"; then
  echo "Release bundle without a Javadoc jar unexpectedly passed" >&2
  exit 1
fi

seed_release_bundle fixture-release-mixed-pom
printf '<project><parent><version>other</version></parent></project>\n' \
  > "$BASE/fixture-release-mixed-pom-$VERSION/$GROUP_PATH/reactive-http-client-test/$VERSION/reactive-http-client-test-$VERSION.pom"
if "$ROOT_DIR/scripts/verify-published-baseline-provenance.sh" \
    fixture-release-mixed-pom "$VERSION" "$WORK/release-mixed-pom" \
    --release-artifacts "${release_artifacts[@]}"; then
  echo "Release bundle with a mismatched module POM version unexpectedly passed" >&2
  exit 1
fi

seed_release_bundle fixture-release-mixed-project-version
printf '<project><parent><version>%s</version></parent><version>other</version></project>\n' \
  "$VERSION" \
  > "$BASE/fixture-release-mixed-project-version-$VERSION/$GROUP_PATH/reactive-http-client-test/$VERSION/reactive-http-client-test-$VERSION.pom"
if "$ROOT_DIR/scripts/verify-published-baseline-provenance.sh" \
    fixture-release-mixed-project-version "$VERSION" "$WORK/release-mixed-project-version" \
    --release-artifacts "${release_artifacts[@]}"; then
  echo "Release bundle with a matching parent but mismatched project version unexpectedly passed" >&2
  exit 1
fi

seed_release_bundle fixture-release-mixed-jar
mixed_jar_dir="$BASE/fixture-release-mixed-jar-$VERSION/$GROUP_PATH/reactive-http-client-test/$VERSION"
mixed_metadata="$WORK/jar-metadata/fixture-release-mixed-jar/reactive-http-client-test/META-INF/maven/io.github.huynhngochuyhoang/reactive-http-client-test"
sed -i 's/^version=.*/version=other/' "$mixed_metadata/pom.properties"
jar --create --file "$mixed_jar_dir/reactive-http-client-test-$VERSION.jar" \
  -C "$WORK/jar-metadata/fixture-release-mixed-jar/reactive-http-client-test" META-INF
if "$ROOT_DIR/scripts/verify-published-baseline-provenance.sh" \
    fixture-release-mixed-jar "$VERSION" "$WORK/release-mixed-jar" \
    --release-artifacts "${release_artifacts[@]}"; then
  echo "Release bundle with a mismatched binary version unexpectedly passed" >&2
  exit 1
fi

PROJECT_VERSION="$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version)"
for scope in root module; do
  if [[ "$scope" == root ]]; then
    command=(mvn -q -N -Papi-compatibility
      -Dapi.compatibility.baseline.version="$PROJECT_VERSION" validate)
  else
    command=(mvn -q -pl reactive-http-client-starter -Papi-compatibility
      -Dapi.compatibility.baseline.version="$PROJECT_VERSION" validate)
  fi
  log="$WORK/self-comparison-$scope.log"
  if "${command[@]}" > "$log" 2>&1; then
    echo "$scope API compatibility unexpectedly accepted the current reactor as its baseline" >&2
    exit 1
  fi
  grep -q "must point to the last published release, not the current reactor version" "$log"
done

echo "Published baseline provenance fixtures passed, including root and module self-comparison guards."
