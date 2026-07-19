package maryk.datastore.foundationdb.processors

import maryk.foundationdb.Range
import maryk.foundationdb.Transaction
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.exceptions.StorageException
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.processors.datastore.findByteIndexAndSizeByPartIndex
import maryk.core.processors.datastore.matchers.IndexPartialSizeToMatch
import maryk.core.processors.datastore.matchers.IndexPartialToMatch
import maryk.core.processors.datastore.matchers.IndexPartialToBeOneOf
import maryk.core.processors.datastore.scanRange.IndexableScanRanges
import maryk.core.processors.datastore.scanRange.IndexValueMatch
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.processors.datastore.scanRange.ScanRange
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsMapReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SetReference
import maryk.core.properties.types.Key
import maryk.core.query.orders.Direction
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.requests.IsScanRequest
import maryk.core.query.requests.ScanContinuation
import maryk.core.query.responses.DataFetchType
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.values.IsValuesGetter
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.helpers.ByteArrayKey
import maryk.datastore.foundationdb.processors.helpers.DecryptValue
import maryk.datastore.foundationdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.concatArrays
import maryk.datastore.foundationdb.processors.helpers.decodeZeroFreeUsing01OrNull
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.forEachInRangeBatch
import maryk.datastore.foundationdb.processors.helpers.getValue
import maryk.datastore.foundationdb.processors.helpers.nextBlocking
import maryk.datastore.foundationdb.processors.helpers.packDescendingExclusiveEnd
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.readCreationVersion
import maryk.datastore.foundationdb.processors.helpers.readReversedVersionBytes
import maryk.datastore.foundationdb.processors.helpers.readMapByReference
import maryk.datastore.foundationdb.processors.helpers.readSetByReference
import maryk.datastore.foundationdb.processors.helpers.toReversedVersionBytes
import maryk.datastore.foundationdb.processors.helpers.asByteArrayKey
import maryk.datastore.foundationdb.processors.helpers.TransactionRunner
import maryk.datastore.shared.ScanType
import maryk.datastore.shared.isSkippableDataError
import maryk.datastore.shared.rethrowIfFatal
import maryk.datastore.shared.helpers.convertToValue
import maryk.lib.extensions.compare.compareDefinedRange
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.compareToRange
import maryk.lib.extensions.compare.matchesRange
import maryk.lib.extensions.compare.matchesRangePart
import maryk.lib.exceptions.ParseException
import kotlin.math.min

