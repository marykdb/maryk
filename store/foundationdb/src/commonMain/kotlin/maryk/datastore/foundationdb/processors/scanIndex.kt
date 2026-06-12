package maryk.datastore.foundationdb.processors

import maryk.foundationdb.Range
import maryk.foundationdb.Transaction
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.exceptions.StorageException
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.processors.datastore.scanRange.IndexValueMatch
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsMapReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SetReference
import maryk.core.properties.types.Key
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.requests.IsScanRequest
import maryk.core.query.responses.DataFetchType
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.values.IsValuesGetter
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.helpers.ByteArrayKey
import maryk.datastore.foundationdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.decodeZeroFreeUsing01OrNull
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.forEachInRangeBatch
import maryk.datastore.foundationdb.processors.helpers.getValue
import maryk.datastore.foundationdb.processors.helpers.packDescendingExclusiveEnd
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.readHLCTimestampIfPresent
import maryk.datastore.foundationdb.processors.helpers.readReversedVersionBytes
import maryk.datastore.foundationdb.processors.helpers.readMapByReference
import maryk.datastore.foundationdb.processors.helpers.readSetByReference
import maryk.datastore.foundationdb.processors.helpers.toReversedVersionBytes
import maryk.datastore.foundationdb.processors.helpers.asByteArrayKey
import maryk.datastore.foundationdb.processors.helpers.TransactionRunner
import maryk.datastore.shared.ScanType
import maryk.datastore.shared.helpers.convertToValue
import maryk.lib.bytes.combineToByteArray
import maryk.lib.extensions.compare.compareDefinedRange
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.compareToRange
import maryk.lib.extensions.compare.matchesRangePart
import kotlin.math.min

