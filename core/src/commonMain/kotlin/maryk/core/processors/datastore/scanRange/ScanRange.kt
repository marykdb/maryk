package maryk.core.processors.datastore.scanRange

import maryk.lib.extensions.compare.compareDefinedTo

/**
 * Defines a range to scan.
 */
final class ScanRange internal constructor(
    val start: ByteArray,
    val startInclusive: Boolean,
    val end: ByteArray? = null,
    val endInclusive: Boolean
) {
    /** Checks if [key] is before start of this scan range */
    fun keyBeforeStart(key: ByteArray, offset: Int = 0) =
        start.compareDefinedTo(key, offset).let {
            if (startInclusive) it > 0 else it >= 0
        }

    /** Checks if [key] is after the end of this range start of this scan range */
    fun keyOutOfRange(key: ByteArray, offset: Int = 0) = end?.let {
        end.compareDefinedTo(key, offset).let {
            if (endInclusive) it < 0 else it <= 0
        }
    } ?: false
}
