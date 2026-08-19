#!/usr/bin/env bash
set -euo pipefail

: "${EXPECTED_SHA:?EXPECTED_SHA must be set}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY must be set}"

expected_sha=$(git rev-parse "${EXPECTED_SHA}^{commit}")
actual_sha=$(git rev-parse HEAD)
if [[ "$actual_sha" != "$expected_sha" ]]; then
  echo "Checked out $actual_sha, expected $expected_sha" >&2
  exit 1
fi

git fetch --no-tags origin '+refs/heads/main:refs/remotes/origin/main'
if ! git merge-base --is-ancestor "$actual_sha" origin/main; then
  echo "Refusing release for $actual_sha: it is not reachable from main" >&2
  exit 1
fi

build_run_id=${BUILD_RUN_ID:-}
if [[ -z "$build_run_id" ]]; then
  : "${GH_TOKEN:?GH_TOKEN must be set}"
  for attempt in {1..6}; do
    build_run_id=$(gh api \
      "/repos/$GITHUB_REPOSITORY/actions/workflows/build.yml/runs?branch=main&event=push&status=completed&head_sha=$actual_sha&per_page=100" \
      --jq '[.workflow_runs[] | select(.conclusion == "success") | .id] | first // empty')
    if [[ -n "$build_run_id" ]]; then
      break
    fi

    if [[ "$attempt" -lt 6 ]]; then
      echo "Waiting for successful Build provenance for $actual_sha (attempt $attempt/6)" >&2
      sleep 10
    fi
  done
fi
if [[ -z "$build_run_id" ]]; then
  echo "Refusing release for $actual_sha: no successful main Build run for this exact SHA" >&2
  exit 1
fi

if [[ -n "${GITHUB_ENV:-}" ]]; then
  {
    echo "MARYK_BUILD_SHA=$actual_sha"
    echo "MARYK_BUILD_RUN_ID=$build_run_id"
  } >> "$GITHUB_ENV"
fi

echo "Verified main Build run $build_run_id for $actual_sha"
