package maryk.core.processors.datastore

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.toCollectionQualifierBytes
import maryk.core.properties.types.Bytes
import maryk.core.properties.definitions.validateCollectionStorageIdentities

/** Write a complete [set] defined by [definition] with [qualifierWriter] of [qualifierCount] to storage with [valueWriter]. */
@Suppress("UNCHECKED_CAST")
fun <T : IsPropertyDefinition<*>> writeSetToStorage(
    qualifierCount: Int,
    qualifierWriter: QualifierWriter,
    valueWriter: ValueWriter<T>,
    definition: T,
    set: Set<*>
) {
    definition.validateCollectionStorageIdentities(set)
    // Write Set Size
    valueWriter(
        SetSize as StorageTypeEnum<T>,
        writeQualifier(qualifierCount, qualifierWriter),
        definition,
        set.size
    )

    // Process Set Values
    val setValueDefinition = (definition as IsSetDefinition<*, *>).valueDefinition as IsSimpleValueDefinition<Any, *>
    val items = (set as Set<Any>).map { it to setValueDefinition.toCollectionQualifierBytes(it) }.sortedBy { Bytes(it.second) }
    items.forEach { (setItem, encodedItem) ->
        val setItemByteSize = encodedItem.size
        val setItemQualifierLength = qualifierCount + setItemByteSize + setItemByteSize.calculateVarByteLength()

        writeValue(
            null,
            setItemQualifierLength,
            { writer ->
                qualifierWriter(writer)
                setItemByteSize.writeVarBytes(writer)
                encodedItem.forEach(writer)
            },
            setValueDefinition,
            setItem,
            valueWriter as ValueWriter<IsValueDefinition<*, *>>
        )
    }
}
