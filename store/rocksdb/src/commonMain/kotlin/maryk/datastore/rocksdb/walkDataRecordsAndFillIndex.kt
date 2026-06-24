package maryk.datastore.rocksdb

import maryk.core.extensions.bytes.toByteArray
import maryk.core.properties.definitions.index.IsIndexable
import maryk.datastore.rocksdb.processors.EMPTY_ARRAY
import maryk.datastore.rocksdb.processors.FALSE_ARRAY
import maryk.datastore.rocksdb.processors.HistoricStoreIndexValuesWalker
import maryk.datastore.rocksdb.processors.StoreValuesGetter
import maryk.datastore.rocksdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.rocksdb.processors.helpers.readVersionBytesIfExact
import maryk.datastore.rocksdb.processors.helpers.setIndexValue

/**
 * Walks all existing data records for [columnFamilies] of model in [dataStore]
 * Will index any [indexesToIndex] with relevant values
 */
internal fun walkDataRecordsAndFillIndex(
    dataStore: RocksDBDataStore,
    columnFamilies: TableColumnFamilies,
    indexesToIndex: List<IsIndexable>
) {
    Transaction(dataStore).use { transaction ->
        transaction.getIterator(dataStore.defaultReadOptions, columnFamilies.keys).use { iterator ->
            val storeGetter = StoreValuesGetter(
                null,
                dataStore.db,
                columnFamilies,
                dataStore.defaultReadOptions,
                captureVersion = true,
                decryptValue = dataStore::decryptValueIfNeeded
            )
            val historicStoreIndexValuesWalker = if (columnFamilies is HistoricTableColumnFamilies) {
                HistoricStoreIndexValuesWalker(
                    columnFamilies,
                    dataStore.defaultReadOptions
                )
            } else null

            try {
                // Go to the start of the column family
                iterator.seek(byteArrayOf())

                while (iterator.isValid()) {
                    val key = iterator.key()
                    val currentVersion = iterator.value().readVersionBytesIfExact()
                    if (currentVersion == null) {
                        iterator.next()
                        continue
                    }

                    storeGetter.moveToKey(key)

                    for (index in indexesToIndex) {
                        storeGetter.lastVersion = null
                        // Store non-historic value
                        index.toStorageByteArraysForIndex(storeGetter, key).forEach { indexValue ->
                            setIndexValue(
                                transaction,
                                columnFamilies,
                                index.referenceStorageByteArray.bytes,
                                indexValue,
                                (storeGetter.lastVersion ?: currentVersion).toByteArray()
                            )
                        }

                        // Process historical values for historical index
                        if (columnFamilies is HistoricTableColumnFamilies) {
                            var futureHistoricReference: ByteArray? = null

                            historicStoreIndexValuesWalker?.walkHistoricalValuesForIndexKeys(
                                key,
                                transaction,
                                index
                            ) { historicReference ->
                                futureHistoricReference?.let {
                                    val newHistoricReference = historicReference.copyOf()
                                    it.copyInto(
                                        destination = newHistoricReference,
                                        destinationOffset = newHistoricReference.size - VERSION_BYTE_SIZE,
                                        startIndex = historicReference.size - VERSION_BYTE_SIZE
                                    )

                                    transaction.put(columnFamilies.historic.index, newHistoricReference, FALSE_ARRAY)
                                }

                                transaction.put(
                                    columnFamilies.historic.index,
                                    historicReference,
                                    EMPTY_ARRAY
                                )

                                futureHistoricReference = historicReference
                            }
                        }
                    }
                    iterator.next()
                }
            } finally {
                historicStoreIndexValuesWalker?.close()
            }
            transaction.commit()
        }
    }
}
