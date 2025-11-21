#!/usr/bin/env bash
set -euo pipefail

# Install or link FoundationDB locally for the current platform.
# - Installs/symlinks into: store/foundationdb/bin
# - Configurable version via FDB_VERSION env var or --version flag (e.g. 7.3.71)
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

FDB_VERSION_DEFAULT="7.3.71"
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
checksum_cmd() {
  if command -v sha256sum >/dev/null 2>&1; then
    echo "sha256sum"
  elif command -v shasum >/dev/null 2>&1; then
    echo "shasum -a 256"
  else
    echo ""
  fi
}
compute_sha256() {
  local file="$1"
  local cmd
  cmd="$(checksum_cmd)"
  [[ -n "$cmd" ]] || err "No sha256 checksum tool found (install sha256sum or shasum)."
  $cmd "$file" | awk '{print $1}'
}
verify_checksum() {
  local file="$1"
  local checksum_url="$2"
  local checksum_file="$file.sha256"

  if ! curl -fsSL --retry 5 --retry-delay 2 --retry-all-errors "$checksum_url" -o "$checksum_file"; then
    warn "Checksum not available for $(basename "$file") at $checksum_url; skipping verification"
    return 0
  fi

  local expected
  expected=$(awk '{print $1}' "$checksum_file" | tr -d '\r')
  if [[ -z "$expected" ]]; then
    warn "Checksum file $checksum_url contained no hash; skipping verification"
    return 0
  fi

  local actual
  actual=$(compute_sha256 "$file")

  if [[ "$expected" != "$actual" ]]; then
    err "Checksum mismatch for $(basename "$file") (expected $expected, got $actual)"
  fi

  log "Checksum verified for $(basename "$file")"
}
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

copy_libs_from_dir() {
  local dir="$1"
  [[ -d "$dir" ]] || return 1
  shopt -s nullglob
  local found_any=0
  for lib in "$dir"/libfdb_c.*; do
    safe_copy "$lib" "$LIB_DIR"
    found_any=1
  done
  shopt -u nullglob
  if [[ "$found_any" -eq 1 ]]; then
    return 0
  fi
  return 1
}

