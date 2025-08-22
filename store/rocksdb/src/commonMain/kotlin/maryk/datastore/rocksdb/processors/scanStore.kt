package maryk.datastore.rocksdb.processors

import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.properties.types.Key
import maryk.core.query.orders.Direction
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.requests.IsScanRequest
import maryk.core.query.responses.DataFetchType
import maryk.core.query.responses.FetchByTableScan
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.readVersionBytes

internal fun <DM : IsRootDataModel> RocksDBDataStore.scanStore(
    dbAccessor: DBAccessor,
    columnFamilies: TableColumnFamilies,
    scanRequest: IsScanRequest<DM, *>,
    direction: Direction,
    scanRange: KeyScanRanges,
    processStoreValue: (Key<DM>, ULong, ByteArray?) -> Unit
): DataFetchType {
    val iterator = dbAccessor.getIterator(defaultReadOptions, columnFamilies.keys)

    var overallStartKey: ByteArray?
    var overallEndKey: ByteArray?

    when (direction) {
        ASC -> {
            overallStartKey = scanRange.ranges.first().getAscendingStartKey(scanRange.startKey, scanRange.includeStart)
            overallEndKey = scanRange.ranges.last().getDescendingStartKey()

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

                    if (scanRequest.shouldBeFiltered(dbAccessor, columnFamilies, defaultReadOptions, key.bytes, 0, key.size, creationVersion, scanRequest.toVersion)) {
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
            overallStartKey = scanRange.ranges.first().getDescendingStartKey(scanRange.startKey, scanRange.includeStart)
            overallEndKey = scanRange.ranges.last().getAscendingStartKey()

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

                    if (scanRequest.shouldBeFiltered(dbAccessor, columnFamilies, defaultReadOptions, key.bytes, 0, key.size, creationVersion, scanRequest.toVersion)) {
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

    return FetchByTableScan(
        direction = direction,
        startKey = overallStartKey,
        stopKey = overallEndKey,
    )
}
