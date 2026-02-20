---
title: CLI
description: Interactive terminal client for browsing and editing Maryk stores.
---

Maryk ships with an interactive CLI for exploring data models and records. It connects to RocksDB and FoundationDB stores and provides scrolling viewers for `get` and `scan`.

## Run

From the repo root:

```bash
./gradlew :cli:runJvm
```

Release binary (macOS example):

```bash
./gradlew :cli:runReleaseExecutableMacosArm64
```

## Connect

```text
connect rocksdb --dir /path/to/rocksdb
connect foundationdb --dir maryk/app/store [--cluster /path/to/fdb.cluster]
```

## Serve

Expose a local store over HTTP:

```text
serve rocksdb --dir ./data --host 127.0.0.1 --port 8210
serve foundationdb --dir maryk/app/store --cluster /path/to/fdb.cluster --port 8210
```

You can also pass `--config` with key/value config (see CLI commands doc for details).
Warning: no auth or TLS; bind to localhost or use SSH tunneling.

## Browse

- `list` shows data models.
- `model <name|id>` prints the schema.
- `get <model> <key>` opens a record viewer.
- `scan <model> [options]` opens a scrollable list; use `show` and `filter` to shape the list.

Example session:

```text
connect rocksdb --dir ./data
list
scan Client --show info.name.firstNames,info.name.familyName --order -info.name.familyName
```

Command reference: [CLI commands](./commands/).

## Edit

Inside the `get` viewer, use `set`, `unset`, `append`, `remove`, `save`, and `load` to change values. If the viewer was opened from `scan`, the `close` command returns to the scan list.

Inline values are parsed as Maryk YAML scalars; quote strings with spaces or YAML special characters.

## Scan options

- `--start-key <base64>`
- `--limit <n>` (default 100)
- `--include-start` / `--exclude-start`
- `--to-version <n>`
- `--include-deleted`
- `--where <expr>`
- `--order <ref,...>`
- `--select <ref,...>`
- `--show <ref,...>`
- `--max-chars <n>`

For large datasets, prefer `--order` on an indexed property or add filters to keep scans efficient.
