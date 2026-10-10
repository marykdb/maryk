package maryk.datastore.indexeddb.processors

import maryk.core.processors.datastore.findByteIndexAndSizeByPartIndex
import maryk.core.processors.datastore.scanRange.IndexableScanRanges
import maryk.core.processors.datastore.scanRange.ScanRange
import maryk.lib.extensions.compare.nextByteInSameLength

internal fun keyPrefixUpperBound(bytes: ByteArray): ByteArray? =
    bytes.nextByteInSameLength().takeUnless { it === bytes }

internal fun resolveIndexValueSize(indexValue: ByteArray, keySize: Int, indexPartCount: Int): Int? {
    if (indexPartCount <= 0 || indexValue.size < keySize) return null

    return try {
        val (offset, size) = findByteIndexAndSizeByPartIndex(
            partIndex = indexPartCount - 1,
            indexable = indexValue,
            keySize = keySize,
            indexPartCount = indexPartCount
        )
        offset + size
    } catch (_: Exception) {
        null
    }
}

internal fun indexRangeLength(
    indexScanRange: IndexableScanRanges,
    range: ScanRange,
    valueSize: Int
): Int =
    indexScanRange.rangeLength(range, valueSize)
