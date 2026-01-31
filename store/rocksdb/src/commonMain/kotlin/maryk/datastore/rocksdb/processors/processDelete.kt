package maryk.datastore.rocksdb.processors

import maryk.core.clock.HLC
import maryk.core.extensions.bytes.invert
import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.IsDeleteResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.Transaction
import maryk.datastore.rocksdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.rocksdb.processors.helpers.deleteIndexValue
import maryk.datastore.rocksdb.processors.helpers.deleteUniqueIndexValue
import maryk.datastore.rocksdb.processors.helpers.setLatestVersion
import maryk.datastore.rocksdb.withTransaction
import maryk.datastore.shared.Cache
import maryk.datastore.shared.updates.Update.Deletion
import maryk.lib.bytes.combineToByteArray
import maryk.lib.extensions.compare.matchPart
import maryk.lib.extensions.compare.nextByteInSameLength
import maryk.lib.recyclableByteArray
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.rocksDBNotFound

internal suspend fun <DM : IsRootDataModel> RocksDBDataStore.processDelete(
    dataModel: DM,
    columnFamilies: TableColumnFamilies,
    key: Key<DM>,
    version: HLC,
    dbIndex: UInt,
    hardDelete: Boolean,
    historicStoreIndexValuesWalker: HistoricStoreIndexValuesWalker?,
    cache: Cache,
): IsDeleteResponseStatus<DM> = try {
    val mayExist = db.keyMayExist(columnFamilies.keys, key.bytes, null)

    val exists = if (mayExist) {
        // Really check if item exists
        db.get(columnFamilies.table, key.bytes, recyclableByteArray) != rocksDBNotFound
    } else false

    when {
        exists -> {
            withTransaction { transaction ->
                // Create version bytes
                val versionBytes = HLC.toStorageBytes(version)

                for (reference in getUniqueIndices(
                    dbIndex, columnFamilies.unique
                )) {
                    val referenceAndKey = key.bytes + reference
                    val valueLength = transaction.get(
                        columnFamilies.table,
                        defaultReadOptions,
                        referenceAndKey,
                        recyclableByteArray
                    )

                    if (valueLength != rocksDBNotFound) {
                        val value = if (valueLength > recyclableByteArray.size) {
                            // Large value which did not fit in recyclableByteArray
                            transaction.get(columnFamilies.table, defaultReadOptions, referenceAndKey)!!
                        } else recyclableByteArray

                        deleteUniqueIndexValue(
                            transaction,
                            columnFamilies,
                            reference,
                            value,
                            VERSION_BYTE_SIZE,
                            valueLength - VERSION_BYTE_SIZE,
                            versionBytes,
                            hardDelete
                        )
                    }

                    // Delete it from history if it is a hard delete
                    if (hardDelete && columnFamilies is HistoricTableColumnFamilies) {
                        hardDeleteHistoricalUniqueValues(
                            transaction,
                            columnFamilies,
                            defaultReadOptions,
                            referenceAndKey,
                            reference
                        )
                    }
                }

                // Delete indexed values
                dataModel.Meta.indexes?.let { indexes ->
                    val valuesGetter = DBAccessorStoreValuesGetter(columnFamilies, defaultReadOptions)
                    valuesGetter.moveToKey(key.bytes, transaction)

                    indexes.forEach { indexable ->
                        val indexReference = indexable.referenceStorageByteArray.bytes
                        val valueAndKeyBytes = indexable.toStorageByteArrayForIndex(valuesGetter, key.bytes)
                            ?: return@forEach // skip if no complete values to index are found

                        deleteIndexValue(
                            transaction,
                            columnFamilies,
                            indexReference,
                            valueAndKeyBytes,
                            versionBytes,
                            hardDelete
                        )

                        // Delete all historic values if historicStoreIndexValuesWalker was set
                        // This is only the case with hard deletes
                        historicStoreIndexValuesWalker?.walkHistoricalValuesForIndexKeys(
                            key.bytes,
                            transaction,
                            indexable
                        ) { historicReference ->
                            transaction.delete(
                                historicStoreIndexValuesWalker.columnFamilies.historic.index,
                                historicReference
                            )
                        }
                    }
                }

                if (hardDelete) {
                    cache.delete(dbIndex, key)

                    db.delete(columnFamilies.keys, key.bytes)
                    db.deleteRange(
                        columnFamilies.table,
                        key.bytes,
                        key.bytes.nextByteInSameLength()
                    )
                    if (columnFamilies is HistoricTableColumnFamilies) {
                        db.deleteRange(
                            columnFamilies.historic.table,
                            key.bytes,
                            key.bytes.nextByteInSameLength()
                        )
                    }
                } else {
                    setLatestVersion(transaction, columnFamilies, key, versionBytes)

                    transaction.put(
                        columnFamilies.table,
                        key.bytes + SOFT_DELETE_INDICATOR,
                        versionBytes + TRUE
                    )

                    if (columnFamilies is HistoricTableColumnFamilies) {
                        val historicReference =
                            combineToByteArray(key.bytes, SOFT_DELETE_INDICATOR, versionBytes)
                        // Invert so the time is sorted in reverse order with newest on top
                        historicReference.invert(historicReference.size - versionBytes.size)

                        transaction.put(
                            columnFamilies.historic.table,
                            historicReference,
                            byteArrayOf(TRUE)
                        )
                    }

                }
                transaction.commit()
            }

            emitUpdate(Deletion(dataModel, key, version.timestamp, hardDelete))

            DeleteSuccess(version.timestamp)
        }
        else -> DoesNotExist(key)
    }
} catch (e: Throwable) {
    ServerFail(e.toString(), e)
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
        iterator.seek(referenceAndKey.copyOf(referenceAndKey.size + VERSION_BYTE_SIZE))

        while (iterator.isValid()) {
            val qualifier = iterator.key()

            if (qualifier.matchPart(0, referenceAndKey)) {
                val valueBytes = iterator.value()
                val historicReference = ByteArray(reference.size + valueBytes.size + VERSION_BYTE_SIZE)
                reference.copyInto(historicReference)
                valueBytes.copyInto(historicReference, reference.size)
                qualifier.copyInto(historicReference, valueBytes.size + reference.size, qualifier.size - VERSION_BYTE_SIZE)

                transaction.delete(columnFamilies.historic.unique, historicReference)
                iterator.next()
            } else {
                // Not same value anymore
                break
            }
        }
    }
}
