## Local Test Server

The module can automatically install and run a local FoundationDB server for JVM tests.

- **Install script:** `scripts/install-foundationdb.sh` (macOS/Linux) and `scripts/install-foundationdb.ps1` (Windows).
- **Run script:** `scripts/run-fdb-for-tests.sh` starts `fdbserver` on `127.0.0.1:4500`, writes logs to `build/testdatastore/logs`, and PID to `build/testdatastore/fdbserver.pid`.
- **Stop script:** `scripts/stop-fdb-for-tests.sh` stops the server and removes the test database directory.
- **Install location:** Binaries are placed under `store/foundationdb/bin` and native libs under `store/foundationdb/bin/lib`.

### Gradle Integration

- **Auto start/stop:** `jvmTest` depends on starting FDB and finalizes by stopping it.
- **Tasks:**
    - `installFoundationDB`: installs or links FDB locally.
    - `startFoundationDBForTests`: starts the local server.
    - `stopFoundationDBForTests`: stops the server and cleans data.

### Configuration

- **`FDB_VERSION`:** FDB version to install (default `7.4.3`). Example: `FDB_VERSION=7.4.3 ./gradlew :store:foundationdb:jvmTest`.
- **`FDB_CLEAN_MODE`:** Post‑test cleanup (default `data`). Options:
    - `data`: delete `build/testdatastore/data` (database wiped).
    - `all`: delete `build/testdatastore/data` and `build/testdatastore/logs`.
    - `none`: keep both.
- **Cluster file:** Tests use `store/foundationdb/fdb.cluster` (exported as `FDB_CLUSTER_FILE` for JVM tests). The run script will create it if missing.
- **Ports/paths:** Default listen/public address `127.0.0.1:4500`, data `build/testdatastore/data`, logs `build/testdatastore/logs`.

### Manual Usage

- **Install (macOS/Linux):** `bash scripts/install-foundationdb.sh`.
- **Install (Windows):** `powershell -ExecutionPolicy Bypass -File scripts/install-foundationdb.ps1 -Version 7.4.3`.
- **Start:** `bash scripts/run-fdb-for-tests.sh`.
- **Stop and clean:** `bash scripts/stop-fdb-for-tests.sh` (respects `FDB_CLEAN_MODE`).

Notes:
- On macOS, the installer prefers Homebrew when available; otherwise links from PATH. It also attempts to copy `libfdb_c.*` into `bin/lib`.
- On Linux, if no package manager is detected, the installer downloads and extracts `.deb` artifacts locally.
- On Windows, the installer uses Chocolatey or Winget if available; starting/stopping the Windows service can also be used instead of the scripts.
