# Maryk CLI

The Maryk CLI is an interactive terminal client that communicates with Maryk stores. It is built with the [Kotter](https://github.com/varabyte/kotter) terminal UI library to provide a block-oriented command experience. It can connect to both RocksDB and FoundationDB stores.

## Status

The CLI supports interactive browsing and editing:
- Connect to RocksDB or FoundationDB stores.
- List models, inspect schema, and fetch records.
- Scan through records with keyboard navigation and inline actions.
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
- FoundationDB: `connect foundationdb --dir maryk/app/store [--cluster /path/to/fdb.cluster] [--tenant myTenant]`

### Serving a store over HTTP

Expose a local store via a lightweight Ktor server:

```text
serve rocksdb --dir ./data --host 127.0.0.1 --port 8210
serve foundationdb --dir maryk/app/store --cluster /path/to/fdb.cluster --tenant myTenant --port 8210
serve --config ./serve.conf
```

Config file format (key/value or YAML-style):

```text
store: rocksdb
dir: ./data
host: 127.0.0.1
port: 8210
```

Note: `serve` works in JVM and native desktop binaries.
Warning: no auth or TLS; bind to localhost or use SSH tunneling.

## Development Notes

- Commands are registered through `CommandRegistry` and return structured output so the UI can render consistently.
- The CLI uses a lightweight command line parser that supports quoted arguments, preparing the client for future commands like store connections and queries.
- Tests live in `cli/src/commonTest/kotlin` and can be executed via `./gradlew :cli:jvmTest`.

## Commands

- `connect`: Connect to a store (`rocksdb` or `foundationdb`).
- `serve`: Start an HTTP server that exposes a store (`rocksdb` or `foundationdb`).
- `disconnect`: Close the current store connection.
- `list`: Show available data models.
- `model [--with-deps] [--key-index-format] [<name|id>]`: Inspect a model's schema.
- `model --all [--with-deps] [--key-index-format]`: Inspect all model schemas.
- `add <model> <file> [--yaml|--json|--proto] [--meta] [--key <base64>]`: Add a new record from a file.
- `get <model> <key> [--include-deleted] [subcommand ...]`: Fetch a record and open a viewer, or run a record action inline.
- `undelete <model> <key> [--if-version <n>]`: Restore a soft-deleted record.
- `changes <model> <key> [options]`: Show versioned changes for a record.
- `scan <model> [options]`: Browse records in a scrolling list.

Tip: run `help <command>` for detailed usage, options, and subcommands.

Example session:

```text
connect rocksdb --dir ./data
list
scan Client --show info.name.firstNames,info.name.familyName
```

### Adding records

Use `add` with a file produced by `save` (or authored manually):

```text
add Client ./client.yaml
add Client ./client.meta.yaml --meta
add Client ./client.yaml --key AbCdEf123
```

Notes:
- `--meta` expects a metadata file saved via `save --meta`.
- If `--key` is omitted, the key is derived from the values (UUID keys generate a new random key).
- `add` works in both interactive and one-shot mode.
- YAML/JSON files can contain multiple objects to add in one call.
- Use `-` as the file path to read from stdin.

## One-shot mode

Run a single command without the interactive UI:

```text
maryk --connect rocksdb --dir ./data --exec "list"
maryk --connect rocksdb --dir ./data --exec "model Client"
maryk --connect rocksdb --dir ./data --exec "add Client ./client.yaml"
```

Notes:
- `--connect` is required for one-shot mode.
- `--exec` is parsed the same way as CLI input (quoted arguments are supported).
- Commands that open interactive viewers (`get` without a subcommand or `connect` without args) are rejected in one-shot mode.
- `scan` in one-shot mode returns a single page (respecting `--limit`) as plain output.
- Use `get <model> <key> <subcommand ...>` to run `save`, `load`, `set`, `unset`, `append`, `remove`, or `delete` without entering the viewer.

## Interactive viewers

### Get viewer

Commands: `save`, `load`, `set`, `unset`, `append`, `remove`, `delete`, `close` (when opened from a scan), `q/quit/exit`.
Edits apply immediately and refresh the view.

Inline edit parsing:
- Values are parsed as Maryk YAML scalars for the referenced property type.
- Strings do not require quotes, but quoting is recommended for values with spaces or YAML special characters.
- Use `--if-version` to guard against concurrent updates.

### Scan viewer

- Use Up/Down, PgUp/PgDn, Home/End to move.
- `get` opens the selected record in a viewer. Use `close` there to return to the scan.
- `show <ref,...>` controls which fields are displayed per row and updates the select graph.
- `filter <expr>` adds filters in the same syntax as `scan --where`.
- `save`, `load`, `delete` operate on the selected record.

Scan options:
- `--start-key <base64>` start scanning from a specific key.
- `--limit <n>` page size per fetch (default 100).
- `--include-start` / `--exclude-start` whether to include the start key.
- `--to-version <n>` scan as of a historic version.
- `--include-deleted` include soft-deleted records.
- `--where <expr>` filter expression (Maryk YAML).
- `--order <ref,...>` order by indexed references.
- `--select <ref,...>` fetch only selected fields.
- `--show <ref,...>` display these fields in each row.
- `--max-chars <n>` max characters per row (default 160).

## Docs

See `cli/docs/README.md` and `cli/docs/commands.md` for detailed flows and options.
