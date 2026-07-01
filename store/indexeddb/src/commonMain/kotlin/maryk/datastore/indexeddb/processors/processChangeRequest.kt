package maryk.datastore.indexeddb.processors

import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.types.Bytes
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.IndexChange
import maryk.core.query.changes.IndexDelete
import maryk.core.query.changes.IndexUpdate
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.IsChangeResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.indexeddb.IndexedDbDataStore
import maryk.datastore.indexeddb.IndexedDbRecordMeta
import maryk.datastore.indexeddb.IndexedDbTransactionMode
import maryk.datastore.indexeddb.IndexedDbWriteOperation
import maryk.datastore.indexeddb.decodeRecordMeta
import maryk.datastore.indexeddb.tableQualifierFromRowKey
import maryk.datastore.shared.UniqueException
import maryk.datastore.shared.updates.Update

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.processChangeRequest(
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

                val currentValues = readCurrentValuesDecrypted(byteStore, request.dataModel, tableStoreName, keyBytes, null)
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

