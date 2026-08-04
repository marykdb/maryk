#!/usr/bin/env bash
set -euo pipefail

archive="${1:?Usage: verify-native-bundle.sh <archive> <linux-x64|macos-arm64>}"
platform="${2:?Usage: verify-native-bundle.sh <archive> <linux-x64|macos-arm64>}"

[[ -f "$archive" ]] || { echo "Missing archive: $archive" >&2; exit 1; }

bundle_name="maryk-cli-${platform}"
case "$platform" in
  linux-x64) library="libfdb_c.so" ;;
  macos-arm64) library="libfdb_c.dylib" ;;
  *) echo "Unsupported platform: $platform" >&2; exit 1 ;;
esac

temp_dir="$(mktemp -d)"
trap 'rm -rf -- "$temp_dir"' EXIT
unzip -q "$archive" -d "$temp_dir"

binary="$temp_dir/$bundle_name/bin/maryk"
bundled_library="$temp_dir/$bundle_name/lib/$library"
license_file="$temp_dir/$bundle_name/LICENSE.txt"
[[ -x "$binary" ]] || { echo "Missing executable: $binary" >&2; exit 1; }
[[ -f "$bundled_library" ]] || { echo "Missing FoundationDB client: $bundled_library" >&2; exit 1; }
[[ -f "$license_file" ]] || { echo "Missing license: $license_file" >&2; exit 1; }

case "$platform" in
  linux-x64)
    readelf -d "$binary" | grep -F 'RUNPATH' | grep -F '$ORIGIN/../lib'
    ldd "$binary" | grep -F "$bundled_library"
    ;;
  macos-arm64)
    lipo -archs "$binary" | tr ' ' '\n' | grep -Fx arm64
    lipo -archs "$bundled_library" | tr ' ' '\n' | grep -Fx arm64
    otool -l "$binary" | grep -A2 'LC_RPATH' | grep -F '@executable_path/../lib'
    otool -L "$binary" | grep -F '@rpath/libfdb_c.dylib'
    ;;
esac

"$binary" --help >/dev/null
