package maryk.core.processors.datastore

import maryk.core.extensions.bytes.writeBytes
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSubDefinition

/** Write a complete [list] defined by [definition] with [qualifierWriter] of [qualifierCount] to storage with [valueWriter]. */
@Suppress("UNCHECKED_CAST")
fun <T : IsPropertyDefinition<*>> writeListToStorage(
    qualifierCount: Int,
    qualifierWriter: QualifierWriter,
    valueWriter: ValueWriter<T>,
    definition: T,
    list: List<*>
) {
    // Write List Size
    valueWriter(
        ListSize as StorageTypeEnum<T>,
        writeQualifier(qualifierCount, qualifierWriter),
        definition,
        list.size
    )

    // Process List values
    val listDefinition = definition as IsListDefinition<*, *>
    list.forEachIndexed { listIndex, listItem ->
        val listValueQualifierWriter: QualifierWriter = { writer ->
            qualifierWriter(writer)
            listIndex.toUInt().writeBytes(writer, 4)
        }
        writeValue(
            null,
            qualifierCount + 4,
            listValueQualifierWriter,
            listDefinition.valueDefinition,
            listItem as Any,
            valueWriter as ValueWriter<IsSubDefinition<*, *>>
        )
    }
}
