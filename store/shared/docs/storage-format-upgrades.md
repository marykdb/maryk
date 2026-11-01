# Storage format upgrades

This guide covers opening a persistent store created by Maryk 0.13.0 with the
current source branch (0.14.0-SNAPSHOT). New stores need no legacy conversion.
Storage formats are independent of application model versions and portable backup
formats. Memory has no persisted layout; Remote uses its backend's layout.

## Before upgrading

1. Keep a recoverable copy of the original store and its schemas and encryption keys.
2. Complete pending application schema migrations with the original library.
3. Stop old writers. Do not downgrade a converted store or run mixed old/new writers.
4. Open with the stored source models first. Changed primary-key bytes require an
   explicit key/reference migration; the opening conversion rejects them.
5. Allow time and space for the resumable conversion. On interruption, reopen with
   the same source schemas and required providers; retain checkpoint/staging data.
6. Restart scans and replication subscriptions from a fresh snapshot after conversion.

Conversion can stop when released bytes do not establish collection presence or
incrementing-map allocation history. Review the engine's recovery requirements
before rollout; a retry cannot recreate lost source information.

## Engine guides

- [RocksDB migrations](../../rocksdb/documentation/migrations.md#released-storage-recovery-limits)
  and [conversion layout](../../rocksdb/documentation/storage.md#opening-storage-format-2).
- [FoundationDB migrations](../../foundationdb/docs/migrations.md#released-storage-recovery-limits)
  and [conversion layout](../../foundationdb/docs/storage.md#opening-storage-format-2).
- [IndexedDB migrations](../../indexeddb/docs/migrations.md)
  and [conversion layout](../../indexeddb/docs/storage.md#opening-storage-format-2).

## What changes

The opening conversion rewrites affected current and historic fields, collection
qualifiers, packed value objects and unique lookups, including contextual
ciphertext and sensitive lookup tokens. Original record IDs and versions remain.
Unchanged cells and index encodings are preserved. Variable-width index entries
use escaped boundaries and UTF-16 string ordering. Fixed floating or packed-value
map/set ancestors also need descriptor conversion, even for unchanged integer leaves.

The store publishes `maryk.storage/format = 2` only after verification. Later opens
skip conversion. Durable checkpoints and temporary staging make key movement
resumable; temporary sensitive-token translations are removed before publication.
Legacy field encryption needs separate conversion. Old scan cursors are rejected.

Retained transaction receipts keep their request identity. New receipts use the
current request fingerprint; retries against released receipts use the released
codec. If that encoding cannot establish identity, resolution fails explicitly
and preserves the receipt instead of applying the mutation again.

## Format and recovery contract

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

## Opening maintenance

In RocksDB and FoundationDB, per-model `maryk.storage/maintenance` metadata separately records completion of
clock repair and optional history/tombstone backfills. Later opens read these
completion flags and the operational clock watermark, skipping old repair-marker
checks and scans. Models first opened later and newly enabled history still get
their required maintenance. This is engine metadata, independent of model schema
versions, with no extra fields on application records.
