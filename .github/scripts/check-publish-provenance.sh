#!/usr/bin/env bash
set -euo pipefail

publish_workflow=.github/workflows/publish.yml
release_workflow=.github/workflows/release-installers.yml

check_required() {
  local workflow=$1
  shift
  for fragment in "$@"; do
    if ! grep -Fq -- "$fragment" "$workflow"; then
      echo "$workflow is missing required fragment: $fragment" >&2
      exit 1
    fi
  done
}

check_required "$publish_workflow" 'github.event.workflow_run.head_sha' 'ref: ${{ env.EXPECTED_REF }}' 'git rev-parse "${EXPECTED_REF}^{commit}"' 'publish-provenance.txt'

check_required "$release_workflow" 'types: [published]' 'description: Existing release tag to build and attach' 'ref: ${{ env.RELEASE_TAG }}' ':app:verifyDistributionVersion' '-PreleaseTag="$RELEASE_TAG"' 'actions/download-artifact@v4' "! -name 'SHA256SUMS'" 'Duplicate release asset basename:'

if grep -Fq 'workflow_run:' "$release_workflow"; then
  echo "Installer releases must not run for every successful build" >&2
  exit 1
fi

echo "Publish and release-installer workflow structures are valid"
