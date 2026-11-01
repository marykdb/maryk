# Model migration contract

Model migrations change an application's schema. They are separate from the
[storage format upgrade](storage-format-upgrades.md) performed when opening an
older persistent layout. Read the engine guide for its ownership and startup rules.

## Schema compatibility

Stores compare persisted model definitions with configured models at opening.
New models, compatible property/index additions and relaxed validation can be
handled automatically; incompatible types, removals and stricter constraints need
a migration. Keep reference key widths and dependent definitions compatible.

## Phases and outcomes

Configure hooks in `MigrationConfiguration`. Each receives a `MigrationContext`
for the concrete store, including stored/target models, attempt and previous state.

| Phase | Hook |
| --- | --- |
| Expand | `migrationExpandHandler` |
| Backfill | `migrationHandler` |
| Verify | `migrationVerifyHandler` |
| Contract | `migrationContractHandler` |

Hooks return `MigrationOutcome.Success`, `Partial`, `Retry` or `Fatal`.
Persisted progress includes phase, status, attempt, cursor, message and version
range. `MigrationContext.previousState` supplies resumable progress. IndexedDB
pauses opening on `Partial`; RocksDB and FoundationDB also expose startup/background
controls described in their engine guides. Make callback side effects safe to retry.

## Target identity and recovery

Pending application migrations are bound to the complete target schema, including
dependent definitions and reference widths. The checkpoint and its target digest
are persisted together. Reopening with the source schema or a different target,
even one using the same version number, fails before opening callbacks run. Resume
with the original target; changing a version string cannot authorize skipping
completed phases.

Older unfinished checkpoints without a target digest cannot establish which schema
already rewrote their rows. They require authoritative recovery of the intended
target and progress; do not delete the checkpoint to bypass the guard. A final
marker can be cleaned automatically only when the independently published schema
proves that its target was completed. Normal state serialization and storage-format
versions are unchanged.

## Operator controls

RocksDB and FoundationDB expose `pendingMigrations`, `migrationStatus`,
`migrationStatuses`, `awaitMigration`, `pauseMigration`, `resumeMigration` and
`cancelMigration`. These controls apply to model migrations; they are not a way
to bypass an unfinished opening storage-format conversion.

Operator semantics:
- `awaitMigration(modelId)` returns normally when migration completes successfully.
- `awaitMigration(modelId)` completes exceptionally with `MigrationException` if migration fails or is canceled.
- `pauseMigration(modelId)` only affects pending/background progress. It does not interrupt a currently running phase step.
- While paused, the model remains request-blocked.
- `resumeMigration(modelId)` allows the next background loop iteration to continue from the persisted `MigrationState`.
- After `cancelMigration`, the current store instance keeps the model blocked. Reopening the store resumes from the persisted phase/cursor.

## Observability

Use `migrationMetrics`, status payloads and the configured audit reporter to
monitor progress and retries. Durable audit history is opt-in through
`persistMigrationAuditEvents`; inspect it with `migrationAuditEvents`.
Engine-specific guides describe lease ownership, transaction fences, dependency
publication and background continuation.

## Engine guides

- [RocksDB](../../rocksdb/documentation/migrations.md)
- [FoundationDB](../../foundationdb/docs/migrations.md)
- [IndexedDB](../../indexeddb/docs/migrations.md)
