package maryk.datastore.foundationdb.processors.helpers

import maryk.foundationdb.Transaction
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.core.properties.references.AnyPropertyReference
import maryk.datastore.shared.hasStoredReferenceValue
import maryk.datastore.shared.readStoredReferenceValue
import maryk.datastore.shared.requiresStorageParentMarker
import maryk.datastore.shared.storageReferenceParents

/** Materialize the selected complex value, retaining only its own subtree and real ancestor markers. */
internal fun Transaction.readFilterStructure(
    tableDirs: IsTableDirectories,
    key: ByteArray,
    reference: AnyPropertyReference,
    toVersion: ULong?,
    decryptValue: DecryptValue?,
): Any? {
    if (getValue(tableDirs, toVersion, key + reference.toStorageByteArray(), keyLength = key.size, decryptValue = decryptValue) { bytes, offset, length ->
        hasStoredReferenceValue(reference, bytes, offset, length)
    } != true) return null
    val rows = mutableListOf<Pair<ByteArray, ByteArray>>()
    for (parent in storageReferenceParents(reference)) {
        val present = getValue(tableDirs, toVersion, key + parent.toStorageByteArray(), keyLength = key.size, decryptValue = decryptValue) { bytes, offset, length ->
            if (!hasStoredReferenceValue(parent, bytes, offset, length)) false
            else {
                rows += parent.toStorageByteArray() to bytes.copyOfRange(offset, offset + length)
                true
            }
        } == true
        if (!present && requiresStorageParentMarker(parent)) return null
    }
    iterateValues<Any>(tableDirs, toVersion, key.size, key + reference.toStorageByteArray(), decryptValue = decryptValue) { qualifier, offset, length, bytes, valueOffset, valueLength ->
        rows += qualifier.copyOfRange(offset, offset + length) to bytes.copyOfRange(valueOffset, valueOffset + valueLength)
        null
    }
    return tableDirs.dataStore.dataModelsById.getValue(tableDirs.modelId).readStoredReferenceValue(reference, rows)
}
