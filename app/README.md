# Maryk App (Desktop)

Desktop UI for browsing/editing Maryk stores.
Built with Compose Multiplatform.

## Features

- Store manager (RocksDB / FoundationDB / Remote).
- Model catalog + schema inspection.
- Record scan/filter/sort + pinned columns.
- Record inspector (data tree, YAML, history/diff).
- Form editor + raw YAML editor.
- Import/export for schemas and data.

## Run

From repo root:

```bash
./gradlew :app:run
```

Notes:

- FoundationDB needs `libfdb_c` (see `/store/foundationdb/README.md`).
- Saved store configs: `~/.maryk/app/stores.conf`.

## Full guide

See `/app/docs/README.md`.
