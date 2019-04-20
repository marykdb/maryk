package maryk.core.processors.datastore

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSubDefinition

/** Write a complete [map] defined by [definition] with [qualifierWriter] of [qualifierLength] to storage with [valueWriter]. */
@Suppress("UNCHECKED_CAST")
fun <T : IsPropertyDefinition<*>, K : Any, V : Any> writeMapToStorage(
    qualifierLength: Int,
    qualifierWriter: QualifierWriter?,
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
            null, mapValueQualifierLength, mapValueQualifierWriter,
            mapDefinition.valueDefinition,
            mapValue,
            valueWriter as ValueWriter<IsSubDefinition<*, *>>
        )
    }
}
