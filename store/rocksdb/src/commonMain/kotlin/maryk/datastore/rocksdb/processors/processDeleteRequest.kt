package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.channels.SendChannel
import maryk.core.clock.HLC
import maryk.core.extensions.bytes.invert
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.IsDeleteResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.Transaction
import maryk.datastore.rocksdb.processors.helpers.deleteIndexValue
import maryk.datastore.rocksdb.processors.helpers.deleteUniqueIndexValue
import maryk.datastore.rocksdb.processors.helpers.setLatestVersion
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.Update
import maryk.datastore.shared.updates.Update.Deletion
import maryk.lib.extensions.compare.matchPart
import maryk.lib.extensions.compare.nextByteInSameLength
import maryk.lib.recyclableByteArray
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.rocksDBNotFound
import maryk.rocksdb.use

internal typealias DeleteStoreAction<DM, P> = StoreAction<DM, P, DeleteRequest<DM>, DeleteResponse<DM>>
internal typealias AnyDeleteStoreAction = DeleteStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes a DeleteRequest in a [storeAction] into a [dataStore] */
internal suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processDeleteRequest(
    storeAction: DeleteStoreAction<DM, P>,
    dataStore: RocksDBDataStore,
    updateSendChannel: SendChannel<Update<DM>>
) {
    val deleteRequest = storeAction.request
    val statuses = mutableListOf<IsDeleteResponseStatus<DM>>()

    if (deleteRequest.keys.isNotEmpty()) {
        val version = storeAction.version
        val dbIndex = dataStore.getDataModelId(deleteRequest.dataModel)
        val columnFamilies = dataStore.getColumnFamilies(dbIndex)

        // Delete it from history if it is a hard deletion
        val historicStoreIndexValuesWalker = if (deleteRequest.hardDelete && columnFamilies is HistoricTableColumnFamilies) {
            HistoricStoreIndexValuesWalker(columnFamilies, dataStore.defaultReadOptions)
        } else null

        for (key in deleteRequest.keys) {
            try {
                val mayExist = dataStore.db.keyMayExist(columnFamilies.keys, key.bytes, null)

                val exists = if (mayExist) {
                    // Really check if item exists
                    dataStore.db.get(columnFamilies.table, key.bytes, recyclableByteArray) != rocksDBNotFound
                } else false

                val status: IsDeleteResponseStatus<DM> = when {
                    exists -> {
                        Transaction(dataStore).use { transaction ->
                            // Create version bytes
                            val versionBytes = HLC.toStorageBytes(version)

                            for (reference in dataStore.getUniqueIndices(
                                dbIndex, columnFamilies.unique
                            )) {
                                val referenceAndKey = byteArrayOf(*key.bytes, *reference)
                                val valueLength = transaction.get(columnFamilies.table, dataStore.defaultReadOptions, referenceAndKey, recyclableByteArray)

                                if (valueLength != rocksDBNotFound) {
                                    val value = if (valueLength > recyclableByteArray.size) {
                                        // Large value which did not fit in recyclableByteArray
                                        transaction.get(columnFamilies.table, dataStore.defaultReadOptions, referenceAndKey)!!
                                    } else recyclableByteArray

                                    deleteUniqueIndexValue(
                                        transaction,
                                        columnFamilies,
                                        reference,
                                        value,
                                        ULong.SIZE_BYTES,
                                        valueLength - ULong.SIZE_BYTES,
                                        versionBytes,
                                        deleteRequest.hardDelete
                                    )
                                }

                                // Delete it from history if it is a hard delete
                                if (deleteRequest.hardDelete && columnFamilies is HistoricTableColumnFamilies) {
                                    hardDeleteHistoricalUniqueValues(
                                        transaction,
                                        columnFamilies,
                                        dataStore.defaultReadOptions,
                                        referenceAndKey,
                                        reference
                                    )
                                }
                            }

                            // Delete indexed values
                            deleteRequest.dataModel.indices?.let { indices ->
                                val valuesGetter = DBAccessorValuesGetter(
                                    key,
                                    transaction,
                                    columnFamilies,
                                    dataStore.defaultReadOptions
                                )

                                indices.forEach { indexable ->
                                    val indexReference = indexable.toReferenceStorageByteArray()
                                    val valueAndKeyBytes = indexable.toStorageByteArrayForIndex(valuesGetter, key.bytes)
                                        ?: return@forEach // skip if no complete values to index are found
                                    deleteIndexValue(transaction, columnFamilies, indexReference, valueAndKeyBytes, versionBytes, deleteRequest.hardDelete)

                                    // Delete all historic values if historicStoreIndexValuesWalker was set
                                    historicStoreIndexValuesWalker?.walkIndexHistory(key, transaction, indexable, indexReference) { historicReference ->
                                        transaction.delete(
                                            historicStoreIndexValuesWalker.columnFamilies.historic.index,
                                            historicReference
                                        )
                                    }
                                }
                            }

                            if (deleteRequest.hardDelete) {
                                dataStore.deleteCacheForKey(dbIndex, key)

                                dataStore.db.delete(columnFamilies.keys, key.bytes)
                                dataStore.db.deleteRange(
                                    columnFamilies.table,
                                    key.bytes,
                                    key.bytes.nextByteInSameLength()
                                )
                                if (columnFamilies is HistoricTableColumnFamilies) {
                                    dataStore.db.deleteRange(
                                        columnFamilies.historic.table,
                                        key.bytes,
                                        key.bytes.nextByteInSameLength()
                                    )
                                }
                            } else {
                                setLatestVersion(transaction, columnFamilies, key, versionBytes)

                                transaction.put(
                                    columnFamilies.table,
                                    byteArrayOf(*key.bytes, SOFT_DELETE_INDICATOR),
                                    byteArrayOf(*versionBytes, TRUE)
                                )

                                if (columnFamilies is HistoricTableColumnFamilies) {
                                    val historicReference =
                                        byteArrayOf(*key.bytes, SOFT_DELETE_INDICATOR, *versionBytes)
                                    // Invert so the time is sorted in reverse order with newest on top
                                    historicReference.invert(historicReference.size - versionBytes.size)

                                    transaction.put(
                                        columnFamilies.historic.table,
                                        historicReference,
                                        TRUE_ARRAY
                                    )
                                }

                            }
                            transaction.commit()
                        }

                        updateSendChannel.send(
                            Deletion(deleteRequest.dataModel, key, version, deleteRequest.hardDelete)
                        )

                        DeleteSuccess(version.timestamp)
                    }
                    else -> DoesNotExist(key)
                }

                statuses.add(status)
            } catch (e: Throwable) {
                statuses.add(ServerFail(e.toString(), e))
            }
        }
    }

    storeAction.response.complete(
        DeleteResponse(
            storeAction.request.dataModel,
            statuses
        )
    )
}

/** Take care of hard deleting the historical unique values */
private fun hardDeleteHistoricalUniqueValues(
    transaction: Transaction,
    columnFamilies: HistoricTableColumnFamilies,
    readOptions: ReadOptions,
    referenceAndKey: ByteArray,
    reference: ByteArray
) {
    transaction.getIterator(readOptions, columnFamilies.historic.table).use { iterator ->
        // Add empty version so iterator works correctly
        iterator.seek(referenceAndKey.copyOf(referenceAndKey.size + 8))

        while (iterator.isValid()) {
            val qualifier = iterator.key()

            if (qualifier.matchPart(0, referenceAndKey)) {
                val valueBytes = iterator.value()
                val historicReference = ByteArray(reference.size + valueBytes.size + ULong.SIZE_BYTES)
                reference.copyInto(historicReference)
                valueBytes.copyInto(historicReference, reference.size)
                qualifier.copyInto(historicReference, valueBytes.size + reference.size, qualifier.size - ULong.SIZE_BYTES)

                transaction.delete(columnFamilies.historic.unique, historicReference)
                iterator.next()
            } else {
                // Not same value anymore
                break
            }
        }
    }
}
