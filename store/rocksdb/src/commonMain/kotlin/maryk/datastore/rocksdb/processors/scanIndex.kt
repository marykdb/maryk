package maryk.datastore.rocksdb.processors

import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.findByteIndexAndSizeByPartIndex
import maryk.core.processors.datastore.matchers.IndexPartialSizeToMatch
import maryk.core.processors.datastore.matchers.IndexPartialToMatch
import maryk.core.processors.datastore.scanRange.IndexableScanRanges
import maryk.core.processors.datastore.scanRange.IndexValueMatch
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.processors.datastore.scanRange.ScanRange
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
import maryk.datastore.rocksdb.processors.helpers.HistoricalTableReader
import maryk.datastore.rocksdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.rocksdb.processors.helpers.RequestKeySoftDeleteCache
import maryk.datastore.rocksdb.processors.helpers.createIndexKey
import maryk.datastore.rocksdb.processors.helpers.createIndexKeyPrefix
import maryk.datastore.rocksdb.processors.helpers.createIndexRangeEnd
import maryk.datastore.rocksdb.processors.helpers.createIndexKeyWithPrefix
import maryk.datastore.rocksdb.processors.helpers.readCreationVersion
import maryk.datastore.rocksdb.processors.helpers.readReversedVersionBytes
import maryk.datastore.rocksdb.processors.helpers.toReversedVersionBytes
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.isSkippableDataError
import maryk.datastore.shared.rethrowIfFatal
import maryk.lib.bytes.combineToByteArray
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.compareDefinedRange
import maryk.lib.extensions.compare.compareToRange
import maryk.lib.extensions.compare.compareToRange
import maryk.lib.extensions.compare.matchesRangePart
import maryk.lib.exceptions.ParseException
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.ReadOptions

