# FoundationDB Migrations

This guide documents migration behavior in `FoundationDBDataStore` and operational controls.

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

`migrationHandler` receives `MigrationContext<FoundationDBDataStore>` and returns `MigrationOutcome`:
- `Success`
- `Partial`
- `Retry`
- `Fatal`

Optional `migrationVerifyHandler` executes after backfill and before model readiness.

## Runtime phases

Execution phases:
1. `Expand`
2. `Backfill`
3. `Verify`
4. `Contract`

Progress is persisted via `MigrationState` per model (phase/status/attempt/cursor/message).
Restart resumes from persisted state.

## Dependency ordering and cycles

Model migrations are ordered by dependency graph.
- Referenced models migrate first.
- Dependency cycles are detected and rejected up front with `MigrationException`.

## Startup budget and background continuation

Use:
- `migrationStartupBudgetMs`
- `continueMigrationsInBackground = true`

Behavior:
- Startup can hand migration over to background.
- Pending models remain request-blocked until completion.

## Operator APIs

- `pendingMigrations()`
- `migrationStatus(modelId)`
- `migrationStatuses()`
- `awaitMigration(modelId)`
- `pauseMigration(modelId)`
- `resumeMigration(modelId)`
- `cancelMigration(modelId, reason)`

## Lease behavior (distributed)

Default lease is `FoundationDBMigrationLease`.
- Lease key per model in metadata subspace
- owner token
- TTL via `migrationLeaseTimeoutMs`
- heartbeat via `migrationLeaseHeartbeatMs`
- automatic takeover after TTL expiry

Use custom `migrationLease` only if external orchestrator semantics are needed.

## Observability

Retry thresholds:
- `migrationRetryPolicy.maxAttempts`
- `migrationRetryPolicy.maxRetryOutcomes`

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
- default sink: `migrationAuditEventReporter` (default line logger)
- optional persisted audit store: `persistMigrationAuditEvents = true`
- query persisted events: `migrationAuditEvents(modelId, limit)`

## Operational rollout pattern

1. Enable background continuation for long-running migrations.
2. Set retry thresholds for bounded failure handling.
3. Monitor runtime status/metrics.
4. Intervene with pause/resume/cancel only when required.
5. Keep persisted audit logs opt-in; use default reporter for baseline observability.
