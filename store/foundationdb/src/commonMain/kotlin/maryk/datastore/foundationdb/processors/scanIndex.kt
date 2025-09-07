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
import maryk.datastore.foundationdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.foundationdb.processors.helpers.decodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.getValue
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.readReversedVersionBytes
import maryk.datastore.foundationdb.processors.helpers.toReversedVersionBytes
import maryk.datastore.shared.ScanType
import maryk.datastore.shared.helpers.convertToValue
import maryk.lib.bytes.combineToByteArray
import maryk.lib.extensions.compare.compareDefinedTo
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.compareToWithOffsetLength

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
    val useHistoric = scanRequest.toVersion != null
    if (useHistoric && tableDirs !is HistoricTableDirectories) {
        throw StorageException("No historic table stored so toVersion in query cannot be processed")
    }
    val versionSize = if (useHistoric) VERSION_BYTE_SIZE else 0

    // Compute response metadata start/stop
    // Build a helper valuesGetter for computing index start from the startKey (respecting toVersion)
    val startIndexKey: ByteArray? = scanRequest.startKey?.let { startKey ->
        tc.run { tr ->
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
        if (!useHistoric) {
            // Iterate per computed index range segment
            val base = packKey(tableDirs.indexPrefix, indexReference)
            val baseEnd = Range.startsWith(base).end
            indexScanRange.ranges.forEachIndexed { i, range ->
                val startSeg = when (indexScan.direction) {
                    ASC -> range.getAscendingStartKey(startIndexKey.takeIf { i == 0 }, if (i == 0) scanRequest.includeStart else true)
                    DESC -> range.getAscendingStartKey(null, true)
                }
                val endExclusiveSeg = range.getDescendingStartKey()
                val begin = combineToByteArray(base, startSeg)
                val end = if (endExclusiveSeg == null || endExclusiveSeg.isEmpty()) baseEnd else combineToByteArray(base, endExclusiveSeg)

                val it = tr.getRange(Range(begin, end)).iterator()
                while (it.hasNext()) {
                    val kv = it.next()
                    val indexKeyBytes = kv.key
                    val totalLen = indexKeyBytes.size
                    val valueSize = totalLen - valueOffset - keySize - versionSize
                    if (valueSize < 0) continue

                    val sortingKey = indexKeyBytes.copyOfRange(valueOffset, totalLen - versionSize)

                    if (!indexScanRange.matchesPartials(indexKeyBytes, valueOffset, valueSize)) continue

                    val keyOffset = valueOffset + valueSize
                    val keyBytes = indexKeyBytes.copyOfRange(keyOffset, keyOffset + keySize)
                    val createdPacked = tr.get(packKey(tableDirs.keysPrefix, keyBytes)).join() ?: continue
                    val createdVersion = HLC.fromStorageBytes(createdPacked).timestamp
                    if (scanRequest.shouldBeFiltered(tr, tableDirs, keyBytes, 0, keySize, createdVersion, scanRequest.toVersion)) continue
                    collected += Rec(sortingKey, keyBytes, createdVersion)
                }
            }
        } else {
            // Historic index scan: iterate per segment over versioned index
            require(tableDirs is HistoricTableDirectories)
            val histBase = tableDirs.historicIndexPrefix
            val basePrefix = encodeZeroFreeUsing01(indexReference)
            val baseEnd = Range.startsWith(packKey(histBase, basePrefix)).end
            val versionFloor = scanRequest.toVersion!!.toReversedVersionBytes()
            val toVersionBytes = versionFloor
            data class Rev(val version: ULong, val rec: Rec)
            val latestByKey = mutableMapOf<Int, Rev>() // keep the max version per key

            indexScanRange.ranges.forEachIndexed { i, range ->
                val startSeg = when (indexScan.direction) {
                    ASC -> range.getAscendingStartKey(startIndexKey.takeIf { i == 0 }, if (i == 0) scanRequest.includeStart else true)
                    DESC -> range.getAscendingStartKey(null, true)
                }
                val endExclusiveSeg = range.getDescendingStartKey()

                val beginQualifier = encodeZeroFreeUsing01(combineToByteArray(indexReference, startSeg))
                val begin = packKey(histBase, beginQualifier, byteArrayOf(0), versionFloor)
                val end = if (endExclusiveSeg == null || endExclusiveSeg.isEmpty()) {
                    baseEnd
                } else {
                    val endQualifier = encodeZeroFreeUsing01(combineToByteArray(indexReference, endExclusiveSeg))
                    packKey(histBase, endQualifier)
                }

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
                    // Only process first entry per qualifier (this is the latest <= toVersion)
                    val prevEnc = lastQualifierEncoded
                    if (prevEnc != null && prevEnc.contentEquals(encQualifier)) continue
                    lastQualifierEncoded = encQualifier
                    val qualifier = decodeZeroFreeUsing01(encQualifier)
                    if (qualifier.size <= indexReference.size) continue
                    val valueAndKey = qualifier.copyOfRange(indexReference.size, qualifier.size)

                    if (!indexScanRange.matchesPartials(valueAndKey, 0, valueAndKey.size - keySize)) continue

                    val keyBytes = valueAndKey.copyOfRange(valueAndKey.size - keySize, valueAndKey.size)
                    val keyId = keyBytes.contentHashCode()
                    // For historic filter checks, pass null as creationVersion to avoid excluding records created after toVersion
                    if (scanRequest.shouldBeFiltered(tr, tableDirs, keyBytes, 0, keySize, null, scanRequest.toVersion)) continue

                    val createdPacked = tr.get(packKey(tableDirs.keysPrefix, keyBytes)).join() ?: continue
                    val createdVersion = HLC.fromStorageBytes(createdPacked).timestamp
                    val version = k.readReversedVersionBytes(versionOffset)
                    val rec = Rec(valueAndKey, keyBytes, createdVersion)
                    val prev = latestByKey[keyId]
                    if (prev == null || version > prev.version) {
                        latestByKey[keyId] = Rev(version, rec)
                    }
                }
            }
            collected += latestByKey.values.map { it.rec }
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
