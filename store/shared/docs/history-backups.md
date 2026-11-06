# Portable history backups

Use a history backup when earlier record revisions must remain queryable after
restore. For current records or backend transfer without earlier revisions, use
[state backup](state-backups.md).

## Requirements

- Enable `keepAllVersions` on the source before data is written. Enabling it later
  cannot recreate missing history.
- Open an isolated target with compatible models and `keepAllVersions = true`.
  By default, the addressed target models must be empty.
- Coordinate hard deletion with the entire backup. It can erase history even
  after a snapshot version has been captured; use soft deletion until export ends.

## Export and restore

Your application implements `DataStoreBackupWriter` and `DataStoreBackupReader`
for its storage format. Keep output private until the writer's `complete()`
succeeds, and make the reader reject incomplete or corrupt input. The binary
`StateBackupArchiveWriter` is for state archives and cannot store history archives.

With an open source, target and those application-owned adapters:

```kotlin
import maryk.datastore.shared.backup
import maryk.datastore.shared.restore

val manifest = source.backup(historyWriter)
// Later, open a reader for the completed archive.
val result = target.restore(historyReader)
```

Keep the target unavailable to application traffic until restore finishes and
application validation succeeds. Verify current values, historical reads, indexed
queries, soft deletes and unique constraints before switching to it.

Restore replays complete source-version groups atomically, including groups across
models. The whole restore is not one transaction. If any later group fails,
discard the partial target and start again.

## Choosing a snapshot version

Memory, RocksDB and IndexedDB can capture their own authoritative Maryk boundary.
Remote negotiates that ability with its backing store. If automatic capture is
unavailable, export fails rather than combining reads from different points in time.

FoundationDB requires an explicit Maryk version obtained through your application's
cluster coordination. A process-local clock is not sufficient. Pass that boundary
when exporting:

```kotlin
source.backup(historyWriter, snapshotVersion = authoritativeMarykVersion)
```

`authoritativeMarykVersion` above is supplied by the application; it is not an FDB
engine read version or a timestamp guessed by the caller. If you cannot establish
that boundary, use [FoundationDB's native recovery tools](../../foundationdb/docs/backups.md)
or a [state export](state-backups.md) that fits its capture window.

## Size limits

History restore stages and validates the input before replay. Its defaults are
64 MiB of serialized input and 100,000 staged records. `DataStoreRestoreOptions`
can adjust those staging limits. Export defaults to at most 10,000 retained
versions per record; `batchSize` limits records per chunk, not one record's history.

A complete source-version group must fit the backend's atomic limits and the
common limits of 1 MiB and 1,000 record versions. A group cannot be split. Raising
staging limits does not raise that replay ceiling. See the
[reference](backup-reference.md#resource-limits) before tuning large restores.

## Using Remote

Both peers and the backend must support atomic version-group restore. Source
snapshot capture also requires a compatible server, unless the application supplies
an authoritative boundary. Upgrade missing capabilities instead of falling back to
individual update requests.

A lost HTTP acknowledgement may follow a committed group. Restore into a
disposable target and restart after an uncertain outcome. See
[remote history restore](../../remote/docs/client.md#atomic-backup-restore) for
permissions and transport behavior.