link_existing_install() {
  local server
  if ! server="$(command -v fdbserver 2>/dev/null)"; then
    return 1
  fi

  safe_copy "$server" "$BIN_DIR"
  chmod +x "$BIN_DIR/fdbserver" 2>/dev/null || true

  if command -v fdbcli >/dev/null 2>&1; then
    safe_copy "$(command -v fdbcli)" "$BIN_DIR"
    chmod +x "$BIN_DIR/fdbcli" 2>/dev/null || true
  fi

  local copied_lib=0

  if command -v ldconfig >/dev/null 2>&1; then
    while IFS= read -r libpath; do
      [[ -n "$libpath" ]] || continue
      safe_copy "$libpath" "$LIB_DIR"
      copied_lib=1
    done < <((ldconfig -p 2>/dev/null | awk '/libfdb_c\./ {print $4}' | sort -u) || true)
  fi

  local server_dir
  server_dir="$(dirname "$server")"
  for candidate in \
    "$server_dir" \
    "$server_dir/.." \
    "$server_dir/../lib" \
    "$server_dir/../lib64" \
    "/usr/lib" "/usr/lib64" "/usr/local/lib" "/usr/local/lib64" \
    "/usr/lib/x86_64-linux-gnu" "/usr/lib/aarch64-linux-gnu" \
    "/opt/foundationdb/lib" "/opt/foundationdb/lib64"; do
    if copy_libs_from_dir "$candidate"; then
      copied_lib=1
    fi
  done

  if [[ ! -x "$BIN_DIR/fdbserver" ]]; then
    return 1
  fi

  if [[ "$copied_lib" -eq 0 ]]; then
    warn "libfdb_c not found next to system installation; continuing"
  fi

  log "Linked existing FoundationDB installation from PATH ($server)"
  return 0
}

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
  verify_checksum "$tmp/$pkg" "$url.sha256"

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
  curl -fsSL --retry 5 --retry-delay 2 --retry-all-errors "$base/$clients_pkg" -o "$tmp/$clients_pkg"
  verify_checksum "$tmp/$clients_pkg" "$base/$clients_pkg.sha256"
  curl -fsSL --retry 5 --retry-delay 2 --retry-all-errors "$base/$server_pkg" -o "$tmp/$server_pkg"
  verify_checksum "$tmp/$server_pkg" "$base/$server_pkg.sha256"

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

  local server_found=0
  for candidate in \
    "$tmp/root/usr/bin/fdbserver" \
    "$tmp/root/usr/sbin/fdbserver" \
    "$tmp/root/usr/lib/foundationdb/fdbserver" \
    "$tmp/root/usr/libexec/fdbserver"; do
    if [[ -x "$candidate" ]]; then
      safe_copy "$candidate" "$BIN_DIR"
      chmod +x "$BIN_DIR/fdbserver" 2>/dev/null || true
      server_found=1
      break
    fi
  done

  local cli_found=0
  for candidate in \
    "$tmp/root/usr/bin/fdbcli" \
    "$tmp/root/usr/lib/foundationdb/fdbcli"; do
    if [[ -x "$candidate" ]]; then
      safe_copy "$candidate" "$BIN_DIR"
      chmod +x "$BIN_DIR/fdbcli" 2>/dev/null || true
      cli_found=1
      break
    fi
  done

  # Libraries may reside under a number of lib directories
  shopt -s nullglob
  local libs_found=0
  for lib in \
    "$tmp/root"/usr/lib*/libfdb_c.* \
    "$tmp/root"/usr/lib/foundationdb/libfdb_c.* \
    "$tmp/root"/usr/local/lib/libfdb_c.*; do
    if [[ -f "$lib" ]]; then
      safe_copy "$lib" "$LIB_DIR"
      libs_found=1
    fi
  done
  shopt -u nullglob

  if [[ "$server_found" -ne 1 ]]; then
    warn "fdbserver binary not found in extracted packages"
    return 1
  fi

  if [[ "$cli_found" -ne 1 ]]; then
    warn "fdbcli binary not found in extracted packages"
  fi

  if [[ "$libs_found" -ne 1 ]]; then
    warn "libfdb_c library not found in extracted packages"
  fi

  log "Installed FoundationDB binaries into $BIN_DIR"
}

install_linux_with_apt() {
  local sudo_prefix=()
  if (( EUID != 0 )); then
    if have sudo; then
      sudo_prefix=(sudo -n)
    else
      warn "apt-get found but no sudo/root privileges; skipping"
      return 1
    fi
  fi

  "${sudo_prefix[@]}" apt-get update || return 1
  if ! "${sudo_prefix[@]}" apt-get install -y foundationdb-clients foundationdb-server; then
    return 1
  fi

  if ! link_existing_install; then
    warn "apt install succeeded but linking binaries failed"
    return 1
  fi

  return 0
}

install_linux_with_dnf() {
  local sudo_prefix=()
  if (( EUID != 0 )); then
    if have sudo; then
      sudo_prefix=(sudo -n)
    else
      warn "dnf found but no sudo/root privileges; skipping"
      return 1
    fi
  fi

  if ! "${sudo_prefix[@]}" dnf install -y foundationdb; then
    return 1
  fi

  if ! link_existing_install; then
    warn "dnf install succeeded but linking binaries failed"
    return 1
  fi

  return 0
}

install_linux() {
  if link_existing_install; then
    return 0
  fi

  local installed=1

  if have apt-get; then
    warn "Attempting apt-based installation of FoundationDB"
    if install_linux_with_apt; then
      installed=0
    fi
  elif have dnf; then
    warn "Attempting dnf-based installation of FoundationDB"
    if install_linux_with_dnf; then
      installed=0
    fi
  fi

  if [[ "$installed" -ne 0 ]]; then
    log "Falling back to downloading .deb packages from GitHub releases"
    if ! install_linux_from_deb; then
      warn "Deb extraction method failed"
    fi
  fi

  link_existing_install || true
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
