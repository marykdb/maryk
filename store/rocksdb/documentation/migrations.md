# RocksDB Migrations

This guide documents migration behavior in `RocksDBDataStore` and how to operate it safely.

Preferred API:
- pass migration settings as `migrationConfiguration = MigrationConfiguration(...)`
- flat migration arguments on `RocksDBDataStore.open(...)` still work for compatibility

## When migration is required

On open, the store compares persisted model definitions with configured models.

No migration required (automatic):
- new model
- safe property additions
- new indexes on compatible existing properties (index backfill)
- relaxed validation

Migration required:
- incompatible property type changes
- removals/renames without alternatives
- stricter validation changes that break compatibility

## Handler contract

All migration hooks receive `MigrationContext<RocksDBDataStore>` and return `MigrationOutcome`:
- `Success`: phase complete
- `Partial`: progress persisted, continue with provided cursor/message
- `Retry`: retry same phase (optional delay)
- `Fatal`: migration fails

Available hooks:
- `migrationExpandHandler`
- `migrationHandler` (`Backfill`)
- `migrationVerifyHandler`
- `migrationContractHandler`

## Runtime phases

Runtime phase order:
1. `Expand`
2. `Backfill`
3. `Verify`
4. `Contract`

Progress is persisted per model with `MigrationState` (phase, status, attempt, cursor, message, version range).
If the process restarts, migration resumes from persisted state.

Current handler hooks:
- `Expand`: runs `migrationExpandHandler`
- `Backfill`: runs `migrationHandler`
- `Verify`: runs `migrationVerifyHandler`
- `Contract`: runs `migrationContractHandler`

## Dependency ordering and cycles

Migrations are executed in dependency order across models.
- A model migrates after models it references.
- Cycles are rejected before execution with `MigrationException`.

This prevents dependent models from migrating against stale referenced schemas.

## Startup handoff to background

Use:
- `migrationConfiguration.migrationStartupBudgetMs`
- `migrationConfiguration.continueMigrationsInBackground = true`

Behavior:
- If budget is exceeded, migration continues in background.
- Requests to pending models are blocked until completion.

## Operator control API

- `pendingMigrations()`
- `migrationStatus(modelId)`
- `migrationStatuses()`
- `awaitMigration(modelId)`
- `pauseMigration(modelId)`
- `resumeMigration(modelId)`
- `cancelMigration(modelId, reason)`

These APIs are intended for operational tooling and rollout control.
`cancelMigration` is terminal for the current store instance: the model stays blocked and you must reopen the store to resume from persisted state.

Operator semantics:
- `awaitMigration(modelId)` returns normally when migration completes successfully.
- `awaitMigration(modelId)` completes exceptionally with `MigrationException` if migration fails or is canceled.
- `pauseMigration(modelId)` only affects pending/background progress. It does not interrupt a currently running phase step.
- While paused, the model remains request-blocked.
- `resumeMigration(modelId)` allows the next background loop iteration to continue from the persisted `MigrationState`.
- After `cancelMigration`, the current store instance keeps the model blocked. Reopening the store resumes from the persisted phase/cursor.

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
