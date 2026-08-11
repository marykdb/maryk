# Maryk Store Shared

Common datastore layer used by all store engines.
Not intended as a standalone store.

## What it contains

- Unified datastore contracts and request orchestration used by all engines.
- Shared update/listener flow behavior.
- Shared versioning/query semantics.
- Shared sensitive-field encryption contracts:
  - `maryk.datastore.shared.encryption.FieldEncryptionProvider`
  - `maryk.datastore.shared.encryption.SensitiveIndexTokenProvider`
- Built-in cross-platform provider:
  - `maryk.datastore.shared.encryption.AesGcmHmacSha256EncryptionProvider`
  - Field encryption: AES-GCM
  - Deterministic sensitive lookup tokens: HMAC-SHA256
- Additive key rotation:
  - `KeyringFieldEncryptionProvider` writes versioned key-id envelopes and reads
    active, previous, and legacy ciphertext.
  - Deterministic-token candidate derivation and candidate-aware unique reads support
    staged index-key rotation. Keep previous token keys configured until stored
    unique indexes have been rebuilt with the active token key.
  - `runReEncryptionBatch` provides a bounded, resumable adapter contract with a
    versioned cursor/state record.
- Portable point-in-time operations:
  - `captureSnapshotVersion`, `backup`, and `restore`
  - Versioned manifests, opaque cursor paging, bounded streaming chunks, and
    restore through Maryk's normal replication path.
  - Snapshot capture waits for an in-flight mutation before fixing the read
    boundary. A backup is valid only after its writer completes successfully.
  - Restore is streaming, not globally transactional. Restore into an empty
    disposable store and publish it only after success.

## Encryption rotation constraints

Key rotation is an additive, per-store operation. The shared
`runReEncryptionBatch` loop coordinates bounded reads and invokes integration-
supplied write/state callbacks. The backend must provide idempotent writes and
durable state; the helper does not make payloads, historic rows, or deterministic
unique-index rows atomic across a store or across engines. Persist state after each
batch and design retries to replay a batch safely.

Keep the previous token keys and the legacy payload provider configured until all
encrypted payloads, historic rows, and deterministic unique-index rows have been
re-encrypted or rebuilt. FoundationDB, RocksDB, and IndexedDB identify existing
unique rows before removing or historicizing retained-token entries; a row owned
by another key is left untouched. Run each backend's migration under its own
durable transaction/job boundary rather than treating rotation as a turnkey
cross-store transaction.

## Point-in-time backup and restore

`backup` creates a portable logical backup: the manifest identifies the format,
snapshot version, and models; each chunk contains complete versioned changes for
one model. It is not a filesystem or engine-level backup.

Requirements and operation:

- Enable `keepAllVersions` before data is written. History cannot be recreated
  later, and `backup` rejects stores without it.
- Let `backup` capture the snapshot version, or provide one obtained from the
  same cluster. Use that version for every page; do not mix snapshots.
- A remote store needs a compatible server that exposes an authoritative
  snapshot version. A newer client refuses an older server without that
  capability rather than producing a mixed-time export.
- A `DataStoreBackupWriter` must keep output private until `complete()` returns.
  Use durable storage, integrity checks, encryption where needed, and an atomic
  publish/rename step in the writer implementation.
- Choose `batchSize` for memory and transport limits. It bounds records per
  chunk, not the size of one record's complete history.
- Backups include historic and soft-deleted data. Protect them as sensitive
  production data and retain them according to the same policy.
- Restore requires matching registered model names and major model versions.
  Review minor/patch schema compatibility before restoring. By default it
  refuses non-empty target models. The target must retain all versions so the
  restored history is not silently collapsed to current state.
- Restore validates creation history, ordering, and snapshot bounds before
  applying each chunk, and rejects unexpected or incomplete backend responses.
- Restore is streaming, so a failure can leave earlier chunks—and part of the
  failing chunk—applied. Restore into a new disposable store, validate it, then
  publish or switch to it only after success.

## Where to use

Use through concrete engines:
- [Memory store](../memory/README.md)
- [RocksDB store](../rocksdb/README.md)
- [FoundationDB store](../foundationdb/README.md)
