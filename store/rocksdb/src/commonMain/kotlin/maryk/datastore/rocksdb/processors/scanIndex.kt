package maryk.datastore.rocksdb.processors

import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.initULong
import maryk.core.extensions.bytes.toULong
import maryk.core.extensions.bytes.writeBytes
import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.scanRange.IndexableScanRanges
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.types.Key
import maryk.core.query.orders.Direction
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.requests.IsScanRequest
import maryk.datastore.rocksdb.DBIterator
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.Transaction
import maryk.datastore.rocksdb.processors.helpers.readCreationVersion
import maryk.datastore.shared.ScanType.IndexScan
import maryk.lib.extensions.compare.compareToWithOffsetLength
import maryk.lib.extensions.compare.matchPart
import maryk.lib.extensions.compare.nextByteInSameLength
import maryk.lib.extensions.compare.prevByteInSameLength
import maryk.rocksdb.ReadOptions
import kotlin.experimental.xor

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

    val indexColumnHandle = if(scanRequest.toVersion == null) {
        columnFamilies.index
    } else {
        (columnFamilies as? HistoricTableColumnFamilies)?.historic?.index
            ?: throw StorageException("No historic table stored so toVersion in query cannot be processed")
    }

    val iterator = transaction.getIterator(dataStore.defaultReadOptions, indexColumnHandle)

    val keySize = scanRequest.dataModel.keyByteSize
    val valueOffset = indexReference.size
    val toSubstractFromSize = keySize + indexReference.size + if(scanRequest.toVersion != null) ULong.SIZE_BYTES else 0

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

                checkAndProcess(
                    transaction,
                    columnFamilies,
                    dataStore.defaultReadOptions,
                    iterator,
                    keySize,
                    scanRequest,
                    indexScanRange,
                    toSubstractFromSize,
                    valueOffset,
                    processStoreValue,
                    { indexRecord, valueSize ->
                        indexRange.keyOutOfRange(indexRecord, valueOffset, valueSize)
                    },
                    createVersionChecker(scanRequest.toVersion, iterator, indexScan.direction),
                    createVersionReader(scanRequest.toVersion, iterator),
                    createGotoNext(scanRequest, iterator, iterator::next)
                )
            }
        }
        DESC -> {
            for (indexRange in indexScanRange.ranges.reversed()) {
                indexRange.end?.let { endRange ->
                    if (endRange.isEmpty()) {
                        iterator.seek(indexReference.nextByteInSameLength())
                        if (!iterator.isValid()) {
                            iterator.seekToLast()
                        }
                    } else {
                        val endRangeToSearch = if (indexRange.endInclusive) {
                            endRange.nextByteInSameLength()
                        } else {
                            endRange
                        }

                        if (indexRange.endInclusive && endRangeToSearch === endRange) {
                            // If was not highered it was not possible so scan to lastIndex
                            iterator.seek(indexReference.nextByteInSameLength())
                            if (!iterator.isValid()) {
                                iterator.seekToLast()
                            }
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

                checkAndProcess(
                    transaction,
                    columnFamilies,
                    dataStore.defaultReadOptions,
                    iterator,
                    keySize,
                    scanRequest,
                    indexScanRange,
                    toSubstractFromSize,
                    valueOffset,
                    processStoreValue,
                    { indexRecord, valueSize ->
                        indexRange.keyBeforeStart(indexRecord, valueOffset, valueSize)
                    },
                    createVersionChecker(scanRequest.toVersion, iterator, indexScan.direction),
                    createVersionReader(scanRequest.toVersion, iterator),
                    createGotoNext(scanRequest, iterator, iterator::prev)
                )
            }
        }
    }
}

/**
 * Create a version checker to see if record has to be skipped or not.
 */
fun createVersionChecker(toVersion: ULong?, iterator: DBIterator, direction: Direction): (ByteArray) -> Boolean =
    if (toVersion == null) {
        { true } // Version is always latest and thus valid, because is scanning on normal table
    } else {
        val versionBytesToMatch = ByteArray(ULong.SIZE_BYTES)
        var writeIndex = 0
        // Since index stores versions in reverse order, reverse the version here too
        toVersion.writeBytes({ versionBytesToMatch[writeIndex++] = it xor -1 })

        when (direction) {
            ASC -> {
                { indexKey ->
                    var sameKey = true
                    // Skip all
                    while (iterator.isValid()) {
                        val newKey = iterator.key()
                        if (newKey.let { !it.matchPart(0, indexKey, it.size, 0, indexKey.size - ULong.SIZE_BYTES) }) {
                            sameKey = false // Key does not match anymore so break out
                            break
                        }

                        if (versionBytesToMatch.compareToWithOffsetLength(newKey, newKey.size - ULong.SIZE_BYTES) > 0
                            // Check if is deleted and skip if so
                            || iterator.value().contentEquals(FALSE_ARRAY)
                        ) {
                            iterator.next()
                        } else {
                            break
                        }
                    }

                    sameKey
                }
            }
            DESC -> {
                { indexKey ->
                    var hadAKeyMatch = false
                    // This iterator starts at the first version so has to walk past all, will correct back if a key match was found
                    while (iterator.isValid()) {
                        val newKey = iterator.key()
                        if (newKey.let { !it.matchPart(0, indexKey, it.size - ULong.SIZE_BYTES, 0, indexKey.size - ULong.SIZE_BYTES) }) {
                            break
                        }

                        if (versionBytesToMatch.compareToWithOffsetLength(newKey, newKey.size - ULong.SIZE_BYTES) <= 0) {
                            // Only was a match if it was not deleted
                            if (!iterator.value().contentEquals(FALSE_ARRAY)) {
                                hadAKeyMatch = true
                            }
                            iterator.prev()
                        }
                    }
                    // If it found a matching key go back to last found one.
                    if (hadAKeyMatch) {
                        iterator.next()
                        // Skip all deleted indices since it matched on a non deleted one
                        while(iterator.isValid() && iterator.value().contentEquals(FALSE_ARRAY)) {
                            iterator.next()
                        }
                    }

                    hadAKeyMatch
                }
            }
        }
    }

/** Create a version reader based on if it reads the historic table or the normal one. */
fun createVersionReader(
    toVersion: ULong?,
    iterator: DBIterator
): (ByteArray, Int) -> ULong =
    if (toVersion == null) {
        { _, _ -> iterator.value().toULong() }
    } else {
        { key, offset ->
            var index = offset
            // Invert version
            initULong(reader = { key[index++] xor -1 })
        }
    }

/**
 * Create a handler to go to the next index record to read.
 * If it is a versioned read, skip all index records but with older versions
 * The order depends on what is defined in the [next] function
 */
private fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> createGotoNext(
    scanRequest: IsScanRequest<DM, P, *>,
    iterator: DBIterator,
    next: () -> Unit
): (ByteArray, Int, Int) -> Unit =
    if (scanRequest.toVersion == null) {
        { _, _, _ -> next() }
    } else {
        { key, keyOffset, keyLength ->
            next() // First skip is for free since last processed key/value matches for sure
            // Skip all of same index/value/key since they are different versions of the same
            while (iterator.isValid() && iterator.key().let { it.matchPart(0, key, it.size, 0, keyOffset + keyLength) }) {
                next()
            }
        }
    }

/** Walk through index and processes any valid keys and versions */
private fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> checkAndProcess(
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    iterator: DBIterator,
    keySize: Int,
    scanRequest: IsScanRequest<DM, P, *>,
    indexScanRange: IndexableScanRanges,
    toSubstractFromSize: Int,
    valueOffset: Int,
    processStoreValue: (Key<DM>, ULong) -> Unit,
    isPastRange: (ByteArray, Int) -> Boolean,
    checkVersion: (ByteArray) -> Boolean,
    readVersion: (ByteArray, Int) -> ULong,
    next: (ByteArray, Int, Int) -> Unit
) {
    var currentSize: UInt = 0u
    while (iterator.isValid()) {
        val indexRecord = iterator.key()
        val valueSize = indexRecord.size - toSubstractFromSize
        val keyOffset = valueOffset + valueSize

        if (isPastRange(indexRecord, valueSize)) {
            break
        }

        if (indexScanRange.matchesPartials(indexRecord, valueOffset, valueSize) && checkVersion(indexRecord)) {
            val setAtVersion = readVersion(indexRecord, indexRecord.size - ULong.SIZE_BYTES)

            if (
                !scanRequest.shouldBeFiltered(
                    transaction,
                    columnFamilies,
                    readOptions,
                    indexRecord,
                    keyOffset,
                    keySize,
                    setAtVersion,
                    scanRequest.toVersion
                )
            ) {
                val key = createKey(scanRequest.dataModel, indexRecord, keyOffset)
                readCreationVersion(
                    transaction,
                    columnFamilies,
                    readOptions,
                    key.bytes
                )?.let { createdVersion ->
                    processStoreValue(key, createdVersion)
                }

                // Break when limit is found
                if (++currentSize == scanRequest.limit) break
            }
        }
        next(indexRecord, keyOffset, keySize)
    }
}

/** Creates a Key out of a [indexRecord] by reading from [keyOffset] */
private fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> createKey(
    dataModel: DM,
    indexRecord: ByteArray,
    keyOffset: Int
): Key<DM> {
    var readIndex = keyOffset
    @Suppress("UNCHECKED_CAST")
    return dataModel.key {
        indexRecord[readIndex++]
    } as Key<DM>
}
