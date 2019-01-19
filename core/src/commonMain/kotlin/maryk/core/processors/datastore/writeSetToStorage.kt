package maryk.core.processors.datastore

import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.references.SetReference

/** Write a complete [set] referenced by [reference] to storage with [valueWriter]. */
fun <T: Any> writeSetToStorage(
    reference: SetReference<T, *>,
    valueWriter: ValueWriter<IsPropertyDefinition<*>>,
    set: Set<T>
) {
    writeSetToStorage(reference::writeStorageBytes, reference.calculateStorageByteLength(), valueWriter, reference.propertyDefinition.definition, set)
}

@Suppress("UNCHECKED_CAST")
internal fun <T : IsPropertyDefinition<*>> writeSetToStorage(
    qualifierWriter: QualifierWriter,
    qualifierCount: Int,
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
    ) // for set count

    // Process Set Values
    val setValueDefinition = (definition as SetDefinition<Any, *>).valueDefinition as IsSimpleValueDefinition<Any, *>
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
