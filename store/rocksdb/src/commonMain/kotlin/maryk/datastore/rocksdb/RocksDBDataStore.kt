package maryk.datastore.rocksdb

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import maryk.core.clock.HLC
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.TypeException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.migration.MigrationContext
import maryk.core.models.migration.MigrationAuditEvent
import maryk.core.models.migration.MigrationAuditEventType
import maryk.core.models.migration.MigrationAuditLogStore
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.MigrationHandler
import maryk.core.models.migration.MigrationLease
import maryk.core.models.migration.MigrationMetrics
import maryk.core.models.migration.MigrationOutcome
import maryk.core.models.migration.MigrationPhase
import maryk.core.models.migration.MigrationRetryPolicy
import maryk.core.models.migration.MigrationRuntimeState
import maryk.core.models.migration.MigrationRuntimeStatus
import maryk.core.models.migration.MigrationState
import maryk.core.models.migration.MigrationStateStatus
import maryk.core.models.migration.MigrationStatus
import maryk.core.models.migration.MigrationVerifyHandler
import maryk.core.models.migration.canTransitionTo
import maryk.core.models.migration.defaultMigrationAuditEventReporter
import maryk.core.models.migration.nextRuntimePhaseOrNull
import maryk.core.models.migration.normalizedRuntimePhase
import maryk.core.models.migration.orderMigrationModelIds
import maryk.core.models.migration.remainingRuntimePhaseCount
import maryk.core.models.migration.MigrationStatus.NeedsMigration
import maryk.core.models.migration.MigrationStatus.NewIndicesOnExistingProperties
import maryk.core.models.migration.MigrationStatus.NewModel
import maryk.core.models.migration.MigrationStatus.OnlySafeAdds
import maryk.core.models.migration.MigrationStatus.UpToDate
import maryk.core.models.migration.StoredRootDataModelDefinition
import maryk.core.models.migration.VersionUpdateHandler
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.wrapper.IsSensitiveValueDefinitionWrapper
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.requests.AddRequest
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.requests.GetChangesRequest
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.GetUpdatesRequest
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.requests.ScanRequest
import maryk.core.query.requests.ScanUpdatesRequest
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.rocksdb.TableType.HistoricIndex
import maryk.datastore.rocksdb.TableType.HistoricTable
import maryk.datastore.rocksdb.TableType.HistoricUnique
import maryk.datastore.rocksdb.TableType.Index
import maryk.datastore.rocksdb.TableType.Keys
import maryk.datastore.rocksdb.TableType.Model
import maryk.datastore.rocksdb.TableType.Table
import maryk.datastore.rocksdb.TableType.Unique
import maryk.datastore.rocksdb.metadata.ModelMeta
import maryk.datastore.rocksdb.metadata.readMetaFile
import maryk.datastore.rocksdb.metadata.writeMetaFile
import maryk.datastore.rocksdb.model.checkModelIfMigrationIsNeeded
import maryk.datastore.rocksdb.model.RocksDBLocalMigrationLease
import maryk.datastore.rocksdb.model.RocksDBMigrationAuditLogStore
import maryk.datastore.rocksdb.model.RocksDBMigrationStateStore
import maryk.datastore.rocksdb.model.storeModelDefinition
import maryk.datastore.rocksdb.processors.AnyAddStoreAction
import maryk.datastore.rocksdb.processors.AnyChangeStoreAction
import maryk.datastore.rocksdb.processors.AnyDeleteStoreAction
import maryk.datastore.rocksdb.processors.AnyGetChangesStoreAction
import maryk.datastore.rocksdb.processors.AnyGetStoreAction
import maryk.datastore.rocksdb.processors.AnyGetUpdatesStoreAction
import maryk.datastore.rocksdb.processors.AnyProcessUpdateResponseStoreAction
import maryk.datastore.rocksdb.processors.AnyScanChangesStoreAction
import maryk.datastore.rocksdb.processors.AnyScanStoreAction
import maryk.datastore.rocksdb.processors.AnyScanUpdatesStoreAction
import maryk.datastore.rocksdb.processors.EMPTY_ARRAY
import maryk.datastore.rocksdb.processors.VersionedComparator
import maryk.datastore.rocksdb.processors.deleteCompleteIndexContents
import maryk.datastore.rocksdb.processors.processAddRequest
import maryk.datastore.rocksdb.processors.processAdditionUpdate
import maryk.datastore.rocksdb.processors.processChangeRequest
import maryk.datastore.rocksdb.processors.processChangeUpdate
import maryk.datastore.rocksdb.processors.processDeleteRequest
import maryk.datastore.rocksdb.processors.processDeleteUpdate
import maryk.datastore.rocksdb.processors.processGetChangesRequest
import maryk.datastore.rocksdb.processors.processGetRequest
import maryk.datastore.rocksdb.processors.processGetUpdatesRequest
import maryk.datastore.rocksdb.processors.processInitialChangesUpdate
import maryk.datastore.rocksdb.processors.processScanChangesRequest
import maryk.datastore.rocksdb.processors.processScanRequest
import maryk.datastore.rocksdb.processors.processScanUpdatesRequest
import maryk.datastore.shared.AbstractDataStore
import maryk.datastore.shared.Cache
import maryk.datastore.shared.encryption.FieldEncryptionProvider
import maryk.datastore.shared.encryption.SensitiveIndexTokenProvider
import maryk.datastore.shared.updates.Update
import maryk.rocksdb.ColumnFamilyDescriptor
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.ColumnFamilyOptions
import maryk.rocksdb.ComparatorOptions
import maryk.rocksdb.DBOptions
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.RocksDB
import maryk.rocksdb.WriteOptions
import maryk.rocksdb.defaultColumnFamily
import maryk.rocksdb.openRocksDB
import kotlin.time.TimeSource

