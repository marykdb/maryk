package maryk.datastore.indexeddb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import maryk.core.aggregations.Aggregator
import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootDataModel
import maryk.core.models.emptyValues
import maryk.core.models.fromChanges
import maryk.core.models.key
import maryk.core.models.migration.MigrationConfiguration
import maryk.core.models.migration.VersionUpdateHandler
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.types.Bytes
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.matchesNamedSearchIndex
import maryk.core.properties.definitions.index.matchesNamedSearchIndexPrefix
import maryk.core.properties.definitions.index.matchesNamedSearchIndexRegex
import maryk.core.properties.definitions.index.stringIndexTransform
import maryk.core.properties.exceptions.AlreadyExistsException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.processors.datastore.findByteIndexAndSizeByPartIndex
import maryk.core.processors.datastore.matchers.IndexPartialSizeToMatch
import maryk.core.processors.datastore.matchers.IndexPartialToMatch
import maryk.core.processors.datastore.StorageTypeEnum.ObjectDelete
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.processors.datastore.StorageTypeEnum
import maryk.core.processors.datastore.matchers.IsQualifierMatcher
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.MATCH
import maryk.core.processors.datastore.matchers.QualifierExactMatcher
import maryk.core.processors.datastore.matchers.QualifierFuzzyMatcher
import maryk.core.processors.datastore.matchers.ReferencedQualifierMatcher
import maryk.core.processors.datastore.writeToStorage
import maryk.core.processors.datastore.scanRange.IndexableScanRanges
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.processors.datastore.scanRange.ScanRange
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.protobuf.WriteCache
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.filters.And
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Exists
import maryk.core.query.filters.FilterType
import maryk.core.query.filters.GreaterThan
import maryk.core.query.filters.GreaterThanEquals
import maryk.core.query.filters.IsFilter
import maryk.core.query.filters.LessThan
import maryk.core.query.filters.LessThanEquals
import maryk.core.query.filters.matchesFilter
import maryk.core.query.filters.Not
import maryk.core.query.filters.Or
import maryk.core.query.filters.Prefix
import maryk.core.query.filters.Range
import maryk.core.query.filters.RegEx
import maryk.core.query.filters.ValueIn
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.changes.Check
import maryk.core.query.changes.Change
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IncMapKeyAdditions
import maryk.core.query.changes.IndexChange
import maryk.core.query.changes.IndexDelete
import maryk.core.query.changes.IndexUpdate
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.changes.change
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
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.responses.AddOrChangeResponse
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.ChangesResponse
import maryk.core.query.responses.DataFetchType
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.FetchByKey
import maryk.core.query.responses.FetchByTableScan
import maryk.core.query.responses.FetchByUniqueKey
import maryk.core.query.responses.FetchByUpdateHistoryIndex
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.AlreadyExists
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.core.query.responses.statuses.IsAddOrChangeResponseStatus
import maryk.core.query.responses.statuses.IsChangeResponseStatus
import maryk.core.query.responses.statuses.IsDeleteResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalReason.NotInRange
import maryk.core.query.responses.updates.RemovalReason.SoftDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.references.ListReference
import maryk.core.properties.references.MapReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.NumberType
import maryk.core.query.ValueRange
import maryk.core.values.Values
import maryk.datastore.shared.AbstractDataStore
import maryk.datastore.shared.DISPATCHER
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.ScanType.TableScan
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.shared.UniqueException
import maryk.datastore.shared.checkToVersion
import maryk.datastore.shared.encryption.FieldEncryptionProvider
import maryk.datastore.shared.optimizeTableScan
import maryk.datastore.shared.orderToScanType
import maryk.datastore.shared.rethrowIfFatal
import maryk.datastore.shared.updates.Update
import maryk.lib.bytes.combineToByteArray
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.matchesRangePart
import maryk.lib.extensions.compare.nextByteInSameLength

