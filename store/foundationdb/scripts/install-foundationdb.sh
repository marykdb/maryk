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
#       - Windows: print hint to use the PowerShell script in this repo.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"
BIN_DIR="$ROOT_DIR/store/foundationdb/bin"
LIB_DIR="$BIN_DIR/lib"

FDB_VERSION_DEFAULT="7.3.69"
FDB_VERSION="${FDB_VERSION:-$FDB_VERSION_DEFAULT}"

if [[ "${1:-}" == "--version" && -n "${2:-}" ]]; then
  FDB_VERSION="$2"
fi

: "${VERBOSE:=0}"

mkdir -p "$BIN_DIR" "$LIB_DIR"

log() { echo "[install-foundationdb] $*"; }
warn() { echo "[install-foundationdb][WARN] $*" >&2; }
err() { echo "[install-foundationdb][ERROR] $*" >&2; exit 1; }

debug() { [[ "${VERBOSE:-0}" == "1" ]] && echo "[install-foundationdb][DEBUG] $*"; }
safe_copy() {
  # Usage: safe_copy <src> <dst_dir>
  local src
  local dst_dir
  local dst
  src="$1"
  dst_dir="$2"
  dst="$dst_dir/$(basename -- "$src")"
  if [[ -e "$dst" ]] && [[ "$src" -ef "$dst" ]]; then
    debug "Skip copy: $dst is already the same file as $src"
    return 0
  fi
  cp -f "$src" "$dst_dir/" 2>/dev/null || true
}

have() { command -v "$1" >/dev/null 2>&1; }

macos_pkg_arch() {
  case "$(uname -m)" in
    arm64|aarch64) echo "arm64" ;;
    x86_64|amd64)  echo "x86_64" ;;
    *) echo "arm64" ;; # default to arm64 on modern runners
  esac
}

install_macos() {
  local tmp
  tmp="$(mktemp -d)"
  # shellcheck disable=SC2064 # Expand now to capture the literal tmp path for RETURN trap
  trap "rm -rf -- '$tmp'" RETURN

  local arch
  arch="$(macos_pkg_arch)"
  local pkg="FoundationDB-${FDB_VERSION}_${arch}.pkg"
  local url="https://github.com/apple/foundationdb/releases/download/${FDB_VERSION}/${pkg}"

  log "Downloading $pkg from FoundationDB releases"
  curl -fsSL --retry 5 --retry-delay 2 --retry-all-errors "$url" -o "$tmp/$pkg"

  # Expand meta-pkg
  pkgutil --expand-full "$tmp/$pkg" "$tmp/expanded"
  mkdir -p "$tmp/root"

  # Copy directory Payloads into $tmp/root (FoundationDB uses directory Payloads on macOS)
  while IFS= read -r dir; do
    log "Copying directory payload: $dir -> $tmp/root"
    if command -v rsync >/dev/null 2>&1; then
      rsync -a "$dir"/ "$tmp/root"/ || true
    else
      (cd "$dir" && tar -cf - .) | (cd "$tmp/root" && tar -xf -) || cp -R "$dir"/. "$tmp/root"/ || true
    fi
  done < <( (find "$tmp/expanded" -type d -name Payload 2>/dev/null) || true )

  # Copy binaries we know exist in the pkg layout
  if [[ -f "$tmp/root/usr/local/libexec/fdbserver" ]]; then
    safe_copy "$tmp/root/usr/local/libexec/fdbserver" "$BIN_DIR"
    chmod +x "$BIN_DIR/fdbserver" 2>/dev/null || true
    log "Installed fdbserver to $BIN_DIR"
  else
    err "Expected fdbserver not found under usr/local/libexec in expanded package."
  fi

  if [[ -f "$tmp/root/usr/local/bin/fdbcli" ]]; then
    safe_copy "$tmp/root/usr/local/bin/fdbcli" "$BIN_DIR"
    chmod +x "$BIN_DIR/fdbcli" 2>/dev/null || true
    log "Installed fdbcli to $BIN_DIR"
  else
    warn "fdbcli not found under usr/local/bin in expanded package (continuing)."
  fi

  # Copy client library
  shopt -s nullglob
  for lib in "$tmp/root"/usr/local/lib/libfdb_c.*; do
    safe_copy "$lib" "$LIB_DIR"
  done
  shopt -u nullglob

  if [[ "${VERBOSE:-0}" == "1" ]]; then
    log "Contents of $BIN_DIR:"
    ls -l "$BIN_DIR" || true
    log "Contents of $LIB_DIR:"
    ls -l "$LIB_DIR" || true
  fi

  log "FoundationDB installation complete (macOS minimal path)"
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
  # shellcheck disable=SC2064 # Expand now to capture the literal tmp path for RETURN trap
  trap "rm -rf -- '$tmp'" RETURN

  local base="https://github.com/apple/foundationdb/releases/download/${FDB_VERSION}"
  local clients_pkg="foundationdb-clients_${FDB_VERSION}-1_${deb_arch}.deb"
  local server_pkg="foundationdb-server_${FDB_VERSION}-1_${deb_arch}.deb"

  log "Downloading $clients_pkg and $server_pkg"
  curl -fsSL "$base/$clients_pkg" -o "$tmp/$clients_pkg"
  curl -fsSL "$base/$server_pkg" -o "$tmp/$server_pkg"

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
  elif have dnf && have sudo; then
    warn "Attempting dnf install (may require sudo password)"
    sudo dnf install -y foundationdb || true
  else
    log "Falling back to downloading .deb packages from GitHub releases"
    install_linux_from_deb || warn "Deb extraction method failed"
  fi
}

main() {
  debug "System: $(uname -a)"
  debug "Arch: $(uname -m)"
  debug "OS: $(uname -s)"
  debug "PATH: $PATH"
  debug "BIN_DIR: $BIN_DIR"
  debug "LIB_DIR: $LIB_DIR"

  if [[ -x "$BIN_DIR/fdbserver" ]]; then
    log "fdbserver already present in $BIN_DIR"
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

  if [[ "$(uname -s)" == "Darwin" ]]; then
    if [[ ! -x "$BIN_DIR/fdbserver" ]]; then
      err "fdbserver not installed to $BIN_DIR on macOS."
    fi
  fi

  if [[ ! -x "$BIN_DIR/fdbserver" ]]; then
    warn "fdbserver still missing from $BIN_DIR; dumping diagnostics"
    command -v fdbserver || true
    err "fdbserver not installed. Please install FoundationDB and ensure fdbserver is available."
  fi

  # Inventory what we ended up with
  if [[ "${VERBOSE:-0}" == "1" ]]; then
    log "Contents of $BIN_DIR:"
    ls -l "$BIN_DIR" || true
    log "Contents of $LIB_DIR:"
    ls -l "$LIB_DIR" || true
  fi

  log "FoundationDB installation complete"
}

main "$@"