private val ENCRYPTED_VALUE_MAGIC = byteArrayOf(0x4D, 0x4B, 0x45, 0x31) // "MKE1"

class RocksDBDataStore private constructor(
    override val keepAllVersions: Boolean = true,
    relativePath: String,
    dataModelsById: Map<UInt, IsRootDataModel>,
    rocksDBOptions: DBOptions? = null,
    private val onlyCheckModelVersion: Boolean = false,
    val migrationHandler: MigrationHandler<RocksDBDataStore>? = null,
    val migrationVerifyHandler: MigrationVerifyHandler<RocksDBDataStore>? = null,
    val migrationRetryPolicy: MigrationRetryPolicy = MigrationRetryPolicy(),
    val migrationStartupBudgetMs: Long? = null,
    val continueMigrationsInBackground: Boolean = false,
    val migrationLease: MigrationLease? = null,
    val persistMigrationAuditEvents: Boolean = false,
    val migrationAuditLogMaxEntries: Int = 1000,
    val migrationAuditEventReporter: ((MigrationAuditEvent) -> Unit) = ::defaultMigrationAuditEventReporter,
    val versionUpdateHandler: VersionUpdateHandler<RocksDBDataStore>? = null,
    val fieldEncryptionProvider: FieldEncryptionProvider? = null,
) : AbstractDataStore(dataModelsById, Dispatchers.IO.limitedParallelism(1)) {
    private val columnFamilyHandlesByDataModelIndex = mutableMapOf<UInt, TableColumnFamilies>()
    private val prefixSizesByColumnFamilyHandlesIndex = mutableMapOf<Int, Int>()
    private val uniqueIndicesByDataModelIndex = atomic(mapOf<UInt, List<ByteArray>>())
    private val sensitiveReferencesByModelId: Map<UInt, SensitiveModelReferences> =
        dataModelsById.mapValues { (modelId, model) ->
            collectSensitiveReferences(modelId, model)
        }
    private val sensitiveReferencePrefixesByModelId: Map<UInt, List<ByteArray>> =
        sensitiveReferencesByModelId.mapValues { it.value.sensitiveReferences }
    private val sensitiveUniqueReferencesByModelId: Map<UInt, List<ByteArray>> =
        sensitiveReferencesByModelId.mapValues { it.value.sensitiveUniqueReferences }

    override val supportsFuzzyQualifierFiltering: Boolean = true
    override val supportsSubReferenceFiltering: Boolean = true

    // Only create Options if no Options were passed. Will take ownership and close it if this object is closed
    private val ownRocksDBOptions: DBOptions? =
        if (rocksDBOptions == null) {
            DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true)
        } else null

    internal val db: RocksDB

    internal val updateDispatcher = Dispatchers.IO

    private val storePath: String = relativePath

    private val modelMetas: MutableMap<UInt, ModelMeta> = readMetaFile(storePath).toMutableMap()

    private val defaultWriteOptions = WriteOptions()
    internal val defaultReadOptions = ReadOptions().apply {
        setPrefixSameAsStart(true)
    }

    private val scheduledVersionUpdateHandlers = mutableListOf<suspend () -> Unit>()
    private val pendingMigrationModelIds = atomic(setOf<UInt>())
    private val pendingMigrationReasons = atomic(mapOf<UInt, String>())
    private val pausedMigrationModelIds = atomic(setOf<UInt>())
    private val canceledMigrationReasons = atomic(mapOf<UInt, String>())
    private val pendingMigrationWaiters = atomic(mapOf<UInt, CompletableDeferred<Unit>>())
    private val migrationRuntimeDetailsByModelId = atomic(mapOf<UInt, MigrationRuntimeDetails>())
    private val migrationMetricsByModelId = atomic(mapOf<UInt, MigrationMetrics>())
    private var migrationAuditLogStore: MigrationAuditLogStore? = null

    init {
        val descriptors: MutableList<ColumnFamilyDescriptor> = mutableListOf()
        descriptors.add(ColumnFamilyDescriptor(defaultColumnFamily))
        for ((index, db) in dataModelsById) {
            createColumnFamilyHandles(descriptors, index, db)
        }

        val handles = mutableListOf<ColumnFamilyHandle>()
        this.db = openRocksDB(rocksDBOptions ?: ownRocksDBOptions!!, storePath, descriptors, handles)

        try {
            var handleIndex = 1
            if (keepAllVersions) {
                for ((index, db) in dataModelsById) {
                    prefixSizesByColumnFamilyHandlesIndex[handles[handleIndex+2].getID()] = db.Meta.keyByteSize
                    prefixSizesByColumnFamilyHandlesIndex[handles[handleIndex+5].getID()] = db.Meta.keyByteSize
                    columnFamilyHandlesByDataModelIndex[index] = HistoricTableColumnFamilies(
                        model = handles[handleIndex++],
                        keys = handles[handleIndex++],
                        table = handles[handleIndex++],
                        index = handles[handleIndex++],
                        unique = handles[handleIndex++],
                        historic = BasicTableColumnFamilies(
                            table = handles[handleIndex++],
                            index = handles[handleIndex++],
                            unique = handles[handleIndex++]
                        )
                    )
                }
            } else {
                for ((index, db) in dataModelsById) {
                    prefixSizesByColumnFamilyHandlesIndex[handles[handleIndex+2].getID()] = db.Meta.keyByteSize
                    columnFamilyHandlesByDataModelIndex[index] = TableColumnFamilies(
                        model = handles[handleIndex++],
                        keys = handles[handleIndex++],
                        table = handles[handleIndex++],
                        index = handles[handleIndex++],
                        unique = handles[handleIndex++]
                    )
                }
            }
        } catch (e: Throwable) {
            closeResources()
            throw e
        }
    }

    private suspend fun initAsync() {
        if (sensitiveReferencePrefixesByModelId.values.any { it.isNotEmpty() } && fieldEncryptionProvider == null) {
            throw RequestException(
                "Sensitive properties configured but no fieldEncryptionProvider set on RocksDBDataStore.open"
            )
        }
        if (sensitiveUniqueReferencesByModelId.values.any { it.isNotEmpty() } && fieldEncryptionProvider !is SensitiveIndexTokenProvider) {
            throw RequestException(
                "Sensitive unique properties configured but fieldEncryptionProvider does not implement SensitiveIndexTokenProvider"
            )
        }

        val conversionContext = DefinitionsConversionContext()
        val startupStarted = TimeSource.Monotonic.markNow()
        val effectiveMigrationLease = migrationLease ?: RocksDBLocalMigrationLease(storePath)
        val migrationStateStore = RocksDBMigrationStateStore(
            db,
            columnFamilyHandlesByDataModelIndex.mapValues { (_, tableColumnFamilies) -> tableColumnFamilies.model }
        )
        if (persistMigrationAuditEvents) {
            migrationAuditLogStore = RocksDBMigrationAuditLogStore(
                rocksDB = db,
                modelColumnFamiliesById = columnFamilyHandlesByDataModelIndex.mapValues { (_, tableColumnFamilies) -> tableColumnFamilies.model },
                maxEntries = migrationAuditLogMaxEntries
            )
        }

        for (index in orderMigrationModelIds(dataModelsById)) {
            val dataModel = dataModelsById.getValue(index)
            columnFamilyHandlesByDataModelIndex[index]?.let { tableColumnFamilies ->
                when (
                    val migrationStatus = checkModelIfMigrationIsNeeded(
                        db,
                        modelMetas[index],
                        index,
                        tableColumnFamilies.model,
                        dataModel,
                        onlyCheckModelVersion,
                        conversionContext
                    )
                ) {
                    UpToDate, MigrationStatus.AlreadyProcessed -> Unit // Do nothing since no work is needed
                    NewModel -> {
                        scheduledVersionUpdateHandlers.add {
                            versionUpdateHandler?.invoke(this, null, dataModel)
                            storeModelDefinition(db, modelMetas, index, tableColumnFamilies.model, dataModel)
                        }
                    }
                    is OnlySafeAdds -> {
                        scheduledVersionUpdateHandlers.add {
                            versionUpdateHandler?.invoke(this, migrationStatus.storedDataModel as StoredRootDataModelDefinition, dataModel)
                            storeModelDefinition(db, modelMetas, index, tableColumnFamilies.model, dataModel)
                        }
                    }
                    is NewIndicesOnExistingProperties -> {
                        fillIndex(migrationStatus.indexesToIndex, tableColumnFamilies)
                        scheduledVersionUpdateHandlers.add {
                            versionUpdateHandler?.invoke(this, migrationStatus.storedDataModel as StoredRootDataModelDefinition, dataModel)
                            storeModelDefinition(db, modelMetas, index, tableColumnFamilies.model, dataModel)
                        }
                    }
                    is NeedsMigration -> {
                        val handler = migrationHandler
                            ?: throw MigrationException("Migration needed: No migration handler present. \n$migrationStatus")
                        val storedModel = migrationStatus.storedDataModel as StoredRootDataModelDefinition
                        val migrationId = "${dataModel.Meta.name}:${storedModel.Meta.version}->${dataModel.Meta.version}"
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

                        fun launchBackgroundMigration(
                            leaseAlreadyAcquired: Boolean,
                            executeStep: suspend (MigrationState?, UInt) -> Pair<MigrationPhase, MigrationOutcome>,
                            writeState: suspend (MigrationState) -> Unit,
                        ) {
                            this.launch {
                                var hasLease = leaseAlreadyAcquired
                                try {
                                    while (!hasLease) {
                                        canceledMigrationReasons.value[index]?.let { cancelReason ->
                                            pendingMigrationReasons.update {
                                                it + (index to "Migration canceled by operator: $cancelReason")
                                            }
                                            failPendingMigration(index, "Migration canceled by operator: $cancelReason")
                                            return@launch
                                        }
                                        if (pausedMigrationModelIds.value.contains(index)) {
                                            pendingMigrationReasons.update {
                                                it + (index to "Migration paused by operator")
                                            }
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

                                    while (true) {
                                        canceledMigrationReasons.value[index]?.let { cancelReason ->
                                            pendingMigrationReasons.update {
                                                it + (index to "Migration canceled by operator: $cancelReason")
                                            }
                                            failPendingMigration(index, "Migration canceled by operator: $cancelReason")
                                            break
                                        }
                                        if (pausedMigrationModelIds.value.contains(index)) {
                                            pendingMigrationReasons.update {
                                                it + (index to "Migration paused by operator")
                                            }
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
                                                    writeState(
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
                                                migrationStatus.indexesToIndex?.let { fillIndex(it, tableColumnFamilies) }
                                                versionUpdateHandler?.invoke(this@RocksDBDataStore, storedModel, dataModel)
                                                storeModelDefinition(db, modelMetas, index, tableColumnFamilies.model, dataModel)
                                                writeMetaFile(storePath, modelMetas)
                                                pendingMigrationModelIds.update { it - index }
                                                pendingMigrationReasons.update { it - index }
                                                pausedMigrationModelIds.update { it - index }
                                                canceledMigrationReasons.update { it - index }
                                                completePendingMigration(index)
                                                break
                                            }
                                            is MigrationOutcome.Partial -> {
                                                appendMigrationAuditEvent(index, migrationId, MigrationAuditEventType.Partial, phase = phase, attempt = attempt, message = outcome.message)
                                                writeState(
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
                                                writeState(
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
                                                writeState(
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

                        var completedInStartup = false
                        var releaseLeaseInFinally: Boolean
                        suspend fun writeMigrationState(state: MigrationState) {
                            migrationStateStore.write(index, state)
                            updateMigrationRuntimeDetails(index, state)
                        }
                        suspend fun executeMigrationOrVerifyStep(previousState: MigrationState?, attempt: UInt): Pair<MigrationPhase, MigrationOutcome> {
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
                                store = this@RocksDBDataStore,
                                storedDataModel = storedModel,
                                newDataModel = dataModel,
                                migrationStatus = migrationStatus,
                                previousState = previousState,
                                attempt = attempt,
                            )
                            val outcome = when (phase) {
                                MigrationPhase.Backfill -> handler(context)
                                MigrationPhase.Verify -> migrationVerifyHandler?.invoke(context) ?: MigrationOutcome.Success
                                MigrationPhase.Expand, MigrationPhase.Contract -> MigrationOutcome.Success
                                else -> MigrationOutcome.Fatal("Unsupported migration phase $phase")
                            }
                            return phase to outcome
                        }

                        val leaseAcquired = effectiveMigrationLease.tryAcquire(index, migrationId)
                        if (!leaseAcquired) {
                            appendMigrationAuditEvent(index, migrationId, MigrationAuditEventType.LeaseRejected, message = "Lease held by other migrator")
                            if (continueMigrationsInBackground) {
                                pendingMigrationModelIds.update { it + index }
                                pendingMigrationReasons.update { it + (index to "Migration lease held by another migrator for $migrationId") }
                                ensurePendingMigrationWaiter(index)
                                launchBackgroundMigration(
                                    leaseAlreadyAcquired = false,
                                    executeStep = { previousState, attempt -> executeMigrationOrVerifyStep(previousState, attempt) },
                                    writeState = { state -> writeMigrationState(state) }
                                )
                                return@let
                            } else {
                                throw MigrationException("Migration lease could not be acquired for ${dataModel.Meta.name}: $migrationId")
                            }
                        }
                        appendMigrationAuditEvent(index, migrationId, MigrationAuditEventType.LeaseAcquired)
                        releaseLeaseInFinally = true

                        try {
                            while (true) {
                                if (migrationStartupBudgetMs != null && startupStarted.elapsedNow().inWholeMilliseconds > migrationStartupBudgetMs) {
                                    if (!continueMigrationsInBackground) {
                                        throw MigrationException("Migration startup budget exceeded for ${dataModel.Meta.name} after ${migrationStartupBudgetMs}ms")
                                    }
                                    pendingMigrationModelIds.update { it + index }
                                    pendingMigrationReasons.update {
                                        it + (index to "Migration for ${dataModel.Meta.name} is running in background")
                                    }
                                    ensurePendingMigrationWaiter(index)
                                    launchBackgroundMigration(
                                        leaseAlreadyAcquired = true,
                                        executeStep = { previousState, attempt -> executeMigrationOrVerifyStep(previousState, attempt) },
                                        writeState = { state -> writeMigrationState(state) }
                                    )
                                    releaseLeaseInFinally = false
                                    break
                                }

                                val previousState = migrationStateStore.read(index)
                                val attempt = (previousState?.attempt ?: 0u) + 1u
                                val (phase, outcome) = executeMigrationOrVerifyStep(previousState, attempt)

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
                        if (!completedInStartup) {
                            return@let
                        }

                        migrationStatus.indexesToIndex?.let {
                            fillIndex(it, tableColumnFamilies)
                        }
                        scheduledVersionUpdateHandlers.add {
                            versionUpdateHandler?.invoke(this, migrationStatus.storedDataModel as StoredRootDataModelDefinition, dataModel)
                            storeModelDefinition(db, modelMetas, index, tableColumnFamilies.model, dataModel)
                        }
                    }
                }
            }
        }

        startFlows()

        scheduledVersionUpdateHandlers.forEach {
            it()
            writeMetaFile(storePath, modelMetas)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun startFlows() {
        super.startFlows()

        this.launch {
            val cache = Cache()

            var clock = HLC()
            storeActorHasStarted.complete(Unit)
            try {
                for (storeAction in storeChannel) {
                    try {
                        clock = clock.calculateMaxTimeStamp()

                        @Suppress("UNCHECKED_CAST")
                        when (storeAction.request) {
                            is AddRequest<*> ->
                                processAddRequest(clock, storeAction as AnyAddStoreAction)
                            is ChangeRequest<*> ->
                                processChangeRequest(clock, storeAction as AnyChangeStoreAction)
                            is DeleteRequest<*> ->
                                processDeleteRequest(clock, storeAction as AnyDeleteStoreAction, cache)
                            is GetRequest<*> ->
                                processGetRequest(storeAction as AnyGetStoreAction, cache)
                            is GetChangesRequest<*> ->
                                processGetChangesRequest(storeAction as AnyGetChangesStoreAction, cache)
                            is GetUpdatesRequest<*> ->
                                processGetUpdatesRequest(storeAction as AnyGetUpdatesStoreAction, cache)
                            is ScanRequest<*> ->
                                processScanRequest(storeAction as AnyScanStoreAction, cache)
                            is ScanChangesRequest<*> ->
                                processScanChangesRequest(storeAction as AnyScanChangesStoreAction, cache)
                            is ScanUpdatesRequest<*> ->
                                processScanUpdatesRequest(storeAction as AnyScanUpdatesStoreAction, cache)
                            is UpdateResponse<*> -> when(val update = (storeAction.request as UpdateResponse<*>).update) {
                                is AdditionUpdate<*> -> processAdditionUpdate(storeAction as AnyProcessUpdateResponseStoreAction)
                                is ChangeUpdate<*> -> processChangeUpdate(storeAction as AnyProcessUpdateResponseStoreAction)
                                is RemovalUpdate<*> -> processDeleteUpdate(storeAction as AnyProcessUpdateResponseStoreAction, cache)
                                is InitialChangesUpdate<*> -> processInitialChangesUpdate(storeAction as AnyProcessUpdateResponseStoreAction)
                                is InitialValuesUpdate<*> -> throw RequestException("Cannot process Values requests into data store since they do not contain all version information, do a changes request")
                                is OrderedKeysUpdate<*> -> throw RequestException("Cannot process Update requests into data store since they do not contain all change information, do a changes request")
                                else -> throw TypeException("Unknown update type $update for datastore processing")
                            }
                            else -> throw TypeException("Unknown request type ${storeAction.request}")
                        }
                    } catch (e: CancellationException) {
                        storeAction.response.cancel(e)
                        throw e
                    } catch (e: Throwable) {
                        storeAction.response.completeExceptionally(e)
                    }
                }
            } finally {
                while (!storeChannel.isEmpty) {
                    val pending = storeChannel.tryReceive().getOrNull() ?: break
                    pending.response.completeExceptionally(CancellationException("Datastore closing"))
                }
            }
        }
    }

    /** Walk all current values in [tableColumnFamilies] and fill [indexesToIndex] */
    private fun fillIndex(
        indexesToIndex: List<IsIndexable>,
        tableColumnFamilies: TableColumnFamilies
    ) {
        for (indexable in indexesToIndex) {
            deleteCompleteIndexContents(this.db, tableColumnFamilies, indexable)
        }

        walkDataRecordsAndFillIndex(this, tableColumnFamilies, indexesToIndex)
    }

    private fun createColumnFamilyHandles(descriptors: MutableList<ColumnFamilyDescriptor>, tableIndex: UInt, db: IsRootDataModel) {
        val nameSize = tableIndex.calculateVarByteLength() + 1

        // Prefix set to key size for more optimal search.
        val tableOptions = ColumnFamilyOptions().apply {
            useFixedLengthPrefixExtractor(db.Meta.keyByteSize)
        }

        descriptors += Model.getDescriptor(tableIndex, nameSize)
        descriptors += Keys.getDescriptor(tableIndex, nameSize)
        descriptors += Table.getDescriptor(tableIndex, nameSize, tableOptions)
        descriptors += Index.getDescriptor(tableIndex, nameSize)
        descriptors += Unique.getDescriptor(tableIndex, nameSize)

        if (keepAllVersions) {
            val comparatorOptions = ComparatorOptions()
            val comparator = VersionedComparator(comparatorOptions, db.Meta.keyByteSize)
            // Prefix set to key size for more optimal search.
            val tableOptionsHistoric = ColumnFamilyOptions().apply {
                useFixedLengthPrefixExtractor(db.Meta.keyByteSize)
                setComparator(comparator)
            }

            // Prefix set to key size for more optimal search.
            val indexOptionsHistoric = ColumnFamilyOptions().apply {
                setComparator(comparator)
            }

            descriptors += HistoricTable.getDescriptor(tableIndex, nameSize, tableOptionsHistoric)
            descriptors += HistoricIndex.getDescriptor(tableIndex, nameSize, indexOptionsHistoric)
            descriptors += HistoricUnique.getDescriptor(tableIndex, nameSize, indexOptionsHistoric)
        }
    }

    internal fun getPrefixSize(columnFamilyHandle: ColumnFamilyHandle) =
        this.prefixSizesByColumnFamilyHandlesIndex.getOrElse(columnFamilyHandle.getID()) { 0 }

    override suspend fun close() {
        super.close()

        closeResources()
    }

    fun closeResources() {
        ownRocksDBOptions?.close()
        defaultWriteOptions.close()
        defaultReadOptions.close()

        columnFamilyHandlesByDataModelIndex.values.forEach {
            it.close()
        }
        db.close()
    }

    internal fun getColumnFamilies(dbIndex: UInt) =
        columnFamilyHandlesByDataModelIndex[dbIndex]
            ?: throw DefNotFoundException("DataModel definition not found for $dbIndex")

    internal fun getColumnFamilies(dataModel: IsRootDataModel) =
        columnFamilyHandlesByDataModelIndex[dataModelIdsByString[dataModel.Meta.name]]
            ?: throw DefNotFoundException("DataModel definition not found for ${dataModel.Meta.name}")

    internal fun readStoredModelNamesById(): Map<UInt, String> =
        modelMetas.mapValues { it.value.name }

    /** Get the unique indexes for [dbIndex] and [uniqueHandle] */
    internal fun getUniqueIndices(dbIndex: UInt, uniqueHandle: ColumnFamilyHandle) =
        uniqueIndicesByDataModelIndex.value[dbIndex] ?: searchExistingUniqueIndices(uniqueHandle)

    /**
     * Checks if unique index exists and creates it if not otherwise.
     * This is needed so delete knows which indexes to scan for values to delete.
     */
    internal fun createUniqueIndexIfNotExists(dbIndex: UInt, uniqueHandle: ColumnFamilyHandle, uniqueName: ByteArray) {
        val existingDbUniques = uniqueIndicesByDataModelIndex.value[dbIndex]
            ?: searchExistingUniqueIndices(uniqueHandle)
        val existingValue = existingDbUniques.find { it.contentEquals(uniqueName) }

        if (existingValue == null) {
            val uniqueReference = byteArrayOf(0, *uniqueName)
            db.put(uniqueHandle, uniqueReference, EMPTY_ARRAY)

            uniqueIndicesByDataModelIndex.value =
                uniqueIndicesByDataModelIndex.value.plus(
                    dbIndex to existingDbUniques.plus(uniqueName)
                )
        }
    }

    /** Search for existing unique indexes in data store by [uniqueHandle] */
    private fun searchExistingUniqueIndices(
        uniqueHandle: ColumnFamilyHandle
    ) = buildList {
        this@RocksDBDataStore.db.newIterator(uniqueHandle).use { iterator ->
            while (iterator.isValid()) {
                val key = iterator.key()
                if (key[0] != 0.toByte()) {
                    break // break because it is not describing an index
                }
                this += key.copyOfRange(1, key.size)
            }
        }
    }

    internal fun emitUpdate(updateToEmit: Update<*>?) {
        if (updateToEmit != null) {
            launch(this.updateDispatcher, start = CoroutineStart.UNDISPATCHED) {
                updateSharedFlow.emit(updateToEmit)
            }
        }
    }

    internal fun emitUpdates(updatesToEmit: List<Update<*>>) {
        launch(this.updateDispatcher, start = CoroutineStart.UNDISPATCHED) {
            updateSharedFlow.emitAll(updatesToEmit.asFlow())
        }
    }

    internal fun encryptValueIfSensitive(modelId: UInt, reference: ByteArray, value: ByteArray): ByteArray {
        if (!isSensitiveReference(modelId, reference)) return value
        val provider = fieldEncryptionProvider
            ?: throw RequestException("No fieldEncryptionProvider configured for sensitive property write")
        val encrypted = runBlocking { provider.encrypt(value) }
        return ENCRYPTED_VALUE_MAGIC + encrypted
    }

    internal fun decryptValueIfNeeded(value: ByteArray): ByteArray {
        if (!isEncryptedValue(value)) return value
        val provider = fieldEncryptionProvider
            ?: throw RequestException("Encrypted value encountered but no fieldEncryptionProvider configured")
        return runBlocking { provider.decrypt(value.copyOfRange(ENCRYPTED_VALUE_MAGIC.size, value.size)) }
    }

    internal fun mapUniqueValueBytes(modelId: UInt, reference: ByteArray, value: ByteArray): ByteArray {
        if (!isSensitiveUniqueReference(modelId, reference)) return value
        val tokenProvider = fieldEncryptionProvider as? SensitiveIndexTokenProvider
            ?: throw RequestException("Sensitive unique property requires SensitiveIndexTokenProvider")
        return runBlocking { tokenProvider.deriveDeterministicToken(modelId, reference, value) }
    }

    private fun isSensitiveReference(modelId: UInt, reference: ByteArray): Boolean =
        sensitiveReferencePrefixesByModelId[modelId]?.any { prefix -> reference.hasPrefix(prefix) } == true

    private fun isSensitiveUniqueReference(modelId: UInt, reference: ByteArray): Boolean =
        sensitiveUniqueReferencesByModelId[modelId]?.any { it.contentEquals(reference) } == true

    private fun collectSensitiveReferences(modelId: UInt, dataModel: IsRootDataModel): SensitiveModelReferences {
        val sensitiveReferences = mutableListOf<ByteArray>()
        val sensitiveUniqueReferences = mutableListOf<ByteArray>()
        collectSensitiveReferencesRecursive(
            modelId = modelId,
            rootDataModel = dataModel,
            dataModel = dataModel,
            parentRef = null,
            sensitiveReferences = sensitiveReferences,
            sensitiveUniqueReferences = sensitiveUniqueReferences,
            modelPath = mutableListOf()
        )
        return SensitiveModelReferences(sensitiveReferences, sensitiveUniqueReferences)
    }

    private fun collectSensitiveReferencesRecursive(
        modelId: UInt,
        rootDataModel: IsRootDataModel,
        dataModel: IsValuesDataModel,
        parentRef: AnyPropertyReference?,
        sensitiveReferences: MutableList<ByteArray>,
        sensitiveUniqueReferences: MutableList<ByteArray>,
        modelPath: MutableList<IsValuesDataModel>
    ) {
        if (modelPath.any { it === dataModel }) return
        modelPath += dataModel
        try {
            dataModel.forEach { wrapper ->
                val propertyReference = wrapper.ref(parentRef)
                val reference = propertyReference.toStorageByteArray()
                if (wrapper is IsSensitiveValueDefinitionWrapper<*, *, *, *> && wrapper.sensitive) {
                    val isUnique = validateSensitiveWrapper(modelId, rootDataModel, dataModel, wrapper, propertyReference)
                    sensitiveReferences += reference
                    if (isUnique) {
                        sensitiveUniqueReferences += reference
                    }
                }
                val definition = wrapper.definition
                if (definition is EmbeddedValuesDefinition<*>) {
                    collectSensitiveReferencesRecursive(
                        modelId = modelId,
                        rootDataModel = rootDataModel,
                        dataModel = definition.dataModel,
                        parentRef = wrapper.ref(parentRef),
                        sensitiveReferences = sensitiveReferences,
                        sensitiveUniqueReferences = sensitiveUniqueReferences,
                        modelPath = modelPath
                    )
                }
            }
        } finally {
            modelPath.removeAt(modelPath.lastIndex)
        }
    }

    private fun validateSensitiveWrapper(
        modelId: UInt,
        rootDataModel: IsRootDataModel,
        dataModel: IsValuesDataModel,
        wrapper: IsSensitiveValueDefinitionWrapper<*, *, *, *>,
        propertyReference: AnyPropertyReference
    ): Boolean {
        val definition = wrapper.definition
        val isUnique = (definition as? IsComparableDefinition<*, *>)?.unique == true
        val indexed = rootDataModel.Meta.indexes?.any {
            it.isForPropertyReference(propertyReference)
        } == true
        if (indexed) {
            throw RequestException(
                "Sensitive property ${dataModel.Meta.name}.${wrapper.name} (modelId=$modelId) cannot be indexed yet. Use explicit blind-index field"
            )
        }
        return isUnique
    }

    private fun IsIndexable.isForPropertyReference(propertyReference: AnyPropertyReference): Boolean = when (this) {
        is IsIndexablePropertyReference<*> -> isForPropertyReference(propertyReference)
        is Multiple -> references.any { it is IsIndexablePropertyReference<*> && it.isForPropertyReference(propertyReference) }
        else -> false
    }

    private fun isEncryptedValue(value: ByteArray): Boolean =
        value.size >= ENCRYPTED_VALUE_MAGIC.size &&
            value[0] == ENCRYPTED_VALUE_MAGIC[0] &&
            value[1] == ENCRYPTED_VALUE_MAGIC[1] &&
            value[2] == ENCRYPTED_VALUE_MAGIC[2] &&
            value[3] == ENCRYPTED_VALUE_MAGIC[3]

    private fun ByteArray.hasPrefix(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }

    fun pendingMigrations(): Map<UInt, String> = pendingMigrationReasons.value

    fun migrationStatus(modelId: UInt): MigrationRuntimeStatus {
        val reason = pendingMigrationReasons.value[modelId] ?: return MigrationRuntimeStatus(MigrationRuntimeState.Idle)
        val details = migrationRuntimeDetailsByModelId.value[modelId]
        val state = when {
            canceledMigrationReasons.value.containsKey(modelId) -> MigrationRuntimeState.Canceled
            pausedMigrationModelIds.value.contains(modelId) -> MigrationRuntimeState.Paused
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
            etaMs = details?.etaMs
        )
    }

    fun migrationStatuses(): Map<UInt, MigrationRuntimeStatus> =
        pendingMigrationReasons.value.mapValues { (modelId, reason) ->
            val details = migrationRuntimeDetailsByModelId.value[modelId]
            val state = when {
                canceledMigrationReasons.value.containsKey(modelId) -> MigrationRuntimeState.Canceled
                pausedMigrationModelIds.value.contains(modelId) -> MigrationRuntimeState.Paused
                details?.lastError != null || reason.contains("failed", ignoreCase = true) -> MigrationRuntimeState.Failed
                else -> MigrationRuntimeState.Running
            }
            MigrationRuntimeStatus(
                state = state,
                message = reason,
                phase = details?.phase,
                attempt = details?.attempt,
                lastError = details?.lastError,
                hasCursor = details?.hasCursor,
                etaMs = details?.etaMs
            )
        }

    fun pauseMigration(modelId: UInt): Boolean {
        if (!pendingMigrationModelIds.value.contains(modelId)) return false
        pausedMigrationModelIds.update { it + modelId }
        pendingMigrationReasons.update { it + (modelId to "Migration paused by operator") }
        migrationRuntimeDetailsByModelId.value[modelId]?.let { details ->
            runBlocking {
                appendMigrationAuditEvent(
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

    fun resumeMigration(modelId: UInt): Boolean {
        val wasPaused = pausedMigrationModelIds.value.contains(modelId)
        if (!wasPaused) return false
        pausedMigrationModelIds.update { it - modelId }
        if (pendingMigrationModelIds.value.contains(modelId)) {
            pendingMigrationReasons.update { it + (modelId to "Migration resumed") }
        }
        migrationRuntimeDetailsByModelId.value[modelId]?.let { details ->
            runBlocking {
                appendMigrationAuditEvent(
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

    fun cancelMigration(modelId: UInt, reason: String = "Canceled by operator"): Boolean {
        if (!pendingMigrationModelIds.value.contains(modelId)) return false
        canceledMigrationReasons.update { it + (modelId to reason) }
        pausedMigrationModelIds.update { it - modelId }
        pendingMigrationReasons.update { it + (modelId to "Migration canceled by operator: $reason") }
        migrationRuntimeDetailsByModelId.value[modelId]?.let { details ->
            runBlocking {
                appendMigrationAuditEvent(
                    modelId = modelId,
                    migrationId = details.migrationId,
                    type = MigrationAuditEventType.Canceled,
                    phase = details.phase,
                    attempt = details.attempt,
                    message = reason
                )
            }
        }
        failPendingMigration(modelId, "Migration canceled by operator: $reason")
        return true
    }

    fun migrationMetrics(modelId: UInt): MigrationMetrics =
        migrationMetricsByModelId.value[modelId] ?: MigrationMetrics()

    fun migrationMetrics(): Map<UInt, MigrationMetrics> = migrationMetricsByModelId.value

    suspend fun migrationAuditEvents(modelId: UInt, limit: Int = 100): List<MigrationAuditEvent> =
        migrationAuditLogStore?.read(modelId, limit) ?: emptyList()

    suspend fun awaitMigration(modelId: UInt) {
        pendingMigrationWaiters.value[modelId]?.await()
    }

    private fun ensurePendingMigrationWaiter(modelId: UInt): CompletableDeferred<Unit> {
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

    private fun completePendingMigration(modelId: UInt) {
        var waiter: CompletableDeferred<Unit>? = null
        pendingMigrationWaiters.update { current ->
            waiter = current[modelId]
            current - modelId
        }
        waiter?.complete(Unit)
        migrationRuntimeDetailsByModelId.update { it - modelId }
    }

    private fun failPendingMigration(modelId: UInt, reason: String) {
        var waiter: CompletableDeferred<Unit>? = null
        pendingMigrationWaiters.update { current ->
            waiter = current[modelId]
            current - modelId
        }
        waiter?.completeExceptionally(MigrationException(reason))
    }

    private fun updateMigrationRuntimeDetails(modelId: UInt, state: MigrationState) {
        val nowMs = HLC().toPhysicalUnixTime().toLong()
        migrationRuntimeDetailsByModelId.update {
            val previous = it[modelId]
            val stepDurationMs = previous?.lastUpdateAtMs?.let { last -> (nowMs - last).coerceAtLeast(0) }
            val averageStepMs = when {
                previous?.averageStepMs == null -> stepDurationMs
                stepDurationMs == null -> previous.averageStepMs
                else -> ((previous.averageStepMs * 3L) + stepDurationMs) / 4L
            }
            val phase = state.phase.normalizedRuntimePhase()
            it + (
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
    }

    private suspend fun appendMigrationAuditEvent(
        modelId: UInt,
        migrationId: String,
        type: MigrationAuditEventType,
        phase: MigrationPhase? = null,
        attempt: UInt? = null,
        message: String? = null,
    ) {
        val event = MigrationAuditEvent(
            timestampMs = HLC().toPhysicalUnixTime().toLong(),
            modelId = modelId,
            migrationId = migrationId,
            type = type,
            phase = phase?.normalizedRuntimePhase(),
            attempt = attempt,
            message = message
        )
        runCatching { migrationAuditEventReporter(event) }
        migrationAuditLogStore?.append(modelId, event)
        incrementMigrationMetric(modelId, type)
    }

    private fun incrementMigrationMetric(modelId: UInt, type: MigrationAuditEventType) {
        val nowMs = HLC().toPhysicalUnixTime().toLong()
        migrationMetricsByModelId.update { current ->
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
            current + (modelId to updated)
        }
    }

    override fun assertModelReady(dataModelId: UInt) {
        if (pendingMigrationModelIds.value.contains(dataModelId)) {
            val modelName = dataModelsById[dataModelId]?.Meta?.name ?: dataModelId.toString()
            val reason = pendingMigrationReasons.value[dataModelId] ?: "Migration in progress"
            throw RequestException("Model $modelName is unavailable while migration is running: $reason")
        }
    }

    companion object {
        suspend fun open(
            keepAllVersions: Boolean = true,
            relativePath: String,
            dataModelsById: Map<UInt, IsRootDataModel>,
            rocksDBOptions: DBOptions? = null,
            onlyCheckModelVersion: Boolean = false,
            migrationHandler: MigrationHandler<RocksDBDataStore>? = null,
            migrationVerifyHandler: MigrationVerifyHandler<RocksDBDataStore>? = null,
            migrationRetryPolicy: MigrationRetryPolicy = MigrationRetryPolicy(),
            migrationStartupBudgetMs: Long? = null,
            continueMigrationsInBackground: Boolean = false,
            migrationLease: MigrationLease? = null,
            persistMigrationAuditEvents: Boolean = false,
            migrationAuditLogMaxEntries: Int = 1000,
            migrationAuditEventReporter: ((MigrationAuditEvent) -> Unit) = ::defaultMigrationAuditEventReporter,
            versionUpdateHandler: VersionUpdateHandler<RocksDBDataStore>? = null,
            fieldEncryptionProvider: FieldEncryptionProvider? = null,
        ): RocksDBDataStore {
            return RocksDBDataStore(
                keepAllVersions = keepAllVersions,
                relativePath = relativePath,
                dataModelsById = dataModelsById,
                rocksDBOptions = rocksDBOptions,
                onlyCheckModelVersion = onlyCheckModelVersion,
                migrationHandler = migrationHandler,
                migrationVerifyHandler = migrationVerifyHandler,
                migrationRetryPolicy = migrationRetryPolicy,
                migrationStartupBudgetMs = migrationStartupBudgetMs,
                continueMigrationsInBackground = continueMigrationsInBackground,
                migrationLease = migrationLease,
                persistMigrationAuditEvents = persistMigrationAuditEvents,
                migrationAuditLogMaxEntries = migrationAuditLogMaxEntries,
                migrationAuditEventReporter = migrationAuditEventReporter,
                versionUpdateHandler = versionUpdateHandler,
                fieldEncryptionProvider = fieldEncryptionProvider,
            ).apply {
                try {
                    initAsync()
                } catch (e: Throwable) {
                    this.close()
                    throw e
                }
            }
        }
    }
}

private data class SensitiveModelReferences(
    val sensitiveReferences: List<ByteArray>,
    val sensitiveUniqueReferences: List<ByteArray>,
)

private data class MigrationRuntimeDetails(
    val migrationId: String,
    val phase: MigrationPhase?,
    val attempt: UInt?,
    val lastError: String?,
    val hasCursor: Boolean?,
    val retryCount: UInt = 0u,
    val startedAtMs: Long,
    val lastUpdateAtMs: Long,
    val averageStepMs: Long?,
    val etaMs: Long?,
)
