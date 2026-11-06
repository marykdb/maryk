# Portable state backups

Use a state backup to save current records or move them between compatible Maryk
backends. It preserves record IDs, values, creation/update versions, soft deletes
and incrementing-map allocation counters. Earlier revisions are not included.

For engine recovery or retained history, start with [choosing a backup](backup-restore.md).

## Requirements

- Check `source.supportsStateBackup`. Built-in local stores support it; Remote
  requires compatible client, server and backing-store capabilities.
- Finish schema and index migrations before exporting.
- Open a new target with compatible models and `keepAllVersions = false`.
  Its addressed models must be empty, including soft-deleted records.

Source history can be enabled or disabled. The export includes all registered models.

## Create a backup

Maryk provides the binary archive encoder; your application provides durable byte
storage. In this example, `source` is an open datastore and `privateOutput` is your
storage adapter. Keep partial output private and publish it atomically on success.

```kotlin
import maryk.datastore.shared.StateBackupArchiveWriter
import maryk.datastore.shared.backupState

val writer = StateBackupArchiveWriter(
    dataModelsById = source.dataModelsById,
    writeBytes = { bytes -> privateOutput.append(bytes) },
    publish = { privateOutput.syncAndPublish() },
)
source.backupState(writer)
```

The default batch is 250 records, with at most 1 MiB of records and allocation
metadata per chunk. A single record must also fit. `DataStoreStateBackupOptions`
can adjust the batch size up to 1,000 records or lower the byte limit. Oversized
data fails rather than losing fields.

## Restore and switch

Here, `target` is the empty, history-disabled datastore opened above, and `input`
is your archive reader. Its `readUpTo` returns up to the requested byte count,
including short reads, and an empty array only at EOF.

```kotlin
import maryk.datastore.shared.openStateBackupArchive
import maryk.datastore.shared.restoreState

val reader = openStateBackupArchive(
    dataModelsById = target.dataModelsById,
    readBytes = { maximumBytes -> input.readUpTo(maximumBytes) },
)
val result = target.restoreState(reader)
```

Keep application traffic away from the target until restore and application
validation succeed. Check known records, counts, indexed queries and unique
constraints, then switch to the recovered store.

Each batch commits atomically, including allocation counters and original version
metadata. The whole archive is not one transaction. On failure, discard the target
and retry into a new one; do not merge a retry into the partial result.

## Capture limits that affect operation

An export uses one consistent view across all models and pages. Memory and
IndexedDB can delay writes while exporting; keep the destination fast. Never
execute requests against the source from a writer callback, because the callback
can end up waiting on its own export.

FoundationDB has a short engine snapshot window. If capture cannot finish within
it, the export fails. Smaller pages or an isolated cluster do not extend that
window. Use [native FoundationDB backups](../../foundationdb/docs/backups.md) for
recovery when portable state export is too slow.

The archive verifies checksums and completion. Checksums do not authenticate a
backup against deliberate replacement, and exported values may be decrypted.
Protect the archive and transport; discard incomplete output.

## Using Remote

The same calls work with `RemoteDataStore`. Export runs one capture on the server;
restore sends bounded batches to the backing store. Upgrade both peers when the
state capability is unavailable. FoundationDB's capture window still applies.

The server requires `StateBackup` permission for every exported model, and
`StateRestore` plus the relevant `ProcessUpdate` permissions for import. Ordinary
query access alone does not grant backup access.

Closing the remote store cancels its state operations and leaves an externally
supplied HTTP client open. Cleanup is best effort if the network is unavailable.
An interrupted restore response may follow a committed batch: discard the target
and restart instead of retrying that batch blindly. An interrupted export must
also restart; it does not resume automatically.

See [remote client recovery](../../remote/docs/client.md#state-backup-and-restore)
for connection behavior and the [protocol reference](../../remote/docs/protocol.md#state-archive-transport)
for wire details. Custom writers and store adapters should read the
[portable backup reference](backup-reference.md).
