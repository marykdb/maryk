package maryk.datastore.foundationdb

import kotlinx.atomicfu.update
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.models.migration.MigrationAuditEvent
import maryk.core.models.migration.MigrationAuditEventType
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.MigrationMetrics
import maryk.core.models.migration.MigrationPhase
import maryk.core.models.migration.MigrationRuntimeState
import maryk.core.models.migration.MigrationRuntimeStatus
import maryk.core.models.migration.MigrationState
import maryk.datastore.shared.migration.createMigrationAuditEvent
import maryk.datastore.shared.migration.projectMigrationRuntimeStatus
import maryk.datastore.shared.migration.updatedMigrationMetrics
import maryk.datastore.shared.migration.updatedMigrationRuntimeDetails

internal fun FoundationDBDataStore.pendingMigrationsInternal(): Map<UInt, String> = pendingMigrationReasons.value

internal fun FoundationDBDataStore.migrationStatusInternal(modelId: UInt): MigrationRuntimeStatus {
    val reason = pendingMigrationReasons.value[modelId] ?: return MigrationRuntimeStatus(MigrationRuntimeState.Idle)
    return projectMigrationRuntimeStatus(
        modelId = modelId,
        reason = reason,
        detailsByModelId = migrationRuntimeDetailsByModelId.value,
        canceledModelIds = canceledMigrationReasons.value.keys,
        pausedModelIds = pausedMigrationModelIds.value,
    )
}

internal fun FoundationDBDataStore.migrationStatusesInternal(): Map<UInt, MigrationRuntimeStatus> =
    pendingMigrationReasons.value.mapValues { (modelId, reason) ->
        projectMigrationRuntimeStatus(
            modelId = modelId,
            reason = reason,
            detailsByModelId = migrationRuntimeDetailsByModelId.value,
            canceledModelIds = canceledMigrationReasons.value.keys,
            pausedModelIds = pausedMigrationModelIds.value,
        )
    }

internal fun FoundationDBDataStore.pauseMigrationInternal(modelId: UInt): Boolean {
    if (!pendingMigrationModelIds.value.contains(modelId)) return false
    pausedMigrationModelIds.update { it + modelId }
    pendingMigrationReasons.update { it + (modelId to "Migration paused by operator") }
    migrationRuntimeDetailsByModelId.value[modelId]?.let { details ->
        runBlocking {
            appendMigrationAuditEventInternal(
                modelId = modelId,
                migrationId = details.migrationId,
                type = MigrationAuditEventType.Paused,
                phase = details.phase,
                attempt = details.attempt,
                message = "Paused by operator"
            )
        }
    }
    return true
}

internal fun FoundationDBDataStore.resumeMigrationInternal(modelId: UInt): Boolean {
    val wasPaused = pausedMigrationModelIds.value.contains(modelId)
    if (!wasPaused) return false
    pausedMigrationModelIds.update { it - modelId }
    if (pendingMigrationModelIds.value.contains(modelId)) {
        pendingMigrationReasons.update { it + (modelId to "Migration resumed") }
    }
    migrationRuntimeDetailsByModelId.value[modelId]?.let { details ->
        runBlocking {
            appendMigrationAuditEventInternal(
                modelId = modelId,
                migrationId = details.migrationId,
                type = MigrationAuditEventType.Resumed,
                phase = details.phase,
                attempt = details.attempt,
                message = "Resumed by operator"
            )
        }
    }
    return true
}

internal fun FoundationDBDataStore.cancelMigrationInternal(modelId: UInt, reason: String): Boolean {
    if (!pendingMigrationModelIds.value.contains(modelId)) return false
    val cancellationReason = "$reason. Reopen store to resume migration."
    canceledMigrationReasons.update { it + (modelId to cancellationReason) }
    pausedMigrationModelIds.update { it - modelId }
    pendingMigrationReasons.update { it + (modelId to "Migration canceled by operator: $cancellationReason") }
    migrationRuntimeDetailsByModelId.value[modelId]?.let { details ->
        runBlocking {
            appendMigrationAuditEventInternal(
                modelId = modelId,
                migrationId = details.migrationId,
                type = MigrationAuditEventType.Canceled,
                phase = details.phase,
                attempt = details.attempt,
                message = cancellationReason
            )
        }
    }
    failPendingMigrationInternal(modelId, "Migration canceled by operator: $cancellationReason")
    return true
}

