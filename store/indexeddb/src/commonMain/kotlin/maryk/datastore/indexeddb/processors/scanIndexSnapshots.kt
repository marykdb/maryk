package maryk.datastore.indexeddb.processors

import maryk.core.processors.datastore.scanRange.ScanRange
import maryk.core.query.orders.Direction
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.datastore.indexeddb.IndexedDbDataStore
import maryk.datastore.indexeddb.IndexedDbBatchReadStore
import maryk.datastore.indexeddb.getBatch
import maryk.lib.extensions.compare.matchesRangePart

/** Bound live current snapshot blobs while consuming rows in native index order. */
internal suspend fun IndexedDbDataStore.scanIndexSnapshots(
    indexStoreName: String,
    keyStoreName: String,
    indexPrefix: ByteArray,
    keySize: Int,
    startKey: ByteArray?,
    endKey: ByteArray?,
    includeEnd: Boolean,
    reverse: Boolean,
    remaining: () -> UInt,
    prepareSnapshot: (ByteArray) -> PreparedCurrentSnapshot,
    processRow: suspend (ByteArray, PreparedCurrentSnapshot?, Boolean) -> Boolean,
) {
    var pageStart = startKey
    var pageEnd = endKey
    var includePageStart = true
    var includePageEnd = includeEnd
    while (true) {
        val page = byteStore.scan(indexStoreName, pageStart, includePageStart, pageEnd, includePageEnd,
            reverse = reverse, limit = 256u)
        if (page.isEmpty()) return
        var offset = 0
        while (offset < page.size) {
            val count = minOf(8u, remaining(), (page.size - offset).toUInt()).toInt()
            if (count == 0) return
            val window = page.subList(offset, offset + count)
            val keys = window.map { (rowKey, _) ->
                if (rowKey.size >= indexPrefix.size + keySize && rowKey.matchesRangePart(0, indexPrefix))
                    rowKey.copyOfRange(rowKey.size - keySize, rowKey.size) else null
            }
            val batchStore = byteStore
            val heads = if (batchStore is IndexedDbBatchReadStore) {
                batchStore.mapBatchValues(keyStoreName, keys.filterNotNull()) { _, bytes -> bytes?.let(prepareSnapshot) }
            } else batchStore.getBatch(keyStoreName, keys.filterNotNull()).map { it?.let(prepareSnapshot) }
            var headIndex = 0
            try {
                for (index in window.indices) {
                    val prefetched = keys[index] != null
                    val head = if (prefetched) heads[headIndex++] else null
                    val keepReading = try { processRow(window[index].first, head, prefetched) }
                        finally { head?.release() }
                    if (!keepReading) return
                }
            } finally {
                heads.forEach { it?.release() }
            }
            offset += count
        }
        if (page.size < 256) return
        if (reverse) {
            pageEnd = page.last().first
            includePageEnd = false
        } else {
            pageStart = page.last().first
            includePageStart = false
        }
    }
}

internal fun createIndexScanStart(
    indexPrefix: ByteArray,
    range: ScanRange,
    startIndexValue: ByteArray?,
    includeStart: Boolean,
    direction: Direction,
): ByteArray = when (direction) {
    ASC -> createIndexKeyWithPrefix(indexPrefix,
        startIndexValue?.let { range.getAscendingStartKey(it, includeStart) } ?: range.start)
    DESC -> createIndexKeyWithPrefix(indexPrefix, range.getAscendingStartKey())
}

internal fun createIndexScanEnd(
    indexReference: ByteArray,
    indexPrefix: ByteArray,
    range: ScanRange,
    startIndexValue: ByteArray?,
    direction: Direction,
): ByteArray {
    // Native cursor inclusivity applies to the actual row boundary, not its predecessor.
    val end = when (direction) {
        ASC -> range.getDescendingStartKey()
        DESC -> range.getDescendingStartKey(startIndexValue, inclusiveStartKey = true)
    }
    return if (end == null || end.isEmpty()) createIndexRangeEnd(indexReference)
        else createIndexKeyWithPrefix(indexPrefix, end)
}
