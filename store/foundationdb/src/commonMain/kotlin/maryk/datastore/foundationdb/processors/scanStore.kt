package maryk.datastore.foundationdb.processors

import maryk.foundationdb.KeyValue
import maryk.foundationdb.Range
import maryk.foundationdb.ReadTransaction
import maryk.foundationdb.Transaction
import maryk.core.clock.HLC
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
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.helpers.packDescendingExclusiveEnd
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.nextBlocking
import maryk.lib.extensions.compare.compareDefinedRange
import kotlin.math.min

internal fun <DM : IsRootDataModel> scanStore(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    scanRequest: IsScanRequest<DM, *>,
    direction: Direction,
    scanRange: KeyScanRanges,
    decryptValue: ((ByteArray) -> ByteArray)? = null,
    processStoreValue: (Key<DM>, ULong, ByteArray?) -> Unit
): DataFetchType {
    val prefix = tableDirs.keysPrefix
    val prefixSize = prefix.size
    val prefixEnd = Range.startsWith(prefix).end

    var responseStartKey: ByteArray?
    var responseStopKey: ByteArray?

    if (direction == ASC) {
        responseStartKey = scanRange.ranges.first().getAscendingStartKey(scanRange.startKey, scanRange.includeStart)
        responseStopKey = scanRange.ranges.last().getDescendingStartKey()
    } else {
        responseStartKey = scanRange.ranges.first().getDescendingStartKey(scanRange.startKey, scanRange.includeStart)
        responseStopKey = scanRange.ranges.last().getAscendingStartKey()
    }

    val limit = scanRequest.limit

    when (direction) {
        ASC -> {
            var streamed = 0u
            var startKeyFilter = scanRange.startKey
            var includeStartFilter = scanRange.includeStart

            for (range in scanRange.ranges) {
                if (streamed >= limit) break

                val effectiveStart = startKeyFilter
                val effectiveInclude = includeStartFilter
                if (effectiveStart != null && range.keyOutOfRange(effectiveStart)) {
                    continue
                }

                val beginKey = range.getAscendingStartKey(effectiveStart, effectiveInclude)
                val begin = if (beginKey.isEmpty()) {
                    prefix
                } else {
                    packKey(prefix, beginKey)
                }

                val endExclusiveKey = range.getDescendingStartKey()
                val end = when {
                    endExclusiveKey == null -> prefixEnd
                    endExclusiveKey.isEmpty() -> prefix
                    else -> packKey(prefix, endExclusiveKey)
                }

                val cmpLength = min(begin.size, end.size)
                val beginGteEnd = when (val cmp = begin.compareDefinedRange(end, 0, cmpLength)) {
                    0 -> begin.size >= end.size
                    else -> cmp > 0
                }
                if (beginGteEnd) continue

                val iterator = tr.getRange(Range(begin, end), ReadTransaction.ROW_LIMIT_UNLIMITED, false).iterator()
                while (iterator.hasNext() && streamed < limit) {
                    val kv: KeyValue = iterator.nextBlocking()
                    val modelKeyBytes = kv.key.copyOfRange(prefixSize, kv.key.size)

                    if (!scanRange.keyWithinRanges(modelKeyBytes, 0)) continue
                    if (!scanRange.matchesPartials(modelKeyBytes)) continue

                    if (effectiveStart != null) {
                        val cmp = modelKeyBytes.compareDefinedRange(effectiveStart, 0, scanRange.keySize)
                        if (cmp < 0) continue
                        if (!effectiveInclude && cmp == 0) continue
                    }

                    val key = scanRequest.dataModel.key(modelKeyBytes)
                    val creationVersion = HLC.fromStorageBytes(kv.value).timestamp
                    if (scanRequest.shouldBeFiltered(tr, tableDirs, key.bytes, 0, key.size, creationVersion, scanRequest.toVersion, decryptValue)) continue

                    processStoreValue(key, creationVersion, null)
                    streamed++
                }

                if (startKeyFilter != null) {
                    startKeyFilter = null
                    includeStartFilter = true
                }
            }
        }
        DESC -> {
            var emitted = 0u
            val startKeyFilter = scanRange.startKey
            val includeStartFilter = scanRange.includeStart
            var startUpperBoundApplied = false

            for (rangeIndex in scanRange.ranges.indices.reversed()) {
                if (emitted >= limit) break
                val range = scanRange.ranges[rangeIndex]

                if (!startUpperBoundApplied && startKeyFilter != null &&
                    range.keyBeforeStart(startKeyFilter, 0, scanRange.keySize)
                ) {
                    continue
                }

                val beginKey = range.getAscendingStartKey(null, true)
                val begin = if (beginKey.isEmpty()) {
                    prefix
                } else {
                    packKey(prefix, beginKey)
                }

                val end = if (!startUpperBoundApplied && startKeyFilter != null &&
                    !range.keyOutOfRange(startKeyFilter, 0, scanRange.keySize)
                ) {
                    startUpperBoundApplied = true
                    packDescendingExclusiveEnd(includeStartFilter, prefix, startKeyFilter)
                } else {
                    when (val endExclusiveKey = range.getDescendingStartKey()) {
                        null -> prefixEnd
                        else -> if (endExclusiveKey.isEmpty()) prefix else packKey(prefix, endExclusiveKey)
                    }
                }

                val cmpLength = min(begin.size, end.size)
                val beginGteEnd = when (val cmp = begin.compareDefinedRange(end, 0, cmpLength)) {
                    0 -> begin.size >= end.size
                    else -> cmp > 0
                }
                if (beginGteEnd) continue

                val iterator = tr.getRange(Range(begin, end), ReadTransaction.ROW_LIMIT_UNLIMITED, true).iterator()
                while (iterator.hasNext() && emitted < limit) {
                    val kv: KeyValue = iterator.nextBlocking()
                    val modelKeyBytes = kv.key.copyOfRange(prefixSize, kv.key.size)

                    if (startKeyFilter != null) {
                        val cmp = modelKeyBytes.compareDefinedRange(startKeyFilter, 0, scanRange.keySize)
                        if (cmp > 0) continue
                        if (!includeStartFilter && cmp == 0) continue
                    }

                    if (!scanRange.keyWithinRanges(modelKeyBytes, 0)) continue
                    if (!scanRange.matchesPartials(modelKeyBytes)) continue

                    val key = scanRequest.dataModel.key(modelKeyBytes)
                    val creationVersion = HLC.fromStorageBytes(kv.value).timestamp
                    if (scanRequest.shouldBeFiltered(tr, tableDirs, key.bytes, 0, key.size, creationVersion, scanRequest.toVersion, decryptValue)) continue

                    processStoreValue(key, creationVersion, null)
                    emitted++
                }
            }
        }
    }

    return FetchByTableScan(
        direction = direction,
        startKey = responseStartKey,
        stopKey = responseStopKey,
    )
}
