#!/usr/bin/env bash
set -euo pipefail

# Compile only on macOS. Matching CI hosts must test these exact artifacts.
root=$(cd "$(dirname "$0")/../../.." && pwd)
cd "$root"
[[ $(uname -s) == Darwin ]] || { echo "This cross-build helper requires macOS" >&2; exit 1; }
target=${1:-all}
if [[ $# -gt 0 ]]; then shift; fi
case "$target" in
  all) targets=(macos-aarch64 macos-x86_64 linux-x86_64 linux-aarch64 linux-x86_64-musl linux-aarch64-musl windows-x86_64) ;;
  macos-aarch64|macos-x86_64|linux-x86_64|linux-aarch64|linux-x86_64-musl|linux-aarch64-musl|windows-x86_64) targets=("$target") ;;
  *) echo "Unknown comparator target: $target" >&2; exit 1 ;;
esac

for target in "${targets[@]}"; do
  # Bash 3.2 treats an empty array as unset under nounset. Keep the command
  # nonempty even for targets which need no additional compiler arguments.
  args=(./gradlew :store:rocksdb:prepareJvmNativeComparator --console=plain --no-configuration-cache)
  case "$target" in
    macos-*) cc=${CC:-cc}; cpp=${CXX:-c++} ;;
    windows-*)
      llvm_bin=${LLVM_BIN:-$(brew --prefix llvm)/bin}
      cc="$llvm_bin/clang"; cpp="$llvm_bin/clang++"
      linker=${WINDOWS_LINKER:-$(brew --prefix lld)/bin/lld-link}
      [[ -x $linker ]] || { echo "Set WINDOWS_LINKER to a macOS-hosted lld-link executable" >&2; exit 1; }
      [[ -n ${WINDOWS_SDK_ROOT:-} ]] || { echo "Set WINDOWS_SDK_ROOT to an xwin MSVC SDK" >&2; exit 1; }
      args+=("-Pmaryk.rocksdbNativeWindowsSdk=$WINDOWS_SDK_ROOT"
        "-Pmaryk.rocksdbNativeCompilerArguments=-fuse-ld=lld;-B$(dirname "$linker")")
      ;;
    linux-*-musl)
      musl_bin=${MUSL_CROSS_BIN:-$(brew --prefix musl-cross)/bin}
      arch=${target#linux-}; arch=${arch%-musl}
      cc="$musl_bin/$arch-linux-musl-gcc"
      cpp="$musl_bin/$arch-linux-musl-g++"
      ;;
    linux-*)
      llvm_bin=${LLVM_BIN:-$(brew --prefix llvm)/bin}
      cc="$llvm_bin/clang"; cpp="$llvm_bin/clang++"
      arch=${target#linux-}
      triple="$arch-unknown-linux-gnu"
      dependencies="${KONAN_DATA_DIR:-$HOME/.konan}/dependencies"
      shopt -s nullglob
      toolchains=("$dependencies/$triple"-gcc-*-glibc-*-kernel-*)
      linkers=("$dependencies"/llvm-*-macos-*/bin/ld.lld)
      shopt -u nullglob
      if [[ ${#toolchains[@]} -eq 0 ]]; then
        echo "Missing Kotlin/Native $triple sysroot; run the matching compileKotlinLinux task first" >&2
        exit 1
      fi
      linker=${LLVM_LINKER:-$llvm_bin/ld.lld}
      if [[ ! -x $linker && ${#linkers[@]} -gt 0 ]]; then linker=${linkers[${#linkers[@]}-1]}; fi
      [[ -x $linker ]] || { echo "Set LLVM_LINKER to a macOS-hosted ld.lld executable" >&2; exit 1; }
      toolchain=${toolchains[${#toolchains[@]}-1]}
      args+=("-Pmaryk.rocksdbNativeCompilerArguments=--target=$triple;--sysroot=$toolchain/$triple/sysroot;--gcc-toolchain=$toolchain;-fuse-ld=$linker")
      ;;
  esac
  "${args[@]}" \
    "-Pmaryk.rocksdbNativeTarget=$target" "-Pmaryk.rocksdbNativeCC=$cc" "-Pmaryk.rocksdbNativeCXX=$cpp" "$@"
done