class IndexedDbDataStore private constructor(
    private val databaseName: String,
    private val byteStore: IndexedDbByteStore,
    override val keepAllVersions: Boolean = false,
    override val keepUpdateHistoryIndex: Boolean = false,
    dataModelsById: Map<UInt, IsRootDataModel>,
    private val sensitiveFields: IndexedDbSensitiveFieldSupport,
) : AbstractDataStore(dataModelsById, DISPATCHER) {
    override val supportsFuzzyQualifierFiltering: Boolean = true
    override val supportsSubReferenceFiltering: Boolean = true

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
                for (storeAction in storeChannel) {
                    try {
                        clock = clock.calculateMaxTimeStamp()

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

    private suspend fun <DM : IsRootDataModel> processUpdateResponse(
        storeAction: ProcessUpdateResponseStoreAction<DM>,
    ) {
        val dataModel = storeAction.request.dataModel
        when (val update = storeAction.request.update) {
            is AdditionUpdate<DM> -> {
                if (update.firstVersion != update.version) {
                    throw RequestException("Cannot process an AdditionUpdate with a version different than the first version. Use a query for changes to properly process changes into a data store")
                }

                val response = CompletableDeferred<AddResponse<DM>>()
                processAddRequest(
                    version = HLC(update.version),
                    storeAction = StoreAction(
                        request = dataModel.add(update.key to update.values),
                        response = response,
                    )
                )
                storeAction.response.complete(ProcessResponse(update.version, response.await()))
            }
            is ChangeUpdate<DM> -> {
                if (update.changes.contains(ObjectCreate)) {
                    val response = CompletableDeferred<AddResponse<DM>>()
                    processAddRequest(
                        version = HLC(update.version),
                        storeAction = StoreAction(
                            request = dataModel.add(update.key to dataModel.fromChanges(null, update.changes)),
                            response = response,
                        )
                    )
                    storeAction.response.complete(ProcessResponse(update.version, response.await()))
                } else {
                    val response = CompletableDeferred<ChangeResponse<DM>>()
                    processChangeRequest(
                        version = HLC(update.version),
                        storeAction = StoreAction(
                            request = dataModel.change(update.key.change(*update.changes.toTypedArray())),
                            response = response,
                        )
                    )
                    storeAction.response.complete(ProcessResponse(update.version, response.await()))
                }
            }
            is RemovalUpdate<DM> -> {
                if (update.reason == NotInRange) {
                    throw RequestException("NotInRange deletes are not allowed, don't do limits or filters on requests which need to be processed")
                }

                val response = CompletableDeferred<DeleteResponse<DM>>()
                processDeleteRequest(
                    version = HLC(update.version),
                    storeAction = StoreAction(
                        request = dataModel.delete(update.key, hardDelete = update.reason == HardDelete),
                        response = response,
                    )
                )
                storeAction.response.complete(ProcessResponse(update.version, response.await()))
            }
            is InitialChangesUpdate<DM> -> {
                val statuses = mutableListOf<IsAddOrChangeResponseStatus<DM>>()
                for (change in update.changes) {
                    for (versionedChange in change.changes) {
                        if (versionedChange.changes.contains(ObjectCreate)) {
                            val response = CompletableDeferred<AddResponse<DM>>()
                            processAddRequest(
                                version = HLC(versionedChange.version),
                                storeAction = StoreAction(
                                    request = dataModel.add(change.key to dataModel.fromChanges(null, versionedChange.changes)),
                                    response = response,
                                )
                            )
                            statuses += response.await().statuses
                        } else {
                            val response = CompletableDeferred<ChangeResponse<DM>>()
                            processChangeRequest(
                                version = HLC(versionedChange.version),
                                storeAction = StoreAction(
                                    request = dataModel.change(change.key.change(*versionedChange.changes.toTypedArray())),
                                    response = response,
                                )
                            )
                            statuses += response.await().statuses
                        }
                    }
                }

                storeAction.response.complete(
                    ProcessResponse(
                        update.version,
                        AddOrChangeResponse(dataModel, statuses)
                    )
                )
            }
            is InitialValuesUpdate<DM> -> throw RequestException("Cannot process Values requests into data store since they do not contain all version information, do a changes request")
            is OrderedKeysUpdate<DM> -> throw RequestException("Cannot process Update requests into data store since they do not contain all change information, do a changes request")
            else -> throw TypeException("Unknown update type $update for datastore processing")
        }
    }

    private suspend fun <DM : IsRootDataModel> processAddRequest(
        version: HLC,
        storeAction: AddStoreAction<DM>,
    ) {
        val request = storeAction.request
        val modelId = getDataModelId(request.dataModel)
        val keyStoreName = "k:$modelId"
        val tableStoreName = "t:$modelId"
        val indexStoreName = "i:$modelId"
        val uniqueStoreName = "u:$modelId"
        val changeStoreName = "c:$modelId"
        val updateHistoryStoreName = "uh:$modelId"
        val historicTableStoreName = "ht:$modelId"
        val historicIndexStoreName = "hi:$modelId"
        val historicUniqueStoreName = "hu:$modelId"
        val historicIndexCleanupStoreName = "hik:$modelId"
        val historicUniqueCleanupStoreName = "huk:$modelId"
        val statuses = ArrayList<IsAddResponseStatus<DM>>(request.objects.size.coerceAtLeast(4))
        val writeStoreNames = modelWriteStoreNames(
            keyStoreName = keyStoreName,
            tableStoreName = tableStoreName,
            indexStoreName = indexStoreName,
            uniqueStoreName = uniqueStoreName,
            changeStoreName = changeStoreName,
            updateHistoryStoreName = updateHistoryStoreName,
            historicTableStoreName = historicTableStoreName,
            historicIndexStoreName = historicIndexStoreName,
            historicUniqueStoreName = historicUniqueStoreName,
            historicIndexCleanupStoreName = historicIndexCleanupStoreName,
            historicUniqueCleanupStoreName = historicUniqueCleanupStoreName,
        )

        for ((index, values) in request.objects.withIndex()) {
            try {
                byteStore.transaction(writeStoreNames, IndexedDbTransactionMode.READWRITE) { byteStore ->
                    val key = request.keysForObjects?.getOrNull(index) ?: request.dataModel.key(values)
                    if (byteStore.get(keyStoreName, key.bytes) != null) {
                        statuses += AlreadyExists(key)
                        return@transaction
                    }

                    values.validate()

                    val operations = mutableListOf<IndexedDbWriteOperation>()
                    val rows = mutableListOf<StorageRowToWrite>()
                    val indexRows = mutableListOf<ByteArray>()
                    val uniqueRows = mutableListOf<Triple<ByteArray, ByteArray, ByteArray>>()

                    request.dataModel.Meta.indexes?.forEach { index ->
                        index.toStorageByteArraysForIndex(values, key.bytes).forEach { valueAndKey ->
                            indexRows += createIndexRowKey(index.referenceStorageByteArray.bytes, valueAndKey)
                        }
                    }

                    values.writeToStorage { type, qualifier, definition, value ->
                        if (type == ObjectDelete) return@writeToStorage

                        val encodedValue = encodeStorageValue(type, definition, value)
                        rows += StorageRowToWrite(qualifier, encodedValue, definition, type)
                    }

                    val tableRows = mutableListOf<Pair<ByteArray, ByteArray>>()
                    for (row in rows) {
                        val encryptedValue = sensitiveFields.encryptValueIfSensitive(modelId, row.qualifier, row.encodedValue)
                        tableRows += createTableRowKey(key.bytes, row.qualifier) to encryptedValue

                        if (row.type == Value && row.definition is IsComparableDefinition<*, *> && row.definition.unique) {
                            val uniqueValue = sensitiveFields.mapUniqueValueBytes(modelId, row.qualifier, row.encodedValue)
                            val uniqueKey = createUniqueRowKey(row.qualifier, uniqueValue)
                            uniqueRows += Triple(uniqueKey, key.bytes, row.qualifier)
                        }
                    }

                    for ((uniqueKey, _, qualifier) in uniqueRows) {
                        val existingKey = byteStore.get(uniqueStoreName, uniqueKey)
                        if (existingKey != null) {
                            throw UniqueException(qualifier, request.dataModel.key(existingKey))
                        }
                    }

                    for ((rowKey, encodedValue) in tableRows) {
                        operations.put(tableStoreName, rowKey, encodedValue)
                    }
                    operations.put(
                        keyStoreName,
                        key.bytes,
                        encodeCurrentSnapshot(
                            IndexedDbRecordMeta(version.timestamp, version.timestamp, false),
                            tableRows.map { (rowKey, rowValue) -> tableQualifierFromRowKey(rowKey, key.bytes) to rowValue },
                        )
                    )
                    for (indexRow in indexRows) {
                        operations.put(indexStoreName, indexRow, key.bytes)
                    }
                    for ((uniqueKey, uniqueValue, _) in uniqueRows) {
                        operations.put(uniqueStoreName, uniqueKey, uniqueValue)
                    }
                    if (keepAllVersions) {
                        operations.addHistoricSnapshot(
                            historicTableStoreName,
                            key.bytes,
                            version.timestamp,
                            IndexedDbRecordMeta(version.timestamp, version.timestamp, false),
                            tableRows.map { (rowKey, rowValue) -> tableQualifierFromRowKey(rowKey, key.bytes) to rowValue },
                        )
                        operations.addHistoricIndexRows(historicIndexStoreName, historicIndexCleanupStoreName, key.bytes, indexRows, version.timestamp, active = true)
                        operations.addHistoricUniqueRows(historicUniqueStoreName, historicUniqueCleanupStoreName, uniqueRows, version.timestamp, active = true)
                    }
                    val changePayload = operations.addChangeLog(
                        dataModel = request.dataModel,
                        changeStoreName = changeStoreName,
                        keyBytes = key.bytes,
                        version = version.timestamp,
                        changes = listOf(ObjectCreate) + values.toChanges().toList()
                    )
                    if (keepUpdateHistoryIndex && changePayload != null) {
                        operations.put(updateHistoryStoreName, createUpdateHistoryRowKey(version.timestamp, key.bytes), changePayload)
                    }

                    byteStore.writeBatch(operations)
                    statuses += AddSuccess(key, version.timestamp, emptyList())
                    updateSharedFlow.emit(
                        Update.Addition(request.dataModel, key, version.timestamp, values)
                    )
                }
            } catch (e: ValidationUmbrellaException) {
                statuses += ValidationFail(e)
            } catch (e: ValidationException) {
                statuses += ValidationFail(listOf(e))
            } catch (e: UniqueException) {
                statuses += ValidationFail(listOf(createAlreadyExistsException(request.dataModel, e)))
            }
        }

        storeAction.response.complete(
            AddResponse(
                dataModel = request.dataModel,
                statuses = statuses
            )
        )
    }

    private suspend fun <DM : IsRootDataModel> processChangeRequest(
        version: HLC,
        storeAction: ChangeStoreAction<DM>,
    ) {
        val request = storeAction.request
        val modelId = getDataModelId(request.dataModel)
        val keyStoreName = "k:$modelId"
        val tableStoreName = "t:$modelId"
        val indexStoreName = "i:$modelId"
        val uniqueStoreName = "u:$modelId"
        val changeStoreName = "c:$modelId"
        val updateHistoryStoreName = "uh:$modelId"
        val historicTableStoreName = "ht:$modelId"
        val historicIndexStoreName = "hi:$modelId"
        val historicUniqueStoreName = "hu:$modelId"
        val historicIndexCleanupStoreName = "hik:$modelId"
        val historicUniqueCleanupStoreName = "huk:$modelId"
        val statuses = ArrayList<IsChangeResponseStatus<DM>>(request.objects.size.coerceAtLeast(4))
        val writeStoreNames = modelWriteStoreNames(
            keyStoreName = keyStoreName,
            tableStoreName = tableStoreName,
            indexStoreName = indexStoreName,
            uniqueStoreName = uniqueStoreName,
            changeStoreName = changeStoreName,
            updateHistoryStoreName = updateHistoryStoreName,
            historicTableStoreName = historicTableStoreName,
            historicIndexStoreName = historicIndexStoreName,
            historicUniqueStoreName = historicUniqueStoreName,
            historicIndexCleanupStoreName = historicIndexCleanupStoreName,
            historicUniqueCleanupStoreName = historicUniqueCleanupStoreName,
        )

        for (objectChange in request.objects) {
            try {
                byteStore.transaction(writeStoreNames, IndexedDbTransactionMode.READWRITE) { byteStore ->
                    val keyBytes = objectChange.key.bytes
                    val currentMeta = byteStore.get(keyStoreName, keyBytes)?.let(::decodeRecordMeta)
                    if (currentMeta == null) {
                        statuses += DoesNotExist(objectChange.key)
                        return@transaction
                    }

                    val expectedVersion = objectChange.lastVersion
                    if (expectedVersion != null && currentMeta.lastVersion != expectedVersion) {
                        statuses += ValidationFail(
                            listOf(
                                InvalidValueException(
                                    null,
                                    "Version of object was different than given: $expectedVersion < ${currentMeta.lastVersion}"
                                )
                            )
                        )
                        return@transaction
                    }

                    val currentValues = byteStore.readCurrentValuesDecrypted(request.dataModel, tableStoreName, keyBytes, null)
                        ?: run {
                            statuses += DoesNotExist(objectChange.key)
                            return@transaction
                        }

                    val checkExceptions = evaluateChecks(objectChange.changes, currentValues)
                    if (checkExceptions.isNotEmpty()) {
                        statuses += ValidationFail(checkExceptions)
                        return@transaction
                    }

                    val materializedChanges = materializeChanges(objectChange.changes, currentValues)
                    val valuesToChange = seedMissingRootMaps(currentValues, materializedChanges.appliedChanges)
                    val changedValues = valuesToChange.change(materializedChanges.appliedChanges)
                    val softDeleteChange = objectChange.changes
                        .filterIsInstance<ObjectSoftDeleteChange>()
                        .lastOrNull()
                    val targetIsDeleted = softDeleteChange?.isDeleted ?: currentMeta.isDeleted
                    if (!targetIsDeleted) {
                        changedValues.validate()
                    }

                    if (changedValues == currentValues && targetIsDeleted == currentMeta.isDeleted) {
                        statuses += ChangeSuccess(
                            version = currentMeta.lastVersion,
                            changes = materializedChanges.generatedChanges.ifEmpty { null }
                        )
                        return@transaction
                    }

                    val storagePlan = createStoragePlan(request.dataModel, modelId, keyBytes, changedValues, sensitiveFields)
                    validateUniqueRows(request.dataModel, keyBytes, uniqueStoreName, storagePlan.uniqueRows)

                    val oldIndexRows = collectCurrentIndexRows(request.dataModel, keyBytes)
                    val oldUniqueRows = collectCurrentUniqueRows(request.dataModel, modelId, tableStoreName, keyBytes)
                    val oldTableRows = scanTableRows(tableStoreName, keyBytes)
                    val indexChanges = buildList {
                        request.dataModel.Meta.indexes?.forEach { index ->
                            val oldValues = index.toStorageByteArraysForIndex(currentValues, keyBytes)
                            val newValues = if (targetIsDeleted) {
                                emptyList()
                            } else {
                                index.toStorageByteArraysForIndex(changedValues, keyBytes)
                            }
                            val removed = oldValues.filter { oldValue ->
                                newValues.none { it.contentEquals(oldValue) }
                            }
                            val added = newValues.filter { newValue ->
                                oldValues.none { it.contentEquals(newValue) }
                            }
                            if (removed.size == 1 && added.size == 1) {
                                add(IndexUpdate(index.referenceStorageByteArray, Bytes(added.first()), Bytes(removed.first())))
                            } else {
                                removed.forEach { oldValue ->
                                    add(IndexDelete(index.referenceStorageByteArray, Bytes(oldValue)))
                                }
                                added.forEach { newValue ->
                                    add(IndexUpdate(index.referenceStorageByteArray, Bytes(newValue), null))
                                }
                            }
                        }
                    }
                    val operations = mutableListOf<IndexedDbWriteOperation>()
                    for (indexRow in oldIndexRows) {
                        operations.delete(indexStoreName, indexRow)
                    }
                    for ((uniqueKey, _, _) in oldUniqueRows) {
                        operations.delete(uniqueStoreName, uniqueKey)
                    }
                    for ((rowKey, _) in oldTableRows) {
                        operations.delete(tableStoreName, rowKey)
                    }

                    for ((rowKey, rowValue) in storagePlan.tableRows) {
                        operations.put(tableStoreName, rowKey, rowValue)
                    }
                    operations.put(
                        keyStoreName,
                        keyBytes,
                        encodeCurrentSnapshot(
                            IndexedDbRecordMeta(currentMeta.firstVersion, version.timestamp, targetIsDeleted),
                            storagePlan.tableRows.map { (rowKey, rowValue) -> tableQualifierFromRowKey(rowKey, keyBytes) to rowValue },
                        )
                    )
                    if (!targetIsDeleted) {
                        for (indexRow in storagePlan.indexRows) {
                            operations.put(indexStoreName, indexRow, keyBytes)
                        }
                        for ((uniqueKey, uniqueValue, _) in storagePlan.uniqueRows) {
                            operations.put(uniqueStoreName, uniqueKey, uniqueValue)
                        }
                    }
                    if (keepAllVersions) {
                        operations.addHistoricSnapshot(
                            historicTableStoreName,
                            keyBytes,
                            version.timestamp,
                            IndexedDbRecordMeta(currentMeta.firstVersion, version.timestamp, targetIsDeleted),
                            storagePlan.tableRows.map { (rowKey, rowValue) -> tableQualifierFromRowKey(rowKey, keyBytes) to rowValue },
                        )
                        operations.addHistoricIndexRows(historicIndexStoreName, historicIndexCleanupStoreName, keyBytes, oldIndexRows, version.timestamp, active = false)
                        operations.addHistoricIndexRows(historicIndexStoreName, historicIndexCleanupStoreName, keyBytes, storagePlan.indexRows, version.timestamp, active = true)
                        operations.addHistoricUniqueRows(historicUniqueStoreName, historicUniqueCleanupStoreName, oldUniqueRows, version.timestamp, active = false)
                        operations.addHistoricUniqueRows(historicUniqueStoreName, historicUniqueCleanupStoreName, storagePlan.uniqueRows, version.timestamp, active = true)
                    }
                    val changePayload = operations.addChangeLog(
                        dataModel = request.dataModel,
                        changeStoreName = changeStoreName,
                        keyBytes = keyBytes,
                        version = version.timestamp,
                        changes = materializedChanges.appliedChanges.filterNot { it is Check }
                    )
                    if (keepUpdateHistoryIndex && changePayload != null) {
                        operations.put(updateHistoryStoreName, createUpdateHistoryRowKey(version.timestamp, keyBytes), changePayload)
                    }

                    byteStore.writeBatch(operations)
                    statuses += ChangeSuccess(
                        version = version.timestamp,
                        changes = materializedChanges.generatedChanges.ifEmpty { null }
                    )
                    updateSharedFlow.emit(
                        Update.Change(
                            request.dataModel,
                            objectChange.key,
                            version.timestamp,
                            materializedChanges.appliedChanges.filterNot { it is Check } +
                                indexChanges.takeUnless { it.isEmpty() }?.let { listOf(IndexChange(it)) }.orEmpty()
                        )
                    )
                }
            } catch (e: ValidationUmbrellaException) {
                statuses += ValidationFail(e)
            } catch (e: ValidationException) {
                statuses += ValidationFail(listOf(e))
            } catch (e: UniqueException) {
                statuses += ValidationFail(listOf(createAlreadyExistsException(request.dataModel, e)))
            } catch (e: RequestException) {
                statuses += ServerFail(e.message ?: "Could not apply change request", e)
            } catch (e: IllegalStateException) {
                statuses += ServerFail(e.message ?: "Could not apply change request", e)
            }
        }

        storeAction.response.complete(
            ChangeResponse(
                dataModel = request.dataModel,
                statuses = statuses
            )
        )
    }

    private suspend fun <DM : IsRootDataModel> processDeleteRequest(
        version: HLC,
        storeAction: DeleteStoreAction<DM>,
    ) {
        val request = storeAction.request
        val modelId = getDataModelId(request.dataModel)
        val keyStoreName = "k:$modelId"
        val tableStoreName = "t:$modelId"
        val indexStoreName = "i:$modelId"
        val uniqueStoreName = "u:$modelId"
        val changeStoreName = "c:$modelId"
        val updateHistoryStoreName = "uh:$modelId"
        val historicTableStoreName = "ht:$modelId"
        val historicIndexStoreName = "hi:$modelId"
        val historicUniqueStoreName = "hu:$modelId"
        val historicIndexCleanupStoreName = "hik:$modelId"
        val historicUniqueCleanupStoreName = "huk:$modelId"
        val statuses = ArrayList<IsDeleteResponseStatus<DM>>(request.keys.size.coerceAtLeast(4))
        val writeStoreNames = modelWriteStoreNames(
            keyStoreName = keyStoreName,
            tableStoreName = tableStoreName,
            indexStoreName = indexStoreName,
            uniqueStoreName = uniqueStoreName,
            changeStoreName = changeStoreName,
            updateHistoryStoreName = updateHistoryStoreName,
            historicTableStoreName = historicTableStoreName,
            historicIndexStoreName = historicIndexStoreName,
            historicUniqueStoreName = historicUniqueStoreName,
            historicIndexCleanupStoreName = historicIndexCleanupStoreName,
            historicUniqueCleanupStoreName = historicUniqueCleanupStoreName,
        )

        for (key in request.keys) {
            byteStore.transaction(writeStoreNames, IndexedDbTransactionMode.READWRITE) { byteStore ->
                val currentMeta = byteStore.get(keyStoreName, key.bytes)?.let(::decodeRecordMeta)
                if (currentMeta == null) {
                    statuses += DoesNotExist(key)
                    return@transaction
                }

                val oldTableRows = if (keepAllVersions && !request.hardDelete) {
                    scanTableRows(tableStoreName, key.bytes)
                } else {
                    emptyList()
                }
                val oldIndexRows = collectCurrentIndexRows(request.dataModel, key.bytes)
                val oldUniqueRows = collectCurrentUniqueRows(request.dataModel, modelId, tableStoreName, key.bytes)
                val operations = mutableListOf<IndexedDbWriteOperation>()
                for (indexRow in oldIndexRows) {
                    operations.delete(indexStoreName, indexRow)
                }
                for ((uniqueKey, _, _) in oldUniqueRows) {
                    operations.delete(uniqueStoreName, uniqueKey)
                }

                if (request.hardDelete) {
                    operations.delete(keyStoreName, key.bytes)
                    for ((rowKey, _) in scanTableRows(tableStoreName, key.bytes)) {
                        operations.delete(tableStoreName, rowKey)
                    }
                    if (keepAllVersions) {
                        for ((rowKey, _) in byteStore.scanObjectScopedRows(historicTableStoreName, key.bytes)) {
                            operations.delete(historicTableStoreName, rowKey)
                        }
                        for ((cleanupRowKey, historicRowKey) in byteStore.historicCleanupRowsForKey(historicIndexCleanupStoreName, key.bytes)) {
                            operations.delete(historicIndexStoreName, historicRowKey)
                            operations.delete(historicIndexCleanupStoreName, cleanupRowKey)
                        }
                        for ((cleanupRowKey, historicRowKey) in byteStore.historicCleanupRowsForKey(historicUniqueCleanupStoreName, key.bytes)) {
                            operations.delete(historicUniqueStoreName, historicRowKey)
                            operations.delete(historicUniqueCleanupStoreName, cleanupRowKey)
                        }
                    }
                    for ((rowKey, _) in byteStore.scanObjectScopedRows(changeStoreName, key.bytes)) {
                        operations.delete(changeStoreName, rowKey)
                    }
                    if (keepUpdateHistoryIndex) {
                        operations.put(updateHistoryStoreName, createUpdateHistoryRowKey(version.timestamp, key.bytes), byteArrayOf(1))
                    }
                } else {
                    operations.put(
                        keyStoreName,
                        key.bytes,
                        encodeCurrentSnapshot(
                            IndexedDbRecordMeta(currentMeta.firstVersion, version.timestamp, true),
                            oldTableRows.map { (rowKey, rowValue) -> tableQualifierFromRowKey(rowKey, key.bytes) to rowValue },
                        )
                    )
                    if (keepAllVersions) {
                        operations.addHistoricSnapshot(
                            historicTableStoreName,
                            key.bytes,
                            version.timestamp,
                            IndexedDbRecordMeta(currentMeta.firstVersion, version.timestamp, true),
                            oldTableRows.map { (rowKey, rowValue) -> tableQualifierFromRowKey(rowKey, key.bytes) to rowValue },
                        )
                        operations.addHistoricIndexRows(historicIndexStoreName, historicIndexCleanupStoreName, key.bytes, oldIndexRows, version.timestamp, active = true)
                        operations.addHistoricUniqueRows(historicUniqueStoreName, historicUniqueCleanupStoreName, oldUniqueRows, version.timestamp, active = true)
                    }
                    val changePayload = operations.addChangeLog(
                        dataModel = request.dataModel,
                        changeStoreName = changeStoreName,
                        keyBytes = key.bytes,
                        version = version.timestamp,
                        changes = listOf(ObjectSoftDeleteChange(true))
                    )
                    if (keepUpdateHistoryIndex && changePayload != null) {
                        operations.put(updateHistoryStoreName, createUpdateHistoryRowKey(version.timestamp, key.bytes), changePayload)
                    }
                }

                byteStore.writeBatch(operations)
                statuses += DeleteSuccess(version.timestamp)
                updateSharedFlow.emit(
                    Update.Deletion(request.dataModel, key, version.timestamp, request.hardDelete)
                )
            }
        }

        storeAction.response.complete(
            DeleteResponse(
                dataModel = request.dataModel,
                statuses = statuses
            )
        )
    }

    private suspend fun <DM : IsRootDataModel> processGetRequest(
        storeAction: GetStoreAction<DM>,
    ) {
        val request = storeAction.request
        request.checkToVersion(keepAllVersions)

        val modelId = getDataModelId(request.dataModel)
        val keyStoreName = "k:$modelId"
        val tableStoreName = "t:$modelId"
        val historicTableStoreName = "ht:$modelId"
        val aggregator = request.aggregations?.let(::Aggregator)
        val values = ArrayList<ValuesWithMetaData<DM>>(request.keys.size.coerceAtLeast(4))

        for (key in request.keys) {
            val toVersion = request.toVersion
            val record = if (toVersion != null) {
                byteStore.readHistoricRecordDecrypted(request.dataModel, historicTableStoreName, key.bytes, toVersion, request.select)
            } else {
                byteStore.readCurrentSnapshotDecrypted(request.dataModel, keyStoreName, key.bytes, request.select)
                    ?: byteStore.readRecordDecrypted(request.dataModel, keyStoreName, tableStoreName, key.bytes, request.select)
            }
                ?: continue
            if (request.filterSoftDeleted && record.isDeleted) continue
            val valuesForFilter = if (request.where != null && request.select != null) {
                if (toVersion != null) {
                    byteStore.readHistoricRecordDecrypted(request.dataModel, historicTableStoreName, key.bytes, toVersion, null)
                } else {
                    byteStore.readCurrentSnapshotDecrypted(request.dataModel, keyStoreName, key.bytes, null)
                        ?: byteStore.readRecordDecrypted(request.dataModel, keyStoreName, tableStoreName, key.bytes, null)
                }?.values ?: continue
            } else {
                record.values
            }
            if (!valuesMatchFilter(request.dataModel, valuesForFilter, request.where, request.toVersion)) continue

            values += record
            aggregator?.aggregate { reference -> record.values[reference] }
        }

        storeAction.response.complete(
            ValuesResponse(
                dataModel = request.dataModel,
                values = values,
                aggregations = aggregator?.toResponse(),
                dataFetchType = FetchByKey,
            )
        )
    }

    private suspend fun <DM : IsRootDataModel> processGetChangesRequest(
        storeAction: GetChangesStoreAction<DM>,
    ) {
        val request = storeAction.request
        request.checkToVersion(keepAllVersions)
        if (!keepAllVersions && request.maxVersions > 1u) {
            throw RequestException("Cannot use maxVersions > 1 on a table which has keepAllVersions set to false")
        }

        val modelId = getDataModelId(request.dataModel)
        val keyStoreName = "k:$modelId"
        val changeStoreName = "c:$modelId"
        val historicTableStoreName = "ht:$modelId"
        val changes = mutableListOf<DataObjectVersionedChange<DM>>()

        for (key in request.keys) {
            val versionedChanges = byteStore.readChangeLog(
                dataModel = request.dataModel,
                changeStoreName = changeStoreName,
                historicTableStoreName = historicTableStoreName,
                keyBytes = key.bytes,
                fromVersion = request.fromVersion,
                toVersion = request.toVersion,
                maxVersions = request.maxVersions,
                select = request.select,
                decryptValue = sensitiveFields::decryptValueIfNeeded,
            )

            if (versionedChanges.isEmpty()) continue

            val record = request.toVersion?.let { toVersion ->
                byteStore.readHistoricRecordDecrypted(request.dataModel, historicTableStoreName, key.bytes, toVersion, null)
            } ?: (
                byteStore.readCurrentSnapshotDecrypted(request.dataModel, keyStoreName, key.bytes, null)
                    ?: byteStore.readRecordDecrypted(request.dataModel, keyStoreName, "t:$modelId", key.bytes, null)
                )
                ?: continue
            if (request.filterSoftDeleted && record.isDeleted) continue
            if (!valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion)) continue

            changes += DataObjectVersionedChange(
                key = key,
                changes = versionedChanges,
            )
        }

        storeAction.response.complete(
            ChangesResponse(
                dataModel = request.dataModel,
                changes = changes,
                dataFetchType = FetchByKey,
            )
        )
    }

    private suspend fun <DM : IsRootDataModel> processGetUpdatesRequest(
        storeAction: GetUpdatesStoreAction<DM>,
    ) {
        val request = storeAction.request
        request.checkToVersion(keepAllVersions)
        if (!keepAllVersions && request.maxVersions > 1u) {
            throw RequestException("Cannot use maxVersions > 1 on a table which has keepAllVersions set to false")
        }

        val modelId = getDataModelId(request.dataModel)
        val keyStoreName = "k:$modelId"
        val tableStoreName = "t:$modelId"
        val historicTableStoreName = "ht:$modelId"
        val updates = mutableListOf<IsUpdateResponse<DM>>()
        val keys = mutableListOf<Key<DM>>()
        var highestVersion = 0uL

        for (key in request.keys) {
            val toVersion = request.toVersion
            val record = if (toVersion != null) {
                byteStore.readHistoricRecordDecrypted(request.dataModel, historicTableStoreName, key.bytes, toVersion, request.select)
            } else {
                byteStore.readCurrentSnapshotDecrypted(request.dataModel, keyStoreName, key.bytes, request.select)
                    ?: byteStore.readRecordDecrypted(request.dataModel, keyStoreName, tableStoreName, key.bytes, request.select)
            }
                ?: continue
            if (request.filterSoftDeleted && record.isDeleted) continue
            if (!valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion)) continue

            keys += key
            highestVersion = maxOf(highestVersion, record.lastVersion)
            if (record.lastVersion >= request.fromVersion && (toVersion == null || record.lastVersion <= toVersion)) {
                updates += AdditionUpdate(
                    key = key,
                    version = record.lastVersion,
                    firstVersion = record.firstVersion,
                    insertionIndex = keys.lastIndex,
                    isDeleted = record.isDeleted,
                    values = record.values,
                )
            }
        }

        storeAction.response.complete(
            UpdatesResponse(
                dataModel = request.dataModel,
                updates = listOf(OrderedKeysUpdate(keys, highestVersion)) + updates,
                dataFetchType = FetchByKey,
            )
        )
    }

    private suspend fun <DM : IsRootDataModel> processScanChangesRequest(
        storeAction: ScanChangesStoreAction<DM>,
    ) {
        val request = storeAction.request
        request.checkToVersion(keepAllVersions)
        if (!keepAllVersions && request.maxVersions > 1u) {
            throw RequestException("Cannot use maxVersions > 1 on a table which has keepAllVersions set to false")
        }

        val modelId = getDataModelId(request.dataModel)
        val keyStoreName = "k:$modelId"
        val tableStoreName = "t:$modelId"
        val indexStoreName = "i:$modelId"
        val historicTableStoreName = "ht:$modelId"
        val historicIndexStoreName = "hi:$modelId"
        val changeStoreName = "c:$modelId"
        val keyScanRange = request.dataModel.createScanRange(request.where, request.startKey?.bytes, request.includeStart)
        val changes = mutableListOf<DataObjectVersionedChange<DM>>()
        val scanType = request.dataModel.orderToScanType(request.order, keyScanRange.equalPairs)
        if (scanType is IndexScan) {
            storeAction.response.complete(
                processIndexScanChanges(
                    request = request,
                    keyStoreName = keyStoreName,
                    tableStoreName = tableStoreName,
                    indexStoreName = indexStoreName,
                    historicTableStoreName = historicTableStoreName,
                    historicIndexStoreName = historicIndexStoreName,
                    changeStoreName = changeStoreName,
                    keyScanRange = keyScanRange,
                    indexScan = scanType,
                )
            )
            return
        }

        val direction = scanType as? TableScan
        val scanDirection = direction?.direction ?: ASC
        val overallStartKey = when (scanDirection) {
            ASC -> request.startKey?.bytes ?: keyScanRange.ranges.firstOrNull()?.getAscendingStartKey(keyScanRange.startKey, keyScanRange.includeStart)
            DESC -> keyScanRange.ranges.firstOrNull()?.getDescendingStartKey(keyScanRange.startKey, keyScanRange.includeStart)
        }
        val overallStopKey = when (scanDirection) {
            ASC -> keyScanRange.ranges.lastOrNull()?.getDescendingStartKey()
            DESC -> keyScanRange.ranges.lastOrNull()?.getAscendingStartKey()
        }
        val ranges = if (scanDirection == ASC) keyScanRange.ranges else keyScanRange.ranges.asReversed()
        rangeLoop@ for (range in ranges) {
            val startKey = when (scanDirection) {
                ASC -> range.getAscendingStartKey(keyScanRange.startKey, keyScanRange.includeStart)
                DESC -> range.start.takeUnless { it.isEmpty() }
            }
            val endKey = when (scanDirection) {
                ASC -> range.end
                DESC -> range.getDescendingStartKey(keyScanRange.startKey, keyScanRange.includeStart)
            }
            byteStore.scanInBatches(
                storeName = keyStoreName,
                startKey = startKey,
                includeStart = if (scanDirection == ASC) range.startInclusive else range.startInclusive,
                endKey = endKey,
                includeEnd = if (scanDirection == ASC) range.endInclusive else true,
                reverse = scanDirection == DESC,
                targetLimit = UInt.MAX_VALUE,
            ) { keyBytes, rowValue ->
                if (scanDirection == ASC && range.keyOutOfRange(keyBytes)) return@scanInBatches false
                if (scanDirection == DESC && range.keyBeforeStart(keyBytes)) return@scanInBatches false
                if (!keyScanRange.keyWithinRanges(keyBytes, 0) || !keyScanRange.matchesPartials(keyBytes, 0)) return@scanInBatches true

                val toVersion = request.toVersion
                val record = if (toVersion == null) {
                    decodeCurrentSnapshotRecord(request.dataModel, keyBytes, rowValue, null, sensitiveFields::decryptValueIfNeeded)
                        ?: byteStore.readRecordDecrypted(request.dataModel, keyStoreName, tableStoreName, keyBytes, null)
                } else {
                    byteStore.readHistoricRecordDecrypted(request.dataModel, historicTableStoreName, keyBytes, toVersion, null)
                }
                    ?: return@scanInBatches true
                if (request.filterSoftDeleted && record.isDeleted) return@scanInBatches true
                if (!valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion)) return@scanInBatches true

                val versionedChanges = byteStore.readChangeLog(
                    dataModel = request.dataModel,
                    changeStoreName = changeStoreName,
                    historicTableStoreName = historicTableStoreName,
                    keyBytes = keyBytes,
                    fromVersion = request.fromVersion,
                    toVersion = request.toVersion,
                    maxVersions = request.maxVersions,
                    select = request.select,
                    decryptValue = sensitiveFields::decryptValueIfNeeded,
                )
                if (versionedChanges.isEmpty()) return@scanInBatches true

                changes += DataObjectVersionedChange(
                    key = request.dataModel.key(keyBytes),
                    changes = versionedChanges,
                )
                changes.size.toUInt() < request.limit
            }
            if (changes.size.toUInt() == request.limit) break@rangeLoop
        }

        storeAction.response.complete(
            ChangesResponse(
                dataModel = request.dataModel,
                changes = changes,
                dataFetchType = FetchByTableScan(
                    direction = scanDirection,
                    startKey = overallStartKey,
                    stopKey = overallStopKey,
                ),
            )
        )
    }

    private suspend fun <DM : IsRootDataModel> processIndexScanChanges(
        request: ScanChangesRequest<DM>,
        keyStoreName: String,
        tableStoreName: String,
        indexStoreName: String,
        historicTableStoreName: String,
        historicIndexStoreName: String,
        changeStoreName: String,
        keyScanRange: KeyScanRanges,
        indexScan: IndexScan,
    ): ChangesResponse<DM> {
        val changes = ArrayList<DataObjectVersionedChange<DM>>(request.limit.toInt().coerceAtLeast(4))
        val seenKeys = mutableSetOf<String>()
        val keySize = request.dataModel.Meta.keyByteSize
        val indexPrefix = createIndexKeyPrefix(indexScan.index.referenceStorageByteArray.bytes)
        val toVersion = request.toVersion
        val indexKeyScanRange = if (request.startKey == null) {
            keyScanRange
        } else {
            request.dataModel.createScanRange(request.where, null, request.includeStart)
        }
        val baseIndexRanges = indexScan.index.createScanRange(request.where, indexKeyScanRange)
        val startIndexValue = request.startKey?.let { startKey ->
            val record = if (toVersion != null) {
                byteStore.readHistoricRecordDecrypted(request.dataModel, historicTableStoreName, startKey.bytes, toVersion, null)
            } else {
                byteStore.readCurrentSnapshotDecrypted(request.dataModel, keyStoreName, startKey.bytes, null)
                    ?: byteStore.readRecordDecrypted(request.dataModel, keyStoreName, tableStoreName, startKey.bytes, null)
            }
            record?.let {
                val allIndexValues = indexScan.index.toStorageByteArraysForIndex(it.values, startKey.bytes)
                val matchedIndexValues = allIndexValues.filter { indexValue ->
                    resolveIndexValueSize(indexValue, keySize, indexScan.index.indexPartCount)?.let { valueSize ->
                        baseIndexRanges.matchesPartials(indexValue, length = valueSize, sourceEnd = indexValue.size) &&
                            baseIndexRanges.ranges.any { range ->
                                val rangeLength = indexRangeLength(baseIndexRanges, range, valueSize)
                                !range.keyBeforeStart(indexValue, length = rangeLength) &&
                                    !range.keyOutOfRange(indexValue, length = rangeLength)
                            }
                    } == true
                }
                when (indexScan.direction) {
                    ASC -> matchedIndexValues.minWithOrNull { a, b -> a compareTo b }
                    DESC -> matchedIndexValues.maxWithOrNull { a, b -> a compareTo b }
                }
            }
        }
        val indexRanges = baseIndexRanges

        val overallStartKey = when (indexScan.direction) {
            ASC -> startIndexValue?.let {
                indexRanges.ranges.first().getAscendingStartKey(it, keyScanRange.includeStart)
            } ?: indexRanges.ranges.first().start
            DESC -> indexRanges.ranges.first().getDescendingStartKey(startIndexValue, keyScanRange.includeStart)
        }
        val overallStopKey = when (indexScan.direction) {
            ASC -> indexRanges.ranges.last().getDescendingStartKey()
            DESC -> indexRanges.ranges.last().getAscendingStartKey()
        }

        val rangeList = if (indexScan.direction == ASC) indexRanges.ranges else indexRanges.ranges.asReversed()
        rangeLoop@ for (range in rangeList) {
            val startKey = if (indexScan.direction == ASC) {
                createIndexKeyWithPrefix(
                    indexPrefix,
                    startIndexValue?.let { range.getAscendingStartKey(it, keyScanRange.includeStart) } ?: range.start
                )
            } else {
                indexPrefix
            }
            val endKey = if (indexScan.direction == ASC) {
                when (val rangeEnd = range.getDescendingStartKey()) {
                    null -> createIndexRangeEnd(indexScan.index.referenceStorageByteArray.bytes)
                    else -> if (rangeEnd.isEmpty()) {
                        createIndexRangeEnd(indexScan.index.referenceStorageByteArray.bytes)
                    } else {
                        createIndexKeyWithPrefix(indexPrefix, rangeEnd)
                    }
                }
            } else {
                startIndexValue?.let {
                    keyPrefixUpperBound(createIndexKeyWithPrefix(indexPrefix, it))
                } ?: createIndexRangeEnd(indexScan.index.referenceStorageByteArray.bytes)
            }

            val historicRows = toVersion?.let {
                byteStore.readHistoricIndexRows(
                    storeName = historicIndexStoreName,
                    startKey = startKey,
                    endKey = endKey,
                    includeEnd = indexScan.direction == DESC && startIndexValue != null && request.includeStart,
                    toVersion = it,
                    reverse = indexScan.direction == DESC,
                )
            }

            suspend fun processIndexChangeRow(rowKey: ByteArray): Boolean {
                if (!rowKey.matchesRangePart(0, indexPrefix, sourceLength = rowKey.size, length = indexPrefix.size)) return true
                val valueAndKey = rowKey.copyOfRange(indexPrefix.size, rowKey.size)
                val valueSize = resolveIndexValueSize(valueAndKey, keySize, indexScan.index.indexPartCount) ?: return true
                if (startIndexValue != null) {
                    val comparison = valueAndKey compareTo startIndexValue
                    if (indexScan.direction == ASC && (comparison < 0 || (comparison == 0 && !request.includeStart))) return true
                    if (indexScan.direction == DESC && (comparison > 0 || (comparison == 0 && !request.includeStart))) return true
                }
                if (indexScan.direction == ASC) {
                    val rangeLength = indexRangeLength(indexRanges, range, valueSize)
                    if (range.keyOutOfRange(valueAndKey, length = rangeLength)) return false
                    if (!indexRanges.matchesPartials(valueAndKey, length = valueSize, sourceEnd = valueAndKey.size)) return true
                } else if (startIndexValue != null) {
                    val comparison = valueAndKey compareTo startIndexValue
                    if (comparison > 0 || (comparison == 0 && !keyScanRange.includeStart)) return true
                }

                val keyBytes = valueAndKey.copyOfRange(valueAndKey.size - keySize, valueAndKey.size)
                if (!indexKeyScanRange.keyWithinRanges(keyBytes, 0) || !indexKeyScanRange.matchesPartials(keyBytes, 0)) return true

                val dedupe = keyBytes.joinToString(",")
                if (!seenKeys.add(dedupe)) return true

                val record = if (toVersion != null) {
                    byteStore.readHistoricRecordDecrypted(request.dataModel, historicTableStoreName, keyBytes, toVersion, null)
                } else {
                    byteStore.readCurrentSnapshotDecrypted(request.dataModel, keyStoreName, keyBytes, null)
                        ?: byteStore.readRecordDecrypted(request.dataModel, keyStoreName, tableStoreName, keyBytes, null)
                } ?: return true
                if (request.filterSoftDeleted && record.isDeleted) return true
                if (!valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion, indexScan.index)) return true

                val versionedChanges = byteStore.readChangeLog(
                    dataModel = request.dataModel,
                    changeStoreName = changeStoreName,
                    historicTableStoreName = historicTableStoreName,
                    keyBytes = keyBytes,
                    fromVersion = request.fromVersion,
                    toVersion = request.toVersion,
                    maxVersions = request.maxVersions,
                    select = request.select,
                    decryptValue = sensitiveFields::decryptValueIfNeeded,
                ).ifEmpty {
                    record.toCreationChanges(request.fromVersion, request.toVersion, request.select)
                }
                if (versionedChanges.isEmpty()) return true

                changes += DataObjectVersionedChange(
                    key = request.dataModel.key(keyBytes),
                    sortingKey = Bytes(valueAndKey),
                    changes = versionedChanges,
                )
                return changes.size.toUInt() < request.limit
            }

            if (historicRows == null) {
                byteStore.scanInBatches(
                    storeName = indexStoreName,
                    startKey = startKey,
                    includeStart = true,
                    endKey = endKey,
                    includeEnd = indexScan.direction == DESC && startIndexValue != null && request.includeStart,
                    reverse = indexScan.direction == DESC,
                    targetLimit = UInt.MAX_VALUE,
                ) { rowKey, _ ->
                    processIndexChangeRow(rowKey)
                }
            } else {
                for ((rowKey, _) in historicRows) {
                    if (!processIndexChangeRow(rowKey)) break@rangeLoop
                }
            }
            if (changes.size.toUInt() == request.limit) break@rangeLoop
        }

        return ChangesResponse(
            dataModel = request.dataModel,
            changes = changes,
            dataFetchType = FetchByIndexScan(
                index = indexScan.index.referenceStorageByteArray.bytes,
                direction = indexScan.direction,
                startKey = overallStartKey,
                stopKey = overallStopKey,
            ),
        )
    }

    private suspend fun <DM : IsRootDataModel> processScanUpdatesRequest(
        storeAction: ScanUpdatesStoreAction<DM>,
    ) {
        val request = storeAction.request
        request.checkToVersion(keepAllVersions)
        if (!keepAllVersions && request.maxVersions > 1u) {
            throw RequestException("Cannot use maxVersions > 1 on a table which has keepAllVersions set to false")
        }

        val modelId = getDataModelId(request.dataModel)
        val keyStoreName = "k:$modelId"
        val tableStoreName = "t:$modelId"
        val historicTableStoreName = "ht:$modelId"
        val indexStoreName = "i:$modelId"
        val historicIndexStoreName = "hi:$modelId"
        val changeStoreName = "c:$modelId"
        val keyScanRange = request.dataModel.createScanRange(request.where, request.startKey?.bytes, request.includeStart)
        val indexKeyScanRange = if (request.startKey == null) {
            keyScanRange
        } else {
            request.dataModel.createScanRange(request.where, null, request.includeStart)
        }
        val scanType = request.dataModel.orderToScanType(request.order, keyScanRange.equalPairs)

        val scanRows = when {
            request.canUseUpdateHistoryIndex() && keepUpdateHistoryIndex -> collectUpdateHistoryScanUpdateRows(
                request = request,
                modelId = modelId,
                keyStoreName = keyStoreName,
                tableStoreName = tableStoreName,
                historicTableStoreName = historicTableStoreName,
                keyScanRange = keyScanRange,
            )
            scanType is IndexScan -> collectIndexScanUpdateRows(
                request = request,
                keyStoreName = keyStoreName,
                tableStoreName = tableStoreName,
                indexStoreName = indexStoreName,
                historicTableStoreName = historicTableStoreName,
                historicIndexStoreName = historicIndexStoreName,
                keyScanRange = indexKeyScanRange,
                indexScan = scanType,
            )
            else -> collectTableScanUpdateRows(
                request = request,
                keyStoreName = keyStoreName,
                tableStoreName = tableStoreName,
                historicTableStoreName = historicTableStoreName,
                keyScanRange = keyScanRange,
                tableScan = scanType as? TableScan ?: TableScan(ASC),
            )
        }

        val rows = scanRows.rows
        val highestVersion = minOf(request.toVersion ?: ULong.MAX_VALUE, rows.maxOfOrNull { it.lastVersion } ?: 0uL)
        val updates = mutableListOf<IsUpdateResponse<DM>>()
        val matchingKeys = rows.map { it.key }
        updates += OrderedKeysUpdate(matchingKeys, highestVersion, scanRows.sortingKeys)
        rows.forEachIndexed { index, record ->
            val versionedChanges = byteStore.readChangeLog(
                dataModel = request.dataModel,
                changeStoreName = changeStoreName,
                historicTableStoreName = historicTableStoreName,
                keyBytes = record.key.bytes,
                fromVersion = request.fromVersion,
                toVersion = request.toVersion,
                maxVersions = request.maxVersions,
                select = request.select,
                decryptValue = sensitiveFields::decryptValueIfNeeded,
            )
            for (versionedChange in versionedChanges) {
                if (versionedChange.changes.any { it is ObjectCreate } || request.orderedKeys?.contains(record.key) == false) {
                    updates += AdditionUpdate(
                        key = record.key,
                        version = versionedChange.version,
                        firstVersion = record.firstVersion,
                        insertionIndex = index,
                        isDeleted = record.isDeleted,
                        values = record.values,
                    )
                } else {
                    updates += ChangeUpdate(
                        key = record.key,
                        version = versionedChange.version,
                        index = index,
                        changes = versionedChange.changes,
                    )
                }
            }
        }
        request.orderedKeys?.let { orderedKeys ->
            val matchingSet = matchingKeys.toSet()
            val orderedSet = orderedKeys.toSet()
            for (removedKey in orderedKeys.filter { it !in matchingSet }) {
                val meta = byteStore.get(keyStoreName, removedKey.bytes)?.let(::decodeRecordMeta)
                updates += RemovalUpdate(
                    key = removedKey,
                    version = highestVersion,
                    reason = when {
                        meta == null -> HardDelete
                        meta.isDeleted -> SoftDelete
                        else -> NotInRange
                    },
                )
            }
            for (addedRecord in rows.filter { it.key !in orderedSet }) {
                if (updates.none { it is AdditionUpdate<*> && it.key == addedRecord.key }) {
                    updates += AdditionUpdate(
                        key = addedRecord.key,
                        version = highestVersion,
                        firstVersion = addedRecord.firstVersion,
                        insertionIndex = matchingKeys.indexOf(addedRecord.key),
                        isDeleted = addedRecord.isDeleted,
                        values = addedRecord.values,
                    )
                }
            }
        }

        storeAction.response.complete(
            UpdatesResponse(
                dataModel = request.dataModel,
                updates = updates,
                dataFetchType = scanRows.dataFetchType,
            )
        )
    }

    private suspend fun <DM : IsRootDataModel> collectTableScanUpdateRows(
        request: ScanUpdatesRequest<DM>,
        keyStoreName: String,
        tableStoreName: String,
        historicTableStoreName: String,
        keyScanRange: KeyScanRanges,
        tableScan: TableScan,
    ): ScanUpdateRows<DM> {
        val direction = tableScan.direction
        val rows = mutableListOf<ValuesWithMetaData<DM>>()
        val ranges = if (direction == ASC) keyScanRange.ranges else keyScanRange.ranges.asReversed()

        rangeLoop@ for (range in ranges) {
            byteStore.scanInBatches(
                storeName = keyStoreName,
                startKey = if (direction == ASC) range.getAscendingStartKey(keyScanRange.startKey, keyScanRange.includeStart) else range.start.takeUnless { it.isEmpty() },
                endKey = if (direction == ASC) range.end else range.getDescendingStartKey(keyScanRange.startKey, keyScanRange.includeStart),
                includeEnd = direction == DESC || range.endInclusive,
                reverse = direction == DESC,
                targetLimit = UInt.MAX_VALUE,
            ) { keyBytes, snapshotBytes ->
                if (direction == ASC && range.keyOutOfRange(keyBytes)) return@scanInBatches false
                if (direction == DESC && range.keyBeforeStart(keyBytes)) return@scanInBatches false
                if (!keyScanRange.keyWithinRanges(keyBytes, 0) || !keyScanRange.matchesPartials(keyBytes, 0)) return@scanInBatches true

                val toVersion = request.toVersion
                val record = if (toVersion == null) {
                    decodeCurrentSnapshotRecord(
                        request.dataModel,
                        keyBytes,
                        snapshotBytes,
                        request.select,
                        sensitiveFields::decryptValueIfNeeded,
                    )
                        ?: byteStore.readRecordDecrypted(request.dataModel, keyStoreName, tableStoreName, keyBytes, request.select)
                } else {
                    byteStore.readHistoricRecordDecrypted(request.dataModel, historicTableStoreName, keyBytes, toVersion, request.select)
                }
                    ?: return@scanInBatches true
                if (request.filterSoftDeleted && record.isDeleted) return@scanInBatches true
                if (!valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion)) return@scanInBatches true

                rows += record
                rows.size.toUInt() < request.limit
            }
            if (rows.size.toUInt() == request.limit) break@rangeLoop
        }

        return ScanUpdateRows(
            rows = rows,
            sortingKeys = null,
            dataFetchType = FetchByTableScan(direction, request.startKey?.bytes, null),
        )
    }

    private suspend fun <DM : IsRootDataModel> collectIndexScanUpdateRows(
        request: ScanUpdatesRequest<DM>,
        keyStoreName: String,
        tableStoreName: String,
        indexStoreName: String,
        historicTableStoreName: String,
        historicIndexStoreName: String,
        keyScanRange: KeyScanRanges,
        indexScan: IndexScan,
    ): ScanUpdateRows<DM> {
        val rows = ArrayList<ValuesWithMetaData<DM>>(request.limit.toInt().coerceAtLeast(4))
        val sortingKeys = ArrayList<Bytes>(request.limit.toInt().coerceAtLeast(4))
        val seenKeys = mutableSetOf<String>()
        val keySize = request.dataModel.Meta.keyByteSize
        val indexPrefix = createIndexKeyPrefix(indexScan.index.referenceStorageByteArray.bytes)
        val indexKeyScanRange = if (request.startKey == null) {
            keyScanRange
        } else {
            request.dataModel.createScanRange(request.where, null, request.includeStart)
        }
        val baseIndexRanges = indexScan.index.createScanRange(request.where, indexKeyScanRange)
        val startIndexValue = request.startKey?.let { startKey ->
            val toVersion = request.toVersion
            val record = if (toVersion != null) {
                byteStore.readHistoricRecordDecrypted(request.dataModel, historicTableStoreName, startKey.bytes, toVersion, null)
            } else {
                byteStore.readCurrentSnapshotDecrypted(request.dataModel, keyStoreName, startKey.bytes, null)
                    ?: byteStore.readRecordDecrypted(request.dataModel, keyStoreName, tableStoreName, startKey.bytes, null)
            }
            record?.let {
                val allIndexValues = indexScan.index.toStorageByteArraysForIndex(it.values, startKey.bytes)
                val matchedIndexValues = allIndexValues.filter { indexValue ->
                    resolveIndexValueSize(indexValue, keySize, indexScan.index.indexPartCount)?.let { valueSize ->
                        baseIndexRanges.matchesPartials(indexValue, length = valueSize, sourceEnd = indexValue.size) &&
                            baseIndexRanges.ranges.any { range ->
                                val rangeLength = indexRangeLength(baseIndexRanges, range, valueSize)
                                !range.keyBeforeStart(indexValue, length = rangeLength) &&
                                    !range.keyOutOfRange(indexValue, length = rangeLength)
                            }
                    } == true
                }
                when (indexScan.direction) {
                    ASC -> matchedIndexValues.minWithOrNull { a, b -> a compareTo b }
                    DESC -> matchedIndexValues.maxWithOrNull { a, b -> a compareTo b }
                }
            }
        }
        val indexRanges = baseIndexRanges
        val overallStartKey = when (indexScan.direction) {
            ASC -> startIndexValue?.let {
                indexRanges.ranges.first().getAscendingStartKey(it, keyScanRange.includeStart)
            } ?: indexRanges.ranges.first().start
            DESC -> indexRanges.ranges.first().getDescendingStartKey(startIndexValue, keyScanRange.includeStart)
        }
        val overallStopKey = when (indexScan.direction) {
            ASC -> indexRanges.ranges.last().getDescendingStartKey()
            DESC -> indexRanges.ranges.last().getAscendingStartKey()
        }

        val rangeList = if (indexScan.direction == ASC) indexRanges.ranges else indexRanges.ranges.asReversed()
        rangeLoop@ for (range in rangeList) {
            val startKey = when (indexScan.direction) {
                ASC -> createIndexKeyWithPrefix(
                    indexPrefix,
                    startIndexValue?.let { range.getAscendingStartKey(it, keyScanRange.includeStart) } ?: range.start
                )
                DESC -> indexPrefix
            }
            val endKey = when (indexScan.direction) {
                ASC -> when (val rangeEnd = range.getDescendingStartKey()) {
                    null -> createIndexRangeEnd(indexScan.index.referenceStorageByteArray.bytes)
                    else -> if (rangeEnd.isEmpty()) {
                        createIndexRangeEnd(indexScan.index.referenceStorageByteArray.bytes)
                    } else {
                        createIndexKeyWithPrefix(indexPrefix, rangeEnd)
                    }
                }
                DESC -> createIndexRangeEnd(indexScan.index.referenceStorageByteArray.bytes)
            }
            val historicRows = request.toVersion?.let { toVersion ->
                byteStore.readHistoricIndexRows(
                    storeName = historicIndexStoreName,
                    startKey = startKey,
                    endKey = endKey,
                    includeEnd = indexScan.direction == DESC && startIndexValue != null && request.includeStart,
                    toVersion = toVersion,
                    reverse = indexScan.direction == DESC,
                )
            }

            suspend fun processIndexRow(rowKey: ByteArray): Boolean {
                if (!rowKey.matchesRangePart(0, indexPrefix, sourceLength = rowKey.size, length = indexPrefix.size)) return true
                val valueAndKey = rowKey.copyOfRange(indexPrefix.size, rowKey.size)
                val valueSize = resolveIndexValueSize(valueAndKey, keySize, indexScan.index.indexPartCount) ?: return true
                val rangeLength = indexRangeLength(indexRanges, range, valueSize)

                if (indexScan.direction == DESC && startIndexValue != null) {
                    val startComparison = valueAndKey compareTo startIndexValue
                    if (startComparison > 0 || (startComparison == 0 && !keyScanRange.includeStart)) return true
                }
                if (indexScan.direction == ASC && range.keyBeforeStart(valueAndKey, length = rangeLength)) return true
                if (indexScan.direction == ASC && range.keyOutOfRange(valueAndKey, length = rangeLength)) return false
                if (indexScan.direction == DESC && range.keyOutOfRange(valueAndKey, length = rangeLength)) return true
                if (indexScan.direction == DESC && request.startKey == null && range.keyBeforeStart(valueAndKey, length = rangeLength)) return false
                if (!indexRanges.matchesPartials(valueAndKey, length = valueSize, sourceEnd = valueAndKey.size)) return true

                val keyBytes = valueAndKey.copyOfRange(valueAndKey.size - keySize, valueAndKey.size)
                if (!keyScanRange.keyWithinRanges(keyBytes, 0) || !keyScanRange.matchesPartials(keyBytes, 0)) return true

                val dedupe = keyBytes.joinToString(",")
                if (!seenKeys.add(dedupe)) return true

                val toVersion = request.toVersion
                val record = if (toVersion != null) {
                    byteStore.readHistoricRecordDecrypted(request.dataModel, historicTableStoreName, keyBytes, toVersion, request.select)
                } else {
                    byteStore.readCurrentSnapshotDecrypted(request.dataModel, keyStoreName, keyBytes, request.select)
                        ?: byteStore.readRecordDecrypted(request.dataModel, keyStoreName, tableStoreName, keyBytes, request.select)
                }
                    ?: return true
                if (request.filterSoftDeleted && record.isDeleted) return true
                if (!valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion, indexScan.index)) return true

                rows += record
                sortingKeys += Bytes(valueAndKey)
                return true
            }

            if (historicRows == null) {
                byteStore.scanInBatches(
                    storeName = indexStoreName,
                    startKey = startKey,
                    includeStart = true,
                    endKey = endKey,
                    includeEnd = indexScan.direction == DESC && startIndexValue != null && request.includeStart,
                    reverse = indexScan.direction == DESC,
                    targetLimit = UInt.MAX_VALUE,
                ) { rowKey, _ ->
                    processIndexRow(rowKey)
                }
            } else {
                for ((rowKey, _) in historicRows) {
                    if (!processIndexRow(rowKey)) break
                }
            }
        }
        val ordered = rows.zip(sortingKeys).take(request.limit.toInt())

        return ScanUpdateRows(
            rows = ordered.map { it.first },
            sortingKeys = ordered.map { it.second },
            dataFetchType = FetchByIndexScan(
                index = indexScan.index.referenceStorageByteArray.bytes,
                direction = indexScan.direction,
                startKey = overallStartKey,
                stopKey = overallStopKey,
            ),
        )
    }

    private suspend fun <DM : IsRootDataModel> collectUpdateHistoryScanUpdateRows(
        request: ScanUpdatesRequest<DM>,
        modelId: UInt,
        keyStoreName: String,
        tableStoreName: String,
        historicTableStoreName: String,
        keyScanRange: KeyScanRanges,
    ): ScanUpdateRows<DM> {
        val rows = mutableListOf<ValuesWithMetaData<DM>>()
        byteStore.scanInBatches(storeName = keyStoreName, targetLimit = UInt.MAX_VALUE) { keyBytes, snapshotBytes ->
            if (!keyScanRange.keyWithinRanges(keyBytes, 0) || !keyScanRange.matchesPartials(keyBytes, 0)) return@scanInBatches true
            val toVersion = request.toVersion
            val record = if (toVersion == null) {
                decodeCurrentSnapshotRecord(
                    request.dataModel,
                    keyBytes,
                    snapshotBytes,
                    request.select,
                    sensitiveFields::decryptValueIfNeeded,
                ) ?: byteStore.readRecordDecrypted(request.dataModel, keyStoreName, tableStoreName, keyBytes, request.select)
            } else {
                byteStore.readHistoricRecordDecrypted(request.dataModel, historicTableStoreName, keyBytes, toVersion, request.select)
            } ?: return@scanInBatches true
            if (request.filterSoftDeleted && record.isDeleted) return@scanInBatches true
            if (!valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion)) return@scanInBatches true
            rows += record
            true
        }
        rows.sortWith { first, second ->
            val versionComparison = second.lastVersion.compareTo(first.lastVersion)
            if (versionComparison != 0) versionComparison else second.key.bytes.compareTo(first.key.bytes)
        }
        if (rows.size.toUInt() > request.limit) {
            rows.subList(request.limit.toInt(), rows.size).clear()
        }
        return ScanUpdateRows(
            rows = rows,
            sortingKeys = null,
            dataFetchType = FetchByUpdateHistoryIndex(),
        )
    }

    private suspend fun <DM : IsRootDataModel> processScanUpdateHistoryRequest(
        storeAction: ScanUpdateHistoryStoreAction<DM>,
    ) {
        val request = storeAction.request
        if (!(keepAllVersions && keepUpdateHistoryIndex)) {
            throw RequestException("Scan update history requires keepAllVersions and keepUpdateHistoryIndex")
        }

        val modelId = getDataModelId(request.dataModel)
        val historicTableStoreName = "ht:$modelId"
        val updates = mutableListOf<IsUpdateResponse<DM>>()

        if (request.limit == 0u) {
            storeAction.response.complete(
                UpdatesResponse(
                    dataModel = request.dataModel,
                    updates = emptyList(),
                    dataFetchType = FetchByUpdateHistoryIndex(),
                )
            )
            return
        }

        byteStore.scanInBatches(
            storeName = "uh:$modelId",
            targetLimit = UInt.MAX_VALUE,
        ) { rowKey, rowValue ->
            val version = rowKey.readInvertedVersion()
            val toVersion = request.toVersion
            if (version < request.fromVersion) return@scanInBatches false
            if (toVersion != null && version > toVersion) return@scanInBatches true

            val keyBytes = rowKey.copyOfRange(rowKey.size - request.dataModel.Meta.keyByteSize, rowKey.size)
            if (rowValue.size == 1 && rowValue[0] == 1.toByte()) {
                if (request.where == null) {
                    updates += RemovalUpdate(
                        key = request.dataModel.key(keyBytes),
                        version = version,
                        reason = HardDelete,
                    )
                }
                return@scanInBatches updates.size.toUInt() < request.limit
            }

            if (rowValue.isUnserializableChangeLogMarker()) {
                val historicRecord = byteStore.readHistoricRecordDecrypted(
                    dataModel = request.dataModel,
                    storeName = historicTableStoreName,
                    keyBytes = keyBytes,
                    toVersion = version,
                    select = request.select,
                )
                if (historicRecord?.firstVersion != version) return@scanInBatches true

                if (request.filterSoftDeleted && historicRecord.isDeleted) return@scanInBatches true
                if (!valuesMatchFilter(request.dataModel, historicRecord.values, request.where, version)) return@scanInBatches true

                val changes = (listOf(ObjectCreate) + historicRecord.values.toChanges().toList()).mapNotNull { change ->
                    request.select?.let { change.filterWithSelect(it) } ?: change
                }
                if (changes.isEmpty()) return@scanInBatches true

                updates += AdditionUpdate(
                    key = historicRecord.key,
                    version = version,
                    firstVersion = historicRecord.firstVersion,
                    insertionIndex = updates.size,
                    isDeleted = historicRecord.isDeleted,
                    values = historicRecord.values,
                )
                return@scanInBatches updates.size.toUInt() < request.limit
            }

            val decoded = decodeVersionedChange(request.dataModel, rowValue)
            val historicRecord = byteStore.readHistoricRecordDecrypted(
                dataModel = request.dataModel,
                storeName = historicTableStoreName,
                keyBytes = decoded.key.bytes,
                toVersion = version,
                select = request.select,
            ) ?: return@scanInBatches true
            if (request.filterSoftDeleted && historicRecord.isDeleted) return@scanInBatches true
            if (!valuesMatchFilter(request.dataModel, historicRecord.values, request.where, version)) return@scanInBatches true

            val versionedChanges = decoded.changes.firstOrNull() ?: return@scanInBatches true
            val filteredChanges = versionedChanges.changes.mapNotNull { change ->
                request.select?.let { change.filterWithSelect(it) } ?: change
            }
            if (filteredChanges.isEmpty()) return@scanInBatches true

            if (filteredChanges.any { it is ObjectCreate }) {
                updates += AdditionUpdate(
                    key = decoded.key,
                    version = versionedChanges.version,
                    firstVersion = historicRecord.firstVersion,
                    insertionIndex = updates.size,
                    isDeleted = historicRecord.isDeleted,
                    values = historicRecord.values,
                )
            } else {
                updates += ChangeUpdate(
                    key = decoded.key,
                    version = versionedChanges.version,
                    index = updates.size,
                    changes = filteredChanges,
                )
            }
            updates.size.toUInt() < request.limit
        }

        storeAction.response.complete(
            UpdatesResponse(
                dataModel = request.dataModel,
                updates = updates,
                dataFetchType = FetchByUpdateHistoryIndex(),
            )
        )
    }

    private suspend fun <DM : IsRootDataModel> processScanRequest(
        storeAction: ScanStoreAction<DM>,
    ) {
        val request = storeAction.request
        request.checkToVersion(keepAllVersions)

        val keyScanRange = request.dataModel.createScanRange(request.where, request.startKey?.bytes, request.includeStart)
        if (keyScanRange.ranges.isEmpty()) {
            storeAction.response.complete(
                ValuesResponse(
                    dataModel = request.dataModel,
                    values = emptyList(),
                    dataFetchType = FetchByTableScan(ASC, null, null),
                )
            )
            return
        }

        val modelId = getDataModelId(request.dataModel)
        val keyStoreName = "k:$modelId"
        val tableStoreName = "t:$modelId"
        val uniqueStoreName = "u:$modelId"
        val historicTableStoreName = "ht:$modelId"
        val historicUniqueStoreName = "hu:$modelId"
        val aggregator = request.aggregations?.let(::Aggregator)
        val values = ArrayList<ValuesWithMetaData<DM>>(request.limit.toInt().coerceAtLeast(4))

        if (keyScanRange.isSingleKey()) {
            val keyBytes = keyScanRange.ranges.first().start
            val toVersion = request.toVersion
            val record = if (toVersion != null) {
                byteStore.readHistoricRecordDecrypted(request.dataModel, historicTableStoreName, keyBytes, toVersion, request.select)
            } else {
                byteStore.readCurrentSnapshotDecrypted(request.dataModel, keyStoreName, keyBytes, request.select)
                    ?: byteStore.readRecordDecrypted(request.dataModel, keyStoreName, tableStoreName, keyBytes, request.select)
            }
            if (
                record != null &&
                (!request.filterSoftDeleted || !record.isDeleted) &&
                valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion)
            ) {
                values += record
                aggregator?.aggregate { reference -> record.values[reference] }
            }

            storeAction.response.complete(
                ValuesResponse(
                    dataModel = request.dataModel,
                    values = values,
                    aggregations = aggregator?.toResponse(),
                    dataFetchType = FetchByKey,
                )
            )
            return
        }

        keyScanRange.uniques?.firstOrNull()?.let { uniqueToMatch ->
            @Suppress("UNCHECKED_CAST")
            val uniqueValue = Value.castDefinition(uniqueToMatch.definition).toStorageBytes(
                uniqueToMatch.value as Comparable<Any>,
                TypeIndicator.NoTypeIndicator.byte
            )
            val mappedUniqueValue = sensitiveFields.mapUniqueValueBytes(modelId, uniqueToMatch.reference, uniqueValue)
            val uniqueKey = createUniqueRowKey(uniqueToMatch.reference, mappedUniqueValue)
            val keyBytes = request.toVersion?.let { toVersion ->
                byteStore.readHistoricUniqueKey(historicUniqueStoreName, uniqueKey, toVersion)
            } ?: byteStore.get(uniqueStoreName, uniqueKey)
            if (keyBytes != null) {
                val toVersion = request.toVersion
                val record = if (toVersion != null) {
                    byteStore.readHistoricRecordDecrypted(request.dataModel, historicTableStoreName, keyBytes, toVersion, request.select)
                } else {
                    byteStore.readCurrentSnapshotDecrypted(request.dataModel, keyStoreName, keyBytes, request.select)
                        ?: byteStore.readRecordDecrypted(request.dataModel, keyStoreName, tableStoreName, keyBytes, request.select)
                }
                if (
                    record != null &&
                    (!request.filterSoftDeleted || !record.isDeleted) &&
                    valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion)
                ) {
                    values += record
                    aggregator?.aggregate { reference -> record.values[reference] }
                }
            }

            storeAction.response.complete(
                ValuesResponse(
                    dataModel = request.dataModel,
                    values = values,
                    aggregations = aggregator?.toResponse(),
                    dataFetchType = FetchByUniqueKey(uniqueToMatch.reference),
                )
            )
            return
        }

        val requestedScanType = request.dataModel.orderToScanType(request.order, keyScanRange.equalPairs)
        val scanType = if (requestedScanType is TableScan) {
            request.dataModel.optimizeTableScan(
                requestedScanType,
                keyScanRange,
                filter = request.where,
                allowTableScan = request.allowTableScan
            )
        } else {
            requestedScanType
        }

        if (scanType is IndexScan) {
            storeAction.response.complete(
                processIndexScan(
                    request = request,
                    modelId = modelId,
                    keyStoreName = keyStoreName,
                    tableStoreName = tableStoreName,
                    indexStoreName = "i:$modelId",
                    historicTableStoreName = historicTableStoreName,
                    historicIndexStoreName = "hi:$modelId",
                    keyScanRange = keyScanRange,
                    indexScan = scanType,
                    aggregator = aggregator,
                )
            )
            return
        }

        require(scanType is TableScan)

        val overallStartKey: ByteArray?
        val overallEndKey: ByteArray?

        when (scanType.direction) {
            ASC -> {
                overallStartKey = keyScanRange.ranges.first().getAscendingStartKey(keyScanRange.startKey, keyScanRange.includeStart)
                overallEndKey = keyScanRange.ranges.last().getDescendingStartKey()

                rangeLoop@ for (range in keyScanRange.ranges) {
                    val startKey = range.getAscendingStartKey(keyScanRange.startKey, keyScanRange.includeStart)
                    byteStore.scanInBatches(
                        storeName = keyStoreName,
                        startKey = startKey,
                        endKey = range.end,
                        includeEnd = range.endInclusive,
                        targetLimit = UInt.MAX_VALUE,
                    ) { keyBytes, snapshotBytes ->
                        if (range.keyOutOfRange(keyBytes)) return@scanInBatches false
                        if (!keyScanRange.keyWithinRanges(keyBytes, 0) || !keyScanRange.matchesPartials(keyBytes, 0)) return@scanInBatches true

                        val toVersion = request.toVersion
                        val record = if (toVersion != null) {
                            byteStore.readHistoricRecordDecrypted(request.dataModel, historicTableStoreName, keyBytes, toVersion, request.select)
                        } else {
                            decodeCurrentSnapshotRecord(
                                request.dataModel,
                                keyBytes,
                                snapshotBytes,
                                request.select,
                                sensitiveFields::decryptValueIfNeeded,
                            )
                                ?: byteStore.readRecordDecrypted(request.dataModel, keyStoreName, tableStoreName, keyBytes, request.select)
                        }
                            ?: return@scanInBatches true
                        if (request.filterSoftDeleted && record.isDeleted) return@scanInBatches true
                        if (!valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion)) return@scanInBatches true

                        values += record
                        aggregator?.aggregate { reference -> record.values[reference] }
                        values.size.toUInt() < request.limit
                    }
                    if (values.size.toUInt() == request.limit) break@rangeLoop
                }
            }
            DESC -> {
                overallStartKey = keyScanRange.ranges.first().getDescendingStartKey(keyScanRange.startKey, keyScanRange.includeStart)
                overallEndKey = keyScanRange.ranges.last().getAscendingStartKey()

                rangeLoop@ for (range in keyScanRange.ranges.asReversed()) {
                    val upperKey = range.getDescendingStartKey(keyScanRange.startKey, keyScanRange.includeStart)
                    byteStore.scanInBatches(
                        storeName = keyStoreName,
                        startKey = range.start.takeUnless { it.isEmpty() },
                        includeStart = range.startInclusive,
                        endKey = upperKey,
                        reverse = true,
                        targetLimit = UInt.MAX_VALUE,
                    ) { keyBytes, snapshotBytes ->
                        if (range.keyBeforeStart(keyBytes)) return@scanInBatches false
                        if (!keyScanRange.keyWithinRanges(keyBytes, 0) || !keyScanRange.matchesPartials(keyBytes, 0)) return@scanInBatches true

                        val toVersion = request.toVersion
                        val record = if (toVersion != null) {
                            byteStore.readHistoricRecordDecrypted(request.dataModel, historicTableStoreName, keyBytes, toVersion, request.select)
                        } else {
                            decodeCurrentSnapshotRecord(
                                request.dataModel,
                                keyBytes,
                                snapshotBytes,
                                request.select,
                                sensitiveFields::decryptValueIfNeeded,
                            )
                                ?: byteStore.readRecordDecrypted(request.dataModel, keyStoreName, tableStoreName, keyBytes, request.select)
                        }
                            ?: return@scanInBatches true
                        if (request.filterSoftDeleted && record.isDeleted) return@scanInBatches true
                        if (!valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion)) return@scanInBatches true

                        values += record
                        aggregator?.aggregate { reference -> record.values[reference] }
                        values.size.toUInt() < request.limit
                    }
                    if (values.size.toUInt() == request.limit) break@rangeLoop
                }
            }
        }

        storeAction.response.complete(
            ValuesResponse(
                dataModel = request.dataModel,
                values = values,
                aggregations = aggregator?.toResponse(),
                dataFetchType = FetchByTableScan(
                    direction = scanType.direction,
                    startKey = overallStartKey,
                    stopKey = overallEndKey,
                )
            )
        )
    }

    private suspend fun scanTableRows(
        tableStoreName: String,
        keyBytes: ByteArray,
    ): List<Pair<ByteArray, ByteArray>> {
        val rowKeyPrefix = createObjectRowKeyPrefix(keyBytes)
        return byteStore.scan(
            storeName = tableStoreName,
            startKey = rowKeyPrefix,
            endKey = keyPrefixUpperBound(rowKeyPrefix),
            includeEnd = false,
        ).filter { (rowKey, _) ->
            rowKey.matchesRangePart(0, rowKeyPrefix, sourceLength = rowKey.size, length = rowKeyPrefix.size)
        }
    }

    private suspend fun <DM : IsRootDataModel> processIndexScan(
        request: ScanRequest<DM>,
        modelId: UInt,
        keyStoreName: String,
        tableStoreName: String,
        indexStoreName: String,
        historicTableStoreName: String,
        historicIndexStoreName: String,
        keyScanRange: KeyScanRanges,
        indexScan: IndexScan,
        aggregator: Aggregator?,
    ): ValuesResponse<DM> {
        val values = ArrayList<ValuesWithMetaData<DM>>(request.limit.toInt().coerceAtLeast(4))
        val seenKeys = mutableSetOf<String>()
        val keySize = request.dataModel.Meta.keyByteSize
        val indexPrefix = createIndexKeyPrefix(indexScan.index.referenceStorageByteArray.bytes)
        val indexKeyScanRange = if (request.startKey == null) {
            keyScanRange
        } else {
            request.dataModel.createScanRange(request.where, null, request.includeStart)
        }
        val baseIndexRanges = indexScan.index.createScanRange(request.where, indexKeyScanRange)
        val startIndexValue = request.startKey?.let { startKey ->
            val toVersion = request.toVersion
            val record = if (toVersion != null) {
                byteStore.readHistoricRecordDecrypted(request.dataModel, historicTableStoreName, startKey.bytes, toVersion, null)
            } else {
                byteStore.readCurrentSnapshotDecrypted(request.dataModel, keyStoreName, startKey.bytes, null)
                    ?: byteStore.readRecordDecrypted(request.dataModel, keyStoreName, tableStoreName, startKey.bytes, null)
            }
            record?.let {
                val allIndexValues = indexScan.index.toStorageByteArraysForIndex(it.values, startKey.bytes)
                val matchedIndexValues = allIndexValues.filter { indexValue ->
                    resolveIndexValueSize(indexValue, keySize, indexScan.index.indexPartCount)?.let { valueSize ->
                        baseIndexRanges.matchesPartials(indexValue, length = valueSize, sourceEnd = indexValue.size) &&
                            baseIndexRanges.ranges.any { range ->
                                val rangeLength = indexRangeLength(baseIndexRanges, range, valueSize)
                                !range.keyBeforeStart(indexValue, length = rangeLength) &&
                                    !range.keyOutOfRange(indexValue, length = rangeLength)
                            }
                    } == true
                }
                when (indexScan.direction) {
                    ASC -> matchedIndexValues.minWithOrNull { a, b -> a compareTo b }
                    DESC -> matchedIndexValues.maxWithOrNull { a, b -> a compareTo b }
                }
            }
        }
        val indexRanges = baseIndexRanges

        val overallStartKey = when (indexScan.direction) {
            ASC -> startIndexValue?.let {
                indexRanges.ranges.first().getAscendingStartKey(it, keyScanRange.includeStart)
            } ?: indexRanges.ranges.first().start
            DESC -> indexRanges.ranges.first().getDescendingStartKey(startIndexValue, keyScanRange.includeStart)
        }
        val overallStopKey = when (indexScan.direction) {
            ASC -> indexRanges.ranges.last().getDescendingStartKey()
            DESC -> indexRanges.ranges.last().getAscendingStartKey()
        }

        val rangeList = if (indexScan.direction == ASC) indexRanges.ranges else indexRanges.ranges.asReversed()
        for (range in rangeList) {
            val startKey = when (indexScan.direction) {
                ASC -> createIndexKeyWithPrefix(
                    indexPrefix,
                    startIndexValue?.let { range.getAscendingStartKey(it, keyScanRange.includeStart) } ?: range.start
                )
                DESC -> createIndexKeyWithPrefix(indexPrefix, range.getAscendingStartKey())
            }
            val endKey = when (indexScan.direction) {
                ASC -> when (val rangeEnd = range.getDescendingStartKey()) {
                    null -> createIndexRangeEnd(indexScan.index.referenceStorageByteArray.bytes)
                    else -> if (rangeEnd.isEmpty()) {
                        createIndexRangeEnd(indexScan.index.referenceStorageByteArray.bytes)
                    } else {
                        createIndexKeyWithPrefix(indexPrefix, rangeEnd)
                    }
                }
                DESC -> when (val descendingStart = range.getDescendingStartKey(startIndexValue, keyScanRange.includeStart)) {
                    null -> createIndexRangeEnd(indexScan.index.referenceStorageByteArray.bytes)
                    else -> if (descendingStart.isEmpty()) {
                        createIndexRangeEnd(indexScan.index.referenceStorageByteArray.bytes)
                    } else {
                        createIndexKeyWithPrefix(indexPrefix, descendingStart)
                    }
                }
            }

            val rows = request.toVersion?.let { toVersion ->
                byteStore.readHistoricIndexRows(
                    storeName = historicIndexStoreName,
                    startKey = startKey,
                    endKey = endKey,
                    includeEnd = indexScan.direction == DESC && startIndexValue != null && request.includeStart,
                    toVersion = toVersion,
                    reverse = indexScan.direction == DESC,
                )
            }

            suspend fun processIndexRow(rowKey: ByteArray): Boolean {
                if (!rowKey.matchesRangePart(0, indexPrefix, sourceLength = rowKey.size, length = indexPrefix.size)) return true
                val valueAndKey = rowKey.copyOfRange(indexPrefix.size, rowKey.size)
                val valueSize = resolveIndexValueSize(valueAndKey, keySize, indexScan.index.indexPartCount) ?: return true
                if (startIndexValue != null) {
                    val comparison = valueAndKey compareTo startIndexValue
                    if (indexScan.direction == ASC && (comparison < 0 || (comparison == 0 && !request.includeStart))) return true
                    if (indexScan.direction == DESC && (comparison > 0 || (comparison == 0 && !request.includeStart))) return true
                }
                val rangeLength = indexRangeLength(indexRanges, range, valueSize)

                if (indexScan.direction == ASC && range.keyOutOfRange(valueAndKey, length = rangeLength)) return false
                if (indexScan.direction == DESC && startIndexValue == null && range.keyBeforeStart(valueAndKey, length = rangeLength)) return false
                if (!indexRanges.matchesPartials(valueAndKey, length = valueSize, sourceEnd = valueAndKey.size)) return true

                val keyBytes = valueAndKey.copyOfRange(valueAndKey.size - keySize, valueAndKey.size)
                if (!indexKeyScanRange.keyWithinRanges(keyBytes, 0) || !indexKeyScanRange.matchesPartials(keyBytes, 0)) return true

                val dedupe = keyBytes.joinToString(",")
                if (!seenKeys.add(dedupe)) return true

                val toVersion = request.toVersion
                val record = if (toVersion != null) {
                    byteStore.readHistoricRecordDecrypted(request.dataModel, historicTableStoreName, keyBytes, toVersion, request.select)
                } else {
                    byteStore.readCurrentSnapshotDecrypted(request.dataModel, keyStoreName, keyBytes, request.select)
                        ?: byteStore.readRecordDecrypted(request.dataModel, keyStoreName, tableStoreName, keyBytes, request.select)
                }
                    ?: return true
                if (request.filterSoftDeleted && record.isDeleted) return true
                if (!valuesMatchFilter(request.dataModel, record.values, request.where, request.toVersion, indexScan.index)) return true

                values += record
                aggregator?.aggregate { reference -> record.values[reference] }
                return values.size.toUInt() < request.limit
            }

            if (rows == null) {
                byteStore.scanInBatches(
                    storeName = indexStoreName,
                    startKey = startKey,
                    includeStart = true,
                    endKey = endKey,
                    includeEnd = indexScan.direction == DESC && startIndexValue != null && request.includeStart,
                    reverse = indexScan.direction == DESC,
                    targetLimit = UInt.MAX_VALUE,
                ) { rowKey, _ ->
                    processIndexRow(rowKey)
                }
            } else {
                for ((rowKey, _) in rows) {
                    if (!processIndexRow(rowKey)) break
                }
            }
            if (values.size.toUInt() == request.limit) {
                return ValuesResponse(
                    dataModel = request.dataModel,
                    values = values,
                    aggregations = aggregator?.toResponse(),
                    dataFetchType = FetchByIndexScan(
                        index = indexScan.index.referenceStorageByteArray.bytes,
                        direction = indexScan.direction,
                        startKey = overallStartKey,
                        stopKey = overallStopKey,
                    )
                )
            }
        }

        return ValuesResponse(
            dataModel = request.dataModel,
            values = values,
            aggregations = aggregator?.toResponse(),
            dataFetchType = FetchByIndexScan(
                index = indexScan.index.referenceStorageByteArray.bytes,
                direction = indexScan.direction,
                startKey = overallStartKey,
                stopKey = overallStopKey,
            )
        )
    }

    private suspend fun <DM : IsRootDataModel> collectCurrentIndexRows(
        dataModel: DM,
        keyBytes: ByteArray,
    ): List<ByteArray> {
        val values = byteStore.readCurrentValuesDecrypted(dataModel, "t:${getDataModelId(dataModel)}", keyBytes, null)
            ?: return emptyList()
        val rows = mutableListOf<ByteArray>()
        dataModel.Meta.indexes?.forEach { index ->
            index.toStorageByteArraysForIndex(values, keyBytes).forEach { valueAndKey ->
                rows += createIndexRowKey(index.referenceStorageByteArray.bytes, valueAndKey)
            }
        }
        return rows
    }

    private suspend fun <DM : IsRootDataModel> collectCurrentUniqueRows(
        dataModel: DM,
        modelId: UInt,
        tableStoreName: String,
        keyBytes: ByteArray,
    ): List<Triple<ByteArray, ByteArray, ByteArray>> {
        val rows = mutableListOf<Triple<ByteArray, ByteArray, ByteArray>>()
        for ((rowKey, rowValue) in scanTableRows(tableStoreName, keyBytes)) {
            val qualifier = tableQualifierFromRowKey(rowKey, keyBytes)
            var index = 0
            val reference = dataModel.getPropertyReferenceByStorageBytes(
                length = qualifier.size,
                reader = { qualifier[index++] }
            )
            val definition = reference.comparablePropertyDefinition
            if (definition is IsComparableDefinition<*, *> && definition.unique) {
                val encodedValue = sensitiveFields.decryptValueIfNeeded(rowValue)
                val uniqueValue = sensitiveFields.mapUniqueValueBytes(modelId, qualifier, encodedValue)
                rows += Triple(createUniqueRowKey(qualifier, uniqueValue), keyBytes, qualifier)
            }
        }
        return rows
    }

    private suspend fun <DM : IsRootDataModel> validateUniqueRows(
        dataModel: DM,
        keyBytes: ByteArray,
        uniqueStoreName: String,
        uniqueRows: List<Triple<ByteArray, ByteArray, ByteArray>>,
    ) {
        for ((uniqueKey, _, qualifier) in uniqueRows) {
            val existingKey = byteStore.get(uniqueStoreName, uniqueKey) ?: continue
            if (!existingKey.contentEquals(keyBytes)) {
                throw UniqueException(qualifier, dataModel.key(existingKey))
            }
        }
    }

    private suspend fun <DM : IsRootDataModel> IndexedDbByteStore.readCurrentValuesDecrypted(
        dataModel: DM,
        tableStoreName: String,
        keyBytes: ByteArray,
        select: RootPropRefGraph<DM>?,
    ): Values<DM>? = readCurrentValues(dataModel, tableStoreName, keyBytes, select, sensitiveFields::decryptValueIfNeeded)

    private suspend fun <DM : IsRootDataModel> IndexedDbByteStore.readRecordDecrypted(
        dataModel: DM,
        keyStoreName: String,
        tableStoreName: String,
        keyBytes: ByteArray,
        select: RootPropRefGraph<DM>?,
    ): ValuesWithMetaData<DM>? = readRecord(
        dataModel,
        keyStoreName,
        tableStoreName,
        keyBytes,
        select,
        sensitiveFields::decryptValueIfNeeded,
    )

    private suspend fun <DM : IsRootDataModel> IndexedDbByteStore.readCurrentSnapshotDecrypted(
        dataModel: DM,
        keyStoreName: String,
        keyBytes: ByteArray,
        select: RootPropRefGraph<DM>?,
    ): ValuesWithMetaData<DM>? = readCurrentSnapshot(
        dataModel,
        keyStoreName,
        keyBytes,
        select,
        sensitiveFields::decryptValueIfNeeded,
    )

    private suspend fun <DM : IsRootDataModel> IndexedDbByteStore.readHistoricRecordDecrypted(
        dataModel: DM,
        storeName: String,
        keyBytes: ByteArray,
        toVersion: ULong,
        select: RootPropRefGraph<DM>?,
    ): ValuesWithMetaData<DM>? = readHistoricRecord(
        dataModel,
        storeName,
        keyBytes,
        toVersion,
        select,
        sensitiveFields::decryptValueIfNeeded,
    )

    private suspend fun <DM : IsRootDataModel> valuesMatchFilter(
        dataModel: DM,
        values: Values<DM>,
        filter: IsFilter?,
        toVersion: ULong?,
        normalizingIndex: IsIndexable? = null,
    ): Boolean {
        if (filter == null) return true
        if (!filter.hasReferencedQualifier()) {
            if (normalizingIndex == null) return values.matches(filter)
            return matchesFilter(
                filter,
                valueMatcher = { propertyReference, valueMatcher ->
                    @Suppress("UNCHECKED_CAST")
                    val value = values[propertyReference as IsPropertyReference<Any, IsPropertyDefinition<Any>, Any>]

                    if (value is List<*> && propertyReference !is ListReference<*, *>) {
                        value.any { valueMatcher(it) }
                    } else {
                        valueMatcher(value)
                    }
                },
                normalizer = { propertyReference, value ->
                    val transform = normalizingIndex.stringIndexTransform(propertyReference) ?: return@matchesFilter value
                    when (value) {
                        is String -> transform.apply(value)
                        else -> value
                    }
                },
                searchMatcher = { name, value ->
                    dataModel.matchesNamedSearchIndex(name, value) { propertyReference, valueMatcher ->
                        @Suppress("UNCHECKED_CAST")
                        val actualValue = values[propertyReference as IsPropertyReference<Any, IsPropertyDefinition<Any>, Any>]
                        valueMatcher(actualValue)
                    }
                },
                searchPrefixMatcher = { name, value ->
                    dataModel.matchesNamedSearchIndexPrefix(name, value) { propertyReference, valueMatcher ->
                        @Suppress("UNCHECKED_CAST")
                        val actualValue = values[propertyReference as IsPropertyReference<Any, IsPropertyDefinition<Any>, Any>]
                        valueMatcher(actualValue)
                    }
                },
                searchRegexMatcher = { name, regex ->
                    dataModel.matchesNamedSearchIndexRegex(name, regex) { propertyReference, valueMatcher ->
                        @Suppress("UNCHECKED_CAST")
                        val actualValue = values[propertyReference as IsPropertyReference<Any, IsPropertyDefinition<Any>, Any>]
                        valueMatcher(actualValue)
                    }
                },
            )
        }
        return valuesMatchReferencedFilter(dataModel, values, filter, toVersion)
    }

    private suspend fun <DM : IsRootDataModel> valuesMatchReferencedFilter(
        dataModel: DM,
        values: Values<DM>,
        filter: IsFilter,
        toVersion: ULong?,
    ): Boolean = when (filter.filterType) {
        FilterType.And -> (filter as And).filters.all {
            valuesMatchReferencedFilter(dataModel, values, it, toVersion)
        }
        FilterType.Or -> (filter as Or).filters.any {
            valuesMatchReferencedFilter(dataModel, values, it, toVersion)
        }
        FilterType.Not -> (filter as Not).filters.none {
            valuesMatchReferencedFilter(dataModel, values, it, toVersion)
        }
        FilterType.Exists -> (filter as Exists).references.all { reference ->
            matchPropertyReference(dataModel, values, reference, toVersion) { it != null }
        }
        FilterType.Equals -> (filter as Equals).referenceValuePairs.all { (reference, value) ->
            matchPropertyReference(dataModel, values, reference, toVersion) { actual ->
                valuesEqual(actual, value)
            }
        }
        FilterType.LessThan -> (filter as LessThan).referenceValuePairs.all { (reference, value) ->
            @Suppress("UNCHECKED_CAST")
            matchPropertyReference(dataModel, values, reference, toVersion) { actual ->
                actual != null && (value as Comparable<Any>) > actual
            }
        }
        FilterType.LessThanEquals -> (filter as LessThanEquals).referenceValuePairs.all { (reference, value) ->
            @Suppress("UNCHECKED_CAST")
            matchPropertyReference(dataModel, values, reference, toVersion) { actual ->
                actual != null && (value as Comparable<Any>) >= actual
            }
        }
        FilterType.GreaterThan -> (filter as GreaterThan).referenceValuePairs.all { (reference, value) ->
            @Suppress("UNCHECKED_CAST")
            matchPropertyReference(dataModel, values, reference, toVersion) { actual ->
                actual != null && (value as Comparable<Any>) < actual
            }
        }
        FilterType.GreaterThanEquals -> (filter as GreaterThanEquals).referenceValuePairs.all { (reference, value) ->
            @Suppress("UNCHECKED_CAST")
            matchPropertyReference(dataModel, values, reference, toVersion) { actual ->
                actual != null && (value as Comparable<Any>) <= actual
            }
        }
        FilterType.Prefix -> (filter as Prefix).referenceValuePairs.all { (reference, prefix) ->
            matchPropertyReference(dataModel, values, reference, toVersion) { actual ->
                when (actual) {
                    is Collection<*> -> actual.any { it is String && it.startsWith(prefix) }
                    is String -> actual.startsWith(prefix)
                    else -> false
                }
            }
        }
        FilterType.Range -> (filter as Range).referenceValuePairs.all { (reference, range) ->
            @Suppress("UNCHECKED_CAST")
            matchPropertyReference(dataModel, values, reference, toVersion) { actual ->
                val comparable = actual as? Comparable<Any>
                comparable != null && comparable in range as ValueRange<Comparable<Any>>
            }
        }
        FilterType.RegEx -> (filter as RegEx).referenceValuePairs.all { (reference, regex) ->
            matchPropertyReference(dataModel, values, reference, toVersion) { actual ->
                when (actual) {
                    is Collection<*> -> actual.any { it is String && regex.matches(it) }
                    is String -> regex.matches(actual)
                    else -> false
                }
            }
        }
        FilterType.ValueIn -> (filter as ValueIn).referenceValuePairs.all { (reference, expectedValues) ->
            matchPropertyReference(dataModel, values, reference, toVersion) { actual ->
                actual != null && expectedValues.any { valuesEqual(actual, it) }
            }
        }
        FilterType.Matches,
        FilterType.MatchesPrefix,
        FilterType.MatchesRegEx -> values.matches(filter)
    }

    private suspend fun <DM : IsRootDataModel> matchPropertyReference(
        dataModel: DM,
        values: Values<DM>,
        propertyReference: AnyPropertyReference,
        toVersion: ULong?,
        valueMatcher: (Any?) -> Boolean,
    ): Boolean {
        val qualifierMatcher = propertyReference.toQualifierMatcher()
        val referencedMatcher = qualifierMatcher.referencedMatcher()
        if (referencedMatcher != null) {
            return matchReferencedProperty(values, referencedMatcher, toVersion, valueMatcher)
        }

        @Suppress("UNCHECKED_CAST")
        val value = values[propertyReference as IsPropertyReference<Any, *, *>]

        return if (value is List<*> && propertyReference !is ListReference<*, *>) {
            value.any { valueMatcher(it) }
        } else {
            valueMatcher(value)
        }
    }

    private suspend fun <DM : IsRootDataModel> matchReferencedProperty(
        values: Values<DM>,
        referencedMatcher: ReferencedQualifierMatcher,
        toVersion: ULong?,
        valueMatcher: (Any?) -> Boolean,
    ): Boolean {
        val reference = referencedMatcher.reference
        @Suppress("UNCHECKED_CAST")
        val referencedKey = values[reference as IsPropertyReference<Any, *, *>] as? Key<*> ?: return false
        val referencedDataModel = reference.propertyDefinition.definition.dataModel
        val rows = readStorageRows(referencedDataModel, referencedKey.bytes, toVersion) ?: return false

        return matchQualifierOnRows(rows, referencedMatcher.qualifierMatcher, valueMatcher)
    }

    private suspend fun readStorageRows(
        dataModel: IsRootDataModel,
        keyBytes: ByteArray,
        toVersion: ULong?,
    ): List<Pair<ByteArray, ByteArray>>? {
        val modelId = getDataModelId(dataModel)
        if (toVersion != null) {
            val rowKeyPrefix = createObjectRowKeyPrefix(keyBytes)
            val rows = byteStore.scan(
                storeName = "ht:$modelId",
                startKey = createHistoricSnapshotRowKey(keyBytes, toVersion),
                endKey = keyPrefixUpperBound(rowKeyPrefix),
                includeEnd = false,
                limit = 1u,
            ).filter { (rowKey, _) ->
                rowKey.matchesRangePart(0, rowKeyPrefix, sourceLength = rowKey.size, length = rowKeyPrefix.size)
            }
            val snapshot = rows.firstOrNull()?.second ?: return null
            return decodeHistoricSnapshot(snapshot).second
        }

        val snapshot = byteStore.get("k:$modelId", keyBytes)
        if (snapshot != null && snapshot.size > 17) {
            return decodeCurrentSnapshot(snapshot).second
        }

        return byteStore.scanObjectScopedRows("t:$modelId", keyBytes).map { (rowKey, rowValue) ->
            tableQualifierFromRowKey(rowKey, keyBytes) to rowValue
        }
    }

    private suspend fun matchQualifierOnRows(
        rows: List<Pair<ByteArray, ByteArray>>,
        qualifierMatcher: IsQualifierMatcher,
        valueMatcher: (Any?) -> Boolean,
    ): Boolean {
        val reference = qualifierMatcher.referenceForMatch() ?: return false
        val referenceForCache = reference as? IsPropertyReferenceForCache<*, *> ?: return false

        for ((qualifier, valueBytes) in rows) {
            val matched = when (qualifierMatcher) {
                is QualifierExactMatcher -> qualifier.contentEquals(qualifierMatcher.qualifier)
                is QualifierFuzzyMatcher -> qualifierMatcher.isMatch(qualifier, 0) == MATCH
            }
            if (!matched) continue

            if (valueMatcher(decodeStorageValue(referenceForCache, sensitiveFields.decryptValueIfNeeded(valueBytes)))) {
                return true
            }
        }
        return false
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
                decryptValue = sensitiveFields::decryptValueIfNeeded,
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
                                qualifier to sensitiveFields.decryptValueIfNeeded(value)
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
                    }
                }
            }

            val byteStore = openIndexedDbByteStore(
                databaseName = databaseName,
                objectStoreNames = objectStoreNames,
            )

            val dataStore = IndexedDbDataStore(
                databaseName = databaseName,
                byteStore = byteStore,
                keepAllVersions = keepAllVersions,
                keepUpdateHistoryIndex = keepUpdateHistoryIndex,
                dataModelsById = dataModelsById,
                sensitiveFields = sensitiveFields,
            )

            byteStore.migrateStoreMetadata(
                dataStore = dataStore,
                keepAllVersions = keepAllVersions,
                keepUpdateHistoryIndex = keepUpdateHistoryIndex,
                dataModelsById = dataModelsById,
                migrationConfiguration = migrationConfiguration,
                versionUpdateHandler = versionUpdateHandler,
            )

            return dataStore
        }
    }
}

