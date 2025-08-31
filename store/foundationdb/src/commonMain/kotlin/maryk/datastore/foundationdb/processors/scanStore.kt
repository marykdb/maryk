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

    // First pass: collect all matching keys in ascending order
    val collected = mutableListOf<Pair<ByteArray, ULong>>()

    if (direction == ASC) {
        responseStartKey = scanRange.ranges.first().getAscendingStartKey(scanRange.startKey, scanRange.includeStart)
        responseStopKey = scanRange.ranges.last().getDescendingStartKey()
    } else {
        responseStartKey = scanRange.ranges.first().getDescendingStartKey(scanRange.startKey, scanRange.includeStart)
        responseStopKey = scanRange.ranges.last().getAscendingStartKey()
    }

    tc.run { tr ->
        val it = tr.getRange(Range.startsWith(prefix)).iterator()
        while (it.hasNext()) {
            val kv: KeyValue = it.next()
            val modelKeyBytes = kv.key.copyOfRange(prefix.size, kv.key.size)

            if (!scanRange.keyWithinRanges(modelKeyBytes, 0)) continue
            if (!scanRange.matchesPartials(modelKeyBytes)) continue

            val key = scanRequest.dataModel.key(modelKeyBytes)
            val creationVersion = HLC.fromStorageBytes(kv.value).timestamp
            if (scanRequest.shouldBeFiltered(tr, tableDirs, key.bytes, 0, key.size, creationVersion, scanRequest.toVersion)) continue

            collected += modelKeyBytes to creationVersion
        }
    }

    // Emit in requested direction with proper startKey slicing and limit
    var emitted = 0u
    when (direction) {
        ASC -> {
            // find first index >= start (or 0 if no start)
            var idx = 0
            scanRange.startKey?.let { start ->
                // locate first >= start
                while (idx < collected.size && collected[idx].first.compareDefinedTo(start, 0, scanRange.keySize) < 0) idx++
                if (!scanRange.includeStart && idx < collected.size && collected[idx].first.contentEquals(start)) {
                    idx++
                }
            }
            while (idx < collected.size && emitted < scanRequest.limit) {
                val (kBytes, created) = collected[idx++]
                val key = scanRequest.dataModel.key(kBytes)
                processStoreValue(key, created, null)
                emitted++
            }
        }
        DESC -> {
            val start = scanRange.startKey
            var idx = collected.lastIndex
            if (start != null) {
                // find last index <= start
                idx = collected.indexOfLast { it.first.compareDefinedTo(start, 0, scanRange.keySize) <= 0 }
                if (!scanRange.includeStart && idx >= 0 && collected[idx].first.contentEquals(start)) {
                    idx--
                }
            }
            while (idx >= 0 && emitted < scanRequest.limit) {
                val (kBytes, created) = collected[idx--]
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
