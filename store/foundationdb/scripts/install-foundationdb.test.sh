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

# A system binary on PATH has no release-artifact checksum provenance, so it
# must not be copied into the CLI bundle.
PATH_ROOT="$TEST_ROOT/path-repo"
PATH_SCRIPTS="$PATH_ROOT/store/foundationdb/scripts"
mkdir -p "$PATH_SCRIPTS"
cp "$INSTALLER" "$PATH_SCRIPTS/install-foundationdb.sh"
if output="$(FAKE_UNAME_OS=Linux FAKE_UNAME_ARCH=x86_64 PATH="$FAKE_BIN:/usr/bin:/bin" bash "$PATH_SCRIPTS/install-foundationdb.sh" 2>&1)"; then
  echo "Expected an unverified PATH FoundationDB binary to be rejected, but it succeeded." >&2
  exit 1
fi
grep -Fq 'Checksum mismatch' <<< "$output" || {
  echo "Expected trusted-download checksum failure instead of PATH reuse, got:" >&2
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
FoundationDB-7.3.79_arm64.pkg 5104ade94d1e1b62119f49e3e16d43bd9ffb8b5ec604b1730f46f680c0c1890e
FoundationDB-7.3.79_x86_64.pkg 0bcd0f9430984ab72d7ba47f8ed95c85f14d237f3878ab1521feff14e074dbc4
foundationdb-clients_7.3.79-1_aarch64.deb 52de6931803c322e131c5e84cebb35d647237ef51a7df3df6fbe32b9b971e7fb
foundationdb-clients_7.3.79-1_amd64.deb 52cc22565c42e7eb60c08f395a0626483735c311be0d80b7c035ac8e328e2fff
foundationdb-server_7.3.79-1_aarch64.deb d92ecf9ebb5cba4b4c06ee3c74c44d31871957a1fb35a1efb662836ae7c87025
foundationdb-server_7.3.79-1_amd64.deb f85a4126a76919a4dd6194e69bc29200d0d4fa3d7b5261e7eb91c090b7fdba55
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

# Cached CLI binaries have no release-artifact checksum provenance. They must
# be discarded instead of silently reused.
CACHED_ROOT="$TEST_ROOT/cached-repo"
CACHED_SCRIPTS="$CACHED_ROOT/store/foundationdb/scripts"
CACHED_BIN="$CACHED_ROOT/store/foundationdb/bin"
mkdir -p "$CACHED_SCRIPTS" "$CACHED_BIN"
cp "$INSTALLER" "$CACHED_SCRIPTS/install-foundationdb.sh"
printf '#!/usr/bin/env bash\nexit 0\n' > "$CACHED_BIN/fdbserver"
chmod +x "$CACHED_BIN/fdbserver"

if output="$(PATH="$FAKE_BIN:/usr/bin:/bin" bash "$CACHED_SCRIPTS/install-foundationdb.sh" 2>&1)"; then
  echo "Expected an unverified cached FoundationDB bundle to be rejected, but it succeeded." >&2
  exit 1
fi
grep -Fq 'Checksum mismatch' <<< "$output" || {
  echo "Expected trusted-download checksum failure instead of cached bundle reuse, got:" >&2
  echo "$output" >&2
  exit 1
}

# Even a forged manifest cannot make arbitrary cached binaries reusable.
printf '#!/usr/bin/env bash\nexit 0\n' > "$CACHED_BIN/fdbserver"
chmod +x "$CACHED_BIN/fdbserver"
printf 'version 7.3.79\n%s fdbserver\n' "$(shasum -a 256 "$CACHED_BIN/fdbserver" | awk '{print $1}')" > "$CACHED_BIN/.maryk-foundationdb-integrity"
if output="$(PATH="$FAKE_BIN:/usr/bin:/bin" bash "$CACHED_SCRIPTS/install-foundationdb.sh" 2>&1)"; then
  echo "Expected a forged cached FoundationDB bundle to be rejected, but it succeeded." >&2
  exit 1
fi
grep -Fq 'Checksum mismatch' <<< "$output" || {
  echo "Expected forged cached bundle to fall through to trusted download, got:" >&2
  echo "$output" >&2
  exit 1
}
