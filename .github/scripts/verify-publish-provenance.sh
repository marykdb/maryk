#!/usr/bin/env bash
set -euo pipefail

: "${GH_TOKEN:?GH_TOKEN must be set}"
: "${SOURCE_SHA:?SOURCE_SHA must be set}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY must be set}"

publication_name="publish-provenance-$SOURCE_SHA"
publication_verified=false
for attempt in {1..6}; do
  artifact_lines=$(gh api \
    "/repos/$GITHUB_REPOSITORY/actions/artifacts?name=$publication_name&per_page=100" \
    --jq '.artifacts[] | select(.expired == false) | [.id, .workflow_run.id] | @tsv')
  while IFS=$'\t' read -r artifact_id run_id; do
    [[ -n "$artifact_id" && -n "$run_id" ]] || continue
    if ! run_details=$(gh api \
      "/repos/$GITHUB_REPOSITORY/actions/runs/$run_id" \
      --jq '[.conclusion, .path] | @tsv'); then
      continue
    fi
    IFS=$'\t' read -r run_conclusion run_path <<< "$run_details"
    if [[ "$run_conclusion" != "success" || ( "$run_path" != .github/workflows/publish.yml && "$run_path" != .github/workflows/publish.yml@* ) ]]; then
      continue
    fi

    artifact_zip="$RUNNER_TEMP/publish-provenance-$artifact_id.zip"
    if ! gh api \
      --header "Accept: application/vnd.github+json" \
      "/repos/$GITHUB_REPOSITORY/actions/artifacts/$artifact_id/zip" > "$artifact_zip"; then
      continue
    fi
    if unzip -p "$artifact_zip" publish-provenance.txt | grep -Fxq "commit_sha=$SOURCE_SHA"; then
      publication_verified=true
      break
    fi
  done <<< "$artifact_lines"

  if [[ "$publication_verified" == "true" ]]; then
    break
  fi
  if [[ "$attempt" -lt 6 ]]; then
    echo "Waiting for successful Maven publication for $SOURCE_SHA (attempt $attempt/6)" >&2
    sleep 10
  fi
done
if [[ "$publication_verified" != "true" ]]; then
  echo "Refusing installer release for $SOURCE_SHA: no matching successful Maven publication" >&2
  exit 1
fi
