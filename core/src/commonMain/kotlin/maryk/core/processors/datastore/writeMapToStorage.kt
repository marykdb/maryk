package maryk.core.processors.datastore

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.references.MapReference

/** Write a complete [map] referenced by [reference] to storage with [valueWriter]. */
fun <K: Any, V: Any> writeMapToStorage(
    reference: MapReference<K, V, *>,
    valueWriter: ValueWriter<IsPropertyDefinition<*>>,
    map: Map<K, V>
) {
    writeMapToStorage(reference::writeStorageBytes, reference.calculateStorageByteLength(), valueWriter, reference.propertyDefinition.definition, map)
}

@Suppress("UNCHECKED_CAST")
internal fun <T : IsPropertyDefinition<*>, K: Any, V: Any> writeMapToStorage(
    qualifierWriter: QualifierWriter?,
    qualifierLength: Int,
    valueWriter: ValueWriter<T>,
    definition: T,
    map: Map<K, V>
) {
    // Process Map Count
    valueWriter(
        MapSize as StorageTypeEnum<T>,
        writeQualifier(qualifierLength, qualifierWriter),
        definition,
        map.size
    )

    // Process Map Values
    val mapDefinition = (definition as IsMapDefinition<Any, *, *>)
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
