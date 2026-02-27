# RocksDB Migrations

This guide documents migration behavior in `RocksDBDataStore` and how to operate it safely.

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

`migrationHandler` receives `MigrationContext<RocksDBDataStore>` and returns `MigrationOutcome`:
- `Success`: phase complete
- `Partial`: progress persisted, continue with provided cursor/message
- `Retry`: retry same phase (optional delay)
- `Fatal`: migration fails

Optional `migrationVerifyHandler` runs after backfill success and before model readiness.

## Runtime phases

Runtime phase order:
1. `Expand`
2. `Backfill`
3. `Verify`
4. `Contract`

Progress is persisted per model with `MigrationState` (phase, status, attempt, cursor, message, version range).
If the process restarts, migration resumes from persisted state.

## Dependency ordering and cycles

Migrations are executed in dependency order across models.
- A model migrates after models it references.
- Cycles are rejected before execution with `MigrationException`.

This prevents dependent models from migrating against stale referenced schemas.

## Startup handoff to background

Use:
- `migrationStartupBudgetMs`
- `continueMigrationsInBackground = true`

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

## Lease behavior

Default lease is `RocksDBLocalMigrationLease`.
- Prevents duplicate runners in-process.
- Cross-process migration lease is usually unnecessary because RocksDB lock already enforces a single opener.

You can inject `migrationLease` for custom orchestration.

## Observability

Retry controls:
- `migrationRetryPolicy.maxAttempts`
- `migrationRetryPolicy.maxRetryOutcomes`

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
- default: emitted to `migrationAuditEventReporter` (default reporter logs line output)
- optional persistence: set `persistMigrationAuditEvents = true`
- read persisted history: `migrationAuditEvents(modelId, limit)`

## Recommended rollout pattern

1. Deploy with `continueMigrationsInBackground = true`.
2. Set conservative retry policy.
3. Watch `migrationStatuses()` and `migrationMetrics()`.
4. Use `pause/resume/cancel` only for operator interventions.
5. Enable persisted audit events only when durable per-model history is required.
