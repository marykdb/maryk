package maryk.datastore.foundationdb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.atomicfu.atomic
import maryk.core.clock.HLC
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootDataModel
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.MigrationHandler
import maryk.core.models.migration.MigrationStatus
import maryk.core.models.migration.MigrationStatus.NeedsMigration
import maryk.core.models.migration.MigrationStatus.NewIndicesOnExistingProperties
import maryk.core.models.migration.MigrationStatus.NewModel
import maryk.core.models.migration.MigrationStatus.OnlySafeAdds
import maryk.core.models.migration.MigrationStatus.UpToDate
import maryk.core.models.migration.StoredRootDataModelDefinition
import maryk.core.models.migration.VersionUpdateHandler
import maryk.core.properties.definitions.index.IsIndexable
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
import maryk.datastore.foundationdb.metadata.readStoredModelNames
import maryk.datastore.foundationdb.model.checkModelIfMigrationIsNeeded
import maryk.datastore.foundationdb.model.storeModelDefinition
import maryk.datastore.foundationdb.clusterlog.ClusterUpdateLog
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
import maryk.datastore.foundationdb.processors.deleteCompleteIndexContents
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.nextBlocking
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
import maryk.datastore.shared.updates.Update
import maryk.foundationdb.DatabaseOptions
import maryk.foundationdb.FDB
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.Transaction
import maryk.foundationdb.TransactionContext
import maryk.foundationdb.directory.DirectoryLayer
import maryk.foundationdb.directory.DirectorySubspace
import maryk.foundationdb.tuple.Tuple
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val storeMetadataModelsByIdDirectoryPath = listOf("__meta__", "models_by_id")

/**
 * FoundationDB DataStore (JVM-only).
 */
