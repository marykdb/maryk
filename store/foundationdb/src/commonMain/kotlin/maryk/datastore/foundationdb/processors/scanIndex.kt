package maryk.datastore.foundationdb.processors

import com.apple.foundationdb.Range
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
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.helpers.getValue
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.shared.ScanType
import maryk.datastore.shared.helpers.convertToValue
import maryk.lib.bytes.combineToByteArray
import maryk.lib.extensions.compare.compareDefinedTo
import maryk.lib.extensions.compare.compareTo

internal fun <DM : IsRootDataModel> FoundationDBDataStore.scanIndex(
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
    val versionSize = if (scanRequest.toVersion != null) {
        if (tableDirs !is HistoricTableDirectories) {
            throw StorageException("No historic table stored so toVersion in query cannot be processed")
        }
        // For now: we do not support historic index reads in FDB yet
        0
    } else 0

    // Compute response metadata start/stop
    // Build a helper valuesGetter for computing index start from the startKey
    val startIndexKey: ByteArray? = scanRequest.startKey?.let { startKey ->
        tc.run { tr ->
            val getter = object : IsValuesGetter {
                override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(
                    propertyReference: IsPropertyReference<T, D, C>
                ): T? {
                    val keyAndRef = combineToByteArray(startKey.bytes, propertyReference.toStorageByteArray())
                    return tr.getValue(tableDirs, null, keyAndRef) { valueBytes, offset, length ->
                        valueBytes.convertToValue(propertyReference, offset, length) as T?
                    }
                }
            }
            indexScan.index.toStorageByteArrayForIndex(getter, startKey.bytes)
        }
    }

    val responseStartKey = when (indexScan.direction) {
        ASC -> indexScanRange.ranges.first().getAscendingStartKey(startIndexKey, scanRequest.includeStart)
        DESC -> indexScanRange.ranges.first().getDescendingStartKey(startIndexKey, scanRequest.includeStart)
    }
    val responseStopKey = when (indexScan.direction) {
        ASC -> indexScanRange.ranges.last().getDescendingStartKey()
        DESC -> indexScanRange.ranges.last().getAscendingStartKey()
    }

    // Collect matching records (valueAndKey slice + (key, createdVersion))
    data class Rec(val sort: ByteArray, val keyBytes: ByteArray, val created: ULong)
    val collected = mutableListOf<Rec>()

    tc.run { tr ->
        val it = tr.getRange(Range.startsWith(packKey(tableDirs.indexPrefix, indexReference))).iterator()
        while (it.hasNext()) {
            val kv = it.next()
            val indexKeyBytes = kv.key
            // Ensure still under same index reference
            // Not strictly needed due to startsWith, but kept for clarity
            // Compute sizes
            val totalLen = indexKeyBytes.size
            val valueSize = totalLen - valueOffset - keySize - versionSize
            if (valueSize < 0) continue

            // Check primary key ranges based on value+key segment
            var inRange = false
            for (range in indexScanRange.ranges) {
                val segmentLength = valueSize + keySize
                val before = range.keyBeforeStart(indexKeyBytes, valueOffset, segmentLength)
                val out = range.keyOutOfRange(indexKeyBytes, valueOffset, segmentLength)
                if (!before && !out) { inRange = true; break }
            }
            if (!inRange) continue
            if (!indexScanRange.matchesPartials(indexKeyBytes, valueOffset, valueSize)) continue

            val keyOffset = valueOffset + valueSize
            val keyBytes = indexKeyBytes.copyOfRange(keyOffset, keyOffset + keySize)
            // read creation version from keys record
            val createdPacked = tr.get(packKey(tableDirs.keysPrefix, keyBytes)).join() ?: continue
            val createdVersion = HLC.fromStorageBytes(createdPacked).timestamp

            // sorting key is value+key bytes without version suffix
            val sortingKey = indexKeyBytes.copyOfRange(valueOffset, totalLen - versionSize)
            collected += Rec(sortingKey, keyBytes, createdVersion)
        }
    }

    // Ensure deterministic ordering by sorting on sortingKey asc
    collected.sortWith { a, b -> a.sort compareTo b.sort }

    // Emit according to direction with start slicing and limit
    var emitted = 0u
    when (indexScan.direction) {
        ASC -> {
            var idx = 0
            startIndexKey?.let { si ->
                while (idx < collected.size && collected[idx].sort.compareDefinedTo(si) < 0) idx++
                if (!scanRequest.includeStart && idx < collected.size && collected[idx].sort.contentEquals(si)) idx++
            }
            while (idx < collected.size && emitted < scanRequest.limit) {
                val rec = collected[idx++]
                val key = scanRequest.dataModel.key(rec.keyBytes)
                processStoreValue(key, rec.created, rec.sort)
                emitted++
            }
        }
        DESC -> {
            var idx = collected.lastIndex
            startIndexKey?.let { si ->
                idx = collected.indexOfLast { it.sort.compareDefinedTo(si) <= 0 }
                if (!scanRequest.includeStart && idx >= 0 && collected[idx].sort.contentEquals(si)) idx--
            }
            while (idx >= 0 && emitted < scanRequest.limit) {
                val rec = collected[idx--]
                val key = scanRequest.dataModel.key(rec.keyBytes)
                processStoreValue(key, rec.created, rec.sort)
                emitted++
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
