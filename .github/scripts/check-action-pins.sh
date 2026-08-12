#!/usr/bin/env bash
set -euo pipefail

root=${1:-.github}
invalid=0

while IFS= read -r -d '' workflow; do
  while IFS= read -r reference; do
    reference=${reference%%#*}
    reference=$(echo "$reference" | xargs)
    [[ "$reference" == ./* ]] && continue

    if [[ ! "$reference" =~ @[0-9a-f]{40}$ ]]; then
      echo "Mutable or unpinned action in $workflow: $reference" >&2
      invalid=1
    fi
  done < <(sed -nE 's/^[[:space:]]*(-[[:space:]]+)?uses:[[:space:]]*([^[:space:]#]+).*/\2/p' "$workflow")
done < <(find "$root" -type f \( -name '*.yml' -o -name '*.yaml' \) -print0)

exit "$invalid"
