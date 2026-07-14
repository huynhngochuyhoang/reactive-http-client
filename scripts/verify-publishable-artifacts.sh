#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_VERSION="${1:-$(mvn -q -f "$ROOT_DIR/pom.xml" -DforceStdout help:evaluate -Dexpression=project.version)}"
EVIDENCE_DIR="$ROOT_DIR/target/release-evidence/v20-priority5"
STAGING_REPOSITORY="$EVIDENCE_DIR/staging-repository"
CONSUMER_REPOSITORY="$EVIDENCE_DIR/consumer-repository"
EFFECTIVE_POMS="$EVIDENCE_DIR/effective-poms"
SETTINGS_FILE="$EVIDENCE_DIR/staged-consumer-settings.xml"
GROUP_PATH="io/github/huynhngochuyhoang"
PUBLISHABLE_MODULES=(reactive-http-client-starter reactive-http-client-test reactive-http-client-otel)

fail() {
  echo "Publishable artifact check failed: $*" >&2
  exit 1
}

require_file() {
  [[ -f "$1" ]] || fail "missing $1"
}

assert_signed() {
  local artifact="$1"
  local signature="${2:-$artifact.asc}"
  require_file "$signature"
  gpg --batch --verify "$signature" "$artifact" >/dev/null 2>&1 || fail "invalid signature $signature"
}

rm -rf "$EVIDENCE_DIR"
mkdir -p "$STAGING_REPOSITORY" "$CONSUMER_REPOSITORY" "$EFFECTIVE_POMS"

for pom in "$ROOT_DIR/pom.xml" "$ROOT_DIR/reactive-http-client-starter/pom.xml" "$ROOT_DIR/reactive-http-client-test/pom.xml" "$ROOT_DIR/reactive-http-client-otel/pom.xml" "$ROOT_DIR/reactive-http-client-benchmarks/pom.xml"; do
  if grep -Pq '>(/[^<]+|[A-Za-z]:[\\/][^<]+)<' "$pom" \
      || grep -Eq '(file:|\{user.home}|\{project.basedir}|boot4-spike|reactive-http-client-.*-compat)' "$pom"; then
    fail "$pom contains a local path, spike profile, or unpublished compatibility coordinate"
  fi
done

for module in . reactive-http-client-starter reactive-http-client-test reactive-http-client-otel; do
  output_name="${module#.}"
  [[ -n "$output_name" ]] || output_name="parent"
  pom_dir="$ROOT_DIR"
  [[ "$module" == "." ]] || pom_dir="$ROOT_DIR/$module"
  mvn -q -s "$ROOT_DIR/.mvn/maven-central-settings.xml" -f "$pom_dir/pom.xml" help:effective-pom -Doutput="$EFFECTIVE_POMS/$output_name.xml"
done
mvn -q -s "$ROOT_DIR/.mvn/maven-central-settings.xml" -Pbenchmarks -pl reactive-http-client-benchmarks help:effective-pom -Doutput="$EFFECTIVE_POMS/benchmarks.xml"

require_file "$ROOT_DIR/target/reactive-http-client-$PROJECT_VERSION.pom.asc"
assert_signed "$ROOT_DIR/pom.xml" "$ROOT_DIR/target/reactive-http-client-$PROJECT_VERSION.pom.asc"
mvn -q -s "$ROOT_DIR/.mvn/maven-central-settings.xml" org.apache.maven.plugins:maven-deploy-plugin:3.1.4:deploy-file -DrepositoryId=v20-stage -Durl="file://$STAGING_REPOSITORY" -Dfile="$ROOT_DIR/pom.xml" -DpomFile="$ROOT_DIR/pom.xml" -DgeneratePom=false

