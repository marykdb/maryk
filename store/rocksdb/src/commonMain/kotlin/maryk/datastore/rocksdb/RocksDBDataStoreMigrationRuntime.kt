package maryk.datastore.rocksdb

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

internal fun RocksDBDataStore.pendingMigrationsInternal(): Map<UInt, String> = pendingMigrationReasons.value

internal fun RocksDBDataStore.migrationStatusInternal(modelId: UInt): MigrationRuntimeStatus {
    val reason = pendingMigrationReasons.value[modelId] ?: return MigrationRuntimeStatus(MigrationRuntimeState.Idle)
    return projectMigrationRuntimeStatus(
        modelId = modelId,
        reason = reason,
        detailsByModelId = migrationRuntimeDetailsByModelId.value,
        canceledModelIds = canceledMigrationReasons.value.keys,
        pausedModelIds = pausedMigrationModelIds.value,
    )
}

internal fun RocksDBDataStore.migrationStatusesInternal(): Map<UInt, MigrationRuntimeStatus> =
    pendingMigrationReasons.value.mapValues { (modelId, reason) ->
        projectMigrationRuntimeStatus(
            modelId = modelId,
            reason = reason,
            detailsByModelId = migrationRuntimeDetailsByModelId.value,
            canceledModelIds = canceledMigrationReasons.value.keys,
            pausedModelIds = pausedMigrationModelIds.value,
        )
    }

internal fun RocksDBDataStore.pauseMigrationInternal(modelId: UInt): Boolean {
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

internal fun RocksDBDataStore.resumeMigrationInternal(modelId: UInt): Boolean {
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

internal fun RocksDBDataStore.cancelMigrationInternal(modelId: UInt, reason: String): Boolean {
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

internal fun RocksDBDataStore.migrationMetricsInternal(modelId: UInt): MigrationMetrics =
    migrationMetricsByModelId.value[modelId] ?: MigrationMetrics()

internal fun RocksDBDataStore.migrationMetricsInternal(): Map<UInt, MigrationMetrics> = migrationMetricsByModelId.value

internal suspend fun RocksDBDataStore.migrationAuditEventsInternal(modelId: UInt, limit: Int): List<MigrationAuditEvent> =
    migrationAuditLogStore?.read(modelId, limit) ?: emptyList()

internal suspend fun RocksDBDataStore.awaitMigrationInternal(modelId: UInt) {
    pendingMigrationWaiters.value[modelId]?.await()
}

internal fun RocksDBDataStore.ensurePendingMigrationWaiterInternal(modelId: UInt): CompletableDeferred<Unit> {
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

internal fun RocksDBDataStore.completePendingMigrationInternal(modelId: UInt) {
    var waiter: CompletableDeferred<Unit>? = null
    pendingMigrationWaiters.update { current ->
        waiter = current[modelId]
        current - modelId
    }
    waiter?.complete(Unit)
    migrationRuntimeDetailsByModelId.update { it - modelId }
}

internal fun RocksDBDataStore.failPendingMigrationInternal(modelId: UInt, reason: String) {
    var waiter: CompletableDeferred<Unit>? = null
    pendingMigrationWaiters.update { current ->
        waiter = current[modelId]
        current - modelId
    }
    waiter?.completeExceptionally(MigrationException(reason))
}

internal fun RocksDBDataStore.updateMigrationRuntimeDetailsInternal(modelId: UInt, state: MigrationState) {
    val nowMs = HLC().toPhysicalUnixTime().toLong()
    migrationRuntimeDetailsByModelId.update { current ->
        updatedMigrationRuntimeDetails(current, modelId, state, nowMs)
    }
}

internal suspend fun RocksDBDataStore.appendMigrationAuditEventInternal(
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

internal fun RocksDBDataStore.incrementMigrationMetricInternal(modelId: UInt, type: MigrationAuditEventType) {
    val nowMs = HLC().toPhysicalUnixTime().toLong()
    migrationMetricsByModelId.update { current -> updatedMigrationMetrics(current, modelId, type, nowMs) }
}

internal fun RocksDBDataStore.assertModelReadyForMigrations(dataModelId: UInt) {
    if (pendingMigrationModelIds.value.contains(dataModelId)) {
        val modelName = dataModelsById[dataModelId]?.Meta?.name ?: dataModelId.toString()
        val reason = pendingMigrationReasons.value[dataModelId] ?: "Migration in progress"
        throw RequestException("Model $modelName is unavailable while migration is running: $reason")
    }
}
