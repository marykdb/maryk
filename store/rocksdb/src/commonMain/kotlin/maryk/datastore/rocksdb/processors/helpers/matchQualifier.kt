package maryk.datastore.rocksdb.processors.helpers

import maryk.core.processors.datastore.matchers.FuzzyMatchResult.MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.NO_MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.OUT_OF_RANGE
import maryk.core.processors.datastore.matchers.QualifierExactMatcher
import maryk.core.processors.datastore.matchers.QualifierFuzzyMatcher
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.Transaction

/**
 * Match a qualifier from [reference] on transaction at [columnFamilies] and [readOptions] with [matcher] for [key].
 * Will search in history if [toVersion] is set.
 */
fun <T : Any> Transaction.matchQualifier(
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: Key<*>,
    reference: IsPropertyReference<T, *, *>,
    toVersion: ULong?,
    matcher: (T?) -> Boolean
): Boolean {
    when (val qualifierMatcher = reference.toQualifierMatcher()) {
        is QualifierExactMatcher -> {
            val value = this.getValue(
                columnFamilies,
                readOptions,
                toVersion,
                byteArrayOf(*key.bytes, *qualifierMatcher.qualifier)
            ) { valueBytes, offset, length ->
                valueBytes.convertToValue(reference, offset, length)
            }
            return matcher(value)
        }
        is QualifierFuzzyMatcher -> {
            this.iterateValues(columnFamilies, readOptions, toVersion, key, qualifierMatcher.firstPossible()) { referenceBytes, refOffset, refLength, valueBytes, valOffset, valLength ->
                when (qualifierMatcher.isMatch(referenceBytes, refOffset, refLength)) {
                    NO_MATCH -> null
                    MATCH -> {
                        val value = valueBytes.convertToValue(reference, valOffset, valLength)
                        if (matcher(value)) true else null
                    }
                    OUT_OF_RANGE -> false
                }
            }
            return false
        }
    }
}
