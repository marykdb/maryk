# Kotlin Toolchain development

The repository provides a Kotlin Toolchain 0.12.2 build for its libraries,
CLI, Protobuf interoperability tests, and local build plugins alongside Gradle. This is a partial migration, not a complete
replacement for Gradle. The wrappers pin the distribution and its SHA-256;
no global Kotlin installation is needed.

## Commands

```sh
./kotlin show modules
./kotlin build -p jvm
./kotlin test -p jvm --exclude-module foundationdb
./kotlin test -p jvm -m core
./kotlin test -p jvm -m jvmTest
./kotlin do foundationdbJvmTest -m foundationdb
./kotlin do foundationdbNativeTest -m foundationdb
./kotlin test -p jvm -m cli
./kotlin run -m cli-jvm
./kotlin package -m cli-jvm
java -jar build/tasks/_cli-jvm_executableJarJvm/cli-jvm-jvm-executable.jar
TZ=UTC KOTLIN_NATIVE_BACKTRACE=full ./kotlin test -p macosArm64 -m lib
```

Use `kotlin.bat` on Windows. Module selectors use the final directory name, for
example `memory`, `remote`, `conformance`, and `test`. The `jvmTest` module is
the Protobuf interoperability fixture; `cli-jvm` is the CLI application launcher.

The common template pins Kotlin 2.4.20, JDK 21, Java 17 compatibility, and the
existing compiler checks. Dependencies continue to come from
`gradle/libs.versions.toml`; `api` dependencies are exported in `module.yaml`.
When changing the Kotlin or release version, update both the Gradle source of
truth and the corresponding template in this directory.

## Source layout

Library sources now use the toolchain layout:

| Source set | Directory |
| --- | --- |
| Common production | `src/` |
| JVM production | `src@jvm/` |
| Shared native production | `src@native/` |
| Common tests | `test/` |
| JVM tests | `test@jvm/` |
| Common resources | `resources/` |
| Common test resources | `testResources/` |

Other platform qualifiers follow the same pattern. The file module uses a
`posix` alias to retain its Apple/Linux/Android Native source hierarchy. Remote
SSH C bindings live in `store/remote/cinterop`, with headers in `include/`.
The Gradle base convention maps these directories only for projects containing
`module.yaml`; the desktop application and Gradle plugin layouts remain unchanged.
The JVM-only Protobuf fixture keeps its Maven-style layout.

## Paths migrated beyond library compilation

- **FoundationDB tests:** the `foundationdbJvmTest` and `foundationdbNativeTest`
  commands install the pinned binaries, acquire the same file lock as Gradle,
  reset the local database, wait for readiness, run the selected toolchain suite,
  and stop the server before releasing the lock. They require macOS or Linux;
  the native command selects the host architecture. Cleanup also runs on test
  failures and normal process termination. Use these commands instead of running
  the FoundationDB suite directly. Other JVM suites can run together with
  `--exclude-module foundationdb`.
- **Native FoundationDB linking:** a generated cinterop definition in a separate
  test-support module supplies absolute client-library search and runtime paths.
  Toolchain 0.12.2 ignores test-scoped cinterop definitions, so this module is a
  native test dependency only. Its local paths never enter published libraries.
- **Protobuf:** the local plugin runs `protoc`, compiles generated Java as test
  sources, and copies the schema fixtures into test resources. It reads the
  compiler version and executable SHA-256 from the existing Gradle catalog and
  verification metadata. Unpinned host artifacts fail before download/execution.
- **CLI:** its multiplatform implementation is shared by both builds. The small
  `cli-jvm` launcher supports running and packaging an executable JAR with its
  dependencies. It requires Java 17 or newer when launched directly. FoundationDB
  connections additionally need the installed client library and cluster file;
  RocksDB's JNI library is bundled in its dependency.

## Remaining Gradle paths

| Area | Gap and working path |
| --- | --- |
| JS / Wasm tests | Toolchain 0.12.2 creates compilation/link tasks but no JS/Wasm test runner. Keep the existing Gradle Node/Karma tasks, including IndexedDB's `fake-indexeddb` dependency and real browser tests. |
| Android | Keep Gradle Android host/device verification and RocksDB core-library desugaring. Toolchain platform declarations alone do not establish Android behavior parity. |
| Native platform matrix | Host-native store tests have toolchain commands; retain Gradle's remaining platform matrix until each target has equivalent verification. |
| Generator Gradle plugin | Build and test `:generator:gradle-plugin` with Gradle. TestKit, plugin descriptors, plugin validation and marker publication remain part of its contract. |
| Native CLI / desktop app | Keep Gradle for native CLI executables/bundles and the Compose desktop application, ProGuard and installers. The JVM CLI has a working toolchain replacement. |
| Release publication | Keep `.github/workflows/publish.yml`. Toolchain publishing templates preserve library coordinates and metadata, but publication/consumer compatibility and signing have not yet been validated. No remote publication repository is enabled. |

Toolchain 0.12.2 passes deprecated flags to Kotlin 2.4's web compilers. The
`settings@web` override keeps those warnings visible without treating them as
errors; the retained Gradle web builds continue enforcing warnings-as-errors.
JVM and native toolchain compilation retain the strict policy. On `macosX64`,
`KONAN_ARGUMENT_STRONG_WARNING` remains a warning even with `-Werror`, allowing
Intel builds while Kotlin reports the target deprecation. This diagnostic
category covers native compiler-argument warnings; source warnings remain errors.

CI checks the toolchain JVM suites, managed FoundationDB tests, the JVM CLI
package, and selected macOS native suites, plus JS/Wasm library compilation.
Linux JVM library, CLI and Protobuf checks now use the toolchain job. Gradle
continues testing the desktop app and Gradle plugin, as well as the existing
native platform matrix. A successful host run does not establish parity across
all native, Android or web targets.
The remaining Gradle paths are migration and verification work, except that
Gradle remains the runtime under test for Maryk's Gradle plugin.

## Upstream references

- [Gradle migration guide](https://kotlin-toolchain.org/latest/getting-started/migrating-from-gradle/)
- [Plugin tasks and generated sources](https://kotlin-toolchain.org/latest/user-guide/plugins/topics/tasks/)
- [Multiplatform publication](https://kotlin-toolchain.org/latest/user-guide/publishing/)
- [Pinned 0.12.2 release](https://github.com/JetBrains/kotlin-toolchain/releases/tag/v0.12.2)
