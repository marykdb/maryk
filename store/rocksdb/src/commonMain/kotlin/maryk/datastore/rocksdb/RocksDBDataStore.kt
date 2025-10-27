package maryk.datastore.rocksdb

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import maryk.core.clock.HLC
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.TypeException
import maryk.core.extensions.bytes.calculateVarByteLength
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
import maryk.datastore.rocksdb.TableType.HistoricIndex
import maryk.datastore.rocksdb.TableType.HistoricTable
import maryk.datastore.rocksdb.TableType.HistoricUnique
import maryk.datastore.rocksdb.TableType.Index
import maryk.datastore.rocksdb.TableType.Keys
import maryk.datastore.rocksdb.TableType.Model
import maryk.datastore.rocksdb.TableType.Table
import maryk.datastore.rocksdb.TableType.Unique
import maryk.datastore.rocksdb.metadata.readStoredModelNames
import maryk.datastore.rocksdb.model.checkModelIfMigrationIsNeeded
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

private val storeMetadataColumnFamilyName = byteArrayOf(0)

class RocksDBDataStore private constructor(
    override val keepAllVersions: Boolean = true,
    relativePath: String,
    dataModelsById: Map<UInt, IsRootDataModel>,
    rocksDBOptions: DBOptions? = null,
    private val onlyCheckModelVersion: Boolean = false,
    val migrationHandler: MigrationHandler<RocksDBDataStore>? = null,
    val versionUpdateHandler: VersionUpdateHandler<RocksDBDataStore>? = null,
) : AbstractDataStore(dataModelsById, Dispatchers.IO.limitedParallelism(1)) {
    private val columnFamilyHandlesByDataModelIndex = mutableMapOf<UInt, TableColumnFamilies>()
    private val prefixSizesByColumnFamilyHandlesIndex = mutableMapOf<Int, Int>()
    private val uniqueIndicesByDataModelIndex = atomic(mapOf<UInt, List<ByteArray>>())

    override val supportsFuzzyQualifierFiltering: Boolean = true
    override val supportsSubReferenceFiltering: Boolean = true

    // Only create Options if no Options were passed. Will take ownership and close it if this object is closed
    private val ownRocksDBOptions: DBOptions? =
        if (rocksDBOptions == null) {
            DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true)
        } else null

    internal val db: RocksDB

    internal val updateDispatcher = Dispatchers.IO

    private val metadataColumnFamily: ColumnFamilyHandle

    private val defaultWriteOptions = WriteOptions()
    internal val defaultReadOptions = ReadOptions().apply {
        setPrefixSameAsStart(true)
    }

    private val scheduledVersionUpdateHandlers = mutableListOf<suspend () -> Unit>()

    init {
        val descriptors: MutableList<ColumnFamilyDescriptor> = mutableListOf()
        descriptors.add(ColumnFamilyDescriptor(defaultColumnFamily))
        descriptors.add(ColumnFamilyDescriptor(storeMetadataColumnFamilyName))
        for ((index, db) in dataModelsById) {
            createColumnFamilyHandles(descriptors, index, db)
        }

        val handles = mutableListOf<ColumnFamilyHandle>()
        this.db = openRocksDB(rocksDBOptions ?: ownRocksDBOptions!!, relativePath, descriptors, handles)

        try {
            metadataColumnFamily = handles[1]

            var handleIndex = 2
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
        val conversionContext = DefinitionsConversionContext()

        for ((index, dataModel) in dataModelsById) {
            columnFamilyHandlesByDataModelIndex[index]?.let { tableColumnFamilies ->
                when (
                    val migrationStatus = checkModelIfMigrationIsNeeded(
                        db,
                        metadataColumnFamily,
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
                            storeModelDefinition(db, metadataColumnFamily, index, tableColumnFamilies.model, dataModel)
                        }
                    }
                    is OnlySafeAdds -> {
                        scheduledVersionUpdateHandlers.add {
                            versionUpdateHandler?.invoke(this, migrationStatus.storedDataModel as StoredRootDataModelDefinition, dataModel)
                            storeModelDefinition(db, metadataColumnFamily, index, tableColumnFamilies.model, dataModel)
                        }
                    }
                    is NewIndicesOnExistingProperties -> {
                        fillIndex(migrationStatus.indexesToIndex, tableColumnFamilies)
                        scheduledVersionUpdateHandlers.add {
                            versionUpdateHandler?.invoke(this, migrationStatus.storedDataModel as StoredRootDataModelDefinition, dataModel)
                            storeModelDefinition(db, metadataColumnFamily, index, tableColumnFamilies.model, dataModel)
                        }
                    }
                    is NeedsMigration -> {
                        val succeeded = migrationHandler?.invoke(this, migrationStatus.storedDataModel as StoredRootDataModelDefinition, dataModel)
                            ?: throw MigrationException("Migration needed: No migration handler present. \n$migrationStatus")

                        if (!succeeded) {
                            throw MigrationException("Migration could not be handled for ${dataModel.Meta.name} & ${(migrationStatus.storedDataModel as? StoredRootDataModelDefinition)?.Meta?.version}\n$migrationStatus")
                        }

                        migrationStatus.indexesToIndex?.let {
                            fillIndex(it, tableColumnFamilies)
                        }
                        scheduledVersionUpdateHandlers.add {
                            versionUpdateHandler?.invoke(this, migrationStatus.storedDataModel as StoredRootDataModelDefinition, dataModel)
                            storeModelDefinition(db, metadataColumnFamily, index, tableColumnFamilies.model, dataModel)
                        }
                    }
                }
            }
        }

        startFlows()

        scheduledVersionUpdateHandlers.forEach { it() }
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
        metadataColumnFamily.close()
        db.close()
    }

    internal fun getColumnFamilies(dbIndex: UInt) =
        columnFamilyHandlesByDataModelIndex[dbIndex]
            ?: throw DefNotFoundException("DataModel definition not found for $dbIndex")

    internal fun getColumnFamilies(dataModel: IsRootDataModel) =
        columnFamilyHandlesByDataModelIndex[dataModelIdsByString[dataModel.Meta.name]]
            ?: throw DefNotFoundException("DataModel definition not found for ${dataModel.Meta.name}")

    internal fun readStoredModelNamesById(): Map<UInt, String> =
        readStoredModelNames(db, metadataColumnFamily)

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

    companion object {
        suspend fun open(
            keepAllVersions: Boolean = true,
            relativePath: String,
            dataModelsById: Map<UInt, IsRootDataModel>,
            rocksDBOptions: DBOptions? = null,
            onlyCheckModelVersion: Boolean = false,
            migrationHandler: MigrationHandler<RocksDBDataStore>? = null,
            versionUpdateHandler: VersionUpdateHandler<RocksDBDataStore>? = null,
        ): RocksDBDataStore {
            return RocksDBDataStore(
                keepAllVersions = keepAllVersions,
                relativePath = relativePath,
                dataModelsById = dataModelsById,
                rocksDBOptions = rocksDBOptions,
                onlyCheckModelVersion = onlyCheckModelVersion,
                migrationHandler = migrationHandler,
                versionUpdateHandler = versionUpdateHandler,
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
