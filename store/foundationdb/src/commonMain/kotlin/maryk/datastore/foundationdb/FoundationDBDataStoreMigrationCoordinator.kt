package maryk.datastore.foundationdb

import kotlin.time.TimeMark
import kotlinx.atomicfu.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import maryk.core.models.IsRootDataModel
import maryk.core.clock.HLC
import maryk.core.models.migration.MigrationContext
import maryk.core.models.migration.MigrationAuditEvent
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
import maryk.datastore.shared.migration.nextMigrationAttemptOrNull
import maryk.datastore.shared.migration.createMigrationAuditEvent
import maryk.datastore.shared.runCatchingNonFatal
import maryk.datastore.foundationdb.model.FoundationDBMigrationLease
import maryk.datastore.foundationdb.model.FoundationDBMigrationLeaseLostException
import maryk.datastore.foundationdb.model.FoundationDBMigrationAuditLogStore
import maryk.datastore.foundationdb.model.FoundationDBMigrationStateStore
import maryk.datastore.foundationdb.model.MIGRATION_FINALIZATION_PENDING_MESSAGE
import maryk.foundationdb.Transaction
import kotlin.time.Duration.Companion.milliseconds

internal class FoundationDBMigrationRequestContext(
    val transactionGuard: (Transaction) -> Unit,
) : AbstractCoroutineContextElement(FoundationDBMigrationRequestContext) {
    companion object : CoroutineContext.Key<FoundationDBMigrationRequestContext>
}