internal fun <DM : IsRootDataModel> scanIndex(
    transactionRunner: TransactionRunner,
    tableDirs: IsTableDirectories,
    scanRequest: IsScanRequest<DM, *>,
    indexScan: ScanType.IndexScan,
    keyScanRange: KeyScanRanges,
    continuation: ScanContinuation?,
    decryptValue: DecryptValue? = null,
    processStoreValue: (Transaction, Key<DM>, ULong, ByteArray?) -> Unit
): DataFetchType {
    val indexReference = indexScan.index.referenceStorageByteArray.bytes
    val indexScanRange = indexScan.index.createScanRange(scanRequest.where, keyScanRange)

    val keySize = scanRequest.dataModel.Meta.keyByteSize
    val baseOffset = tableDirs.indexPrefix.size
    val valueOffset = baseOffset + indexReference.size
    val useHistoric = scanRequest.toVersion != null
    if (useHistoric && tableDirs !is HistoricTableDirectories) {
        throw StorageException("No historic table stored so toVersion in query cannot be processed")
    }
    val versionSize = if (useHistoric) VERSION_BYTE_SIZE else 0
    val indexPartCount = indexScan.index.indexPartCount

    // Compute response metadata start/stop
    // Build a helper valuesGetter for computing index start from the startKey (respecting toVersion)
    val startIndexKey: ByteArray? = continuation?.let {
        it.orderKey?.bytes ?: throw RequestException("Scan cursor has no index ordering boundary")
    } ?: scanRequest.startKey?.let { startKey -> transactionRunner.run { tr ->
        val startKeyBytes = startKey.bytes
        val getter = createIndexValuesGetter(
            tr = tr,
            tableDirs = tableDirs,
            keyBytes = startKeyBytes,
            toVersion = scanRequest.toVersion,
            decryptValue = decryptValue,
        )
        try {
            selectIndexKeyForScan(
                valuesGetter = getter,
                index = indexScan.index,
                direction = indexScan.direction,
                keyBytes = startKeyBytes,
                indexScanRange = indexScanRange
            )
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            if (!error.isSkippableDataError()) {
                throw error
            }
            scanRequest.toVersion?.let { toVersion ->
                findHistoricIndexEntryForKey(
                    transactionRunner = transactionRunner,
                    tableDirs = tableDirs as? HistoricTableDirectories ?: return@let null,
                    indexReference = indexReference,
                    key = startKeyBytes,
                    toVersion = toVersion,
                    direction = indexScan.direction
                )
            } ?: findCurrentIndexEntryForKey(
                tr = tr,
                tableDirs = tableDirs,
                indexReference = indexReference,
                key = startKey.bytes,
                direction = indexScan.direction
            )
        }
    } }

    val responseStartKey = when (indexScan.direction) {
        ASC -> startIndexKey?.let {
            indexScanRange.ranges.first().getAscendingStartKey(it, keyScanRange.includeStart)
        } ?: indexScanRange.ranges.first().start
        DESC -> indexScanRange.ranges.first().getDescendingStartKey(startIndexKey, keyScanRange.includeStart)
    }
    val responseStopKey = when (indexScan.direction) {
        ASC -> indexScanRange.ranges.last().getDescendingStartKey()
        DESC -> indexScanRange.ranges.last().getAscendingStartKey()
    }

    data class Rec(val sort: ByteArray, val keyBytes: ByteArray, val created: ULong)

    if (!useHistoric) {
        val base = packKey(tableDirs.indexPrefix, indexReference)
        val baseEnd = Range.startsWith(base).end
        val ranges = indexScanRange.ranges
        var emitted = 0u
        val seenKeys = mutableSetOf<ByteArrayKey>()

        when (indexScan.direction) {
            ASC -> {
                var startKeyFilter = startIndexKey
                var includeStartFilter = keyScanRange.includeStart
                for (i in ranges.indices) {
                    if (emitted >= scanRequest.limit) break
                    val range = ranges[i]
                    val effectiveStartKey = startKeyFilter.takeIf { i == 0 }

                    val startSeg = effectiveStartKey?.let {
                        range.getAscendingStartKey(it, if (i == 0) includeStartFilter else true)
                    } ?: range.start
                    val begin = concatArrays(base, startSeg)
                    val end = when (val endExclusiveSeg = range.getDescendingStartKey()) {
                        null -> baseEnd
                        else -> if (endExclusiveSeg.isEmpty()) baseEnd else concatArrays(base, endExclusiveSeg)
                    }

                    val cmpLength = min(begin.size, end.size)
                    val beginGteEnd = when (val cmp = begin.compareDefinedRange(end, 0, cmpLength)) {
                        0 -> begin.size >= end.size
                        else -> cmp > 0
                    }
                    if (beginGteEnd) continue

                    var nextBegin = begin
                    while (emitted < scanRequest.limit) {
                        val result = transactionRunner.run { tr ->
                            tr.forEachInRangeBatch(Range(nextBegin, end), false) { kv ->
                                if (emitted >= scanRequest.limit) return@forEachInRangeBatch false
                                val indexKeyBytes = kv.key
                                val totalLen = indexKeyBytes.size
                                val valueSize = findIndexValueSize(
                                    indexKeyBytes,
                                    valueOffset,
                                    totalLen - versionSize,
                                    keySize,
                                    indexPartCount
                                )
                                if (valueSize < 0) return@forEachInRangeBatch true
                                if (!indexScanRange.matchesPartials(indexKeyBytes, valueOffset, valueSize, totalLen - versionSize)) return@forEachInRangeBatch true
                                val keyOffset = totalLen - keySize - versionSize
                                var keyReadIndex = keyOffset
                                val key = scanRequest.dataModel.key { indexKeyBytes[keyReadIndex++] }
                                if (continuation?.key?.bytes?.contentEquals(key.bytes) == true) return@forEachInRangeBatch true
                                if (!hasAdditionalMatches(transactionRunner, tr, tableDirs, indexReference, key.bytes, indexScanRange.valueMatches, null)) return@forEachInRangeBatch true
                                val createdVersion = tr.readCreationVersion(tableDirs, key.bytes, scanRequest.toVersion)
                                    ?: return@forEachInRangeBatch true
                                if (scanRequest.shouldBeFiltered(tr, tableDirs, key.bytes, 0, keySize, createdVersion, scanRequest.toVersion, decryptValue, indexScan.index)) {
                                    return@forEachInRangeBatch true
                                }
                                if (effectiveStartKey != null) {
                                    val cmp = compareDefinedRange(
                                        indexKeyBytes,
                                        valueOffset,
                                        totalLen - versionSize - valueOffset,
                                        effectiveStartKey
                                    )
                                    if (cmp < 0) return@forEachInRangeBatch true
                                    if (!includeStartFilter && cmp == 0) return@forEachInRangeBatch true
                                }

                                if (continuation != null) {
                                    val getter = createIndexValuesGetter(
                                        tr = tr,
                                        tableDirs = tableDirs,
                                        keyBytes = key.bytes,
                                        toVersion = null,
                                        decryptValue = decryptValue,
                                    )
                                    val canonicalIndexValue = selectIndexKeyForScan(
                                        valuesGetter = getter,
                                        index = indexScan.index,
                                        direction = indexScan.direction,
                                        keyBytes = key.bytes,
                                        indexScanRange = indexScanRange,
                                    )
                                    val currentIndexValue = indexKeyBytes.copyOfRange(valueOffset, totalLen)
                                    if (canonicalIndexValue?.contentEquals(currentIndexValue) != true) {
                                        return@forEachInRangeBatch true
                                    }
                                }

                                if (!seenKeys.add(key.bytes.asByteArrayKey(copy = true))) return@forEachInRangeBatch true

                                val sortingKey = indexKeyBytes.copyOfRange(valueOffset, totalLen - versionSize)
                                processStoreValue(tr, key, createdVersion, sortingKey)
                                emitted++
                                emitted < scanRequest.limit
                            }
                        }

                        if (result.completed || emitted >= scanRequest.limit) break
                        nextBegin = result.lastKey?.let { it + byteArrayOf(0) } ?: break
                    }

                    if (startKeyFilter != null) {
                        startKeyFilter = null
                        includeStartFilter = true
                    }
                }
            }
            DESC -> {
                var startUpperBoundApplied = false
                for (rangeIndex in ranges.indices.reversed()) {
                    if (emitted >= scanRequest.limit) break
                    val range = ranges[rangeIndex]

                    if (!startUpperBoundApplied && startIndexKey != null &&
                        range.keyBeforeStart(startIndexKey, 0, startIndexKey.size)
                    ) {
                        continue
                    }

                    val startSeg = range.getAscendingStartKey(null, true)
                    val begin = concatArrays(base, startSeg)
                    val end = if (!startUpperBoundApplied && startIndexKey != null &&
                        !range.keyOutOfRange(startIndexKey, 0, startIndexKey.size)
                    ) {
                        startUpperBoundApplied = true
                        packDescendingExclusiveEnd(keyScanRange.includeStart, base, startIndexKey)
                    } else {
                        when (val endExclusiveSeg = range.getDescendingStartKey()) {
                            null -> baseEnd
                            else -> if (endExclusiveSeg.isEmpty()) baseEnd else concatArrays(base, endExclusiveSeg)
                        }
                    }

                    val cmpLength = min(begin.size, end.size)
                    val beginGteEnd = when (val cmp = begin.compareDefinedRange(end, 0, cmpLength)) {
                        0 -> begin.size >= end.size
                        else -> cmp > 0
                    }
                    if (beginGteEnd) continue

                    var nextEnd = end
                    while (emitted < scanRequest.limit) {
                        val result = transactionRunner.run { tr ->
                            tr.forEachInRangeBatch(Range(begin, nextEnd), true) { kv ->
                                if (emitted >= scanRequest.limit) return@forEachInRangeBatch false
                                val indexKeyBytes = kv.key
                                val totalLen = indexKeyBytes.size
                                val valueSize = findIndexValueSize(
                                    indexKeyBytes,
                                    valueOffset,
                                    totalLen - versionSize,
                                    keySize,
                                    indexPartCount
                                )
                                if (valueSize < 0) return@forEachInRangeBatch true
                                if (startIndexKey != null) {
                                    val cmp = compareDefinedRange(
                                        indexKeyBytes,
                                        valueOffset,
                                        totalLen - versionSize - valueOffset,
                                        startIndexKey
                                    )
                                    if (cmp > 0) return@forEachInRangeBatch true
                                    if (!keyScanRange.includeStart && cmp == 0) return@forEachInRangeBatch true
                                }
                                if (!indexScanRange.matchesPartials(indexKeyBytes, valueOffset, valueSize, totalLen - versionSize)) return@forEachInRangeBatch true
                                val keyOffset = totalLen - keySize - versionSize
                                var keyReadIndex = keyOffset
                                val key = scanRequest.dataModel.key { indexKeyBytes[keyReadIndex++] }
                                if (continuation?.key?.bytes?.contentEquals(key.bytes) == true) return@forEachInRangeBatch true
                                if (!hasAdditionalMatches(transactionRunner, tr, tableDirs, indexReference, key.bytes, indexScanRange.valueMatches, null)) return@forEachInRangeBatch true
                                val createdVersion = tr.readCreationVersion(tableDirs, key.bytes, scanRequest.toVersion)
                                    ?: return@forEachInRangeBatch true
                                if (scanRequest.shouldBeFiltered(tr, tableDirs, key.bytes, 0, keySize, createdVersion, scanRequest.toVersion, decryptValue, indexScan.index)) {
                                    return@forEachInRangeBatch true
                                }
                                if (continuation != null) {
                                    val getter = createIndexValuesGetter(
                                        tr = tr,
                                        tableDirs = tableDirs,
                                        keyBytes = key.bytes,
                                        toVersion = null,
                                        decryptValue = decryptValue,
                                    )
                                    val canonicalIndexValue = selectIndexKeyForScan(
                                        valuesGetter = getter,
                                        index = indexScan.index,
                                        direction = indexScan.direction,
                                        keyBytes = key.bytes,
                                        indexScanRange = indexScanRange,
                                    )
                                    val currentIndexValue = indexKeyBytes.copyOfRange(valueOffset, totalLen)
                                    if (canonicalIndexValue?.contentEquals(currentIndexValue) != true) {
                                        return@forEachInRangeBatch true
                                    }
                                }
                                if (!seenKeys.add(key.bytes.asByteArrayKey(copy = true))) return@forEachInRangeBatch true

                                val sortingKey = indexKeyBytes.copyOfRange(valueOffset, totalLen - versionSize)
                                processStoreValue(tr, key, createdVersion, sortingKey)
                                emitted++
                                emitted < scanRequest.limit
                            }
                        }

                        if (result.completed || emitted >= scanRequest.limit) break
                        nextEnd = result.lastKey ?: break
                    }
                }
            }
        }
    } else {
        require(tableDirs is HistoricTableDirectories)
        val histBase = tableDirs.historicIndexPrefix
        val basePrefix = encodeZeroFreeUsing01(indexReference)
        val baseEnd = Range.startsWith(packKey(histBase, basePrefix)).end
        val versionFloor = scanRequest.toVersion!!.toReversedVersionBytes()
        val toVersionBytes = versionFloor
        val selectedByKey = mutableMapOf<ByteArrayKey, Rec>()
        var descendingUpperBoundApplied = false

        val ranges = indexScanRange.ranges
        for (i in ranges.indices) {
            val range = ranges[i]
            if (indexScan.direction == DESC && startIndexKey != null &&
                range.keyBeforeStart(startIndexKey, 0, startIndexKey.size)
            ) {
                continue
            }

            val startSeg = when (indexScan.direction) {
                ASC -> startIndexKey.takeIf { i == 0 }?.let {
                    range.getAscendingStartKey(it, if (i == 0) keyScanRange.includeStart else true)
                } ?: range.start
                DESC -> range.getAscendingStartKey(null, true)
            }

            val beginQualifier = encodeZeroFreeUsing01(concatArrays(indexReference, startSeg))
            val begin = packKey(histBase, beginQualifier, byteArrayOf(0), versionFloor)
            val end = if (indexScan.direction == DESC && startIndexKey != null &&
                !descendingUpperBoundApplied &&
                !range.keyOutOfRange(startIndexKey, 0, startIndexKey.size)
            ) {
                descendingUpperBoundApplied = true
                val qualifier = encodeZeroFreeUsing01(concatArrays(indexReference, startIndexKey))
                packDescendingExclusiveEnd(keyScanRange.includeStart, histBase, qualifier, byteArrayOf(0), versionFloor)
            } else {
                when (val endExclusiveSeg = range.getDescendingStartKey()) {
                    null -> baseEnd
                    else -> {
                        if (endExclusiveSeg.isEmpty()) {
                            baseEnd
                        } else {
                            val endQualifier = encodeZeroFreeUsing01(concatArrays(indexReference, endExclusiveSeg))
                            packKey(histBase, endQualifier)
                        }
                    }
                }
            }

            val cmpLength = min(begin.size, end.size)
            val beginGteEnd = when (val cmp = begin.compareDefinedRange(end, 0, cmpLength)) {
                0 -> begin.size >= end.size
                else -> cmp > 0
            }
            if (beginGteEnd) continue

            var lastQualifierSource: ByteArray? = null
            var lastQualifierOffset = 0
            var lastQualifierLength = 0
            var nextBegin = begin
            while (true) {
                val result = transactionRunner.run { tr ->
                    tr.forEachInRangeBatch(Range(nextBegin, end), false) { kv ->
                        val k = kv.key
                        val versionOffset = k.size - toVersionBytes.size
                        val sepIndex = versionOffset - 1
                        if (sepIndex < histBase.size || k[sepIndex] != 0.toByte()) return@forEachInRangeBatch true
                        if (toVersionBytes.compareToRange(k, versionOffset) > 0) return@forEachInRangeBatch true

                        val encQualifierOffset = histBase.size
                        val encQualifierLength = sepIndex - histBase.size
                        if (
                            lastQualifierSource != null &&
                            qualifierMatches(
                                lastQualifierSource!!,
                                lastQualifierOffset,
                                lastQualifierLength,
                                k,
                                encQualifierOffset,
                                encQualifierLength
                            )
                        ) return@forEachInRangeBatch true
                        lastQualifierSource = k
                        lastQualifierOffset = encQualifierOffset
                        lastQualifierLength = encQualifierLength
                        if (!isHistoricIndexVisible(kv.value)) return@forEachInRangeBatch true
                        val qualifier = decodeZeroFreeUsing01OrNull(k, encQualifierOffset, encQualifierLength) ?: return@forEachInRangeBatch true
                        if (qualifier.size <= indexReference.size) return@forEachInRangeBatch true
                        val valueSize = findIndexValueSize(
                            qualifier,
                            indexReference.size,
                            qualifier.size,
                            keySize,
                            indexPartCount
                        )
                        if (valueSize < 0) return@forEachInRangeBatch true

                        if (!indexScanRange.matchesPartials(qualifier, indexReference.size, valueSize, qualifier.size)) return@forEachInRangeBatch true

                        val keyOffset = qualifier.size - keySize
                        val keyBytes = qualifier.copyOfRange(keyOffset, qualifier.size)
                        val createdVersion = tr.readCreationVersion(tableDirs, keyBytes, scanRequest.toVersion)
                            ?: return@forEachInRangeBatch true
                        if (!hasHistoricAdditionalMatches(transactionRunner, tr, tableDirs, indexReference, keyBytes, indexScanRange.valueMatches, scanRequest.toVersion!!)) return@forEachInRangeBatch true
                        if (scanRequest.shouldBeFiltered(tr, tableDirs, keyBytes, 0, keySize, createdVersion, scanRequest.toVersion, decryptValue, indexScan.index)) {
                            return@forEachInRangeBatch true
                        }

                        val rec = Rec(qualifier.copyOfRange(indexReference.size, qualifier.size), keyBytes, createdVersion)
                        val keyRef = keyBytes.asByteArrayKey()
                        val prev = selectedByKey[keyRef]
                        if (
                            prev == null ||
                            when (indexScan.direction) {
                                ASC -> rec.sort compareTo prev.sort < 0
                                DESC -> rec.sort compareTo prev.sort > 0
                            }
                        ) {
                            selectedByKey[keyRef] = rec
                        }
                        true
                    }
                }

                if (result.completed) break
                nextBegin = result.lastKey?.let { it + byteArrayOf(0) } ?: break
            }
        }

        val results = selectedByKey.values.toMutableList()
        results.sortWith { a, b -> a.sort compareTo b.sort }

        var emitted = 0u
        when (indexScan.direction) {
            ASC -> {
                var idx = 0
                startIndexKey?.let { si ->
                    while (idx < results.size && results[idx].sort.compareTo(si) < 0) idx++
                    if (!keyScanRange.includeStart && idx < results.size && results[idx].sort.contentEquals(si)) idx++
                }
                while (idx < results.size && emitted < scanRequest.limit) {
                    val rec = results[idx++]
                    if (continuation?.key?.bytes?.contentEquals(rec.keyBytes) == true) continue
                    val key = scanRequest.dataModel.key(rec.keyBytes)
                    transactionRunner.run { tr ->
                        processStoreValue(tr, key, rec.created, rec.sort)
                    }
                    emitted++
                }
            }
            DESC -> {
                var idx = results.lastIndex
                startIndexKey?.let { si ->
                    while (idx >= 0 && results[idx].sort.compareTo(si) > 0) idx--
                    if (!keyScanRange.includeStart && idx >= 0 && results[idx].sort.contentEquals(si)) idx--
                }
                while (idx >= 0 && emitted < scanRequest.limit) {
                    val rec = results[idx--]
                    if (continuation?.key?.bytes?.contentEquals(rec.keyBytes) == true) continue
                    val key = scanRequest.dataModel.key(rec.keyBytes)
                    transactionRunner.run { tr ->
                        processStoreValue(tr, key, rec.created, rec.sort)
                    }
                    emitted++
                }
            }
        }
    }

    return FetchByIndexScan(
        index = indexReference,
        direction = indexScan.direction,
        startKey = responseStartKey,
        stopKey = responseStopKey
    )
}