private typealias AddStoreAction<DM> = StoreAction<DM, AddRequest<DM>, AddResponse<DM>>
private typealias ChangeStoreAction<DM> = StoreAction<DM, ChangeRequest<DM>, ChangeResponse<DM>>
private typealias DeleteStoreAction<DM> = StoreAction<DM, DeleteRequest<DM>, DeleteResponse<DM>>
private typealias GetChangesStoreAction<DM> = StoreAction<DM, GetChangesRequest<DM>, ChangesResponse<DM>>
private typealias GetStoreAction<DM> = StoreAction<DM, GetRequest<DM>, ValuesResponse<DM>>
private typealias GetUpdatesStoreAction<DM> = StoreAction<DM, GetUpdatesRequest<DM>, UpdatesResponse<DM>>
private typealias ProcessUpdateResponseStoreAction<DM> = StoreAction<DM, UpdateResponse<DM>, ProcessResponse<DM>>
private typealias ScanChangesStoreAction<DM> = StoreAction<DM, ScanChangesRequest<DM>, ChangesResponse<DM>>
private typealias ScanStoreAction<DM> = StoreAction<DM, ScanRequest<DM>, ValuesResponse<DM>>
private typealias ScanUpdateHistoryStoreAction<DM> = StoreAction<DM, ScanUpdateHistoryRequest<DM>, UpdatesResponse<DM>>
private typealias ScanUpdatesStoreAction<DM> = StoreAction<DM, ScanUpdatesRequest<DM>, UpdatesResponse<DM>>

