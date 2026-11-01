package maryk.datastore.rocksdb.processors.helpers

import maryk.core.processors.datastore.matchers.FuzzyMatchResult.MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.NO_MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.OUT_OF_RANGE
import maryk.core.processors.datastore.matchers.IsQualifierMatcher
import maryk.core.processors.datastore.matchers.QualifierExactMatcher
import maryk.core.processors.datastore.matchers.QualifierFuzzyMatcher
import maryk.core.processors.datastore.matchers.ReferencedQualifierMatcher
import maryk.core.properties.references.TypeReference
import maryk.core.properties.references.SimpleTypedValueReference
import maryk.core.properties.references.IsPropertyReference
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.shared.helpers.convertToValue
import maryk.datastore.shared.helpers.matchedQualifier
import maryk.datastore.shared.helpers.readFuzzyValue
import maryk.datastore.shared.requiresStoredReferenceStructure
import maryk.datastore.shared.hasStoredValuePayload
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
    historicalReader: HistoricalTableReader? = null,
    matcher: (T?) -> Boolean
) =
    this.matchQualifierWithReader(columnFamilies, readOptions, key, keyOffset, keyLength, reference, reference.toQualifierMatcher(), toVersion, historicalReader, matcher)

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
    historicalReader: HistoricalTableReader? = null,
    matcher: (T?) -> Boolean
): Boolean {
    if (historicalReader != null || toVersion == null || columnFamilies !is HistoricTableColumnFamilies) {
        return matchQualifierWithReader(
            columnFamilies,
            readOptions,
            key,
            keyOffset,
            keyLength,
            reference,
            qualifierMatcher,
            toVersion,
            historicalReader,
            matcher
        )
    }

    HistoricalTableReader(this, columnFamilies, readOptions, toVersion).use { reader ->
        return matchQualifierWithReader(
            columnFamilies,
            readOptions,
            key,
            keyOffset,
            keyLength,
            reference,
            qualifierMatcher,
            toVersion,
            reader,
            matcher
        )
    }
}

private fun <T : Any> DBAccessor.matchQualifierWithReader(
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: ByteArray,
    keyOffset: Int,
    keyLength: Int,
    reference: IsPropertyReference<T, *, *>,
    qualifierMatcher: IsQualifierMatcher,
    toVersion: ULong?,
    historicalReader: HistoricalTableReader?,
    matcher: (T?) -> Boolean
): Boolean {
    when (qualifierMatcher) {
        is QualifierExactMatcher -> {
            val qualifier = ByteArray(keyLength + qualifierMatcher.qualifier.size)
            key.copyInto(qualifier, 0, keyOffset, keyOffset + keyLength)
            qualifierMatcher.qualifier.copyInto(qualifier, keyLength)

            return when (val referencedMatcher = qualifierMatcher.referencedQualifierMatcher) {
                null -> {
                    var index = 0
                    val actualReference = if (reference is TypeReference<*, *, *> || reference is SimpleTypedValueReference<*, *, *>) {
                        reference
                    } else dataStore.dataModelsById.getValue(columnFamilies.modelId).getPropertyReferenceByStorageBytes(
                        qualifierMatcher.qualifier.size, { qualifierMatcher.qualifier[index++] }
                    )
                    if (!hasActiveStoredType(columnFamilies, readOptions, key.copyOfRange(keyOffset, keyOffset + keyLength), actualReference, toVersion, historicalReader)) return matcher(null)
                    if (requiresStoredReferenceStructure(actualReference)) {
                        @Suppress("UNCHECKED_CAST")
                        return matcher(readFilterStructure(columnFamilies, readOptions, key.copyOfRange(keyOffset, keyOffset + keyLength), actualReference, toVersion, historicalReader) as T?)
                    }
                    val value = this.getValue(columnFamilies, readOptions, toVersion, qualifier, historicalReader) { valueBytes, offset, length ->
                        valueBytes.convertToValue(reference, offset, length)
                    }

                    matcher(value)
                }
                else ->
                    matchReferenced(columnFamilies, readOptions, toVersion, qualifier, referencedMatcher, reference, historicalReader, matcher)
            }
        }
        is QualifierFuzzyMatcher -> {
            val firstPossible = qualifierMatcher.firstPossible()
            val qualifier = ByteArray(keyLength + firstPossible.size)
            key.copyInto(qualifier, 0, keyOffset, keyOffset + keyLength)
            firstPossible.copyInto(qualifier, keyLength)

            val result = this.iterateValues(columnFamilies, readOptions, toVersion, keyLength, qualifier, historicalReader, decryptValue = false) { referenceBytes, refOffset, refLength, valueBytes, valOffset, valLength ->
                when (qualifierMatcher.isMatch(referenceBytes, refOffset, refLength)) {
                    NO_MATCH -> null
                    MATCH -> {
                        var index = refOffset
                        val actualReference = dataStore.dataModelsById.getValue(columnFamilies.modelId)
                            .getPropertyReferenceByStorageBytes(refLength, { referenceBytes[index++] })
                        // Do not move the iterator backing this fuzzy scan while reading its parent markers.
                        if (!hasActiveStoredType(columnFamilies, readOptions, key.copyOfRange(keyOffset, keyOffset + keyLength), actualReference, toVersion)) return@iterateValues null
                        if (!hasStoredValuePayload(actualReference.comparablePropertyDefinition, valueBytes, valOffset, valLength)) return@iterateValues null
                        val matches = when (val referencedMatcher = qualifierMatcher.referencedQualifierMatcher) {
                            null -> {
                                @Suppress("UNCHECKED_CAST")
                                val value = (readFuzzyValue(reference, referenceBytes, refOffset, refLength) as T?)
                                    ?: if (requiresStoredReferenceStructure(actualReference)) {
                                        readFilterStructure(columnFamilies, readOptions, key.copyOfRange(keyOffset, keyOffset + keyLength), actualReference, toVersion, null) as T?
                                    } else getValue(columnFamilies, readOptions, toVersion,
                                        matchedQualifier(key, keyOffset, keyLength, referenceBytes, refOffset, refLength)) { bytes, offset, length ->
                                        bytes.convertToValue(reference, offset, length)
                                    }
                                matcher(value)
                            }
                            else ->
                                matchReferenced(
                                    columnFamilies,
                                    readOptions,
                                    toVersion,
                                    matchedQualifier(key, keyOffset, keyLength, referenceBytes, refOffset, refLength),
                                    referencedMatcher,
                                    reference,
                                    null,
                                    matcher
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

private fun <T : Any> DBAccessor.matchReferenced(
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    toVersion: ULong?,
    qualifier: ByteArray,
    referencedMatcher: ReferencedQualifierMatcher,
    reference: IsPropertyReference<T, *, *>,
    historicalReader: HistoricalTableReader?,
    matcher: (T?) -> Boolean
): Boolean {
    val keyLength = columnFamilies.keyByteSize
    var index = keyLength
    val actualReference = dataStore.dataModelsById.getValue(columnFamilies.modelId)
        .getPropertyReferenceByStorageBytes(qualifier.size - keyLength, { qualifier[index++] })
    if (!hasActiveStoredType(columnFamilies, readOptions, qualifier.copyOfRange(0, keyLength), actualReference, toVersion, historicalReader)) return false
    val referencedKey = this.getValue(columnFamilies, readOptions, toVersion, qualifier, historicalReader) { valueBytes, offset, length ->
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