private fun createIndexValuesGetter(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    keyBytes: ByteArray,
    toVersion: ULong?,
    decryptValue: DecryptValue?,
): IsValuesGetter = object : IsValuesGetter {
    private val cache = HashMap<IsPropertyReference<*, *, *>, Any?>(8)

    override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(
        propertyReference: IsPropertyReference<T, D, C>,
    ): T? {
        if (cache.containsKey(propertyReference)) {
            @Suppress("UNCHECKED_CAST")
            return cache[propertyReference] as T?
        }

        val value = if (propertyReference is IsMapReference<*, *, *, *> && toVersion == null) {
            @Suppress("UNCHECKED_CAST")
            tr.readMapByReference(
                tableDirs.tablePrefix,
                keyBytes,
                propertyReference as IsMapReference<Any, Any, IsPropertyContext, *>,
                decryptValue,
            ) as T?
        } else if (propertyReference is SetReference<*, *> && toVersion == null) {
            @Suppress("UNCHECKED_CAST")
            tr.readSetByReference(
                tableDirs.tablePrefix,
                keyBytes,
                propertyReference as SetReference<Any, IsPropertyContext>,
            ) as T?
        } else {
            tr.getValue(
                tableDirs,
                toVersion,
                keyBytes,
                propertyReference.toStorageByteArray(),
                decryptValue = decryptValue,
            ) { valueBytes, offset, length ->
                valueBytes.convertToValue(propertyReference, offset, length) as T?
            }
        }

        cache[propertyReference] = value
        return value
    }
}

