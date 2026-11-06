# Backup and restore

Start here to choose a backup. Use an engine backup to recover a complete database,
or a portable backup to move application data between compatible Maryk stores.

## Which backup should I use?

| I need to… | Use | Read next |
| --- | --- | --- |
| Recover a RocksDB database | Native RocksDB backup | [RocksDB recovery guide](../../rocksdb/documentation/backups.md) |
| Recover a FoundationDB deployment | FoundationDB's backup tools | [FoundationDB recovery guide](../../foundationdb/docs/backups.md) |
| Save current records or transfer them to another backend | Portable state backup | [State backup guide](state-backups.md) |
| Preserve and restore earlier record revisions as well | Portable history backup | [History backup guide](history-backups.md) |

Native backups preserve engine state. Portable restore rebuilds application
records and indexes; it does not clone the engine's operational metadata.

## Where do portable backups work?

| Store | State backup | History backup |
| --- | --- | --- |
| Memory (`InMemoryDataStore`) | Yes | Yes |
| IndexedDB (browser storage) | Yes, with atomic support | Yes, with atomic support |
| RocksDB | Yes | Yes |
| FoundationDB | Yes, within its short snapshot window | Yes, with an application-coordinated Maryk snapshot version |
| Remote | Yes, with a compatible server and backing store | Yes, with compatible snapshot and restore capabilities |

Remote inherits its backing store's limits. Check `supportsStateBackup` before
using state backup; an older remote server reports it as unsupported.

## State or history?

| | State | History |
| --- | --- | --- |
| API | `backupState` / `restoreState` | `backup` / `restore` |
| Saves | Current records, including soft deletes | Current and earlier retained values |
| Source `keepAllVersions` | Either setting | Must be enabled before writes |
| Target `keepAllVersions` | Must be disabled | Must be enabled |

Choose state unless you need earlier revisions. Keeping creation/update versions
in a state backup does not recreate those earlier values.

## Before restoring

Restore into a new, isolated target with compatible model definitions. Read the
whole archive, check the recovered data, and only then switch application traffic.
A failed restore can leave earlier batches applied: discard that target and retry.

Protect backup storage and transport as production data. Keep encryption keys
separately, store recovery copies off-device, and rehearse restores. Portable
backup scheduling, retention and durable output storage belong to the application.

For adapter contracts, consistency details and resource limits, see the
[portable backup reference](backup-reference.md).
