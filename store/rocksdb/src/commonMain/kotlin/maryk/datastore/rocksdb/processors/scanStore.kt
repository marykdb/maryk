package maryk.datastore.rocksdb.processors

import maryk.core.extensions.bytes.toULong
import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.types.Key
import maryk.core.query.orders.Direction
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.requests.IsScanRequest
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.Transaction

internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> scanStore(
    dataStore: RocksDBDataStore,
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    scanRequest: IsScanRequest<DM, P, *>,
    direction: Direction,
    scanRange: KeyScanRanges,
    processStoreValue: (Key<DM>, ULong) -> Unit
) {
    val iterator = transaction.getIterator(dataStore.defaultReadOptions, columnFamilies.keys)

    when (direction) {
        ASC -> {
            for (range in scanRange.ranges) {
                iterator.seek(range.start)

                // Skip if value should not be included
                if (iterator.isValid() && !range.startInclusive && range.start.contentEquals(iterator.key())) {
                    iterator.next()
                }

                var currentSize: UInt = 0u

                while (iterator.isValid()) {
                    @Suppress("UNCHECKED_CAST")
                    val key = scanRequest.dataModel.key(iterator.key()) as Key<DM>

                    if (range.keyOutOfRange(key.bytes)) {
                        break
                    }

                    if (!scanRange.matchesPartials(key.bytes)) {
                        iterator.next()
                        continue
                    }

                    val creationVersion = iterator.value().toULong()

                    if (scanRequest.shouldBeFiltered(transaction, columnFamilies, dataStore.defaultReadOptions, key.bytes, 0, key.size, creationVersion, scanRequest.toVersion)) {
                        iterator.next()
                        continue
                    }

                    processStoreValue(key, creationVersion)

                    // Break when limit is found
                    if (++currentSize == scanRequest.limit) break

                    iterator.next()
                }
            }
        }
        DESC -> {
            for (range in scanRange.ranges.reversed()) {
                range.end?.let { last ->
                    iterator.seekForPrev(last)
                } ?: iterator.seekToLast()

                // Skip if value should not be included
                if (iterator.isValid() && !range.endInclusive && range.end?.contentEquals(iterator.key()) == true) {
                    iterator.prev()
                }

                var currentSize: UInt = 0u

                while (iterator.isValid()) {
                    @Suppress("UNCHECKED_CAST")
                    val key = scanRequest.dataModel.key(iterator.key()) as Key<DM>

                    if (range.keyBeforeStart(key.bytes)) {
                        break
                    }

                    if (!scanRange.matchesPartials(key.bytes)) {
                        iterator.prev()
                        continue
                    }

                    val creationVersion = iterator.value().toULong()

                    if (scanRequest.shouldBeFiltered(transaction, columnFamilies, dataStore.defaultReadOptions, key.bytes, 0, key.size, creationVersion, scanRequest.toVersion)) {
                        iterator.prev()
                        continue
                    }

                    processStoreValue(key, creationVersion)

                    // Break when limit is found
                    if (++currentSize == scanRequest.limit) break

                    iterator.prev()
                }
            }
        }
    }

    iterator.close()
}
