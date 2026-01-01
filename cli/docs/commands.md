# CLI commands

## Global commands

- `help` — show available commands.
- `connect` — connect to a store.
- `disconnect` — close the current store connection.
- `list` — list available data models.
- `model <name|id>` — render the schema for a model.
- `model --key-index-format <name|id>` — include key and index format details.
- `add <model> <file> [--yaml|--json|--proto] [--meta] [--key <base64>]` — add a new record from a file.
- `get <model> <key> [subcommand ...]` — fetch a record and open the viewer, or run a record action inline.
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
```

Notes:
- `--connect` is required for one-shot mode.
- `--exec` is parsed like CLI input (quotes supported).
- Commands that open interactive viewers (`get` without a subcommand or `connect` without args) are rejected in one-shot mode.
- `scan` in one-shot mode returns a single page (respecting `--limit`) as plain output.
- Use `get <model> <key> <subcommand ...>` to run `save`, `load`, `set`, `unset`, `append`, `remove`, or `delete` without entering the viewer.

## Connect

```text
connect rocksdb --dir /path/to/rocksdb
connect foundationdb --dir maryk/app/store [--cluster /path/to/fdb.cluster] [--tenant myTenant]
```

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
```

Notes:
- Format defaults to YAML.
- `--meta` expects a metadata file saved via `save --meta`; its key is used (version/deleted flags are ignored).
- If `--key` is omitted, the key is derived from the values (UUID keys generate a new random key).
- `--key` must match the metadata key when `--meta` is used.

## Get viewer

When `get` returns data, you enter a viewer with scrolling output.

Commands:
- `save <dir> [--yaml|--json|--proto] [--meta]`
- `load <file> [--yaml|--json|--proto] [--if-version <n>] [--meta]`
- `set <ref> <value> [--if-version <n>]`
- `unset <ref> [--if-version <n>]`
- `append <ref> <value> [--if-version <n>]`
- `remove <ref> <value> [--if-version <n>]`
- `delete [--hard]`
- `q|quit|exit`

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

## Scan viewer

The scan viewer shows one record per line, with a leading `>` for the selected row.

Navigation:
- Up/Down: move the selection
- PgUp/PgDn: page
- Home/End: jump to start/end

Commands:
- `get` — open the selected record in the viewer.
- `show <ref,...>` — pick which fields to show for each row. This also updates the select graph so only those fields are fetched.
- `filter <expr>` — add another `where` filter clause (Maryk YAML format).
- `save`, `load`, `delete` — operate on the selected record.
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

Notes:
- Results are fetched in pages (default 100) and the viewer auto-loads more as you scroll.
- Large datasets should prefer `--order` on an indexed property for faster scans.
