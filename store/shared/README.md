# Maryk Store Shared

The common datastore layer used by Maryk storage engines. Applications normally
choose a concrete store; this module is for shared capabilities and store adapters.

## Application guides

- [Queries and transactions](../../core/docs/query.md): typed requests, atomic
  write sets, serializable scopes, history and sync.
- [Backup and restore](docs/backup-restore.md): choose native, state or history backup, then
  follow the recovery guide for that approach.
- [Store behavior](docs/store-behavior.md): deletion, uniqueness, replication,
  structural queries and listener lifecycle.
- [Encryption key rotation](docs/encryption-key-rotation.md): application-owned
  keys, retained providers, resumable re-encryption and index rebuilding.
- [Model migrations](docs/migrations.md): shared schema compatibility, phase,
  target recovery and operator-control contracts.
- [Storage format upgrades](docs/storage-format-upgrades.md): preparing and
  recovering persistent stores opened with a newer library.

## Shared capabilities

- Request orchestration, versioning, scan optimization and live update listeners.
- Atomic write sets and serializable transactions, using each engine's commit
  implementation. Custom adapters explicitly advertise supported capabilities.
- Field encryption and sensitive-index token contracts, including the common
  AES-GCM/HMAC-SHA256 provider and keyring support.
- Snapshot capture, streaming logical backup and atomic replay of complete
  source-version groups. Restore is not globally transactional; publish a
  disposable target only after successful completion.

Key rotation requires backend integration: the shared batch helper does not make
payloads, history and unique-index updates atomic across a store. Retain old keys
and providers until all affected data and indexes have been migrated. Follow the
[rotation guide](docs/encryption-key-rotation.md) for callback and durability requirements.

## Point-in-time backup and restore

Use `backupState` for consistent current records with or without source history,
and `restoreState` for a history-disabled isolated target. Use `backup` / `restore`
to preserve retained revisions; that path requires history on both stores.
Both paths require compatible models and successful writer completion. See
[backup and restore](docs/backup-restore.md) for capture guarantees, binary archive
transport, resource limits and publication rules.

## Implementing a store

`IsDataStore` defines requests, updates, flows and lifecycle. `AbstractDataStore`
provides actor routing and listener management. Engines implement persistence,
range reads, encoding and native commit boundaries. Optional capability interfaces
cover transactions, snapshots and version-group restore; decorators must forward
those guarantees explicitly.

Use the conformance helpers in [store/test](../test/README.md) to verify behavior
across adds, changes, deletes, queries, history, uniqueness and update flows.

## Concrete engines

- [Memory](../memory/README.md)
- [IndexedDB](../indexeddb/README.md)
- [RocksDB](../rocksdb/README.md)
- [FoundationDB](../foundationdb/README.md)
- [Remote](../remote/README.md)

## Published API compatibility

The baseline for a published JVM module is its latest released public API.
Minor and patch releases preserve binary-compatible public signatures; intentional
incompatible changes require a major release and migration notes. Check released
types, call signatures and serialized defaults before changing them. An arbitrary
unreleased branch is not a compatibility baseline.
