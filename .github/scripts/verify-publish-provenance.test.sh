#!/usr/bin/env bash
set -euo pipefail
repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
verifier="$repository_root/.github/scripts/verify-publish-provenance.sh"
test_root=$(mktemp -d)
trap 'rm -rf -- "$test_root"' EXIT
mkdir "$test_root/bin"
cat > "$test_root/bin/gh" <<'GH'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${API_FAILURE:-false}" == true ]]; then exit 1; fi
case "$*" in
  *actions/artifacts\?*) [[ "${MISSING_ARTIFACT:-false}" == true ]] || printf '123\t456\n' ;;
  *actions/runs/456*) printf '%s\t%s\n' "${RUN_CONCLUSION:-success}" "${RUN_PATH:-.github/workflows/publish.yml}" ;;
  *actions/artifacts/123/zip*) cat "$ARTIFACT_ZIP" ;;
  *) echo "Unexpected API call: $*" >&2; exit 2 ;;
esac
GH
printf '#!/usr/bin/env bash\nexit 0\n' > "$test_root/bin/sleep"
chmod +x "$test_root/bin/gh" "$test_root/bin/sleep"
sha=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
printf 'commit_sha=%s\nbuild_run_id=42\n' "$sha" > "$test_root/publish-provenance.txt"
(cd "$test_root" && zip -q matching.zip publish-provenance.txt)
printf 'commit_sha=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\n' > "$test_root/publish-provenance.txt"
(cd "$test_root" && zip -q mismatch.zip publish-provenance.txt)
run_verifier() {
  env PATH="$test_root/bin:$PATH" SOURCE_SHA="$sha" GITHUB_REPOSITORY=marykdb/maryk \
    GH_TOKEN=test-token RUNNER_TEMP="$test_root" ARTIFACT_ZIP="$test_root/matching.zip" \
    "$@" bash "$verifier" > "$test_root/output" 2>&1
}
run_verifier || { cat "$test_root/output"; echo 'Rejected successful publication with normal API path' >&2; exit 1; }
run_verifier RUN_PATH=.github/workflows/publish.yml@refs/heads/main
for setting in RUN_CONCLUSION=failure RUN_CONCLUSION=in_progress RUN_PATH=.github/workflows/other.yml RUN_PATH=.github/workflows/publish.yml.evil MISSING_ARTIFACT=true API_FAILURE=true; do
  if run_verifier "$setting"; then echo "Accepted invalid provenance: $setting" >&2; exit 1; fi
done
if run_verifier ARTIFACT_ZIP="$test_root/mismatch.zip"; then echo 'Accepted wrong commit' >&2; exit 1; fi
echo 'Maven publication provenance tests passed'
