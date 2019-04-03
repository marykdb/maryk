package maryk.core.processors.datastore.matchers

import maryk.core.extensions.bytes.initIntByVar

/** Describes a fuzzy match where any byte will be matched */
sealed class IsFuzzyMatch {
    abstract fun skip(reader: () -> Byte)
}

/** Exact fuzzy [length] match */
class FuzzyExactLengthMatch(
    val length: Int
) : IsFuzzyMatch() {
    override fun skip(reader: () -> Byte) {
        for (i in 1..length) {
            reader()
        }
    }
}

/** Dynamic fuzzy length match, will read the length from the first bytes */
object FuzzyDynamicLengthMatch : IsFuzzyMatch() {
    override fun skip(reader: () -> Byte) {
        val length = initIntByVar(reader)

        for (i in 1..length) {
            reader()
        }
    }
}
