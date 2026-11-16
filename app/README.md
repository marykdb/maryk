# Maryk App (Desktop)

Desktop UI for browsing/editing Maryk stores.
Built with Compose Multiplatform.

## Features

- Store manager (RocksDB / FoundationDB / Remote).
- Model catalog + schema inspection.
- Record scan/filter/sort + pinned columns.
- SQL modal with highlighting, catalog autocomplete, typed parameters, results, serializable writes, transaction sessions, savepoints, and commit resolution.
- Record inspector (data tree, YAML, history/diff).
- Typed form editor.
- Schema export plus data import/export.

## Run

From repo root:

```bash
./gradlew :app:run
```

Notes:

- FoundationDB needs `libfdb_c` (see [FoundationDB setup](../store/foundationdb/README.md)).
- Saved store configs: `~/.maryk/app/stores.conf`.

## Full guide

See [App guide](docs/README.md).

## Application icon

Window, Dock/taskbar, and native installer icons are generated from the original
raster logo on a padded gradient background. Resource processing and packaging
run the generation task automatically. See [brand sources and generation](../branding/README.md).