private fun selectIndexKeyForScan(
    valuesGetter: IsValuesGetter,
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

private fun findCurrentIndexEntryForKey(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    indexReference: ByteArray,
    key: ByteArray,
    direction: Direction
): ByteArray? {
    val prefix = packKey(tableDirs.indexPrefix, indexReference)
    val iterator = tr.getRange(Range.startsWith(prefix)).iterator()
    var matchedValueAndKey: ByteArray? = null

    while (iterator.hasNext()) {
        val kv = iterator.nextBlocking()
        val indexKeyBytes = kv.key
        val keyOffset = indexKeyBytes.size - key.size
        if (indexKeyBytes.size < prefix.size + key.size) {
            continue
        }
        if (indexKeyBytes.matchesRangePart(keyOffset, key, length = key.size)) {
            matchedValueAndKey = indexKeyBytes.copyOfRange(prefix.size, indexKeyBytes.size)
            if (direction == ASC) {
                break
            }
        }
    }

    return matchedValueAndKey
}

private fun findHistoricIndexEntryForKey(
    transactionRunner: TransactionRunner,
    tableDirs: HistoricTableDirectories,
    indexReference: ByteArray,
    key: ByteArray,
    toVersion: ULong,
    direction: Direction
): ByteArray? {
    val scanPrefix = packKey(tableDirs.historicIndexPrefix, encodeZeroFreeUsing01(indexReference))
    val toVersionBytes = toVersion.toReversedVersionBytes()
    var settledQualifierSource: ByteArray? = null
    var settledQualifierOffset = 0
    var settledQualifierLength = 0
    var matchedValueAndKey: ByteArray? = null
    val range = Range.startsWith(scanPrefix)
    var nextBegin = range.begin

    while (true) {
        var foundAscendingMatch = false
        val result = transactionRunner.run { tr ->
            tr.forEachInRangeBatch(Range(nextBegin, range.end), false) { kv ->
                val historicKey = kv.key
                val versionOffset = historicKey.size - toVersionBytes.size
                val sepIndex = versionOffset - 1
                if (sepIndex < tableDirs.historicIndexPrefix.size || historicKey[sepIndex] != 0.toByte()) return@forEachInRangeBatch true

                val encodedQualifierOffset = tableDirs.historicIndexPrefix.size
                val encodedQualifierLength = sepIndex - encodedQualifierOffset
                if (!historicKey.matchesRangePart(0, scanPrefix, length = scanPrefix.size)) {
                    return@forEachInRangeBatch false
                }
                if (toVersionBytes.compareToRange(historicKey, versionOffset) > 0) return@forEachInRangeBatch true

                if (
                    settledQualifierSource != null &&
                    qualifierMatches(
                        settledQualifierSource!!,
                        settledQualifierOffset,
                        settledQualifierLength,
                        historicKey,
                        encodedQualifierOffset,
                        encodedQualifierLength
                    )
                ) return@forEachInRangeBatch true

                settledQualifierSource = historicKey
                settledQualifierOffset = encodedQualifierOffset
                settledQualifierLength = encodedQualifierLength

                if (!isHistoricIndexVisible(kv.value)) return@forEachInRangeBatch true

                val qualifier = decodeZeroFreeUsing01OrNull(historicKey, encodedQualifierOffset, encodedQualifierLength) ?: return@forEachInRangeBatch true
                if (qualifier.size < indexReference.size + key.size) return@forEachInRangeBatch true

                val keyOffset = qualifier.size - key.size
                if (!qualifier.matchesRangePart(keyOffset, key, length = key.size)) return@forEachInRangeBatch true

                matchedValueAndKey = qualifier.copyOfRange(indexReference.size, qualifier.size)
                if (direction == ASC) {
                    foundAscendingMatch = true
                    return@forEachInRangeBatch false
                }
                true
            }
        }

        if (foundAscendingMatch || result.stoppedByCallback) {
            return matchedValueAndKey
        }
        if (result.completed) {
            return matchedValueAndKey
        }
        nextBegin = result.lastKey?.let { it + byteArrayOf(0) } ?: return matchedValueAndKey
    }
}

private fun isHistoricIndexVisible(value: ByteArray) = !value.contentEquals(HISTORIC_REMOVAL_MARKER)

private fun hasAdditionalMatches(
    transactionRunner: TransactionRunner,
    tr: Transaction,
    tableDirs: IsTableDirectories,
    indexReference: ByteArray,
    recordKey: ByteArray,
    matches: List<IndexValueMatch>,
    toVersion: ULong?
) = matches.all { match ->
    if (toVersion == null) {
        if (match.partialMatch) {
            hasMatchingPrefixValue(transactionRunner, tableDirs, indexReference, recordKey, match.toMatch)
        } else {
            hasMatchingExactValue(tr, tableDirs, indexReference, recordKey, match.toMatch)
        }
    } else {
        val historicTableDirs = tableDirs as? HistoricTableDirectories ?: return@all false
        if (match.partialMatch) {
            hasHistoricMatchingPrefixValue(transactionRunner, historicTableDirs, indexReference, recordKey, match.toMatch, toVersion)
        } else {
            hasHistoricMatchingExactValue(transactionRunner, historicTableDirs, indexReference, recordKey, match.toMatch, toVersion)
        }
    }
}

private fun hasMatchingExactValue(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    indexReference: ByteArray,
    recordKey: ByteArray,
    value: ByteArray
) = tr.get(
    packKey(tableDirs.indexPrefix, indexReference, createIndexValue(value, recordKey))
).awaitResult() != null

private fun hasMatchingPrefixValue(
    transactionRunner: TransactionRunner,
    tableDirs: IsTableDirectories,
    indexReference: ByteArray,
    recordKey: ByteArray,
    prefix: ByteArray
): Boolean {
    val prefixKey = packKey(tableDirs.indexPrefix, indexReference, prefix)
    val range = Range.startsWith(prefixKey)
    var nextBegin = range.begin

    while (true) {
        var found = false
        val result = transactionRunner.run { tr ->
            tr.forEachInRangeBatch(Range(nextBegin, range.end), false) {
                val indexKeyBytes = it.key
                val keyOffset = indexKeyBytes.size - recordKey.size
                if (indexKeyBytes.size < prefixKey.size + recordKey.size) return@forEachInRangeBatch true
                if (indexKeyBytes.matchesRangePart(keyOffset, recordKey, length = recordKey.size)) {
                    found = true
                    return@forEachInRangeBatch false
                }
                true
            }
        }

        if (found) return true
        if (result.stoppedByCallback) return false
        if (result.completed) return false
        nextBegin = result.lastKey?.let { it + byteArrayOf(0) } ?: return false
    }
}

private fun hasHistoricAdditionalMatches(
    transactionRunner: TransactionRunner,
    tr: Transaction,
    tableDirs: HistoricTableDirectories,
    indexReference: ByteArray,
    recordKey: ByteArray,
    matches: List<IndexValueMatch>,
    toVersion: ULong
) = matches.all { match ->
    if (match.partialMatch) {
        hasHistoricMatchingPrefixValue(transactionRunner, tableDirs, indexReference, recordKey, match.toMatch, toVersion)
    } else {
        hasHistoricMatchingExactValue(transactionRunner, tableDirs, indexReference, recordKey, match.toMatch, toVersion)
    }
}

private fun hasHistoricMatchingExactValue(
    transactionRunner: TransactionRunner,
    tableDirs: HistoricTableDirectories,
    indexReference: ByteArray,
    recordKey: ByteArray,
    value: ByteArray,
    toVersion: ULong
): Boolean {
    val qualifier = encodeZeroFreeUsing01(concatArrays(indexReference, createIndexValue(value, recordKey)))
    val prefix = packKey(tableDirs.historicIndexPrefix, qualifier)
    val toVersionBytes = toVersion.toReversedVersionBytes()
    val range = Range.startsWith(prefix)
    var nextBegin = range.begin

    while (true) {
        var matchedValue: Boolean? = null
        val result = transactionRunner.run { tr ->
            tr.forEachInRangeBatch(Range(nextBegin, range.end), false) { kv ->
                val versionOffset = prefix.size + 1
                if (kv.key.size != versionOffset + VERSION_BYTE_SIZE || kv.key[prefix.size] != 0.toByte()) return@forEachInRangeBatch true
                if (toVersionBytes.compareToRange(kv.key, versionOffset) <= 0) {
                    matchedValue = isHistoricIndexVisible(kv.value)
                    return@forEachInRangeBatch false
                }
                true
            }
        }

        matchedValue?.let { return it }
        if (result.stoppedByCallback) return false
        if (result.completed) return false
        nextBegin = result.lastKey?.let { it + byteArrayOf(0) } ?: return false
    }
}

private fun createIndexValue(value: ByteArray, key: ByteArray): ByteArray {
    val valueLength = value.size
    val valueLengthBytes = valueLength.calculateVarByteLength()
    return ByteArray(valueLength + valueLengthBytes + key.size).also { output ->
        value.copyInto(output, 0)
        var index = valueLength
        valueLength.writeVarBytes { output[index++] = it }
        key.copyInto(output, index)
    }
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
        // Skip malformed index rows instead of aborting the whole scan.
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
                    it.toMatch.contentEquals(range.start)) ||
                (it is IndexPartialToBeOneOf &&
                    it.partialMatch &&
                    it.toBeOneOf.any(range.start::contentEquals))
        } == true
    ) {
        range.start.size
    } else {
        valueSize
    }