private data class ScanUpdateRows<DM : IsRootDataModel>(
    val rows: List<ValuesWithMetaData<DM>>,
    val sortingKeys: List<Bytes>?,
    val dataFetchType: DataFetchType,
)

private fun ScanUpdatesRequest<*>.canUseUpdateHistoryIndex() =
    order == null && startKey == null && includeStart && fromVersion == 0uL && toVersion == null && maxVersions == 1u

private data class CurrentStateStoragePlan(
    val tableRows: List<Pair<ByteArray, ByteArray>>,
    val indexRows: List<ByteArray>,
    val uniqueRows: List<Triple<ByteArray, ByteArray, ByteArray>>,
)

private data class MaterializedChanges(
    val appliedChanges: List<IsChange>,
    val generatedChanges: List<IsChange>,
)

private data class StorageRowToWrite(
    val qualifier: ByteArray,
    val encodedValue: ByteArray,
    val definition: IsPropertyDefinition<*>,
    val type: StorageTypeEnum<IsPropertyDefinition<*>>,
)

private fun IndexedDbDataStore.modelWriteStoreNames(
    keyStoreName: String,
    tableStoreName: String,
    indexStoreName: String,
    uniqueStoreName: String,
    changeStoreName: String,
    updateHistoryStoreName: String,
    historicTableStoreName: String,
    historicIndexStoreName: String,
    historicUniqueStoreName: String,
    historicIndexCleanupStoreName: String,
    historicUniqueCleanupStoreName: String,
): Set<String> = buildSet {
    add(keyStoreName)
    add(tableStoreName)
    add(indexStoreName)
    add(uniqueStoreName)
    add(changeStoreName)
    if (keepUpdateHistoryIndex) {
        add(updateHistoryStoreName)
    }
    if (keepAllVersions) {
        add(historicTableStoreName)
        add(historicIndexStoreName)
        add(historicUniqueStoreName)
        add(historicIndexCleanupStoreName)
        add(historicUniqueCleanupStoreName)
    }
}

