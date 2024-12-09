package maryk.datastore.rocksdb.processors

import maryk.core.exceptions.StorageException
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.processors.datastore.scanRange.IndexableScanRanges
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.types.Key
import maryk.core.query.orders.Direction
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.requests.IsScanRequest
import maryk.core.query.responses.DataFetchType
import maryk.core.query.responses.FetchByIndexScan
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.DBIterator
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.rocksdb.processors.helpers.readCreationVersion
import maryk.datastore.rocksdb.processors.helpers.toReversedVersionBytes
import maryk.datastore.shared.ScanType.IndexScan
import maryk.lib.extensions.compare.compareToWithOffsetLength
import maryk.lib.extensions.compare.matchPart
import maryk.lib.extensions.compare.nextByteInSameLength
import org.rocksdb.ReadOptions

internal fun <DM : IsRootDataModel> scanIndex(
    dataStore: RocksDBDataStore,
    dbAccessor: DBAccessor,
    columnFamilies: TableColumnFamilies,
    scanRequest: IsScanRequest<DM, *>,
    indexScan: IndexScan,
    keyScanRange: KeyScanRanges,
    processStoreValue: (Key<DM>, ULong, ByteArray?) -> Unit
): DataFetchType {
    val indexReference = indexScan.index.referenceStorageByteArray.bytes

    val startKey = scanRequest.startKey?.let { startKey ->
        val startValuesGetter = DBAccessorStoreValuesGetter(columnFamilies, dataStore.defaultReadOptions)
        startValuesGetter.moveToKey(startKey.bytes, dbAccessor, scanRequest.toVersion)
        indexScan.index.toStorageByteArrayForIndex(startValuesGetter, startKey.bytes)
    }

    var overallStartKey: ByteArray? = null
    var overallStopKey: ByteArray? = null

    val indexScanRange = indexScan.index.createScanRange(scanRequest.where, keyScanRange)

    val indexColumnHandle = if(scanRequest.toVersion == null) {
        columnFamilies.index
    } else {
        (columnFamilies as? HistoricTableColumnFamilies)?.historic?.index
            ?: throw StorageException("No historic table stored so toVersion in query cannot be processed")
    }

    val iterator = dbAccessor.getIterator(dataStore.defaultReadOptions, indexColumnHandle)

    val keySize = scanRequest.dataModel.Meta.keyByteSize
    val valueOffset = indexReference.size
    val versionSize = if(scanRequest.toVersion != null) VERSION_BYTE_SIZE else 0

    when (indexScan.direction) {
        ASC -> {
            overallStartKey = indexScanRange.ranges.first().getAscendingStartKey(startKey, keyScanRange.includeStart)
            overallStopKey = indexScanRange.ranges.last().getDescendingStartKey()

            for (indexRange in indexScanRange.ranges) {
                val indexStartKey = indexRange.getAscendingStartKey(startKey, keyScanRange.includeStart)

                iterator.seek(indexReference + indexStartKey)

                checkAndProcess(
                    dbAccessor,
                    columnFamilies,
                    dataStore.defaultReadOptions,
                    iterator,
                    keySize,
                    scanRequest,
                    indexScanRange,
                    versionSize,
                    valueOffset,
                    processStoreValue,
                    { indexRecord, valueSize ->
                        !indexRecord.matchPart(0, indexReference) ||
                        indexRange.keyOutOfRange(indexRecord, valueOffset, valueSize)
                    },
                    createVersionChecker(scanRequest.toVersion, iterator, indexScan.direction),
                    createGotoNext(scanRequest, iterator, iterator::next)
                )
            }
        }
        DESC -> {
            overallStartKey = indexScanRange.ranges.first().getDescendingStartKey(startKey, keyScanRange.includeStart)
            overallStopKey = indexScanRange.ranges.last().getAscendingStartKey()

            for (indexRange in indexScanRange.ranges.reversed()) {
                val indexStartKey = indexRange.getDescendingStartKey(startKey, keyScanRange.includeStart)?.let {
                    // If was not highered it was not possible so scan to lastIndex
                    if (indexRange.endInclusive && indexRange.end === it) byteArrayOf() else it
                }

                if (indexStartKey == null || indexStartKey.isEmpty()) {
                    iterator.seek(indexReference.nextByteInSameLength())
                    if (!iterator.isValid()) {
                        iterator.seekToLast()
                    }
                } else {
                    iterator.seekForPrev(indexReference + indexStartKey)
                }

                checkAndProcess(
                    dbAccessor,
                    columnFamilies,
                    dataStore.defaultReadOptions,
                    iterator,
                    keySize,
                    scanRequest,
                    indexScanRange,
                    versionSize,
                    valueOffset,
                    processStoreValue,
                    { indexRecord, valueSize ->
                        !indexRecord.matchPart(0, indexReference) ||
                        indexRange.keyBeforeStart(indexRecord, valueOffset, valueSize)
                    },
                    createVersionChecker(scanRequest.toVersion, iterator, indexScan.direction),
                    createGotoNext(scanRequest, iterator, iterator::prev)
                )
            }
        }
    }

    return FetchByIndexScan(
        index = indexScan.index.referenceStorageByteArray.bytes,
        direction = indexScan.direction,
        startKey = overallStartKey,
        stopKey = overallStopKey,
    )
}

