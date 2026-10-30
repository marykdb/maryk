# FoundationDB Migrations

This guide documents migration behavior in `FoundationDBDataStore` and operational controls.

Preferred API:
- pass migration settings as `migrationConfiguration = MigrationConfiguration(...)`
- pass distributed lease tuning as `migrationLeaseConfiguration = FoundationDBMigrationLeaseConfiguration(...)`
- flat migration arguments on `FoundationDBDataStore.open(...)` still work for compatibility

## When migration is required

On startup, the store compares stored model definitions with configured models.

No migration required (automatic):
- new model
- safe property additions
- compatible index additions (with backfill)
- relaxed validation

Migration required:
- incompatible type changes
- removals/renames without compatibility aliases
- stricter constraints that break compatibility

## Handler contract

All migration hooks receive `MigrationContext<FoundationDBDataStore>` and return `MigrationOutcome`:
- `Success`
- `Partial`
- `Retry`
- `Fatal`

Available hooks:
- `migrationExpandHandler`
- `migrationHandler` (`Backfill`)
- `migrationVerifyHandler`
- `migrationContractHandler`

## Runtime phases

Execution phases:
1. `Expand`
2. `Backfill`
3. `Verify`
4. `Contract`

Progress is persisted via `MigrationState` per model (phase/status/attempt/cursor/message).
Restart resumes from persisted state.

Current handler hooks:
- `Expand`: runs `migrationExpandHandler`
- `Backfill`: runs `migrationHandler`
- `Verify`: runs `migrationVerifyHandler`
- `Contract`: runs `migrationContractHandler`

For a compatible index addition, `versionUpdateHandler` runs while the model remains fenced as `Rebuilding`. Writes through the same `FoundationDBDataStore` are allowed only after each transaction proves ownership of that exact rebuild fence. Other open stores remain blocked until the hook succeeds and the definition and `Ready` epoch publish together.

## Dependency ordering and cycles

Model migrations are ordered by dependency graph.
- Referenced models migrate first.
- A dependent handler does not start until each referenced model has completed finalization and definition publication.
- Dependency cycles are detected and rejected up front with `MigrationException`.

## Released storage recovery limits

The released unversioned layout upgrades directly to storage format 2 during the
unreleased 0.14 transition. All collection storage is inspected, including models
without floating-point fields. Ordinary empty collection counts remain `[0]`.
Released FoundationDB also wrote `[0]` for historic deletions. A count at the
record's original creation version proves a present empty collection; a later
historic zero can be intrinsically ambiguous. Deleting a collection and setting
it empty can produce identical historic bytes, even after later writes restore
the same current state. The upgrade stops in Expand before moving source rows,
with model, qualifier and version evidence and recovery guidance. It does not
infer presence from aggregate counts. New historic deletions use an empty payload.

Incrementing-map recovery separately establishes each maximum allocated key over
the complete record lifetime, including replaced maps and removed ancestors.
Creation-only records are safe without history. Edited records require continuous
retained history or external verification of all allocation maxima. Raw historic
qualifiers seed model metadata watermarks before Backfill, and retries never lower
a recovered maximum. Matching historic creation and latest frontier checks the
retained bounds; it does not prove that no interval was erased. History must have
been enabled continuously and must not have been pruned. Otherwise use an
authoritative application log or backup; current keys cannot establish erased
allocations and opening fails with the affected model and record.

Model metadata stores maxima below `maryk.incmap/<modelId>/` plus the scoped
record key and canonical map qualifier. A separate
`maryk.incmap.verified/<modelId>/` record attests complete per-record recovery.
The attestation is valid only after all maxima have been established; setting it
alone does not repair erased allocations. Hard deletion clears both namespaces.
Intermediate development checkpoints predating these proof rules are rejected
without changing storage format 2; they are not a released compatibility baseline.
An application migration that changes record identities, map qualifiers or the
incrementing key definition must remap lifetime watermarks with the data.

## Startup budget and background continuation

Use:
- `migrationConfiguration.migrationStartupBudgetMs`
- `migrationConfiguration.continueMigrationsInBackground = true`

