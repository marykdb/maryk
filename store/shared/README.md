# Maryk Store Shared

Common datastore layer used by all store engines.
Not intended as a standalone store.

## What it contains

- Separate atomic write-set and transaction capabilities, sharing each built-in
  backend's commit implementation. See [Choosing a write boundary](../../core/docs/query.md#choosing-a-write-boundary).
- Unified datastore contracts and request orchestration used by all engines.
- Optional `TransactionalDataStore` capability and common serializable transaction
  scopes, read-your-own-writes, repeated mutations, savepoints, rollback, explicit
  conflict retries and successful commit receipts. `AtomicRequest` uses the same
  backend transaction engine for one-shot initial-state-checked write sets.
  See the [transaction contract](../../core/docs/query.md#serializable-transactions).
- Replication creation changes retain their final soft-delete state in both single
  updates and initial history replay. Deleted creations remain excluded from normal
  reads and active indexes until an explicit later undelete.
- Shared update/listener flow behavior.
- Flow completion includes listener cleanup. If store shutdown overlaps that
  cleanup, its shutdown acknowledgement does not replace the flow's cancellation
  or original failure. Other cleanup failures remain visible, and closing active
  requests still fails them with `StorageException`.
- Shared versioning/query semantics.
- Shared sensitive-field encryption contracts:
  - `maryk.datastore.shared.encryption.FieldEncryptionProvider`
  - `maryk.datastore.shared.encryption.SensitiveIndexTokenProvider`
- Built-in cross-platform provider:
  - `maryk.datastore.shared.encryption.AesGcmHmacSha256EncryptionProvider`
  - Field encryption: AES-GCM
  - Deterministic sensitive lookup tokens: HMAC-SHA256
- Additive key rotation:
  - `KeyringFieldEncryptionProvider` rejects unencodable key IDs, owns its token-read
    configuration, writes versioned key-id envelopes and reads
    active, previous, and legacy ciphertext. Existing unwrapped MKE2 ciphertext
    requires a contextual `legacyProvider` during adoption.
  - Deterministic-token candidate derivation and candidate-aware unique reads support
    staged index-key rotation. Keep previous token keys configured until stored
    unique indexes have been rebuilt with the active token key.
  - `runReEncryptionBatch` provides a bounded, resumable adapter contract with a
    versioned cursor/state record. Adapters supply the persisted MKE1/MKE2 field
    envelope plus its model, record-key, and property-reference identity so MKE2
    authentication is preserved during rotation.
- Portable point-in-time operations:
  - `captureSnapshotVersion`, `backup`, and `restore`
  - Versioned manifests, opaque cursor paging, bounded streaming chunks, and
    atomic replay of complete source-version groups.
  - Snapshot capture waits for an in-flight mutation before fixing the read
    boundary. A backup is valid only after its writer completes successfully.
  - Restore replays bounded requests in source-version order and is not globally
    transactional. Publish a disposable target only after success.

## Encryption rotation constraints

Key rotation is an additive, per-store operation. The shared
`runReEncryptionBatch` loop coordinates bounded reads and invokes integration-
supplied write/state callbacks. The backend must provide idempotent writes and
durable state; the helper does not make payloads, historic rows, or deterministic
unique-index rows atomic across a store or across engines. Persist state after each
batch and design retries to replay a batch safely. Read callbacks exclude backend
version bytes but retain the complete MKE1/MKE2 field envelope; write callbacks
receive the complete replacement MKE2 payload.

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

The current portable format is version `3` (`DATA_STORE_BACKUP_FORMAT_VERSION`).
Restore accepts only that exact manifest version; it never guesses how to read an
older or newer format. A format change must increment the version, update both
writer and reader, and document whether a compatibility reader is supplied.

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
- Backups include retained historic data and the soft-delete transition/state
  visible at the snapshot. Hard deletion can permanently erase earlier history
  in persistent engines: capturing a version does not preserve erased rows.
  Coordinate hard deletion with backup creation, or use soft deletion until the
  backup completes. Protect backups as sensitive production data and retain
  them according to the same policy.
- Backup history paging preserves frequently changed fields even when another
  field has much older sparse revisions. A scanned record without its creation
  history, or history outside the exclusive snapshot boundary, stops publication.
  Creation reads must match that record and creation version. Duplicate or
  unordered page revisions, or conflicting copies of a complete revision across
  pages, also stop publication rather than silently selecting one copy.
- Restore requires matching registered model names and major model versions.
  Review minor/patch schema compatibility before restoring. By default it
  refuses non-empty target models. The target must retain all versions so the
  restored history is not silently collapsed to current state.
- Restore validates creation history, ordering, and snapshot bounds before replay,
  and rejects unexpected or incomplete backend responses.
- Restore uses serialized staging capped at 64 MiB and 100,000
  records by default. Exceeding either bound fails before changing the target.
  `restore(reader, requireEmpty, DataStoreRestoreOptions(...))` adjusts these bounds.
- Restore stages every reader once before replay. This avoids repeated scans for every
  record version while preserving global source-version order.
- Replay groups all records with the same source version, including across models
  and backup chunks. The group commits atomically and preserves original creation,
  change and deletion versions. Its old unique claims are released inside the
  transaction; final ownership is validated and installed before publication.
  Transfers and cyclic swaps do not depend on backup record/key order.
- Every group's serialized size and record count are checked before any replay.
  `maxReplayBytes` remains 8 MiB by default for API compatibility; the effective
  ceiling also includes the backend's advertised limits and the common replay
  limits of 1 MiB and 1,000 record versions per group. A group cannot be split.
  Native transaction/work limits can still reject a group atomically at execution.
- The backup format is version 3, and the original restore overload remains.
  No extra application-record metadata or live-store opening migration is needed.
- Built-in local stores advertise `VersionedRestoreDataStore`. Custom stores and
  decorators must explicitly implement/forward this capability and its atomicity
  contract. Unsupported targets fail before replay; there is no sequential fallback.
  Remote clients negotiate the capability with their server.
- Replay is not globally transactional: backend or later replay failures can leave
  earlier requests applied. Restore into a disposable store, validate it, then
  publish or switch to it only after success.

## Published API compatibility

The baseline for a published JVM module is its latest released public API.
Minor and patch releases preserve binary-compatible public signatures; an
intentional incompatible removal or signature change requires a major release
and migration notes. Check that released baseline before changing a public
type, constructor, method, property, or serialized default. This policy does
not promise that an arbitrary unreleased branch is a compatibility baseline.

## Where to use

Use through concrete engines:
- [Memory store](../memory/README.md)
- [RocksDB store](../rocksdb/README.md)
- [FoundationDB store](../foundationdb/README.md)

Storage format 2 unifies ordinary Float/Double and ordered index bytes. It has one
opening migration from the released, unversioned layout, separate from model
schema versions. Backups use
format 3 because packed value objects can contain the changed bytes; restore
rejects earlier formats instead of guessing or wrapping legacy backup payloads.

The same opening boundary selects the released Protobuf codec for NANOS DateTime
collections in persisted logs and schema defaults. Source model/dependency
definitions are re-encoded together with a per-model codec marker before final
publication, so interruption cannot mix legacy and current default encodings.
Property references containing packed floating value objects retain that legacy
context while their map keys and set items are decoded.

Persisted schema definitions retain each reference's key width using the existing
`Name(width)` representation. This metadata context leaves ordinary request and
receipt encodings unchanged. Source roots are loaded before compatibility checks;
each stored pair keeps its own recursive dependencies and decoder. A parent
definition's explicit source width remains authoritative across a separate target
migration, and source value reads consume that width.

Older Protobuf definitions stored reference target names without widths. Registered
source roots supply those widths during opening, including references to later
models and reference-based primary keys. An old external target absent from the
persisted source catalogue has no recoverable width. Opening reports that an
authoritative source schema is required; it does not trust the configured target's
possibly changed width. New persisted definitions retain external widths directly.
The released-layout opening conversion persists these widths before application
migration callbacks run. An intermediate, unreleased format-2 definition without
widths cannot establish an old parent's width after a referenced target has already
been re-keyed and published. Resume that state only with an authoritative source
schema snapshot; the surviving target catalogue is not proof of the old width.

This boundary supports the released legacy layout. Intermediate development
layouts that mixed new transport bytes with missing format markers cannot be
identified from the payload safely. Do not remove a current format stamp to
rerun legacy conversion; recover those stores from a known source snapshot.

Resuming an opening conversion validates its phase cursor, staged entries and
target ownership inventory before applying further writes. A current format
stamp with an unfinished checkpoint is also rejected. Missing or inconsistent
upgrade metadata requires recovery from authoritative checkpoint and staging
data; opening does not skip the missing work or publish a completed stamp.

Opening conversion also tags retained transaction receipts with their source
codec. Identical retries are checked against an exact released request encoding
before their original response is returned; they never execute the mutation
again. Requests whose released encoding lost collection element boundaries or
typed-empty identity cannot be proven from the receipt digest. They return an
explicit invalid-state response and require resolution in the original
source-format environment. Changing the transaction ID would bypass that
protection and must not be used as a recovery procedure.
Current receipts retain only the request digest and response metadata. Generated
incrementing-map values are restored from their exact original request operation
when replaying the receipt, rather than persisting sensitive request values.

Opening conversion leaves unaffected ciphertext and sensitive unique tokens
byte-for-byte intact. Encryption providers are required for affected encrypted
values or qualifiers and for encrypted logs containing changed value objects or
legacy NANOS DateTime framing;
retained models with unaffected sensitive fields do not require their keys merely
to convert another plaintext Float/Double field.

## Structural query reads

`Exists` distinguishes a present empty collection or embedded value from an
absent or deleted property. Exact structural comparisons decode the requested
subtree at the query's version. List and set comparisons retain their existing
membership rules; map and embedded-value comparisons retain value equality.
These rules also apply to concrete map entries and list positions.

Persistent stores inspect sensitive scalar presence without decrypting its
payload. Structural comparisons read their selected subtree and required parent
markers, without fetching unrelated sensitive fields. Ancestor collection
projection is temporary and does not rewrite stored counts or qualifiers.

Live indexed scans retain their original ordering boundary when a start record is
changed or deleted. Refills use bounded pages and choose a new record against that
fixed boundary, so another index entry for an already visible record cannot fill
the slot. Records with index entries on both sides of the boundary retain their
eligible position. This can require scanning an earlier index prefix; scanning
stops when later entries cannot improve the refill candidate. Projected cursor
requests retain their validated boundary while internal reads fetch the complete
values needed for ordering.
