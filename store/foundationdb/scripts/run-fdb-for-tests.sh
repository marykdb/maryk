#!/usr/bin/env bash
set -euo pipefail

# Start a local fdbserver for tests, ensuring it is installed.
# - Respects FDB_VERSION env var (optional)
# - Writes PID file to build/testdatastore/fdbserver.pid
# - Ensures cluster is configured (single memory) if uninitialized.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"
BIN_DIR="$ROOT_DIR/store/foundationdb/bin"
LIB_DIR="$BIN_DIR/lib"

CLUSTER_FILE="$ROOT_DIR/store/foundationdb/fdb.cluster"
DATA_DIR="$ROOT_DIR/build/testdatastore/data"
LOG_DIR="$ROOT_DIR/build/testdatastore/logs"
PID_FILE="$ROOT_DIR/build/testdatastore/fdbserver.pid"

FDB_LISTEN="${FDB_LISTEN:-127.0.0.1:4500}"

mkdir -p "$DATA_DIR" "$LOG_DIR"

if { [[ ! -x "$BIN_DIR/fdbserver" ]] && ! command -v fdbserver >/dev/null 2>&1; } ||
   { [[ ! -x "$BIN_DIR/fdbcli" ]] && ! command -v fdbcli >/dev/null 2>&1; }; then
  bash "$SCRIPT_DIR/install-foundationdb.sh"
fi

resolve_tool() {
  local name="$1"
  if [[ -x "$BIN_DIR/$name" ]]; then
    printf '%s\n' "$BIN_DIR/$name"
  else
    command -v "$name" 2>/dev/null || true
  fi
}

FDBSERVER_BIN="$(resolve_tool fdbserver)"
FDBCLI_BIN="$(resolve_tool fdbcli)"
if [[ -z "$FDBSERVER_BIN" ]]; then
  echo "fdbserver is unavailable both in $BIN_DIR and on PATH" >&2
  exit 1
fi
if [[ -z "$FDBCLI_BIN" ]]; then
  echo "fdbcli is required for authoritative FoundationDB readiness checks" >&2
  exit 1
fi

export PATH="$BIN_DIR:$PATH"
# Set library paths for JVM tests to pick up libfdb_c; Gradle also sets java.library.path.
case "$(uname -s)" in
  Darwin) export DYLD_LIBRARY_PATH="$LIB_DIR:${DYLD_LIBRARY_PATH:-}" ;;
  Linux) export LD_LIBRARY_PATH="$LIB_DIR:${LD_LIBRARY_PATH:-}" ;;
esac

validate_cluster_address() {
  if [[ ! -f "$CLUSTER_FILE" ]]; then
    printf 'test@%s\n' "$FDB_LISTEN" > "$CLUSTER_FILE"
    return
  fi

  local configured_address
  configured_address="$(sed -n '1{s/^[^@]*@//;p;}' "$CLUSTER_FILE")"
  if [[ -z "$configured_address" || "$configured_address" != "$FDB_LISTEN" ]]; then
    echo "FDB_LISTEN=$FDB_LISTEN does not match $CLUSTER_FILE ($configured_address). Update the cluster file or use its configured address." >&2
    exit 1
  fi
}

have() { command -v "$1" >/dev/null 2>&1; }

server_started=false

pid_is_managed_server() {
  local pid="$1"
  local command
  command="$(ps -p "$pid" -o command= 2>/dev/null || true)"
  [[ "$command" == *"$FDBSERVER_BIN"* ]] &&
    [[ "$command" == *"--cluster-file $CLUSTER_FILE"* ]] &&
    [[ "$command" == *"--datadir $DATA_DIR"* ]]
}

cleanup_failed_start() {
  if [[ "$server_started" != "true" ]] || [[ ! -f "$PID_FILE" ]]; then
    return
  fi
  local pid
  pid="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null && pid_is_managed_server "$pid"; then
    kill "$pid" 2>/dev/null || true
  fi
  rm -f "$PID_FILE"
}

trap cleanup_failed_start EXIT

start_server() {
  validate_cluster_address
  if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE" 2>/dev/null)" 2>/dev/null; then
    local existing_pid
    existing_pid="$(cat "$PID_FILE")"
    if pid_is_managed_server "$existing_pid"; then
      server_started=false
      return 0
    fi
    echo "Ignoring unrelated process in $PID_FILE (PID $existing_pid)" >&2
  fi
  rm -f "$PID_FILE"

  # Ensure log file exists before redirect
  touch "$LOG_DIR/fdbserver.out" || true

  set -x
  "$FDBSERVER_BIN" \
    --cluster-file "$CLUSTER_FILE" \
    --listen-address "$FDB_LISTEN" \
    --public-address "$FDB_LISTEN" \
    --datadir "$DATA_DIR" \
    --logdir "$LOG_DIR" \
    --locality-machineid maryk-tests \
    --locality-zoneid local-test \
    --knob_max_outstanding=400 \
    --knob_desired_teams_per_server=1 \
    --knob_desired_machine_teams_per_machine=1 \
    --knob_dd_storage_team_required=1 \
    --knob_disable_posix_kernel_aio=1 \
    --knob_min_available_space_ratio=0.001 \
    --memory 512MiB \
    --storage-memory 256MiB \
    --cache-memory 64MiB \
    >"$LOG_DIR/fdbserver.out" 2>&1 &
  set +x
  echo $! > "$PID_FILE"
  server_started=true
}

wait_ready() {
  export FDB_CLUSTER_FILE="$CLUSTER_FILE"
  # Wait until the cluster reports as available.
  local attempts="${FDB_READY_ATTEMPTS:-180}"
  local delay_seconds="${FDB_READY_DELAY_SECONDS:-1}"
  local attempt
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    if "$FDBCLI_BIN" --exec "status minimal" 2>/dev/null | grep -qi "The database is available"; then
      return 0
    fi
    sleep "$delay_seconds"
  done
  return 1
}

configure_if_needed() {
  export FDB_CLUSTER_FILE="$CLUSTER_FILE"
  # Configure a single-memory test database; ignore errors if it already exists.
  local configure_output
  configure_output="$("$FDBCLI_BIN" --timeout 15 --exec "configure new single memory logs=1 commit_proxies=1 grv_proxies=1" 2>&1 || true)"
  if grep -qi "Database created" <<<"$configure_output"; then
    echo "$configure_output"
  elif grep -qi "Database already exists" <<<"$configure_output"; then
    echo "Reusing existing FoundationDB configuration"
  elif [[ -n "$configure_output" ]]; then
    echo "$configure_output" >&2
  fi
}

start_server

PID="$(cat "$PID_FILE")"
if [[ "$server_started" == "true" ]]; then
  echo "fdbserver started with PID $PID"
else
  echo "Reusing existing fdbserver (PID $PID)"
fi

# Fail fast if server exited immediately
sleep "${FDB_STARTUP_PROBE_DELAY_SECONDS:-0.5}"
if ! kill -0 "$(cat "$PID_FILE" 2>/dev/null)" 2>/dev/null; then
  echo "fdbserver exited immediately; see $LOG_DIR/fdbserver.out" >&2
  exit 1
fi

# Ensure configuration happens before waiting for availability
configure_if_needed || true

if ! wait_ready; then
  echo "fdbserver did not become ready in time" >&2
  exit 1
fi

trap - EXIT
echo "FoundationDB is ready for tests (PID $(cat "$PID_FILE"))"