for module in "${PUBLISHABLE_MODULES[@]}"; do
  binary="$ROOT_DIR/$module/target/$module-$PROJECT_VERSION.jar"
  sources="$ROOT_DIR/$module/target/$module-$PROJECT_VERSION-sources.jar"
  javadoc="$ROOT_DIR/$module/target/$module-$PROJECT_VERSION-javadoc.jar"
  pom="$ROOT_DIR/$module/pom.xml"

  for artifact in "$pom" "$binary" "$sources" "$javadoc"; do
    require_file "$artifact"
    if [[ "$artifact" == "$pom" ]]; then
      assert_signed "$artifact" "$ROOT_DIR/$module/target/$module-$PROJECT_VERSION.pom.asc"
    else
      assert_signed "$artifact"
    fi
  done

  mvn -q -s "$ROOT_DIR/.mvn/maven-central-settings.xml" org.apache.maven.plugins:maven-deploy-plugin:3.1.4:deploy-file -DrepositoryId=v20-stage -Durl="file://$STAGING_REPOSITORY" -Dfile="$binary" -DpomFile="$pom" -Dsources="$sources" -Djavadoc="$javadoc" -DgeneratePom=false
done

parent_dir="$STAGING_REPOSITORY/$GROUP_PATH/reactive-http-client/$PROJECT_VERSION"
cp "$ROOT_DIR/target/reactive-http-client-$PROJECT_VERSION.pom.asc" "$parent_dir/reactive-http-client-$PROJECT_VERSION.pom.asc"
for module in "${PUBLISHABLE_MODULES[@]}"; do
  coordinate_dir="$STAGING_REPOSITORY/$GROUP_PATH/$module/$PROJECT_VERSION"
  require_file "$coordinate_dir/$module-$PROJECT_VERSION.pom"
  cp "$ROOT_DIR/$module/target/$module-$PROJECT_VERSION.pom.asc" "$coordinate_dir/$module-$PROJECT_VERSION.pom.asc"
  cp "$ROOT_DIR/$module/target/$module-$PROJECT_VERSION.jar.asc" "$coordinate_dir/"
  cp "$ROOT_DIR/$module/target/$module-$PROJECT_VERSION-sources.jar.asc" "$coordinate_dir/"
  cp "$ROOT_DIR/$module/target/$module-$PROJECT_VERSION-javadoc.jar.asc" "$coordinate_dir/"
done

find "$STAGING_REPOSITORY/$GROUP_PATH" -type f ! -name '*.sha256' -print0 |
  while IFS= read -r -d '' artifact; do
    sha256sum "$artifact" | awk '{print $1}' > "$artifact.sha256"
  done

if find "$STAGING_REPOSITORY" -path '*reactive-http-client-benchmarks*' -print -quit | grep -q .; then
  fail "benchmark artifacts must not be staged"
fi

cat > "$SETTINGS_FILE" <<EOF
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <localRepository>$CONSUMER_REPOSITORY</localRepository>
  <profiles>
    <profile>
      <id>v20-stage</id>
      <repositories>
        <repository><id>v20-stage</id><url>file://$STAGING_REPOSITORY</url></repository>
        <repository><id>central</id><url>https://repo.maven.apache.org/maven2</url></repository>
      </repositories>
    </profile>
  </profiles>
  <activeProfiles><activeProfile>v20-stage</activeProfile></activeProfiles>
</settings>
EOF

mvn -q -s "$SETTINGS_FILE" -f "$ROOT_DIR/.github/boot4-consumer/pom.xml" -Dreactive-http-client.version="$PROJECT_VERSION" clean test
mvn -q -s "$SETTINGS_FILE" -f "$ROOT_DIR/.github/boot4-consumer/pom.xml" -Dreactive-http-client.version="$PROJECT_VERSION" dependency:tree -DoutputFile="$EVIDENCE_DIR/staged-consumer-dependency-tree.txt"

for module in "${PUBLISHABLE_MODULES[@]}"; do
  marker="$CONSUMER_REPOSITORY/$GROUP_PATH/$module/$PROJECT_VERSION/_remote.repositories"
  require_file "$marker"
  grep -q 'v20-stage' "$marker" || fail "$module did not resolve from the staged repository"
done

if grep -REqs "$ROOT_DIR/reactive-http-client-(starter|test|otel)/target/(test-)?classes" "$ROOT_DIR/.github/boot4-consumer/target/surefire-reports"; then
  fail "consumer test classpath contains reactor module classes"
fi

echo "Publishable parent, starter, test-helper, and OTel artifacts passed staged consumer validation at $PROJECT_VERSION."
