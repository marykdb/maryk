#!/usr/bin/env bash
set -euo pipefail

repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
test_root=$(mktemp -d)
trap 'rm -rf -- "$test_root"' EXIT
mkdir "$test_root/bin"
cp "$repository_root/.github/scripts/verify-installer-publication.sh" "$test_root/verifier.sh"
cat > "$test_root/verify-publish-provenance.sh" <<'PROVENANCE'
#!/usr/bin/env bash
echo checked > "$TEST_ROOT/provenance"
exit "${PROVENANCE_STATUS:-0}"
PROVENANCE
cat > "$test_root/bin/curl" <<'CURL'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$TEST_ROOT/requests"
if [[ -n "${FAILED_ARTIFACT:-}" && "$*" == *"$FAILED_ARTIFACT"* ]]; then
  exit 22
fi
CURL
chmod +x "$test_root/bin/curl"
run_verifier() {
  rm -f "$test_root/requests" "$test_root/provenance" "$test_root/summary"
  env PATH="$test_root/bin:$PATH" TEST_ROOT="$test_root" \
    MAVEN_PUBLISHED_LOCALLY=false GITHUB_EVENT_NAME=workflow_dispatch \
    RELEASE_TAG=0.13.0 SOURCE_SHA=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa \
    GITHUB_STEP_SUMMARY="$test_root/summary" \
    "$@" bash "$test_root/verifier.sh" > "$test_root/output" 2>&1
}

run_verifier
test -f "$test_root/provenance"
test ! -f "$test_root/requests"
if run_verifier PROVENANCE_STATUS=1; then
  echo 'Accepted failed CI publication provenance' >&2; exit 1
fi
test ! -f "$test_root/requests"

run_verifier MAVEN_PUBLISHED_LOCALLY=true
test ! -f "$test_root/provenance"
test "$(wc -l < "$test_root/requests" | tr -d ' ')" = 5
for artifact in maryk-core maryk-core-jvm; do
  for extension in pom module; do
    grep -Fq "https://repo.maven.apache.org/maven2/io/maryk/$artifact/0.13.0/$artifact-0.13.0.$extension" "$test_root/requests"
  done
done
grep -Fq 'maryk-core-jvm-0.13.0.jar' "$test_root/requests"
grep -Fq 'Maven artifact source provenance is unverified' "$test_root/summary"

for setting in GITHUB_EVENT_NAME=release RELEASE_TAG=0.13.0-SNAPSHOT RELEASE_TAG=../0.13.0 MAVEN_PUBLISHED_LOCALLY=invalid; do
  if run_verifier MAVEN_PUBLISHED_LOCALLY=true "$setting"; then
    echo "Accepted invalid manual publication: $setting" >&2; exit 1
  fi
  test ! -f "$test_root/requests"
done
for artifact in maryk-core-0.13.0.pom maryk-core-0.13.0.module maryk-core-jvm-0.13.0.pom maryk-core-jvm-0.13.0.module maryk-core-jvm-0.13.0.jar; do
  if run_verifier MAVEN_PUBLISHED_LOCALLY=true FAILED_ARTIFACT="$artifact"; then
    echo "Accepted unavailable Central artifact: $artifact" >&2; exit 1
  fi
  test ! -f "$test_root/summary"
done
echo 'Installer publication verifier tests passed'
