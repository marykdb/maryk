# Maryk CLI

The Maryk CLI is an interactive terminal client that communicates with Maryk stores. It is built with the [Kotter](https://github.com/varabyte/kotter) terminal UI library to provide a block-oriented command experience. It can connect to both RocksDB and FoundationDB stores.

Native CLI builds support Linux x64 and macOS. Linux ARM64 is not a native CLI
target because Kotter does not publish that variant; the JVM CLI remains available.

## Status

The CLI supports interactive browsing and editing:
- Connect to RocksDB or FoundationDB stores.
- List models, inspect schema, and fetch records.
- Scan through records with keyboard navigation and inline actions.
- Run bounded SQL queries with `sql SELECT ...` or `sql --file report.sql`, serializable mutations with `sql --write ...`, and interactive BEGIN/COMMIT sessions with savepoints; see the [command reference](docs/commands.md) and [SQL guide](../sql/README.md).
- Edit records with `set`, `unset`, `append`, and `remove`, and save/load snapshots.

## Getting Started

Run the CLI from the project root:

```bash
./gradlew :cli:runJvm
```

For release builds on macOS, you can run:

```bash
./gradlew :cli:runReleaseExecutableMacosArm64
```

When executed in a non-interactive environment (like Gradle), the release binary prints the bundled help text and exits cleanly.

Once running interactively, type `help` to see the available commands, or `help <command>` for detailed usage. Use `Ctrl+C` to exit the session.

### Connecting to a store

- RocksDB: `connect rocksdb --dir /path/to/rocksdb`
- FoundationDB: `connect foundationdb --dir maryk/app/store [--cluster /path/to/fdb.cluster]`

### Serving a store over HTTP

```text
serve rocksdb --dir ./data --host 127.0.0.1 --port 8210
```

Keep the server loopback-bound behind TLS termination or use SSH tunneling.
Bearer authentication does not encrypt traffic. See the [serve reference](docs/commands.md#serve)
for authentication, configuration files and remote-binding options.

## Development Notes

- Commands are registered through `CommandRegistry` and return structured output so the UI can render consistently.
- The CLI uses a lightweight command line parser for command arguments; SQL commands preserve SQL quotes and comments for the SQL parser.
- Tests live in `cli/src/commonTest/kotlin` and can be executed via `./gradlew :cli:jvmTest`.

## Commands

Run `help` or `help <command>` for usage. The [command reference](docs/commands.md)
covers connections, schema inspection, records, SQL and migrations.

Example session:

```text
connect rocksdb --dir ./data
list
scan Client --show info.name.firstNames,info.name.familyName
```

### Adding records

```text
add Client ./client.yaml
```

See [add](docs/commands.md#add) for metadata, explicit keys, batches and stdin.

## One-shot mode

```text
maryk --connect rocksdb --dir ./data --exec "list"
```

See [one-shot mode](docs/commands.md#one-shot-mode) for supported commands and viewer restrictions.

## Interactive viewers

### Get viewer

Open a record with `get <model> <key>`. Edits apply immediately.
See [record controls](docs/commands.md#get-viewer) for editing, import and export.

### Scan viewer

Use `scan <model>` to browse records. See [scan controls and options](docs/commands.md#scan-viewer)
for paging, filtering, selection and history.

## Docs

The [command reference](docs/commands.md) owns detailed usage and options.
For SQL commit recovery, see [SQL commands](docs/commands.md#sql).
