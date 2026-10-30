package maryk.datastore.foundationdb.processors.helpers

import maryk.foundationdb.Transaction
import maryk.datastore.foundationdb.IsTableDirectories
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
internal fun Transaction.matchQualifierPresence(
    tableDirs: IsTableDirectories,
    key: ByteArray,
    reference: AnyPropertyReference,
    toVersion: ULong?,
    decryptValue: DecryptValue? = null,
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
                tableDirs.dataStore.dataModelsById.getValue(tableDirs.modelId).getPropertyReferenceByStorageBytes(qualifierBytes.size, { qualifierBytes[index++] })
            if (!hasActiveStoredType(tableDirs, key, actualReference, toVersion, decryptValue)) return false
            return getValue(tableDirs, toVersion, qualifier, keyLength = key.size) { bytes, offset, length ->
                hasStoredReferenceValue(actualReference, bytes, offset, length)
            } == true
        }
        var index = 0
        val actualReference = tableDirs.dataStore.dataModelsById.getValue(tableDirs.modelId)
            .getPropertyReferenceByStorageBytes(qualifierBytes.size, { qualifierBytes[index++] })
        if (!hasActiveStoredType(tableDirs, key, actualReference, toVersion, decryptValue)) return false
        val referencedKey = getValue(tableDirs, toVersion, qualifier, keyLength = key.size, decryptValue = decryptValue) { bytes, offset, length ->
            bytes.convertToValue(referenced.reference, offset, length)
        } ?: return false
        return matchQualifierPresence(
            tableDirs.dataStore.getTableDirs(referenced.reference.propertyDefinition.dataModel),
            referencedKey.bytes, reference, toVersion, decryptValue, referenced.qualifierMatcher
        )
    }
    return when (qualifierMatcher) {
        is QualifierExactMatcher -> atQualifier(qualifierMatcher.qualifier)
        is QualifierFuzzyMatcher -> iterateValues<Boolean>(tableDirs, toVersion, key.size, key + qualifierMatcher.firstPossible()) { bytes, offset, length, value, valueOffset, valueLength ->
            when (qualifierMatcher.isMatch(bytes, offset, length)) {
                NO_MATCH -> null
                OUT_OF_RANGE -> false
                MATCH -> {
                    val qualifier = bytes.copyOfRange(offset, offset + length)
                    var index = 0
                    val actualReference = tableDirs.dataStore.dataModelsById.getValue(tableDirs.modelId).getPropertyReferenceByStorageBytes(length, { qualifier[index++] })
                    if (!hasActiveStoredType(tableDirs, key, actualReference, toVersion, decryptValue)) return@iterateValues null
                    if (hasStoredReferenceValue(actualReference, value, valueOffset, valueLength) &&
                        (qualifierMatcher.referencedQualifierMatcher == null || atQualifier(qualifier, false))) true else null
                }
            }
        } == true
    }
}
