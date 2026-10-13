#!/usr/bin/env bash
set -euo pipefail

case "${MAVEN_PUBLISHED_LOCALLY:-false}" in
  false)
    bash "$(dirname "${BASH_SOURCE[0]}")/verify-publish-provenance.sh"
    exit 0
    ;;
  true)
    if [[ "${GITHUB_EVENT_NAME:-}" != workflow_dispatch ]]; then
      echo 'Local Maven publication is only allowed for an explicit manual installer release' >&2
      exit 1
    fi
    ;;
  *)
    echo 'MAVEN_PUBLISHED_LOCALLY must be true or false' >&2
    exit 1
    ;;
esac

: "${RELEASE_TAG:?RELEASE_TAG must be set}"
: "${SOURCE_SHA:?SOURCE_SHA must be set}"
if [[ ! "$RELEASE_TAG" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo 'Local Maven publication requires a final numeric release tag' >&2
  exit 1
fi

# Check actual core metadata and the JVM binary, rather than the search index.
# Availability is evidence of publication, not evidence of its source commit.
central_url=https://repo.maven.apache.org/maven2/io/maryk
for artifact in maryk-core maryk-core-jvm; do
  for extension in pom module; do
    url="$central_url/$artifact/$RELEASE_TAG/$artifact-$RELEASE_TAG.$extension"
    curl --fail --silent --show-error --location --retry 3 --connect-timeout 10 --max-time 60 --output /dev/null "$url"
  done
done
url="$central_url/maryk-core-jvm/$RELEASE_TAG/maryk-core-jvm-$RELEASE_TAG.jar"
curl --fail --silent --show-error --location --retry 3 --connect-timeout 10 --max-time 60 --output /dev/null "$url"

message="Verified Maven Central core artifacts for $RELEASE_TAG. Local publication selected for installer source $SOURCE_SHA; Maven artifact source provenance is unverified."
echo "$message"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  printf '%s\n' "$message" >> "$GITHUB_STEP_SUMMARY"
fi
