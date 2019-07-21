package maryk.datastore.rocksdb.processors.helpers

import maryk.core.processors.datastore.matchers.FuzzyMatchResult.MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.NO_MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.OUT_OF_RANGE
import maryk.core.processors.datastore.matchers.QualifierExactMatcher
import maryk.core.processors.datastore.matchers.QualifierFuzzyMatcher
import maryk.core.properties.references.IsPropertyReference
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.Transaction

/**
 * Match a qualifier from [reference] on transaction at [columnFamilies] and [readOptions] with [matcher] for [key].
 * Will search in history if [toVersion] is set.
 */
internal fun <T : Any> Transaction.matchQualifier(
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: ByteArray,
    keyOffset: Int,
    keyLength: Int,
    reference: IsPropertyReference<T, *, *>,
    toVersion: ULong?,
    matcher: (T?) -> Boolean
): Boolean {
    when (val qualifierMatcher = reference.toQualifierMatcher()) {
        is QualifierExactMatcher -> {
            val qualifier = ByteArray(keyLength + qualifierMatcher.qualifier.size)
            key.copyInto(qualifier, 0, keyOffset, keyOffset + keyLength)
            qualifierMatcher.qualifier.copyInto(qualifier, keyLength)
            val value = this.getValue(
                columnFamilies,
                readOptions,
                toVersion,
                qualifier
            ) { valueBytes, offset, length ->
                valueBytes.convertToValue(reference, offset, length)
            }
            return matcher(value)
        }
        is QualifierFuzzyMatcher -> {
            this.iterateValues(columnFamilies, readOptions, toVersion, keyLength, qualifierMatcher.firstPossible()) { referenceBytes, refOffset, refLength, valueBytes, valOffset, valLength ->
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