/**
 * Create a version checker to see if record has to be skipped or not.
 */
fun createVersionChecker(toVersion: ULong?, iterator: DBIterator, direction: Direction): (ByteArray) -> Boolean =
    if (toVersion == null) {
        { true } // Version is always latest and thus valid, because is scanning on normal table
    } else {
        // Since index stores versions in reverse order, reverse the version here too
        val versionBytesToMatch = toVersion.toReversedVersionBytes()

        when (direction) {
            ASC -> {
                { indexKey ->
                    var validResult = false
                    // Skip all
                    while (iterator.isValid()) {
                        val newKey = iterator.key()

                        if (!newKey.matchPart(0, indexKey, newKey.size, 0, indexKey.size - VERSION_BYTE_SIZE)) {
                            iterator.prev() // Go back to last key, so it can be processed next
                            break // Key does not match anymore so break out
                        }

                        if (versionBytesToMatch.compareToWithOffsetLength(newKey, newKey.size - VERSION_BYTE_SIZE) > 0) {
                            // Continue to older version since key was too new for request
                            iterator.next()
                        } else {
                            // Check if is deleted and skip if so
                            validResult = iterator.value().contentEquals(EMPTY_ARRAY)
                            break
                        }
                    }
                    // Return if a valid non deleted result was found
                    validResult
                }
            }
            DESC -> {
                { indexKey ->
                    var validResult = false

                    // This iterator starts at the first version so has to walk past all valid versions
                    while (iterator.isValid()) {
                        val newKey = iterator.key()
                        // Check if new key matches expected key and otherwise skips out
                        if (newKey.let { !it.matchPart(0, indexKey, it.size - VERSION_BYTE_SIZE, 0, indexKey.size - VERSION_BYTE_SIZE) }) {
                            iterator.next() // Move back iterator so next call will start at right key
                            break
                        }

                        // Continue to newer versions until key is not of a valid version
                        if (versionBytesToMatch.compareToWithOffsetLength(newKey, newKey.size - VERSION_BYTE_SIZE) <= 0) {
                            validResult = iterator.value().contentEquals(EMPTY_ARRAY)
                            iterator.prev()
                        } else {
                            break
                        }
                    }
                    // Return if a valid non deleted result was found
                    validResult
                }
            }
        }
    }

/**
 * Create a handler to go to the next index record to read.
 * If it is a versioned read, skip all index records but with older versions
 * The order depends on what is defined in the [next] function
 */
private fun <DM : IsRootDataModel> createGotoNext(
    scanRequest: IsScanRequest<DM, *>,
    iterator: DBIterator,
    next: () -> Unit
): (ByteArray, Int, Int) -> Unit =
    if (scanRequest.toVersion == null) {
        { _, _, _ -> next() }
    } else {
        { key, keyOffset, keyLength ->
            if (iterator.isValid()) {
                next()
            }
            // Skip all same index/value/key since they are different versions of the same
            while (iterator.isValid() && iterator.key().let { it.matchPart(0, key, it.size, 0, keyOffset + keyLength) }) {
                next()
            }
        }
    }

/** Walk through index and processes any valid keys and versions */
private fun <DM : IsRootDataModel> checkAndProcess(
    dbAccessor: DBAccessor,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    iterator: DBIterator,
    keySize: Int,
    scanRequest: IsScanRequest<DM, *>,
    indexScanRange: IndexableScanRanges,
    versionSize: Int,
    valueOffset: Int,
    processStoreValue: (Key<DM>, ULong, ByteArray?) -> Unit,
    isPastRange: (ByteArray, Int) -> Boolean,
    checkVersion: (ByteArray) -> Boolean,
    next: (ByteArray, Int, Int) -> Unit
) {
    var currentSize = 0u
    while (iterator.isValid()) {
        val indexRecord = iterator.key()
        val valueSize = indexRecord.size - valueOffset - keySize - versionSize
        val keyOffset = valueOffset + valueSize

        if (isPastRange(indexRecord, valueSize)) {
            break
        }

        if (indexScanRange.matchesPartials(indexRecord, valueOffset, valueSize) && checkVersion(indexRecord)) {
            if (
                !scanRequest.shouldBeFiltered(
                    dbAccessor,
                    columnFamilies,
                    readOptions,
                    indexRecord,
                    keyOffset,
                    keySize,
                    null, // Since version is checked in checkVersion, created version does not need to be checked
                    null
                )
            ) {
                val key = createKey(scanRequest.dataModel, indexRecord, keyOffset)

                readCreationVersion(
                    dbAccessor,
                    columnFamilies,
                    readOptions,
                    key.bytes
                )?.let { createdVersion ->
                    processStoreValue(key, createdVersion, indexRecord.copyOfRange(valueOffset, indexRecord.size - versionSize))
                }

                // Break when limit is found
                if (++currentSize == scanRequest.limit) break
            }
        }

        next(indexRecord, keyOffset, keySize)
    }
}

/** Creates a Key out of a [indexRecord] by reading from [keyOffset] */
private fun <DM : IsRootDataModel> createKey(
    dataModel: DM,
    indexRecord: ByteArray,
    keyOffset: Int
): Key<DM> {
    var readIndex = keyOffset
    return dataModel.key {
        indexRecord[readIndex++]
    }
}
