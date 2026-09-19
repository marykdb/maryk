# Local Test Server

This module can automatically install and run a local FoundationDB server for JVM and host-native tests.

- **Automatic test lifecycle:** macOS/Linux only. `scripts/run-fdb-for-tests.sh` starts `fdbserver` on `127.0.0.1:4500`, writes logs to `build/testdatastore/logs`, and PID to `build/testdatastore/fdbserver.pid`; `scripts/stop-fdb-for-tests.sh` stops that managed server and removes the test database directory.
- **Windows:** unsupported for Maryk FoundationDB JVM tests. `scripts/install-foundationdb.ps1` exits intentionally because the required release artifacts and checksums are not pinned. Do not use it as an installer; use macOS/Linux for the managed local test lifecycle.
- **Install location:** Binaries are placed under `store/foundationdb/bin` and native libs under `store/foundationdb/bin/lib`.
  The managed test commands set `java.library.path` and `DYLD_LIBRARY_PATH`/`LD_LIBRARY_PATH` to this location.

### Kotlin Toolchain Integration

Run from the repository root:

```bash
./kotlin do foundationdbJvmTest -m foundationdb
./kotlin do foundationdbNativeTest -m foundationdb
```

Both commands provision the pinned binaries, hold the same file lock used by
Gradle, reset the test database, wait for readiness, and stop the server after
the suite. The native command chooses the current macOS/Linux architecture and
supplies the native client linker paths through generated test bindings.
Cleanup runs on success, test/startup failure, and normal process termination.
Logs are retained after the run; the database data is removed.

Exclude this module from direct aggregate runs with
`./kotlin test -p jvm --exclude-module foundationdb`, then run its managed command.
A direct `./kotlin test -m foundationdb` does not start a server.

### Gradle Integration

- **Auto start/stop:** `jvmTest` depends on starting FDB and finalizes by stopping it.
- **Tasks:**
    - `installFoundationDB`: installs or links FDB locally.
    - `startFoundationDBForTests`: starts the local server.
    - `stopFoundationDBForTests`: stops the server and cleans data.

### Configuration

- **`FDB_VERSION`:** Optional FoundationDB release selector. The only supported pinned release is `7.3.79`; any other version fails before local/system installation, download, or extraction. If unset, scripts use the project default matching the client dependency.
- **`FDB_CLEAN_MODE`:** Post‑test cleanup (default `data`). Options:
    - `data`: delete `build/testdatastore/data` (database wiped).
    - `all`: delete `build/testdatastore/data` and `build/testdatastore/logs`.
    - `none`: keep both.
- **Cluster file:** Tests use `store/foundationdb/fdb.cluster` (exported as `FDB_CLUSTER_FILE` for JVM tests). The run script will create it if missing.
- **Ports/paths:** Default listen/public address `127.0.0.1:4500`, data `build/testdatastore/data`, logs `build/testdatastore/logs`.

### Manual Usage

- **Install (macOS/Linux):** `bash scripts/install-foundationdb.sh`.
- **Install (Windows):** not supported by Maryk. The PowerShell script exits intentionally; it does not install or configure a test runtime.
- **Start/stop (macOS/Linux only):** `bash scripts/run-fdb-for-tests.sh` and `bash scripts/stop-fdb-for-tests.sh` (respects `FDB_CLEAN_MODE`).

Notes:
- On macOS, the installer downloads and extracts the FoundationDB `.pkg` from GitHub releases if `fdbserver` is not on the `PATH`, and copies `libfdb_c.*` into `bin/lib`.
- On Linux, if no package manager is detected, the installer downloads and extracts `.deb` artifacts locally.
- Windows FoundationDB testing is not supported by this module's local tooling until release artifacts and checksums are pinned.
