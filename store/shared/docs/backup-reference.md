# Portable backup reference

This page is for custom storage adapters, store implementations and limit tuning.
For normal use, start with [choosing a backup](backup-restore.md), then follow the
[state](state-backups.md) or [history](history-backups.md) guide.

## Formats and schema compatibility

| Format | Current version | Payload |
| --- | --- | --- |
| State | 2 | Current values, keys, creation/update versions, soft-delete state and lifetime allocation maxima |
| History | 3 | Retained creation and change history at a Maryk boundary |

Readers require the exact supported format version; they do not guess how to
interpret other versions. State, history and native engine backups are separate
formats. A format change requires a new version and an explicit compatibility policy.

Registered model names and major versions must match. Review minor/patch schema
changes too: matching major versions alone does not prove compatibility. State
and history replay reject unknown fields, including nested fields. A restore
requires compatible definitions or an explicit migration; it does not silently
adapt an arbitrary schema.

## State capture guarantees

`StateBackupDataStore` guarantees one stable view across every model, page and
allocation-counter read, plus atomic import of a state batch. Custom stores and
decorators must explicitly implement or forward it. Ordinary paginated scans do
not establish this guarantee.

| Backend | Capture scope |
| --- | --- |
| RocksDB | One pinned engine snapshot; later writes and hard deletes cannot alter it |
| Memory | Actor read scope that excludes writes during capture |
| IndexedDB | Actor exclusion plus a cross-context writer fence; no suspended native browser transaction |
| FoundationDB | One pinned engine read version; expiry fails capture |
| Remote | One server-owned capture using the backing store's scope |

Finish migrations before capture. Every model is checked before the view is
pinned, and readiness is checked again while reading. Direct IndexedDB writers
that bypass Maryk's ownership protocol are outside its consistency guarantee.
An isolated FoundationDB cluster still has the same snapshot lifetime.

State chunks follow manifest model order and strictly increasing record keys.
The `allocations` map belongs to the records: retain it even when its references
point into currently absent parents. Visible values cannot reconstruct lifetime
allocation maxima after entries have been removed.

## Writer and reader contracts

Writers must keep partial output private and publish it durably and atomically
only when `complete()` succeeds. Do not call the source datastore from writer
callbacks. Keep callbacks bounded and cancellation-aware; failed output is discarded.

`StateBackupArchiveWriter` encodes contextual protobuf records inside bounded
binary frames. Chained SHA-256 checksums, a required completion frame and EOF
checks reject corruption, truncation, reordered frames and trailing content.
`openStateBackupArchive` validates model registrations and major versions before
record decoding. Checksums detect corruption but do not authenticate the archive.
Storage, compression, encryption, scheduling and retention belong to the caller.

State readers are consumed once. Invoke and await the consumer sequentially,
propagate its failures, and verify the complete archive before returning. Exported
values are detached from backend buffers; restore detaches and bounds input before
replay. Custom transports must preserve all record metadata and allocation maps.

History adapters implement `DataStoreBackupWriter` / `DataStoreBackupReader`.
They must provide their own durable storage and integrity checks. Export rejects
missing creation history, conflicting or unordered revisions, and revisions
outside its snapshot boundary instead of publishing an incomplete history.

## Resource limits

| Operation | Default / ceiling |
| --- | --- |
| State export batch | 250 records; configurable up to 1,000 |
| State chunk | At most 1 MiB of records plus allocation metadata, including any single record |
| History export per record | 10,000 retained versions by default; configurable through the export overload |
| History restore staging | 64 MiB and 100,000 records by default; configurable with `DataStoreRestoreOptions` |
| History replay group | At most 1 MiB and 1,000 record versions, further limited by backend capabilities |

State capture checks the full atomic replay request before publishing a chunk
and splits batches where possible. A single oversized state record fails. History
replay cannot split a complete source-version group. `maxReplayBytes` defaults to
8 MiB for API compatibility, but does not override the common or backend ceilings.
Backend transaction/work limits can still reject a batch atomically at execution.

State restore streams batches without whole-archive staging. History restore
stages each reader once and replays groups in global source-version order. It
releases old unique claims and validates final ownership inside each transaction,
so transfers and cyclic swaps do not depend on record order.

Custom history targets must implement `VersionedRestoreDataStore`; unsupported
targets fail before replay. Remote negotiates the equivalent server capability.
Neither restore is globally transactional, and neither resumes an old replication
lineage or rewinds external consumers.

## Related references

- [Remote state transport](../../remote/docs/protocol.md#state-archive-transport): framing, limits, permissions and cancellation.
- [RocksDB recovery](../../rocksdb/documentation/backups.md): physical backups and engine metadata.
- [FoundationDB recovery](../../foundationdb/docs/backups.md): cluster user-keyspace recovery and validation.
