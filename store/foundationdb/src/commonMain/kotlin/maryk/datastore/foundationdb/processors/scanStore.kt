package maryk.datastore.foundationdb.processors

import maryk.foundationdb.Range
import maryk.foundationdb.Transaction
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
import maryk.datastore.foundationdb.processors.helpers.forEachInRangeBatch
import maryk.datastore.foundationdb.processors.helpers.packDescendingExclusiveEnd
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.readHLCTimestampIfPresent
import maryk.datastore.foundationdb.processors.helpers.TransactionRunner
import maryk.lib.extensions.compare.compareDefinedRange
import kotlin.math.min

internal fun <DM : IsRootDataModel> scanStore(
    transactionRunner: TransactionRunner,
    tableDirs: IsTableDirectories,
    scanRequest: IsScanRequest<DM, *>,
    direction: Direction,
    scanRange: KeyScanRanges,
    decryptValue: ((ByteArray) -> ByteArray)? = null,
    processStoreValue: (Transaction, Key<DM>, ULong, ByteArray?) -> Unit
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

                var nextBegin = begin
                while (streamed < limit) {
                    val result = transactionRunner.run { tr ->
                        tr.forEachInRangeBatch(Range(nextBegin, end), false) { kv ->
                            if (streamed >= limit) return@forEachInRangeBatch false
                            val modelKeyBytes = kv.key.copyOfRange(prefixSize, kv.key.size)

                            if (!scanRange.keyWithinRanges(modelKeyBytes, 0)) return@forEachInRangeBatch true
                            if (!scanRange.matchesPartials(modelKeyBytes)) return@forEachInRangeBatch true

                            if (effectiveStart != null) {
                                val cmp = modelKeyBytes.compareDefinedRange(effectiveStart, 0, scanRange.keySize)
                                if (cmp < 0) return@forEachInRangeBatch true
                                if (!effectiveInclude && cmp == 0) return@forEachInRangeBatch true
                            }

                            val key = scanRequest.dataModel.key(modelKeyBytes)
                            val creationVersion = kv.value.readHLCTimestampIfPresent() ?: return@forEachInRangeBatch true
                            if (scanRequest.shouldBeFiltered(tr, tableDirs, key.bytes, 0, key.size, creationVersion, scanRequest.toVersion, decryptValue)) {
                                return@forEachInRangeBatch true
                            }

                            processStoreValue(tr, key, creationVersion, null)
                            streamed++
                            streamed < limit
                        }
                    }

                    if (result.completed || streamed >= limit) break
                    nextBegin = result.lastKey?.let { it + byteArrayOf(0) } ?: break
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

                var nextEnd = end
                while (emitted < limit) {
                    val result = transactionRunner.run { tr ->
                        tr.forEachInRangeBatch(Range(begin, nextEnd), true) { kv ->
                            if (emitted >= limit) return@forEachInRangeBatch false
                            val modelKeyBytes = kv.key.copyOfRange(prefixSize, kv.key.size)

                            if (startKeyFilter != null) {
                                val cmp = modelKeyBytes.compareDefinedRange(startKeyFilter, 0, scanRange.keySize)
                                if (cmp > 0) return@forEachInRangeBatch true
                                if (!includeStartFilter && cmp == 0) return@forEachInRangeBatch true
                            }

                            if (!scanRange.keyWithinRanges(modelKeyBytes, 0)) return@forEachInRangeBatch true
                            if (!scanRange.matchesPartials(modelKeyBytes)) return@forEachInRangeBatch true

                            val key = scanRequest.dataModel.key(modelKeyBytes)
                            val creationVersion = kv.value.readHLCTimestampIfPresent() ?: return@forEachInRangeBatch true
                            if (scanRequest.shouldBeFiltered(tr, tableDirs, key.bytes, 0, key.size, creationVersion, scanRequest.toVersion, decryptValue)) {
                                return@forEachInRangeBatch true
                            }

                            processStoreValue(tr, key, creationVersion, null)
                            emitted++
                            emitted < limit
                        }
                    }

                    if (result.completed || emitted >= limit) break
                    nextEnd = result.lastKey ?: break
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
