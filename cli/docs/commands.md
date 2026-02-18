# CLI commands

## Global commands

- `help [command]` — show available commands or details for one command.
- `connect` — connect to a store.
- `serve` — serve a store over HTTP.
- `disconnect` — close the current store connection.
- `list` — list available data models.
- `model [--with-deps] [--key-index-format] [<name|id>]` — render the schema for a model.
- `model --all [--with-deps] [--key-index-format]` — render schemas for all models.
- `add <model> <file> [--yaml|--json|--proto] [--meta] [--key <base64>]` — add a new record from a file.
- `get <model> <key> [--include-deleted] [subcommand ...]` — fetch a record and open the viewer, or run a record action inline.
- `undelete <model> <key> [--if-version <n>]` — restore a soft-deleted record.
- `changes <model> <key> [options]` — show versioned changes for a record.
- `scan <model> [options]` — browse records in a scrolling list.

## Quick example

```text
connect rocksdb --dir ./data
list
scan Client --show info.name.firstNames,info.name.familyName --order -info.name.familyName
```

## One-shot mode

Run a single command without the interactive UI:

```text
maryk --connect rocksdb --dir ./data --exec "list"
maryk --connect rocksdb --dir ./data --exec "model Client"
maryk --connect rocksdb --dir ./data --exec "add Client ./client.yaml"
maryk --exec "serve rocksdb --dir ./data --port 8210"
```

Notes:
- `--connect` is optional for commands that do not require an existing connection (for example `serve` or `help`).
- `--exec` is parsed like CLI input (quotes supported).
- Commands that open interactive viewers (`get` without a subcommand or `connect` without args) are rejected in one-shot mode.
- `scan` in one-shot mode returns a single page (respecting `--limit`) as plain output.
- Use `get <model> <key> <subcommand ...>` to run `save`, `load`, `set`, `unset`, `append`, `remove`, or `delete` without entering the viewer.

## Connect

```text
connect rocksdb --dir /path/to/rocksdb
connect foundationdb --dir maryk/app/store [--cluster /path/to/fdb.cluster] [--tenant myTenant]
```

## Serve

Serve a local store over HTTP (Ktor):

```text
serve rocksdb --dir ./data --host 127.0.0.1 --port 8210
serve foundationdb --dir maryk/app/store --cluster /path/to/fdb.cluster --tenant myTenant --port 8210
```

Config file:

```text
store: rocksdb
dir: ./data
host: 127.0.0.1
port: 8210
```

```text
serve --config ./serve.conf
```

Notes:
- Uses ProtoBuf `Requests`/responses over HTTP.
- Each response message is length-prefixed (4 bytes, big-endian).
- `serve` blocks the CLI session until the server is stopped.
- `serve` is available in JVM and native desktop binaries.
- Warning: no auth or TLS; bind to localhost or use SSH tunneling.

## Model

```text
model [--with-deps] [--key-index-format] [<name|id>]
model --all [--with-deps] [--key-index-format]
```

Options:
- `--with-deps` includes dependent definitions.
- `--key-index-format` prints key/index format details.
- `--all` renders every model in the connected store.

When the output viewer is shown, `save` supports YAML/JSON/PROTO, plus Kotlin output:
- `save <dir> --kotlin --package <name>` (generates Kotlin files)
- `save <dir> --no-deps` (export without dependencies)

## Add

Add a new record from a file:

```text
add <model> <file> [--yaml|--json|--proto] [--meta] [--key <base64>]
```

Examples:

```text
add Client ./client.yaml
add Client ./client.meta.yaml --meta
add Client ./client.yaml --key AbCdEf123
add Client - --yaml < ./client.yaml
```

Notes:
- Format defaults to YAML.
- `--meta` expects a metadata file saved via `save --meta`; its key is used (version/deleted flags are ignored).
- If `--key` is omitted, the key is derived from the values (UUIDv4/UUIDv7 keys generate a new key).
- `--key` must match the metadata key when `--meta` is used.
- YAML/JSON files can contain a list of objects to add multiple records.
- Use `-` as the file path to read from stdin.

