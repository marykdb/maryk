package maryk.datastore.indexeddb.processors

import maryk.core.processors.datastore.findByteIndexAndSizeByPartIndex
import maryk.core.processors.datastore.matchers.IndexPartialSizeToMatch
import maryk.core.processors.datastore.matchers.IndexPartialToMatch
import maryk.core.processors.datastore.matchers.IndexPartialToBeOneOf
import maryk.core.processors.datastore.scanRange.IndexableScanRanges
import maryk.core.processors.datastore.scanRange.ScanRange

internal fun keyPrefixUpperBound(bytes: ByteArray): ByteArray? {
    val next = bytes.copyOf()
    for (index in next.lastIndex downTo 0) {
        if (next[index] != 0xFF.toByte()) {
            next[index]++
            return next
        }
    }
    return null
}

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
    if (
        range.start.isNotEmpty() &&
        range.startInclusive &&
        range.endInclusive &&
        range.end?.contentEquals(range.start) == true &&
        indexScanRange.partialMatches?.any {
            (it is IndexPartialSizeToMatch && it.size == range.start.size) ||
                (it is IndexPartialToMatch &&
                    it.partialMatch &&
                    it.toMatch.contentEquals(range.start)) ||
                (it is IndexPartialToBeOneOf &&
                    it.partialMatch &&
                    it.toBeOneOf.any(range.start::contentEquals))
        } == true
    ) {
        range.start.size
    } else {
        valueSize
    }
