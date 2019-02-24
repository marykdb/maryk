package maryk.core.processors.datastore

import maryk.core.extensions.bytes.writeBytes
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.IsValueDefinition

/** Write a complete [list] defined by [definition] with [qualifierWriter] of [qualifierCount] to storage with [valueWriter]. */
@Suppress("UNCHECKED_CAST")
fun <T : IsPropertyDefinition<*>> writeListToStorage(
    qualifierCount: Int,
    qualifierWriter: QualifierWriter,
    valueWriter: ValueWriter<T>,
    definition: T,
    list: List<*>
) {
    // Process List Count
    valueWriter(
        ListSize as StorageTypeEnum<T>,
        writeQualifier(qualifierCount, qualifierWriter),
        definition,
        list.size
    )

    // Process List values
    val listValueDefinition =
        (definition as IsListDefinition<Any, *>).valueDefinition as IsSimpleValueDefinition<Any, *>
    for ((listIndex, listItem) in (list as List<Any>).withIndex()) {
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
