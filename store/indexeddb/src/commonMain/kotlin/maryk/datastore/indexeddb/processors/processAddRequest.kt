package maryk.datastore.indexeddb.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.processors.datastore.StorageTypeEnum.ObjectDelete
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.processors.datastore.writeToStorage
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.AlreadyExists
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.indexeddb.IndexedDbDataStore
import maryk.datastore.indexeddb.IndexedDbRecordMeta
import maryk.datastore.indexeddb.IndexedDbTransactionMode
import maryk.datastore.indexeddb.IndexedDbWriteOperation
import maryk.datastore.indexeddb.createTableRowKey
import maryk.datastore.indexeddb.decodeRecordMeta
import maryk.datastore.indexeddb.encodeStorageValue
import maryk.datastore.indexeddb.tableQualifierFromRowKey
import maryk.datastore.shared.UniqueException
import maryk.datastore.shared.updates.Update

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.processAddRequest(
    version: HLC,
    storeAction: AddStoreAction<DM>,
    isDeleted: Boolean = false,
    ignoreIfVersionNotNewer: Boolean = false,
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
                val currentMeta = byteStore.get(keyStoreName, key.bytes)?.let(::decodeRecordMeta)
                val tombstoneVersion = byteStore.get("hd:$modelId", key.bytes)?.readTrailingVersion()
                if (ignoreIfVersionNotNewer) {
                    val lastAppliedVersion = listOfNotNull(currentMeta?.lastVersion, tombstoneVersion).maxOrNull()
                    if (lastAppliedVersion != null && version.timestamp <= lastAppliedVersion) {
                        statuses += AddSuccess(key, version.timestamp, emptyList())
                        return@transaction
                    }
                }
                if (currentMeta != null) {
                    statuses += AlreadyExists(key)
                    return@transaction
                }

                values.validate()

                val operations = mutableListOf<IndexedDbWriteOperation>()
                if (tombstoneVersion != null) {
                    operations.delete("hd:$modelId", key.bytes)
                }
                val rows = mutableListOf<StorageRowToWrite>()
                val indexRows = mutableListOf<ByteArray>()
                val uniqueRows = mutableListOf<IndexedDbUniqueRow>()

                if (!isDeleted) {
                    request.dataModel.Meta.indexes?.forEach { index ->
                        index.toStorageByteArraysForIndex(values, key.bytes).forEach { valueAndKey ->
                            indexRows += createIndexRowKey(index.referenceStorageByteArray.bytes, valueAndKey)
                        }
                    }
                }

                values.writeToStorage { type, qualifier, definition, value ->
                    if (type == ObjectDelete) return@writeToStorage

                    val encodedValue = encodeStorageValue(type, definition, value)
                    rows += StorageRowToWrite(qualifier, encodedValue, definition, type)
                }

                val tableRows = mutableListOf<Pair<ByteArray, ByteArray>>()
                for (row in rows) {
                    val encryptedValue = sensitiveFields.encryptValueIfSensitive(modelId, key.bytes, row.qualifier, row.encodedValue)
                    tableRows += createTableRowKey(key.bytes, row.qualifier) to encryptedValue

                    if (!isDeleted && row.type == Value && row.definition is IsComparableDefinition<*, *> && row.definition.unique) {
                        val uniqueKeys = sensitiveFields.mapUniqueValueByteCandidates(modelId, row.qualifier, row.encodedValue)
                            .map { uniqueValue -> createUniqueRowKey(row.qualifier, uniqueValue) }
                        uniqueRows += IndexedDbUniqueRow(
                            uniqueKey = uniqueKeys.first(),
                            keyBytes = key.bytes,
                            qualifier = row.qualifier,
                            candidateKeys = uniqueKeys,
                        )
                    }
                }

                for (row in uniqueRows) {
                    for (candidateKey in row.candidateKeys) {
                        val existingKey = byteStore.get(uniqueStoreName, candidateKey)
                        if (existingKey != null) {
                            throw UniqueException(row.qualifier, request.dataModel.key(existingKey))
                        }
                    }
                }

                for ((rowKey, encodedValue) in tableRows) {
                    operations.put(tableStoreName, rowKey, encodedValue)
                }
                val currentSnapshot = encodeCurrentSnapshot(
                    IndexedDbRecordMeta(version.timestamp, version.timestamp, isDeleted),
                    tableRows.map { (rowKey, rowValue) -> tableQualifierFromRowKey(rowKey, key.bytes) to rowValue },
                )
                operations.put(
                    keyStoreName,
                    key.bytes,
                    currentSnapshot,
                )
                for (indexRow in indexRows) {
                    operations.put(indexStoreName, indexRow, key.bytes)
                }
                for (row in uniqueRows) {
                    operations.put(uniqueStoreName, row.uniqueKey, row.keyBytes)
                }
                if (keepAllVersions) {
                    operations.addHistoricSnapshot(
                        historicTableStoreName,
                        key.bytes,
                        version.timestamp,
                        IndexedDbRecordMeta(version.timestamp, version.timestamp, isDeleted),
                        tableRows.map { (rowKey, rowValue) -> tableQualifierFromRowKey(rowKey, key.bytes) to rowValue },
                    )
                    operations.addHistoricIndexRows(historicIndexStoreName, historicIndexCleanupStoreName, key.bytes, indexRows, version.timestamp, active = !isDeleted)
                    operations.addHistoricUniqueRows(historicUniqueStoreName, historicUniqueCleanupStoreName, uniqueRows, version.timestamp, active = !isDeleted)
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

                val update = Update.Addition(request.dataModel, key, version.timestamp, values)
                commitIndexedDbUpdate(operations, update, currentSnapshot)
                statuses += AddSuccess(key, version.timestamp, emptyList())
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