private fun compareDefinedRange(
    source: ByteArray,
    offset: Int,
    length: Int,
    other: ByteArray
): Int {
    val minSize = min(other.size, length)
    var index = 0
    while (index < minSize) {
        val a = source[offset + index].toInt() and 0xFF
        val b = other[index].toInt() and 0xFF
        if (a != b) {
            return a - b
        }
        index++
    }
    return length - other.size
}

private fun hasHistoricMatchingPrefixValue(
    transactionRunner: TransactionRunner,
    tableDirs: HistoricTableDirectories,
    indexReference: ByteArray,
    recordKey: ByteArray,
    prefix: ByteArray,
    toVersion: ULong
): Boolean {
    val qualifierPrefix = encodeZeroFreeUsing01(concatArrays(indexReference, prefix))
    val scanPrefix = packKey(tableDirs.historicIndexPrefix, qualifierPrefix)
    val toVersionBytes = toVersion.toReversedVersionBytes()
    var settledQualifierSource: ByteArray? = null
    var settledQualifierOffset = 0
    var settledQualifierLength = 0
    val range = Range.startsWith(scanPrefix)
    var nextBegin = range.begin

    while (true) {
        var matchedValue: Boolean? = null
        val result = transactionRunner.run { tr ->
            tr.forEachInRangeBatch(Range(nextBegin, range.end), false) { kv ->
                val key = kv.key
                val versionOffset = key.size - toVersionBytes.size
                val sepIndex = versionOffset - 1
                if (sepIndex < tableDirs.historicIndexPrefix.size || key[sepIndex] != 0.toByte()) return@forEachInRangeBatch true

                val encodedQualifierOffset = tableDirs.historicIndexPrefix.size
                val encodedQualifierLength = sepIndex - encodedQualifierOffset
                if (!key.matchesRangePart(encodedQualifierOffset, qualifierPrefix, sourceLength = encodedQualifierLength, length = qualifierPrefix.size)) {
                    return@forEachInRangeBatch false
                }
                if (
                    settledQualifierSource != null &&
                    qualifierMatches(
                        settledQualifierSource!!,
                        settledQualifierOffset,
                        settledQualifierLength,
                        key,
                        encodedQualifierOffset,
                        encodedQualifierLength
                    )
                ) return@forEachInRangeBatch true

                if (toVersionBytes.compareToRange(key, versionOffset) > 0) {
                    return@forEachInRangeBatch true
                }

                val qualifier = decodeZeroFreeUsing01OrNull(key, encodedQualifierOffset, encodedQualifierLength) ?: return@forEachInRangeBatch true
                if (qualifier.size <= indexReference.size + recordKey.size) {
                    settledQualifierSource = key
                    settledQualifierOffset = encodedQualifierOffset
                    settledQualifierLength = encodedQualifierLength
                    return@forEachInRangeBatch true
                }

                val keyOffset = qualifier.size - recordKey.size
                if (keyOffset < indexReference.size || !qualifier.matchesRangePart(keyOffset, recordKey, length = recordKey.size)) {
                    settledQualifierSource = key
                    settledQualifierOffset = encodedQualifierOffset
                    settledQualifierLength = encodedQualifierLength
                    return@forEachInRangeBatch true
                }

                if (isHistoricIndexVisible(kv.value)) {
                    matchedValue = true
                    return@forEachInRangeBatch false
                }

                settledQualifierSource = key
                settledQualifierOffset = encodedQualifierOffset
                settledQualifierLength = encodedQualifierLength
                true
            }
        }

        matchedValue?.let { return it }
        if (result.stoppedByCallback) return false
        if (result.completed) return false
        nextBegin = result.lastKey?.let { it + byteArrayOf(0) } ?: return false
    }
}

private fun qualifierMatches(
    left: ByteArray,
    leftOffset: Int,
    leftLength: Int,
    right: ByteArray,
    rightOffset: Int,
    rightLength: Int
): Boolean {
    if (leftLength != rightLength) return false
    return left.matchesRange(leftOffset, right, sourceLength = leftLength, offset = rightOffset, length = rightLength)
}
