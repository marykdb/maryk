package maryk.datastore.foundationdb.processors.helpers

import maryk.foundationdb.Transaction
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.NO_MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.OUT_OF_RANGE
import maryk.core.processors.datastore.matchers.IsQualifierMatcher
import maryk.core.processors.datastore.matchers.QualifierExactMatcher
import maryk.core.processors.datastore.matchers.QualifierFuzzyMatcher
import maryk.core.processors.datastore.matchers.ReferencedQualifierMatcher
import maryk.core.properties.references.IsPropertyReference
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.shared.helpers.convertToValue

/**
 * FoundationDB version of `matchQualifier`, mirroring the RocksDB behaviour but using FoundationDB primitives.
 * It relies on existing helpers on Transaction: `getValue` and `iterateValues`, which read historical values
 * when `toVersion` is provided and iterate ranges via AsyncIterable.
 */
internal fun <T : Any> Transaction.matchQualifier(
    tableDirs: IsTableDirectories,
    key: ByteArray,
    keyOffset: Int,
    keyLength: Int,
    reference: IsPropertyReference<T, *, *>,
    toVersion: ULong?,
    decryptValue: ((ByteArray) -> ByteArray)? = null,
    matcher: (T?) -> Boolean
): Boolean =
    this.matchQualifier(
        tableDirs = tableDirs,
        key = key,
        keyOffset = keyOffset,
        keyLength = keyLength,
        reference = reference,
        qualifierMatcher = reference.toQualifierMatcher(),
        toVersion = toVersion,
        decryptValue = decryptValue,
        matcher = matcher
    )

internal fun <T : Any> Transaction.matchQualifier(
    tableDirs: IsTableDirectories,
    key: ByteArray,
    keyOffset: Int,
    keyLength: Int,
    reference: IsPropertyReference<T, *, *>,
    qualifierMatcher: IsQualifierMatcher,
    toVersion: ULong?,
    decryptValue: ((ByteArray) -> ByteArray)? = null,
    matcher: (T?) -> Boolean
): Boolean {
    when (qualifierMatcher) {
        is QualifierExactMatcher -> {
            val qualifier = ByteArray(keyLength + qualifierMatcher.qualifier.size)
            key.copyInto(qualifier, 0, keyOffset, keyOffset + keyLength)
            qualifierMatcher.qualifier.copyInto(qualifier, keyLength)

            return when (val referencedMatcher = qualifierMatcher.referencedQualifierMatcher) {
                null -> {
                    val value = this.getValue(
                        tableDirs,
                        toVersion,
                        qualifier,
                        keyLength = keyLength,
                        decryptValue = decryptValue
                    ) { valueBytes, offset, length ->
                        valueBytes.convertToValue(reference, offset, length)
                    }
                    if (value != null) matcher(value)
                    else matchExactFromRange(tableDirs, toVersion, keyLength, qualifier, reference, decryptValue, matcher)
                }
                else ->
                    matchReferenced(
                        tableDirs = tableDirs,
                        toVersion = toVersion,
                        qualifier = qualifier,
                        keyLength = keyLength,
                        referencedMatcher = referencedMatcher,
                        reference = reference,
                        decryptValue = decryptValue,
                        matcher = matcher
                    )
            }
        }
        is QualifierFuzzyMatcher -> {
            val firstPossible = qualifierMatcher.firstPossible()
            val qualifier = ByteArray(keyLength + firstPossible.size)
            key.copyInto(qualifier, 0, keyOffset, keyOffset + keyLength)
            firstPossible.copyInto(qualifier, keyLength)

            val result = this.iterateValues(
                tableDirs,
                toVersion,
                keyLength,
                qualifier,
                decryptValue = decryptValue
            ) { referenceBytes, refOffset, refLength, valueBytes, valOffset, valLength ->
                when (qualifierMatcher.isMatch(referenceBytes, refOffset, refLength)) {
                    NO_MATCH -> null
                    MATCH -> {
                        val matches = when (val referencedMatcher = qualifierMatcher.referencedQualifierMatcher) {
                            null -> {
                                val value = valueBytes.convertToValue(reference, valOffset, valLength)
                                matcher(value)
                            }
                            else ->
                                matchReferenced(
                                    tableDirs = tableDirs,
                                    toVersion = toVersion,
                                    qualifier = qualifier,
                                    keyLength = keyLength,
                                    referencedMatcher = referencedMatcher,
                                    reference = reference,
                                    decryptValue = decryptValue,
                                    matcher = matcher
                                )
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

private fun <T : Any> Transaction.matchReferenced(
    tableDirs: IsTableDirectories,
    toVersion: ULong?,
    qualifier: ByteArray,
    keyLength: Int,
    referencedMatcher: ReferencedQualifierMatcher,
    reference: IsPropertyReference<T, *, *>,
    decryptValue: ((ByteArray) -> ByteArray)? = null,
    matcher: (T?) -> Boolean
): Boolean {
    val referencedKey = this.getValue(
        tableDirs,
        toVersion,
        qualifier,
        keyLength = keyLength,
        decryptValue = decryptValue
    ) { valueBytes, offset, length ->
        valueBytes.convertToValue(referencedMatcher.reference, offset, length)
    }

    if (referencedKey == null) return false

    // When the reference points to another model, try to resolve its directories if available
    val nextDirs = tableDirs.dataStore.getTableDirs(referencedMatcher.reference.propertyDefinition.dataModel)

    return this.matchQualifier(
        tableDirs = nextDirs,
        key = referencedKey.bytes,
        keyOffset = 0,
        keyLength = referencedKey.bytes.size,
        reference = reference,
        qualifierMatcher = referencedMatcher.qualifierMatcher,
        toVersion = toVersion,
        decryptValue = decryptValue,
        matcher = matcher
    )
}

/**
 * Fallback helper for exact qualifier matching. If a direct get does not return a value,
 * iterate the range and compare the qualifier bytes exactly, then decode and test the value.
 */
private fun <T : Any> Transaction.matchExactFromRange(
    tableDirs: IsTableDirectories,
    toVersion: ULong?,
    keyLength: Int,
    qualifier: ByteArray,
    reference: IsPropertyReference<T, *, *>,
    decryptValue: ((ByteArray) -> ByteArray)? = null,
    matcher: (T?) -> Boolean
): Boolean {
    val exact = qualifier.copyOfRange(keyLength, qualifier.size)
    val iterRes = this.iterateValues(
        tableDirs,
        toVersion,
        keyLength,
        qualifier,
        decryptValue = decryptValue
    ) { referenceBytes, refOffset, refLength, valueBytes, valOffset, valLength ->
        if (refLength == exact.size) {
            var i = 0
            while (i < exact.size && referenceBytes[refOffset + i] == exact[i]) i++
            if (i == exact.size) {
                val v = valueBytes.convertToValue(reference, valOffset, valLength)
                if (matcher(v)) true else null
            } else null
        } else null
    }
    return iterRes == true
}
