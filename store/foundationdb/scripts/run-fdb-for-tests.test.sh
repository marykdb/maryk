#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LAUNCHER="$SCRIPT_DIR/run-fdb-for-tests.sh"
TEST_ROOT="$(mktemp -d)"
SERVER_PIDS=()

cleanup() {
  for pid in "${SERVER_PIDS[@]:-}"; do
    kill "$pid" 2>/dev/null || true
  done
  rm -rf -- "$TEST_ROOT"
}
trap cleanup EXIT

new_fixture() {
  local name="$1"
  local fixture="$TEST_ROOT/$name"
  mkdir -p "$fixture/repo/store/foundationdb/scripts" "$fixture/bin"
  cp "$LAUNCHER" "$fixture/repo/store/foundationdb/scripts/run-fdb-for-tests.sh"
  cat > "$fixture/repo/store/foundationdb/scripts/install-foundationdb.sh" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
  chmod +x "$fixture/repo/store/foundationdb/scripts/install-foundationdb.sh"
  printf '%s\n' "$fixture"
}

write_server() {
  local bin_dir="$1"
  cat > "$bin_dir/fdbserver" <<'EOF'
#!/usr/bin/env bash
trap 'exit 0' TERM INT
while true; do sleep 1; done
EOF
  chmod +x "$bin_dir/fdbserver"
}

path_only="$(new_fixture path-only)"
write_server "$path_only/bin"
cat > "$path_only/bin/fdbcli" <<'EOF'
#!/usr/bin/env bash
if [[ "$*" == *"status minimal"* ]]; then
  echo "The database is available"
else
  echo "Database created"
fi
EOF
chmod +x "$path_only/bin/fdbcli"
PATH="$path_only/bin:/usr/bin:/bin" \
  FDB_READY_ATTEMPTS=1 FDB_READY_DELAY_SECONDS=0 FDB_STARTUP_PROBE_DELAY_SECONDS=0 \
  bash "$path_only/repo/store/foundationdb/scripts/run-fdb-for-tests.sh" >/dev/null
SERVER_PIDS+=("$(cat "$path_only/repo/build/testdatastore/fdbserver.pid")")

missing_cli="$(new_fixture missing-cli)"
write_server "$missing_cli/bin"
if output="$(PATH="$missing_cli/bin:/usr/bin:/bin" \
  FDB_READY_ATTEMPTS=1 FDB_READY_DELAY_SECONDS=0 FDB_STARTUP_PROBE_DELAY_SECONDS=0 \
  bash "$missing_cli/repo/store/foundationdb/scripts/run-fdb-for-tests.sh" 2>&1)"; then
  echo "Expected missing fdbcli to fail readiness, but launcher succeeded." >&2
  exit 1
fi
grep -Fqi 'fdbcli' <<<"$output" || {
  echo "Expected missing fdbcli failure, got:" >&2
  echo "$output" >&2
  exit 1
}

unready="$(new_fixture unready-cli)"
write_server "$unready/bin"
cat > "$unready/bin/fdbcli" <<'EOF'
#!/usr/bin/env bash
if [[ "$*" == *"status minimal"* ]]; then
  echo "The database is unavailable"
else
  echo "Database already exists"
fi
EOF
chmod +x "$unready/bin/fdbcli"
if output="$(PATH="$unready/bin:/usr/bin:/bin" \
  FDB_READY_ATTEMPTS=1 FDB_READY_DELAY_SECONDS=0 FDB_STARTUP_PROBE_DELAY_SECONDS=0 \
  bash "$unready/repo/store/foundationdb/scripts/run-fdb-for-tests.sh" 2>&1)"; then
  echo "Expected unavailable fdbcli status to fail readiness, but launcher succeeded." >&2
  exit 1
fi
SERVER_PIDS+=("$(cat "$unready/repo/build/testdatastore/fdbserver.pid")")
grep -Fq 'did not become ready' <<<"$output" || {
  echo "Expected authoritative readiness failure, got:" >&2
  echo "$output" >&2
  exit 1
}
