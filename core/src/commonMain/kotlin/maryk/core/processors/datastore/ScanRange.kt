package maryk.core.processors.datastore

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.query.pairs.ReferenceValuePair
import maryk.lib.extensions.compare.compareTo

/**
 * Defines a range to scan. Also contains partial matches to check.
 */
class ScanRange internal constructor(
    val start: ByteArray,
    val startInclusive: Boolean,
    val end: ByteArray? = null,
    val endInclusive: Boolean,
    val equalPairs: List<ReferenceValuePair<Any>>,
    val uniques: List<UniqueToMatch>? = null,
    val partialMatches: List<IsKeyPartialToMatch>? = null
) {
    fun keyBeforeStart(key: ByteArray) = if (startInclusive) key < start else key <= start
    fun keyOutOfRange(key: ByteArray) = end?.let { if (endInclusive) end < key else end <= key } ?: false

    fun keyMatches(key: ByteArray): Boolean {
        partialMatches?.let {
            for (partial in partialMatches) {
                if (!partial.match(key)) return false
            }
        }
        return true
    }
}

class UniqueToMatch(
    val reference: ByteArray,
    val definition: IsComparableDefinition<Comparable<Any>, IsPropertyContext>,
    val value: Comparable<*>
)
