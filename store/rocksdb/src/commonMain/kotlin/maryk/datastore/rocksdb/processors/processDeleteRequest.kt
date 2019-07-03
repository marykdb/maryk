package maryk.datastore.rocksdb.processors

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
import maryk.datastore.rocksdb.processors.helpers.deleteIndexValue
import maryk.datastore.rocksdb.processors.helpers.deleteUniqueIndexValue
import maryk.datastore.rocksdb.processors.helpers.setLatestVersion
import maryk.datastore.shared.StoreAction
import maryk.lib.extensions.compare.matchPart
import maryk.lib.extensions.compare.nextByteInSameLength
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.Transaction
import maryk.rocksdb.use

internal typealias DeleteStoreAction<DM, P> = StoreAction<DM, P, DeleteRequest<DM>, DeleteResponse<DM>>
internal typealias AnyDeleteStoreAction = DeleteStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes a DeleteRequest in a [storeAction] into a [dataStore] */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processDeleteRequest(
    storeAction: DeleteStoreAction<DM, P>,
    dataStore: RocksDBDataStore
) {
    val deleteRequest = storeAction.request
    val statuses = mutableListOf<IsDeleteResponseStatus<DM>>()

    if (deleteRequest.keys.isNotEmpty()) {
        val version = storeAction.version
        val columnFamilies = dataStore.getColumnFamilies(storeAction.dbIndex)

        for (key in deleteRequest.keys) {
            try {
                val mayExist = dataStore.db.keyMayExist(columnFamilies.table, key.bytes, StringBuilder())

                val exists = if (mayExist) {
                    // Really check if item exists
                    dataStore.db.get(columnFamilies.table, key.bytes) != null
                } else false

                val status: IsDeleteResponseStatus<DM> = when {
                    exists -> {
                        dataStore.db.beginTransaction(dataStore.defaultWriteOptions).use { transaction ->
                            // Create version bytes
                            val versionBytes = HLC.toStorageBytes(version)

                            for (ref in dataStore.getUniqueIndices(
                                storeAction.dbIndex,
                                columnFamilies.unique
                            )) {
                                val reference = byteArrayOf(*key.bytes, *ref)
                                val value = transaction.get(columnFamilies.table, dataStore.defaultReadOptions, reference)

                                if (value != null) {
                                    deleteUniqueIndexValue(
                                        transaction,
                                        columnFamilies,
                                        ref,
                                        value,
                                        ULong.SIZE_BYTES,
                                        value.size - ULong.SIZE_BYTES,
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
                                        reference,
                                        ref
                                    )
                                }
                            }

                            val valuesGetter = StoreValuesGetter(
                                key,
                                transaction,
                                columnFamilies,
                                dataStore.defaultReadOptions
                            )

                            // Delete indexed values
                            deleteRequest.dataModel.indices?.forEach { indexable ->
                                val indexReference = indexable.toReferenceStorageByteArray()
                                val valueAndKeyBytes =
                                    indexable.toStorageByteArrayForIndex(valuesGetter, key.bytes)
                                        ?: return@forEach // skip if no complete values to index are found
                                deleteIndexValue(
                                    transaction,
                                    columnFamilies,
                                    indexReference,
                                    valueAndKeyBytes,
                                    versionBytes,
                                    deleteRequest.hardDelete
                                )
                            }

                            if (deleteRequest.hardDelete) {
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
    reference: ByteArray,
    ref: ByteArray
) {
    transaction.getIterator(readOptions, columnFamilies.historic.table).use { iterator ->
        iterator.seek(reference)

        while (iterator.isValid()) {
            val qualifier = iterator.key()

            if (qualifier.matchPart(0, reference)) {
                val valueBytes = iterator.value()
                val historicReference = ByteArray(ref.size + valueBytes.size + ULong.SIZE_BYTES)
                ref.copyInto(historicReference)
                valueBytes.copyInto(historicReference, ref.size)
                qualifier.copyInto(historicReference, valueBytes.size + ref.size, qualifier.size - ULong.SIZE_BYTES)

                transaction.delete(columnFamilies.historic.unique, historicReference)
                iterator.next()
            } else {
                // Not same value anymore
                break
            }
        }
    }
}
