package maryk.datastore.indexeddb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import maryk.core.clock.HLC
import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootDataModel
import maryk.core.models.migration.MigrationConfiguration
import maryk.core.models.migration.VersionUpdateHandler
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.query.requests.AddRequest
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.requests.GetChangesRequest
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.GetUpdatesRequest
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.requests.ScanRequest
import maryk.core.query.requests.ScanUpdateHistoryRequest
import maryk.core.query.requests.ScanUpdatesRequest
import maryk.core.query.requests.add
import maryk.core.query.responses.UpdateResponse
import maryk.datastore.indexeddb.processors.AddStoreAction
import maryk.datastore.indexeddb.processors.ChangeStoreAction
import maryk.datastore.indexeddb.processors.CurrentStateStoragePlan
import maryk.datastore.indexeddb.processors.DeleteStoreAction
import maryk.datastore.indexeddb.processors.GetChangesStoreAction
import maryk.datastore.indexeddb.processors.GetStoreAction
import maryk.datastore.indexeddb.processors.GetUpdatesStoreAction
import maryk.datastore.indexeddb.processors.ProcessUpdateResponseStoreAction
import maryk.datastore.indexeddb.processors.ScanChangesStoreAction
import maryk.datastore.indexeddb.processors.ScanStoreAction
import maryk.datastore.indexeddb.processors.ScanUpdateHistoryStoreAction
import maryk.datastore.indexeddb.processors.ScanUpdatesStoreAction
import maryk.datastore.indexeddb.processors.addHistoricIndexRows
import maryk.datastore.indexeddb.processors.createIndexKeyPrefix
import maryk.datastore.indexeddb.processors.createHardDeleteHistoryRowKey
import maryk.datastore.indexeddb.processors.createStoragePlan
import maryk.datastore.indexeddb.processors.decodeCurrentSnapshotRecord
import maryk.datastore.indexeddb.processors.decodeHistoricSnapshot
import maryk.datastore.indexeddb.processors.processAddRequest
import maryk.datastore.indexeddb.processors.processChangeRequest
import maryk.datastore.indexeddb.processors.processDeleteRequest
import maryk.datastore.indexeddb.processors.processGetChangesRequest
import maryk.datastore.indexeddb.processors.processGetRequest
import maryk.datastore.indexeddb.processors.processGetUpdatesRequest
import maryk.datastore.indexeddb.processors.processScanChangesRequest
import maryk.datastore.indexeddb.processors.processScanRequest
import maryk.datastore.indexeddb.processors.processScanUpdateHistoryRequest
import maryk.datastore.indexeddb.processors.processScanUpdatesRequest
import maryk.datastore.indexeddb.processors.processUpdateResponse
import maryk.datastore.indexeddb.processors.put
import maryk.datastore.indexeddb.processors.isHardDeleteTombstone
import maryk.datastore.indexeddb.processors.readInvertedVersion
import maryk.datastore.indexeddb.processors.readTrailingVersion
import maryk.datastore.indexeddb.processors.readTrailingInvertedVersion
import maryk.datastore.indexeddb.processors.toBigEndianBytes
import maryk.datastore.shared.AbstractDataStore
import maryk.datastore.shared.DISPATCHER
import maryk.datastore.shared.RequestExecutionKind
import maryk.datastore.shared.SnapshotVersionProvider
import maryk.datastore.shared.encryption.FieldEncryptionProvider
import maryk.datastore.shared.rethrowIfFatal
import maryk.datastore.shared.requestExecutionKind
import maryk.datastore.shared.updates.Update
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.matchesRangePart

internal var hardDeleteTombstoneMigrationBeforeWrite: (suspend (UInt, ByteArray, ULong) -> Unit)? = null

