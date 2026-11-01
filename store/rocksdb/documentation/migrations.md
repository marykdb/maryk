# RocksDB Migrations

This guide documents migration behavior in `RocksDBDataStore` and how to operate it safely.

Preferred API:
- pass migration settings as `migrationConfiguration = MigrationConfiguration(...)`
- flat migration arguments on `RocksDBDataStore.open(...)` still work for compatibility

## When migration is required

Follow the [shared migration contract](../../shared/docs/migrations.md) for schema
compatibility, hook outcomes and target recovery.

## Handler contract

Hooks receive `MigrationContext<RocksDBDataStore>` and return `MigrationOutcome`.
See [phases and outcomes](../../shared/docs/migrations.md#phases-and-outcomes) for hook ordering.

## Runtime phases

Expand, Backfill, Verify and Contract run in order, with persisted progress.
See [phases and outcomes](../../shared/docs/migrations.md#phases-and-outcomes) and
[target identity](../../shared/docs/migrations.md#target-identity-and-recovery).

## Dependency ordering and cycles

Migrations are executed in dependency order across models.
- A model migrates after models it references, including their index backfill,
  version-update hook writes, and schema metadata publication. Dependent backfills
  therefore read the finalized dependency data.
- Cycles are rejected before execution with `MigrationException`.

This prevents dependent models from migrating against stale referenced schemas.
Compatible legacy index-format conversion runs before dependency version hooks use
normal requests. When combining a legacy index-format upgrade with incompatible
dependent schema changes, first open with the compatible source models to convert
the index format, then apply the dependent schema migrations. Requests remain
blocked while an incompatible legacy format has not yet been converted.

Legacy index rebuilds reconstruct each retained record version and compare complete
index-entry sets. Collection members, fan-out and composite indexes keep independent
add/remove boundaries, including field removal and soft-delete/undelete transitions.
A later change to one indexed value cannot remove another retained value from
historical scans. Current indexes exclude soft-deleted records.

The storage-format upgrade also converts defaults embedded in persisted source
model and dependency definitions, including released packed nanosecond DateTime
collections. Each original schema pair and its format marker publish atomically;
reopening after interruption reads converted pairs with the current decoder while
unconverted pairs retain the legacy decoder. Source names and schema versions are
preserved until the separate application migration publishes a replacement.

## Released storage recovery limits

The unversioned released layout upgrades directly to storage format 2 in the
unreleased 0.14 transition. The upgrade inventories collection storage even when
a model has no floating-point properties. Ordinary empty counts remain `[0]`.
An old retained current `[0]` can also mean deletion: its original creation version
proves an empty collection present, while an exact-version historic empty payload
proves deletion. Other current zeros require authoritative recovery. The upgrade
stops during Expand, before moving source rows, and reports the model, qualifier
and version rather than guessing from aggregate counts.

Incrementing maps require their maximum allocated key over the record's complete
lifetime, including removed map instances and removed ancestors. Current keys
cannot prove that maximum after a replacement or removal. Creation-only records
need no historic recovery. Edited records require continuous retained history or
an externally verified recovery of every allocation maximum. Recovery scans raw
historic qualifiers without interpreting ambiguous value payloads and persists
maxima before Backfill. Interrupted scans only increase the recovered maxima.

Retained history must have been enabled continuously and must not have been
pruned. Matching the creation marker and latest frontier validates the retained
bounds; it cannot prove that no intermediate interval was erased. If history was
disabled or pruned, recover from an authoritative application log or backup.
Without sufficient evidence, opening fails with the model and record identity;
it does not silently reuse a possibly erased allocation key.

Allocation maxima use the model metadata family under
`maryk.incmap/<modelId>/` followed by the scoped record key and canonical map
qualifier. `maryk.incmap.verified/<modelId>/` records a complete per-record recovery
attestation. Writing an attestation alone does not recover missing maxima; it is
valid only after the complete lifetime has been established. Hard deletion clears
both kinds of metadata. Older intermediate development checkpoints that predate
these proof rules are rejected; they are not a released compatibility baseline.
Application migrations that change record identities, incrementing-map qualifiers
or key definitions must remap these lifetime watermarks with the corresponding
data. A storage-format opening does not perform an application schema migration.

## Startup handoff to background

Use:
- `migrationConfiguration.migrationStartupBudgetMs`
- `migrationConfiguration.continueMigrationsInBackground = true`

Behavior:
- If budget is exceeded, migration continues in background.
- Requests to pending models are blocked until completion.

## Operator control API

Use the [shared operator API](../../shared/docs/migrations.md#operator-controls)
for status, waiting, pause, resume and cancellation. A canceled store instance
must reopen to resume the persisted migration.

## Lease behavior

Default lease is `RocksDBLocalMigrationLease`.
- Prevents duplicate runners in-process.
- Cross-process migration lease is usually unnecessary because RocksDB lock already enforces a single opener.

You can inject `migrationLease` for custom orchestration.

## Observability

Retry controls:
- `migrationConfiguration.migrationRetryPolicy.maxAttempts`
- `migrationConfiguration.migrationRetryPolicy.maxRetryOutcomes`

Status payload includes:
- runtime state
- current phase
- attempt
- last error
- cursor presence
- retry count
- ETA estimate

Metrics:
- `migrationMetrics(modelId)` / `migrationMetrics()`

Audit events:
- default: emitted to `migrationConfiguration.migrationAuditEventReporter` (default reporter logs line output)
- optional persistence: set `migrationConfiguration.persistMigrationAuditEvents = true`
- read persisted history: `migrationAuditEvents(modelId, limit)`

## Recommended rollout pattern

1. Deploy with `migrationConfiguration.continueMigrationsInBackground = true`.
2. Set conservative retry policy.
3. Watch `migrationStatuses()` and `migrationMetrics()`.
4. Use `pause/resume/cancel` only for operator interventions.
5. Enable persisted audit events only when durable per-model history is required.

## Minimal four-phase example

```kotlin
RocksDBDataStore.open(
    keepAllVersions = true,
    relativePath = "path/to/store",
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

Migration request access belongs to the callback's store instance. Calling another
RocksDB store from a callback does not bypass that store's pending migration guard.
