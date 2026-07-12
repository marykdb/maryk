#!/usr/bin/env bash
set -euo pipefail

workflow=.github/workflows/publish.yml

required_fragments=(
  'github.event.workflow_run.head_sha'
  'github.event_name == '\''workflow_dispatch'\'''
  'ref: ${{ env.EXPECTED_REF }}'
  'git rev-parse "${EXPECTED_REF}^{commit}"'
  'git rev-parse HEAD'
  'publish-provenance.txt'
)

for fragment in "${required_fragments[@]}"; do
  if ! grep -Fq "$fragment" "$workflow"; then
    echo "Publish workflow is missing provenance requirement: $fragment" >&2
    exit 1
  fi
done

if ! grep -Fq 'fetch-depth: 1' "$workflow"; then
  echo "Publish checkout must fetch only the selected revision" >&2
  exit 1
fi

echo "Publish workflow provenance structure is valid"
