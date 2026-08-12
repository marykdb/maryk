#!/usr/bin/env bash
set -euo pipefail

repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
checker="$repository_root/.github/scripts/check-action-pins.sh"
test_root=$(mktemp -d)
trap 'rm -rf -- "$test_root"' EXIT

mkdir -p "$test_root/.github/actions/local"

printf '%s\n' \
  'runs:' \
  '  using: composite' \
  '  steps:' \
  '    - uses: ./local' \
  '    - uses: actions/checkout@fbc6f3992d24b796d5a048ff273f7fcc4a7b6c09 # v5' \
  > "$test_root/.github/actions/local/action.yml"

bash "$checker" "$test_root/.github"

printf '%s\n' \
  'steps:' \
  '  - uses: actions/checkout@v5' \
  > "$test_root/.github/workflow.yml"

if bash "$checker" "$test_root/.github"; then
  echo 'Action pin checker accepted a mutable third-party action' >&2
  exit 1
fi

echo 'Action pin checker tests passed'