internal fun FoundationDBDataStore.migrationMetricsInternal(modelId: UInt): MigrationMetrics =
    migrationMetricsByModelId.value[modelId] ?: MigrationMetrics()

internal fun FoundationDBDataStore.migrationMetricsInternal(): Map<UInt, MigrationMetrics> = migrationMetricsByModelId.value

internal suspend fun FoundationDBDataStore.migrationAuditEventsInternal(modelId: UInt, limit: Int): List<MigrationAuditEvent> =
    migrationAuditLogStore?.read(modelId, limit) ?: emptyList()

internal suspend fun FoundationDBDataStore.awaitMigrationInternal(modelId: UInt) {
    pendingMigrationWaiters.value[modelId]?.await()
}

internal fun FoundationDBDataStore.ensurePendingMigrationWaiterInternal(modelId: UInt): CompletableDeferred<Unit> {
    var waiter: CompletableDeferred<Unit>? = null
    pendingMigrationWaiters.update { current ->
        val existing = current[modelId]
        if (existing != null) {
            waiter = existing
            current
        } else {
            CompletableDeferred<Unit>().let { created ->
                waiter = created
                current + (modelId to created)
            }
        }
    }
    return waiter ?: throw IllegalStateException("Pending waiter could not be created for model $modelId")
}

internal fun FoundationDBDataStore.completePendingMigrationInternal(modelId: UInt) {
    var waiter: CompletableDeferred<Unit>? = null
    pendingMigrationWaiters.update { current ->
        waiter = current[modelId]
        current - modelId
    }
    waiter?.complete(Unit)
    migrationRuntimeDetailsByModelId.update { it - modelId }
}

internal fun FoundationDBDataStore.failPendingMigrationInternal(modelId: UInt, reason: String) {
    var waiter: CompletableDeferred<Unit>? = null
    pendingMigrationWaiters.update { current ->
        waiter = current[modelId]
        current - modelId
    }
    waiter?.completeExceptionally(MigrationException(reason))
}

internal fun FoundationDBDataStore.updateMigrationRuntimeDetailsInternal(modelId: UInt, state: MigrationState) {
    val nowMs = HLC().toPhysicalUnixTime().toLong()
    migrationRuntimeDetailsByModelId.update { current ->
        updatedMigrationRuntimeDetails(current, modelId, state, nowMs)
    }
}

internal suspend fun FoundationDBDataStore.appendMigrationAuditEventInternal(
    modelId: UInt,
    migrationId: String,
    type: MigrationAuditEventType,
    phase: MigrationPhase? = null,
    attempt: UInt? = null,
    message: String? = null,
) {
    val event = createMigrationAuditEvent(
        nowMs = HLC().toPhysicalUnixTime().toLong(),
        modelId = modelId,
        migrationId = migrationId,
        type = type,
        phase = phase,
        attempt = attempt,
        message = message,
    )
    runCatching { migrationConfiguration.migrationAuditEventReporter(event) }
    migrationAuditLogStore?.append(modelId, event)
    incrementMigrationMetricInternal(modelId, type)
}

internal fun FoundationDBDataStore.incrementMigrationMetricInternal(modelId: UInt, type: MigrationAuditEventType) {
    val nowMs = HLC().toPhysicalUnixTime().toLong()
    migrationMetricsByModelId.update { current -> updatedMigrationMetrics(current, modelId, type, nowMs) }
}

internal fun FoundationDBDataStore.assertModelReadyForMigrations(dataModelId: UInt) {
    if (pendingMigrationModelIds.value.contains(dataModelId)) {
        val modelName = dataModelsById[dataModelId]?.Meta?.name ?: dataModelId.toString()
        val reason = pendingMigrationReasons.value[dataModelId] ?: "Migration in progress"
        throw RequestException("Model $modelName is unavailable while migration is running: $reason")
    }
}
