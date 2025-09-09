#!/usr/bin/env bash
set -euo pipefail

# Install or link FoundationDB locally for the current platform.
# - Installs/symlinks into: store/foundationdb/bin
# - Configurable version via FDB_VERSION env var or --version flag (e.g. 7.3.69)
# - Strategy:
#   * If fdbserver already in bin, skip.
#   * Else if fdbserver found on PATH, symlink into bin (and try to locate/copy libfdb_c.*).
#   * Else attempt platform-native install:
#       - macOS: download .pkg from GitHub Releases and extract.
#       - Linux: download .deb packages from GitHub Releases and extract.
#       - Windows: print hint to use the PowerShell installer in this repo.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"
BIN_DIR="$ROOT_DIR/store/foundationdb/bin"
LIB_DIR="$BIN_DIR/lib"

FDB_VERSION_DEFAULT="7.3.69"
FDB_VERSION="${FDB_VERSION:-$FDB_VERSION_DEFAULT}"

if [[ "${1:-}" == "--version" && -n "${2:-}" ]]; then
  FDB_VERSION="$2"
fi

mkdir -p "$BIN_DIR" "$LIB_DIR"

log() { echo "[install-foundationdb] $*"; }
warn() { echo "[install-foundationdb][WARN] $*" >&2; }
err() { echo "[install-foundationdb][ERROR] $*" >&2; exit 1; }

have() { command -v "$1" >/dev/null 2>&1; }

link_from_path_if_present() {
  if have fdbserver; then
    local fs
    fs="$(command -v fdbserver)"
    ln -sf "$fs" "$BIN_DIR/fdbserver"
    log "Linked fdbserver from PATH: $fs"
  else
    return 1
  fi

  if have fdbcli; then
    local fc
    fc="$(command -v fdbcli)"
    ln -sf "$fc" "$BIN_DIR/fdbcli"
    log "Linked fdbcli from PATH: $fc"
  else
    warn "fdbcli not found on PATH; some setup steps may fail"
  fi

  # Try to find libfdb_c.* on common locations and link/copy into LIB_DIR
  local candidates=(
    /usr/local/lib/libfdb_c.*
    /opt/homebrew/lib/libfdb_c.*
    /usr/lib/libfdb_c.*
    /usr/lib64/libfdb_c.*
  )
  local found_lib=0
  for c in "${candidates[@]}"; do
    for f in $c; do
      if [[ -e "$f" ]]; then
        cp -f "$f" "$LIB_DIR/" || true
        found_lib=1
      fi
    done
  done
  if [[ "$found_lib" -eq 1 ]]; then
    log "Copied libfdb_c.* into $LIB_DIR"
  else
    warn "libfdb_c.* not found; Java client may not load without it"
  fi
}

install_macos() {
  local tmp
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT

  local pkg
  pkg="FoundationDB-${FDB_VERSION}.pkg"
  local url
  url="https://github.com/apple/foundationdb/releases/download/${FDB_VERSION}/${pkg}"

  log "Downloading $pkg from FoundationDB releases"
  curl -fL "$url" -o "$tmp/$pkg"

  pkgutil --expand "$tmp/$pkg" "$tmp/pkg"
  mkdir -p "$tmp/root"
  (
    cd "$tmp/root"
    gzip -dc "$tmp/pkg/Payload" | cpio -id >/dev/null 2>&1
  )

  if [[ -x "$tmp/root/usr/local/bin/fdbserver" ]]; then
    cp -f "$tmp/root/usr/local/bin/fdbserver" "$BIN_DIR/"
    chmod +x "$BIN_DIR/fdbserver"
  fi
  if [[ -x "$tmp/root/usr/local/bin/fdbcli" ]]; then
    cp -f "$tmp/root/usr/local/bin/fdbcli" "$BIN_DIR/"
    chmod +x "$BIN_DIR/fdbcli"
  fi

  shopt -s nullglob
  for lib in "$tmp/root"/usr/local/lib/libfdb_c.*; do
    cp -f "$lib" "$LIB_DIR/"
  done
  shopt -u nullglob

  log "Installed FoundationDB binaries into $BIN_DIR"
}

