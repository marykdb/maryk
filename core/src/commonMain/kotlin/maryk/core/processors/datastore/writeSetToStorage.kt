package maryk.core.processors.datastore

import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.IsValueDefinition

/** Write a complete [set] defined by [definition] with [qualifierWriter] of [qualifierSize] to storage with [valueWriter]. */
@Suppress("UNCHECKED_CAST")
fun <T : IsPropertyDefinition<*>> writeSetToStorage(
    qualifierCount: Int,
    qualifierWriter: QualifierWriter,
    valueWriter: ValueWriter<T>,
    definition: T,
    set: Set<*>
) {
    // Process Set Count
    valueWriter(
        SetSize as StorageTypeEnum<T>,
        writeQualifier(qualifierCount, qualifierWriter),
        definition,
        set.size
    )

    // Process Set Values
    val setValueDefinition = (definition as IsSetDefinition<Any, *>).valueDefinition as IsSimpleValueDefinition<Any, *>
    val comparableSet = set as Set<Comparable<Any>>
    for (setItem in comparableSet.sorted()) {
        val setValueQualifierWriter: QualifierWriter = { writer ->
            qualifierWriter.invoke(writer)
            setValueDefinition.writeStorageBytes(setItem, writer)
        }
        writeValue(
            -1,
            qualifierCount + setValueDefinition.calculateStorageByteLength(setItem),
            setValueQualifierWriter,
            setValueDefinition,
            setItem,
            valueWriter as ValueWriter<IsValueDefinition<*, *>>
        )
    }
}
