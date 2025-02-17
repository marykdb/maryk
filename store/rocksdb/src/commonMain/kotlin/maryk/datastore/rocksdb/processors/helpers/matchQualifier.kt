package maryk.datastore.rocksdb.processors.helpers

import maryk.core.processors.datastore.matchers.FuzzyMatchResult.MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.NO_MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.OUT_OF_RANGE
import maryk.core.processors.datastore.matchers.IsQualifierMatcher
import maryk.core.processors.datastore.matchers.QualifierExactMatcher
import maryk.core.processors.datastore.matchers.QualifierFuzzyMatcher
import maryk.core.processors.datastore.matchers.ReferencedQualifierMatcher
import maryk.core.properties.references.IsPropertyReference
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.rocksdb.ReadOptions

/**
 * Match a qualifier from [reference] on transaction at [columnFamilies] and [readOptions] with [matcher] for [key].
 * Will search in history if [toVersion] is set.
 */
internal fun <T : Any> DBAccessor.matchQualifier(
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: ByteArray,
    keyOffset: Int,
    keyLength: Int,
    reference: IsPropertyReference<T, *, *>,
    toVersion: ULong?,
    matcher: (T?) -> Boolean
) =
    this.matchQualifier(columnFamilies, readOptions, key, keyOffset, keyLength, reference, reference.toQualifierMatcher(), toVersion, matcher)

/**
 * Match a qualifier from [reference] on transaction at [columnFamilies] and [readOptions] with [matcher] for [key].
 * Will search in history if [toVersion] is set.
 */
internal fun <T : Any> DBAccessor.matchQualifier(
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: ByteArray,
    keyOffset: Int,
    keyLength: Int,
    reference: IsPropertyReference<T, *, *>,
    qualifierMatcher: IsQualifierMatcher,
    toVersion: ULong?,
    matcher: (T?) -> Boolean
): Boolean {
    when (qualifierMatcher) {
        is QualifierExactMatcher -> {
            val qualifier = ByteArray(keyLength + qualifierMatcher.qualifier.size)
            key.copyInto(qualifier, 0, keyOffset, keyOffset + keyLength)
            qualifierMatcher.qualifier.copyInto(qualifier, keyLength)

            return when (val referencedMatcher = qualifierMatcher.referencedQualifierMatcher) {
                null -> {
                    val value = this.getValue(columnFamilies, readOptions, toVersion, qualifier) { valueBytes, offset, length ->
                        valueBytes.convertToValue(reference, offset, length)
                    }

                    matcher(value)
                }
                else ->
                    matchReferenced(columnFamilies, readOptions, toVersion, qualifier, referencedMatcher, reference, matcher)
            }
        }
        is QualifierFuzzyMatcher -> {
            val firstPossible = qualifierMatcher.firstPossible()
            val qualifier = ByteArray(keyLength + firstPossible.size)
            key.copyInto(qualifier, 0, keyOffset, keyOffset + keyLength)
            firstPossible.copyInto(qualifier, keyLength)

            val result = this.iterateValues(columnFamilies, readOptions, toVersion, keyLength, qualifier) { referenceBytes, refOffset, refLength, valueBytes, valOffset, valLength ->
                when (qualifierMatcher.isMatch(referenceBytes, refOffset, refLength)) {
                    NO_MATCH -> null
                    MATCH -> {
                        val matches = when (val referencedMatcher = qualifierMatcher.referencedQualifierMatcher) {
                            null -> {
                                val value = valueBytes.convertToValue(reference, valOffset, valLength)
                                matcher(value)
                            }
                            else ->
                                matchReferenced(columnFamilies, readOptions, toVersion, qualifier, referencedMatcher, reference, matcher)
                        }

                        if (matches) true else null
                    }
                    OUT_OF_RANGE -> false
                }
            }
            return result == true
        }
    }
}

private fun <T : Any> DBAccessor.matchReferenced(
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    toVersion: ULong?,
    qualifier: ByteArray,
    referencedMatcher: ReferencedQualifierMatcher,
    reference: IsPropertyReference<T, *, *>,
    matcher: (T?) -> Boolean
): Boolean {
    val referencedKey = this.getValue(columnFamilies, readOptions, toVersion, qualifier) { valueBytes, offset, length ->
        valueBytes.convertToValue(referencedMatcher.reference, offset, length)
    }

    return if (referencedKey == null) {
        false
    } else {
        this.matchQualifier<T>(
            columnFamilies = this.dataStore.getColumnFamilies(referencedMatcher.reference.propertyDefinition.dataModel),
            readOptions = readOptions,
            key = referencedKey.bytes,
            keyOffset = 0,
            keyLength = referencedKey.bytes.size,
            reference = reference,
            qualifierMatcher = referencedMatcher.qualifierMatcher,
            toVersion = toVersion,
            matcher = matcher
        )
    }
}
