package maryk.datastore.rocksdb.processors

import maryk.core.extensions.bytes.toULong
import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.types.Key
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.requests.IsScanRequest
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.readCreationVersion
import maryk.datastore.shared.ScanType.IndexScan
import maryk.lib.extensions.compare.nextByteInSameLength
import maryk.lib.extensions.compare.prevByteInSameLength
import maryk.rocksdb.Transaction

internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> scanIndex(
    dataStore: RocksDBDataStore,
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    scanRequest: IsScanRequest<DM, P, *>,
    indexScan: IndexScan,
    keyScanRange: KeyScanRanges,
    processStoreValue: (Key<DM>, ULong) -> Unit
) {
    val indexReference = indexScan.index.toReferenceStorageByteArray()

    val indexScanRange = indexScan.index.createScanRange(scanRequest.where, keyScanRange)

    scanRequest.toVersion?.let {
        TODO("SCAN index toVersion")
    }

    val iterator = transaction.getIterator(dataStore.defaultReadOptions, columnFamilies.index)

    when (indexScan.direction) {
        ASC -> {
            for (indexRange in indexScanRange.ranges) {
                indexRange.start.let { startRange ->
                    val startRangeToSearch = if (indexRange.startInclusive) {
                        startRange
                    } else {
                        // Go past start range if not inclusive.
                        startRange.nextByteInSameLength()
                    }

                    iterator.seek(byteArrayOf(*indexReference, *startRangeToSearch))
                }

                var currentSize: UInt = 0u

                while (iterator.isValid()) {
                    val indexRecord = iterator.key()
                    val keySize = scanRequest.dataModel.keyByteSize
                    val keyOffset = indexRecord.size - keySize
                    val valueOffset = indexReference.size
                    val valueSize = indexRecord.size - keySize - indexReference.size

                    if (indexRange.keyOutOfRange(indexRecord, valueOffset, valueSize)) {
                        break
                    }

                    if (!indexScanRange.matchesPartials(indexRecord, valueOffset, valueSize)) {
                        iterator.next()
                        continue
                    }

                    val setAtVersion = iterator.value().toULong()

                    if (scanRequest.shouldBeFiltered(transaction, columnFamilies, dataStore.defaultReadOptions, indexRecord, keyOffset, keySize, setAtVersion, scanRequest.toVersion)) {
                        iterator.next()
                        continue
                    }
                    var readIndex = keyOffset
                    @Suppress("UNCHECKED_CAST")
                    val key = scanRequest.dataModel.key {
                        indexRecord[readIndex++]
                    } as Key<DM>

                    processStoreValue(key, setAtVersion)

                    // Break when limit is found
                    if (++currentSize == scanRequest.limit) break

                    iterator.next()
                }
            }
        }
        DESC -> {
            for (indexRange in indexScanRange.ranges.reversed()) {
                indexRange.end?.let { endRange ->
                    if (endRange.isEmpty()) {
                        iterator.seekToLast()
                    } else {
                        val endRangeToSearch = if (indexRange.endInclusive) {
                            endRange.nextByteInSameLength()
                        } else {
                            endRange
                        }

                        if (indexRange.endInclusive && endRangeToSearch === endRange) {
                            // If was not highered it was not possible so scan to lastIndex
                            iterator.seekToLast()
                        } else {
                            val endRangeToSeek = if (indexRange.endInclusive) {
                                endRangeToSearch
                            } else {
                                // Go past start range if not inclusive.
                                endRangeToSearch.prevByteInSameLength()
                            }
                            iterator.seekForPrev(byteArrayOf(*indexReference, *endRangeToSeek))
                        }
                    }
                }

                var currentSize: UInt = 0u

                while (iterator.isValid()) {
                    val indexRecord = iterator.key()
                    val keySize = scanRequest.dataModel.keyByteSize
                    val keyOffset = indexRecord.size - keySize
                    val valueOffset = indexReference.size
                    val valueSize = indexRecord.size - keySize - indexReference.size

                    if (indexRange.keyBeforeStart(indexRecord, valueOffset, valueSize)) {
                        break
                    }

                    if (!indexScanRange.matchesPartials(indexRecord, valueOffset, valueSize)) {
                        iterator.prev()
                        continue
                    }

                    val setAtVersion = iterator.value().toULong()

                    if (scanRequest.shouldBeFiltered(transaction, columnFamilies, dataStore.defaultReadOptions, indexRecord, keyOffset, keySize, setAtVersion, scanRequest.toVersion)) {
                        iterator.prev()
                        continue
                    }

                    var readIndex = keyOffset
                    @Suppress("UNCHECKED_CAST")
                    val key = scanRequest.dataModel.key {
                        indexRecord[readIndex++]
                    } as Key<DM>

                    readCreationVersion(transaction, columnFamilies, dataStore.defaultReadOptions, key)?.let { createdVersion ->
                        processStoreValue(key, createdVersion)
                    }

                    // Break when limit is found
                    if (++currentSize == scanRequest.limit) break

                    iterator.prev()
                }
            }
        }
    }
}