class FoundationDBDataStore private constructor(
    override val keepAllVersions: Boolean = true,
    val fdbClusterFilePath: String? = null,
    val tenantName: Tuple? = null,
    val directoryRootPath: List<String> = listOf("maryk"),
    dataModelsById: Map<UInt, IsRootDataModel>,
    private val onlyCheckModelVersion: Boolean = false,
    val databaseOptionsSetter: DatabaseOptions.() -> Unit = {},
    val migrationHandler: MigrationHandler<FoundationDBDataStore>? = null,
    val versionUpdateHandler: VersionUpdateHandler<FoundationDBDataStore>? = null,
    private val enableClusterUpdateLog: Boolean = false,
    private val clusterUpdateLogConsumerId: String? = null,
    private val clusterUpdateLogOriginId: String? = null,
    private val clusterUpdateLogShardCount: Int = 64,
    private val clusterUpdateLogRetention: Duration = 60.minutes,
    private val clusterUpdateLogBatchSize: Int = 256,
    private val clusterUpdateLogPollInterval: Duration = 250.milliseconds,
) : AbstractDataStore(dataModelsById, Dispatchers.IO.limitedParallelism(1)) {
    override val supportsFuzzyQualifierFiltering: Boolean = true
    override val supportsSubReferenceFiltering: Boolean = true

    private val fdb = FDB.selectAPIVersion(730)
    private val db = (if (fdbClusterFilePath != null) fdb.open(fdbClusterFilePath) else fdb.open())
    private val tenantDB = tenantName?.let { db.openTenant(tenantName) }
    internal val tc: TransactionContext = tenantDB ?: db

    init {
        db.options().apply {
            databaseOptionsSetter()
        }
    }

    // Keep the actor single-threaded for transactional safety while dispatching updates on IO.
    internal val updateDispatcher = Dispatchers.IO

    private val scheduledVersionUpdateHandlers = mutableListOf<suspend () -> Unit>()

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
    private val clusterLogLastTailAtMs = atomic(0L)
    private val clusterLogLastDecodedAtMs = atomic(0L)

    internal inline fun <T> runTransaction(
        crossinline block: (Transaction) -> T
    ): T =
        tc.run { tr ->
            block(tr)
        }

    internal inline fun <T> runTransactionAsync(
        crossinline block: (Transaction) -> FdbFuture<T>
    ): FdbFuture<T> =
        tc.runAsync { tr ->
            block(tr)
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

        for ((index, dataModel) in dataModelsById) {
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
                    is NeedsMigration -> {
                        val succeeded = migrationHandler?.invoke(this, migrationStatus.storedDataModel as StoredRootDataModelDefinition, dataModel)
                            ?: throw MigrationException("Migration needed: No migration handler present. \n$migrationStatus")

                        if (!succeeded) {
                            throw MigrationException("Migration could not be handled for ${dataModel.Meta.name} & ${(migrationStatus.storedDataModel as? StoredRootDataModelDefinition)?.Meta?.version}\n$migrationStatus")
                        }

                        migrationStatus.indexesToIndex?.let {
                            fillIndex(it, tableDirectories)
                        }
                        storeModelDefinition(tc, metadataPrefix, index, tableDirectories.modelPrefix, dataModel)
                        scheduledVersionUpdateHandlers.add {
                            versionUpdateHandler?.invoke(this, migrationStatus.storedDataModel as StoredRootDataModelDefinition, dataModel)
                        }
                    }
                }
            }
        }

        if (enableClusterUpdateLog) {
            val consumerId = clusterUpdateLogConsumerId
                ?: throw RequestException("Cluster update log enabled but clusterUpdateLogConsumerId is missing")
            val originId = clusterUpdateLogOriginId ?: consumerId

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
            val consumerDir = runTransaction { tr ->
                rootDirectory.createOrOpen(tr, listOf("__updates__", "v1", "consumers", consumerId)).awaitResult()
            }

            clusterUpdateLog = ClusterUpdateLog(
                logPrefix = logDir.pack(),
                consumerPrefix = consumerDir.pack(),
                headPrefix = headDir.pack(),
                headGroupCount = headGroupCount,
                hlcPrefix = hlcDir.pack(),
                shardCount = clusterUpdateLogShardCount,
                originId = originId,
                dataModelsById = dataModelsById,
                consumerId = consumerId,
                retention = clusterUpdateLogRetention,
            )
        }

        startFlows()

        clusterUpdateLog?.also { log ->
            // Cluster HLC sync on startup: advance local HLC to max(hlc/<node>) to avoid emitting versions behind the cluster.
            val maxClusterHlc = runTransaction { tr ->
                var maxSeen = 0uL
                val it = tr.getRange(log.hlcRange()).iterator()
                while (it.hasNext()) {
                    val kv = it.nextBlocking()
                    if (kv.value.size >= 8) {
                        val v = readULongBigEndian(kv.value)
                        if (v > maxSeen) maxSeen = v
                    }
                }
                maxSeen
            }
            observedClusterHlc.value = maxOf(observedClusterHlc.value, maxClusterHlc)

            // Tail cluster log into this node's in-memory update flow.
            this.launch(updateDispatcher) {
                val shardCount = clusterUpdateLogShardCount
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
                                    waitForAnyClusterLogHeadChange(log = log, headGroupCount = headGroupCount)
                                } catch (_: Throwable) {
                                    kotlinx.coroutines.delay(clusterUpdateLogPollInterval)
                                }
                            }
                            continue
                        }

                        val cutoff = ClusterUpdateLog.cutoffTimestamp(clusterUpdateLogRetention)
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
                                    ClusterUpdateLog.cutoffTimestamp(clusterUpdateLogRetention)
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
                                limit = clusterUpdateLogBatchSize
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
                                // Prefer watch-based wakeups (one watch per group) and fall back to polling.
                                try {
                                    waitForAnyClusterLogHeadChange(log = log, headGroupCount = headGroupCount)
                                } catch (_: Throwable) {
                                    kotlinx.coroutines.delay(clusterUpdateLogPollInterval)
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
                        kotlinx.coroutines.delay(backoffMs)
                        backoffMs = minOf(backoffMs * 2, 5_000L)
                    }
                }
            }

            // Retention GC. Clears by HLC timestamp from key.
            this.launch(updateDispatcher) {
                val shardCount = clusterUpdateLogShardCount
                val batch = 8
                val modelIds = dataModelsById.keys.sorted()
                while (isActive) {
                    try {
                        val cutoff = ClusterUpdateLog.cutoffTimestamp(clusterUpdateLogRetention)
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
                        kotlinx.coroutines.delay(5.minutes)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        clusterLogGcErrors.incrementAndGet()
                        kotlinx.coroutines.delay(5_000L)
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

        if (!historic) {
            TableDirectories(
                dataStore = this,
                model  = modelDir,
                keys   = keysDir,
                table  = tableDir,
                unique = uniqueDir,
                index  = indexDir
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
        this.tenantDB?.close()
        this.db.close()
        super.close()
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
                observedClusterHlc = observedClusterHlc.value,
                observedClusterUnixMs = HLC(observedClusterHlc.value).toPhysicalUnixTime().toLong(),
                lastTailAtUnixMs = clusterLogLastTailAtMs.value,
                lastDecodedAtUnixMs = clusterLogLastDecodedAtMs.value,
                activeListenerCountsByModelId = activeUpdateListenersByModelId.mapValues { it.value.value },
            )
        }

    private suspend fun waitForAnyClusterLogHeadChange(log: ClusterUpdateLog, headGroupCount: Int) {
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
                select {
                    for (w in waiters) {
                        w.onAwait { }
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

    companion object {
        /**
         * Open a FoundationDB-backed Maryk store.
         *
         * Cluster update log (optional):
         * - `enableClusterUpdateLog`: write each local mutation (add/change/delete) into an FDB-backed log and tail it back into this store's update flow.
         *   This enables `executeFlow` listeners to observe updates made by other processes connected to the same FDB cluster + `directoryPath`.
         * - `clusterUpdateLogConsumerId`: identifies the tailer cursor location. Must be unique per node/process if multiple nodes tail the same store root.
         *   Required when `enableClusterUpdateLog = true`.
         * - `clusterUpdateLogOriginId`: defaults to consumer id; used to suppress echo of updates written by this node.
         * - `clusterUpdateLogRetention`: time window to keep update entries; old entries cleared by time-based range clears.
         */
        suspend fun open(
            keepAllVersions: Boolean = true,
            fdbClusterFilePath: String? = null,
            directoryPath: List<String> = listOf("maryk"),
            tenantName: Tuple? = null,
            dataModelsById: Map<UInt, IsRootDataModel>,
            onlyCheckModelVersion: Boolean = false,
            databaseOptionsSetter: DatabaseOptions.() -> Unit = {},
            migrationHandler: MigrationHandler<FoundationDBDataStore>? = null,
            versionUpdateHandler: VersionUpdateHandler<FoundationDBDataStore>? = null,
            enableClusterUpdateLog: Boolean = false,
            clusterUpdateLogConsumerId: String? = null,
            clusterUpdateLogOriginId: String? = null,
            clusterUpdateLogShardCount: Int = 64,
            clusterUpdateLogRetention: Duration = 60.minutes,
            clusterUpdateLogBatchSize: Int = 256,
            clusterUpdateLogPollInterval: Duration = 250.milliseconds,
        ) = FoundationDBDataStore(
            keepAllVersions = keepAllVersions,
            fdbClusterFilePath = fdbClusterFilePath,
            directoryRootPath = directoryPath,
            tenantName = tenantName,
            dataModelsById = dataModelsById,
            onlyCheckModelVersion = onlyCheckModelVersion,
            databaseOptionsSetter = databaseOptionsSetter,
            migrationHandler = migrationHandler,
            versionUpdateHandler = versionUpdateHandler,
            enableClusterUpdateLog = enableClusterUpdateLog,
            clusterUpdateLogConsumerId = clusterUpdateLogConsumerId,
            clusterUpdateLogOriginId = clusterUpdateLogOriginId,
            clusterUpdateLogShardCount = clusterUpdateLogShardCount,
            clusterUpdateLogRetention = clusterUpdateLogRetention,
            clusterUpdateLogBatchSize = clusterUpdateLogBatchSize,
            clusterUpdateLogPollInterval = clusterUpdateLogPollInterval,
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
    val observedClusterHlc: ULong,
    val observedClusterUnixMs: Long,
    val lastTailAtUnixMs: Long,
    val lastDecodedAtUnixMs: Long,
    val activeListenerCountsByModelId: Map<UInt, Int>,
)