internal fun <DM : IsRootDataModel> RocksDBDataStore.scanIndex(
    dbAccessor: DBAccessor,
    columnFamilies: TableColumnFamilies,
    scanRequest: IsScanRequest<DM, *>,
    indexScan: IndexScan,
    keyScanRange: KeyScanRanges,
    includeSortingKey: Boolean,
    softDeleteCache: RequestKeySoftDeleteCache? = null,
    historicalReader: HistoricalTableReader? = null,
    processStoreValue: (Key<DM>, ULong, ByteArray?) -> Unit
): DataFetchType {
    val indexReference = indexScan.index.referenceStorageByteArray.bytes
    val indexKeyPrefix = createIndexKeyPrefix(indexReference)
    val indexScanRange = indexScan.index.createScanRange(scanRequest.where, keyScanRange)

    val startKey = scanRequest.startKey?.let { startKey ->
        DBAccessorStoreValuesGetter(columnFamilies, defaultReadOptions).use { startValuesGetter ->
            startValuesGetter.moveToKey(startKey.bytes, dbAccessor, scanRequest.toVersion)
            try {
                selectIndexKeyForScan(
                    valuesGetter = startValuesGetter,
                    index = indexScan.index,
                    direction = indexScan.direction,
                    keyBytes = startKey.bytes,
                    indexScanRange = indexScanRange
                )
            } catch (error: Throwable) {
                error.rethrowIfFatal()
                if (!error.isSkippableDataError()) {
                    throw error
                }
                scanRequest.toVersion?.let { toVersion ->
                    findHistoricIndexEntryForKey(
                        dbAccessor = dbAccessor,
                        columnFamilies = columnFamilies as? HistoricTableColumnFamilies ?: return@let null,
                        readOptions = defaultReadOptions,
                        indexReference = indexReference,
                        key = startKey.bytes,
                        toVersion = toVersion,
                        direction = indexScan.direction,
                        includeSoftDeleted = !scanRequest.filterSoftDeleted
                    )
                } ?: findCurrentIndexEntryForKey(
                    dbAccessor = dbAccessor,
                    columnFamily = columnFamilies.index,
                    readOptions = defaultReadOptions,
                    indexReference = indexReference,
                    key = startKey.bytes,
                    direction = indexScan.direction
                )
            }
        }
    }

    var overallStartKey: ByteArray? = null
    var overallStopKey: ByteArray? = null
    overallStartKey = when (indexScan.direction) {
        ASC -> startKey?.let {
            indexScanRange.ranges.first().getAscendingStartKey(it, keyScanRange.includeStart)
        } ?: indexScanRange.ranges.first().start
        DESC -> indexScanRange.ranges.first().getDescendingStartKey(startKey, keyScanRange.includeStart)
    }
    overallStopKey = when (indexScan.direction) {
        ASC -> indexScanRange.ranges.last().getDescendingStartKey()
        DESC -> indexScanRange.ranges.last().getAscendingStartKey()
    }

    val indexColumnHandle = if(scanRequest.toVersion == null) {
        columnFamilies.index
    } else {
        (columnFamilies as? HistoricTableColumnFamilies)?.historic?.index
            ?: throw StorageException("No historic table stored so toVersion in query cannot be processed")
    }

    val keySize = scanRequest.dataModel.Meta.keyByteSize
    val indexPartCount = indexScan.index.indexPartCount
    val valueOffset = indexKeyPrefix.size
    val versionSize = if(scanRequest.toVersion != null) VERSION_BYTE_SIZE else 0
    val indexReferenceUpperBound = createIndexRangeEnd(indexReference)
    var currentSize = 0u
    val seenKeys = mutableSetOf<ByteArrayKey>()

    if (scanRequest.toVersion != null) {
        val toVersion = scanRequest.toVersion ?: throw StorageException("Historic scan requires toVersion")
        data class HistoricIndexRecord(val sort: ByteArray, val keyBytes: ByteArray, val createdVersion: ULong)

        val latestByKey = hashMapOf<ByteArrayKey, HistoricIndexRecord>()
        val filterCache = mutableMapOf<ByteArrayKey, Pair<ULong, Boolean>>()

        dbAccessor.getIterator(defaultReadOptions, indexColumnHandle).use { iterator ->
            iterator.seek(indexKeyPrefix)

            while (iterator.isValid()) {
                val indexRecord = iterator.key()
                if (!isKeyInIndexRange(indexRecord, indexKeyPrefix, indexReferenceUpperBound)) {
                    break
                }

                val qualifierSize = indexRecord.size - VERSION_BYTE_SIZE
                if (qualifierSize <= valueOffset + keySize) {
                    iterator.next()
                    continue
                }

                val valueSize = findIndexValueSize(indexRecord, valueOffset, qualifierSize, keySize, indexPartCount)
                if (valueSize < 0) {
                    iterator.next()
                    continue
                }

                val keyOffset = qualifierSize - keySize
                if (
                    indexScanRange.ranges.none { range ->
                        range.matchesIndexValue(indexScanRange, indexRecord, valueSize, valueOffset)
                    } ||
                    !indexScanRange.matchesPartials(indexRecord, valueOffset, valueSize, qualifierSize)
                ) {
                    skipCurrentHistoricIndexQualifier(iterator, indexRecord, qualifierSize)
                    continue
                }

                var resolvedVisibleState: ByteArray? = null

                while (
                    iterator.isValid() &&
                    iterator.key().size >= qualifierSize &&
                    iterator.key().matchesRangePart(0, indexRecord, sourceLength = qualifierSize, offset = 0, length = qualifierSize)
                ) {
                    val candidateRecord = iterator.key()
                    val candidateVersion = candidateRecord.readReversedVersionBytes(qualifierSize)
                    if (candidateVersion > toVersion) {
                        iterator.next()
                        continue
                    }

                    resolvedVisibleState = iterator.value()
                    break
                }

                if (resolvedVisibleState != null && isHistoricIndexVisible(resolvedVisibleState, !scanRequest.filterSoftDeleted)) {
                    if (
                        !hasAdditionalMatches(
                            dbAccessor,
                            indexColumnHandle,
                            defaultReadOptions,
                            keySize,
                            indexPartCount,
                            indexKeyPrefix,
                            indexScanRange.valueMatches,
                            indexRecord,
                            keyOffset,
                            keySize,
                            scanRequest.toVersion,
                            !scanRequest.filterSoftDeleted
                        )
                    ) {
                        skipCurrentHistoricIndexQualifier(iterator, indexRecord, qualifierSize)
                        continue
                    }

                    val keyBytes = createKeyBytes(indexRecord, keyOffset, keySize)
                    val keyRef = ByteArrayKey(keyBytes)
                    val (createdVersion, shouldFilter) = filterCache.getOrPut(keyRef) {
                        val createdVersion = softDeleteCache?.getCreationVersion(keyBytes)
                            ?: readCreationVersion(
                                dbAccessor,
                                columnFamilies,
                                defaultReadOptions,
                                keyBytes,
                                scanRequest.toVersion
                            )
                            ?: return@getOrPut 0uL to true

                        createdVersion to scanRequest.shouldBeFiltered(
                            dbAccessor,
                            columnFamilies,
                            defaultReadOptions,
                            keyBytes,
                            0,
                            keySize,
                            createdVersion,
                            scanRequest.toVersion,
                            indexScan.index,
                            historicalReader = historicalReader,
                            softDeleteCache = softDeleteCache
                        )
                    }

                    if (!shouldFilter) {
                        val record = HistoricIndexRecord(
                            sort = indexRecord.copyOfRange(valueOffset, qualifierSize),
                            keyBytes = keyBytes,
                            createdVersion = createdVersion
                        )
                        val previous = latestByKey[keyRef]
                        if (
                            previous == null ||
                            when (indexScan.direction) {
                                ASC -> record.sort compareTo previous.sort < 0
                                DESC -> record.sort compareTo previous.sort > 0
                            }
                        ) {
                            latestByKey[keyRef] = record
                        }
                    }
                }

                skipCurrentHistoricIndexQualifier(iterator, indexRecord, qualifierSize)
            }
        }

        val sortedResults = latestByKey.values.sortedWith { a, b -> a.sort compareTo b.sort }

        val orderedResults = when (indexScan.direction) {
            ASC -> sortedResults
            DESC -> sortedResults.asReversed()
        }

        for (result in orderedResults) {
            if (currentSize == scanRequest.limit) {
                break
            }
            if (startKey != null) {
                val comparison = result.sort.compareToRange(startKey, 0)
                if (
                    (indexScan.direction == ASC && (comparison < 0 || (!keyScanRange.includeStart && comparison == 0))) ||
                    (indexScan.direction == DESC && (comparison > 0 || (!keyScanRange.includeStart && comparison == 0)))
                ) {
                    continue
                }
            }

            processStoreValue(
                Key<DM>(result.keyBytes),
                result.createdVersion,
                if (includeSortingKey) result.sort else null
            )
            currentSize++
        }

        return FetchByIndexScan(
            index = indexScan.index.referenceStorageByteArray.bytes,
            direction = indexScan.direction,
            startKey = overallStartKey,
            stopKey = overallStopKey,
        )
    }

    dbAccessor.getIterator(defaultReadOptions, indexColumnHandle).use { iterator ->
        when (indexScan.direction) {
            ASC -> {
                overallStartKey = startKey?.let {
                    indexScanRange.ranges.first().getAscendingStartKey(it, keyScanRange.includeStart)
                } ?: indexScanRange.ranges.first().start
                overallStopKey = indexScanRange.ranges.last().getDescendingStartKey()

                for (indexRange in indexScanRange.ranges) {
                    val indexStartKey = startKey?.let {
                        indexRange.getAscendingStartKey(it, keyScanRange.includeStart)
                    } ?: indexRange.start

                    iterator.seek(indexKeyPrefix + indexStartKey)

                    currentSize = checkAndProcess(
                        dbAccessor,
                        columnFamilies,
                        defaultReadOptions,
                        iterator,
                        indexScan,
                        indexColumnHandle,
                        keySize,
                        indexKeyPrefix,
                        indexPartCount,
                        scanRequest,
                        indexScanRange,
                        versionSize,
                        valueOffset,
                        includeSortingKey,
                        currentSize,
                        processStoreValue,
                        { indexRecord, valueSize ->
                            val rangeLength = indexRangeLength(indexScanRange, indexRange, valueSize)
                            !isKeyInIndexRange(indexRecord, indexKeyPrefix, indexReferenceUpperBound) ||
                            indexRange.keyOutOfRange(indexRecord, valueOffset, rangeLength)
                        },
                        createVersionChecker(scanRequest.toVersion, !scanRequest.filterSoftDeleted, iterator, indexScan.direction),
                        seenKeys,
                        historicalReader,
                        softDeleteCache,
                        createGotoNext(scanRequest, iterator, iterator::next)
                    )
                    if (currentSize == scanRequest.limit) break
                }
            }
            DESC -> {
                overallStartKey = indexScanRange.ranges.first().getDescendingStartKey(startKey, keyScanRange.includeStart)
                overallStopKey = indexScanRange.ranges.last().getAscendingStartKey()

                for (indexRange in indexScanRange.ranges.reversed()) {
                    val indexStartKey = indexRange.getDescendingStartKey(startKey, keyScanRange.includeStart)?.let {
                        // If was not highered it was not possible so scan to lastIndex
                        if (indexRange.endInclusive && indexRange.end === it) null else it
                    }

                    if (indexStartKey == null || indexStartKey.isEmpty()) {
                        iterator.seek(indexReferenceUpperBound)
                        if (iterator.isValid()) {
                            iterator.prev()
                        } else {
                            iterator.seekToLast()
                        }
                    } else {
                        iterator.seekForPrev(indexKeyPrefix + indexStartKey)
                    }

                    currentSize = checkAndProcess(
                        dbAccessor,
                        columnFamilies,
                        defaultReadOptions,
                        iterator,
                        indexScan,
                        indexColumnHandle,
                        keySize,
                        indexKeyPrefix,
                        indexPartCount,
                        scanRequest,
                        indexScanRange,
                        versionSize,
                        valueOffset,
                        includeSortingKey,
                        currentSize,
                        processStoreValue,
                        { indexRecord, valueSize ->
                            val rangeLength = indexRangeLength(indexScanRange, indexRange, valueSize)
                            !isKeyInIndexRange(indexRecord, indexKeyPrefix, indexReferenceUpperBound) ||
                            indexRange.keyBeforeStart(indexRecord, valueOffset, rangeLength)
                        },
                        createVersionChecker(scanRequest.toVersion, !scanRequest.filterSoftDeleted, iterator, indexScan.direction),
                        seenKeys,
                        historicalReader,
                        softDeleteCache,
                        createGotoNext(scanRequest, iterator, iterator::prev)
                    )
                    if (currentSize == scanRequest.limit) break
                }
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

private fun selectIndexKeyForScan(
    valuesGetter: DBAccessorStoreValuesGetter,
    index: maryk.core.properties.definitions.index.IsIndexable,
    direction: Direction,
    keyBytes: ByteArray,
    indexScanRange: IndexableScanRanges
): ByteArray? {
    val allIndexValues = index.toStorageByteArraysForIndex(valuesGetter, keyBytes)
    return allIndexValues
        .filter { indexValue ->
            resolveIndexRangeValueSize(indexValue, index.indexPartCount, indexScanRange.keyScanRange.keySize)?.let { valueSize ->
                indexScanRange.matchesPartials(indexValue, length = valueSize) &&
                    indexScanRange.ranges.any { range ->
                        range.matchesIndexValue(indexScanRange, indexValue, valueSize)
                    }
            } == true
        }
        .let { candidateIndexValues ->
            when (direction) {
                ASC -> candidateIndexValues.minWithOrNull { a, b -> a compareTo b }
                DESC -> candidateIndexValues.maxWithOrNull { a, b -> a compareTo b }
            }
        }
}

private fun skipCurrentHistoricIndexQualifier(
    iterator: DBIterator,
    indexRecord: ByteArray,
    qualifierSize: Int
) {
    while (
        iterator.isValid() &&
        iterator.key().size >= qualifierSize &&
        iterator.key().matchesRangePart(0, indexRecord, sourceLength = qualifierSize, offset = 0, length = qualifierSize)
    ) {
        iterator.next()
    }
}

private fun findCurrentIndexEntryForKey(
    dbAccessor: DBAccessor,
    columnFamily: ColumnFamilyHandle,
    readOptions: ReadOptions,
    indexReference: ByteArray,
    key: ByteArray,
    direction: Direction
): ByteArray? {
    val indexKeyPrefix = createIndexKeyPrefix(indexReference)
    var matchedValueAndKey: ByteArray? = null

    dbAccessor.getIterator(readOptions, columnFamily).use { iterator ->
        iterator.seek(indexKeyPrefix)

        while (iterator.isValid()) {
            val indexRecord = iterator.key()
            if (!indexRecord.matchesRangePart(0, indexKeyPrefix, length = indexKeyPrefix.size)) {
                break
            }

            val keyOffset = indexRecord.size - key.size
            if (keyOffset >= indexKeyPrefix.size && indexRecord.matchesRangePart(keyOffset, key, length = key.size)) {
                matchedValueAndKey = indexRecord.copyOfRange(indexKeyPrefix.size, indexRecord.size)
                if (direction == ASC) {
                    break
                }
            }

            iterator.next()
        }
    }

    return matchedValueAndKey
}

private fun findHistoricIndexEntryForKey(
    dbAccessor: DBAccessor,
    columnFamilies: HistoricTableColumnFamilies,
    readOptions: ReadOptions,
    indexReference: ByteArray,
    key: ByteArray,
    toVersion: ULong,
    direction: Direction,
    includeSoftDeleted: Boolean
): ByteArray? {
    val indexKeyPrefix = createIndexKeyPrefix(indexReference)
    val indexReferenceUpperBound = createIndexRangeEnd(indexReference)
    var matchedValueAndKey: ByteArray? = null

    dbAccessor.getIterator(readOptions, columnFamilies.historic.index).use { iterator ->
        iterator.seek(indexKeyPrefix)

        while (iterator.isValid()) {
            val indexRecord = iterator.key()
            if (!isKeyInIndexRange(indexRecord, indexKeyPrefix, indexReferenceUpperBound)) {
                break
            }

            val qualifierSize = indexRecord.size - VERSION_BYTE_SIZE
            if (qualifierSize <= indexKeyPrefix.size + key.size) {
                iterator.next()
                continue
            }

            val keyOffset = qualifierSize - key.size
            var resolvedVisibleState: ByteArray? = null

            while (
                iterator.isValid() &&
                iterator.key().size >= qualifierSize &&
                iterator.key().matchesRangePart(0, indexRecord, sourceLength = qualifierSize, offset = 0, length = qualifierSize)
            ) {
                val candidateRecord = iterator.key()
                val candidateVersion = candidateRecord.readReversedVersionBytes(qualifierSize)
                if (candidateVersion > toVersion) {
                    iterator.next()
                    continue
                }

                resolvedVisibleState = iterator.value()
                break
            }

            if (
                resolvedVisibleState != null &&
                isHistoricIndexVisible(resolvedVisibleState, includeSoftDeleted) &&
                indexRecord.matchesRangePart(keyOffset, key, length = key.size)
            ) {
                matchedValueAndKey = indexRecord.copyOfRange(indexKeyPrefix.size, qualifierSize)
                if (direction == ASC) {
                    break
                }
            }

            while (
                iterator.isValid() &&
                iterator.key().size >= qualifierSize &&
                iterator.key().matchesRangePart(0, indexRecord, sourceLength = qualifierSize, offset = 0, length = qualifierSize)
            ) {
                iterator.next()
            }
        }
    }

    return matchedValueAndKey
}

/**
 * Create a version checker to see if record has to be skipped or not.
 */
fun createVersionChecker(
    toVersion: ULong?,
    includeSoftDeleted: Boolean,
    iterator: DBIterator,
    direction: Direction
): (ByteArray) -> Boolean =
    if (toVersion == null) {
        { true } // Version is always latest and thus valid, because is scanning on normal table
    } else {
        // Since index stores versions in reverse order, reverse the version here too
        val versionBytesToMatch = toVersion.toReversedVersionBytes()

        when (direction) {
            ASC -> {
                asc@{ indexKey ->
                    val indexKeyVersionOffset = indexKey.size - VERSION_BYTE_SIZE
                    if (indexKeyVersionOffset < 0) return@asc false
                    var validResult = false
                    // Skip all
                    while (iterator.isValid()) {
                        val newKey = iterator.key()
                        val newKeyVersionOffset = newKey.size - VERSION_BYTE_SIZE
                        if (newKeyVersionOffset < 0) {
                            iterator.next()
                            continue
                        }

                        if (!newKey.matchesRangePart(0, indexKey, newKey.size, 0, indexKeyVersionOffset)) {
                            iterator.prev() // Go back to last key, so it can be processed next
                            break // Key does not match anymore so break out
                        }

                        if (versionBytesToMatch.compareToRange(newKey, newKeyVersionOffset) > 0) {
                            // Continue to older version since key was too new for request
                            iterator.next()
                        } else {
                            validResult = isHistoricIndexVisible(iterator.value(), includeSoftDeleted)
                            break
                        }
                    }
                    // Return if a valid non deleted result was found
                    validResult
                }
            }
            DESC -> {
                desc@{ indexKey ->
                    val indexKeyVersionOffset = indexKey.size - VERSION_BYTE_SIZE
                    if (indexKeyVersionOffset < 0) return@desc false
                    var validResult = false

                    // This iterator starts at the first version so has to walk past all valid versions
                    while (iterator.isValid()) {
                        val newKey = iterator.key()
                        val newKeyVersionOffset = newKey.size - VERSION_BYTE_SIZE
                        if (newKeyVersionOffset < 0) {
                            iterator.prev()
                            continue
                        }
                        // Check if new key matches expected key and otherwise skips out
                        if (!newKey.matchesRangePart(0, indexKey, newKeyVersionOffset, 0, indexKeyVersionOffset)) {
                            iterator.next() // Move back iterator so next call will start at right key
                            break
                        }

                        // Continue to newer versions until key is not of a valid version
                        if (versionBytesToMatch.compareToRange(newKey, newKeyVersionOffset) <= 0) {
                            validResult = isHistoricIndexVisible(iterator.value(), includeSoftDeleted)
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
            while (iterator.isValid() && iterator.key().let { it.matchesRangePart(0, key, it.size, 0, keyOffset + keyLength) }) {
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
    indexScan: IndexScan,
    indexColumnHandle: ColumnFamilyHandle,
    keySize: Int,
    indexKeyPrefix: ByteArray,
    indexPartCount: Int,
    scanRequest: IsScanRequest<DM, *>,
    indexScanRange: IndexableScanRanges,
    versionSize: Int,
    valueOffset: Int,
    includeSortingKey: Boolean,
    emitted: UInt,
    processStoreValue: (Key<DM>, ULong, ByteArray?) -> Unit,
    isPastRange: (ByteArray, Int) -> Boolean,
    checkVersion: (ByteArray) -> Boolean,
    seenKeys: MutableSet<ByteArrayKey>,
    historicalReader: HistoricalTableReader?,
    softDeleteCache: RequestKeySoftDeleteCache?,
    next: (ByteArray, Int, Int) -> Unit
): UInt {
    var currentSize = emitted
    while (iterator.isValid()) {
        val indexRecord = iterator.key()
        if (versionSize == 0) {
            val valueSize = findIndexValueSize(indexRecord, valueOffset, indexRecord.size, keySize, indexPartCount)
            if (valueSize < 0) {
                iterator.next()
                continue
            }
            val keyOffset = indexRecord.size - keySize
            if (isPastRange(indexRecord, valueSize)) {
                break
            }

            if (
                indexScanRange.matchesPartials(indexRecord, valueOffset, valueSize, indexRecord.size) &&
                hasAdditionalMatches(
                    dbAccessor,
                    indexColumnHandle,
                    readOptions,
                    keySize,
                    indexPartCount,
                    indexKeyPrefix,
                    indexScanRange.valueMatches,
                    indexRecord,
                    keyOffset,
                    keySize,
                    null,
                    true
                ) &&
                checkVersion(indexRecord)
            ) {
                if (
                    !scanRequest.shouldBeFiltered(
                        dbAccessor,
                        columnFamilies,
                        readOptions,
                        indexRecord,
                        keyOffset,
                        keySize,
                        null,
                        null,
                        indexScan.index
                    )
                ) {
                    val keyBytes = createKeyBytes(indexRecord, keyOffset, keySize)
                    if (!seenKeys.add(ByteArrayKey(keyBytes))) {
                        next(indexRecord, keyOffset, keySize)
                        continue
                    }

                    val createdVersion = readCreationVersion(
                        dbAccessor,
                        columnFamilies,
                        readOptions,
                        keyBytes,
                        scanRequest.toVersion
                    ) ?: run {
                        next(indexRecord, keyOffset, keySize)
                        continue
                    }

                    processStoreValue(
                        Key<DM>(keyBytes),
                        createdVersion,
                        if (includeSortingKey) indexRecord.copyOfRange(valueOffset, indexRecord.size) else null
                    )

                    if (++currentSize == scanRequest.limit) break
                }
            }

            next(indexRecord, keyOffset, keySize)
            continue
        }

        val qualifierEnd = indexRecord.size - versionSize
        if (qualifierEnd <= valueOffset + keySize || indexRecord.size < versionSize) {
            iterator.next()
            continue
        }
        val valueSize = findIndexValueSize(indexRecord, valueOffset, qualifierEnd, keySize, indexPartCount)
        if (valueSize < 0) {
            iterator.next()
            continue
        }
        val keyOffset = qualifierEnd - keySize
        if (isPastRange(indexRecord, valueSize)) {
            break
        }

        if (
            indexScanRange.matchesPartials(indexRecord, valueOffset, valueSize, qualifierEnd) &&
            hasAdditionalMatches(
                dbAccessor,
                indexColumnHandle,
                readOptions,
                keySize,
                indexPartCount,
                indexKeyPrefix,
                indexScanRange.valueMatches,
                indexRecord,
                keyOffset,
                keySize,
                scanRequest.toVersion,
                !scanRequest.filterSoftDeleted
            ) &&
            checkVersion(indexRecord)
        ) {
            val keyBytes = createKeyBytes(indexRecord, keyOffset, keySize)
            if (!seenKeys.add(ByteArrayKey(keyBytes))) {
                next(indexRecord, keyOffset, keySize)
                continue
            }

            if (
                !scanRequest.shouldBeFiltered(
                    dbAccessor,
                    columnFamilies,
                    readOptions,
                    indexRecord,
                    keyOffset,
                    keySize,
                    null, // Since version is checked in checkVersion, created version does not need to be checked
                    null,
                    indexScan.index,
                    checkSoftDelete = false,
                    historicalReader = historicalReader,
                    softDeleteCache = softDeleteCache
                )
            ) {
                (softDeleteCache?.getCreationVersion(keyBytes)
                    ?: readCreationVersion(
                        dbAccessor,
                        columnFamilies,
                        readOptions,
                        keyBytes,
                        scanRequest.toVersion
                    ))?.let { createdVersion ->
                    processStoreValue(
                        Key<DM>(keyBytes),
                        createdVersion,
                        if (includeSortingKey) indexRecord.copyOfRange(valueOffset, qualifierEnd) else null
                    )
                }

                // Break when limit is found
                if (++currentSize == scanRequest.limit) break
            }
        }

        next(indexRecord, keyOffset, keySize)
    }
    return currentSize
}

private fun hasAdditionalMatches(
    dbAccessor: DBAccessor,
    indexColumnHandle: ColumnFamilyHandle,
    readOptions: ReadOptions,
    keySize: Int,
    indexPartCount: Int,
    indexKeyPrefix: ByteArray,
    matches: List<IndexValueMatch>,
    recordKeySource: ByteArray,
    recordKeyOffset: Int,
    recordKeyLength: Int,
    toVersion: ULong?,
    includeSoftDeleted: Boolean
) = matches.all { match ->
    if (match.partialMatch) {
        hasMatchingPrefixValue(
            dbAccessor,
            indexColumnHandle,
            readOptions,
            keySize,
            indexPartCount,
            indexKeyPrefix,
            match.toMatch,
            recordKeySource,
            recordKeyOffset,
            recordKeyLength,
            toVersion,
            includeSoftDeleted
        )
    } else {
        hasMatchingExactValue(
            dbAccessor,
            indexColumnHandle,
            readOptions,
            indexKeyPrefix,
            match.toMatch,
            recordKeySource,
            recordKeyOffset,
            recordKeyLength,
            toVersion,
            includeSoftDeleted
        )
    }
}

private fun hasMatchingExactValue(
    dbAccessor: DBAccessor,
    indexColumnHandle: ColumnFamilyHandle,
    readOptions: ReadOptions,
    indexKeyPrefix: ByteArray,
    value: ByteArray,
    recordKeySource: ByteArray,
    recordKeyOffset: Int,
    recordKeyLength: Int,
    toVersion: ULong?,
    includeSoftDeleted: Boolean
): Boolean {
    val fullIndexValue = createIndexKeyWithPrefix(indexKeyPrefix, createIndexValue(value, recordKeySource, recordKeyOffset, recordKeyLength))
    return if (toVersion == null) {
        dbAccessor.get(indexColumnHandle, readOptions, fullIndexValue) != null
    } else {
        val versionBytes = toVersion.toReversedVersionBytes()
        dbAccessor.getIterator(readOptions, indexColumnHandle).use { iterator ->
            iterator.seek(fullIndexValue + versionBytes)
            while (iterator.isValid()) {
                val key = iterator.key()
                if (key.size < fullIndexValue.size) {
                    iterator.next()
                    continue
                }
                if (!key.matchesRangePart(0, fullIndexValue, length = fullIndexValue.size)) {
                    return false
                }

                val versionOffset = key.size - VERSION_BYTE_SIZE
                if (versionOffset != fullIndexValue.size) {
                    iterator.next()
                    continue
                }
                if (versionBytes.compareToRange(key, versionOffset) <= 0) {
                    return isHistoricIndexVisible(iterator.value(), includeSoftDeleted)
                }
                iterator.next()
            }
        }
        false
    }
}

private fun hasMatchingPrefixValue(
    dbAccessor: DBAccessor,
    indexColumnHandle: ColumnFamilyHandle,
    readOptions: ReadOptions,
    keySize: Int,
    indexPartCount: Int,
    indexKeyPrefix: ByteArray,
    prefix: ByteArray,
    recordKeySource: ByteArray,
    recordKeyOffset: Int,
    recordKeyLength: Int,
    toVersion: ULong?,
    includeSoftDeleted: Boolean
): Boolean {
    val prefixWithReference = combineToByteArray(indexKeyPrefix, prefix)
    val versionBytes = toVersion?.toReversedVersionBytes()

    dbAccessor.getIterator(readOptions, indexColumnHandle).use { iterator ->
        iterator.seek(prefixWithReference)

        if (versionBytes != null) {
            while (iterator.isValid()) {
                val firstKey = iterator.key()
                if (firstKey.size < prefixWithReference.size) {
                    iterator.next()
                    continue
                }
                if (prefixWithReference.compareDefinedRange(firstKey, 0, prefixWithReference.size) > 0) {
                    iterator.next()
                    continue
                }
                if (!firstKey.matchesRangePart(0, prefixWithReference, length = prefixWithReference.size)) {
                    return false
                }

                val qualifierEnd = firstKey.size - VERSION_BYTE_SIZE
                val qualifierLength = qualifierEnd - indexKeyPrefix.size
                if (qualifierLength < keySize) {
                    iterator.next()
                    continue
                }
                val qualifierOffset = indexKeyPrefix.size
                val recordKeyInQualifierOffset = qualifierLength - keySize
                var foundVisibleVersion = false
                while (iterator.isValid()) {
                    val indexRecord = iterator.key()
                    if (indexRecord.size < prefixWithReference.size) {
                        iterator.next()
                        continue
                    }
                    if (!indexRecord.matchesRangePart(0, prefixWithReference, length = prefixWithReference.size)) {
                        return false
                    }

                    if (indexRecord.size < indexKeyPrefix.size + qualifierLength) {
                        iterator.next()
                        continue
                    }
                    if (!indexRecord.matchesRangePart(indexKeyPrefix.size, firstKey, qualifierLength, qualifierOffset, qualifierLength)) {
                        break
                    }

                    val versionOffset = indexRecord.size - VERSION_BYTE_SIZE
                    if (versionOffset < qualifierLength + indexKeyPrefix.size) {
                        iterator.next()
                        continue
                    }
                    if (versionBytes.compareToRange(indexRecord, versionOffset) <= 0) {
                        foundVisibleVersion = true
                        if (
                            isHistoricIndexVisible(iterator.value(), includeSoftDeleted) &&
                            indexRecord.matchesRangePart(
                                indexKeyPrefix.size + recordKeyInQualifierOffset,
                                recordKeySource,
                                recordKeyLength,
                                recordKeyOffset,
                                recordKeyLength
                            )
                        ) {
                            return true
                        }
                        break
                    }

                    iterator.next()
                }

                if (!foundVisibleVersion) {
                    continue
                }

                while (iterator.isValid()) {
                    val indexRecord = iterator.key()
                    if (indexRecord.size < prefixWithReference.size) {
                        iterator.next()
                        continue
                    }
                    if (!indexRecord.matchesRangePart(0, prefixWithReference, length = prefixWithReference.size)) {
                        return false
                    }
                    if (indexRecord.size < indexKeyPrefix.size + qualifierLength) {
                        iterator.next()
                        continue
                    }
                    if (!indexRecord.matchesRangePart(indexKeyPrefix.size, firstKey, qualifierLength, qualifierOffset, qualifierLength)) {
                        break
                    }
                    iterator.next()
                }
            }

            return false
        }

        var settledValueAndKeyBytes: ByteArray? = null
        var settledValueAndKeyOffset = 0
        var settledValueAndKeyLength = 0
        while (iterator.isValid()) {
            val indexRecord = iterator.key()
            if (indexRecord.size < prefixWithReference.size) {
                iterator.next()
                continue
            }
            if (prefixWithReference.compareDefinedRange(indexRecord, 0, prefixWithReference.size) > 0) {
                iterator.next()
                continue
            }
            if (!indexRecord.matchesRangePart(0, prefixWithReference, length = prefixWithReference.size)) {
                return false
            }

            val valueSize = findIndexValueSize(indexRecord, indexKeyPrefix.size, indexRecord.size, keySize, indexPartCount)
            if (valueSize < 0) {
                iterator.next()
                continue
            }
            val keyOffset = indexRecord.size - keySize
            val valueAndKeyOffset = indexKeyPrefix.size
            val valueAndKeyLength = valueSize + keySize

            if (
                settledValueAndKeyBytes != null &&
                settledValueAndKeyLength == valueAndKeyLength &&
                indexRecord.matchesRangePart(
                    valueAndKeyOffset,
                    settledValueAndKeyBytes,
                    valueAndKeyLength,
                    settledValueAndKeyOffset,
                    settledValueAndKeyLength
                )
            ) {
                iterator.next()
                continue
            }

            if (indexRecord.matchesRangePart(keyOffset, recordKeySource, recordKeyLength, recordKeyOffset, recordKeyLength)) {
                return true
            } else {
                settledValueAndKeyBytes = indexRecord
                settledValueAndKeyOffset = valueAndKeyOffset
                settledValueAndKeyLength = valueAndKeyLength
            }
            iterator.next()
        }

        return false
    }
}

private fun isHistoricIndexVisible(value: ByteArray, includeSoftDeleted: Boolean) = when {
    value.isEmpty() -> true
    value.contentEquals(TRUE_ARRAY) -> includeSoftDeleted
    else -> false
}

private fun ScanRange.matchesIndexValue(
    indexScanRange: IndexableScanRanges,
    indexValue: ByteArray,
    valueSize: Int,
    offset: Int = 0
): Boolean {
    val rangeLength = indexRangeLength(indexScanRange, this, valueSize)
    return !keyBeforeStart(indexValue, offset, rangeLength) && !keyOutOfRange(indexValue, offset, rangeLength)
}

private fun indexRangeLength(indexScanRange: IndexableScanRanges, range: ScanRange, valueSize: Int): Int =
    if (
        range.start.isNotEmpty() &&
        range.startInclusive &&
        range.endInclusive &&
        range.end?.contentEquals(range.start) == true &&
        indexScanRange.partialMatches?.any {
            (it is IndexPartialSizeToMatch && it.size == range.start.size) ||
                (it is IndexPartialToMatch &&
                    it.partialMatch &&
                    it.toMatch.contentEquals(range.start))
        } == true
    ) {
        range.start.size
    } else {
        valueSize
    }

private fun isKeyInIndexRange(
    key: ByteArray,
    indexKeyPrefix: ByteArray,
    indexReferenceUpperBound: ByteArray?
): Boolean {
    if (indexKeyPrefix.compareDefinedRange(key) > 0) {
        return false
    }

    return indexReferenceUpperBound?.compareDefinedRange(key)?.let { it > 0 } != false
}

private fun findIndexValueSize(
    indexRecord: ByteArray,
    valueOffset: Int,
    sourceEnd: Int,
    keySize: Int,
    indexPartCount: Int
): Int {
    if (sourceEnd <= valueOffset + keySize || indexPartCount <= 0) {
        return -1
    }

    val (lastPartOffset, lastPartSize) = try {
        findByteIndexAndSizeByPartIndex(
            indexPartCount - 1,
            indexRecord,
            keySize,
            sourceStart = valueOffset,
            sourceEnd = sourceEnd,
            indexPartCount = indexPartCount
        )
    } catch (_: ParseException) {
        // Skip malformed index rows.
        return -1
    }
    return lastPartOffset + lastPartSize
}

private fun resolveIndexRangeValueSize(indexValue: ByteArray, indexPartCount: Int, keySize: Int): Int? =
    try {
        val (lastPartOffset, lastPartSize) = findByteIndexAndSizeByPartIndex(
            indexPartCount - 1,
            indexValue,
            keySize,
            indexPartCount = indexPartCount
        )
        lastPartOffset + lastPartSize
    } catch (_: ParseException) {
        null
    }

private fun createIndexValue(value: ByteArray, key: ByteArray): ByteArray {
    return createIndexValue(value, key, 0, key.size)
}

private fun createIndexValue(value: ByteArray, keySource: ByteArray, keyOffset: Int, keyLength: Int): ByteArray {
    val valueLength = value.size
    val valueLengthBytes = valueLength.calculateVarByteLength()
    return ByteArray(valueLength + valueLengthBytes + keyLength).also { output ->
        value.copyInto(output, 0)
        var index = valueLength
        valueLength.writeVarBytes { output[index++] = it }
        keySource.copyInto(output, index, keyOffset, keyOffset + keyLength)
    }
}

private class ByteArrayKey(
    private val bytes: ByteArray
) {
    override fun hashCode() = bytes.contentHashCode()

    override fun equals(other: Any?) = other is ByteArrayKey && bytes.contentEquals(other.bytes)
}

private fun createKeyBytes(
    source: ByteArray,
    keyOffset: Int,
    keyLength: Int
) = source.copyOfRange(keyOffset, keyOffset + keyLength)

/** Creates a Key out of a [indexRecord] by reading from [keyOffset] */
