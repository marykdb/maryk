package maryk.core.processors.datastore

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.IsValueDefinition

/** Write a complete [set] defined by [definition] with [qualifierWriter] of [qualifierCount] to storage with [valueWriter]. */
@Suppress("UNCHECKED_CAST")
fun <T : IsPropertyDefinition<*>> writeSetToStorage(
    qualifierCount: Int,
    qualifierWriter: QualifierWriter,
    valueWriter: ValueWriter<T>,
    definition: T,
    set: Set<*>
) {
    // Write Set Size
    valueWriter(
        SetSize as StorageTypeEnum<T>,
        writeQualifier(qualifierCount, qualifierWriter),
        definition,
        set.size
    )

    // Process Set Values
    val setValueDefinition = (definition as IsSetDefinition<*, *>).valueDefinition as IsSimpleValueDefinition<Any, *>
    (set as Set<Comparable<Any>>).sorted().forEach { setItem ->
        val setItemByteSize = setValueDefinition.calculateStorageByteLength(setItem)
        val setItemQualifierLength = qualifierCount + setItemByteSize + setItemByteSize.calculateVarByteLength()

        writeValue(
            null,
            setItemQualifierLength,
            { writer ->
                qualifierWriter(writer)
                setItemByteSize.writeVarBytes(writer)
                setValueDefinition.writeStorageBytes(setItem, writer)
            },
            setValueDefinition,
            setItem,
            valueWriter as ValueWriter<IsValueDefinition<*, *>>
        )
    }
}