private fun IsQualifierMatcher.referencedMatcher(): ReferencedQualifierMatcher? = when (this) {
    is QualifierExactMatcher -> referencedQualifierMatcher
    is QualifierFuzzyMatcher -> referencedQualifierMatcher
}

private fun IsQualifierMatcher.referenceForMatch(): AnyPropertyReference? = when (this) {
    is QualifierExactMatcher -> reference
    is QualifierFuzzyMatcher -> reference
}

private fun IsFilter.hasReferencedQualifier(): Boolean = when (filterType) {
    FilterType.And -> (this as And).filters.any { it.hasReferencedQualifier() }
    FilterType.Or -> (this as Or).filters.any { it.hasReferencedQualifier() }
    FilterType.Not -> (this as Not).filters.any { it.hasReferencedQualifier() }
    FilterType.Exists -> (this as Exists).references.any { it.toQualifierMatcher().referencedMatcher() != null }
    FilterType.Equals -> (this as Equals).referenceValuePairs.any { (reference, _) ->
        reference.toQualifierMatcher().referencedMatcher() != null
    }
    FilterType.LessThan -> (this as LessThan).referenceValuePairs.any { (reference, _) ->
        reference.toQualifierMatcher().referencedMatcher() != null
    }
    FilterType.LessThanEquals -> (this as LessThanEquals).referenceValuePairs.any { (reference, _) ->
        reference.toQualifierMatcher().referencedMatcher() != null
    }
    FilterType.GreaterThan -> (this as GreaterThan).referenceValuePairs.any { (reference, _) ->
        reference.toQualifierMatcher().referencedMatcher() != null
    }
    FilterType.GreaterThanEquals -> (this as GreaterThanEquals).referenceValuePairs.any { (reference, _) ->
        reference.toQualifierMatcher().referencedMatcher() != null
    }
    FilterType.Prefix -> (this as Prefix).referenceValuePairs.any { (reference, _) ->
        reference.toQualifierMatcher().referencedMatcher() != null
    }
    FilterType.Range -> (this as Range).referenceValuePairs.any { (reference, _) ->
        reference.toQualifierMatcher().referencedMatcher() != null
    }
    FilterType.RegEx -> (this as RegEx).referenceValuePairs.any { (reference, _) ->
        reference.toQualifierMatcher().referencedMatcher() != null
    }
    FilterType.ValueIn -> (this as ValueIn).referenceValuePairs.any { (reference, _) ->
        reference.toQualifierMatcher().referencedMatcher() != null
    }
    FilterType.Matches,
    FilterType.MatchesPrefix,
    FilterType.MatchesRegEx -> false
}

