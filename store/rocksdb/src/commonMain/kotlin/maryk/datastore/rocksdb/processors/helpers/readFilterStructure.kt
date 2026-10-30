package maryk.datastore.rocksdb.processors.helpers

import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.rocksdb.ReadOptions
import maryk.core.properties.references.AnyPropertyReference
import maryk.datastore.shared.hasStoredReferenceValue
import maryk.datastore.shared.readStoredReferenceValue
import maryk.datastore.shared.requiresStorageParentMarker
import maryk.datastore.shared.storageReferenceParents

/** Materialize the selected complex value, retaining only its own subtree and real ancestor markers. */
internal fun DBAccessor.readFilterStructure(
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: ByteArray,
    reference: AnyPropertyReference,
    toVersion: ULong?,
    historicalReader: HistoricalTableReader?,
): Any? {
    if (getValue(columnFamilies, readOptions, toVersion, key + reference.toStorageByteArray(), historicalReader) { bytes, offset, length ->
        hasStoredReferenceValue(reference, bytes, offset, length)
    } != true) return null
    val rows = mutableListOf<Pair<ByteArray, ByteArray>>()
    for (parent in storageReferenceParents(reference)) {
        val present = getValue(columnFamilies, readOptions, toVersion, key + parent.toStorageByteArray(), historicalReader) { bytes, offset, length ->
            if (!hasStoredReferenceValue(parent, bytes, offset, length)) false
            else {
                rows += parent.toStorageByteArray() to bytes.copyOfRange(offset, offset + length)
                true
            }
        } == true
        if (!present && requiresStorageParentMarker(parent)) return null
    }
    iterateValues<Any>(columnFamilies, readOptions, toVersion, key.size, key + reference.toStorageByteArray(), historicalReader) { qualifier, offset, length, bytes, valueOffset, valueLength ->
        rows += qualifier.copyOfRange(offset, offset + length) to bytes.copyOfRange(valueOffset, valueOffset + valueLength)
        null
    }
    return dataStore.dataModelsById.getValue(columnFamilies.modelId).readStoredReferenceValue(reference, rows)
}
