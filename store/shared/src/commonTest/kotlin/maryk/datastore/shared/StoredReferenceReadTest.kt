package maryk.datastore.shared

import maryk.core.extensions.bytes.toVarBytes
import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.processors.datastore.StorageTypeEnum.Embed
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.processors.datastore.writeToStorage
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Bytes
import maryk.core.query.pairs.with
import maryk.core.values.Values
import maryk.lib.extensions.compare.matchesRangePart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private object FilterProjectionLeaf : DataModel<FilterProjectionLeaf>() {
    val items by list(1u, required = false, valueDefinition = StringDefinition())
    val mapping by map(2u, required = false, keyDefinition = StringDefinition(), valueDefinition = StringDefinition())
}

private object FilterProjectionRecord : RootDataModel<FilterProjectionRecord>() {
    val entries by list(1u, valueDefinition = EmbeddedValuesDefinition(dataModel = { FilterProjectionLeaf }))
}

class StoredReferenceReadTest {
    @Test
    fun selectedListAncestorUsesItsActualBoundsWithoutInventingEarlierItems() {
        for (empty in listOf(true, false)) {
            val leaf = FilterProjectionLeaf.create {
                items with if (empty) emptyList() else listOf("selected")
                mapping with if (empty) emptyMap() else mapOf("key" to "selected")
            }
            val record = FilterProjectionRecord.create {
                entries with listOf(FilterProjectionLeaf.create { items with listOf("unselected") }, leaf)
            }
            val rows = record.toEncodedRows()
            val entry = FilterProjectionRecord.ref { entries at 1u }
            for (reference in listOf<AnyPropertyReference>(entry,
                FilterProjectionLeaf.ref(entry) { items }, FilterProjectionLeaf.ref(entry) { mapping },
            )) {
                val selectedRows = rows.selectedRows(reference)
                val originalRows = selectedRows.map { Bytes(it.first.copyOf()) to Bytes(it.second.copyOf()) }
                @Suppress("UNCHECKED_CAST")
                assertEquals(record[reference as IsPropertyReference<Any, *, *>],
                    FilterProjectionRecord.readStoredReferenceValue(reference, selectedRows), reference.completeName)
                assertEquals(originalRows, selectedRows.map { Bytes(it.first) to Bytes(it.second) })
                val outOfBounds = selectedRows.map { (qualifier, bytes) ->
                    qualifier to if (qualifier.contentEquals(FilterProjectionRecord.entries.ref().toStorageByteArray())) byteArrayOf(1) else bytes
                }
                assertNull(FilterProjectionRecord.readStoredReferenceValue(reference, outOfBounds))
            }
        }
    }
}

private fun Values<FilterProjectionRecord>.toEncodedRows(): List<Pair<ByteArray, ByteArray>> = buildList {
    writeToStorage { type, qualifier, definition, value ->
        val bytes = when (type) {
            ListSize, SetSize, MapSize -> (value as Int).toVarBytes()
            Embed -> byteArrayOf(TypeIndicator.EmbedIndicator.byte)
            Value -> Value.castDefinition(definition).toStorageBytes(value, TypeIndicator.NoTypeIndicator.byte)
            else -> error("Unexpected test storage type $type")
        }
        add(qualifier to bytes)
    }
}

private fun List<Pair<ByteArray, ByteArray>>.selectedRows(reference: AnyPropertyReference): List<Pair<ByteArray, ByteArray>> {
    val qualifier = reference.toStorageByteArray()
    val parents = storageReferenceParents(reference).map { Bytes(it.toStorageByteArray()) }.toSet()
    return filter { (candidate, _) -> candidate.matchesRangePart(0, qualifier) || Bytes(candidate) in parents }
}
