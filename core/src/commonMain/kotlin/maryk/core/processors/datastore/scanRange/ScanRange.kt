package maryk.core.processors.datastore.scanRange

import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.compareToRange
import maryk.lib.extensions.compare.nextByteInSameLength
import maryk.lib.extensions.compare.prevByteInSameLength

/**
 * Defines a range to scan.
 */
data class ScanRange internal constructor(
    val start: ByteArray,
    val startInclusive: Boolean,
    val end: ByteArray? = null,
    val endInclusive: Boolean
) {
    /** Checks if [key] is before start of this scan range */
    fun keyBeforeStart(key: ByteArray, offset: Int = 0, length: Int = key.size - offset) =
        if (start.isEmpty()) {
            false
        } else {
            start.compareToRange(key, offset, length).let {
                if (startInclusive) it > 0 else it >= 0
            }
        }

    /** Checks if [key] is after the end of this range end of this scan range */
    fun keyOutOfRange(key: ByteArray, offset: Int = 0, length: Int = key.size - offset) = end?.let {
        if (end.isEmpty()) {
            false
        } else {
            end.compareToRange(key, offset, length).let {
                if (endInclusive) it < 0 else it <= 0
            }
        }
    } == true

    /** Get the descending start key for scans */
    fun getDescendingStartKey(startKey: ByteArray? = null, inclusiveStartKey: Boolean = true): ByteArray? {
        // An inclusive prefix ends at its successor. Full index cursors inside the
        // prefix sort after the prefix itself, but before this exclusive boundary.
        val upperBound = if (endInclusive) {
            end?.nextByteInSameLength()?.takeUnless { it === end && it.isNotEmpty() }
        } else end
        return when {
            startKey != null && (upperBound == null || upperBound.isEmpty() || startKey < upperBound) ->
                if (inclusiveStartKey) startKey else startKey.takeUnless { key -> key.all { it == 0.toByte() } }?.prevByteInSameLength()
            else -> upperBound
        }
    }

    /** Get the ascending start key for scans */
    fun getAscendingStartKey(startKey: ByteArray? = null, inclusiveStartKey: Boolean = true): ByteArray =
        when {
            startKey != null && startKey > start ->
                if (inclusiveStartKey) startKey else startKey.nextByteInSameLength()
            startInclusive -> start
            else -> start.nextByteInSameLength()
        }
}