class IndexedDbDataStore private constructor(
    internal val byteStore: IndexedDbByteStore,
    override val keepAllVersions: Boolean = false,
    override val keepUpdateHistoryIndex: Boolean = false,
    dataModelsById: Map<UInt, IsRootDataModel>,
    internal val sensitiveFields: IndexedDbSensitiveFieldSupport,
) : AbstractDataStore(dataModelsById, DISPATCHER), SnapshotVersionProvider {
    private val indexedDbModelsById = dataModelsById
    override val supportsFuzzyQualifierFiltering: Boolean = true
    override val supportsSubReferenceFiltering: Boolean = true

    override suspend fun captureSnapshotVersion(): ULong = captureLocalSnapshotVersion()

    internal suspend fun emitIndexedDbUpdate(update: Update<out IsRootDataModel>) {
        emitFlowUpdate(update)
    }

    private suspend fun migrateHardDeleteTombstones() {
        if (!keepUpdateHistoryIndex) return
        for (modelId in dataModelsById.keys) {
            val markerKey = "hard-delete-history:$modelId".encodeToByteArray()
            if (byteStore.get("meta", markerKey) != null) continue

            byteStore.scanInBatches("uh:$modelId", targetLimit = UInt.MAX_VALUE) { rowKey, rowValue ->
                if (!rowValue.isHardDeleteTombstone()) return@scanInBatches true
                val version = rowKey.readInvertedVersion()
                val keyBytes = rowKey.copyOfRange(rowKey.size - indexedDbModelsById.getValue(modelId).Meta.keyByteSize, rowKey.size)
                hardDeleteTombstoneMigrationBeforeWrite?.invoke(modelId, keyBytes, version)
                byteStore.transaction(
                    setOf("hd:$modelId", "hdk:$modelId"),
                    IndexedDbTransactionMode.READWRITE,
                ) { store ->
                    val operations = mutableListOf<IndexedDbWriteOperation>()
                    operations += IndexedDbWriteOperation.Put("hdk:$modelId", createHardDeleteHistoryRowKey(keyBytes, version), byteArrayOf(1))
                    val current = store.get("hd:$modelId", keyBytes)?.readTrailingVersion()
                    if (current == null || version > current) {
                        operations += IndexedDbWriteOperation.Put("hd:$modelId", keyBytes, version.toBigEndianBytes())
                    }
                    store.writeBatch(operations)
                }
                true
            }
            byteStore.put("meta", markerKey, byteArrayOf(1))
        }
    }

    init {
        startFlows()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun startFlows() {
        super.startFlows()

        launch {
            var clock = HLC()

            storeActorHasStarted.complete(Unit)
            try {
                processStoreActions { storeAction ->
                    try {
                        if (storeAction.request.requestExecutionKind == RequestExecutionKind.Mutation) {
                            clock = nextMutationClock(
                                clock,
                                (storeAction.request as? UpdateResponse<*>)?.update?.version,
                            )
                            observeCommittedVersion(clock.timestamp)
                        }

                        @Suppress("UNCHECKED_CAST")
                        when (storeAction.request) {
                            is AddRequest<*> -> processAddRequest(clock, storeAction as AddStoreAction<IsRootDataModel>)
                            is ChangeRequest<*> -> processChangeRequest(clock, storeAction as ChangeStoreAction<IsRootDataModel>)
                            is DeleteRequest<*> -> processDeleteRequest(clock, storeAction as DeleteStoreAction<IsRootDataModel>)
                            is GetRequest<*> -> processGetRequest(storeAction as GetStoreAction<IsRootDataModel>)
                            is GetChangesRequest<*> -> processGetChangesRequest(storeAction as GetChangesStoreAction<IsRootDataModel>)
                            is GetUpdatesRequest<*> -> processGetUpdatesRequest(storeAction as GetUpdatesStoreAction<IsRootDataModel>)
                            is ScanRequest<*> -> processScanRequest(storeAction as ScanStoreAction<IsRootDataModel>)
                            is ScanChangesRequest<*> -> processScanChangesRequest(storeAction as ScanChangesStoreAction<IsRootDataModel>)
                            is ScanUpdateHistoryRequest<*> -> processScanUpdateHistoryRequest(storeAction as ScanUpdateHistoryStoreAction<IsRootDataModel>)
                            is ScanUpdatesRequest<*> -> processScanUpdatesRequest(storeAction as ScanUpdatesStoreAction<IsRootDataModel>)
                            is UpdateResponse<*> -> processUpdateResponse(storeAction as ProcessUpdateResponseStoreAction<IsRootDataModel>)
                            else -> throw TypeException("Unknown request type ${storeAction.request}")
                        }
                    } catch (e: CancellationException) {
                        storeAction.response.cancel(e)
                        throw e
                    } catch (e: Throwable) {
                        e.rethrowIfFatal()
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

    internal suspend fun backfillIndexRows(
        dataModel: IsRootDataModel,
        indexesToIndex: List<IsIndexable>,
    ) {
        if (indexesToIndex.isEmpty()) return

        val modelId = getDataModelId(dataModel)
        val indexStoreName = "i:$modelId"
        val historicIndexStoreName = "hi:$modelId"
        val historicIndexCleanupStoreName = "hik:$modelId"
        val indexReferences = indexesToIndex.map { it.referenceStorageByteArray.bytes }
        val indexPrefixes = indexReferences.map(::createIndexKeyPrefix)
        val operations = mutableListOf<IndexedDbWriteOperation>()

        fun CurrentStateStoragePlan.filteredIndexRows(): List<ByteArray> =
            indexRows.filter { rowKey ->
                indexPrefixes.any { prefix ->
                    rowKey.matchesRangePart(0, prefix, sourceLength = rowKey.size, length = prefix.size)
                }
            }

        byteStore.scanInBatches(storeName = "k:$modelId", targetLimit = UInt.MAX_VALUE) { keyBytes, snapshot ->
            val current = decodeCurrentSnapshotRecord(
                dataModel = dataModel,
                keyBytes = keyBytes,
                snapshot = snapshot,
                select = null,
                decryptValue = { qualifier, value -> sensitiveFields.decryptValueIfNeeded(modelId, keyBytes, qualifier, value) },
            ) ?: return@scanInBatches true
            if (current.isDeleted) return@scanInBatches true

            val plan = createStoragePlan(dataModel, modelId, keyBytes, current.values, sensitiveFields)
            for (indexRow in plan.filteredIndexRows()) {
                operations.put(indexStoreName, indexRow, keyBytes)
            }
            if (operations.size >= 500) {
                byteStore.writeBatch(operations.toList())
                operations.clear()
            }
            true
        }

        if (keepAllVersions) {
            var currentHistoricKey: ByteArray? = null
            val snapshotsForKey = mutableListOf<Pair<ByteArray, ByteArray>>()

            suspend fun replayHistoricSnapshotsForKey(snapshots: List<Pair<ByteArray, ByteArray>>) {
                var previousRowsByKey = emptyMap<String, ByteArray>()
                val chronologicalSnapshots = snapshots.sortedWith { first, second ->
                    first.first.readTrailingInvertedVersion().compareTo(second.first.readTrailingInvertedVersion())
                }

                for ((rowKey, snapshot) in chronologicalSnapshots) {
                    val keyBytes = objectKeyBytesFromScopedRowKey(rowKey)
                    val version = rowKey.readTrailingInvertedVersion()
                    val currentRowsByKey = mutableMapOf<String, ByteArray>()
                    val (meta, storageRows) = decodeHistoricSnapshot(snapshot)
                    if (!meta.isDeleted) {
                        val values = decodeStorageRowsToValues(
                            dataModel = dataModel,
                            rows = storageRows.map { (qualifier, value) ->
                                qualifier to sensitiveFields.decryptValueIfNeeded(modelId, keyBytes, qualifier, value)
                            },
                            select = null,
                        )
                        if (values != null) {
                            val plan = createStoragePlan(dataModel, modelId, keyBytes, values, sensitiveFields)
                            for (indexRow in plan.filteredIndexRows()) {
                                currentRowsByKey[indexRow.contentToString()] = indexRow
                            }
                        }
                    }

                    val removedRows = previousRowsByKey
                        .filterKeys { it !in currentRowsByKey }
                        .values
                        .toList()
                    if (removedRows.isNotEmpty()) {
                        operations.addHistoricIndexRows(
                            historicIndexStoreName,
                            historicIndexCleanupStoreName,
                            keyBytes,
                            removedRows,
                            version,
                            active = false,
                        )
                    }
                    if (currentRowsByKey.isNotEmpty()) {
                        operations.addHistoricIndexRows(
                            historicIndexStoreName,
                            historicIndexCleanupStoreName,
                            keyBytes,
                            currentRowsByKey.values.toList(),
                            version,
                            active = true,
                        )
                    }
                    previousRowsByKey = currentRowsByKey

                    if (operations.size >= 500) {
                        byteStore.writeBatch(operations.toList())
                        operations.clear()
                    }
                }
            }

            byteStore.scanInBatches(storeName = "ht:$modelId", targetLimit = UInt.MAX_VALUE) { rowKey, snapshot ->
                val keyBytes = objectKeyBytesFromScopedRowKey(rowKey)
                val activeKey = currentHistoricKey
                if (activeKey != null && !activeKey.contentEquals(keyBytes)) {
                    replayHistoricSnapshotsForKey(snapshotsForKey)
                    snapshotsForKey.clear()
                }
                currentHistoricKey = keyBytes
                snapshotsForKey += rowKey to snapshot
                true
            }

            if (snapshotsForKey.isNotEmpty()) {
                replayHistoricSnapshotsForKey(snapshotsForKey)
            }
        }

        if (operations.isNotEmpty()) {
            byteStore.writeBatch(operations)
        }
    }

    override suspend fun close() {
        if (!startClosingDataStore()) return
        byteStore.close()
        cancelAndJoinDataStoreScope()
    }

    private suspend fun stopFailedStartupWork() {
        if (!startClosingDataStore()) return
        cancelAndJoinDataStoreScope()
    }

    companion object {
        suspend fun open(
            databaseName: String,
            dataModelsById: Map<UInt, IsRootDataModel>,
            keepAllVersions: Boolean = false,
            keepUpdateHistoryIndex: Boolean = false,
            fieldEncryptionProvider: FieldEncryptionProvider? = null,
            migrationConfiguration: MigrationConfiguration<IndexedDbDataStore> = MigrationConfiguration(),
            versionUpdateHandler: VersionUpdateHandler<IndexedDbDataStore>? = null,
        ): IndexedDbDataStore {
            val sensitiveFields = IndexedDbSensitiveFieldSupport(dataModelsById, fieldEncryptionProvider)
            val objectStoreNames = buildSet {
                add("meta")
                for (modelId in dataModelsById.keys) {
                    add("k:$modelId")
                    add("t:$modelId")
                    add("i:$modelId")
                    add("u:$modelId")
                    add("c:$modelId")
                    if (keepAllVersions) {
                        add("ht:$modelId")
                        add("hi:$modelId")
                        add("hu:$modelId")
                        add("hik:$modelId")
                        add("huk:$modelId")
                    }
                    if (keepUpdateHistoryIndex) {
                        add("uh:$modelId")
                        add("hd:$modelId")
                        add("hdk:$modelId")
                    }
                }
            }

            val byteStore = openIndexedDbByteStore(
                databaseName = databaseName,
                objectStoreNames = objectStoreNames,
            )

            val dataStore = IndexedDbDataStore(
                byteStore = byteStore,
                keepAllVersions = keepAllVersions,
                keepUpdateHistoryIndex = keepUpdateHistoryIndex,
                dataModelsById = dataModelsById,
                sensitiveFields = sensitiveFields,
            )

            return try {
                byteStore.withStartupWriteLock {
                    try {
                        byteStore.migrateStoreMetadata(
                            dataStore = dataStore,
                            keepAllVersions = keepAllVersions,
                            keepUpdateHistoryIndex = keepUpdateHistoryIndex,
                            dataModelsById = dataModelsById,
                            migrationConfiguration = migrationConfiguration,
                            versionUpdateHandler = versionUpdateHandler,
                        )
                        dataStore.migrateHardDeleteTombstones()
                    } catch (error: Throwable) {
                        withContext(NonCancellable) {
                            dataStore.stopFailedStartupWork()
                        }
                        throw error
                    }
                }
                dataStore
            } catch (error: Throwable) {
                withContext(NonCancellable) {
                    dataStore.stopFailedStartupWork()
                    byteStore.close()
                }
                throw error
            }
        }
    }
}
