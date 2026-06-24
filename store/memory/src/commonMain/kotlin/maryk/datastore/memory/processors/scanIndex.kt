package maryk.datastore.memory.processors

import maryk.core.clock.HLC
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.findByteIndexAndSizeByPartIndex
import maryk.core.processors.datastore.matchers.IndexPartialToMatch
import maryk.core.processors.datastore.matchers.IndexPartialSizeToMatch
import maryk.core.processors.datastore.scanRange.IndexValueMatch
import maryk.core.processors.datastore.scanRange.IndexableScanRanges
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.processors.datastore.scanRange.ScanRange
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.requests.IsScanRequest
import maryk.core.query.responses.DataFetchType
import maryk.core.query.responses.FetchByIndexScan
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataStore
import maryk.datastore.memory.records.index.AbstractIndexValues
import maryk.datastore.shared.ScanType.IndexScan
import maryk.lib.bytes.combineToByteArray
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.matchesRangePart
import kotlin.math.min

internal fun <DM : IsRootDataModel> scanIndex(
    dataStore: DataStore<DM>,
    scanRequest: IsScanRequest<DM, *>,
    recordFetcher: (IsRootDataModel, Key<*>) -> DataRecord<*>?,
    indexScan: IndexScan,
    keyScanRange: KeyScanRanges,
    processStoreValue: (DataRecord<DM>, ByteArray?) -> Unit
): DataFetchType {
    val indexReference = indexScan.index.referenceStorageByteArray.bytes
    val index = dataStore.getOrCreateIndex(indexReference)

    var overallStartKey: ByteArray?
    var overallStopKey: ByteArray?
    var currentSize = 0u
    val seenKeys = mutableSetOf<Bytes>()

    val indexScanRange = indexScan.index.createScanRange(scanRequest.where, keyScanRange)
    val startKey = scanRequest.startKey?.let { startKey ->
        recordFetcher(scanRequest.dataModel, scanRequest.startKey as Key<*>)?.let { startRecord ->
            val allIndexValues = indexScan.index.toStorageByteArraysForIndex(startRecord, startKey.bytes)
            allIndexValues
                .filter { indexValue ->
                    resolveIndexValueSize(indexValue, keyScanRange.keySize, indexScan.index.indexPartCount)?.let { valueSize ->
                            indexScanRange.matchesPartials(indexValue, length = valueSize) &&
                            indexScanRange.ranges.any { range ->
                                range.matchesIndexValue(indexScanRange, indexValue, valueSize)
                            }
                    } == true
                }
                .let { candidateIndexValues ->
                    when (indexScan.direction) {
                        ASC -> candidateIndexValues.minWithOrNull { a, b -> a compareTo b }
                        DESC -> candidateIndexValues.maxWithOrNull { a, b -> a compareTo b }
                    }
                }
        }
    }
    val excludedStartIndexValue = startKey.takeIf { !keyScanRange.includeStart }
    val excludedStartRecordKey = scanRequest.startKey
        ?.takeIf { excludedStartIndexValue != null }
        ?.let { Bytes((it as Key<*>).bytes) }

    val toVersion = scanRequest.toVersion?.let { HLC(it) }
    val includeSoftDeleted = !scanRequest.filterSoftDeleted

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

                val startIndex = if (startKey != null) {
                    indexStartKey.let { startRange ->
                        if (!keyScanRange.includeStart && startKey === startRange) {
                            index.indexValues.size
                        } else {
                            index.indexValues.binarySearch { it.value compareTo indexStartKey }.let { valueIndex ->
                                when {
                                    valueIndex < 0 -> valueIndex * -1 - 1
                                    else -> valueIndex
                                }
                            }
                        }
                    }
                } else {
                    index.indexValues.binarySearch { it.value compareTo indexStartKey }.let { valueIndex ->
                        when {
                            valueIndex < 0 -> valueIndex * -1 - 1
                            else -> valueIndex
                        }
                    }
                }

                for (i in startIndex until index.indexValues.size) {
                    val indexRecord = index.indexValues[i]
                    val dataRecord = resolveIndexRecord(dataStore, indexRecord, toVersion, includeSoftDeleted) ?: continue
                    val valueSize = resolveIndexValueSize(indexRecord.value, keyScanRange.keySize, indexScan.index.indexPartCount) ?: continue
                    val rangeLength = indexRangeLength(indexScanRange, indexRange, valueSize)

                    if (indexRange.keyOutOfRange(indexRecord.value, length = rangeLength)) {
                        break
                    }

                    if (indexRange.keyBeforeStart(indexRecord.value, length = rangeLength)) {
                        continue
                    }

                    if (!indexScanRange.matchesPartials(indexRecord.value, length = valueSize)) {
                        continue
                    }

                    if (!hasAdditionalMatches(index, dataStore, dataRecord, indexScanRange.valueMatches, toVersion, includeSoftDeleted)) {
                        continue
                    }

                    if (
                        excludedStartRecordKey != null &&
                        excludedStartIndexValue != null &&
                        Bytes(dataRecord.key.bytes) == excludedStartRecordKey &&
                        indexRecord.value.contentEquals(excludedStartIndexValue)
                    ) {
                        continue
                    }

                    if (scanRequest.shouldBeFiltered(dataRecord, toVersion, recordFetcher, indexScan.index)) {
                        continue
                    }

                    if (!seenKeys.add(Bytes(dataRecord.key.bytes))) {
                        continue
                    }

                    processStoreValue(dataRecord, indexRecord.value)

                    // Break when limit is found
                    if (++currentSize == scanRequest.limit) break
                }
                if (currentSize == scanRequest.limit) break
            }
        }
        DESC -> {
            overallStartKey = if (!keyScanRange.includeStart &&
                startKey != null &&
                startKey.all { it == 0.toByte() } &&
                indexScanRange.ranges.first().end?.let { end -> end.isEmpty() || startKey < end } != false
            ) {
                null
            } else {
                indexScanRange.ranges.first().getDescendingStartKey(startKey, keyScanRange.includeStart)
            }
            overallStopKey = indexScanRange.ranges.last().getAscendingStartKey()

            for (indexRange in indexScanRange.ranges.reversed()) {
                val rangeEnd = indexRange.end
                val startIndex = if (!scanRequest.includeStart &&
                    startKey != null &&
                    startKey.all { it == 0.toByte() } &&
                    (rangeEnd == null || rangeEnd.isEmpty() || startKey < rangeEnd)
                ) {
                    -1
                } else {
                    val lastKey = indexRange.getDescendingStartKey(startKey, scanRequest.includeStart)?.let {
                        if (indexRange.endInclusive && indexRange.end === it) null else it
                    }

                    lastKey?.let { endRange ->
                        if (endRange.isEmpty()) {
                            index.indexValues.lastIndex
                        } else {
                            index.indexValues.binarySearch { it.value compareTo endRange }.let { valueIndex ->
                                when {
                                    valueIndex < 0 -> valueIndex * -1 - 2 // If negative start at before first entry point because it should be before match
                                    else -> valueIndex
                                }
                            }
                        }
                    } ?: index.indexValues.lastIndex
                }

                for (i in min(startIndex, index.indexValues.lastIndex) downTo 0) {
                    val indexRecord = index.indexValues[i]
                    val dataRecord = resolveIndexRecord(dataStore, indexRecord, toVersion, includeSoftDeleted) ?: continue
                    val valueSize = resolveIndexValueSize(indexRecord.value, keyScanRange.keySize, indexScan.index.indexPartCount) ?: continue

                    if (indexRange.keyBeforeStart(indexRecord.value, length = indexRangeLength(indexScanRange, indexRange, valueSize))) {
                        break
                    }

                    if (!indexScanRange.matchesPartials(indexRecord.value, length = valueSize)) {
                        continue
                    }

                    if (!hasAdditionalMatches(index, dataStore, dataRecord, indexScanRange.valueMatches, toVersion, includeSoftDeleted)) {
                        continue
                    }

                    if (
                        excludedStartRecordKey != null &&
                        excludedStartIndexValue != null &&
                        Bytes(dataRecord.key.bytes) == excludedStartRecordKey &&
                        indexRecord.value.contentEquals(excludedStartIndexValue)
                    ) {
                        continue
                    }

                    if (scanRequest.shouldBeFiltered(dataRecord, toVersion, recordFetcher, indexScan.index)) {
                        continue
                    }

                    if (!seenKeys.add(Bytes(dataRecord.key.bytes))) {
                        continue
                    }

                    processStoreValue(dataRecord, indexRecord.value)

                    // Break when limit is found
                    if (++currentSize == scanRequest.limit) break
                }
                if (currentSize == scanRequest.limit) break
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

private fun <DM : IsRootDataModel> hasAdditionalMatches(
    index: AbstractIndexValues<DM, ByteArray>,
    dataStore: DataStore<DM>,
    dataRecord: DataRecord<DM>,
    matches: List<IndexValueMatch>,
    toVersion: HLC?,
    includeSoftDeleted: Boolean
) = matches.all { match ->
    if (match.partialMatch) {
        hasMatchingPrefixValue(index, dataStore, dataRecord, match.toMatch, toVersion, includeSoftDeleted)
    } else {
        val fullIndexValue = createIndexValue(match.toMatch, dataRecord.key.bytes)
        index.resolveRecordForValue(fullIndexValue, toVersion, includeSoftDeleted, dataStore) != null
    }
}

private fun <DM : IsRootDataModel> hasMatchingPrefixValue(
    index: AbstractIndexValues<DM, ByteArray>,
    dataStore: DataStore<DM>,
    dataRecord: DataRecord<DM>,
    prefix: ByteArray,
    toVersion: HLC?,
    includeSoftDeleted: Boolean
): Boolean {
    val startIndex = index.indexValues.binarySearch { it.value compareTo prefix }.let { valueIndex ->
        if (valueIndex < 0) valueIndex * -1 - 1 else valueIndex
    }

    for (i in startIndex until index.indexValues.size) {
        val indexValue = index.indexValues[i]
        if (!indexValue.value.matchesRangePart(0, prefix, length = prefix.size)) {
            return false
        }

        val recordAtVersion = resolveIndexRecord(dataStore, indexValue, toVersion, includeSoftDeleted)
        if (recordAtVersion == dataRecord) {
            return true
        }
    }

    return false
}

private fun createIndexValue(value: ByteArray, key: ByteArray): ByteArray {
    val valueLength = value.size
    return combineToByteArray(
        value,
        ByteArray(valueLength.calculateVarByteLength()).also { lengthBytes ->
            var index = 0
            valueLength.writeVarBytes { lengthBytes[index++] = it }
        },
        key
    )
}

private fun ScanRange.matchesIndexValue(indexScanRange: IndexableScanRanges, indexValue: ByteArray, valueSize: Int): Boolean {
    val rangeLength = indexRangeLength(indexScanRange, this, valueSize)
    return !keyBeforeStart(indexValue, length = rangeLength) && !keyOutOfRange(indexValue, length = rangeLength)
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

private fun resolveIndexValueSize(indexValue: ByteArray, keySize: Int, indexPartCount: Int): Int? {
    if (indexPartCount <= 0 || indexValue.size < keySize) return null

    return try {
        val (offset, size) = findByteIndexAndSizeByPartIndex(
            partIndex = indexPartCount - 1,
            indexable = indexValue,
            keySize = keySize,
            indexPartCount = indexPartCount
        )
        offset + size
    } catch (_: Exception) {
        null
    }
}
