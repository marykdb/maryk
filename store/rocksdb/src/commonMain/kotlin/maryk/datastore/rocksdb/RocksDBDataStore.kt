package maryk.datastore.rocksdb

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
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
import maryk.core.properties.references.IsPropertyReferenceForCache
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
import maryk.datastore.rocksdb.TableType.HistoricIndex
import maryk.datastore.rocksdb.TableType.HistoricTable
import maryk.datastore.rocksdb.TableType.HistoricUnique
import maryk.datastore.rocksdb.TableType.Index
import maryk.datastore.rocksdb.TableType.Keys
import maryk.datastore.rocksdb.TableType.Model
import maryk.datastore.rocksdb.TableType.Table
import maryk.datastore.rocksdb.TableType.UpdateHistory
import maryk.datastore.rocksdb.TableType.Unique
import maryk.datastore.rocksdb.metadata.ModelMeta
import maryk.datastore.rocksdb.metadata.readMetaFile
import maryk.datastore.rocksdb.metadata.writeMetaFile
import maryk.datastore.rocksdb.model.RocksDBLocalMigrationLease
import maryk.datastore.rocksdb.model.RocksDBMigrationAuditLogStore
import maryk.datastore.rocksdb.model.RocksDBMigrationStateStore
import maryk.datastore.rocksdb.model.checkModelIfMigrationIsNeeded
import maryk.datastore.rocksdb.model.modelUpdateHistoryBackfillCompleteKey
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
import maryk.datastore.rocksdb.processors.addSoftDeleteChangeIfMissing
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
import maryk.datastore.rocksdb.processors.readTransactionIntoObjectChanges
import maryk.datastore.rocksdb.processors.processScanChangesRequest
import maryk.datastore.rocksdb.processors.processScanRequest
import maryk.datastore.rocksdb.processors.processScanUpdatesRequest
import maryk.datastore.rocksdb.processors.helpers.getLastVersion
import maryk.datastore.rocksdb.processors.helpers.readVersionBytes
import maryk.datastore.rocksdb.processors.helpers.toReversedVersionBytes
import maryk.datastore.shared.AbstractDataStore
import maryk.datastore.shared.Cache
import maryk.datastore.shared.encryption.FieldEncryptionProvider
import maryk.datastore.shared.encryption.SensitiveIndexTokenProvider
import maryk.datastore.shared.migration.MigrationRuntimeDetails
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
    override val keepUpdateHistoryIndex: Boolean = false,
    relativePath: String,
    dataModelsById: Map<UInt, IsRootDataModel>,
    rocksDBOptions: DBOptions? = null,
    private val onlyCheckModelVersion: Boolean = false,
    val migrationConfiguration: MigrationConfiguration<RocksDBDataStore> = MigrationConfiguration(),
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
    private val updateHistoryReadyModelIds = atomic(setOf<UInt>())
    internal val pendingMigrationModelIds = atomic(setOf<UInt>())
    internal val pendingMigrationReasons = atomic(mapOf<UInt, String>())
    internal val pausedMigrationModelIds = atomic(setOf<UInt>())
    internal val canceledMigrationReasons = atomic(mapOf<UInt, String>())
    internal val pendingMigrationWaiters = atomic(mapOf<UInt, CompletableDeferred<Unit>>())
    internal val migrationRuntimeDetailsByModelId = atomic(mapOf<UInt, MigrationRuntimeDetails>())
    internal val migrationMetricsByModelId = atomic(mapOf<UInt, MigrationMetrics>())
    internal var migrationAuditLogStore: MigrationAuditLogStore? = null

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
                        updateHistory = if (keepUpdateHistoryIndex) handles[handleIndex++] else null,
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
                        unique = handles[handleIndex++],
                        updateHistory = if (keepUpdateHistoryIndex) handles[handleIndex++] else null
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
        val effectiveMigrationLease = migrationConfiguration.migrationLease ?: RocksDBLocalMigrationLease(storePath)
        val migrationStateStore = RocksDBMigrationStateStore(
            db,
            columnFamilyHandlesByDataModelIndex.mapValues { (_, tableColumnFamilies) -> tableColumnFamilies.model }
        )
        if (migrationConfiguration.persistMigrationAuditEvents) {
            migrationAuditLogStore = RocksDBMigrationAuditLogStore(
                rocksDB = db,
                modelColumnFamiliesById = columnFamilyHandlesByDataModelIndex.mapValues { (_, tableColumnFamilies) -> tableColumnFamilies.model },
                maxEntries = migrationConfiguration.migrationAuditLogMaxEntries
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
                    is NeedsMigration -> handleRequiredMigration(
                        index = index,
                        dataModel = dataModel,
                        migrationStatus = migrationStatus,
                        startupStarted = startupStarted,
                        effectiveMigrationLease = effectiveMigrationLease,
                        migrationStateStore = migrationStateStore,
                        recheckMigrationStatus = {
                            checkModelIfMigrationIsNeeded(
                                rocksDB = db,
                                modelMeta = modelMetas[index],
                                modelId = index,
                                modelColumnFamily = tableColumnFamilies.model,
                                dataModel = dataModel,
                                onlyCheckVersion = onlyCheckModelVersion,
                                conversionContext = DefinitionsConversionContext(),
                            )
                        },
                        finalizeInBackground = { storedModel ->
                            migrationStatus.indexesToIndex?.let { fillIndex(it, tableColumnFamilies) }
                            versionUpdateHandler?.invoke(this, storedModel, dataModel)
                            storeModelDefinition(db, modelMetas, index, tableColumnFamilies.model, dataModel)
                            ensureUpdateHistoryIndexReady(index, dataModel, tableColumnFamilies)
                            writeMetaFile(storePath, modelMetas)
                        },
                        finalizeInStartup = {
                            migrationStatus.indexesToIndex?.let { fillIndex(it, tableColumnFamilies) }
                            scheduledVersionUpdateHandlers.add {
                                versionUpdateHandler?.invoke(this, migrationStatus.storedDataModel as StoredRootDataModelDefinition, dataModel)
                                storeModelDefinition(db, modelMetas, index, tableColumnFamilies.model, dataModel)
                                ensureUpdateHistoryIndexReady(index, dataModel, tableColumnFamilies)
                            }
                        }
                    )
                }
            }
        }

        if (keepUpdateHistoryIndex) {
            for ((index, dataModel) in dataModelsById) {
                if (index !in pendingMigrationModelIds.value) {
                    ensureUpdateHistoryIndexReady(index, dataModel, getColumnFamilies(index))
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

    internal fun canUseUpdateHistoryIndex(dbIndex: UInt) =
        keepUpdateHistoryIndex && dbIndex in updateHistoryReadyModelIds.value

    private fun ensureUpdateHistoryIndexReady(
        dbIndex: UInt,
        dataModel: IsRootDataModel,
        tableColumnFamilies: TableColumnFamilies,
    ) {
        if (!keepUpdateHistoryIndex || tableColumnFamilies.updateHistory == null || canUseUpdateHistoryIndex(dbIndex)) return
        if (db.get(tableColumnFamilies.model, modelUpdateHistoryBackfillCompleteKey)?.firstOrNull() == 1.toByte()) {
            updateHistoryReadyModelIds.value = updateHistoryReadyModelIds.value + dbIndex
            return
        }

        backfillUpdateHistoryIndex(dbIndex, dataModel, tableColumnFamilies)
        db.put(tableColumnFamilies.model, modelUpdateHistoryBackfillCompleteKey, byteArrayOf(1))
        updateHistoryReadyModelIds.value = updateHistoryReadyModelIds.value + dbIndex
    }

    private fun backfillUpdateHistoryIndex(
        dbIndex: UInt,
        dataModel: IsRootDataModel,
        tableColumnFamilies: TableColumnFamilies,
    ) {
        if (tableColumnFamilies.updateHistory == null) return

        DBAccessor(this).use { dbAccessor ->
            dbAccessor.getIterator(defaultReadOptions, tableColumnFamilies.keys).use { keyIterator ->
                keyIterator.seekToFirst()

                if (keepAllVersions && tableColumnFamilies is HistoricTableColumnFamilies) {
                    val cache = Cache()

                    while (keyIterator.isValid()) {
                        val key = Key<IsRootDataModel>(keyIterator.key().copyOf())
                        val creationVersion = keyIterator.value().readVersionBytes()
                        val cachedRead = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                            cache.readValue(dbIndex, key, reference, version, valueReader)
                        }

                        dbAccessor.getIterator(defaultReadOptions, tableColumnFamilies.historic.table).use { objectIterator ->
                            dataModel.readTransactionIntoObjectChanges(
                                iterator = objectIterator,
                                creationVersion = creationVersion,
                                columnFamilies = tableColumnFamilies,
                                key = key,
                                select = null,
                                fromVersion = 0uL,
                                toVersion = null,
                                maxVersions = UInt.MAX_VALUE,
                                sortingKey = null,
                                cachedRead = cachedRead
                            )
                        }?.let { changes ->
                            addSoftDeleteChangeIfMissing(
                                dbAccessor = dbAccessor,
                                columnFamilies = tableColumnFamilies,
                                readOptions = defaultReadOptions,
                                key = key,
                                fromVersion = 0uL,
                                objectChange = changes
                            )
                        }?.changes?.forEach { versionedChange ->
                            db.put(
                                tableColumnFamilies.updateHistory,
                                versionedChange.version.toReversedVersionBytes() + key.bytes,
                                EMPTY_ARRAY
                            )
                        }

                        keyIterator.next()
                    }
                } else {
                    while (keyIterator.isValid()) {
                        val key = Key<IsRootDataModel>(keyIterator.key().copyOf())
                        val lastVersion = getLastVersion(dbAccessor, tableColumnFamilies, defaultReadOptions, key)
                        db.put(
                            tableColumnFamilies.updateHistory,
                            lastVersion.toReversedVersionBytes() + key.bytes,
                            EMPTY_ARRAY
                        )
                        keyIterator.next()
                    }
                }
            }
        }
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
        if (keepUpdateHistoryIndex) {
            descriptors += UpdateHistory.getDescriptor(tableIndex, nameSize)
        }

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
            launch(this.updateDispatcher) {
                updateSharedFlow.emit(updateToEmit)
            }
        }
    }

    internal fun emitUpdates(updatesToEmit: List<Update<*>>) {
        launch(this.updateDispatcher) {
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
        suspend fun open(
            keepAllVersions: Boolean = true,
            keepUpdateHistoryIndex: Boolean = false,
            relativePath: String,
            dataModelsById: Map<UInt, IsRootDataModel>,
            rocksDBOptions: DBOptions? = null,
            onlyCheckModelVersion: Boolean = false,
            migrationConfiguration: MigrationConfiguration<RocksDBDataStore> = MigrationConfiguration(),
            versionUpdateHandler: VersionUpdateHandler<RocksDBDataStore>? = null,
            fieldEncryptionProvider: FieldEncryptionProvider? = null,
        ): RocksDBDataStore {
            return RocksDBDataStore(
                keepAllVersions = keepAllVersions,
                keepUpdateHistoryIndex = keepUpdateHistoryIndex,
                relativePath = relativePath,
                dataModelsById = dataModelsById,
                rocksDBOptions = rocksDBOptions,
                onlyCheckModelVersion = onlyCheckModelVersion,
                migrationConfiguration = migrationConfiguration,
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
