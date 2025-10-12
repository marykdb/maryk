package maryk.datastore.foundationdb.processors

import com.apple.foundationdb.KeyValue
import com.apple.foundationdb.Range
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
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.lib.extensions.compare.compareDefinedTo

internal fun <DM : IsRootDataModel> FoundationDBDataStore.scanStore(
    tableDirs: IsTableDirectories,
    scanRequest: IsScanRequest<DM, *>,
    direction: Direction,
    scanRange: KeyScanRanges,
    processStoreValue: (Key<DM>, ULong, ByteArray?) -> Unit
): DataFetchType {
    val prefix = tableDirs.keysPrefix

    var responseStartKey: ByteArray?
    var responseStopKey: ByteArray?

    // For DESC we keep a bounded deque to avoid retaining all matches
    val buffer = if (direction == ASC) null else ArrayDeque<Pair<ByteArray, ULong>>()
    val limit = scanRequest.limit

    if (direction == ASC) {
        responseStartKey = scanRange.ranges.first().getAscendingStartKey(scanRange.startKey, scanRange.includeStart)
        responseStopKey = scanRange.ranges.last().getDescendingStartKey()
    } else {
        responseStartKey = scanRange.ranges.first().getDescendingStartKey(scanRange.startKey, scanRange.includeStart)
        responseStopKey = scanRange.ranges.last().getAscendingStartKey()
    }

    runTransaction { tr ->
        val it = tr.getRange(Range.startsWith(prefix)).iterator()
        var streamed = 0u
        val start = scanRange.startKey
        while (it.hasNext()) {
            val kv: KeyValue = it.next()
            val modelKeyBytes = kv.key.copyOfRange(prefix.size, kv.key.size)

            if (!scanRange.keyWithinRanges(modelKeyBytes, 0)) continue
            if (!scanRange.matchesPartials(modelKeyBytes)) continue

            val key = scanRequest.dataModel.key(modelKeyBytes)
            val creationVersion = HLC.fromStorageBytes(kv.value).timestamp
            if (scanRequest.shouldBeFiltered(tr, tableDirs, key.bytes, 0, key.size, creationVersion, scanRequest.toVersion)) continue

            if (direction == ASC) {
                // Apply startKey slicing for ASC
                if (start != null) {
                    val cmp = modelKeyBytes.compareDefinedTo(start, 0, scanRange.keySize)
                    if (cmp < 0) continue
                    if (!scanRange.includeStart && cmp == 0) continue
                }
                // Stream directly and stop at limit
                processStoreValue(key, creationVersion, null)
                streamed++
                if (streamed >= scanRequest.limit) break
            } else {
                // DESC: only include keys up to startKey (if defined)
                if (start != null) {
                    val cmp = modelKeyBytes.compareDefinedTo(start, 0, scanRange.keySize)
                    when {
                        cmp > 0 -> break // past start; remaining will be greater too
                        cmp == 0 && !scanRange.includeStart -> break // equal but excluded; next ones will be > start
                        // else: <= start (or equal and included) -> include
                    }
                }

                buffer!!.addLast(modelKeyBytes to creationVersion)
                if (buffer.size.toUInt() > limit) {
                    buffer.removeFirst()
                }
            }
        }
    }

    // Emit in requested direction with proper startKey slicing and limit
    var emitted = 0u
    when (direction) {
        ASC -> { /* already streamed above */ }
        DESC -> {
            // Emit from highest to lowest from the bounded buffer
            var idx = buffer!!.lastIndex
            while (idx >= 0 && emitted < scanRequest.limit) {
                val (kBytes, created) = buffer[idx--]
                val key = scanRequest.dataModel.key(kBytes)
                processStoreValue(key, created, null)
                emitted++
            }
        }
    }

    return FetchByTableScan(
        direction = direction,
        startKey = responseStartKey,
        stopKey = responseStopKey,
    )
}
