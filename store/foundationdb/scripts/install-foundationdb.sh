#!/usr/bin/env bash
set -euo pipefail

# Install or link FoundationDB locally for the current platform.
# - Installs/symlinks into: store/foundationdb/bin
# - Version selector via FDB_VERSION env var or --version flag. Only releases with
#   pinned checksums are accepted (currently 7.3.79).
# - Strategy:
#   * Always rebuild the CLI bundle from pinned release artifacts and verify
#     their checksums before extraction. Cached, PATH, and package-manager
#     binaries have no trusted artifact provenance and are never bundled.
#       - macOS: download .pkg from GitHub Releases and extract.
#       - Linux: download .deb packages from GitHub Releases and extract.
#       - Windows: reject bundling until release artifacts and checksums are pinned.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"
BIN_DIR="$ROOT_DIR/store/foundationdb/bin"
LIB_DIR="$BIN_DIR/lib"

FDB_VERSION_DEFAULT="7.3.79"
FDB_VERSION="${FDB_VERSION:-$FDB_VERSION_DEFAULT}"

if [[ "${1:-}" == "--version" && -n "${2:-}" ]]; then
  FDB_VERSION="$2"
fi

: "${VERBOSE:=0}"

mkdir -p "$BIN_DIR" "$LIB_DIR"

log() { echo "[install-foundationdb] $*"; }
warn() { echo "[install-foundationdb][WARN] $*" >&2; }
err() { echo "[install-foundationdb][ERROR] $*" >&2; exit 1; }

debug() {
  if [[ "${VERBOSE:-0}" == "1" ]]; then
    echo "[install-foundationdb][DEBUG] $*"
  fi
}
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
pinned_sha256() {
  case "$FDB_VERSION/$1" in
    "7.3.79/FoundationDB-7.3.79_arm64.pkg") echo "5104ade94d1e1b62119f49e3e16d43bd9ffb8b5ec604b1730f46f680c0c1890e" ;;
    "7.3.79/FoundationDB-7.3.79_x86_64.pkg") echo "0bcd0f9430984ab72d7ba47f8ed95c85f14d237f3878ab1521feff14e074dbc4" ;;
    "7.3.79/foundationdb-clients_7.3.79-1_aarch64.deb") echo "52de6931803c322e131c5e84cebb35d647237ef51a7df3df6fbe32b9b971e7fb" ;;
    "7.3.79/foundationdb-clients_7.3.79-1_amd64.deb") echo "52cc22565c42e7eb60c08f395a0626483735c311be0d80b7c035ac8e328e2fff" ;;
    "7.3.79/foundationdb-server_7.3.79-1_aarch64.deb") echo "d92ecf9ebb5cba4b4c06ee3c74c44d31871957a1fb35a1efb662836ae7c87025" ;;
    "7.3.79/foundationdb-server_7.3.79-1_amd64.deb") echo "f85a4126a76919a4dd6194e69bc29200d0d4fa3d7b5261e7eb91c090b7fdba55" ;;
    *) return 1 ;;
  esac
}
validate_pinned_version() {
  case "$FDB_VERSION" in
    7.3.79) ;;
    *) err "No pinned SHA-256 checksums for FoundationDB version $FDB_VERSION." ;;
  esac
}
verify_checksum() {
  local file="$1"
  local expected
  expected="$(pinned_sha256 "$(basename "$file")")" || err "No pinned SHA-256 checksum for FoundationDB $FDB_VERSION artifact $(basename "$file")."

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

discard_existing_bundle() {
  rm -f "$BIN_DIR/fdbserver" "$BIN_DIR/fdbcli"
  shopt -s nullglob
  rm -f "$LIB_DIR"/libfdb_c.*
  shopt -u nullglob
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
  verify_checksum "$tmp/$pkg"

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
    aarch64|arm64) deb_arch="aarch64";;
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
  verify_checksum "$tmp/$clients_pkg"
  curl -fsSL --retry 5 --retry-delay 2 --retry-all-errors "$base/$server_pkg" -o "$tmp/$server_pkg"
  verify_checksum "$tmp/$server_pkg"

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

install_linux() {
  install_linux_from_deb
}

main() {
  validate_pinned_version

  debug "System: $(uname -a)"
  debug "Arch: $(uname -m)"
  debug "OS: $(uname -s)"
  debug "PATH: $PATH"
  debug "BIN_DIR: $BIN_DIR"
  debug "LIB_DIR: $LIB_DIR"

  discard_existing_bundle

  case "$(uname -s)" in
    Darwin) install_macos ;;
    Linux) install_linux ;;
    MINGW*|MSYS*|CYGWIN*)
      err "Windows CLI bundling is unavailable until FoundationDB release artifacts and SHA-256 checksums are pinned."
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

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
