package maryk.core.processors.datastore

import maryk.core.properties.definitions.IncrementingMapDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.lib.extensions.compare.prevByteInSameLength

/** [addValues] to an incrementing map defined by [definition] with [currentIncMapKey] to storage with [valueWriter]. */
@Suppress("UNCHECKED_CAST")
fun <K: Comparable<K>, V : Any, D : IncrementingMapDefinition<K, V, *>> writeIncMapAdditionsToStorage(
    currentIncMapKey: ByteArray,
    valueWriter: ValueWriter<D>,
    definition: D,
    addValues: List<V>
): List<K> {
    val qualifierLength = currentIncMapKey.size
    var nextIncMapKey = currentIncMapKey
    val qualifierWriter: QualifierWriter = { writer ->
        nextIncMapKey.forEach(writer)
    }

    val keyDefinition = definition.keyDefinition

    return addValues.map { mapValue ->
        nextIncMapKey = nextIncMapKey.prevByteInSameLength(keyDefinition.byteSize)
        writeValue(
            null, qualifierLength, qualifierWriter,
            definition.valueDefinition,
            mapValue,
            valueWriter as ValueWriter<IsSubDefinition<*, *>>
        )

        var keyIndex = currentIncMapKey.size - keyDefinition.byteSize
        keyDefinition.readStorageBytes(keyDefinition.byteSize) {
            nextIncMapKey[keyIndex++]
        }
    }
}