internal suspend fun FoundationDBDataStore.handleRequiredMigration(
    index: UInt,
    dataModel: IsRootDataModel,
    migrationStatus: NeedsMigration,
    startupStarted: TimeMark,
    effectiveMigrationLease: MigrationLease,
    migrationStateStore: FoundationDBMigrationStateStore,
    recheckMigrationStatus: suspend () -> MigrationStatus,
    finalizeMigration: suspend (
        StoredRootDataModelDefinition,
        ((Transaction) -> Unit)?,
        (Transaction) -> Unit,
    ) -> Unit,
    deferStartupFinalization: (suspend () -> Unit, suspend () -> Unit) -> Unit,
) {
    if (
        migrationConfiguration.migrationExpandHandler == null &&
        migrationConfiguration.migrationHandler == null &&
        migrationConfiguration.migrationVerifyHandler == null &&
        migrationConfiguration.migrationContractHandler == null
    ) {
        throw MigrationException("Migration needed: No migration handler present. \n$migrationStatus")
    }
    val storedModel = migrationStatus.storedDataModel as StoredRootDataModelDefinition
    val migrationId = "${dataModel.Meta.name}:${storedModel.Meta.version}->${dataModel.Meta.version}"
    val finalizationPendingMessage = MIGRATION_FINALIZATION_PENDING_MESSAGE
    val foundationDBMigrationLease = effectiveMigrationLease as? FoundationDBMigrationLease
    var deferredAuditEvent: MigrationAuditEvent? = null

    fun MigrationState.isFinalizationPending() =
        phase == MigrationPhase.Contract &&
            status == MigrationStateStatus.Running &&
            message == finalizationPendingMessage

    suspend fun bindLeaseOwner() {
        foundationDBMigrationLease?.bindOwner(index, migrationId)
    }

    suspend fun assertLeaseOwnership() {
        foundationDBMigrationLease?.assertOwnership(index, migrationId)
    }

    suspend fun appendOwnedAuditEvent(
        type: MigrationAuditEventType,
        phase: MigrationPhase? = null,
        attempt: UInt? = null,
        message: String? = null,
        deferUntilStateWrite: Boolean = false,
    ) {
        assertLeaseOwnership()
        val event = createMigrationAuditEvent(HLC().toPhysicalUnixTime().toLong(), index, migrationId, type, phase, attempt, message)
        if (deferUntilStateWrite) {
            check(deferredAuditEvent == null) { "Migration audit event already waiting for state persistence" }
            deferredAuditEvent = event
        } else {
            val auditStore = migrationAuditLogStore as? FoundationDBMigrationAuditLogStore
            if (auditStore != null) {
                auditStore.append(index, event) { transaction ->
                    foundationDBMigrationLease?.requireOwnership(transaction, index, migrationId)
                }
            }
            incrementMigrationMetricInternal(index, type)
        }
    }

    suspend fun failOrCompleteIfMigrationPlanChangedWhileWaiting(): Boolean {
        return when (val currentStatus = recheckMigrationStatus()) {
            MigrationStatus.UpToDate, MigrationStatus.AlreadyProcessed -> {
                assertLeaseOwnership()
                migrationStateStore.clear(index) { transaction ->
                    foundationDBMigrationLease?.requireOwnership(transaction, index, migrationId)
                }
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
            delay(waitMs.milliseconds)
            remaining -= waitMs
        }
    }

    suspend fun writeMigrationState(state: MigrationState) {
        assertLeaseOwnership()
        val guard: (Transaction) -> Unit = { transaction ->
            foundationDBMigrationLease?.requireOwnership(transaction, index, migrationId)
        }
        val event = deferredAuditEvent
        deferredAuditEvent = null
        val auditStore = migrationAuditLogStore as? FoundationDBMigrationAuditLogStore
        if (event != null && auditStore != null) {
            migrationStateStore.writeWithAudit(index, state, auditStore, event, guard)
            runCatchingNonFatal { migrationConfiguration.migrationAuditEventReporter(event) }
            incrementMigrationMetricInternal(index, event.type)
        } else {
            migrationStateStore.write(index, state, guard)
        }
        updateMigrationRuntimeDetails(index, state)
    }

    suspend fun readMigrationState(): MigrationState? =
        migrationStateStore.read(index)?.also {
            it.requireMatchingMigration(
                migrationId = migrationId,
                fromVersion = storedModel.Meta.version.toString(),
                toVersion = dataModel.Meta.version.toString(),
            )
        }

    suspend fun executeStep(previousState: MigrationState?, attempt: UInt): Pair<MigrationPhase, MigrationOutcome> {
        val phase = previousState?.phase?.normalizedRuntimePhase() ?: MigrationPhase.Expand
        migrationConfiguration.migrationRetryPolicy.maxAttempts?.let { maxAttempts ->
            if (attempt > maxAttempts) {
                return phase to MigrationOutcome.Fatal("Retry policy exceeded max attempts $maxAttempts")
            }
        }
        migrationConfiguration.migrationRetryPolicy.maxRetryOutcomes?.let { maxRetries ->
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
        appendOwnedAuditEvent(MigrationAuditEventType.PhaseStarted, phase = phase, attempt = attempt)
        val context = MigrationContext(
            store = this,
            storedDataModel = storedModel,
            newDataModel = dataModel,
            migrationStatus = migrationStatus,
            previousState = previousState,
            attempt = attempt,
        )
        assertLeaseOwnership()
        val transactionGuard = foundationDBMigrationLease?.let { lease ->
            { transaction: Transaction -> lease.requireOwnership(transaction, index, migrationId) }
        }
        val outcome = if (transactionGuard == null) {
            when (phase) {
                MigrationPhase.Expand -> migrationConfiguration.migrationExpandHandler?.invoke(context) ?: MigrationOutcome.Success
                MigrationPhase.Backfill -> migrationConfiguration.migrationHandler?.invoke(context) ?: MigrationOutcome.Success
                MigrationPhase.Verify -> migrationConfiguration.migrationVerifyHandler?.invoke(context) ?: MigrationOutcome.Success
                MigrationPhase.Contract -> migrationConfiguration.migrationContractHandler?.invoke(context) ?: MigrationOutcome.Success
            }
        } else {
            migrationTransactionGuards.update { it + (index to transactionGuard) }
            try {
                withContext(FoundationDBMigrationRequestContext(transactionGuard)) {
                    when (phase) {
                        MigrationPhase.Expand -> migrationConfiguration.migrationExpandHandler?.invoke(context) ?: MigrationOutcome.Success
                        MigrationPhase.Backfill -> migrationConfiguration.migrationHandler?.invoke(context) ?: MigrationOutcome.Success
                        MigrationPhase.Verify -> migrationConfiguration.migrationVerifyHandler?.invoke(context) ?: MigrationOutcome.Success
                        MigrationPhase.Contract -> migrationConfiguration.migrationContractHandler?.invoke(context) ?: MigrationOutcome.Success
                    }
                }
            } finally {
                migrationTransactionGuards.update { it - index }
            }
        }
        return phase to outcome
    }

    suspend fun executeNextStep(previousState: MigrationState?): Pair<UInt, Pair<MigrationPhase, MigrationOutcome>> {
        val attempt = nextMigrationAttemptOrNull(previousState?.attempt)
        if (attempt == null) {
            val phase = previousState?.phase?.normalizedRuntimePhase() ?: MigrationPhase.Expand
            return UInt.MAX_VALUE to (phase to MigrationOutcome.Fatal("Migration attempt counter overflow"))
        }
        return attempt to executeStep(previousState, attempt)
    }

    suspend fun finalizeCompletedPhases(state: MigrationState) {
        assertLeaseOwnership()
        finalizeMigration(
            storedModel,
            foundationDBMigrationLease?.let { lease ->
                { transaction -> lease.requireOwnership(transaction, index, migrationId) }
            },
        ) { transaction -> migrationStateStore.clear(transaction, index) }
        assertLeaseOwnership()
        migrationRuntimeDetailsByModelId.update { it - index }
        appendOwnedAuditEvent(
            MigrationAuditEventType.Completed,
            phase = MigrationPhase.Contract,
            attempt = state.attempt,
        )
    }

    fun launchBackgroundMigration(leaseAlreadyAcquired: Boolean) {
        launch {
            var hasLease = leaseAlreadyAcquired
            var completeAfterLeaseRelease = false
            try {
                while (!hasLease) {
                    canceledMigrationReasons.value[index]?.let { cancelReason ->
                        pendingMigrationReasons.update { it + (index to "Migration canceled by operator: $cancelReason") }
                        failPendingMigration(index, "Migration canceled by operator: $cancelReason")
                        return@launch
                    }
                    if (pausedMigrationModelIds.value.contains(index)) {
                        pendingMigrationReasons.update { it + (index to "Migration paused by operator") }
                        delay(250.milliseconds)
                        continue
                    }
                    if (effectiveMigrationLease.tryAcquire(index, migrationId)) {
                        hasLease = true
                        appendOwnedAuditEvent(MigrationAuditEventType.LeaseAcquired)
                        pendingMigrationReasons.update {
                            it + (index to "Migration for ${dataModel.Meta.name} is running in background")
                        }
                        break
                    }
                    pendingMigrationReasons.update {
                        it + (index to "Migration lease held by another migrator for $migrationId")
                    }
                    delay(250.milliseconds)
                }
                if (leaseAlreadyAcquired) {
                    bindLeaseOwner()
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
                        delay(250.milliseconds)
                        continue
                    }
                    val previousState = readMigrationState()
                    if (previousState?.isFinalizationPending() == true) {
                        finalizeCompletedPhases(previousState)
                        pendingMigrationModelIds.update { it - index }
                        pendingMigrationReasons.update { it - index }
                        pausedMigrationModelIds.update { it - index }
                        canceledMigrationReasons.update { it - index }
                        completeAfterLeaseRelease = true
                        break
                    }
                    val (attempt, step) = executeNextStep(previousState)
                    val (phase, outcome) = step

                    when (outcome) {
                        MigrationOutcome.Success -> {
                            val nextPhase = phase.nextRuntimePhaseOrNull()
                            if (nextPhase != null) {
                                if (!phase.canTransitionTo(nextPhase)) {
                                    throw MigrationException("Invalid phase transition for ${dataModel.Meta.name}: $phase -> $nextPhase")
                                }
                                appendOwnedAuditEvent(MigrationAuditEventType.PhaseCompleted, phase = phase, attempt = attempt, deferUntilStateWrite = true)
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
                            appendOwnedAuditEvent(MigrationAuditEventType.PhaseCompleted, phase = phase, attempt = attempt, deferUntilStateWrite = true)
                            val finalizationState = MigrationState(
                                migrationId = migrationId,
                                phase = MigrationPhase.Contract,
                                status = MigrationStateStatus.Running,
                                attempt = attempt,
                                fromVersion = storedModel.Meta.version.toString(),
                                toVersion = dataModel.Meta.version.toString(),
                                message = finalizationPendingMessage,
                            )
                            writeMigrationState(finalizationState)
                            finalizeCompletedPhases(finalizationState)
                            pendingMigrationModelIds.update { it - index }
                            pendingMigrationReasons.update { it - index }
                            pausedMigrationModelIds.update { it - index }
                            canceledMigrationReasons.update { it - index }
                            completeAfterLeaseRelease = true
                            break
                        }
                        is MigrationOutcome.Partial -> {
                            appendOwnedAuditEvent(MigrationAuditEventType.Partial, phase = phase, attempt = attempt, message = outcome.message, deferUntilStateWrite = true)
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
                            appendOwnedAuditEvent(MigrationAuditEventType.RetryScheduled, phase = phase, attempt = attempt, message = outcome.message, deferUntilStateWrite = true)
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
                            appendOwnedAuditEvent(MigrationAuditEventType.Failed, phase = phase, attempt = attempt, message = outcome.reason, deferUntilStateWrite = true)
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
            } catch (error: Throwable) {
                val leaseLossReason = foundationDBMigrationLease?.leaseLossReason(index, migrationId)
                if (error is FoundationDBMigrationLeaseLostException || leaseLossReason != null) {
                    val reason = leaseLossReason
                        ?: error.message
                        ?: "FoundationDB migration lease lost for ${dataModel.Meta.name}"
                    pendingMigrationReasons.update { it + (index to reason) }
                    failPendingMigration(index, reason)
                    return@launch
                }
                if (error is CancellationException) throw error
                val reason = "Migration failed in background for ${dataModel.Meta.name}: ${error.message ?: "unknown error"}"
                pendingMigrationReasons.update { it + (index to reason) }
                failPendingMigration(index, reason)
            } finally {
                var releaseFailure: Throwable? = null
                if (hasLease) {
                    try {
                        effectiveMigrationLease.release(index, migrationId)
                    } catch (error: Throwable) {
                        releaseFailure = error
                        val reason = "Migration lease release failed for ${dataModel.Meta.name}: ${error.message ?: "unknown error"}"
                        pendingMigrationReasons.update { it + (index to reason) }
                        failPendingMigration(index, reason)
                    }
                }
                if (completeAfterLeaseRelease && releaseFailure == null) {
                    completePendingMigration(index)
                }
            }
        }
    }

    val leaseAcquired = effectiveMigrationLease.tryAcquire(index, migrationId)
    if (!leaseAcquired) {
        appendMigrationAuditEvent(index, migrationId, MigrationAuditEventType.LeaseRejected, message = "Lease held by other migrator")
        if (migrationConfiguration.continueMigrationsInBackground) {
            pendingMigrationModelIds.update { it + index }
            pendingMigrationReasons.update { it + (index to "Migration lease held by another migrator for $migrationId") }
            ensurePendingMigrationWaiter(index)
            launchBackgroundMigration(leaseAlreadyAcquired = false)
            return
        }
        throw MigrationException("Migration lease could not be acquired for ${dataModel.Meta.name}: $migrationId")
    }

    var releaseLeaseInFinally = true
    var deferredFinalization = false

    try {
        appendOwnedAuditEvent(MigrationAuditEventType.LeaseAcquired)
        while (true) {
            val startupBudgetMs = migrationConfiguration.migrationStartupBudgetMs
            if (startupBudgetMs != null && startupStarted.elapsedNow().inWholeMilliseconds > startupBudgetMs) {
                if (!migrationConfiguration.continueMigrationsInBackground) {
                    throw MigrationException("Migration startup budget exceeded for ${dataModel.Meta.name} after ${startupBudgetMs}ms")
                }
                pendingMigrationModelIds.update { it + index }
                pendingMigrationReasons.update { it + (index to "Migration for ${dataModel.Meta.name} is running in background") }
                ensurePendingMigrationWaiter(index)
                launchBackgroundMigration(leaseAlreadyAcquired = true)
                releaseLeaseInFinally = false
                break
            }

            val previousState = readMigrationState()
            if (previousState?.isFinalizationPending() == true) {
                deferStartupFinalization(
                    {
                        try {
                            bindLeaseOwner()
                            finalizeCompletedPhases(previousState)
                        } finally {
                            effectiveMigrationLease.release(index, migrationId)
                        }
                    },
                    { effectiveMigrationLease.release(index, migrationId) },
                )
                releaseLeaseInFinally = false
                deferredFinalization = true
                break
            }
            val (attempt, step) = executeNextStep(previousState)
            val (phase, outcome) = step

            when (outcome) {
                MigrationOutcome.Success -> {
                    val nextPhase = phase.nextRuntimePhaseOrNull()
                    if (nextPhase != null) {
                        if (!phase.canTransitionTo(nextPhase)) {
                            throw MigrationException("Invalid phase transition for ${dataModel.Meta.name}: $phase -> $nextPhase")
                        }
                        appendOwnedAuditEvent(MigrationAuditEventType.PhaseCompleted, phase = phase, attempt = attempt, deferUntilStateWrite = true)
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
                    appendOwnedAuditEvent(MigrationAuditEventType.PhaseCompleted, phase = phase, attempt = attempt, deferUntilStateWrite = true)
                    val finalizationState = MigrationState(
                        migrationId = migrationId,
                        phase = MigrationPhase.Contract,
                        status = MigrationStateStatus.Running,
                        attempt = attempt,
                        fromVersion = storedModel.Meta.version.toString(),
                        toVersion = dataModel.Meta.version.toString(),
                        message = finalizationPendingMessage,
                    )
                    writeMigrationState(finalizationState)
                    deferStartupFinalization(
                        {
                            try {
                                bindLeaseOwner()
                                finalizeCompletedPhases(finalizationState)
                            } finally {
                                effectiveMigrationLease.release(index, migrationId)
                            }
                        },
                        { effectiveMigrationLease.release(index, migrationId) },
                    )
                    releaseLeaseInFinally = false
                    deferredFinalization = true
                    break
                }
                is MigrationOutcome.Partial -> {
                    appendOwnedAuditEvent(MigrationAuditEventType.Partial, phase = phase, attempt = attempt, message = outcome.message, deferUntilStateWrite = true)
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
                    appendOwnedAuditEvent(MigrationAuditEventType.RetryScheduled, phase = phase, attempt = attempt, message = outcome.message, deferUntilStateWrite = true)
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
                    appendOwnedAuditEvent(MigrationAuditEventType.Failed, phase = phase, attempt = attempt, message = outcome.reason, deferUntilStateWrite = true)
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
    if (deferredFinalization) return
}