install_linux_from_deb() {
  local arch
  arch="$(uname -m)"
  local deb_arch
  case "$arch" in
    x86_64|amd64) deb_arch="amd64";;
    aarch64|arm64) deb_arch="arm64";;
    *) err "Unsupported Linux arch: $arch";;
  esac

  local tmp
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT

  local base="https://github.com/apple/foundationdb/releases/download/${FDB_VERSION}"
  local clients_pkg="foundationdb-clients_${FDB_VERSION}-1_${deb_arch}.deb"
  local server_pkg="foundationdb-server_${FDB_VERSION}-1_${deb_arch}.deb"

  log "Downloading $clients_pkg and $server_pkg"
  curl -fL "$base/$clients_pkg" -o "$tmp/$clients_pkg"
  curl -fL "$base/$server_pkg" -o "$tmp/$server_pkg"

  extract_deb() {
    local deb="$1"
    mkdir -p "$tmp/extract"
    (cd "$tmp/extract" && ar x "$deb")
    local data
    data=$(ls "$tmp/extract"/data.*)
    mkdir -p "$tmp/root"
    if [[ "$data" == *.xz ]]; then
      tar -C "$tmp/root" -xJf "$data"
    else
      tar -C "$tmp/root" -xzf "$data"
    fi
  }

  extract_deb "$tmp/$clients_pkg"
  extract_deb "$tmp/$server_pkg"

  # Copy binaries and libs from extracted tree
  if [[ -x "$tmp/root/usr/bin/fdbserver" ]]; then
    cp -f "$tmp/root/usr/bin/fdbserver" "$BIN_DIR/"
    chmod +x "$BIN_DIR/fdbserver"
  fi
  if [[ -x "$tmp/root/usr/bin/fdbcli" ]]; then
    cp -f "$tmp/root/usr/bin/fdbcli" "$BIN_DIR/"
    chmod +x "$BIN_DIR/fdbcli"
  fi
  # Libraries may reside under /usr/lib or /usr/lib/x86_64-linux-gnu
  shopt -s nullglob
  for lib in "$tmp/root"/usr/lib*/libfdb_c.*; do
    cp -f "$lib" "$LIB_DIR/"
  done
  shopt -u nullglob

  log "Installed FoundationDB binaries into $BIN_DIR"
}

install_linux() {
  # Prefer using system packages if available and user has privileges
  if have apt-get && have sudo; then
    warn "Attempting apt install (may require sudo password)"
    sudo apt-get update || true
    sudo apt-get install -y foundationdb-clients foundationdb-server || true
    link_from_path_if_present || true
  elif have dnf && have sudo; then
    warn "Attempting dnf install (may require sudo password)"
    sudo dnf install -y foundationdb || true
    link_from_path_if_present || true
  else
    log "Falling back to downloading .deb packages from GitHub releases"
    install_linux_from_deb || warn "Deb extraction method failed"
  fi
}

main() {
  if [[ -x "$BIN_DIR/fdbserver" ]]; then
    log "fdbserver already present in $BIN_DIR"
    exit 0
  fi

  if link_from_path_if_present; then
    exit 0
  fi

  case "$(uname -s)" in
    Darwin) install_macos ;;
    Linux) install_linux ;;
    MINGW*|MSYS*|CYGWIN*)
      warn "Windows detected. Use PowerShell script: store/foundationdb/scripts/install-foundationdb.ps1"
      ;;
    *) err "Unsupported OS: $(uname -s)" ;;
  esac

  if [[ ! -x "$BIN_DIR/fdbserver" ]]; then
    err "fdbserver not installed. Please install FoundationDB and ensure fdbserver is available."
  fi

  log "FoundationDB installation complete"
}

main "$@"
