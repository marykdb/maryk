package maryk.datastore.rocksdb.processors.helpers

import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.rocksdb.ReadOptions
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.NO_MATCH
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.OUT_OF_RANGE
import maryk.core.processors.datastore.matchers.IsQualifierMatcher
import maryk.core.processors.datastore.matchers.QualifierExactMatcher
import maryk.core.processors.datastore.matchers.QualifierFuzzyMatcher
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.SimpleTypedValueReference
import maryk.datastore.shared.hasStoredReferenceValue
import maryk.datastore.shared.helpers.convertToValue

/** Presence reads raw point markers or matching raw rows, without decrypting field payloads. */
internal fun DBAccessor.matchQualifierPresence(
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: ByteArray,
    reference: AnyPropertyReference,
    toVersion: ULong?,
    historicalReader: HistoricalTableReader? = null,
    qualifierMatcher: IsQualifierMatcher = reference.toQualifierMatcher(),
): Boolean {
    fun atQualifier(qualifierBytes: ByteArray, reuseReader: Boolean = true): Boolean {
        val qualifier = key + qualifierBytes
        val referenced = when (qualifierMatcher) {
            is QualifierExactMatcher -> qualifierMatcher.referencedQualifierMatcher
            is QualifierFuzzyMatcher -> qualifierMatcher.referencedQualifierMatcher
        }
        if (referenced == null) {
            var index = 0
            val actualReference = if (reference is SimpleTypedValueReference<*, *, *>) reference else
                dataStore.dataModelsById.getValue(columnFamilies.modelId).getPropertyReferenceByStorageBytes(qualifierBytes.size, { qualifierBytes[index++] })
            if (!hasActiveStoredType(columnFamilies, readOptions, key, actualReference, toVersion, if (reuseReader) historicalReader else null)) return false
            return getValue(columnFamilies, readOptions, toVersion, qualifier, if (reuseReader) historicalReader else null, decryptValue = false) { bytes, offset, length ->
                hasStoredReferenceValue(actualReference, bytes, offset, length)
            } == true
        }
        var index = 0
        val actualReference = dataStore.dataModelsById.getValue(columnFamilies.modelId)
            .getPropertyReferenceByStorageBytes(qualifierBytes.size, { qualifierBytes[index++] })
        if (!hasActiveStoredType(columnFamilies, readOptions, key, actualReference, toVersion, if (reuseReader) historicalReader else null)) return false
        val referencedKey = getValue(columnFamilies, readOptions, toVersion, qualifier, if (reuseReader) historicalReader else null) { bytes, offset, length ->
            bytes.convertToValue(referenced.reference, offset, length)
        } ?: return false
        return matchQualifierPresence(
            dataStore.getColumnFamilies(referenced.reference.propertyDefinition.dataModel), readOptions,
            referencedKey.bytes, reference, toVersion, null, referenced.qualifierMatcher
        )
    }
    return when (qualifierMatcher) {
        is QualifierExactMatcher -> atQualifier(qualifierMatcher.qualifier)
        is QualifierFuzzyMatcher -> iterateValues<Boolean>(columnFamilies, readOptions, toVersion, key.size, key + qualifierMatcher.firstPossible(), historicalReader, decryptValue = false) { bytes, offset, length, value, valueOffset, valueLength ->
            when (qualifierMatcher.isMatch(bytes, offset, length)) {
                NO_MATCH -> null
                OUT_OF_RANGE -> false
                MATCH -> {
                    val qualifier = bytes.copyOfRange(offset, offset + length)
                    var index = 0
                    val actualReference = dataStore.dataModelsById.getValue(columnFamilies.modelId).getPropertyReferenceByStorageBytes(length, { qualifier[index++] })
                    if (!hasActiveStoredType(columnFamilies, readOptions, key, actualReference, toVersion)) return@iterateValues null
                    if (hasStoredReferenceValue(actualReference, value, valueOffset, valueLength) &&
                        (qualifierMatcher.referencedQualifierMatcher == null || atQualifier(qualifier, false))) true else null
                }
            }
        } == true
    }
}
