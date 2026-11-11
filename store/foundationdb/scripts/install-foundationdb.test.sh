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

if output="$(FDB_TARGET_ARCH=arm64 FAKE_UNAME_OS=Linux FAKE_UNAME_ARCH=x86_64 PATH="$FAKE_BIN:/usr/bin:/bin" bash "$PATH_SCRIPTS/install-foundationdb.sh" 2>&1)"; then
  echo "Expected the cross-target ARM64 artifact to fail checksum verification in the fixture." >&2
  exit 1
fi
grep -Fq 'foundationdb-clients_7.3.81-1_aarch64.deb' <<< "$output" || {
  echo "Expected FDB_TARGET_ARCH=arm64 to select the ARM64 FoundationDB package, got:" >&2
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
FoundationDB-7.3.81_arm64.pkg cfa81beae6ac36410f3110f832b582f820cea4f43d7dbf0cb60a24da1824c56f
FoundationDB-7.3.81_x86_64.pkg 5718fc8cce59ee11e538c3a3c54f3224a362c4cfc7cfcf1415c5de6956b50c60
foundationdb-clients_7.3.81-1_aarch64.deb 7b6437e23137672a576ce0cdbefb5d866cd32aac1ed5e0d88be76dc6bd493035
foundationdb-clients_7.3.81-1_amd64.deb 28bb8291c6b32647d1149286cd2b6b5204360833a42e39e7799d80be57cbfa87
foundationdb-server_7.3.81-1_aarch64.deb 61fef33b59db860d421bf8fec4586e29fa2960de4509ab49c508cbc4ed4bdc0a
foundationdb-server_7.3.81-1_amd64.deb 232370c0be6c185c8a2c3225f61a56d7fe37c7bb9a197f94a23ee0dda6820ef4
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
printf 'version 7.3.81\n%s fdbserver\n' "$(shasum -a 256 "$CACHED_BIN/fdbserver" | awk '{print $1}')" > "$CACHED_BIN/.maryk-foundationdb-integrity"
if output="$(PATH="$FAKE_BIN:/usr/bin:/bin" bash "$CACHED_SCRIPTS/install-foundationdb.sh" 2>&1)"; then
  echo "Expected a forged cached FoundationDB bundle to be rejected, but it succeeded." >&2
  exit 1
fi
grep -Fq 'Checksum mismatch' <<< "$output" || {
  echo "Expected forged cached bundle to fall through to trusted download, got:" >&2
  echo "$output" >&2
  exit 1
}
