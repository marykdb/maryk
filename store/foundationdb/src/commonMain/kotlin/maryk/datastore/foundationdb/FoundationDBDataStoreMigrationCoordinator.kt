package maryk.datastore.foundationdb

import kotlin.time.TimeMark
import kotlinx.atomicfu.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import maryk.core.models.IsRootDataModel
import maryk.core.models.migration.MigrationContext
import maryk.core.models.migration.MigrationAuditEventType
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.MigrationLease
import maryk.core.models.migration.MigrationOutcome
import maryk.core.models.migration.MigrationPhase
import maryk.core.models.migration.MigrationState
import maryk.core.models.migration.MigrationStateStatus
import maryk.core.models.migration.MigrationStatus
import maryk.core.models.migration.MigrationStatus.NeedsMigration
import maryk.core.models.migration.StoredRootDataModelDefinition
import maryk.core.models.migration.canTransitionTo
import maryk.core.models.migration.nextRuntimePhaseOrNull
import maryk.core.models.migration.normalizedRuntimePhase
import maryk.datastore.foundationdb.model.FoundationDBMigrationStateStore

internal suspend fun FoundationDBDataStore.handleRequiredMigration(
    index: UInt,
    dataModel: IsRootDataModel,
    migrationStatus: NeedsMigration,
    startupStarted: TimeMark,
    effectiveMigrationLease: MigrationLease,
    migrationStateStore: FoundationDBMigrationStateStore,
    recheckMigrationStatus: suspend () -> MigrationStatus,
    finalizeInBackground: suspend (StoredRootDataModelDefinition) -> Unit,
    finalizeInStartup: (StoredRootDataModelDefinition) -> Unit,
) {
    val handler = migrationHandler
        ?: throw MigrationException("Migration needed: No migration handler present. \n$migrationStatus")
    val storedModel = migrationStatus.storedDataModel as StoredRootDataModelDefinition
    val migrationId = "${dataModel.Meta.name}:${storedModel.Meta.version}->${dataModel.Meta.version}"

    suspend fun failOrCompleteIfMigrationPlanChangedWhileWaiting(): Boolean {
        return when (val currentStatus = recheckMigrationStatus()) {
            MigrationStatus.UpToDate, MigrationStatus.AlreadyProcessed -> {
                migrationStateStore.clear(index)
                pendingMigrationModelIds.update { it - index }
                pendingMigrationReasons.update { it - index }
                pausedMigrationModelIds.update { it - index }
                canceledMigrationReasons.update { it - index }
                completePendingMigration(index)
                false
            }
            is NeedsMigration -> {
                val currentStoredModel = currentStatus.storedDataModel as StoredRootDataModelDefinition
                if (currentStoredModel.Meta.version == storedModel.Meta.version) {
                    true
                } else {
                    val currentMigrationId =
                        "${dataModel.Meta.name}:${currentStoredModel.Meta.version}->${dataModel.Meta.version}"
                    val reason =
                        "Migration plan changed while waiting on lease for ${dataModel.Meta.name}: expected $migrationId but found $currentMigrationId. Reopen the store."
                    pendingMigrationReasons.update { it + (index to reason) }
                    failPendingMigration(index, reason)
                    false
                }
            }
            else -> {
                val reason =
                    "Migration plan changed while waiting on lease for ${dataModel.Meta.name}: $currentStatus. Reopen the store."
                pendingMigrationReasons.update { it + (index to reason) }
                failPendingMigration(index, reason)
                false
            }
        }
    }

    suspend fun delayWithCancellationChecks(retryAfterMs: Long?) {
        if (retryAfterMs == null || retryAfterMs <= 0L) return
        var remaining = retryAfterMs
        while (remaining > 0L) {
            if (canceledMigrationReasons.value.containsKey(index)) return
            val waitMs = minOf(remaining, 250L)
            delay(waitMs)
            remaining -= waitMs
        }
    }

    suspend fun writeMigrationState(state: MigrationState) {
        migrationStateStore.write(index, state)
        updateMigrationRuntimeDetails(index, state)
    }

    suspend fun executeStep(previousState: MigrationState?, attempt: UInt): Pair<MigrationPhase, MigrationOutcome> {
        val phase = previousState?.phase?.normalizedRuntimePhase() ?: MigrationPhase.Expand
        migrationRetryPolicy.maxAttempts?.let { maxAttempts ->
            if (attempt > maxAttempts) {
                return phase to MigrationOutcome.Fatal("Retry policy exceeded max attempts $maxAttempts")
            }
        }
        migrationRetryPolicy.maxRetryOutcomes?.let { maxRetries ->
            val retriesSoFar = migrationRuntimeDetailsByModelId.value[index]?.retryCount ?: 0u
            if (retriesSoFar >= maxRetries) {
                return phase to MigrationOutcome.Fatal("Retry policy exceeded max retries $maxRetries")
            }
        }
        writeMigrationState(
            MigrationState(
                migrationId = migrationId,
                phase = phase,
                status = MigrationStateStatus.Running,
                attempt = attempt,
                fromVersion = storedModel.Meta.version.toString(),
                toVersion = dataModel.Meta.version.toString(),
                cursor = previousState?.cursor,
            )
        )
        appendMigrationAuditEvent(index, migrationId, MigrationAuditEventType.PhaseStarted, phase = phase, attempt = attempt)
        val context = MigrationContext(
            store = this,
            storedDataModel = storedModel,
            newDataModel = dataModel,
            migrationStatus = migrationStatus,
            previousState = previousState,
            attempt = attempt,
        )
        val outcome = when (phase) {
            MigrationPhase.Expand -> migrationExpandHandler?.invoke(context) ?: MigrationOutcome.Success
            MigrationPhase.Backfill -> handler(context)
            MigrationPhase.Verify -> migrationVerifyHandler?.invoke(context) ?: MigrationOutcome.Success
            MigrationPhase.Contract -> migrationContractHandler?.invoke(context) ?: MigrationOutcome.Success
        }
        return phase to outcome
    }

    fun launchBackgroundMigration(leaseAlreadyAcquired: Boolean) {
        launch {
            var hasLease = leaseAlreadyAcquired
            try {
                while (!hasLease) {
                    canceledMigrationReasons.value[index]?.let { cancelReason ->
                        pendingMigrationReasons.update { it + (index to "Migration canceled by operator: $cancelReason") }
                        failPendingMigration(index, "Migration canceled by operator: $cancelReason")
                        return@launch
                    }
                    if (pausedMigrationModelIds.value.contains(index)) {
                        pendingMigrationReasons.update { it + (index to "Migration paused by operator") }
                        delay(250)
                        continue
                    }
                    if (effectiveMigrationLease.tryAcquire(index, migrationId)) {
                        appendMigrationAuditEvent(index, migrationId, MigrationAuditEventType.LeaseAcquired)
                        pendingMigrationReasons.update {
                            it + (index to "Migration for ${dataModel.Meta.name} is running in background")
                        }
                        hasLease = true
                        break
                    }
                    pendingMigrationReasons.update {
                        it + (index to "Migration lease held by another migrator for $migrationId")
                    }
                    delay(250)
                }
                if (!leaseAlreadyAcquired && !failOrCompleteIfMigrationPlanChangedWhileWaiting()) {
                    return@launch
                }

                while (true) {
                    canceledMigrationReasons.value[index]?.let { cancelReason ->
                        pendingMigrationReasons.update { it + (index to "Migration canceled by operator: $cancelReason") }
                        failPendingMigration(index, "Migration canceled by operator: $cancelReason")
                        break
                    }
                    if (pausedMigrationModelIds.value.contains(index)) {
                        pendingMigrationReasons.update { it + (index to "Migration paused by operator") }
                        delay(250)
                        continue
                    }
                    val previousState = migrationStateStore.read(index)
                    val attempt = (previousState?.attempt ?: 0u) + 1u
                    val (phase, outcome) = executeStep(previousState, attempt)

                    when (outcome) {
                        MigrationOutcome.Success -> {
                            val nextPhase = phase.nextRuntimePhaseOrNull()
                            if (nextPhase != null) {
                                if (!phase.canTransitionTo(nextPhase)) {
                                    throw MigrationException("Invalid phase transition for ${dataModel.Meta.name}: $phase -> $nextPhase")
                                }
                                appendMigrationAuditEvent(index, migrationId, MigrationAuditEventType.PhaseCompleted, phase = phase, attempt = attempt)
                                writeMigrationState(
                                    MigrationState(
                                        migrationId = migrationId,
                                        phase = nextPhase,
                                        status = MigrationStateStatus.Running,
                                        attempt = attempt,
                                        fromVersion = storedModel.Meta.version.toString(),
                                        toVersion = dataModel.Meta.version.toString(),
                                        message = "Migration phase complete; advancing to $nextPhase"
                                    )
                                )
                                continue
                            }
                            migrationStateStore.clear(index)
                            migrationRuntimeDetailsByModelId.update { it - index }
                            appendMigrationAuditEvent(index, migrationId, MigrationAuditEventType.Completed, phase = phase, attempt = attempt)
                            finalizeInBackground(storedModel)
                            pendingMigrationModelIds.update { it - index }
                            pendingMigrationReasons.update { it - index }
                            pausedMigrationModelIds.update { it - index }
                            canceledMigrationReasons.update { it - index }
                            completePendingMigration(index)
                            break
                        }
                        is MigrationOutcome.Partial -> {
                            appendMigrationAuditEvent(index, migrationId, MigrationAuditEventType.Partial, phase = phase, attempt = attempt, message = outcome.message)
                            writeMigrationState(
                                MigrationState(
                                    migrationId = migrationId,
                                    phase = phase,
                                    status = MigrationStateStatus.Partial,
                                    attempt = attempt,
                                    fromVersion = storedModel.Meta.version.toString(),
                                    toVersion = dataModel.Meta.version.toString(),
                                    cursor = outcome.nextCursor,
                                    message = outcome.message
                                )
                            )
                        }
                        is MigrationOutcome.Retry -> {
                            appendMigrationAuditEvent(index, migrationId, MigrationAuditEventType.RetryScheduled, phase = phase, attempt = attempt, message = outcome.message)
                            writeMigrationState(
                                MigrationState(
                                    migrationId = migrationId,
                                    phase = phase,
                                    status = MigrationStateStatus.Retry,
                                    attempt = attempt,
                                    fromVersion = storedModel.Meta.version.toString(),
                                    toVersion = dataModel.Meta.version.toString(),
                                    cursor = outcome.nextCursor,
                                    message = outcome.message
                                )
                            )
                            delayWithCancellationChecks(outcome.retryAfterMs)
                        }
                        is MigrationOutcome.Fatal -> {
                            appendMigrationAuditEvent(index, migrationId, MigrationAuditEventType.Failed, phase = phase, attempt = attempt, message = outcome.reason)
                            writeMigrationState(
                                MigrationState(
                                    migrationId = migrationId,
                                    phase = phase,
                                    status = MigrationStateStatus.Failed,
                                    attempt = attempt,
                                    fromVersion = storedModel.Meta.version.toString(),
                                    toVersion = dataModel.Meta.version.toString(),
                                    cursor = previousState?.cursor,
                                    message = outcome.reason
                                )
                            )
                            val failurePrefix = "Migration phase $phase failed"
                            pendingMigrationReasons.update {
                                it + (index to "$failurePrefix for ${dataModel.Meta.name}: ${outcome.reason}")
                            }
                            failPendingMigration(index, "$failurePrefix for ${dataModel.Meta.name}: ${outcome.reason}")
                            break
                        }
                    }
                }
            } finally {
                if (hasLease) {
                    effectiveMigrationLease.release(index, migrationId)
                }
            }
        }
    }

    val leaseAcquired = effectiveMigrationLease.tryAcquire(index, migrationId)
    if (!leaseAcquired) {
        appendMigrationAuditEvent(index, migrationId, MigrationAuditEventType.LeaseRejected, message = "Lease held by other migrator")
        if (continueMigrationsInBackground) {
            pendingMigrationModelIds.update { it + index }
            pendingMigrationReasons.update { it + (index to "Migration lease held by another migrator for $migrationId") }
            ensurePendingMigrationWaiter(index)
            launchBackgroundMigration(leaseAlreadyAcquired = false)
            return
        }
        throw MigrationException("Migration lease could not be acquired for ${dataModel.Meta.name}: $migrationId")
    }

    appendMigrationAuditEvent(index, migrationId, MigrationAuditEventType.LeaseAcquired)
    var completedInStartup = false
    var releaseLeaseInFinally = true

    try {
        while (true) {
            if (migrationStartupBudgetMs != null && startupStarted.elapsedNow().inWholeMilliseconds > migrationStartupBudgetMs) {
                if (!continueMigrationsInBackground) {
                    throw MigrationException("Migration startup budget exceeded for ${dataModel.Meta.name} after ${migrationStartupBudgetMs}ms")
                }
                pendingMigrationModelIds.update { it + index }
                pendingMigrationReasons.update { it + (index to "Migration for ${dataModel.Meta.name} is running in background") }
                ensurePendingMigrationWaiter(index)
                launchBackgroundMigration(leaseAlreadyAcquired = true)
                releaseLeaseInFinally = false
                break
            }

            val previousState = migrationStateStore.read(index)
            val attempt = (previousState?.attempt ?: 0u) + 1u
            val (phase, outcome) = executeStep(previousState, attempt)

            when (outcome) {
                MigrationOutcome.Success -> {
                    val nextPhase = phase.nextRuntimePhaseOrNull()
                    if (nextPhase != null) {
                        if (!phase.canTransitionTo(nextPhase)) {
                            throw MigrationException("Invalid phase transition for ${dataModel.Meta.name}: $phase -> $nextPhase")
                        }
                        appendMigrationAuditEvent(index, migrationId, MigrationAuditEventType.PhaseCompleted, phase = phase, attempt = attempt)
                        writeMigrationState(
                            MigrationState(
                                migrationId = migrationId,
                                phase = nextPhase,
                                status = MigrationStateStatus.Running,
                                attempt = attempt,
                                fromVersion = storedModel.Meta.version.toString(),
                                toVersion = dataModel.Meta.version.toString(),
                                message = "Migration phase complete; advancing to $nextPhase"
                            )
                        )
                        continue
                    }
                    migrationStateStore.clear(index)
                    migrationRuntimeDetailsByModelId.update { it - index }
                    appendMigrationAuditEvent(index, migrationId, MigrationAuditEventType.Completed, phase = phase, attempt = attempt)
                    completedInStartup = true
                    break
                }
                is MigrationOutcome.Partial -> {
                    appendMigrationAuditEvent(index, migrationId, MigrationAuditEventType.Partial, phase = phase, attempt = attempt, message = outcome.message)
                    writeMigrationState(
                        MigrationState(
                            migrationId = migrationId,
                            phase = phase,
                            status = MigrationStateStatus.Partial,
                            attempt = attempt,
                            fromVersion = storedModel.Meta.version.toString(),
                            toVersion = dataModel.Meta.version.toString(),
                            cursor = outcome.nextCursor,
                            message = outcome.message
                        )
                    )
                }
                is MigrationOutcome.Retry -> {
                    appendMigrationAuditEvent(index, migrationId, MigrationAuditEventType.RetryScheduled, phase = phase, attempt = attempt, message = outcome.message)
                    writeMigrationState(
                        MigrationState(
                            migrationId = migrationId,
                            phase = phase,
                            status = MigrationStateStatus.Retry,
                            attempt = attempt,
                            fromVersion = storedModel.Meta.version.toString(),
                            toVersion = dataModel.Meta.version.toString(),
                            cursor = outcome.nextCursor,
                            message = outcome.message
                        )
                    )
                    delayWithCancellationChecks(outcome.retryAfterMs)
                }
                is MigrationOutcome.Fatal -> {
                    appendMigrationAuditEvent(index, migrationId, MigrationAuditEventType.Failed, phase = phase, attempt = attempt, message = outcome.reason)
                    writeMigrationState(
                        MigrationState(
                            migrationId = migrationId,
                            phase = phase,
                            status = MigrationStateStatus.Failed,
                            attempt = attempt,
                            fromVersion = storedModel.Meta.version.toString(),
                            toVersion = dataModel.Meta.version.toString(),
                            cursor = previousState?.cursor,
                            message = outcome.reason
                        )
                    )
                    val failurePrefix = "Migration phase $phase could not be handled"
                    throw MigrationException("$failurePrefix for ${dataModel.Meta.name}: ${outcome.reason}\n$migrationStatus")
                }
            }
        }
    } finally {
        if (releaseLeaseInFinally) {
            effectiveMigrationLease.release(index, migrationId)
        }
    }

    if (completedInStartup) {
        finalizeInStartup(storedModel)
    }
}