private fun valuesEqual(actual: Any?, expected: Any?): Boolean = when {
    actual is Collection<*> && expected is Collection<*> -> expected.all { expectedValue ->
        actual.any { it == expectedValue }
    }
    actual is Collection<*> -> actual.any { it == expected }
    expected is Collection<*> -> expected.any { it == actual }
    else -> actual == expected
}

private fun <DM : IsRootDataModel> ValuesWithMetaData<DM>.toCreationChanges(
    fromVersion: ULong,
    toVersion: ULong?,
    select: RootPropRefGraph<DM>?,
): List<VersionedChanges> {
    if (firstVersion < fromVersion) return emptyList()
    if (toVersion != null && firstVersion > toVersion) return emptyList()

    val changes = (listOf(ObjectCreate) + values.toChanges().toList()).mapNotNull { change ->
        select?.let { change.filterWithSelect(it) } ?: change
    }
    if (changes.isEmpty()) return emptyList()

    return listOf(
        VersionedChanges(
            version = firstVersion,
            changes = changes,
        )
    )
}

private fun createChangeLogRowKey(keyBytes: ByteArray, version: ULong): ByteArray =
    combineToByteArray(createObjectRowKeyPrefix(keyBytes), version.toBigEndianBytes())

private fun createUpdateHistoryRowKey(version: ULong, keyBytes: ByteArray): ByteArray =
    combineToByteArray((ULong.MAX_VALUE - version).toBigEndianBytes(), keyBytes)

