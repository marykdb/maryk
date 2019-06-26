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
import maryk.datastore.rocksdb.processors.helpers.deleteUniqueIndexValue
import maryk.datastore.rocksdb.processors.helpers.setLatestVersion
import maryk.datastore.shared.StoreAction
import maryk.lib.extensions.compare.nextByteInSameLength
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.WriteOptions
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
                        WriteOptions().use { writeOptions ->
                            dataStore.db.beginTransaction(writeOptions).use { transaction ->
                                // Create version bytes
                                val versionBytes = HLC.toStorageBytes(version)
                                ReadOptions().use { readOptions ->
                                    dataStore.getUniqueIndices(storeAction.dbIndex, columnFamilies.unique).forEach { ref ->
                                        val value = transaction.get(columnFamilies.table, readOptions, byteArrayOf(*key.bytes, *ref))

                                        if (value != null) {
                                            val newValue = value.copyOfRange(ULong.SIZE_BYTES, value.size)
                                            deleteUniqueIndexValue(
                                                transaction,
                                                columnFamilies,
                                                ref,
                                                newValue,
                                                versionBytes
                                            )
                                        }
                                    }

//                                    // Delete indexed values
//                                    deleteRequest.dataModel.indices?.forEach { indexable ->
//                                        val indexRef = indexable.toReferenceStorageByteArray()
//                                    }
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
                                        val historicReference = byteArrayOf(*key.bytes, SOFT_DELETE_INDICATOR, *versionBytes)
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
