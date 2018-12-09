package maryk.core.processors.datastore

import maryk.lib.extensions.compare.compareTo

/**
 * Defines a range to scan. Also contains partial matches to check.
 */
class ScanRange internal constructor(
    val start: ByteArray,
    val end: ByteArray? = null,
    private val uniques: List<UniqueToMatch>? = null,
    private val partialMatches: List<IsPartialToMatch>? = null
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

interface IsPartialToMatch {
    val fromIndex: Int
    fun match(bytes: ByteArray): Boolean
}

internal class PartialToMatch(
    override val fromIndex: Int,
    val toMatch: ByteArray
): IsPartialToMatch {
    /** Matches [bytes] to partial and returns true if matches */
    override fun match(bytes: ByteArray): Boolean {
        toMatch.forEachIndexed { index, byte ->
            if(bytes[index + this.fromIndex] != byte) return false
        }
        return true
    }
}