private fun createHistoricSnapshotRowKey(keyBytes: ByteArray, version: ULong): ByteArray =
    combineToByteArray(createObjectRowKeyPrefix(keyBytes), (ULong.MAX_VALUE - version).toBigEndianBytes())

private fun createHistoricVersionedRowKey(rowKey: ByteArray, version: ULong): ByteArray =
    combineToByteArray(rowKey, (ULong.MAX_VALUE - version).toBigEndianBytes())

private fun createHistoricCleanupRowKey(keyBytes: ByteArray, historicRowKey: ByteArray): ByteArray =
    combineToByteArray(createObjectRowKeyPrefix(keyBytes), historicRowKey)

private fun encodeCurrentSnapshot(
    meta: IndexedDbRecordMeta,
    rows: List<Pair<ByteArray, ByteArray>>,
): ByteArray = encodeRowsSnapshot(meta, rows)

private fun decodeCurrentSnapshot(bytes: ByteArray): Pair<IndexedDbRecordMeta, List<Pair<ByteArray, ByteArray>>> =
    decodeRowsSnapshot(bytes)

private suspend fun <DM : IsRootDataModel> IndexedDbByteStore.readCurrentSnapshot(
    dataModel: DM,
    keyStoreName: String,
    keyBytes: ByteArray,
    select: RootPropRefGraph<DM>?,
    decryptValue: suspend (ByteArray) -> ByteArray = { it },
): ValuesWithMetaData<DM>? {
    val snapshot = get(keyStoreName, keyBytes) ?: return null
    if (snapshot.size == 17) return null

    val (meta, rows) = decodeCurrentSnapshot(snapshot)
    val values = decodeStorageRowsToValues(
        dataModel,
        rows.map { (qualifier, value) -> qualifier to decryptValue(value) },
        select,
    ) ?: return null

    return ValuesWithMetaData(
        key = dataModel.key(keyBytes),
        values = values,
        firstVersion = meta.firstVersion,
        lastVersion = meta.lastVersion,
        isDeleted = meta.isDeleted,
    )
}

private suspend fun <DM : IsRootDataModel> decodeCurrentSnapshotRecord(
    dataModel: DM,
    keyBytes: ByteArray,
    snapshot: ByteArray,
    select: RootPropRefGraph<DM>?,
    decryptValue: suspend (ByteArray) -> ByteArray = { it },
): ValuesWithMetaData<DM>? {
    if (snapshot.size == 17) return null

    val (meta, rows) = decodeCurrentSnapshot(snapshot)
    val values = decodeStorageRowsToValues(
        dataModel,
        rows.map { (qualifier, value) -> qualifier to decryptValue(value) },
        select,
    ) ?: return null

    return ValuesWithMetaData(
        key = dataModel.key(keyBytes),
        values = values,
        firstVersion = meta.firstVersion,
        lastVersion = meta.lastVersion,
        isDeleted = meta.isDeleted,
    )
}

private fun ByteArray.readInvertedVersion(): ULong {
    var inverted = 0uL
    for (index in 0 until ULong.SIZE_BYTES) {
        inverted = (inverted shl Byte.SIZE_BITS) or this[index].toUByte().toULong()
    }
    return ULong.MAX_VALUE - inverted
}

private fun ByteArray.readTrailingInvertedVersion(): ULong =
    copyOfRange(size - ULong.SIZE_BYTES, size).readInvertedVersion()

private fun ByteArray.readTrailingVersion(): ULong {
    var version = 0uL
    for (index in size - ULong.SIZE_BYTES until size) {
        version = (version shl Byte.SIZE_BITS) or this[index].toUByte().toULong()
    }
    return version
}

private val unserializableChangeLogMarker = byteArrayOf(0)

private fun ByteArray.isUnserializableChangeLogMarker() =
    size == unserializableChangeLogMarker.size && contentEquals(unserializableChangeLogMarker)

private fun encodeHistoricSnapshot(
    meta: IndexedDbRecordMeta,
    rows: List<Pair<ByteArray, ByteArray>>,
): ByteArray = encodeRowsSnapshot(meta, rows)

private fun encodeRowsSnapshot(
    meta: IndexedDbRecordMeta,
    rows: List<Pair<ByteArray, ByteArray>>,
): ByteArray {
    val size = 17 + rows.size.calculateVarByteLength() + rows.sumOf { (qualifier, value) ->
        qualifier.size.calculateVarByteLength() + qualifier.size +
            value.size.calculateVarByteLength() + value.size
    }
    val bytes = ByteArray(size)
    var index = 0
    encodeRecordMeta(meta).copyInto(bytes, destinationOffset = index)
    index += 17
    rows.size.writeVarBytes { bytes[index++] = it }
    for ((qualifier, value) in rows) {
        qualifier.size.writeVarBytes { bytes[index++] = it }
        qualifier.copyInto(bytes, destinationOffset = index)
        index += qualifier.size
        value.size.writeVarBytes { bytes[index++] = it }
        value.copyInto(bytes, destinationOffset = index)
        index += value.size
    }
    return bytes
}

private fun decodeHistoricSnapshot(bytes: ByteArray): Pair<IndexedDbRecordMeta, List<Pair<ByteArray, ByteArray>>> {
    return decodeRowsSnapshot(bytes)
}

private fun decodeRowsSnapshot(bytes: ByteArray): Pair<IndexedDbRecordMeta, List<Pair<ByteArray, ByteArray>>> {
    var index = 0
    val meta = decodeRecordMeta(bytes.copyOfRange(0, 17))
    index = 17
    val count = initIntByVar { bytes[index++] }
    val rows = ArrayList<Pair<ByteArray, ByteArray>>(count)
    repeat(count) {
        val qualifierSize = initIntByVar { bytes[index++] }
        val qualifier = bytes.copyOfRange(index, index + qualifierSize)
        index += qualifierSize
        val valueSize = initIntByVar { bytes[index++] }
        val value = bytes.copyOfRange(index, index + valueSize)
        index += valueSize
        rows += qualifier to value
    }
    return meta to rows
}

private fun ULong.toBigEndianBytes(): ByteArray {
    val bytes = ByteArray(ULong.SIZE_BYTES)
    for (index in bytes.indices) {
        bytes[index] = (this shr ((ULong.SIZE_BYTES - 1 - index) * Byte.SIZE_BITS)).toByte()
    }
    return bytes
}

private fun <DM : IsRootDataModel> encodeVersionedChange(
    dataModel: DM,
    change: DataObjectVersionedChange<DM>,
): ByteArray {
    val requestContext = DataObjectVersionedChange.Serializer.transformContext(requestContextFor(dataModel))
    val cache = WriteCache()
    val byteLength = DataObjectVersionedChange.Serializer.calculateObjectProtoBufLength(change, cache, requestContext)
    val bytes = ByteArray(byteLength)
    var index = 0
    DataObjectVersionedChange.Serializer.writeObjectProtoBuf(change, cache, { bytes[index++] = it }, requestContext)
    return bytes
}

private fun <DM : IsRootDataModel> decodeVersionedChange(
    dataModel: DM,
    bytes: ByteArray,
): DataObjectVersionedChange<DM> {
    val requestContext = DataObjectVersionedChange.Serializer.transformContext(requestContextFor(dataModel))
    var index = 0
    @Suppress("UNCHECKED_CAST")
    return DataObjectVersionedChange.Serializer.readProtoBuf(bytes.size, { bytes[index++] }, requestContext).toDataObject()
        as DataObjectVersionedChange<DM>
}

private fun requestContextFor(dataModel: IsRootDataModel): RequestContext =
    RequestContext(
        DefinitionsContext(
            mutableMapOf(dataModel.Meta.name to DataModelReference(dataModel))
        ),
        dataModel = dataModel,
    )

private fun MutableList<IndexedDbWriteOperation>.put(
    storeName: String,
    key: ByteArray,
    value: ByteArray,
) {
    add(IndexedDbWriteOperation.Put(storeName, key, value))
}

private fun MutableList<IndexedDbWriteOperation>.delete(
    storeName: String,
    key: ByteArray,
) {
    add(IndexedDbWriteOperation.Delete(storeName, key))
}

private fun <DM : IsRootDataModel> MutableList<IndexedDbWriteOperation>.addChangeLog(
    dataModel: DM,
    changeStoreName: String,
    keyBytes: ByteArray,
    version: ULong,
    changes: List<IsChange>,
): ByteArray? {
    if (changes.isEmpty()) return null

    val versionedChange = DataObjectVersionedChange(
        key = dataModel.key(keyBytes),
        changes = listOf(
            VersionedChanges(
                version = version,
                changes = changes,
            )
        )
    )

    val encoded = try {
        encodeVersionedChange(dataModel, versionedChange)
    } catch (e: Throwable) {
        e.rethrowIfFatal()
        unserializableChangeLogMarker
    }

    put(
        storeName = changeStoreName,
        key = createChangeLogRowKey(keyBytes, version),
        value = encoded,
    )
    return encoded
}

private fun MutableList<IndexedDbWriteOperation>.addHistoricSnapshot(
    storeName: String,
    keyBytes: ByteArray,
    version: ULong,
    meta: IndexedDbRecordMeta,
    rows: List<Pair<ByteArray, ByteArray>>,
) {
    put(storeName, createHistoricSnapshotRowKey(keyBytes, version), encodeHistoricSnapshot(meta, rows))
}

private fun MutableList<IndexedDbWriteOperation>.addHistoricIndexRows(
    storeName: String,
    cleanupStoreName: String,
    keyBytes: ByteArray,
    indexRows: List<ByteArray>,
    version: ULong,
    active: Boolean,
) {
    val value = byteArrayOf(if (active) 1 else 0)
    for (row in indexRows) {
        val historicRowKey = createHistoricVersionedRowKey(row, version)
        put(storeName, historicRowKey, value)
        put(cleanupStoreName, createHistoricCleanupRowKey(keyBytes, historicRowKey), historicRowKey)
    }
}

