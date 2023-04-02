package maryk.datastore.rocksdb.processors

import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.properties.types.Key
import maryk.core.query.orders.Direction
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.requests.IsScanRequest
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.readVersionBytes

internal fun <DM : IsRootDataModel> scanStore(
    dataStore: RocksDBDataStore,
    dbAccessor: DBAccessor,
    columnFamilies: TableColumnFamilies,
    scanRequest: IsScanRequest<DM, *>,
    direction: Direction,
    scanRange: KeyScanRanges,
    processStoreValue: (Key<DM>, ULong, ByteArray?) -> Unit
) {
    val iterator = dbAccessor.getIterator(dataStore.defaultReadOptions, columnFamilies.keys)

    when (direction) {
        ASC -> {
            for (range in scanRange.ranges) {
                val startKey = range.getAscendingStartKey(scanRange.startKey, scanRange.includeStart)

                iterator.seek(startKey)

                // Skip if value should not be included
                if (iterator.isValid() && !range.startInclusive && range.start.contentEquals(iterator.key())) {
                    iterator.next()
                }

                var currentSize = 0u

                while (iterator.isValid()) {
                    val key = scanRequest.dataModel.key(iterator.key())

                    if (range.keyOutOfRange(key.bytes)) {
                        break
                    }

                    if (!scanRange.matchesPartials(key.bytes)) {
                        iterator.next()
                        continue
                    }

                    val creationVersion = iterator.value().readVersionBytes()

                    if (scanRequest.shouldBeFiltered(dbAccessor, columnFamilies, dataStore.defaultReadOptions, key.bytes, 0, key.size, creationVersion, scanRequest.toVersion)) {
                        iterator.next()
                        continue
                    }

                    processStoreValue(key, creationVersion, null)

                    // Break when limit is found
                    if (++currentSize == scanRequest.limit) break

                    iterator.next()
                }
            }
        }
        DESC -> {
            for (range in scanRange.ranges.reversed()) {
                val lastKey = range.getDescendingStartKey(scanRange.startKey, scanRange.includeStart)

                lastKey?.let { last ->
                    iterator.seekForPrev(last)
                } ?: iterator.seekToLast()

                // Skip if value should not be included
                if (iterator.isValid() && !range.endInclusive && range.end?.contentEquals(iterator.key()) == true) {
                    iterator.prev()
                }

                var currentSize = 0u

                while (iterator.isValid()) {
                    val key = scanRequest.dataModel.key(iterator.key())

                    if (range.keyBeforeStart(key.bytes)) {
                        break
                    }

                    if (!scanRange.matchesPartials(key.bytes)) {
                        iterator.prev()
                        continue
                    }

                    val creationVersion = iterator.value().readVersionBytes()

                    if (scanRequest.shouldBeFiltered(dbAccessor, columnFamilies, dataStore.defaultReadOptions, key.bytes, 0, key.size, creationVersion, scanRequest.toVersion)) {
                        iterator.prev()
                        continue
                    }

                    processStoreValue(key, creationVersion, null)

                    // Break when limit is found
                    if (++currentSize == scanRequest.limit) break

                    iterator.prev()
                }
            }
        }
    }

    iterator.close()
}
