package maryk.core.processors.datastore.scanRange

import maryk.lib.extensions.compare.compareTo
import kotlin.math.min

internal fun createRanges(
    start: List<List<Byte>>,
    end: List<List<Byte>>,
    startInclusive: Boolean,
    endInclusive: Boolean,
    startKey: ByteArray? = null
): List<ScanRange> {
    val maxSize = maxOf(start.size, end.size)
    val ranges = ArrayList<ScanRange>(maxSize)
    val multiplier = if (start.size >= end.size) start.size / end.size else end.size / start.size

    for (i in 0 until maxSize) {
        val startIndex = if (start.size >= end.size) i else i / multiplier
        val endIndex = if (start.size >= end.size) i / multiplier else i
        
        val startArray = start[min(startIndex, start.lastIndex)].toByteArray()
        val endArray = end[min(endIndex, end.lastIndex)].toByteArray()

        ranges += ScanRange(
            start = if (startKey != null && startArray < startKey) startKey else startArray,
            startInclusive = startInclusive,
            end = endArray,
            endInclusive = endInclusive
        )
    }

    return ranges
}