internal fun <DM : IsRootDataModel> scanIndex(
    transactionRunner: TransactionRunner,
    tableDirs: IsTableDirectories,
    scanRequest: IsScanRequest<DM, *>,
    indexScan: ScanType.IndexScan,
    keyScanRange: KeyScanRanges,
    decryptValue: ((ByteArray) -> ByteArray)? = null,
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

    // Compute response metadata start/stop
    // Build a helper valuesGetter for computing index start from the startKey (respecting toVersion)
    val startIndexKey: ByteArray? = scanRequest.startKey?.let { startKey -> transactionRunner.run { tr ->
        val getter = object : IsValuesGetter {
            private val cache = mutableMapOf<IsPropertyReference<*, *, *>, Any?>()

            override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(
                propertyReference: IsPropertyReference<T, D, C>
            ): T? {
                if (cache.containsKey(propertyReference)) {
                    @Suppress("UNCHECKED_CAST")
                    return cache[propertyReference] as T?
                }

                val value = if (propertyReference is IsMapReference<*, *, *, *> && scanRequest.toVersion == null) {
                    @Suppress("UNCHECKED_CAST")
                    tr.readMapByReference(
                        tableDirs.tablePrefix,
                        startKey.bytes,
                        propertyReference as IsMapReference<Any, Any, IsPropertyContext, *>,
                        decryptValue
                    ) as T?
                } else if (propertyReference is SetReference<*, *> && scanRequest.toVersion == null) {
                    @Suppress("UNCHECKED_CAST")
                    tr.readSetByReference(
                        tableDirs.tablePrefix,
                        startKey.bytes,
                        propertyReference as SetReference<Any, IsPropertyContext>
                    ) as T?
                } else {
                    val keyAndRef = combineToByteArray(startKey.bytes, propertyReference.toStorageByteArray())
                    tr.getValue(
                        tableDirs,
                        scanRequest.toVersion,
                        keyAndRef,
                        startKey.size,
                        decryptValue = decryptValue
                    ) { valueBytes, offset, length ->
                        valueBytes.convertToValue(propertyReference, offset, length) as T?
                    }
                }

                cache[propertyReference] = value
                return value
            }
        }
        indexScan.index.toStorageByteArrayForIndex(getter, startKey.bytes)
    } }

    val responseStartKey = when (indexScan.direction) {
        ASC -> indexScanRange.ranges.first().getAscendingStartKey(startIndexKey, scanRequest.includeStart)
        DESC -> indexScanRange.ranges.first().getDescendingStartKey(startIndexKey, scanRequest.includeStart)
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

        when (indexScan.direction) {
            ASC -> {
                var startKeyFilter = startIndexKey
                var includeStartFilter = scanRequest.includeStart
                for (i in ranges.indices) {
                    if (emitted >= scanRequest.limit) break
                    val range = ranges[i]
                    val effectiveStartKey = startKeyFilter.takeIf { i == 0 }

                    val startSeg = range.getAscendingStartKey(effectiveStartKey, if (i == 0) includeStartFilter else true)
                    val begin = combineToByteArray(base, startSeg)
                    val end = when (val endExclusiveSeg = range.getDescendingStartKey()) {
                        null -> baseEnd
                        else -> if (endExclusiveSeg.isEmpty()) baseEnd else combineToByteArray(base, endExclusiveSeg)
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
                                val valueSize = totalLen - valueOffset - keySize - versionSize
                                if (valueSize < 0) return@forEachInRangeBatch true
                                val sortingKey = indexKeyBytes.copyOfRange(valueOffset, totalLen - versionSize)
                                if (!indexScanRange.matchesPartials(indexKeyBytes, valueOffset, valueSize)) return@forEachInRangeBatch true
                                val keyOffset = valueOffset + valueSize
                                val keyBytes = indexKeyBytes.copyOfRange(keyOffset, keyOffset + keySize)
                                if (!hasAdditionalMatches(transactionRunner, tr, tableDirs, indexReference, keyBytes, indexScanRange.valueMatches, null)) return@forEachInRangeBatch true
                                val createdPacked = tr.get(packKey(tableDirs.keysPrefix, keyBytes)).awaitResult() ?: return@forEachInRangeBatch true
                                val createdVersion = createdPacked.readHLCTimestampIfPresent() ?: return@forEachInRangeBatch true
                                if (scanRequest.shouldBeFiltered(tr, tableDirs, keyBytes, 0, keySize, createdVersion, scanRequest.toVersion, decryptValue, indexScan.index)) {
                                    return@forEachInRangeBatch true
                                }

                                if (effectiveStartKey != null) {
                                    val cmp = sortingKey.compareDefinedRange(effectiveStartKey)
                                    if (cmp < 0) return@forEachInRangeBatch true
                                    if (!includeStartFilter && cmp == 0) return@forEachInRangeBatch true
                                }

                                val key = scanRequest.dataModel.key(keyBytes)
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
                    val begin = combineToByteArray(base, startSeg)
                    val end = if (!startUpperBoundApplied && startIndexKey != null &&
                        !range.keyOutOfRange(startIndexKey, 0, startIndexKey.size)
                    ) {
                        startUpperBoundApplied = true
                        packDescendingExclusiveEnd(scanRequest.includeStart, base, startIndexKey)
                    } else {
                        when (val endExclusiveSeg = range.getDescendingStartKey()) {
                            null -> baseEnd
                            else -> if (endExclusiveSeg.isEmpty()) baseEnd else combineToByteArray(base, endExclusiveSeg)
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
                                val valueSize = totalLen - valueOffset - keySize - versionSize
                                if (valueSize < 0) return@forEachInRangeBatch true
                                val sortingKey = indexKeyBytes.copyOfRange(valueOffset, totalLen - versionSize)
                                if (startIndexKey != null) {
                                    val cmp = sortingKey.compareDefinedRange(startIndexKey)
                                    if (cmp > 0) return@forEachInRangeBatch true
                                    if (!scanRequest.includeStart && cmp == 0) return@forEachInRangeBatch true
                                }
                                if (!indexScanRange.matchesPartials(indexKeyBytes, valueOffset, valueSize)) return@forEachInRangeBatch true
                                val keyOffset = valueOffset + valueSize
                                val keyBytes = indexKeyBytes.copyOfRange(keyOffset, keyOffset + keySize)
                                if (!hasAdditionalMatches(transactionRunner, tr, tableDirs, indexReference, keyBytes, indexScanRange.valueMatches, null)) return@forEachInRangeBatch true
                                val createdPacked = tr.get(packKey(tableDirs.keysPrefix, keyBytes)).awaitResult() ?: return@forEachInRangeBatch true
                                val createdVersion = createdPacked.readHLCTimestampIfPresent() ?: return@forEachInRangeBatch true
                                if (scanRequest.shouldBeFiltered(tr, tableDirs, keyBytes, 0, keySize, createdVersion, scanRequest.toVersion, decryptValue, indexScan.index)) {
                                    return@forEachInRangeBatch true
                                }

                                val key = scanRequest.dataModel.key(keyBytes)
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
        data class Rev(val version: ULong, val rec: Rec)
        val latestByKey = mutableMapOf<ByteArrayKey, Rev>()
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
                ASC -> range.getAscendingStartKey(startIndexKey.takeIf { i == 0 }, if (i == 0) scanRequest.includeStart else true)
                DESC -> range.getAscendingStartKey(null, true)
            }

            val beginQualifier = encodeZeroFreeUsing01(combineToByteArray(indexReference, startSeg))
            val begin = packKey(histBase, beginQualifier, byteArrayOf(0), versionFloor)
            val end = if (indexScan.direction == DESC && startIndexKey != null &&
                !descendingUpperBoundApplied &&
                !range.keyOutOfRange(startIndexKey, 0, startIndexKey.size)
            ) {
                descendingUpperBoundApplied = true
                val qualifier = encodeZeroFreeUsing01(combineToByteArray(indexReference, startIndexKey))
                packDescendingExclusiveEnd(scanRequest.includeStart, histBase, qualifier, byteArrayOf(0), versionFloor)
            } else {
                when (val endExclusiveSeg = range.getDescendingStartKey()) {
                    null -> baseEnd
                    else -> {
                        if (endExclusiveSeg.isEmpty()) {
                            baseEnd
                        } else {
                            val endQualifier = encodeZeroFreeUsing01(combineToByteArray(indexReference, endExclusiveSeg))
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

            var lastQualifierEncoded: ByteArray? = null
            var nextBegin = begin
            while (true) {
                val result = transactionRunner.run { tr ->
                    tr.forEachInRangeBatch(Range(nextBegin, end), false) { kv ->
                        val k = kv.key
                        val versionOffset = k.size - toVersionBytes.size
                        val sepIndex = versionOffset - 1
                        if (sepIndex < histBase.size || k[sepIndex] != 0.toByte()) return@forEachInRangeBatch true
                        if (toVersionBytes.compareToRange(k, versionOffset) > 0) return@forEachInRangeBatch true

                        val encQualifier = k.copyOfRange(histBase.size, sepIndex)
                        val prevEnc = lastQualifierEncoded
                        if (prevEnc != null && prevEnc.contentEquals(encQualifier)) return@forEachInRangeBatch true
                        lastQualifierEncoded = encQualifier
                        val qualifier = decodeZeroFreeUsing01OrNull(encQualifier) ?: return@forEachInRangeBatch true
                        if (qualifier.size <= indexReference.size) return@forEachInRangeBatch true
                        val valueAndKey = qualifier.copyOfRange(indexReference.size, qualifier.size)
                        val valueSize = valueAndKey.size - keySize
                        if (valueSize < 0) return@forEachInRangeBatch true

                        if (!indexScanRange.matchesPartials(valueAndKey, 0, valueSize)) return@forEachInRangeBatch true

                        val keyBytes = valueAndKey.copyOfRange(valueAndKey.size - keySize, valueAndKey.size)
                        if (!hasHistoricAdditionalMatches(transactionRunner, tr, tableDirs, indexReference, keyBytes, indexScanRange.valueMatches, scanRequest.toVersion!!)) return@forEachInRangeBatch true
                        if (scanRequest.shouldBeFiltered(tr, tableDirs, keyBytes, 0, keySize, null, scanRequest.toVersion, decryptValue, indexScan.index)) {
                            return@forEachInRangeBatch true
                        }

                        val createdPacked = tr.get(packKey(tableDirs.keysPrefix, keyBytes)).awaitResult() ?: return@forEachInRangeBatch true
                        val createdVersion = createdPacked.readHLCTimestampIfPresent() ?: return@forEachInRangeBatch true
                        val version = k.readReversedVersionBytes(versionOffset)
                        val rec = Rec(valueAndKey, keyBytes, createdVersion)
                        val keyRef = keyBytes.asByteArrayKey()
                        val prev = latestByKey[keyRef]
                        if (prev == null || version > prev.version) {
                            latestByKey[keyRef] = Rev(version, rec)
                        }
                        true
                    }
                }

                if (result.completed) break
                nextBegin = result.lastKey?.let { it + byteArrayOf(0) } ?: break
            }
        }

        val results = latestByKey.values.map { it.rec }.toMutableList()
        results.sortWith { a, b -> a.sort compareTo b.sort }

        var emitted = 0u
        when (indexScan.direction) {
            ASC -> {
                var idx = 0
                startIndexKey?.let { si ->
                    while (idx < results.size && results[idx].sort.compareDefinedRange(si) < 0) idx++
                    if (!scanRequest.includeStart && idx < results.size && results[idx].sort.contentEquals(si)) idx++
                }
                while (idx < results.size && emitted < scanRequest.limit) {
                    val rec = results[idx++]
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
                    while (idx >= 0 && results[idx].sort.compareDefinedRange(si) > 0) idx--
                    if (!scanRequest.includeStart && idx >= 0 && results[idx].sort.contentEquals(si)) idx--
                }
                while (idx >= 0 && emitted < scanRequest.limit) {
                    val rec = results[idx--]
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
    val qualifier = encodeZeroFreeUsing01(combineToByteArray(indexReference, createIndexValue(value, recordKey)))
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
                    matchedValue = kv.value.isEmpty()
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
    return combineToByteArray(
        value,
        ByteArray(valueLength.calculateVarByteLength()).also { lengthBytes ->
            var index = 0
            valueLength.writeVarBytes { lengthBytes[index++] = it }
        },
        key
    )
}

private fun hasHistoricMatchingPrefixValue(
    transactionRunner: TransactionRunner,
    tableDirs: HistoricTableDirectories,
    indexReference: ByteArray,
    recordKey: ByteArray,
    prefix: ByteArray,
    toVersion: ULong
): Boolean {
    val qualifierPrefix = encodeZeroFreeUsing01(combineToByteArray(indexReference, prefix))
    val scanPrefix = packKey(tableDirs.historicIndexPrefix, qualifierPrefix)
    val toVersionBytes = toVersion.toReversedVersionBytes()
    var settledQualifier: ByteArray? = null
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

                val encodedQualifier = key.copyOfRange(tableDirs.historicIndexPrefix.size, sepIndex)
                if (!encodedQualifier.matchesRangePart(0, qualifierPrefix, length = qualifierPrefix.size)) {
                    return@forEachInRangeBatch false
                }
                if (settledQualifier?.contentEquals(encodedQualifier) == true) return@forEachInRangeBatch true

                if (toVersionBytes.compareToRange(key, versionOffset) > 0) {
                    return@forEachInRangeBatch true
                }

                val qualifier = decodeZeroFreeUsing01OrNull(encodedQualifier) ?: return@forEachInRangeBatch true
                if (qualifier.size <= indexReference.size + recordKey.size) {
                    settledQualifier = encodedQualifier
                    return@forEachInRangeBatch true
                }

                val valueAndKey = qualifier.copyOfRange(indexReference.size, qualifier.size)
                val keyOffset = valueAndKey.size - recordKey.size
                if (keyOffset < 0 || !valueAndKey.matchesRangePart(keyOffset, recordKey, length = recordKey.size)) {
                    settledQualifier = encodedQualifier
                    return@forEachInRangeBatch true
                }

                if (kv.value.isEmpty()) {
                    matchedValue = true
                    return@forEachInRangeBatch false
                }

                settledQualifier = encodedQualifier
                true
            }
        }

        matchedValue?.let { return it }
        if (result.stoppedByCallback) return false
        if (result.completed) return false
        nextBegin = result.lastKey?.let { it + byteArrayOf(0) } ?: return false
    }
}