Behavior:
- Startup can hand migration over to background.
- Pending models remain request-blocked until completion.
- Dependents waiting on a background migration remain blocked too; they do not delay opening the store or access to unrelated models.
- Startup clock recovery reads raw version metadata before resuming an unfinished schema rebuild. Request-level schema fencing remains active.

## Operator APIs

- `pendingMigrations()`
- `migrationStatus(modelId)`
- `migrationStatuses()`
- `awaitMigration(modelId)`
- `pauseMigration(modelId)`
- `resumeMigration(modelId)`
- `cancelMigration(modelId, reason)`

`cancelMigration` is terminal for the current store instance: the model stays blocked and you must reopen the store to resume from persisted state.

Operator semantics:
- `awaitMigration(modelId)` returns normally when migration completes successfully.
- `awaitMigration(modelId)` completes exceptionally with `MigrationException` if migration fails or is canceled.
- `pauseMigration(modelId)` only affects pending/background progress. It does not interrupt a currently running phase step.
- While paused, the model remains request-blocked.
- `resumeMigration(modelId)` allows the next background loop iteration to continue from the persisted `MigrationState`.
- After `cancelMigration`, the current store instance keeps the model blocked. Reopening the store resumes from the persisted phase/cursor.

## Lease behavior (distributed)

Default lease is `FoundationDBMigrationLease`.
- Lease key per model in metadata subspace
- owner token
- monotonically increasing fencing token, checked in every datastore mutation transaction submitted by a migration or version hook, including mutations for another registered model
- TTL via `migrationLeaseConfiguration.migrationLeaseTimeoutMs`
- heartbeat via `migrationLeaseConfiguration.migrationLeaseHeartbeatMs`
- automatic takeover after TTL expiry

Use custom `migrationLease` only if external orchestrator semantics are needed.

The fencing token protects FoundationDB transactions made through the datastore. It cannot roll back or fence arbitrary external side effects. Hooks that call external systems must be idempotent or use an external coordinator/fencing token appropriate to that system; do not rely on host wall-clock lease expiry alone for those effects.

## Observability

Retry thresholds:
- `migrationConfiguration.migrationRetryPolicy.maxAttempts`
- `migrationConfiguration.migrationRetryPolicy.maxRetryOutcomes`

Status payload includes:
- state
- phase
- attempt
- last error
- cursor presence
- retry count
- ETA estimate

Metrics:
- `migrationMetrics(modelId)` / `migrationMetrics()`

Audit:
- default sink: `migrationConfiguration.migrationAuditEventReporter` (default line logger)
- optional persisted audit store: `migrationConfiguration.persistMigrationAuditEvents = true`
- query persisted events: `migrationAuditEvents(modelId, limit)`

## Operational rollout pattern

1. Enable background continuation for long-running migrations.
2. Set retry thresholds for bounded failure handling.
3. Monitor runtime status/metrics.
4. Intervene with pause/resume/cancel only when required.
5. Keep persisted audit logs opt-in; use default reporter for baseline observability.

## Minimal four-phase example

```kotlin
FoundationDBDataStore.open(
    keepAllVersions = true,
    directoryPath = listOf("maryk", "app"),
    dataModelsById = mapOf(1u to Account),
    migrationConfiguration = MigrationConfiguration(
        migrationExpandHandler = { context ->
            // Prepare compatibility before data rewrite
            MigrationOutcome.Success
        },
        migrationHandler = { context ->
            // Backfill existing rows
            MigrationOutcome.Success
        },
        migrationVerifyHandler = { context ->
            // Validate rewritten data
            MigrationOutcome.Success
        },
        migrationContractHandler = { context ->
            // Final cleanup after verification
            MigrationOutcome.Success
        }
    )
)
```


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

Already-open instances also check persisted migration progress inside their native
read and write transactions. They reject access while a transition is pending and
require reopening after the target schema is published. Migration callbacks retain
access under their migration ownership guard.

Synchronous phase callbacks can use the normal datastore request API. Encryption,
durable clocks and the cluster log are initialized before its actor starts, and
failed or canceled startup closes that actor. Migration request access belongs to
one store instance; calling another store from a callback does not bypass that
store's migration guards. Cluster log readers check the same schema fence before
decoding updates.