private fun MutableList<IndexedDbWriteOperation>.addHistoricUniqueRows(
    storeName: String,
    cleanupStoreName: String,
    uniqueRows: List<Triple<ByteArray, ByteArray, ByteArray>>,
    version: ULong,
    active: Boolean,
) {
    for ((uniqueKey, keyBytes, _) in uniqueRows) {
        val historicRowKey = createHistoricVersionedRowKey(uniqueKey, version)
        put(storeName, historicRowKey, if (active) keyBytes else byteArrayOf())
        put(cleanupStoreName, createHistoricCleanupRowKey(keyBytes, historicRowKey), historicRowKey)
    }
}

private suspend fun IndexedDbByteStore.scanObjectScopedRows(
    storeName: String,
    keyBytes: ByteArray,
): List<Pair<ByteArray, ByteArray>> {
    val rowKeyPrefix = createObjectRowKeyPrefix(keyBytes)
    return scan(
        storeName = storeName,
        startKey = rowKeyPrefix,
        endKey = keyPrefixUpperBound(rowKeyPrefix),
        includeEnd = false,
    ).filter { (rowKey, _) ->
        rowKey.matchesRangePart(0, rowKeyPrefix, sourceLength = rowKey.size, length = rowKeyPrefix.size)
    }
}

private suspend fun <DM : IsRootDataModel> IndexedDbByteStore.readChangeLog(
    dataModel: DM,
    changeStoreName: String,
    historicTableStoreName: String?,
    keyBytes: ByteArray,
    fromVersion: ULong,
    toVersion: ULong?,
    maxVersions: UInt,
    select: RootPropRefGraph<DM>?,
    decryptValue: suspend (ByteArray) -> ByteArray = { it },
): List<VersionedChanges> {
    val creationChanges = mutableListOf<VersionedChanges>()
    val nonCreationChanges = mutableListOf<VersionedChanges>()

    val rows = scanObjectScopedRows(changeStoreName, keyBytes)

    for ((rowKey, rowValue) in rows) {
        if (rowValue.isUnserializableChangeLogMarker()) {
            val version = rowKey.readTrailingVersion()
            val historicRecord = historicTableStoreName?.let {
                readHistoricRecord(dataModel, it, keyBytes, version, select, decryptValue)
            }
            if (historicRecord?.firstVersion == version) {
                creationChanges += historicRecord.toCreationChanges(fromVersion, toVersion, select)
            }
            continue
        }

        val decoded = decodeVersionedChange(dataModel, rowValue)
        for (versionedChanges in decoded.changes) {
            if (versionedChanges.version < fromVersion) continue
            if (toVersion != null && versionedChanges.version > toVersion) continue

            val filteredChanges = versionedChanges.changes.mapNotNull { change ->
                select?.let { change.filterWithSelect(it) } ?: change
            }
            if (filteredChanges.isEmpty()) continue

            val filtered = versionedChanges.copy(changes = filteredChanges)
            if (filteredChanges.any { it is ObjectCreate }) {
                creationChanges += filtered
            } else {
                nonCreationChanges += filtered
            }
        }
    }

    val limitedNonCreationChanges = if (maxVersions >= nonCreationChanges.size.toUInt()) {
        nonCreationChanges
    } else {
        nonCreationChanges.takeLast(maxVersions.toInt())
    }

    val returnedCreationChanges = if (limitedNonCreationChanges.isEmpty()) {
        creationChanges
    } else {
        creationChanges.map { versionedChanges ->
            versionedChanges.copy(
                changes = versionedChanges.changes.filterIsInstance<ObjectCreate>()
            )
        }.filter { it.changes.isNotEmpty() }
    }

    return returnedCreationChanges + limitedNonCreationChanges
}

private suspend fun IndexedDbByteStore.historicCleanupRowsForKey(
    storeName: String,
    keyBytes: ByteArray,
): List<Pair<ByteArray, ByteArray>> =
    scanObjectScopedRows(storeName, keyBytes)

private suspend fun <DM : IsRootDataModel> IndexedDbByteStore.readHistoricRecord(
    dataModel: DM,
    storeName: String,
    keyBytes: ByteArray,
    toVersion: ULong,
    select: RootPropRefGraph<DM>?,
    decryptValue: suspend (ByteArray) -> ByteArray = { it },
): ValuesWithMetaData<DM>? {
    val rowKeyPrefix = createObjectRowKeyPrefix(keyBytes)
    val rows = scan(
        storeName = storeName,
        startKey = createHistoricSnapshotRowKey(keyBytes, toVersion),
        endKey = keyPrefixUpperBound(rowKeyPrefix),
        includeEnd = false,
        limit = 1u,
    ).filter { (rowKey, _) ->
        rowKey.matchesRangePart(0, rowKeyPrefix, sourceLength = rowKey.size, length = rowKeyPrefix.size)
    }

    val (_, value) = rows.firstOrNull() ?: return null
    val (meta, storageRows) = decodeHistoricSnapshot(value)
    val values = decodeStorageRowsToValues(
        dataModel,
        storageRows.map { (qualifier, rowValue) -> qualifier to decryptValue(rowValue) },
        select,
    )
        ?: dataModel.emptyValues()

    return ValuesWithMetaData(
        key = dataModel.key(keyBytes),
        values = values,
        firstVersion = meta.firstVersion,
        lastVersion = meta.lastVersion,
        isDeleted = meta.isDeleted,
    )
}

private suspend fun IndexedDbByteStore.readHistoricUniqueKey(
    storeName: String,
    uniqueKey: ByteArray,
    toVersion: ULong,
): ByteArray? {
    val rows = scan(
        storeName = storeName,
        startKey = createHistoricVersionedRowKey(uniqueKey, toVersion),
        endKey = keyPrefixUpperBound(uniqueKey),
        includeEnd = false,
        limit = 1u,
    ).filter { (rowKey, _) ->
        rowKey.matchesRangePart(0, uniqueKey, sourceLength = rowKey.size, length = uniqueKey.size)
    }

    return rows.firstOrNull()?.second?.takeUnless { it.isEmpty() }
}

private suspend fun IndexedDbByteStore.readHistoricIndexRows(
    storeName: String,
    startKey: ByteArray,
    endKey: ByteArray,
    includeEnd: Boolean,
    toVersion: ULong,
    reverse: Boolean,
): List<Pair<ByteArray, ByteArray>> {
    val latestByBaseKey = mutableMapOf<String, Triple<ByteArray, ULong, ByteArray>>()

    scanInBatches(
        storeName = storeName,
        startKey = startKey,
        endKey = endKey,
        includeEnd = includeEnd,
        targetLimit = UInt.MAX_VALUE,
    ) { rowKey, rowValue ->
        if (rowKey.size < ULong.SIZE_BYTES) return@scanInBatches true
        val baseKey = rowKey.copyOfRange(0, rowKey.size - ULong.SIZE_BYTES)
        val version = rowKey.readTrailingInvertedVersion()
        if (version > toVersion) return@scanInBatches true

        val mapKey = baseKey.contentToString()
        val current = latestByBaseKey[mapKey]
        if (current == null || version > current.second) {
            latestByBaseKey[mapKey] = Triple(baseKey, version, rowValue)
        }
        true
    }

    val activeRows = latestByBaseKey.values
        .filter { (_, _, value) -> value.firstOrNull() == 1.toByte() }
        .map { (baseKey, _, value) -> baseKey to value }
        .sortedWith { a, b -> a.first compareTo b.first }

    return if (reverse) activeRows.asReversed() else activeRows
}

private fun createUniqueRowKey(reference: ByteArray, valueBytes: ByteArray): ByteArray {
    val combined = ByteArray(reference.size + valueBytes.size)
    reference.copyInto(combined, endIndex = reference.size)
    valueBytes.copyInto(combined, destinationOffset = reference.size)
    return combined
}

private fun createIndexKeyPrefix(indexReference: ByteArray): ByteArray {
    val length = indexReference.size
    val prefix = ByteArray(length.calculateVarByteLength() + length)
    var writeIndex = 0
    length.writeVarBytes { prefix[writeIndex++] = it }
    indexReference.copyInto(prefix, writeIndex)
    return prefix
}

private fun createIndexRowKey(indexReference: ByteArray, valueAndKey: ByteArray) =
    combineToByteArray(createIndexKeyPrefix(indexReference), valueAndKey)

private fun createIndexKeyWithPrefix(indexKeyPrefix: ByteArray, valueAndKey: ByteArray) =
    combineToByteArray(indexKeyPrefix, valueAndKey)

private fun createIndexRangeEnd(indexReference: ByteArray) =
    createIndexKeyPrefix(indexReference).nextByteInSameLength()

private suspend fun <DM : IsRootDataModel> createStoragePlan(
    dataModel: DM,
    modelId: UInt,
    keyBytes: ByteArray,
    values: Values<DM>,
    sensitiveFields: IndexedDbSensitiveFieldSupport,
): CurrentStateStoragePlan {
    val rows = mutableListOf<StorageRowToWrite>()
    val indexRows = mutableListOf<ByteArray>()
    val uniqueRows = mutableListOf<Triple<ByteArray, ByteArray, ByteArray>>()

    dataModel.Meta.indexes?.forEach { index ->
        index.toStorageByteArraysForIndex(values, keyBytes).forEach { valueAndKey ->
            indexRows += createIndexRowKey(index.referenceStorageByteArray.bytes, valueAndKey)
        }
    }

    values.writeToStorage { type, qualifier, definition, value ->
        if (type == ObjectDelete) return@writeToStorage

        val encodedValue = encodeStorageValue(type, definition, value)
        rows += StorageRowToWrite(qualifier, encodedValue, definition, type)
    }

    val encryptedTableRows = rows.map { row ->
        createTableRowKey(keyBytes, row.qualifier) to
            sensitiveFields.encryptValueIfSensitive(modelId, row.qualifier, row.encodedValue)
    }
    for (row in rows) {
        if (row.type == Value && row.definition is IsComparableDefinition<*, *> && row.definition.unique) {
            val uniqueValue = sensitiveFields.mapUniqueValueBytes(modelId, row.qualifier, row.encodedValue)
            val uniqueKey = createUniqueRowKey(row.qualifier, uniqueValue)
            uniqueRows += Triple(uniqueKey, keyBytes, row.qualifier)
        }
    }

    return CurrentStateStoragePlan(
        tableRows = encryptedTableRows,
        indexRows = indexRows,
        uniqueRows = uniqueRows,
    )
}

private fun <DM : IsRootDataModel> materializeChanges(
    changes: List<IsChange>,
    currentValues: Values<DM>,
) : MaterializedChanges {
    val appliedChanges = mutableListOf<IsChange>()
    val generatedChanges = mutableListOf<IsChange>()
    val latestGeneratedKeyByReference = mutableMapOf<Any, Comparable<Any>>()

    for (change in changes) {
        if (change !is IncMapChange) {
            appliedChanges += change
            continue
        }

        val additions = change.valueChanges.mapNotNull { valueChange ->
            val addValues = valueChange.addValues ?: return@mapNotNull null
            if (addValues.isEmpty()) return@mapNotNull null

            val currentMap = currentValues[valueChange.reference].orEmptyComparableMap()
            val currentMaxKey = latestGeneratedKeyByReference[valueChange.reference]
                ?: currentMap.keys.maxOrNull()
                ?: zeroComparableKeyFor(valueChange.reference)
            val nextKeys = ArrayList<Comparable<Any>>(addValues.size)
            var nextKey = currentMaxKey

            repeat(addValues.size) {
                nextKey = nextComparableKey(nextKey)
                nextKeys += nextKey
            }
            latestGeneratedKeyByReference[valueChange.reference] = nextKey

            createIncMapKeyAdditions(
                valueChange.reference,
                nextKeys.zip(addValues).map { (key, value) ->
                    ComparableMapEntry(key, value)
                }
            )
        }

        if (additions.isNotEmpty()) {
            val addition = IncMapAddition(additions)
            appliedChanges += addition
            generatedChanges += addition
        }
    }

    return MaterializedChanges(
        appliedChanges = appliedChanges,
        generatedChanges = generatedChanges,
    )
}

private fun <DM : IsRootDataModel> seedMissingRootMaps(
    currentValues: Values<DM>,
    changes: List<IsChange>,
): Values<DM> {
    var seededValues = currentValues
    val seen = mutableSetOf<UInt>()

    for (change in changes) {
        if (change !is Change) continue

        for (pair in change.referenceValuePairs) {
            if (pair.value == null) continue
            val reference = pair.reference as? MapValueReference<*, *, *> ?: continue
            val mapReference = reference.parentReference as? MapReference<*, *, *> ?: continue
            if (currentValues[mapReference] != null || !seen.add(mapReference.index)) continue

            seededValues = seededValues.copyWithValue(mapReference.index, emptyMap<Any, Any>())
        }
    }

    return seededValues
}

private fun <DM : IsRootDataModel> evaluateChecks(
    changes: List<IsChange>,
    currentValues: Values<DM>,
): List<ValidationException> {
    val exceptions = mutableListOf<ValidationException>()

    for (change in changes) {
        if (change !is Check) continue

        for ((reference, value) in change.referenceValuePairs) {
            if (currentValues[reference] != value) {
                exceptions += InvalidValueException(reference, value.toString())
            }
        }
    }

    return exceptions
}

@Suppress("UNCHECKED_CAST")
private fun Any?.orEmptyComparableMap(): Map<Comparable<Any>, Any> =
    this as? Map<Comparable<Any>, Any> ?: emptyMap()

@Suppress("UNCHECKED_CAST")
private fun zeroComparableKeyFor(
    reference: IncMapReference<out Comparable<Any>, out Any, *>,
): Comparable<Any> = when (reference.propertyDefinition.definition.keyNumberDescriptor.type) {
    NumberType.UInt8Type -> 0.toUByte()
    NumberType.UInt16Type -> 0.toUShort()
    NumberType.UInt32Type -> 0u
    NumberType.UInt64Type -> 0uL
    NumberType.SInt8Type -> 0.toByte()
    NumberType.SInt16Type -> 0.toShort()
    NumberType.SInt32Type -> 0
    NumberType.SInt64Type -> 0L
    else -> error("Unsupported incrementing map key type ${reference.propertyDefinition.definition.keyNumberDescriptor.type}")
} as Comparable<Any>

@Suppress("UNCHECKED_CAST")
private fun nextComparableKey(value: Comparable<Any>): Comparable<Any> = when (value) {
    is UByte -> (value + 1u).toUByte()
    is UShort -> (value + 1u).toUShort()
    is UInt -> value + 1u
    is ULong -> value + 1u
    is Byte -> (value + 1).toByte()
    is Short -> (value + 1).toShort()
    is Int -> value + 1
    is Long -> value + 1
    else -> error("Unsupported incrementing map key type ${value::class}")
} as Comparable<Any>

@Suppress("UNCHECKED_CAST")
private fun createIncMapKeyAdditions(
    reference: IncMapReference<out Comparable<Any>, out Any, *>,
    addedEntries: List<ComparableMapEntry>,
): IncMapKeyAdditions<out Comparable<Any>, out Any> {
    return IncMapKeyAdditions(
        reference = reference as IncMapReference<Comparable<Any>, Any, *>,
        addedKeys = addedEntries.map { it.key },
        addedValues = addedEntries.map { it.value }
    ) as IncMapKeyAdditions<out Comparable<Any>, out Any>
}

private data class ComparableMapEntry(
    override val key: Comparable<Any>,
    override val value: Any,
) : Map.Entry<Comparable<Any>, Any>

private fun <DM : IsRootDataModel> createAlreadyExistsException(
    dataModel: DM,
    uniqueException: UniqueException,
): AlreadyExistsException {
    var index = 0
    val reference = dataModel.getPropertyReferenceByStorageBytes(
        length = uniqueException.reference.size,
        reader = { uniqueException.reference[index++] }
    )
    return AlreadyExistsException(reference, uniqueException.key)
}

private fun keyPrefixUpperBound(bytes: ByteArray): ByteArray? {
    val next = bytes.copyOf()
    for (index in next.lastIndex downTo 0) {
        if (next[index] != 0xFF.toByte()) {
            next[index]++
            return next
        }
    }
    return null
}

private fun resolveIndexValueSize(indexValue: ByteArray, keySize: Int, indexPartCount: Int): Int? {
    if (indexPartCount <= 0 || indexValue.size < keySize) return null

    return try {
        val (offset, size) = findByteIndexAndSizeByPartIndex(
            partIndex = indexPartCount - 1,
            indexable = indexValue,
            keySize = keySize,
            indexPartCount = indexPartCount
        )
        offset + size
    } catch (_: Exception) {
        null
    }
}

private fun indexRangeLength(
    indexScanRange: IndexableScanRanges,
    range: ScanRange,
    valueSize: Int
): Int =
    if (
        range.start.isNotEmpty() &&
        range.startInclusive &&
        range.endInclusive &&
        range.end?.contentEquals(range.start) == true &&
        indexScanRange.partialMatches?.any {
            (it is IndexPartialSizeToMatch && it.size == range.start.size) ||
                (it is IndexPartialToMatch &&
                    it.partialMatch &&
                    it.toMatch.contentEquals(range.start))
        } == true
    ) {
        range.start.size
    } else {
        valueSize
    }
