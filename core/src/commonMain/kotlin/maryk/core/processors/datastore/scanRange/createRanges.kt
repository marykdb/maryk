package maryk.core.processors.datastore.scanRange

import maryk.lib.extensions.compare.compareTo
import kotlin.math.floor

internal fun createRanges(
    start: MutableList<MutableList<Byte>>,
    end: MutableList<MutableList<Byte>>,
    startInclusive: Boolean,
    endInclusive: Boolean,
    startKey: ByteArray? = null
): List<ScanRange> {
    val ranges = ArrayList<ScanRange>(maxOf(start.size, end.size))

    if (start.size >= end.size) {
        val startMultiplier = start.size / end.size

        for ((index, startList) in start.withIndex()) {
            val startArray = startList.toByteArray()

            ranges += ScanRange(
                start = if (startKey != null && startArray < startKey) startKey else startArray,
                startInclusive = startInclusive,
                end = end[floor(index.toDouble() / startMultiplier).toInt()].toByteArray(),
                endInclusive = endInclusive
            )
        }
    } else {
        val endMultiplier = end.size / start.size

        for ((index, endList) in end.withIndex()) {
            val startArray = start[floor(index.toDouble() / endMultiplier).toInt()].toByteArray()

            ranges += ScanRange(
                start = if (startKey != null && startArray < startKey) startKey else startArray,
                startInclusive = startInclusive,
                end = endList.toByteArray(),
                endInclusive = endInclusive
            )
        }
    }

    return ranges
}
