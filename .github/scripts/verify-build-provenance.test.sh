#!/usr/bin/env bash
set -euo pipefail

repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
verifier="$repository_root/.github/scripts/verify-build-provenance.sh"
test_root=$(mktemp -d)
trap 'rm -rf -- "$test_root"' EXIT

mkdir "$test_root/bin"
printf '%s\n' '#!/usr/bin/env bash' \
  'case "$1" in' \
  '  rev-parse) echo "${FAKE_HEAD_SHA:?}" ;;' \
  '  fetch) exit 0 ;;' \
  '  merge-base) exit "${FAKE_MERGE_STATUS:-0}" ;;' \
  '  *) echo "unexpected git command: $*" >&2; exit 2 ;;' \
  'esac' > "$test_root/bin/git"
printf '%s\n' '#!/usr/bin/env bash' \
  'if [[ "${FAKE_GH_STATUS:-0}" != 0 ]]; then exit "$FAKE_GH_STATUS"; fi' \
  'printf "%s\\n" "${FAKE_GH_RESULT:-}"' > "$test_root/bin/gh"
chmod +x "$test_root/bin/git" "$test_root/bin/gh"

run_verifier() {
  env PATH="$test_root/bin:$PATH" \
    EXPECTED_SHA=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa \
    FAKE_HEAD_SHA=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa \
    GITHUB_REPOSITORY=marykdb/maryk \
    "$@" \
    bash "$verifier"
}

if run_verifier; then
  echo 'Verifier accepted a missing GH_TOKEN' >&2
  exit 1
fi

if run_verifier GH_TOKEN=token FAKE_GH_RESULT=''; then
  echo 'Verifier accepted missing successful Build provenance' >&2
  exit 1
fi

if run_verifier GH_TOKEN=token FAKE_GH_STATUS=1; then
  echo 'Verifier accepted a GitHub API failure' >&2
  exit 1
fi

environment_file="$test_root/environment"
run_verifier GH_TOKEN=token FAKE_GH_RESULT=123456 GITHUB_ENV="$environment_file"
grep -Fx 'MARYK_BUILD_SHA=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' "$environment_file"
grep -Fx 'MARYK_BUILD_RUN_ID=123456' "$environment_file"

echo 'Build provenance verifier tests passed'
