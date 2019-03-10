package maryk.core.processors.datastore

import maryk.lib.extensions.compare.compareTo

/**
 * Defines a range to scan. Also contains partial matches to check.
 */
abstract class ScanRange internal constructor(
    val start: ByteArray,
    val end: ByteArray? = null,
    private val partialMatches: List<IsIndexPartialToMatch>? = null
) {
    open fun keyBeforeStart(key: ByteArray) =  key < start
    open fun keyOutOfRange(key: ByteArray) = end?.let {  end < key } ?: false

    fun keyMatches(key: ByteArray): Boolean {
        partialMatches?.let {
            for (partial in partialMatches) {
                if (!partial.match(key)) return false
            }
        }
        return true
    }
}
