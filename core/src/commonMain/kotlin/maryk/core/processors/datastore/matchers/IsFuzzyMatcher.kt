package maryk.core.processors.datastore.matchers

import maryk.core.extensions.bytes.initIntByVar
import maryk.lib.exceptions.ParseException

/** Describes a fuzzy match where any byte will be matched */
sealed class IsFuzzyMatcher {
    abstract fun skip(reader: () -> Byte)
}

/** Exact fuzzy [length] match */
class FuzzyExactLengthMatch(
    val length: Int
) : IsFuzzyMatcher() {
    override fun skip(reader: () -> Byte) {
        repeat(length) {
            reader()
        }
    }
}

/** Dynamic fuzzy length match, will read the length from the first bytes */
object FuzzyDynamicLengthMatch : IsFuzzyMatcher() {
    override fun skip(reader: () -> Byte) {
        val length = initIntByVar(reader)
        if (length < 0) {
            throw ParseException("Negative fuzzy match length")
        }

        repeat(length) {
            reader()
        }
    }
}
