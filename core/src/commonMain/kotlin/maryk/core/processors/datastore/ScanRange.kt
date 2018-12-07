package maryk.core.processors.datastore

import maryk.core.query.filters.IsFilter
import maryk.lib.extensions.compare.compareTo

/**
 * Defines a range to scan. Also contains partial matches to check.
 */
class ScanRange internal constructor(
    val start: ByteArray,
    private val end: ByteArray? = null,
    private val uniques: List<UniqueToMatch>? = null,
    private val partialMatches: List<PartialToMatch>? = null
) {
    fun keyOutOfRange(key: ByteArray) = end?.let { end < key } ?: false

    fun keyMatches(key: ByteArray): Boolean {
        partialMatches?.let {
            for (partial in partialMatches) {
                partial.toMatch.forEachIndexed { index, byte ->
                    if(key[index + partial.fromIndex] != byte) return false
                }
            }
        }
        return true
    }
}

internal class UniqueToMatch(
    val reference: ByteArray,
    val value: Comparable<*>
)

internal class PartialToMatch(
    val fromIndex: Int,
    val toMatch: ByteArray
)

fun IsFilter?.toScanRange(startKey: ByteArray): ScanRange {
    return ScanRange(
        start = startKey
    )
}
