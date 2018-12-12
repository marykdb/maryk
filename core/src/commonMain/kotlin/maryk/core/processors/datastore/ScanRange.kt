package maryk.core.processors.datastore

import maryk.lib.extensions.compare.compareTo

/**
 * Defines a range to scan. Also contains partial matches to check.
 */
class ScanRange internal constructor(
    val start: ByteArray, // Is inclusive
    val end: ByteArray? = null, // Is inclusive
    private val uniques: List<UniqueToMatch>? = null,
    private val partialMatches: List<IsKeyPartialToMatch>? = null
) {
    fun keyOutOfRange(key: ByteArray) = end?.let { end < key } ?: false

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
