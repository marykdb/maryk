package maryk.core.processors.datastore

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.references.MapReference

@Suppress("UNCHECKED_CAST")
fun <T : IsPropertyDefinition<*>> writeMapToStorage(
    reference: MapReference<*, *, *>,
    valueWriter: ValueWriter<T>,
    definition: T,
    value: Map<*, *>
) {
    writeMapToStorage(reference::writeStorageBytes, reference.calculateStorageByteLength(), valueWriter, definition, value)
}

@Suppress("UNCHECKED_CAST")
internal fun <T : IsPropertyDefinition<*>> writeMapToStorage(
    qualifierWriter: QualifierWriter?,
    qualifierLength: Int,
    valueWriter: ValueWriter<T>,
    definition: T,
    value: Map<*, *>
) {
    // Process Map Count
    valueWriter(
        MapSize as StorageTypeEnum<T>,
        writeQualifier(qualifierLength, qualifierWriter),
        definition,
        value.size
    )

    // Process Map Values
    val mapDefinition = (definition as IsMapDefinition<Any, *, *>)
    val map = value as Map<Any, Any>
    for ((key, mapValue) in map) {
        val keyByteSize = mapDefinition.keyDefinition.calculateStorageByteLength(key)
        val keyByteCountSize = keyByteSize.calculateVarByteLength()

        val mapValueQualifierWriter: QualifierWriter = { writer ->
            qualifierWriter?.invoke(writer)
            keyByteSize.writeVarBytes(writer)
            mapDefinition.keyDefinition.writeStorageBytes(key, writer)
        }
        val mapValueQualifierLength = qualifierLength + keyByteSize + keyByteCountSize

        writeValue(
            -1, mapValueQualifierLength, mapValueQualifierWriter,
            mapDefinition.valueDefinition,
            mapValue,
            valueWriter as ValueWriter<IsSubDefinition<*, *>>
        )
    }
}
