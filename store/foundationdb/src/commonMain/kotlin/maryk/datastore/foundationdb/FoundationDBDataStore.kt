package maryk.datastore.foundationdb

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import maryk.core.clock.HLC
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.migration.MigrationAuditEvent
import maryk.core.models.migration.MigrationAuditEventType
import maryk.core.models.migration.MigrationAuditLogStore
import maryk.core.models.migration.MigrationConfiguration
import maryk.core.models.migration.MigrationMetrics
import maryk.core.models.migration.MigrationPhase
import maryk.core.models.migration.MigrationRuntimeStatus
import maryk.core.models.migration.MigrationState
import maryk.core.models.migration.MigrationStatus
import maryk.core.models.migration.MigrationStatus.NeedsMigration
import maryk.core.models.migration.MigrationStatus.NewIndicesOnExistingProperties
import maryk.core.models.migration.MigrationStatus.NewModel
import maryk.core.models.migration.MigrationStatus.OnlySafeAdds
import maryk.core.models.migration.MigrationStatus.UpToDate
import maryk.core.models.migration.StoredRootDataModelDefinition
import maryk.core.models.migration.VersionUpdateHandler
import maryk.core.models.migration.orderMigrationModelIds
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.wrapper.IsSensitiveValueDefinitionWrapper
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.properties.types.Key
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
import maryk.datastore.foundationdb.clusterlog.ClusterUpdateLog
import maryk.datastore.foundationdb.metadata.readStoredModelNames
import maryk.datastore.foundationdb.model.FoundationDBMigrationAuditLogStore
import maryk.datastore.foundationdb.model.FoundationDBMigrationLease
import maryk.datastore.foundationdb.model.FoundationDBMigrationStateStore
import maryk.datastore.foundationdb.model.checkModelIfMigrationIsNeeded
import maryk.datastore.foundationdb.model.modelUpdateHistoryBackfillCompleteKey
import maryk.datastore.foundationdb.model.storeModelDefinition
import maryk.datastore.foundationdb.processors.AnyAddStoreAction
import maryk.datastore.foundationdb.processors.AnyChangeStoreAction
import maryk.datastore.foundationdb.processors.AnyDeleteStoreAction
import maryk.datastore.foundationdb.processors.AnyGetChangesStoreAction
import maryk.datastore.foundationdb.processors.AnyGetStoreAction
import maryk.datastore.foundationdb.processors.AnyGetUpdatesStoreAction
import maryk.datastore.foundationdb.processors.AnyProcessUpdateResponseStoreAction
import maryk.datastore.foundationdb.processors.AnyScanChangesStoreAction
import maryk.datastore.foundationdb.processors.AnyScanStoreAction
import maryk.datastore.foundationdb.processors.AnyScanUpdatesStoreAction
import maryk.datastore.foundationdb.processors.EMPTY_BYTEARRAY
import maryk.datastore.foundationdb.processors.SOFT_DELETE_INDICATOR
import maryk.datastore.foundationdb.processors.deleteCompleteIndexContents
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.getLastVersion
import maryk.datastore.foundationdb.processors.helpers.nextBlocking
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.readReversedVersionBytes
import maryk.datastore.foundationdb.processors.helpers.toReversedVersionBytes
import maryk.datastore.foundationdb.processors.helpers.unwrapFdb
import maryk.datastore.foundationdb.processors.processAddRequest
import maryk.datastore.foundationdb.processors.processAdditionUpdate
import maryk.datastore.foundationdb.processors.processChangeRequest
import maryk.datastore.foundationdb.processors.processChangeUpdate
import maryk.datastore.foundationdb.processors.processDeleteRequest
import maryk.datastore.foundationdb.processors.processDeleteUpdate
import maryk.datastore.foundationdb.processors.processGetChangesRequest
import maryk.datastore.foundationdb.processors.processGetRequest
import maryk.datastore.foundationdb.processors.processGetUpdatesRequest
import maryk.datastore.foundationdb.processors.processInitialChangesUpdate
import maryk.datastore.foundationdb.processors.processScanChangesRequest
import maryk.datastore.foundationdb.processors.processScanRequest
import maryk.datastore.foundationdb.processors.processScanUpdatesRequest
import maryk.datastore.foundationdb.processors.walkDataRecordsAndFillIndex
import maryk.datastore.shared.AbstractDataStore
import maryk.datastore.shared.Cache
import maryk.datastore.shared.encryption.FieldEncryptionProvider
import maryk.datastore.shared.encryption.SensitiveIndexTokenProvider
import maryk.datastore.shared.migration.MigrationRuntimeDetails
import maryk.datastore.shared.updates.Update
import maryk.foundationdb.DatabaseOptions
import maryk.foundationdb.FDB
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.Transaction
import maryk.foundationdb.TransactionContext
import maryk.foundationdb.directory.DirectoryLayer
import maryk.foundationdb.directory.DirectorySubspace
import maryk.lib.bytes.combineToByteArray
import maryk.lib.extensions.compare.nextByteInSameLength
import maryk.foundationdb.Range
import maryk.datastore.foundationdb.processors.helpers.VERSION_BYTE_SIZE
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

private val storeMetadataModelsByIdDirectoryPath = listOf("__meta__", "models_by_id")
private val ENCRYPTED_VALUE_MAGIC = byteArrayOf(0x4D, 0x4B, 0x45, 0x31) // "MKE1"
private const val UPDATE_HISTORY_BACKFILL_WRITE_BATCH_SIZE = 256

/**
 * FoundationDB DataStore (JVM-only).
 */
