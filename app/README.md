# Maryk App (Desktop)

Maryk App is a Kotlin Compose Multiplatform desktop UI for browsing and editing Maryk data stores.
It connects to RocksDB or FoundationDB and provides model catalogs, record scans, and editors.

## Key features

- Manage and connect to saved stores (RocksDB / FoundationDB).
- Browse models, inspect schemas, and pin fields.
- Scan records with sorting and filters.
- Inspect record data, raw YAML, and history.
- Add/edit records with validation or raw YAML.
- Import/export model schemas and data.

## Running

From the project root:

```bash
./gradlew :app:run
```

Notes:
- FoundationDB requires the native client library (`libfdb_c`). See `/store/foundationdb/`.
- Saved store configurations live at `~/.maryk/app/stores.conf`.

## Docs

See `app/docs/README.md` for UI walkthroughs and workflows.