## Reference paths

Many commands take property references (for `set`, `show`, `select`, `order`, etc.). Use dot notation:

- `info.name.firstNames` — nested fields
- `addresses.@0` — list item at index 0
- `addresses.*` — list/map wildcard
- `moment.*ShiftMetaDataType.executor` — multi-type list/map with type name

## Get viewer

When `get` returns data, you enter a viewer with scrolling output.

Commands:
- `save <dir> [--yaml|--json|--proto] [--meta]` — export the record.
- `load <file> [--yaml|--json|--proto] [--if-version <n>] [--meta]` — apply values from a file.
- `set <ref> <value> [--if-version <n>]` — set or replace a value.
- `unset <ref> [--if-version <n>]` — clear a value.
- `append <ref> <value> [--if-version <n>]` — append to a list or add to a set.
- `remove <ref> <value> [--if-version <n>]` — remove from a list or set.
- `delete [--hard]` — soft delete (default) or hard delete.
- `undelete [--if-version <n>]` — restore a soft-deleted record.
- `close` (when opened from a scan) — return to the scan list.
- `q|quit|exit` — leave the viewer.

If the viewer was opened from a scan, `close` returns to the scan list.

One-shot examples:

```text
maryk --connect rocksdb --dir ./data --exec "get Client AbCdEf123 save ./out --yaml"
maryk --connect rocksdb --dir ./data --exec "get Client AbCdEf123 set info.name.nickname \"Captain Jasper\""
```

Notes:
- Values are parsed as Maryk YAML scalars for the referenced type.
- Strings do not require quotes, but quoting is recommended for spaces or YAML special characters.
- `--if-version` applies optimistic concurrency checks.
- `save --meta` stores the record metadata; `load --meta` uses metadata from the file (key/version).
- `append`/`remove` only support list or set references.
- Use `get --include-deleted` to access soft-deleted records.
- Use `-` as the load file path to read from stdin.

## Scan viewer

The scan viewer shows one record per line, with a leading `>` for the selected row.

Navigation:
- Up/Down: move the selection
- PgUp/PgDn: page
- Home/End: jump to start/end

Commands:
- `get` — open the selected record in the viewer.
- `show <ref,...>` — choose fields to display (also updates the select graph).
- `filter <expr>` — add another `where` clause (Maryk YAML format).
- `save <dir> [--yaml|--json|--proto] [--meta]` — export the selected record.
- `load <file> [--yaml|--json|--proto] [--if-version <n>] [--meta]` — apply values to the selected record.
- `delete [--hard]` — soft delete (default) or hard delete.
- `undelete [--if-version <n>]` — restore a soft-deleted record.
- `close` — leave the scan viewer.
- `q|quit|exit` — leave the scan.

Filtering examples (Maryk YAML tags):

```text
scan Client --where "!Equals { info.name.familyName: \"Smith\" }"
scan Client --where "!And
  - !Equals { info.status: \"Active\" }
  - !GreaterThanEquals { info.age: 21 }"
```

Ordering examples:

```text
scan Client --order info.name.familyName
scan Client --order -info.name.familyName
scan Client --order info.name.familyName:desc
```

Scan options (from the `scan` command):
- `--start-key <base64>`
- `--limit <n>`
- `--include-start` / `--exclude-start`
- `--to-version <n>`
- `--include-deleted`
- `--where <expr>`
- `--order <ref,...>`
- `--select <ref,...>`
- `--show <ref,...>`
- `--max-chars <n>`

## Undelete

```text
undelete <model> <key> [--if-version <n>]
```

Restores a soft-deleted record.

## Changes

```text
changes <model> <key> [--from-version <n>] [--to-version <n>] [--limit <n>] [--include-deleted]
```

Examples:

```text
changes Client AbCdEf123 --from-version 10
changes Client AbCdEf123 --include-deleted --limit 100
```

Notes:
- Results are fetched in pages (default 100) and the viewer auto-loads more as you scroll.
- Large datasets should prefer `--order` on an indexed property for faster scans.
