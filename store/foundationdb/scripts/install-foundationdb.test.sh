#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INSTALLER="$SCRIPT_DIR/install-foundationdb.sh"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf -- "$TEST_ROOT"' EXIT

FAKE_BIN="$TEST_ROOT/fake-bin"
ISOLATED_ROOT="$TEST_ROOT/repo"
ISOLATED_SCRIPTS="$ISOLATED_ROOT/store/foundationdb/scripts"
mkdir -p "$FAKE_BIN" "$ISOLATED_SCRIPTS"
cp "$INSTALLER" "$ISOLATED_SCRIPTS/install-foundationdb.sh"

cat > "$FAKE_BIN/uname" <<'EOF'
#!/usr/bin/env bash
case "${1:-}" in
  -s) echo "${FAKE_UNAME_OS:-Darwin}" ;;
  -m) echo "${FAKE_UNAME_ARCH:-arm64}" ;;
  *) echo "${FAKE_UNAME_OS:-Darwin}" ;;
esac
EOF

cat > "$FAKE_BIN/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

output=""
url=""
while [[ $# -gt 0 ]]; do
  if [[ "$1" == "-o" ]]; then
    output="$2"
    shift 2
  elif [[ "$1" == http* ]]; then
    url="$1"
    shift
  else
    shift
  fi
done

[[ "$url" == *.sha256 ]] && exit 22
printf 'untrusted artifact' > "$output"
EOF

cat > "$FAKE_BIN/pkgutil" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

destination="$3"
payload="$destination/FoundationDB.pkg/Payload/usr/local"
mkdir -p "$payload/libexec" "$payload/bin" "$payload/lib"
printf '#!/usr/bin/env bash\n' > "$payload/libexec/fdbserver"
printf '#!/usr/bin/env bash\n' > "$payload/bin/fdbcli"
printf 'library' > "$payload/lib/libfdb_c.dylib"
chmod +x "$payload/libexec/fdbserver" "$payload/bin/fdbcli"
EOF

chmod +x "$FAKE_BIN/uname" "$FAKE_BIN/curl" "$FAKE_BIN/pkgutil"

if output="$(PATH="$FAKE_BIN:/usr/bin:/bin" bash "$ISOLATED_SCRIPTS/install-foundationdb.sh" 2>&1)"; then
  echo "Expected installer to reject an unverified downloaded artifact, but it succeeded." >&2
  exit 1
fi

grep -Fq 'Checksum mismatch' <<< "$output" || {
  echo "Expected a checksum mismatch failure, got:" >&2
  echo "$output" >&2
  exit 1
}

cat > "$FAKE_BIN/fdbserver" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$FAKE_BIN/fdbserver"

if output="$(FDB_VERSION=7.3.76 FAKE_UNAME_OS=Linux FAKE_UNAME_ARCH=x86_64 PATH="$FAKE_BIN:/usr/bin:/bin" bash "$ISOLATED_SCRIPTS/install-foundationdb.sh" 2>&1)"; then
  echo "Expected an unpinned version to be rejected before linking fdbserver from PATH." >&2
  exit 1
fi
grep -Fq 'No pinned SHA-256 checksums for FoundationDB version 7.3.76' <<< "$output" || {
  echo "Expected a version pin failure before PATH linking, got:" >&2
  echo "$output" >&2
  exit 1
}

while read -r artifact expected; do
  actual="$(bash -c 'source "$1"; pinned_sha256 "$2"' _ "$INSTALLER" "$artifact")"
  [[ "$actual" == "$expected" ]] || {
    echo "Unexpected pinned checksum for $artifact: $actual" >&2
    exit 1
  }
done <<'EOF'
FoundationDB-7.3.75_arm64.pkg 6b162b0bebefd49873ce2e7d7db7bb001515c80ce3a90545585859c26362488f
FoundationDB-7.3.75_x86_64.pkg 62a19eddf0a46df7b835d55309c27040853530460087804b07390fefa925d0ab
foundationdb-clients_7.3.75-1_aarch64.deb ab58b30f6bc2fa2c8ba0a30e156e292be1322b804340e089e55d21de878398c0
foundationdb-clients_7.3.75-1_amd64.deb 642841a90acd7f2cc0ae08297245f4f9df76fe250b7b1331f2f99702fec3bee8
foundationdb-server_7.3.75-1_aarch64.deb 8189d4aaf5eb29e4a79819700a08096a3853ec8806693729e3f69a91a75c6a0e
foundationdb-server_7.3.75-1_amd64.deb 2cc48b1863125dadc834f0678c3cb54191d637fdc6502d571d63a1628937721e
EOF

if bash -c 'source "$1"; pinned_sha256 unsupported-artifact' _ "$INSTALLER" >/dev/null; then
  echo "Expected an unknown artifact to have no pinned checksum." >&2
  exit 1
fi

if FDB_VERSION=7.3.76 bash -c 'source "$1"; pinned_sha256 FoundationDB-7.3.76_arm64.pkg' _ "$INSTALLER" >/dev/null; then
  echo "Expected an unpinned FoundationDB version to be rejected." >&2
  exit 1
fi

unknown_artifact="$TEST_ROOT/FoundationDB-7.3.76_arm64.pkg"
printf 'artifact' > "$unknown_artifact"
if output="$(FDB_VERSION=7.3.76 bash -c 'source "$1"; verify_checksum "$2"' _ "$INSTALLER" "$unknown_artifact" 2>&1)"; then
  echo "Expected verification to reject an unpinned FoundationDB version." >&2
  exit 1
fi
grep -Fq 'No pinned SHA-256 checksum' <<< "$output" || {
  echo "Expected an unpinned checksum failure, got:" >&2
  echo "$output" >&2
  exit 1
}
