# IndexedDB migrations

On open, stored model definitions are compared with the configured models.
Compatible changes are handled automatically; incompatible changes require a
migration handler. A `Partial` outcome persists progress and pauses opening;
reopen with the same target to resume through `MigrationContext.previousState`.
Version-update callbacks run after required index backfills and can query them.

## Opening an older store

Read the [storage upgrade guide](../../shared/docs/storage-format-upgrades.md)
before opening a released store. Engine-specific collection and incrementing-map
recovery requirements are in the [storage layout](./storage.md#incrementing-map-lifetime-metadata-and-opening-recovery).

## Pending migrations and other browser contexts

Target-schema matching and unfinished-checkpoint recovery follow the
[shared migration contract](../../shared/docs/migrations.md#target-identity-and-recovery).

Existing browser contexts reject both reads and writes after a pending migration
or schema change. Read responses are checked again before publication, and stale
live listeners stop before decoding another schema's journal entries.
