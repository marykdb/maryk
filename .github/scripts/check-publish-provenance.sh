#!/usr/bin/env bash
set -euo pipefail

publish_workflow=.github/workflows/publish.yml
release_workflow=.github/workflows/release-installers.yml
app_build=app/build.gradle.kts
build_verifier=.github/scripts/verify-build-provenance.sh

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

check_required "$build_verifier" \
  'git merge-base --is-ancestor "$actual_sha" origin/main' \
  'actions/workflows/build.yml/runs?branch=main&event=push&status=completed&head_sha=$actual_sha' \
  'select(.conclusion == "success")' \
  ': "${GH_TOKEN:?GH_TOKEN must be set}"' \
  'MARYK_BUILD_RUN_ID'

check_required "$publish_workflow" \
  'github.event.workflow_run.head_sha' \
  'ref: ${{ env.EXPECTED_REF }}' \
  'actions: read' \
  'GH_TOKEN: ${{ github.token }}' \
  'bash .github/scripts/verify-build-provenance.sh' \
  'build_run_id=$MARYK_BUILD_RUN_ID'

check_required "$release_workflow" \
  'types: [published]' \
  'description: Existing release tag to build and attach' \
  'ref: ${{ env.RELEASE_TAG }}' \
  'actions: read' \
  'is_draft=$(gh release view "$TAG" --json isDraft --jq '\''.isDraft'\'')' \
  'if [[ "$is_draft" != "false" ]]; then' \
  'EXPECTED_SHA="$ACTUAL_SHA" bash .github/scripts/verify-build-provenance.sh' \
  ':app:verifyDistributionVersion' \
  '-PreleaseTag="$RELEASE_TAG"' \
  'Smoke test packaged macOS app' \
  'hdiutil attach "$dmg"' \
  'Smoke test packaged Linux app' \
  'sudo dpkg --install "$deb"' \
  'Smoke test packaged Windows app' \
  'Start-Process msiexec.exe' \
  'actions/download-artifact@v4' \
  "! -name 'SHA256SUMS'" \
  'Duplicate release asset basename:'

check_required "$app_build" 'check(tag == releaseVersion)'

if grep -Fq -- "select(.isDraft" "$release_workflow"; then
  echo "Manual installer releases must assert the explicit false draft value" >&2
  exit 1
fi

if grep -Fq -- 'check(tag == "v$releaseVersion")' "$app_build"; then
  echo "Installer release verification must use bare version tags" >&2
  exit 1
fi

if grep -Fq 'workflow_run:' "$release_workflow"; then
  echo "Installer releases must not run for every successful build" >&2
  exit 1
fi

echo "Publish and release-installer workflow structures are valid"