class FoundationDBDataStore private constructor(
    override val keepAllVersions: Boolean = true,
    override val keepUpdateHistoryIndex: Boolean = false,
    val fdbClusterFilePath: String? = null,
    val directoryRootPath: List<String> = listOf("maryk"),
    dataModelsById: Map<UInt, IsRootDataModel>,
    private val onlyCheckModelVersion: Boolean = false,
    val databaseOptionsSetter: DatabaseOptions.() -> Unit = {},
    val migrationConfiguration: MigrationConfiguration<FoundationDBDataStore> = MigrationConfiguration(),
    val migrationLeaseConfiguration: FoundationDBMigrationLeaseConfiguration = FoundationDBMigrationLeaseConfiguration(),
    val versionUpdateHandler: VersionUpdateHandler<FoundationDBDataStore>? = null,
    private val clusterUpdateLogConfiguration: FoundationDBClusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(),
    private val fieldEncryptionProvider: FieldEncryptionProvider? = null,
) : AbstractDataStore(dataModelsById, Dispatchers.IO.limitedParallelism(1)) {
    override val supportsFuzzyQualifierFiltering: Boolean = true
    override val supportsSubReferenceFiltering: Boolean = true

    private val fdb = FDB.selectAPIVersion(730)
    private val db = (if (fdbClusterFilePath != null) fdb.open(fdbClusterFilePath) else fdb.open())
    internal val tc: TransactionContext = db

    init {
        db.options().apply {
            databaseOptionsSetter()
        }
    }

    // Keep the actor single-threaded for transactional safety while dispatching updates on IO.
    internal val updateDispatcher = Dispatchers.IO

    private val scheduledVersionUpdateHandlers = mutableListOf<suspend () -> Unit>()
    private val updateHistoryReadyModelIds = atomic(setOf<UInt>())
    internal val pendingMigrationModelIds = atomic(setOf<UInt>())
    internal val pendingMigrationReasons = atomic(mapOf<UInt, String>())
    internal val pausedMigrationModelIds = atomic(setOf<UInt>())
    internal val canceledMigrationReasons = atomic(mapOf<UInt, String>())
    internal val pendingMigrationWaiters = atomic(mapOf<UInt, CompletableDeferred<Unit>>())
    internal val migrationRuntimeDetailsByModelId = atomic(mapOf<UInt, MigrationRuntimeDetails>())
    internal val migrationMetricsByModelId = atomic(mapOf<UInt, MigrationMetrics>())
    internal var migrationAuditLogStore: MigrationAuditLogStore? = null

    private lateinit var rootDirectory: DirectorySubspace
    private lateinit var metadataDirectory: DirectorySubspace
    private lateinit var metadataPrefix: ByteArray
    internal val directoriesByDataModelIndex = mutableMapOf<UInt, IsTableDirectories>()

    // Cluster HLC sync: store actor uses max(observedClusterHlc, local wall clock) when generating new versions.
    private val observedClusterHlc = atomic(0uL)
    private var clusterUpdateLogHeadGroupCount: Int = 0
    private val activeUpdateListenersByModelId = dataModelsById.keys.associateWith { atomic(0) }
    private val clusterLogTailTransactions = atomic(0L)
    private val clusterLogDecodedUpdates = atomic(0L)
    private val clusterLogTailErrors = atomic(0L)
    private val clusterLogGcRuns = atomic(0L)
    private val clusterLogGcErrors = atomic(0L)
    private val clusterLogCurrentBackoffMs = atomic(0L)
    private val clusterLogHlcSyncTransactions = atomic(0L)
    private val clusterLogHlcSyncErrors = atomic(0L)
    private val clusterLogHlcCurrentBackoffMs = atomic(0L)
    private val clusterLogLastTailAtMs = atomic(0L)
    private val clusterLogLastDecodedAtMs = atomic(0L)
    private val clusterLogLastHlcSyncAtMs = atomic(0L)
    private val isClosing = atomic(false)
    private val sensitiveReferencesByModelId: Map<UInt, SensitiveModelReferences> =
        dataModelsById.mapValues { (modelId, model) ->
            collectSensitiveReferences(modelId, model)
        }
    private val sensitiveReferencePrefixesByModelId: Map<UInt, List<ByteArray>> =
        sensitiveReferencesByModelId.mapValues { it.value.sensitiveReferences }
    private val sensitiveUniqueReferencesByModelId: Map<UInt, List<ByteArray>> =
        sensitiveReferencesByModelId.mapValues { it.value.sensitiveUniqueReferences }

    internal inline fun <T> runTransaction(
        crossinline block: (Transaction) -> T
    ): T {
        if (isClosing.value) throw CancellationException("Datastore closing")
        return tc.run { tr ->
            block(tr)
        }
    }

    internal inline fun <T> runTransactionAsync(
        crossinline block: (Transaction) -> FdbFuture<T>
    ): FdbFuture<T> {
        if (isClosing.value) throw CancellationException("Datastore closing")
        return tc.runAsync { tr ->
            block(tr)
        }
    }

    suspend fun initAsync() {
        rootDirectory = runTransactionAsync { tr ->
            DirectoryLayer.getDefault().createOrOpen(tr, directoryRootPath)
        }.await()

        metadataDirectory = runTransaction { tr ->
            rootDirectory.createOrOpen(tr, storeMetadataModelsByIdDirectoryPath).awaitResult()
        }
        metadataPrefix = metadataDirectory.pack()

        for ((index, dataModel) in dataModelsById) {
            directoriesByDataModelIndex[index] = openTableDirs(dataModel.Meta.name, historic = keepAllVersions)
        }

        val conversionContext = DefinitionsConversionContext()
        val startupStarted = TimeSource.Monotonic.markNow()
        val effectiveMigrationLease = migrationConfiguration.migrationLease ?: FoundationDBMigrationLease(
            tc = tc,
            modelPrefixesById = directoriesByDataModelIndex.mapValues { (_, tableDirectories) -> tableDirectories.modelPrefix },
            scope = this,
            leaseTimeoutMs = migrationLeaseConfiguration.migrationLeaseTimeoutMs,
            heartbeatIntervalMs = migrationLeaseConfiguration.migrationLeaseHeartbeatMs,
        )
        val migrationStateStore = FoundationDBMigrationStateStore(
            tc = tc,
            modelPrefixesById = directoriesByDataModelIndex.mapValues { (_, tableDirectories) -> tableDirectories.modelPrefix }
        )
        if (migrationConfiguration.persistMigrationAuditEvents) {
            migrationAuditLogStore = FoundationDBMigrationAuditLogStore(
                tc = tc,
                modelPrefixesById = directoriesByDataModelIndex.mapValues { (_, tableDirectories) -> tableDirectories.modelPrefix },
                maxEntries = migrationConfiguration.migrationAuditLogMaxEntries
            )
        }

        for (index in orderMigrationModelIds(dataModelsById)) {
            val dataModel = dataModelsById.getValue(index)
            directoriesByDataModelIndex[index]?.let { tableDirectories ->
                when (
                    val migrationStatus = checkModelIfMigrationIsNeeded(
                        tc,
                        metadataPrefix,
                        index,
                        tableDirectories.modelPrefix,
                        dataModel,
                        onlyCheckModelVersion,
                        conversionContext
                    )
                ) {
                    UpToDate, MigrationStatus.AlreadyProcessed -> Unit // Do nothing since no work is needed
                    NewModel -> {
                        // Persist model metadata immediately to ensure subsequent opens see it
                        storeModelDefinition(tc, metadataPrefix, index, tableDirectories.modelPrefix, dataModel)
                        scheduledVersionUpdateHandlers.add {
                            versionUpdateHandler?.invoke(this, null, dataModel)
                        }
                    }
                    is OnlySafeAdds -> {
                        // Conservatively (re)index all indexes defined on the new model
                        dataModel.Meta.indexes?.let { idxs ->
                            if (idxs.isNotEmpty()) {
                                fillIndex(idxs, tableDirectories)
                            }
                        }
                        storeModelDefinition(tc, metadataPrefix, index, tableDirectories.modelPrefix, dataModel)
                        scheduledVersionUpdateHandlers.add {
                            versionUpdateHandler?.invoke(this, migrationStatus.storedDataModel as StoredRootDataModelDefinition, dataModel)
                        }
                    }
                    is NewIndicesOnExistingProperties -> {
                        fillIndex(migrationStatus.indexesToIndex, tableDirectories)
                        storeModelDefinition(tc, metadataPrefix, index, tableDirectories.modelPrefix, dataModel)
                        scheduledVersionUpdateHandlers.add {
                            versionUpdateHandler?.invoke(this, migrationStatus.storedDataModel as StoredRootDataModelDefinition, dataModel)
                        }
                    }
                    is NeedsMigration -> handleRequiredMigration(
                        index = index,
                        dataModel = dataModel,
                        migrationStatus = migrationStatus,
                        startupStarted = startupStarted,
                        effectiveMigrationLease = effectiveMigrationLease,
                        migrationStateStore = migrationStateStore,
                        recheckMigrationStatus = {
                            checkModelIfMigrationIsNeeded(
                                tc = tc,
                                metadataPrefix = metadataPrefix,
                                modelId = index,
                                model = tableDirectories.modelPrefix,
                                dataModel = dataModel,
                                onlyCheckModelVersion = onlyCheckModelVersion,
                                conversionContext = DefinitionsConversionContext(),
                            )
                        },
                        finalizeInBackground = { storedModel ->
                            migrationStatus.indexesToIndex?.let { fillIndex(it, tableDirectories) }
                            storeModelDefinition(tc, metadataPrefix, index, tableDirectories.modelPrefix, dataModel)
                            ensureUpdateHistoryIndexReady(index, tableDirectories)
                            versionUpdateHandler?.invoke(this, storedModel, dataModel)
                        },
                        finalizeInStartup = {
                            migrationStatus.indexesToIndex?.let { fillIndex(it, tableDirectories) }
                            storeModelDefinition(tc, metadataPrefix, index, tableDirectories.modelPrefix, dataModel)
                            ensureUpdateHistoryIndexReady(index, tableDirectories)
                            scheduledVersionUpdateHandlers.add {
                                versionUpdateHandler?.invoke(this, migrationStatus.storedDataModel as StoredRootDataModelDefinition, dataModel)
                            }
                        }
                    )
                }
            }
        }

        if (keepUpdateHistoryIndex) {
            for ((index, dataModel) in dataModelsById) {
                if (index !in pendingMigrationModelIds.value) {
                    ensureUpdateHistoryIndexReady(index, getTableDirs(index))
                }
            }
        }

        if (sensitiveReferencePrefixesByModelId.values.any { it.isNotEmpty() } && fieldEncryptionProvider == null) {
            throw RequestException(
                "Sensitive properties configured but no fieldEncryptionProvider set on FoundationDBDataStore.open"
            )
        }
        if (sensitiveUniqueReferencesByModelId.values.any { it.isNotEmpty() } && fieldEncryptionProvider !is SensitiveIndexTokenProvider) {
            throw RequestException(
                "Sensitive unique properties configured but fieldEncryptionProvider does not implement SensitiveIndexTokenProvider"
            )
        }

        if (clusterUpdateLogConfiguration.enableClusterUpdateLog) {
            val consumerId = clusterUpdateLogConfiguration.clusterUpdateLogConsumerId
                ?: throw RequestException("Cluster update log enabled but clusterUpdateLogConsumerId is missing")
            val originId = clusterUpdateLogConfiguration.clusterUpdateLogOriginId ?: consumerId

            val headGroupCount = 8
            clusterUpdateLogHeadGroupCount = headGroupCount
            val logDir = runTransaction { tr ->
                rootDirectory.createOrOpen(tr, listOf("__updates__", "v1", "log")).awaitResult()
            }
            val headDir = runTransaction { tr ->
                rootDirectory.createOrOpen(tr, listOf("__updates__", "v1", "heads")).awaitResult()
            }
            val hlcDir = runTransaction { tr ->
                rootDirectory.createOrOpen(tr, listOf("__updates__", "v1", "hlc")).awaitResult()
            }
            val hlcMaxDir = runTransaction { tr ->
                rootDirectory.createOrOpen(tr, listOf("__updates__", "v1", "hlc_max")).awaitResult()
            }
            val consumerDir = runTransaction { tr ->
                rootDirectory.createOrOpen(tr, listOf("__updates__", "v1", "consumers", consumerId)).awaitResult()
            }

            clusterUpdateLog = ClusterUpdateLog(
                logPrefix = logDir.pack(),
                consumerPrefix = consumerDir.pack(),
                headPrefix = headDir.pack(),
                headGroupCount = headGroupCount,
                hlcPrefix = hlcDir.pack(),
                hlcMaxPrefix = hlcMaxDir.pack(),
                shardCount = clusterUpdateLogConfiguration.clusterUpdateLogShardCount,
                originId = originId,
                dataModelsById = dataModelsById,
                consumerId = consumerId,
                retention = clusterUpdateLogConfiguration.clusterUpdateLogRetention,
            )
        }

        startFlows()

        clusterUpdateLog?.also { log ->
            // Cluster HLC sync on startup: advance local HLC to max cluster marker.
            val maxClusterHlc = runTransaction { tr ->
                readMaxClusterHlc(log, tr)
            }
            observedClusterHlc.value = maxOf(observedClusterHlc.value, maxClusterHlc)
            clusterLogLastHlcSyncAtMs.value = HLC().toPhysicalUnixTime().toLong()

            // Dedicated HLC syncer; independent from update listeners/tailing.
            this.launch(updateDispatcher) {
                val headGroupCount = clusterUpdateLogHeadGroupCount
                var backoffMs = 100L
                while (isActive) {
                    try {
                        val maxSeen = runTransaction { tr ->
                            readMaxClusterHlc(log, tr)
                        }
                        observedClusterHlc.value = maxOf(observedClusterHlc.value, maxSeen)
                        clusterLogHlcSyncTransactions.incrementAndGet()
                        clusterLogLastHlcSyncAtMs.value = HLC().toPhysicalUnixTime().toLong()
                        backoffMs = 100L
                        clusterLogHlcCurrentBackoffMs.value = 0L

                        try {
                            waitForAnyClusterLogHeadChange(
                                log = log,
                                headGroupCount = headGroupCount,
                                timeoutMs = clusterUpdateLogConfiguration.clusterUpdateLogPollInterval.inWholeMilliseconds
                            )
                        } catch (_: Throwable) {
                            delay(clusterUpdateLogConfiguration.clusterUpdateLogPollInterval)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        clusterLogHlcSyncErrors.incrementAndGet()
                        clusterLogHlcCurrentBackoffMs.value = backoffMs
                        delay(backoffMs)
                        backoffMs = minOf(backoffMs * 2, 5_000L)
                    }
                }
            }

            // Tail cluster log into this node's in-memory update flow.
            this.launch(updateDispatcher) {
                val shardCount = clusterUpdateLogConfiguration.clusterUpdateLogShardCount
                val headGroupCount = clusterUpdateLogHeadGroupCount
                val modelIds = dataModelsById.keys.sorted()
                val modelCount = modelIds.size
                if (modelCount == 0) return@launch
                val pairCount = shardCount * modelCount
                val cursors = arrayOfNulls<ByteArray>(pairCount)
                var shard = 0
                var modelIndex = 0
                var idle = 0
                var backoffMs = 100L

                fun idx(modelIndex: Int, shard: Int) = (modelIndex * shardCount) + shard
                fun advancePair() {
                    shard++
                    if (shard >= shardCount) {
                        shard = 0
                        modelIndex = (modelIndex + 1) % modelCount
                    }
                }

                while (isActive) {
                    try {
                        val modelId = modelIds[modelIndex]
                        if ((activeUpdateListenersByModelId[modelId]?.value ?: 0) <= 0) {
                            advancePair()
                            idle++
                            if (idle >= pairCount) {
                                idle = 0
                                try {
                                    waitForAnyClusterLogHeadChange(
                                        log = log,
                                        headGroupCount = headGroupCount,
                                        timeoutMs = clusterUpdateLogConfiguration.clusterUpdateLogPollInterval.inWholeMilliseconds
                                    )
                                } catch (_: Throwable) {
                                    delay(clusterUpdateLogConfiguration.clusterUpdateLogPollInterval)
                                }
                            }
                            continue
                        }

                        val cutoff = ClusterUpdateLog.cutoffTimestamp(clusterUpdateLogConfiguration.clusterUpdateLogRetention)
                        val i = idx(modelIndex, shard)
                        if (cursors[i] == null) {
                            val existingCursor = runTransaction { tr ->
                                log.readCursorKey(tr, shard, modelId)
                            }
                            if (existingCursor != null) {
                                cursors[i] = existingCursor
                            } else {
                                // For lazily activated models, start from retention cutoff (not "now") to avoid
                                // missing writes which landed before this shard cursor got initialized.
                                val cutoffCursor = log.minimalKeyAtOrAfter(
                                    shard,
                                    modelId,
                                    ClusterUpdateLog.cutoffTimestamp(clusterUpdateLogConfiguration.clusterUpdateLogRetention)
                                )
                                cursors[i] = cutoffCursor
                                runTransaction { tr ->
                                    log.writeCursorKey(tr, shard, modelId, cutoffCursor)
                                }
                            }
                        }
                        val cursorKey = cursors[i]?.let { existing ->
                            if (log.cursorIsBeforeCutoff(shard, modelId, existing, cutoff)) {
                                val advanced = log.minimalKeyAtOrAfter(shard, modelId, cutoff)
                                cursors[i] = advanced
                                runTransaction { tr ->
                                    log.writeCursorKey(tr, shard, modelId, advanced)
                                }
                                advanced
                            } else existing
                        }

                        clusterLogTailTransactions.incrementAndGet()
                        val tail = runTransaction { tr ->
                            log.tailOnce(
                                tr = tr,
                                shard = shard,
                                modelId = modelId,
                                cursorKey = cursorKey,
                                limit = clusterUpdateLogConfiguration.clusterUpdateLogBatchSize
                            )
                        }

                        for (decoded in tail.decoded) {
                            val model = dataModelsById[decoded.header.modelId] ?: continue
                            observedClusterHlc.value = maxOf(observedClusterHlc.value, decoded.update.version)
                            clusterLogDecodedUpdates.incrementAndGet()
                            updateSharedFlow.emit(decoded.toInternalUpdate(model))
                        }
                        if (tail.decoded.isNotEmpty()) {
                            clusterLogLastDecodedAtMs.value = HLC().toPhysicalUnixTime().toLong()
                        }

                        tail.lastKey?.also { lastKey ->
                            cursors[i] = lastKey
                            runTransaction { tr ->
                                log.writeCursorKey(tr, shard, modelId, lastKey)
                            }
                        }

                        if (tail.decoded.isEmpty()) {
                            advancePair()
                            idle++
                            if (idle >= pairCount) {
                                idle = 0
                                // Prefer watch-based wake-ups (one watch per group) and fall back to polling.
                                try {
                                    waitForAnyClusterLogHeadChange(
                                        log = log,
                                        headGroupCount = headGroupCount,
                                        timeoutMs = clusterUpdateLogConfiguration.clusterUpdateLogPollInterval.inWholeMilliseconds
                                    )
                                } catch (_: Throwable) {
                                    delay(clusterUpdateLogConfiguration.clusterUpdateLogPollInterval)
                                }
                            }
                        } else {
                            idle = 0
                        }

                        clusterLogLastTailAtMs.value = HLC().toPhysicalUnixTime().toLong()
                        backoffMs = 100L
                        clusterLogCurrentBackoffMs.value = backoffMs
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        // Conservative retry loop: transient FDB errors, decode errors, etc.
                        clusterLogTailErrors.incrementAndGet()
                        clusterLogCurrentBackoffMs.value = backoffMs
                        delay(backoffMs)
                        backoffMs = minOf(backoffMs * 2, 5_000L)
                    }
                }
            }

            // Retention GC. Clears by HLC timestamp from key.
            this.launch(updateDispatcher) {
                val shardCount = clusterUpdateLogConfiguration.clusterUpdateLogShardCount
                val batch = 8
                val modelIds = dataModelsById.keys.sorted()
                while (isActive) {
                    try {
                        val cutoff = ClusterUpdateLog.cutoffTimestamp(clusterUpdateLogConfiguration.clusterUpdateLogRetention)
                        var shard = 0
                        while (shard < shardCount) {
                            val end = minOf(shard + batch, shardCount)
                            runTransaction { tr ->
                                for (s in shard until end) {
                                    for (modelId in modelIds) {
                                        log.clearBefore(tr, s, modelId, cutoff)
                                    }
                                }
                            }
                            shard = end
                        }
                        clusterLogGcRuns.incrementAndGet()
                        delay(5.minutes)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        clusterLogGcErrors.incrementAndGet()
                        delay(5_000L)
                    }
                }
            }
        }

        scheduledVersionUpdateHandlers.forEach { it() }
    }

    private fun openTableDirs(
        modelName: String,
        historic: Boolean = false
    ): IsTableDirectories = runTransaction { tr ->
        val modelDir        = rootDirectory.createOrOpen(tr, listOf(modelName, "meta")).awaitResult()
        val keysDir         = rootDirectory.createOrOpen(tr, listOf(modelName, "keys")).awaitResult()
        val tableDir        = rootDirectory.createOrOpen(tr, listOf(modelName, "table")).awaitResult()
        val uniqueDir       = rootDirectory.createOrOpen(tr, listOf(modelName, "unique")).awaitResult()
        val indexDir        = rootDirectory.createOrOpen(tr, listOf(modelName, "index")).awaitResult()
        val updateHistoryDir = if (keepUpdateHistoryIndex) {
            rootDirectory.createOrOpen(tr, listOf(modelName, "update_history")).awaitResult()
        } else null

        if (!historic) {
            TableDirectories(
                dataStore = this,
                model  = modelDir,
                keys   = keysDir,
                table  = tableDir,
                unique = uniqueDir,
                index  = indexDir,
                updateHistory = updateHistoryDir
            )
        } else {
            val histTableDir    = rootDirectory.createOrOpen(tr, listOf(modelName, "table_versioned")).awaitResult()
            val histIndexDir    = rootDirectory.createOrOpen(tr, listOf(modelName, "index_versioned")).awaitResult()
            val histUniqueDir   = rootDirectory.createOrOpen(tr, listOf(modelName, "unique_versioned")).awaitResult()

            HistoricTableDirectories(
                dataStore     = this,
                model         = modelDir,
                keys          = keysDir,
                table         = tableDir,
                unique        = uniqueDir,
                index         = indexDir,
                updateHistory = updateHistoryDir,
                historicTable = histTableDir,
                historicIndex = histIndexDir,
                historicUnique= histUniqueDir
            )
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
                        val observed = observedClusterHlc.value
                        clock = if (observed != 0uL) {
                            clock.calculateMaxTimeStamp(HLC(observed))
                        } else {
                            clock.calculateMaxTimeStamp()
                        }
                        @Suppress("UNCHECKED_CAST")
                        when (val request = storeAction.request) {
                            is AddRequest<*> ->
                                processAddRequest(clock, storeAction as AnyAddStoreAction)
                            is ChangeRequest<*> ->
                                processChangeRequest(clock, storeAction as AnyChangeStoreAction)
                            is DeleteRequest<*> ->
                                processDeleteRequest(clock, storeAction as AnyDeleteStoreAction, cache)
                            is GetRequest<*> ->
                                processGetRequest(storeAction as AnyGetStoreAction, cache)
                            is ScanRequest<*> ->
                                processScanRequest(storeAction as AnyScanStoreAction, cache)
                            is GetChangesRequest<*> ->
                                processGetChangesRequest(storeAction as AnyGetChangesStoreAction, cache)
                            is ScanChangesRequest<*> ->
                                processScanChangesRequest(storeAction as AnyScanChangesStoreAction, cache)
                            is GetUpdatesRequest<*> ->
                                processGetUpdatesRequest(storeAction as AnyGetUpdatesStoreAction, cache)
                            is ScanUpdatesRequest<*> ->
                                processScanUpdatesRequest(storeAction as AnyScanUpdatesStoreAction, cache)
                            is UpdateResponse<*> -> when (val update = (storeAction.request as UpdateResponse<*>).update) {
                                is AdditionUpdate<*> ->
                                    processAdditionUpdate(storeAction as AnyProcessUpdateResponseStoreAction)
                                is ChangeUpdate<*> ->
                                    processChangeUpdate(storeAction as AnyProcessUpdateResponseStoreAction)
                                is RemovalUpdate<*> ->
                                    processDeleteUpdate(storeAction as AnyProcessUpdateResponseStoreAction, cache)
                                is InitialChangesUpdate<*> ->
                                    processInitialChangesUpdate(storeAction as AnyProcessUpdateResponseStoreAction)
                                is InitialValuesUpdate<*> ->
                                    throw RequestException("Cannot process Values requests into data store since they do not contain all version information, do a changes request")
                                is OrderedKeysUpdate<*> ->
                                    throw RequestException("Cannot process Update requests into data store since they do not contain all change information, do a changes request")
                                else -> throw TypeException("Unknown update type $update for datastore processing")
                            }
                            else -> throw TypeException("Unsupported request type ${request::class.simpleName}")
                        }
                    } catch (e: CancellationException) {
                        storeAction.response.cancel(e)
                        if (!isActive) {
                            throw e
                        }
                    } catch (e: Throwable) {
                        storeAction.response.completeExceptionally(e.unwrapFdb())
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

    /** Walk all current values in [tableDirectories] and fill [indexesToIndex] */
    private fun fillIndex(
        indexesToIndex: List<IsIndexable>,
        tableDirectories: IsTableDirectories,
    ) {
        for (indexable in indexesToIndex) {
            deleteCompleteIndexContents(tc, tableDirectories, indexable)
        }

        walkDataRecordsAndFillIndex(tc, tableDirectories, indexesToIndex)
    }

    internal fun canUseUpdateHistoryIndex(dbIndex: UInt) =
        keepUpdateHistoryIndex && dbIndex in updateHistoryReadyModelIds.value

    private fun ensureUpdateHistoryIndexReady(
        dbIndex: UInt,
        tableDirectories: IsTableDirectories,
    ) {
        val updateHistoryPrefix = tableDirectories.updateHistoryPrefix ?: return
        if (!keepUpdateHistoryIndex || canUseUpdateHistoryIndex(dbIndex)) return

        val markerKey = packKey(tableDirectories.modelPrefix, modelUpdateHistoryBackfillCompleteKey)
        val complete = runTransaction { tr ->
            tr.get(markerKey).awaitResult()?.firstOrNull() == 1.toByte()
        }
        if (complete) {
            updateHistoryReadyModelIds.value = updateHistoryReadyModelIds.value + dbIndex
            return
        }

        backfillUpdateHistoryIndex(tableDirectories, updateHistoryPrefix)
        runTransaction { tr ->
            tr.set(markerKey, byteArrayOf(1))
        }
        updateHistoryReadyModelIds.value = updateHistoryReadyModelIds.value + dbIndex
    }

    private fun backfillUpdateHistoryIndex(
        tableDirectories: IsTableDirectories,
        updateHistoryPrefix: ByteArray,
    ) {
        var nextStart = tableDirectories.keysPrefix
        val end = tableDirectories.keysPrefix.nextByteInSameLength()
        val batchSize = 128
        while (true) {
            val batch = runTransaction { tr ->
                val iterator = tr.getRange(Range(nextStart, end), batchSize, false).iterator()
                buildList {
                    while (iterator.hasNext()) {
                        val entry = iterator.nextBlocking()
                        add(entry.key.copyOf() to entry.value.copyOf())
                    }
                }
            }
            if (batch.isEmpty()) break

            batch.forEach { (packedKey, storedValue) ->
                val keyBytes = packedKey.copyOfRange(tableDirectories.keysPrefix.size, packedKey.size)
                val key = Key<IsRootDataModel>(keyBytes)

                if (keepAllVersions && tableDirectories is HistoricTableDirectories) {
                    val creationVersion = HLC.fromStorageBytes(storedValue).timestamp
                    val versions = runTransaction { tr ->
                        buildSet {
                            add(creationVersion)

                            val historicIterator = tr.getRange(Range.startsWith(packKey(tableDirectories.historicTablePrefix, key.bytes))).iterator()
                            while (historicIterator.hasNext()) {
                                val historicEntry = historicIterator.nextBlocking()
                                val historicKey = historicEntry.key
                                if (historicKey.size <= tableDirectories.historicTablePrefix.size + key.size + VERSION_BYTE_SIZE) continue

                                val versionOffset = historicKey.size - VERSION_BYTE_SIZE
                                val separatorIndex = versionOffset - 1
                                if (separatorIndex < 0 || historicKey[separatorIndex] != 0.toByte()) continue

                                add(historicKey.readReversedVersionBytes(versionOffset))
                            }

                            tr.get(packKey(tableDirectories.tablePrefix, key.bytes + SOFT_DELETE_INDICATOR)).awaitResult()?.let { softDeleteValue ->
                                add(HLC.fromStorageBytes(softDeleteValue).timestamp)
                            }
                        }
                    }.toList().sorted()

                    versions.chunked(UPDATE_HISTORY_BACKFILL_WRITE_BATCH_SIZE).forEach { versionChunk ->
                        runTransaction { tr ->
                            versionChunk.forEach { version ->
                                tr.set(
                                    packKey(updateHistoryPrefix, version.toReversedVersionBytes(), key.bytes),
                                    EMPTY_BYTEARRAY
                                )
                            }
                        }
                    }
                } else {
                    val lastVersion = runTransaction { tr ->
                        getLastVersion(tr, tableDirectories, key)
                    }
                    runTransaction { tr ->
                        tr.set(
                            packKey(updateHistoryPrefix, lastVersion.toReversedVersionBytes(), key.bytes),
                            EMPTY_BYTEARRAY
                        )
                    }
                }

                nextStart = packedKey + byteArrayOf(0)
            }
        }
    }

    internal fun getTableDirs(dataModel: IsRootDataModel) =
        getTableDirs(dataModelIdsByString.getValue(dataModel.Meta.name))

    internal fun getTableDirs(dbIndex: UInt) =
        directoriesByDataModelIndex[dbIndex]
            ?: throw DefNotFoundException("DataModel definition not found for $dbIndex")

    internal fun readStoredModelNamesById(): Map<UInt, String> =
        readStoredModelNames(tc, metadataPrefix)

    internal var clusterUpdateLog: ClusterUpdateLog? = null

    internal fun emitUpdate(update: Update<*>?) {
        if (update == null) return
        launch(updateDispatcher) {
            updateSharedFlow.emit(
                update
            )
        }
    }

    override suspend fun close() {
        if (!isClosing.compareAndSet(false, true)) return

        // Stop new work first.
        storeChannel.close()
        val job = this.coroutineContext[Job]
        job?.cancel()

        // Give cooperative coroutines a brief chance to finish before forcing native close.
        val stoppedBeforeNativeClose = withTimeoutOrNull(2_000) {
            job?.join()
            true
        } ?: false

        runCatching { this.db.close() }

        if (!stoppedBeforeNativeClose) {
            withTimeoutOrNull(10_000) {
                job?.join()
            }
        }
    }

    override fun onUpdateListenerAdded(dataModelId: UInt) {
        activeUpdateListenersByModelId[dataModelId]?.incrementAndGet()
    }

    override fun onUpdateListenerRemoved(dataModelId: UInt) {
        val counter = activeUpdateListenersByModelId[dataModelId] ?: return
        while (true) {
            val current = counter.value
            val next = if (current > 0) current - 1 else 0
            if (counter.compareAndSet(current, next)) break
        }
    }

    override fun onAllUpdateListenersRemoved() {
        activeUpdateListenersByModelId.values.forEach { it.value = 0 }
    }

    internal fun getClusterUpdateLogStats(): ClusterUpdateLogStats? =
        clusterUpdateLog?.let {
            ClusterUpdateLogStats(
                tailTransactions = clusterLogTailTransactions.value,
                decodedUpdates = clusterLogDecodedUpdates.value,
                tailErrors = clusterLogTailErrors.value,
                gcRuns = clusterLogGcRuns.value,
                gcErrors = clusterLogGcErrors.value,
                currentBackoffMs = clusterLogCurrentBackoffMs.value,
                hlcSyncTransactions = clusterLogHlcSyncTransactions.value,
                hlcSyncErrors = clusterLogHlcSyncErrors.value,
                hlcSyncCurrentBackoffMs = clusterLogHlcCurrentBackoffMs.value,
                observedClusterHlc = observedClusterHlc.value,
                observedClusterUnixMs = HLC(observedClusterHlc.value).toPhysicalUnixTime().toLong(),
                lastTailAtUnixMs = clusterLogLastTailAtMs.value,
                lastDecodedAtUnixMs = clusterLogLastDecodedAtMs.value,
                lastHlcSyncAtUnixMs = clusterLogLastHlcSyncAtMs.value,
                activeListenerCountsByModelId = activeUpdateListenersByModelId.mapValues { it.value.value },
            )
        }

    private suspend fun waitForAnyClusterLogHeadChange(log: ClusterUpdateLog, headGroupCount: Int, timeoutMs: Long) {
        if (headGroupCount <= 0) return
        coroutineScope {
            val waiters = (0 until headGroupCount).map { group ->
                async {
                    runTransactionAsync { tr ->
                        tr.watch(log.headKey(group))
                    }.await()
                }
            }
            try {
                if (timeoutMs > 0) {
                    withTimeoutOrNull(timeoutMs) {
                        select {
                            for (w in waiters) {
                                w.onAwait { }
                            }
                        }
                    }
                } else {
                    select {
                        for (w in waiters) {
                            w.onAwait { }
                        }
                    }
                }
            } finally {
                waiters.forEach { it.cancel() }
            }
        }
    }

    private fun readULongBigEndian(bytes: ByteArray): ULong {
        require(bytes.size >= 8) { "Expected at least 8 bytes" }
        val v =
            ((bytes[0].toLong() and 0xFFL) shl 56) or
                ((bytes[1].toLong() and 0xFFL) shl 48) or
                ((bytes[2].toLong() and 0xFFL) shl 40) or
                ((bytes[3].toLong() and 0xFFL) shl 32) or
                ((bytes[4].toLong() and 0xFFL) shl 24) or
                ((bytes[5].toLong() and 0xFFL) shl 16) or
                ((bytes[6].toLong() and 0xFFL) shl 8) or
                (bytes[7].toLong() and 0xFFL)
        return v.toULong()
    }

    internal fun encryptValueIfSensitive(modelId: UInt, reference: ByteArray, value: ByteArray): ByteArray {
        if (!isSensitiveReference(modelId, reference)) return value
        val provider = fieldEncryptionProvider
            ?: throw RequestException("No fieldEncryptionProvider configured for sensitive property write")
        val encrypted = runBlocking { provider.encrypt(value) }
        return combineToByteArray(ENCRYPTED_VALUE_MAGIC, encrypted)
    }

    internal fun decryptValueIfNeeded(value: ByteArray): ByteArray {
        if (!isEncryptedValue(value)) return value
        val provider = fieldEncryptionProvider
            ?: throw RequestException("Encrypted value encountered but no fieldEncryptionProvider configured")
        val payload = value.copyOfRange(ENCRYPTED_VALUE_MAGIC.size, value.size)
        return runBlocking { provider.decrypt(payload) }
    }

    internal fun mapUniqueValueBytes(modelId: UInt, reference: ByteArray, value: ByteArray): ByteArray {
        if (!isSensitiveUniqueReference(modelId, reference)) return value
        val tokenProvider = fieldEncryptionProvider as? SensitiveIndexTokenProvider
            ?: throw RequestException("Sensitive unique property requires SensitiveIndexTokenProvider")
        return runBlocking { tokenProvider.deriveDeterministicToken(modelId, reference, value) }
    }

    private fun isSensitiveReference(modelId: UInt, reference: ByteArray): Boolean {
        val prefixes = sensitiveReferencePrefixesByModelId[modelId] ?: return false
        return prefixes.any { prefix -> reference.hasPrefix(prefix) }
    }

    private fun isSensitiveUniqueReference(modelId: UInt, reference: ByteArray): Boolean {
        val references = sensitiveUniqueReferencesByModelId[modelId] ?: return false
        return references.any { it.contentEquals(reference) }
    }

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

    private fun readMaxClusterHlc(log: ClusterUpdateLog, tr: Transaction): ULong {
        var maxSeen = 0uL

        val shardIt = tr.getRange(log.hlcMaxRange()).iterator()
        while (shardIt.hasNext()) {
            val kv = shardIt.nextBlocking()
            if (kv.value.size >= 8) {
                val v = readULongBigEndian(kv.value)
                if (v > maxSeen) maxSeen = v
            }
        }

        // Backward compatibility / bootstrap: include per-node markers.
        val nodeIt = tr.getRange(log.hlcRange()).iterator()
        while (nodeIt.hasNext()) {
            val kv = nodeIt.nextBlocking()
            if (kv.value.size >= 8) {
                val v = readULongBigEndian(kv.value)
                if (v > maxSeen) maxSeen = v
            }
        }

        return maxSeen
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

    fun pendingMigrations(): Map<UInt, String> = pendingMigrationsInternal()

    fun migrationStatus(modelId: UInt): MigrationRuntimeStatus = migrationStatusInternal(modelId)

    fun migrationStatuses(): Map<UInt, MigrationRuntimeStatus> = migrationStatusesInternal()

    fun pauseMigration(modelId: UInt): Boolean = pauseMigrationInternal(modelId)

    fun resumeMigration(modelId: UInt): Boolean = resumeMigrationInternal(modelId)

    fun cancelMigration(modelId: UInt, reason: String = "Canceled by operator"): Boolean = cancelMigrationInternal(modelId, reason)

    fun migrationMetrics(modelId: UInt): MigrationMetrics = migrationMetricsInternal(modelId)

    fun migrationMetrics(): Map<UInt, MigrationMetrics> = migrationMetricsInternal()

    suspend fun migrationAuditEvents(modelId: UInt, limit: Int = 100): List<MigrationAuditEvent> = migrationAuditEventsInternal(modelId, limit)

    suspend fun awaitMigration(modelId: UInt) = awaitMigrationInternal(modelId)

    internal fun ensurePendingMigrationWaiter(modelId: UInt): CompletableDeferred<Unit> = ensurePendingMigrationWaiterInternal(modelId)

    internal fun completePendingMigration(modelId: UInt) = completePendingMigrationInternal(modelId)

    internal fun failPendingMigration(modelId: UInt, reason: String) = failPendingMigrationInternal(modelId, reason)

    internal fun updateMigrationRuntimeDetails(modelId: UInt, state: MigrationState) = updateMigrationRuntimeDetailsInternal(modelId, state)

    internal suspend fun appendMigrationAuditEvent(
        modelId: UInt,
        migrationId: String,
        type: MigrationAuditEventType,
        phase: MigrationPhase? = null,
        attempt: UInt? = null,
        message: String? = null,
    ) = appendMigrationAuditEventInternal(modelId, migrationId, type, phase, attempt, message)

    override fun assertModelReady(dataModelId: UInt) = assertModelReadyForMigrations(dataModelId)

    companion object {
        /**
         * Open a FoundationDB-backed Maryk store.
         *
         * Cluster update log (optional):
         * - `clusterUpdateLogConfiguration.enableClusterUpdateLog`: write each local mutation (add/change/delete) into an FDB-backed log and tail it back into this store's update flow.
         *   This enables `executeFlow` listeners to observe updates made by other processes connected to the same FDB cluster + `directoryPath`.
         * - `clusterUpdateLogConfiguration.clusterUpdateLogConsumerId`: identifies the tailer cursor location. Must be unique per node/process if multiple nodes tail the same store root.
         *   Required when `clusterUpdateLogConfiguration.enableClusterUpdateLog = true`.
         * - `clusterUpdateLogConfiguration.clusterUpdateLogOriginId`: defaults to consumer id; used to suppress echo of updates written by this node.
         * - `clusterUpdateLogConfiguration.clusterUpdateLogRetention`: time window to keep update entries; old entries cleared by time-based range clears.
         * - `migrationConfiguration`: contains migration hooks, retry policy, startup budget, audit settings, and optional custom lease.
         * - `migrationLeaseConfiguration`: configures FoundationDB's default distributed migration lease heartbeat + timeout.
         * - Cluster HLC safety: writes update per-node and shard-max HLC markers; a background syncer keeps local version generation
         *   at/above observed cluster HLC even without active `executeFlow` listeners.
         * - `fieldEncryptionProvider`: optional field-value encryption provider for properties marked with `sensitive = true`.
         *   Sensitive properties are restricted to simple values.
         *   Sensitive+unique requires the provider to also implement [SensitiveIndexTokenProvider].
         *   Sensitive+index is not supported yet.
         */
        suspend fun open(
            keepAllVersions: Boolean = true,
            keepUpdateHistoryIndex: Boolean = false,
            fdbClusterFilePath: String? = null,
            directoryPath: List<String> = listOf("maryk"),
            dataModelsById: Map<UInt, IsRootDataModel>,
            onlyCheckModelVersion: Boolean = false,
            databaseOptionsSetter: DatabaseOptions.() -> Unit = {},
            migrationConfiguration: MigrationConfiguration<FoundationDBDataStore> = MigrationConfiguration(),
            migrationLeaseConfiguration: FoundationDBMigrationLeaseConfiguration = FoundationDBMigrationLeaseConfiguration(),
            versionUpdateHandler: VersionUpdateHandler<FoundationDBDataStore>? = null,
            clusterUpdateLogConfiguration: FoundationDBClusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(),
            fieldEncryptionProvider: FieldEncryptionProvider? = null,
        ) = FoundationDBDataStore(
            keepAllVersions = keepAllVersions,
            keepUpdateHistoryIndex = keepUpdateHistoryIndex,
            fdbClusterFilePath = fdbClusterFilePath,
            directoryRootPath = directoryPath,
            dataModelsById = dataModelsById,
            onlyCheckModelVersion = onlyCheckModelVersion,
            databaseOptionsSetter = databaseOptionsSetter,
            migrationConfiguration = migrationConfiguration,
            migrationLeaseConfiguration = migrationLeaseConfiguration,
            versionUpdateHandler = versionUpdateHandler,
            clusterUpdateLogConfiguration = clusterUpdateLogConfiguration,
            fieldEncryptionProvider = fieldEncryptionProvider,
        ).apply {
            try {
                initAsync()
            } catch (e: Throwable) {
                this.close()
                throw e.unwrapFdb()
            }
        }

    }
}

internal data class ClusterUpdateLogStats(
    val tailTransactions: Long,
    val decodedUpdates: Long,
    val tailErrors: Long,
    val gcRuns: Long,
    val gcErrors: Long,
    val currentBackoffMs: Long,
    val hlcSyncTransactions: Long,
    val hlcSyncErrors: Long,
    val hlcSyncCurrentBackoffMs: Long,
    val observedClusterHlc: ULong,
    val observedClusterUnixMs: Long,
    val lastTailAtUnixMs: Long,
    val lastDecodedAtUnixMs: Long,
    val lastHlcSyncAtUnixMs: Long,
    val activeListenerCountsByModelId: Map<UInt, Int>,
)

private data class SensitiveModelReferences(
    val sensitiveReferences: List<ByteArray>,
    val sensitiveUniqueReferences: List<ByteArray>,
)
