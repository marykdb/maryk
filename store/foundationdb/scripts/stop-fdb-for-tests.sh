#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"
PID_FILE="$ROOT_DIR/build/testdatastore/fdbserver.pid"
DATA_DIR="$ROOT_DIR/build/testdatastore/data"
LOG_DIR="$ROOT_DIR/build/testdatastore/logs"

# Control cleanup behavior after stopping the server:
#   FDB_CLEAN_MODE=data  (default) removes only the data directory
#   FDB_CLEAN_MODE=all   removes data and logs
#   FDB_CLEAN_MODE=none  keeps both
FDB_CLEAN_MODE="${FDB_CLEAN_MODE:-data}"

if [[ -f "$PID_FILE" ]]; then
  PID="$(cat "$PID_FILE")"
  if kill -0 "$PID" 2>/dev/null; then
    kill "$PID" || true
    # Give it a moment to shut down
    for i in {1..20}; do
      if kill -0 "$PID" 2>/dev/null; then
        sleep 0.2
      else
        break
      fi
    done
  fi
  rm -f "$PID_FILE"
  echo "Stopped fdbserver (PID $PID)"
else
  echo "No PID file; fdbserver not running?"
fi

# Cleanup data/logs to remove the whole database content
case "$FDB_CLEAN_MODE" in
  data)
    if [[ -d "$DATA_DIR" ]]; then
      rm -rf "$DATA_DIR"
      echo "Removed data directory: $DATA_DIR"
    fi
    ;;
  all)
    if [[ -d "$DATA_DIR" ]]; then
      rm -rf "$DATA_DIR"
      echo "Removed data directory: $DATA_DIR"
    fi
    if [[ -d "$LOG_DIR" ]]; then
      rm -rf "$LOG_DIR"
      echo "Removed log directory: $LOG_DIR"
    fi
    ;;
  none)
    ;;
  *)
    echo "Unknown FDB_CLEAN_MODE='$FDB_CLEAN_MODE' (use data|all|none). Keeping files."
    ;;
esac
