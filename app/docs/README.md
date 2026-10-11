# Maryk App Desktop Guide

## Overview

Maryk App is the desktop UI for Maryk stores.
Use it to inspect models, scan records, edit values, and export/import data.

## First launch: unsigned desktop packages

Current macOS and Windows release packages are not code-signed, so your operating system may block the first launch. On macOS, first try to open the app, then go to **System Settings → Privacy & Security** and select **Open Anyway**. On Windows, a security warning may appear; continue only after confirming that you downloaded the package from the official Maryk release page.

## Prerequisites

- JDK 21+ (the Gradle build uses a Java 21 toolchain).
- Local store or remote HTTP endpoint.
- FoundationDB users: install `libfdb_c` (see `/store/foundationdb/`).

## Run locally

From repo root:

```bash
./gradlew :app:run
```

## First-time workflow

1. Open Stores window.
2. Add a store config (RocksDB / FoundationDB / Remote).
3. Open store.
4. Select model in catalog.
5. Scan/filter/sort records.
6. Open a record.
7. Edit with the form.
8. Save and verify in History tab.

## SQL queries and writes

Open a store and optionally select a model in the catalog. Click **SQL** beside the
time-travel button to open a separate modal. Click **Use selected model** to start a
bounded query for that model, or type SQL directly.
The editor highlights keywords, functions, strings, numbers, comments, and parameters.

- Suggestions include tables, scalar columns, table aliases, keywords, and supported
  functions. Type a prefix or an alias followed by a dot; **Ctrl+Space** also opens
  suggestions. Use **Up/Down** to choose, **Tab/Enter** to insert, and **Escape** to dismiss.
- **Ctrl+Enter** or **⌘+Enter** runs the statement. **Cancel** stops the current execution.
- **Parameters** exposes one typed input per `?`, ignoring question marks inside strings
  and comments. Read queries infer initial types where possible; select a type explicitly
  for writes or unconstrained parameters. Choose `NULL` for SQL null, and `TEXT` for encoded
  keys or enum names. Text values are supplied directly, without SQL quotes.
- **Explain** shows the prepared read plan without executing it. Results show column names
  and types; **Copy TSV** copies the full displayed result with escaped tabs and newlines.
  SQL `NULL` is distinct from the string `'NULL'`.
- Completion statistics show requests, fetched rows, expression work, buffer use, and the
  snapshot version when applicable. On failure or cancellation, displayed rows are explicitly
  marked as partial. Syntax errors link to their editor position.
- **Snapshot** uses a retained-history snapshot when the connected store supports it.
  The browser's time-travel controls also apply to SQL reads. **Include deleted** exposes
  soft-deleted rows.

Queries are bounded to 10,000 rows, 4 MiB of SQL intermediate data, a separate 4 MiB of
displayed results, and 30 seconds. Exceeding a limit reports an error rather than silently
truncating the query. Closing the modal cancels execution while preserving its draft and
results for reopening. Disconnecting the store clears connection-specific results.

Turn on **Enable writes** to run `INSERT`, `UPDATE`, `DELETE`, `UNDELETE`, or `DELETE HARD`.
Updates and deletes require WHERE and accept bounded SQL predicates. Each statement
uses a serializable autocommit transaction with version guards. Writes cannot run with
time travel, snapshot, or include-deleted read options. Multi-row statements publish
all changes together; RETURNING rows appear in the result table. Cancellation before
commit discards staging. A submitted commit may be uncertain; confirm its outcome
before retrying. Close the SQL modal and reload the record browser to view changed data.

See [SQL](../../sql/README.md) for the supported language, types, and consistency guarantees.

## Store types

- RocksDB:
  - Local directory path.
  - Single process lock applies.
- FoundationDB:
  - Directory path.
  - Optional cluster file.
- Remote:
  - HTTP URL to CLI `serve` endpoint.
  - Optional SSH tunnel.

Saved configs: `~/.maryk/app/stores.conf`.

## Remote setup (HTTP + optional SSH)

Server side example:

```bash
maryk --exec "serve rocksdb --dir /path/to/store --port 8210"
```

(`--connect` is not needed for `serve` in one-shot mode.)

Then in app:

1. Add store type `Remote`.
2. Set URL (`http://host:8210`).
3. If needed, enable SSH tunnel and set host/user/port/identity.

Notes:

- Remote transport is HTTP. For direct public access, terminate HTTPS/TLS in a
  reverse proxy and forward to a loopback-bound Maryk server. SSH tunneling is
  an alternative for private or administrative access, not the only way to
  encrypt transport.
- If server binds to loopback (`127.0.0.1`) on remote host, set URL to that loopback URL and use SSH tunnel.

## UI map

- Stores window:
  - Saved store list.
  - Add/edit/remove/open.
- Browser window:
  - Left: model catalog.
  - Center: data/model tabs.
  - Right: record inspector.
  - Top bar: connection info, actions, panel toggles.
  - Operations menu: migration status, metrics, pause, resume, cancel, and refresh.

## Data browsing

- Model search with fuzzy match.
- Scan paging.
- Sort by key or index.
- Filters via builder or raw YAML.
- Pin model fields to columns.
- Row actions: open, edit, copy key/data, export, delete.

## Record inspector

- Data tab:
  - Structured tree view.
  - Search.
  - Reference navigation and preview.
- Raw tab:
  - YAML.
  - Search/copy.
- History tab:
  - Version timeline.
  - Diff view.

## Record editor

The form editor provides:

- Typed inputs with inline validation.
- Support for scalar and complex definitions:
  - List / Set / Map / IncrementingMap.
  - Embedded object/values.
  - MultiType (type select + value editing).
  - Reference, bytes, date/time/datetime, enums, numbers, booleans, strings.

## Import / export

- Schema export: JSON, YAML, Proto, Kotlin.
- Data export: row/model/all.
  - In time-travel mode, row exports read the selected version boundary. Row
    history exports use that boundary directly; otherwise they capture a
    snapshot when the store supports it.
  - With historical retention and an authoritative snapshot provider,
    row/model/all history exports and model/all current-state exports capture a
    point-in-time version before paging. Otherwise the app refuses a
    point-in-time export.
  - Version-history exports include soft-deleted records and their deletion
    changes.
  - Without historical retention, current-state export remains available, but
    concurrent writes can appear across pages.
- Data import:
  - Auto-detect format/scope.
  - Expects Maryk export structures (metadata + versioned changes where applicable).

## Shortcuts

- Cmd/Ctrl+R: reload scan.
- Cmd/Ctrl+B: toggle catalog.
- Cmd/Ctrl+I: toggle inspector.
- Esc: close drawer/dialog.
- Arrow/PageUp/PageDown: move selection.
- Enter/Space: open selected record.
- Cmd/Ctrl+click: multi-select.
- Shift+click: range select.

## Troubleshooting

- Cannot open RocksDB:
  - Ensure no other process has the DB open.
- FoundationDB connect fails:
  - Check `libfdb_c`, cluster file.
- Remote connect fails:
  - Verify CLI `serve` running.
  - Check URL/port/tunnel target.
- Empty scans:
  - Confirm selected model and filter expression.

## Local state paths

- Store configs: `~/.maryk/app/stores.conf`.
- UI prefs (panel/pins): Java Preferences node `io.maryk.app.ui`.

An uncertain SQL commit can be resolved with **Resolve commit**. The console blocks
further writes until that retained commit has a confirmed outcome.
