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

Once running interactively, type `help` to see the available commands. Use `Ctrl+C` to exit the session.

### Connecting to a store

- RocksDB: `connect rocksdb --dir /path/to/rocksdb`
- FoundationDB: `connect foundationdb --dir maryk/app/store [--cluster /path/to/fdb.cluster] [--tenant myTenant]`

## Development Notes

- Commands are registered through `CommandRegistry` and return structured output so the UI can render consistently.
- The CLI uses a lightweight command line parser that supports quoted arguments, preparing the client for future commands like store connections and queries.
- Tests live in `cli/src/commonTest/kotlin` and can be executed via `./gradlew :cli:jvmTest`.

## Commands (non-interactive)

- `connect`: Connect to a store (`rocksdb` or `foundationdb`).
- `disconnect`: Close the current store connection.
- `list`: Show available data models.
- `model <name|id>`: Inspect a model's schema.
- `get <model> <key>`: Fetch a record and open a viewer.
- `scan <model> [options]`: Browse records in a scrolling list.

Example session:

```text
connect rocksdb --dir ./data
list
scan Client --show info.name.firstNames,info.name.familyName
```

## Interactive viewers

### Get viewer

Commands: `save`, `load`, `set`, `unset`, `append`, `remove`, `delete`, `q/quit/exit`.
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
