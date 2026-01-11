# Maryk App (Desktop)

Maryk App is a Kotlin Compose Multiplatform desktop tool for browsing and editing Maryk data stores.
It connects to RocksDB or FoundationDB stores and provides a visual browser for models, records,
and version metadata.

## Status

The initial UI focuses on the core CLI workflows in a desktop-first layout:
- Manage and connect to saved stores (RocksDB / FoundationDB).
- Browse models and scan records with filters and field selections.
- Inspect record metadata and edit records via YAML.
- Delete/restore records and export YAML.

## Running

From the project root:

```bash
./gradlew :app:run
```

Notes:
- FoundationDB requires the native client library (`libfdb_c`). See `store/foundationdb/README.md`.
- Saved store configurations live in your local user config directory under `~/.maryk/app/stores.conf`.

## Docs

See `app/docs/README.md` for UI walkthroughs and tips.
