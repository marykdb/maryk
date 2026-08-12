package maryk.datastore.rocksdb

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import maryk.core.clock.HLC
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.TypeException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.decodeVarUInt
import maryk.core.extensions.bytes.toByteArray
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
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.wrapper.IsSensitiveValueDefinitionWrapper
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Key
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.addDataModelReferences
import maryk.core.query.requests.AddRequest
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.requests.GetChangesRequest
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.GetUpdatesRequest
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.requests.ScanUpdateHistoryRequest
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
import maryk.datastore.rocksdb.metadata.CURRENT_INDEX_KEY_FORMAT_VERSION
import maryk.datastore.rocksdb.metadata.ModelMeta
import maryk.datastore.rocksdb.metadata.StoreMeta
import maryk.datastore.rocksdb.metadata.readStoreMetaFile
import maryk.datastore.rocksdb.metadata.writeStoreMetaFile
import maryk.datastore.rocksdb.model.RocksDBLocalMigrationLease
import maryk.datastore.rocksdb.processors.LAST_VERSION_INDICATOR
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
import maryk.datastore.rocksdb.processors.AnyScanUpdateHistoryStoreAction
import maryk.datastore.rocksdb.processors.AnyScanUpdatesStoreAction
import maryk.datastore.rocksdb.processors.EMPTY_ARRAY
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
import maryk.datastore.rocksdb.processors.SOFT_DELETE_INDICATOR
import maryk.datastore.rocksdb.processors.StoreValuesGetter
import maryk.datastore.rocksdb.processors.processScanChangesRequest
import maryk.datastore.rocksdb.processors.processScanRequest
import maryk.datastore.rocksdb.processors.processScanUpdateHistoryRequest
import maryk.datastore.rocksdb.processors.processScanUpdatesRequest
import maryk.datastore.rocksdb.processors.helpers.readVersionBytes
import maryk.datastore.rocksdb.processors.helpers.readVersionBytesIfExact
import maryk.datastore.rocksdb.processors.helpers.readReversedVersionBytes
import maryk.datastore.rocksdb.processors.helpers.setUniqueIndexValue
import maryk.datastore.rocksdb.processors.helpers.toReversedVersionBytes
import maryk.datastore.shared.AbstractDataStore
import maryk.datastore.shared.Cache
import maryk.datastore.shared.RequestExecutionKind
import maryk.datastore.shared.SnapshotVersionProvider
import maryk.datastore.shared.encryption.FieldEncryptionProvider
import maryk.datastore.shared.encryption.ContextualFieldEncryptionProvider
import maryk.datastore.shared.encryption.FieldEncryptionContext
import maryk.datastore.shared.encryption.FieldEncryptionEnvelope
import maryk.datastore.shared.encryption.SensitiveIndexTokenProvider
import maryk.datastore.shared.migration.MigrationRuntimeDetails
import maryk.datastore.shared.migration.MigrationAdmin
import maryk.datastore.shared.isSkippableDataError
import maryk.datastore.shared.rethrowIfFatal
import maryk.datastore.shared.requestExecutionKind
import maryk.datastore.shared.updates.Update
import maryk.datastore.shared.TypeIndicator
import maryk.rocksdb.ColumnFamilyDescriptor
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.DBOptions
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.OptimisticTransactionDB
import maryk.rocksdb.Options
import maryk.rocksdb.WriteOptions
import maryk.rocksdb.defaultColumnFamily
import maryk.rocksdb.listColumnFamilies
import maryk.rocksdb.openOptimisticTransactionDB
import maryk.lib.extensions.compare.matchesRangePart
import maryk.lib.recyclableByteArray
import kotlin.time.TimeSource

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
    maxConcurrentReads: Int = DEFAULT_MAX_CONCURRENT_READS,
) : AbstractDataStore(
    dataModelsById = dataModelsById,
    coroutineContext = Dispatchers.IO,
    maxConcurrentReads = maxConcurrentReads,
    readWorkerCoroutineContext = Dispatchers.IO.limitedParallelism(maxConcurrentReads),
), MigrationAdmin, SnapshotVersionProvider {
    private val columnFamilyHandlesByDataModelIndex = mutableMapOf<UInt, TableColumnFamilies>()
    private var retainedColumnFamilyHandles: List<ColumnFamilyHandle> = emptyList()
    private val prefixSizesByColumnFamilyHandlesIndex = mutableMapOf<Int, Int>()
    private val uniqueIndicesByDataModelIndex = atomic(mapOf<UInt, List<ByteArray>>())
    private val resourcesClosed = atomic(false)
    private val sensitiveReferencesByModelId: Map<UInt, SensitiveModelReferences> =
        dataModelsById.mapValues { (modelId, model) ->
            collectSensitiveReferences(modelId, model)
        }
    private val sensitiveReferencePrefixesByModelId: Map<UInt, List<ByteArray>> =
        sensitiveReferencesByModelId.mapValues { it.value.sensitiveReferences }
    private val sensitiveUniqueReferencesByModelId: Map<UInt, List<ByteArray>> =
        sensitiveReferencesByModelId.mapValues { it.value.sensitiveUniqueReferences }
    private val declaredUniqueReferencesByModelId: Map<UInt, List<ByteArray>> =
        dataModelsById.mapValues { (_, model) -> collectUniqueReferences(model) }

    override val supportsFuzzyQualifierFiltering: Boolean = true
    override val supportsSubReferenceFiltering: Boolean = true

    override suspend fun captureSnapshotVersion(): ULong = captureLocalSnapshotVersion()

    private val blockCache = RocksDBBlockCache()

    // Only create Options if no Options were passed. Will take ownership and close it if this object is closed
    private val ownRocksDBOptions: DBOptions? =
        if (rocksDBOptions == null) {
            DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true)
        } else null

    internal val db: OptimisticTransactionDB

    private val storePath: String = relativePath
    private var storeMeta: StoreMeta = readStoreMetaFile(storePath)
    private val modelMetas: MutableMap<UInt, ModelMeta> = storeMeta.models.toMutableMap()

    internal val defaultWriteOptions = WriteOptions()
    internal val defaultReadOptions = ReadOptions().apply {
        setPrefixSameAsStart(true)
    }
    internal val sequentialReadOptions = ReadOptions()

    internal fun createReadContext() = RocksDBReadContext.create(db)

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
        descriptors.add(ColumnFamilyDescriptor(defaultColumnFamily, blockCache.createColumnFamilyOptions()))
        for ((index, db) in dataModelsById) {
            createColumnFamilyHandles(descriptors, index, db)
        }
        addRetainedColumnFamilyDescriptors(descriptors)

        val handles = mutableListOf<ColumnFamilyHandle>()
        this.db = try {
            openOptimisticTransactionDB(rocksDBOptions ?: ownRocksDBOptions!!, storePath, descriptors, handles)
        } catch (throwable: Throwable) {
            ownRocksDBOptions?.close()
            blockCache.close()
            throw throwable
        }

        try {
            var handleIndex = 1
            if (keepAllVersions) {
                for ((index, db) in dataModelsById) {
                    prefixSizesByColumnFamilyHandlesIndex[handles[handleIndex+2].getID()] = db.Meta.keyByteSize
                    prefixSizesByColumnFamilyHandlesIndex[handles[handleIndex+5].getID()] = db.Meta.keyByteSize
                    columnFamilyHandlesByDataModelIndex[index] = HistoricTableColumnFamilies(
                        modelId = index,
                        keyByteSize = db.Meta.keyByteSize,
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
                        modelId = index,
                        keyByteSize = db.Meta.keyByteSize,
                        model = handles[handleIndex++],
                        keys = handles[handleIndex++],
                        table = handles[handleIndex++],
                        index = handles[handleIndex++],
                        unique = handles[handleIndex++],
                        updateHistory = if (keepUpdateHistoryIndex) handles[handleIndex++] else null
                    )
                }
            }
            retainedColumnFamilyHandles = handles.drop(handleIndex)

            validateStoredIndexKeyFormat()
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

        val conversionContext = DefinitionsConversionContext().apply {
            addDataModelReferences(dataModelsById.values)
        }
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
                                conversionContext = DefinitionsConversionContext().apply {
                                    addDataModelReferences(dataModelsById.values)
                                },
                            )
                        },
                        finalizeInBackground = { storedModel ->
                            migrationStatus.indexesToIndex?.let { fillIndex(it, tableColumnFamilies) }
                            versionUpdateHandler?.invoke(this, storedModel, dataModel)
                            storeModelDefinition(db, modelMetas, index, tableColumnFamilies.model, dataModel)
                            ensureUpdateHistoryIndexReady(index, tableColumnFamilies)
                            writeStoreMeta()
                        },
                        finalizeInStartup = {
                            migrationStatus.indexesToIndex?.let { fillIndex(it, tableColumnFamilies) }
                            scheduledVersionUpdateHandlers.add {
                                versionUpdateHandler?.invoke(this, migrationStatus.storedDataModel as StoredRootDataModelDefinition, dataModel)
                                storeModelDefinition(db, modelMetas, index, tableColumnFamilies.model, dataModel)
                                ensureUpdateHistoryIndexReady(index, tableColumnFamilies)
                            }
                        }
                    )
                }
            }
        }

        if (keepUpdateHistoryIndex) {
            for ((index, _) in dataModelsById) {
                if (index !in pendingMigrationModelIds.value) {
                    ensureUpdateHistoryIndexReady(index, getColumnFamilies(index))
                }
            }
        }

        startFlows()

        scheduledVersionUpdateHandlers.forEach {
            it()
            writeStoreMeta()
        }
    }

    private fun writeStoreMeta() {
        dataModelsById.forEach { (modelId, dataModel) ->
            if (modelId !in modelMetas) {
                modelMetas[modelId] = ModelMeta(dataModel.Meta.name, dataModel.Meta.keyByteSize)
            }
        }
        storeMeta = StoreMeta(
            models = modelMetas.toMap(),
            indexKeyFormatVersion = CURRENT_INDEX_KEY_FORMAT_VERSION
        )
        writeStoreMetaFile(storePath, storeMeta)
    }

    private fun validateStoredIndexKeyFormat() {
        val hasIndexOrUniqueData = !isIndexAndUniqueStorageEmpty()
        val storedFormat = storeMeta.indexKeyFormatVersion
        if (storedFormat == CURRENT_INDEX_KEY_FORMAT_VERSION) {
            return
        }

        if (!hasIndexOrUniqueData) {
            writeStoreMeta()
            return
        }

        migrateStoredIndexKeyFormat()
    }

    private fun migrateStoredIndexKeyFormat() {
        Transaction(this).use { transaction ->
            for ((_, columnFamilies) in columnFamilyHandlesByDataModelIndex) {
                clearColumnFamily(transaction, columnFamilies.index)
                clearColumnFamily(transaction, columnFamilies.unique)
                if (columnFamilies is HistoricTableColumnFamilies) {
                    clearColumnFamily(transaction, columnFamilies.historic.index)
                }
            }
            transaction.commit()
        }

        for ((modelId, dataModel) in dataModelsById) {
            val columnFamilies = getColumnFamilies(modelId)
            val indexes = dataModel.Meta.indexes.orEmpty()
            if (indexes.isNotEmpty()) {
                walkDataRecordsAndFillIndex(this, columnFamilies, indexes)
            }
            rebuildCurrentUniqueIndex(modelId, dataModel, columnFamilies)
        }

        uniqueIndicesByDataModelIndex.value = emptyMap()
        writeStoreMeta()
    }

    private fun rebuildCurrentUniqueIndex(
        modelId: UInt,
        dataModel: IsRootDataModel,
        columnFamilies: TableColumnFamilies
    ) {
        val uniqueReferences = declaredUniqueReferencesByModelId[modelId].orEmpty()
        if (uniqueReferences.isEmpty()) return

        Transaction(this).use { transaction ->
            val storeGetter = StoreValuesGetter(
                null,
                db,
                columnFamilies,
                defaultReadOptions,
                captureVersion = true,
                decryptValue = { key, reference, value, offset, length ->
                    decryptValueIfNeeded(columnFamilies.modelId, key, reference, value, offset, length)
                }
            )

            transaction.getIterator(defaultReadOptions, columnFamilies.keys).use { iterator ->
                iterator.seekToFirst()
                while (iterator.isValid()) {
                    val keyBytes = iterator.key()
                    if (iterator.value().readVersionBytesIfExact() == null) {
                        iterator.next()
                        continue
                    }

                    storeGetter.moveToKey(keyBytes)
                    val key = Key<IsRootDataModel>(keyBytes)
                    for (reference in uniqueReferences) {
                        writeCurrentUniqueValue(
                            modelId = modelId,
                            dataModel = dataModel,
                            transaction = transaction,
                            columnFamilies = columnFamilies,
                            storeGetter = storeGetter,
                            key = key,
                            reference = reference
                        )
                    }
                    iterator.next()
                }
            }
            transaction.commit()
        }
    }

    private fun writeCurrentUniqueValue(
        modelId: UInt,
        dataModel: IsRootDataModel,
        transaction: Transaction,
        columnFamilies: TableColumnFamilies,
        storeGetter: StoreValuesGetter,
        key: Key<*>,
        reference: ByteArray
    ) {
        try {
            var index = 0
            @Suppress("UNCHECKED_CAST")
            val propertyReference = dataModel.getPropertyReferenceByStorageBytes(
                reference.size,
                { reference[index++] },
                null
            ) as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>

            storeGetter.lastVersion = null
            val value = storeGetter[propertyReference] ?: return
            val storableDefinition = Value.castDefinition(propertyReference.comparablePropertyDefinition)
            @Suppress("UNCHECKED_CAST")
            val valueBytes = (storableDefinition as IsStorageBytesEncodable<Any>).toStorageBytes(
                value,
                TypeIndicator.NoTypeIndicator.byte
            )
            val uniqueReferenceWithValue = reference + mapUniqueValueBytes(modelId, reference, valueBytes)
            val versionBytes = storeGetter.lastVersion?.toByteArray() ?: return

            createUniqueIndexIfNotExists(modelId, columnFamilies.unique, reference)
            setUniqueIndexValue(columnFamilies, transaction, uniqueReferenceWithValue, versionBytes, key)
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            if (!error.isSkippableDataError()) {
                throw error
            }
        }
    }

    private fun clearColumnFamily(
        transaction: Transaction,
        columnFamily: ColumnFamilyHandle
    ) {
        do {
            val keysToDelete = ArrayList<ByteArray>(INDEX_FORMAT_MIGRATION_DELETE_BATCH_SIZE)
            transaction.getIterator(defaultReadOptions, columnFamily).use { iterator ->
                iterator.seekToFirst()
                while (iterator.isValid() && keysToDelete.size < INDEX_FORMAT_MIGRATION_DELETE_BATCH_SIZE) {
                    keysToDelete += iterator.key()
                    iterator.next()
                }
            }

            keysToDelete.forEach { transaction.delete(columnFamily, it) }
        } while (keysToDelete.size == INDEX_FORMAT_MIGRATION_DELETE_BATCH_SIZE)
    }

    private fun isIndexAndUniqueStorageEmpty(): Boolean {
        for ((_, columnFamilies) in columnFamilyHandlesByDataModelIndex) {
            if (!isColumnFamilyEmpty(columnFamilies.index)) return false
            if (!isColumnFamilyEmpty(columnFamilies.unique)) return false
            if (columnFamilies is HistoricTableColumnFamilies) {
                if (!isColumnFamilyEmpty(columnFamilies.historic.index)) return false
                if (!isColumnFamilyEmpty(columnFamilies.historic.unique)) return false
            }
        }
        return true
    }

    private fun isColumnFamilyEmpty(columnFamily: ColumnFamilyHandle): Boolean =
        db.newIterator(columnFamily, defaultReadOptions).use { iterator ->
            iterator.seekToFirst()
            !iterator.isValid()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun startFlows() {
        super.startFlows()

        this.launch {
            var clock = HLC()
            storeActorHasStarted.complete(Unit)
            try {
                processStoreActions(
                    createReadContext = ::createReadContext,
                    closeReadContext = RocksDBReadContext::close,
                    createFlowSnapshotBoundary = {
                        this@RocksDBDataStore.createFlowSnapshotBoundary()
                    },
                ) { storeAction, readContext ->
                    val cache = Cache()
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
                            is AddRequest<*> ->
                                processAddRequest(clock, storeAction as AnyAddStoreAction)
                            is ChangeRequest<*> ->
                                processChangeRequest(clock, storeAction as AnyChangeStoreAction)
                            is DeleteRequest<*> ->
                                processDeleteRequest(clock, storeAction as AnyDeleteStoreAction, cache)
                            is GetRequest<*> ->
                                processGetRequest(storeAction as AnyGetStoreAction, cache, requireNotNull(readContext))
                            is GetChangesRequest<*> ->
                                processGetChangesRequest(storeAction as AnyGetChangesStoreAction, cache, requireNotNull(readContext))
                            is GetUpdatesRequest<*> ->
                                processGetUpdatesRequest(storeAction as AnyGetUpdatesStoreAction, cache, requireNotNull(readContext))
                            is ScanRequest<*> ->
                                processScanRequest(storeAction as AnyScanStoreAction, cache, requireNotNull(readContext))
                            is ScanChangesRequest<*> ->
                                processScanChangesRequest(storeAction as AnyScanChangesStoreAction, cache, requireNotNull(readContext))
                            is ScanUpdateHistoryRequest<*> ->
                                processScanUpdateHistoryRequest(storeAction as AnyScanUpdateHistoryStoreAction, cache, requireNotNull(readContext))
                            is ScanUpdatesRequest<*> ->
                                processScanUpdatesRequest(storeAction as AnyScanUpdatesStoreAction, cache, requireNotNull(readContext))
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

    /** Walk all current values in [tableColumnFamilies] and fill [indexesToIndex] */
    private fun fillIndex(
        indexesToIndex: List<IsIndexable>,
        tableColumnFamilies: TableColumnFamilies
    ) {
        Transaction(this).use { transaction ->
            for (indexable in indexesToIndex) {
                deleteCompleteIndexContents(transaction, tableColumnFamilies, indexable)
            }
            transaction.commit()
        }

        walkDataRecordsAndFillIndex(this, tableColumnFamilies, indexesToIndex)
    }

    internal fun canUseUpdateHistoryIndex(dbIndex: UInt) =
        keepUpdateHistoryIndex && dbIndex in updateHistoryReadyModelIds.value

    private fun ensureUpdateHistoryIndexReady(
        dbIndex: UInt,
        tableColumnFamilies: TableColumnFamilies,
    ) {
        if (!keepUpdateHistoryIndex || tableColumnFamilies.updateHistory == null || canUseUpdateHistoryIndex(dbIndex)) return
        if (db.get(tableColumnFamilies.model, modelUpdateHistoryBackfillCompleteKey)?.firstOrNull() == 1.toByte()) {
            updateHistoryReadyModelIds.value += dbIndex
            return
        }

        backfillUpdateHistoryIndex(tableColumnFamilies)
        db.put(tableColumnFamilies.model, modelUpdateHistoryBackfillCompleteKey, byteArrayOf(1))
        updateHistoryReadyModelIds.value += dbIndex
    }

    private fun backfillUpdateHistoryIndex(
        tableColumnFamilies: TableColumnFamilies,
    ) {
        if (tableColumnFamilies.updateHistory == null) return

        DBAccessor(this).use { dbAccessor ->
            dbAccessor.getIterator(defaultReadOptions, tableColumnFamilies.keys).use { keyIterator ->
                keyIterator.seekToFirst()

                if (keepAllVersions && tableColumnFamilies is HistoricTableColumnFamilies) {
                    while (keyIterator.isValid()) {
                        val key = Key<IsRootDataModel>(keyIterator.key().copyOf())
                        val creationVersion = keyIterator.value().readVersionBytesIfExact() ?: run {
                            keyIterator.next()
                            continue
                        }

                        writeSingleUpdateHistoryVersion(tableColumnFamilies, key, creationVersion)

                        dbAccessor.getIterator(defaultReadOptions, tableColumnFamilies.historic.table).use { historicIterator ->
                            historicIterator.seek(key.bytes)
                            while (historicIterator.isValid()) {
                                val historicKey = historicIterator.key()
                                if (!historicKey.matchesRangePart(0, key.bytes)) break

                                val versionOffset = historicKey.size - ULong.SIZE_BYTES
                                if (versionOffset < key.bytes.size) {
                                    historicIterator.next()
                                    continue
                                }

                                writeSingleUpdateHistoryVersion(
                                    tableColumnFamilies,
                                    key,
                                    historicKey.readReversedVersionBytes(versionOffset)
                                )
                                historicIterator.next()
                            }
                        }

                        val softDeleteQualifier = key.bytes + SOFT_DELETE_INDICATOR
                        val softDeleteValueLength = dbAccessor.get(
                            tableColumnFamilies.table,
                            defaultReadOptions,
                            softDeleteQualifier,
                            recyclableByteArray
                        )
                        if (softDeleteValueLength == ULong.SIZE_BYTES + 1) {
                            writeSingleUpdateHistoryVersion(tableColumnFamilies, key, recyclableByteArray.readVersionBytes())
                        }

                        keyIterator.next()
                    }
                } else {
                    while (keyIterator.isValid()) {
                        val key = Key<IsRootDataModel>(keyIterator.key().copyOf())
                        val latestKey = key.bytes + LAST_VERSION_INDICATOR
                        val latestLength = dbAccessor.get(tableColumnFamilies.table, defaultReadOptions, latestKey, recyclableByteArray)
                        val lastVersion = recyclableByteArray.readVersionBytesIfExact(latestLength) ?: run {
                            keyIterator.next()
                            continue
                        }
                        writeSingleUpdateHistoryVersion(tableColumnFamilies, key, lastVersion)
                        keyIterator.next()
                    }
                }
            }
        }
    }

    private fun writeSingleUpdateHistoryVersion(
        tableColumnFamilies: TableColumnFamilies,
        key: Key<IsRootDataModel>,
        version: ULong,
    ) {
        val updateHistory = tableColumnFamilies.updateHistory ?: return
        db.put(
            updateHistory,
            version.toReversedVersionBytes() + key.bytes,
            EMPTY_ARRAY
        )
    }

    private fun createColumnFamilyHandles(descriptors: MutableList<ColumnFamilyDescriptor>, tableIndex: UInt, db: IsRootDataModel) {
        val nameSize = tableIndex.calculateVarByteLength() + 1

        // Prefix set to key size for more optimal search.
        val tableOptions = blockCache.createColumnFamilyOptions {
            useFixedLengthPrefixExtractor(db.Meta.keyByteSize)
        }

        descriptors += Model.getDescriptor(tableIndex, nameSize, blockCache.createColumnFamilyOptions())
        descriptors += Keys.getDescriptor(tableIndex, nameSize, blockCache.createColumnFamilyOptions())
        descriptors += Table.getDescriptor(tableIndex, nameSize, tableOptions)
        descriptors += Index.getDescriptor(tableIndex, nameSize, blockCache.createColumnFamilyOptions())
        descriptors += Unique.getDescriptor(tableIndex, nameSize, blockCache.createColumnFamilyOptions())
        if (keepUpdateHistoryIndex) {
            descriptors += UpdateHistory.getDescriptor(tableIndex, nameSize, blockCache.createColumnFamilyOptions())
        }

        if (keepAllVersions) {
            // Prefix set to key size for more optimal search.
            val tableOptionsHistoric = blockCache.createColumnFamilyOptions {
                useFixedLengthPrefixExtractor(db.Meta.keyByteSize)
                setComparator(blockCache.createVersionedComparator(db.Meta.keyByteSize))
            }

            val indexOptionsHistoric = blockCache.createColumnFamilyOptions {
                setComparator(blockCache.createVersionedComparator(db.Meta.keyByteSize))
            }

            val uniqueOptionsHistoric = blockCache.createColumnFamilyOptions {
                setComparator(blockCache.createVersionedComparator(db.Meta.keyByteSize))
            }

            descriptors += HistoricTable.getDescriptor(tableIndex, nameSize, tableOptionsHistoric)
            descriptors += HistoricIndex.getDescriptor(tableIndex, nameSize, indexOptionsHistoric)
            descriptors += HistoricUnique.getDescriptor(tableIndex, nameSize, uniqueOptionsHistoric)
        }
    }

    /**
     * RocksDB requires descriptors for every persisted column family at open time. Keep handles for
     * models or history modes no longer configured so callers can explicitly migrate or remove them.
     */
    private fun addRetainedColumnFamilyDescriptors(descriptors: MutableList<ColumnFamilyDescriptor>) {
        if (storeMeta.models.isEmpty()) return

        val configuredNames = buildList {
            add(defaultColumnFamily)
            dataModelsById.keys.forEach { modelId ->
                TableType.entries.forEach { tableType ->
                    if (tableType != UpdateHistory || keepUpdateHistoryIndex) {
                        if (tableType !in setOf(HistoricTable, HistoricIndex, HistoricUnique) || keepAllVersions) {
                            add(tableType.getName(modelId))
                        }
                    }
                }
            }
        }
        val options = Options()
        val persistedNames = try {
            listColumnFamilies(options, storePath)
        } finally {
            options.close()
        }

        persistedNames
            .filter { persistedName -> configuredNames.none { it.contentEquals(persistedName) } }
            .forEach { name ->
                val tableType = name.firstOrNull()?.let { TableType.entries.firstOrNull { type -> type.byte == it } }
                val modelId = name.decodeVarUInt(startIndex = 1)
                val keySize = modelId?.let { storeMeta.models[it]?.keySize }
                val columnFamilyOptions = when (tableType) {
                    HistoricTable, HistoricIndex, HistoricUnique -> blockCache.createColumnFamilyOptions {
                        setComparator(blockCache.createVersionedComparator(keySize ?: 0))
                    }
                    else -> blockCache.createColumnFamilyOptions()
                }
                descriptors += ColumnFamilyDescriptor(name, columnFamilyOptions)
            }
    }

    override suspend fun close() {
        cancelPendingMigrations("Datastore closing")

        super.close()

        closeResources()
    }

    fun closeResources() {
        if (resourcesClosed.getAndSet(true)) return

        columnFamilyHandlesByDataModelIndex.values.forEach {
            it.close()
        }
        retainedColumnFamilyHandles.forEach { it.close() }
        db.close()
        blockCache.close()
        defaultWriteOptions.close()
        defaultReadOptions.close()
        sequentialReadOptions.close()
        ownRocksDBOptions?.close()
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
        uniqueIndicesByDataModelIndex.value[dbIndex] ?: mergeUniqueReferences(
            declaredUniqueReferencesByModelId[dbIndex].orEmpty(),
            searchExistingUniqueIndices(uniqueHandle)
        ).also { existingDbUniques ->
            uniqueIndicesByDataModelIndex.value =
                uniqueIndicesByDataModelIndex.value.plus(dbIndex to existingDbUniques)
        }

    /**
     * Checks if a unique index exists and creates it if not otherwise.
     * This is needed so delete knows which indexes to scan for values to delete.
     */
    internal fun createUniqueIndexIfNotExists(dbIndex: UInt, uniqueHandle: ColumnFamilyHandle, uniqueName: ByteArray) {
        val existingDbUniques = getUniqueIndices(dbIndex, uniqueHandle)
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

    /** Search for existing unique indexes in the data store by [uniqueHandle] */
    private fun searchExistingUniqueIndices(
        uniqueHandle: ColumnFamilyHandle
    ) = buildList {
        this@RocksDBDataStore.db.newIterator(uniqueHandle).use { iterator ->
            iterator.seekToFirst()
            while (iterator.isValid()) {
                val key = iterator.key()
                if (key.isEmpty() || key[0] != 0.toByte()) {
                    break // break because it is not describing an index
                }
                this += key.copyOfRange(1, key.size)
                iterator.next()
            }
        }
    }

    internal suspend fun emitUpdate(updateToEmit: Update<*>?) {
        if (updateToEmit != null) {
            emitFlowUpdate(updateToEmit)
        }
    }

    internal suspend fun emitUpdates(updatesToEmit: List<Update<*>>) {
        updatesToEmit.forEach { emitFlowUpdate(it) }
    }

    internal fun encryptValueIfSensitive(modelId: UInt, key: ByteArray, reference: ByteArray, value: ByteArray): ByteArray {
        if (!isSensitiveReference(modelId, reference)) return value
        val provider = fieldEncryptionProvider as? ContextualFieldEncryptionProvider
            ?: throw RequestException("No contextual fieldEncryptionProvider configured for sensitive property write")
        val encrypted = runBlocking { provider.encrypt(FieldEncryptionContext(modelId, key, reference), value) }
        return FieldEncryptionEnvelope.Contextual.magic + encrypted
    }

    internal fun decryptValueIfNeeded(modelId: UInt, key: ByteArray, reference: ByteArray, value: ByteArray): ByteArray =
        decryptValueIfNeeded(modelId, key, reference, value, 0, value.size)

    internal fun decryptValueIfNeeded(
        modelId: UInt,
        key: ByteArray,
        reference: ByteArray,
        value: ByteArray,
        offset: Int,
        length: Int,
    ): ByteArray {
        val envelope = FieldEncryptionEnvelope.from(value, offset, length)
            ?: return if (offset == 0 && length == value.size) value else value.copyOfRange(offset, offset + length)
        val provider = fieldEncryptionProvider
            ?: throw RequestException("Encrypted value encountered but no fieldEncryptionProvider configured")
        val payloadOffset = offset + envelope.magic.size
        val payloadLength = length - envelope.magic.size
        return when (envelope) {
            FieldEncryptionEnvelope.Legacy -> runBlocking { provider.decrypt(value, payloadOffset, payloadLength) }
            FieldEncryptionEnvelope.Contextual -> {
                val contextualProvider = provider as? ContextualFieldEncryptionProvider
                    ?: throw RequestException("Contextual encrypted value encountered but no contextual fieldEncryptionProvider configured")
                runBlocking { contextualProvider.decrypt(FieldEncryptionContext(modelId, key, reference), value, payloadOffset, payloadLength) }
            }
        }
    }

    internal inline fun <T> withDecryptedValueIfNeeded(
        modelId: UInt,
        key: ByteArray,
        reference: ByteArray,
        value: ByteArray,
        offset: Int = 0,
        length: Int = value.size - offset,
        handle: (ByteArray, Int, Int) -> T
    ): T {
        if (FieldEncryptionEnvelope.from(value, offset, length) == null) return handle(value, offset, length)
        val decrypted = decryptValueIfNeeded(modelId, key, reference, value, offset, length)
        return handle(decrypted, 0, decrypted.size)
    }

    internal fun mapUniqueValueBytes(modelId: UInt, reference: ByteArray, value: ByteArray): ByteArray {
        if (!isSensitiveUniqueReference(modelId, reference)) return value
        val tokenProvider = fieldEncryptionProvider as? SensitiveIndexTokenProvider
            ?: throw RequestException("Sensitive unique property requires SensitiveIndexTokenProvider")
        return runBlocking { tokenProvider.deriveDeterministicToken(modelId, reference, value) }
    }

    internal fun mapUniqueValueByteCandidates(
        modelId: UInt,
        reference: ByteArray,
        value: ByteArray,
    ): List<ByteArray> {
        if (!isSensitiveUniqueReference(modelId, reference)) return listOf(value)
        val tokenProvider = fieldEncryptionProvider as? SensitiveIndexTokenProvider
            ?: throw RequestException("Sensitive unique property requires SensitiveIndexTokenProvider")
        return runBlocking {
            tokenProvider.deriveDeterministicTokenCandidates(modelId, reference, value)
        }
    }

    internal fun mapUniqueValueByteCandidates(
        modelId: UInt,
        reference: ByteArray,
        value: ByteArray,
        offset: Int,
        length: Int,
    ): List<ByteArray> {
        if (!isSensitiveUniqueReference(modelId, reference)) {
            return listOf(
                if (offset == 0 && length == value.size) value else value.copyOfRange(offset, offset + length)
            )
        }
        val tokenProvider = fieldEncryptionProvider as? SensitiveIndexTokenProvider
            ?: throw RequestException("Sensitive unique property requires SensitiveIndexTokenProvider")
        return runBlocking {
            tokenProvider.deriveDeterministicTokenCandidates(modelId, reference, value, offset, length)
        }
    }

    internal fun mapUniqueValueBytes(
        modelId: UInt,
        reference: ByteArray,
        value: ByteArray,
        offset: Int,
        length: Int
    ): ByteArray {
        if (!isSensitiveUniqueReference(modelId, reference)) {
            return if (offset == 0 && length == value.size) value else value.copyOfRange(offset, offset + length)
        }
        val tokenProvider = fieldEncryptionProvider as? SensitiveIndexTokenProvider
            ?: throw RequestException("Sensitive unique property requires SensitiveIndexTokenProvider")
        return runBlocking { tokenProvider.deriveDeterministicToken(modelId, reference, value, offset, length) }
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

    private fun collectUniqueReferences(dataModel: IsRootDataModel): List<ByteArray> {
        val uniqueReferences = mutableListOf<ByteArray>()
        collectUniqueReferencesRecursive(
            dataModel = dataModel,
            parentRef = null,
            uniqueReferences = uniqueReferences,
            modelPath = mutableListOf()
        )
        return uniqueReferences
    }

    private fun collectUniqueReferencesRecursive(
        dataModel: IsValuesDataModel,
        parentRef: AnyPropertyReference?,
        uniqueReferences: MutableList<ByteArray>,
        modelPath: MutableList<IsValuesDataModel>
    ) {
        if (modelPath.any { it === dataModel }) return
        modelPath += dataModel
        try {
            dataModel.forEach { wrapper ->
                val propertyReference = wrapper.ref(parentRef)
                val definition = wrapper.definition
                if (definition is IsComparableDefinition<*, *> && definition.unique) {
                    uniqueReferences += propertyReference.toStorageByteArray()
                }
                if (definition is EmbeddedValuesDefinition<*>) {
                    collectUniqueReferencesRecursive(
                        dataModel = definition.dataModel,
                        parentRef = propertyReference,
                        uniqueReferences = uniqueReferences,
                        modelPath = modelPath
                    )
                }
            }
        } finally {
            modelPath.removeAt(modelPath.lastIndex)
        }
    }

    private fun mergeUniqueReferences(
        declaredReferences: List<ByteArray>,
        storedReferences: List<ByteArray>
    ) = buildList {
        declaredReferences.forEach { add(it) }
        storedReferences.forEach { storedReference ->
            if (none { it.contentEquals(storedReference) }) {
                add(storedReference)
            }
        }
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

    override suspend fun getMigrationStatuses(): Map<UInt, MigrationRuntimeStatus> = migrationStatuses()

    override suspend fun getMigrationMetrics(): Map<UInt, MigrationMetrics> = migrationMetrics()

    override suspend fun requestMigrationPause(modelId: UInt): Boolean = pauseMigration(modelId)

    override suspend fun requestMigrationResume(modelId: UInt): Boolean = resumeMigration(modelId)

    override suspend fun requestMigrationCancel(modelId: UInt, reason: String): Boolean =
        cancelMigration(modelId, reason)

    suspend fun migrationAuditEvents(modelId: UInt, limit: Int = 100): List<MigrationAuditEvent> = migrationAuditEventsInternal(modelId, limit)

    suspend fun awaitMigration(modelId: UInt) = awaitMigrationInternal(modelId)

    internal fun ensurePendingMigrationWaiter(modelId: UInt): CompletableDeferred<Unit> = ensurePendingMigrationWaiterInternal(modelId)

    internal fun completePendingMigration(modelId: UInt) = completePendingMigrationInternal(modelId)

    internal fun failPendingMigration(modelId: UInt, reason: String) = failPendingMigrationInternal(modelId, reason)

    internal fun cancelPendingMigrations(reason: String) = cancelPendingMigrationsInternal(reason)

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
            maxConcurrentReads: Int = DEFAULT_MAX_CONCURRENT_READS,
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
                maxConcurrentReads = maxConcurrentReads,
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

private const val INDEX_FORMAT_MIGRATION_DELETE_BATCH_SIZE = 1024
private const val DEFAULT_MAX_CONCURRENT_READS = 4

private data class SensitiveModelReferences(
    val sensitiveReferences: List<ByteArray>,
    val sensitiveUniqueReferences: List<ByteArray>,
)
