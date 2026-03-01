package maryk.datastore.shared.migration

import maryk.core.models.migration.MigrationAuditEvent
import maryk.core.models.migration.MigrationAuditEventType
import maryk.core.models.migration.MigrationMetrics
import maryk.core.models.migration.MigrationPhase
import maryk.core.models.migration.MigrationRuntimeState
import maryk.core.models.migration.MigrationRuntimeStatus
import maryk.core.models.migration.MigrationState
import maryk.core.models.migration.MigrationStateStatus
import maryk.core.models.migration.normalizedRuntimePhase
import maryk.core.models.migration.remainingRuntimePhaseCount

data class MigrationRuntimeDetails(
    val migrationId: String,
    val phase: MigrationPhase?,
    val attempt: UInt?,
    val lastError: String?,
    val hasCursor: Boolean?,
    val retryCount: UInt = 0u,
    val startedAtMs: Long,
    val lastUpdateAtMs: Long,
    val averageStepMs: Long? = null,
    val etaMs: Long? = null,
)

fun projectMigrationRuntimeStatus(
    modelId: UInt,
    reason: String,
    detailsByModelId: Map<UInt, MigrationRuntimeDetails>,
    canceledModelIds: Set<UInt>,
    pausedModelIds: Set<UInt>,
): MigrationRuntimeStatus {
    val details = detailsByModelId[modelId]
    val state = when {
        canceledModelIds.contains(modelId) -> MigrationRuntimeState.Canceled
        pausedModelIds.contains(modelId) -> MigrationRuntimeState.Paused
        details?.lastError != null || reason.contains("failed", ignoreCase = true) -> MigrationRuntimeState.Failed
        else -> MigrationRuntimeState.Running
    }
    return MigrationRuntimeStatus(
        state = state,
        message = reason,
        phase = details?.phase,
        attempt = details?.attempt,
        lastError = details?.lastError,
        hasCursor = details?.hasCursor,
        etaMs = details?.etaMs,
    )
}

fun updatedMigrationRuntimeDetails(
    current: Map<UInt, MigrationRuntimeDetails>,
    modelId: UInt,
    state: MigrationState,
    nowMs: Long,
): Map<UInt, MigrationRuntimeDetails> {
    val previous = current[modelId]
    val stepDurationMs = previous?.lastUpdateAtMs?.let { last -> (nowMs - last).coerceAtLeast(0) }
    val averageStepMs = when {
        previous?.averageStepMs == null -> stepDurationMs
        stepDurationMs == null -> previous.averageStepMs
        else -> ((previous.averageStepMs * 3L) + stepDurationMs) / 4L
    }
    val phase = state.phase.normalizedRuntimePhase()
    return current + (
        modelId to MigrationRuntimeDetails(
            migrationId = state.migrationId,
            phase = phase,
            attempt = state.attempt,
            lastError = if (state.status == MigrationStateStatus.Failed) state.message else null,
            hasCursor = state.cursor != null,
            retryCount = if (state.status == MigrationStateStatus.Retry) (previous?.retryCount ?: 0u) + 1u else previous?.retryCount ?: 0u,
            startedAtMs = previous?.startedAtMs ?: nowMs,
            lastUpdateAtMs = nowMs,
            averageStepMs = averageStepMs,
            etaMs = averageStepMs?.let { avg -> avg * phase.remainingRuntimePhaseCount().toLong() },
        )
    )
}

fun createMigrationAuditEvent(
    nowMs: Long,
    modelId: UInt,
    migrationId: String,
    type: MigrationAuditEventType,
    phase: MigrationPhase? = null,
    attempt: UInt? = null,
    message: String? = null,
) = MigrationAuditEvent(
    timestampMs = nowMs,
    modelId = modelId,
    migrationId = migrationId,
    type = type,
    phase = phase?.normalizedRuntimePhase(),
    attempt = attempt,
    message = message,
)

fun updatedMigrationMetrics(
    current: Map<UInt, MigrationMetrics>,
    modelId: UInt,
    type: MigrationAuditEventType,
    nowMs: Long,
): Map<UInt, MigrationMetrics> {
    val previous = current[modelId] ?: MigrationMetrics()
    val updated = when (type) {
        MigrationAuditEventType.PhaseStarted -> previous.copy(started = previous.started + 1u, lastEventAtMs = nowMs)
        MigrationAuditEventType.Completed -> previous.copy(completed = previous.completed + 1u, lastEventAtMs = nowMs)
        MigrationAuditEventType.Failed -> previous.copy(failed = previous.failed + 1u, lastEventAtMs = nowMs)
        MigrationAuditEventType.RetryScheduled -> previous.copy(retries = previous.retries + 1u, lastEventAtMs = nowMs)
        MigrationAuditEventType.Partial -> previous.copy(partials = previous.partials + 1u, lastEventAtMs = nowMs)
        MigrationAuditEventType.Paused -> previous.copy(paused = previous.paused + 1u, lastEventAtMs = nowMs)
        MigrationAuditEventType.Resumed -> previous.copy(resumed = previous.resumed + 1u, lastEventAtMs = nowMs)
        MigrationAuditEventType.Canceled -> previous.copy(canceled = previous.canceled + 1u, lastEventAtMs = nowMs)
        else -> previous.copy(lastEventAtMs = nowMs)
    }
    return current + (modelId to updated)
}
