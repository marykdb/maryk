package maryk.datastore.foundationdb.processors

import com.apple.foundationdb.Range
import com.apple.foundationdb.ReadTransaction
import com.apple.foundationdb.Transaction
import maryk.core.clock.HLC
import maryk.core.exceptions.StorageException
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
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
import maryk.datastore.foundationdb.processors.helpers.decodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.getValue
import maryk.datastore.foundationdb.processors.helpers.packDescendingExclusiveEnd
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.readReversedVersionBytes
import maryk.datastore.foundationdb.processors.helpers.toReversedVersionBytes
import maryk.datastore.foundationdb.processors.helpers.asByteArrayKey
import maryk.datastore.shared.ScanType
import maryk.datastore.shared.helpers.convertToValue
import maryk.lib.bytes.combineToByteArray
import maryk.lib.extensions.compare.compareDefinedTo
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.compareToWithOffsetLength
import kotlin.math.min

internal fun <DM : IsRootDataModel> scanIndex(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    scanRequest: IsScanRequest<DM, *>,
    indexScan: ScanType.IndexScan,
    keyScanRange: KeyScanRanges,
    processStoreValue: (Key<DM>, ULong, ByteArray?) -> Unit
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
    val startIndexKey: ByteArray? = scanRequest.startKey?.let { startKey ->
        val getter = object : IsValuesGetter {
            override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(
                propertyReference: IsPropertyReference<T, D, C>
            ): T? {
                val keyAndRef = combineToByteArray(startKey.bytes, propertyReference.toStorageByteArray())
                return tr.getValue(tableDirs, scanRequest.toVersion, keyAndRef, startKey.size) { valueBytes, offset, length ->
                    valueBytes.convertToValue(propertyReference, offset, length) as T?
                }
            }
        }
        indexScan.index.toStorageByteArrayForIndex(getter, startKey.bytes)
    }

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

                    val startSeg = range.getAscendingStartKey(startKeyFilter.takeIf { i == 0 }, if (i == 0) includeStartFilter else true)
                    val begin = combineToByteArray(base, startSeg)
                    val end = when (val endExclusiveSeg = range.getDescendingStartKey()) {
                        null -> baseEnd
                        else -> if (endExclusiveSeg.isEmpty()) baseEnd else combineToByteArray(base, endExclusiveSeg)
                    }

                    val cmpLength = min(begin.size, end.size)
                    val beginGteEnd = when (val cmp = begin.compareDefinedTo(end, 0, cmpLength)) {
                        0 -> begin.size >= end.size
                        else -> cmp > 0
                    }
                    if (beginGteEnd) continue

                    val iterator = tr.getRange(Range(begin, end), ReadTransaction.ROW_LIMIT_UNLIMITED, false).iterator()
                    while (iterator.hasNext() && emitted < scanRequest.limit) {
                        val kv = iterator.next()
                        val indexKeyBytes = kv.key
                        val totalLen = indexKeyBytes.size
                        val valueSize = totalLen - valueOffset - keySize - versionSize
                        if (valueSize < 0) continue
                        val sortingKey = indexKeyBytes.copyOfRange(valueOffset, totalLen - versionSize)
                        if (!indexScanRange.matchesPartials(indexKeyBytes, valueOffset, valueSize)) continue
                        val keyOffset = valueOffset + valueSize
                        val keyBytes = indexKeyBytes.copyOfRange(keyOffset, keyOffset + keySize)
                        val createdPacked = tr.get(packKey(tableDirs.keysPrefix, keyBytes)).awaitResult() ?: continue
                        val createdVersion = HLC.fromStorageBytes(createdPacked).timestamp
                        if (scanRequest.shouldBeFiltered(tr, tableDirs, keyBytes, 0, keySize, createdVersion, scanRequest.toVersion)) continue

                        if (startKeyFilter != null) {
                            val cmp = sortingKey.compareDefinedTo(startKeyFilter)
                            if (cmp < 0) continue
                            if (!includeStartFilter && cmp == 0) continue
                        }

                        val key = scanRequest.dataModel.key(keyBytes)
                        processStoreValue(key, createdVersion, sortingKey)
                        emitted++
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
                    val beginGteEnd = when (val cmp = begin.compareDefinedTo(end, 0, cmpLength)) {
                        0 -> begin.size >= end.size
                        else -> cmp > 0
                    }
                    if (beginGteEnd) continue

                    val iterator = tr.getRange(Range(begin, end), ReadTransaction.ROW_LIMIT_UNLIMITED, true).iterator()
                    while (iterator.hasNext() && emitted < scanRequest.limit) {
                        val kv = iterator.next()
                        val indexKeyBytes = kv.key
                        val totalLen = indexKeyBytes.size
                        val valueSize = totalLen - valueOffset - keySize - versionSize
                        if (valueSize < 0) continue
                        val sortingKey = indexKeyBytes.copyOfRange(valueOffset, totalLen - versionSize)
                        if (startIndexKey != null) {
                            val cmp = sortingKey.compareDefinedTo(startIndexKey)
                            if (cmp > 0) continue
                            if (!scanRequest.includeStart && cmp == 0) continue
                        }
                        if (!indexScanRange.matchesPartials(indexKeyBytes, valueOffset, valueSize)) continue
                        val keyOffset = valueOffset + valueSize
                        val keyBytes = indexKeyBytes.copyOfRange(keyOffset, keyOffset + keySize)
                        val createdPacked = tr.get(packKey(tableDirs.keysPrefix, keyBytes)).awaitResult() ?: continue
                        val createdVersion = HLC.fromStorageBytes(createdPacked).timestamp
                        if (scanRequest.shouldBeFiltered(tr, tableDirs, keyBytes, 0, keySize, createdVersion, scanRequest.toVersion)) continue

                        val key = scanRequest.dataModel.key(keyBytes)
                        processStoreValue(key, createdVersion, sortingKey)
                        emitted++
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
            val beginGteEnd = when (val cmp = begin.compareDefinedTo(end, 0, cmpLength)) {
                0 -> begin.size >= end.size
                else -> cmp > 0
            }
            if (beginGteEnd) continue

            val it = tr.getRange(Range(begin, end)).iterator()
            var lastQualifierEncoded: ByteArray? = null
            while (it.hasNext()) {
                val kv = it.next()
                val k = kv.key
                val versionOffset = k.size - toVersionBytes.size
                val sepIndex = versionOffset - 1
                if (sepIndex < histBase.size || k[sepIndex] != 0.toByte()) continue
                if (toVersionBytes.compareToWithOffsetLength(k, versionOffset) > 0) continue

                val encQualifier = k.copyOfRange(histBase.size, sepIndex)
                val prevEnc = lastQualifierEncoded
                if (prevEnc != null && prevEnc.contentEquals(encQualifier)) continue
                lastQualifierEncoded = encQualifier
                val qualifier = decodeZeroFreeUsing01(encQualifier)
                if (qualifier.size <= indexReference.size) continue
                val valueAndKey = qualifier.copyOfRange(indexReference.size, qualifier.size)

                if (!indexScanRange.matchesPartials(valueAndKey, 0, valueAndKey.size - keySize)) continue

                val keyBytes = valueAndKey.copyOfRange(valueAndKey.size - keySize, valueAndKey.size)
                if (scanRequest.shouldBeFiltered(tr, tableDirs, keyBytes, 0, keySize, null, scanRequest.toVersion)) continue

                val createdPacked = tr.get(packKey(tableDirs.keysPrefix, keyBytes)).awaitResult() ?: continue
                val createdVersion = HLC.fromStorageBytes(createdPacked).timestamp
                val version = k.readReversedVersionBytes(versionOffset)
                val rec = Rec(valueAndKey, keyBytes, createdVersion)
                val keyRef = keyBytes.asByteArrayKey()
                val prev = latestByKey[keyRef]
                if (prev == null || version > prev.version) {
                    latestByKey[keyRef] = Rev(version, rec)
                }
            }
        }

        val results = latestByKey.values.map { it.rec }.toMutableList()
        results.sortWith { a, b -> a.sort compareTo b.sort }

        var emitted = 0u
        when (indexScan.direction) {
            ASC -> {
                var idx = 0
                startIndexKey?.let { si ->
                    while (idx < results.size && results[idx].sort.compareDefinedTo(si) < 0) idx++
                    if (!scanRequest.includeStart && idx < results.size && results[idx].sort.contentEquals(si)) idx++
                }
                while (idx < results.size && emitted < scanRequest.limit) {
                    val rec = results[idx++]
                    val key = scanRequest.dataModel.key(rec.keyBytes)
                    processStoreValue(key, rec.created, rec.sort)
                    emitted++
                }
            }
            DESC -> {
                var idx = results.lastIndex
                startIndexKey?.let { si ->
                    while (idx >= 0 && results[idx].sort.compareDefinedTo(si) > 0) idx--
                    if (!scanRequest.includeStart && idx >= 0 && results[idx].sort.contentEquals(si)) idx--
                }
                while (idx >= 0 && emitted < scanRequest.limit) {
                    val rec = results[idx--]
                    val key = scanRequest.dataModel.key(rec.keyBytes)
                    processStoreValue(key, rec.created, rec.sort)
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
