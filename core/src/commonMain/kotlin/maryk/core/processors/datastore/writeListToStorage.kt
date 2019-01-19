@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.core.processors.datastore

import maryk.core.extensions.bytes.writeBytes
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.references.ListReference

/** Write a complete [list] referenced by [reference] to storage with [valueWriter]. */
fun <T: Any> writeListToStorage(
    reference: ListReference<T, *>,
    valueWriter: ValueWriter<IsPropertyDefinition<*>>,
    list: List<T>
) {
    writeListToStorage(reference::writeStorageBytes, reference.calculateStorageByteLength(), valueWriter, reference.propertyDefinition.definition, list)
}

@Suppress("UNCHECKED_CAST")
internal fun <T : IsPropertyDefinition<*>> writeListToStorage(
    qualifierWriter: QualifierWriter,
    qualifierCount: Int,
    valueWriter: ValueWriter<T>,
    definition: T,
    value: List<*>
) {
    // Process List Count
    valueWriter(
        ListSize as StorageTypeEnum<T>,
        writeQualifier(qualifierCount, qualifierWriter),
        definition,
        value.size
    ) // for list count

    // Process List values
    val listValueDefinition = (definition as ListDefinition<Any, *>).valueDefinition as IsSimpleValueDefinition<Any, *>
    for ((listIndex, listItem) in (value as List<Any>).withIndex()) {
        val listValueQualifierWriter: QualifierWriter = { writer ->
            qualifierWriter.invoke(writer)
            listIndex.toUInt().writeBytes(writer, 4)
        }
        writeValue(
            -1, qualifierCount + 4, listValueQualifierWriter,
            listValueDefinition,
            listItem,
            valueWriter as ValueWriter<IsValueDefinition<*, *>>
        )
    }
}
