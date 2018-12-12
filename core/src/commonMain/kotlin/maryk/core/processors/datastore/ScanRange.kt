package maryk.core.processors.datastore

import maryk.lib.extensions.compare.compareTo

/**
 * Defines a range to scan. Also contains partial matches to check.
 */
class ScanRange internal constructor(
    val start: ByteArray,
    val startInclusive: Boolean,
    val end: ByteArray? = null,
    val endInclusive: Boolean,
    private val uniques: List<UniqueToMatch>? = null,
    private val partialMatches: List<IsKeyPartialToMatch>? = null
) {
    fun keyBeforeStart(key: ByteArray) = if (startInclusive) start < key else start <= key
    fun keyOutOfRange(key: ByteArray) = end?.let { if (endInclusive) end < key else end <= key } ?: false

    fun keyMatches(key: ByteArray): Boolean {
        partialMatches?.let {
            for (partial in partialMatches) {
                if(!partial.match(key)) return false
            }
        }
        return true
    }
}

internal class UniqueToMatch(
    val reference: ByteArray,
    val value: Comparable<*>
)
