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

FDB_LISTEN="127.0.0.1:4500"

mkdir -p "$DATA_DIR" "$LOG_DIR"

if [[ ! -x "$BIN_DIR/fdbserver" ]] && ! command -v fdbserver >/dev/null 2>&1; then
  bash "$SCRIPT_DIR/install-foundationdb.sh"
fi

export PATH="$BIN_DIR:$PATH"
# Set library paths for JVM tests to pick up libfdb_c; Gradle also sets java.library.path.
case "$(uname -s)" in
  Darwin) export DYLD_LIBRARY_PATH="$LIB_DIR:${DYLD_LIBRARY_PATH:-}" ;;
  Linux) export LD_LIBRARY_PATH="$LIB_DIR:${LD_LIBRARY_PATH:-}" ;;
esac

have() { command -v "$1" >/dev/null 2>&1; }

start_server() {
  if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE" 2>/dev/null)" 2>/dev/null; then
    echo "fdbserver already running with PID $(cat "$PID_FILE")"
    return 0
  fi
  rm -f "$PID_FILE"

  # Create a minimal cluster file if missing; fdbserver will update it.
  if [[ ! -f "$CLUSTER_FILE" ]]; then
    echo "test@$FDB_LISTEN" > "$CLUSTER_FILE"
  fi

  set -x
  "$BIN_DIR/fdbserver" \
    --cluster-file "$CLUSTER_FILE" \
    --listen-address "$FDB_LISTEN" \
    --public-address "$FDB_LISTEN" \
    --datadir "$DATA_DIR" \
    --logdir "$LOG_DIR" \
    --locality-machineid maryk-tests \
    --locality-zoneid local-test \
    --knob_max_outstanding=400 \
    >"$LOG_DIR/fdbserver.out" 2>&1 &
  set +x
  echo $! > "$PID_FILE"
}

wait_ready() {
  export FDB_CLUSTER_FILE="$CLUSTER_FILE"
  if [[ ! -x "$BIN_DIR/fdbcli" ]]; then
    # Best effort: give server a short time to be ready
    sleep 2
    return 0
  fi
  for i in {1..60}; do
    if "$BIN_DIR/fdbcli" --exec "status minimal" >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.5
  done
  return 1
}

configure_if_needed() {
  export FDB_CLUSTER_FILE="$CLUSTER_FILE"
  # If database is not created yet, configure a single memory engine (fast for tests)
  if [[ -x "$BIN_DIR/fdbcli" ]]; then
    if ! "$BIN_DIR/fdbcli" --exec "status minimal" 2>&1 | grep -qi "configuration"; then
      "$BIN_DIR/fdbcli" --exec "configure new single memory" || true
    fi
  fi
}

start_server
if ! wait_ready; then
  echo "fdbserver did not become ready in time" >&2
  exit 1
fi
configure_if_needed || true
echo "FoundationDB is ready for tests (PID $(cat "$PID_FILE"))"
